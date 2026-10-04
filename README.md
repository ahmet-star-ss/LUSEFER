# LUSİFER – Tam sürüm (v1.0)
Yapımcı: @BY_SADRAZAM · Kanal: https://t.me/LulzSecARSIV

## Derleme (Termux + GitHub Actions)
    unzip Lusifer.zip && cd Lusifer
    git init && git add . && git commit -m "lusifer"
    git branch -M main
    git remote add origin https://github.com/KULLANICI/REPO.git
    git push -u origin main
GitHub > Actions > "LUSIFER APK" > bitince Artifacts > LUSIFER-apk (app-release.apk).
İlk derleme 20-40 dk sürebilir (llama.cpp + ~1,5 GB model indirilir ve APK'ya gömülür).
Modeller repoya KONMAZ; workflow derleme sırasında indirir (Vosk TR, Qwen 0.5B + 1.5B).
APK ~1,7 GB olur. Sabit anahtarla imzalanır (lusifer.keystore): üstüne güncellenir.

## İlk kullanım
1. Kur, aç, hitap adını gir, izinleri ver.
2. Menü > Modeller: 0.5B veya 1.5B seç > "Hazırla" (bir kez, birkaç dk).
3. Menü > İzinler/Yetki: Erişilebilirlik, tüm dosyalar, overlay vb. aç; Shizuku/ADB/Root seç.
4. Menü > API Key: sağlayıcı seç, anahtarı gir, "Test et + modelleri bul".
5. "LUSİFER" veya "Hey LUSİFER" de.

## Görseller (res/drawable-nodpi)
profile_photo.jpg  -> profil (şapkalı adam, birebir kopya, dokunulmadı)
bg_lusifer.jpg     -> uygulama arka planı
splash_panoptik.png-> her açılışta ~2 sn, yavaşça silinir
