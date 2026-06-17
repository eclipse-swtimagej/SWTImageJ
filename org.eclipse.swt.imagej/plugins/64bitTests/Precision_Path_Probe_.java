import ij.IJ;
import ij.ImagePlus;
import ij.process.DoubleProcessor;
import ij.process.FHT;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.plugin.PlugIn;

/**
 * Pinpoint diagnostic for the DoubleProcessorSupport branch.
 *
 * Probe 1: getf(x,y) round-trip            -> exposes the float32 floor of
 *                                             the getf / getPixelValue(int,int)
 *                                             accessors.
 *
 * Probe 2: rotate(0)                       -> measures the *interior* (drops the
 *                                             last row/column to avoid the
 *                                             pre-existing edge-clamp issue
 *                                             carried over from FloatProcessor).
 *                                             Tests both BILINEAR and BICUBIC.
 *
 * Probe 3: FHT.transform / inverseTransform -> calls the Fast Hartley Transform
 *                                              directly to bypass the FFT
 *                                              plugin's "Convert to 32-bit"
 *                                              dialog. Confirms the FFT row of
 *                                              the audit table is structurally
 *                                              32-bit (FHT extends FloatProcessor).
 */
public class Precision_Path_Probe_ implements PlugIn {

    @Override
    public void run(String arg) {
        IJ.log("\\Clear");
        IJ.log("=== DoubleProcessorSupport precision probe ===");
        probeGetfRoundTrip();
        probeRotateInterior();
        probeFHTRoundTrip();
        IJ.log("=== done ===");
    }

    // --- Probe 1: getf round-trip ------------------------------------------

    private void probeGetfRoundTrip() {
        final int W = 64, H = 64;
        DoubleProcessor dp = makeNonRepresentableDouble(W, H, 1234L);
        double[] orig = (double[]) dp.getPixels();

        double maxErr = 0.0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double through = dp.getf(x, y);
                double err = Math.abs(through - orig[y * W + x]);
                if (err > maxErr) maxErr = err;
            }
        }
        report("Probe 1: DoubleProcessor.getf(x,y) round-trip", maxErr);
        IJ.log("           expected: ~1e-8..1e-7 (float32 ULP)");
    }

    // --- Probe 2: rotate(0) interior precision -----------------------------

    private void probeRotateInterior() {
        final int W = 64, H = 64;
        DoubleProcessor dp = makeNonRepresentableDouble(W, H, 5678L);
        double[] orig = ((double[]) dp.getPixels()).clone();

        DoubleProcessor dpB = (DoubleProcessor) dp.duplicate();
        dpB.setInterpolationMethod(ImageProcessor.BILINEAR);
        dpB.rotate(0.0);
        double maxErrInteriorBL = maxAbsDeltaInterior((double[]) dpB.getPixels(), orig, W, H);
        double maxErrEdgeBL     = maxAbsDeltaEdge    ((double[]) dpB.getPixels(), orig, W, H);

        DoubleProcessor dpC = (DoubleProcessor) dp.duplicate();
        dpC.setInterpolationMethod(ImageProcessor.BICUBIC);
        dpC.rotate(0.0);
        double maxErrInteriorBC = maxAbsDeltaInterior((double[]) dpC.getPixels(), orig, W, H);
        double maxErrEdgeBC     = maxAbsDeltaEdge    ((double[]) dpC.getPixels(), orig, W, H);

        IJ.log(String.format("Probe 2a: rotate(0) BILINEAR  interior |err|=%.3e   edge |err|=%.3e   -> %s",
                maxErrInteriorBL, maxErrEdgeBL, verdict(maxErrInteriorBL)));
        IJ.log("           interior expected: 0 (identity); edge ~1e-3 due to last-row/col clamp (pre-existing)");

        IJ.log(String.format("Probe 2b: rotate(0) BICUBIC   interior |err|=%.3e   edge |err|=%.3e   -> %s",
                maxErrInteriorBC, maxErrEdgeBC, verdict(maxErrInteriorBC)));
        IJ.log("           interior expected: ~1e-8..1e-7 if bicubic goes through getf; 0 if it doesn't");
    }

    // --- Probe 3: FHT round-trip without the FFT plugin --------------------

    private void probeFHTRoundTrip() {
        final int N = 64; // power of two, square -- required by FHT
        DoubleProcessor dp = makeNonRepresentableDouble(N, N, 9012L);
        double[] orig = ((double[]) dp.getPixels()).clone();

        // FHT extends FloatProcessor and its constructor calls convertToFloat()
        // on a non-FloatProcessor input. So this is the structural float floor
        // of the FFT pipeline on this branch.
        FHT fht = new FHT(dp);
        fht.transform();
        fht.inverseTransform();

        // FHT pixel array is float[].
        float[] back = (float[]) fht.getPixels();
        double maxErr = 0.0;
        for (int i = 0; i < back.length; i++) {
            double err = Math.abs(((double) back[i]) - orig[i]);
            if (err > maxErr) maxErr = err;
        }
        report("Probe 3: FHT forward + inverse round-trip", maxErr);
        IJ.log("           expected: ~1e-6..1e-5 (FHT is a FloatProcessor; double input is narrowed)");
    }

    // ---- helpers ----------------------------------------------------------

    private static DoubleProcessor makeNonRepresentableDouble(int W, int H, long seed) {
        DoubleProcessor dp = new DoubleProcessor(W, H);
        double[] pix = (double[]) dp.getPixels();
        java.util.Random rnd = new java.util.Random(seed);
        for (int i = 0; i < pix.length; i++) {
            pix[i] = 0.5 + rnd.nextDouble() + 1e-9 * Math.PI;
        }
        return dp;
    }

    /** Max |a-b| over the inner (W-1) x (H-1) region (excludes last row & col). */
    private static double maxAbsDeltaInterior(double[] a, double[] b, int W, int H) {
        double m = 0.0;
        for (int y = 0; y < H - 1; y++) {
            int row = y * W;
            for (int x = 0; x < W - 1; x++) {
                double e = Math.abs(a[row + x] - b[row + x]);
                if (e > m) m = e;
            }
        }
        return m;
    }

    /** Max |a-b| over the last row and last column. */
    private static double maxAbsDeltaEdge(double[] a, double[] b, int W, int H) {
        double m = 0.0;
        // last column
        for (int y = 0; y < H; y++) {
            int idx = y * W + (W - 1);
            double e = Math.abs(a[idx] - b[idx]);
            if (e > m) m = e;
        }
        // last row
        for (int x = 0; x < W; x++) {
            int idx = (H - 1) * W + x;
            double e = Math.abs(a[idx] - b[idx]);
            if (e > m) m = e;
        }
        return m;
    }

    private static void report(String label, double err) {
        IJ.log(String.format("%s%n   |err|_max = %.3e   ->  %s",
                label, err, verdict(err)));
    }

    private static String verdict(double err) {
        if (err <= 1e-12) return "OK (full double precision)";
        if (err <= 1e-4)  return "32-BIT FLOOR (float32 narrowing in this path)";
        return "LARGE ERROR (likely a geometric/edge issue, not precision)";
    }
}
