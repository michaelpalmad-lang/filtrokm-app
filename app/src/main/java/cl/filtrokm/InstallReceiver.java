package cl.filtrokm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Recibe el resultado de la instalación de la actualización. */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(confirm);
            }
        } else if (st == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(c, "FiltroKM actualizada", Toast.LENGTH_SHORT).show();
        } else if (st != PackageInstaller.STATUS_FAILURE_ABORTED) {
            String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(c, "No se pudo actualizar: " + msg, Toast.LENGTH_LONG).show();
        }
    }
}
