package it.salottino.tv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.List;

public class FoyhoAccessibilityService extends AccessibilityService {
    private long lastRedirect = 0L;

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.notificationTimeout = 20;
        setServiceInfo(info);

        if (overrideEnabled() && !paused()) {
            redirectToFoyho();
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("salottino_tv", MODE_PRIVATE);
    }

    private boolean overrideEnabled() {
        return prefs().getBoolean("override_launcher", false);
    }

    private boolean paused() {
        return System.currentTimeMillis() < prefs().getLong("override_pause_until", 0L);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!overrideEnabled() || paused() || event == null) return false;

        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                redirectToFoyho();
            }
            return true;
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!overrideEnabled() || paused() || event == null) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;

        CharSequence packageName = event.getPackageName();
        if (packageName == null) return;

        String pkg = packageName.toString();
        if (pkg.equals(getPackageName()) || shouldIgnore(pkg)) return;

        if (isLauncherPackage(pkg)) {
            redirectToFoyho();
        }
    }

    private boolean shouldIgnore(String pkg) {
        String p = pkg.toLowerCase();
        return p.contains("settings") ||
                p.contains("permissioncontroller") ||
                p.contains("packageinstaller") ||
                p.contains("systemui");
    }

    private boolean isLauncherPackage(String pkg) {
        if ("com.spocky.projengmenu".equals(pkg)) return true;

        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> launchers = getPackageManager().queryIntentActivities(home, 0);
            for (ResolveInfo r : launchers) {
                if (r.activityInfo != null && pkg.equals(r.activityInfo.packageName)) {
                    return true;
                }
            }
        } catch(Exception ignored) {}
        return false;
    }

    private void redirectToFoyho() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRedirect < 250L) return;
        lastRedirect = now;

        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.setComponent(new ComponentName(this, MainActivity.class));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch(Exception ignored) {}
    }

    @Override
    public void onInterrupt() {}
}
