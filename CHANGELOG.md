# Changelog

## 2026.11.06-alpha.1 — 2026-11-06

Перша датована alpha-версія для GitHub.

- Android Control з root-керуванням правилами Meet/Spotify.
- Актуальний backend AutoRouter v8 із робочим binary-safe читанням `/proc/self/cmdline` (усуває `cmdline_error`).
- Правила для двох одночасних AAudio-виходів на один A2DP-пристрій.
- Відображення A2DP ID і HFP/SCO, Magisk watcher.
- Офіційний Gradle 8.9 Wrapper із SHA256-перевіркою URL GitHub.
- CalVer (`YYYY.MM.DD-alpha.N`) у APK і в GitHub tag.
- Build helper, GitHub Actions, README, обережний чистий інсталятор (з окремими caveat'ами).

### Обмеження

Підтримуються лише Google Meet і Spotify, один A2DP-пристрій, вимкнений HFP, лише **нові** AAudio-потоки. Android-додаток не встановлює backend автоматично.
