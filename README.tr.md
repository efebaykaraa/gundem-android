<div align="center">

<h1>Gündem</h1>

<p>Haberlerini topla. Önceliklerini belirle. Cihazında analiz et.</p>

<p>
  <a href="#kurulum-ve-kullanım"><img src="https://img.shields.io/badge/Android-9%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android: 9+"></a>
  <a href="#derleme-ve-yükleme"><img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin: Compose"></a>
  <a href="#modeller"><img src="https://img.shields.io/badge/AI-On%20device-0D9488?style=for-the-badge" alt="AI: On device"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-64748B?style=for-the-badge" alt="License: MIT"></a>
</p>

<p>
  <a href="README.md">English</a> &nbsp;·&nbsp; <a href="#kurulum-ve-kullanım">Başlangıç</a> &nbsp;·&nbsp; <a href="#modeller">Modeller</a> &nbsp;·&nbsp; <a href="https://github.com/efebaykaraa/gundem-llms/blob/master/README.tr.md">Distillation Lab</a>
</p>

</div>

RSS/Atom kaynaklarını okuyun, haberleri kaydedip paylaşın, sıralamayı tercihlerinize göre ayarlayın ve ilgili başlıkları elle gruplayın. Analiz cihazınızda çalışır; hesap veya bulut AI hizmeti gerekmez. İndirilen başlıklar ve besleme özetleri çevrimdışı okunabilir.

## Kurulum ve kullanım

**Android 9+** gerekir. [v0.9.1 APK’sını indirin](https://github.com/efebaykaraa/gundem-android/releases/tag/v0.9.1) veya aşağıdaki adımlarla derleyin. İndirilen APK debug imzalı bir ön sürümdür.

1. **Kaynaklar** ekranında kaynakları açın, ardından **Akış → Yenile** seçin.
2. **Model → Model seç → İndir ve kullan** ile model indirin veya **Model dosyası seç** ile uyumlu bir dosya yükleyin.
3. Analiz için **Başlat / devam** düğmesine basın. **Durdur** analizi duraklatır; **Hatalı / eksik analizleri yeniden dene** başarısız sonuçları yeniden işler.
4. Haberi açarak kaynakları ve puanını görün, analizini düzeltin veya ilgili başlıkları birleştirip ayırın.
5. **Ayarlar → Katsayılar** altında sıralamayı düzenleyip **Uygula** seçin. Tema, metin boyutu, ayar dışa aktarımı ve loglar da ayarlardadır.

Model olmadan haber okuyabilirsiniz. Besleme yenilemek, model indirmek ve orijinal haber sayfasını açmak internet gerektirir. Arka plan yenilemesi Android’in pil ve ağ kısıtlamalarına bağlıdır.

## Modeller

Model seçicisinde **10 model ailesinin orijinal ve fine-tuned sürümleri olmak üzere 20 indirme seçeneği** bulunur. Her model kartında ve indirme kartında **100 üzerinden Gündem Score**, sürüm, boyut ve biçim gösterilir. Varsayılan seçim fine-tuned LFM3 Base’dir.

LFM3 Base, LFM3, Octen, Spark, Llama 3.2 (1B ve 3B), Limite, Qwen 3.5 (0.8B ve 2B) ve GPT-X3 arasından seçim yapabilirsiniz. 18 seçenek Q4_K_M GGUF, GPT-X3’ün iki sürümü ise bağımsız ONNX INT8 paketidir. Uygulama çalıştırma motorunu otomatik seçer; modeller APK’dan ayrı indirilir.

[Puanları karşılaştırın ve dosyaları indirin](https://github.com/efebaykaraa/gundem-llms/blob/master/docs/MOBILE.tr.md). Puanlar, eğitimde kullanılmayan 101 başlıkta HF/BF16 ile ölçülen sınıflandırma sonuçlarıdır. Telefon hızını veya quantize edilmiş dosyaların yeniden ölçülmüş kalitesini göstermez. 0,0 puan da ölçülmüş bir sonuçtur.

İndirmeler sabit sürüm, boyut ve SHA-256 ile doğrulanır. En fazla 3 GiB boyutunda uyumlu GGUF dosyaları veya GPT-X3 ONNX ZIP paketleri içe aktarılabilir. Kopyalama ve ONNX paketini açma ek depolama gerektirir; çalışabilecek modelleri cihazın RAM’i sınırlar. [Uygulama kataloğu](app/src/main/assets/models.json).

## Derleme ve yükleme

Uyumlu JDK (Java dil seviyesi 17), Android SDK 37, NDK `29.0.14206865` ve CMake `3.22.1` kurun. Derlemeden önce sabitlenmiş submodule’leri indirin.

```bash
git clone --recurse-submodules https://github.com/efebaykaraa/gundem-android.git
cd gundem-android
```

Zaten klonladıysanız `git submodule update --init --recursive` çalıştırın.

`local.properties` dosyasına SDK yolunu yazın:

```properties
sdk.dir=/tam/yol/android-sdk
```

Proje kökünde:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Geliştirme kontrolleri için:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

## Model eğitme ve kıyaslama

Masaüstü [Distillation Lab](https://github.com/efebaykaraa/gundem-llms/blob/master/README.tr.md), haber sınıflandırma modellerini eğitir, orijinal ve distilled sürümleri kıyaslar ve Türkçe/İngilizce grafik üretir.

[Model sonuçları ve Gündem Score](https://github.com/efebaykaraa/gundem-llms/blob/master/docs/multimodel-results/README.tr.md) görev kalitesini karşılaştırmak için kullanılabilir. Bu masaüstü ölçümleri telefon performansından ayrıdır.

## Lisans

[MIT](LICENSE). Üçüncü taraf kodlar, model ağırlıkları ve RSS içerikleri kendi lisans ve haklarına tabidir.
