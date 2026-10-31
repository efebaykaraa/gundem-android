<div align="center">

<h1>Gündem</h1>

<p>Your feeds. Your priorities. AI on your device.</p>

<p>
  <a href="#get-started"><img src="https://img.shields.io/badge/Android-9%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android: 9+"></a>
  <a href="#build-and-install"><img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin: Compose"></a>
  <a href="#models"><img src="https://img.shields.io/badge/AI-On%20device-0D9488?style=for-the-badge" alt="AI: On device"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-64748B?style=for-the-badge" alt="License: MIT"></a>
</p>

<p>
  <a href="README.tr.md">Türkçe</a> &nbsp;·&nbsp; <a href="#get-started">Get started</a> &nbsp;·&nbsp; <a href="#models">Models</a> &nbsp;·&nbsp; <a href="https://github.com/efebaykaraa/gundem-llms/blob/master/README.md">Distillation Lab</a>
</p>

</div>

Read RSS/Atom feeds, save and share stories, adjust their ranking, and group related headlines manually. Classification runs on your device without an account or cloud AI service. Downloaded headlines and feed summaries can be read offline.

## Get started

Requires **Android 9+**. Build and install the APK using the instructions below.

1. Enable sources in **Kaynaklar**, then select **Akış → Yenile**.
2. Open **Model → Model seç → İndir ve kullan**, or import a compatible file with **GGUF dosyası seç**.
3. Press **Başlat / devam** to analyze headlines. **Durdur** pauses analysis; **Hatalı / eksik analizleri yeniden dene** retries unsuccessful items.
4. Open a story to see its sources and score, correct its analysis, or merge/split related headlines.
5. Adjust ranking in **Ayarlar → Katsayılar** and press **Uygula**. Theme, text size, settings export and logs are also available in settings.

The interface is Turkish. Reading works without a model. Fetching feeds, downloading models and opening original articles require internet access. Background refresh depends on Android's battery and network restrictions.

## Models

Models are downloaded separately from the APK.

| Model | Download | Use |
| --- | ---: | --- |
| Gündem LFM3 Base · Q4_K_M | About 1.10 GB | Default; fine-tuned from LFM3 Base |
| Gündem LFM3 · Q4_K_M | About 1.10 GB | Fine-tuned from post-trained LFM3 |

You can import compatible GGUF files up to 3 GiB. Imports need extra storage for the app's copy; available RAM limits which models can run. Model compatibility depends on the bundled llama.cpp runtime.

The catalog contains our fine-tuned models. Downloads are pinned by revision, size and SHA-256. You can also import other compatible GGUF files. [Model catalog](app/src/main/assets/models.json).

[Download fine-tuned LFM GGUFs](https://github.com/efebaykaraa/gundem-llms/blob/master/docs/RELEASES.md) and import them from the model screen.

## Build and install

Install a compatible JDK (Java language level 17), Android SDK 37, NDK `29.0.14206865` and CMake `3.22.1`. Fetch the pinned submodules before building.

```bash
git clone --recurse-submodules https://github.com/efebaykaraa/gundem-android.git
cd gundem-android
```

Already cloned? Run `git submodule update --init --recursive`.

Set the SDK path in `local.properties`:

```properties
sdk.dir=/absolute/path/to/android-sdk
```

From the project root:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For development checks:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

## Train and compare models

The desktop [Distillation Lab](https://github.com/efebaykaraa/gundem-llms/blob/master/README.md) trains headline classifiers, compares original and distilled models, and generates English/Turkish charts.

[Model results and Gündem Score](https://github.com/efebaykaraa/gundem-llms/blob/master/docs/multimodel-results/README.md) help compare task quality. These desktop measurements are separate from phone performance.

## License

[MIT](LICENSE). Third-party code, model weights and RSS content retain their respective licenses and rights.
