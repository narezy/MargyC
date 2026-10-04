package cat.narezany.mods;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Перевод строк, которые приходят с сервера (описания моделей, уровни усилия и т.п.)
 * и поэтому не попадают в values-ru. Вызывается из конструктора TextStringSimpleElement,
 * то есть для каждого обычного Text(String) в Compose. Сообщения чата рисуются через
 * AnnotatedString и сюда не попадают.
 */
public final class Tr {
    private static final Map<String, String> RU = new HashMap<String, String>();

    static {
        // описания моделей
        RU.put("For complex work and everyday tasks", "Для сложной работы и повседневных задач");
        RU.put("Most efficient for simpler tasks", "Самая экономичная для простых задач");
        RU.put("For your toughest challenges", "Для самых трудных задач");
        RU.put("Fastest for quick answers", "Самая быстрая для коротких ответов");
        RU.put("Requires usage credits", "Нужны кредиты");
        // уровни усилия; «Max» не трогаем, это ещё и название тарифа
        RU.put("Low", "Низкое");
        RU.put("Medium", "Среднее");
        RU.put("High", "Высокое");
        RU.put("Extra high", "Очень высокое");
        RU.put("Extra High", "Очень высокое");
        RU.put("Minimal", "Минимальное");
    }

    private Tr() {}

    public static String tr(String s) {
        if (s == null) {
            return null;
        }
        String r = RU.get(s);
        if (r != null && "ru".equals(Locale.getDefault().getLanguage())) {
            s = r;
        }
        return Plugins.TEXTS.isEmpty() ? s : Plugins.text(s);
    }
}
