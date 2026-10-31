# Gündem 0.5.0

## Başlıktan analiz

Sınıflandırma ve alan tamirine yalnız haber başlığı gönderilir. Giriş/gövde, kaynak adı ve yayın tarihi gönderilmez. Gruplama girdisi de yalnız numaralandırılmış başlıklardır. Okuma ekranındaki tam haber metni korunur. Modelin sınıflandırma kurallarını anlatan sabit talimatları hâlâ girdi tokenlarına dahildir.

Önbellek kimliği artık başlık + model + `tr-4-title` talimat sürümüdür. Giriş değişikliği aynı başlığın analiz önbelleğini geçersiz kılmaz. Eski otomatik analizler yeni sürümle yenilenir; elle düzeltilen sonuçlar korunur. Hatalıları tekrar deneme de yeni önbellek kimliğini siler.

LFM kısa bayrak dizisi üretir. Qwen ve harici modeller JSON üretmeye devam eder; başlık girdisi için daha kısa İngilizce tanımlar kullanılır. Türkçe başlık ve depolanan etiketler değişmez. Qwen sistem öneki önbelleği korunur.

## Belleğe yükleme

Paylaşılan JSONL'de son kayıt dosyanın hazır olduğunu bildiriyor; bunu izleyen bellek yüklemesi veya hata kaydı bulunmuyor. Kayıtlı analizler eski `tr-2` talimatını kullanıyor. Bu dosya yeni takılmanın kesin nedenini kanıtlamıyor.

Kodda şu sorunlar düzeltildi:

- Model indirme/içe aktarma doğrulaması modeli yükleyip hemen kapatıyordu. Başlatınca tekrar yükleniyordu. Doğrulanmış model artık bellekte kalır; sonraki analiz aynı modeli kullanır.
- Yükleme başarısız olduğunda `finally` bloğu yine “Model bellekte hazır” yazıyordu. Artık gerçek yükleme hatası görünür ve native kaynaklar temizlenir.
- Kuyruk seviyesindeki yükleme hatası bütün bekleyen haberleri hatalıya çevirmiyor. Kuyruk duraklatılır, bekleyenler korunur; Devam et ile yeniden yükleme denenebilir.
- 4.096 token bağlam 2.048'e indirildi. Başlık girdisi için yeterlidir ve bağlam önbelleği ihtiyacını azaltır. Batch 256, CPU parçacığı sayısı 2 ve ARM KleidiAI derlemesi korunur.
- Model dosyası açma, ağırlık yükleme yüzdesi ve bağlam hazırlama aşamaları gösterilir. Aşamalar ve aynı aşamada bekleniyorsa her 10 saniyede geçen süre loglanır. Yükleme başlangıcına dosya boyutu ve boş RAM bilgisi eklenir.
- Üç dakikalık kooperatif yükleme iptali eklendi: native yükleme callback'leri ve bağlamın iptal callback'i durdurmayı uygular. İşletim sisteminin bloklanan bir dosya okumasını veya süreç öldürmesini bu mekanizma kesin sürede kesme garantisi vermez.

Model mevcut oturumda 30 dakika boşta kalana kadar bellekte tutulur. Android'in kritik bellek baskısı veya süreç sonlandırması modeli bırakabilir. Gerçek ARM telefondaki son takılma bu ortamda tekrar üretilemedi; yalnız host model yüklemesiyle telefon sorununun tamamen çözüldüğü iddia edilmez.

## Arayüz

Kurulum tamamlanınca indirme kartı gizlenir. Geçerli model ve bellek durumu kalır; model seçici “Model değiştir” ile açılır. Kullanılan model için yeniden indirme düğmesi etkin değildir. Başka modele geçiş ve aktif indirme ilerlemesi gerektiğinde gösterilir.

Akış başlığı artık görünür/gizli eşiğinde birden açılıp kapanan sticky liste öğesi değildir. Kaydırma mesafesini izleyen, kırpılmış ayrı bir başlık alanıdır. Başlığın hareket ettiği piksel miktarı kaydırmadan tüketilir; kalan miktar listeye gider. Ters yönde sürükleme başlığı geri getirir. Kısmi hareket sonunda 220 ms geçişle en yakın duruma yerleşir.

## Doğrulama

Gerçek GGUF ile host JNI testleri: geçersiz yükleme sonrası hata durumu, ardından geçerli kurulum, kurulumun bellekte tutulması, aynı modelin yeniden kullanılması, yalnız başlıktan sınıflandırma, Room/önbellek/sıralama ve altılı gruplama doğrulanır. Standart testler başlık dışındaki metnin gönderilmediğini ve başlık hareketindeki kaydırma tüketimini de denetler.

İlk tam gerçek LFM koşusu: 57 test, 56 başarılı; ayrı canlı indirme testi atlandı. Qwen son entegrasyon koşusu: 4 test başarılı. APK ve lint son teslim kontrolü ayrıca çalıştırıldı.

Aynı RSS örneğinde eski yük 877 girdi/59 çıktı tokenı, yeni yalnız başlık yükü 542 girdi/29 çıktı tokenı üretti. Host üzerinde yaklaşık 10,2 saniyeden 5,8 saniyeye düştü. Önceki 0.4'ün kısaltılmış başlık+giriş örneği 572 token idi; yalnız başlığa geçişin bu kısa RSS örneğindeki ek kazancı sınırlıdır. Bunlar telefon hız ölçümü değildir.

Bu testler kapsamlı anlamsal doğruluk ölçümü değildir. Özellikle küçük Qwen modelinde ilgisiz ek etiketler ve yanlış tür/puanlar gözlendi. Başlıkta bulunmayan bilgi model tarafından güvenilir biçimde çıkarılamaz; elle düzeltme korunur.

Android APK iki ABI için derlendi. Yazılım emülasyonunda Gündem ekranı açıldı; emülatörün System UI ANR'ı nedeniyle animasyon akıcılığı cihaz üzerinde güvenilir biçimde doğrulanamadı. Fiziksel ARM cihaz bu oturuma bağlı değil.

Sürüm kodu 5; uygulama kimliği ve debug imzası korunur. Önceki APK üzerine güncelleme yapılabilir; mevcut model dosyası yeniden indirilmez.
