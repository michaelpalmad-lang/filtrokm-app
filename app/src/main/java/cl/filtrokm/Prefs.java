package cl.filtrokm;

import android.content.Context;
import android.content.SharedPreferences;

/** Configuración del usuario (filtros y apps a vigilar). */
public class Prefs {
    public static final String DEFAULT_PKGS =
            "com.ubercab.driver\n" +
            "com.didiglobal.driver\n" +
            "com.didi.global.driver\n" +
            "sinet.startup.inDriver";

    public double minPerKm;
    public double minPerHour;
    public double maxPickupKm;   // 0 = sin filtro
    public double costPerKm;     // 0 = no calcula neto
    public double comUber, comDidi, comIndrive; // % comisión a descontar del precio mostrado
    public double retencion;                    // % retención boleta honorarios (2026: 15,25)
    public String pkgs;
    public int overlayY;

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("filtrokm", Context.MODE_PRIVATE);
    }

    public static Prefs load(Context c) {
        SharedPreferences p = sp(c);
        Prefs r = new Prefs();
        r.minPerKm = getD(p, "minKm", 600);
        r.minPerHour = getD(p, "minHora", 13000);
        r.maxPickupKm = getD(p, "maxRecogida", 0);
        r.costPerKm = getD(p, "costoKm", 0);
        r.comUber = getD(p, "comUber", 0);
        r.comDidi = getD(p, "comDidi", 0);
        r.comIndrive = getD(p, "comIndrive", 12);
        r.retencion = getD(p, "retencion", 15.25);
        r.pkgs = p.getString("pkgs", DEFAULT_PKGS);
        r.overlayY = p.getInt("overlayY", 80);
        return r;
    }

    static double getD(SharedPreferences p, String k, double def) {
        try {
            return Double.parseDouble(p.getString(k, String.valueOf(def)).replace(',', '.'));
        } catch (Exception e) {
            return def;
        }
    }

    public double commission(String app) {
        if ("Uber".equals(app)) return comUber;
        if ("DiDi".equals(app)) return comDidi;
        if ("inDrive".equals(app)) return comIndrive;
        return 0;
    }

    public boolean isTarget(String pkg) {
        if (pkg == null) return false;
        String lp = pkg.toLowerCase();
        if (lp.contains("didi") && lp.contains("driver")) return true;
        for (String s : pkgs.split("[\\n,; ]+")) {
            s = s.trim();
            if (s.length() > 2 && pkg.equalsIgnoreCase(s)) return true;
        }
        return false;
    }
}
