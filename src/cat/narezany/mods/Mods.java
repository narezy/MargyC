package cat.narezany.mods;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;

/** Общие штуки мода: открыть экран модов, настройки, язык приложения, перезапуск. */
public final class Mods {
    static final String TAG = "MargyC";
    static final String VERSION = "1.1"; // версия MargyC, не Claude

    private Mods() {}

    public static void open() {
        try {
            Context ctx = app();
            Intent intent = new Intent(ctx, ModsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Throwable t) {
            Log.e(TAG, "не удалось открыть экран модов", t);
        }
    }

    /** Application.onCreate, сразу после CrashLog: питомец и свои моды. */
    public static void init(android.app.Application app) {
        try {
            Pet.install(app);
        } catch (Throwable t) {
            Log.e(TAG, "Pet", t);
        }
        try {
            Plugins.load(app);
        } catch (Throwable t) {
            Log.e(TAG, "Plugins", t);
        }
    }

    private static SharedPreferences prefs;

    static SharedPreferences prefs() throws Exception {
        if (prefs == null) {
            prefs = app().getSharedPreferences("claude_mods", Context.MODE_PRIVATE);
        }
        return prefs;
    }

    static void showGoogleInfo() {
        try {
            Context ctx = app();
            Intent intent = new Intent(ctx, InfoActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Throwable t) {
            Log.e(TAG, "не удалось показать окно про Google", t);
        }
    }

    private static boolean restartNeeded;
    private static final java.util.List<Runnable> restartListeners = new java.util.ArrayList<Runnable>();

    /** Изменение применится после перезапуска: копим и показываем плашку внизу экрана модов. */
    static void needRestart() {
        restartNeeded = true;
        for (Runnable r : new java.util.ArrayList<Runnable>(restartListeners)) {
            r.run();
        }
    }

    static boolean restartNeeded() {
        return restartNeeded;
    }

    static void onRestartNeeded(Runnable r, boolean add) {
        if (add) {
            restartListeners.add(r);
        } else {
            restartListeners.remove(r);
        }
    }

    /** Перезапуск приложения: цвета темы читаются один раз при старте. */
    static void restart(Context ctx) {
        try {
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            // система поднимет процесс заново, чтобы показать запущенную активность
            ctx.startActivity(launch);
        } catch (Throwable t) {
            Log.e(TAG, "restart", t);
        }
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    static Context app() throws Exception {
        Class<?> at = Class.forName("android.app.ActivityThread");
        return (Context) at.getMethod("currentApplication").invoke(null);
    }

    /** Per-app язык есть только с Android 13 (API 33). */
    static boolean localeSupported() {
        return Build.VERSION.SDK_INT >= 33;
    }

    private static Object localeManager(Context ctx) {
        return ctx.getSystemService("locale"); // Context.LOCALE_SERVICE
    }

    static boolean isRussian(Context ctx) {
        if (!localeSupported()) {
            return false;
        }
        try {
            Object lm = localeManager(ctx);
            Object list = lm.getClass().getMethod("getApplicationLocales").invoke(lm);
            String tags = (String) list.getClass().getMethod("toLanguageTags").invoke(list);
            if (tags == null || tags.isEmpty()) {
                return systemRussian(); // язык не задан, значит берётся системный
            }
            return tags.startsWith("ru");
        } catch (Throwable t) {
            Log.e(TAG, "getApplicationLocales", t);
            return false;
        }
    }

    /** Системный язык, без учёта языка, выбранного для приложения. */
    static boolean systemRussian() {
        return android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0)
                .getLanguage().equals("ru");
    }

    static boolean setRussian(Context ctx, boolean enabled) {
        if (!localeSupported()) {
            return false;
        }
        try {
            Class<?> localeList = Class.forName("android.os.LocaleList");
            // выключение = язык системы, но если система сама на русском, ставим английский явно,
            // иначе пустой список снова даёт русский
            Object list = enabled || systemRussian()
                    ? localeList.getMethod("forLanguageTags", String.class).invoke(null, enabled ? "ru" : "en")
                    : localeList.getMethod("getEmptyLocaleList").invoke(null);
            Object lm = localeManager(ctx);
            Method set = lm.getClass().getMethod("setApplicationLocales", localeList);
            set.invoke(lm, list);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "setApplicationLocales", t);
            return false;
        }
    }
}
