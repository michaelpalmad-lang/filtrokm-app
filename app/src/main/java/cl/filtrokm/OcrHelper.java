package cl.filtrokm;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.view.Display;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Lee el texto dibujado en pantalla (por ejemplo, las etiquetas del mapa de inDrive
 * "27 min / 25,0 km") cuando la accesibilidad no lo entrega. Todo ocurre en el teléfono.
 */
public class OcrHelper {

    public interface Callback { void done(List<Parser.Item> items); }

    static TextRecognizer rec;

    public static boolean supported() { return Build.VERSION.SDK_INT >= 30; }

    public static void capture(final AccessibilityService svc, final List<Rect> exclude, final Callback cb) {
        if (!supported()) { cb.done(null); return; }
        try {
            svc.takeScreenshot(Display.DEFAULT_DISPLAY, svc.getMainExecutor(),
                    new AccessibilityService.TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(AccessibilityService.ScreenshotResult r) {
                            Bitmap bmp = null;
                            try {
                                Bitmap hw = Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(), r.getColorSpace());
                                if (hw != null) bmp = hw.copy(Bitmap.Config.ARGB_8888, false);
                                r.getHardwareBuffer().close();
                            } catch (Exception ignored) { }
                            if (bmp == null) { cb.done(null); return; }
                            recognize(bmp, exclude, cb);
                        }

                        @Override
                        public void onFailure(int errorCode) { cb.done(null); }
                    });
        } catch (Exception e) {
            cb.done(null);
        }
    }

    static void recognize(Bitmap bmp, final List<Rect> exclude, final Callback cb) {
        try {
            if (rec == null) rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            rec.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener(new OnSuccessListener<Text>() {
                        @Override
                        public void onSuccess(Text t) {
                            List<Parser.Item> out = new ArrayList<Parser.Item>();
                            List<Text.Line> lines = new ArrayList<Text.Line>();
                            for (Text.TextBlock b : t.getTextBlocks()) {
                                for (Text.Line l : b.getLines()) {
                                    Rect lb = l.getBoundingBox();
                                    if (lb != null && !excluded(lb, exclude)) lines.add(l);
                                }
                            }
                            // etiquetas del mapa: "13 min" con "5,5 km" justo debajo → un solo par
                            for (Text.Line a : lines) {
                                String ta = a.getText().trim();
                                if (!ta.matches("(?i)^\\d+\\s*min\\.?$")) continue;
                                Rect ra = a.getBoundingBox();
                                Text.Line best = null;
                                int bestDy = Integer.MAX_VALUE;
                                for (Text.Line b : lines) {
                                    if (b == a) continue;
                                    String tb = b.getText().trim();
                                    if (!tb.matches("(?i)^\\d+(?:[.,]\\d+)?\\s*(km|m)$")) continue;
                                    Rect rb = b.getBoundingBox();
                                    int dy = rb.top - ra.top;
                                    if (dy <= 0 || dy > ra.height() * 3) continue;
                                    if (Math.abs(rb.centerX() - ra.centerX()) > Math.max(ra.width(), rb.width())) continue;
                                    if (dy < bestDy) { bestDy = dy; best = b; }
                                }
                                if (best != null) out.add(new Parser.Item(ta + " " + best.getText().trim(), ra.height(), ra.top));
                            }
                            for (Text.TextBlock b : t.getTextBlocks()) {
                                Rect bb = b.getBoundingBox();
                                if (bb != null && excluded(bb, exclude)) continue;
                                StringBuilder sb = new StringBuilder();
                                int h = 0;
                                for (Text.Line l : b.getLines()) {
                                    if (sb.length() > 0) sb.append(' ');
                                    sb.append(l.getText());
                                    Rect lb = l.getBoundingBox();
                                    if (lb != null) h = Math.max(h, lb.height());
                                }
                                if (sb.length() > 0) out.add(new Parser.Item(sb.toString(), h, bb != null ? bb.top : -1));
                            }
                            cb.done(out);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) { cb.done(null); }
                    });
        } catch (Exception e) {
            cb.done(null);
        }
    }

    static boolean excluded(Rect r, List<Rect> ex) {
        if (ex == null) return false;
        for (Rect e : ex) if (Rect.intersects(r, e)) return true;
        return false;
    }
}
