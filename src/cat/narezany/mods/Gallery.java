package cat.narezany.mods;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Галерея готовых тем. Тема задаёт ряд цветов от тёмного к светлому для каждой из тем Claude, а серые
 * цвета оригинальной палитры (Names.DARK_PALETTE / LIGHT_PALETTE) раскладываются по этому ряду по своей
 * яркости. Так тема не зависит от точных цветов конкретной версии Claude.
 */
final class Gallery {
    /** Яркость (HSL L) опорных серых тёмной темы Claude: глубже фона, фон, карточки, приподнятые, линии,
     *  приглушённые, второстепенный текст, заголовки разделов, текст, белый. */
    private static final float[] DARK_L = {0.04f, 0.082f, 0.125f, 0.17f, 0.215f, 0.37f, 0.57f, 0.74f, 0.97f, 1f};
    /** То же для светлой: почти чёрный, текст, тёмные, второстепенный, приглушённые, заголовки, линии,
     *  приподнятые, фон, белый. */
    private static final float[] LIGHT_L = {0.04f, 0.115f, 0.21f, 0.41f, 0.62f, 0.74f, 0.87f, 0.93f, 0.97f, 1f};

    static final class Preset {
        final String name, description;
        final Integer accent;
        final int[] dark, light; // null — эту тему Claude не трогать

        Preset(String name, String description, Integer accent, int[] dark, int[] light) {
            this.name = name;
            this.description = description;
            this.accent = accent;
            this.dark = dark;
            this.light = light;
        }

        /** Цвета для превью: фон, карточка, текст, акцент. */
        int[] preview(boolean night) {
            int[] ramp = night && dark != null ? dark : light != null ? light : dark;
            boolean d = ramp == dark;
            int a = accent != null ? accent : 0xFFD97757;
            return d ? new int[] {ramp[1], ramp[2], ramp[8], a} : new int[] {ramp[8], ramp[9], ramp[1], a};
        }
    }

    private Gallery() {}

    static List<Preset> all(Context ctx) {
        List<Preset> list = new ArrayList<Preset>();
        list.add(new Preset("AMOLED", L.t("Чистый чёрный фон для OLED-экранов. Светлая тема без изменений."), null,
                new int[] {0xFF000000, 0xFF000000, 0xFF0E0E0E, 0xFF191919, 0xFF262626, 0xFF4A4A4A, 0xFF8E8E8E,
                        0xFFBDBDBD, 0xFFF2F2F2, 0xFFFFFFFF}, null));
        Preset you = materialYou(ctx);
        if (you != null) {
            list.add(you);
        }
        list.add(new Preset("Catppuccin", L.t("Mocha в тёмной теме и Latte в светлой, акцент mauve."), 0xFFCBA6F7,
                new int[] {0xFF11111B, 0xFF1E1E2E, 0xFF262637, 0xFF313244, 0xFF45475A, 0xFF6C7086, 0xFF9399B2,
                        0xFFBAC2DE, 0xFFCDD6F4, 0xFFE8ECFF},
                new int[] {0xFF11111B, 0xFF4C4F69, 0xFF5C5F77, 0xFF6C6F85, 0xFF8C8FA1, 0xFF9CA0B0, 0xFFCCD0DA,
                        0xFFDCE0E8, 0xFFEFF1F5, 0xFFFFFFFF}));
        list.add(new Preset("Nord", L.t("Холодные северные цвета, акцент frost."), 0xFF88C0D0,
                new int[] {0xFF242933, 0xFF2E3440, 0xFF3B4252, 0xFF434C5E, 0xFF4C566A, 0xFF616E88, 0xFF8C97AD,
                        0xFFD8DEE9, 0xFFECEFF4, 0xFFFFFFFF},
                new int[] {0xFF1F232B, 0xFF2E3440, 0xFF3B4252, 0xFF4C566A, 0xFF7B88A1, 0xFF9AA5BA, 0xFFD8DEE9,
                        0xFFE5E9F0, 0xFFECEFF4, 0xFFF8F9FB}));
        list.add(new Preset("Dracula", L.t("Тёмная фиолетовая классика. Светлая тема без изменений."), 0xFFBD93F9,
                new int[] {0xFF191A21, 0xFF282A36, 0xFF2F3241, 0xFF383A4A, 0xFF44475A, 0xFF6272A4, 0xFF9AA1C4,
                        0xFFD6D7EA, 0xFFF8F8F2, 0xFFFFFFFF}, null));
        list.add(new Preset("Gruvbox", L.t("Тёплые ретро-цвета, акцент оранжевый."), 0xFFFE8019,
                new int[] {0xFF1D2021, 0xFF282828, 0xFF32302F, 0xFF3C3836, 0xFF504945, 0xFF665C54, 0xFFA89984,
                        0xFFD5C4A1, 0xFFEBDBB2, 0xFFFBF1C7},
                new int[] {0xFF1D2021, 0xFF3C3836, 0xFF504945, 0xFF7C6F64, 0xFF928374, 0xFFA89984, 0xFFD5C4A1,
                        0xFFEBDBB2, 0xFFFBF1C7, 0xFFFFFBEA}));
        list.add(new Preset("Tokyo Night", L.t("Ночной Токио: тёмно-синий и неон. Светлая — Tokyo Day."), 0xFF7AA2F7,
                new int[] {0xFF16161E, 0xFF1A1B26, 0xFF1F2335, 0xFF24283B, 0xFF292E42, 0xFF565F89, 0xFF828BB8,
                        0xFFA9B1D6, 0xFFC0CAF5, 0xFFE4E8FF},
                new int[] {0xFF1A1B26, 0xFF3760BF, 0xFF4C5A8C, 0xFF6172B0, 0xFF8990B3, 0xFFA1A6C5, 0xFFC4C8DA,
                        0xFFD0D5E3, 0xFFE1E2E7, 0xFFF2F3F7}));
        list.add(new Preset(L.t("Сепия"), L.t("Тёплая бумага и чернила, мягко для глаз."), 0xFFB5651D,
                new int[] {0xFF15110C, 0xFF1C1712, 0xFF241E17, 0xFF2E261D, 0xFF3A3025, 0xFF5E5040, 0xFF9C8A72,
                        0xFFC7B699, 0xFFEADBC3, 0xFFF6EEDF},
                new int[] {0xFF1E1912, 0xFF3B3226, 0xFF4F4434, 0xFF75664F, 0xFF9C8B70, 0xFFB3A285, 0xFFDCCDB0,
                        0xFFE9DDC5, 0xFFF4ECD8, 0xFFFBF6EA}));
        return list;
    }

    /** Material You (Android 12+): серые и акцент из обоев телефона. */
    private static Preset materialYou(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) {
            return null;
        }
        try {
            int[] n = new int[13];
            int[] tones = {1000, 900, 800, 700, 600, 500, 400, 300, 200, 100, 50, 10, 0};
            for (int i = 0; i < tones.length; i++) {
                n[i] = sys(ctx, "neutral1", tones[i]);
            }
            int deep = blend(n[0], n[1], 0.5f);
            return new Preset("Material You", L.t("Цвета из обоев телефона, как в системе."), sys(ctx, "accent1", 300),
                    new int[] {deep, n[1], blend(n[1], n[2], 0.5f), n[2], n[3], n[4], n[6], n[8], n[10], n[12]},
                    new int[] {n[0], n[1], n[2], n[4], n[5], n[7], n[9], n[10], n[11], n[12]});
        } catch (Throwable t) {
            return null;
        }
    }

    private static int sys(Context ctx, String palette, int tone) {
        int id = ctx.getResources().getIdentifier("system_" + palette + "_" + tone, "color", "android");
        if (id == 0) {
            throw new IllegalStateException(palette + tone);
        }
        return ctx.getColor(id);
    }

    /** Применить: акцент (если есть), замены серых в обеих темах, своя тема включается. */
    static void apply(Preset p) throws Exception {
        Fake.log("theme gallery: " + p.name);
        Theme.setOverrides(true, p.dark != null ? map(Names.DARK_PALETTE, DARK_L, p.dark) : new HashMap<Integer, Integer>());
        Theme.setOverrides(false, p.light != null ? map(Names.LIGHT_PALETTE, LIGHT_L, p.light) : new HashMap<Integer, Integer>());
        Theme.setCustom(true);
        if (p.accent != null) {
            Theme.setAccent(true, p.accent);
        }
    }

    /** Обычные цвета Claude: своя тема и акцент выключаются. */
    static void reset() throws Exception {
        Theme.setCustom(false);
        Theme.setAccent(false, Theme.accent());
    }

    private static Map<Integer, Integer> map(int[] palette, float[] stops, int[] ramp) {
        Map<Integer, Integer> out = new HashMap<Integer, Integer>();
        for (int c : palette) {
            float[] hsl = Theme.hsl(c);
            if (hsl[1] > 0.12f && hsl[2] > 0.05f && hsl[2] < 0.97f) {
                continue; // цветной: статусы, ссылки, акцент — их не трогаем
            }
            out.put(c, along(hsl[2], stops, ramp) | (c & 0xFF000000));
        }
        return out;
    }

    /** Цвет ряда для яркости l: между соседними опорными точками — линейно. */
    private static int along(float l, float[] stops, int[] ramp) {
        if (l <= stops[0]) {
            return ramp[0] & 0xFFFFFF;
        }
        for (int i = 1; i < stops.length; i++) {
            if (l <= stops[i]) {
                float t = (l - stops[i - 1]) / (stops[i] - stops[i - 1]);
                return blend(ramp[i - 1], ramp[i], t) & 0xFFFFFF;
            }
        }
        return ramp[ramp.length - 1] & 0xFFFFFF;
    }

    private static int blend(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
