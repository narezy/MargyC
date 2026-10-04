package cat.narezany.mods;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Мод «Мемные модели»: свои модели в меню выбора, например «Fable 6969». На самом деле отвечает
 * настоящая модель, выбранная для мемной, а свой промпт мемной модели уходит в hidden_context
 * вместе с пресетом мода.
 *
 * Меню показывает модели, которые есть и в ModelSelectorConfig.models, и в списке моделей
 * организации, поэтому мемная модель — копия записи настоящей в обоих списках, с другим id,
 * именем и описанием. Сервер мемного id не видит: в запросах он меняется на настоящий.
 */
public final class Fake {
    static final String PREFIX = "margyc-meme-";

    /** Мемная модель, выбранная для текущего SendMessage: ModelId создаётся прямо перед ним. */
    private static final ThreadLocal<Model> sending = new ThreadLocal<Model>();
    /** ModelId для сообщения создали, но модель настоящая. */
    private static final Model NONE = new Model("", "", "", "", "");

    private static final Map<String, String> ENTRY = parse(Names.FAKE_ENTRY);
    private static final Map<String, String> OPTION = parse(Names.FAKE_OPTION);
    private static final Map<String, String> AVAILABLE = parse(Names.FAKE_AVAILABLE);

    private Fake() {}

    static final class Model {
        final String id;
        final String name;
        final String description;
        final String base;
        final String prompt;

        Model(String id, String name, String description, String base, String prompt) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.base = base;
            this.prompt = prompt;
        }
    }

    // ---- хуки ----

    /** ModelSelectorConfig.models: мемные модели сверху списка «chat». */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List entries(String surface, List list) {
        try {
            if (list == null || !"chat".equals(surface)) {
                return list;
            }
            List result = withoutFakes(list, ENTRY.get("id"));
            remember(result);
            if (result.isEmpty()) {
                return result;
            }
            Object first = result.get(0);
            List<Object> fakes = new ArrayList<Object>();
            for (Model m : all()) {
                Object template = find(result, ENTRY.get("id"), m.base);
                if (template == null) {
                    continue; // настоящей модели у аккаунта нет
                }
                Object e = copy(template);
                set(e, ENTRY, "id", m.id);
                set(e, ENTRY, "name", m.name);
                set(e, ENTRY, "short_name", m.name);
                set(e, ENTRY, "description", m.description.isEmpty() ? null : m.description);
                set(e, ENTRY, "notice", null);
                set(e, ENTRY, "selection_notice", null);
                set(e, ENTRY, "section", get(first, ENTRY, "section"));
                set(e, ENTRY, "disabled", false);
                set(e, ENTRY, "badge", null);
                fakes.add(e);
            }
            result.addAll(0, fakes);
            log("menu: " + fakes.size() + " meme + " + (result.size() - fakes.size()) + " real");
            return result;
        } catch (Throwable t) {
            log("menu error: " + t);
            Log.e(Mods.TAG, "мемные модели: меню", t);
            return list;
        }
    }

    /** Organization.claude_ai_bootstrap_models_config: модели, доступные аккаунту. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List options(List list) {
        try {
            if (list == null) {
                return null;
            }
            List result = withoutFakes(list, OPTION.get("model"));
            List<Object> fakes = new ArrayList<Object>();
            for (Model m : all()) {
                Object template = find(result, OPTION.get("model"), m.base);
                if (template == null) {
                    continue;
                }
                Object o = copy(template);
                set(o, OPTION, "model", m.id);
                set(o, OPTION, "name", m.name);
                set(o, OPTION, "overflow", null);
                set(o, OPTION, "inactive", null);
                fakes.add(o);
            }
            result.addAll(0, fakes);
            log("models: " + fakes.size() + " meme + " + (result.size() - fakes.size()) + " real");
            return result;
        } catch (Throwable t) {
            log("models error: " + t);
            Log.e(Mods.TAG, "мемные модели: список моделей", t);
            return list;
        }
    }

    /**
     * AvailableModelsConfig.models: минимальный тариф моделей. Мемная получает тариф своей настоящей,
     * иначе приложение считает её моделью Pro и на бесплатном аккаунте не даёт с ней писать.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List available(List list) {
        try {
            if (list == null) {
                return null;
            }
            List result = withoutFakes(list, AVAILABLE.get("model_id"));
            List<Object> fakes = new ArrayList<Object>();
            for (Model m : all()) {
                Object template = find(result, AVAILABLE.get("model_id"), m.base);
                if (template != null) {
                    Object o = copy(template);
                    set(o, AVAILABLE, "model_id", m.id);
                    fakes.add(o);
                }
            }
            result.addAll(0, fakes);
            log("tiers: " + fakes.size() + " meme + " + (result.size() - fakes.size()) + " real");
            return result;
        } catch (Throwable t) {
            log("tiers error: " + t);
            return list;
        }
    }

    /**
     * ModelId(String): модель для отправки сообщения или смены модели беседы. Вместо мемного id
     * настоящий. Отправка с мемной запоминает её как выбранную.
     */
    public static String send(String id) {
        try {
            Model m = byId(id);
            sending.set(m != null ? m : NONE);
            log("send " + id + (m != null ? " -> " + m.base : ""));
            if (m != null) {
                select(m.id);
            }
            return m != null ? m.base : id;
        } catch (Throwable t) {
            return id;
        }
    }

    /** Основной конструктор ModelId (и разбор ответов сервера): только замена id. */
    public static String real(String id) {
        try {
            Model m = byId(id);
            if (m != null) {
                log("ModelId " + id + " -> " + m.base);
            }
            return m != null ? m.base : id;
        } catch (Throwable t) {
            return id;
        }
    }

    /** Тело PUT model_selector_state: сервер сохраняет настоящую модель, мемный выбор — у нас. */
    public static String remember(String id) {
        try {
            if (id == null) {
                return null;
            }
            Model m = byId(id);
            log("save default " + id);
            select(m != null ? m.id : "");
            return m != null ? m.base : id;
        } catch (Throwable t) {
            return id;
        }
    }

    /**
     * Выбор модели в чате (для нового чата, для следующего сообщения, в состоянии чата). Приложение
     * выставляет его и само, после ответа — настоящей моделью беседы. Поэтому мемная здесь только
     * запоминается, а настоящая модель выбранной мемной превращается обратно в мемную; сбрасывает
     * мемную только явный выбор другой модели в меню (сохранение выбора на сервер, {@link #remember}).
     */
    public static String pick(String id) {
        try {
            if (id == null) {
                return null;
            }
            if (byId(id) != null) {
                log("pick " + id + " (" + caller() + ")");
                select(id);
                return id;
            }
            Model m = selected();
            if (m != null && bare(m.base).equals(bare(id))) {
                log("pick " + id + " -> " + m.id + " (" + caller() + ")");
                return m.id;
            }
        } catch (Throwable ignored) {
        }
        return id;
    }

    private static void select(String id) throws Exception {
        if (!id.equals(Mods.prefs().getString("fake_selected", ""))) {
            log("selected: " + (id.isEmpty() ? "-" : id));
            Mods.prefs().edit().putString("fake_selected", id).apply();
        }
    }

    static Model selected() throws Exception {
        return byId(Mods.prefs().getString("fake_selected", ""));
    }

    /**
     * Модель с сервера (ModelSelectorState, настройка MODEL беседы, модель беседы в состоянии чата):
     * если это настоящая модель выбранной мемной, вернуть мемную, иначе после ответа чат переключится.
     */
    public static String restore(String id) {
        try {
            Model m = selected();
            String out = m != null && id != null && bare(m.base).equals(bare(id)) ? m.id : id;
            if (id != null && (out != id || id.startsWith(PREFIX) || isBase(id))) {
                log("restore " + id + (out != id ? " -> " + out : "") + " (" + caller() + ")");
            }
            return out;
        } catch (Throwable t) {
            return id;
        }
    }

    /**
     * Промпт мемной модели для hidden_context текущего сообщения, или null. Если для сообщения
     * ModelId не создавали (приложение не пишет модель, когда она совпадает с сохранённой на сервере),
     * берётся выбранная мемная модель.
     */
    static String prompt() {
        Model m = sending.get();
        sending.remove(); // при копировании запроса ModelId не создаётся заново
        try {
            if (m == null) {
                m = selected();
                log("message without ModelId, selected: " + (m != null ? m.id : "-"));
            } else {
                log("message with ModelId" + (m != NONE ? " " + m.id : ""));
            }
        } catch (Throwable ignored) {
        }
        if (m == null || m == NONE) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Context added automatically by the app, not typed by the user. For fun, the user picked a joke model "
                + "named \"").append(m.name).append("\" in the model list of MargyC");
        if (!m.description.isEmpty()) {
            sb.append(" (its description: \"").append(m.description).append("\")");
        }
        sb.append(". Play along: in this chat your model name is ").append(m.name)
                .append(", say so if asked which model you are.");
        if (!m.prompt.trim().isEmpty()) {
            sb.append("\nInstructions for this model from the user:\n").append(m.prompt.trim());
        }
        return sb.toString();
    }

    private static boolean isBase(String id) throws Exception {
        for (Model m : all()) {
            if (bare(m.base).equals(bare(id))) {
                return true;
            }
        }
        return false;
    }

    // ---- журнал: чтобы по телефону было видно, какие хуки сработали ----

    private static final java.util.ArrayDeque<String> journal = new java.util.ArrayDeque<String>();

    static synchronized void log(String line) {
        String t = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
        journal.addLast(t + " " + line);
        while (journal.size() > 150) {
            journal.removeFirst();
        }
    }

    static synchronized String journal() {
        StringBuilder sb = new StringBuilder();
        for (String line : journal) {
            sb.append(line).append('\n');
        }
        return sb.length() == 0 ? L.t("Пока пусто.") : sb.toString();
    }

    /** Класс, который позвал хук (обфусцированное имя, для журнала). */
    private static String caller() {
        StackTraceElement[] st = new Throwable().getStackTrace(); // caller, хук мода, место хука, кто его позвал
        return st.length > 3 ? st[2].getClassName() + " < " + st[3].getClassName() + "." + st[3].getMethodName() : "?";
    }

    /** id без суффикса вроде «[1m]». */
    private static String bare(String id) {
        int i = id.indexOf('[');
        return i < 0 ? id : id.substring(0, i);
    }

    // ---- настройки ----

    private static String cachedJson;
    private static List<Model> cached;

    /** Хуки зовут это на каждый ModelId, поэтому разобранный список кэшируется. */
    static synchronized List<Model> all() throws Exception {
        String json = Mods.prefs().getString("fake_models", "[]");
        if (json.equals(cachedJson)) {
            return cached;
        }
        List<Model> list = new ArrayList<Model>();
        JSONArray a = new JSONArray(json);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            list.add(new Model(o.getString("id"), o.getString("name"), o.optString("description"),
                    o.getString("base"), o.optString("prompt")));
        }
        cachedJson = json;
        cached = list;
        return list;
    }

    static Model byId(String id) throws Exception {
        if (id == null || !id.startsWith(PREFIX)) {
            return null;
        }
        for (Model m : all()) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    /** Новая (id == null) или изменённая модель. */
    static void save(String id, String name, String description, String base, String prompt) throws Exception {
        JSONArray a = new JSONArray(Mods.prefs().getString("fake_models", "[]"));
        JSONArray out = new JSONArray();
        if (id == null) {
            id = PREFIX + UUID.randomUUID().toString().substring(0, 8);
        }
        JSONObject model = new JSONObject().put("id", id).put("name", name).put("description", description)
                .put("base", base).put("prompt", prompt);
        boolean found = false;
        for (int i = 0; i < a.length(); i++) {
            if (a.getJSONObject(i).getString("id").equals(id)) {
                out.put(model);
                found = true;
            } else {
                out.put(a.getJSONObject(i));
            }
        }
        if (!found) {
            out.put(model);
        }
        Mods.prefs().edit().putString("fake_models", out.toString()).apply();
    }

    static void delete(String id) throws Exception {
        JSONArray a = new JSONArray(Mods.prefs().getString("fake_models", "[]"));
        JSONArray out = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            if (!a.getJSONObject(i).getString("id").equals(id)) {
                out.put(a.getJSONObject(i));
            }
        }
        Mods.prefs().edit().putString("fake_models", out.toString()).apply();
    }

    // ---- обмен моделями текстом, как темами ----

    static final String HEADER = "MargyC model";

    /** Модель текстом: заголовок, поля «ключ: значение», в конце промпт до конца текста. */
    static String export(Model m) {
        return HEADER + "\nname: " + m.name + "\ndescription: " + m.description.replace('\n', ' ')
                + "\nbase: " + m.base + "\nprompt:\n" + m.prompt.trim() + "\n";
    }

    /**
     * Модели из текста (их может быть несколько подряд). Если настоящей модели из текста у аккаунта нет,
     * отвечать будет первая из меню. Возвращает число добавленных.
     */
    static int importText(String text) throws Exception {
        int n = 0;
        String[] parts = text.replace("\r", "").split("(?m)^\\s*" + HEADER + "\\s*$");
        Map<String, String> real = realModels();
        for (int i = 1; i < parts.length; i++) {
            String name = "", description = "", base = "";
            StringBuilder prompt = null;
            for (String line : parts[i].split("\n", -1)) {
                if (prompt != null) {
                    prompt.append(line).append('\n');
                } else if (line.trim().equals("prompt:")) {
                    prompt = new StringBuilder();
                } else if (line.startsWith("name:")) {
                    name = line.substring(5).trim();
                } else if (line.startsWith("description:")) {
                    description = line.substring(12).trim();
                } else if (line.startsWith("base:")) {
                    base = line.substring(5).trim();
                }
            }
            if (name.isEmpty()) {
                continue;
            }
            if (!real.isEmpty() && !real.containsKey(base)) {
                base = real.keySet().iterator().next();
            }
            if (base.isEmpty()) {
                continue;
            }
            save(null, name, description, base, prompt != null ? prompt.toString().trim() : "");
            n++;
        }
        return n;
    }

    /** Настоящие модели из меню (id -> название), их мод запоминает при загрузке меню. */
    static Map<String, String> realModels() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        try {
            JSONArray a = new JSONArray(Mods.prefs().getString("real_models", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                map.put(o.getString("id"), o.getString("name"));
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    static String realName(String id) {
        String name = realModels().get(id);
        return name != null ? name : id;
    }

    @SuppressWarnings("rawtypes")
    private static void remember(List entries) throws Exception {
        JSONArray a = new JSONArray();
        for (Object e : entries) {
            Object name = get(e, ENTRY, "name");
            a.put(new JSONObject().put("id", get(e, ENTRY, "id")).put("name", name != null ? name : ""));
        }
        String json = a.toString();
        if (!json.equals(Mods.prefs().getString("real_models", ""))) {
            Mods.prefs().edit().putString("real_models", json).apply();
        }
    }

    // ---- копии записей ----

    /** Список без мемных моделей: закэшированный конфиг может прийти уже с ними. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List withoutFakes(List list, String idField) throws Exception {
        List result = new ArrayList();
        for (Object o : list) {
            Object id = o == null ? null : field(o.getClass(), idField).get(o);
            if (!(id instanceof String && ((String) id).startsWith(PREFIX))) {
                result.add(o);
            }
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    private static Object find(List list, String idField, String id) throws Exception {
        for (Object o : list) {
            if (o != null && id.equals(field(o.getClass(), idField).get(o))) {
                return o;
            }
        }
        return null;
    }

    private static Object unsafe;

    /** Копия data-класса поле в поле, без конструктора. */
    private static Object copy(Object src) throws Exception {
        Class<?> u = Class.forName("sun.misc.Unsafe");
        if (unsafe == null) {
            Field f = u.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = f.get(null);
        }
        Object dst = u.getMethod("allocateInstance", Class.class).invoke(unsafe, src.getClass());
        for (Field f : src.getClass().getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                f.set(dst, f.get(src));
            }
        }
        return dst;
    }

    private static Object get(Object o, Map<String, String> fields, String element) throws Exception {
        return field(o.getClass(), fields.get(element)).get(o);
    }

    private static void set(Object o, Map<String, String> fields, String element, Object value) throws Exception {
        Field f = field(o.getClass(), fields.get(element));
        if (f.getType() == boolean.class) {
            f.setBoolean(o, (Boolean) value);
        } else {
            f.set(o, value);
        }
    }

    private static final Map<String, Field> cache = new HashMap<String, Field>();

    private static synchronized Field field(Class<?> c, String name) throws Exception {
        String key = c.getName() + "." + name;
        Field f = cache.get(key);
        if (f == null) {
            f = c.getDeclaredField(name);
            f.setAccessible(true);
            cache.put(key, f);
        }
        return f;
    }

    private static Map<String, String> parse(String s) {
        Map<String, String> map = new HashMap<String, String>();
        for (String pair : s.split(",")) {
            int eq = pair.indexOf('=');
            map.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return map;
    }
}
