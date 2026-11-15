package cl.filtrokm;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Busca la última versión publicada en GitHub (Releases), la descarga y la instala
 * desde la misma app, sin abrir el navegador.
 */
public class Updater {

    static final String API = "https://api.github.com/repos/michaelpalmad-lang/filtrokm-app/releases/latest";

    public interface Listener {
        void status(String msg);
    }

    public static int currentCode(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) { return 0; }
    }

    public static String currentName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) { return "?"; }
    }

    /** Revisa y, si hay versión nueva, la descarga e instala. */
    public static void run(final Activity a, final boolean silentIfNone, final Listener l) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                try {
                    post(ui, l, "Buscando actualización…");
                    JSONObject rel = new JSONObject(get(API));
                    String tag = rel.optString("tag_name", "");
                    int code = remoteCode(rel);
                    String url = null;
                    JSONArray assets = rel.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject as = assets.getJSONObject(i);
                            if (as.optString("name").endsWith(".apk")) { url = as.optString("browser_download_url"); break; }
                        }
                    }
                    if (url == null || code <= currentCode(a)) {
                        post(ui, l, silentIfNone ? "" : "Tienes la última versión (" + currentName(a) + ")");
                        return;
                    }
                    if (!canInstall(a)) {
                        post(ui, l, "Falta un permiso: activa \"Permitir de esta fuente\" y vuelve atrás.");
                        ui.post(new Runnable() { public void run() { askInstallPermission(a); } });
                        return;
                    }
                    post(ui, l, "Descargando " + tag + "…");
                    File f = new File(a.getCacheDir(), "update.apk");
                    download(url, f);
                    post(ui, l, "Instalando " + tag + "…");
                    install(a, f);
                } catch (Exception e) {
                    post(ui, l, silentIfNone ? "" : "No se pudo revisar: " + e.getMessage());
                }
            }
        }).start();
    }

    static void post(Handler ui, final Listener l, final String msg) {
        ui.post(new Runnable() { public void run() { l.status(msg); } });
    }

    /** El workflow escribe "versionCode=NNN" en la descripción del release. */
    static int remoteCode(JSONObject rel) {
        Matcher m = Pattern.compile("versionCode=(\\d+)").matcher(rel.optString("body", ""));
        if (m.find()) return Integer.parseInt(m.group(1));
        Matcher t = Pattern.compile("v\\d+\\.(\\d+)").matcher(rel.optString("tag_name", ""));
        return t.find() ? 100 + Integer.parseInt(t.group(1)) : 0;
    }

    static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "FiltroKM");
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    static void download(String u, File f) throws Exception {
        URL url = new URL(u);
        HttpURLConnection c = null;
        for (int i = 0; i < 5; i++) { // seguir redirecciones de GitHub
            c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "FiltroKM");
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) { url = new URL(url, c.getHeaderField("Location")); continue; }
            if (code != 200) throw new Exception("HTTP " + code);
            break;
        }
        InputStream in = c.getInputStream();
        OutputStream out = new FileOutputStream(f);
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        out.close();
        in.close();
    }

    static boolean canInstall(Context c) {
        if (Build.VERSION.SDK_INT < 26) return true;
        try {
            return (Boolean) PackageManager.class.getMethod("canRequestPackageInstalls").invoke(c.getPackageManager());
        } catch (Exception e) { return true; }
    }

    static void askInstallPermission(Activity a) {
        try {
            a.startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                    Uri.parse("package:" + a.getPackageName())));
        } catch (Exception ignored) { }
    }

    static void install(Context c, File f) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12+: las siguientes actualizaciones pueden instalarse sin preguntar
            try { p.getClass().getMethod("setRequireUserAction", int.class).invoke(p, 2); } catch (Exception ignored) { }
        }
        int id = pi.createSession(p);
        PackageInstaller.Session s = pi.openSession(id);
        InputStream in = new FileInputStream(f);
        OutputStream out = s.openWrite("filtrokm.apk", 0, f.length());
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        s.fsync(out);
        out.close();
        in.close();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? 0x02000000 /* FLAG_MUTABLE */ : 0);
        PendingIntent pend = PendingIntent.getBroadcast(c, id, new Intent(c, InstallReceiver.class), flags);
        s.commit(pend.getIntentSender());
        s.close();
    }
}
