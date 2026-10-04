package com.example.hello;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.widget.Toast;

import cat.narezany.mods.api.MargyCPlugin;
import cat.narezany.mods.api.PluginContext;

/** Example MargyC mod: settings, hidden prompt context and an activity callback. */
public final class HelloMod implements MargyCPlugin {
    @Override
    public void onCreate(final PluginContext ctx) {
        ctx.log("hello from " + ctx.id() + ", MargyC " + ctx.margycVersion());

        // Hidden context for every message: Claude sees it, the chat does not.
        ctx.addPromptContext(() -> {
            StringBuilder sb = new StringBuilder();
            String nick = ctx.getString("nickname").trim();
            if (!nick.isEmpty()) {
                sb.append("Call the user \"").append(nick).append("\". ");
            }
            if (ctx.getBoolean("pirate")) {
                sb.append("Answer like a friendly pirate, arr.");
            }
            return sb.toString();
        });

        // A toast when the first Claude screen opens.
        final String greeting = ctx.getString("greeting");
        if (!greeting.equals("Off")) {
            ctx.addActivityCallbacks(new Application.ActivityLifecycleCallbacks() {
                private boolean shown;

                @Override
                public void onActivityResumed(Activity a) {
                    if (!shown && !a.getClass().getName().startsWith("cat.narezany.mods.")) {
                        shown = true;
                        Toast.makeText(a, greeting, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onActivityCreated(Activity a, Bundle b) {}

                @Override
                public void onActivityStarted(Activity a) {}

                @Override
                public void onActivityPaused(Activity a) {}

                @Override
                public void onActivityStopped(Activity a) {}

                @Override
                public void onActivitySaveInstanceState(Activity a, Bundle b) {}

                @Override
                public void onActivityDestroyed(Activity a) {}
            });
        }
    }
}
