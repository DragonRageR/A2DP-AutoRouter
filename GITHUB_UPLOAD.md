# Публікація на GitHub

## Вебінтерфейс GitHub (без термінала)

1. Створи новий порожній репозиторій, наприклад **A2DP-AutoRouter** (https://github.com/new). Не додавай окремий README, бо він уже в архіві.
2. Розпакуй ZIP **локально**, відкрий репозиторій на GitHub і вибери **Add file → Upload files**. Завантаж **вміст** папки, а не ZIP (вебінтерфейс GitHub може погано підтримувати завантаження вкладених директорій). Для повного збереження дерева краще Git CLI нижче.
3. Після коміту створи тег/Release із назвою **`v2026.11.06-alpha.1`**, увімкни опцію **Set as a pre-release**. Можна використати `RELEASE_NOTES.md` як опис.
4. У вкладці **Actions** перевір, чи зібрано APK. На тег workflow створить pre-release автоматично, якщо Actions має права на запис. Не створюй паралельно другий Release із таким самим тегом.

## Git CLI — надійніше

```bash
cd A2DP-AutoRouter-v2026.11.06-alpha.1
git init -b main
git add .
git commit -m "release: v2026.11.06-alpha.1"
git remote add origin https://github.com/YOUR_USERNAME/A2DP-AutoRouter.git
git push -u origin main
git tag -a v2026.11.06-alpha.1 -m "A2DP AutoRouter alpha 1"
git push origin v2026.11.06-alpha.1
```

Замінити `YOUR_USERNAME` на свій GitHub логін. Репозиторій попередньо створи на https://github.com/new. Відправлення через `git push` потребує GitHub-авторизації (SSH key або token/credential manager).

### Якщо GitHub Actions не має прав створити release

Repository → Settings → Actions → General → Workflow permissions → **Read and write permissions**. Альтернатива: створити Release вручну, обов'язково зазначивши **Pre-release**, і додати APK зі сторінки Actions за бажанням.

**Важливо:** у репозиторії немає твоїх дампів, MAC/серійних номерів, root-бекапів, ключів, токенів і бінарника стороннього Frida Gadget.
