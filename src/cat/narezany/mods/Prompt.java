package cat.narezany.mods;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Мод «Дополнить системный промпт». Системный промпт собирается на сервере, поэтому текст
 * выбранного пресета уходит в поле hidden_context запроса SendMessage: его видит только
 * Claude, в текст сообщения он не попадает.
 *
 * Стандартный пресет нельзя изменить или удалить: в нём сведения о моде и об устройстве,
 * чтобы Claude сразу знал, с чем имеет дело, если спросить про ошибку на телефоне.
 */
public final class Prompt {
    /** Встроенные пресеты: развёрнутый (по умолчанию, с темами) и обычный. */
    static final String DEFAULT_ID = "default";
    static final String SHORT_ID = "short";
    static final String SUPER_ID = "super";
    /** Сервер не принимает слишком длинные строки hidden_context: длинный текст уходит частями. */
    private static final int CHUNK = 3000;

    private static String deviceInfo;

    private Prompt() {}

    /** Поле hidden_context запроса SendMessage: пресет и промпт мемной модели, если она выбрана. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List hidden(List context) {
        List result = context;
        try {
            for (String part : texts()) {
                for (String chunk : chunks(part)) {
                    result = add(result, chunk);
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    /** Всё, что уходит с сообщением: пресет, промпт мемной модели, контекст своих модов. */
    private static List<String> texts() throws Exception {
        List<String> out = new ArrayList<String>();
        String fake = Fake.prompt();
        if (enabled()) {
            out.add(selected().text);
        }
        if (fake != null) {
            out.add(fake);
        }
        out.addAll(Plugins.prompts());
        return out;
    }

    /**
     * Готовое тело JSON-запроса (okio ByteString). Старый движок чата (у аккаунтов без hub) шлёт сообщение в
     * REST /completion, в ChatCompletionRequest нет hidden_context. Туда текст уходит стилем
     * (personalized_styles, как в веб-версии): его тоже видит только Claude.
     */
    public static Object body(Object bytes) {
        try {
            byte[] b = bytes(bytes);
            if (b == null || b.length < 2 || b[0] != '{') {
                return bytes;
            }
            String json = new String(b, "UTF-8");
            if (!json.contains("\"prompt\"") || !json.contains("\"timezone\"")) {
                return bytes;
            }
            JSONObject o = new JSONObject(json);
            if (!o.has("prompt") || !o.has("timezone")) {
                return bytes;
            }
            StringBuilder text = new StringBuilder();
            for (String part : texts()) {
                if (!part.trim().isEmpty()) {
                    text.append(text.length() == 0 ? "" : "\n\n").append(part.trim());
                }
            }
            if (text.length() == 0) {
                return bytes;
            }
            JSONArray styles = o.optJSONArray("personalized_styles");
            if (styles == null) {
                styles = new JSONArray();
            }
            styles.put(new JSONObject().put("type", "custom").put("key", "margyc").put("uuid", STYLE_UUID)
                    .put("name", "MargyC").put("prompt", text.toString()).put("summary", "MargyC")
                    .put("isDefault", false));
            o.put("personalized_styles", styles);
            Fake.log("REST /completion: prompt as style, " + text.length() + " chars");
            return withBytes(bytes, o.toString().getBytes("UTF-8"));
        } catch (Throwable t) {
            Fake.log("REST /completion error: " + t);
            return bytes;
        }
    }

    private static final String STYLE_UUID = "4d617267-7943-4000-8000-6d6172677963";

    private static java.lang.reflect.Method toBytes;

    /**
     * Байты okio ByteString: метод базового класса без параметров, возвращающий byte[]. Базовый — тот, у
     * которого конструктор (byte[]); его подкласс (сегментный ByteString) этот метод переопределяет.
     */
    private static byte[] bytes(Object bs) throws Exception {
        if (toBytes == null) {
            Class<?> base = base(bs.getClass());
            for (java.lang.reflect.Method m : base == null ? new java.lang.reflect.Method[0] : base.getDeclaredMethods()) {
                if (m.getReturnType() == byte[].class && m.getParameterTypes().length == 0
                        && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    m.setAccessible(true);
                    toBytes = m;
                    break;
                }
            }
        }
        return toBytes == null || !toBytes.getDeclaringClass().isInstance(bs) ? null : (byte[]) toBytes.invoke(bs);
    }

    private static Class<?> base(Class<?> c) {
        for (; c != null; c = c.getSuperclass()) {
            try {
                c.getDeclaredConstructor(byte[].class);
                return c;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    /** Новый ByteString с этими байтами. */
    private static Object withBytes(Object bs, byte[] b) throws Exception {
        Class<?> c = base(bs.getClass());
        if (c == null) {
            return bs;
        }
        java.lang.reflect.Constructor<?> k = c.getDeclaredConstructor(byte[].class);
        k.setAccessible(true);
        return k.newInstance((Object) b);
    }

    /** Текст частями не длиннее CHUNK, по границам абзацев (строк, если абзац слишком длинный). */
    static List<String> chunks(String text) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (String para : text.split("(?<=\n)")) {
            if (cur.length() + para.length() > CHUNK && cur.length() > 0) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            }
            while (para.length() > CHUNK) {
                out.add(para.substring(0, CHUNK));
                para = para.substring(CHUNK);
            }
            cur.append(para);
        }
        if (cur.toString().trim().length() > 0) {
            out.add(cur.toString().trim());
        }
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List add(List context, String text) {
        if (text.trim().isEmpty() || (context != null && context.contains(text))) {
            return context; // конструктор зовут и при копировании запроса
        }
        List result = context == null ? new ArrayList() : new ArrayList(context);
        result.add(text);
        return result;
    }

    // ---- настройки ----

    static boolean enabled() throws Exception {
        return Mods.prefs().getBoolean("prompt_on", true);
    }

    static void setEnabled(boolean on) throws Exception {
        Mods.prefs().edit().putBoolean("prompt_on", on).apply();
    }

    static final class Preset {
        final String id;
        final String name;
        final String text;

        Preset(String id, String name, String text) {
            this.id = id;
            this.name = name;
            this.text = text;
        }

        boolean builtIn() {
            return DEFAULT_ID.equals(id) || SHORT_ID.equals(id) || SUPER_ID.equals(id);
        }
    }

    static List<Preset> presets() throws Exception {
        List<Preset> list = new ArrayList<Preset>();
        list.add(new Preset(DEFAULT_ID, L.t("Развёрнутый"), defaultText(Mods.app())));
        list.add(new Preset(SHORT_ID, L.t("Обычный"), shortText(Mods.app())));
        list.add(new Preset(SUPER_ID, L.t("Суперразвёрнутый"), superText(Mods.app())));
        JSONArray a = new JSONArray(Mods.prefs().getString("presets", "[]"));
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            list.add(new Preset(o.getString("id"), o.getString("name"), o.getString("text")));
        }
        return list;
    }

    static Preset selected() throws Exception {
        String id = Mods.prefs().getString("preset", DEFAULT_ID);
        List<Preset> all = presets();
        for (Preset p : all) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return all.get(0);
    }

    static void select(String id) throws Exception {
        Mods.prefs().edit().putString("preset", id).apply();
    }

    /** Новый (id == null) или изменённый пресет. Возвращает его id. */
    static String save(String id, String name, String text) throws Exception {
        if (DEFAULT_ID.equals(id) || SHORT_ID.equals(id) || SUPER_ID.equals(id)) {
            throw new IllegalArgumentException("встроенный пресет не меняется");
        }
        JSONArray a = new JSONArray(Mods.prefs().getString("presets", "[]"));
        JSONArray out = new JSONArray();
        boolean found = false;
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            if (o.getString("id").equals(id)) {
                o.put("name", name).put("text", text);
                found = true;
            }
            out.put(o);
        }
        if (!found) {
            out.put(new JSONObject().put("id", id).put("name", name).put("text", text));
        }
        Mods.prefs().edit().putString("presets", out.toString()).apply();
        return id;
    }

    static void delete(String id) throws Exception {
        JSONArray a = new JSONArray(Mods.prefs().getString("presets", "[]"));
        JSONArray out = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            if (!a.getJSONObject(i).getString("id").equals(id)) {
                out.put(a.getJSONObject(i));
            }
        }
        Mods.prefs().edit().putString("presets", out.toString())
                .putString("preset", id.equals(Mods.prefs().getString("preset", DEFAULT_ID))
                        ? DEFAULT_ID : Mods.prefs().getString("preset", DEFAULT_ID))
                .apply();
    }

    // ---- стандартный пресет ----

    /** Обычный: о моде и устройстве. */
    static String shortText(Context ctx) {
        return about(ctx);
    }

    /** Развёрнутый: обычный + как писать темы MargyC и исходные цвета тем. */
    static String defaultText(Context ctx) {
        return about(ctx) + "\n\n" + themeGuide();
    }

    /** Суперразвёрнутый: всё о MargyC — функции, включённые моды, темы и палитра Material You телефона. */
    static String superText(Context ctx) {
        String you = materialYou(ctx);
        return about(ctx) + "\n\n" + features(ctx) + "\n\n" + themeGuide() + (you.isEmpty() ? "" : "\n" + you);
    }

    /** Коротко обо всех функциях MargyC и включённых своих модах. */
    static String features(Context ctx) {
        StringBuilder sb = new StringBuilder("MargyC " + Mods.label() + " features, all on the Mods screen "
                + "(\u041c\u043e\u0434\u044b in the side menu): Russian UI toggle; accent color and custom themes "
                + "(format below) plus a gallery of ready themes (AMOLED, Material You, Catppuccin, Nord, Dracula, "
                + "Gruvbox, Tokyo Night, Sepia); app-wide font choice (system, serif, Claude serif, monospace or a "
                + ".ttf/.otf file); app lock with fingerprint/face/PIN and hiding the chat in recent apps; update "
                + "checks with an optional beta channel; system prompt presets (this text); meme models: fake models on top of the model "
                + "picker, answered by a real model, with their own prompt, shared as text in this format:\n"
                + "MargyC model\nname: Fable 6969\ndescription: one line\nbase: <real model id>\nprompt:\n<prompt to the end>\n"
                + "(the user pastes it via Meme models -> Paste a model); Clawd pet on the message box that types on a laptop while "
                + "Claude answers and jumps or dances when the answer is ready; conversation "
                + "export to .md from the chat's menu and opening .md as a chat; custom mods (.mcmod plugins: dex + "
                + "manifest.json, also a one-tap mod catalog, docs at github.com/narezy/MargyC/blob/main/docs/plugins.md); crash log and journal.");
        List<Plugins.Info> mods = new ArrayList<Plugins.Info>();
        for (Plugins.Info info : Plugins.installed(ctx)) {
            if (Plugins.enabled(info.id)) {
                mods.add(info);
            }
        }
        if (!mods.isEmpty()) {
            sb.append("\nEnabled custom mods:");
            for (Plugins.Info info : mods) {
                sb.append("\n- ").append(info.name);
                if (!info.author.isEmpty()) {
                    sb.append(" by ").append(info.author);
                }
                if (!info.description.isEmpty()) {
                    sb.append(": ").append(info.description);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Палитра Material You (Android 12+): цвета, которые система берёт из обоев. По ней Claude может
     * сделать тему MargyC «под телефон».
     */
    static String materialYou(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) {
            return "";
        }
        String[] palettes = {"accent1", "accent2", "accent3", "neutral1", "neutral2"};
        int[] tones = {0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
        StringBuilder sb = new StringBuilder("The phone's Material You palette (Android dynamic colors from the "
                + "wallpaper). If the user asks for a theme matching their phone, system or wallpaper colors, "
                + "build it from these: neutral tones for backgrounds and text, accent1 for the accent.\n");
        for (String palette : palettes) {
            sb.append(palette).append(':');
            for (int tone : tones) {
                int id = ctx.getResources().getIdentifier("system_" + palette + "_" + tone, "color", "android");
                if (id != 0) {
                    sb.append(' ').append(tone).append('=').append(Theme.hex(ctx.getColor(id)).substring(1));
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String about(Context ctx) {
        return "Context added automatically by the app, not typed by the user. "
                + "The user is chatting with you through MargyC (Margy Claude), an unofficial modification "
                + "of the Claude Android app made by narezany. Telegram channel of the mod: "
                + "https://t.me/margyclaude, author: https://t.me/narezany.\n"
                + "User's device: " + device(ctx) + "\n"
                + "Use this when the user asks which app they are using, or about problems with their "
                + "phone or other apps on it. Do not bring it up otherwise.";
    }

    /** Как писать темы MargyC: чтобы Claude мог сделать тему по просьбе пользователя. */
    static String themeGuide() {
        StringBuilder sb = new StringBuilder();
        sb.append("MargyC themes: if asked for a theme, reply with one in a code block; the user pastes it via "
                + "Mods -> Paste theme and restarts. Format, one entry per line:\n"
                + "MargyC theme\n"
                + "accent: #RRGGBB (optional: Claude's orange and the blue selection color, all shades)\n"
                + "dark RRGGBB: #RRGGBB / light RRGGBB: #RRGGBB (original color of the dark/light theme -> new color)\n"
                + "Keys are the original colors below; unlisted colors stay. Keep text readable; for a full restyle "
                + "replace the whole gray scale, keeping light-to-dark order.\n");
        palette(sb, "Original dark theme colors", Names.DARK_PALETTE, true);
        palette(sb, "Original light theme colors", Names.LIGHT_PALETTE, false);
        return sb.toString();
    }

    private static void palette(StringBuilder sb, String title, int[] colors, boolean dark) {
        sb.append(title).append(":");
        for (int c : colors) {
            sb.append(' ').append(Theme.hex(c).substring(1));
            String role = Theme.role(c, dark);
            if (role != null) {
                sb.append(" (").append(english(role)).append(')');
            }
        }
        sb.append('\n');
    }

    /** Роли цветов для Claude по-английски. */
    private static String english(String role) {
        switch (role) {
            case "фирменный оранжевый (меняется и акцентом)": return "Claude orange, also changed by accent";
            case "тёмный фирменный оранжевый": return "dark Claude orange";
            case "выбранный пункт, ссылки (меняется и акцентом)": return "selected item, links, also changed by accent";
            case "включённый переключатель (меняется и акцентом)": return "switch on, also changed by accent";
            case "фон экранов и окон": return "screen and dialog background";
            case "карточки, строки настроек, поле ввода": return "cards, settings rows, input field";
            case "сообщения пользователя, приподнятые элементы": return "user message bubbles, raised elements";
            case "основной текст": return "main text";
            case "второстепенный текст": return "secondary text";
            case "заголовки разделов": return "section titles";
            case "выключенный переключатель, линии": return "switch off, lines";
            default: return role;
        }
    }

    static synchronized String device(Context ctx) {
        if (deviceInfo != null) {
            return deviceInfo;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(cap(Build.MANUFACTURER)).append(' ').append(Build.MODEL)
                .append(" (device ").append(Build.DEVICE).append(", brand ").append(Build.BRAND).append(')');
        sb.append("; Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT)
                .append(", security patch ").append(Build.VERSION.SECURITY_PATCH).append(')');
        if (Build.VERSION.SDK_INT >= 31 && !Build.SOC_MODEL.equals(Build.UNKNOWN)) {
            sb.append("; SoC ").append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL);
        } else {
            sb.append("; hardware ").append(Build.HARDWARE);
        }
        sb.append("; CPU ABIs ").append(String.join(", ", Build.SUPPORTED_ABIS));
        sb.append("; ").append(Runtime.getRuntime().availableProcessors()).append(" cores");
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            sb.append(String.format(Locale.US, "; RAM %.1f GB", mi.totalMem / 1073741824.0));
        } catch (Throwable ignored) {
        }
        try {
            DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            DisplayMetrics m = new DisplayMetrics();
            d.getRealMetrics(m);
            sb.append("; screen ").append(m.widthPixels).append('x').append(m.heightPixels)
                    .append(" px, ").append(m.densityDpi).append(" dpi, ")
                    .append(Math.round(d.getRefreshRate())).append(" Hz");
        } catch (Throwable ignored) {
        }
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            sb.append("; Claude app ").append(pi.versionName).append(" (MargyC build)");
        } catch (Throwable ignored) {
        }
        sb.append("; system language ").append(Locale.getDefault().toLanguageTag());
        deviceInfo = sb.toString();
        return deviceInfo;
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
