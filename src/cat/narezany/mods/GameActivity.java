package cat.narezany.mods;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Мини-игра: Clawd бежит, коснись — прыгнет через баги. Скорость растёт, рекорд запоминается.
 * Clawd — кадры анимаций самого приложения, баги нарисованы здесь пикселями.
 */
public final class GameActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        Ui ui = new Ui(this);
        ui.setupWindow(this);
        FrameLayout box = new FrameLayout(this);
        box.addView(new Game(this, ui));
        View screen = ui.screen(this, L.t("Игра с Clawd"), box);
        fill(screen); // экран кладёт содержимое в ScrollView, а там у игры была бы нулевая высота
        setContentView(screen);
    }

    private static void fill(View v) {
        if (v instanceof android.widget.ScrollView) {
            ((android.widget.ScrollView) v).setFillViewport(true);
        } else if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                fill(g.getChildAt(i));
            }
        }
    }

    /** Жук 9×6: 1 — тело, 2 — глаза. */
    private static final String[] BUG = {
        "..1...1..",
        "...1.1...",
        ".1111111.",
        "112111211",
        "111111111",
        ".1.1.1.1.",
    };

    private static final class Obstacle {
        float x;
        final int count; // жуков в ряд
        boolean passed;

        Obstacle(float x, int count) {
            this.x = x;
            this.count = count;
        }
    }

    private static final class Game extends View {
        private final Ui ui;
        private final Paint pixels = new Paint(), text = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint();
        private final Random random = new Random();
        private final List<Obstacle> obstacles = new ArrayList<Obstacle>();
        private List<Bitmap> jumpFrames, warnFrames;
        private final Rect src = new Rect(), body = new Rect();
        private final RectF dst = new RectF();
        private final float px; // размер «пикселя» спрайтов
        private float y, vy; // высота Clawd над землёй и скорость, px/с
        private float speed, distance, nextGap;
        private long last;
        private int score, best;
        private boolean running, over;

        Game(Context c, Ui ui) {
            super(c);
            this.ui = ui;
            px = ui.dp(2);
            pixels.setFilterBitmap(false);
            pixels.setAntiAlias(false);
            text.setColor(ui.text);
            text.setTypeface(ui.medium);
            text.setTextAlign(Paint.Align.CENTER);
            line.setColor(ui.secondary);
            line.setStrokeWidth(ui.dp(2));
            try {
                jumpFrames = Pet.sheet("Jumping");
                warnFrames = Pet.sheet("Warning");
            } catch (Throwable t) {
                Fake.log("game: no Clawd frames: " + t);
            }
            if (jumpFrames != null && !jumpFrames.isEmpty()) {
                bounds(jumpFrames.get(0), body);
            }
            try {
                best = Mods.prefs().getInt("game_best", 0);
            } catch (Exception ignored) {
            }
            reset();
        }

        /** Где в кадре сам Clawd: по непрозрачным пикселям, для столкновений. */
        private static void bounds(Bitmap b, Rect out) {
            int l = b.getWidth(), t = b.getHeight(), r = -1, bo = -1;
            for (int yy = 0; yy < b.getHeight(); yy++) {
                for (int xx = 0; xx < b.getWidth(); xx++) {
                    if ((b.getPixel(xx, yy) >>> 24) > 0x40) {
                        l = Math.min(l, xx);
                        t = Math.min(t, yy);
                        r = Math.max(r, xx);
                        bo = Math.max(bo, yy);
                    }
                }
            }
            if (r < 0) {
                out.set(0, 0, b.getWidth(), b.getHeight());
            } else {
                out.set(l, t, r + 1, bo + 1);
            }
        }

        private void reset() {
            obstacles.clear();
            y = 0;
            vy = 0;
            speed = ui.dp(220);
            distance = 0;
            score = 0;
            nextGap = ui.dp(320);
            over = false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_DOWN) {
                return true;
            }
            if (over) {
                reset();
                running = true;
            } else if (!running) {
                running = true;
            }
            if (y == 0 && !over) {
                vy = ui.dp(620);
            }
            last = SystemClock.uptimeMillis();
            invalidate();
            return true;
        }

        private float ground() {
            return getHeight() * 0.62f;
        }

        private void step(float dt) {
            float gravity = ui.dp(1900);
            y += vy * dt;
            vy -= gravity * dt;
            if (y <= 0) {
                y = 0;
                vy = 0;
            }
            speed += ui.dp(6) * dt; // постепенно быстрее
            float dx = speed * dt;
            distance += dx;
            score = (int) (distance / ui.dp(40));
            for (Obstacle o : obstacles) {
                o.x -= dx;
            }
            while (!obstacles.isEmpty() && obstacles.get(0).x < -ui.dp(80)) {
                obstacles.remove(0);
            }
            nextGap -= dx;
            if (nextGap <= 0) {
                obstacles.add(new Obstacle(getWidth() + ui.dp(20), 1 + (score > 30 && random.nextInt(3) == 0 ? 1 : 0)));
                // промежуток побольше на скорости, чтобы прыжок успевал
                nextGap = speed * (0.9f + random.nextFloat() * 0.9f) + ui.dp(60);
            }
            if (hit()) {
                over = true;
                running = false;
                if (score > best) {
                    best = score;
                    try {
                        Mods.prefs().edit().putInt("game_best", best).apply();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        private float clawdLeft() {
            return getWidth() * 0.15f;
        }

        private boolean hit() {
            float g = ground();
            float left = clawdLeft() + body.left * px, right = clawdLeft() + body.right * px;
            float bottom = g - y; // ноги Clawd
            float inset = px * 2;
            for (Obstacle o : obstacles) {
                float ol = o.x + inset, or = o.x + BUG[0].length() * px * o.count - inset;
                float ot = g - BUG.length * px + inset;
                if (ol < right - inset && or > left + inset && bottom > ot) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            if (running) {
                float dt = Math.min(0.05f, (now - last) / 1000f);
                last = now;
                step(dt);
            }
            float g = ground();
            c.drawLine(0, g, getWidth(), g, line);
            // земля бежит: штрихи
            float dash = ui.dp(24);
            float off = distance % (dash * 2);
            for (float x = -off; x < getWidth(); x += dash * 2) {
                c.drawLine(x, g + ui.dp(10), x + dash * 0.4f, g + ui.dp(10), line);
            }
            pixels.setColor(0xFFD97757);
            for (Obstacle o : obstacles) {
                for (int k = 0; k < o.count; k++) {
                    drawBug(c, o.x + k * BUG[0].length() * px, g);
                }
            }
            drawClawd(c, g, now);

            text.setTextSize(ui.dp(18));
            text.setTextAlign(Paint.Align.RIGHT);
            c.drawText(L.t("Рекорд ") + best + "   " + score, getWidth() - ui.dp(16), ui.dp(32), text);
            text.setTextAlign(Paint.Align.CENTER);
            if (!running) {
                text.setTextSize(ui.dp(20));
                String msg = over ? L.t("Баг! Коснись, чтобы ещё раз") : L.t("Коснись, чтобы прыгнуть");
                c.drawText(msg, getWidth() / 2f, getHeight() * 0.25f, text);
            }
            if (running) {
                postInvalidateOnAnimation();
            }
        }

        private void drawBug(Canvas c, float x, float g) {
            float top = g - BUG.length * px;
            for (int r = 0; r < BUG.length; r++) {
                for (int col = 0; col < BUG[r].length(); col++) {
                    char ch = BUG[r].charAt(col);
                    if (ch == '.') {
                        continue;
                    }
                    pixels.setColor(ch == '2' ? 0xFF101010 : 0xFFD97757);
                    c.drawRect(x + col * px, top + r * px, x + (col + 1) * px, top + (r + 1) * px, pixels);
                }
            }
        }

        private void drawClawd(Canvas c, float g, long now) {
            Bitmap f = null;
            if (over && warnFrames != null && !warnFrames.isEmpty()) {
                f = warnFrames.get(Math.min(warnFrames.size() - 1, 6));
            } else if (jumpFrames != null && !jumpFrames.isEmpty()) {
                int n = jumpFrames.size();
                // в воздухе — кадр прыжка, на бегу — чередуем пару первых кадров
                f = y > 0 ? jumpFrames.get(Math.min(n - 1, n / 2)) : jumpFrames.get((int) ((now / 120) % Math.min(2, n)));
            }
            float left = clawdLeft();
            if (f == null) {
                pixels.setColor(0xFFF05A40);
                c.drawRect(left, g - y - ui.dp(30), left + ui.dp(40), g - y, pixels);
                return;
            }
            src.set(0, 0, f.getWidth(), f.getHeight());
            // ноги (низ непрозрачной части кадра) стоят на земле
            float bottom = g - y + (f.getHeight() - body.bottom) * px;
            dst.set(left, bottom - f.getHeight() * px, left + f.getWidth() * px, bottom);
            c.drawBitmap(f, src, dst, pixels);
        }
    }
}
