package cl.filtrokm;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Arma la tarjeta flotante con los colores según los filtros. */
public class CardBuilder {

    static final int GREEN = 0xFF2E7D32, ORANGE = 0xFFEF6C00, RED = 0xFFC62828;
    static final int BOX_OK = 0x33FFFFFF, BOX_BAD = 0xFF8E0000;

    public static View build(Context c, Parser.Offer o, Prefs p, View.OnClickListener onClose) {
        boolean okKm = o.perKm() >= p.minPerKm;
        boolean hasH = o.min > 0;
        boolean okH = !hasH || o.perHour() >= p.minPerHour;
        boolean usePick = p.maxPickupKm > 0 && o.pickupKm >= 0;
        boolean okPick = !usePick || o.pickupKm <= p.maxPickupKm;

        List<String> fails = new ArrayList<String>();
        if (!okKm) fails.add("$/km");
        if (!okH) fails.add("$/h");
        if (!okPick) fails.add("recogida");
        int total = 1 + (hasH ? 1 : 0) + (usePick ? 1 : 0);
        int bg = fails.isEmpty() ? GREEN : (fails.size() >= total ? RED : ORANGE);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(c, 12);
        root.setPadding(pad, dp(c, 8), pad, dp(c, 10));
        root.setBackground(round(c, bg, 18, 0));
        root.setElevation(dp(c, 6));

        // fila 1: app + cerrar
        LinearLayout r1 = new LinearLayout(c);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        TextView chip = tv(c, o.app.isEmpty() ? "Viaje" : o.app, 13, true);
        chip.setPadding(dp(c, 10), dp(c, 2), dp(c, 10), dp(c, 2));
        chip.setBackground(round(c, 0x33FFFFFF, 20, 0));
        r1.addView(chip);
        View sp = new View(c);
        r1.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        if (onClose != null) {
            TextView x = tv(c, "✕", 20, true);
            x.setPadding(dp(c, 12), 0, dp(c, 4), 0);
            x.setOnClickListener(onClose);
            r1.addView(x);
        }
        root.addView(r1);

        // fila 2: total
        LinearLayout r2 = new LinearLayout(c);
        r2.setGravity(Gravity.BOTTOM);
        TextView lt = tv(c, "TOTAL ", 13, false);
        TextView tot = tv(c, Parser.clp(o.price), 30, true);
        TextView chk = tv(c, fails.isEmpty() ? "  ✓✓" : "  ✗", 22, true);
        r2.addView(lt);
        r2.addView(tot);
        r2.addView(chk);
        root.addView(r2);

        String status = fails.isEmpty() ? "TODOS LOS FILTROS CUMPLEN" : "NO CUMPLE: " + join(fails);
        TextView st = tv(c, status, 13, false);
        st.setPadding(0, 0, 0, dp(c, 6));
        root.addView(st);

        // fila 3: recuadros
        LinearLayout r3 = new LinearLayout(c);
        r3.addView(box(c, (okKm ? "✓ " : "✗ ") + Parser.clp(o.perKm()) + "/km",
                "Mín. " + Parser.clp(p.minPerKm), okKm), boxLp(c, false));
        if (hasH) {
            r3.addView(box(c, (okH ? "✓ " : "✗ ") + Parser.clp(o.perHour()) + "/h",
                    "Mín. " + Parser.clp(p.minPerHour), okH), boxLp(c, false));
        }
        String sub = Parser.km(o.km) + " km total";
        if (o.pickupKm >= 0) sub += "\nRecogida " + Parser.km(o.pickupKm) + " km";
        r3.addView(box(c, "⏱ " + (hasH ? Parser.mins(o.min) : "-- min"), sub, okPick), boxLp(c, true));
        root.addView(r3);

        if (p.costPerKm > 0) {
            double fuel = p.costPerKm * o.km;
            TextView neto = tv(c, "Neto " + Parser.clp(o.price - fuel) + "  ·  combustible " + Parser.clp(fuel), 13, true);
            neto.setPadding(0, dp(c, 6), 0, 0);
            root.addView(neto);
        }
        return root;
    }

    static LinearLayout box(Context c, String big, String small, boolean ok) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(c, 8), dp(c, 6), dp(c, 6), dp(c, 6));
        b.setBackground(round(c, ok ? BOX_OK : BOX_BAD, 10, 0x55FFFFFF));
        TextView t1 = tv(c, big, 17, true);
        t1.setSingleLine(true);
        TextView t2 = tv(c, small, 12, false);
        t2.setAlpha(0.9f);
        b.addView(t1);
        b.addView(t2);
        return b;
    }

    static LinearLayout.LayoutParams boxLp(Context c, boolean last) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        if (!last) lp.rightMargin = dp(c, 6);
        return lp;
    }

    static TextView tv(Context c, String s, int sp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static GradientDrawable round(Context c, int color, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(", "); sb.append(l.get(i)); }
        return sb.toString();
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
