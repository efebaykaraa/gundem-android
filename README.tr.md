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

**Android 9+** gerekir. APK’yı aşağıdaki derleme adımlarıyla oluşturup kurun.

1. **Kaynaklar** ekranında kaynakları açın, ardından **Akış → Yenile** seçin.
2. **Model → Model seç → İndir ve kullan** ile model indirin veya **GGUF dosyası seç** ile uyumlu bir dosya yükleyin.
3. Analiz için **Başlat / devam** düğmesine basın. **Durdur** analizi duraklatır; **Hatalı / eksik analizleri yeniden dene** başarısız sonuçları yeniden işler.
4. Haberi açarak kaynakları ve puanını görün, analizini düzeltin veya ilgili başlıkları birleştirip ayırın.
5. **Ayarlar → Katsayılar** altında sıralamayı düzenleyip **Uygula** seçin. Tema, metin boyutu, ayar dışa aktarımı ve loglar da ayarlardadır.

Model olmadan haber okuyabilirsiniz. Besleme yenilemek, model indirmek ve orijinal haber sayfasını açmak internet gerektirir. Arka plan yenilemesi Android’in pil ve ağ kısıtlamalarına bağlıdır.

## Modeller

Modeller APK’dan ayrı indirilir.

| Model | İndirme | Kullanım |
| --- | ---: | --- |
| Gündem LFM3 Base · Q4_K_M | Yaklaşık 1,10 GB | Varsayılan; LFM3 Base üzerinden eğitilmiş |
| Gündem LFM3 · Q4_K_M | Yaklaşık 1,10 GB | Post-trained LFM3 üzerinden eğitilmiş |

En fazla 3 GiB boyutunda uyumlu GGUF dosyaları içe aktarılabilir. Uygulamanın oluşturacağı kopya için ek depolama gerekir; çalışabilecek modelleri cihazın RAM’i sınırlar. Uyumluluk, uygulamayla gelen llama.cpp sürümüne bağlıdır.

Katalog kendi fine-tuned modellerimizi içerir. İndirmeler sürüm, boyut ve SHA-256 ile sabittir. Başka uyumlu GGUF dosyalarını da içe aktarabilirsiniz. [Model kataloğu](app/src/main/assets/models.json).

[Fine-tuned LFM GGUF dosyalarını indir](https://github.com/efebaykaraa/gundem-llms/blob/master/docs/RELEASES.tr.md) ve model ekranından içe aktar.

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
