package it.salottino.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(4, 7, 8);
    private static final int BG_2 = Color.rgb(3, 16, 12);
    private static final int PANEL = Color.argb(234, 1, 28, 19);
    private static final int PANEL_2 = Color.argb(205, 8, 20, 19);
    private static final int CARD = Color.argb(212, 17, 20, 24);
    private static final int CARD_FOCUS = Color.argb(232, 24, 30, 36);
    private static final int STROKE = Color.argb(100, 58, 120, 92);
    private static final int GREEN = Color.rgb(105, 255, 156);
    private static final int GREEN_SOFT = Color.rgb(72, 192, 122);
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(180, 198, 188);

    private final Handler handler = new Handler();
    private TextView clockView;
    private TextView dateView;
    private TextView subtitleView;
    private LinearLayout continueRow;
    private LinearLayout mediaRow;
    private LinearLayout allRow;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 30000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUi();
        buildHome();
        handler.post(clockTick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        updateClock();
        populateRows();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clockTick);
        super.onDestroy();
    }

    private void buildHome() {
        ScrollView root = new ScrollView(this);
        root.setFillViewport(true);
        root.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.setBackground(makeBackground());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(36), dp(28), dp(36), dp(28));
        root.addView(content, new ViewGroup.LayoutParams(-1, -2));

        content.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout quickActions = new LinearLayout(this);
        quickActions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams qaLp = new LinearLayout.LayoutParams(-2, -2);
        qaLp.setMargins(0, dp(18), 0, dp(10));
        content.addView(quickActions, qaLp);
        quickActions.addView(actionChip("Personalizza", new View.OnClickListener() {
            @Override public void onClick(View v) { safeStart(Settings.ACTION_SETTINGS); }
        }));
        addSpaceHorizontal(quickActions, 10);
        quickActions.addView(actionChip("Ingressi", new View.OnClickListener() {
            @Override public void onClick(View v) { safeStart("android.settings.TV_INPUT_SETTINGS"); }
        }));
        addSpaceHorizontal(quickActions, 10);
        quickActions.addView(actionChip("App", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (continueRow != null && continueRow.getChildCount() > 0) continueRow.getChildAt(0).requestFocus();
            }
        }));

        content.addView(sectionTitle("Guarda subito"));
        continueRow = horizontalRow();
        content.addView(wrapRow(continueRow));

        content.addView(sectionTitle("Streaming & Media"));
        mediaRow = horizontalRow();
        content.addView(wrapRow(mediaRow));

        content.addView(sectionTitle("Tutte le app"));
        allRow = horizontalRow();
        content.addView(wrapRow(allRow));

        setContentView(root);
        updateClock();
        populateRows();
    }

    private View buildTopBar() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(22), dp(24), dp(22));
        panel.setBackground(makeRounded(PANEL, 34, Color.argb(110, 34, 124, 82), 1));
        if (Build.VERSION.SDK_INT >= 21) panel.setElevation(dp(3));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout brandWrap = new LinearLayout(this);
        brandWrap.setOrientation(LinearLayout.HORIZONTAL);
        brandWrap.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(brandWrap, new LinearLayout.LayoutParams(0, -2, 1.4f));

        TextView tvIcon = new TextView(this);
        tvIcon.setText("▣");
        tvIcon.setTextColor(GREEN);
        tvIcon.setTextSize(24);
        tvIcon.setTypeface(Typeface.DEFAULT_BOLD);
        brandWrap.addView(tvIcon);

        LinearLayout brandTextWrap = new LinearLayout(this);
        brandTextWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams btwLp = new LinearLayout.LayoutParams(-2, -2);
        btwLp.setMargins(dp(14), 0, 0, 0);
        brandWrap.addView(brandTextWrap, btwLp);

        TextView brand = new TextView(this);
        brand.setText("FOYHO\nTV");
        brand.setTextColor(GREEN);
        brand.setTextSize(28);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brandTextWrap.addView(brand);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        top.addView(right, new LinearLayout.LayoutParams(0, -2, 2.1f));

        right.addView(headerIconButton("①", "Profilo 1", new View.OnClickListener() {
            @Override public void onClick(View v) { toast("Profilo 1"); }
        }));
        addSpaceHorizontal(right, 12);
        right.addView(headerIconButton("⇥", "Ingressi", new View.OnClickListener() {
            @Override public void onClick(View v) { safeStart("android.settings.TV_INPUT_SETTINGS"); }
        }));
        addSpaceHorizontal(right, 12);
        right.addView(headerIconButton("⚙", "Impostazioni", new View.OnClickListener() {
            @Override public void onClick(View v) { safeStart(Settings.ACTION_SETTINGS); }
        }));
        addSpaceHorizontal(right, 12);
        right.addView(headerAccountButton("G", "Google", new View.OnClickListener() {
            @Override public void onClick(View v) {
                safeStart("android.settings.ACCOUNT_SYNC_SETTINGS");
            }
        }));
        addSpaceHorizontal(right, 22);

        LinearLayout clockWrap = new LinearLayout(this);
        clockWrap.setOrientation(LinearLayout.VERTICAL);
        clockWrap.setGravity(Gravity.END);
        right.addView(clockWrap, new LinearLayout.LayoutParams(-2, -2));

        clockView = new TextView(this);
        clockView.setTextColor(TEXT);
        clockView.setTextSize(34);
        clockView.setTypeface(Typeface.DEFAULT_BOLD);
        clockWrap.addView(clockView);

        dateView = new TextView(this);
        dateView.setTextColor(MUTED);
        dateView.setTextSize(18);
        dateView.setGravity(Gravity.END);
        clockWrap.addView(dateView);

        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(70, 130, 220, 170));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, dp(1));
        divLp.setMargins(0, dp(22), 0, dp(18));
        panel.addView(divider, divLp);

        subtitleView = new TextView(this);
        subtitleView.setText("Home Foyho TV • seleziona un'app per aprirla subito");
        subtitleView.setTextColor(Color.rgb(150, 173, 159));
        subtitleView.setTextSize(18);
        panel.addView(subtitleView);

        return panel;
    }

    private TextView headerIconButton(String symbol, final String hint, View.OnClickListener onClick) {
        final TextView t = new TextView(this);
        t.setText(symbol);
        t.setTextColor(TEXT);
        t.setTextSize(24);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setBackground(makeRounded(Color.argb(36, 105, 255, 156), 22, Color.argb(110, 81, 190, 128), 1));
        t.setOnClickListener(onClick);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                v.animate().scaleX(hasFocus ? 1.08f : 1f).scaleY(hasFocus ? 1.08f : 1f).setDuration(120).start();
                t.setBackground(makeRounded(hasFocus ? Color.argb(80, 105, 255, 156) : Color.argb(36, 105, 255, 156),
                        22, hasFocus ? GREEN : Color.argb(110, 81, 190, 128), hasFocus ? 2 : 1));
                if (hasFocus && subtitleView != null) subtitleView.setText(hint + " • premi OK per aprire");
            }
        });
        return t;
    }

    private View headerAccountButton(String letter, final String hint, View.OnClickListener onClick) {
        final LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER);
        wrap.setFocusable(true);
        wrap.setClickable(true);
        wrap.setPadding(dp(6), dp(6), dp(6), dp(6));
        wrap.setBackground(makeRounded(Color.argb(24, 255, 255, 255), 26, Color.argb(90, 81, 190, 128), 1));

        final TextView inner = new TextView(this);
        inner.setText(letter);
        inner.setTextColor(TEXT);
        inner.setTextSize(20);
        inner.setTypeface(Typeface.DEFAULT_BOLD);
        inner.setGravity(Gravity.CENTER);
        inner.setBackground(makeRounded(Color.rgb(45, 125, 255), 20, Color.TRANSPARENT, 0));
        inner.setPadding(dp(15), dp(10), dp(15), dp(10));
        wrap.addView(inner, new LinearLayout.LayoutParams(-2, -2));

        wrap.setOnClickListener(onClick);
        wrap.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                v.animate().scaleX(hasFocus ? 1.08f : 1f).scaleY(hasFocus ? 1.08f : 1f).setDuration(120).start();
                wrap.setBackground(makeRounded(hasFocus ? Color.argb(60, 105, 255, 156) : Color.argb(24, 255, 255, 255),
                        26, hasFocus ? GREEN : Color.argb(90, 81, 190, 128), hasFocus ? 2 : 1));
                if (hasFocus && subtitleView != null) subtitleView.setText(hint + " • account Google collegato");
            }
        });
        return wrap;
    }

    private TextView actionChip(String label, View.OnClickListener onClick) {
        final TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(TEXT);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(dp(18), dp(12), dp(18), dp(12));
        t.setBackground(makeRounded(PANEL_2, 20, Color.argb(90, 58, 120, 92), 1));
        t.setOnClickListener(onClick);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.animate().scaleX(hasFocus ? 1.04f : 1f).scaleY(hasFocus ? 1.04f : 1f).setDuration(120).start();
                t.setBackground(makeRounded(hasFocus ? Color.argb(220, 18, 58, 38) : PANEL_2, 20,
                        hasFocus ? GREEN : Color.argb(90, 58, 120, 92), hasFocus ? 2 : 1));
                if (hasFocus && subtitleView != null) subtitleView.setText(label + " • scorciatoia rapida");
            }
        });
        return t;
    }

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(TEXT);
        tv.setTextSize(28);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(18), 0, dp(12));
        tv.setLayoutParams(lp);
        return tv;
    }

    private HorizontalScrollView wrapRow(LinearLayout row) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.addView(row, new ViewGroup.LayoutParams(-2, -2));
        return hsv;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private void populateRows() {
        List<ResolveInfo> allApps = loadLaunchableApps();
        List<ResolveInfo> mediaApps = filterMediaApps(allApps);
        List<ResolveInfo> continueApps = new ArrayList<ResolveInfo>();
        for (int i = 0; i < allApps.size() && i < 7; i++) continueApps.add(allApps.get(i));
        if (mediaApps.isEmpty()) {
            for (int i = 0; i < allApps.size() && i < 8; i++) mediaApps.add(allApps.get(i));
        }

        fillRow(continueRow, continueApps, 7);
        fillRow(mediaRow, mediaApps, 10);
        fillRow(allRow, allApps, 18);

        if (continueRow != null && continueRow.getChildCount() > 0) continueRow.getChildAt(0).requestFocus();
    }

    private void fillRow(LinearLayout row, List<ResolveInfo> apps, int limit) {
        if (row == null) return;
        row.removeAllViews();
        int count = Math.min(limit, apps.size());
        for (int i = 0; i < count; i++) row.addView(buildAppTile(apps.get(i)));
    }

    private View buildAppTile(final ResolveInfo ri) {
        final PackageManager pm = getPackageManager();
        final CharSequence labelCs = ri.loadLabel(pm);
        final Drawable iconDrawable = ri.loadIcon(pm);
        final String appLabel = String.valueOf(labelCs);

        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setPadding(dp(18), dp(16), dp(18), dp(14));
        tile.setBackground(makeRounded(CARD, 24, STROKE, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(220), dp(132));
        lp.setMargins(0, 0, dp(14), dp(14));
        tile.setLayoutParams(lp);

        LinearLayout iconWrap = new LinearLayout(this);
        iconWrap.setGravity(Gravity.CENTER);
        iconWrap.setBackground(makeRounded(Color.argb(40, 255, 255, 255), 18, Color.TRANSPARENT, 0));
        iconWrap.setPadding(dp(18), dp(12), dp(18), dp(12));
        tile.addView(iconWrap, new LinearLayout.LayoutParams(-2, -2));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(iconDrawable);
        icon.setAdjustViewBounds(true);
        iconWrap.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView name = new TextView(this);
        name.setText(appLabel);
        name.setTextColor(TEXT);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setMaxLines(2);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.setMargins(0, dp(12), 0, 0);
        tile.addView(name, nameLp);

        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launchApp(ri); }
        });
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                v.animate().scaleX(hasFocus ? 1.06f : 1f).scaleY(hasFocus ? 1.06f : 1f).setDuration(120).start();
                tile.setBackground(makeRounded(hasFocus ? CARD_FOCUS : CARD, 24,
                        hasFocus ? GREEN : STROKE, hasFocus ? 2 : 1));
                if (Build.VERSION.SDK_INT >= 21) v.setElevation(hasFocus ? dp(6) : 0f);
                if (hasFocus && subtitleView != null) subtitleView.setText(appLabel + " • premi OK per aprire l'applicazione");
            }
        });
        return tile;
    }

    private List<ResolveInfo> loadLaunchableApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
        if (apps == null || apps.isEmpty()) {
            Intent fallback = new Intent(Intent.ACTION_MAIN);
            fallback.addCategory(Intent.CATEGORY_LAUNCHER);
            apps = pm.queryIntentActivities(fallback, 0);
        }
        if (apps == null) apps = new ArrayList<ResolveInfo>();
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                String la = String.valueOf(a.loadLabel(pm));
                String lb = String.valueOf(b.loadLabel(pm));
                return la.compareToIgnoreCase(lb);
            }
        });
        return apps;
    }

    private List<ResolveInfo> filterMediaApps(List<ResolveInfo> all) {
        List<ResolveInfo> out = new ArrayList<ResolveInfo>();
        for (ResolveInfo ri : all) {
            String pkg = ri.activityInfo != null ? ri.activityInfo.packageName.toLowerCase(Locale.ROOT) : "";
            if (pkg.contains("kodi") || pkg.contains("netflix") || pkg.contains("youtube") ||
                    pkg.contains("prime") || pkg.contains("disney") || pkg.contains("plex") ||
                    pkg.contains("spotify") || pkg.contains("crunchyroll") || pkg.contains("vlc") ||
                    pkg.contains("rai") || pkg.contains("mediaset") || pkg.contains("mx")) {
                out.add(ri);
            }
        }
        return out;
    }

    private void launchApp(ResolveInfo ri) {
        try {
            Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(ri.activityInfo.packageName);
            if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(ri.activityInfo.packageName);
            if (launch == null) {
                launch = new Intent(Intent.ACTION_MAIN);
                launch.addCategory(Intent.CATEGORY_LAUNCHER);
                launch.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } catch (Exception e) {
            Toast.makeText(this, "Impossibile aprire l'app", Toast.LENGTH_SHORT).show();
        }
    }

    private void safeStart(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            toast("Azione non disponibile");
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void updateClock() {
        Date now = new Date();
        if (clockView != null) clockView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        if (dateView != null) dateView.setText(new SimpleDateFormat("EEE d MMM", Locale.ITALIAN).format(now));
    }

    private GradientDrawable makeBackground() {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG, BG_2, Color.rgb(2, 10, 10)});
        gd.setCornerRadius(0f);
        return gd;
    }

    private GradientDrawable makeRounded(int fill, int radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) gd.setStroke(dp(strokeWidthDp), strokeColor);
        return gd;
    }

    private void addSpaceHorizontal(LinearLayout parent, int spaceDp) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(dp(spaceDp), 1));
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }
}
