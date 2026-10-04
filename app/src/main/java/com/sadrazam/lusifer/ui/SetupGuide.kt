package com.sadrazam.lusifer.ui

/** Proje belgesi §9 - uygulama içi kurulum rehberi (Ayarlar > Kurulum Rehberi). */
val SetupGuide: List<Pair<String, String>> = listOf(
    "A) Genel kurulum" to """
1. APK'yı yükle (Bilinmeyen kaynaklara izin ver, Play Protect'te "Yine de yükle").
2. Uygulamayı aç, kullanıcı adını gir.
3. İzinleri sırayla ver: Mikrofon, Bildirim, Kişiler, Telefon, SMS, Tüm dosyalara erişim (İzinler/Yetki ekranı).
4. Erişilebilirlik servisini aç (Ayarlar > Erişilebilirlik > LUSİFER).
5. Pil: Uygulama için "Kısıtlama yok" seç, Autostart izni ver, son uygulamalarda kilitle.
6. Modeller ekranından kullanacağın offline modeli (0.5B veya 1.5B) seç ve "Hazırla"ya bas (ilk kez birkaç dakika sürer).
""".trim(),
    "B) Shizuku ile yetki" to """
1. Shizuku uygulamasını aç.
2. Kablosuz hata ayıklamayı etkinleştir (Geliştirici seçenekleri > Kablosuz hata ayıklama), eşleştirme koduyla Shizuku'yu başlat.
3. LUSİFER > İzinler/Yetki > "Shizuku" seç, "Shizuku izni iste" düğmesine bas, çıkan pencerede "İzin ver" de.
4. Telefon yeniden başlarsa Shizuku'yu yeniden başlat.
""".trim(),
    "C) ADB ile yetki" to """
1. Geliştirici seçeneklerinde USB/Kablosuz hata ayıklamayı aç.
2. İzinler/Yetki ekranındaki ADB komutlarını PC veya Termux (adb) üzerinden bir kez çalıştır.
3. LUSİFER > İzinler/Yetki > "ADB" seç. (ADB modu izinleri verir; sessiz kaldırma/yükleme için Shizuku veya Root gerekir.)
""".trim(),
    "D) Root (Magisk) ile yetki" to """
1. Cihazda Magisk kurulu olmalı.
2. LUSİFER > İzinler/Yetki > "Root" seç, "Root'u test et"e bas.
3. Magisk'te çıkan süper kullanıcı isteğine "İzin ver" de.
""".trim(),
    "E) Wake word kullanımı" to """
"LUSİFER" veya "Hey LUSİFER" de; asistan "Evet efendim" diye cevap verir ve dinlemeye geçer. Yanlış tetikleme veya hiç algılamama olursa Ayarlar'dan uyandırma hassasiyetini değiştir.
""".trim(),
    "F) HyperOS (Redmi) ipuçları" to """
• Ayarlar > Uygulamalar > LUSİFER > Pil tasarrufu: "Kısıtlama yok".
• Otomatik başlatma: Açık.
• Son uygulamalar ekranında LUSİFER kartını aşağı çekip kilitle.
• "Diğer uygulamaların üzerinde göster" ve "Arka planda açılır pencere" izinlerini ver (uygulama açabilmesi için).
• Telefon yeniden başlayınca uygulamayı bir kez elle aç (Android 14+ kısıtı).
""".trim()
)
