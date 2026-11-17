package com.gathin.tvhome;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * This month, in the open space to the right of the reminders. US holidays are gold, Islamic days
 * are green, and today has a white ring. The launcher only draws the text it was given.
 */
final class MonthGrid {
    private static final int US = 0xFFF5C16C;
    private static final int HIJRI = 0xFF2BB673;
    private static final int PLAIN = 0xB3FFFFFF;
    private static final int NOTES = 4;
    private static final String[] WEEK = {"S", "M", "T", "W", "T", "F", "S"};

    private final float density;
    private final int stroke;
    private final LinearLayout view;
    private final TextView title;
    private final LinearLayout[] rows = new LinearLayout[6];
    private final TextView[] days = new TextView[42];
    private final TextView[] notes = new TextView[NOTES];

    MonthGrid(android.app.Activity activity, float density) {
        this.density = density;
        this.stroke = Math.max(2, Math.round(density));
        view = column(activity);
        view.setFocusable(false);
        title = label(activity, 12, 0xB3FFFFFF);
        title.setLetterSpacing(0.18f);
        title.setAllCaps(true);
        view.addView(title);
        LinearLayout week = row(activity);
        for (String name : WEEK) {
            TextView day = label(activity, 11, 0x80FFFFFF);
            day.setGravity(Gravity.CENTER);
            week.addView(day, cell());
            day.setText(name);
        }
        LinearLayout.LayoutParams weekParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        weekParams.topMargin = dp(4);
        view.addView(week, weekParams);
        for (int r = 0; r < rows.length; r++) {
            rows[r] = row(activity);
            for (int c = 0; c < 7; c++) {
                TextView day = label(activity, 14, PLAIN);
                day.setGravity(Gravity.CENTER);
                days[r * 7 + c] = day;
                rows[r].addView(day, cell());
            }
            view.addView(rows[r]);
        }
        for (int i = 0; i < NOTES; i++) {
            notes[i] = label(activity, 12, Color.WHITE);
            notes[i].setSingleLine(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(196), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(i == 0 ? 8 : 2);
            view.addView(notes[i], params);
        }
    }

    LinearLayout view() {
        return view;
    }

    /** {@code yyyy-MM} and then {@code day \t kind \t us title \t hijri title}. An empty payload still draws the month. */
    void bind(String payload) {
        LocalDate today = LocalDate.now();
        YearMonth shown = YearMonth.from(today);
        String[] markKind = new String[32];
        String[] markUs = new String[32];
        String[] markHijri = new String[32];
        if (payload != null) {
            String[] lines = payload.split("\n", -1);
            if (lines.length > 0 && shown.toString().equals(lines[0].trim())) {
                for (int i = 1; i < lines.length; i++) {
                    String[] parts = lines[i].split("\t", -1);
                    if (parts.length < 2) continue;
                    int day;
                    try {
                        day = Integer.parseInt(parts[0].trim());
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                    if (day < 1 || day > 31) continue;
                    markKind[day] = parts[1].trim();
                    markUs[day] = parts.length > 2 ? parts[2].trim() : "";
                    markHijri[day] = parts.length > 3 ? parts[3].trim() : "";
                }
            }
        }
        title.setText(shown.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()));
        int offset = shown.atDay(1).getDayOfWeek().getValue() % 7;
        int length = shown.lengthOfMonth();
        rows[5].setVisibility(offset + length > 35 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < days.length; i++) {
            int day = i - offset + 1;
            TextView cell = days[i];
            if (day < 1 || day > length) {
                cell.setText("");
                cell.setBackground(null);
                cell.setVisibility(View.INVISIBLE);
                continue;
            }
            cell.setVisibility(View.VISIBLE);
            cell.setText(Integer.toString(day));
            paint(cell, markKind[day], day == today.getDayOfMonth());
        }
        List<Note> captions = captions(today.getDayOfMonth(), markUs, markHijri);
        for (int i = 0; i < NOTES; i++) {
            if (i < captions.size()) {
                notes[i].setText(captions.get(i).text);
                notes[i].setTextColor(captions.get(i).color);
                notes[i].setVisibility(View.VISIBLE);
            } else {
                notes[i].setText("");
                notes[i].setVisibility(View.GONE);
            }
        }
        view.setVisibility(View.VISIBLE);
    }

    private void paint(TextView cell, String kind, boolean today) {
        int color = PLAIN;
        int fill = Color.TRANSPARENT;
        if ("us".equals(kind)) {
            color = US;
            fill = 0x33F5C16C;
        } else if ("hijri".equals(kind)) {
            color = HIJRI;
            fill = 0x332BB673;
        } else if ("both".equals(kind)) {
            color = Color.WHITE;
            fill = 0x33F5C16C;
        } else if (today) {
            color = Color.WHITE;
        }
        cell.setTextColor(color);
        if (today || fill != Color.TRANSPARENT) {
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(fill);
            if (today) disc.setStroke(stroke, Color.WHITE);
            cell.setBackground(disc);
        } else {
            cell.setBackground(null);
        }
    }

    /** Up to four names. Days still ahead come first when the month has more marks than that. */
    private static List<Note> captions(int today, String[] us, String[] hijri) {
        List<Note> past = new ArrayList<>();
        List<Note> ahead = new ArrayList<>();
        for (int day = 1; day <= 31; day++) {
            List<Note> bucket = day >= today ? ahead : past;
            if (us[day] != null && !us[day].isEmpty()) bucket.add(new Note(US, day + "  " + us[day]));
            if (hijri[day] != null && !hijri[day].isEmpty()) bucket.add(new Note(HIJRI, day + "  " + hijri[day]));
        }
        List<Note> shown = new ArrayList<>();
        if (ahead.size() >= NOTES) {
            shown.addAll(ahead.subList(0, NOTES));
        } else {
            int room = NOTES - ahead.size();
            int from = Math.max(0, past.size() - room);
            shown.addAll(past.subList(from, past.size()));
            shown.addAll(ahead);
        }
        return shown;
    }

    private LinearLayout.LayoutParams cell() {
        return new LinearLayout.LayoutParams(dp(28), dp(28));
    }

    private int dp(int value) {
        return Math.round(value * density);
    }

    private static LinearLayout column(android.app.Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout row(android.app.Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private static TextView label(android.app.Activity activity, float sizeSp, int color) {
        TextView view = new TextView(activity);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setIncludeFontPadding(false);
        view.setFocusable(false);
        return view;
    }

    private static final class Note {
        final int color;
        final String text;

        Note(int color, String text) {
            this.color = color;
            this.text = text;
        }
    }
}
