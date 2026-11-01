#include "onnx_tokens.h"
#include "llama.h"
#include <stdexcept>
#include <limits>

OnnxTokens::~OnnxTokens() { close(); }
void OnnxTokens::close() {
    if(sampler) llama_sampler_free(sampler);
    if(model) llama_model_free(model);
    sampler=nullptr;model=nullptr;
}
void OnnxTokens::load(const std::string &path) {
    close();auto params=llama_model_default_params();params.vocab_only=true;params.n_gpu_layers=0;
    model=llama_model_load_from_file(path.c_str(),params);
    if(!model) throw std::runtime_error("ONNX tokenizer yuklenemedi");
}
std::vector<int32_t> OnnxTokens::encode(const std::string &text) const {
    if(!model) throw std::runtime_error("Tokenizer yuklu degil");
    const auto *vocab=llama_model_get_vocab(model);
    int count=-llama_tokenize(vocab,text.data(),text.size(),nullptr,0,false,true);
    if(count<=0) throw std::runtime_error("Tokenizer girdisi bos");
    std::vector<int32_t> result(count);
    llama_tokenize(vocab,text.data(),text.size(),result.data(),count,false,true);
    return result;
}
std::string OnnxTokens::decode(const std::vector<int32_t> &tokens) const {
    if(!model) throw std::runtime_error("Tokenizer yuklu degil");
    std::string text;std::vector<char> piece(256);const auto *vocab=llama_model_get_vocab(model);
    for(auto token:tokens) {
        int n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false);
        if(n<0) { piece.resize(-n);n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false); }
        if(n>0) text.append(piece.data(),n);
    }
    return text;
}
void OnnxTokens::grammar(const std::string &source) {
    if(!model) throw std::runtime_error("Tokenizer yuklu degil");
    if(sampler) llama_sampler_free(sampler);
    sampler=llama_sampler_chain_init(llama_sampler_chain_default_params());
    auto *grammar=llama_sampler_init_grammar(llama_model_get_vocab(model),source.c_str(),"root");
    if(!grammar) throw std::runtime_error("ONNX grammar yuklenemedi");
    llama_sampler_chain_add(sampler,grammar);llama_sampler_chain_add(sampler,llama_sampler_init_greedy());
}
int OnnxTokens::sample(const float *logits,int count) {
    if(!model || !sampler || count!=llama_vocab_n_tokens(llama_model_get_vocab(model)))
        throw std::runtime_error("ONNX cikti/tokenizer boyutu uyusmuyor");
    std::vector<llama_token_data> data(count);
    const auto *vocab=llama_model_get_vocab(model);
    for(int i=0;i<count;++i) data[i]={i,(llama_vocab_get_attr(vocab,i)&LLAMA_TOKEN_ATTR_UNUSED)?-std::numeric_limits<float>::infinity():logits[i],0};
    llama_token_data_array candidates={data.data(),data.size(),-1,false};
    llama_sampler_apply(sampler,&candidates);
    if(candidates.selected<0 || candidates.selected>=static_cast<int64_t>(candidates.size))
        throw std::runtime_error("ONNX grammar token secemedi");
    auto token=candidates.data[candidates.selected].id;llama_sampler_accept(sampler,token);return token;
}
bool OnnxTokens::end(int token) const {
    return model && llama_vocab_is_eog(llama_model_get_vocab(model),token);
}
