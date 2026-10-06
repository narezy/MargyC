package cat.narezany.mods;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * Свои приветствия на главном экране. Приветствие приходит с сервера (GreetingSlot.text), поэтому
 * и не переводится. Патчер отдаёт текст сюда при разборе ответа: мод помечает его невидимым символом,
 * а при показе ({@link Tr}) помеченный текст меняется на фразу по времени суток.
 */
public final class Greeting {
    /** Невидимый разделитель: если текст покажется не через Text(String), его просто не видно. */
    static final char MARK = '⁣';
    static final String CLAUDE = "", RUSSIAN = "ru", CUSTOM = "custom";

    private static final Random RANDOM = new Random();
    private static String chosen;
    private static int chosenBucket = -1;

    private Greeting() {}

    static String mode() {
        try {
            return Mods.prefs().getString("greeting_mode", CLAUDE);
        } catch (Exception e) {
            return CLAUDE;
        }
    }

    static void setMode(String m) throws Exception {
        Mods.prefs().edit().putString("greeting_mode", m).apply();
        chosen = null;
    }

    static String custom() {
        try {
            return Mods.prefs().getString("greeting_custom", "");
        } catch (Exception e) {
            return "";
        }
    }

    static void setCustom(String text) throws Exception {
        Mods.prefs().edit().putString("greeting_custom", text).apply();
        chosen = null;
    }

    /** Хук: текст слота приветствия из ответа сервера. */
    public static String slot(String text) {
        try {
            if (text != null && (!CLAUDE.equals(mode()) || Streamer.enabled()) && text.indexOf(MARK) < 0) {
                return MARK + text;
            }
        } catch (Throwable ignored) {
        }
        return text;
    }

    /** Из Tr: помеченный текст — приветствие. */
    static String show(String s) {
        int i = s.indexOf(MARK);
        if (i < 0) {
            return s;
        }
        String original = s.substring(0, i) + s.substring(i + 1);
        String mine = CLAUDE.equals(mode()) ? null : phrase();
        return mine != null ? mine : original;
    }

    /** 0 утро (5–11), 1 день (12–16), 2 вечер (17–22), 3 ночь. */
    private static int bucket() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 5 && h < 12 ? 0 : h >= 12 && h < 17 ? 1 : h >= 17 && h < 23 ? 2 : 3;
    }

    /** Фраза на сейчас: одна и та же, пока не сменилась часть суток. */
    private static synchronized String phrase() {
        int b = bucket();
        if (chosen == null || chosenBucket != b) {
            List<String> list = phrases(b);
            chosen = list.isEmpty() ? null : list.get(RANDOM.nextInt(list.size()));
            chosenBucket = b;
        }
        if (chosen == null) {
            return null;
        }
        String name = Streamer.firstName();
        String s = chosen.replace("{name}", name);
        if (name.isEmpty()) { // имени нет — без «, » и висящих запятых
            s = s.replace(", !", "!").replace(", ?", "?").replace(", .", ".").replaceAll(",\\s*$", "").trim();
        }
        return s;
    }

    private static List<String> phrases(int b) {
        List<String> out = new ArrayList<String>();
        if (CUSTOM.equals(mode())) {
            String[] keys = {"утро", "день", "вечер", "ночь"};
            List<String> any = new ArrayList<String>();
            for (String line : custom().split("\n")) {
                String l = line.trim();
                if (l.isEmpty()) {
                    continue;
                }
                int colon = l.indexOf(':');
                String key = colon > 0 ? l.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT) : "";
                int k = java.util.Arrays.asList(keys).indexOf(key);
                if (k < 0) { // в английском интерфейсе подсказка с английскими ключами
                    k = java.util.Arrays.asList("morning", "day", "evening", "night").indexOf(key);
                }
                if (k >= 0) {
                    if (k == b) {
                        out.add(l.substring(colon + 1).trim());
                    }
                } else {
                    any.add(l);
                }
            }
            if (out.isEmpty()) {
                out.addAll(any);
            }
            return out;
        }
        switch (b) {
            case 0:
                out.add("Доброе утро, {name}");
                out.add("С добрым утром, {name}");
                out.add("Утро, {name}. Кофе уже?");
                break;
            case 1:
                out.add("Добрый день, {name}");
                out.add("Чем займёмся, {name}?");
                out.add("Привет, {name}");
                break;
            case 2:
                out.add("Добрый вечер, {name}");
                out.add("Вечер, {name}. О чём поговорим?");
                out.add("С возвращением, {name}");
                break;
            default:
                out.add("Доброй ночи, {name}");
                out.add("Не спится, {name}?");
                out.add("Полуночничаем, {name}?");
                break;
        }
        return out;
    }
}
