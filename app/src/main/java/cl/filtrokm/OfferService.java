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

    static OfferService instance;
    View bubble;
    WindowManager.LayoutParams blp;
    boolean paused;

    final Runnable analyzeR = new Runnable() { public void run() { pending = false; analyze(); } };
    final Runnable hideR = new Runnable() { public void run() { hide(); } };

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = Prefs.load(this);
        instance = this;
        paused = Prefs.sp(this).getBoolean("paused", false);
        if (!Prefs.sp(this).getBoolean("off", false)) showBubble();
        else paused = true;
    }

    /** Encender (burbuja + lectura) o apagar todo. */
    public void setOn(boolean on) {
        paused = !on;
        Prefs.sp(this).edit().putBoolean("off", !on).putBoolean("paused", !on).apply();
        if (on) { showBubble(); paintBubble(); }
        else {
            hide();
            hideMenu();
            removeTarget();
            if (bubble != null) { try { wm.removeView(bubble); } catch (Exception ignored) { } bubble = null; }
        }
    }

    // zona "✕" para cerrar arrastrando
    View target;
    void showTarget() {
        if (target != null) return;
        android.widget.TextView t = new android.widget.TextView(this);
        t.setText("✕");
        t.setTextSize(26);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(0xCC000000);
        g.setStroke(CardBuilder.dp(this, 2), 0xFFFFFFFF);
        t.setBackground(g);
        int size = CardBuilder.dp(this, 64);
        WindowManager.LayoutParams tl = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        tl.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        tl.y = CardBuilder.dp(this, 70);
        target = t;
        try { wm.addView(target, tl); } catch (Exception e) { target = null; }
    }
    void removeTarget() {
        if (target != null) { try { wm.removeView(target); } catch (Exception ignored) { } target = null; }
    }
    boolean overTarget(float rawX, float rawY) {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float cx = dm.widthPixels / 2f;
        float cy = dm.heightPixels - CardBuilder.dp(this, 102);
        float r = CardBuilder.dp(this, 70);
        return Math.abs(rawX - cx) < r && Math.abs(rawY - cy) < r;
    }

    // ---------- burbuja flotante: muestra que el lector está prendido ----------
    void showBubble() {
        if (bubble != null) return;
        final android.widget.TextView b = new android.widget.TextView(this);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(0xFFFFFFFF);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setTextSize(13);
        b.setElevation(CardBuilder.dp(this, 6));
        bubble = b;
        paintBubble();
        int size = CardBuilder.dp(this, 54);
        blp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        blp.gravity = Gravity.TOP | Gravity.START;
        blp.x = Prefs.sp(this).getInt("bubbleX", 0);
        blp.y = Prefs.sp(this).getInt("bubbleY", CardBuilder.dp(this, 260));
        final int slop = CardBuilder.dp(this, 14);
        b.setOnTouchListener(new View.OnTouchListener() {
            float rx, ry; int sx, sy; boolean moved; long downAt;
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        rx = ev.getRawX(); ry = ev.getRawY(); sx = blp.x; sy = blp.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = ev.getRawX() - rx, dy = ev.getRawY() - ry;
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop) { moved = true; hideMenu(); }
                        if (moved) {
                            showTarget();
                            blp.x = Math.max(0, sx + (int) dx);
                            blp.y = Math.max(0, sy + (int) dy);
                            try { wm.updateViewLayout(bubble, blp); } catch (Exception ignored) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        removeTarget();
                        if (moved && overTarget(ev.getRawX(), ev.getRawY())) {
                            setOn(false);
                            android.widget.Toast.makeText(OfferService.this, "FiltroKM apagado. Ábrelo para encender.", android.widget.Toast.LENGTH_LONG).show();
                            return true;
                        }
                        if (!moved) { if (menu != null) hideMenu(); else showMenu(); }
                        else Prefs.sp(OfferService.this).edit().putInt("bubbleX", blp.x).putInt("bubbleY", blp.y).apply();
                        return true;
                }
                return false;
            }
        });
        try { wm.addView(bubble, blp); } catch (Exception e) { bubble = null; }
    }

    void paintBubble() {
        if (bubble == null) return;
        android.widget.TextView b = (android.widget.TextView) bubble;
        b.setText(paused ? "OFF" : "$/km");
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(paused ? 0xDD757575 : 0xEE2E7D32);
        g.setStroke(CardBuilder.dp(this, 2), 0xCCFFFFFF);
        b.setBackground(g);
    }

    void togglePause() {
        paused = !paused;
        Prefs.sp(this).edit().putBoolean("paused", paused).apply();
        if (paused) hide();
        paintBubble();
        android.widget.Toast.makeText(this, paused ? "FiltroKM en pausa" : "FiltroKM leyendo ofertas", android.widget.Toast.LENGTH_SHORT).show();
    }

    void openApp() {
        hideMenu();
        android.content.Intent i = new android.content.Intent(this, MainActivity.class);
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            int fl = android.os.Build.VERSION.SDK_INT >= 23 ? 0x04000000 /* FLAG_IMMUTABLE */ : 0;
            android.app.PendingIntent.getActivity(this, 7, i, fl | android.app.PendingIntent.FLAG_UPDATE_CURRENT).send();
        } catch (Exception e) {
            try { startActivity(i); } catch (Exception ignored) { }
        }
    }

    // ---------- menú de la burbuja ----------
    View menu;
    void showMenu() {
        if (menu != null) return;
        android.widget.LinearLayout m = new android.widget.LinearLayout(this);
        m.setOrientation(android.widget.LinearLayout.VERTICAL);
        int p = CardBuilder.dp(this, 6);
        m.setPadding(p, p, p, p);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xF0212121);
        bg.setCornerRadius(CardBuilder.dp(this, 14));
        m.setBackground(bg);
        m.setElevation(CardBuilder.dp(this, 8));
        m.addView(menuItem("📱  Abrir FiltroKM", new Runnable() { public void run() { openApp(); } }));
        m.addView(menuItem(paused ? "▶  Reanudar" : "⏸  Pausar", new Runnable() { public void run() { hideMenu(); togglePause(); } }));
        m.addView(menuItem("⏻  Apagar", new Runnable() { public void run() {
            hideMenu(); setOn(false);
            android.widget.Toast.makeText(OfferService.this, "FiltroKM apagado. Ábrelo para encender.", android.widget.Toast.LENGTH_LONG).show();
        } }));
        WindowManager.LayoutParams ml = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        ml.gravity = Gravity.TOP | Gravity.START;
        int sw = getResources().getDisplayMetrics().widthPixels;
        int bsize = CardBuilder.dp(this, 54);
        boolean left = blp.x < sw / 2;
        ml.x = left ? blp.x + bsize + CardBuilder.dp(this, 6) : Math.max(0, blp.x - CardBuilder.dp(this, 200));
        ml.y = blp.y;
        m.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_OUTSIDE) { hideMenu(); return true; }
                return false;
            }
        });
        menu = m;
        try { wm.addView(menu, ml); } catch (Exception e) { menu = null; }
        h.removeCallbacks(hideMenuR);
        h.postDelayed(hideMenuR, 6000);
    }
    final Runnable hideMenuR = new Runnable() { public void run() { hideMenu(); } };
    void hideMenu() {
        h.removeCallbacks(hideMenuR);
        if (menu != null) { try { wm.removeView(menu); } catch (Exception ignored) { } menu = null; }
    }
    android.widget.TextView menuItem(String label, final Runnable action) {
        android.widget.TextView t = new android.widget.TextView(this);
        t.setText(label);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(17);
        int ph = CardBuilder.dp(this, 16), pv = CardBuilder.dp(this, 12);
        t.setPadding(ph, pv, ph * 2, pv);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { action.run(); } });
        return t;
    }

    /** Muestra una oferta de ejemplo para comprobar que la tarjeta flota. */
    public void showDemo() {
        List<Parser.Item> items = new ArrayList<Parser.Item>();
        for (String l : MainActivity.SAMPLE.split("\n")) items.add(new Parser.Item(l, 0));
        Parser.Offer o = Parser.parse(items);
        o.app = "Uber";
        prefs = Prefs.load(this);
        dismissedSig = "";
        show(o);
        h.removeCallbacks(hideR);
        h.postDelayed(hideR, 8000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null) return;
        CharSequence p = e.getPackageName();
        String pkg = p == null ? null : p.toString();
        if (getPackageName().equals(pkg)) return;
        if (paused) return;
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
    public void onDestroy() {
        hide();
        hideMenu();
        removeTarget();
        if (bubble != null) { try { wm.removeView(bubble); } catch (Exception ignored) { } bubble = null; }
        instance = null;
        super.onDestroy();
    }
}
