package cat.narezany.mods;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import cat.narezany.mods.api.MargyCPlugin;
import cat.narezany.mods.api.PluginContext;

import dalvik.system.DexClassLoader;

/**
 * Свои моды: архив .mcmod (zip) с manifest.json, classes.dex (можно и classes2.dex...) и необязательной
 * icon.png. Ставится в files/plugins/<id>/, грузится при старте Claude через DexClassLoader, родитель —
 * загрузчик приложения, так что моду видны и классы Claude, и API MargyC (cat.narezany.mods.api).
 * Документация для авторов — docs/plugins.md в репозитории.
 */
public final class Plugins {
    static final int API = 1;

    static final List<Supplier<String>> PROMPTS = new CopyOnWriteArrayList<Supplier<String>>();
    static final List<UnaryOperator<String>> TEXTS = new CopyOnWriteArrayList<UnaryOperator<String>>();
    static final List<IntUnaryOperator> COLORS = new CopyOnWriteArrayList<IntUnaryOperator>();

    private static Activity current;

    private Plugins() {}

    /** Мод из manifest.json. */
    static final class Info {
        final String id, name, description, author, version, entry;
        final JSONArray settings;
        final File dir;

        Info(File dir, JSONObject m) {
            this.dir = dir;
            id = m.optString("id");
            name = m.optString("name", id);
            description = m.optString("description");
            author = m.optString("author");
            version = m.optString("version");
            entry = m.optString("entry");
            settings = m.optJSONArray("settings") != null ? m.optJSONArray("settings") : new JSONArray();
        }

        Bitmap icon() {
            File f = new File(dir, "icon.png");
            return f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
        }

        SharedPreferences prefs(Context ctx) {
            return ctx.getSharedPreferences("plugin_" + id, Context.MODE_PRIVATE);
        }

        JSONObject setting(String key) {
            for (int i = 0; i < settings.length(); i++) {
                JSONObject s = settings.optJSONObject(i);
                if (s != null && key.equals(s.optString("key"))) {
                    return s;
                }
            }
            return null;
        }
    }

    static File root(Context ctx) {
        return new File(ctx.getFilesDir(), "plugins");
    }

    static List<Info> installed(Context ctx) {
        List<Info> list = new ArrayList<Info>();
        File[] dirs = root(ctx).listFiles();
        if (dirs == null) {
            return list;
        }
        java.util.Arrays.sort(dirs);
        for (File d : dirs) {
            try {
                list.add(new Info(d, new JSONObject(read(new File(d, "manifest.json")))));
            } catch (Exception e) {
                Log.e(Mods.TAG, "мод " + d.getName(), e);
            }
        }
        return list;
    }

    static boolean enabled(String id) {
        try {
            return Mods.prefs().getBoolean("plugin_on_" + id, true);
        } catch (Exception e) {
            return false;
        }
    }

    static void setEnabled(String id, boolean on) throws Exception {
        Mods.prefs().edit().putBoolean("plugin_on_" + id, on).apply();
    }

    static String error(String id) {
        try {
            return Mods.prefs().getString("plugin_error_" + id, "");
        } catch (Exception e) {
            return "";
        }
    }

    /** Поставить .mcmod. Бросает исключение с понятным текстом, если архив не мод. */
    static Info install(Context ctx, InputStream in) throws Exception {
        File tmp = new File(ctx.getCacheDir(), "plugin-" + System.nanoTime());
        tmp.mkdirs();
        try {
            ZipInputStream zip = new ZipInputStream(in);
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory() || name.contains("/") || name.contains("\\")) {
                    continue; // только файлы в корне архива
                }
                if (!name.equals("manifest.json") && !name.equals("icon.png") && !name.matches("classes\\d*\\.dex")) {
                    continue;
                }
                FileOutputStream out = new FileOutputStream(new File(tmp, name));
                byte[] buf = new byte[65536];
                int n;
                while ((n = zip.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                out.close();
            }
            File manifest = new File(tmp, "manifest.json");
            if (!manifest.exists()) {
                throw new IllegalArgumentException("в архиве нет manifest.json");
            }
            JSONObject m = new JSONObject(read(manifest));
            String id = m.optString("id");
            if (!id.matches("[a-z0-9][a-z0-9._-]{1,63}")) {
                throw new IllegalArgumentException("id в manifest.json: маленькие латинские буквы, цифры, . _ -");
            }
            if (m.optString("entry").isEmpty()) {
                throw new IllegalArgumentException("в manifest.json нет entry (класс мода)");
            }
            if (!new File(tmp, "classes.dex").exists()) {
                throw new IllegalArgumentException("в архиве нет classes.dex");
            }
            if (m.optInt("api", 1) > API) {
                throw new IllegalArgumentException("мод для более новой версии MargyC (API " + m.optInt("api") + ")");
            }
            File dir = new File(root(ctx), id);
            delete(dir);
            root(ctx).mkdirs();
            if (!tmp.renameTo(dir)) {
                throw new IllegalStateException("не удалось сохранить мод");
            }
            for (File f : dir.listFiles()) {
                if (f.getName().endsWith(".dex")) {
                    f.setReadOnly(); // Android 14+ грузит только неизменяемые dex
                }
            }
            Mods.prefs().edit().remove("plugin_error_" + id).apply();
            return new Info(dir, m);
        } finally {
            delete(tmp);
        }
    }

    static void uninstall(Context ctx, Info info) throws Exception {
        delete(info.dir);
        info.prefs(ctx).edit().clear().apply();
        Mods.prefs().edit().remove("plugin_on_" + info.id).remove("plugin_error_" + info.id).apply();
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                delete(c);
            }
        }
        f.delete();
    }

    private static String read(File f) throws Exception {
        InputStream in = new java.io.FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toString("UTF-8");
    }

    /** При старте Claude: загрузить включённые моды. Ошибка одного мода не мешает остальным. */
    static void load(final Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity a) {
                current = a;
            }

            @Override
            public void onActivityPaused(Activity a) {
                if (current == a) {
                    current = null;
                }
            }

            @Override
            public void onActivityCreated(Activity a, Bundle b) {}

            @Override
            public void onActivityStarted(Activity a) {}

            @Override
            public void onActivityStopped(Activity a) {}

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {}

            @Override
            public void onActivityDestroyed(Activity a) {}
        });
        for (Info info : installed(app)) {
            if (!enabled(info.id)) {
                continue;
            }
            try {
                StringBuilder path = new StringBuilder();
                File[] files = info.dir.listFiles();
                java.util.Arrays.sort(files);
                for (File f : files) {
                    if (f.getName().endsWith(".dex")) {
                        f.setReadOnly();
                        path.append(path.length() == 0 ? "" : File.pathSeparator).append(f.getPath());
                    }
                }
                DexClassLoader loader = new DexClassLoader(path.toString(), app.getCodeCacheDir().getPath(), null,
                        Plugins.class.getClassLoader());
                Object plugin = loader.loadClass(info.entry).getDeclaredConstructor().newInstance();
                ((MargyCPlugin) plugin).onCreate(new Ctx(app, info));
                Fake.log("mod " + info.id + " loaded");
                Mods.prefs().edit().remove("plugin_error_" + info.id).apply();
            } catch (Throwable t) {
                Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ? t.getCause() : t;
                Log.e(Mods.TAG, "мод " + info.id, cause);
                Fake.log("mod " + info.id + " failed: " + cause);
                try {
                    Mods.prefs().edit().putString("plugin_error_" + info.id, String.valueOf(cause)).apply();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Скрытый контекст модов для SendMessage. */
    static List<String> prompts() {
        List<String> out = new ArrayList<String>();
        for (Supplier<String> s : PROMPTS) {
            try {
                String text = s.get();
                if (text != null && !text.trim().isEmpty()) {
                    out.add(text);
                }
            } catch (Throwable t) {
                Log.e(Mods.TAG, "мод: промпт", t);
            }
        }
        return out;
    }

    static String text(String s) {
        for (UnaryOperator<String> f : TEXTS) {
            try {
                String r = f.apply(s);
                if (r != null) {
                    s = r;
                }
            } catch (Throwable ignored) {
            }
        }
        return s;
    }

    static int color(int argb) {
        for (IntUnaryOperator f : COLORS) {
            try {
                argb = f.applyAsInt(argb);
            } catch (Throwable ignored) {
            }
        }
        return argb;
    }

    private static final class Ctx implements PluginContext {
        private final Application app;
        private final Info info;

        Ctx(Application app, Info info) {
            this.app = app;
            this.info = info;
        }

        @Override
        public int apiVersion() {
            return API;
        }

        @Override
        public String margycVersion() {
            return Mods.VERSION;
        }

        @Override
        public Application app() {
            return app;
        }

        @Override
        public String id() {
            return info.id;
        }

        @Override
        public File dir() {
            return info.dir;
        }

        @Override
        public SharedPreferences prefs() {
            return info.prefs(app);
        }

        @Override
        public boolean getBoolean(String key) {
            JSONObject s = info.setting(key);
            return prefs().getBoolean(key, s != null && s.optBoolean("default"));
        }

        @Override
        public String getString(String key) {
            JSONObject s = info.setting(key);
            return prefs().getString(key, s != null ? s.optString("default") : "");
        }

        @Override
        public int getInt(String key) {
            JSONObject s = info.setting(key);
            return prefs().getInt(key, s != null ? s.optInt("default") : 0);
        }

        @Override
        public void log(String message) {
            Fake.log(info.id + ": " + message);
        }

        @Override
        public void addPromptContext(Supplier<String> provider) {
            PROMPTS.add(provider);
        }

        @Override
        public void addTextFilter(UnaryOperator<String> filter) {
            TEXTS.add(filter);
        }

        @Override
        public void addColorFilter(IntUnaryOperator filter) {
            COLORS.add(filter);
        }

        @Override
        public void addActivityCallbacks(Application.ActivityLifecycleCallbacks callbacks) {
            app.registerActivityLifecycleCallbacks(callbacks);
        }

        @Override
        public Activity currentActivity() {
            return current;
        }
    }
}
