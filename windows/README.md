# Teyfey voor Windows

Windows-versie van Teyfey (IPTV-speler in TiviMate-stijl). Gemaakt met Kotlin + Compose Desktop; video via VLC (vlcj).

## Installeren
1. Installeer **VLC media player 64-bit** (gratis): https://www.videolan.org
2. Download bij **Actions → laatste groene run → Artifacts → Teyfey-Windows** de zip.
3. Pak uit en dubbelklik op **Teyfey-1.0.0.msi** (installatie), of pak `Teyfey-windows-portable.zip` uit en start `Teyfey.exe`.

## Bouwen
Elke push bouwt automatisch op GitHub (windows-latest): `./gradlew packageMsi createDistributable`.

## Bediening
Pijltjes = bewegen (gele rand) · Enter = kiezen · Esc = terug · F11 = volledig scherm.
Live: ↑/↓ = zappen. Films: spatie = pauze, ←/→ = 10 s, ↑/↓ = volume.
