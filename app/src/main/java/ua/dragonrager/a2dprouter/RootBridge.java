package ua.dragonrager.a2dprouter;

import android.util.Base64;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Minimal, fixed-path root bridge for the already-installed v8 router.
 * No arbitrary user-provided shell commands or script/module replacement.
 */
final class RootBridge {
    static final String BASE = "/data/local/tmp/re.zyg.fri";
    private static final String MARK = BASE + "/audio_router_enabled";
    private static final String RULES = BASE + "/audio_router_rules.json";
    private static final String STATUS = BASE + "/audio_router_status.json";

    private RootBridge() {}

    private static String root(String command) throws Exception {
        Process p = new ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true).start();
        // The bridge executes only commands with tiny output (<64KB), so waiting
        // before draining stdout is safe for our fixed list of commands.
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new Exception("Час очікування root вичерпано. Перевір запит Magisk.");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (out.size() + n > 65536) throw new Exception("Несподівано великий вивід su");
                out.write(buf, 0, n);
            }
        }
        String result = out.toString(StandardCharsets.UTF_8.name()).trim();
        if (p.exitValue() != 0) {
            throw new Exception("Root-команда не виконалась: " + result);
        }
        return result;
    }

    static Snapshot read() throws Exception {
        if (!"0".equals(root("id -u")))
            throw new Exception("Немає root-доступу. Надай дозвіл у Magisk.");
        if (!"yes".equals(root("test -f '" + MARK + "' && echo yes || echo no")))
            throw new Exception("AutoRouter v8 не знайдено. Спочатку встанови робочий v8.");
        JSONObject rules = new JSONObject(root("cat '" + RULES + "'"));
        JSONObject device = new JSONObject(root("cat '" + STATUS + "' 2>/dev/null || echo '{}'"));
        if (!rules.has("apps") || !rules.getJSONObject("apps").has("com.spotify.music")
                || !rules.getJSONObject("apps").has("com.google.android.apps.tachyon")) {
            throw new JSONException("Несумісний формат правил v8. Зміни не виконуватимуться.");
        }
        return new Snapshot(rules, device);
    }

    static Snapshot change(String app, boolean enabled) throws Exception {
        Snapshot snap = read();
        JSONObject rules = snap.rules;
        if (app == null) {
            rules.put("enabled", enabled);
        } else if ("com.spotify.music".equals(app) ||
                   "com.google.android.apps.tachyon".equals(app)) {
            rules.getJSONObject("apps").getJSONObject(app).put("enabled", enabled);
        } else {
            throw new IllegalArgumentException("Непідтримуваний застосунок");
        }
        writeRulesAtomically(rules);
        Snapshot verified = read();
        boolean actual = app == null ? verified.rules.getBoolean("enabled")
                : verified.rules.getJSONObject("apps").getJSONObject(app).getBoolean("enabled");
        if (actual != enabled) throw new Exception("Не вдалося перевірити збережені правила");
        return verified;
    }

    private static void writeRulesAtomically(JSONObject rules) throws Exception {
        String json = rules.toString(2) + "\n";
        String b64 = Base64.encodeToString(json.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        // The only variable shell argument is standard base64 ([A-Za-z0-9+/=]).
        // Atomic rename makes the Gadget see either old or new complete JSON.
        String temp = BASE + "/.audio_router_rules.ui.tmp";
        String command = "umask 022; printf '%s' '" + b64 + "' | base64 -d > '"
                + temp + "' && mv -f '" + temp + "' '" + RULES + "' && echo __SAVED__";
        if (!root(command).endsWith("__SAVED__"))
            throw new Exception("Не отримано підтвердження запису правил");
    }

    static void forceStop(String packageName) throws Exception {
        if (!"com.spotify.music".equals(packageName) &&
            !"com.google.android.apps.tachyon".equals(packageName)) {
            throw new IllegalArgumentException("Непідтримуваний застосунок");
        }
        root("am force-stop '" + packageName + "' && echo __STOPPED__");
    }

    static final class Snapshot {
        final JSONObject rules;
        final JSONObject device;
        Snapshot(JSONObject rules, JSONObject device) {
            this.rules = rules;
            this.device = device;
        }
        boolean master() { return rules.optBoolean("enabled", false); }
        boolean app(String name) {
            return rules.optJSONObject("apps") != null &&
                rules.optJSONObject("apps").optJSONObject(name) != null &&
                rules.optJSONObject("apps").optJSONObject(name).optBoolean("enabled", false);
        }
        int a2dpId() { return device.optInt("a2dp_id", 0); }
        int a2dpCount() { return device.optInt("a2dp_count", 0); }
        boolean sco() { return device.optBoolean("sco_present", true); }
        long ageSeconds() { return Math.abs(System.currentTimeMillis() / 1000L - device.optLong("updated", 0L)); }
        boolean ready() {
            return a2dpId() > 0 && a2dpCount() == 1 && !sco() && ageSeconds() < 35;
        }
    }
}
