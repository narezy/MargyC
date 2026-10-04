package cat.narezany.mods;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Мод «Скачать диалог»: пункт в меню «⋮» чата сохраняет беседу в Загрузки/MargyC/*.md.
 *
 * Сообщения берутся из локального кэша приложения (SQLite): таблица snapshot_messages, body — JSON
 * CachedMessageRecord (sender, created_at, parent_message_id, blocks[].text). Если её нет — старая
 * cachedMessages с JSON сообщений claude.ai. uuid беседы ищется в полях лямбды меню и сверяется с базой.
 */
public final class Export {
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** Параметры функции меню (лямбды с захваченным состоянием экрана): в них uuid беседы или события сессии. */
    private static final List<Object> owners = new ArrayList<Object>();
    private static final List<Object> codeOwners = new ArrayList<Object>();
    private static Object chatClick, codeClick, icon;
    private static Method item, paint;

    private Export() {}

    static boolean enabled() {
        try {
            return Mods.prefs().getBoolean("export_on", true);
        } catch (Exception e) {
            return false;
        }
    }

    static void setEnabled(boolean on) throws Exception {
        Mods.prefs().edit().putBoolean("export_on", on).apply();
    }

    public static void owner(Object o) {
        owners.clear();
        owners.add(o);
    }

    public static void ownerAlso(Object o) {
        owners.add(o);
    }

    public static void codeOwner(Object o) {
        codeOwners.clear();
        codeOwners.add(o);
    }

    public static void codeOwnerAlso(Object o) {
        codeOwners.add(o);
    }

    /** Меню чата, после «На главный экран». */
    public static void menu(Object composer) {
        try {
            if (chatClick == null) {
                chatClick = Bridge.function0("MargyC.export", () -> {
                    final List<Object> roots = new ArrayList<Object>(owners);
                    new Thread(() -> save(roots), "MargyC export").start();
                });
            }
            draw(composer, chatClick);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            Log.e(Mods.TAG, "export menu", t);
        }
    }

    /** Меню сессии Code, после «Поделиться». */
    public static void codeMenu(Object composer) {
        try {
            if (codeClick == null) {
                codeClick = Bridge.function0("MargyC.exportCode", () -> {
                    final List<Object> roots = new ArrayList<Object>(codeOwners);
                    new Thread(() -> saveCode(roots), "MargyC export").start();
                });
            }
            draw(composer, codeClick);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            Log.e(Mods.TAG, "export code menu", t);
        }
    }

    /** Свой пункт той же Compose-функцией пункта меню, что и пункты приложения. */
    private static void draw(Object composer, Object onClick) throws Exception {
        if (!enabled() || Names.MENU_ITEM.isEmpty()) {
            return;
        }
        if (item == null) {
            for (Method m : Class.forName(Names.MENU_ITEM).getDeclaredMethods()) {
                if (m.getName().equals(Names.MENU_ITEM_METHOD) && Modifier.isStatic(m.getModifiers())
                        && m.getParameterTypes().length > 4 && m.getParameterTypes()[0] == String.class) {
                    item = m;
                }
            }
            for (Method m : Class.forName(Names.PAINTER).getDeclaredMethods()) {
                if (m.getName().equals(Names.PAINTER_METHOD) && m.getParameterTypes().length == 2) {
                    paint = m;
                }
            }
            icon = Bridge.icon(Class.forName(Names.ICON), "download", null);
        }
        // значок — remember внутри композиции, поэтому painter берётся в каждой композиции заново
        Object painter = icon != null && paint != null ? paint.invoke(null, icon, composer) : null;
        Class<?>[] types = item.getParameterTypes();
        Object[] args = new Object[types.length];
        int composerIndex = -1;
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            args[i] = t == boolean.class ? Boolean.FALSE : t == int.class ? Integer.valueOf(0)
                    : t == long.class ? Long.valueOf(0) : t == float.class ? Float.valueOf(0) : null;
            if (i > 1 && composerIndex < 0 && t.isInstance(composer)) {
                composerIndex = i;
            }
        }
        args[0] = L.t("Скачать диалог (.md)");
        args[1] = onClick;
        if (painter != null && types[3].isInstance(painter)) {
            args[3] = painter;
        }
        args[composerIndex] = composer;
        args[types.length - 1] = Integer.parseInt(Names.MENU_DEFAULTS);
        try {
            item.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new RuntimeException(e.getCause()); // упало внутри композиции: пусть будет виден вылет
        }
    }

    // ---- сохранение ----

    static void save(List<Object> roots) {
        Context ctx;
        try {
            ctx = Mods.app();
        } catch (Exception e) {
            return;
        }
        try {
            Set<String> ids = uuids(roots);
            Conversation c = null;
            for (String id : ids) {
                c = load(ctx, id);
                if (c != null) {
                    break;
                }
            }
            if (c == null) {
                toast(ctx, L.t("Не нашёл этот диалог в кэше приложения. Пролистай его до начала и попробуй ещё раз."));
                Fake.log("export: no conversation among " + ids.size() + " uuids");
                return;
            }
            write(ctx, c);
        } catch (Throwable t) {
            Log.e(Mods.TAG, "export", t);
            toast(ctx, L.t("Не получилось сохранить: ") + t);
        }
    }

    /** Беседа в Загрузки/MargyC/<название>.md. */
    private static void write(Context ctx, Conversation c) throws Exception {
        String md = markdown(c);
        String name = safeName(c.title.isEmpty() ? "Claude" : c.title) + ".md";
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "text/markdown");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MargyC");
        Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        try (OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
            out.write(md.getBytes("UTF-8"));
        }
        toast(ctx, L.t("Сохранено: Загрузки/MargyC/") + name);
        Fake.log("export: " + c.messages.size() + " messages -> " + name);
    }

    private static void toast(Context ctx, String s) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(ctx, s, Toast.LENGTH_LONG).show());
    }

    private static String safeName(String s) {
        String n = s.replaceAll("[\\\\/:*?\"<>|\\n\\r]+", " ").trim();
        return n.length() > 80 ? n.substring(0, 80).trim() : n;
    }

    interface Visitor {
        /** false — дальше не обходить. */
        boolean visit(Object o, int depth);
    }

    /**
     * Обход в ширину от корней по полям объектов приложения, а также по спискам, словарям и массивам.
     * Классы Android, Java и Kotlin, кроме коллекций, не обходятся: там нет состояния экрана, зато много лишнего.
     */
    static void walk(List<Object> roots, int maxDepth, int maxNodes, Visitor v) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<Object[]>();
        for (Object r : roots) {
            queue.add(new Object[] {r, 0});
        }
        int n = 0;
        while (!queue.isEmpty() && n++ < maxNodes) {
            Object[] e = queue.poll();
            Object o = e[0];
            int depth = (Integer) e[1];
            if (o == null || !seen.add(o) || !v.visit(o, depth) || depth >= maxDepth) {
                continue;
            }
            List<Object> next = new ArrayList<Object>();
            if (o instanceof java.util.Collection) {
                try {
                    next.addAll((java.util.Collection<?>) o);
                } catch (Throwable ignored) {
                }
            } else if (o instanceof Map) {
                try {
                    next.addAll(((Map<?, ?>) o).values());
                } catch (Throwable ignored) {
                }
            } else if (o instanceof Object[]) {
                next.addAll(java.util.Arrays.asList((Object[]) o));
            } else {
                String name = o.getClass().getName();
                if (name.startsWith("java.") || name.startsWith("android.") || name.startsWith("kotlin.")
                        || name.startsWith("dalvik.") || o instanceof Class) {
                    continue;
                }
                for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) {
                            continue;
                        }
                        try {
                            f.setAccessible(true);
                            next.add(f.get(o));
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            for (Object x : next) {
                if (x != null) {
                    queue.add(new Object[] {x, depth + 1});
                }
            }
        }
    }

    /** uuid-строки рядом с корнями: ближние первыми. */
    private static Set<String> uuids(List<Object> roots) {
        final Set<String> out = new LinkedHashSet<String>();
        walk(roots, 5, 40000, (o, depth) -> {
            if (o instanceof String) {
                java.util.regex.Matcher m = UUID.matcher((String) o);
                while (m.find()) {
                    out.add(m.group());
                }
                return false;
            }
            return true;
        });
        return out;
    }

    // ---- сессия Code: события из памяти ----

    static void saveCode(List<Object> roots) {
        Context ctx;
        try {
            ctx = Mods.app();
        } catch (Exception e) {
            return;
        }
        try {
            final Map<String, String> ev = fields(Names.SESSION_EVENT);
            if (ev.isEmpty()) {
                toast(ctx, L.t("Не нашёл этот диалог в кэше приложения. Пролистай его до начала и попробуй ещё раз."));
                return;
            }
            final Class<?> eventCls = Class.forName(ev.get("cls"));
            final List<?>[] best = {null};
            walk(roots, 8, 60000, (o, depth) -> {
                if (o instanceof List) {
                    List<?> l = (List<?>) o;
                    int events = 0;
                    try {
                        for (Object x : l) {
                            if (eventCls.isInstance(x)) {
                                events++;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    if (events > 0 && (best[0] == null || l.size() > best[0].size())) {
                        best[0] = l;
                    }
                }
                return !eventCls.isInstance(o); // внутрь событий не нужно
            });
            if (best[0] == null) {
                toast(ctx, L.t("Не нашёл переписку этой сессии. Открой её и попробуй ещё раз."));
                Fake.log("export code: no event list");
                return;
            }
            Conversation c = session(new ArrayList<Object>(best[0]), eventCls, ev);
            c.title = "Claude Code";
            write(ctx, c);
        } catch (Throwable t) {
            Log.e(Mods.TAG, "export code", t);
            toast(ctx, L.t("Не получилось сохранить: ") + t);
        }
    }

    private static Conversation session(List<Object> events, Class<?> eventCls, Map<String, String> ev) throws Exception {
        Map<String, String> assistant = fields(Names.SESSION_ASSISTANT), user = fields(Names.SESSION_USER);
        Class<?> assistantCls = Class.forName(assistant.get("cls")), userCls = Class.forName(user.get("cls"));
        Conversation conv = new Conversation();
        for (Object e : events) {
            if (!eventCls.isInstance(e)) {
                continue;
            }
            Object msg = get(e, ev.get("message"));
            Message m = new Message();
            if (assistantCls.isInstance(msg)) {
                m.sender = "assistant";
                m.text = texts(get(msg, assistant.get("content")));
            } else if (userCls.isInstance(msg)) {
                Object role = get(msg, user.get("role"));
                m.sender = role instanceof String && ((String) role).equals("user") ? "human" : String.valueOf(role);
                m.text = texts(get(msg, user.get("content")));
            } else {
                continue;
            }
            if (!m.text.isEmpty()) {
                conv.messages.add(m);
            }
        }
        return conv;
    }

    /** Текст блоков сообщения: ContentBlock.Text и ApiUserMessageContent.Text, или просто строка. */
    private static String texts(Object content) throws Exception {
        if (content instanceof String) {
            return ((String) content).trim();
        }
        final Map<String, String> text = fields(Names.SESSION_TEXT), userText = fields(Names.SESSION_USER_TEXT);
        final Class<?> textCls = Class.forName(text.get("cls")), userTextCls = Class.forName(userText.get("cls"));
        final StringBuilder sb = new StringBuilder();
        List<Object> roots = new ArrayList<Object>();
        roots.add(content);
        walk(roots, 4, 2000, (o, depth) -> {
            try {
                Object t = textCls.isInstance(o) ? get(o, text.get("text"))
                        : userTextCls.isInstance(o) ? get(o, userText.get("text")) : null;
                if (t instanceof String && !((String) t).trim().isEmpty()) {
                    sb.append(((String) t).trim()).append("\n\n");
                    return false;
                }
            } catch (Throwable ignored) {
            }
            return true;
        });
        return sb.toString().trim();
    }

    private static Object get(Object o, String field) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static Map<String, String> fields(String s) {
        Map<String, String> map = new HashMap<String, String>();
        for (String pair : s.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                map.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return map;
    }

    // ---- беседа из кэша ----

    static final class Message {
        String sender = "", text = "";
        long created;
    }

    static final class Conversation {
        String id = "", title = "";
        List<Message> messages = new ArrayList<Message>();
    }

    private static Conversation load(Context ctx, String id) {
        for (String name : ctx.databaseList()) {
            if (name.endsWith("-journal") || name.endsWith("-wal") || name.endsWith("-shm")) {
                continue;
            }
            SQLiteDatabase db = null;
            try {
                db = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).getPath(), null, SQLiteDatabase.OPEN_READONLY);
                Conversation c = fromSnapshots(db, id);
                if (c == null) {
                    c = fromCached(db, id);
                }
                if (c != null) {
                    c.title = title(db, id);
                    return c;
                }
            } catch (Throwable ignored) {
            } finally {
                if (db != null) {
                    db.close();
                }
            }
        }
        return null;
    }

    private static boolean hasTable(SQLiteDatabase db, String table) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", new String[] {table})) {
            return c.moveToFirst();
        }
    }

    /** Новый кэш: snapshot_messages.body — JSON CachedMessageRecord, ветка — от leaf_message_id к корню. */
    private static Conversation fromSnapshots(SQLiteDatabase db, String id) throws Exception {
        if (!hasTable(db, "snapshot_messages")) {
            return null;
        }
        Map<String, JSONObject> byId = new HashMap<String, JSONObject>();
        Map<String, String> parent = new HashMap<String, String>();
        try (Cursor c = db.rawQuery("SELECT message_id, parent_message_id, body FROM snapshot_messages WHERE conversation_id=?",
                new String[] {id})) {
            while (c.moveToNext()) {
                byId.put(c.getString(0), new JSONObject(new String(c.getBlob(2), "UTF-8")));
                parent.put(c.getString(0), c.getString(1));
            }
        }
        if (byId.isEmpty()) {
            return null;
        }
        String leaf = null;
        if (hasTable(db, "snapshot_headers")) {
            try (Cursor c = db.rawQuery("SELECT leaf_message_id FROM snapshot_headers WHERE conversation_id=?", new String[] {id})) {
                if (c.moveToFirst()) {
                    leaf = c.getString(0);
                }
            }
        }
        List<JSONObject> chain = new ArrayList<JSONObject>();
        if (leaf != null && byId.containsKey(leaf)) {
            for (String m = leaf; m != null && byId.containsKey(m) && chain.size() <= byId.size(); m = parent.get(m)) {
                chain.add(byId.get(m));
            }
            Collections.reverse(chain);
        } else {
            chain.addAll(byId.values());
            Collections.sort(chain, (a, b) -> Integer.compare(a.optInt("index"), b.optInt("index")));
        }
        Conversation conv = new Conversation();
        conv.id = id;
        for (JSONObject o : chain) {
            Message m = new Message();
            m.sender = o.optString("sender");
            m.created = time(o.opt("created_at"));
            StringBuilder sb = new StringBuilder();
            JSONArray blocks = o.optJSONArray("blocks");
            for (int i = 0; blocks != null && i < blocks.length(); i++) {
                appendBlock(sb, blocks.optJSONObject(i));
            }
            m.text = sb.toString().trim();
            conv.messages.add(m);
        }
        return conv;
    }

    private static void appendBlock(StringBuilder sb, JSONObject block) {
        if (block == null) {
            return;
        }
        JSONObject content = block.optJSONObject("content");
        String type = content != null ? content.optString("type") : "";
        String text = block.optString("text", "");
        if (text.isEmpty() && content != null) {
            text = content.optString("text", "");
        }
        if (type.contains("thinking")) {
            String thinking = content.optString("thinking", text);
            if (!thinking.isEmpty()) {
                sb.append("<details><summary>").append(L.t("Размышления")).append("</summary>\n\n")
                        .append(thinking.trim()).append("\n\n</details>\n\n");
            }
            return;
        }
        if (type.contains("tool_use")) {
            sb.append("*🔧 ").append(content.optString("name", "tool")).append("*\n\n");
            return;
        }
        if (type.contains("tool_result")) {
            return;
        }
        if (!text.isEmpty()) {
            sb.append(text.trim()).append("\n\n");
        }
    }

    /** Старый кэш: cachedMessages.message_json — сообщение claude.ai (sender, text или content[]). */
    private static Conversation fromCached(SQLiteDatabase db, String id) throws Exception {
        if (!hasTable(db, "cachedMessages")) {
            return null;
        }
        List<JSONObject> list = new ArrayList<JSONObject>();
        try (Cursor c = db.rawQuery("SELECT message_json FROM cachedMessages WHERE conversation_uuid=?", new String[] {id})) {
            while (c.moveToNext()) {
                list.add(new JSONObject(c.getString(0)));
            }
        }
        if (list.isEmpty()) {
            return null;
        }
        Collections.sort(list, (a, b) -> Integer.compare(a.optInt("index"), b.optInt("index")));
        Conversation conv = new Conversation();
        conv.id = id;
        for (JSONObject o : list) {
            Message m = new Message();
            m.sender = o.optString("sender");
            m.created = time(o.opt("created_at"));
            StringBuilder sb = new StringBuilder();
            JSONArray content = o.optJSONArray("content");
            for (int i = 0; content != null && i < content.length(); i++) {
                JSONObject b = content.optJSONObject(i);
                if (b != null) {
                    appendBlock(sb, new JSONObject().put("content", b).put("text", b.optString("text")));
                }
            }
            m.text = sb.length() > 0 ? sb.toString().trim() : o.optString("text").trim();
            conv.messages.add(m);
        }
        return conv;
    }

    private static String title(SQLiteDatabase db, String id) {
        try {
            if (hasTable(db, "cachedConversations")) {
                try (Cursor c = db.rawQuery("SELECT conversation_json FROM cachedConversations WHERE uuid=?", new String[] {id})) {
                    if (c.moveToFirst()) {
                        return new JSONObject(c.getString(0)).optString("name");
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static long time(Object v) {
        if (v instanceof Number) {
            long t = ((Number) v).longValue();
            return t < 100000000000L ? t * 1000 : t;
        }
        if (v instanceof String) {
            try {
                return java.time.Instant.parse((String) v).toEpochMilli();
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    static boolean isUser(String sender) {
        return sender.equals("human") || sender.equals("user") || sender.equalsIgnoreCase("HUMAN");
    }

    static String markdown(Conversation c) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(c.title.isEmpty() ? "Claude" : c.title).append("\n\n");
        sb.append("<!-- MargyC export · conversation ").append(c.id).append(" -->\n\n");
        for (Message m : c.messages) {
            if (m.text.isEmpty()) {
                continue;
            }
            sb.append("## ").append(isUser(m.sender) ? L.t("Ты") : "Claude");
            if (m.created > 0) {
                sb.append(" · ").append(f.format(new Date(m.created)));
            }
            sb.append("\n\n").append(m.text).append("\n\n");
        }
        return sb.toString();
    }

    // ---- обратно: .md -> сообщения ----

    /** Разбор .md (своего экспорта или любого с заголовками «## Имя»): пары (отправитель, текст). */
    static Conversation parse(String md) {
        Conversation c = new Conversation();
        Message cur = null;
        StringBuilder text = new StringBuilder();
        for (String line : md.replace("\r", "").split("\n", -1)) {
            if (line.startsWith("# ") && c.title.isEmpty() && cur == null) {
                c.title = line.substring(2).trim();
            } else if (line.startsWith("## ")) {
                if (cur != null) {
                    cur.text = text.toString().trim();
                    c.messages.add(cur);
                }
                cur = new Message();
                String who = line.substring(3).split(" · ")[0].trim();
                cur.sender = who.equalsIgnoreCase("Claude") || who.equalsIgnoreCase("Assistant") ? "assistant" : "human";
                text.setLength(0);
            } else if (cur != null) {
                text.append(line).append('\n');
            }
        }
        if (cur != null) {
            cur.text = text.toString().trim();
            c.messages.add(cur);
        }
        return c;
    }
}
