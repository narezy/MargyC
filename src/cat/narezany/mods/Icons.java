package cat.narezany.mods;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Иконка приложения на рабочем столе. Патчер добавляет на каждый вариант activity-alias главного экрана
 * (cat.narezany.mods.icon.&lt;id&gt;); включён ровно один. Список вариантов должен совпадать с ICONS в patcher.py.
 */
final class Icons {
    static final String[][] ALL = {
            {"margyc", "MargyC"},
            {"claude", "Claude"},
            {"forest", "Лес"},
            {"amoled", "AMOLED"},
            {"clawd", "Clawd"},
            {"clawd_dark", "Clawd (тёмный)"},
    };
    static final int[] COLORS = {0xFF8FD2B1, 0xFFD97757, 0xFF1F5C45, 0xFF000000, 0xFFFAF3E8, 0xFF151515};

    private Icons() {}

    private static ComponentName alias(Context ctx, String id) {
        return new ComponentName(ctx.getPackageName(), "cat.narezany.mods.icon." + id);
    }

    /** Включённый сейчас вариант. */
    static String current(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        for (int i = 0; i < ALL.length; i++) {
            int state = pm.getComponentEnabledSetting(alias(ctx, ALL[i][0]));
            boolean on = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && i == 0);
            if (on) {
                return ALL[i][0];
            }
        }
        return ALL[0][0];
    }

    /** Сначала включается новый, потом выключаются остальные: так иконка не пропадает совсем. */
    static void set(Context ctx, String id) {
        PackageManager pm = ctx.getPackageManager();
        pm.setComponentEnabledSetting(alias(ctx, id), PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        for (String[] icon : ALL) {
            if (!icon[0].equals(id)) {
                pm.setComponentEnabledSetting(alias(ctx, icon[0]), PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        }
        Fake.log("icon: " + id);
    }

    static String name(String id) {
        for (String[] icon : ALL) {
            if (icon[0].equals(id)) {
                return L.t(icon[1]);
            }
        }
        return id;
    }
}
