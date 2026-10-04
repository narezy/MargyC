package cat.narezany.mods;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;

import java.util.Locale;

/** Прозрачная активность с одним окном: объясняет, почему не работает вход через Google. */
public final class InfoActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        boolean ru = "ru".equals(Locale.getDefault().getLanguage());
        Ui ui = new Ui(this);
        ui.new Sheet(ru ? "Вход через Google недоступен" : "Google sign-in isn't available")
                .message(ru
                        ? "В MargyC вход через Google не работает: Google пускает только оригинальное "
                                + "приложение.\n\nВведи ниже ту же почту, что у твоего Google-аккаунта, "
                                + "и войди по коду из письма. Откроется тот же аккаунт, все чаты и подписка "
                                + "на месте."
                        : "Google sign-in doesn't work in MargyC: Google only accepts the original app.\n\n"
                                + "Enter the same email as your Google account below and sign in with the code "
                                + "from the email. You'll get the same account with all your chats and your "
                                + "subscription.")
                .button(ru ? "Понятно" : "Got it", true, null)
                .show()
                .setOnDismissListener(d -> finish());
    }
}
