package cat.narezany.mods;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Поиск по чату, как Ctrl+F на компьютере: пункт «Найти в чате» в меню «⋮» открывает беседу целиком
 * из кэша приложения, совпадения подсвечиваются, стрелки переходят между ними.
 */
final class ChatSearch {
    static final String EXTRA = "cat.narezany.mods.search";
    private static volatile Export.Conversation pending;

    private ChatSearch() {}

    static boolean enabled() {
        try {
            return Mods.prefs().getBoolean("search_on", true);
        } catch (Exception e) {
            return false;
        }
    }

    static void setEnabled(boolean on) throws Exception {
        Mods.prefs().edit().putBoolean("search_on", on).apply();
    }

    /** Из меню чата: беседа слишком большая для Intent, поэтому передаётся через поле. */
    static void open(Export.Conversation c) throws Exception {
        pending = c;
        Intent i = new Intent().setClassName(Mods.app(), ModsActivity.class.getName())
                .putExtra(EXTRA, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Mods.app().startActivity(i);
    }

    static Export.Conversation take() {
        Export.Conversation c = pending;
        pending = null;
        return c;
    }

    /** Экран поиска для ModsActivity. */
    static View screen(final Activity a, final Ui ui, Export.Conversation c) {
        final List<TextView> views = new ArrayList<TextView>();
        final List<String> texts = new ArrayList<String>();
        final List<int[]> hits = new ArrayList<int[]>(); // {сообщение, начало}
        final int[] current = {-1};

        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        Ui.ArrowView back = new Ui.ArrowView(a, ui.text, ui.dp(2));
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(ui.pressed), null, null));
        back.setOnClickListener(v -> a.finish());
        bar.addView(back, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        final EditText field = ui.field("", L.t("Найти в чате"), false);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, ui.dp(48), 1f);
        flp.leftMargin = ui.dp(4);
        bar.addView(field, flp);
        final TextView count = ui.label("", 14, ui.secondary, ui.regular);
        count.setPadding(ui.dp(8), 0, ui.dp(4), 0);
        bar.addView(count);
        TextView up = iconButton(a, ui, 0xe316);
        TextView down = iconButton(a, ui, 0xe313);
        bar.addView(up, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(48)));
        bar.addView(down, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(48)));

        final LinearLayout list = ui.column();
        list.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(24));
        TextView title = ui.label(c.title.isEmpty() ? "Claude" : c.title, 18, ui.text, ui.medium);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, ui.dp(4), 0, ui.dp(8));
        list.addView(title);
        for (Export.Message m : c.messages) {
            boolean me = Export.isUser(m.sender);
            TextView t = ui.label(m.text, 16, ui.text, ui.regular);
            t.setLineSpacing(ui.dp(4), 1f);
            t.setTextIsSelectable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    me ? LinearLayout.LayoutParams.WRAP_CONTENT : LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = ui.dp(me ? 16 : 10);
            if (me) {
                t.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
                t.setBackground(ui.round(ui.card, 20));
                t.setMaxWidth(Math.round(a.getResources().getDisplayMetrics().widthPixels * 0.8f));
                lp.gravity = Gravity.END;
            } else {
                t.setPadding(ui.dp(4), 0, ui.dp(4), 0);
            }
            list.addView(t, lp);
            views.add(t);
            texts.add(m.text == null ? "" : m.text);
        }
        final ScrollView scroll = new ScrollView(a);
        scroll.setClipToPadding(false);
        scroll.addView(list);

        final int soft = (ui.accent & 0x00FFFFFF) | 0x55000000;
        final Runnable paint = () -> {
            String q = field.getText().toString().trim().toLowerCase(Locale.ROOT);
            for (int i = 0; i < views.size(); i++) {
                String s = texts.get(i);
                if (q.isEmpty()) {
                    views.get(i).setText(s);
                    continue;
                }
                SpannableString sp = new SpannableString(s);
                String low = s.toLowerCase(Locale.ROOT);
                for (int k = low.indexOf(q); k >= 0; k = low.indexOf(q, k + q.length())) {
                    boolean cur = current[0] >= 0 && current[0] < hits.size()
                            && hits.get(current[0])[0] == i && hits.get(current[0])[1] == k;
                    sp.setSpan(new BackgroundColorSpan(cur ? ui.accent : soft), k, k + q.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (cur) {
                        sp.setSpan(new ForegroundColorSpan(Color.WHITE), k, k + q.length(),
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                views.get(i).setText(sp);
            }
            count.setText(q.isEmpty() ? "" : hits.isEmpty() ? "0" : (current[0] + 1) + "/" + hits.size());
        };
        final Runnable jump = () -> {
            if (current[0] < 0 || current[0] >= hits.size()) {
                return;
            }
            final int[] h = hits.get(current[0]);
            final TextView t = views.get(h[0]);
            t.post(() -> {
                Layout l = t.getLayout();
                int y = t.getTop() + t.getPaddingTop() + (l != null ? l.getLineTop(l.getLineForOffset(h[1])) : 0);
                scroll.smoothScrollTo(0, Math.max(0, y - scroll.getHeight() / 3));
            });
        };
        final Runnable search = () -> {
            hits.clear();
            String q = field.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (!q.isEmpty()) {
                for (int i = 0; i < texts.size(); i++) {
                    String low = texts.get(i).toLowerCase(Locale.ROOT);
                    for (int k = low.indexOf(q); k >= 0; k = low.indexOf(q, k + q.length())) {
                        hits.add(new int[] {i, k});
                    }
                }
            }
            // как в браузере: начинаем с последнего совпадения — ближе к свежим сообщениям
            current[0] = hits.isEmpty() ? -1 : hits.size() - 1;
            paint.run();
            jump.run();
        };
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                search.run();
            }
        });
        View.OnClickListener step = v -> {
            if (hits.isEmpty()) {
                return;
            }
            int d = "up".equals(v.getTag()) ? -1 : 1;
            current[0] = (current[0] + d + hits.size()) % hits.size();
            paint.run();
            jump.run();
        };
        up.setTag("up");
        up.setOnClickListener(step);
        down.setOnClickListener(step);
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                InputMethodManager imm = (InputMethodManager) a.getSystemService(Activity.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });

        final LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.bg);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), 0);
            scroll.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        // в конец, к свежим сообщениям, и сразу клавиатура
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        field.requestFocus();
        field.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) a.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(field, 0);
            }
        }, 200);
        return root;
    }

    private static TextView iconButton(Activity a, Ui ui, int codepoint) {
        TextView b = new TextView(a);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(ui.text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        if (ui.iconFont() != null) {
            b.setTypeface(ui.iconFont());
            b.setText(new String(Character.toChars(codepoint)));
        } else {
            b.setText(codepoint == 0xe316 ? "▲" : "▼");
        }
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(ui.pressed), null, null));
        return b;
    }

    static void notFound() {
        try {
            final android.content.Context ctx = Mods.app();
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(ctx,
                    L.t("Не нашёл этот диалог в кэше приложения. Пролистай его до начала и попробуй ещё раз."),
                    Toast.LENGTH_LONG).show());
        } catch (Exception ignored) {
        }
    }
}
