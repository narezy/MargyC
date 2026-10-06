package cat.narezany.mods;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Обои в чате. Фон экранов Claude (цвет 151515 тёмной темы / F9F9F7 светлой) при включённых обоях мод
 * делает прозрачным через {@link Theme} (цвет фона читается оттуда), а под Compose-контент кладёт картинку.
 * Карточки сообщений и поле ввода (другие цвета палитры) остаются непрозрачными — получается как в Telegram.
 * Картинка — свой файл или один из встроенных градиентных пресетов.
 */
public final class Wallpaper {
    static final int BG_DARK = 0xFF151515, BG_LIGHT = 0xFFF9F9F7;
    private static final String TAG_LAYER = "margyc-wallpaper";

    private static volatile Boolean on;
    private static Bitmap bitmap;
    private static String bitmapKey;

    private Wallpaper() {}

    static boolean enabled() {
        if (on == null) {
            try {
                on = Mods.prefs().getBoolean("wallpaper_on", false);
            } catch (Exception e) {
                return false;
            }
        }
        return on;
    }

    static void setEnabled(boolean value) throws Exception {
        Mods.prefs().edit().putBoolean("wallpaper_on", value).commit();
        on = value;
        Theme.reload();
    }

    static int dim() {
        try {
            return Mods.prefs().getInt("wallpaper_dim", 55); // 0..100 %
        } catch (Exception e) {
            return 55;
        }
    }

    static void setDim(int percent) throws Exception {
        Mods.prefs().edit().putInt("wallpaper_dim", Math.max(0, Math.min(100, percent))).apply();
        clearCache();
    }

    static int blur() {
        try {
            return Mods.prefs().getInt("wallpaper_blur", 0); // 0..25 dp
        } catch (Exception e) {
            return 0;
        }
    }

    static void setBlur(int dp) throws Exception {
        Mods.prefs().edit().putInt("wallpaper_blur", Math.max(0, Math.min(25, dp))).apply();
    }

    /** Выбранное: preset:<id> или file. */
    static String choice() {
        try {
            return Mods.prefs().getString("wallpaper", "");
        } catch (Exception e) {
            return "";
        }
    }

    static void setChoice(String c) throws Exception {
        Mods.prefs().edit().putString("wallpaper", c).apply();
        clearCache();
    }

    private static synchronized void clearCache() {
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
        bitmapKey = null;
    }

    static final class Preset {
        final String id, name;
        final int[] colors;
        final GradientDrawable.Orientation orientation;

        Preset(String id, String name, int[] colors, GradientDrawable.Orientation orientation) {
            this.id = id;
            this.name = name;
            this.colors = colors;
            this.orientation = orientation;
        }
    }

    static Preset[] presets() {
        return new Preset[] {
            new Preset("dusk", L.t("Сумерки"), new int[] {0xFF2B1055, 0xFF7597DE}, GradientDrawable.Orientation.TOP_BOTTOM),
            new Preset("clay", L.t("Глина"), new int[] {0xFF3A1C0E, 0xFFD97757}, GradientDrawable.Orientation.TL_BR),
            new Preset("forest", L.t("Лес"), new int[] {0xFF0B2818, 0xFF1C5B3A, 0xFF8FD2B1}, GradientDrawable.Orientation.BL_TR),
            new Preset("night", L.t("Ночь"), new int[] {0xFF000000, 0xFF101828, 0xFF1E3A5F}, GradientDrawable.Orientation.TOP_BOTTOM),
            new Preset("peach", L.t("Персик"), new int[] {0xFFFFE6D5, 0xFFFFC2A8, 0xFFFF9E80}, GradientDrawable.Orientation.TL_BR),
            new Preset("mint", L.t("Мята"), new int[] {0xFFE8FBF2, 0xFFB6ECD6, 0xFF8FD2B1}, GradientDrawable.Orientation.TOP_BOTTOM),
            new Preset("grape", L.t("Виноград"), new int[] {0xFF1B0A2E, 0xFF4A2574, 0xFF8E54C9}, GradientDrawable.Orientation.BL_TR),
            new Preset("mono", L.t("Графит"), new int[] {0xFF0D0D0D, 0xFF2A2A2A}, GradientDrawable.Orientation.TOP_BOTTOM),
        };
    }

    static Preset preset(String id) {
        for (Preset p : presets()) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "margyc_wallpaper");
    }

    /** Свой файл картинки: копируется в данные приложения, уменьшается под экран. */
    static void install(Context ctx, InputStream in) throws Exception {
        File tmp = new File(ctx.getCacheDir(), "wp.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(tmp.getPath(), o);
        if (o.outWidth <= 0) {
            tmp.delete();
            throw new IllegalArgumentException(L.t("это не картинка"));
        }
        if (!tmp.renameTo(file(ctx))) {
            throw new java.io.IOException("rename");
        }
        clearCache();
    }

    // ---- рисование ----

    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity a) {
                if (a.getClass().getName().startsWith("cat.narezany.mods.")) {
                    return;
                }
                try {
                    apply(a);
                } catch (Throwable t) {
                    Log.e(Mods.TAG, "wallpaper", t);
                }
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

    private static void apply(Activity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        View existing = content.findViewWithTag(TAG_LAYER);
        if (!enabled() || choice().isEmpty()) {
            if (existing != null) {
                content.removeView(existing);
            }
            return;
        }
        if (existing == null) {
            ImageView iv = new ImageView(a);
            iv.setTag(TAG_LAYER);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(drawable(a));
            content.addView(iv, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            // окно под обои: цвет окна у Claude тоже фон темы, его тоже делаем прозрачным
            a.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        } else {
            content.removeView(existing);
            content.addView(existing, 0);
            ((ImageView) existing).setImageDrawable(drawable(a));
        }
    }

    private static Drawable drawable(Context ctx) {
        boolean night = (ctx.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        String choice = choice();
        Drawable base;
        if (choice.startsWith("preset:")) {
            Preset p = preset(choice.substring(7));
            if (p == null) {
                return new android.graphics.drawable.ColorDrawable(night ? BG_DARK : BG_LIGHT);
            }
            GradientDrawable g = new GradientDrawable(p.orientation, p.colors);
            base = g;
        } else {
            Bitmap bmp = bitmap(ctx);
            if (bmp == null) {
                return new android.graphics.drawable.ColorDrawable(night ? BG_DARK : BG_LIGHT);
            }
            BitmapDrawable bd = new BitmapDrawable(ctx.getResources(), bmp);
            if (blur() > 0 && Build.VERSION.SDK_INT >= 31) {
                // лёгкое размытие картинки
            }
            base = bd;
        }
        // затемнение: тёмная вуаль поверх (или светлая для светлой темы)
        int d = dim();
        if (d <= 0) {
            return base;
        }
        int veil = (Math.round(d * 2.55f) << 24) | (night ? 0x000000 : 0xFFFFFF);
        return new android.graphics.drawable.LayerDrawable(new Drawable[] {base,
                new android.graphics.drawable.ColorDrawable(veil)});
    }

    private static synchronized Bitmap bitmap(Context ctx) {
        if (bitmap != null && "file".equals(bitmapKey)) {
            return bitmap;
        }
        try {
            File f = file(ctx);
            if (!f.exists()) {
                return null;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int screen = Math.max(ctx.getResources().getDisplayMetrics().widthPixels,
                    ctx.getResources().getDisplayMetrics().heightPixels);
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / sample > screen * 1.5) {
                sample *= 2;
            }
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            bitmap = BitmapFactory.decodeFile(f.getPath(), d);
            if (bitmap != null && blur() > 0) {
                bitmap = blurred(bitmap, blur());
            }
            bitmapKey = "file";
            return bitmap;
        } catch (Throwable t) {
            Log.e(Mods.TAG, "wallpaper bitmap", t);
            return null;
        }
    }

    /** Простое размытие уменьшением-увеличением: без RenderScript, работает на всех версиях. */
    private static Bitmap blurred(Bitmap src, int radius) {
        try {
            int w = Math.max(1, src.getWidth() / (radius + 1));
            int h = Math.max(1, src.getHeight() / (radius + 1));
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            Bitmap out = Bitmap.createScaledBitmap(small, src.getWidth(), src.getHeight(), true);
            small.recycle();
            if (out != src) {
                src.recycle();
            }
            return out;
        } catch (Throwable t) {
            return src;
        }
    }

    /** Превью пресета для галереи. */
    static Drawable previewDrawable(Preset p) {
        GradientDrawable g = new GradientDrawable(p.orientation, p.colors);
        g.setCornerRadius(0);
        return g;
    }
}
