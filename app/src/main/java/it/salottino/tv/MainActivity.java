package it.salottino.tv;

import android.app.*;
import android.os.*;
import android.provider.Settings;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    private static final int REQ_WALLPAPER = 101;
    private static final int REQ_ICON = 102;
    private static final int REQ_BACKUP = 103;
    private static final int REQ_RESTORE = 104;

    private SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout content;
    private TextView clock, date, focusTitle, focusSubtitle;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockRunnable = new Runnable() {
        @Override public void run() {
            renderClock();
            handler.postDelayed(this, 30000);
        }
    };
    private final List<AppItem> apps = new ArrayList<>();
    private String pendingIconPackage = null;
    private boolean autoLaunchDone = false;
    private Bitmap wallpaperBitmap = null;

    static class AppItem {
        String pkg, activity, label, category;
        Drawable icon;
        AppItem(String p, String a, String l, String c, Drawable i) {
            pkg=p; activity=a; label=l; category=c; icon=i;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("salottino_tv", MODE_PRIVATE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
        loadApps();
        buildHome();
        updateClock();
        maybeAutoLaunch();
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
        content.setPadding(dp(44), dp(28), dp(44), dp(36));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        buildHeader();
        buildHero();

        Set<String> fav = getSet("favorites");
        List<AppItem> favorites = filterApps("favorites");
        if (!favorites.isEmpty()) addAppRow("Preferiti", favorites);

        addAppRowIfAny("Streaming & Media", filterByCategory("media"));
        addAppRowIfAny("Giochi", filterByCategory("games"));
        addAppRowIfAny("Strumenti", filterByCategory("tools"));
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
        TextView title = label("SALOTTINO TV", 25, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        TextView sub = label("Home", 13, 0xFFB7C0CB);
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
        LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(dp(150), dp(52));
        cbp.setMargins(dp(24),0,0,0);
        customize.setOnClickListener(v -> showSettings());
        header.addView(customize, cbp);

        content.addView(header);
    }

    private void buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(0, dp(36), 0, dp(24));

        focusTitle = label("La tua TV, come vuoi tu.", 32, Color.WHITE);
        focusTitle.setTypeface(null, Typeface.BOLD);
        focusSubtitle = label("Scegli un'app oppure personalizza la home.", 16, 0xFFD1D6DD);

        hero.addView(focusTitle);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2);
        sp.setMargins(0, dp(8), 0, 0);
        hero.addView(focusSubtitle, sp);
        content.addView(hero);
    }

    private void addAppRowIfAny(String title, List<AppItem> list) {
        if (!list.isEmpty()) addAppRow(title, list);
    }

    private void addAppRow(String title, List<AppItem> list) {
        TextView rowTitle = label(title, 19, Color.WHITE);
        rowTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams rtp = new LinearLayout.LayoutParams(-1,-2);
        rtp.setMargins(0, dp(10), 0, dp(8));
        content.addView(rowTitle, rtp);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipChildren(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(dp(3), dp(8), dp(3), dp(16));
        hsv.addView(row, new HorizontalScrollView.LayoutParams(-2,-2));

        for (AppItem item : list) row.addView(createAppCard(item), appCardParams());
        content.addView(hsv, new LinearLayout.LayoutParams(-1, cardHeight()+dp(30)));
    }

    private void addSystemRow() {
        TextView rowTitle = label("TV & Sistema", 19, Color.WHITE);
        rowTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams rtp = new LinearLayout.LayoutParams(-1,-2);
        rtp.setMargins(0, dp(12), 0, dp(8));
        content.addView(rowTitle, rtp);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(3), dp(8), dp(3), dp(18));
        hsv.addView(row);

        row.addView(systemCard("Impostazioni", "⚙", Settings.ACTION_SETTINGS), appCardParams());
        row.addView(systemCard("Ingressi", "⇄", "android.settings.TV_INPUT_SETTINGS"), appCardParams());
        row.addView(systemCard("Wi‑Fi", "◉", Settings.ACTION_WIFI_SETTINGS), appCardParams());
        row.addView(systemCard("Schermo", "▣", Settings.ACTION_DISPLAY_SETTINGS), appCardParams());
        row.addView(systemCard("Audio", "♪", Settings.ACTION_SOUND_SETTINGS), appCardParams());
        content.addView(hsv, new LinearLayout.LayoutParams(-1, cardHeight()+dp(30)));
    }

    private View systemCard(String name, String symbol, String action) {
        LinearLayout card = baseCard();
        TextView ico = label(symbol, 44, Color.WHITE);
        ico.setGravity(Gravity.CENTER);
        card.addView(ico, new LinearLayout.LayoutParams(-1,0,1f));
        TextView tx = label(name, 14, Color.WHITE);
        tx.setGravity(Gravity.CENTER);
        tx.setSingleLine(true);
        card.addView(tx, new LinearLayout.LayoutParams(-1, dp(42)));
        card.setOnClickListener(v -> safeStartSettings(action));
        focusBehavior(card, name, "Impostazioni di sistema");
        return card;
    }

    private View createAppCard(AppItem item) {
        LinearLayout card = baseCard();

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(18), dp(16), dp(18), dp(8));
        Bitmap custom = loadCustomIcon(item.pkg);
        if (custom != null) icon.setImageBitmap(custom); else icon.setImageDrawable(item.icon);
        card.addView(icon, new LinearLayout.LayoutParams(-1,0,1f));

        String customName = prefs.getString("label_"+item.pkg, null);
        TextView name = label(customName == null ? item.label : customName, 14, Color.WHITE);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(dp(8),0,dp(8),0);
        card.addView(name, new LinearLayout.LayoutParams(-1, dp(42)));

        card.setOnClickListener(v -> launchProtected(item));
        card.setOnLongClickListener(v -> {
            showAppMenu(item);
            return true;
        });
        focusBehavior(card, name.getText().toString(), categoryDisplay(item.category)+" • tieni premuto per le opzioni");
        return card;
    }

    private LinearLayout baseCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setGravity(Gravity.CENTER);
        card.setBackground(cardDrawable(false));
        card.setElevation(dp(5));
        return card;
    }

    private LinearLayout.LayoutParams appCardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardWidth(), cardHeight());
        lp.setMargins(0,0,dp(16),0);
        return lp;
    }

    private void focusBehavior(View view, String title, String subtitle) {
        view.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f).setDuration(130).start();
            v.setBackground(cardDrawable(focused));
            if (focused && focusTitle != null) {
                focusTitle.setText(title);
                focusSubtitle.setText(subtitle);
            }
        });
    }

    private GradientDrawable cardDrawable(boolean focused) {
        int theme = prefs.getInt("theme", 0);
        int opacity = prefs.getInt("opacity", 78);
        int alpha = Math.max(50, Math.min(255, (int)(255 * (opacity/100f))));
        int base;
        if (theme == 1) base = Color.rgb(40, 49, 61);
        else if (theme == 2) base = Color.rgb(30, 37, 45);
        else base = Color.rgb(24, 29, 35);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)));
        g.setCornerRadius(dp(18));
        if (focused) g.setStroke(dp(3), accentColor());
        else g.setStroke(dp(1), 0x334F5B68);
        return g;
    }

    private int accentColor() {
        int theme = prefs.getInt("theme", 0);
        if (theme == 1) return 0xFFE9EDF3;
        if (theme == 2) return 0xFF9FD3FF;
        return 0xFFF4F4F4;
    }

    private int cardWidth() {
        int s = prefs.getInt("card_size", 1);
        if (s == 0) return dp(150);
        if (s == 2) return dp(230);
        return dp(190);
    }

    private int cardHeight() {
        int s = prefs.getInt("card_size", 1);
        if (s == 0) return dp(150);
        if (s == 2) return dp(210);
        return dp(178);
    }

    private void applyBackground() {
        String uriString = prefs.getString("wallpaper_uri", null);
        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setBackground(defaultGradient());

        if (uriString != null) {
            try {
                Uri uri = Uri.parse(uriString);
                wallpaperBitmap = decodeUriSampled(uri, 1920, 1080);
                if (wallpaperBitmap != null) bg.setImageBitmap(wallpaperBitmap);
            } catch(Exception ignored) {}
        }

        root.addView(bg, new FrameLayout.LayoutParams(-1,-1));

        View dim = new View(this);
        int level = prefs.getInt("dim", 48);
        dim.setBackgroundColor(Color.argb((int)(255*(level/100f)), 0,0,0));
        root.addView(dim, new FrameLayout.LayoutParams(-1,-1));
    }

    private GradientDrawable defaultGradient() {
        int theme = prefs == null ? 0 : prefs.getInt("theme", 0);
        int[] colors;
        if (theme == 1) colors = new int[]{0xFF11151A,0xFF26313B,0xFF11151A};
        else if (theme == 2) colors = new int[]{0xFF07111B,0xFF153148,0xFF0C1720};
        else colors = new int[]{0xFF090C10,0xFF17202A,0xFF090C10};
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        LinkedHashMap<String,ResolveInfo> found = new LinkedHashMap<>();
        Intent lean = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(lean, 0)) found.put(r.activityInfo.packageName, r);
        Intent normal = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(normal, 0)) {
            if (!found.containsKey(r.activityInfo.packageName)) found.put(r.activityInfo.packageName, r);
        }

        Set<String> hidden = getSet("hidden");
        for (ResolveInfo r : found.values()) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg)) continue;
            String label = String.valueOf(r.loadLabel(pm));
            String category = categoryFor(r);
            apps.add(new AppItem(pkg, r.activityInfo.name, label, category, r.loadIcon(pm)));
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

    private String categoryFor(ResolveInfo r) {
        String custom = prefs.getString("cat_"+r.activityInfo.packageName, null);
        if (custom != null) return custom;
        if (Build.VERSION.SDK_INT >= 26) {
            int c = r.activityInfo.applicationInfo.category;
            if (c == ApplicationInfo.CATEGORY_GAME) return "games";
            if (c == ApplicationInfo.CATEGORY_VIDEO || c == ApplicationInfo.CATEGORY_AUDIO) return "media";
            if (c == ApplicationInfo.CATEGORY_PRODUCTIVITY) return "tools";
        }
        String p = r.activityInfo.packageName.toLowerCase(Locale.ROOT);
        String l = String.valueOf(r.loadLabel(getPackageManager())).toLowerCase(Locale.ROOT);
        if (containsAny(p+" "+l, "netflix","youtube","prime","video","disney","plex","kodi","spotify","rai","mediaset","dazn","stream")) return "media";
        if (containsAny(p+" "+l, "game","play games","gaming")) return "games";
        if (containsAny(p+" "+l, "settings","file","browser","manager","utility","store")) return "tools";
        return "other";
    }

    private boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private List<AppItem> filterByCategory(String category) {
        List<AppItem> out = new ArrayList<>();
        for (AppItem a : apps) if (category.equals(a.category)) out.add(a);
        return out;
    }

    private List<AppItem> filterApps(String mode) {
        List<AppItem> out = new ArrayList<>();
        Set<String> fav = getSet("favorites");
        for (AppItem a : apps) if ("favorites".equals(mode) && fav.contains(a.pkg)) out.add(a);
        return out;
    }

    private void launchProtected(AppItem item) {
        if (getSet("protected").contains(item.pkg) && prefs.contains("pin_hash")) {
            askPin(() -> launch(item));
        } else launch(item);
    }

    private void launch(AppItem item) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            i.setComponent(new ComponentName(item.pkg, item.activity));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch(Exception e) {
            Intent i = getPackageManager().getLaunchIntentForPackage(item.pkg);
            if (i != null) startActivity(i);
        }
    }

    private void showAppMenu(AppItem item) {
        Set<String> fav = getSet("favorites");
        Set<String> prot = getSet("protected");
        String[] options = new String[] {
            fav.contains(item.pkg) ? "Rimuovi dai preferiti" : "Aggiungi ai preferiti",
            "Nascondi app",
            "Sposta a sinistra",
            "Sposta a destra",
            "Sposta categoria",
            "Rinomina",
            "Icona personalizzata",
            prot.contains(item.pkg) ? "Rimuovi protezione PIN" : "Proteggi con PIN",
            "Ripristina nome e icona"
        };
        new AlertDialog.Builder(this)
            .setTitle(displayName(item))
            .setItems(options, (d, which) -> {
                switch(which) {
                    case 0: toggleSet("favorites", item.pkg); refresh(); break;
                    case 1: addToSet("hidden", item.pkg); refresh(); break;
                    case 2: movePackage(item.pkg, -1); refresh(); break;
                    case 3: movePackage(item.pkg, 1); refresh(); break;
                    case 4: chooseCategory(item); break;
                    case 5: renameApp(item); break;
                    case 6: chooseCustomIcon(item.pkg); break;
                    case 7: toggleProtection(item.pkg); break;
                    case 8:
                        prefs.edit().remove("label_"+item.pkg).remove("icon_"+item.pkg).apply();
                        refresh(); break;
                }
            }).show();
    }

    private String displayName(AppItem a) {
        return prefs.getString("label_"+a.pkg, a.label);
    }

    private void chooseCategory(AppItem item) {
        String[] names = {"Streaming & Media","Giochi","Strumenti","Altre app","Automatico"};
        String[] ids = {"media","games","tools","other",null};
        new AlertDialog.Builder(this).setTitle("Categoria")
            .setItems(names, (d,w) -> {
                SharedPreferences.Editor e = prefs.edit();
                if (ids[w] == null) e.remove("cat_"+item.pkg); else e.putString("cat_"+item.pkg, ids[w]);
                e.apply(); refresh();
            }).show();
    }

    private void renameApp(AppItem item) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(displayName(item));
        input.selectAll();
        new AlertDialog.Builder(this).setTitle("Rinomina app").setView(input)
            .setPositiveButton("Salva", (d,w) -> {
                String s = input.getText().toString().trim();
                if (s.isEmpty()) prefs.edit().remove("label_"+item.pkg).apply();
                else prefs.edit().putString("label_"+item.pkg, s).apply();
                refresh();
            }).setNegativeButton("Annulla", null).show();
    }

    private void chooseCustomIcon(String pkg) {
        pendingIconPackage = pkg;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_ICON);
    }

    private void toggleProtection(String pkg) {
        if (!prefs.contains("pin_hash")) {
            setPin(() -> { addToSet("protected", pkg); refresh(); });
        } else {
            toggleSet("protected", pkg); refresh();
        }
    }

    private void movePackage(String pkg, int delta) {
        List<String> order = getOrder();
        int idx = order.indexOf(pkg);
        if (idx < 0) return;
        int ni = Math.max(0, Math.min(order.size()-1, idx+delta));
        if (ni == idx) return;
        Collections.swap(order, idx, ni);
        saveOrder(order);
    }

    private void showSettings() {
        String[] options = {
            "Scegli sfondo",
            "Rimuovi sfondo",
            "Tema",
            "Dimensione schede",
            "Trasparenza schede",
            "Oscuramento sfondo",
            "App da avviare automaticamente",
            "Imposta / cambia PIN",
            "Gestisci app nascoste",
            "Backup impostazioni",
            "Ripristina backup",
            "Impostazioni Android TV",
            "Ripristina layout"
        };
        new AlertDialog.Builder(this).setTitle("Personalizza Salottino TV")
            .setItems(options, (d,w) -> {
                switch(w) {
                    case 0: chooseWallpaper(); break;
                    case 1: prefs.edit().remove("wallpaper_uri").apply(); refresh(); break;
                    case 2: cycleInt("theme", 0, 2, "Tema"); break;
                    case 3: cycleInt("card_size", 0, 2, "Dimensione schede"); break;
                    case 4: chooseNumber("Trasparenza", "opacity", new int[]{55,68,78,88,100}); break;
                    case 5: chooseNumber("Oscuramento sfondo", "dim", new int[]{25,35,48,60,72}); break;
                    case 6: chooseAutoLaunch(); break;
                    case 7: setPin(null); break;
                    case 8: manageHidden(); break;
                    case 9: createBackup(); break;
                    case 10: restoreBackup(); break;
                    case 11: startActivity(new Intent(Settings.ACTION_SETTINGS)); break;
                    case 12: resetLayout(); break;
                }
            }).show();
    }

    private void chooseWallpaper() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_WALLPAPER);
    }

    private void cycleInt(String key, int min, int max, String title) {
        int v = prefs.getInt(key, min);
        v++;
        if (v > max) v = min;
        prefs.edit().putInt(key, v).apply();
        Toast.makeText(this, title+" aggiornato", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void chooseNumber(String title, String key, int[] values) {
        String[] labels = new String[values.length];
        for (int i=0;i<values.length;i++) labels[i] = values[i]+"%";
        new AlertDialog.Builder(this).setTitle(title).setItems(labels, (d,w)->{
            prefs.edit().putInt(key, values[w]).apply(); refresh();
        }).show();
    }

    private void chooseAutoLaunch() {
        final List<AppItem> options = new ArrayList<>(apps);
        String[] names = new String[options.size()+1];
        names[0] = "Nessuna";
        for (int i=0;i<options.size();i++) names[i+1] = displayName(options.get(i));
        new AlertDialog.Builder(this).setTitle("Avvio automatico").setItems(names, (d,w)->{
            if (w==0) prefs.edit().remove("autolaunch_pkg").apply();
            else prefs.edit().putString("autolaunch_pkg", options.get(w-1).pkg).apply();
            Toast.makeText(this, "Impostazione salvata", Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void manageHidden() {
        Set<String> hidden = getSet("hidden");
        if (hidden.isEmpty()) {
            Toast.makeText(this, "Nessuna app nascosta", Toast.LENGTH_SHORT).show();
            return;
        }
        PackageManager pm = getPackageManager();
        final List<String> pkgs = new ArrayList<>(hidden);
        String[] names = new String[pkgs.size()];
        for (int i=0;i<pkgs.size();i++) {
            try { names[i] = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkgs.get(i),0))); }
            catch(Exception e) { names[i] = pkgs.get(i); }
        }
        new AlertDialog.Builder(this).setTitle("Ripristina app nascosta")
            .setItems(names, (d,w)->{ removeFromSet("hidden", pkgs.get(w)); refresh(); }).show();
    }

    private void resetLayout() {
        new AlertDialog.Builder(this).setTitle("Ripristinare il layout?")
            .setMessage("Preferiti, app nascoste, ordine, categorie, nomi e icone personalizzate verranno azzerati. Il PIN e lo sfondo restano invariati.")
            .setPositiveButton("Ripristina", (d,w)->{
                SharedPreferences.Editor e = prefs.edit();
                Map<String,?> all = prefs.getAll();
                for (String k : all.keySet()) {
                    if (k.equals("favorites") || k.equals("hidden") || k.equals("order") ||
                        k.startsWith("cat_") || k.startsWith("label_") || k.startsWith("icon_")) e.remove(k);
                }
                e.apply(); refresh();
            }).setNegativeButton("Annulla", null).show();
    }

    private void maybeAutoLaunch() {
        if (autoLaunchDone) return;
        autoLaunchDone = true;
        String pkg = prefs.getString("autolaunch_pkg", null);
        if (pkg == null) return;
        handler.postDelayed(() -> {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) {
                try { startActivity(i); } catch(Exception ignored) {}
            }
        }, 1200);
    }

    private void setPin(final Runnable after) {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("4 cifre");
        new AlertDialog.Builder(this).setTitle("Imposta PIN").setView(input)
            .setPositiveButton("Salva", (d,w)->{
                String p = input.getText().toString();
                if (p.length() < 4) {
                    Toast.makeText(this, "Usa almeno 4 cifre", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putString("pin_hash", hash(p)).apply();
                if (after != null) after.run();
            }).setNegativeButton("Annulla", null).show();
    }

    private void askPin(final Runnable ok) {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("PIN richiesto").setView(input)
            .setPositiveButton("Apri", (d,w)->{
                if (hash(input.getText().toString()).equals(prefs.getString("pin_hash",""))) ok.run();
                else Toast.makeText(this, "PIN non corretto", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Annulla", null).show();
    }

    private String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (byte x : b) out.append(String.format(Locale.ROOT,"%02x",x));
            return out.toString();
        } catch(Exception e) { return s; }
    }

    private void createBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "salottino-tv-backup.json");
        startActivityForResult(i, REQ_BACKUP);
    }

    private void restoreBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/json");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_RESTORE);
    }

    private void writeBackup(Uri uri) {
        try {
            JSONObject j = new JSONObject();
            for (Map.Entry<String,?> e : prefs.getAll().entrySet()) {
                Object v = e.getValue();
                if (v instanceof Set) {
                    JSONArray a = new JSONArray();
                    for (Object x : (Set<?>)v) a.put(String.valueOf(x));
                    j.put(e.getKey(), a);
                } else j.put(e.getKey(), v);
            }
            OutputStream out = getContentResolver().openOutputStream(uri);
            out.write(j.toString(2).getBytes("UTF-8"));
            out.close();
            Toast.makeText(this, "Backup salvato", Toast.LENGTH_SHORT).show();
        } catch(Exception e) { Toast.makeText(this, "Errore backup", Toast.LENGTH_SHORT).show(); }
    }

    private void readBackup(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            BufferedReader br = new BufferedReader(new InputStreamReader(in,"UTF-8"));
            StringBuilder s = new StringBuilder();
            String line;
            while((line=br.readLine())!=null) s.append(line);
            br.close();
            JSONObject j = new JSONObject(s.toString());
            SharedPreferences.Editor ed = prefs.edit().clear();
            Iterator<String> keys = j.keys();
            while(keys.hasNext()) {
                String k = keys.next();
                Object v = j.get(k);
                if (v instanceof JSONArray) {
                    JSONArray a=(JSONArray)v; Set<String> set=new HashSet<>();
                    for(int i=0;i<a.length();i++) set.add(a.getString(i));
                    ed.putStringSet(k,set);
                } else if (v instanceof Integer) ed.putInt(k,(Integer)v);
                else if (v instanceof Boolean) ed.putBoolean(k,(Boolean)v);
                else if (v instanceof Long) ed.putLong(k,(Long)v);
                else ed.putString(k,String.valueOf(v));
            }
            ed.apply();
            Toast.makeText(this, "Backup ripristinato", Toast.LENGTH_SHORT).show();
            refresh();
        } catch(Exception e) { Toast.makeText(this, "Backup non valido", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req,result,data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_WALLPAPER || req == REQ_ICON) {
            try {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch(Exception ignored) {}
        }
        if (req == REQ_WALLPAPER) {
            prefs.edit().putString("wallpaper_uri", uri.toString()).apply(); refresh();
        } else if (req == REQ_ICON && pendingIconPackage != null) {
            prefs.edit().putString("icon_"+pendingIconPackage, uri.toString()).apply();
            pendingIconPackage = null; refresh();
        } else if (req == REQ_BACKUP) writeBackup(uri);
        else if (req == REQ_RESTORE) readBackup(uri);
    }

    private Bitmap loadCustomIcon(String pkg) {
        String s = prefs.getString("icon_"+pkg, null);
        if (s == null) return null;
        try {
            return decodeUriSampled(Uri.parse(s), 512, 512);
        } catch(Exception e) { return null; }
    }

    private void refresh() {
        loadApps();
        buildHome();
        updateClock();
    }

    private Set<String> getSet(String key) {
        return new HashSet<>(prefs.getStringSet(key, new HashSet<String>()));
    }

    private void addToSet(String key, String value) {
        Set<String> s = getSet(key); s.add(value); prefs.edit().putStringSet(key,s).apply();
    }

    private void removeFromSet(String key, String value) {
        Set<String> s = getSet(key); s.remove(value); prefs.edit().putStringSet(key,s).apply();
    }

    private void toggleSet(String key, String value) {
        Set<String> s = getSet(key);
        if (s.contains(value)) s.remove(value); else s.add(value);
        prefs.edit().putStringSet(key,s).apply();
    }

    private List<String> getOrder() {
        String s = prefs.getString("order","");
        List<String> out = new ArrayList<>();
        if (!s.isEmpty()) out.addAll(Arrays.asList(s.split(",")));
        return out;
    }

    private void saveOrder(List<String> order) {
        StringBuilder s = new StringBuilder();
        for (String p : order) {
            if (s.length()>0) s.append(",");
            s.append(p);
        }
        prefs.edit().putString("order",s.toString()).apply();
    }

    private String categoryDisplay(String id) {
        if ("media".equals(id)) return "Streaming & Media";
        if ("games".equals(id)) return "Giochi";
        if ("tools".equals(id)) return "Strumenti";
        return "App";
    }

    private TextView label(String text, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        return v;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setFocusable(true);
        b.setTextColor(Color.WHITE);
        b.setBackground(cardDrawable(false));
        b.setOnFocusChangeListener((v,f)-> {
            v.animate().scaleX(f?1.06f:1f).scaleY(f?1.06f:1f).setDuration(120).start();
            v.setBackground(cardDrawable(f));
        });
        return b;
    }

    private void updateClock() {
        handler.removeCallbacks(clockRunnable);
        renderClock();
        handler.postDelayed(clockRunnable, 30000);
    }

    private void renderClock() {
        if (clock != null) clock.setText(new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date()));
        if (date != null) date.setText(new SimpleDateFormat("EEE d MMM", Locale.ITALY).format(new Date()));
    }

    private void safeStartSettings(String action) {
        try {
            Intent i = new Intent(action);
            if (i.resolveActivity(getPackageManager()) != null) startActivity(i);
            else startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch(Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch(Exception ignored) {}
        }
    }

    private Bitmap decodeUriSampled(Uri uri, int reqW, int reqH) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream in1 = getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(in1, null, bounds);
        if (in1 != null) in1.close();

        int sample = 1;
        while (bounds.outWidth / sample > reqW * 2 || bounds.outHeight / sample > reqH * 2) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = Math.max(1, sample);
        InputStream in2 = getContentResolver().openInputStream(uri);
        Bitmap b = BitmapFactory.decodeStream(in2, null, opts);
        if (in2 != null) in2.close();
        return b;
    }

    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }
}
