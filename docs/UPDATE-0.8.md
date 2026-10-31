# Gündem 0.8.0 — Kısa yönlendirme, doğrudan başlık

Uzun sınıflandırma talimatı ve sayısal çıktı örneği çıkarıldı. Modele haber verisi olarak artık JSON sarmalı veya “Haber:” öneki olmadan ham başlık gönderilir. Giriş/gövde, tarih ve kaynak gönderilmez. Kısa yönlendirme yalnız alanları ve konu karşılıklarını belirtir; eski ayrıntılı puanlama yönergeleri bulunmaz.

- LFM: önceki 1.714 karakterlik gerçek sistem girdisi yerine 294 karakterlik kısa yönlendirme.
- Qwen: önceki 1.558 karakterlik yönlendirme yerine 178 karakterlik yönlendirme.
- LFM'de kısa sayı dizisini örneksiz kullanmak tüm etiketleri sıfırladığı için beş alanlı JSON biçimine geçildi. Normal kayıt/dışa aktarma şeması aynı kaldı. Qwen de aynı JSON şemasını kullanır.
- Alan tamirinde de yalnız başlık ve kısa tek alan isteği kullanılır.
- Talimat sürümü `tr-5-minimal` oldu. Eski otomatik sonuçlar yeni sürümde yeniden değerlendirilebilir; elle düzeltilenler korunur.
- Model dosyası ve ARM CPU uyumluluk ayarları değişmedi. Yeni model indirmez. Otomatik “aynı haber mi?” kontrolü kapalı kalır.

## Gerçek model ölçümü

Aynı RSS haberinin aynı başlığı, aynı host motoru ve aynı modelle önce 0.7 yükü, sonra 0.8 yükü kullanıldı. Kısa yönlendirmenin toplam girdiyi öncekinin yarısının altına indirmesi testte zorunlu tutuluyor.

| Model | 0.7 toplam girdi | 0.8 toplam girdi | Girdi azalması |
|---|---:|---:|---:|
| LFM3-1.7B | 542 token | 170 token | ≈%69 |
| Qwen3-0.6B | 472 token | 122 token | ≈%74 |

İlk LFM host koşusunda girdi süresi 4.208 ms → 1.348 ms, çıktı 29 → 50 token oldu. Toplam yaklaşık 5,8 → 3,9 saniye. JSON'un daha fazla çıktı tokenı üretmesi, girdi kazancının bir kısmını tüketir. Qwen host toplamı yaklaşık 3,1 → 1,8 saniye oldu. Bunlar telefon ölçümü değildir; telefonda sabit bir süre veya hız çarpanı vaat edilmez.

## Doğruluk ve sınırlar

En kısa denemelerde etiketler kaybolduğu için bunlar ürüne alınmadı. Seçilen kısa yönlendirmeler gerçek LFM ve Qwen ile üç haber örneğinde beklenen ana EMEK/KUBA/TKP etiketini korudu; bütün etiketleri körlemesine kopyalamama kontrolü de geçti. Bu üç örnek kapsamlı doğruluk ölçümü değildir. İlgisiz ek etiketler, yanlış tür ve puanlar hâlâ görüldü. Ayrıntılı puanlama tanımları kaldırıldığı için önceki kalibrasyonun aynen korunması beklenmemeli. Elle düzeltme korunur.

Birim testinde gönderilen haber girdisinin birebir başlık olduğu, giriş değişince girdi/önbelleğin değişmediği ve başlık değişince değiştiği kontrol edilir. Gerçek JNI entegrasyonu yükleme hatasından kurtarma, bellekte yeniden kullanım, sınıflandırma, Room/önbellek/sıralama ve girdi tokenı azaltma kontrollerini çalıştırır. Qwen ile 4 entegrasyon testi başarılı. LFM ile 59 testten 58 başarılı, ayrı canlı indirme testi atlandı. Tam test koşusu, APK/Android test APK derlemesi ve lint denetimi yapıldı.

0.8.0 sürüm kodu 8; uygulama kimliği/debug imzası korunur. Önceki sürüm üzerine kurulabilir.
