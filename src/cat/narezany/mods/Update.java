package cat.narezany.mods;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Проверка обновлений. В ветке Download репозитория лежит version.json с последним релизом и последней
 * бетой; бета-канал включается на экране модов (у беты он включён сразу). Релиз той же версии, что и
 * установленная бета, тоже предлагается: у релиза номер сборки больше ({@link Mods#code}).
 *
 * <pre>
 * {"release": {"version": "1.2", "beta": 0, "apk": "MargyC.apk", "notes": "..."},
 *  "beta":    {"version": "1.3", "beta": 1, "apk": "MargyC_beta.apk", "notes": "..."}}
 * </pre>
 */
final class Update {
    static final String RAW = "https://raw.githubusercontent.com/narezy/MargyC/Download/";
    static final String DOWNLOAD = "https://github.com/narezy/MargyC/raw/Download/";
    /** Сама проверяем не чаще, чем раз в столько. */
    private static final long PERIOD = 4 * 60 * 60 * 1000L;

    private static volatile Info available;
    private static boolean checking, shown;

    private Update() {}

    static final class Info {
        final String version, apk, notes;
        final int beta, code;

        Info(JSONObject o) {
            version = o.optString("version");
            beta = o.optInt("beta");
            apk = o.optString("apk");
            notes = o.optString("notes");
            code = Mods.code(version, beta);
        }

        String label() {
            return version + (beta == 0 ? "" : beta == 1 ? " beta" : " beta " + beta);
        }
    }

    static boolean betas() {
        try {
            return Mods.prefs().getBoolean("update_beta", Mods.BETA > 0);
        } catch (Exception e) {
            return Mods.BETA > 0;
        }
    }

    static void setBetas(boolean on) throws Exception {
        Mods.prefs().edit().putBoolean("update_beta", on).putLong("update_checked", 0).apply();
        available = pick(Mods.prefs().getString("update_json", ""));
    }

    static Info available() {
        return available;
    }

    /** При открытии экранов Claude: проверка в фоне и, если есть новая версия, одно окно за запуск. */
    static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(final Activity a) {
                if (a.getClass().getName().startsWith("cat.narezany.mods.")) {
                    return;
                }
                check(false, info -> offer(a, info));
            }

            @Override
            public void onActivityCreated(Activity a, Bundle b) {}

            @Override
            public void onActivityStarted(Activity a) {}

            @Override
            public void onActivityPaused(Activity a) {}

            @Override
            public void onActivityStopped(Activity a) {}

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {}

            @Override
            public void onActivityDestroyed(Activity a) {}
        });
    }

    interface Done {
        /** В главном потоке; info == null — новее нет (или проверить не вышло, тогда error != null). */
        void done(Info info);
    }

    private static String error;

    static String error() {
        return error;
    }

    /** force — не смотреть на время прошлой проверки. */
    static void check(final boolean force, final Done done) {
        final Handler main = new Handler(Looper.getMainLooper());
        synchronized (Update.class) {
            long last = 0;
            try {
                last = Mods.prefs().getLong("update_checked", 0);
            } catch (Exception ignored) {
            }
            if (checking || (!force && System.currentTimeMillis() - last < PERIOD)) {
                if (!checking) {
                    if (available == null) {
                        try { // прошлый ответ сервера: после перезапуска окно снова покажется
                            available = pick(Mods.prefs().getString("update_json", ""));
                        } catch (Exception ignored) {
                        }
                    }
                    final Info info = available;
                    main.post(() -> done.done(info));
                }
                return;
            }
            checking = true;
        }
        new Thread(() -> {
            Info found = null;
            String err = null;
            try {
                String json = fetch(RAW + "version.json?t=" + System.currentTimeMillis());
                found = pick(json);
                Mods.prefs().edit().putLong("update_checked", System.currentTimeMillis())
                        .putString("update_json", json).apply();
            } catch (Throwable t) {
                Log.e(Mods.TAG, "update check", t);
                err = String.valueOf(t.getMessage() != null ? t.getMessage() : t);
            }
            final Info result = found;
            final String e = err;
            main.post(() -> {
                synchronized (Update.class) {
                    checking = false;
                }
                error = e;
                if (e == null) {
                    available = result;
                }
                done.done(available);
            });
        }, "MargyC update").start();
    }

    /** Окно «Вышла новая версия» поверх Claude: раз за запуск, и не для версии, которую отложили. */
    private static void offer(Activity a, Info info) {
        try {
            if (info == null || shown || Lock.locked() || a.isFinishing() || a.isDestroyed()
                    || Mods.prefs().getInt("update_skipped", 0) == info.code) {
                return;
            }
            shown = true;
            sheet(a, info, true);
        } catch (Throwable t) {
            Log.e(Mods.TAG, "update offer", t);
        }
    }

    static void sheet(final Activity a, final Info info, boolean canSkip) {
        Ui ui = new Ui(a);
        String text = L.t("Сейчас у тебя MargyC ") + Mods.label() + "."
                + (info.beta > 0 ? L.t(" Это бета: в ней новые функции, но могут быть ошибки.") : "")
                + (info.notes.isEmpty() ? "" : "\n\n" + info.notes)
                + L.t("\n\nAPK скачается в браузере, установи его поверх, настройки сохранятся.");
        Ui.Sheet sheet = ui.new Sheet(L.t("Вышла MargyC ") + info.label()).message(text);
        if (canSkip) {
            sheet.button(L.t("Пропустить"), false, () -> {
                try {
                    Mods.prefs().edit().putInt("update_skipped", info.code).apply();
                } catch (Exception ignored) {
                }
            });
            sheet.button(L.t("Позже"), false, null);
        } else {
            sheet.button(L.t("Закрыть"), false, null);
        }
        sheet.button(L.t("Скачать"), true, () -> open(a, DOWNLOAD + info.apk));
        sheet.show();
    }

    static void open(Activity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            android.widget.Toast.makeText(a, url, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /** Новейшая версия из version.json, которая новее установленной (беты — если включены), или null. */
    static Info pick(String json) throws Exception {
        if (json == null || json.isEmpty()) {
            return null;
        }
        JSONObject j = new JSONObject(json);
        boolean betas = betas();
        Info found = null;
        for (String key : new String[] {"release", "beta"}) {
            JSONObject o = j.optJSONObject(key);
            if (o == null || o.optString("version").isEmpty()) {
                continue;
            }
            Info i = new Info(o);
            if (i.beta > 0 && !betas) {
                continue;
            }
            if (i.code > Mods.CODE && (found == null || i.code > found.code)) {
                found = i;
            }
        }
        return found;
    }

    static String fetch(String url) throws Exception {
        return new String(download(url), "UTF-8");
    }

    static byte[] download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) {
                throw new java.io.IOException("HTTP " + c.getResponseCode());
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
