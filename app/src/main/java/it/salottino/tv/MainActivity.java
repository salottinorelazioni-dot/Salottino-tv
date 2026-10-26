package it.salottino.tv;

import android.app.*;
import android.os.*;
import android.provider.Settings;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout appsRow;
    private TextView clock;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        buildUi();
        updateClock();
    }

    private TextView text(String value, int sp) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextColor(Color.WHITE); v.setTextSize(sp);
        return v;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(64, 42, 64, 42);
        root.setBackgroundColor(Color.rgb(11,15,20));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("SALOTTINO TV", 30);
        title.setTypeface(null, 1);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        clock = text("", 26);
        header.addView(clock);
        root.addView(header);

        TextView subtitle = text("La tua TV. Semplice, pulita, senza pubblicità.", 17);
        subtitle.setTextColor(Color.LTGRAY);
        LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(-1,-2);
        sub.setMargins(0,8,0,34);
        root.addView(subtitle, sub);

        Button settings = new Button(this);
        settings.setText("⚙  Impostazioni TV");
        styleFocusable(settings);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));
        root.addView(settings, new LinearLayout.LayoutParams(300,80));

        TextView appsTitle = text("Le tue app", 22);
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-1,-2);
        at.setMargins(0,34,0,12);
        root.addView(appsTitle, at);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        appsRow = new LinearLayout(this);
        appsRow.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(appsRow);
        root.addView(hsv, new LinearLayout.LayoutParams(-1,220));
        setContentView(scroll);
        loadApps();
    }

    private void styleFocusable(View v) {
        v.setFocusable(true);
        v.setOnFocusChangeListener((view, focused) -> {
            view.animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f).setDuration(120).start();
            view.setAlpha(focused ? 1f : .82f);
        });
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
        Collections.sort(list, new ResolveInfo.DisplayNameComparator(pm));

        for (ResolveInfo r : list) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            Button b = new Button(this);
            b.setText(r.loadLabel(pm));
            b.setTextSize(15); b.setAllCaps(false); b.setGravity(Gravity.CENTER);
            Drawable icon = r.loadIcon(pm);
            icon.setBounds(0,0,88,88);
            b.setCompoundDrawables(null, icon, null, null);
            b.setCompoundDrawablePadding(10);
            styleFocusable(b);
            b.setOnClickListener(v -> {
                Intent launch = pm.getLaunchIntentForPackage(r.activityInfo.packageName);
                if (launch != null) startActivity(launch);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(190,190);
            lp.setMargins(0,8,18,8);
            appsRow.addView(b, lp);
        }
    }

    private void updateClock() {
        clock.setText(new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date()));
        handler.postDelayed(this::updateClock, 30000);
    }
}
