# Gerçek JNI ile masaüstü entegrasyon testi

`NativePipelineTest` varsayılan olarak model ve host JNI yoksa atlanır. Bu bir Android ABI testi veya telefon performans ölçümü değildir. Aynı `bridge.cpp` / `engine.cpp` kaynakları masaüstü llama.cpp ile bağlanır ve Robolectric Android 9 ortamında üretim Kotlin `LocalModel` sınıfı tarafından gerçekten çağrılır.

Önce `docs/run-native-smoke.sh MODEL` ile host llama.cpp derlemesini hazırlayın. Sonra (Linux örneği):

```sh
mkdir -p build/host-jni
c++ -shared -fPIC -std=c++17 -O2 \
  app/src/main/cpp/bridge.cpp app/src/main/cpp/engine.cpp \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -Ivendor/llama.cpp/include -Ivendor/llama.cpp/ggml/include \
  -Lbuild/host-smoke/bin -Wl,-rpath,"$PWD/build/host-smoke/bin" \
  -lllama -o build/host-jni/libgundem.so
GUNDEM_NATIVE_LIB_DIR="$PWD/build/host-jni" \
GUNDEM_TEST_MODEL=/path/to/model.gguf \
./gradlew :app:testDebugUnitTest
```

İlgili llama.cpp CMake sürümünüz kütüphaneyi alt `llama/bin` dizinine yerleştirirse `-L`/`rpath` yolunu buna göre değiştirin. Bu projedeki teslim doğrulaması `work/llama-host/bin` derlemesiyle çalıştırıldı.

Test akışı: gerçek RSS'ten alınmış kısa bir örneği ayrıştır → Room'a kaydet → JNI modeli bir kez yükle → gerçek sınıflandırma/alan tamiri → önbellek ve Room'a yaz → puan hesapla → bilerek 1 token'a kesilmiş çıktının başarısız sayılması. Haber örnekleri yalnız test kaynaklarındadır ve uygulamada başlangıç verisi olarak kullanılmaz.
