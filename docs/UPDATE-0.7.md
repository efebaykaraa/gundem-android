# Gündem 0.7.0 — Otomatik haber karşılaştırması kaldırıldı

Kullanıcının isteğiyle “aynı haber / aynı olay mı?” kontrolü kaldırıldı. Worker başlık analizlerinden sonra tamamlanır. Aday çift taraması, toplu grup karşılaştırması, gruplama ilerleme aşaması ve modelin grup giriş noktaları kaldırıldı. Grup prompt/grammar dosyaları APK'dan çıkarıldı. Analiz bekleyen haber yoksa model belleğe yüklenmeden iş tamamlanır.

Akış eski otomatik grup kararlarını kullanmaz. Elle birleştirme ve ayırma korunur; bunlar yapay zekâ çağırmaz. Eski veriler silinmez; otomatik kararlar yalnızca yok sayılır. Aynı başlığa sahip iki ayrı kayıt kendiliğinden birleştirilmez. Kaynağın aynı kaydının yeniden indirilmesinde kullanılan normal veritabanı kimlik/tekrar kontrolü korunur; bu kontrol model çağırmaz.

## Paylaşılan logun bulgusu

`gundem-loglar (1).jsonl` dosyasının son 0.6 kuyruğunda tamamlanan 19 çağrı bulunuyor. Bu bölümde gruplama planı veya grup karşılaştırması kaydı yok. Çağrılar yalnız başlık sınıflandırmasıdır:

- Ortalama toplam girdi: 535,9 token. Buna 1.714 karakterlik sabit model talimatları ve sohbet şablonu da dahildir.
- Ortalama girdiyi işleme süresi: 63,49 saniye.
- Ortalama çıktı üretme süresi: 6,48 saniye.
- Toplamın yaklaşık %91'i girdiyi işlemeye gidiyor. Model yükleme veya gruplama, bu çağrılardaki beklemenin sebebi değil.

Dolayısıyla otomatik gruplamayı kaldırmak ileride yapılacak ek çağrıları önler; bu logdaki yaklaşık 70 saniyelik başlık analizini tek başına ortadan kaldırmaz. Bu sürümde sınıflandırma talimatı ve model değiştirilmedi. Geçerli önbellek sonuçları korunur. Temel ARM64 CPU uyumluluk yolu sürdürülür.

## Doğrulama

Birim testleri eski otomatik eşleştirmelerin ve aynı başlığın haberleri birleştirmediğini; kullanıcının elle birleştirme/ayırma kararlarının korunduğunu denetler. Gerçek LFM JNI entegrasyonu haber → sınıflandırma → Room/önbellek/sıralama yolunu çalıştırır. Gerçek LFM koşusunda 59 testten 58 test başarılı; ayrı canlı indirme testi atlandı. Testlerden kaldırılan otomatik gruplama çağrıları çıkarıldı. APK, Android test APK'sı ve lint kontrolü çalıştırıldı.

Fiziksel telefonda bu sürümün süresi henüz ölçülmedi. Önceki sürüm üzerine güncellenebilir; model tekrar indirilmez. Uygulama kimliği ve debug imzası aynı; sürüm kodu 7.
