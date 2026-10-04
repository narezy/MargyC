package cat.narezany.mods;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Мост между обфусцированным кодом Claude и модом. Обфусцированные классы не упоминаются
 * в коде напрямую: имена подставляет патчер в {@link Names}, остальное ищется через reflection,
 * поэтому мод не надо переписывать под каждую версию приложения.
 *
 * Вызывается из smali-патчей бокового меню:
 *  - addDrawerItem() дописывает пункт «Моды» в список вкладок;
 *  - key() даёт пункту уникальный ключ LazyColumn;
 *  - callback()/selected() подменяют нажатие и подсветку при отрисовке пункта.
 * И из патча кнопки «Продолжить с Google»: googleLogin().
 */
public final class Bridge {
    public static final String LABEL = "Моды";

    private static Class<?> itemClass;
    private static Constructor<?> itemCtor;
    private static Field tabField;
    private static Field titleField;
    private static Object openCallback;
    private static Object googleCallback;

    private Bridge() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addDrawerItem(List list) {
        try {
            init();
            Set<Object> used = new HashSet<Object>();
            for (Object o : list) {
                if (isMods(o)) {
                    return;
                }
                if (itemClass.isInstance(o)) {
                    used.add(tabField.get(o));
                }
            }
            // Вкладка служит ключом LazyColumn, поэтому берём ту, которой сейчас нет в меню
            // (с конца, там редкие). Нажатие на пункт всё равно перехватываем сами.
            Class<?>[] params = itemCtor.getParameterTypes();
            Object[] tabs = params[0].getEnumConstants();
            Object icon = icon(params[1], "plugin", list);
            for (int i = tabs.length - 1; i >= 0; i--) {
                if (!used.contains(tabs[i])) {
                    list.add(itemCtor.newInstance(tabs[i], icon, LABEL, 1, null));
                    return;
                }
            }
        } catch (Throwable t) {
            Log.e(Mods.TAG, "не удалось добавить пункт в меню", t);
        }
    }

    /** Ключ пункта в LazyColumn меню: у «Модов» свой, остальным оставляем вкладку. */
    public static Object key(Object item) {
        try {
            init();
            return isMods(item) ? "narezany.mods" : tabField.get(item);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static Object callback(Object item, Object original) {
        if (!isMods(item)) {
            return original;
        }
        try {
            return openCallback();
        } catch (Throwable t) {
            Log.e(Mods.TAG, "callback", t);
            return original;
        }
    }

    public static boolean selected(Object item, boolean original) {
        return !isMods(item) && original;
    }

    private static boolean isMods(Object item) {
        try {
            init();
            return itemClass.isInstance(item) && LABEL.equals(titleField.get(item));
        } catch (Throwable t) {
            return false;
        }
    }

    private static void init() throws Exception {
        if (titleField != null) {
            return;
        }
        Class<?> c = Class.forName(Names.DRAWER_ITEM);
        // (вкладка, иконка, заголовок, int, бейдж)
        for (Constructor<?> k : c.getDeclaredConstructors()) {
            Class<?>[] p = k.getParameterTypes();
            if (p.length == 5 && p[0].isEnum() && p[2] == String.class && p[3] == int.class) {
                k.setAccessible(true);
                itemCtor = k;
            }
        }
        if (itemCtor == null) {
            throw new IllegalStateException("нет конструктора " + Names.DRAWER_ITEM);
        }
        Field tab = null;
        Field title = null;
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            if (f.getType() == itemCtor.getParameterTypes()[0]) {
                tab = f;
            } else if (f.getType() == String.class) {
                title = f;
            }
        }
        if (tab == null || title == null) {
            throw new IllegalStateException("нет полей " + Names.DRAWER_ITEM);
        }
        itemClass = c;
        tabField = tab;
        titleField = title;
    }

    /**
     * Иконка из шрифта Anthropicon по имени. Иконки лежат полями в объекте-холдере,
     * на который ссылается статическое поле класса иконки.
     */
    private static Object icon(Class<?> iconClass, String name, List<?> items) {
        try {
            Field nameField = null;
            for (Field f : iconClass.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                    f.setAccessible(true);
                    nameField = f;
                    break;
                }
            }
            for (Field s : iconClass.getDeclaredFields()) {
                if (!Modifier.isStatic(s.getModifiers()) || s.getType().isPrimitive()
                        || s.getType().getName().startsWith("java.")) {
                    continue;
                }
                s.setAccessible(true);
                Object holder = s.get(null);
                if (holder == null) {
                    continue;
                }
                for (Field f : holder.getClass().getDeclaredFields()) {
                    if (f.getType() != iconClass) {
                        continue;
                    }
                    f.setAccessible(true);
                    Object icon = f.get(holder);
                    if (icon != null && name.equals(nameField.get(icon))) {
                        return icon;
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(Mods.TAG, "иконка " + name, t);
        }
        // запасной вариант: иконка любого пункта меню
        try {
            for (Field f : itemClass.getDeclaredFields()) {
                if (f.getType() == iconClass) {
                    f.setAccessible(true);
                    return f.get(items.get(0));
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Лямбда () -> Unit приложения (Function0 после обфускации), открывающая экран модов. */
    private static synchronized Object openCallback() throws Exception {
        if (openCallback == null) {
            openCallback = function0("ClaudeMods.open", Mods::open);
        }
        return openCallback;
    }

    /** Кнопка «Продолжить с Google»: вместо входа объясняем, как войти через почту. */
    public static Object googleLogin(Object original) {
        try {
            if (googleCallback == null) {
                googleCallback = function0("ClaudeMods.google", Mods::showGoogleInfo);
            }
            return googleCallback;
        } catch (Throwable t) {
            Log.e(Mods.TAG, "googleLogin", t);
            return original;
        }
    }

    /** Реализация обфусцированного Function0 через Proxy: invoke() выполняет action и возвращает Unit. */
    private static Object function0(final String name, final Runnable action) throws Exception {
        final Class<?> fn = Class.forName(Names.FUNCTION0);
        final Object unit = unit();
        return Proxy.newProxyInstance(fn.getClassLoader(), new Class<?>[] {fn},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return name;
                        default:
                            action.run();
                            return unit;
                    }
                });
    }

    /** kotlin.Unit.INSTANCE; результат onClick никто не читает, так что null тоже сойдёт. */
    static Object unit() {
        try {
            Class<?> c = Class.forName(Names.UNIT);
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == c) {
                    f.setAccessible(true);
                    return f.get(null);
                }
            }
        } catch (Throwable t) {
            Log.e(Mods.TAG, "kotlin.Unit", t);
        }
        return null;
    }
}
