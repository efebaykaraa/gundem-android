# Gündem 0.6.0 — CPU uyumluluk sürümü

0.4'te açılan ARM KleidiAI hızlandırması yeni takılma için şüpheli. Fiziksel cihaz logu/stack trace olmadan kesin neden doğrulanmış değildir. Bu sürüm şüpheli yolları tamamen çıkarır:

- `GGML_CPU_KLEIDIAI=OFF`, `GGML_CPU_REPACK=OFF`, `GGML_NATIVE=OFF`.
- ARM64 için açıkça `GGML_CPU_ARM_ARCH=armv8-a`; i8mm, SVE veya daha yeni ARM mimarisi zorlanmaz. Temel ARM64/NEON CPU desteği korunur.
- Model yüklemede `use_extra_bufts=false`: ek ağırlık dönüştürme tamponları kullanılmaz.
- CPU motoru artık JNI kütüphanesinin statik kurucusunda başlatılmaz. Java kütüphanesi önce açılır; motor başlatma model yükleme işleminin içine alınır. Böylece bu aşama boyunca durum sorgulaması yapılabilir ve “CPU motoru hazırlanıyor” aşaması loglanabilir.
- Çalışma logunda `cpu_mode=portable`, `weight_repack=0`, `arm_kleidiai=0` görünür.

Başlıkla analiz, 2 iş parçacığı, batch 256, 2.048 token bağlam, RAM'de model tutma ve önceki arayüz düzeltmeleri korunur. Model/prompt kimliği değişmediğinden geçerli analizler sırf bu sürüm için tekrar çalıştırılmaz. Model dosyası yeniden indirilmez.

APK'daki iki ABI kütüphanesinde portable CPU işareti ve `arm_kleidiai=0` doğrulandı. ARM derlemesinin gerçek compile_commands/CMakeCache dosyalarında temel armv8-a hedefi, kapalı KleidiAI/repack bayrakları ve KleidiAI kaynaklarının bulunmadığı kontrol edildi. Yeni ARM kütüphanesinde KleidiAI çekirdek dizgileri bulunmuyor.

APK derlemesi, standart birim testler ve lint başarılı. Gerçek LFM ile 57 testten 56 test başarılı; ayrı canlı indirme testi atlandı. Gerçek Qwen ile 4 entegrasyon testi başarılı. Gerçek LFM host JNI testleri model yükleme, başlıktan sonuç üretme, yükleme hatasından kurtarma, RAM'de yeniden kullanım ve Room/önbellek/gruplama yollarını çalıştırır. Host x86 testleri ARM donanım testi yerine geçmez. Fiziksel ARM telefon bu oturuma bağlı değil; telefondaki takılmanın tamamen çözüldüğü henüz doğrulanmadı.

Uygulama kimliği/debug imzası korunur; sürüm kodu 6. Mevcut uygulamanın üzerine güncellenebilir.
