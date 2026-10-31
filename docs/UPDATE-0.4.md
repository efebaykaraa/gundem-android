# Gündem 0.4 — telefon loguna göre gecikme düzeltmesi

Önceki APK üzerine kurulabilir; uygulama kimliği ve debug imzası aynıdır. İndirilen model dosyaları, haberler ve elle düzeltmeler korunur. Modelin yeniden indirilmesi gerekmez. Otomatik analiz sürümü `tr-3` oldu; eski otomatik sonuçlar yeni ayarlarla güncellenmek üzere kuyruğa girer.

## Telefon logunda bulunanlar

Kullanıcının sağladığı JSONL logundan dokuz tamamlanmış model çağrısı ayrıştırıldı. Ham kullanıcı logu kaynak arşivine kopyalanmadı; yalnız toplu sayısal özet `update-0.4-tests/device-log-summary.json` içinde.

| İşlem | Gerçek telefon logu |
|---|---:|
| Model yükleme | 568 ms, bir kez |
| Ortalama girdi | 1.407 token |
| Ortalama girdi işleme | 165.024 ms |
| Ortalama çıktı | 73 token |
| Ortalama çıktı üretme | 19.077 ms |
| Girdinin çağrı süresindeki payı | %89,6 |

Model RAM'de kalıyordu; tekrar yükleme bu gecikmeyi açıklamıyor. Bu ölçümlerde alan tamiri yok. RSS/X erişim hataları ayrı işlemler; model çağrılarındaki yaklaşık üç dakikalık CPU süresinin sebebi değiller.

İki ayrı “Kullanıcı devam ettirdi” kaydının hemen ardından devam eden native çağrı iptal edilmiş. O sırada hesaplanmış 59,4 ve 78,3 saniye kaybedilmiş. Sebep: Devam et işinin `ExistingWorkPolicy.REPLACE` ile mevcut kuyruğu değiştirmesiydi.

## Yapılan düzeltmeler

- Model sayfasında tek kontrol var: analiz etkin olduğunda **Durdur**, duraklatılmışken **Devam et**. Hatalıları tekrar deneme çalışırken devre dışı.
- Hem ekran hem bildirim aynı devam ettirme fonksiyonunu kullanır. Mutex, aktif iş kontrolü ve `KEEP` ile yinelenen istek mevcut işi iptal etmez.
- **İşlenen** sayacı kaldırıldı. Devam eden haber bekleyen sayısına dahildir. Eski model/talimat sürümüne ait sonuçlar güncel tamamlanan sayısına katılmaz; gizli haberler kuyruk sayacından çıkarılır. İçteki geçici `CALISIYOR` durumu yarım iş kurtarma için korunur.
- ARM64 derlemesinde **KleidiAI v1.32.0** açıldı. Eski derlemede kapalıydı. Çalışma anında uygun FP16/dotprod/i8mm çekirdekleri seçilir; global işlemci tabanı yükseltilmez, desteklenmeyen donanımda uyumlu yol kullanılır. Q4_K ve diğer desteklenen matrislerde hızlandırma uygulanır; bütün işlemler için sabit hız çarpanı vaat edilmez.
- Prefill batch/ubatch 128 → 256. CPU iş parçacığı sayısı 2, bağlam 4.096 olarak korundu.
- LFM3'ün GGUF sohbet şablonundaki BOS ve `enable_thinking=false` son eki uygulandı. Genel ChatML biçimleyici bunları tek başına üretmiyordu. LFM3 için önek durumu geri yükleme hâlâ kapalı; model ağırlıkları ve bağlam RAM'de tutulur.
- Modele gönderilen başlık en fazla 240, giriş en fazla **900 karakter**. Giriş boşlukları sadeleştirilir ve mümkünse tam cümle sonunda kesilir. Okunan/kaydedilen haber metni kısaltılmaz. Gerçek ve gönderilen metin uzunluğu loglanır.
- LFM sabit talimatı daha az token kullanan İngilizce tanımlarla kısaltıldı; Türkçe haber ve Türkçe etiketler korunur. LFM yedi bağımsız konu bayrağı, tür ve üç puanı kısa bir dizide üretir. Sonuç normal `Analysis` JSON şemasına çevrilir. Qwen'de kısa sayı dizisi etiket kaçırdığı için mevcut JSON biçimi ve Türkçe talimat korunur; sistem öneki RAM önbelleği kullanılır.
- Native loglara `batch`, `threads`, `arm_kleidiai`, gerçek donanımdan `cpu_dotprod` ve `cpu_fp16` eklendi. Kuyruk başlangıcında uygulama sürümü, ABI, cihaz modeli, işlemci sayısı ve Android termal durumu kaydedilir. Dönüştürülmüş okunabilir analiz JSON'u ayrıca loglanır.

## Karşılaştırma ve testler

Aynı soL RSS örneği, aynı LFM GGUF, aynı host CPU, aynı yeni native motor üzerinde eski ve yeni yük ile karşılaştırıldı. Bu deney ARM/KleidiAI hızlandırmasını içermez; telefon süresi değildir:

| | Eski yük | Yeni LFM yükü |
|---|---:|---:|
| Girdi | 877 token | 572 token |
| Çıktı | 59 token | 29 token |
| Girdi süresi | 7.022 ms | 4.569 ms |
| Çıktı süresi | 3.012 ms | 1.468 ms |
| Toplam | ≈10,0 s | ≈6,0 s |

Bu örnekte toplam süre yaklaşık %40 azaldı. Telefonun yeni sürümdeki hızı henüz ölçülmedi; ARM optimizasyonunun kazancı bu tablodan çıkarılamaz. Üç kısa haber testinde LFM çıktısı 25–29 token oldu. Metin uzunluğu ve içerik bu sayıları değiştirir.

- Son standart derleme, Android test APK derlemesi ve lint başarılı; standart test koşusunda **55 testten 51 başarılı, gerçek model/ağ ortamı isteyen 4 test atlandı**.
- Gerçek LFM host JNI ile tam koşu: **55 test, 54 başarılı, ayrı canlı model indirme testi atlandı**. RSS → Room → gerçek sınıflandırma → cache → sıralama ve altı başlıkla gruplama dahil.
- Gerçek Qwen host JNI: üç entegrasyon testi başarılı. Hem kısa ve uzun örnek hem cache/Room/gruplama yolu doğrulandı.
- Android emülatöründe `AnalysisControlTest`: **OK (1 test)**. On eşzamanlı devam ettirme isteği karşısında bekletilen mevcut işin UUID'si ve ENQUEUED durumu korundu; ikinci aktif iş oluşmadı.
- Girdi cümle sınırı, kısa çıktı doğrulama, geçersiz/yarım çıktı reddi ve güncel kuyruk sayımı testleri eklendi.
- Bunlar kapsamlı doğruluk ölçümü değildir. Ek etiket, tür ve puan hataları örneklerde hâlâ görüldü; teknik olarak tamamlanmış çıktı anlamsal doğruluk garantisi vermez. Elle düzeltme korunur. Bu sürümün amacı süreyi azaltmak ve gereksiz iptalleri önlemektir.

## Kaynaklar ve tekrar derleme

[Resmî Android entegrasyonu](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md) ve sabitlenmiş llama.cpp içindeki Android örneği ARM için KleidiAI kullanımını gösterir. Derleme ayarı uygulamanın `CMakeLists.txt` dosyasındadır; llama.cpp kaynakları değiştirilmedi.

KleidiAI kaynakları `vendor/kleidiai` içinde paketlenir; yapılandırma sırasında ayrıca indirilmez. [Resmî v1.32.0 sürümü](https://github.com/ARM-software/kleidiai/releases/tag/v1.32.0), arşiv SHA-256: `a48d373e7c5d721a0aecbf5ddaf0bdf245bc8064e6b84086f13db0e102b553a6`, upstream CMake'in beklediği MD5: `05f1953428ffe8e8c7f585dc6d508887`. Lisans dosyaları paketle birlikte korunur.

[Resmî LFM3 GGUF deposu](https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF) model şablonunu ve çalıştırma gereksinimlerini sağlar. Ağırlık URL'si/SHA önceki sürümle aynı; güncelleme yeni model indirmez.
