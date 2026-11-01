#include <jni.h>
#include "engine.h"
#include "onnx_tokens.h"
#include <exception>
static Engine engine;
static OnnxTokens onnx_tokens;
static std::string string(JNIEnv *env,jstring s) { const char *p=env->GetStringUTFChars(s,nullptr);std::string r(p);env->ReleaseStringUTFChars(s,p);return r; }
static void fail(JNIEnv *e,const std::exception &x) { e->ThrowNew(e->FindClass("java/lang/IllegalStateException"),x.what()); }
extern "C" JNIEXPORT void JNICALL Java_tr_gundem_NativeLlama_load(JNIEnv *e,jobject,jstring p) { try { engine.load(string(e,p)); } catch(const std::exception &x) { fail(e,x); } }
extern "C" JNIEXPORT void JNICALL Java_tr_gundem_NativeLlama_close(JNIEnv *,jobject) { engine.close(); }
extern "C" JNIEXPORT void JNICALL Java_tr_gundem_NativeLlama_cancel(JNIEnv *,jobject) { engine.cancelled=true; }
extern "C" JNIEXPORT jstring JNICALL Java_tr_gundem_NativeLlama_generate(JNIEnv *e,jobject,jstring s,jstring u,jstring g,jint n) { try { auto result=engine.generate(string(e,s),string(e,u),string(e,g),n);return e->NewStringUTF(result.c_str()); } catch(const std::exception &x) { fail(e,x); return nullptr; } }

extern "C" JNIEXPORT jstring JNICALL Java_tr_gundem_NativeLlama_stats(JNIEnv *e,jobject) { return e->NewStringUTF(engine.stats.c_str()); }

extern "C" JNIEXPORT jstring JNICALL Java_tr_gundem_NativeLlama_architecture(JNIEnv *e,jobject) {return e->NewStringUTF(engine.architecture().c_str());}
extern "C" JNIEXPORT jint JNICALL Java_tr_gundem_NativeLlama_loadProgress(JNIEnv *,jobject) { return engine.load_progress.load(); }

extern "C" JNIEXPORT void JNICALL Java_tr_gundem_NativeTokens_load(JNIEnv *e,jobject,jstring p) {try {onnx_tokens.load(string(e,p));} catch(const std::exception &x) {fail(e,x);}}
extern "C" JNIEXPORT void JNICALL Java_tr_gundem_NativeTokens_close(JNIEnv *,jobject) {onnx_tokens.close();}
extern "C" JNIEXPORT void JNICALL Java_tr_gundem_NativeTokens_grammar(JNIEnv *e,jobject,jstring g) {try {onnx_tokens.grammar(string(e,g));} catch(const std::exception &x) {fail(e,x);}}
extern "C" JNIEXPORT jintArray JNICALL Java_tr_gundem_NativeTokens_encode(JNIEnv *e,jobject,jbyteArray bytes) {
    try {std::string text(e->GetArrayLength(bytes),'\0');e->GetByteArrayRegion(bytes,0,text.size(),reinterpret_cast<jbyte*>(text.data()));auto tokens=onnx_tokens.encode(text);auto result=e->NewIntArray(tokens.size());e->SetIntArrayRegion(result,0,tokens.size(),tokens.data());return result;} catch(const std::exception &x) {fail(e,x);return nullptr;}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_tr_gundem_NativeTokens_decode(JNIEnv *e,jobject,jintArray input) {
    try {std::vector<int32_t> tokens(e->GetArrayLength(input));e->GetIntArrayRegion(input,0,tokens.size(),tokens.data());auto text=onnx_tokens.decode(tokens);auto result=e->NewByteArray(text.size());e->SetByteArrayRegion(result,0,text.size(),reinterpret_cast<const jbyte*>(text.data()));return result;} catch(const std::exception &x) {fail(e,x);return nullptr;}
}
extern "C" JNIEXPORT jint JNICALL Java_tr_gundem_NativeTokens_sample(JNIEnv *e,jobject,jfloatArray input) {
    try {std::vector<float> logits(e->GetArrayLength(input));e->GetFloatArrayRegion(input,0,logits.size(),logits.data());return onnx_tokens.sample(logits.data(),logits.size());} catch(const std::exception &x) {fail(e,x);return -1;}
}
extern "C" JNIEXPORT jboolean JNICALL Java_tr_gundem_NativeTokens_end(JNIEnv *,jobject,jint token) {return onnx_tokens.end(token);}
