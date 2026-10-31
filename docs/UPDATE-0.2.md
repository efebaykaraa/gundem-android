# Gündem 0.2.0 — model indirme ve X hesapları

APK SHA-256: `07bae33a24d231e03569341d9276c2a60a7993c194027834a990052f65fbc9b5`.

Bu sürüm önceki debug APK'nın üzerine güncellenebilir: applicationId `tr.gundem`, versionCode `2`, aynı debug imzası. Haberler, manuel düzeltmeler ve katsayılar korunur. Room şeması değişmedi; DataStore'a geriye uyumlu alanlar eklendi.

## Eklenenler

**Model → Modeli indir ve kur** önerilen Qwen3-0.6B Q4_K_M dosyasını doğrudan indirir. Yaklaşık 397 MB. Dosya Unsloth'un Hugging Face deposundaki sabit bir sürümden gelir; boyut ve SHA-256 kontrolünden, ardından gerçek GGUF yükleme kontrolünden geçer. Başarılı kurulumdan sonra model otomatik seçilir. Analiz duraklatılmış kalır; Başlat ile açılır. Dosya seçerek içe aktarma da korunur.

WorkManager indirmeyi ekran kapanmasından bağımsız yürütür; Android işi geciktirebilir/durdurabilir. İlerleme, hata ve iptal aynı ekrandadır. `.part` dosyası korunur; tekrar basıldığında gerçek HTTP Range ile devam edilir. Sunucu Range'i desteklemeyip 200 döndürürse güvenli biçimde baştan indirilir. Yanlış boyut/SHA model olarak kabul edilmez. Önceki seçilmiş model başarılı kurulum tamamlanana kadar korunur.

**Kaynaklar → X hesapları:**

- TKP `@tkpninsesi`, Kemal Okuyan `@OkuyanKemal`, PE `@pensendeyiz` başlangıç listesine bir defa eklenir.
- Seçme/açma-kapatma, ayrı kaynak katsayısı, listeden kaldırma ve son yenileme/hata durumu vardır.
- `@kullanıcı_adı` veya X/Twitter profil bağlantısıyla yeni hesap eklenir; görünen ad isteğe bağlıdır.
- Büyük/küçük harf veya profil bağlantısı farkıyla aynı hesap tekrar eklenmez.
- Kaldırılan hazır hesap bir sonraki açılışta yeniden eklenmez.
- Kayıtlı hesap araması yereldir. **X'te hesap ara** X'in kendi kişi arama sayfasını açar; oturum gerekebilir. Tüm X kullanıcılarını uygulama içinde canlı aradığı iddia edilmez.
- Ortak HTTPS Nitter sunucusu `/kullanıcı/rss` beslemelerine dönüştürülür. Hesaba özel tam RSS adresi bu ayarın önüne geçer.
- Liste, katsayılar ve Nitter ayarı JSON dışa/içe aktarımına dahildir.

**Hesabı seçmek içerik bağlantısı kurmaz.** Kullanıcı çalışan Nitter sunucusu veya hesaba özel RSS adresi sağlamalıdır. Hiçbir ücretsiz sunucu çalışıyormuş gibi gösterilmedi; X API kimlik bilgisi olmadan gönderi çekildiği iddia edilmedi. X bağlantısız kalırken haber sitelerinin RSS akışı kullanılabilir. API ile uygulama içi genel kullanıcı araması/hesap doğrulaması bu sürümde yoktur. Kullanıcı adı biçimi doğrulanır; hesabın canlı varlığı doğrulanmaz.

## Doğrulama

- `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`: başarılı, lint 0 hata.
- **43 host testi**, 0 başarısız, 0 atlama. Gerçek GGUF ile native sınıflandırma ve üretim indirme koduyla gerçek internetten tam dosya indirmesi dahil.
- Test dağılımı: Domain 19, RSS 5, model aktarımı 7, canlı model aktarımı 1, native akış 1, Room/ayarlar 6, X hesapları 4.
- İndirme testleri: tam aktarım, geçerli Range ile devam, Range'i yok sayan sunucuda baştan yazma, SHA hatasında silme, iptalde kısmi dosyayı koruma, HTTPS yönlendirmesi/HTTP düşürme reddi, bozuk Content-Range ve kesilmiş yanıt.
- X testleri: handle/profil URL kimliği, kötü/geçersiz bağlantılar, hazır hesaplar, sunucu/RSS önceliği, harici arama URL'si, kalıcı silme/tekrarsız ekleme ve ayar aktarımı.
- **Android API 35 x86_64 emülatörü:** butonun çağırdığı aynı WorkManager işiyle gerçek HTTP Range devamı, tüm dosyanın SHA doğrulaması, gerçek GGUF yüklemesi ve seçimin DataStore'a kaydı geçti (`OK (1 test)`).

Emülatör kapsamı özellikle sınırlıdır: KVM olmayan yazılım emülasyonunda tam TLS indirmesi çok yavaştı; ilk çalıştırma sistem ANR ile kesildi. Tam dosya, üretim `ModelTransfer` koduyla bilgisayarda gerçekten indirildi ve doğrulandı. Android devam testinde dosyanın **son 64 KiB dışındaki kısmı** ADB ile test hazırlığı olarak aktarıldı (`396639936` bayt); son **65536 bayt gerçek Hugging Face/CDN üzerinden** alındı. Test HTTP durumunun 206 ve devam başlangıcının `396639936` olduğunu ayrıca doğrular. Sonra dosyanın tamamı hash kontrolünden ve native GGUF yüklemesinden geçti. Son APK ayrıca indirme kilidi içinden önbellekteki tam dosyanın SHA/native doğrulamasını ve model seçimini yeniden yaparak aynı cihaz testini geçti. Bu, bütün 397 MB'ın emülatör internetinden indirildiği iddiası değildir. Redmi üzerinde süre/RAM/pil ölçümü yapılmadı.

Gerçek X/Nitter gönderi alımı sınanmadı; kullanıcı bir erişim kaynağı sağlamadı. Arayüz bunu bağlantısız olarak gösterir. Genel X araması için oturum/erişim gerekebilir.

## Model kimliği

- Depo: [unsloth/Qwen3-0.6B-GGUF](https://huggingface.co/unsloth/Qwen3-0.6B-GGUF)
- Revizyon: `50968a4468ef4233ed78cd7c3de230dd1d61a56b`
- Dosya: `Qwen3-0.6B-Q4_K_M.gguf`
- Boyut: `396705472` bayt
- SHA-256: `ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a`
- Lisans: Apache 2.0. Model APK'ya gömülmedi.

## Çalıştırılan kontroller

```sh
GUNDEM_LIVE_MODEL_DOWNLOAD=1 \
GUNDEM_NATIVE_LIB_DIR=/path/to/host-jni \
GUNDEM_TEST_MODEL=/path/to/Qwen3-0.6B-Q4_K_M.gguf \
./gradlew :app:testDebugUnitTest :app:lintDebug

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

adb shell am instrument -w -r -e class tr.gundem.UpdateFlowTest \
  -e liveModelDownload 1 -e expectedResumeOffset 396639936 \
  tr.gundem.test/androidx.test.runner.AndroidJUnitRunner
```

Standart test çalıştırması 41 testi çalıştırır; model ve gerçek indirme ortam değişkenleri verilmezse isteğe bağlı iki host testi atlanır. `liveModelDownload` verilmezse pahalı Android indirme testi de atlanır. Kanıt günlükleri/XML dosyaları `update-0.2-tests/` altındadır. Önceki sürümün ayrıntılı temel doğrulaması `VALIDATION.md` içinde tarihsel kayıt olarak korunur.

## Kaynak başvuruları

- TKP resmî sitesinin X bağlantısı: [tkp.org.tr](https://www.tkp.org.tr/)
- PE resmî sitesinin X bağlantısı: [patronlarinensesindeyiz.org](https://patronlarinensesindeyiz.org/)
- Kemal Okuyan profil adresi: [x.com/OkuyanKemal](https://x.com/OkuyanKemal) (araçla doğrudan erişim 403 döndü; hazır liste hesabın içerik erişiminin kanıtı sayılmaz)
- Nitter RSS rota yapısı: [resmî kaynak kodu](https://github.com/zedeus/nitter/blob/master/src/routes/rss.nim)
- X uygulama içi kullanıcı araması erişimi: [User Search](https://docs.x.com/x-api/users/search/introduction)
