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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Random;

/**
 * Слой поверх экранов Claude, касания проходят насквозь: счётчик символов у поля ввода, сезонные эффекты
 * (снег, листья, лепестки, звёзды) и свой экран запуска с Clawd из анимаций приложения.
 */
final class Overlay {
    private static final String TAG_LAYER = "margyc-overlay";
    private static boolean splashShown;

    private Overlay() {}

    // ---- настройки ----

    static boolean counter() {
        return pref("counter_on", false);
    }

    static void setCounter(boolean on) throws Exception {
        Mods.prefs().edit().putBoolean("counter_on", on).apply();
    }

    /** off, auto, snow, leaves, petals, stars. */
    static String season() {
        try {
            return Mods.prefs().getString("season", "off");
        } catch (Exception e) {
            return "off";
        }
    }

    static void setSeason(String s) throws Exception {
        Mods.prefs().edit().putString("season", s).apply();
    }

    /** Что идёт сейчас: для auto — по месяцу, летом без эффекта. */
    static String effectiveSeason() {
        String s = season();
        if (!"auto".equals(s)) {
            return s;
        }
        int m = Calendar.getInstance().get(Calendar.MONTH);
        if (m == Calendar.DECEMBER || m <= Calendar.FEBRUARY) {
            return "snow";
        }
        if (m <= Calendar.MAY) {
            return "petals";
        }
        return m >= Calendar.SEPTEMBER ? "leaves" : "off";
    }

    static boolean splash() {
        return pref("splash_on", false);
    }

    static void setSplash(boolean on) throws Exception {
        Mods.prefs().edit().putBoolean("splash_on", on).apply();
    }

    private static boolean pref(String key, boolean def) {
        try {
            return Mods.prefs().getBoolean(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    // ---- установка ----

    static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new SimpleCallbacks() {
            @Override
            public void onActivityResumed(Activity a) {
                if (a.getClass().getName().startsWith("cat.narezany.mods.")) {
                    return;
                }
                try {
                    attach(a);
                } catch (Throwable t) {
                    Fake.log("overlay error: " + t);
                }
            }

            @Override
            public void onActivityPaused(Activity a) {
                Layer l = find(a);
                if (l != null) {
                    l.stop();
                }
            }
        });
    }

    private static Layer find(Activity a) {
        View v = a.getWindow().getDecorView().findViewWithTag(TAG_LAYER);
        return v instanceof Layer ? (Layer) v : null;
    }

    private static void attach(Activity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        boolean wanted = counter() || !"off".equals(effectiveSeason());
        Layer layer = find(a);
        if (!wanted) {
            if (layer != null) {
                content.removeView(layer);
            }
        } else {
            if (layer == null) {
                layer = new Layer(a);
                content.addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                layer.bringToFront();
            }
            layer.start();
        }
        if (splash() && !splashShown) {
            splashShown = true;
            showSplash(a, content);
        }
    }

    /** Экран запуска: фон темы и Clawd из анимаций приложения, потом плавно исчезает. */
    private static void showSplash(Activity a, ViewGroup content) {
        Ui ui = new Ui(a);
        final FrameLayout cover = new FrameLayout(a);
        cover.setBackgroundColor(ui.bg);
        cover.setClickable(true);
        View clawd = Pet.Frames.make(a, "Jumping", 0, 20, 2);
        if (clawd != null) {
            int w = ui.dp(165), h = ui.dp(111);
            cover.addView(clawd, new FrameLayout.LayoutParams(w, h, Gravity.CENTER));
        }
        TextView name = ui.label("MargyC", 18, ui.secondary, ui.medium);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        nl.bottomMargin = ui.dp(64);
        cover.addView(name, nl);
        content.addView(cover, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        cover.postDelayed(() -> cover.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> content.removeView(cover)).start(), 1500);
    }

    /** Прозрачный слой без касаний: эффекты и счётчик. */
    static final class Layer extends FrameLayout {
        private final Activity activity;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Pet.Semantics semantics = new Pet.Semantics();
        private final TextView count;
        private final Particles particles;
        private View host;
        private boolean running;
        private int polls;
        private final int[] loc = new int[2];

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }
                try {
                    poll();
                } catch (Throwable ignored) {
                }
                handler.postDelayed(this, 300);
            }
        };

        Layer(Activity a) {
            super(a);
            activity = a;
            setTag(TAG_LAYER);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            Ui ui = new Ui(a);
            particles = new Particles(a);
            addView(particles, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            count = ui.label("", 12, ui.secondary, ui.regular);
            count.setVisibility(INVISIBLE);
            addView(count, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }

        void start() {
            particles.setKind(effectiveSeason());
            if (!running) {
                running = true;
                handler.post(tick);
            }
        }

        void stop() {
            running = false;
            handler.removeCallbacks(tick);
            particles.setKind("off");
        }

        private void poll() {
            if (!counter()) {
                count.setVisibility(INVISIBLE);
                return;
            }
            polls++;
            if (host == null || !host.isAttachedToWindow()) {
                host = Pet.findHost(activity.getWindow().getDecorView());
                semantics.reset();
            }
            if (host == null || !semantics.works) {
                count.setVisibility(INVISIBLE);
                return;
            }
            Rect r = semantics.editable(host, polls % 10 == 0);
            int n = r != null ? semantics.textLength(host) : -1;
            if (r == null || n <= 0) {
                count.setVisibility(INVISIBLE);
                return;
            }
            getLocationOnScreen(loc);
            int layerTop = loc[1], layerLeft = loc[0];
            host.getLocationOnScreen(loc);
            count.setText(String.valueOf(n));
            count.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            count.setTranslationX(r.right + loc[0] - layerLeft - count.getMeasuredWidth());
            count.setTranslationY(r.top + loc[1] - layerTop - count.getMeasuredHeight() - dp(16));
            count.setVisibility(VISIBLE);
        }

        private int dp(float v) {
            return Math.round(v * getResources().getDisplayMetrics().density);
        }
    }

    /** Частицы: снег, листья, лепестки, звёзды. Рисуется только пока экран открыт. */
    static final class Particles extends View {
        private static final int N = 36;
        private final float[] x = new float[N], y = new float[N], vx = new float[N], vy = new float[N],
                size = new float[N], angle = new float[N], spin = new float[N];
        private final int[] color = new int[N];
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private String kind = "off";
        private long lastFrame;

        Particles(Context ctx) {
            super(ctx);
        }

        void setKind(String k) {
            if (!k.equals(kind)) {
                kind = k;
                lastFrame = 0;
                for (int i = 0; i < N; i++) {
                    reset(i, true);
                }
            }
            invalidate();
        }

        private void reset(int i, boolean anywhere) {
            int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
            float d = getResources().getDisplayMetrics().density;
            x[i] = random.nextFloat() * w;
            y[i] = anywhere ? random.nextFloat() * h : -20 * d;
            angle[i] = random.nextFloat() * 360;
            spin[i] = (random.nextFloat() - 0.5f) * 90;
            switch (kind) {
                case "snow":
                    size[i] = (1.5f + random.nextFloat() * 2.5f) * d;
                    vy[i] = (20 + random.nextFloat() * 40) * d;
                    vx[i] = (random.nextFloat() - 0.5f) * 20 * d;
                    color[i] = 0xCCFFFFFF;
                    break;
                case "leaves":
                    size[i] = (5 + random.nextFloat() * 4) * d;
                    vy[i] = (25 + random.nextFloat() * 30) * d;
                    vx[i] = (random.nextFloat() - 0.3f) * 30 * d;
                    color[i] = new int[] {0xCCD97757, 0xCCC6613F, 0xCCE0A040, 0xCC8E5A2B}[random.nextInt(4)];
                    break;
                case "petals":
                    size[i] = (3.5f + random.nextFloat() * 3) * d;
                    vy[i] = (18 + random.nextFloat() * 25) * d;
                    vx[i] = (random.nextFloat() - 0.2f) * 25 * d;
                    color[i] = new int[] {0xCCF9D4E2, 0xCCF3A8C3, 0xCCFFFFFF}[random.nextInt(3)];
                    break;
                case "stars":
                    size[i] = (1 + random.nextFloat() * 1.5f) * d;
                    vy[i] = 0;
                    vx[i] = 0;
                    color[i] = 0xCCFFFFFF;
                    break;
                default:
                    break;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            if ("off".equals(kind) || getWidth() == 0) {
                return;
            }
            long now = android.os.SystemClock.uptimeMillis();
            float dt = lastFrame == 0 ? 0 : Math.min(0.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            for (int i = 0; i < N; i++) {
                x[i] += vx[i] * dt;
                y[i] += vy[i] * dt;
                angle[i] += spin[i] * dt;
                if (y[i] > getHeight() + 20 || x[i] < -40 || x[i] > getWidth() + 40) {
                    reset(i, false);
                }
                paint.setColor(color[i]);
                if ("stars".equals(kind)) {
                    // мерцание
                    int a = 80 + (int) (120 * (0.5 + 0.5 * Math.sin(now / 600.0 + i)));
                    paint.setAlpha(a);
                    c.drawCircle(x[i], y[i], size[i], paint);
                } else if ("snow".equals(kind)) {
                    c.drawCircle(x[i], y[i], size[i], paint);
                } else {
                    c.save();
                    c.rotate(angle[i], x[i], y[i]);
                    c.drawOval(x[i] - size[i], y[i] - size[i] * 0.55f, x[i] + size[i], y[i] + size[i] * 0.55f, paint);
                    c.restore();
                }
            }
            postInvalidateOnAnimation();
        }
    }

    /** Колбэки активностей, где нужны не все методы. */
    abstract static class SimpleCallbacks implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(Activity a, Bundle b) {}

        @Override
        public void onActivityStarted(Activity a) {}

        @Override
        public void onActivityResumed(Activity a) {}

        @Override
        public void onActivityPaused(Activity a) {}

        @Override
        public void onActivityStopped(Activity a) {}

        @Override
        public void onActivitySaveInstanceState(Activity a, Bundle b) {}

        @Override
        public void onActivityDestroyed(Activity a) {}
    }
}
