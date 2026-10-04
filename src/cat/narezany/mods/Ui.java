package cat.narezany.mods;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Дизайн экранов мода под Claude: цвета из палитры приложения (с учётом своей темы
 * и акцента), шрифт Anthropic Sans, карточки, переключатели и окна как в приложении.
 */
final class Ui {
    private static final String FONT = "composeResources/claude.theme.generated.resources/font/anthropic_sans.ttf";

    final Context ctx;
    final boolean night;
    final int bg, card, pressed, text, secondary, section, accent, divider;
    final Typeface regular, medium;

    Ui(Context ctx) {
        this.ctx = ctx;
        night = (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        // цвета тональной палитры Claude, которые берут темы (тёмная / светлая), с заменами пользователя
        bg = c(0xFF151515, 0xFFF9F9F7);
        card = c(0xFF20201F, 0xFFFFFFFF);
        text = c(0xFFF9F9F7, 0xFF151515);
        secondary = c(0xFF97958D, 0xFF6D6B67);
        section = c(0xFFC3C2B7, 0xFF454442);
        divider = c(0xFF383835, 0xFFE1E0D9);
        accent = c(0xFF6DA7EC, 0xFF2A78D6);
        pressed = night ? 0x22FFFFFF : 0x14000000;
        regular = font(400);
        medium = font(500);
    }

    private int c(int dark, int light) {
        return night ? Theme.resolve(dark, true) : Theme.resolve(light, false);
    }

    int dp(float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    private Typeface font(int weight) {
        try {
            return new Typeface.Builder(ctx.getAssets(), FONT).setFontVariationSettings("'wght' " + weight).build();
        } catch (Throwable t) {
            return Typeface.create(weight >= 500 ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL);
        }
    }

    TextView label(String s, int sp, int color, Typeface tf) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    RippleDrawable ripple(GradientDrawable content, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(pressed), content, round(Color.WHITE, radiusDp));
    }

    // ---- экран ----

    /** Окно от края до края, прозрачные системные панели под цвет темы. */
    void setupWindow(Activity a) {
        Window w = a.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(bg));
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | (night ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(night ? 0 : light, light);
            }
        }
    }

    /** Заголовок по центру со стрелкой назад + прокручиваемое содержимое. */
    View screen(final Activity a, String title, View content) {
        FrameLayout bar = new FrameLayout(ctx);
        ArrowView back = new ArrowView(ctx, text, dp(2));
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(pressed), null, null));
        back.setOnClickListener(v -> a.finish());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START | Gravity.CENTER_VERTICAL);
        lp.leftMargin = dp(8);
        bar.addView(back, lp);
        bar.addView(label(title, 20, text, medium), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        final ScrollView scroll = new ScrollView(ctx);
        scroll.setClipToPadding(false);
        scroll.addView(content);

        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), 0);
            scroll.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        return root;
    }

    /**
     * Экран с плашкой «перезапустите Claude», прибитой к низу: появляется, когда накопились изменения,
     * которые применятся только после перезапуска.
     */
    View withRestartBanner(final Activity a, final View screen) {
        final FrameLayout frame = new FrameLayout(ctx);
        frame.addView(screen, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        final LinearLayout banner = new LinearLayout(ctx);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(20), dp(10), dp(10), dp(10));
        GradientDrawable bg = round(text, 26);
        banner.setBackground(bg);
        banner.setElevation(dp(6));
        TextView msg = label("Перезапустите Claude, чтобы применить изменения", 15, this.bg, regular);
        msg.setLineSpacing(dp(2), 1f);
        banner.addView(msg, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView go = label("Перезапустить", 15, Color.WHITE, medium);
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(16), 0, dp(16), 0);
        go.setBackground(ripple(round(accent, 20), 20));
        go.setOnClickListener(v -> Mods.restart(a));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        gl.leftMargin = dp(12);
        banner.addView(go, gl);

        final FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bl.leftMargin = bl.rightMargin = dp(16);
        bl.bottomMargin = dp(16);
        frame.addView(banner, bl);
        banner.setVisibility(Mods.restartNeeded() ? View.VISIBLE : View.GONE);

        frame.setOnApplyWindowInsetsListener((v, insets) -> {
            bl.bottomMargin = dp(16) + insets.getSystemWindowInsetBottom();
            banner.setLayoutParams(bl);
            screen.dispatchApplyWindowInsets(insets);
            return insets.consumeSystemWindowInsets();
        });
        final Runnable show = () -> {
            if (banner.getVisibility() != View.VISIBLE) {
                banner.setVisibility(View.VISIBLE);
                banner.setTranslationY(dp(120));
                banner.animate().translationY(0).setDuration(220).start();
            }
        };
        frame.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                Mods.onRestartNeeded(show, true);
                if (Mods.restartNeeded()) {
                    banner.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                Mods.onRestartNeeded(show, false);
            }
        });
        return frame;
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    TextView sectionTitle(String s) {
        TextView t = label(s, 15, section, regular);
        t.setPadding(dp(12), dp(20), 0, dp(10));
        return t;
    }

    /** Скругления как в «Выборе модели»: большие снаружи группы, маленькие между строками. */
    void restyle(LinearLayout g) {
        int first = -1, last = -1;
        for (int i = 0; i < g.getChildCount(); i++) {
            if (g.getChildAt(i).getVisibility() == View.VISIBLE) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        for (int i = 0; i < g.getChildCount(); i++) {
            View row = g.getChildAt(i);
            float big = dp(20), small = dp(4);
            float top = i == first ? big : small, bottom = i == last ? big : small;
            float[] radii = {top, top, top, top, bottom, bottom, bottom, bottom};
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(card);
            shape.setCornerRadii(radii);
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(Color.WHITE);
            mask.setCornerRadii(radii);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(pressed), shape, mask));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = i == first ? 0 : dp(2);
            row.setLayoutParams(lp);
        }
    }

    /** Строка карточки: заголовок, пояснение и элемент справа. */
    final class Row extends LinearLayout {
        final TextView title;
        final TextView subtitle;

        Row(String t, String sub) {
            super(ctx);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(64));
            setPadding(dp(20), dp(16), dp(16), dp(16));
            LinearLayout texts = column();
            title = label(t, 17, text, regular);
            texts.addView(title);
            subtitle = label(sub, 14, secondary, regular);
            subtitle.setLineSpacing(dp(2), 1f);
            subtitle.setPadding(0, dp(6), dp(12), 0);
            subtitle.setVisibility(sub.isEmpty() ? GONE : VISIBLE);
            texts.addView(subtitle);
            addView(texts, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        void sub(String s, int maxLines) {
            subtitle.setText(s);
            subtitle.setMaxLines(maxLines);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setVisibility(s.isEmpty() ? GONE : VISIBLE);
        }

        Toggle toggle(boolean on, boolean enabled, Toggle.Listener listener) {
            final Toggle t = new Toggle(ctx, Ui.this, on, listener);
            t.setEnabled(enabled);
            t.setClickable(enabled);
            addView(t, new LayoutParams(dp(52), dp(32)));
            if (enabled) {
                setOnClickListener(v -> t.flip());
            } else {
                setAlpha(0.6f);
            }
            return t;
        }

        void chevron() {
            addView(label("›", 26, secondary, regular));
        }

        View swatch(int color) {
            View s = new View(ctx);
            GradientDrawable g = round(color, 14);
            g.setStroke(dp(1), divider);
            s.setBackground(g);
            LayoutParams lp = new LayoutParams(dp(28), dp(28));
            lp.rightMargin = dp(4);
            addView(s, lp);
            return s;
        }
    }

    // ---- окна ----

    /** Окно в стиле Claude: тёмная карточка со скруглением, заголовок, содержимое, кнопки. */
    final class Sheet {
        final Dialog dialog;
        final LinearLayout body;
        private LinearLayout buttons;

        Sheet(String title) {
            dialog = new Dialog(ctx);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            body = column();
            body.setPadding(dp(24), dp(24), dp(24), dp(16));
            body.setBackground(round(bg, 28));
            if (title != null) {
                TextView t = label(title, 21, text, medium);
                t.setPadding(0, 0, 0, dp(16));
                body.addView(t);
            }
        }

        Sheet message(String s) {
            TextView m = label(s, 16, secondary, regular);
            m.setLineSpacing(dp(4), 1f);
            m.setPadding(0, 0, 0, dp(8));
            body.addView(m);
            return this;
        }

        /** Пункт списка: выбранный подсвечивается акцентом и галочкой, как в приложении. */
        View item(String title, String sub, boolean selected, View.OnClickListener click) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(56));
            row.setPadding(dp(4), dp(10), dp(4), dp(10));
            row.setBackground(ripple(round(Color.TRANSPARENT, 12), 12));
            LinearLayout texts = column();
            texts.addView(label(title, 18, selected ? accent : text, regular));
            if (sub != null && !sub.isEmpty()) {
                TextView s = label(sub, 14, secondary, regular);
                s.setPadding(0, dp(4), 0, 0);
                s.setMaxLines(2);
                s.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(s);
            }
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (selected) {
                row.addView(new CheckView(ctx, accent, dp(2)), new LinearLayout.LayoutParams(dp(24), dp(24)));
            }
            row.setOnClickListener(click);
            body.addView(row);
            return row;
        }

        Sheet view(View v) {
            body.addView(v);
            return this;
        }

        /** Кнопки справа: основная — светлая «таблетка», как «Новый проект» в приложении. */
        Sheet button(String label, boolean primary, final Runnable action) {
            if (buttons == null) {
                buttons = new LinearLayout(ctx);
                buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                buttons.setPadding(0, dp(16), 0, dp(4));
                body.addView(buttons);
            }
            TextView b = Ui.this.label(label, 16, primary ? bg : text, medium);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(20), 0, dp(20), 0);
            b.setBackground(ripple(round(primary ? text : Color.TRANSPARENT, 24), 24));
            b.setOnClickListener(v -> {
                if (action != null) {
                    action.run();
                }
                dialog.dismiss();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
            lp.leftMargin = dp(8);
            buttons.addView(b, lp);
            return this;
        }

        Dialog show() {
            ScrollView scroll = new ScrollView(ctx);
            scroll.addView(body);
            FrameLayout frame = new FrameLayout(ctx);
            frame.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            dialog.setContentView(frame);
            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout(Math.min(ctx.getResources().getDisplayMetrics().widthPixels - dp(48), dp(440)),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                w.setDimAmount(0.45f);
            }
            dialog.show();
            return dialog;
        }
    }

    EditText field(String value, String hint, boolean multiline) {
        EditText e = new EditText(ctx);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(secondary);
        e.setTextColor(text);
        e.setTypeface(regular);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setPadding(dp(16), dp(12), dp(16), dp(12));
        e.setBackground(round(card, 16));
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setMinLines(5);
            e.setMaxLines(12);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
        }
        return e;
    }

    interface ColorListener {
        void onColor(int color);
    }

    /** Выбор цвета: насыщенность/яркость, оттенок, HEX и готовые цвета. */
    void pickColor(String title, int initial, final ColorListener listener, final Runnable reset) {
        final Sheet sheet = new Sheet(title);
        final float[] hsv = new float[3];
        Color.colorToHSV(initial, hsv);
        final int[] current = {initial | 0xFF000000};

        final View preview = new View(ctx);
        final EditText hex = field(String.format("%06X", current[0] & 0xFFFFFF), "RRGGBB", false);
        final SvView sv = new SvView(ctx);
        final HueView hue = new HueView(ctx);
        final boolean[] fromText = {false};

        final Runnable update = () -> {
            current[0] = Color.HSVToColor(hsv);
            preview.setBackground(round(current[0], 14));
            sv.set(hsv);
            hue.set(hsv[0]);
            if (!fromText[0]) {
                hex.setText(String.format("%06X", current[0] & 0xFFFFFF));
            }
        };
        sv.listener = (s, v) -> {
            hsv[1] = s;
            hsv[2] = v;
            update.run();
        };
        hue.listener = h -> {
            hsv[0] = h;
            update.run();
        };
        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable e) {
                String s = e.toString().replace("#", "").trim();
                if (s.length() == 6 && hex.hasFocus()) {
                    try {
                        Color.colorToHSV(0xFF000000 | Integer.parseInt(s, 16), hsv);
                        fromText[0] = true;
                        update.run();
                        fromText[0] = false;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        });

        sheet.view(sv);
        sv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
        hl.topMargin = dp(14);
        sheet.view(hue);
        hue.setLayoutParams(hl);

        LinearLayout line = new LinearLayout(ctx);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(14), 0, 0);
        line.addView(preview, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        fl.leftMargin = dp(12);
        line.addView(hex, fl);
        sheet.view(line);

        int[] presets = {0xFFD97757, 0xFF74ABE2, 0xFF8FD2B1, 0xFFE57373, 0xFFBA68C8, 0xFF7986CB,
                0xFF4DB6AC, 0xFFFFB74D, 0xFFF06292, 0xFFA1887F};
        LinearLayout swatches = new LinearLayout(ctx);
        swatches.setPadding(0, dp(14), 0, 0);
        for (final int p : presets) {
            View s = new View(ctx);
            s.setBackground(round(p, 14));
            s.setOnClickListener(v -> {
                Color.colorToHSV(p, hsv);
                update.run();
            });
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, dp(28), 1f);
            sl.rightMargin = dp(6);
            swatches.addView(s, sl);
        }
        sheet.view(swatches);
        update.run();

        if (reset != null) {
            sheet.button("Сбросить", false, reset);
        }
        sheet.button("Отмена", false, null);
        sheet.button("Готово", true, () -> listener.onColor(current[0]));
        sheet.show();
    }

    // ---- рисованные элементы ----

    /** Переключатель в цветах приложения. */
    static final class Toggle extends View {
        interface Listener {
            /** false — изменение не удалось, переключатель останется как был. */
            boolean onChange(boolean on);
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Ui ui;
        private final Listener listener;
        private boolean on;
        private float pos;

        Toggle(Context ctx, Ui ui, boolean on, Listener listener) {
            super(ctx);
            this.ui = ui;
            this.on = on;
            this.pos = on ? 1f : 0f;
            this.listener = listener;
            setOnClickListener(v -> flip());
        }

        boolean isOn() {
            return on;
        }

        void flip() {
            boolean next = !on;
            if (!listener.onChange(next)) {
                return;
            }
            on = next;
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(pos, on ? 1f : 0f);
            a.setDuration(150);
            a.addUpdateListener(an -> {
                pos = (float) an.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            paint.setColor(blend(ui.divider, ui.accent, pos));
            c.drawRoundRect(0, 0, w, h, r, r, paint);
            float thumb = r - ui.dp(4) + ui.dp(2) * pos;
            paint.setColor(blend(ui.secondary, Color.WHITE, pos));
            c.drawCircle(r + (w - 2 * r) * pos, r, thumb, paint);
        }

        private static int blend(int a, int b, float t) {
            return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                    Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                    Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
        }
    }

    /** Стрелка «назад». */
    static final class ArrowView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        ArrowView(Context ctx, int color, float stroke) {
            super(ctx);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            setContentDescription("Назад");
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = getResources().getDisplayMetrics().density, cx = getWidth() / 2f, cy = getHeight() / 2f;
            path.reset();
            path.moveTo(cx + 9 * s, cy);
            path.lineTo(cx - 9 * s, cy);
            path.moveTo(cx - 2 * s, cy - 7 * s);
            path.lineTo(cx - 9 * s, cy);
            path.lineTo(cx - 2 * s, cy + 7 * s);
            c.drawPath(path, paint);
        }
    }

    /** Галочка выбранного пункта. */
    static final class CheckView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        CheckView(Context ctx, int color, float stroke) {
            super(ctx);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            path.reset();
            path.moveTo(w * 0.15f, h * 0.52f);
            path.lineTo(w * 0.40f, h * 0.76f);
            path.lineTo(w * 0.88f, h * 0.26f);
            c.drawPath(path, paint);
        }
    }

    /** Квадрат насыщенности/яркости. */
    interface SvListener {
        void on(float s, float v);
    }

    interface HueListener {
        void on(float h);
    }

    final class SvView extends View {
        SvListener listener;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float h, s, v;

        SvView(Context ctx) {
            super(ctx);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp(2));
            ring.setColor(Color.WHITE);
        }

        void set(float[] hsv) {
            h = hsv[0];
            s = hsv[1];
            v = hsv[2];
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), ht = getHeight(), r = dp(16);
            Shader sat = new LinearGradient(0, 0, w, 0, Color.WHITE, Color.HSVToColor(new float[] {h, 1, 1}), Shader.TileMode.CLAMP);
            Shader val = new LinearGradient(0, 0, 0, ht, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP);
            paint.setShader(new ComposeShader(val, sat, PorterDuff.Mode.MULTIPLY));
            c.drawRoundRect(0, 0, w, ht, r, r, paint);
            c.drawCircle(s * w, (1 - v) * ht, dp(9), ring);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float ns = Math.max(0, Math.min(1, e.getX() / getWidth()));
            float nv = 1 - Math.max(0, Math.min(1, e.getY() / getHeight()));
            if (listener != null) {
                listener.on(ns, nv);
            }
            return true;
        }
    }

    /** Полоса оттенка. */
    final class HueView extends View {
        HueListener listener;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float h;

        HueView(Context ctx) {
            super(ctx);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp(2));
            ring.setColor(Color.WHITE);
        }

        void set(float hue) {
            h = hue;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), ht = getHeight();
            int[] colors = new int[7];
            for (int i = 0; i < 7; i++) {
                colors[i] = Color.HSVToColor(new float[] {i * 60f % 360, 1, 1});
            }
            paint.setShader(new LinearGradient(0, 0, w, 0, colors, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(0, 0, w, ht, ht / 2, ht / 2, paint);
            c.drawCircle(h / 360f * w, ht / 2, ht / 2 - dp(1), ring);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            if (listener != null) {
                listener.on(Math.max(0, Math.min(359.9f, e.getX() / getWidth() * 360f)));
            }
            return true;
        }
    }
}
