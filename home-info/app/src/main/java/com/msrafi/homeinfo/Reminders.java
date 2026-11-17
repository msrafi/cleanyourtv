package com.msrafi.homeinfo;

import android.content.Context;
import android.content.SharedPreferences;

/** The five lines under the prayer line. The phone page replaces the list shipped in config.xml. */
final class Reminders {
    private static final String SET = "reminders_set";
    private static final String TEXT = "reminders_text";

    private Reminders() { }

    static String text(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(Refresh.PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(SET, false)) return prefs.getString(TEXT, "");
        return EditorPage.join(ctx.getResources().getStringArray(R.array.reminders));
    }

    static void save(Context ctx, String text) {
        ctx.getSharedPreferences(Refresh.PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(SET, true)
                .putString(TEXT, text == null ? "" : text)
                .commit();
        ctx.getContentResolver().notifyChange(NowProvider.URI, null);
    }
}
