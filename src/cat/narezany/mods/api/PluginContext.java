package cat.narezany.mods.api;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;

import java.io.File;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** What MargyC gives a mod. API version: {@link #apiVersion()}, currently 1. */
public interface PluginContext {
    /** Version of this API. New methods come with a higher version; existing ones never change. */
    int apiVersion();

    /** MargyC version, e.g. "14". */
    String margycVersion();

    Application app();

    /** Mod id from manifest.json. */
    String id();

    /** Mod folder: manifest.json, classes.dex, icon.png. Read only. */
    File dir();

    /** The mod's own storage. Setting values from manifest.json live here too (key = setting key). */
    SharedPreferences prefs();

    /** Value of a manifest.json setting, or its default if the user has not changed it. */
    boolean getBoolean(String key);

    String getString(String key);

    int getInt(String key);

    /** A line in the MargyC journal (Моды → Мемные модели → Журнал). */
    void log(String message);

    /**
     * Hidden context for every message: Claude sees it, the chat does not (like the system prompt
     * preset). The supplier is called on every send; null or an empty string adds nothing.
     */
    void addPromptContext(Supplier<String> provider);

    /**
     * Filter for plain UI strings (Compose Text(String); chat messages are not included). Called very
     * often: keep it fast and return the same string when there is nothing to change.
     */
    void addTextFilter(UnaryOperator<String> filter);

    /**
     * Filter for colors: every app color (ARGB) made through Color(Long), after the MargyC accent.
     * A hot path too. Colors are read at start, so changes show after a restart.
     */
    void addColorFilter(IntUnaryOperator filter);

    /** Activity callbacks for the app (Claude screens and MargyC screens). */
    void addActivityCallbacks(Application.ActivityLifecycleCallbacks callbacks);

    /** The Claude activity in the foreground, or null. */
    Activity currentActivity();
}
