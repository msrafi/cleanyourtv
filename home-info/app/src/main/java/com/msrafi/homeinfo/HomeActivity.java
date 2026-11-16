package com.msrafi.homeinfo;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** A place to land if the app is opened on its own. The strip itself is drawn by Home. */
public final class HomeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        text.setText(R.string.blurb);
        text.setTextColor(Color.WHITE);
        text.setTextSize(22);
        text.setGravity(Gravity.CENTER);
        int pad = Math.round(48 * getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad);
        setContentView(text);
        Refresh.kick(this);
    }
}
