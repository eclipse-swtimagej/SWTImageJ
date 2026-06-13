import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.plugin.PlugIn;
import ij.process.DoubleProcessor;
import ij.process.ImageProcessor;

import java.io.File;

public class Test_64bit_Plugin_ implements PlugIn {

    private static final String OUT_DIR =
            System.getProperty("user.home") + File.separator + "test64";

    private int failures = 0;

    @Override
    public void run(String arg) {
        IJ.log("\\Clear");
        IJ.log("=== 64-bit plugin test ===");
        new File(OUT_DIR).mkdirs();
        IJ.log("Output dir: " + OUT_DIR);

        testSingleImage();
        testStack();
        testHyperstack();
        testFloatVsDoublePrecision();

        if (failures == 0)
            IJ.log("\n=== ALL JAVA 64-BIT TESTS PASSED ===");
        else
            IJ.log("\n=== " + failures + " TESTS FAILED ===");
    }

    // ------------------------------------------------------------------
    // 1. Single 64-bit image
    // ------------------------------------------------------------------
    private void testSingleImage() {
        IJ.log("\n-- single image --");
        int w = 32, h = 16;
        double[] pixels = new double[w * h];

        double base = 1.0e8;
        double step = 0.01;
        for (int i = 0; i < pixels.length; i++)
            pixels[i] = base + i * step;

        DoubleProcessor dp = new DoubleProcessor(w, h, pixels);
        dp.setMinAndMax(base, base + pixels.length * step);

        ImagePlus imp = new ImagePlus("test64-single", dp);
        check("bitDepth == 64", imp.getBitDepth() == 64);
        check("processor is DoubleProcessor", imp.getProcessor() instanceof DoubleProcessor);
        check("pixels is double[]", imp.getProcessor().getPixels() instanceof double[]);

        // Save
        String path = OUT_DIR + File.separator + "test64-single.tif";
        new FileSaver(imp).saveAsTiff(path);
        IJ.log("Saved: " + path);

        // Reopen
        ImagePlus reopened = new Opener().openImage(path);
        check("reopened not null", reopened != null);
        if (reopened == null) return;

        check("reopened bitDepth == 64", reopened.getBitDepth() == 64);
        check("reopened processor is DoubleProcessor",
                reopened.getProcessor() instanceof DoubleProcessor);

        double[] reopenedPixels = (double[]) reopened.getProcessor().getPixels();
        boolean exact = true;
        for (int i = 0; i < pixels.length; i++) {
            if (Double.compare(pixels[i], reopenedPixels[i]) != 0) {
                exact = false;
                IJ.log("DIFF at " + i + ": " + pixels[i] + " vs " + reopenedPixels[i]);
                break;
            }
        }
        check("all pixel values preserved bit-exactly", exact);
    }

    // ------------------------------------------------------------------
    // 2. 64-bit stack
    // ------------------------------------------------------------------
    private void testStack() {
        IJ.log("\n-- stack --");
        int w = 16, h = 8;
        int nSlices = 4;
        ImageStack stack = new ImageStack(w, h);
        for (int s = 1; s <= nSlices; s++) {
            double[] px = new double[w * h];
            for (int i = 0; i < px.length; i++)
                px[i] = 1.0e8 + s * 1000.0 + i * 0.01;
            stack.addSlice("slice " + s, new DoubleProcessor(w, h, px));
        }
        ImagePlus imp = new ImagePlus("test64-stack", stack);

        check("stack bitDepth == 64", imp.getBitDepth() == 64);
        check("stack size == " + nSlices, imp.getStackSize() == nSlices);

        String path = OUT_DIR + File.separator + "test64-stack.tif";
        new FileSaver(imp).saveAsTiffStack(path);
        IJ.log("Saved stack: " + path);

        ImagePlus reopened = new Opener().openImage(path);
        check("reopened stack not null", reopened != null);
        if (reopened == null) return;

        check("reopened stack bitDepth == 64", reopened.getBitDepth() == 64);
        check("reopened stack size == " + nSlices,
                reopened.getStackSize() == nSlices);

        boolean exact = true;
        outer:
        for (int s = 1; s <= nSlices; s++) {
            double[] orig = (double[]) stack.getProcessor(s).getPixels();
            double[] back = (double[]) reopened.getStack().getProcessor(s).getPixels();
            for (int i = 0; i < orig.length; i++) {
                if (Double.compare(orig[i], back[i]) != 0) {
                    exact = false;
                    IJ.log("DIFF slice " + s + " idx " + i +
                            ": " + orig[i] + " vs " + back[i]);
                    break outer;
                }
            }
        }
        check("all stack values preserved bit-exactly", exact);
    }

    // ------------------------------------------------------------------
    // 3. 64-bit hyperstack
    // ------------------------------------------------------------------
    private void testHyperstack() {
        IJ.log("\n-- hyperstack --");
        int w = 8, h = 8;
        int c = 2, z = 2, t = 3;
        int n = c * z * t;
        ImageStack stack = new ImageStack(w, h);
        for (int i = 1; i <= n; i++) {
            double[] px = new double[w * h];
            for (int j = 0; j < px.length; j++)
                px[j] = 1.0e8 + i * 10.0 + j * 0.01;
            stack.addSlice("slice " + i, new DoubleProcessor(w, h, px));
        }
        ImagePlus imp = new ImagePlus("test64-hyper", stack);
        imp.setDimensions(c, z, t);
        imp.setOpenAsHyperStack(true);

        check("hyper bitDepth == 64", imp.getBitDepth() == 64);
        check("hyper dims c/z/t",
                imp.getNChannels() == c &&
                imp.getNSlices() == z &&
                imp.getNFrames() == t);

        String path = OUT_DIR + File.separator + "test64-hyper.tif";
        new FileSaver(imp).saveAsTiffStack(path);
        IJ.log("Saved hyperstack: " + path);

        ImagePlus reopened = new Opener().openImage(path);
        check("reopened hyper not null", reopened != null);
        if (reopened == null) return;

        check("reopened hyper bitDepth == 64", reopened.getBitDepth() == 64);
        check("reopened hyper dims c/z/t",
                reopened.getNChannels() == c &&
                reopened.getNSlices() == z &&
                reopened.getNFrames() == t);

        boolean exact = true;
        outer:
        for (int i = 1; i <= n; i++) {
            double[] orig = (double[]) stack.getProcessor(i).getPixels();
            double[] back = (double[]) reopened.getStack().getProcessor(i).getPixels();
            for (int j = 0; j < orig.length; j++) {
                if (Double.compare(orig[j], back[j]) != 0) {
                    exact = false;
                    IJ.log("DIFF slice " + i + " idx " + j +
                            ": " + orig[j] + " vs " + back[j]);
                    break outer;
                }
            }
        }
        check("all hyperstack values preserved bit-exactly", exact);
    }

    // ------------------------------------------------------------------
    // 4. Float-vs-double precision sanity test
    // ------------------------------------------------------------------
    private void testFloatVsDoublePrecision() {
        IJ.log("\n-- float-vs-double precision sanity --");
        double base = 1.0e16;
        double v1 = base + 1.0;
        double v2 = base + 2.0;

        check("v1 != v2 in double",
                Double.compare(v1, v2) != 0);

        check("(float)v1 == (float)v2 (float collapse)",
                Float.compare((float) v1, (float) v2) == 0);

        IJ.log("v1 = " + v1);
        IJ.log("v2 = " + v2);
        IJ.log("(float)v1 = " + (float) v1);
        IJ.log("(float)v2 = " + (float) v2);
    }

    // ------------------------------------------------------------------
    private void check(String desc, boolean cond) {
        if (cond)
            IJ.log("OK  : " + desc);
        else {
            failures++;
            IJ.log("FAIL: " + desc);
        }
    }
}
