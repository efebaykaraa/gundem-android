# Doğrulama — 29 Ekim 2026

## Teslim

- Proje: `outputs/Gundem/`.
- Son debug APK: `app/build/outputs/apk/debug/app-debug.apk`; ayrıca teslim dizininde `Gundem-debug.apk`.
- APK SHA-256: `4a56586227aa3435a559648111ca2a09c133317df1fabb36e9248ddcf49383c0`.
- Android 9+ / minSdk 28, targetSdk 36, compileSdk 37.
- APK içinde `arm64-v8a` ve `x86_64` JNI kütüphaneleri var; model, haber örnekleri ve X kimlik bilgileri yok.
- `apksigner verify` başarılı, debug APK v2 imzası doğrulandı.

## Başarılı kontroller

| Kontrol | Sonuç |
|---|---|
| `:app:assembleDebug` | Başarılı; iki ABI native derlendi |
| `:app:assembleDebugAndroidTest` | Başarılı |
| `:app:lintDebug` | Başarılı, 0 hata; sürüm/target/backup/KTX öneri uyarıları mevcut |
| DomainTest | 19 başarılı |
| FeedTest | 5 başarılı |
| RoomPersistenceTest (Robolectric API 28) | 4 başarılı |
| NativePipelineTest (gerçek GGUF + host JNI) | 1 başarılı |
| Android PersistenceTest, API 35 x86_64 | 5 başarılı, 1 isteğe bağlı tam-model testi atlandı |
| Android NativeBridgeTest, son APK | 1 başarılı, gerçek GGUF yükleme ve kısıtlı token üretimi |
| Üç kaynağın canlı HTTP/RSS doğrulaması | Üçü de HTTP 200; XML haberleri ayrıştırıldı |

Toplam **29 host testi** geçti (0 atlama, 0 hata). NativePipelineTest olmadan standart kurulumda 28 test çalışır, model isteyen bir test atlanır. Android PersistenceTest çıktısındaki `OK (6 tests)` bir atlanan testi de sayar; atlama `INSTRUMENTATION_STATUS_CODE: -4` ile kayıtlıdır. Son APK üzerindeki bağımsız NativeBridgeTest modeli gerçekten kullanır; atlanan tam sınıflandırma testiyle karıştırılmamalıdır.

### Gerçek model ve JNI kapsamı

Resmî `Qwen/Qwen3-0.6B-GGUF` deposundan Q8_0 indirildi; llama.cpp ile geçici Q4_K_M'e dönüştürüldü. APK'ya eklenmedi. Model hash'leri:

- Q8_0: `9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031`.
- Test Q4_K_M: `15e434d8ddf0af38fe3f7003af6ce6eac4edada6df34324aa45979f9cc79f02e`.

Host NativePipelineTest üretim Kotlin `LocalModel`, aynı C++ `bridge.cpp` ve `engine.cpp` dosyalarıyla gerçek çıkarım yapar. Gerçek RSS'ten alınmış kısa metin → Room → yerel sınıflandırma → alan doğrulama → önbellek → puan hesaplama → gruplama indeks doğrulama akışı geçmiştir. Bir token'a bilerek kesilen çıktı başarılı sayılmamıştır. Bu test Android ABI veya Redmi performans testi değildir.

Android NativeBridgeTest **son teslim APK'sının x86_64 kütüphanesiyle**, uygulamanın özel alanına aktarılan gerçek GGUF'u yüklemiş ve grammar altında gerçek token üretmiştir. Düşük token sınırı nedeniyle `CUT` dönmesi ve başarılı analiz sayılmaması doğrulanmıştır. Tam 160 token'lık Türkçe sınıflandırma Android emülatöründe ölçülmedi; tam akış host testinde çalıştırıldı. arm64-v8a derlendi, fiziksel ARM cihazında çalıştırılmadı.

### Kalıcılık ve mantık kapsamı

- Zorunlu alanlar, enumlar, 0–3 tam sayılar, hatalı/kesilmiş çıktılar, DIGER ayrımı ve etiket tekrarları.
- Hatalı alan başına tek tamir; tamir hatasında geçerli alanların korunması; etiket listesinin değiştirilmesi.
- Katsayı sınırları, aktif etiketin deterministik seçimi, formül, eksik tarih ve eksik puanın sıfıra dönüştürülmemesi.
- Saat/tarih/ondalık karışıklıkları ve geçmiş/gelecek/belirsiz işaretleri.
- Gruplama indekslerinin eksiksiz/tekrarsız doğrulanması, ortak konu/tarih adaylığı, zincirleme birleşmelerin engellenmesi ve manuel ayırmanın üstünlüğü.
- Room'un gerçek SQL sorgularıyla URL/kaynak kimliği benzersizliği; tekrarlı alımda manuel analiz, kayıt/okundu/gizleme ve çift kararlarının korunması.
- Eski analizin değişmiş içeriği ezmemesi; süreç kesintisi sonrası çalışan işlerin kuyruğa dönmesi.
- Model rol ayraçlarının haber metni üzerinden tokenizer'a enjekte edilmesinin engellenmesi.
- Yüzde kodlanmış URL sorgularının ve beslemelerin anlamlı son `/` işaretinin korunması.

### Canlı kaynak ve UI kapsamı

- soL: `https://haber.sol.org.tr/rss.xml` — HTTP 200, 10 kayıt.
- TKP: `https://www.tkp.org.tr/feed/` — HTTP 200, 30 kayıt.
- PE: `https://patronlarinensesindeyiz.org/feed/` — HTTP 200, 1000 kayıt; uygulama ilk 40'ı alır.
- Android canlı testinde **üçü de** indirildi, Room'a yazıldı ve aynı kayıtlar tekrar alındığında çoğalmadı.
- Emülatörde uygulamanın ana ekranı ve gerçek soL haber kartları görüldü; model yokken analiz bekliyor akışı çalıştı.
- Tüm filtre/tema/paylaşım/dosya seçici etkileşimleri otomatik uçtan uca UI testiyle taranmadı.

Emülatörde KVM yoktu. İlk başlangıçlarda System UI, Pixel Launcher ve Google servisleri de ANR yaşadı. İsteğe bağlı Google servisleri yalnız oluşturulan test emülatöründe kapatıldı, ekran yükü azaltıldı ve APK'lar önceden derlendi. Sonrasında yukarıdaki cihaz testleri geçti. Bu koşullar telefon hızını, pilini veya akıcılığını temsil etmez.

## Çalıştırma ve kanıt dosyaları

```sh
GUNDEM_NATIVE_LIB_DIR=/path/to/host-jni \
GUNDEM_TEST_MODEL=/path/to/Qwen3-0.6B-Q4_K_M.gguf \
./gradlew :app:testDebugUnitTest --rerun :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug

adb shell am instrument -w -r -e class tr.gundem.PersistenceTest -e liveFeeds 1 \
  tr.gundem.test/androidx.test.runner.AndroidJUnitRunner

adb shell am instrument -w -r -e class tr.gundem.NativeBridgeTest \
  -e modelPath /data/user/0/tr.gundem/files/models/android-smoke.gguf \
  tr.gundem.test/androidx.test.runner.AndroidJUnitRunner
```

`test-results/` içinde XML test sonuçları, JSON özeti, son APK derleme/lint çıktısı, Android kalıcılık/canlı RSS ve native günlükleri vardır. `source-verification.json` ve `verify-feeds.py` canlı kaynak doğrulamasını kaydeder/tekrarlar. Host JNI hazırlığı `host-jni.md` içindedir.

## Kalan sınırlar ve kullanıcıdan gerekenler

- `/mnt/Projects/Gundem` üst dizini yazılabilir değildi; sudo parola istedi. Kullanıcı dosyalarının üzerine yazılmadan alternatif `outputs/Gundem` oluşturuldu. Kaynak arşivi istenen makinede hedef dizine açılabilir.
- Kullanıcı GGUF modelini dosya seçiciyle sağlamalıdır. X için çalışan hesaba özel HTTPS Nitter/RSS adresi gerekir; ücretsiz çalışan sunucu veya resmî API erişimi varsayılmadı. Resmî X API adaptörü uygulanmadı.
- Redmi Note 13 Pro 4G üzerinde hız/RAM/pil ve termal ölçüm yapılmadı. APK'nın fiziksel cihaz smoke testi hâlâ gerekir.
- Küçük modelin anlamsal doğruluğu kalibre edilmedi. Gerçek denemelerde etiket/tür/puan hataları gözlendi; grammar bunları çözmez. Manuel düzeltme ve daha büyük uyumlu model seçimi sağlanır.
- Otomatik gruplama son 120 uygun kayıtta, 36 saatlik pencerede ikili başlık karşılaştırmasıyla yapılır. Belirsiz başlığa giriş soran ek tur yoktur. Eski kayıtlar elle birleştirilebilir.
- Görsel URL'si saklanır; görseller indirilmez/gösterilmez. Tam sayfa çevrimdışı arşiv ve bildirim yoktur; saklanan haber girişleri çevrimdışı okunur.
