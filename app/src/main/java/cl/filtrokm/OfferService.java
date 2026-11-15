package cl.filtrokm;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

/** Lee la pantalla de Uber/DiDi/inDrive y muestra la tarjeta flotante. */
public class OfferService extends AccessibilityService {

    static final long THROTTLE_MS = 350, HIDE_AFTER_MS = 1500, MAX_SHOW_MS = 60000;

    WindowManager wm;
    View card;
    WindowManager.LayoutParams lp;
    final Handler h = new Handler(Looper.getMainLooper());
    long lastRun;
    boolean pending;
    String lastSig = "";
    String dismissedSig = "";
    Prefs prefs;

    final Runnable analyzeR = new Runnable() { public void run() { pending = false; analyze(); } };
    final Runnable hideR = new Runnable() { public void run() { hide(); } };

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = Prefs.load(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null) return;
        CharSequence p = e.getPackageName();
        String pkg = p == null ? null : p.toString();
        if (getPackageName().equals(pkg)) return;
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (prefs == null) prefs = Prefs.load(this);
            if (!prefs.isTarget(pkg)) return;
        }
        if (pending) return;
        pending = true;
        long wait = Math.max(0, THROTTLE_MS - (SystemClock.uptimeMillis() - lastRun));
        h.postDelayed(analyzeR, wait);
    }

    void analyze() {
        lastRun = SystemClock.uptimeMillis();
        prefs = Prefs.load(this);
        List<Parser.Item> items = new ArrayList<Parser.Item>();
        String pkg = null;
        try {
            List<AccessibilityWindowInfo> ws = getWindows();
            for (AccessibilityWindowInfo w : ws) {
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null) continue;
                CharSequence rp = root.getPackageName();
                if (rp != null && prefs.isTarget(rp.toString())) {
                    pkg = rp.toString();
                    collect(root, items, 0);
                }
            }
        } catch (Exception ignored) { }
        if (pkg == null) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null && prefs.isTarget(root.getPackageName().toString())) {
                pkg = root.getPackageName().toString();
                collect(root, items, 0);
            }
        }

        if (pkg == null) { scheduleHide(); return; }

        Parser.Offer o = Parser.parse(items);
        o.app = Parser.appName(pkg);
        saveDiag(pkg, items, o);

        if (o.valid && o.sig().equals(dismissedSig)) return; // la cerró a mano
        if (o.valid) {
            h.removeCallbacks(hideR);
            if (card == null || !o.sig().equals(lastSig)) show(o);
            h.postDelayed(hideR, MAX_SHOW_MS);
        } else {
            scheduleHide();
        }
    }

    void scheduleHide() {
        if (card == null) return;
        h.removeCallbacks(hideR);
        h.postDelayed(hideR, HIDE_AFTER_MS);
    }

    void collect(AccessibilityNodeInfo n, List<Parser.Item> out, int depth) {
        if (n == null || depth > 45 || out.size() > 700) return;
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        CharSequence t = n.getText();
        if (t != null && t.length() > 0) out.add(new Parser.Item(t.toString(), r.height()));
        CharSequence d = n.getContentDescription();
        if (d != null && d.length() > 0 && (t == null || !d.toString().equals(t.toString())))
            out.add(new Parser.Item(d.toString(), r.height()));
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo ch = n.getChild(i);
            if (ch != null) collect(ch, out, depth + 1);
        }
    }

    long lastDiag;
    void saveDiag(String pkg, List<Parser.Item> items, Parser.Offer o) {
        long now = SystemClock.uptimeMillis();
        if (!o.valid && now - lastDiag < 3000) return; // no escribir a cada rato
        lastDiag = now;
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(pkg).append(o.valid ? " (oferta detectada)" : " (sin oferta)").append('\n');
        for (Parser.Item it : items) {
            if (sb.length() > 6000) break;
            sb.append(it.h).append('|').append(it.text.replace('\n', ' ')).append('\n');
        }
        String key = o.valid ? "diagOk" : "diag";
        Prefs.sp(this).edit().putString(key, sb.toString()).apply();
    }

    void show(Parser.Offer o) {
        lastSig = o.sig();
        if (card != null) { try { wm.removeView(card); } catch (Exception ignored) { } }
        card = CardBuilder.build(this, o, prefs, new View.OnClickListener() {
            public void onClick(View v) { dismissedSig = lastSig; hide(); }
        });
        int sw = getResources().getDisplayMetrics().widthPixels;
        int w = Math.min(sw - CardBuilder.dp(this, 20), CardBuilder.dp(this, 440));
        lp = new WindowManager.LayoutParams(w, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = prefs.overlayY;
        card.setOnTouchListener(new View.OnTouchListener() {
            float startRaw; int startY;
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN: startRaw = ev.getRawY(); startY = lp.y; return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.y = Math.max(0, startY + (int) (ev.getRawY() - startRaw));
                        try { wm.updateViewLayout(card, lp); } catch (Exception ignored) { }
                        return true;
                    case MotionEvent.ACTION_UP:
                        Prefs.sp(OfferService.this).edit().putInt("overlayY", lp.y).apply();
                        return true;
                }
                return false;
            }
        });
        try { wm.addView(card, lp); } catch (Exception e) { card = null; }
    }

    void hide() {
        h.removeCallbacks(hideR);
        if (card != null) {
            try { wm.removeView(card); } catch (Exception ignored) { }
            card = null;
        }
        lastSig = "";
    }

    @Override
    public void onInterrupt() { hide(); }

    @Override
    public void onDestroy() { hide(); super.onDestroy(); }
}
