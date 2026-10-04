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

    private static Object owner;
    private static Object onClick, icon;
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

    /** Начало лямбды меню: запоминаем её, в её полях — состояние чата с uuid беседы. */
    public static void owner(Object o) {
        owner = o;
    }

    /** Внутри меню, после «На главный экран»: свой пункт той же Compose-функцией пункта меню. */
    public static void menu(Object composer) {
        if (!enabled() || Names.MENU_ITEM.isEmpty()) {
            return;
        }
        try {
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
                onClick = Bridge.function0("MargyC.export", () -> save(owner));
            }
            // значок — remember внутри композиции, поэтому берётся в каждой композиции заново
            if (icon == null) {
                icon = Bridge.icon(Class.forName(Names.ICON), "download", null);
            }
            Object painter = icon != null && paint != null ? paint.invoke(null, icon, composer) : null;
            Class<?>[] types = item.getParameterTypes();
            Object[] args = new Object[types.length];
            int composerIndex = -1;
            for (int i = 0; i < types.length; i++) {
                if (types[i].isInstance(composer) && composerIndex < 0 && i > 1) {
                    composerIndex = i;
                }
            }
            for (int i = 0; i < types.length; i++) {
                Class<?> t = types[i];
                args[i] = t == boolean.class ? Boolean.FALSE : t == int.class ? Integer.valueOf(0)
                        : t == long.class ? Long.valueOf(0) : t == float.class ? Float.valueOf(0) : null;
            }
            args[0] = L.t("Скачать диалог (.md)");
            args[1] = onClick;
            if (painter != null && types[3].isInstance(painter)) {
                args[3] = painter;
            }
            args[composerIndex] = composer;
            args[types.length - 1] = Integer.parseInt(Names.MENU_DEFAULTS);
            item.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new RuntimeException(e.getCause()); // упало внутри композиции — пусть видно в журнале вылетов
        } catch (Throwable t) {
            Log.e(Mods.TAG, "export menu", t);
        }
    }

    // ---- сохранение ----

    static void save(Object menuOwner) {
        Context ctx;
        try {
            ctx = Mods.app();
        } catch (Exception e) {
            return;
        }
        try {
            Set<String> ids = new LinkedHashSet<String>();
            collect(menuOwner, 0, ids, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()));
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
        } catch (Throwable t) {
            Log.e(Mods.TAG, "export", t);
            toast(ctx, L.t("Не получилось сохранить: ") + t);
        }
    }

    private static void toast(Context ctx, String s) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(ctx, s, Toast.LENGTH_LONG).show());
    }

    private static String safeName(String s) {
        String n = s.replaceAll("[\\\\/:*?\"<>|\\n\\r]+", " ").trim();
        return n.length() > 80 ? n.substring(0, 80).trim() : n;
    }

    /** uuid-строки в полях объекта и вложенных объектов (до двух уровней). */
    private static void collect(Object o, int depth, Set<String> out, Set<Object> seen) {
        if (o == null || depth > 3 || !seen.add(o)) {
            return;
        }
        if (o instanceof String) {
            java.util.regex.Matcher m = UUID.matcher((String) o);
            while (m.find()) {
                out.add(m.group());
            }
            return;
        }
        Class<?> c = o.getClass();
        if (c.isPrimitive() || c.getName().startsWith("java.") || c.getName().startsWith("android.")
                || c.getName().startsWith("kotlin.")) {
            return;
        }
        for (; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    collect(f.get(o), depth + 1, out, seen);
                } catch (Throwable ignored) {
                }
            }
        }
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
