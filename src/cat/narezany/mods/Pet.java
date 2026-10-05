package cat.narezany.mods;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Мод «Clawd»: питомец сидит на верхней границе поля ввода в чате и в Code, справа. Если поле
 * растёт от длинного сообщения, Clawd поднимается вместе с ним; зажать и потащить — пересесть
 * (вдоль поля и немного выше-ниже), коснуться — анимация заново.
 *
 * Сам Clawd — Compose-функция приложения с его анимациями (та, что на экранах загрузки), её рисует
 * ComposeView мода. Если она не нашлась или упала, Clawd не показывается: своего мод не рисует.
 *
 * Реакции — тоже анимации приложения (кадры Clawd из его же листов): пока Claude отвечает, Clawd печатает
 * на ноутбуке, а когда ответ готов — прыгает или пританцовывает. Что Claude отвечает, видно по кнопке
 * «Остановить» у поля ввода.
 *
 * Где поле ввода: карта узлов semantics из делегата специальных возможностей AndroidComposeView.
 * Без включённых служб он её не обновляет, поэтому перед каждым опросом мод помечает её устаревшей.
 * Поле ввода — самый нижний широкий узел, который специальные возможности считают редактируемым.
 */
public final class Pet {
    private static final String TAG_LAYER = "margyc-clawd";
    private static final String HOST = "androidx.compose.ui.platform.AndroidComposeView";

    private Pet() {}

    static boolean enabled() {
        try {
            return Mods.prefs().getBoolean("pet_on", false);
        } catch (Exception e) {
            return false;
        }
    }

    /** Включение заодно даёт Clawd приложения ещё один шанс, если он когда-то упал. */
    static void setEnabled(boolean on) throws Exception {
        Mods.prefs().edit().putBoolean("pet_on", on).putBoolean("pet_compose_broken", false).apply();
    }

    /** Application.onCreate, после CrashLog. */
    public static void install(Application app) {
        try {
            // прошлый запуск упал, пока рисовался Clawd приложения: больше не пробовать (до выключения-включения)
            if (Mods.prefs().getBoolean("pet_compose_trying", false)) {
                Mods.prefs().edit().putBoolean("pet_compose_trying", false).putBoolean("pet_compose_broken", true).commit();
            }
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityResumed(Activity a) {
                    if (a.getClass().getName().startsWith("cat.narezany.mods.")) {
                        return;
                    }
                    Layer layer = find(a);
                    if (!enabled()) {
                        if (layer != null) {
                            ((ViewGroup) layer.getParent()).removeView(layer);
                        }
                        return;
                    }
                    if (layer == null) {
                        ViewGroup content = a.findViewById(android.R.id.content);
                        if (content == null) {
                            return;
                        }
                        layer = new Layer(a);
                        if (layer.app == null) {
                            return; // Clawd приложения недоступен: своего не рисуем
                        }
                        content.addView(layer, new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    }
                    layer.start();
                }

                @Override
                public void onActivityPaused(Activity a) {
                    Layer layer = find(a);
                    if (layer != null) {
                        layer.stop();
                    }
                }

                @Override
                public void onActivityCreated(Activity a, Bundle b) {}

                @Override
                public void onActivityStarted(Activity a) {}

                @Override
                public void onActivityStopped(Activity a) {}

                @Override
                public void onActivitySaveInstanceState(Activity a, Bundle b) {}

                @Override
                public void onActivityDestroyed(Activity a) {}
            });
        } catch (Throwable t) {
            Log.e(Mods.TAG, "Pet.install", t);
        }
    }

    private static Layer find(Activity a) {
        View v = a.getWindow().getDecorView().findViewWithTag(TAG_LAYER);
        return v instanceof Layer ? (Layer) v : null;
    }

    /** Прозрачный слой поверх экрана: касания мимо Clawd уходят приложению. */
    static final class Layer extends FrameLayout {
        private final Activity activity;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Box box;
        private final int w, h;
        private final Rect rect = new Rect();
        private final int[] loc = new int[2];
        private final Semantics semantics = new Semantics();
        private View host;
        private boolean running;
        private int polls;
        private String state = "";
        float frac, lift;
        private final View app;
        private Frames reaction;
        private int busy; // сколько опросов подряд Claude отвечает (+) или нет (-)
        private boolean working;

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }
                try {
                    place();
                } catch (Throwable t) {
                    Log.e(Mods.TAG, "Clawd place", t);
                }
                handler.postDelayed(this, 50);
            }
        };

        Layer(Activity a) {
            super(a);
            activity = a;
            setTag(TAG_LAYER);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            w = dp(112); // ClawdFraming 55×37
            h = dp(76);
            try {
                frac = Mods.prefs().getFloat("pet_x", 0.9f);
                lift = Mods.prefs().getFloat("pet_lift", 0f);
            } catch (Exception e) {
                frac = 0.9f;
            }
            box = new Box(a);
            app = clawd(a);
            if (app != null) {
                box.addView(app, new FrameLayout.LayoutParams(w, h));
            }
            box.setVisibility(INVISIBLE);
            addView(box, new FrameLayout.LayoutParams(w, h));
        }

        void start() {
            if (!running) {
                running = true;
                handler.post(tick);
            }
        }

        void stop() {
            running = false;
            handler.removeCallbacks(tick);
        }

        int dp(float v) {
            return Math.round(v * getResources().getDisplayMetrics().density);
        }

        /** Сидеть на рамке: от неё вверх на высоту Clawd, лапки чуть заходят на рамку. */
        float anchorY;

        private void place() {
            if (getWidth() == 0) {
                return;
            }
            polls++;
            int top = composerTop();
            if (top == Integer.MIN_VALUE) {
                box.setVisibility(INVISIBLE);
                return;
            }
            box.setVisibility(VISIBLE);
            if (polls % 10 == 0) {
                react();
            }
            anchorY = top - h + dp(4);
            if (!box.dragging) {
                box.setTranslationX(frac * (getWidth() - w));
                float y = anchorY + lift * getResources().getDisplayMetrics().density;
                if (Math.abs(box.getTranslationY() - y) > 1) {
                    box.setTranslationY(y);
                }
            }
        }

        /** Claude отвечает — Clawd печатает; закончил — прыгает или танцует, потом снова сам по себе. */
        private void react() {
            if (!reacts() || host == null || !semantics.works) {
                return;
            }
            boolean now = semantics.has(host, stopLabel(activity));
            busy = now ? Math.max(1, busy + 1) : Math.min(-1, busy - 1);
            if (!working && busy >= 2) {
                working = true;
                Fake.log("clawd: Claude is answering");
                play(Frames.make(getContext(), "Laptop", 17, 34, 0));
            } else if (working && busy <= -2) {
                working = false;
                Fake.log("clawd: answer is ready");
                boolean dance = Math.random() < 0.4;
                play(dance ? Frames.make(getContext(), "Dancing", 8, 39, 1) : Frames.make(getContext(), "Jumping", 0, 20, 2));
            }
        }

        /** Реакция поверх Clawd приложения (он прячется, но живёт дальше); null — убрать реакцию. */
        void play(Frames f) {
            if (reaction != null) {
                box.removeView(reaction);
                reaction = null;
            }
            if (f != null) {
                reaction = f;
                f.done = () -> play(null);
                box.addView(f, new FrameLayout.LayoutParams(w, h));
            }
            if (app != null) {
                app.setVisibility(reaction != null ? INVISIBLE : VISIBLE);
            }
        }

        /** Верх рамки поля ввода в координатах слоя или MIN_VALUE, если поля на экране нет. */
        private int composerTop() {
            if (host == null || !host.isAttachedToWindow()) {
                host = findHost(activity.getWindow().getDecorView());
                semantics.reset();
                if (host == null) {
                    journal("no Compose view");
                    return Integer.MIN_VALUE;
                }
            }
            getLocationOnScreen(loc);
            int layerTop = loc[1];
            host.getLocationOnScreen(loc);
            int hostTop = loc[1];
            if (semantics.works) {
                Rect r = semantics.editable(host, polls % 20 == 0);
                if (r != null) {
                    journal("composer from semantics");
                    int textTop = r.top + hostTop - layerTop;
                    remember(textTop);
                    return textTop - dp(12); // от текста до рамки поля
                }
                if (semantics.works) {
                    journal("no composer on screen");
                    return Integer.MIN_VALUE; // дерево есть, а поля ввода нет: другой экран
                }
            }
            // запасной путь: где поле было в последний раз, от низа экрана
            journal("composer from saved distance");
            int distance;
            try {
                distance = Mods.prefs().getInt("pet_distance", dp(92));
            } catch (Exception e) {
                distance = dp(92);
            }
            return getHeight() - bottomInset() - distance - dp(12);
        }

        private void journal(String s) {
            if (!s.equals(state)) {
                state = s;
                Fake.log("clawd: " + s);
            }
        }

        /** Расстояние от текста поля до низа (над клавиатурой или панелью навигации). */
        private void remember(int textTop) {
            int distance = getHeight() - bottomInset() - textTop;
            if (distance > 0 && distance < getHeight() / 2 && polls % 60 == 0) {
                try {
                    Mods.prefs().edit().putInt("pet_distance", distance).apply();
                } catch (Exception ignored) {
                }
            }
        }

        private int bottomInset() {
            WindowInsets in = getRootWindowInsets();
            if (in == null) {
                return 0;
            }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                return in.getInsets(WindowInsets.Type.ime() | WindowInsets.Type.navigationBars()).bottom;
            }
            return in.getSystemWindowInsetBottom();
        }
    }

    /**
     * Карта узлов semantics делегата специальных возможностей: id узла -> (узел, границы IntRect
     * в координатах AndroidComposeView). Редактируемость узла — по AccessibilityNodeInfo того же id.
     */
    static final class Semantics {
        boolean works = !Names.SEMANTICS_DELEGATE.isEmpty();
        private Object delegate;
        private Field stale;
        private Method nodes;
        private int cached = Integer.MIN_VALUE;
        private final Rect rect = new Rect();

        void reset() {
            delegate = null;
            cached = Integer.MIN_VALUE;
        }

        /** Границы поля ввода в координатах host или null. full — искать заново, а не проверять прошлое. */
        Rect editable(View host, boolean full) {
            try {
                if (delegate == null) {
                    Class<?> dc = Class.forName(Names.SEMANTICS_DELEGATE);
                    for (Class<?> c = host.getClass(); c != null && delegate == null; c = c.getSuperclass()) {
                        for (Field f : c.getDeclaredFields()) {
                            if (f.getType() == dc) {
                                f.setAccessible(true);
                                delegate = f.get(host);
                            }
                        }
                    }
                    if (delegate == null) {
                        throw new IllegalStateException("нет делегата у AndroidComposeView");
                    }
                    stale = dc.getDeclaredField(Names.SEMANTICS_STALE);
                    stale.setAccessible(true);
                    nodes = dc.getDeclaredMethod(Names.SEMANTICS_NODES);
                    nodes.setAccessible(true);
                }
                load();
                AccessibilityNodeProvider provider = host.getAccessibilityNodeProvider();
                int best = Integer.MIN_VALUE;
                Rect bestRect = null;
                for (int i = 0; i < values.length && i < keys.length; i++) {
                    if (values[i] == null || !bounds(values[i]) || !candidate(host)) {
                        continue;
                    }
                    int id = keys[i];
                    boolean editable;
                    if (id == cached && !full) {
                        editable = true;
                    } else {
                        AccessibilityNodeInfo n = provider != null ? provider.createAccessibilityNodeInfo(id) : null;
                        editable = n != null && n.isEditable();
                    }
                    if (editable && (bestRect == null || rect.bottom > bestRect.bottom)) {
                        best = id;
                        bestRect = new Rect(rect);
                    }
                }
                cached = best;
                return bestRect;
            } catch (Throwable t) {
                Log.e(Mods.TAG, "Clawd: semantics", t);
                Fake.log("clawd: semantics failed: " + t);
                works = false;
                return null;
            }
        }

        private int[] keys;
        private Object[] values;

        /** Свежая карта узлов: ключи — id, значения — узел с границами. */
        private void load() throws Exception {
            stale.setBoolean(delegate, true);
            Object map = nodes.invoke(delegate);
            keys = null;
            values = null;
            for (Class<?> c = map.getClass(); c != null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    f.setAccessible(true);
                    if (f.getType() == int[].class) {
                        keys = (int[]) f.get(map);
                    } else if (f.getType() == Object[].class) {
                        values = (Object[]) f.get(map);
                    }
                }
            }
            if (keys == null || values == null) {
                throw new IllegalStateException("не разобрал карту узлов");
            }
        }

        /** Есть ли в нижней половине экрана узел с таким описанием (кнопка «Остановить» у поля ввода). */
        boolean has(View host, String description) {
            if (delegate == null || description == null || description.isEmpty()) {
                return false;
            }
            try {
                load();
                AccessibilityNodeProvider provider = host.getAccessibilityNodeProvider();
                if (provider == null) {
                    return false;
                }
                for (int i = 0; i < values.length && i < keys.length; i++) {
                    if (values[i] == null || !bounds(values[i]) || rect.top < host.getHeight() / 2
                            || rect.width() > host.getWidth() / 3) {
                        continue; // кнопка: маленькая и внизу
                    }
                    AccessibilityNodeInfo n = provider.createAccessibilityNodeInfo(keys[i]);
                    CharSequence d = n != null ? n.getContentDescription() : null;
                    if (d != null && description.contentEquals(d)) {
                        return true;
                    }
                }
            } catch (Throwable t) {
                Log.e(Mods.TAG, "Clawd: stop button", t);
            }
            return false;
        }

        /** Широкий узел в нижних двух третях экрана. */
        private boolean candidate(View host) {
            return rect.width() > host.getWidth() / 2 && rect.top > host.getHeight() / 3 && rect.height() > 0;
        }

        private Class<?> valueClass;
        private Field rectField;
        private Field[] sides;

        /** Границы из значения карты: поле-объект с четырьмя int (left, top, right, bottom). */
        private boolean bounds(Object value) throws Exception {
            if (value.getClass() != valueClass) {
                valueClass = value.getClass();
                rectField = null;
                for (Field f : valueClass.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) {
                        continue;
                    }
                    List<Field> ints = new ArrayList<Field>();
                    for (Field g : f.getType().getDeclaredFields()) {
                        if (!Modifier.isStatic(g.getModifiers()) && g.getType() == int.class) {
                            ints.add(g);
                        }
                    }
                    if (ints.size() == 4) {
                        java.util.Collections.sort(ints, (x, y) -> x.getName().compareTo(y.getName()));
                        f.setAccessible(true);
                        for (Field g : ints) {
                            g.setAccessible(true);
                        }
                        rectField = f;
                        sides = ints.toArray(new Field[4]);
                        break;
                    }
                }
            }
            if (rectField == null) {
                return false;
            }
            Object r = rectField.get(value);
            if (r == null) {
                return false;
            }
            rect.set(sides[0].getInt(r), sides[1].getInt(r), sides[2].getInt(r), sides[3].getInt(r));
            return true;
        }
    }

    private static View findHost(View v) {
        if (v instanceof Layer) {
            return null; // в Clawd свой AndroidComposeView
        }
        if (v.getClass().getName().equals(HOST) && v.isShown()) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) { // сверху вниз: диалоги и шторки позже
                View found = findHost(g.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Clawd: зажать и тащить (вдоль поля и немного выше-ниже), короткое касание — анимация сначала. */
    static final class Box extends FrameLayout {
        boolean dragging;
        private float downX, downY, startX, startY;
        private boolean moved;

        Box(Context ctx) {
            super(ctx);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            return true; // касания Clawd не уходят в его ComposeView
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            Layer layer = (Layer) getParent();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = getTranslationX();
                    startY = getTranslationY();
                    moved = false;
                    dragging = true;
                    animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (Math.hypot(dx, dy) > getWidth() / 8f) {
                        moved = true;
                    }
                    float max = layer.getWidth() - getWidth();
                    setTranslationX(Math.max(0, Math.min(max, startX + dx)));
                    // по вертикали — подогнать посадку, не дальше 40dp от рамки
                    float range = layer.dp(40);
                    setTranslationY(Math.max(layer.anchorY - range, Math.min(layer.anchorY + range, startY + dy)));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    float span = layer.getWidth() - getWidth();
                    if (moved) {
                        layer.frac = span > 0 ? getTranslationX() / span : 0.9f;
                        layer.lift = (getTranslationY() - layer.anchorY) / getResources().getDisplayMetrics().density;
                        try {
                            Mods.prefs().edit().putFloat("pet_x", layer.frac).putFloat("pet_lift", layer.lift).apply();
                        } catch (Exception ignored) {
                        }
                    } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                        // касание — прыжок из анимаций Clawd (если он не занят ответом Claude)
                        if (!layer.working) {
                            layer.play(Frames.make(getContext(), "Jumping", 0, 20, 1));
                        }
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    // ---- сам Clawd ----

    static View clawd(Context ctx) {
        try {
            if (!Mods.prefs().getBoolean("pet_compose_broken", false) && !Names.CLAWD.isEmpty()) {
                View v = composeClawd(ctx);
                Fake.log("clawd: app's Clawd");
                return v;
            }
        } catch (Throwable t) {
            Log.e(Mods.TAG, "Clawd приложения", t);
            Fake.log("clawd: app's Clawd failed: " + t);
        }
        Fake.log("clawd: app's Clawd is not available");
        return null;
    }

    /**
     * ComposeView приложения с его Clawd. Clawd(modifier, ?, ?, ?, composer, changed, default):
     * модификатор передаётся (своего значения по умолчанию у него после R8 нет), остальное по умолчанию.
     */
    private static View composeClawd(Context ctx) throws Exception {
        Class<?> viewClass = Class.forName(Names.COMPOSE_VIEW);
        final View view = (View) viewClass.getConstructor(Context.class).newInstance(ctx);
        Class<?> f2 = Class.forName(Names.FUNCTION2);
        Method draw = null;
        for (Method m : Class.forName(Names.CLAWD).getDeclaredMethods()) {
            if (m.getName().equals(Names.CLAWD_METHOD) && m.getParameterTypes().length == 7
                    && Modifier.isStatic(m.getModifiers())) {
                draw = m;
            }
        }
        if (draw == null) {
            throw new NoSuchMethodException(Names.CLAWD + "." + Names.CLAWD_METHOD);
        }
        final Object modifier = singleton(Class.forName(Names.MODIFIER));
        if (modifier == null) {
            throw new IllegalStateException("нет Modifier");
        }
        final Method clawd = draw;
        final Object unit = Bridge.unit();
        Object content = Proxy.newProxyInstance(f2.getClassLoader(), new Class<?>[] {f2}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "MargyC.Clawd";
                default:
                    try {
                        clawd.invoke(null, modifier, false, false, 0, args[0], 0, 0xE);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    return unit;
            }
        });
        viewClass.getMethod("setContent", f2).invoke(view, content);
        // первая композиция идёт при добавлении в окно; если упадёт, следующий запуск возьмёт свой Clawd
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                v.postDelayed(() -> {
                    try {
                        Mods.prefs().edit().putBoolean("pet_compose_trying", false).apply();
                    } catch (Exception ignored) {
                    }
                }, 3000);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {}
        });
        Mods.prefs().edit().putBoolean("pet_compose_trying", true).commit();
        return view;
    }

    /** Статическое поле класса с его же экземпляром (object в Kotlin). */
    private static Object singleton(Class<?> c) throws Exception {
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == c) {
                f.setAccessible(true);
                return f.get(null);
            }
        }
        return null;
    }

    // ---- реакции: кадры анимаций Clawd из приложения ----

    static boolean reacts() {
        try {
            return Mods.prefs().getBoolean("pet_react", true);
        } catch (Exception e) {
            return true;
        }
    }

    static void setReacts(boolean on) throws Exception {
        Mods.prefs().edit().putBoolean("pet_react", on).apply();
    }

    private static String stop;

    /** Подпись кнопки «Остановить» у поля ввода, на языке приложения. */
    private static String stopLabel(Context ctx) {
        if (stop == null) {
            int id = ctx.getResources().getIdentifier("chat_input_stop_button_content_description", "string",
                    ctx.getPackageName());
            stop = id != 0 ? ctx.getString(id) : "";
        }
        return stop;
    }

    private static final java.util.Map<String, List<android.graphics.Bitmap>> SHEETS =
            new java.util.HashMap<String, List<android.graphics.Bitmap>>();

    /**
     * Кадры анимации Clawd приложения (Jumping, Dancing, Laptop, Guitar, NodOff, Sleep, WakeUp, Warning):
     * лист кадров анимации, превращённый в Bitmap той же функцией приложения, с его палитрой, полный кадр 55×37.
     */
    static synchronized List<android.graphics.Bitmap> sheet(String name) throws Exception {
        List<android.graphics.Bitmap> cached = SHEETS.get(name);
        if (cached != null) {
            return cached;
        }
        if (Names.CLAWD_ANIM.isEmpty()) {
            throw new IllegalStateException("нет анимаций Clawd");
        }
        Object anim = null;
        for (Object c : Class.forName(Names.CLAWD_ANIM).getEnumConstants()) {
            if (((Enum<?>) c).name().equals(name)) {
                anim = c;
            }
        }
        if (anim == null) {
            throw new IllegalStateException("нет анимации " + name);
        }
        Method sheetMethod = anim.getClass().getMethod(Names.CLAWD_SHEET);
        Object sheet = sheetMethod.invoke(anim);
        Method frames = null;
        for (Method m : Class.forName(Names.CLAWD_FRAMES).getDeclaredMethods()) {
            if (m.getName().equals(Names.CLAWD_FRAMES_METHOD) && m.getParameterTypes().length == 3
                    && m.getParameterTypes()[0] == sheetMethod.getReturnType()) {
                frames = m;
            }
        }
        if (frames == null) {
            throw new NoSuchMethodException(Names.CLAWD_FRAMES + "." + Names.CLAWD_FRAMES_METHOD);
        }
        frames.setAccessible(true);
        Class<?> paletteClass = frames.getParameterTypes()[1];
        Object palette = singleton(paletteClass);
        Object crop = frames.getParameterTypes()[2].getConstructor(int.class, int.class, int.class, int.class)
                .newInstance(0, 0, 55, 37);
        List<android.graphics.Bitmap> out = new ArrayList<android.graphics.Bitmap>();
        for (Object f : (List<?>) frames.invoke(null, sheet, palette, crop)) {
            for (Field field : f.getClass().getDeclaredFields()) {
                if (field.getType() == android.graphics.Bitmap.class) {
                    field.setAccessible(true);
                    out.add((android.graphics.Bitmap) field.get(f));
                }
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("пустая анимация " + name);
        }
        SHEETS.put(name, out);
        return out;
    }

    /** Проигрыватель кадров Clawd, 12 кадров в секунду, пиксели без сглаживания. */
    static final class Frames extends View {
        private final List<android.graphics.Bitmap> frames;
        private final int first, last, loops; // loops == 0 — без конца
        private final Paint paint = new Paint();
        private final Rect src = new Rect(), dst = new Rect();
        private final long born = android.os.SystemClock.uptimeMillis();
        Runnable done;

        private Frames(Context ctx, List<android.graphics.Bitmap> frames, int first, int last, int loops) {
            super(ctx);
            this.frames = frames;
            this.first = Math.max(0, Math.min(first, frames.size() - 1));
            this.last = Math.max(this.first, Math.min(last, frames.size() - 1));
            this.loops = loops;
            paint.setFilterBitmap(false);
            paint.setAntiAlias(false);
        }

        /** null, если анимаций нет: тогда реакции просто не будет. */
        static Frames make(Context ctx, String name, int first, int last, int loops) {
            try {
                return new Frames(ctx, sheet(name), first, last, loops);
            } catch (Throwable t) {
                Log.e(Mods.TAG, "Clawd " + name, t);
                Fake.log("clawd: animation " + name + " failed: " + t);
                return null;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            int count = last - first + 1;
            long n = (android.os.SystemClock.uptimeMillis() - born) * 12 / 1000;
            if (loops > 0 && n >= (long) count * loops) {
                if (done != null) {
                    Runnable r = done;
                    done = null;
                    post(r);
                }
                n = count - 1;
            }
            android.graphics.Bitmap b = frames.get(first + (int) (n % count));
            // целый масштаб, по низу: лапки стоят на рамке поля, как у Clawd приложения
            int scale = Math.max(1, Math.min(getWidth() / b.getWidth(), getHeight() / b.getHeight()));
            int bw = b.getWidth() * scale, bh = b.getHeight() * scale;
            src.set(0, 0, b.getWidth(), b.getHeight());
            dst.set((getWidth() - bw) / 2, getHeight() - bh, (getWidth() + bw) / 2, getHeight());
            c.drawBitmap(b, src, dst, paint);
            postInvalidateOnAnimation();
        }
    }
}
