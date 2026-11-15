package cl.filtrokm;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee los textos visibles de la oferta y saca precio, km y minutos.
 * Formato Uber: "CLP18,904", "3 min (0.6 km)", "38 min (13.6 km)".
 * DiDi / inDrive: se suman todos los "X km" y "X min" que aparezcan.
 */
public class Parser {

    public static class Item {
        public String text;
        public int h; // alto en pantalla (sirve para elegir el precio, que es el texto más grande)
        public Item(String t, int h) { this.text = t; this.h = h; }
    }

    public static class Offer {
        public String app = "";
        public long price;
        public double km;
        public double min;
        public double pickupKm = -1;
        public double pickupMin = -1;
        public boolean valid;

        public double perKm() { return km > 0 ? price / km : 0; }
        public double perHour() { return min > 0 ? price / (min / 60.0) : 0; }
        public String sig() { return price + "|" + Math.round(km * 10) + "|" + Math.round(min); }
    }

    // grupo1 = "+" (bono incluido, se ignora) · grupo2 = monto · grupo3 = "/km" "/h" (se ignora)
    static final Pattern MONEY = Pattern.compile(
            "(\\+\\s?)?(?:CLP|\\$)\\s?(\\d{1,3}(?:[.,\\u00A0\\u202F ]\\d{3})+(?:[.,]\\d{2})?|\\d+(?:[.,]\\d{2})?)(\\s?/\\s?(?:km|h|hr|min))?",
            Pattern.CASE_INSENSITIVE);

    // "1 h 5 min (40 km)" o "3 min (0.6 km)"
    static final Pattern PAIR = Pattern.compile(
            "(?:(\\d+)\\s*h(?:rs?|oras?)?\\s*)?(\\d+)\\s*min\\s*\\(\\s*(\\d+(?:[.,]\\d+)?)\\s*(km|m)\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    static final Pattern KM = Pattern.compile(
            "(?<![\\d.,])(\\d+(?:[.,]\\d+)?)\\s*(km|m)\\b", Pattern.CASE_INSENSITIVE);

    static final Pattern MIN = Pattern.compile(
            "(?<![\\d+])(?:(\\d+)\\s*h(?:rs?|oras?)?\\s*)?(\\d+)\\s*min", Pattern.CASE_INSENSITIVE);

    public static String appName(String pkg) {
        if (pkg == null) return "";
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.contains("uber")) return "Uber";
        if (p.contains("didi")) return "DiDi";
        if (p.contains("indriver") || p.contains("indrive")) return "inDrive";
        return pkg;
    }

    public static Offer parse(List<Item> raw) {
        List<Item> items = dedupe(raw);
        StringBuilder sb = new StringBuilder();
        for (Item it : items) sb.append(it.text).append('\n');
        String all = sb.toString();

        Offer o = new Offer();
        o.price = pickPrice(items, all);

        List<double[]> pairs = new ArrayList<double[]>();
        Matcher m = PAIR.matcher(all);
        while (m.find()) {
            double min = (m.group(1) != null ? Integer.parseInt(m.group(1)) * 60 : 0) + Integer.parseInt(m.group(2));
            double km = num(m.group(3));
            if (m.group(4).equalsIgnoreCase("m")) km /= 1000.0;
            pairs.add(new double[]{min, km});
        }

        if (!pairs.isEmpty()) {
            for (double[] p : pairs) { o.min += p[0]; o.km += p[1]; }
            if (pairs.size() >= 2) { o.pickupMin = pairs.get(0)[0]; o.pickupKm = pairs.get(0)[1]; }
        } else {
            List<Double> kms = new ArrayList<Double>();
            Matcher k = KM.matcher(all);
            while (k.find()) {
                double v = num(k.group(1));
                if (k.group(2).equalsIgnoreCase("m")) v /= 1000.0;
                kms.add(v);
                o.km += v;
            }
            if (kms.size() >= 2) o.pickupKm = kms.get(0);
            Matcher t = MIN.matcher(all);
            boolean firstMin = true;
            while (t.find()) {
                double v = (t.group(1) != null ? Integer.parseInt(t.group(1)) * 60 : 0) + Integer.parseInt(t.group(2));
                o.min += v;
                if (firstMin && kms.size() >= 2) { o.pickupMin = v; }
                firstMin = false;
            }
        }
        o.valid = o.price > 0 && o.km > 0.05;
        return o;
    }

    /** Quita textos repetidos y contenedores que solo repiten el texto de sus hijos. */
    static List<Item> dedupe(List<Item> raw) {
        LinkedHashMap<String, Item> uniq = new LinkedHashMap<String, Item>();
        for (Item it : raw) {
            if (it.text == null) continue;
            String t = it.text.trim();
            if (t.isEmpty()) continue;
            Item prev = uniq.get(t);
            if (prev == null) uniq.put(t, new Item(t, it.h));
            else if (it.h > prev.h) prev.h = it.h;
        }
        List<Item> list = new ArrayList<Item>(uniq.values());
        List<Item> out = new ArrayList<Item>();
        for (Item a : list) {
            int contained = 0;
            if (a.text.length() >= 12) {
                for (Item b : list) {
                    if (b != a && b.text.length() >= 3 && b.text.length() < a.text.length() && a.text.contains(b.text)) {
                        contained++;
                        if (contained >= 2) break;
                    }
                }
            }
            if (contained < 2) out.add(a);
        }
        return out;
    }

    /** El precio es el monto más grande en pantalla (no "+bono", no "$/km"). */
    static long pickPrice(List<Item> items, String all) {
        long best = 0;
        int bestH = -1;
        for (Item it : items) {
            if (it.text.length() > 40) continue;
            Matcher mm = MONEY.matcher(it.text);
            while (mm.find()) {
                if (mm.group(1) != null || mm.group(3) != null) continue;
                long v = money(mm.group(2));
                if (v < 300) continue;
                if (it.h > bestH) { best = v; bestH = it.h; }
            }
        }
        if (best == 0) {
            Matcher mm = MONEY.matcher(all);
            while (mm.find()) {
                if (mm.group(1) != null || mm.group(3) != null) continue;
                long v = money(mm.group(2));
                if (v >= 300) return v;
            }
        }
        return best;
    }

    static long money(String s) {
        s = s.trim();
        if (s.matches(".*[.,]\\d{2}$")) s = s.substring(0, s.length() - 3); // centavos
        String d = s.replaceAll("\\D", "");
        if (d.isEmpty()) return 0;
        try { return Long.parseLong(d); } catch (Exception e) { return 0; }
    }

    static double num(String s) {
        try { return Double.parseDouble(s.replace(',', '.')); } catch (Exception e) { return 0; }
    }

    // ---------- formato chileno ----------
    static final NumberFormat CL = NumberFormat.getIntegerInstance(new Locale("es", "CL"));

    public static String clp(double v) { return "$" + CL.format(Math.round(v)).replace(',', '.'); }

    public static String km(double v) { return String.format(Locale.ROOT, "%.1f", v).replace('.', ','); }

    public static String mins(double v) {
        int t = (int) Math.round(v);
        if (t >= 60) return (t / 60) + " h " + (t % 60) + " min";
        return t + " min";
    }
}
