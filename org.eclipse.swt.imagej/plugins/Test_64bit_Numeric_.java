import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.process.Blitter;
import ij.process.DoubleProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/**
 * Stress-test numerical operations on 64-bit (DoubleProcessor) images.
 * Verifies:
 *   - operations preserve the 64-bit type (no silent downcast to float)
 *   - results match a manually computed double[] reference
 *   - precision survives round-tripping operations
 *   - values that distinguish double from float survive
 */
public class Test_64bit_Numeric_ implements PlugIn {

    private int passed = 0;
    private int failed = 0;

    @Override
    public void run(String arg) {
        IJ.log("\\Clear");
        IJ.log("=== 64-bit numeric stress test ===");

        // small image so logs are readable and tests are fast
        int w = 8, h = 4;

        testAdd(w, h);
        testSubtract(w, h);
        testMultiply(w, h);
        testDivide(w, h);
        testSqrt(w, h);
        testSqr(w, h);
        testLogExpRoundTrip(w, h);
        testAbs(w, h);
        testMinMax(w, h);
        testGamma(w, h);
        testFill(w, h);
        testGetSetPixelValue(w, h);
        testSnapshotReset(w, h);
        testDuplicate(w, h);
        testConvertToFloatPrecisionLoss(w, h);
        testBlitterAdd(w, h);
        testGetStatistics(w, h);
        testTypePreservedOnOps(w, h);

        IJ.log("");
        IJ.log("Passed: " + passed);
        IJ.log("Failed: " + failed);
        if (failed == 0)
            IJ.log("=== ALL 64-BIT NUMERIC TESTS PASSED ===");
        else
            IJ.log("=== SOME 64-BIT NUMERIC TESTS FAILED ===");
    }

    // ----------------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------------
    private DoubleProcessor makeBase(int w, int h, double base, double step) {
        double[] px = new double[w * h];
        for (int i = 0; i < px.length; i++)
            px[i] = base + i * step;
        return new DoubleProcessor(w, h, px);
    }

    private DoubleProcessor makePositive(int w, int h) {
        double[] px = new double[w * h];
        for (int i = 0; i < px.length; i++)
            px[i] = 1.0 + i; // 1..n
        return new DoubleProcessor(w, h, px);
    }

    private void check(String name, boolean cond) {
        if (cond) {
            passed++;
            IJ.log("OK  : " + name);
        } else {
            failed++;
            IJ.log("FAIL: " + name);
        }
    }

    private boolean equalsBitExact(double[] a, double[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (Double.compare(a[i], b[i]) != 0)
                return false;
        }
        return true;
    }

    private boolean approxEquals(double[] a, double[] b, double eps) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            double d = Math.abs(a[i] - b[i]);
            if (Double.isNaN(d) || d > eps) return false;
        }
        return true;
    }

    private static double[] copy(double[] in) {
        double[] out = new double[in.length];
        System.arraycopy(in, 0, out, 0, in.length);
        return out;
    }

    // ----------------------------------------------------------------------
    // tests
    // ----------------------------------------------------------------------

    private void testAdd(int w, int h) {
        DoubleProcessor dp = makeBase(w, h, 1.0e8, 0.01);
        double[] before = copy((double[]) dp.getPixels());
        double k = 0.5;
        dp.add(k);
        double[] after = (double[]) dp.getPixels();
        double[] ref = new double[before.length];
        for (int i = 0; i < ref.length; i++) ref[i] = before[i] + k;

        check("add: type preserved", dp.getBitDepth() == 64
                && dp.getPixels() instanceof double[]);
        check("add: values bit-exact vs reference", equalsBitExact(after, ref));
    }

    private void testSubtract(int w, int h) {
        DoubleProcessor dp = makeBase(w, h, 1.0e8, 0.01);
        double[] before = copy((double[]) dp.getPixels());
        double k = 0.25;
        dp.subtract(k);
        double[] after = (double[]) dp.getPixels();
        double[] ref = new double[before.length];
        for (int i = 0; i < ref.length; i++) ref[i] = before[i] - k;

        check("sub: type preserved", dp.getBitDepth() == 64
                && dp.getPixels() instanceof double[]);
        check("sub: values bit-exact vs reference", equalsBitExact(after, ref));
    }

    private void testMultiply(int w, int h) {
        DoubleProcessor dp = makeBase(w, h, 1.0e8, 0.01);
        double[] before = copy((double[]) dp.getPixels());
        double k = 1.5;
        dp.multiply(k);
        double[] after = (double[]) dp.getPixels();
        double[] ref = new double[before.length];
        for (int i = 0; i < ref.length; i++) ref[i] = before[i] * k;

        check("mul: type preserved", dp.getBitDepth() == 64
                && dp.getPixels() instanceof double[]);
        check("mul: values bit-exact vs reference", equalsBitExact(after, ref));
    }

    private void testDivide(int w, int h) {
        DoubleProcessor dp = makeBase(w, h, 1.0e8, 0.01);
        double[] before = copy((double[]) dp.getPixels());
        double k = 0.5;
        dp.multiply(1.0 / k);
        double[] after = (double[]) dp.getPixels();
        double[] ref = new double[before.length];
        for (int i = 0; i < ref.length; i++) ref[i] = before[i] * (1.0 / k);

        check("div (via multiply(1/k)): type preserved",
                dp.getBitDepth() == 64 && dp.getPixels() instanceof double[]);
        check("div: values bit-exact vs reference",
                equalsBitExact(after, ref));
    }

    private void testSqrt(int w, int h) {
        DoubleProcessor dp = makePositive(w, h);
        double[] before = copy((double[]) dp.getPixels());
        dp.sqrt();
        double[] after = (double[]) dp.getPixels();
        double[] ref = new double[before.length];
        for (int i = 0; i < ref.length; i++) ref[i] = Math.sqrt(before[i]);

        check("sqrt: type preserved",
                dp.getBitDepth() == 64 && dp.getPixels() instanceof double[]);
        // sqrt itself is well-defined; allow tiny float-eps in case implementation casts
        check("sqrt: values match double Math.sqrt (bit-exact)",
                equalsBitExact(after, ref));
    }

    private void testSqr(int w, int h) {
        DoubleProcessor dp = makePositive(w, h);
        double[] before = copy((double[]) dp.getPixels());
        dp.sqr();
        double[] after = (double[]) dp.getPixels();
        double[] ref = new double[before.length];
        for (int i = 0; i < ref.length; i++) ref[i] = before[i] * before[i];

        check("sqr: type preserved",
                dp.getBitDepth() == 64 && dp.getPixels() instanceof double[]);
        check("sqr: values bit-exact vs reference", equalsBitExact(after, ref));
    }

    private void testLogExpRoundTrip(int w, int h) {
        DoubleProcessor dp = makePositive(w, h);
        double[] before = copy((double[]) dp.getPixels());
        dp.log();
        dp.exp();
        double[] after = (double[]) dp.getPixels();

        check("log+exp: type preserved",
                dp.getBitDepth() == 64 && dp.getPixels() instanceof double[]);
        // log/exp aren't exactly inverses in fp, allow small tolerance,
        // but still tighter than float precision
        check("log+exp: round-trip approx equals original (1e-12)",
                approxEquals(after, before, 1.0e-12));
    }

    private void testAbs(int w, int h) {
        double[] px = new double[w * h];
        for (int i = 0; i < px.length; i++) px[i] = (i % 2 == 0) ? -i : i;
        DoubleProcessor dp = new DoubleProcessor(w, h, px);
        double[] before = copy((double[]) dp.getPixels());
        dp.abs();
        double[] after = (double[]) dp.getPixels();
        double[] ref = new double[before.length];
        for (int i = 0; i < ref.length; i++) ref[i] = Math.abs(before[i]);

        check("abs: type preserved",
                dp.getBitDepth() == 64 && dp.getPixels() instanceof double[]);
        check("abs: values bit-exact vs reference", equalsBitExact(after, ref));
    }

    private void testMinMax(int w, int h) {
        DoubleProcessor dp = makeBase(w, h, 1.0e8, 0.01);
        double cutoff = 1.0e8 + 5 * 0.01;
        double[] before = copy((double[]) dp.getPixels());
        dp.min(cutoff); // clamp from below
        double[] minResult = copy((double[]) dp.getPixels());
        double[] ref = new double[before.length];
        for (int i = 0; i < ref.length; i++)
            ref[i] = Math.max(before[i], cutoff); // ip.min(c) sets pixel = max(p,c)
        check("min(cutoff): type preserved",
                dp.getBitDepth() == 64 && dp.getPixels() instanceof double[]);
        check("min(cutoff): values match reference",
                equalsBitExact(minResult, ref));

        DoubleProcessor dp2 = makeBase(w, h, 1.0e8, 0.01);
        double cutoffMax = 1.0e8 + 5 * 0.01;
        double[] before2 = copy((double[]) dp2.getPixels());
        dp2.max(cutoffMax); // clamp from above
        double[] maxResult = copy((double[]) dp2.getPixels());
        double[] ref2 = new double[before2.length];
        for (int i = 0; i < ref2.length; i++)
            ref2[i] = Math.min(before2[i], cutoffMax);
        check("max(cutoff): type preserved",
                dp2.getBitDepth() == 64 && dp2.getPixels() instanceof double[]);
        check("max(cutoff): values match reference",
                equalsBitExact(maxResult, ref2));
    }

    private void testGamma(int w, int h) {
        DoubleProcessor dp = makePositive(w, h);
        dp.setMinAndMax(1.0, w * h);
        double[] before = copy((double[]) dp.getPixels());
        dp.gamma(0.5);
        double[] after = (double[]) dp.getPixels();

        check("gamma: type preserved",
                dp.getBitDepth() == 64 && dp.getPixels() instanceof double[]);
        // can't easily reference-compute without replicating IJ formula;
        // just sanity check it changed and stayed finite
        boolean changed = !equalsBitExact(before, after);
        boolean finite = true;
        for (double v : after)
            if (Double.isNaN(v) || Double.isInfinite(v)) { finite = false; break; }
        check("gamma: values changed", changed);
        check("gamma: values finite", finite);
    }

    private void testFill(int w, int h) {
        DoubleProcessor dp = makeBase(w, h, 1.0e8, 0.01);
        double fillVal = 1.0e8 + 0.0001;
        dp.setValue(fillVal);
        dp.fill();
        double[] after = (double[]) dp.getPixels();

        boolean allEqual = true;
        for (double v : after)
            if (Double.compare(v, fillVal) != 0) { allEqual = false; break; }

        check("fill: type preserved",
                dp.getBitDepth() == 64 && dp.getPixels() instanceof double[]);
        check("fill: every pixel == fillVal (bit-exact)", allEqual);
    }

    private void testGetSetPixelValue(int w, int h) {
    DoubleProcessor dp = new DoubleProcessor(w, h);
    double v = 1.0e8 + 0.123456789012345;
    dp.putPixelValue(3, 2, v);

    // Full-precision accessor:
    double r = dp.getPixelValueDouble(3, 2);
    check("putPixelValue/getPixelValueDouble: bit-exact",
            Double.compare(r, v) == 0);

    // Old getPixelValue API only returns float and MUST collapse:
    float fr = dp.getPixelValue(3, 2);
    check("getPixelValue returns float (API constraint)",
            Float.compare(fr, (float) v) == 0);

    // sanity: equivalent value through float would collapse
    float f = (float) v;
    check("(float) of that value differs from original (float collapse)",
            Double.compare(v, (double) f) != 0);
}

    private void testSnapshotReset(int w, int h) {
        DoubleProcessor dp = makeBase(w, h, 1.0e8, 0.01);
        double[] orig = copy((double[]) dp.getPixels());
        dp.snapshot();
        dp.add(123.456);
        dp.reset();
        double[] restored = (double[]) dp.getPixels();

        check("snapshot+reset: type preserved",
                dp.getBitDepth() == 64 && dp.getPixels() instanceof double[]);
        check("snapshot+reset: values restored bit-exactly",
                equalsBitExact(orig, restored));
    }

    private void testDuplicate(int w, int h) {
        DoubleProcessor dp = makeBase(w, h, 1.0e8, 0.01);
        ImageProcessor copy = dp.duplicate();

        check("duplicate: still 64-bit",
                copy instanceof DoubleProcessor
                        && copy.getBitDepth() == 64
                        && copy.getPixels() instanceof double[]);
        check("duplicate: values bit-exact",
                equalsBitExact((double[]) dp.getPixels(),
                        (double[]) copy.getPixels()));

        // Modify copy and ensure original unchanged
        ((DoubleProcessor) copy).add(1.0);
        check("duplicate: independence from original",
                !equalsBitExact((double[]) dp.getPixels(),
                        (double[]) copy.getPixels()));
    }

    private void testConvertToFloatPrecisionLoss(int w, int h) {
        // Demonstrate that converting to 32-bit loses precision the macro user can see.
        DoubleProcessor dp = makeBase(w, h, 1.0e16, 1.0); // each pixel differs by 1, undetectable in float
        FloatProcessor fp = (FloatProcessor) dp.convertToFloat();

        check("convertToFloat returns FloatProcessor (32-bit)",
                fp != null && fp.getBitDepth() == 32);

        double[] d = (double[]) dp.getPixels();
        float[]  f = (float[])  fp.getPixels();

        // Verify that adjacent doubles differ in double but collapse in float at 1e16
        // (Note: at 1e16, even doubles cannot represent each integer exactly,
        // so this only proves they collapse further in float.)
        boolean someCollapsed = false;
        for (int i = 1; i < d.length; i++) {
            if (Double.compare(d[i], d[i - 1]) != 0
                    && Float.compare(f[i], f[i - 1]) == 0) {
                someCollapsed = true;
                break;
            }
        }
        check("at 1e16: at least one pair distinct in double but equal in float",
                someCollapsed);
    }

    private void testBlitterAdd(int w, int h) {
        DoubleProcessor a = makeBase(w, h, 1.0e8, 0.01);
        DoubleProcessor b = makeBase(w, h, 0.0, 0.001);
        double[] aBefore = copy((double[]) a.getPixels());
        double[] bBefore = copy((double[]) b.getPixels());

        // a += b
        a.copyBits(b, 0, 0, Blitter.ADD);
        double[] aAfter = (double[]) a.getPixels();

        double[] ref = new double[aBefore.length];
        for (int i = 0; i < ref.length; i++)
            ref[i] = aBefore[i] + bBefore[i];

        check("blitter add: a still 64-bit",
                a.getBitDepth() == 64 && a.getPixels() instanceof double[]);
        check("blitter add: a == aBefore + b (bit-exact)",
                equalsBitExact(aAfter, ref));
    }

    private void testGetStatistics(int w, int h) {
        DoubleProcessor dp = makeBase(w, h, 1.0e8, 0.01);
        ij.process.ImageStatistics stats = dp.getStatistics();
        double expectedMin = 1.0e8;
        double expectedMax = 1.0e8 + (w * h - 1) * 0.01;

        check("stats.min approx", Math.abs(stats.min - expectedMin) < 1e-6);
        check("stats.max approx", Math.abs(stats.max - expectedMax) < 1e-6);
        // mean check
        double sum = 0;
        for (double v : (double[]) dp.getPixels()) sum += v;
        double meanRef = sum / dp.getPixelCount();
        check("stats.mean approx", Math.abs(stats.mean - meanRef) < 1e-6);
    }

    private void testTypePreservedOnOps(int w, int h) {
        // Apply a longish chain of ops and assert type never changes.
        DoubleProcessor dp = makePositive(w, h);
        dp.add(1.0);
        dp.multiply(2.0);
        dp.sqr();
        dp.sqrt();
        dp.subtract(0.5);
        dp.abs();
        dp.log();
        dp.exp();

        check("chained ops: still DoubleProcessor",
                dp.getClass() == DoubleProcessor.class);
        check("chained ops: still 64-bit",
                dp.getBitDepth() == 64
                        && dp.getPixels() instanceof double[]);
    }
}
