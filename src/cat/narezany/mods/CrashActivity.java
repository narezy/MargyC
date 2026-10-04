package cat.narezany.mods;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;

/** Показывает последний вылет, чтобы его можно было скопировать и скинуть. */
public final class CrashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Claude Mods: вылет");
        final String log = read();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(0x26, 0x26, 0x24));
        root.setFitsSystemWindows(true);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView hint = new TextView(this);
        hint.setText("Приложение вылетело в прошлый раз. Скопируй текст ниже и скинь его. "
                + "Копия лежит в Загрузки/ClaudeMods.");
        hint.setTextColor(Color.rgb(0xFA, 0xF9, 0xF5));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(hint);

        Button copy = new Button(this);
        copy.setText("Скопировать");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("crash", log));
                Toast.makeText(CrashActivity.this, "Скопировано", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(copy);

        TextView text = new TextView(this);
        text.setText(log);
        text.setTextIsSelectable(true);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        text.setTextColor(Color.rgb(0xB0, 0xAE, 0xA5));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private String read() {
        try {
            File f = new File(getFilesDir(), CrashLog.FILE);
            byte[] data = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
            in.close();
            return new String(data, 0, off, "UTF-8");
        } catch (Exception e) {
            return "не удалось прочитать лог: " + e;
        }
    }
}
