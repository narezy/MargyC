package cat.narezany.mods;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Каталог модов: catalog.json в ветке Download репозитория, рядом с файлами .mcmod.
 *
 * <pre>
 * {"mods": [{"id": "hello", "name": "Hello, MargyC", "description": "...", "author": "narezany",
 *            "version": "1.0", "file": "hello.mcmod"}]}
 * </pre>
 */
final class Catalog {
    private Catalog() {}

    static final class Entry {
        final String id, name, description, author, version, file;

        Entry(JSONObject o) {
            id = o.optString("id");
            name = o.optString("name", id);
            description = o.optString("description");
            author = o.optString("author");
            version = o.optString("version");
            file = o.optString("file", id + ".mcmod");
        }
    }

    interface Loaded {
        /** В главном потоке; entries == null — не загрузился, текст ошибки в error. */
        void loaded(List<Entry> entries, String error);
    }

    static void load(final Loaded loaded) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<Entry> list = null;
            String err = null;
            try {
                JSONArray a = new JSONObject(Update.fetch(Update.RAW + "catalog.json?t=" + System.currentTimeMillis()))
                        .optJSONArray("mods");
                list = new ArrayList<Entry>();
                for (int i = 0; a != null && i < a.length(); i++) {
                    Entry e = new Entry(a.getJSONObject(i));
                    if (!e.id.isEmpty()) {
                        list.add(e);
                    }
                }
            } catch (Throwable t) {
                Log.e(Mods.TAG, "catalog", t);
                err = String.valueOf(t.getMessage() != null ? t.getMessage() : t);
            }
            final List<Entry> result = list;
            final String e = err;
            main.post(() -> loaded.loaded(result, e));
        }, "MargyC catalog").start();
    }

    interface Installed {
        /** В главном потоке; info == null — не вышло, текст ошибки в error. */
        void installed(Plugins.Info info, String error);
    }

    static void install(final Activity a, final Entry entry, final Installed done) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Plugins.Info info = null;
            String err = null;
            try {
                byte[] bytes = Update.download(Update.RAW + entry.file);
                info = Plugins.install(a, new java.io.ByteArrayInputStream(bytes));
                Plugins.setEnabled(info.id, true);
            } catch (Throwable t) {
                Log.e(Mods.TAG, "catalog install", t);
                err = String.valueOf(t.getMessage() != null ? t.getMessage() : t);
            }
            final Plugins.Info result = info;
            final String e = err;
            main.post(() -> done.installed(result, e));
        }, "MargyC catalog install").start();
    }

    /** Установленная версия мода или null. */
    static Plugins.Info installed(Activity a, String id) {
        for (Plugins.Info info : Plugins.installed(a)) {
            if (info.id.equals(id)) {
                return info;
            }
        }
        return null;
    }
}
