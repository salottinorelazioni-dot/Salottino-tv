package it.salottino.tv;

import android.app.*;
import android.os.*;
import android.provider.Settings;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.tv.TvContract;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_WALLPAPER = 101;
    private static final int REQ_LOCAL_VIDEO = 102;

    private SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout content;
    private TextView clock, date, focusTitle, focusSubtitle;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppItem> apps = new ArrayList<>();

    static class AppItem {
        String pkg, activity, label, category;
        Drawable icon, banner;
        boolean leanback;
        AppItem(String p, String a, String l, String c, Drawable i, Drawable b, boolean lb) {
            pkg=p; activity=a; label=l; category=c; icon=i; banner=b; leanback=lb;
        }
    }

    static class WatchItem {
        String title, subtitle, intentUri, posterUri;
        WatchItem(String t, String s, String i, String p) {
            title=t; subtitle=s; intentUri=i; posterUri=p;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("salottino_tv", MODE_PRIVATE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        loadApps();
        buildHome();
        updateClock();
    }

    @Override protected void onResume() {
        super.onResume();
        if (root != null) {
            loadApps();
            buildHome();
            updateClock();
        }
    }

    private void buildHome() {
        root = new FrameLayout(this);
        applyBackground();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(48), dp(28), dp(48), dp(42));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        buildHeader();
        buildHero();

        List<WatchItem> watch = loadWatchNext();
        if (!watch.isEmpty()) {
            addWatchNextRow("Guarda successivo", watch);
        } else {
            List<AppItem> recent = recentApps();
            if (!recent.isEmpty()) addAppRow("Aperti di recente", recent);
        }

        List<AppItem> fav = favoriteApps();
        if (!fav.isEmpty()) addAppRow("Preferiti", fav);

        addAppRowIfAny("App TV", filterByCategory("tv"));
        addAppRowIfAny("Streaming & Film", filterByCategory("media"));
        addAppRowIfAny("Musica", filterByCategory("music"));
        addAppRowIfAny("Giochi", filterByCategory("games"));
        addAppRowIfAny("Utility", filterByCategory("tools"));
        addAppRowIfAny("Altre app", filterByCategory("other"));

        addSystemRow();

        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private void buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("FOYHO TV", 27, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setLetterSpacing(.04f);
        TextView sub = label("Home", 13, 0xFF8EFFB0);
        brand.addView(title);
        brand.addView(sub);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout timeBox = new LinearLayout(this);
        timeBox.setOrientation(LinearLayout.VERTICAL);
        timeBox.setGravity(Gravity.RIGHT);
        clock = label("", 28, Color.WHITE);
        clock.setTypeface(null, Typeface.BOLD);
        date = label("", 13, 0xFFB7C0CB);
        timeBox.addView(clock);
        timeBox.addView(date);
        header.addView(timeBox);

        Button customize = smallButton("Personalizza");
        LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(dp(156), dp(52));
        cbp.setMargins(dp(24),0,0,0);
        customize.setOnClickListener(v -> showSettings());
        header.addView(customize, cbp);

        content.addView(header);
    }

    private void buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(0, dp(34), 0, dp(20));

        focusTitle = label("Scegli cosa guardare.", 30, Color.WHITE);
        focusTitle.setTypeface(null, Typeface.BOLD);
        focusSubtitle = label("FOYHO TV • tieni premuto su un'app per personalizzarla", 15, 0xFFD1D6DD);

        hero.addView(focusTitle);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2);
        sp.setMargins(0, dp(8), 0, 0);
        hero.addView(focusSubtitle, sp);
        content.addView(hero);
    }

    private void addWatchNextRow(String title, List<WatchItem> list) {
        addRowTitle(title);
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipChildren(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(dp(3), dp(8), dp(3), dp(18));
        hsv.addView(row, new HorizontalScrollView.LayoutParams(-2,-2));

        int count = Math.min(12, list.size());
        for (int x=0; x<count; x++) {
            WatchItem item = list.get(x);
            row.addView(createWatchCard(item), wideCardParams());
        }
        content.addView(hsv, new LinearLayout.LayoutParams(-1, dp(188)));
    }

    private View createWatchCard(WatchItem item) {
        FrameLayout card = baseFrameCard();
        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (item.posterUri != null) {
            try { art.setImageURI(Uri.parse(item.posterUri)); } catch(Exception ignored) {}
        }
        card.addView(art, new FrameLayout.LayoutParams(-1,-1));

        View shade = new View(this);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xCC000000});
        shade.setBackground(gd);
        card.addView(shade, new FrameLayout.LayoutParams(-1,-1));

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setGravity(Gravity.BOTTOM);
        textBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView t = label(item.title == null ? "Continua a guardare" : item.title, 16, Color.WHITE);
        t.setTypeface(null, Typeface.BOLD);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView s = label(item.subtitle == null ? "Guarda successivo" : item.subtitle, 12, 0xFFD2D6DB);
        s.setSingleLine(true);
        textBox.addView(t);
        textBox.addView(s);
        card.addView(textBox, new FrameLayout.LayoutParams(-1,-1));

        card.setOnClickListener(v -> {
            if (item.intentUri != null) {
                try { startActivity(Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME)); }
                catch(Exception ignored) {}
            }
        });
        focusBehavior(card, t.getText().toString(), "Guarda successivo");
        return card;
    }

    private void addAppRowIfAny(String title, List<AppItem> list) {
        if (!list.isEmpty()) addAppRow(title, list);
    }

    private void addAppRow(String title, List<AppItem> list) {
        addRowTitle(title);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipChildren(false);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(dp(3), dp(8), dp(3), dp(18));
        hsv.addView(row, new HorizontalScrollView.LayoutParams(-2,-2));

        for (AppItem item : list) row.addView(createAppCard(item), appCardParams());
        content.addView(hsv, new LinearLayout.LayoutParams(-1, cardHeight()+dp(30)));
    }

    private void addRowTitle(String title) {
        TextView rowTitle = label(title, 19, Color.WHITE);
        rowTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams rtp = new LinearLayout.LayoutParams(-1,-2);
        rtp.setMargins(0, dp(10), 0, dp(7));
        content.addView(rowTitle, rtp);
    }

    private View createAppCard(AppItem item) {
        FrameLayout card = baseFrameCard();

        ImageView art = new ImageView(this);
        if (item.banner != null) {
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageDrawable(item.banner);
        } else {
            art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            art.setPadding(dp(18), dp(14), dp(18), dp(26));
            art.setImageDrawable(item.icon);
            art.setBackground(appFallbackGradient(item.pkg));
        }
        card.addView(art, new FrameLayout.LayoutParams(-1,-1));

        View shade = new View(this);
        GradientDrawable shadeBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x22000000, 0xD5000000});
        shade.setBackground(shadeBg);
        card.addView(shade, new FrameLayout.LayoutParams(-1,-1));

        TextView name = label(customLabel(item), 14, Color.WHITE);
        name.setGravity(Gravity.LEFT | Gravity.BOTTOM);
        name.setTypeface(null, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(dp(13), 0, dp(13), dp(11));
        card.addView(name, new FrameLayout.LayoutParams(-1,-1));

        card.setOnClickListener(v -> launchApp(item));
        card.setOnLongClickListener(v -> {
            showAppMenu(item);
            return true;
        });

        focusBehavior(card, customLabel(item), categoryDisplay(item.category) + " • tieni premuto per le opzioni");
        return card;
    }

    private FrameLayout baseFrameCard() {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(cardBorder(false));
        if (Build.VERSION.SDK_INT >= 21) {
            card.setClipToOutline(true);
            card.setElevation(dp(5));
        }
        return card;
    }

    private GradientDrawable cardBorder(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x33101714);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(focused ? 3 : 1), focused ? 0xFF58FF89 : 0x554C6657);
        return g;
    }

    private LinearLayout.LayoutParams appCardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardWidth(), cardHeight());
        lp.setMargins(0,0,dp(16),0);
        return lp;
    }

    private LinearLayout.LayoutParams wideCardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(290), dp(160));
        lp.setMargins(0,0,dp(16),0);
        return lp;
    }

    private void focusBehavior(View view, String title, String subtitle) {
        view.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.07f : 1f).scaleY(focused ? 1.07f : 1f).setDuration(120).start();
            v.setBackground(cardBorder(focused));
            if (focused && focusTitle != null) {
                focusTitle.setText(title);
                focusSubtitle.setText(subtitle);
            }
        });
    }

    private Drawable appFallbackGradient(String seed) {
        int h = Math.abs(seed.hashCode());
        int g = 60 + (h % 60);
        int b = 40 + ((h / 7) % 70);
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(8, 32 + g/3, 18), Color.rgb(10, 16, b), Color.rgb(3, 14, 8)});
        d.setCornerRadius(dp(18));
        return d;
    }

    private void applyBackground() {
        String custom = prefs.getString("wallpaper_uri", null);
        if (custom == null) {
            root.addView(new CyberBackgroundView(this), new FrameLayout.LayoutParams(-1,-1));
        } else {
            ImageView bg = new ImageView(this);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try { bg.setImageURI(Uri.parse(custom)); } catch(Exception ignored) {}
            root.addView(bg, new FrameLayout.LayoutParams(-1,-1));
        }

        View dim = new View(this);
        int level = prefs.getInt("dim", 28);
        dim.setBackgroundColor(Color.argb((int)(255*(level/100f)), 0,0,0));
        root.addView(dim, new FrameLayout.LayoutParams(-1,-1));
    }

    class CyberBackgroundView extends View {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random random = new Random(2202);

        CyberBackgroundView(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();

            Paint bg = new Paint();
            bg.setShader(new LinearGradient(0,0,w,h,
                    new int[]{0xFF020806,0xFF071A11,0xFF050714,0xFF020705},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,bg);

            Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            glow.setColor(0x1438FF7A);
            c.drawCircle(w*.18f, h*.32f, h*.34f, glow);
            glow.setColor(0x1223C96A);
            c.drawCircle(w*.78f, h*.72f, h*.42f, glow);
            glow.setColor(0x101A4DFF);
            c.drawCircle(w*.58f, h*.28f, h*.28f, glow);

            paint.setTypeface(Typeface.MONOSPACE);
            paint.setTextSize(Math.max(15, w/105f));
            float stepX = Math.max(27, w/50f);
            float stepY = paint.getTextSize()*1.35f;
            String chars = "01アイウエオカキクケコサシスセソABCDEFGHIJKLMNOPQRSTUVWXYZ";

            random.setSeed(2202);
            for (float x=0; x<w; x+=stepX) {
                float start = -random.nextInt(Math.max(1,h/2));
                int len = 14 + random.nextInt(24);
                for (int i=0;i<len;i++) {
                    float y = start + i*stepY;
                    if (y < -stepY || y > h+stepY) continue;
                    char ch = chars.charAt(random.nextInt(chars.length()));
                    int alpha = Math.max(14, 115 - i*4);
                    paint.setColor(Color.argb(alpha, 70, 255, 120));
                    c.drawText(String.valueOf(ch), x, y, paint);
                }
            }

            Paint brand = new Paint(Paint.ANTI_ALIAS_FLAG);
            brand.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            brand.setTextAlign(Paint.Align.RIGHT);
            brand.setTextSize(Math.min(w*.145f, h*.24f));
            brand.setColor(0x1658FF89);
            c.drawText("FOYHO", w*.94f, h*.63f, brand);
            brand.setTextSize(Math.min(w*.055f, h*.09f));
            brand.setColor(0x2458FF89);
            c.drawText("TV", w*.94f, h*.72f, brand);
        }
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        LinkedHashMap<String, ResolveInfo> found = new LinkedHashMap<>();
        HashSet<String> leanbackPkgs = new HashSet<>();

        Intent lean = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(lean, 0)) {
            found.put(r.activityInfo.packageName, r);
            leanbackPkgs.add(r.activityInfo.packageName);
        }

        Intent normal = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(normal, 0)) {
            if (!found.containsKey(r.activityInfo.packageName)) found.put(r.activityInfo.packageName, r);
        }

        Set<String> hidden = getSet("hidden");
        for (ResolveInfo r : found.values()) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg)) continue;

            Drawable icon = r.loadIcon(pm);
            Drawable banner = null;
            try { banner = r.activityInfo.loadBanner(pm); } catch(Exception ignored) {}
            if (banner == null) {
                try { banner = r.activityInfo.applicationInfo.loadBanner(pm); } catch(Exception ignored) {}
            }

            String label = String.valueOf(r.loadLabel(pm));
            boolean lb = leanbackPkgs.contains(pkg);
            apps.add(new AppItem(pkg, r.activityInfo.name, label, categoryFor(r, lb), icon, banner, lb));
        }

        List<String> order = getOrder();
        Collections.sort(apps, (a,b) -> {
            int ia = order.indexOf(a.pkg), ib = order.indexOf(b.pkg);
            if (ia >= 0 && ib >= 0) return Integer.compare(ia,ib);
            if (ia >= 0) return -1;
            if (ib >= 0) return 1;
            return a.label.compareToIgnoreCase(b.label);
        });

        boolean changed = false;
        for (AppItem a : apps) if (!order.contains(a.pkg)) { order.add(a.pkg); changed = true; }
        if (changed) saveOrder(order);
    }

    private String categoryFor(ResolveInfo r, boolean leanback) {
        String pkg = r.activityInfo.packageName;
        String custom = prefs.getString("cat_"+pkg, null);
        if (custom != null) return custom;

        String all = (pkg + " " + r.loadLabel(getPackageManager())).toLowerCase(Locale.ROOT);

        if (containsAny(all, "spotify","music","musica","radio","tunein","deezer","tidal","soundcloud")) return "music";
        if (containsAny(all, "netflix","prime video","disney","raiplay","rai play","mediaset","infinity","dazn",
                "youtube","plex","paramount","apple tv","now tv","stream","film","movie","video")) return "media";
        if (containsAny(all, "live tv","livetv","tv app","tvplayer","kodi","iptv","tivimate","smarttube","rb live","live77","nerozone")) return "tv";
        if (containsAny(all, "game","giochi","steam","retroarch","moonlight","geforce","xbox")) return "games";
        if (containsAny(all, "settings","impostazioni","file","manager","browser","utility","tools","store","installer")) return "tools";

        if (Build.VERSION.SDK_INT >= 26) {
            int c = r.activityInfo.applicationInfo.category;
            if (c == ApplicationInfo.CATEGORY_GAME) return "games";
            if (c == ApplicationInfo.CATEGORY_AUDIO) return "music";
            if (c == ApplicationInfo.CATEGORY_VIDEO) return "media";
            if (c == ApplicationInfo.CATEGORY_PRODUCTIVITY) return "tools";
        }
        if (leanback) return "tv";
        return "other";
    }

    private List<AppItem> filterByCategory(String category) {
        List<AppItem> out = new ArrayList<>();
        for (AppItem a : apps) if (category.equals(a.category)) out.add(a);
        return out;
    }

    private List<AppItem> favoriteApps() {
        Set<String> fav = getSet("favorites");
        List<AppItem> out = new ArrayList<>();
        for (AppItem a : apps) if (fav.contains(a.pkg)) out.add(a);
        return out;
    }

    private List<AppItem> recentApps() {
        List<AppItem> out = new ArrayList<>(apps);
        Collections.sort(out, (a,b) -> Long.compare(
                prefs.getLong("last_"+b.pkg, 0),
                prefs.getLong("last_"+a.pkg, 0)));
        List<AppItem> result = new ArrayList<>();
        for (AppItem a : out) {
            if (prefs.getLong("last_"+a.pkg, 0) > 0 && result.size() < 8) result.add(a);
        }
        return result;
    }

    private List<WatchItem> loadWatchNext() {
        List<WatchItem> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT < 26) return out;
        Cursor c = null;
        try {
            String[] projection = {
                    TvContract.WatchNextPrograms.COLUMN_TITLE,
                    TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
                    TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION,
                    TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                    TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                    TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS
            };
            c = getContentResolver().query(
                    TvContract.WatchNextPrograms.CONTENT_URI,
                    projection, null, null,
                    TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC");
            if (c != null) {
                int t = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_TITLE);
                int e = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE);
                int s = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION);
                int i = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_INTENT_URI);
                int p = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI);
                while (c.moveToNext() && out.size() < 12) {
                    String title = t >= 0 ? c.getString(t) : null;
                    String ep = e >= 0 ? c.getString(e) : null;
                    String sub = ep != null && !ep.isEmpty() ? ep : (s >= 0 ? c.getString(s) : null);
                    out.add(new WatchItem(title, sub, i >= 0 ? c.getString(i) : null, p >= 0 ? c.getString(p) : null));
                }
            }
        } catch(Exception ignored) {
            out.clear();
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    private void launchApp(AppItem item) {
        prefs.edit().putLong("last_"+item.pkg, System.currentTimeMillis()).apply();
        try {
            Intent i = new Intent();
            i.setClassName(item.pkg, item.activity);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch(Exception e) {
            Intent i = getPackageManager().getLaunchIntentForPackage(item.pkg);
            if (i != null) startActivity(i);
        }
    }

    private void showAppMenu(AppItem item) {
        Set<String> fav = getSet("favorites");
        boolean isFav = fav.contains(item.pkg);
        String[] options = {
                isFav ? "Rimuovi dai preferiti" : "Aggiungi ai preferiti",
                "Cambia categoria",
                "Sposta a sinistra",
                "Sposta a destra",
                "Nascondi app"
        };

        new AlertDialog.Builder(this)
                .setTitle(customLabel(item))
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        Set<String> set = getSet("favorites");
                        if (set.contains(item.pkg)) set.remove(item.pkg); else set.add(item.pkg);
                        saveSet("favorites", set);
                        buildHome();
                    } else if (which == 1) {
                        showCategoryPicker(item);
                    } else if (which == 2) {
                        moveApp(item.pkg, -1);
                    } else if (which == 3) {
                        moveApp(item.pkg, 1);
                    } else if (which == 4) {
                        Set<String> hidden = getSet("hidden");
                        hidden.add(item.pkg);
                        saveSet("hidden", hidden);
                        loadApps(); buildHome();
                    }
                }).show();
    }

    private void showCategoryPicker(AppItem item) {
        String[] names = {"App TV","Streaming & Film","Musica","Giochi","Utility","Altre app"};
        String[] ids = {"tv","media","music","games","tools","other"};
        new AlertDialog.Builder(this)
                .setTitle("Categoria")
                .setItems(names, (d, which) -> {
                    prefs.edit().putString("cat_"+item.pkg, ids[which]).apply();
                    loadApps(); buildHome();
                }).show();
    }

    private void moveApp(String pkg, int delta) {
        List<String> order = getOrder();
        int i = order.indexOf(pkg);
        if (i < 0) return;
        int j = Math.max(0, Math.min(order.size()-1, i+delta));
        Collections.swap(order, i, j);
        saveOrder(order);
        loadApps(); buildHome();
    }

    private void showSettings() {
        String[] options = {
                "Scegli sfondo personale",
                "Ripristina sfondo Foyho",
                "Dimensione tile",
                "Oscuramento sfondo",
                "Mostra app nascoste"
        };
        new AlertDialog.Builder(this)
                .setTitle("Personalizza Foyho TV")
                .setItems(options, (d, which) -> {
                    if (which == 0) chooseWallpaper();
                    else if (which == 1) {
                        prefs.edit().remove("wallpaper_uri").apply();
                        buildHome();
                    } else if (which == 2) chooseCardSize();
                    else if (which == 3) chooseDim();
                    else if (which == 4) showHiddenApps();
                }).show();
    }

    private void chooseWallpaper() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_WALLPAPER);
    }

    private void chooseCardSize() {
        String[] items = {"Compatta","Normale","Grande"};
        new AlertDialog.Builder(this).setTitle("Dimensione tile")
                .setSingleChoiceItems(items, prefs.getInt("card_size",1), (d, which) -> {
                    prefs.edit().putInt("card_size", which).apply();
                    d.dismiss(); buildHome();
                }).show();
    }

    private void chooseDim() {
        String[] items = {"Leggero","Medio","Scuro"};
        int current = prefs.getInt("dim",28) < 20 ? 0 : prefs.getInt("dim",28) < 42 ? 1 : 2;
        new AlertDialog.Builder(this).setTitle("Oscuramento sfondo")
                .setSingleChoiceItems(items, current, (d, which) -> {
                    int value = which == 0 ? 12 : which == 1 ? 28 : 50;
                    prefs.edit().putInt("dim", value).apply();
                    d.dismiss(); buildHome();
                }).show();
    }

    private void showHiddenApps() {
        Set<String> hidden = getSet("hidden");
        if (hidden.isEmpty()) {
            Toast.makeText(this, "Nessuna app nascosta", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> pkgs = new ArrayList<>(hidden);
        String[] labels = new String[pkgs.size()];
        PackageManager pm = getPackageManager();
        for (int i=0;i<pkgs.size();i++) {
            try { labels[i] = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkgs.get(i),0))); }
            catch(Exception e) { labels[i] = pkgs.get(i); }
        }
        new AlertDialog.Builder(this).setTitle("Ripristina app")
                .setItems(labels, (d, which) -> {
                    Set<String> set = getSet("hidden");
                    set.remove(pkgs.get(which));
                    saveSet("hidden", set);
                    loadApps(); buildHome();
                }).show();
    }

    private void addSystemRow() {
        addRowTitle("TV & Sistema");
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(3),dp(8),dp(3),dp(18));
        hsv.addView(row);

        row.addView(systemCard("Video locali","▶", v -> openLocalVideo()), appCardParams());
        row.addView(systemCard("Ingressi","⇄", v -> safeStart("android.settings.TV_INPUT_SETTINGS")), appCardParams());
        row.addView(systemCard("Wi‑Fi","◉", v -> safeStart(Settings.ACTION_WIFI_SETTINGS)), appCardParams());
        row.addView(systemCard("Impostazioni","⚙", v -> safeStart(Settings.ACTION_SETTINGS)), appCardParams());
        content.addView(hsv, new LinearLayout.LayoutParams(-1, cardHeight()+dp(30)));
    }

    private View systemCard(String name, String symbol, View.OnClickListener click) {
        FrameLayout card = baseFrameCard();
        TextView bg = label(symbol, 58, 0xFF66FF8C);
        bg.setGravity(Gravity.CENTER);
        bg.setBackground(appFallbackGradient(name));
        card.addView(bg, new FrameLayout.LayoutParams(-1,-1));

        View shade = new View(this);
        GradientDrawable sh = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000,0xB0000000});
        shade.setBackground(sh);
        card.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        TextView tx = label(name,14,Color.WHITE);
        tx.setGravity(Gravity.LEFT|Gravity.BOTTOM);
        tx.setTypeface(null,Typeface.BOLD);
        tx.setPadding(dp(13),0,dp(13),dp(11));
        card.addView(tx,new FrameLayout.LayoutParams(-1,-1));

        card.setOnClickListener(click);
        focusBehavior(card,name,"TV & Sistema");
        return card;
    }

    private void openLocalVideo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("video/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_LOCAL_VIDEO);
    }

    private void safeStart(String action) {
        try { startActivity(new Intent(action)); }
        catch(Exception e) { try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch(Exception ignored) {} }
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (result != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        if (req == REQ_WALLPAPER) {
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch(Exception ignored) {}
            prefs.edit().putString("wallpaper_uri", uri.toString()).apply();
            buildHome();
        } else if (req == REQ_LOCAL_VIDEO) {
            Intent play = new Intent(Intent.ACTION_VIEW);
            play.setDataAndType(uri, "video/*");
            play.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivity(play); } catch(Exception ignored) {}
        }
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x5534D96B);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1),0xAA58FF89);
        b.setBackground(g);
        b.setFocusable(true);
        return b;
    }

    private TextView label(String text, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    private String customLabel(AppItem item) {
        return prefs.getString("label_"+item.pkg, item.label);
    }

    private String categoryDisplay(String id) {
        if ("tv".equals(id)) return "App TV";
        if ("media".equals(id)) return "Streaming & Film";
        if ("music".equals(id)) return "Musica";
        if ("games".equals(id)) return "Giochi";
        if ("tools".equals(id)) return "Utility";
        return "Altre app";
    }

    private boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private int cardWidth() {
        int s = prefs.getInt("card_size",1);
        return s == 0 ? dp(170) : s == 2 ? dp(250) : dp(210);
    }

    private int cardHeight() {
        int s = prefs.getInt("card_size",1);
        return s == 0 ? dp(116) : s == 2 ? dp(168) : dp(140);
    }

    private Set<String> getSet(String key) {
        return new LinkedHashSet<>(prefs.getStringSet(key, new LinkedHashSet<>()));
    }

    private void saveSet(String key, Set<String> set) {
        prefs.edit().putStringSet(key, new LinkedHashSet<>(set)).apply();
    }

    private List<String> getOrder() {
        String raw = prefs.getString("order","");
        List<String> out = new ArrayList<>();
        if (!raw.isEmpty()) out.addAll(Arrays.asList(raw.split("\\|")));
        return out;
    }

    private void saveOrder(List<String> order) {
        prefs.edit().putString("order", android.text.TextUtils.join("|",order)).apply();
    }

    private void updateClock() {
        renderClock();
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                renderClock();
                handler.postDelayed(this, 30000);
            }
        }, 30000);
    }

    private void renderClock() {
        if (clock != null) clock.setText(new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date()));
        if (date != null) date.setText(new SimpleDateFormat("EEE d MMM", Locale.ITALY).format(new Date()));
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + .5f);
    }
}
