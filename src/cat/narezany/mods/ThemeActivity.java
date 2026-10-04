package cat.narezany.mods;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Редактор своей темы: все цвета палитры тёмной или светлой темы Claude, сгруппированные
 * по смыслу. Палитру патчер достаёт из приложения и кладёт в {@link Names}.
 */
public final class ThemeActivity extends Activity {
    private Ui ui;
    private boolean dark;
    private Map<Integer, Integer> overrides;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        dark = getIntent().getBooleanExtra("dark", true);
        ui = new Ui(this);
        ui.setupWindow(this);
        overrides = Theme.overrides(dark);
        content = ui.column();
        content.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(104));
        build();
        setContentView(ui.withRestartBanner(this, ui.screen(this, dark ? "Тёмная тема" : "Светлая тема", content)));
    }

    private void build() {
        content.removeAllViews();
        Map<String, List<Integer>> groups = new LinkedHashMap<String, List<Integer>>();
        for (String g : new String[] {"Фон и поверхности", "Текст и линии", "Акцент", "Синий", "Фиолетовый",
                "Красный", "Зелёный", "Жёлтый", "Прозрачные"}) {
            groups.put(g, new ArrayList<Integer>());
        }
        for (int c : dark ? Names.DARK_PALETTE : Names.LIGHT_PALETTE) {
            groups.get(group(c, dark)).add(c);
        }
        for (Map.Entry<String, List<Integer>> e : groups.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            content.addView(ui.sectionTitle(e.getKey()));
            LinearLayout g = ui.column();
            for (final int original : e.getValue()) {
                Integer o = overrides.get(original);
                final int now = o != null ? o : original;
                String role = Theme.role(original, dark);
                String sub = (role != null ? role : "") + (o != null ? (role != null ? ", было " : "было ") + hex(original) : "");
                Ui.Row row = ui.new Row(hex(now), sub);
                row.swatch(now);
                row.setOnClickListener(v -> ui.pickColor(hex(original), now, color -> {
                    if (color == original) {
                        overrides.remove(original);
                    } else {
                        overrides.put(original, color);
                    }
                    save();
                }, () -> {
                    overrides.remove(original);
                    save();
                }));
                g.addView(row);
            }
            ui.restyle(g);
            content.addView(g);
        }
        if (!overrides.isEmpty()) {
            LinearLayout g = ui.column();
            Ui.Row reset = ui.new Row("Сбросить все цвета", "");
            reset.title.setTextColor(Theme.resolve(dark ? 0xFFFE8181 : 0xFFB53333, dark));
            reset.setOnClickListener(v -> {
                overrides.clear();
                save();
            });
            g.addView(reset);
            ui.restyle(g);
            View gap = new View(this);
            content.addView(gap, new LinearLayout.LayoutParams(1, ui.dp(24)));
            content.addView(g);
        }
    }

    private void save() {
        try {
            Theme.setOverrides(dark, overrides);
        } catch (Exception e) {
            Log.e(Mods.TAG, "theme", e);
        }
        build();
        Mods.needRestart();
    }

    private static String hex(int c) {
        return Color.alpha(c) == 255 ? String.format("#%06X", c & 0xFFFFFF) : String.format("#%08X", c);
    }

    private static String group(int c, boolean dark) {
        for (int a : Theme.ORANGE) {
            if (a == c) {
                return "Акцент";
            }
        }
        if (Color.alpha(c) < 255) {
            return "Прозрачные";
        }
        float[] hsl = Theme.hsl(c);
        if (hsl[1] < 0.2f || (hsl[2] < 0.08f) || hsl[2] > 0.96f) {
            return (hsl[2] < 0.5f) == dark ? "Фон и поверхности" : "Текст и линии";
        }
        float h = hsl[0];
        if (h < 15 || h >= 330) {
            return "Красный";
        }
        if (h < 45) {
            return "Акцент";
        }
        if (h < 70) {
            return "Жёлтый";
        }
        if (h < 170) {
            return "Зелёный";
        }
        if (h < 245) {
            return "Синий";
        }
        return "Фиолетовый";
    }
}
