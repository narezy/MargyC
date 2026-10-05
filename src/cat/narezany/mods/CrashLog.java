package cat.narezany.mods;

import android.app.Application;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Ловит вылеты: пишет стектрейс в files/last_crash.txt и в Загрузки/ClaudeMods,
 * а при следующем запуске открывает CrashActivity с текстом и кнопкой «Скопировать».
 * Ставится первой строчкой ClaudeApplication.onCreate (smali-патч).
 */
public final class CrashLog {
    static final String FILE = "last_crash.txt";
    static final String SEEN = "last_crash.seen";

    private CrashLog() {}

    public static void install(final Application app) {
        try {
            final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable error) {
                    try {
                        save(app, thread, error);
                    } catch (Throwable ignored) {
                    }
                    try {
                        StackTraceElement[] st = error.getStackTrace();
                        Journal.crash("CRASH in " + thread.getName() + ": " + error
                                + (st.length > 0 ? " at " + st[0] : ""));
                    } catch (Throwable ignored) {
                    }
                    if (previous != null) {
                        previous.uncaughtException(thread, error);
                    }
                }
            });
            File crash = new File(app.getFilesDir(), FILE);
            File seen = new File(app.getFilesDir(), SEEN);
            if (crash.exists() && !seen.exists()) {
                new FileOutputStream(seen).close();
                Intent intent = new Intent(app, CrashActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                app.startActivity(intent);
            }
        } catch (Throwable t) {
            Log.e(Mods.TAG, "CrashLog.install", t);
        }
    }

    private static void save(Application app, Thread thread, Throwable error) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        pw.println("Claude Mods crash " + time);
        pw.println("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), "
                + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println("thread: " + thread.getName());
        pw.println();
        error.printStackTrace(pw);
        pw.flush();
        byte[] text = sw.toString().getBytes("UTF-8");

        FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), FILE));
        out.write(text);
        out.close();
        new File(app.getFilesDir(), SEEN).delete();

        // копия в общие Загрузки, разрешения на Android 10+ не нужны
        ContentValues values = new ContentValues();
        values.put("_display_name", "crash_" + time.replace(':', '-').replace(' ', '_') + ".txt");
        values.put("mime_type", "text/plain");
        values.put("relative_path", "Download/ClaudeMods");
        Uri uri = app.getContentResolver().insert(Uri.parse("content://media/external/downloads"), values);
        if (uri != null) {
            OutputStream os = app.getContentResolver().openOutputStream(uri);
            os.write(text);
            os.close();
        }
    }
}
