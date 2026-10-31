# Gündem 0.9.0

## Yerel güncellik kesintisi

Mevcut güncellik çarpanına ek olarak, yayından itibaren tamamlanan her 24 saat için varsayılan **0,5 puan** düşer. Ayarlar → Katsayılar → “Her 24 saatte düşülecek puan” ile 0–5 arasında değiştirilebilir; 0 kesintiyi kapatır.

Sonuç = max(0, önceki ağırlıklı/güncellikli puan − tamamlanan gün × günlük kesinti).

Sonuç sıfırın altına inmez. Gelecek tarihli haberin yaşı 0'dır. Tarihi bilinmeyene yaş uydurulmaz; günlük kesinti yapılmaz, önceki taban güncellik kuralı korunur. Eksik analiz için de yerel kesinti uygulanır. Puan dökümünde yaş ve düşülen puan gösterilir.

Güncellik, tarih veya kesinti modele gönderilmez. Haber başlığına dayalı AI girdisi ve model önbellek anahtarı bu ayardan etkilenmez; mevcut analizler sırf bu değişiklik için tekrar çalıştırılmaz. Eski ayar JSON'larında yeni alan yoksa 0,5 varsayılır. Yeni değer ayar dışa/içe aktarımında korunur.

## Analiz kuyruğu

Model en yeni yayın tarihinden başlayarak çalışır. Tarih eşitse indirme zamanı, sonra sabit kimlik sıralanır; tarihi bilinmeyenler sona alınır. Her sonuçtan sonra kuyruk yeniden okunur: işlem sürerken gelen yeni haber bir sonraki seçimde öne geçer. Devam eden model çağrısı sırf yeni haber geldi diye iptal edilmez. Otomatik “aynı haber mi?” karşılaştırması kapalıdır.

## Qwen3.8-27B → LFM damıtma kodu

`tools/distill/` bağımsız bir komut satırı hattıdır:

1. RSS'ten gerçek başlık toplama;
2. Qwen/Qwen3.8-27B OpenAI uyumlu API ile beş alanlı JSON etiketleme;
3. doğrulama, devam ettirme, isteğe bağlı insan incelemesi ve eğitim/doğrulama/test ayrımı;
4. LiquidAI/LFM3-1.7B için assistant-only LoRA SFT;
5. adapter birleştirme, F16 → Q4_K_M GGUF dışa aktarma;
6. ayrılmış test örneklerinde öğretmenle uyum değerlendirmesi.

Öğretmen uzun görev tanımını kullanabilir; hem öğretmen hem öğrenci haber verisi olarak yalnız başlığı alır. Tarih/güncellik kaynak verisinden modele taşınmaz. Öğrenci kısa yönlendirmesi APK'daki LFM yönlendirmesiyle aynıdır. Eğitim kütüphaneleri resmî LFM3 notebook'undaki sürümlere sabitlendi. Eğitim, telefondaki GGUF üzerinde değil HF temel modelinde yapılır.

80 gerçek etiketsiz başlık ayrıca verildi: soL 10, TKP 30, Patronların Ensesindeyiz 40. Bunlar APK'nın örnek/veri başlangıcı değildir; yalnız ayrı eğitim aracı paketindedir.

## Doğrulama ve sınırlar

Android derlemesi, test APK'sı ve lint başarılı. 64 birim testten 59 başarılı; ortam isteyen 5 test atlandı. Günlük kesinti sınırları, sıfır tabanı, tarih yokluğu/gelecek tarih, ayar kalıcılığı/eski ayar uyumu, en yeni önceliği ve tarih/ayarın AI girdisine girmemesi test edildi.

10 veri hattı testi başarılı. Veri hattı testleri gerçek yerel HTTP test sunucusuyla JSON doğrulama, yalnız başlık gönderme, kesilmiş yanıt reddi, devam ettirme, dosya/öğretmen sürümü ayrımı, veri sızıntısı kontrolü, assistant-only maskesi, değerlendirme hataları ve GGUF komut/hash kontrolünü çalıştırır. Küçük sentetik CPU modeli üzerinde gerçek iki adımlı LoRA eğitimi ve birleştirme yapıldı; yedi model tensörünün değiştiği doğrulandı.

Bu oturumda gerçek Qwen3.8-27B öğretmen çağrısı, gerçek LFM3 eğitimi veya eğitilmiş LFM'nin GGUF dönüşümü yapılmadı. Bu teslim bir eğitim hattı ve örnek başlık verisidir; eğitilmiş model değildir. Gerçek kullanım için öğretmen API adresi ve uygun eğitim ortamı ayarlanmalıdır. Ayrıntılı komutlar `tools/distill/README.md` içindedir.

Sürüm kodu 9; uygulama kimliği ve debug imzası korunur. Önceki sürüm üzerine kurulabilir; mevcut model dosyası yeniden indirilmez.
