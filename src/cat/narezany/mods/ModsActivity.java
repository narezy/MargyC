package cat.narezany.mods;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


/** Экран «Моды». Вёрстка кодом, дизайн и цвета — из {@link Ui}. */
public final class ModsActivity extends Activity {
    private Ui ui;
    private Ui.Row presetRow, presetTextRow, accentRow, darkRow, lightRow;
    private View accentSwatch;
    private LinearLayout fakeGroup, pluginGroup;
    private static final int PICK_PLUGIN = 7, PICK_MD = 8, PICK_FONT = 9;
    // кодовые точки Material Icons (Round), шрифт в app-assets
    private static final int IC_LANG = 0xe8e2, IC_ACCENT = 0xe40a, IC_THEME = 0xe243, IC_DARK = 0xe51c,
            IC_LIGHT = 0xe518, IC_COPY = 0xf08a, IC_PASTE = 0xf098, IC_AUTHOR = 0xe0b7, IC_PROMPT = 0xea4a,
            IC_PRESET = 0xe429, IC_PRESET_TEXT = 0xf1c6, IC_PET = 0xe91d, IC_ADD = 0xe145, IC_JOURNAL = 0xe889,
            IC_INSTALL = 0xe87b, IC_DOCS = 0xea19, IC_DOWNLOAD = 0xf090, IC_OPEN = 0xe873, IC_GALLERY = 0xe41d,
            IC_FONT = 0xe167, IC_REACT = 0xea65, IC_LOCK = 0xe897, IC_TIMER = 0xe425, IC_RECENTS = 0xe8f5,
            IC_CATALOG = 0xea12, IC_UPDATE = 0xe923, IC_BETA = 0xea4b, IC_CARD = 0xe870, IC_DONATE = 0xea70,
            IC_LICENSE = 0xe90c, IC_ICON = 0xe5c3, IC_WALLPAPER = 0xe3f4, IC_STREAMER = 0xe8f4, IC_GREETING = 0xe769,
            IC_SEASON = 0xeb3b, IC_SPLASH = 0xeb9b, IC_COUNTER = 0xeac7, IC_STATS = 0xf092, IC_SHARE = 0xe80d,
            IC_DELETE = 0xe872;
    private static final int PICK_WALLPAPER = 10;
    private Ui.Row fontRow, lockTimeRow, updateRow, langRow, iconRow, wallpaperRow, greetingRow, seasonRow, statsRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        ui = new Ui(this);
        ui.setupWindow(this);

        LinearLayout content = ui.column();
        content.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(104)); // место под плашку перезапуска

        content.addView(ui.sectionTitle(L.t("Язык")));
        content.addView(languageGroup());
        content.addView(ui.sectionTitle(L.t("Оформление")));
        content.addView(themeGroup());
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gap.topMargin = ui.dp(12);
        content.addView(shareGroup(), gap);
        content.addView(ui.sectionTitle(L.t("Питомец")));
        content.addView(petGroup());
        content.addView(ui.sectionTitle(L.t("Защита")));
        content.addView(lockGroup());
        content.addView(ui.sectionTitle("Claude"));
        content.addView(promptGroup());
        content.addView(ui.sectionTitle(L.t("Мемные модели")));
        fakeGroup = ui.column();
        content.addView(fakeGroup);
        content.addView(ui.sectionTitle(L.t("Диалоги")));
        content.addView(dialogsGroup());
        content.addView(ui.sectionTitle(L.t("Статистика")));
        content.addView(statsGroup());
        content.addView(ui.sectionTitle(L.t("Свои моды")));
        pluginGroup = ui.column();
        content.addView(pluginGroup);

        content.addView(ui.sectionTitle("MargyC"));
        content.addView(updateGroup());
        LinearLayout.LayoutParams gap2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gap2.topMargin = ui.dp(12);
        content.addView(linksGroup(), gap2);
        content.addView(ui.sectionTitle(L.t("Поддержать автора")));
        content.addView(donateGroup());
        content.addView(ui.sectionTitle(L.t("Журнал")));
        content.addView(journalGroup());

        TextView footer = ui.label("MargyC " + Mods.label() + L.t(" от narezany") + "\n" + L.t("Свободное ПО под лицензией GPL-3.0"),
                13, ui.secondary, ui.regular);
        footer.setGravity(Gravity.CENTER);
        footer.setLineSpacing(ui.dp(3), 1f);
        footer.setPadding(0, ui.dp(32), 0, ui.dp(8));
        footer.setBackground(ui.ripple(ui.round(android.graphics.Color.TRANSPARENT, 12), 12));
        final long[] taps = {0, 0}; // число касаний подряд, время последнего
        footer.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            taps[0] = now - taps[1] < 700 ? taps[0] + 1 : 1;
            taps[1] = now;
            // видно, что подпись нажимается: короткий «клик»
            v.animate().cancel();
            v.setScaleX(0.94f);
            v.setScaleY(0.94f);
            v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            if (taps[0] >= 5) {
                taps[0] = 0;
                kai();
            }
        });
        content.addView(footer);

        setContentView(ui.withRestartBanner(this, ui.screen(this, L.t("Моды"), content)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ---- язык ----

    private View languageGroup() {
        LinearLayout g = ui.column();
        langRow = ui.new Row(L.t("Язык приложения"), "");
        langRow.icon(IC_LANG);
        langRow.chevron();
        langRow.setOnClickListener(v -> {
            if (!Mods.localeSupported()) {
                ui.new Sheet(L.t("Язык приложения"))
                        .message(L.t("Нужен Android 13 или новее. На старых версиях поставь нужный язык языком системы, перевод подхватится сам."))
                        .button(L.t("Понятно"), true, null).show();
                return;
            }
            final Ui.Sheet sheet = ui.new Sheet(L.t("Язык приложения"));
            String current = Mods.language(this);
            for (final String[] lang : Mods.LANGUAGES) {
                sheet.item(lang[0].isEmpty() ? L.t(lang[1]) : lang[1], null, lang[0].equals(current), x -> {
                    sheet.dialog.dismiss();
                    if (!Mods.setLanguage(this, lang[0])) {
                        Toast.makeText(this, L.t("Не получилось сменить язык"), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            sheet.show();
        });
        g.addView(langRow);
        ui.restyle(g);
        return g;
    }

    private static String languageName(String tag) {
        for (String[] lang : Mods.LANGUAGES) {
            if (lang[0].equals(tag)) {
                return lang[0].isEmpty() ? L.t(lang[1]) : lang[1];
            }
        }
        return tag;
    }

    // ---- оформление ----

    private View themeGroup() {
        final LinearLayout g = ui.column();

        accentRow = ui.new Row(L.t("Акцентный цвет"), "");
        accentRow.icon(IC_ACCENT);
        accentSwatch = accentRow.swatch(Theme.accent());
        accentRow.toggle(Theme.accentOn(), true, on -> {
            try {
                Theme.setAccent(on, Theme.accent());
                if (on) {
                    pickAccent();
                } else {
                    Mods.needRestart();
                }
                refresh();
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        accentSwatch.setOnClickListener(v -> pickAccent());
        g.addView(accentRow);

        Ui.Row custom = ui.new Row(L.t("Своя тема"), L.t("Свои цвета для тёмной и светлой темы Claude."));
        custom.toggle(Theme.customOn(), true, on -> {
            try {
                Theme.setCustom(on);
                darkRow.setVisibility(on ? View.VISIBLE : View.GONE);
                lightRow.setVisibility(on ? View.VISIBLE : View.GONE);
                ui.restyle(g);
                Mods.needRestart();
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        custom.icon(IC_THEME);
        g.addView(custom);

        darkRow = ui.new Row(L.t("Тёмная тема"), "");
        darkRow.icon(IC_DARK);
        darkRow.chevron();
        darkRow.setOnClickListener(v -> openTheme(true));
        g.addView(darkRow);
        lightRow = ui.new Row(L.t("Светлая тема"), "");
        lightRow.icon(IC_LIGHT);
        lightRow.chevron();
        lightRow.setOnClickListener(v -> openTheme(false));
        g.addView(lightRow);
        darkRow.setVisibility(Theme.customOn() ? View.VISIBLE : View.GONE);
        lightRow.setVisibility(Theme.customOn() ? View.VISIBLE : View.GONE);

        ui.restyle(g);
        return g;
    }

    private void pickAccent() {
        ui.pickColor(L.t("Акцентный цвет"), Theme.accent(), color -> {
            try {
                Theme.setAccent(true, color);
                refresh();
                Mods.needRestart();
            } catch (Exception e) {
                Log.e(Mods.TAG, "setAccent", e);
            }
        }, null);
    }

    private void openTheme(boolean dark) {
        startActivity(new Intent(this, ThemeActivity.class).putExtra("dark", dark));
    }

    private View shareGroup() {
        LinearLayout g = ui.column();
        Ui.Row gallery = ui.new Row(L.t("Галерея тем"), L.t("Готовые темы: AMOLED, Material You, Catppuccin, Nord и другие."));
        gallery.icon(IC_GALLERY);
        gallery.chevron();
        gallery.setOnClickListener(v -> openGallery());
        g.addView(gallery);
        wallpaperRow = ui.new Row(L.t("Обои в чате"), "");
        wallpaperRow.icon(IC_WALLPAPER);
        wallpaperRow.chevron();
        wallpaperRow.setOnClickListener(v -> openWallpaper());
        g.addView(wallpaperRow);
        iconRow = ui.new Row(L.t("Иконка приложения"), "");
        iconRow.icon(IC_ICON);
        iconRow.chevron();
        iconRow.setOnClickListener(v -> chooseIcon());
        g.addView(iconRow);
        greetingRow = ui.new Row(L.t("Приветствие"), "");
        greetingRow.icon(IC_GREETING);
        greetingRow.chevron();
        greetingRow.setOnClickListener(v -> chooseGreeting());
        g.addView(greetingRow);
        seasonRow = ui.new Row(L.t("Сезонные эффекты"), "");
        seasonRow.icon(IC_SEASON);
        seasonRow.chevron();
        seasonRow.setOnClickListener(v -> chooseSeason());
        g.addView(seasonRow);
        Ui.Row splash = ui.new Row(L.t("Свой экран запуска"),
                L.t("При запуске Claude на секунду появляется Clawd на фоне твоей темы."));
        splash.toggle(Overlay.splash(), true, on -> {
            try {
                Overlay.setSplash(on);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        splash.icon(IC_SPLASH);
        g.addView(splash);
        fontRow = ui.new Row(L.t("Шрифт"), "");
        fontRow.icon(IC_FONT);
        fontRow.chevron();
        fontRow.setOnClickListener(v -> chooseFont());
        g.addView(fontRow);
        Ui.Row copy = ui.new Row(L.t("Скопировать мою тему"), L.t("Текстом, чтобы отправить другу."));
        copy.setOnClickListener(v -> {
            String theme = Theme.export();
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("MargyC theme", theme));
            ui.new Sheet(L.t("Тема скопирована")).message(theme.trim()).button(L.t("Готово"), true, null).show();
        });
        copy.icon(IC_COPY);
        g.addView(copy);
        Ui.Row paste = ui.new Row(L.t("Вставить тему"), L.t("Из буфера обмена. Тему может написать и Claude, просто попроси."));
        paste.setOnClickListener(v -> importTheme());
        paste.icon(IC_PASTE);
        g.addView(paste);
        ui.restyle(g);
        return g;
    }

    private void importTheme() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = cm.getPrimaryClip();
        String text = clip != null && clip.getItemCount() > 0 ? String.valueOf(clip.getItemAt(0).coerceToText(this)) : "";
        int n = 0;
        try {
            n = Theme.importText(text);
        } catch (Exception e) {
            Log.e(Mods.TAG, "import theme", e);
        }
        if (n == 0) {
            ui.new Sheet(L.t("Это не тема"))
                    .message(L.t("В буфере обмена нет темы MargyC. Скопируй её целиком, вместе со строками вида «dark 151515: #0E1116»."))
                    .button(L.t("Понятно"), true, null).show();
            return;
        }
        Mods.needRestart();
        ui.new Sheet(L.t("Тема применена"))
                .message(L.t("Прочитано цветов: ") + n + L.t(". Они появятся после перезапуска приложения."))
                .button(L.t("Готово"), true, this::recreate)
                .show();
    }

    static final String GITHUB = "https://github.com/narezy/MargyC";

    /** Карточки канала (цвета Telegram) и GitHub, строка «Написать автору». */
    private View linksGroup() {
        LinearLayout g = ui.column();
        g.addView(linkCard(L.t("Канал MargyC"), L.t("Новые обновления"), new int[] {0xFF37BBFE, 0xFF2AABEE, 0xFF1E96D4},
                new PlaneView(this), L.t("Подписаться"), 0xFF1E96D4, "https://t.me/margyclaude"));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gl.topMargin = ui.dp(10);
        g.addView(linkCard(L.t("MargyC на GitHub"), L.t("Исходники, свои моды и свежий APK"),
                new int[] {0xFF444C56, 0xFF2D333B, 0xFF1C2128}, new GitHubView(this), L.t("Открыть"), 0xFF24292F, GITHUB), gl);

        LinearLayout authorBox = ui.column();
        Ui.Row author = ui.new Row(L.t("Написать автору"), L.t("@narezany в Telegram"));
        TextView avatar = ui.label("N", 18, Color.WHITE, ui.medium);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable av = new GradientDrawable();
        av.setShape(GradientDrawable.OVAL);
        av.setColor(ui.accent);
        avatar.setBackground(av);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40));
        al.rightMargin = ui.dp(16);
        author.addView(avatar, 0, al);
        author.icon(IC_AUTHOR);
        author.chevron();
        author.setOnClickListener(v -> open("https://t.me/narezany"));
        authorBox.addView(author);
        ui.restyle(authorBox);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.dp(10);
        g.addView(authorBox, lp);
        return g;
    }

    private View linkCard(String title, String subtitle, int[] colors, View icon, String button, int buttonText,
                          final String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        bg.setCornerRadius(ui.dp(20));
        card.setBackground(ui.ripple(bg, 20));
        card.setOnClickListener(v -> open(url));

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0x33FFFFFF);
        icon.setBackground(circle);
        card.addView(icon, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(52)));

        LinearLayout texts = ui.column();
        texts.setPadding(ui.dp(16), 0, ui.dp(12), 0);
        texts.addView(ui.label(title, 18, Color.WHITE, ui.medium));
        TextView sub = ui.label(subtitle, 14, 0xD9FFFFFF, ui.regular);
        sub.setPadding(0, ui.dp(4), 0, 0);
        texts.addView(sub);
        card.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView join = ui.label(button, 14, buttonText, ui.medium);
        join.setGravity(Gravity.CENTER);
        join.setPadding(ui.dp(14), 0, ui.dp(14), 0);
        join.setBackground(ui.round(Color.WHITE, 18));
        card.addView(join, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(36)));
        return card;
    }

    /** Значок GitHub (octicon mark-github), белым. */
    private static final class GitHubView extends View {
        private static final Path MARK = SvgPath.parse("M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17"
                + ".55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01"
                + "-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0"
                + "-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 "
                + ".27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95"
                + ".29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z");
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Matrix matrix = new android.graphics.Matrix();
        private final Path path = new Path();

        GitHubView(android.content.Context ctx) {
            super(ctx);
            paint.setColor(Color.WHITE);
        }

        @Override
        protected void onDraw(Canvas c) {
            float size = Math.min(getWidth(), getHeight()) * 0.5f;
            matrix.setScale(size / 16f, size / 16f);
            matrix.postTranslate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            MARK.transform(matrix, path);
            c.drawPath(path, paint);
        }
    }

    /** Бумажный самолётик Telegram. */
    private static final class PlaneView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        PlaneView(android.content.Context ctx) {
            super(ctx);
            paint.setColor(Color.WHITE);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            path.reset();
            path.moveTo(w * 0.22f, h * 0.50f);
            path.lineTo(w * 0.76f, h * 0.28f);
            path.lineTo(w * 0.66f, h * 0.74f);
            path.lineTo(w * 0.50f, h * 0.62f);
            path.lineTo(w * 0.42f, h * 0.72f);
            path.lineTo(w * 0.42f, h * 0.58f);
            path.lineTo(w * 0.66f, h * 0.38f);
            path.lineTo(w * 0.36f, h * 0.55f);
            path.close();
            c.drawPath(path, paint);
        }
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show();
        }
    }

    // ---- системный промпт ----

    private View promptGroup() {
        final LinearLayout g = ui.column();
        boolean on = true;
        try {
            on = Prompt.enabled();
        } catch (Exception ignored) {
        }
        Ui.Row prompt = ui.new Row(L.t("Дополнить системный промпт"),
                L.t("Claude получает текст пресета вместе с каждым сообщением. В чате его не видно."));
        prompt.toggle(on, true, value -> {
            try {
                Prompt.setEnabled(value);
                presetRow.setVisibility(value ? View.VISIBLE : View.GONE);
                presetTextRow.setVisibility(value ? View.VISIBLE : View.GONE);
                ui.restyle(g);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        prompt.icon(IC_PROMPT);
        g.addView(prompt);

        presetRow = ui.new Row(L.t("Пресет"), "");
        presetRow.icon(IC_PRESET);
        presetRow.chevron();
        presetRow.setOnClickListener(v -> choosePreset());
        g.addView(presetRow);

        presetTextRow = ui.new Row(L.t("Текст пресета"), "");
        presetTextRow.icon(IC_PRESET_TEXT);
        presetTextRow.chevron();
        presetTextRow.setOnClickListener(v -> openPreset());
        g.addView(presetTextRow);

        presetRow.setVisibility(on ? View.VISIBLE : View.GONE);
        presetTextRow.setVisibility(on ? View.VISIBLE : View.GONE);
        ui.restyle(g);
        return g;
    }

    private void choosePreset() {
        try {
            final Ui.Sheet sheet = ui.new Sheet(L.t("Пресет"));
            String current = Prompt.selected().id;
            for (final Prompt.Preset p : Prompt.presets()) {
                sheet.item(p.name, p.id.equals(Prompt.DEFAULT_ID)
                                ? L.t("О моде, устройстве и как писать темы. Не меняется.")
                                : p.id.equals(Prompt.SHORT_ID) ? L.t("О моде и устройстве. Не меняется.")
                                : p.id.equals(Prompt.SUPER_ID)
                                        ? L.t("Всё о MargyC: функции, включённые моды, темы и палитра Material You. Не меняется.")
                                        : p.text,
                        p.id.equals(current), v -> {
                            try {
                                Prompt.select(p.id);
                            } catch (Exception ignored) {
                            }
                            sheet.dialog.dismiss();
                            refresh();
                        });
            }
            sheet.button(L.t("Новый пресет"), false, () -> editPreset(null, "", ""));
            sheet.show();
        } catch (Exception e) {
            Log.e(Mods.TAG, "presets", e);
        }
    }

    private void openPreset() {
        try {
            Prompt.Preset p = Prompt.selected();
            if (p.builtIn()) {
                final String text = p.text;
                ui.new Sheet(L.t("Пресет «") + p.name + "»")
                        .message(text)
                        .button(L.t("Копия в новый"), false, () -> editPreset(null, L.t("Мой пресет"), text))
                        .button(L.t("Закрыть"), true, null)
                        .show();
            } else {
                editPreset(p.id, p.name, p.text);
            }
        } catch (Exception e) {
            Log.e(Mods.TAG, "preset", e);
        }
    }

    private void editPreset(final String id, String name, String text) {
        final EditText nameField = ui.field(name, L.t("Название"), false);
        final EditText textField = ui.field(text, L.t("Что Claude должен знать. Лучше по-английски."), true);
        LinearLayout box = ui.column();
        box.addView(nameField);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.dp(12);
        box.addView(textField, lp);
        Ui.Sheet sheet = ui.new Sheet(id == null ? L.t("Новый пресет") : L.t("Пресет")).view(box);
        if (id != null) {
            sheet.button(L.t("Удалить"), false, () -> {
                try {
                    Prompt.delete(id);
                } catch (Exception ignored) {
                }
                refresh();
            });
        }
        sheet.button(L.t("Отмена"), false, null);
        sheet.button(L.t("Сохранить"), true, () -> {
            String n = nameField.getText().toString().trim();
            try {
                String saved = Prompt.save(id, n.isEmpty() ? L.t("Мой пресет") : n, textField.getText().toString());
                Prompt.select(saved);
            } catch (Exception e) {
                Log.e(Mods.TAG, "save preset", e);
            }
            refresh();
        });
        sheet.show();
    }

    // ---- пасхалка ----

    /** Кай из «Выдающихся зверей»: пять касаний подряд по подписи внизу. */
    private void kai() {
        android.graphics.drawable.Drawable drawable;
        try { // анимированный webp: ImageDecoder отдаёт AnimatedImageDrawable
            drawable = android.graphics.ImageDecoder.decodeDrawable(
                    android.graphics.ImageDecoder.createSource(getAssets(), "margyc/kai.webp"));
        } catch (Exception e) {
            Log.e(Mods.TAG, "kai", e);
            return;
        }
        if (drawable instanceof android.graphics.drawable.AnimatedImageDrawable) {
            ((android.graphics.drawable.AnimatedImageDrawable) drawable).start();
        }
        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = ui.column();
        box.setGravity(Gravity.CENTER);
        box.setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24));
        ImageView image = new ImageView(this);
        image.setImageDrawable(drawable);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setClipToOutline(true);
        image.setBackground(ui.round(ui.card, 28));
        box.addView(image, new LinearLayout.LayoutParams(ui.dp(240), ui.dp(240)));
        TextView text = ui.label(L.t("Кай одобряет MargyC"), 20, Color.WHITE, ui.medium);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, ui.dp(16), 0, 0);
        box.addView(text);
        // первые две секунды Кая не закрыть: иначе лишние касания закрывают его сразу
        final long shown = System.currentTimeMillis();
        dialog.setCancelable(false);
        box.setOnClickListener(v -> {
            if (System.currentTimeMillis() - shown > 2000) {
                dialog.dismiss();
            }
        });
        box.postDelayed(() -> dialog.setCancelable(true), 2000);
        dialog.setContentView(box);
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.7f);
        }
        dialog.show();
        // выезжает с покачиванием и смотрит на тебя
        image.setScaleX(0.2f);
        image.setScaleY(0.2f);
        image.setRotation(-25);
        image.animate().scaleX(1f).scaleY(1f).rotation(0).setDuration(450)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f))
                .withEndAction(() -> {
                    android.animation.ObjectAnimator wobble = android.animation.ObjectAnimator
                            .ofFloat(image, "rotation", 0, -4, 4, -2, 2, 0);
                    wobble.setDuration(700);
                    wobble.start();
                }).start();
    }

    // ---- питомец ----

    private View petGroup() {
        LinearLayout g = ui.column();
        Ui.Row pet = ui.new Row(L.t("Clawd на поле ввода"),
                L.t("Сидит на поле ввода в чате и в Code и поднимается вместе с ним. Зажми и тащи, чтобы пересадить, коснись — анимация заново."));
        pet.toggle(Pet.enabled(), true, on -> {
            try {
                Pet.setEnabled(on);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        pet.icon(IC_PET);
        g.addView(pet);
        Ui.Row react = ui.new Row(L.t("Реагирует на ответы"),
                L.t("Пока Claude отвечает, Clawd печатает на ноутбуке, а потом прыгает или танцует. Анимации — самого Claude."));
        react.toggle(Pet.reacts(), true, on -> {
            try {
                Pet.setReacts(on);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        react.icon(IC_REACT);
        g.addView(react);
        ui.restyle(g);
        return g;
    }

    // ---- мемные модели ----

    private void buildFakes() {
        fakeGroup.removeAllViews();
        try {
            for (final Fake.Model m : Fake.all()) {
                Ui.Row row = ui.new Row(m.name, "");
                row.sub(L.t("Отвечает ") + Fake.realName(m.base) + (m.description.isEmpty() ? "" : ". " + m.description), 2);
                row.chevron();
                row.setOnClickListener(v -> editFake(m));
                fakeGroup.addView(row);
            }
        } catch (Exception e) {
            Log.e(Mods.TAG, "fake models", e);
        }
        Ui.Row add = ui.new Row(L.t("Новая модель"),
                L.t("Своя модель сверху в списке моделей, например «Fable 6969». Отвечает настоящая, какую выберешь."));
        add.setOnClickListener(v -> editFake(null));
        add.icon(IC_ADD);
        fakeGroup.addView(add);
        Ui.Row paste = ui.new Row(L.t("Вставить модель"), L.t("Из буфера обмена: модель, которой с тобой поделились."));
        paste.setOnClickListener(v -> importFake());
        paste.icon(IC_PASTE);
        fakeGroup.addView(paste);
        ui.restyle(fakeGroup);
    }

    private void editFake(final Fake.Model m) {
        final java.util.Map<String, String> real = Fake.realModels();
        final String[] base = {m != null ? m.base : real.isEmpty() ? null : real.keySet().iterator().next()};
        if (base[0] == null) {
            ui.new Sheet(L.t("Нет списка моделей"))
                    .message(L.t("Мод ещё не видел список моделей Claude. Открой чат, чтобы приложение его загрузило, и возвращайся."))
                    .button(L.t("Понятно"), true, null).show();
            return;
        }
        final EditText nameField = ui.field(m != null ? m.name : "", L.t("Название, например Fable 6969"), false);
        final EditText descField = ui.field(m != null ? m.description : "", L.t("Описание под названием"), false);
        final TextView baseField = ui.label("", 16, ui.text, ui.regular);
        baseField.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        baseField.setBackground(ui.ripple(ui.round(ui.card, 16), 16));
        final Runnable showBase = () -> baseField.setText(L.t("Отвечает: ") + Fake.realName(base[0]) + "  ›");
        showBase.run();
        baseField.setOnClickListener(v -> {
            final Ui.Sheet pick = ui.new Sheet(L.t("Кто отвечает на самом деле"));
            for (final java.util.Map.Entry<String, String> e : real.entrySet()) {
                pick.item(e.getValue().isEmpty() ? e.getKey() : e.getValue(), e.getKey(), e.getKey().equals(base[0]), x -> {
                    base[0] = e.getKey();
                    showBase.run();
                    pick.dialog.dismiss();
                });
            }
            pick.show();
        });
        final EditText promptField = ui.field(m != null ? m.prompt : "",
                L.t("Системный промпт этой модели, например: ты Fable 6969 и всегда упоминаешь 6969. ")
                        + L.t("Добавляется к промпту мода."), true);

        LinearLayout box = ui.column();
        for (View v : new View[] {nameField, descField, baseField, promptField}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = box.getChildCount() == 0 ? 0 : ui.dp(12);
            box.addView(v, lp);
        }
        Ui.Sheet sheet = ui.new Sheet(m == null ? L.t("Новая модель") : L.t("Мемная модель")).view(box);
        if (m != null) {
            sheet.button(L.t("Поделиться"), false, () -> {
                String text = Fake.export(m);
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("MargyC model", text));
                ui.new Sheet(L.t("Модель скопирована")).message(text.trim()).button(L.t("Готово"), true, null).show();
            });
            sheet.button(L.t("Удалить"), false, () -> {
                try {
                    Fake.delete(m.id);
                } catch (Exception ignored) {
                }
                refresh();
                Mods.needRestart();
            });
            sheet.breakLine();
        }
        sheet.button(L.t("Отмена"), false, null);
        sheet.button(L.t("Сохранить"), true, () -> {
            String n = nameField.getText().toString().trim();
            try {
                Fake.save(m != null ? m.id : null, n.isEmpty() ? L.t("Мемная модель") : n,
                        descField.getText().toString().trim(), base[0], promptField.getText().toString());
            } catch (Exception e) {
                Log.e(Mods.TAG, "save fake model", e);
            }
            refresh();
            Mods.needRestart();
        });
        sheet.show();
    }

    private void importFake() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = cm.getPrimaryClip();
        String text = clip != null && clip.getItemCount() > 0 ? String.valueOf(clip.getItemAt(0).coerceToText(this)) : "";
        int n = 0;
        try {
            n = Fake.importText(text);
        } catch (Exception e) {
            Log.e(Mods.TAG, "import model", e);
        }
        if (n == 0) {
            ui.new Sheet(L.t("Это не модель"))
                    .message(L.t("В буфере обмена нет мемной модели. Скопируй её целиком, начиная со строки «MargyC model»."))
                    .button(L.t("Понятно"), true, null).show();
            return;
        }
        refresh();
        Mods.needRestart();
        ui.new Sheet(L.t("Модель добавлена"))
                .message((n == 1 ? L.t("Добавлена одна модель.") : L.t("Добавлено моделей: ") + n + ".")
                        + L.t(" Она появится в списке моделей после перезапуска."))
                .button(L.t("Готово"), true, null).show();
    }

    // ---- свои моды ----

    private void buildPlugins() {
        pluginGroup.removeAllViews();
        for (final Plugins.Info info : Plugins.installed(this)) {
            String error = Plugins.error(info.id);
            Ui.Row row = ui.new Row(info.name, "");
            String sub = (info.author.isEmpty() ? "" : info.author + (info.version.isEmpty() ? "" : " · ") )
                    + (info.version.isEmpty() ? "" : "v" + info.version);
            sub = (sub.isEmpty() ? "" : sub + "\n") + (error.isEmpty() ? info.description : L.t("Ошибка: ") + error);
            row.sub(sub.trim(), 3);
            if (!error.isEmpty()) {
                row.subtitle.setTextColor(Theme.resolve(ui.night ? 0xFFFE8181 : 0xFFB53333, ui.night));
            }
            android.graphics.Bitmap icon = info.icon();
            View iconView;
            if (icon != null) {
                ImageView image = new ImageView(this);
                image.setImageBitmap(icon);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setClipToOutline(true);
                image.setBackground(ui.round(ui.divider, 12));
                iconView = image;
            } else {
                TextView letter = ui.label(info.name.isEmpty() ? "?" : info.name.substring(0, 1).toUpperCase(),
                        18, Color.WHITE, ui.medium);
                letter.setGravity(Gravity.CENTER);
                letter.setBackground(ui.round(ui.accent, 12));
                iconView = letter;
            }
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40));
            il.rightMargin = ui.dp(16);
            row.addView(iconView, 0, il);
            row.toggle(Plugins.enabled(info.id), true, on -> {
                try {
                    Plugins.setEnabled(info.id, on);
                    Mods.needRestart();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
            // касание строки — настройки мода, а не переключатель
            row.setOnClickListener(v -> openPlugin(info));
            pluginGroup.addView(row);
        }
        Ui.Row catalog = ui.new Row(L.t("Каталог модов"), L.t("Моды из репозитория MargyC: установка в одно касание."));
        catalog.icon(IC_CATALOG);
        catalog.chevron();
        catalog.setOnClickListener(v -> openCatalog());
        pluginGroup.addView(catalog);
        Ui.Row install = ui.new Row(L.t("Установить мод"), L.t("Файл .mcmod: скомпилированный мод с manifest.json."));
        install.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            try {
                startActivityForResult(pick, PICK_PLUGIN);
            } catch (Exception e) {
                Toast.makeText(this, L.t("Нет приложения для выбора файла"), Toast.LENGTH_SHORT).show();
            }
        });
        install.icon(IC_INSTALL);
        pluginGroup.addView(install);
        Ui.Row docs = ui.new Row(L.t("Как написать свой мод"), L.t("Документация и пример на GitHub."));
        docs.chevron();
        docs.setOnClickListener(v -> open(GITHUB + "/blob/main/docs/plugins.md"));
        docs.icon(IC_DOCS);
        pluginGroup.addView(docs);
        ui.restyle(pluginGroup);
    }

    // ---- диалоги: скачивание из меню чата и открытие .md ----

    private View dialogsGroup() {
        LinearLayout g = ui.column();
        Ui.Row export = ui.new Row(L.t("Скачивание диалогов"),
                L.t("Пункт «Скачать диалог (.md)» в меню «⋮» чата. Файл сохраняется в Загрузки/MargyC."));
        export.toggle(Export.enabled(), true, on -> {
            try {
                Export.setEnabled(on);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        export.icon(IC_DOWNLOAD);
        g.addView(export);
        Ui.Row open = ui.new Row(L.t("Открыть диалог .md"),
                L.t("Экспорт MargyC или любой .md с заголовками «## Ты» / «## Claude»: посмотреть как чат и продолжить в Claude."));
        open.chevron();
        open.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            try {
                startActivityForResult(pick, PICK_MD);
            } catch (Exception e) {
                Toast.makeText(this, L.t("Нет приложения для выбора файла"), Toast.LENGTH_SHORT).show();
            }
        });
        open.icon(IC_OPEN);
        g.addView(open);
        Ui.Row counter = ui.new Row(L.t("Счётчик символов"),
                L.t("Над полем ввода видно, сколько символов в сообщении."));
        counter.toggle(Overlay.counter(), true, on -> {
            try {
                Overlay.setCounter(on);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        counter.icon(IC_COUNTER);
        g.addView(counter);
        ui.restyle(g);
        return g;
    }

    // ---- статистика ----

    private View statsGroup() {
        LinearLayout g = ui.column();
        statsRow = ui.new Row(L.t("Claude в цифрах"), "");
        statsRow.icon(IC_STATS);
        statsRow.chevron();
        statsRow.setOnClickListener(v -> openStats());
        g.addView(statsRow);
        ui.restyle(g);
        return g;
    }

    private static String statsLine(Stats.Summary s) {
        if (s.total == 0) {
            return L.t("Считается с установки 1.3, только на телефоне. Пока сообщений нет.");
        }
        return L.t("Сообщений: ") + s.total + " · " + L.t("Серия: ") + s.streak + L.t(" дн. подряд");
    }

    private void openStats() {
        final Stats.Summary s = Stats.summary();
        final Ui.Sheet sheet = ui.new Sheet(L.t("Claude в цифрах"));
        if (s.total == 0) {
            sheet.message(statsLine(s));
            sheet.button(L.t("Закрыть"), true, null).show();
            return;
        }
        sheet.view(statLine(String.valueOf(s.total), L.t("сообщений отправлено")));
        sheet.view(statLine(String.valueOf(s.activeDays), L.t("дней с Claude")));
        sheet.view(statLine(String.valueOf(s.streak), L.t("дней подряд сейчас")));
        if (!s.bestDay.isEmpty()) {
            sheet.view(statLine(String.valueOf(s.bestDayCount), L.t("сообщений за самый активный день, ") + s.bestDay));
        }
        if (!s.favourite.isEmpty()) {
            sheet.view(statLine(s.favourite, L.t("любимая модель")));
        }
        if (s.topHour >= 0) {
            TextView h = ui.label(L.t("Чаще всего пишешь в ") + s.topHour + ":00", 14, ui.secondary, ui.regular);
            h.setPadding(0, ui.dp(12), 0, ui.dp(6));
            sheet.view(h);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(72));
            View chart = new HoursChart(this, s.hours, ui.accent, ui.secondary);
            chart.setLayoutParams(lp);
            sheet.view(chart);
        }
        if (!s.since.isEmpty()) {
            TextView since = ui.label(L.t("С ") + s.since + L.t(". Считается только на телефоне и никуда не отправляется."),
                    13, ui.secondary, ui.regular);
            since.setPadding(0, ui.dp(12), 0, 0);
            sheet.view(since);
        }
        sheet.button(L.t("Сбросить"), false, () -> ui.new Sheet(L.t("Сбросить статистику?"))
                .button(L.t("Отмена"), false, null)
                .button(L.t("Сбросить"), true, () -> {
                    try {
                        Stats.reset();
                    } catch (Exception ignored) {
                    }
                    refresh();
                }).show());
        sheet.button(L.t("Поделиться"), true, () -> {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, Stats.share(s));
            try {
                startActivity(Intent.createChooser(send, L.t("Поделиться")));
            } catch (Exception ignored) {
            }
        });
        sheet.show();
    }

    private View statLine(String value, String what) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(6), 0, ui.dp(6));
        TextView v = ui.label(value, 24, ui.accent, ui.medium);
        v.setPadding(0, 0, ui.dp(12), 0);
        v.setMaxLines(1);
        row.addView(v);
        TextView w = ui.label(what, 15, ui.text, ui.regular);
        row.addView(w, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** Столбики по часам суток: когда пишешь чаще. */
    private static final class HoursChart extends View {
        private final int[] hours;
        private final android.graphics.Paint bar = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint dim = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();

        HoursChart(android.content.Context c, int[] hours, int accent, int secondary) {
            super(c);
            this.hours = hours;
            bar.setColor(accent);
            dim.setColor(secondary);
            dim.setAlpha(60);
            dim.setTextSize(10 * c.getResources().getDisplayMetrics().scaledDensity);
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            int max = 1;
            for (int h : hours) {
                max = Math.max(max, h);
            }
            float w = getWidth() / 24f, gap = w * 0.2f, bottom = getHeight();
            float radius = w * 0.25f;
            for (int i = 0; i < 24; i++) {
                float top = bottom - Math.max(w * 0.3f, bottom * hours[i] / (float) max);
                r.set(i * w + gap / 2, top, (i + 1) * w - gap / 2, bottom);
                canvas.drawRoundRect(r, radius, radius, hours[i] > 0 ? bar : dim);
            }
        }
    }

    /** Диалог из .md как чат: твои сообщения справа пузырями, Claude слева, как в приложении. */
    private void showChat(final String md) {
        Export.Conversation c = Export.parse(md);
        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar);
        LinearLayout list = ui.column();
        list.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(24));
        TextView count = ui.label(L.t("Сообщений: ") + c.messages.size(), 14, ui.secondary, ui.regular);
        count.setGravity(Gravity.CENTER);
        count.setPadding(0, 0, 0, ui.dp(12));
        list.addView(count);
        for (Export.Message m : c.messages) {
            boolean me = Export.isUser(m.sender);
            TextView t = ui.label(m.text, 16, ui.text, me ? ui.regular : ui.regular);
            t.setLineSpacing(ui.dp(4), 1f);
            t.setTextIsSelectable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    me ? LinearLayout.LayoutParams.WRAP_CONTENT : LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = ui.dp(me ? 16 : 10);
            if (me) {
                t.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
                t.setBackground(ui.round(ui.card, 20));
                t.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.8f));
                lp.gravity = Gravity.END;
            } else {
                t.setPadding(ui.dp(4), 0, ui.dp(4), 0);
            }
            list.addView(t, lp);
        }
        LinearLayout box = ui.column();
        box.addView(list);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, ui.dp(8), 0, ui.dp(8));
        TextView cont = ui.label(L.t("Продолжить в Claude"), 16, ui.bg, ui.medium);
        cont.setGravity(Gravity.CENTER);
        cont.setPadding(ui.dp(24), 0, ui.dp(24), 0);
        cont.setBackground(ui.ripple(ui.round(ui.text, 24), 24));
        cont.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(getPackageName())
                    .putExtra(Intent.EXTRA_TEXT, L.t("Это предыдущий диалог, продолжим его. Вот он целиком:") + "\n\n" + md);
            try {
                startActivity(send);
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(this, String.valueOf(e), Toast.LENGTH_LONG).show();
            }
        });
        actions.addView(cont, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(48)));
        box.addView(actions);
        View screen = ui.screen(this, c.title.isEmpty() ? "Claude" : c.title, box);
        dialog.setContentView(screen);
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            ui.setupDialogWindow(w);
        }
        // стрелка «назад» экрана закрывает Activity — здесь она должна закрыть окно
        screen.findViewWithTag("back").setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_WALLPAPER && result == RESULT_OK && data != null && data.getData() != null) {
            try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
                Wallpaper.install(this, in);
                Wallpaper.setChoice("file");
                Wallpaper.setEnabled(true);
                Mods.needRestart();
                refresh();
            } catch (Exception e) {
                Log.e(Mods.TAG, "wallpaper", e);
                ui.new Sheet(L.t("Не получилось"))
                        .message(L.t("Картинка не подошла: ") + e.getMessage())
                        .button(L.t("Понятно"), true, null).show();
            }
            return;
        }
        if (request == PICK_FONT && result == RESULT_OK && data != null && data.getData() != null) {
            try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
                Font.install(this, in);
                Font.setChoice(Font.FILE);
                Mods.needRestart();
                refresh();
            } catch (Exception e) {
                Log.e(Mods.TAG, "font", e);
                ui.new Sheet(L.t("Не получилось"))
                        .message(L.t("Шрифт не подошёл: ") + e.getMessage())
                        .button(L.t("Понятно"), true, null).show();
            }
            return;
        }
        if (request == PICK_MD && result == RESULT_OK && data != null && data.getData() != null) {
            try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                showChat(out.toString("UTF-8"));
            } catch (Exception e) {
                Log.e(Mods.TAG, "open md", e);
                Toast.makeText(this, L.t("Не получилось открыть файл"), Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (request != PICK_PLUGIN || result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
            Plugins.Info info = Plugins.install(this, in);
            Plugins.setEnabled(info.id, true);
            Mods.needRestart();
            refresh();
            ui.new Sheet(L.t("Мод установлен"))
                    .message(info.name + (info.author.isEmpty() ? "" : L.t(" от ") + info.author)
                            + L.t(". Он заработает после перезапуска Claude."))
                    .button(L.t("Готово"), true, null).show();
        } catch (Exception e) {
            Log.e(Mods.TAG, "install plugin", e);
            ui.new Sheet(L.t("Это не мод"))
                    .message(L.t("Не получилось установить: ") + e.getMessage())
                    .button(L.t("Понятно"), true, null).show();
        }
    }

    /** Настройки мода из manifest.json и удаление. */
    private void openPlugin(final Plugins.Info info) {
        final android.content.SharedPreferences prefs = info.prefs(this);
        LinearLayout box = ui.column();
        if (!info.description.isEmpty()) {
            TextView d = ui.label(info.description, 15, ui.secondary, ui.regular);
            d.setLineSpacing(ui.dp(3), 1f);
            d.setPadding(0, 0, 0, ui.dp(12));
            box.addView(d);
        }
        final java.util.List<Runnable> savers = new java.util.ArrayList<Runnable>();
        for (int i = 0; i < info.settings.length(); i++) {
            final org.json.JSONObject st = info.settings.optJSONObject(i);
            if (st == null || st.optString("key").isEmpty()) {
                continue;
            }
            final String key = st.optString("key");
            String type = st.optString("type", "text");
            String title = st.optString("title", key);
            LinearLayout group = ui.column();
            Ui.Row row = ui.new Row(title, st.optString("description"));
            group.addView(row);
            if (type.equals("toggle")) {
                row.toggle(prefs.getBoolean(key, st.optBoolean("default")), true, on -> {
                    prefs.edit().putBoolean(key, on).apply();
                    Mods.needRestart();
                    return true;
                });
            } else if (type.equals("choice")) {
                final org.json.JSONArray options = st.optJSONArray("options");
                final String[] value = {prefs.getString(key, st.optString("default"))};
                final Ui.Row r = row;
                r.chevron();
                r.subtitle.setVisibility(View.VISIBLE);
                r.sub(value[0], 1);
                r.setOnClickListener(v -> {
                    final Ui.Sheet pick = ui.new Sheet(title);
                    for (int k = 0; options != null && k < options.length(); k++) {
                        final String option = options.optString(k);
                        pick.item(option, null, option.equals(value[0]), x -> {
                            value[0] = option;
                            prefs.edit().putString(key, option).apply();
                            r.sub(option, 1);
                            Mods.needRestart();
                            pick.dialog.dismiss();
                        });
                    }
                    pick.show();
                });
            } else {
                final boolean number = type.equals("number");
                String now = number ? String.valueOf(prefs.getInt(key, st.optInt("default")))
                        : prefs.getString(key, st.optString("default"));
                final EditText field = ui.field(now, st.optString("hint", title), type.equals("multiline"));
                if (number) {
                    field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
                }
                LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                fl.topMargin = ui.dp(6);
                group.addView(field, fl);
                savers.add(() -> {
                    String v = field.getText().toString();
                    if (number) {
                        try {
                            prefs.edit().putInt(key, Integer.parseInt(v.trim())).apply();
                        } catch (NumberFormatException ignored) {
                        }
                    } else {
                        prefs.edit().putString(key, v).apply();
                    }
                });
            }
            cardRow(group, row);
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            gl.topMargin = ui.dp(8);
            box.addView(group, gl);
        }
        Ui.Sheet sheet = ui.new Sheet(info.name).view(box);
        sheet.button(L.t("Удалить"), false, () -> ui.new Sheet(L.t("Удалить мод?"))
                .message(info.name + L.t(" пропадёт после перезапуска Claude."))
                .button(L.t("Отмена"), false, null)
                .button(L.t("Удалить"), true, () -> {
                    try {
                        Plugins.uninstall(this, info);
                    } catch (Exception e) {
                        Log.e(Mods.TAG, "uninstall", e);
                    }
                    Mods.needRestart();
                    refresh();
                }).show());
        if (savers.isEmpty()) {
            sheet.button(L.t("Готово"), true, null);
        } else {
            sheet.button(L.t("Отмена"), false, null);
            sheet.button(L.t("Сохранить"), true, () -> {
                for (Runnable r : savers) {
                    r.run();
                }
                Mods.needRestart();
            });
        }
        sheet.show();
    }

    /** Карточкой оформляется только строка настройки, поле ввода под ней остаётся как есть. */
    private void cardRow(LinearLayout group, Ui.Row row) {
        LinearLayout tmp = ui.column();
        group.removeView(row);
        tmp.addView(row);
        ui.restyle(tmp);
        tmp.removeView(row);
        group.addView(row, 0);
    }

    // ---- обои, иконка, приветствие ----

    private String wallpaperName() {
        String c = Wallpaper.choice();
        if (!Wallpaper.enabled() || c.isEmpty()) {
            return L.t("Выключены");
        }
        if (c.equals("file")) {
            return L.t("Своя картинка");
        }
        Wallpaper.Preset p = Wallpaper.preset(c.substring(c.indexOf(':') + 1));
        return p != null ? p.name : c;
    }

    private void openWallpaper() {
        final Ui.Sheet sheet = ui.new Sheet(L.t("Обои в чате"));
        String current = Wallpaper.enabled() ? Wallpaper.choice() : "";
        sheet.item(L.t("Выключены"), L.t("Обычный фон Claude."), current.isEmpty(), v -> {
            try {
                Wallpaper.setEnabled(false);
            } catch (Exception ignored) {
            }
            sheet.dialog.dismiss();
            Mods.needRestart();
            refresh();
        });
        for (final Wallpaper.Preset p : Wallpaper.presets()) {
            sheet.view(presetRow(p.name, L.t("Градиент"), p.colors.length >= 3
                    ? new int[] {p.colors[0], p.colors[1], p.colors[2]} : new int[] {p.colors[0], p.colors[1]}, v -> {
                try {
                    Wallpaper.setChoice("preset:" + p.id);
                    Wallpaper.setEnabled(true);
                } catch (Exception ignored) {
                }
                sheet.dialog.dismiss();
                Mods.needRestart();
                refresh();
            }));
        }
        sheet.item(L.t("Своя картинка…"), L.t("Фото из галереи. Под чатом, карточки сообщений остаются как есть."),
                current.equals("file"), v -> {
                    sheet.dialog.dismiss();
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
                    try {
                        startActivityForResult(pick, PICK_WALLPAPER);
                    } catch (Exception e) {
                        Toast.makeText(this, L.t("Нет приложения для выбора файла"), Toast.LENGTH_SHORT).show();
                    }
                });
        sheet.item(L.t("Затемнение: ") + Wallpaper.dim() + "%", L.t("Насколько приглушить обои, чтобы текст читался."), false, v -> {
            sheet.dialog.dismiss();
            final Ui.Sheet pick = ui.new Sheet(L.t("Затемнение"));
            for (final int d : new int[] {0, 25, 40, 55, 70, 85}) {
                pick.item(d + "%", null, d == Wallpaper.dim(), x -> {
                    try {
                        Wallpaper.setDim(d);
                    } catch (Exception ignored) {
                    }
                    pick.dialog.dismiss();
                    Mods.needRestart();
                });
            }
            pick.show();
        });
        sheet.button(L.t("Закрыть"), true, null);
        sheet.show();
    }

    private void chooseIcon() {
        final Ui.Sheet sheet = ui.new Sheet(L.t("Иконка приложения"));
        String current = Icons.current(this);
        for (int i = 0; i < Icons.ALL.length; i++) {
            final String id = Icons.ALL[i][0];
            boolean clawd = id.startsWith("clawd");
            sheet.view(presetRow(Icons.name(id) + (id.equals(current) ? "  ✓" : ""),
                    clawd ? L.t("Пиксельный Clawd") : L.t("Звезда Claude"),
                    new int[] {Icons.COLORS[i], clawd ? 0xFFF05A40 : 0xFFFFFFFF}, v -> {
                        Icons.set(this, id);
                        sheet.dialog.dismiss();
                        refresh();
                        ui.new Sheet(L.t("Иконка сменена"))
                                .message(L.t("Лаунчеру может понадобиться несколько секунд. Если иконка пропала с рабочего стола, добавь её заново из списка приложений."))
                                .button(L.t("Понятно"), true, null).show();
                    }));
        }
        sheet.button(L.t("Закрыть"), true, null);
        sheet.show();
    }

    private static String greetingName(String mode) {
        return Greeting.RUSSIAN.equals(mode) ? L.t("По-русски, по времени суток")
                : Greeting.CUSTOM.equals(mode) ? L.t("Свои фразы") : L.t("Как в Claude");
    }

    private void chooseGreeting() {
        final Ui.Sheet sheet = ui.new Sheet(L.t("Приветствие"));
        String mode = Greeting.mode();
        sheet.item(greetingName(Greeting.CLAUDE), L.t("Приходит с сервера Claude, обычно по-английски."),
                Greeting.CLAUDE.equals(mode), v -> setGreeting(sheet, Greeting.CLAUDE));
        sheet.item(greetingName(Greeting.RUSSIAN), L.t("«Доброе утро», «Добрый вечер», «Не спится?» и другие, с твоим именем."),
                Greeting.RUSSIAN.equals(mode), v -> setGreeting(sheet, Greeting.RUSSIAN));
        sheet.item(greetingName(Greeting.CUSTOM), L.t("Свой список. Строка — фраза; «утро:», «день:», «вечер:», «ночь:» в начале — для времени суток; {name} — имя."),
                Greeting.CUSTOM.equals(mode), v -> {
                    sheet.dialog.dismiss();
                    final EditText field = ui.field(Greeting.custom(), L.t("утро: Доброе утро, {name}\nвечер: Как прошёл день?\nПривет!"), true);
                    ui.new Sheet(L.t("Свои фразы")).view(field)
                            .button(L.t("Отмена"), false, null)
                            .button(L.t("Сохранить"), true, () -> {
                                try {
                                    Greeting.setCustom(field.getText().toString());
                                    Greeting.setMode(Greeting.CUSTOM);
                                } catch (Exception ignored) {
                                }
                                Mods.needRestart();
                                refresh();
                            }).show();
                });
        sheet.show();
    }

    private static String seasonName(String s) {
        switch (s) {
            case "auto":
                return L.t("По времени года");
            case "snow":
                return L.t("Снег");
            case "leaves":
                return L.t("Листопад");
            case "petals":
                return L.t("Лепестки сакуры");
            case "stars":
                return L.t("Звёзды");
            default:
                return L.t("Выключены");
        }
    }

    private void chooseSeason() {
        final Ui.Sheet sheet = ui.new Sheet(L.t("Сезонные эффекты"));
        String current = Overlay.season();
        String[][] all = {
            {"off", ""},
            {"auto", L.t("Зимой снег, весной лепестки, осенью листья, летом ничего.")},
            {"snow", ""}, {"leaves", ""}, {"petals", ""}, {"stars", ""},
        };
        for (final String[] o : all) {
            sheet.item(seasonName(o[0]), o[1], o[0].equals(current), v -> {
                try {
                    Overlay.setSeason(o[0]);
                } catch (Exception ignored) {
                }
                sheet.dialog.dismiss();
                refresh();
            });
        }
        sheet.show();
    }

    private void setGreeting(Ui.Sheet sheet, String mode) {
        try {
            Greeting.setMode(mode);
        } catch (Exception ignored) {
        }
        sheet.dialog.dismiss();
        Mods.needRestart();
        refresh();
    }

    // ---- донаты, лицензия, журнал ----

    /** Реквизиты для донатов. По условиям лицензии (LICENSE, раздел «Additional terms») их нельзя убирать. */
    static final String DONATE_CARD = "2204120143055305";
    static final String DONATE_YOOMONEY = "https://yoomoney.ru/to/4100118196133693";

    private View donateGroup() {
        LinearLayout g = ui.column();
        Ui.Row card = ui.new Row(L.t("Перевод на карту"), "2204 1201 4305 5305");
        card.icon(IC_CARD);
        card.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("MargyC", DONATE_CARD));
            Toast.makeText(this, L.t("Номер карты скопирован"), Toast.LENGTH_SHORT).show();
        });
        g.addView(card);
        Ui.Row yoo = ui.new Row(L.t("ЮMoney"), L.t("Донат через ЮMoney, картой любого банка."));
        yoo.icon(IC_DONATE);
        yoo.chevron();
        yoo.setOnClickListener(v -> open(DONATE_YOOMONEY));
        g.addView(yoo);
        Ui.Row license = ui.new Row(L.t("Лицензия"),
                L.t("GPL-3.0: код можно менять и распространять, но с исходниками и с указанием автора (narezany) и этих реквизитов для донатов."));
        license.icon(IC_LICENSE);
        license.chevron();
        license.setOnClickListener(v -> open(GITHUB + "/blob/main/LICENSE"));
        g.addView(license);
        ui.restyle(g);
        return g;
    }

    private View journalGroup() {
        LinearLayout g = ui.column();
        Ui.Row journal = ui.new Row(L.t("Журнал MargyC"),
                L.t("Что делали хуки и моды, включая прошлые запуски. Если что-то не работает, скопируй и отправь автору."));
        journal.icon(IC_JOURNAL);
        journal.chevron();
        journal.setOnClickListener(v -> {
            final String text = Journal.report();
            TextView body = ui.label(text, 12, ui.secondary, ui.regular);
            body.setTypeface(android.graphics.Typeface.MONOSPACE);
            body.setTextIsSelectable(true);
            ui.new Sheet(L.t("Журнал MargyC")).view(body)
                    .button(L.t("Очистить"), false, () -> {
                        Journal.clear();
                        Toast.makeText(this, L.t("Журнал очищен"), Toast.LENGTH_SHORT).show();
                    })
                    .button(L.t("Скопировать"), false, () -> {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("MargyC journal", text));
                        Toast.makeText(this, L.t("Скопировано"), Toast.LENGTH_SHORT).show();
                    })
                    .button(L.t("Закрыть"), true, null).show();
        });
        g.addView(journal);
        ui.restyle(g);
        return g;
    }

    // ---- галерея тем и шрифт ----

    private void openGallery() {
        final Ui.Sheet sheet = ui.new Sheet(L.t("Галерея тем"));
        for (final Gallery.Preset p : Gallery.all(this)) {
            sheet.view(presetRow(p.name, p.description, p.preview(ui.night), v -> {
                try {
                    Gallery.apply(p);
                } catch (Exception e) {
                    Log.e(Mods.TAG, "gallery", e);
                }
                sheet.dialog.dismiss();
                Mods.needRestart();
                recreate();
            }));
        }
        sheet.view(presetRow(L.t("Как в Claude"), L.t("Обычные цвета: своя тема и акцент выключаются."), null, v -> {
            try {
                Gallery.reset();
            } catch (Exception e) {
                Log.e(Mods.TAG, "gallery reset", e);
            }
            sheet.dialog.dismiss();
            Mods.needRestart();
            recreate();
        }));
        sheet.button(L.t("Закрыть"), true, null);
        sheet.show();
    }

    /** Строка галереи: кружки цветов темы (фон, карточка, текст, акцент), название и описание. */
    private View presetRow(String title, String sub, int[] colors, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(4), ui.dp(10), ui.dp(4), ui.dp(10));
        row.setBackground(ui.ripple(ui.round(Color.TRANSPARENT, 12), 12));
        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        int[] cs = colors != null ? colors : new int[] {Theme.resolve(0xFF151515, true), Theme.resolve(0xFF20201F, true),
                0xFFF9F9F7, 0xFFD97757};
        for (int i = 0; i < cs.length; i++) {
            View dot = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(cs[i]);
            d.setStroke(ui.dp(1), ui.divider);
            dot.setBackground(d);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22));
            dl.leftMargin = i == 0 ? 0 : -ui.dp(7);
            dots.addView(dot, dl);
        }
        LinearLayout.LayoutParams dsl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dsl.rightMargin = ui.dp(14);
        row.addView(dots, dsl);
        LinearLayout texts = ui.column();
        texts.addView(ui.label(title, 17, ui.text, ui.regular));
        TextView st = ui.label(sub, 13, ui.secondary, ui.regular);
        st.setPadding(0, ui.dp(2), 0, 0);
        texts.addView(st);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.setOnClickListener(click);
        return row;
    }

    private static String fontName(String choice) {
        switch (choice) {
            case Font.SANS:
                return L.t("Системный");
            case Font.SERIF:
                return L.t("С засечками (системный)");
            case Font.APP_SERIF:
                return L.t("С засечками Claude");
            case Font.MONO:
                return L.t("Моноширинный");
            case Font.FILE:
                return L.t("Свой файл");
            default:
                return L.t("Как в Claude");
        }
    }

    private void chooseFont() {
        final Ui.Sheet sheet = ui.new Sheet(L.t("Шрифт"));
        String current = Font.choice();
        String[][] options = {
                {Font.NONE, L.t("Anthropic Sans в интерфейсе, Anthropic Serif в ответах.")},
                {Font.SANS, L.t("Шрифт телефона (обычно Roboto или шрифт прошивки).")},
                {Font.APP_SERIF, L.t("Anthropic Serif, шрифт ответов Claude, во всём приложении.")},
                {Font.SERIF, L.t("Системный шрифт с засечками.")},
                {Font.MONO, L.t("Как в терминале. Код остаётся своим шрифтом в любом случае.")},
                {Font.FILE, L.t("Файл .ttf или .otf с телефона.")}};
        for (final String[] o : options) {
            sheet.item(fontName(o[0]), o[1], o[0].equals(current), v -> {
                sheet.dialog.dismiss();
                if (o[0].equals(Font.FILE)) {
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                    try {
                        startActivityForResult(pick, PICK_FONT);
                    } catch (Exception e) {
                        Toast.makeText(this, L.t("Нет приложения для выбора файла"), Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                try {
                    Font.setChoice(o[0]);
                } catch (Exception ignored) {
                }
                Mods.needRestart();
                refresh();
            });
        }
        sheet.show();
    }

    // ---- защита ----

    private View lockGroup() {
        final LinearLayout g = ui.column();
        Ui.Row lock = ui.new Row(L.t("Блокировка"),
                L.t("Claude открывается по отпечатку, лицу или PIN-коду телефона."));
        lock.toggle(Lock.enabled(), true, on -> {
            String why = on ? Lock.unavailable(this) : null;
            if (why != null) {
                ui.new Sheet(L.t("Блокировку не включить")).message(why).button(L.t("Понятно"), true, null).show();
                return false;
            }
            try {
                Lock.setEnabled(on);
                lockTimeRow.setVisibility(on ? View.VISIBLE : View.GONE);
                ui.restyle(g);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        lock.icon(IC_LOCK);
        g.addView(lock);
        lockTimeRow = ui.new Row(L.t("Спрашивать снова"), "");
        lockTimeRow.icon(IC_TIMER);
        lockTimeRow.chevron();
        lockTimeRow.setOnClickListener(v -> {
            final Ui.Sheet sheet = ui.new Sheet(L.t("Спрашивать снова через"));
            for (final int t : Lock.TIMEOUTS) {
                sheet.item(Lock.timeoutLabel(t), t == 0 ? L.t("Каждый раз, когда Claude уходит в фон.") : null,
                        t == Lock.timeout(), x -> {
                            try {
                                Lock.setTimeout(t);
                            } catch (Exception ignored) {
                            }
                            sheet.dialog.dismiss();
                            refresh();
                        });
            }
            sheet.show();
        });
        g.addView(lockTimeRow);
        lockTimeRow.setVisibility(Lock.enabled() ? View.VISIBLE : View.GONE);
        Ui.Row recents = ui.new Row(L.t("Скрывать в недавних"),
                L.t("В списке открытых приложений вместо снимка чата пусто."));
        recents.toggle(Lock.hideRecents(), true, on -> {
            try {
                Lock.setHideRecents(on);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        recents.icon(IC_RECENTS);
        g.addView(recents);
        Ui.Row streamer = ui.new Row(L.t("Режим стримера"),
                L.t("Твои имя и почта скрыты везде в Claude, включая приветствие на главном экране. После перезапуска."));
        streamer.toggle(Streamer.enabled(), true, on -> {
            try {
                Streamer.setEnabled(on);
                Mods.needRestart();
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        streamer.icon(IC_STREAMER);
        g.addView(streamer);
        ui.restyle(g);
        return g;
    }

    // ---- обновления ----

    private View updateGroup() {
        LinearLayout g = ui.column();
        updateRow = ui.new Row(L.t("Обновления"), "");
        updateRow.icon(IC_UPDATE);
        updateRow.chevron();
        updateRow.setOnClickListener(v -> {
            updateRow.sub(L.t("Проверяю…"), 2);
            Update.check(true, info -> {
                if (isFinishing()) {
                    return;
                }
                showUpdate();
                if (info != null) {
                    Update.sheet(this, info, false);
                } else if (Update.error() != null) {
                    Toast.makeText(this, L.t("Не получилось проверить: ") + Update.error(), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, L.t("У тебя последняя версия"), Toast.LENGTH_SHORT).show();
                }
            });
        });
        g.addView(updateRow);
        Ui.Row beta = ui.new Row(L.t("Бета-версии"),
                L.t("Новые функции раньше всех, но в бете бывают ошибки. Ставится поверх, настройки сохраняются."));
        beta.toggle(Update.betas(), true, on -> {
            try {
                Update.setBetas(on);
                showUpdate();
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        beta.icon(IC_BETA);
        g.addView(beta);
        ui.restyle(g);
        return g;
    }

    private void showUpdate() {
        if (updateRow == null) {
            return;
        }
        Update.Info info = Update.available();
        updateRow.sub(info != null ? L.t("Вышла ") + info.label() + L.t(". Нажми, чтобы скачать.")
                : L.t("У тебя ") + Mods.label() + L.t(". Нажми, чтобы проверить."), 2);
    }

    // ---- каталог модов ----

    private void openCatalog() {
        final Ui.Sheet sheet = ui.new Sheet(L.t("Каталог модов"));
        final TextView status = ui.label(L.t("Загружаю…"), 15, ui.secondary, ui.regular);
        sheet.view(status);
        final LinearLayout list = ui.column();
        sheet.view(list);
        sheet.button(L.t("Закрыть"), true, null);
        sheet.show();
        Catalog.load((entries, error) -> {
            if (isFinishing()) {
                return;
            }
            if (entries == null) {
                status.setText(L.t("Не получилось загрузить каталог: ") + error);
                return;
            }
            status.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            status.setText(L.t("В каталоге пока пусто."));
            for (final Catalog.Entry e : entries) {
                Plugins.Info have = Catalog.installed(this, e.id);
                String state = have == null ? L.t("Установить")
                        : have.version.equals(e.version) ? L.t("Установлен") : L.t("Обновить до v") + e.version;
                String sub = (e.author.isEmpty() ? "" : e.author + " · ") + (e.version.isEmpty() ? "" : "v" + e.version)
                        + "\n" + e.description + "\n" + state;
                final boolean done = have != null && have.version.equals(e.version);
                final View[] row = new View[1];
                row[0] = sheet.item(e.name, sub.trim(), done, v -> {
                    if (done) {
                        return;
                    }
                    row[0].setEnabled(false);
                    row[0].setAlpha(0.5f);
                    Catalog.install(this, e, (info, err) -> {
                        if (isFinishing()) {
                            return;
                        }
                        sheet.dialog.dismiss();
                        if (info == null) {
                            ui.new Sheet(L.t("Не установился")).message(String.valueOf(err))
                                    .button(L.t("Понятно"), true, null).show();
                            return;
                        }
                        Mods.needRestart();
                        refresh();
                        ui.new Sheet(L.t("Мод установлен"))
                                .message(info.name + L.t(". Он заработает после перезапуска Claude."))
                                .button(L.t("Готово"), true, null).show();
                    });
                });
                ((ViewGroup) row[0].getParent()).removeView(row[0]);
                list.addView(row[0]);
                if (row[0] instanceof LinearLayout) {
                    TextView s2 = findSub((LinearLayout) row[0]);
                    if (s2 != null) {
                        s2.setMaxLines(4);
                    }
                }
            }
        });
    }

    /** Подпись пункта списка окна (Ui.Sheet.item ограничивает её двумя строками). */
    private static TextView findSub(LinearLayout row) {
        View texts = row.getChildAt(0);
        if (texts instanceof LinearLayout && ((LinearLayout) texts).getChildCount() > 1
                && ((LinearLayout) texts).getChildAt(1) instanceof TextView) {
            return (TextView) ((LinearLayout) texts).getChildAt(1);
        }
        return null;
    }

    // ----

    private void refresh() {
        if (presetRow == null) {
            return;
        }
        buildFakes();
        buildPlugins();
        try {
            Prompt.Preset p = Prompt.selected();
            presetRow.sub(p.name, 1);
            presetTextRow.sub(p.text, 3);
        } catch (Exception e) {
            Log.e(Mods.TAG, "refresh", e);
        }
        accentRow.sub(Theme.accentOn()
                ? String.format(L.t("#%06X. Заменяет оранжевый и синий цвет выбора во всём приложении."), Theme.accent() & 0xFFFFFF)
                : L.t("Заменяет оранжевый и синий цвет выбора во всём приложении."), 3);
        accentSwatch.setBackground(ui.round(Theme.accent(), 14));
        accentSwatch.setVisibility(Theme.accentOn() ? View.VISIBLE : View.GONE);
        darkRow.sub(count(true), 1);
        lightRow.sub(count(false), 1);
        fontRow.sub(fontName(Font.choice()), 1);
        langRow.sub(languageName(Mods.language(this)), 1);
        iconRow.sub(Icons.name(Icons.current(this)), 1);
        wallpaperRow.sub(wallpaperName(), 1);
        greetingRow.sub(greetingName(Greeting.mode()), 1);
        seasonRow.sub(seasonName(Overlay.season()), 1);
        statsRow.sub(statsLine(Stats.summary()), 2);
        lockTimeRow.sub(Lock.timeoutLabel(Lock.timeout()), 1);
        showUpdate();
    }

    private String count(boolean dark) {
        int n = Theme.overrides(dark).size();
        return n == 0 ? L.t("Как в Claude") : L.t("Изменено цветов: ") + n;
    }
}
