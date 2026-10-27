package it.salottino.tv;

import android.app.*;
import android.app.role.RoleManager;
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
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    private static final int REQ_WALLPAPER = 101;
    private static final int REQ_LOCAL_VIDEO = 102;
    private static final int REQ_HOME_ROLE = 103;
    private static final int REQ_BACKUP_EXPORT = 104;
    private static final int REQ_BACKUP_IMPORT = 105;

    private SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout content;
    private TextView clock, date, weather, focusTitle, focusSubtitle;
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
        int progress;
        WatchItem(String t, String s, String i, String p, int pr) {
            title=t; subtitle=s; intentUri=i; posterUri=p; progress=pr;
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
        updateWeather();
    }

    @Override protected void onResume() {
        super.onResume();
        if (root != null) {
            loadApps();
            buildHome();
            updateClock();
            updateWeather();
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
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(dp(24), dp(20), dp(14), dp(34));
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        buildHeader();
        buildHero();

        List<WatchItem> watch = loadWatchNext();
        if (!watch.isEmpty()) {
            addWatchNextRow("Guarda successivo", watch);
        }

        for (String categoryId : getCategoryOrder()) {
            addAppRowIfAny(categoryDisplay(categoryId), filterByCategory(categoryId));
        }

        addSystemRow();

        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private void buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        header.setClipToPadding(false);

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

        LinearLayout topInfo = new LinearLayout(this);
        topInfo.setOrientation(LinearLayout.HORIZONTAL);
        topInfo.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);

        weather = label("☁ --°C", 15, 0xFF8EFFB0);
        weather.setSingleLine(true);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
        wp.setMargins(0, 0, dp(18), 0);
        topInfo.addView(weather, wp);

        clock = label("", 28, Color.WHITE);
        clock.setTypeface(null, Typeface.BOLD);
        topInfo.addView(clock);

        date = label("", 13, 0xFFB7C0CB);
        date.setGravity(Gravity.RIGHT);

        timeBox.addView(topInfo);
        timeBox.addView(date);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
        ap.setMargins(dp(16), dp(3), dp(8), dp(3));

        TextView settings = headerAction("⚙", "Impostazioni Foyho", "Personalizzazione, backup e ripristino");
        settings.setOnClickListener(v -> showSettings());
        actions.addView(settings);

        header.addView(actions, ap);
        header.addView(timeBox);

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
        content.addView(hsv, new LinearLayout.LayoutParams(-1, dp(214)));
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
                new int[]{0x00000000, 0x22000000, 0xDD000000});
        shade.setBackground(gd);
        card.addView(shade, new FrameLayout.LayoutParams(-1,-1));

        TextView chip = label("CONTINUA", 10, 0xFF07120B);
        chip.setTypeface(null, Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(0xEE58FF89);
        chipBg.setCornerRadius(dp(10));
        chip.setBackground(chipBg);
        FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(dp(78), dp(24), Gravity.TOP | Gravity.LEFT);
        chipLp.setMargins(dp(12), dp(12), 0, 0);
        card.addView(chip, chipLp);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setGravity(Gravity.BOTTOM);
        textBox.setPadding(dp(14), dp(12), dp(14), item.progress >= 0 ? dp(20) : dp(12));
        TextView t = label(item.title == null ? "Continua a guardare" : item.title, 16, Color.WHITE);
        t.setTypeface(null, Typeface.BOLD);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        String subText = item.subtitle == null ? "Guarda successivo" : item.subtitle;
        if (item.progress >= 0) subText += "  •  " + item.progress + "%";
        TextView sub = label(subText, 12, 0xFFD2D6DB);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textBox.addView(t);
        textBox.addView(sub);
        card.addView(textBox, new FrameLayout.LayoutParams(-1,-1));

        if (item.progress >= 0) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            pb.setProgress(item.progress);
            if (Build.VERSION.SDK_INT >= 21) {
                pb.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF58FF89));
                pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x553A4C40));
            }
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM);
            pp.setMargins(dp(12), 0, dp(12), dp(8));
            card.addView(pb, pp);
        }

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
        hsv.setClipToPadding(false);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(18), dp(16), dp(18), dp(28));
        hsv.addView(row, new HorizontalScrollView.LayoutParams(-2,-2));

        for (AppItem item : list) {
            row.addView(createAppCard(item), mediumAppCardParams());
        }

        content.addView(hsv, new LinearLayout.LayoutParams(-1, dp(198)));
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

        focusBehavior(
                card,
                customLabel(item),
                categoryDisplay(item.category) + " • tieni premuto per le opzioni"
        );
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

    private LinearLayout.LayoutParams mediumAppCardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(210), dp(140));
        lp.setMargins(0,0,dp(14),0);
        return lp;
    }

    private LinearLayout.LayoutParams appCardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardWidth(), cardHeight());
        lp.setMargins(0,0,dp(14),0);
        return lp;
    }

    private LinearLayout.LayoutParams appCardParams(int itemCount) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                responsiveCardWidth(itemCount),
                responsiveCardHeight(itemCount));
        lp.setMargins(0,0,dp(14),0);
        return lp;
    }

    private int responsiveCardWidth(int itemCount) {
        int screen = getResources().getDisplayMetrics().widthPixels;
        int available = Math.max(dp(640), screen - dp(52));
        int size = prefs.getInt("card_size", 1);

        int columns = size == 0 ? 5 : (size == 2 ? 3 : 4);
        if (itemCount == 1) columns = 3;
        else if (itemCount > 1 && itemCount < columns) columns = itemCount;

        int gaps = dp(14) * Math.max(0, columns - 1);
        return Math.max(dp(180), (available - gaps) / columns);
    }

    private int responsiveCardHeight(int itemCount) {
        int size = prefs.getInt("card_size", 1);
        int max = size == 0 ? dp(126) : (size == 2 ? dp(190) : dp(165));
        return Math.min(max, (int)(responsiveCardWidth(itemCount) * 0.60f));
    }

    private LinearLayout.LayoutParams wideCardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(290), dp(160));
        lp.setMargins(0,0,dp(16),0);
        return lp;
    }

    private void playNavigationTick() {
        try {
            android.media.AudioManager audio =
                    (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audio != null) {
                audio.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK, 0.18f);
            }
        } catch(Exception ignored) {}
    }

    private void focusBehavior(View view, String title, String subtitle) {
        view.setOnFocusChangeListener((v, focused) -> {
            if (focused) playNavigationTick();

            ViewParent parent = v.getParent();
            if (parent instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) parent;
                int selected = group.indexOfChild(v);
                for (int i=0; i<group.getChildCount(); i++) {
                    View sibling = group.getChildAt(i);
                    if (sibling == v) continue;
                    float tilt = i < selected ? 4.5f : -4.5f;
                    sibling.animate()
                            .scaleX(focused ? 0.96f : 1f)
                            .scaleY(focused ? 0.96f : 1f)
                            .alpha(focused ? 0.74f : 1f)
                            .rotationY(focused ? tilt : 0f)
                            .setDuration(165)
                            .start();
                    if (Build.VERSION.SDK_INT >= 21 && !focused) {
                        sibling.setTranslationZ(0f);
                    }
                }
            }

            v.animate()
                    .scaleX(focused ? 1.20f : 1f)
                    .scaleY(focused ? 1.20f : 1f)
                    .translationY(focused ? -dp(7) : 0f)
                    .rotationX(focused ? 2.5f : 0f)
                    .alpha(1f)
                    .setDuration(165)
                    .start();

            if (Build.VERSION.SDK_INT >= 21) {
                v.setElevation(dp(focused ? 30 : 5));
                v.setTranslationZ(dp(focused ? 18 : 0));
            }

            if (Build.VERSION.SDK_INT >= 23 && v instanceof FrameLayout) {
                ((FrameLayout) v).setForeground(focusRing(focused));
            } else {
                v.setBackground(cardBorder(focused));
            }

            if (focused && focusTitle != null) {
                focusTitle.setText(title);
                focusSubtitle.setText(subtitle);
            }
        });
    }

    private GradientDrawable focusRing(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setCornerRadius(dp(18));
        g.setStroke(
                dp(focused ? 4 : 1),
                focused ? 0xFF39FF75 : 0x004C6657
        );
        return g;
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

            boolean animate = prefs.getBoolean("matrix_anim", true);
            int intensity = prefs.getInt("matrix_intensity", 1);
            intensity = Math.max(0, Math.min(2, intensity));
            float intensityMul = intensity == 0 ? 0.62f : (intensity == 2 ? 1.18f : 0.88f);

            Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            glow.setColor(0x1638FF7A);
            c.drawCircle(w*.18f, h*.32f, h*.34f, glow);
            glow.setColor(0x1423C96A);
            c.drawCircle(w*.78f, h*.72f, h*.42f, glow);
            glow.setColor(0x111A4DFF);
            c.drawCircle(w*.58f, h*.28f, h*.28f, glow);

            long now = SystemClock.uptimeMillis();
            float phase = animate ? ((now % 9500L) / 9500f) : 0f;

            paint.setTypeface(Typeface.MONOSPACE);
            paint.setTextSize(Math.max(15, w/105f));
            float stepX = Math.max(25, w / (intensity == 2 ? 56f : (intensity == 0 ? 44f : 50f)));
            float stepY = paint.getTextSize()*1.35f;
            String chars = "01アイウエオカキクケコサシスセソABCDEFGHIJKLMNOPQRSTUVWXYZ";

            random.setSeed(2202);
            int column = 0;
            for (float x=0; x<w; x+=stepX, column++) {
                float start = -random.nextInt(Math.max(1,h/2));
                int len = 14 + random.nextInt(24);
                float speed = 0.55f + (column % 5) * 0.09f;
                float flow = phase * h * speed;
                for (int i=0;i<len;i++) {
                    float y = start + i*stepY + flow;
                    float span = h + stepY * 8f;
                    while (y > h + stepY) y -= span;
                    while (y < -stepY) y += span;
                    char ch = chars.charAt(random.nextInt(chars.length()));
                    int alpha = (int)(Math.max(14, 115 - i*4) * intensityMul);
                    alpha = Math.max(8, Math.min(150, alpha));
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

            if (animate) postInvalidateDelayed(80);
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
            if (!isUserFacingLauncherEntry(r, pm)) continue;

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

    private boolean isUserFacingLauncherEntry(ResolveInfo r, PackageManager pm) {
        if (r == null || r.activityInfo == null || r.activityInfo.applicationInfo == null) return false;
        if (!r.activityInfo.enabled || !r.activityInfo.applicationInfo.enabled) return false;
        if (!r.activityInfo.exported) return false;

        String pkg = r.activityInfo.packageName == null ? "" : r.activityInfo.packageName.toLowerCase(Locale.ROOT);
        String activity = r.activityInfo.name == null ? "" : r.activityInfo.name.toLowerCase(Locale.ROOT);
        String label;
        try { label = String.valueOf(r.loadLabel(pm)).trim().toLowerCase(Locale.ROOT); }
        catch(Exception e) { label = ""; }

        // Componenti Philips/Android che possono dichiararsi launcher ma non sono vere app per l'utente.
        if (pkg.startsWith("org.droidtv.devicesetup") ||
                pkg.startsWith("org.droidtv.settings") ||
                pkg.equals("com.android.settings") ||
                pkg.startsWith("com.android.tv.settings") ||
                pkg.contains("setupwizard") ||
                pkg.contains("setupwraith") ||
                pkg.contains("provision")) {
            return false;
        }

        int flags = r.activityInfo.applicationInfo.flags;
        boolean system = (flags & ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

        String technical = pkg + " " + activity + " " + label;

        if (system && containsAny(technical,
                "devicesetup", "device_setup", "setupactivity", ".setup",
                "setupwizard", "wizard", "provision", "provisioning",
                "systemui", "inputmethod", "factorytest", "factory.test",
                "diagnostic", "engineeringmode", "tvsettings",
                "settings.setup", "leanbacklauncher", "tvlauncher")) {
            return false;
        }

        // Se il nome mostrato è ancora un identificatore tecnico (es. org.droidtv....),
        // su un componente di sistema non lo mostriamo come app.
        if (system && (label.startsWith("org.") || label.startsWith("com.") ||
                (!pkg.isEmpty() && label.startsWith(pkg)))) {
            return false;
        }

        return true;
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
        Set<String> manual = getSet("favorites");
        List<AppItem> out = new ArrayList<>();

        for (AppItem a : apps) {
            if (manual.contains(a.pkg)) out.add(a);
        }

        List<AppItem> ranked = new ArrayList<>();
        for (AppItem a : apps) {
            if (manual.contains(a.pkg)) continue;
            long uses = prefs.getLong("use_count_"+a.pkg, 0);
            long last = prefs.getLong("last_"+a.pkg, 0);
            if (uses > 0 || last > 0) ranked.add(a);
        }

        Collections.sort(ranked, (a,b) -> {
            long ua = prefs.getLong("use_count_"+a.pkg, 0);
            long ub = prefs.getLong("use_count_"+b.pkg, 0);
            if (ua != ub) return Long.compare(ub, ua);
            return Long.compare(
                    prefs.getLong("last_"+b.pkg, 0),
                    prefs.getLong("last_"+a.pkg, 0));
        });

        for (AppItem a : ranked) {
            if (out.size() >= 8) break;
            out.add(a);
        }
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
                    TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
                    TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                    TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS
            };
            c = getContentResolver().query(
                    TvContract.WatchNextPrograms.CONTENT_URI,
                    projection, null, null,
                    TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC");
            if (c != null) {
                int t = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_TITLE);
                int e = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE);
                int ds = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION);
                int in = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_INTENT_URI);
                int p = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI);
                int dur = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS);
                int pos = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS);

                while (c.moveToNext() && out.size() < 12) {
                    String title = t >= 0 ? c.getString(t) : null;
                    String ep = e >= 0 ? c.getString(e) : null;
                    String sub = ep != null && !ep.isEmpty() ? ep : (ds >= 0 ? c.getString(ds) : null);
                    int progress = -1;
                    if (dur >= 0 && pos >= 0) {
                        long duration = c.getLong(dur);
                        long position = c.getLong(pos);
                        if (duration > 0 && position >= 0) {
                            progress = (int)Math.max(0, Math.min(100, (position * 100L) / duration));
                        }
                    }
                    out.add(new WatchItem(
                            title, sub,
                            in >= 0 ? c.getString(in) : null,
                            p >= 0 ? c.getString(p) : null,
                            progress));
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
        long now = System.currentTimeMillis();
        long count = prefs.getLong("use_count_"+item.pkg, 0) + 1;
        prefs.edit()
                .putLong("last_"+item.pkg, now)
                .putLong("use_count_"+item.pkg, count)
                .apply();
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
        String[] options = {
                "Cambia categoria",
                "Sposta a sinistra",
                "Sposta a destra",
                "Nascondi app"
        };

        new AlertDialog.Builder(this)
                .setTitle(customLabel(item))
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        showCategoryPicker(item);
                    } else if (which == 1) {
                        moveApp(item.pkg, -1);
                    } else if (which == 2) {
                        moveApp(item.pkg, 1);
                    } else if (which == 3) {
                        Set<String> hidden = getSet("hidden");
                        hidden.add(item.pkg);
                        saveSet("hidden", hidden);
                        loadApps(); buildHome();
                    }
                }).show();
    }

    private void showCategoryPicker(AppItem item) {
        List<String> idsList = new ArrayList<>(getCategoryOrder());
        idsList.add("other");

        String[] names = new String[idsList.size()];
        for (int i=0; i<idsList.size(); i++) names[i] = categoryDisplay(idsList.get(i));

        new AlertDialog.Builder(this)
                .setTitle("Sposta " + customLabel(item) + " in…")
                .setItems(names, (d, which) -> {
                    prefs.edit().putString("cat_"+item.pkg, idsList.get(which)).apply();
                    loadApps();
                    buildHome();
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

    private TextView controlTile(String symbol, String title, Runnable action, Dialog dialog) {
        TextView v = label(symbol + "\n" + title, 16, Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setFocusable(true);
        v.setClickable(true);
        v.setPadding(dp(14), dp(14), dp(14), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD914241C);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x884CFF83);
        v.setBackground(bg);

        v.setOnClickListener(x -> {
            dialog.dismiss();
            action.run();
        });
        v.setOnFocusChangeListener((x, focused) -> {
            x.animate()
                    .scaleX(focused ? 1.07f : 1f)
                    .scaleY(focused ? 1.07f : 1f)
                    .translationY(focused ? -dp(3) : 0f)
                    .setDuration(130)
                    .start();
            GradientDrawable g = new GradientDrawable();
            g.setColor(focused ? 0xEF176C37 : 0xD914241C);
            g.setCornerRadius(dp(18));
            g.setStroke(dp(focused ? 3 : 1), focused ? Color.WHITE : 0x884CFF83);
            x.setBackground(g);
        });
        return v;
    }

    private void addControlRow(LinearLayout panel, TextView left, TextView right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(92), 1f);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        row.addView(left, lp);
        row.addView(right, lp);
        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showControlCenter() {
        final Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(20), dp(22), dp(22));
        panel.setClipChildren(false);

        GradientDrawable panelBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xF20A1510, 0xF20B1D16, 0xF2080D0B});
        panelBg.setCornerRadius(dp(26));
        panelBg.setStroke(dp(2), 0xAA58FF89);
        panel.setBackground(panelBg);

        TextView title = label("Centro di controllo", 24, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        TextView sub = label("Accessi rapidi Foyho TV", 13, 0xFF8EFFB0);
        panel.addView(title);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2);
        sp.setMargins(0, dp(3), 0, dp(12));
        panel.addView(sub, sp);

        addControlRow(panel,
                controlTile("⌕", "Cerca app", this::showAppSearch, dialog),
                controlTile("◉", "Wi-Fi", () -> safeStart(Settings.ACTION_WIFI_SETTINGS), dialog));

        addControlRow(panel,
                controlTile("B", "Bluetooth", () -> safeStart("android.settings.BLUETOOTH_SETTINGS"), dialog),
                controlTile("⇄", "Ingressi", () -> safeStart("android.settings.TV_INPUT_SETTINGS"), dialog));

        addControlRow(panel,
                controlTile("♪", "Audio", () -> safeStart("android.settings.SOUND_SETTINGS"), dialog),
                controlTile("⚙", "Android", () -> safeStart(Settings.ACTION_SETTINGS), dialog));

        addControlRow(panel,
                controlTile("✦", "Personalizza Foyho", this::showSettings, dialog),
                controlTile("↕", "Backup / Ripristino", this::showBackupMenu, dialog));

        dialog.setContentView(panel);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams a = w.getAttributes();
            a.dimAmount = 0.62f;
            w.setAttributes(a);
            w.setLayout(dp(720), WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
    }

    private void showAppSearch() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Nome app");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF93A59A);
        input.setPadding(dp(18), dp(12), dp(18), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF101B15);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(2), 0xAA58FF89);
        input.setBackground(bg);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Cerca app")
                .setView(input)
                .setPositiveButton("Cerca", null)
                .setNegativeButton("Annulla", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String q = input.getText().toString().trim();
            d.dismiss();
            showSearchResults(q);
        }));
        d.show();
    }

    private void showSearchResults(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<AppItem> found = new ArrayList<>();
        for (AppItem a : apps) {
            String hay = (customLabel(a) + " " + a.pkg + " " + categoryDisplay(a.category)).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || hay.contains(q)) found.add(a);
        }

        if (found.isEmpty()) {
            Toast.makeText(this, "Nessuna app trovata", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[found.size()];
        for (int i=0; i<found.size(); i++) {
            names[i] = customLabel(found.get(i)) + "  ·  " + categoryDisplay(found.get(i).category);
        }

        new AlertDialog.Builder(this)
                .setTitle(q.isEmpty() ? "Tutte le app" : "Risultati")
                .setItems(names, (d, which) -> launchApp(found.get(which)))
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void showBackupMenu() {
        String[] items = {"Esporta backup", "Ripristina da backup"};
        new AlertDialog.Builder(this)
                .setTitle("Backup Foyho TV")
                .setItems(items, (d, which) -> {
                    if (which == 0) exportBackup();
                    else importBackup();
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void exportBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/json");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE, "Foyho-TV-backup.json");
        startActivityForResult(i, REQ_BACKUP_EXPORT);
    }

    private void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/json");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_BACKUP_IMPORT);
    }

    private byte[] readUriBytes(Uri uri, int maxBytes) throws Exception {
        InputStream in;
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            in = new FileInputStream(new File(uri.getPath()));
        } else {
            in = getContentResolver().openInputStream(uri);
        }
        if (in == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0, n;
        try {
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maxBytes) throw new IOException("File troppo grande");
                out.write(buf, 0, n);
            }
        } finally {
            in.close();
        }
        return out.toByteArray();
    }

    private void writeBackup(Uri uri) {
        try {
            JSONObject root = new JSONObject();
            root.put("format", 1);
            root.put("app", "Foyho TV");
            root.put("created", System.currentTimeMillis());

            JSONObject values = new JSONObject();
            for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                String key = e.getKey();
                if ("weather_text".equals(key) || "weather_last".equals(key) || "override_pause_until".equals(key)) continue;
                Object value = e.getValue();
                JSONObject item = new JSONObject();

                if (value instanceof String) {
                    item.put("type", "string");
                    item.put("value", value);
                } else if (value instanceof Boolean) {
                    item.put("type", "boolean");
                    item.put("value", value);
                } else if (value instanceof Integer) {
                    item.put("type", "int");
                    item.put("value", value);
                } else if (value instanceof Long) {
                    item.put("type", "long");
                    item.put("value", value);
                } else if (value instanceof Float) {
                    item.put("type", "float");
                    item.put("value", value);
                } else if (value instanceof Set) {
                    item.put("type", "set");
                    JSONArray arr = new JSONArray();
                    for (Object x : (Set<?>) value) arr.put(String.valueOf(x));
                    item.put("value", arr);
                } else {
                    continue;
                }
                values.put(key, item);
            }
            root.put("preferences", values);

            String wallpaper = prefs.getString("wallpaper_uri", null);
            if (wallpaper != null && !wallpaper.isEmpty()) {
                try {
                    byte[] image = readUriBytes(Uri.parse(wallpaper), 12 * 1024 * 1024);
                    if (image != null && image.length > 0) {
                        root.put("wallpaper_data", android.util.Base64.encodeToString(image, android.util.Base64.NO_WRAP));
                    }
                } catch(Exception ignored) {}
            }

            OutputStream os = getContentResolver().openOutputStream(uri, "w");
            if (os == null) throw new IOException("Impossibile aprire il file");
            try {
                os.write(root.toString(2).getBytes("UTF-8"));
                os.flush();
            } finally {
                os.close();
            }
            Toast.makeText(this, "Backup Foyho TV esportato", Toast.LENGTH_LONG).show();
        } catch(Exception e) {
            Toast.makeText(this, "Errore backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void restoreBackup(Uri uri) {
        try {
            byte[] bytes = readUriBytes(uri, 20 * 1024 * 1024);
            if (bytes == null) throw new IOException("Backup vuoto");
            JSONObject root = new JSONObject(new String(bytes, "UTF-8"));
            JSONObject values = root.getJSONObject("preferences");

            SharedPreferences.Editor ed = prefs.edit().clear();
            Iterator<String> keys = values.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject item = values.getJSONObject(key);
                String type = item.getString("type");
                if ("string".equals(type)) ed.putString(key, item.optString("value", ""));
                else if ("boolean".equals(type)) ed.putBoolean(key, item.optBoolean("value", false));
                else if ("int".equals(type)) ed.putInt(key, item.optInt("value", 0));
                else if ("long".equals(type)) ed.putLong(key, item.optLong("value", 0L));
                else if ("float".equals(type)) ed.putFloat(key, (float)item.optDouble("value", 0d));
                else if ("set".equals(type)) {
                    JSONArray arr = item.optJSONArray("value");
                    Set<String> set = new LinkedHashSet<>();
                    if (arr != null) for (int i=0; i<arr.length(); i++) set.add(arr.optString(i));
                    ed.putStringSet(key, set);
                }
            }
            ed.apply();

            String encoded = root.optString("wallpaper_data", "");
            if (!encoded.isEmpty()) {
                byte[] image = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
                File f = new File(getFilesDir(), "restored_wallpaper.img");
                FileOutputStream fos = new FileOutputStream(f);
                try { fos.write(image); } finally { fos.close(); }
                prefs.edit().putString("wallpaper_uri", Uri.fromFile(f).toString()).apply();
            }

            prefs.edit().remove("weather_text").remove("weather_last").apply();
            loadApps();
            buildHome();
            updateClock();
            updateWeather();
            Toast.makeText(this, "Backup ripristinato", Toast.LENGTH_LONG).show();
        } catch(Exception e) {
            Toast.makeText(this, "Backup non valido: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void chooseMatrixIntensity() {
        String[] items = {"Leggera", "Media", "Forte"};
        int current = prefs.getInt("matrix_intensity", 1);
        new AlertDialog.Builder(this)
                .setTitle("Intensità Matrix")
                .setSingleChoiceItems(items, current, (d, which) -> {
                    prefs.edit().putInt("matrix_intensity", which).apply();
                    d.dismiss();
                    buildHome();
                    updateWeather();
                })
                .show();
    }

    private List<String> getCategoryOrder() {
        List<String> defaults = Arrays.asList("tv","media","music","games","tools");
        List<String> out = new ArrayList<>();

        String raw = prefs.getString("category_order", "");
        if (!raw.isEmpty()) {
            for (String id : raw.split("\\|")) {
                if (id != null && !id.trim().isEmpty() && !"other".equals(id) && !out.contains(id)) {
                    out.add(id);
                }
            }
        }

        if (out.isEmpty()) out.addAll(defaults);

        // Le categorie base non si perdono mai.
        for (String id : defaults) if (!out.contains(id)) out.add(id);

        // Recupera eventuali categorie custom anche dopo un backup/import incompleto.
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("category_name_custom_")) {
                String id = key.substring("category_name_".length());
                if (!out.contains(id)) out.add(id);
            }
        }
        for (AppItem a : apps) {
            if (a.category != null && a.category.startsWith("custom_") && !out.contains(a.category)) {
                out.add(a.category);
            }
        }

        return out;
    }

    private void saveCategoryOrder(List<String> order) {
        prefs.edit().putString("category_order", android.text.TextUtils.join("|", order)).apply();
    }

    private boolean isBuiltInCategory(String id) {
        return "tv".equals(id) || "media".equals(id) || "music".equals(id) ||
                "games".equals(id) || "tools".equals(id);
    }

    private int countAppsInCategory(String id) {
        int count = 0;
        for (AppItem a : apps) if (id.equals(a.category)) count++;
        return count;
    }

    private void showCategoryManager() {
        List<String> ids = getCategoryOrder();
        String[] items = new String[ids.size() + 1];
        items[0] = "＋ Crea nuova categoria";

        for (int i=0; i<ids.size(); i++) {
            String id = ids.get(i);
            items[i+1] = (i+1) + ".  " + categoryDisplay(id) +
                    "   (" + countAppsInCategory(id) + " app)";
        }

        new AlertDialog.Builder(this)
                .setTitle("Gestisci categorie")
                .setItems(items, (d, which) -> {
                    if (which == 0) showCreateCategoryDialog();
                    else showCategoryActions(ids.get(which-1));
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void showCreateCategoryDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Nome nuova categoria");

        new AlertDialog.Builder(this)
                .setTitle("Nuova categoria")
                .setView(input)
                .setPositiveButton("Crea", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Inserisci un nome per la categoria", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String id = "custom_" + System.currentTimeMillis();
                    List<String> order = getCategoryOrder();
                    order.add(id);

                    prefs.edit()
                            .putString("category_name_"+id, name)
                            .putString("category_order", android.text.TextUtils.join("|", order))
                            .apply();

                    buildHome();
                    updateWeather();
                    Toast.makeText(this, "Categoria «" + name + "» creata", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void showCategoryActions(String id) {
        List<String> order = getCategoryOrder();
        int index = order.indexOf(id);
        boolean custom = !isBuiltInCategory(id);

        List<String> actions = new ArrayList<>();
        actions.add("Rinomina");
        if (index > 0) actions.add("Sposta verso l’alto");
        if (index >= 0 && index < order.size()-1) actions.add("Sposta verso il basso");
        if (custom) actions.add("Elimina categoria");

        String[] options = actions.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(categoryDisplay(id))
                .setItems(options, (d, which) -> {
                    String action = options[which];
                    if ("Rinomina".equals(action)) {
                        showRenameCategoryDialog(id);
                    } else if ("Sposta verso l’alto".equals(action)) {
                        moveCategory(id, -1);
                    } else if ("Sposta verso il basso".equals(action)) {
                        moveCategory(id, 1);
                    } else if ("Elimina categoria".equals(action)) {
                        confirmDeleteCategory(id);
                    }
                })
                .setNegativeButton("Indietro", (d,w) -> showCategoryManager())
                .show();
    }

    private void showRenameCategoryDialog(String id) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(categoryDisplay(id));
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle("Rinomina categoria")
                .setView(input)
                .setPositiveButton("Salva", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Il nome non può essere vuoto", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefs.edit().putString("category_name_"+id, name).apply();
                    buildHome();
                    updateWeather();
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void moveCategory(String id, int delta) {
        List<String> order = getCategoryOrder();
        int i = order.indexOf(id);
        if (i < 0) return;

        int j = Math.max(0, Math.min(order.size()-1, i + delta));
        if (i == j) return;

        Collections.swap(order, i, j);
        saveCategoryOrder(order);
        buildHome();
        updateWeather();

        Toast.makeText(this,
                "«" + categoryDisplay(id) + "» spostata " + (delta < 0 ? "su" : "giù"),
                Toast.LENGTH_SHORT).show();
    }

    private void confirmDeleteCategory(String id) {
        final String name = categoryDisplay(id);
        int count = countAppsInCategory(id);

        new AlertDialog.Builder(this)
                .setTitle("Elimina " + name + "?")
                .setMessage(count > 0
                        ? "Le " + count + " app presenti verranno spostate in «Altre app»."
                        : "La categoria verrà rimossa.")
                .setPositiveButton("Elimina", (d,w) -> deleteCategory(id))
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void deleteCategory(String id) {
        if (isBuiltInCategory(id)) return;

        SharedPreferences.Editor ed = prefs.edit();
        for (AppItem a : apps) {
            if (id.equals(a.category)) ed.putString("cat_"+a.pkg, "other");
        }
        ed.remove("category_name_"+id);

        List<String> order = getCategoryOrder();
        order.remove(id);
        ed.putString("category_order", android.text.TextUtils.join("|", order));
        ed.apply();

        loadApps();
        buildHome();
        updateWeather();
        Toast.makeText(this, "Categoria eliminata", Toast.LENGTH_SHORT).show();
    }

    private void showSettings() {
        boolean overrideActive = prefs.getBoolean("override_launcher", false) && isFoyhoAccessibilityEnabled();
        boolean matrixAnim = prefs.getBoolean("matrix_anim", true);
        String[] options = {
                "Scegli sfondo personale",
                "Ripristina sfondo Foyho",
                "Animazione Matrix: " + (matrixAnim ? "ATTIVA" : "DISATTIVA"),
                "Intensità Matrix",
                "Città meteo",
                "Imposta Foyho TV come Home",
                "Sostituisci il launcher corrente: " + (overrideActive ? "ATTIVO" : "DISATTIVO"),
                "Oscuramento sfondo",
                "Gestisci categorie",
                "Backup e ripristino",
                "Mostra app nascoste"
        };
        new AlertDialog.Builder(this)
                .setTitle("Personalizza Foyho TV")
                .setItems(options, (d, which) -> {
                    if (which == 0) chooseWallpaper();
                    else if (which == 1) {
                        prefs.edit().remove("wallpaper_uri").apply();
                        buildHome();
                        updateWeather();
                    } else if (which == 2) {
                        prefs.edit().putBoolean("matrix_anim", !matrixAnim).apply();
                        buildHome();
                        updateWeather();
                    } else if (which == 3) chooseMatrixIntensity();
                    else if (which == 4) showWeatherCityDialog();
                    else if (which == 5) requestDefaultHome();
                    else if (which == 6) showLauncherOverrideDialog();
                    else if (which == 7) chooseDim();
                    else if (which == 8) showCategoryManager();
                    else if (which == 9) showBackupMenu();
                    else if (which == 10) showHiddenApps();
                }).show();
    }

    private void showLauncherOverrideDialog() {
        boolean requested = prefs.getBoolean("override_launcher", false);
        boolean serviceEnabled = isFoyhoAccessibilityEnabled();
        boolean projectivyAccessibility = isAccessibilityPackageEnabled("com.spocky.projengmenu");

        String status = requested && serviceEnabled
                ? "ATTIVO"
                : (requested ? "DA ABILITARE IN ACCESSIBILITÀ" : "DISATTIVO");

        String message = "Stato: " + status +
                "\n\nQuando è attivo, Foyho TV intercetta il ritorno a un altro launcher e riporta automaticamente alla Home Foyho.";

        if (projectivyAccessibility) {
            message += "\n\nAttenzione: Projectivy risulta attivo nei servizi di Accessibilità. Per evitare che i due launcher si richiamino a vicenda, disattiva in Projectivy «Sostituisci il launcher corrente».";
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Sostituisci il launcher corrente")
                .setMessage(message)
                .setNegativeButton("Chiudi", null)
                .setNeutralButton("Apri Accessibilità", (d, w) -> openAccessibilitySettings());

        if (requested) {
            b.setPositiveButton("Disattiva", (d, w) -> {
                prefs.edit().putBoolean("override_launcher", false).apply();
                Toast.makeText(this, "Sostituzione launcher disattivata", Toast.LENGTH_LONG).show();
            });
        } else {
            b.setPositiveButton("Attiva", (d, w) -> {
                prefs.edit().putBoolean("override_launcher", true).apply();
                if (!isFoyhoAccessibilityEnabled()) {
                    Toast.makeText(this, "Ora abilita «Foyho TV - Sostituisci launcher» in Accessibilità", Toast.LENGTH_LONG).show();
                    openAccessibilitySettings();
                } else {
                    Toast.makeText(this, "Sostituzione launcher attivata", Toast.LENGTH_LONG).show();
                }
            });
        }
        b.show();
    }

    private void openAccessibilitySettings() {
        prefs.edit().putLong("override_pause_until", System.currentTimeMillis() + 20000L).apply();
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(i);
        } catch(Exception e) {
            safeStart(Settings.ACTION_SETTINGS);
        }
    }

    private boolean isFoyhoAccessibilityEnabled() {
        return isAccessibilityPackageEnabled(getPackageName());
    }

    private boolean isAccessibilityPackageEnabled(String packageName) {
        try {
            String enabled = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.toLowerCase(Locale.ROOT)
                    .contains(packageName.toLowerCase(Locale.ROOT));
        } catch(Exception ignored) {
            return false;
        }
    }

    private void showWeatherCityDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("es. Napoli");
        input.setText(prefs.getString("weather_city", ""));

        new AlertDialog.Builder(this)
                .setTitle("Città meteo")
                .setMessage("Scrivi la città da usare per il meteo. Lascia vuoto per il rilevamento automatico tramite rete.")
                .setView(input)
                .setPositiveButton("Salva", (d, w) -> {
                    String city = input.getText().toString().trim();
                    prefs.edit()
                            .putString("weather_city", city)
                            .remove("weather_last")
                            .remove("weather_text")
                            .apply();
                    updateWeather();
                })
                .setNeutralButton("Automatico", (d, w) -> {
                    prefs.edit()
                            .remove("weather_city")
                            .remove("weather_last")
                            .remove("weather_text")
                            .apply();
                    updateWeather();
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void updateWeather() {
        if (weather == null) return;

        long now = System.currentTimeMillis();
        long last = prefs.getLong("weather_last", 0);
        String cached = prefs.getString("weather_text", null);
        if (cached != null && now - last < 30L * 60L * 1000L) {
            weather.setText(cached);
            return;
        }

        weather.setText("☁ …");

        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                String city = prefs.getString("weather_city", "").trim();
                String target;
                if (city.isEmpty()) {
                    target = "https://wttr.in/?format=j1";
                } else {
                    target = "https://wttr.in/" +
                            URLEncoder.encode(city, "UTF-8").replace("+", "%20") +
                            "?format=j1";
                }

                con = (HttpURLConnection) new URL(target).openConnection();
                con.setConnectTimeout(6000);
                con.setReadTimeout(6000);
                con.setRequestProperty("User-Agent", "FoyhoTV/2.3");
                con.setRequestProperty("Accept", "application/json");

                StringBuilder out = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) out.append(line);
                }

                JSONObject root = new JSONObject(out.toString());
                JSONObject current = root.getJSONArray("current_condition").getJSONObject(0);
                String temp = current.optString("temp_C", "--");
                String desc = "";
                JSONArray descArray = current.optJSONArray("weatherDesc");
                if (descArray != null && descArray.length() > 0) {
                    desc = descArray.getJSONObject(0).optString("value", "");
                }

                String text = weatherSymbol(desc) + " " + temp + "°C";
                prefs.edit()
                        .putString("weather_text", text)
                        .putLong("weather_last", System.currentTimeMillis())
                        .apply();

                final String result = text;
                runOnUiThread(() -> {
                    if (weather != null) weather.setText(result);
                });
            } catch(Exception e) {
                runOnUiThread(() -> {
                    if (weather != null) {
                        weather.setText(prefs.getString("weather_text", "☁ --°C"));
                    }
                });
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }

    private String weatherSymbol(String description) {
        String d = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (d.contains("thunder")) return "⚡";
        if (d.contains("snow") || d.contains("sleet") || d.contains("ice")) return "❄";
        if (d.contains("rain") || d.contains("drizzle") || d.contains("shower")) return "☂";
        if (d.contains("fog") || d.contains("mist")) return "≋";
        if (d.contains("partly")) return "⛅";
        if (d.contains("cloud") || d.contains("overcast")) return "☁";
        if (d.contains("sun") || d.contains("clear")) return "☀";
        return "☁";
    }

    private boolean isDefaultHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    return rm.isRoleHeld(RoleManager.ROLE_HOME);
                }
            }

            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null &&
                    getPackageName().equals(ri.activityInfo.packageName);
        } catch(Exception ignored) {
            return false;
        }
    }

    private void requestDefaultHome() {
        prefs.edit().putLong("override_pause_until", System.currentTimeMillis() + 20000L).apply();
        if (isDefaultHome()) {
            Toast.makeText(this, "Foyho TV è già la Home predefinita", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    startActivityForResult(
                            rm.createRequestRoleIntent(RoleManager.ROLE_HOME),
                            REQ_HOME_ROLE
                    );
                    return;
                }
            }

            Intent settings = new Intent(Settings.ACTION_HOME_SETTINGS);
            if (settings.resolveActivity(getPackageManager()) != null) {
                startActivity(settings);
                Toast.makeText(this, "Seleziona Foyho TV come app Home predefinita", Toast.LENGTH_LONG).show();
                return;
            }

            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addCategory(Intent.CATEGORY_DEFAULT);
            startActivity(Intent.createChooser(home, "Scegli Foyho TV come Home"));
        } catch(Exception e) {
            Toast.makeText(this, "Apri Impostazioni > App > App predefinite > Home e scegli Foyho TV", Toast.LENGTH_LONG).show();
            safeStart(Settings.ACTION_SETTINGS);
        }
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
        hsv.setClipChildren(false);
        hsv.setClipToPadding(false);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        // Più margine sui quattro lati: il tile al 120% non viene più tagliato.
        row.setPadding(dp(26), dp(24), dp(42), dp(34));
        hsv.addView(row, new HorizontalScrollView.LayoutParams(-2,-2));

        row.addView(systemCard("Video locali","▶", v -> openLocalVideo()), mediumAppCardParams());
        row.addView(systemCard("Ingressi","⇄", v -> safeStart("android.settings.TV_INPUT_SETTINGS")), mediumAppCardParams());
        row.addView(systemCard("Home predefinita","⌂", v -> requestDefaultHome()), mediumAppCardParams());
        row.addView(systemCard("Wi‑Fi","◉", v -> safeStart(Settings.ACTION_WIFI_SETTINGS)), mediumAppCardParams());
        row.addView(systemCard("Impostazioni","⚙", v -> safeStart(Settings.ACTION_SETTINGS)), mediumAppCardParams());
        row.addView(systemCard("Altre app","⋯", v -> showOtherApps()), mediumAppCardParams());
        content.addView(hsv, new LinearLayout.LayoutParams(-1, dp(218)));
    }

    private void showOtherApps() {
        List<AppItem> other = filterByCategory("other");
        if (other.isEmpty()) {
            Toast.makeText(this, "Nessuna app nella categoria Altre app", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[other.size()];
        for (int i=0; i<other.size(); i++) names[i] = customLabel(other.get(i));

        new AlertDialog.Builder(this)
                .setTitle("Altre app")
                .setItems(names, (d, which) -> launchApp(other.get(which)))
                .setNegativeButton("Chiudi", null)
                .show();
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
        prefs.edit().putLong("override_pause_until", System.currentTimeMillis() + 10000L).apply();
        try { startActivity(new Intent(action)); }
        catch(Exception e) { try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch(Exception ignored) {} }
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);

        if (req == REQ_HOME_ROLE) {
            if (isDefaultHome()) {
                Toast.makeText(this, "Foyho TV impostata come Home predefinita", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Foyho TV non è ancora la Home predefinita", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (result != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        if (req == REQ_BACKUP_EXPORT) {
            writeBackup(uri);
            return;
        }
        if (req == REQ_BACKUP_IMPORT) {
            restoreBackup(uri);
            return;
        }

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


    private TextView headerAction(String symbol, String title, String subtitle) {
        TextView v = label(symbol, 24, 0xFFE7FFF0);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setFocusable(true);
        v.setClickable(true);
        v.setMinWidth(dp(52));
        v.setMinHeight(dp(50));
        v.setPadding(dp(12), dp(8), dp(12), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x44132A1C);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x7758FF89);
        v.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(50));
        lp.setMargins(0, 0, dp(8), 0);
        v.setLayoutParams(lp);

        v.setOnFocusChangeListener((view, focused) -> {
            if (focused) playNavigationTick();

            view.animate()
                    .scaleX(focused ? 1.09f : 1f)
                    .scaleY(focused ? 1.09f : 1f)
                    .setDuration(120)
                    .start();

            GradientDrawable g = new GradientDrawable();
            g.setColor(focused ? 0xCC16C95A : 0x44132A1C);
            g.setCornerRadius(dp(18));
            g.setStroke(dp(focused ? 3 : 1), focused ? Color.WHITE : 0x7758FF89);
            view.setBackground(g);

            if (Build.VERSION.SDK_INT >= 21) {
                view.setElevation(dp(focused ? 10 : 2));
            }

            if (focused && focusTitle != null) {
                focusTitle.setText(title);
                focusSubtitle.setText(subtitle);
            }
        });
        return v;
    }

    private TextView headerAccountAction() {
        TextView v = headerAction("G", "Account Google", "Account collegato e sincronizzazione");
        v.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC1E5CD6);
        bg.setCornerRadius(dp(25));
        bg.setStroke(dp(1), 0xAA8AB4FF);
        v.setBackground(bg);
        return v;
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
        if ("other".equals(id)) return "Altre app";

        String customName = prefs.getString("category_name_"+id, null);
        if (customName != null && !customName.trim().isEmpty()) return customName.trim();

        if ("tv".equals(id)) return "App TV";
        if ("media".equals(id)) return "Streaming & Film";
        if ("music".equals(id)) return "Musica";
        if ("games".equals(id)) return "Giochi";
        if ("tools".equals(id)) return "Utility";
        return "Categoria";
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
