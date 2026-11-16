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
        if (o.partial()) return buildPartial(c, o, p, onClose);
        // ganancia final = precio - comisión - retención (solo Uber/DiDi) - combustible
        double com = o.price * comPct(o, p) / 100.0;
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

        boolean ok = okKm && okH && okPick;
        int bg = ok ? GREEN : (okKm ? ORANGE : RED);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(c, 14);
        root.setPadding(pad, dp(c, 8), dp(c, 8), dp(c, 12));
        root.setBackground(round(c, bg, 18, 0));
        root.setElevation(dp(c, 6));

        // fila principal: $ por km (lo que importa) + cerrar
        LinearLayout r1 = new LinearLayout(c);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        TextView big = tv(c, (okKm ? "✓ " : "✗ ") + Parser.clp(netKm) + "/km", 38, true);
        r1.addView(big, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (onClose != null) {
            TextView x = tv(c, "✕", 20, true);
            x.setPadding(dp(c, 12), 0, dp(c, 6), dp(c, 12));
            x.setOnClickListener(onClose);
            r1.addView(x);
        }
        root.addView(r1);

        // fila resumen
        StringBuilder sb = new StringBuilder("Ganas " + Parser.clp(net));
        if (hasH) sb.append("  ·  ").append(Parser.clp(netH)).append("/h");
        TextView l2 = tv(c, sb.toString(), 20, true);
        root.addView(l2);

        StringBuilder sb3 = new StringBuilder(Parser.km(o.km) + " km");
        if (o.pickupKm >= 0) sb3.append(" (").append(Parser.km(o.pickupKm)).append(" recogida)");
        if (hasH) sb3.append("  ·  ").append(Parser.mins(o.min));
        if ("Efectivo".equals(o.pago)) sb3.append("  ·  💵 Efectivo");
        else if ("Tarjeta".equals(o.pago)) sb3.append("  ·  💳 Tarjeta");
        TextView l3 = tv(c, sb3.toString(), 15, false);
        l3.setAlpha(0.92f);
        root.addView(l3);

        if (fuel > 0) {
            TextView l4 = tv(c, "⛽ Bencina −" + Parser.clp(fuel) + "  (" + Parser.km(o.km) + " km)", 13, false);
            l4.setAlpha(0.85f);
            root.addView(l4);
        }
        return root;
    }

    /** Falta la distancia/tiempo del viaje: no se da veredicto, tarjeta gris. */
    static View buildPartial(Context c, Parser.Offer o, Prefs p, View.OnClickListener onClose) {
        double com = o.price * comPct(o, p) / 100.0;
        double ret = (o.price - com) * p.retention(o.app) / 100.0;
        double net = o.price - com - ret;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(c, 14), dp(c, 8), dp(c, 8), dp(c, 12));
        root.setBackground(round(c, 0xFF616161, 18, 0));
        root.setElevation(dp(c, 6));
        LinearLayout r1 = new LinearLayout(c);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        TextView big = tv(c, "? $/km", 34, true);
        r1.addView(big, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (onClose != null) {
            TextView x = tv(c, "✕", 20, true);
            x.setPadding(dp(c, 12), 0, dp(c, 6), dp(c, 12));
            x.setOnClickListener(onClose);
            r1.addView(x);
        }
        root.addView(r1);
        root.addView(tv(c, "Ganas " + Parser.clp(net), 20, true));
        TextView l3 = tv(c, "Falta la distancia del viaje (solo veo " + Parser.km(o.km) + " km)", 14, false);
        l3.setAlpha(0.9f);
        root.addView(l3);
        return root;
    }

    /** % a descontar: inDrive usa el % de servicio de la oferta (+ IVA) si aparece. */
    static double comPct(Parser.Offer o, Prefs p) {
        if ("inDrive".equals(o.app) && o.feePct >= 0) return o.feePct * 1.19;
        return p.commission(o.app, o.ponTuPrecio);
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
