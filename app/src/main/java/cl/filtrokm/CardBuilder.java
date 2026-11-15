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
        // ganancia final = precio - comisión - retención (solo Uber/DiDi) - combustible
        double com = o.price * p.commission(o.app, o.ponTuPrecio) / 100.0;
        double ret = (o.price - com) * p.retention(o.app) / 100.0;
        double fuel = p.costPerKm * o.km;
        double net = o.price - com - ret - fuel;
        double netKm = o.km > 0 ? net / o.km : 0;
        double netH = o.min > 0 ? net / (o.min / 60.0) : 0;

        boolean okKm = netKm >= p.minPerKm;
        boolean hasH = o.min > 0;
        boolean okH = !hasH || netH >= p.minPerHour;
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
        TextView chip = tv(c, (o.app.isEmpty() ? "Viaje" : o.app) + (o.ponTuPrecio ? " · Pon Tu Precio" : ""), 13, true);
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
        TextView lt = tv(c, "GANAS ", 13, false);
        TextView tot = tv(c, Parser.clp(net), 30, true);
        TextView chk = tv(c, fails.isEmpty() ? "  ✓✓" : "  ✗", 22, true);
        r2.addView(lt);
        r2.addView(tot);
        r2.addView(chk);
        root.addView(r2);

        StringBuilder det = new StringBuilder("Tarifa " + Parser.clp(o.price));
        if (com > 0) det.append(" · comisión −").append(Parser.clp(com));
        if (ret > 0) det.append(" · retención −").append(Parser.clp(ret));
        if (fuel > 0) det.append(" · bencina −").append(Parser.clp(fuel));
        TextView dt = tv(c, det.toString(), 12, false);
        dt.setAlpha(0.9f);
        root.addView(dt);

        String status = fails.isEmpty() ? "TODOS LOS FILTROS CUMPLEN" : "NO CUMPLE: " + join(fails);
        TextView st = tv(c, status, 13, false);
        st.setPadding(0, 0, 0, dp(c, 6));
        root.addView(st);

        // fila 3: recuadros
        LinearLayout r3 = new LinearLayout(c);
        r3.addView(box(c, (okKm ? "✓ " : "✗ ") + Parser.clp(netKm) + "/km",
                "Mín. " + Parser.clp(p.minPerKm) + "\nBruto " + Parser.clp(o.perKm()), okKm), boxLp(c, false));
        if (hasH) {
            r3.addView(box(c, (okH ? "✓ " : "✗ ") + Parser.clp(netH) + "/h",
                    "Mín. " + Parser.clp(p.minPerHour) + "\nBruto " + Parser.clp(o.perHour()), okH), boxLp(c, false));
        }
        String sub = Parser.km(o.km) + " km total";
        if (o.pickupKm >= 0) sub += "\nRecogida " + Parser.km(o.pickupKm) + " km";
        r3.addView(box(c, "⏱ " + (hasH ? Parser.mins(o.min) : "-- min"), sub, okPick), boxLp(c, true));
        root.addView(r3);

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
