import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.ByteProcessor;
import ij.process.DoubleProcessor;
import ij.process.ImageProcessor;

/**
 * Builds a 256x256 64-bit (DoubleProcessor) image with three disjoint disk-shaped
 * particles whose fill values cannot be represented exactly in float32. Then:
 *
 *   1) Computes per-particle Mean/Min/Max directly from the underlying double[]
 *      pixels (the "reference").
 *
 *   2) Runs Analyze Particles by SEGMENTING an 8-bit binary MASK derived from
 *      the double image, while REDIRECTING measurements to the original double
 *      image. This avoids ImageJ's display-range-dependent thresholding of
 *      float/double images.
 *
 *   3) Compares the per-particle Mean/Min/Max returned by Analyze Particles
 *      against the reference. If your DoubleProcessorSupport branch is wired
 *      correctly, all errors are 0 or <= 1e-15. A jump to ~1e-7..1e-6 would
 *      indicate a remaining (float[]) downcast somewhere in the
 *      ParticleAnalyzer / ImageStatistics path.
 *
 * Particles are paired to references by nearest-centroid distance, because
 * ParticleAnalyzer returns particles in raster scan order which is layout-dependent.
 */
public class Particle_Precision_Test_ implements PlugIn {

    // Values chosen so that each needs more than 24 mantissa bits to represent
    // exactly, i.e. a float32 round-trip would perturb them detectably.
    private static final double V1 = 1.0 / 3.0;            // 0.3333333333333333...
    private static final double V2 = 2.0 / 7.0 + 0.5;      // 0.7857142857142857...
    private static final double V3 = Math.PI / 4.0;        // 0.7853981633974483...

    private static final double BG  = 0.0;
    private static final double THR = 1e-9; // anything > BG is a particle pixel

    @Override
    public void run(String arg) {
        final int W = 256, H = 256;

        // ---- 1) Build a real 64-bit image ----
        DoubleProcessor dp = new DoubleProcessor(W, H);
        double[] pixels = (double[]) dp.getPixels();
        java.util.Arrays.fill(pixels, BG);

        OvalRoi r1 = new OvalRoi( 40,  40, 60, 60); // P1
        OvalRoi r2 = new OvalRoi(150,  80, 40, 40); // P2
        OvalRoi r3 = new OvalRoi( 90, 170, 80, 50); // P3

        fillRoiWithDouble(dp, r1, V1);
        fillRoiWithDouble(dp, r2, V2);
        fillRoiWithDouble(dp, r3, V3);

        ImagePlus imp = new ImagePlus("Double64_Blobs", dp);
        imp.resetDisplayRange();
        imp.show();

        // ---- 2) Reference per-particle stats from double[] pixels ----
        Ref ref1 = referenceStats(pixels, W, H, r1, V1);
        Ref ref2 = referenceStats(pixels, W, H, r2, V2);
        Ref ref3 = referenceStats(pixels, W, H, r3, V3);
        Ref[] refs = { ref1, ref2, ref3 };

        IJ.log("=== Reference (computed from double[] pixels) ===");
        for (int i = 0; i < refs.length; i++) {
            Ref r = refs[i];
            IJ.log(String.format(
                "P%d  n=%d  expected=%.17g  mean=%.17g  min=%.17g  max=%.17g  cx=%.3f  cy=%.3f",
                i + 1, r.n, r.expected, r.mean, r.min, r.max, r.cx, r.cy));
        }

        // ---- 3) Build a clean 8-bit binary MASK from the double image ----
        // Any pixel above THR is foreground (255), background is 0. This
        // bypasses ImageJ's display-range-dependent thresholding for
        // float/double images entirely.
        ByteProcessor mask = new ByteProcessor(W, H);
        byte[] mbytes = (byte[]) mask.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            mbytes[i] = (pixels[i] > THR) ? (byte) 255 : 0;
        }
        // Threshold of the mask itself: anything == 255 is "in".
        mask.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
        ImagePlus maskImp = new ImagePlus("Double64_Blobs_mask", mask);
        // Keep the mask hidden; we only need it for segmentation.
        // maskImp.show();   // uncomment if you want to see it

        // ---- 4) Redirect measurements from the mask back to the double image ----
        // We point the Analyzer at `imp` (the 64-bit image) for VALUE
        // measurements while ParticleAnalyzer segments the binary mask.
        Analyzer.setRedirectImage(imp);

        ResultsTable rt = new ResultsTable();
        rt.setPrecision(9);

        int measurements =
                Measurements.AREA
              | Measurements.MEAN
              | Measurements.STD_DEV
              | Measurements.MIN_MAX
              | Measurements.CENTROID
              | Measurements.INTEGRATED_DENSITY;

        int options = ParticleAnalyzer.SHOW_OUTLINES
                    | ParticleAnalyzer.CLEAR_WORKSHEET;

        ParticleAnalyzer pa = new ParticleAnalyzer(
                options, measurements, rt,
                50.0, Double.POSITIVE_INFINITY,    // min/max area in pixels
                0.0, 1.0);                         // min/max circularity

        Analyzer.setResultsTable(rt);
        boolean ok;
        try {
            ok = pa.analyze(maskImp);
        } finally {
            // Clear the redirect without triggering the "Redirect To..." dialog.
            clearRedirectQuietly();
        }
        if (!ok) {
            IJ.error("Particle_Precision_Test",
                    "ParticleAnalyzer.analyze() returned false");
            return;
        }
        rt.show("Results");

        int found = rt.getCounter();
        IJ.log("=== Analyze Particles found " + found + " particles ===");
        if (found != refs.length) {
            IJ.log("WARNING: expected " + refs.length + " particles, got " + found);
        }

        // ---- 5) Pair each measured particle to its nearest-centroid reference ----
        int colMean = rt.getColumnIndex("Mean");
        int colMin  = rt.getColumnIndex("Min");
        int colMax  = rt.getColumnIndex("Max");
        int colArea = rt.getColumnIndex("Area");
        int colX    = rt.getColumnIndex("X");
        int colY    = rt.getColumnIndex("Y");

        boolean[] used = new boolean[refs.length];
        IJ.log("=== Measured (from Analyze Particles) vs Reference ===");
        double worstErr = 0.0;
        for (int i = 0; i < found; i++) {
            double mMean = rt.getValueAsDouble(colMean, i);
            double mMin  = rt.getValueAsDouble(colMin,  i);
            double mMax  = rt.getValueAsDouble(colMax,  i);
            double mArea = rt.getValueAsDouble(colArea, i);
            double mX    = rt.getValueAsDouble(colX,    i);
            double mY    = rt.getValueAsDouble(colY,    i);

            int best = -1;
            double bestD2 = Double.POSITIVE_INFINITY;
            for (int k = 0; k < refs.length; k++) {
                if (used[k]) continue;
                double dx = mX - refs[k].cx, dy = mY - refs[k].cy;
                double d2 = dx*dx + dy*dy;
                if (d2 < bestD2) { bestD2 = d2; best = k; }
            }
            if (best < 0) continue;
            used[best] = true;
            Ref ref = refs[best];

            double eMean = Math.abs(mMean - ref.mean);
            double eMin  = Math.abs(mMin  - ref.min);
            double eMax  = Math.abs(mMax  - ref.max);
            worstErr = Math.max(worstErr, Math.max(eMean, Math.max(eMin, eMax)));

            IJ.log(String.format(
                "match: measured#%d  <->  reference P%d (centroid dist=%.3f px)%n"
              + "      area=%.1f (ref n=%d)   expected value=%.17g%n"
              + "      meanMeasured=%.17g  meanRef=%.17g  |err|=%.3e%n"
              + "      minMeasured =%.17g  minRef =%.17g  |err|=%.3e%n"
              + "      maxMeasured =%.17g  maxRef =%.17g  |err|=%.3e%n"
              + "      precision verdict: %s",
                i + 1, best + 1, Math.sqrt(bestD2),
                mArea, ref.n, ref.expected,
                mMean, ref.mean, eMean,
                mMin,  ref.min,  eMin,
                mMax,  ref.max,  eMax,
                verdict(eMean)));
        }
        IJ.log("=== Worst absolute error across all matched particles: "
                + String.format("%.3e", worstErr) + "  --  "
                + verdict(worstErr) + " ===");

        WindowManager.setCurrentWindow(imp.getWindow());
    }

    // ---- helpers -----------------------------------------------------------

    private static void fillRoiWithDouble(DoubleProcessor dp, Roi roi, double v) {
        ImageProcessor mask = roi.getMask();
        java.awt.Rectangle b = roi.getBounds();
        double[] pix = (double[]) dp.getPixels();
        int W = dp.getWidth();
        if (mask == null) {
            for (int y = b.y; y < b.y + b.height; y++) {
                int row = y * W;
                for (int x = b.x; x < b.x + b.width; x++) pix[row + x] = v;
            }
        } else {
            for (int yy = 0; yy < b.height; yy++) {
                int y = b.y + yy;
                int row = y * W;
                for (int xx = 0; xx < b.width; xx++) {
                    if (mask.get(xx, yy) != 0) {
                        int x = b.x + xx;
                        pix[row + x] = v;
                    }
                }
            }
        }
    }

    private static final class Ref {
        int n;
        double mean, min, max, cx, cy, expected;
    }

    private static Ref referenceStats(double[] pixels, int W, int H, Roi roi, double expected) {
        ImageProcessor mask = roi.getMask();
        java.awt.Rectangle b = roi.getBounds();
        Ref r = new Ref();
        r.min = Double.POSITIVE_INFINITY;
        r.max = Double.NEGATIVE_INFINITY;
        r.expected = expected;
        double sum = 0.0, sumX = 0.0, sumY = 0.0;
        int n = 0;
        for (int yy = 0; yy < b.height; yy++) {
            int y = b.y + yy;
            if (y < 0 || y >= H) continue;
            int row = y * W;
            for (int xx = 0; xx < b.width; xx++) {
                int x = b.x + xx;
                if (x < 0 || x >= W) continue;
                boolean inside = (mask == null) || (mask.get(xx, yy) != 0);
                if (!inside) continue;
                double v = pixels[row + x];
                sum += v;
                if (v < r.min) r.min = v;
                if (v > r.max) r.max = v;
                // ImageJ centroid convention: pixel centers at (x+0.5, y+0.5)
                sumX += x + 0.5;
                sumY += y + 0.5;
                n++;
            }
        }
        r.n = n;
        r.mean = (n > 0) ? sum / n : Double.NaN;
        r.cx   = (n > 0) ? sumX / n : Double.NaN;
        r.cy   = (n > 0) ? sumY / n : Double.NaN;
        return r;
    }

    private static String verdict(double absErr) {
        if (absErr <= 1e-12) return "OK (double precision preserved)";
        if (absErr <= 1e-6)  return "DEGRADED (~float32 precision; likely downcast somewhere)";
        return "FAIL (large error; check pixel/mask plumbing)";
    }

    /**
     * Clears the Analyzer's measurement-redirect target without triggering
     * the interactive "Redirect To..." chooser that some ImageJ forks pop up
     * when Analyzer.setRedirectImage(null) is called directly.
     *
     * Strategy: ask Analyzer to interpret the macro option "redirect=None"
     * first, then fall back to reflection on the private redirect fields.
     * If neither works we just leave the redirect in place; it has no effect
     * once `imp` is gone, and the next analyzer run will overwrite it.
     */
    private static void clearRedirectQuietly() {
        // 1) Preferred: ask Analyzer to interpret the macro option "redirect=None".
        try {
            Macro.setOptions("redirect=None decimal=9");
            new Analyzer(); // parses the option, clears the redirect
            Macro.setOptions(null);
            return;
        } catch (Throwable ignored) { /* fall through */ }

        // 2) Reflection fallback: null out the private static fields directly.
        try {
            java.lang.reflect.Field tgt = Analyzer.class.getDeclaredField("redirectTarget");
            tgt.setAccessible(true);
            try {
                tgt.set(null, 0);              // int field on stock IJ
            } catch (IllegalArgumentException iae) {
                tgt.set(null, null);           // ImagePlus field on some forks
            }
        } catch (Throwable ignored) { /* give up silently */ }
        try {
            java.lang.reflect.Field ttl = Analyzer.class.getDeclaredField("redirectTitle");
            ttl.setAccessible(true);
            ttl.set(null, null);
        } catch (Throwable ignored) { /* give up silently */ }
    }
}
