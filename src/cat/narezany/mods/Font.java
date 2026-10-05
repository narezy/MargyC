package cat.narezany.mods;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.fonts.FontVariationAxis;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Мод «Шрифт»: свой шрифт во всём Claude. Шрифты приложения (Anthropic Sans, Anthropic Serif, Noto Serif)
 * лежат в assets и создаются через Typeface.Builder(assets, путь) с осью wght. Патчер заменяет вызовы
 * Builder на эти обёртки: они запоминают путь, жирность и курсив, а build() отдаёт выбранный шрифт с той же
 * жирностью. Моноширинный шрифт кода, значки и математика остаются как есть.
 */
public final class Font {
    static final String NONE = "", SANS = "sans", SERIF = "serif", APP_SERIF = "app_serif", MONO = "mono",
            FILE = "file";

    private static final Map<Object, Info> INFO = Collections.synchronizedMap(new WeakHashMap<Object, Info>());
    private static volatile String choice;
    private static Typeface file;
    private static String appSerif;

    private Font() {}

    private static final class Info {
        String path;
        int weight = -1;
        Boolean italic;
    }

    private static Info info(Object b) {
        Info i = INFO.get(b);
        if (i == null) {
            i = new Info();
            INFO.put(b, i);
        }
        return i;
    }

    // ---- хуки: вызовы Typeface.Builder в коде Claude ----

    public static void asset(Typeface.Builder b, String path) {
        try {
            info(b).path = path;
            if (path != null && path.endsWith("/anthropic_serif.ttf")) {
                appSerif = path;
            }
        } catch (Throwable ignored) {
        }
    }

    public static Typeface.Builder axes(Typeface.Builder b, FontVariationAxis[] axes) {
        try {
            for (FontVariationAxis a : axes != null ? axes : new FontVariationAxis[0]) {
                if ("wght".equals(a.getTag())) {
                    info(b).weight = Math.round(a.getStyleValue());
                }
            }
        } catch (Throwable ignored) {
        }
        return b.setFontVariationSettings(axes);
    }

    public static Typeface.Builder weight(Typeface.Builder b, int weight) {
        try {
            info(b).weight = weight;
        } catch (Throwable ignored) {
        }
        return b.setWeight(weight);
    }

    public static Typeface.Builder italic(Typeface.Builder b, boolean italic) {
        try {
            info(b).italic = italic;
        } catch (Throwable ignored) {
        }
        return b.setItalic(italic);
    }

    public static Typeface build(Typeface.Builder b) {
        Typeface original = b.build();
        Info i = INFO.remove(b);
        try {
            if (i == null || i.path == null || !replaceable(i.path)) {
                return original;
            }
            int weight = i.weight > 0 ? i.weight : original != null ? original.getWeight() : 400;
            boolean italic = i.italic != null ? i.italic : i.path.contains("italic");
            Typeface t = typeface(weight, italic);
            if (logged++ < 12) {
                Fake.log("font: " + i.path.substring(i.path.lastIndexOf('/') + 1) + " " + weight + " -> "
                        + (t != null ? choice() : "as in Claude"));
            }
            return t != null ? t : original;
        } catch (Throwable t) {
            Log.e(Mods.TAG, "font", t);
            return original;
        }
    }

    // ---- основной путь: Anthropic Sans / Serif из ByteBuffer через Typeface.CustomFallbackBuilder ----

    private static final Map<Object, int[]> STYLE = Collections.synchronizedMap(new WeakHashMap<Object, int[]>());
    private static int logged;

    public static Typeface.CustomFallbackBuilder style(Typeface.CustomFallbackBuilder b, android.graphics.fonts.FontStyle st) {
        try {
            STYLE.put(b, new int[] {st.getWeight(), st.getSlant()});
        } catch (Throwable ignored) {
        }
        return b.setStyle(st);
    }

    public static Typeface fallback(Typeface.CustomFallbackBuilder b) {
        Typeface original = b.build();
        int[] st = STYLE.remove(b);
        try {
            int weight = st != null ? st[0] : original.getWeight();
            boolean italic = st != null ? st[1] == android.graphics.fonts.FontStyle.FONT_SLANT_ITALIC : original.isItalic();
            Typeface t = typeface(weight, italic);
            if (logged++ < 12) {
                Fake.log("font: Claude font " + weight + (italic ? " italic" : "") + " -> "
                        + (t != null ? choice() : "as in Claude"));
            }
            return t != null ? t : original;
        } catch (Throwable t) {
            Fake.log("font error: " + t);
            return original;
        }
    }

    /** Текстовые шрифты Claude; код (jetbrains_mono), значки и математика — нет. */
    private static boolean replaceable(String path) {
        String p = path.toLowerCase(java.util.Locale.ROOT);
        return p.contains("/font/") && !p.contains("mono") && !p.contains("icon") && !p.contains("symbol")
                && !p.contains("math");
    }

    // ---- выбор ----

    static String choice() {
        if (choice == null) {
            try {
                choice = Mods.prefs().getString("font", NONE);
            } catch (Exception e) {
                return NONE;
            }
        }
        return choice;
    }

    static void setChoice(String c) throws Exception {
        Mods.prefs().edit().putString("font", c).apply();
        choice = c;
    }

    /** Выбранный шрифт с этой жирностью или null, если шрифт как в Claude. */
    static Typeface typeface(int weight, boolean italic) {
        Typeface base;
        switch (choice()) {
            case SANS:
                base = Typeface.SANS_SERIF;
                break;
            case SERIF:
                base = Typeface.SERIF;
                break;
            case MONO:
                base = Typeface.MONOSPACE;
                break;
            case APP_SERIF:
                return appSerif(weight, italic);
            case FILE:
                base = file();
                break;
            default:
                return null;
        }
        if (base == null) {
            return null;
        }
        return Typeface.create(base, Math.max(1, Math.min(1000, weight)), italic);
    }

    /** Anthropic Serif приложения (шрифт ответов Claude) для всего интерфейса. */
    private static Typeface appSerif(int weight, boolean italic) {
        try {
            String path = appSerif != null ? appSerif : "composeResources/claude.theme.generated.resources/font/anthropic_serif.ttf";
            if (italic) {
                path = path.replace("anthropic_serif.ttf", "anthropic_serif_italic.ttf");
            }
            return new Typeface.Builder(Mods.app().getAssets(), path).setFontVariationSettings("'wght' " + weight).build();
        } catch (Throwable t) {
            return null;
        }
    }

    static File fontFile(Context ctx) {
        return new File(ctx.getFilesDir(), "margyc_font");
    }

    private static synchronized Typeface file() {
        if (file == null) {
            try {
                File f = fontFile(Mods.app());
                if (f.exists()) {
                    file = Typeface.createFromFile(f);
                }
            } catch (Throwable t) {
                Log.e(Mods.TAG, "font file", t);
            }
        }
        return file;
    }

    /** Свой файл .ttf/.otf: копируется в данные приложения. Бросает исключение, если это не шрифт. */
    static void install(Context ctx, InputStream in) throws Exception {
        File tmp = new File(ctx.getCacheDir(), "margyc_font.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        try {
            new android.graphics.fonts.Font.Builder(tmp).build(); // не шрифт — IOException
        } catch (Exception e) {
            tmp.delete();
            throw new IllegalArgumentException(L.t("это не шрифт TTF или OTF"));
        }
        File dst = fontFile(ctx);
        if (!tmp.renameTo(dst)) {
            throw new java.io.IOException("rename");
        }
        synchronized (Font.class) {
            file = null;
        }
    }
}
