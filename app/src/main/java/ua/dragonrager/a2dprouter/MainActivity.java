package ua.dragonrager.a2dprouter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")
public final class MainActivity extends Activity {
    private static final int BG = 0xFF0B1018;
    private static final int PANEL = 0xFF18212D;
    private static final int INSET = 0xFF222F3B;
    private static final int WHITE = 0xFFF0F4F7;
    private static final int MUTED = 0xFF9EACB8;
    private static final int TEAL = 0xFF6EE4C4;
    private static final int ORANGE = 0xFFF7BF7D;
    private static final String MEET = "com.google.android.apps.tachyon";
    private static final String SPOTIFY = "com.spotify.music";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private RootBridge.Snapshot snapshot;
    private boolean drawing = false;
    private boolean busy = false;

    private TextView statusText;
    private TextView deviceText;
    private TextView noteText;
    private TextView meetRoute;
    private TextView spotifyRoute;
    private Switch mainSwitch;
    private Switch meetSwitch;
    private Switch spotifySwitch;
    private Button refreshButton;

    private int dp(float pixels) { return (int) (pixels * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUI();
    }

    @Override protected void onResume() {
        super.onResume();
        // No background service: the panel only reads status while it is open.
        if (!busy) refresh();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setIncludeFontPadding(true);
        return v;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private void marginBottom(LinearLayout target, View child, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(bottom);
        target.addView(child, p);
    }

    private LinearLayout panel(LinearLayout container) {
        LinearLayout card = vertical();
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(shape(PANEL, 19, 0xFF293847));
        marginBottom(container, card, 14);
        return card;
    }

    private void addText(LinearLayout container, String t, int s, int c, boolean b, int bottom) {
        marginBottom(container, text(t, s, c, b), bottom);
    }

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = vertical();
        root.setPadding(dp(18), dp(22), dp(18), dp(30));
        scroll.addView(root);
        setContentView(scroll);

        TextView over = text("AUDIO ROUTING  /  ANDROID 15", 11, TEAL, true);
        marginBottom(root, over, 7);
        addText(root, "A2DP AutoRouter", 29, WHITE, true, 3);
        addText(root, "Керування робочим ядром v8 без ноутбука", 14, MUTED, false, 5);
        addText(root, "Alpha · 2026.11.06-alpha.1", 12, TEAL, false, 20);

        LinearLayout statusCard = panel(root);
        addText(statusCard, "СТАН СИСТЕМИ", 11, MUTED, true, 9);
        statusText = text("Очікування root-доступу…", 17, WHITE, true);
        marginBottom(statusCard, statusText, 7);
        addText(statusCard, "Працює з уже встановленими Magisk і ZygiskFrida. Не змінює їхню конфігурацію.",
                12, MUTED, false, 0);

        LinearLayout masterCard = panel(root);
        LinearLayout mainRow = row();
        LinearLayout masterLabels = vertical();
        masterLabels.addView(text("Головний перемикач", 17, WHITE, true));
        masterLabels.addView(text("Маршрутизувати нові потоки", 12, MUTED, false));
        mainRow.addView(masterLabels, new LinearLayout.LayoutParams(0, -2, 1));
        mainSwitch = new Switch(this);
        mainSwitch.setContentDescription("Увімкнути або вимкнути A2DP AutoRouter");
        mainRow.addView(mainSwitch);
        masterCard.addView(mainRow);
        mainSwitch.setOnCheckedChangeListener((v, checked) -> { if (!drawing) save(null, checked); });

        LinearLayout deviceCard = panel(root);
        addText(deviceCard, "ПІДКЛЮЧЕНИЙ АУДІОВИХІД", 11, MUTED, true, 9);
        deviceText = text("A2DP · пошук…", 17, WHITE, true);
        marginBottom(deviceCard, deviceText, 7);
        addText(deviceCard, "v8 автоматично вибирає єдиний активний A2DP-вихід. Якщо їх декілька або ввімкнено HFP, правило не застосовується.",
                12, MUTED, false, 12);
        LinearLayout actions = row();
        refreshButton = action("↻ Оновити", true, () -> refresh());
        actions.addView(refreshButton, new LinearLayout.LayoutParams(0, dp(45), 1));
        Button bt = action("Bluetooth", false, () -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        LinearLayout.LayoutParams btParams = new LinearLayout.LayoutParams(0, dp(45), 1);
        btParams.leftMargin = dp(10);
        actions.addView(bt, btParams);
        deviceCard.addView(actions);

        addText(root, "ПРАВИЛА ЗАСТОСУНКІВ", 12, MUTED, true, 9);
        LinearLayout meetCard = panel(root);
        meetRoute = appCard(meetCard, "Google Meet", "Перетворення VOICE_COMMUNICATION → MEDIA",
                MEET, true);
        LinearLayout spotifyCard = panel(root);
        spotifyRoute = appCard(spotifyCard, "Spotify", "MEDIA без зміни типу потоку",
                SPOTIFY, false);

        LinearLayout micCard = panel(root);
        addText(micCard, "МІКРОФОН", 11, MUTED, true, 9);
        addText(micCard, "Мікрофон телефона", 17, WHITE, true, 5);
        addText(micCard, "Поточний v8 не перенаправляє вхідний звук. У нашій перевіреній конфігурації Meet записує мікрофон телефона.",
                12, MUTED, false, 0);

        LinearLayout other = panel(root);
        addText(other, "ІНШІ ЗАСТОСУНКИ ТА ПРИСТРОЇ", 11, MUTED, true, 8);
        addText(other, "Ця версія керує лише двома перевіреними правилами v8. Для довільних програм, USB/проводових виходів і вибору мікрофона потрібні окремі хуки.",
                12, MUTED, false, 10);
        Button why = action("Чому лише Meet і Spotify?", false, this::explainLimits);
        other.addView(why, new LinearLayout.LayoutParams(-1, dp(44)));

        noteText = text("Правила діють під час створення нового аудіопотоку. Зміни можуть потребувати повторного входу в Meet або перезапуску Spotify.",
                12, MUTED, false);
        marginBottom(root, noteText, 12);
        addText(root, "Без інтернет-дозволу · без запису звуку · root лише для файлів v8", 11, MUTED, false, 0);
        setEnabled(false);
    }

    private TextView appCard(LinearLayout card, String label, String sub, String pkg, boolean meet) {
        LinearLayout titleRow = row();
        LinearLayout words = vertical();
        words.addView(text(label, 19, WHITE, true));
        words.addView(text(sub, 12, MUTED, false));
        titleRow.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setContentDescription("Призначити A2DP для " + label);
        titleRow.addView(sw);
        card.addView(titleRow);
        if (meet) meetSwitch = sw; else spotifySwitch = sw;
        sw.setOnCheckedChangeListener((v, checked) -> { if (!drawing) save(pkg, checked); });
        TextView route = text("Вихід: —", 13, TEAL, true);
        LinearLayout.LayoutParams rt = new LinearLayout.LayoutParams(-1, -2);
        rt.topMargin = dp(12); rt.bottomMargin = dp(13);
        card.addView(route, rt);

        LinearLayout buttons = row();
        Button open = action("Відкрити", false, () -> openPackage(pkg));
        buttons.addView(open, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button restart = action("Перезапустити", false, () -> restartConfirm(pkg, label));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(42), 1);
        rp.leftMargin = dp(9);
        buttons.addView(restart, rp);
        card.addView(buttons);
        return route;
    }

    private Button action(String label, boolean primary, Runnable onTap) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(primary ? BG : WHITE);
        b.setBackground(shape(primary ? TEAL : INSET, 13, primary ? 0 : 0xFF3B4A59));
        b.setOnClickListener(v -> onTap.run());
        return b;
    }

    private void setEnabled(boolean enabled) {
        mainSwitch.setEnabled(enabled);
        meetSwitch.setEnabled(enabled);
        spotifySwitch.setEnabled(enabled);
        refreshButton.setEnabled(!busy);
    }

    private void refresh() {
        if (busy) return;
        busy = true;
        setEnabled(false);
        statusText.setText("Зчитування стану…");
        io.execute(() -> {
            try {
                RootBridge.Snapshot s = RootBridge.read();
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    snapshot = s;
                    busy = false;
                    render(s);
                    setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    snapshot = null;
                    busy = false;
                    setEnabled(false);
                    refreshButton.setEnabled(true);
                    statusText.setTextColor(ORANGE);
                    statusText.setText("Немає зв'язку з AutoRouter");
                    deviceText.setText("Немає даних");
                    showError(e.getMessage());
                });
            }
        });
    }

    private void render(RootBridge.Snapshot s) {
        drawing = true;
        try {
            mainSwitch.setChecked(s.master());
            meetSwitch.setChecked(s.app(MEET));
            spotifySwitch.setChecked(s.app(SPOTIFY));
            meetRoute.setText(s.app(MEET) ? "Вихід: A2DP (при новому потоці)" : "Вихід: системний маршрут");
            spotifyRoute.setText(s.app(SPOTIFY) ? "Вихід: A2DP (при новому потоці)" : "Вихід: системний маршрут");
            statusText.setTextColor(s.master() ? TEAL : ORANGE);
            statusText.setText(s.master() ? "● Маршрутизатор увімкнений" : "● Правила вимкнено");
            if (s.ready()) {
                deviceText.setTextColor(TEAL);
                deviceText.setText("A2DP · порт #" + s.a2dpId() + " · готовий");
            } else if (s.sco()) {
                deviceText.setTextColor(ORANGE);
                deviceText.setText("HFP/SCO активний — маршрутизацію заблоковано");
            } else if (s.a2dpCount() > 1) {
                deviceText.setTextColor(ORANGE);
                deviceText.setText("Кілька A2DP-пристроїв — неоднозначний маршрут");
            } else if (s.a2dpId() == 0) {
                deviceText.setTextColor(ORANGE);
                deviceText.setText("A2DP не знайдено");
            } else {
                deviceText.setTextColor(ORANGE);
                deviceText.setText("Дані застаріли (" + s.ageSeconds() + " с). Онови.");
            }
            noteText.setText("Правила застосовуються лише до нових аудіопотоків. " +
                    "Після перемикання повторно зайди в Meet або перезапусти Spotify.");
        } finally {
            drawing = false;
        }
    }

    private void save(String app, boolean enabled) {
        if (busy || snapshot == null) return;
        busy = true;
        setEnabled(false);
        io.execute(() -> {
            try {
                RootBridge.Snapshot result = RootBridge.change(app, enabled);
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    snapshot = result;
                    busy = false;
                    render(result);
                    setEnabled(true);
                    Toast.makeText(this, "Правило збережено. Потрібен новий аудіопотік.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    busy = false;
                    if (snapshot != null) render(snapshot);
                    setEnabled(snapshot != null);
                    showError("Збереження не вдалося: " + e.getMessage());
                });
            }
        });
    }

    private void restartConfirm(String pkg, String appName) {
        new AlertDialog.Builder(this)
                .setTitle("Перезапустити " + appName + "?")
                .setMessage("Це примусово закриє застосунок і може завершити активний дзвінок чи відтворення. Потім програма відкриється знову.")
                .setNegativeButton("Скасувати", null)
                .setPositiveButton("Перезапустити", (dialog, which) -> {
                    if (busy) return;
                    busy = true;
                    setEnabled(false);
                    io.execute(() -> {
                        try {
                            RootBridge.forceStop(pkg);
                            runOnUiThread(() -> {
                                busy = false;
                                setEnabled(snapshot != null);
                                openPackage(pkg);
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                busy = false;
                                setEnabled(snapshot != null);
                                showError(e.getMessage());
                            });
                        }
                    });
                }).show();
    }

    private void openPackage(String pkg) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) {
            showError("Не вдалося знайти запуск " + pkg + ". Перевір, що застосунок встановлений.");
            return;
        }
        try { startActivity(intent); }
        catch (Exception e) { showError("Не вдалося відкрити застосунок: " + e.getMessage()); }
    }

    private void explainLimits() {
        new AlertDialog.Builder(this)
                .setTitle("Можливості v8")
                .setMessage("Вже перевірено:\n\n• Meet: VOICE_COMMUNICATION → MEDIA → A2DP\n• Spotify: MEDIA → A2DP\n• Мікрофон Meet лишається телефоном.\n\nДовільні застосунки й виходи не можна безпечно додати лише перемикачем: кожна нова програма потребує перевіреного AAudio-правила та конфігурації ZygiskFrida. Ця панель навмисно не переписує їх.")
                .setPositiveButton("Зрозуміло", null).show();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("A2DP AutoRouter")
                .setMessage(message == null ? "Невідома помилка" : message)
                .setPositiveButton("OK", null).show();
    }
}
