# Gündem 0.3.0

Bu sürüm 0.2 üzerine aynı uygulama kimliği ve debug imzasıyla kurulabilir. Room şeması değişmedi; haberler, elle düzeltmeler ve kaynak tercihleri korunur. Talimat sürümü `tr-2` olduğu için eski otomatik analizler kuyruk yeniden başlatıldığında güncellenir. Mevcut seçili model dosyası kullanıcı değiştirmeden silinmez veya değiştirilmez.

## Model ve gecikme incelemesi

Varsayılan katalog sırası LFM3-1.7B Q4_K_M, ardından Qwen3-0.6B Q4_K_M. Model ekranındaki seçim penceresiyle seçilip tek tuşla indirilir. Katalog `app/src/main/assets/models.json` dosyasındadır; kaynak kodda değiştirip yeniden derleyerek seçenekler eklenebilir. Her kayıtta sabit commit URL'si, dosya boyutu ve SHA-256 vardır. Model ağırlıkları APK ve kaynak arşivine eklenmez.

LFM3: [resmî GGUF deposu](https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF), commit `bad5729bba61d076fff20d2548e62526665af215`, 1.099.627.808 bayt, SHA-256 `3793e1e6e2266919d6e015287221b41100175a98eddaba92e111aed3b05c93c4`. Lisansı LFM1.0'dır. Uygulamanın sabitlenmiş llama.cpp sürümü `lfm3` mimarisini destekler. Gerçek dosya ile host JNI üzerinden sınıflandırma, kısıtlı JSON, Room önbelleği, sıralama ve altı başlıkla gruplama doğrulandı.

Önceki kodda bulunan gecikme nedenleri:

- Model 45 saniye boşta kaldığında veya arayüz gizlendiğinde kapatılıyordu. Artık arka plana geçmek modeli bırakmaz; 30 dakika boşta kalma veya bellek baskısında bırakılır. Android süreç sonlandırırsa yeniden yüklenmesi gerekir.
- Her altı haberden sonra worker yeniden deneme/backoff bekliyordu. Haber kuyruğu artık tek ön plan worker'ında kesintisiz işlenir.
- Ortak sınıflandırma talimatı yaklaşık 1.300 token idi ve her çağrıda tekrar işleniyordu. Kısa talimat yaklaşık 760 ortak token kullanır. Girdi, model ve tokenizer'a göre değişir.
- Gruplama her aday çifti için ayrı çağrı yapıyordu. Artık aynı çağrıda en fazla altı başlık işlenir. Parçalar arasındaki henüz karşılaştırılmamış adaylar sonraki çağrılara taşınır; eşleşmeler körlemesine zincirlenmez. Çok haber varsa gruplama yine zaman alabilir.
- Qwen3 için sistem önekinin tam bağlam durumu RAM'de saklanıp tekrar yüklenir. Aynı ve farklı haberlerle soğuk/sıcak çıktı eşitliği doğrulandı. Host testinde girdi işleme yaklaşık 2,7 saniyeden 0,3 saniyeye indi; yanıt üretimi ayrıca yaklaşık 2,2–2,5 saniye sürdü. Bunlar Redmi ölçümü değildir.
- LFM3'te aynı önek geri yükleme yöntemi bu runtime ile soğuk çağrıyla eşit sonuç vermedi. Bu nedenle LFM3 için kapalıdır; LFM modeli ve bağlamı RAM'de kalır, haberler arasında tekrarlayan durum temizlenir. Önek önbelleğini sırf hız için açıp farklı sonuçları gizlemiyoruz.

Model bilgi penceresindeki RAM aralıkları tahmindir; telefon ölçümü değildir. Her iki model 2 CPU iş parçacığı ve 4.096 token bağlam kullanır. Daha büyük modelin her cihazda daha hızlı olacağı vaat edilmez.

## Arayüz

Akış başlığı ve filtreler aşağı kaydırırken kapanır, ters yönde kaydırınca geri gelir. Haber uygulama içinde tam ekran açılır; alt sekme çubuğu görünmez. Geri dönünce filtre ve kaydırma konumu korunur. Yeni alınan girişlerde paragraflar, satır sonları ve listeler korunur. Metin görünümünde Markdown başlıkları, kalın/italik vurgu ve satır sonları işlenir. Önceden düzleştirilmiş metinler kaynaklar yenilendiğinde güncellenir; kaynaktan gelmeyen paragraf veya tam haber metni uydurulmaz.

Kaynak satırları ad, bağlantı ve durum noktası gösterir. Yeşil son denemenin başarılı, kırmızı hatalı, gri henüz denenmemiş veya devre dışı olduğunu belirtir. Noktaya dokununca açıklama, kaynağa dokununca düzenleme açılır. Ayrıntılı hata geçmişi Loglar'dadır.

Ayarlar içinde Katsayılar, Görünüm ve Loglar bulunur. Tema Görünüm'e taşındı; haber metni boyutu eklendi. Kuyruk sayıları renkli dairelerdir; dokunma/uzun basma açıklamayı açar ve ekran okuyucular etiketleri okuyabilir.

## Arka plan ve loglar

Yerel analiz WorkManager ön plan hizmetiyle çalışır; ilerleme, durdurma ve duraklatılmış bildirimde devam et eylemi vardır. Uygulama içinde de ilerleme şeridi gösterilir. Model indirmesi ayrı ilerleme bildirimiyle devam eder ve iptal edilen `.part` dosyasından yeniden başlatılabilir. Bildirimler Android 13+ üzerinde sistem iznine bağlıdır. Android pil, süreç ve iş kotası sınırları geçerlidir; [Android'in uzun worker belgesi](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running).

Ayarlar → Loglar: AI, MODEL, KAYNAK ve X filtreleri; JSONL dışa aktarımı; Android dosya paylaşımı. Kayıtlar yeniden başlatmada korunur. Güncel dosya yaklaşık 2 MB olduğunda önceki dosyaya döner; yaklaşık son 4 MB korunur. Ekranda en yeni 500 kayıt görünür; dışa aktarım saklanan bütün kayıtları içerir.

AI kayıtları model yükleme, RAM'deki model kullanımı, haber kimliği, çağrı girdi/çıktı token sayısı ve süreleri, ortak önek sayısı, sonuç JSON'u, alan tekrarları, önbellek isabetleri ve gruplama planını içerir. Model indirme kayıtları başlangıç, HTTP/Range devamı, tamamlanma, iptal ve hatayı içerir. Kaynak kayıtları istek, alınan haber sayısı, süre ve hatayı içerir. Tam haber gövdesi, imzalı indirme bağlantısı veya kimlik bilgileri loglanmaz.

## X: doğrulanan davranış ve açık sınır

30 Ekim 2026'da `https://nitter.mizuki.st` üzerinden TKP (`tkpninsesi`, 20 gönderi), Kemal Okuyan (`OkuyanKemal`, 20 gönderi) ve PE (`pensendeyiz`, 19 gönderi) RSS yanıtları HTTP 200 ve gerçek içerikle doğrulandı. Varsayılan hesaplarda boş içerik bağlantısı artık bu sunucuya çözülür; hazır hesaplar korunur. Sunucu bağımsız üçüncü taraftır, sürekli erişilebilirliği garanti edilemez. Kaynak düzenlemesindeki özel RSS, varsayılanın önüne geçer. İçe aktarılan ayarlarda `nitterBase` ile sunucu değiştirilebilir.

Arama uygulama içinde yapılır ve tarayıcıya yönlendirme kaldırıldı. Sonuçlar fotoğraf, ad ve @kullanıcı adıyla gösterilir. Genel kullanıcı araması destekleyen sunucularda arama sonuçları ayrıştırılır. Ancak test edilen ücretsiz servis genel isim aramalarına boş liste döndürdü. Tam kullanıcı adıyla gerçek profil doğrulanabilir; hazır hesapların adlarıyla da profil bulunabilir. Rastgele adla bütün X kullanıcılarını güvenilir biçimde arama tamamlanmış değildir. `netbub` 403, `mie.foundation` 429 döndürdü; diğer denemeler de kullanılabilir genel arama vermedi. Bu sınıra rağmen sahte hesap sonuçları üretilmez. Genel aramanın güvenilir olması için çalışan arama sunucusu veya yetkili X API bağlantısı gerekir.

## Doğrulama

- `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`, `lintDebug`: başarılı. Standart host koşusunda 48 test, 46 başarılı ve ortam gerektiren 2 atlandı; hata yok. Lint hata vermedi; bağımlılık/güncellik ve stil önerileri mevcut.
- Gerçek LFM GGUF ve host JNI ile ek koşu: 47 test, 46 başarılı, yalnız ayrı canlı model indirme testi atlandı. Bu koşu kaynak örneği → Room → gerçek çıkarım → cache → puanlama ve altı başlıkla gruplamayı içerir. Dosyanın SHA-256'sı katalogla eşleşir.
- Qwen önek durumu eşitliği: aynı girdi ve farklı sonraki girdiyle sıcak/soğuk sonuçlar birebir eşit. Kanıt `docs/update-0.3-tests/qwen-prefix-equality.log` içindedir.
- Paragraf/satır sonları, zararlı HTML kaçışlama, Markdown vurgu, kalıcı log yeniden açma, katalog varsayılanı, görünüm ayarı uyumluluğu ve uygulama içi gerçek profil ayrıştırma testleri eklendi.
- Güncellenen APK emülatöre veriler korunarak kuruldu. Tam ekran gerçek haber görünümü incelendi; alt uygulama sekmeleri görünmüyor. Cihazdaki bildirim nesnesi/eylemi ve kalıcı log kontrolü başarılı.
- Emülatör donanım hızlandırması olmadan çalışıyor. System UI, Pixel Launcher ve test başlangıcında sistem zaman aşımı/ANR yaşandı. Bunlar telefon performansı ölçümü olarak kullanılmadı. Gerçek Redmi cihazı bağlı değil; telefon üstünde süre, RAM, pil ve uzun arka plan çalışma testi yapılmadı.

- Android bildirim nesnesi/eylemi ve kalıcı log testi tek başına tekrar çalıştırıldı: **OK (1 test)**, 0,621 saniye. Canlı kaynakların emülatör ağında alınması tamamlanmadı; sınırlı süre sonunda test durduruldu, geçer sayılmadı. Kaynakların çalıştığına dair yukarıdaki sonuçlar host üzerindeki gerçek HTTP denemeleridir.
