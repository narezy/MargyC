package cat.narezany.mods;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Виджет Clawd на рабочий стол: приветствие по времени суток и сколько сообщений сегодня. Clawd —
 * кадр из анимаций самого приложения: ночью спит, днём за ноутбуком, вечером с гитарой. Коснись Clawd —
 * подпрыгнет, коснись остального — откроется Claude.
 */
public final class Widget extends AppWidgetProvider {
    private static final String POKE = "cat.narezany.mods.widget.POKE";
    private static final int SCALE = 4; // пиксели Clawd без размытия: увеличиваем сами, ближайшим соседом
    private static boolean playing;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        Fake.log("widget: update " + ids.length);
        update(ctx, mgr, ids, null);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (POKE.equals(intent.getAction())) {
            jump(ctx.getApplicationContext(), goAsync());
        }
    }

    /** Обновить все виджеты: после нового сообщения, смены имени или языка. */
    static void refresh(Context ctx) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, Widget.class));
            if (ids.length > 0 && !playing) {
                update(ctx, mgr, ids, null);
            }
        } catch (Throwable ignored) {
        }
    }

    private static int id(Context ctx, String type, String name) {
        int id = Mods.res(ctx, name, type);
        if (id == 0) {
            Fake.log("widget: no resource " + type + "/" + name);
        }
        return id;
    }

    private static void update(Context ctx, AppWidgetManager mgr, int[] ids, Bitmap frame) {
        try {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), id(ctx, "layout", "margyc_widget"));
            int clawd = id(ctx, "id", "margyc_widget_clawd");
            Bitmap b = frame != null ? frame : pose();
            if (b != null) {
                rv.setImageViewBitmap(clawd, b);
            }
            rv.setTextViewText(id(ctx, "id", "margyc_widget_title"), title());
            rv.setTextViewText(id(ctx, "id", "margyc_widget_sub"), subtitle());
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            if (launch != null) {
                rv.setOnClickPendingIntent(id(ctx, "id", "margyc_widget_root"), PendingIntent.getActivity(ctx, 0,
                        launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            }
            Intent poke = new Intent(POKE).setClass(ctx, Widget.class);
            rv.setOnClickPendingIntent(clawd, PendingIntent.getBroadcast(ctx, 1, poke,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            mgr.updateAppWidget(ids, rv);
        } catch (Throwable t) {
            Log.e(Mods.TAG, "widget", t);
            Fake.log("widget error: " + t);
        }
    }

    /** Поза по времени суток: последний кадр «засыпания» ночью, ноутбук днём, гитара вечером. */
    private static Bitmap pose() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String anim = h >= 23 || h < 6 ? "Sleep" : h >= 18 ? "Guitar" : h < 9 ? "WakeUp" : "Laptop";
        try {
            List<Bitmap> frames = Pet.sheet(anim);
            if (frames.isEmpty()) {
                return null;
            }
            Bitmap f = frames.get("WakeUp".equals(anim) ? frames.size() - 1 : frames.size() / 2);
            return scaled(f);
        } catch (Throwable t) {
            Fake.log("widget: no Clawd frames: " + t);
            return null;
        }
    }

    private static Bitmap scaled(Bitmap f) {
        return Bitmap.createScaledBitmap(f, f.getWidth() * SCALE, f.getHeight() * SCALE, false);
    }

    private static String title() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String hi = h >= 5 && h < 12 ? L.t("Доброе утро") : h >= 12 && h < 17 ? L.t("Добрый день")
                : h >= 17 && h < 23 ? L.t("Добрый вечер") : L.t("Доброй ночи");
        String name = Streamer.enabled() ? "" : Streamer.firstName();
        return name.isEmpty() ? hi : hi + ", " + name;
    }

    private static String subtitle() {
        try {
            JSONObject days = Stats.load().optJSONObject("days");
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            int n = days != null ? days.optInt(today) : 0;
            Stats.Summary s = Stats.summary();
            if (n == 0) {
                return s.streak > 0 ? L.t("Серия: ") + s.streak + L.t(" дн. подряд. Напиши, чтобы не прервать")
                        : L.t("Коснись, чтобы написать Claude");
            }
            String line = L.t("Сегодня сообщений: ") + n;
            return s.streak > 1 ? line + " · " + L.t("серия ") + s.streak + L.t(" дн.") : line;
        } catch (Throwable t) {
            return L.t("Коснись, чтобы написать Claude");
        }
    }

    /** Прыжок: кадры «Jumping» по одному, около 12 в секунду, потом обратно в позу. */
    private static void jump(final Context ctx, final PendingResult result) {
        final AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        final int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, Widget.class));
        final List<Bitmap> frames;
        try {
            frames = Pet.sheet("Jumping");
        } catch (Throwable t) {
            result.finish();
            return;
        }
        if (playing || frames.isEmpty() || ids.length == 0) {
            result.finish();
            return;
        }
        playing = true;
        final Handler h = new Handler(Looper.getMainLooper());
        final int last = Math.min(20, frames.size() - 1);
        final int clawd = id(ctx, "id", "margyc_widget_clawd");
        for (int i = 0; i <= last; i++) {
            final int k = i;
            h.postDelayed(() -> {
                try {
                    RemoteViews rv = new RemoteViews(ctx.getPackageName(), id(ctx, "layout", "margyc_widget"));
                    rv.setImageViewBitmap(clawd, scaled(frames.get(k)));
                    mgr.partiallyUpdateAppWidget(ids, rv);
                } catch (Throwable ignored) {
                }
            }, i * 83L);
        }
        h.postDelayed(() -> {
            playing = false;
            update(ctx, mgr, ids, null);
            result.finish();
        }, (last + 1) * 83L + 200);
    }
}
