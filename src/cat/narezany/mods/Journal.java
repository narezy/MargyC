package cat.narezany.mods;

import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Журнал MargyC: что делали хуки и моды. Последние строки хранятся и в файле, так что журнал переживает
 * перезапуск и падение Claude. На экране модов его можно скопировать и отправить автору или очистить.
 */
final class Journal {
    private static final int MAX = 600;
    private static final ArrayDeque<String> LINES = new ArrayDeque<String>();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MargyC journal");
        t.setDaemon(true);
        return t;
    });
    private static boolean loaded;
    private static boolean pending;

    private Journal() {}

    static synchronized void log(String line) {
        load();
        String t = new java.text.SimpleDateFormat("dd.MM HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
        LINES.addLast(t + " " + line);
        while (LINES.size() > MAX) {
            LINES.removeFirst();
        }
        if (!pending) { // сохраняем пачкой: хуки пишут часто
            pending = true;
            WRITER.execute(() -> {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                }
                String text;
                synchronized (Journal.class) {
                    pending = false;
                    text = text();
                }
                save(text);
            });
        }
    }

    /** Падение: строка и сразу на диск, отложенная запись уже не успеет. */
    static void crash(String line) {
        String text;
        synchronized (Journal.class) {
            log(line);
            text = text();
        }
        save(text);
    }

    static synchronized String text() {
        load();
        StringBuilder sb = new StringBuilder();
        for (String line : LINES) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** Для отправки автору: версия, устройство, затем журнал. */
    static String report() {
        String text = text();
        return "MargyC " + Mods.label() + ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), "
                + Build.MANUFACTURER + " " + Build.MODEL + "\n\n" + (text.isEmpty() ? L.t("Пока пусто.") : text);
    }

    static synchronized void clear() {
        LINES.clear();
        WRITER.execute(() -> save(""));
    }

    private static File file() throws Exception {
        return new File(Mods.app().getFilesDir(), "margyc_journal.txt");
    }

    /** Строки прошлых запусков из файла. */
    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            File f = file();
            if (f.exists()) {
                byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
                for (String line : new String(b, StandardCharsets.UTF_8).split("\n")) {
                    if (!line.isEmpty()) {
                        LINES.addLast(line);
                    }
                }
                while (LINES.size() > MAX) {
                    LINES.removeFirst();
                }
            }
        } catch (Throwable t) {
            loaded = false; // приложение ещё не создано: прочитаем позже
        }
    }

    private static void save(String text) {
        try {
            File f = file();
            File tmp = new File(f.getPath() + ".tmp");
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                w.write(text);
            }
            if (!tmp.renameTo(f)) {
                tmp.delete();
            }
        } catch (Throwable t) {
            Log.e(Mods.TAG, "journal", t);
        }
    }
}
