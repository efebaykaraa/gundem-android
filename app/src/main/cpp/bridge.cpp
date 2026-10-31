#include <jni.h>
#include "engine.h"
#include <exception>
static Engine engine;
static std::string string(JNIEnv *env,jstring s) { const char *p=env->GetStringUTFChars(s,nullptr);std::string r(p);env->ReleaseStringUTFChars(s,p);return r; }
static void fail(JNIEnv *e,const std::exception &x) { e->ThrowNew(e->FindClass("java/lang/IllegalStateException"),x.what()); }
extern "C" JNIEXPORT void JNICALL Java_tr_gundem_NativeLlama_load(JNIEnv *e,jobject,jstring p) { try { engine.load(string(e,p)); } catch(const std::exception &x) { fail(e,x); } }
extern "C" JNIEXPORT void JNICALL Java_tr_gundem_NativeLlama_close(JNIEnv *,jobject) { engine.close(); }
extern "C" JNIEXPORT void JNICALL Java_tr_gundem_NativeLlama_cancel(JNIEnv *,jobject) { engine.cancelled=true; }
extern "C" JNIEXPORT jstring JNICALL Java_tr_gundem_NativeLlama_generate(JNIEnv *e,jobject,jstring s,jstring u,jstring g,jint n) { try { auto result=engine.generate(string(e,s),string(e,u),string(e,g),n);return e->NewStringUTF(result.c_str()); } catch(const std::exception &x) { fail(e,x); return nullptr; } }

extern "C" JNIEXPORT jstring JNICALL Java_tr_gundem_NativeLlama_stats(JNIEnv *e,jobject) { return e->NewStringUTF(engine.stats.c_str()); }

extern "C" JNIEXPORT jstring JNICALL Java_tr_gundem_NativeLlama_architecture(JNIEnv *e,jobject) {return e->NewStringUTF(engine.architecture().c_str());}
extern "C" JNIEXPORT jint JNICALL Java_tr_gundem_NativeLlama_loadProgress(JNIEnv *,jobject) { return engine.load_progress.load(); }
