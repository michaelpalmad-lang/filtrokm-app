package cl.filtrokm;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Pantalla de configuración, prueba del analizador y diagnóstico. */
public class MainActivity extends Activity {

    static final String SAMPLE =
            "Comfort\nExclusivo\nCLP18,904\nCLP1,331/km (estimado)\nTarjeta de crédito verificada\n" +
            "Pago con tarjeta\n4.96\n+CLP6,150.00 incluido\n3 min (0.6 km)\n" +
            "Avenida República, Metro República, Santiago Centro\n38 min (13.6 km)\n" +
            "Sta. Elena de Huechuraba 1660, Huechuraba\nViaje largo (35+ min)";

    LinearLayout col;
    TextView status;
    Button power;
    EditText eKm, eHora, ePick, eCost, eComU, eComD, eComDP, eComI, eRetU, eRetD, eRetI, eLitro, eRend, ePkgs, eTest;
    LinearLayout preview;
    TextView diag;
    TextView upd;
    static long lastCheck;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = CardBuilder.dp(this, 16);
        col.setPadding(p, p, p, p * 2);
        sv.addView(col);
        setContentView(sv);

        // ---------- encabezado ----------
        TextView head = text("FiltroKM", 26);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(head);
        TextView ver = text("Versión " + Updater.currentName(this), 12);
        ver.setTextColor(Color.GRAY);
        col.addView(ver);
        upd = text("", 13);
        upd.setTextColor(0xFF2E7D32);
        col.addView(upd);

        // ---------- botón grande ----------
        power = new Button(this);
        power.setTextSize(20);
        power.setTypeface(Typeface.DEFAULT_BOLD);
        power.setTextColor(Color.WHITE);
        power.setAllCaps(false);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, CardBuilder.dp(this, 72));
        plp.topMargin = CardBuilder.dp(this, 14);
        plp.bottomMargin = CardBuilder.dp(this, 6);
        power.setLayoutParams(plp);
        power.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!serviceOn() || OfferService.instance == null) {
                    toast("Activa FiltroKM en Accesibilidad → Aplicaciones instaladas");
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    return;
                }
                boolean running = !Prefs.sp(MainActivity.this).getBoolean("off", false)
                        && !Prefs.sp(MainActivity.this).getBoolean("paused", false);
                OfferService.instance.setOn(!running);
                toast(running ? "Monitoreo detenido" : "Monitoreo iniciado");
                onResume();
            }
        });
        col.addView(power);
        status = text("", 14);
        col.addView(status);

        // ---------- apps ----------
        title("Apps en monitoreo");
        final SharedPreferences spp = Prefs.sp(this);
        appSwitch("Uber", "appUber", spp);
        appSwitch("DiDi", "appDidi", spp);
        appSwitch("inDrive", "appIndrive", spp);

        TextView legend = text("🟢 Verde: te conviene   🟠 Naranjo: $/km bien, falla otro filtro   🔴 Rojo: no te conviene", 13);
        legend.setPadding(0, CardBuilder.dp(this, 10), 0, 0);
        col.addView(legend);

        button("Probar ventana flotante", new View.OnClickListener() {
            public void onClick(View v) {
                if (OfferService.instance == null) { toast("Primero activa FiltroKM en Accesibilidad"); return; }
                OfferService.instance.showDemo();
                toast("Así se verá cuando llegue una oferta");
            }
        });
        TextView bubbleHelp = text("Burbuja flotante: tócala para abrir el menú (Abrir FiltroKM, Pausar/Reanudar, Apagar). Arrástrala para moverla, o hasta la ✕ de abajo para apagarla.", 13);
        bubbleHelp.setTextColor(Color.GRAY);
        col.addView(bubbleHelp);

        Prefs pr = Prefs.load(this);
        title("Filtros (sobre lo que realmente ganas)");
        eKm = field("Mínimo $ por km", fmt(pr.minPerKm), true);
        eHora = field("Mínimo $ por hora", fmt(pr.minPerHour), true);
        ePick = field("Máximo km de recogida (0 = sin filtro)", fmt(pr.maxPickupKm), true);
        title("Descuentos");
        eComU = field("Comisión Uber % (0 si el precio que muestra ya es tu ganancia)", fmt(pr.comUber), true);
        eComD = field("Comisión DiDi % (0 si el precio que muestra ya es tu ganancia)", fmt(pr.comDidi), true);
        eComDP = field("Comisión DiDi \"Pon Tu Precio\" % (paga el pasajero: 15,33)", fmt(pr.comDidiPtp), true);
        eComI = field("Comisión inDrive % (10,6% + IVA = 12,61%)", fmt(pr.comIndrive), true);
        eRetU = field("Retención Uber % (boleta honorarios 2026: 15,25)", fmt(pr.retUber), true);
        eRetD = field("Retención DiDi % (boleta honorarios 2026: 15,25)", fmt(pr.retDidi), true);
        eRetI = field("Retención inDrive % (no retiene: 0)", fmt(pr.retIndrive), true);
        title("Bencina (opcional)");
        eLitro = field("Precio del litro $ (0 = no descontar)", fmt(pr.fuelPrice), true);
        eRend = field("Rendimiento del auto (km por litro)", fmt(pr.fuelKmL), true);
        eCost = field("O costo directo por km (si no usas los dos de arriba)", fmt(Prefs.getD(Prefs.sp(this), "costoKm", 0)), true);
        ePkgs = field("Avanzado: otros paquetes a vigilar (uno por línea)", pr.pkgs, false);
        button("Guardar", new View.OnClickListener() {
            public void onClick(View v) { save(); runTest(); toast("Guardado"); }
        });

        title("Probar con texto");
        eTest = field("Texto de una oferta", SAMPLE, false);
        button("Analizar texto", new View.OnClickListener() {
            public void onClick(View v) { runTest(); }
        });
        preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(0, CardBuilder.dp(this, 8), 0, 0);
        col.addView(preview);

        title("Permisos y actualización");
        button("Abrir Accesibilidad", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        });
        TextView help = text("En Accesibilidad → Aplicaciones instaladas activa \"FiltroKM\". Si aparece \"Ajuste restringido\": "
                + "Ajustes > Aplicaciones > FiltroKM > ⋮ > \"Permitir ajustes restringidos\".", 13);
        help.setTextColor(Color.GRAY);
        col.addView(help);
        button("Buscar actualización", new View.OnClickListener() {
            public void onClick(View v) { checkUpdate(false); }
        });

        title("Diagnóstico (última pantalla leída)");
        diag = text("", 11);
        diag.setTypeface(Typeface.MONOSPACE);
        diag.setTextIsSelectable(true);
        col.addView(diag);
        button("Copiar captura", new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("captura", diag.getText()));
                toast("Copiado. Pégalo en el chat para ajustar el lector.");
            }
        });
        button("Probar con esta captura", new View.OnClickListener() {
            public void onClick(View v) { eTest.setText(diag.getText()); runTest(); }
        });
        runTest();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = serviceOn();
        boolean paused = Prefs.sp(this).getBoolean("paused", false);
        boolean off = Prefs.sp(this).getBoolean("off", false);
        boolean running = on && OfferService.instance != null && !off && !paused;
        android.graphics.drawable.GradientDrawable pg = new android.graphics.drawable.GradientDrawable();
        pg.setCornerRadius(CardBuilder.dp(this, 16));
        if (!on) {
            power.setText("Activar FiltroKM (Accesibilidad)");
            pg.setColor(0xFF1565C0);
            status.setText("FiltroKM aún no tiene permiso para leer las ofertas.");
        } else if (running) {
            power.setText("⏹  Detener monitoreo");
            pg.setColor(0xFF424242);
            status.setText("✓ Monitoreando ofertas. Burbuja verde activa.");
        } else {
            power.setText("▶  Iniciar monitoreo");
            pg.setColor(0xFF2E7D32);
            status.setText(paused && !off ? "⏸ En pausa" : "Detenido");
        }
        power.setBackground(pg);
        status.setTextColor(running ? 0xFF2E7D32 : (on ? 0xFF616161 : 0xFFC62828));
        // al abrir la app: revisa si hay versión nueva y se actualiza sola
        if (System.currentTimeMillis() - lastCheck > 10 * 60 * 1000L) checkUpdate(true);
        SharedPreferences sp = Prefs.sp(this);
        String ok = sp.getString("diagOk", null);
        String last = sp.getString("diag", null);
        diag.setText(ok != null ? ok : (last != null ? last : "Aún no hay capturas. Abre Uber/DiDi/inDrive con el lector activo."));
    }

    void appSwitch(String label, final String key, final SharedPreferences sp) {
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setText(label);
        sw.setTextSize(18);
        sw.setPadding(0, CardBuilder.dp(this, 6), 0, CardBuilder.dp(this, 6));
        sw.setChecked(sp.getBoolean(key, true));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                sp.edit().putBoolean(key, checked).apply();
            }
        });
        col.addView(sw);
    }

    void checkUpdate(boolean silent) {
        lastCheck = System.currentTimeMillis();
        Updater.run(this, silent, new Updater.Listener() {
            public void status(String msg) { upd.setText(msg); }
        });
    }

    boolean serviceOn() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }

    void save() {
        Prefs.sp(this).edit()
                .putString("minKm", eKm.getText().toString().trim())
                .putString("minHora", eHora.getText().toString().trim())
                .putString("maxRecogida", ePick.getText().toString().trim())
                .putString("costoKm", eCost.getText().toString().trim())
                .putString("precioLitro", eLitro.getText().toString().trim())
                .putString("rendimiento", eRend.getText().toString().trim())
                .putString("comUber", eComU.getText().toString().trim())
                .putString("comDidi", eComD.getText().toString().trim())
                .putString("comDidiPtp", eComDP.getText().toString().trim())
                .putString("comIndrive", eComI.getText().toString().trim())
                .putString("retUber", eRetU.getText().toString().trim())
                .putString("retDidi", eRetD.getText().toString().trim())
                .putString("retIndrive", eRetI.getText().toString().trim())
                .putString("pkgs", ePkgs.getText().toString().trim())
                .apply();
    }

    void runTest() {
        List<Parser.Item> items = new ArrayList<Parser.Item>();
        for (String line : eTest.getText().toString().split("\n")) {
            if (line.startsWith("#")) continue;
            int h = 0;
            int bar = line.indexOf('|');
            if (bar > 0 && line.substring(0, bar).matches("\\d+")) {
                h = Integer.parseInt(line.substring(0, bar));
                line = line.substring(bar + 1);
            }
            items.add(new Parser.Item(line, h));
        }
        Parser.Offer o = Parser.parse(items);
        o.app = guessApp(eTest.getText().toString());
        preview.removeAllViews();
        if (!o.valid) {
            preview.addView(text("No se encontró precio y km en el texto (precio " + o.price + ", km " + o.km + ").", 14));
            return;
        }
        save();
        preview.addView(CardBuilder.build(this, o, Prefs.load(this), null));
    }

    static String guessApp(String t) {
        String l = t.toLowerCase();
        if (l.contains("ubercab")) return "Uber";
        if (l.contains("didi") || l.contains("tarifa base")) return "DiDi";
        if (l.contains("indriver") || l.contains("aceptar por")) return "inDrive";
        return "Uber";
    }

    // ---------- helpers UI ----------
    void title(String s) {
        TextView t = text(s, 18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, CardBuilder.dp(this, 18), 0, CardBuilder.dp(this, 4));
        col.addView(t);
    }

    TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(0xFF222222);
        return t;
    }

    EditText field(String label, String val, boolean numeric) {
        TextView l = text(label, 13);
        l.setPadding(0, CardBuilder.dp(this, 8), 0, 0);
        col.addView(l);
        EditText e = new EditText(this);
        e.setText(val);
        if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        col.addView(e);
        return e;
    }

    void button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setOnClickListener(l);
        col.addView(b);
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
