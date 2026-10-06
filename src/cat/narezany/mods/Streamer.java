package cat.narezany.mods;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Режим стримера: имя и почта аккаунта не видны нигде в интерфейсе Claude, включая приветствие на
 * главном экране и поля профиля. Имена заменяются точками прямо при разборе Account; в запрос изменения
 * профиля вместо точек возвращается настоящее имя, так что на сервер они не уходят. Почта прячется только
 * при показе текста ({@link Tr}).
 */
public final class Streamer {
    static final String MASK = "•••••";

    private static volatile Pattern pattern;
    private static volatile Boolean on;

    private Streamer() {}

    static boolean enabled() {
        if (on == null) {
            try {
                on = Mods.prefs().getBoolean("streamer_on", false);
            } catch (Exception e) {
                return false;
            }
        }
        return on;
    }

    static void setEnabled(boolean value) throws Exception {
        Mods.prefs().edit().putBoolean("streamer_on", value).apply();
        on = value;
    }

    /** Хук: full_name аккаунта. В режиме стримера в приложение попадают точки. */
    public static String fullName(String value) {
        return name(value, "streamer_full");
    }

    /** Хук: display_name аккаунта («Как к вам обращаться?»). */
    public static String displayName(String value) {
        return name(value, "streamer_display");
    }

    private static String name(String value, String key) {
        try {
            if (value == null || value.contains(MASK)) {
                return value; // уже замазанное (из кэша приложения) не запоминаем
            }
            seen(value);
            Mods.prefs().edit().putString(key, value).apply();
            return enabled() && !value.trim().isEmpty() ? MASK : value;
        } catch (Throwable t) {
            return value;
        }
    }

    /** Хук: full_name в запросе изменения профиля — точки обратно в настоящее имя. */
    public static String restoreFull(String value) {
        return restore(value, "streamer_full");
    }

    public static String restoreDisplay(String value) {
        return restore(value, "streamer_display");
    }

    private static String restore(String value, String key) {
        try {
            if (value != null && value.contains(MASK)) {
                String real = Mods.prefs().getString(key, "");
                if (!real.isEmpty()) {
                    Fake.log("streamer: real name restored in profile update");
                    return value.replace(MASK, real);
                }
            }
        } catch (Throwable ignored) {
        }
        return value;
    }

    /** Хук: поле email_address аккаунта при разборе ответа сервера. */
    public static void seen(String value) {
        try {
            if (value == null || value.trim().length() < 2) {
                return;
            }
            Set<String> words = words();
            int before = words.size();
            words.add(value.trim());
            if (value.contains("@")) {
                words.add(value.substring(0, value.indexOf('@')).trim());
            } else {
                for (String w : value.trim().split("\\s+")) {
                    if (w.length() >= 2) {
                        words.add(w);
                    }
                }
            }
            if (words.size() != before) {
                Mods.prefs().edit().putString("streamer_words", String.join("\n", words)).apply();
                pattern = null;
                Fake.log("streamer: " + words.size() + " words to hide");
            }
            if (first == null && !value.contains("@")) {
                first = value.trim().split("\\s+")[0];
            }
        } catch (Throwable ignored) {
        }
    }

    private static String first;

    /** Имя для приветствий: первое слово отображаемого или полного имени. */
    static String firstName() {
        if (first == null) {
            for (String w : words()) {
                if (!w.contains("@") && !w.contains(" ")) {
                    first = w;
                    break;
                }
            }
        }
        return first != null ? first : "";
    }

    private static Set<String> cached;

    private static synchronized Set<String> words() {
        if (cached == null) {
            cached = new LinkedHashSet<String>();
            try {
                for (String w : Mods.prefs().getString("streamer_words", "").split("\n")) {
                    if (!w.trim().isEmpty()) {
                        cached.add(w.trim());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return cached;
    }

    /** Текст интерфейса с замазанными именем и почтой. */
    static String mask(String s) {
        if (s == null || s.isEmpty() || !enabled()) {
            return s;
        }
        Pattern p = pattern;
        if (p == null) {
            List<String> parts = new ArrayList<String>(words());
            if (parts.isEmpty()) {
                return s;
            }
            // длинные раньше коротких: сначала «Егор Иванов», потом «Егор»
            parts.sort((a, b) -> b.length() - a.length());
            StringBuilder sb = new StringBuilder();
            for (String w : parts) {
                sb.append(sb.length() == 0 ? "" : "|").append(Pattern.quote(w));
            }
            p = Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + sb + ")(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            pattern = p;
        }
        Matcher m = p.matcher(s);
        return m.find() ? m.replaceAll(MASK) : s;
    }
}
