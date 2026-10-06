package cat.narezany.mods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

/**
 * Статистика («Claude Wrapped»): сколько сообщений ты отправил, в какие дни и часы, какими моделями.
 * Считается только на телефоне, никуда не отправляется. Сообщение засчитывается, когда мод добавляет
 * к нему контекст (оба пути отправки: hub и REST); копии одного запроса в течение пары секунд — одно.
 */
final class Stats {
    private static long last;
    private static volatile String model = "";

    private Stats() {}

    /** Модель последнего ModelId (Fake.send) или поля model REST-запроса. */
    static void model(String id) {
        if (id != null && !id.isEmpty()) {
            model = id;
        }
    }

    static synchronized void message() {
        try {
            long now = System.currentTimeMillis();
            if (now - last < 2500) {
                return; // тот же запрос: конструктор SendMessage зовут и при копировании
            }
            last = now;
            JSONObject o = load();
            o.put("total", o.optInt("total") + 1);
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now));
            JSONObject days = o.optJSONObject("days") != null ? o.optJSONObject("days") : new JSONObject();
            days.put(day, days.optInt(day) + 1);
            o.put("days", days);
            JSONArray hours = o.optJSONArray("hours") != null ? o.optJSONArray("hours") : new JSONArray();
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            for (int i = hours.length(); i < 24; i++) {
                hours.put(0);
            }
            hours.put(h, hours.optInt(h) + 1);
            o.put("hours", hours);
            String m = displayModel(model);
            if (!m.isEmpty()) {
                JSONObject models = o.optJSONObject("models") != null ? o.optJSONObject("models") : new JSONObject();
                models.put(m, models.optInt(m) + 1);
                o.put("models", models);
            }
            if (!o.has("since")) {
                o.put("since", day);
            }
            Mods.prefs().edit().putString("stats", o.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    /** Мемная модель — по её имени, настоящая — по названию из меню. */
    private static String displayModel(String id) {
        try {
            Fake.Model meme = Fake.byId(id);
            if (meme != null) {
                return meme.name;
            }
            Fake.Model chosen = Fake.selected();
            if (chosen != null && id.equals(chosen.base)) {
                return chosen.name;
            }
        } catch (Throwable ignored) {
        }
        return id.isEmpty() ? "" : Fake.realName(id);
    }

    static JSONObject load() {
        try {
            return new JSONObject(Mods.prefs().getString("stats", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static void reset() throws Exception {
        Mods.prefs().edit().remove("stats").apply();
    }

    static final class Summary {
        int total, activeDays, streak, bestDayCount, favouriteCount;
        String bestDay = "", favourite = "", since = "";
        int[] hours = new int[24];
        int topHour = -1;
    }

    static Summary summary() {
        JSONObject o = load();
        Summary s = new Summary();
        s.total = o.optInt("total");
        s.since = o.optString("since");
        JSONObject days = o.optJSONObject("days");
        if (days != null) {
            Iterator<String> it = days.keys();
            while (it.hasNext()) {
                String d = it.next();
                int n = days.optInt(d);
                s.activeDays++;
                if (n > s.bestDayCount) {
                    s.bestDayCount = n;
                    s.bestDay = d;
                }
            }
            // серия: дни подряд до сегодня (или до вчера, если сегодня ещё не писал)
            Calendar c = Calendar.getInstance();
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            if (!days.has(f.format(c.getTime()))) {
                c.add(Calendar.DAY_OF_MONTH, -1);
            }
            while (days.has(f.format(c.getTime()))) {
                s.streak++;
                c.add(Calendar.DAY_OF_MONTH, -1);
            }
        }
        JSONArray hours = o.optJSONArray("hours");
        int top = 0;
        for (int i = 0; hours != null && i < 24 && i < hours.length(); i++) {
            s.hours[i] = hours.optInt(i);
            if (s.hours[i] > top) {
                top = s.hours[i];
                s.topHour = i;
            }
        }
        JSONObject models = o.optJSONObject("models");
        if (models != null) {
            Iterator<String> it = models.keys();
            while (it.hasNext()) {
                String m = it.next();
                if (models.optInt(m) > s.favouriteCount) {
                    s.favouriteCount = models.optInt(m);
                    s.favourite = m;
                }
            }
        }
        return s;
    }

    /** Текст, чтобы поделиться. */
    static String share(Summary s) {
        StringBuilder sb = new StringBuilder(L.t("Мой Claude в цифрах (MargyC):")).append('\n');
        sb.append(L.t("Сообщений: ")).append(s.total).append('\n');
        sb.append(L.t("Дней с Claude: ")).append(s.activeDays).append('\n');
        sb.append(L.t("Серия: ")).append(s.streak).append(L.t(" дн. подряд")).append('\n');
        if (!s.favourite.isEmpty()) {
            sb.append(L.t("Любимая модель: ")).append(s.favourite).append('\n');
        }
        if (s.topHour >= 0) {
            sb.append(L.t("Чаще всего пишу в ")).append(s.topHour).append(":00\n");
        }
        if (!s.bestDay.isEmpty()) {
            sb.append(L.t("Рекорд: ")).append(s.bestDayCount).append(L.t(" сообщений за день (")).append(s.bestDay).append(")\n");
        }
        return sb.toString();
    }
}
