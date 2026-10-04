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
    private static final int PICK_PLUGIN = 7, PICK_MD = 8;

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
        content.addView(ui.sectionTitle("Claude"));
        content.addView(promptGroup());
        content.addView(ui.sectionTitle(L.t("Мемные модели")));
        fakeGroup = ui.column();
        content.addView(fakeGroup);
        content.addView(ui.sectionTitle(L.t("Диалоги")));
        content.addView(dialogsGroup());
        content.addView(ui.sectionTitle(L.t("Свои моды")));
        pluginGroup = ui.column();
        content.addView(pluginGroup);

        content.addView(ui.sectionTitle("MargyC"));
        content.addView(linksGroup());

        TextView footer = ui.label("MargyC " + Mods.VERSION + L.t(" от narezany"), 13, ui.secondary, ui.regular);
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
        Ui.Row ru = ui.new Row(L.t("Русский язык"), Mods.localeSupported()
                ? (Mods.systemRussian()
                        ? L.t("Весь интерфейс на русском. Выключишь — будет английский.")
                        : L.t("Весь интерфейс на русском. Выключишь — вернётся язык системы."))
                : L.t("Нужен Android 13 или новее. На старых версиях поставь русский языком системы, перевод подхватится сам."));
        ru.toggle(Mods.isRussian(this), Mods.localeSupported(), on -> {
            if (Mods.setRussian(this, on)) {
                return true;
            }
            Toast.makeText(this, L.t("Не получилось сменить язык"), Toast.LENGTH_SHORT).show();
            return false;
        });
        g.addView(ru);
        ui.restyle(g);
        return g;
    }

    // ---- оформление ----

    private View themeGroup() {
        final LinearLayout g = ui.column();

        accentRow = ui.new Row(L.t("Акцентный цвет"), "");
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
        g.addView(custom);

        darkRow = ui.new Row(L.t("Тёмная тема"), "");
        darkRow.chevron();
        darkRow.setOnClickListener(v -> openTheme(true));
        g.addView(darkRow);
        lightRow = ui.new Row(L.t("Светлая тема"), "");
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
        Ui.Row copy = ui.new Row(L.t("Скопировать мою тему"), L.t("Текстом, чтобы отправить другу."));
        copy.setOnClickListener(v -> {
            String theme = Theme.export();
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("MargyC theme", theme));
            ui.new Sheet(L.t("Тема скопирована")).message(theme.trim()).button(L.t("Готово"), true, null).show();
        });
        g.addView(copy);
        Ui.Row paste = ui.new Row(L.t("Вставить тему"), L.t("Из буфера обмена. Тему может написать и Claude, просто попроси."));
        paste.setOnClickListener(v -> importTheme());
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
        g.addView(prompt);

        presetRow = ui.new Row(L.t("Пресет"), "");
        presetRow.chevron();
        presetRow.setOnClickListener(v -> choosePreset());
        g.addView(presetRow);

        presetTextRow = ui.new Row(L.t("Текст пресета"), "");
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
                L.t("Сидит на поле ввода в чате и в Code и поднимается вместе с ним. Зажми и тащи, чтобы пересадить, коснись — подпрыгнет."));
        pet.toggle(Pet.enabled(), true, on -> {
            try {
                Pet.setEnabled(on);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        g.addView(pet);
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
        fakeGroup.addView(add);
        Ui.Row paste = ui.new Row(L.t("Вставить модель"), L.t("Из буфера обмена: модель, которой с тобой поделились."));
        paste.setOnClickListener(v -> importFake());
        fakeGroup.addView(paste);
        Ui.Row journal = ui.new Row(L.t("Журнал"), L.t("Что делали хуки мемных моделей. Если что-то не работает, скопируй и отправь автору."));
        journal.chevron();
        journal.setOnClickListener(v -> {
            final String text = Fake.journal();
            ui.new Sheet(L.t("Журнал мемных моделей")).message(text)
                    .button(L.t("Скопировать"), false, () -> {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("MargyC journal", text));
                        Toast.makeText(this, L.t("Скопировано"), Toast.LENGTH_SHORT).show();
                    })
                    .button(L.t("Закрыть"), true, null).show();
        });
        fakeGroup.addView(journal);
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
        Ui.Row install = ui.new Row(L.t("Установить мод"), L.t("Файл .mcmod: скомпилированный мод с manifest.json."));
        install.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            try {
                startActivityForResult(pick, PICK_PLUGIN);
            } catch (Exception e) {
                Toast.makeText(this, L.t("Нет приложения для выбора файла"), Toast.LENGTH_SHORT).show();
            }
        });
        pluginGroup.addView(install);
        Ui.Row docs = ui.new Row(L.t("Как написать свой мод"), L.t("Документация и пример на GitHub."));
        docs.chevron();
        docs.setOnClickListener(v -> open(GITHUB + "/blob/main/docs/plugins.md"));
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
        g.addView(open);
        ui.restyle(g);
        return g;
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
    }

    private String count(boolean dark) {
        int n = Theme.overrides(dark).size();
        return n == 0 ? L.t("Как в Claude") : L.t("Изменено цветов: ") + n;
    }
}
