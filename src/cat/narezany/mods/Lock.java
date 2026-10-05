package cat.narezany.mods;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Мод «Блокировка»: Claude открывается по отпечатку, лицу или PIN телефона. Блокируется при запуске и
 * когда пробыл в фоне дольше выбранного времени. Пока заблокирован, поверх каждого экрана — непрозрачная
 * заглушка, касания до приложения не доходят. Отдельно — «Скрывать в недавних»: в списке приложений вместо
 * снимка экрана пусто.
 */
final class Lock {
    private static final String TAG_COVER = "margyc-lock";
    private static boolean locked = true;
    private static int started;
    private static long background;
    private static boolean prompting;

    private Lock() {}

    static boolean enabled() {
        try {
            return Mods.prefs().getBoolean("lock_on", false);
        } catch (Exception e) {
            return false;
        }
    }

    static void setEnabled(boolean on) throws Exception {
        Mods.prefs().edit().putBoolean("lock_on", on).apply();
        locked = false; // включили на ходу — не запирать сразу
    }

    /** Сейчас Claude закрыт заглушкой. */
    static boolean locked() {
        return locked && enabled();
    }

    /** Через сколько секунд в фоне снова спрашивать: 0 — сразу. */
    static int timeout() {
        try {
            return Mods.prefs().getInt("lock_timeout", 60);
        } catch (Exception e) {
            return 60;
        }
    }

    static void setTimeout(int seconds) throws Exception {
        Mods.prefs().edit().putInt("lock_timeout", seconds).apply();
    }

    static boolean hideRecents() {
        try {
            return Mods.prefs().getBoolean("lock_recents", false);
        } catch (Exception e) {
            return false;
        }
    }

    static void setHideRecents(boolean on) throws Exception {
        Mods.prefs().edit().putBoolean("lock_recents", on).apply();
    }

    private static int authenticators() {
        return BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    }

    /** null — можно включать; иначе почему нельзя. */
    static String unavailable(Context ctx) {
        if (Build.VERSION.SDK_INT < 30) {
            return L.t("Нужен Android 11 или новее.");
        }
        BiometricManager bm = ctx.getSystemService(BiometricManager.class);
        int r = bm != null ? bm.canAuthenticate(authenticators()) : BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE;
        if (r == BiometricManager.BIOMETRIC_SUCCESS) {
            return null;
        }
        return L.t("На телефоне не настроены ни отпечаток, ни лицо, ни PIN-код экрана блокировки.");
    }

    static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity a) {
                if (started++ == 0 && enabled() && background > 0
                        && System.currentTimeMillis() - background >= timeout() * 1000L) {
                    locked = true;
                }
            }

            @Override
            public void onActivityStopped(Activity a) {
                if (--started <= 0) {
                    started = 0;
                    background = System.currentTimeMillis();
                }
            }

            @Override
            public void onActivityResumed(Activity a) {
                try {
                    recents(a);
                    if (a instanceof CrashActivity) {
                        return;
                    }
                    if (enabled() && locked) {
                        cover(a);
                    } else {
                        uncover(a);
                    }
                } catch (Throwable t) {
                    Log.e(Mods.TAG, "Lock", t);
                }
            }

            @Override
            public void onActivityCreated(Activity a, Bundle b) {
                recents(a);
            }

            @Override
            public void onActivityPaused(Activity a) {}

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {}

            @Override
            public void onActivityDestroyed(Activity a) {}
        });
    }

    private static void recents(Activity a) {
        boolean hide = hideRecents();
        if (Build.VERSION.SDK_INT >= 33) {
            a.setRecentsScreenshotEnabled(!hide);
        } else if (hide) {
            a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private static void cover(final Activity a) {
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        View cover = decor.findViewWithTag(TAG_COVER);
        if (cover == null) {
            Ui ui = new Ui(a);
            FrameLayout frame = new FrameLayout(a);
            frame.setTag(TAG_COVER);
            frame.setBackgroundColor(ui.bg);
            frame.setClickable(true); // касания не уходят приложению
            frame.setFocusable(true);
            LinearLayout box = ui.column();
            box.setGravity(Gravity.CENTER);
            TextView icon = ui.label("", 56, ui.text, ui.regular);
            if (ui.iconFont() != null) {
                icon.setTypeface(ui.iconFont());
                icon.setText(new String(Character.toChars(0xe897))); // lock
            }
            icon.setGravity(Gravity.CENTER);
            box.addView(icon);
            TextView title = ui.label(L.t("Claude заблокирован"), 20, ui.text, ui.medium);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, ui.dp(12), 0, ui.dp(24));
            box.addView(title);
            TextView unlock = ui.label(L.t("Разблокировать"), 16, ui.bg, ui.medium);
            unlock.setGravity(Gravity.CENTER);
            unlock.setPadding(ui.dp(28), 0, ui.dp(28), 0);
            unlock.setBackground(ui.ripple(ui.round(ui.text, 24), 24));
            unlock.setOnClickListener(v -> prompt(a));
            box.addView(unlock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(48)));
            frame.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            decor.addView(frame, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            cover.bringToFront();
        }
        decor.post(() -> prompt(a));
    }

    private static void uncover(Activity a) {
        View cover = a.getWindow().getDecorView().findViewWithTag(TAG_COVER);
        if (cover != null) {
            ((ViewGroup) cover.getParent()).removeView(cover);
        }
    }

    private static void prompt(final Activity a) {
        if (prompting || !locked || Build.VERSION.SDK_INT < 30 || a.isFinishing()) {
            return;
        }
        if (unavailable(a) != null) {
            // отпечаток и PIN убрали из настроек телефона: не запирать Claude навсегда
            locked = false;
            uncover(a);
            return;
        }
        prompting = true;
        BiometricPrompt p = new BiometricPrompt.Builder(a)
                .setTitle(L.t("Вход в Claude"))
                .setSubtitle("MargyC")
                .setAllowedAuthenticators(authenticators())
                .build();
        p.authenticate(new CancellationSignal(), a.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                prompting = false;
                locked = false;
                uncover(a);
            }

            @Override
            public void onAuthenticationError(int code, CharSequence message) {
                prompting = false; // отмена или ошибка: заглушка остаётся, кнопка спросит ещё раз
            }
        });
    }

    static String timeoutLabel(int seconds) {
        return seconds == 0 ? L.t("Сразу") : seconds < 60 ? seconds + L.t(" с")
                : seconds / 60 + L.t(" мин");
    }

    static final int[] TIMEOUTS = {0, 30, 60, 300, 900};
}
