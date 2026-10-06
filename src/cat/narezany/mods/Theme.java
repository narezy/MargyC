package cat.narezany.mods;

import android.content.SharedPreferences;
import android.graphics.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * Акцентный цвет и свои темы.
 *
 * Все цвета Claude создаются через Color(Long) из ARGB-константы. Есть тональная палитра
 * (ряды оттенков серого, синего, красного...), а тёмная и светлая темы — два класса, которые
 * при первом обращении берут из неё цвета. Патчер ставит хуки:
 *  - в Color(Long): {@link #color(long)} — замена фирменного оранжевого и синего на акцент;
 *  - в темы, на каждое чтение цвета палитры: {@link #dark(long)} / {@link #light(long)}.
 * Цвета читаются один раз при старте, поэтому после изменений приложение перезапускается.
 */
public final class Theme {
    /** Оранжевый Claude (clay) и его оттенки, включая тень Clawd. Первый — опорный. */
    static final int[] ORANGE = {0xFFD97757, 0xFFC6613F, 0xFFCC785C, 0xFFC96442, 0xFFBE684D};
    /** Синий ряд палитры (выбор, переключатели, ссылки) и старые синие. Первый — опорный. */
    static final int[] BLUE = {0xFF6DA7EC, 0xFFFAFCFF, 0xFFE7F1FB, 0xFFCDE2FB, 0xFF9EC5F4, 0xFF86B6EF,
            0xFF5598E7, 0xFF3987E5, 0xFF2A78D6, 0xFF256ABF, 0xFF184F95, 0xFF0D366B, 0xFF062B57, 0xFF032042,
            0xFF74ABE2, 0xFF2C84DB, 0xFF1B67B2, 0xFFD3E5F8, 0xFF1C3F62};

    /** Заменённый акцентом цвет -> исходный: темы ищут свои замены по исходному цвету. */
    private static final Map<Integer, Integer> ORIGINAL = new java.util.concurrent.ConcurrentHashMap<Integer, Integer>();

    private static volatile boolean loaded;
    private static int accent;
    private static boolean accentOn;
    private static boolean customOn;
    private static final Map<Integer, Integer> DARK = new HashMap<Integer, Integer>();
    private static final Map<Integer, Integer> LIGHT = new HashMap<Integer, Integer>();

    private Theme() {}

    // ---- хуки ----

    /** Color(Long): аргумент и результат — ARGB в младших 32 битах. */
    public static long color(long argb) {
        load();
        int c = (int) argb;
        if (accentOn) {
            Integer mapped = accentMap(c);
            if (mapped != null) {
                ORIGINAL.put(mapped, c);
                c = mapped;
            }
        }
        if (!Plugins.COLORS.isEmpty()) {
            c = Plugins.color(c);
        }
        return c == (int) argb ? argb : c & 0xFFFFFFFFL;
    }

    /** Цвет палитры, прочитанный тёмной темой. Аргумент — упакованный Color: ARGB в старших 32 битах. */
    public static long dark(long packed) {
        return themed(packed, DARK);
    }

    public static long light(long packed) {
        return themed(packed, LIGHT);
    }

    private static long themed(long packed, Map<Integer, Integer> map) {
        load();
        if ((packed & 0x3F) != 0) {
            return packed; // цвет не в sRGB
        }
        int argb = (int) (packed >>> 32);
        Integer original = ORIGINAL.get(argb);
        int base = original != null ? original : argb;
        boolean dark = map == DARK;
        // обои: фон экрана делаем прозрачным, чтобы была видна картинка под Compose
        if (Wallpaper.enabled() && !Wallpaper.choice().isEmpty()
                && base == (dark ? Wallpaper.BG_DARK : Wallpaper.BG_LIGHT)) {
            return 0L; // прозрачный (Color 0x00000000)
        }
        if (!customOn) {
            return packed;
        }
        Integer c = map.get(base);
        return c == null ? packed : ((long) c) << 32;
    }

    // ---- для экранов мода ----

    /** Итоговый цвет палитры с учётом своей темы и акцента. */
    static int resolve(int original, boolean dark) {
        load();
        int c = original;
        if (customOn) {
            Integer o = (dark ? DARK : LIGHT).get(original);
            if (o != null) {
                return o;
            }
        }
        if (accentOn) {
            Integer a = accentMap(c);
            if (a != null) {
                return a;
            }
        }
        return c;
    }

    static boolean accentOn() {
        load();
        return accentOn;
    }

    static int accent() {
        load();
        return accent;
    }

    static boolean customOn() {
        load();
        return customOn;
    }

    static Map<Integer, Integer> overrides(boolean dark) {
        load();
        return new HashMap<Integer, Integer>(dark ? DARK : LIGHT);
    }

    static void setAccent(boolean on, int color) throws Exception {
        Mods.prefs().edit().putBoolean("accent_on", on).putInt("accent", color).commit();
        loaded = false;
    }

    static void setCustom(boolean on) throws Exception {
        Mods.prefs().edit().putBoolean("theme_on", on).commit();
        loaded = false;
    }

    /** Перечитать настройки при следующем обращении (например, после включения обоев). */
    static void reload() {
        loaded = false;
    }

    static void setOverrides(boolean dark, Map<Integer, Integer> map) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> e : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(Integer.toHexString(e.getKey())).append(':').append(Integer.toHexString(e.getValue()));
        }
        Mods.prefs().edit().putString(dark ? "theme_dark" : "theme_light", sb.toString()).commit();
        loaded = false;
    }

    // ---- роли цветов ----

    /** Что известно о цветах палитры (снято с экранов приложения). Остальные — оттенки для статусов и т.п. */
    static String role(int c, boolean dark) {
        switch (c) {
            case 0xFFD97757:
                return "фирменный оранжевый (меняется и акцентом)";
            case 0xFFC6613F:
                return "тёмный фирменный оранжевый";
            case 0xFF6DA7EC:
                return "выбранный пункт, ссылки (меняется и акцентом)";
            case 0xFF2A78D6:
                return "включённый переключатель (меняется и акцентом)";
            default:
                break;
        }
        if (dark) {
            switch (c) {
                case 0xFF151515:
                    return "фон экранов и окон";
                case 0xFF20201F:
                    return "карточки, строки настроек, поле ввода";
                case 0xFF2C2C2A:
                    return "сообщения пользователя, приподнятые элементы";
                case 0xFFF9F9F7:
                    return "основной текст";
                case 0xFF97958D:
                    return "второстепенный текст";
                case 0xFFC3C2B7:
                    return "заголовки разделов";
                case 0xFF383835:
                    return "выключенный переключатель, линии";
                default:
                    return null;
            }
        }
        return null;
    }

    // ---- обмен темами ----

    /** Тема текстом: акцент и замены цветов. Её можно скопировать и отправить другу. */
    static String export() {
        load();
        StringBuilder sb = new StringBuilder("MargyC theme\n");
        if (accentOn) {
            sb.append("accent: ").append(hex(accent)).append('\n');
        }
        if (customOn) {
            for (Map.Entry<Integer, Integer> e : new java.util.TreeMap<Integer, Integer>(DARK).entrySet()) {
                sb.append("dark ").append(hex(e.getKey()).substring(1)).append(": ").append(hex(e.getValue())).append('\n');
            }
            for (Map.Entry<Integer, Integer> e : new java.util.TreeMap<Integer, Integer>(LIGHT).entrySet()) {
                sb.append("light ").append(hex(e.getKey()).substring(1)).append(": ").append(hex(e.getValue())).append('\n');
            }
        }
        return sb.toString();
    }

    /** Применяет тему из текста. Возвращает число прочитанных строк с цветами (0 — не тема). */
    static int importText(String text) throws Exception {
        Map<Integer, Integer> dark = new HashMap<Integer, Integer>(), light = new HashMap<Integer, Integer>();
        Integer acc = null;
        int n = 0;
        for (String raw : text.split("\n")) {
            String line = raw.trim().replace("`", "");
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT);
            Integer value = parse(line.substring(colon + 1));
            if (value == null) {
                continue;
            }
            if (key.equals("accent")) {
                acc = value;
                n++;
            } else if (key.startsWith("dark ") || key.startsWith("light ")) {
                Integer original = parse(key.substring(key.indexOf(' ') + 1));
                if (original != null) {
                    (key.startsWith("dark") ? dark : light).put(original, value);
                    n++;
                }
            }
        }
        if (n == 0) {
            return 0;
        }
        Mods.prefs().edit().putBoolean("accent_on", acc != null).putInt("accent", acc != null ? acc : accent)
                .putBoolean("theme_on", !dark.isEmpty() || !light.isEmpty()).commit();
        setOverrides(true, dark);
        setOverrides(false, light);
        return n;
    }

    static String hex(int c) {
        return (c >>> 24) == 0xFF ? String.format("#%06X", c & 0xFFFFFF) : String.format("#%08X", c);
    }

    private static Integer parse(String s) {
        s = s.trim().replace("#", "");
        int sp = s.indexOf(' ');
        if (sp > 0) {
            s = s.substring(0, sp);
        }
        try {
            if (s.length() == 6) {
                return 0xFF000000 | Integer.parseInt(s, 16);
            }
            if (s.length() == 8) {
                return (int) Long.parseLong(s, 16);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    // ---- внутреннее ----

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        try {
            SharedPreferences p = Mods.prefs();
            accentOn = p.getBoolean("accent_on", false);
            accent = p.getInt("accent", ORANGE[0]);
            customOn = p.getBoolean("theme_on", false);
            parse(p.getString("theme_dark", ""), DARK);
            parse(p.getString("theme_light", ""), LIGHT);
            loaded = true;
        } catch (Throwable t) {
            // приложение ещё не создано: работаем без замен и попробуем позже
            accentOn = false;
            customOn = false;
        }
    }

    private static void parse(String s, Map<Integer, Integer> into) {
        into.clear();
        if (s == null || s.isEmpty()) {
            return;
        }
        for (String pair : s.split(",")) {
            int i = pair.indexOf(':');
            if (i > 0) {
                into.put((int) Long.parseLong(pair.substring(0, i), 16), (int) Long.parseLong(pair.substring(i + 1), 16));
            }
        }
    }

    /** Оттенок семейства акцента: оттенок и насыщенность от выбранного цвета, разница яркости — от оригинала. */
    private static Integer accentMap(int c) {
        int base = family(c, ORANGE);
        if (base == 0) {
            base = family(c, BLUE);
        }
        if (base == 0) {
            return null;
        }
        return shade(accent, base, c);
    }

    private static int family(int c, int[] fam) {
        for (int f : fam) {
            if (f == c) {
                return fam[0];
            }
        }
        return 0;
    }

    static int shade(int user, int base, int original) {
        if (original == base) {
            return user;
        }
        float[] u = hsl(user), b = hsl(base), o = hsl(original);
        float s = b[1] > 0.01f ? u[1] * o[1] / b[1] : u[1];
        float l = u[2] + (o[2] - b[2]);
        return fromHsl(u[0], clamp(s), Math.max(0.03f, Math.min(0.97f, l)), Color.alpha(original));
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    static float[] hsl(int c) {
        float r = Color.red(c) / 255f, g = Color.green(c) / 255f, b = Color.blue(c) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f, h = 0f, s = 0f;
        if (max != min) {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) {
                h = (g - b) / d + (g < b ? 6f : 0f);
            } else if (max == g) {
                h = (b - r) / d + 2f;
            } else {
                h = (r - g) / d + 4f;
            }
            h *= 60f;
        }
        return new float[] {h, s, l};
    }

    static int fromHsl(float h, float s, float l, int alpha) {
        float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
        float p = 2 * l - q;
        float hk = h / 360f;
        return Color.argb(alpha, Math.round(hue(p, q, hk + 1f / 3) * 255),
                Math.round(hue(p, q, hk) * 255), Math.round(hue(p, q, hk - 1f / 3) * 255));
    }

    private static float hue(float p, float q, float t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1f / 6) {
            return p + (q - p) * 6 * t;
        }
        if (t < 0.5f) {
            return q;
        }
        if (t < 2f / 3) {
            return p + (q - p) * (2f / 3 - t) * 6;
        }
        return p;
    }
}
