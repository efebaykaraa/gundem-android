#include "engine.h"
#include "llama.h"
#include <stdexcept>
#include <vector>
#include <memory>
#include <algorithm>
#include <chrono>
#include <mutex>
#if defined(__ANDROID__) && defined(__aarch64__)
#include <sys/auxv.h>
#include <asm/hwcap.h>
#endif

std::string Engine::architecture() const {
    if(!model) return "";
    char value[128]={0};llama_model_meta_val_str(model,"general.architecture",value,sizeof(value));return value;
}
Engine::Engine() = default;
Engine::~Engine() { close(); }
void Engine::close() { prefix_system.clear();prefix_tokens.clear();prefix_state.clear();prefix_state.shrink_to_fit(); if(ctx) llama_free(ctx); if(model) llama_model_free(model); ctx=nullptr;model=nullptr; }
void Engine::load(const std::string &path) {
    close(); cancelled=false;load_progress=-1;
    const auto deadline=std::chrono::steady_clock::now()+std::chrono::minutes(3);
    struct Loading { Engine *engine; std::chrono::steady_clock::time_point deadline; } loading{this,deadline};
    // Do not initialize the CPU backend in the shared library's constructor:
    // Java must finish loading JNI before progress/cancellation can be queried.
    load_progress=-4;
    static std::once_flag backend_init;
    std::call_once(backend_init,[] { llama_backend_init(); });
    load_progress=-1;
    auto mp=llama_model_default_params();mp.n_gpu_layers=0;mp.load_mode=LLAMA_LOAD_MODE_MMAP;
    mp.use_extra_bufts=false;
    stats="cpu_mode=portable, weight_repack=0";
    mp.progress_callback=[](float progress,void *p) {
        auto *state=static_cast<Loading*>(p);
        state->engine->load_progress=static_cast<int>(progress*100);
        return !state->engine->cancelled.load() && std::chrono::steady_clock::now()<state->deadline;
    };mp.progress_callback_user_data=&loading;
    model=llama_model_load_from_file(path.c_str(),mp);
    if(!model) throw std::runtime_error(std::chrono::steady_clock::now()>=deadline ? "Model yuklemesi zaman sinirini asti. Tekrar deneyin." : "GGUF yuklenemedi: uyumsuz model, iptal veya yetersiz bellek.");
    if(cancelled || std::chrono::steady_clock::now()>=deadline) { close();throw std::runtime_error("Model yuklemesi durduruldu veya zaman sinirini asti."); }
    load_progress=-2;
    // Headline-only requests fit comfortably; halve the previous KV/context allocation.
    auto cp=llama_context_default_params();cp.n_ctx=2048;cp.n_batch=256;cp.n_ubatch=256;cp.n_threads=2;cp.n_threads_batch=2;
    cp.abort_callback=[](void *p) { return static_cast<Engine*>(p)->cancelled.load(); };cp.abort_callback_data=this;
    ctx=llama_init_from_model(model,cp);
    if(!ctx) { close(); throw std::runtime_error("Model baglami olusturulamadi; daha kucuk model deneyin."); }
    if(cancelled || std::chrono::steady_clock::now()>=deadline) { close();throw std::runtime_error("Model yuklemesi durduruldu veya zaman sinirini asti."); }
    load_progress=101;
}
std::string Engine::generate(const std::string &system,const std::string &user,const std::string &grammar,int max_tokens) {
    if(!ctx || !model) throw std::runtime_error("Model yuklu degil.");
    cancelled=false;stats="";
    const auto started=std::chrono::steady_clock::now();
    const auto *vocab=llama_model_get_vocab(model);
    const char *templ=llama_model_chat_template(model,nullptr);
    if(!templ) throw std::runtime_error("Model sohbet sablonu icermiyor.");
    char architecture[128]={0};
    llama_model_meta_val_str(model,"general.architecture",architecture,sizeof(architecture));
    const bool qwen3=std::string(architecture)=="qwen3" || std::string(architecture)=="qwen3moe";
    std::string input=user+(qwen3?"\n/no_think":"");
    llama_chat_message messages[]={{"system",system.c_str()},{"user",input.c_str()}};
    int count=llama_chat_apply_template(templ,messages,2,true,nullptr,0);
    if(count<0) throw std::runtime_error("Bu GGUF sohbet sablonu desteklenmiyor.");
    std::vector<char> formatted(count+1);
    llama_chat_apply_template(templ,messages,2,true,formatted.data(),formatted.size());
    std::string prompt(formatted.data(),count);
    // Qwen3's non-thinking prefix, documented by Qwen. /no_think is the soft switch.
    // Never apply this prefix to unrelated model architectures/templates.
    if(qwen3 && std::string(templ).find("<think>")!=std::string::npos) {
        const std::string suffix="<think>\n\n</think>\n\n";
        if(prompt.size()<suffix.size() || prompt.substr(prompt.size()-suffix.size())!=suffix) prompt+=suffix;
    }
    // Liquid's official enable_thinking=false text-only template ends with this
    // exact suffix. The legacy chatml formatter omits it; do not force JSON into
    // the model's reasoning position.
    if(std::string(architecture)=="lfm3" && std::string(templ).find("<think>")!=std::string::npos) {
        prompt+="<think></think>";
        if(!llama_vocab_get_add_bos(vocab)) {
            char bos[128];int len=llama_token_to_piece(vocab,llama_vocab_bos(vocab),bos,sizeof(bos),0,true);
            if(len>0) prompt=std::string(bos,len)+prompt;
        }
    }
    int n=-llama_tokenize(vocab,prompt.data(),prompt.size(),nullptr,0,true,true);
    if(n<=0 || n+max_tokens+4>static_cast<int>(llama_n_ctx(ctx))) throw std::runtime_error("Girdi model baglam sinirini asiyor; girisi kisaltin.");
    std::vector<llama_token> tokens(n);llama_tokenize(vocab,prompt.data(),prompt.size(),tokens.data(),n,true,true);
    using Sampler=std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)>;
    Sampler sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()),llama_sampler_free);
    auto *g=llama_sampler_init_grammar(vocab,grammar.c_str(),"root");
    if(!g) throw std::runtime_error("Grammar yuklenemedi.");
    llama_sampler_chain_add(sampler.get(),g);llama_sampler_chain_add(sampler.get(),llama_sampler_init_greedy());
    // Restore was equality-tested for Qwen3. LFM3 recurrent state did not
    // reproduce cold results with this pinned runtime; keep its full prefill.
    int shared=0;
    if(qwen3 && prefix_system==system && !prefix_tokens.empty() && prefix_tokens.size()<tokens.size() && std::equal(prefix_tokens.begin(),prefix_tokens.end(),tokens.begin()) && !prefix_state.empty()) {
        llama_memory_clear(llama_get_memory(ctx),true);
        if(llama_state_set_data(ctx,prefix_state.data(),prefix_state.size())==prefix_state.size()) shared=prefix_tokens.size();
    }
    auto decode=[&](int begin,int end) {
        for(int i=begin;i<end;i+=256) {
            if(cancelled) throw std::runtime_error("Analiz iptal edildi.");
            auto batch=llama_batch_get_one(tokens.data()+i,std::min(256,end-i));
            if(llama_decode(ctx,batch)!=0) throw std::runtime_error("Girdi islenemedi; bellek veya iptal.");
        }
    };
    if(shared==0) {
        llama_memory_clear(llama_get_memory(ctx),true);
        prefix_state.clear();prefix_tokens.clear();prefix_system.clear();
        // Find the prefix shared with an empty user message. Never cache article content.
        llama_chat_message empty[]={{"system",system.c_str()},{"user",""}};
        int bytes=llama_chat_apply_template(templ,empty,2,true,nullptr,0);
        int prefix=0;
        if(qwen3 && bytes>0) {
            std::vector<char> buffer(bytes+1);llama_chat_apply_template(templ,empty,2,true,buffer.data(),buffer.size());
            int nt=-llama_tokenize(vocab,buffer.data(),bytes,nullptr,0,true,true);
            if(nt>0) {
                std::vector<llama_token> comparison(nt);llama_tokenize(vocab,buffer.data(),bytes,comparison.data(),nt,true,true);
                while(prefix<std::min(n,nt) && tokens[prefix]==comparison[prefix]) ++prefix;
                prefix=std::max(0,prefix-1);
            }
        }
        if(prefix>0) {
            decode(0,prefix);
            auto size=llama_state_get_size(ctx);
            // Bound the extra RAM used by the prefix snapshot.
            if(size<=192ULL*1024*1024) {
                prefix_state.resize(size);
                if(llama_state_get_data(ctx,prefix_state.data(),size)==size) {
                    prefix_system=system;prefix_tokens.assign(tokens.begin(),tokens.begin()+prefix);
                } else prefix_state.clear();
            }
        }
        decode(prefix,n);
    } else decode(shared,n);
    const auto prefilled=std::chrono::steady_clock::now();
    std::string out;bool complete=false;int emitted=0;
    for(int i=0;i<max_tokens;++i) {
        if(cancelled) throw std::runtime_error("Analiz iptal edildi.");
        auto token=llama_sampler_sample(sampler.get(),ctx,-1);
        if(llama_vocab_is_eog(vocab,token)) { complete=true;break; }
        char piece[512];int size=llama_token_to_piece(vocab,token,piece,sizeof(piece),0,true);
        if(size<0) throw std::runtime_error("Token parcasi siniri asildi.");
        out.append(piece,size);++emitted;
        auto batch=llama_batch_get_one(&token,1);
        if(llama_decode(ctx,batch)!=0) throw std::runtime_error("Cikti islenemedi; bellek veya iptal.");
    }
    const auto finished=std::chrono::steady_clock::now();
    auto ms=[](auto a,auto b) { return std::chrono::duration_cast<std::chrono::milliseconds>(b-a).count(); };
    stats="girdi_token="+std::to_string(n)+", ortak_token="+std::to_string(shared)+", girdi_ms="+std::to_string(ms(started,prefilled))+", cikti_token="+std::to_string(emitted)+", cikti_ms="+std::to_string(ms(prefilled,finished))+", onbellek_bayt="+std::to_string(prefix_state.size());
    stats+=", batch=256, threads=2, weight_repack=0";
#if defined(GUNDEM_PORTABLE_CPU)
    stats+=", cpu_mode=portable";
#endif
#if defined(GUNDEM_KLEIDIAI)
    stats+=", arm_kleidiai=1";
#else
    stats+=", arm_kleidiai=0";
#endif
#if defined(__ANDROID__) && defined(__aarch64__)
    const auto caps=getauxval(AT_HWCAP);
    stats+=", cpu_dotprod="+std::to_string((caps & HWCAP_ASIMDDP)!=0)+", cpu_fp16="+std::to_string((caps & HWCAP_ASIMDHP)!=0);
#endif
    return (complete?"OK\n":"CUT\n")+out;
}
