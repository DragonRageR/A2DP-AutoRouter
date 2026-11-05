# A2DP AutoRouter 🎧

**Дата релізу:** 6 листопада 2026 · **версія:** `2026.11.06-alpha.1` · **стадія:** **Alpha** (експериментальна).

Android-аудіомаршрутизатор для **Google Meet + Spotify → Bluetooth A2DP** зі збереженням вбудованого мікрофона телефона. Складається з Android-застосунку **Control** та системного backend **Router** на базі **Magisk / ZygiskFrida / Frida Gadget**.

> ⚠️ Це **не універсальний аудіомікшер**. Перевірено на Motorola moto g54 5G, Android 15, Sony WH-CH720N. Потребує root. Реліз Alpha: не гарантуємо роботу на інших телефонах, прошивках, програмах або конфігураціях Bluetooth.

## Можливості

- Meet: змінює `AAudio USAGE_VOICE_COMMUNICATION → USAGE_MEDIA` для **вихідного** потоку та запитує A2DP.
- Spotify: запитує A2DP для вихідного потоку `USAGE_MEDIA`.
- **Одночасне** прослуховування Meet та Spotify у Bluetooth-навушниках.
- Meet продовжує використовувати **мікрофон телефона**; вхідні потоки не перехоплюються.
- Автозапуск через ZygiskFrida після перезавантаження, без ноутбука під час звичайного використання.
- Android-панель Control: увімкнення/вимкнення всього маршрутизатора та окремих правил Meet / Spotify, стан A2DP і HFP.
- Динамічне визначення **ID аудіопорту** через watcher; ID може змінюватися при перепідключенні.

### Що наразі **не** підтримується

- Інші застосунки, довільні Bluetooth/USB/проводові виходи, вибір мікрофона з UI.
- Перемикання **вже відкритого** потоку на інший пристрій: потрібно заново створити потік (перезайти в Meet чи перезапустити відтворення Spotify).
- HFP/SCO: у перевіреному сценарії профіль **«Телефонні виклики» вимкнений**, а «Медіа-аудіо» увімкнений.
- Два одночасні A2DP-пристрої: маршрут не встановлюється, щоб не вибрати неправильний.
- Звичайні/екстрені дзвінки не тестувалися; не змінюємо Telecom/Audio Focus або системну AudioPolicy.

## Структура

```text
app/                  Android GUI (Java 17, Android Gradle Plugin)
router/               Frida Gadget JS, правила, watcher, ADB-контролер
scripts/build.sh      Збірка debug APK на Linux із наявним SDK
.github/workflows/    CI збірка APK та prerelease за тегом
VERSION               Єдине позначення дати й етапу
CHANGELOG.md          Історія змін
RELEASE_NOTES.md      Опис першого GitHub alpha-релізу
```

## Встановлення / збірка GUI

### На телефоні (умови)

1. Android із Magisk root та активним Zygisk, сумісним **ZygiskFrida** (окремий проєкт: https://github.com/lico-n/ZygiskFrida).
2. Наявний **Frida Gadget**, встановлений ZygiskFrida як `/data/local/tmp/re.zyg.fri/libgadget.so` (цей репозиторій **не містить** сторонній бінарник).
3. У Bluetooth налаштуваннях Sony: `Media audio` **ON**, `Phone calls / HFP` **OFF**.
4. Спочатку встановіть системний backend `router/`, потім Android-застосунок. **Якщо v8 вже працює, нічого повторно не встановлюйте!** Застосунок Control сам підключиться до наявного backend.

### Якщо backend v8 уже встановлено

**Не запускайте** `router/controller.py install` вдруге. Скомпілюйте лише APK (або завантажте CI debug-збірку), встановіть і надайте дозвіл root у Magisk. Застосунок читає/змінює `audio_router_rules.json` на телефоні; не перезаписує Gadget.

### Чиста інсталяція backend (для досвідчених користувачів)

Потрібні: `adb`, Python 3, робоче `adb shell su -c 'id -u'` із відповіддю `0` і заздалегідь встановлений ZygiskFrida. Не встановлюйте поверх іншої конфігурації Gadget — інсталятор зупиниться, якщо виявить конфлікт.

```bash
python3 router/controller.py status
python3 router/controller.py install
adb shell am force-stop com.google.android.apps.tachyon
adb shell am force-stop com.spotify.music
adb logcat -s A2DPRouter ZygiskFrida
```

Після `ready` і `hooks_ready` відкрийте Meet, заново зайдіть у тестову конференцію; потім запустіть Spotify. Критичні події: `pin_requested` → `open` (result 0) → `start` (result 0). Для вимкнення правил без перевстановлення:

```bash
python3 router/controller.py disable
# або повне видалення власних компонентів:
python3 router/controller.py uninstall
```

**Увага:** наведений інсталятор виправлено на основі успішних ручних тестів, але його **повний чистий сценарій із нуля окремо не пройшов перевірку на іншому пристрої**. Перед інсталяцією зробіть резервну копію `/data/local/tmp/re.zyg.fri/config.json` (якщо існує) та власних налаштувань Magisk/ZygiskFrida.

### Збірка APK на Linux/Nobara

Потрібні JDK 17, Android SDK `platforms;android-35`, `build-tools;35.0.0`, Gradle (Wrapper автоматично завантажує офіційну версію 8.9, перевіряючи SHA-256). Для SDK: https://developer.android.com/tools/sdkmanager

```bash
./scripts/build.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Під час першої збірки потрібен інтернет для Gradle/Android Gradle Plugin. **SDK та ліцензії потрібно налаштувати заздалегідь**; скрипт не змінює систему та не приймає ліцензії автоматично. Якщо `/usr/bin/java` вказує на Java 25, `build.sh` спробує знайти Temurin 17 із `$HOME/.local/share/jdks/temurin-17`.

### GitHub Actions

Пуші та ручний запуск workflow **Android APK** збирають debug APK і додають артефакт до сторінки Actions. Пуш тегу `v2026.11.06-alpha.1` створює **GitHub Pre-release** та прикріплює debug APK (за умови дозволу workflow на `contents: write`).

**Debug APK підписується тимчасовим ключем GitHub Actions.** Для оновлення APK з іншого середовища або іншого CI-запуску може знадобитися видалити старий APK, через інший підпис. Для стабільних оновлень у майбутньому потрібен власний постійний signing key і release-процес. Не публікуйте приватний ключ у репозиторії.

## Перевірка

Робоче підтвердження отримано на **moto g54 5G / Android 15 / Magisk + ZygiskFrida / Sony WH-CH720N** із Meet+Spotify одночасно, мікрофон телефона. Після reboot пристрій теж працював. Цей GitHub-ready вихідний пакет підготовлений зі встановленим hotfix `/proc/self/cmdline`; **нову Android-збірку в цьому середовищі не компілювали**. Додаткові зміни інсталятора та CI перевірено статично, але не протестовано на новому пристрої.

## Безпека та приватність

- Android APK не має дозволу `INTERNET` або `RECORD_AUDIO`.
- Root потрібен для керування файлами **власного** AutoRouter, а не для перехоплення чи збереження вмісту дзвінків.
- Не перехоплюємо мікрофон і не робимо запис звуку.
- На інших пристроях/прошивках модифікація аудіомаршрутів може спричинити збої. Не покладайтеся на Alpha для критичних або екстрених дзвінків.
- Для швидкого вимкнення: головний перемикач у Control або `python3 router/controller.py disable`, після чого перезапустіть застосунки для нових потоків.

## Версійність

Використовуємо **CalVer + prerelease**: `YYYY.MM.DD-alpha.N`. Наприклад, `2026.11.06-alpha.1`, тег GitHub: `v2026.11.06-alpha.1`. `alpha.2` — друге тестове виправлення того самого дня, а наступного дня — нова дата. У Android `versionName` має той самий рядок, а `versionCode=2026110601` є числовим кодом збірки.

## Ліцензія

MIT — дивіться [LICENSE](LICENSE). Сторонній Frida Gadget / ZygiskFrida не включений до вихідного архіву та має свої ліцензії.
