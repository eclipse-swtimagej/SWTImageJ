import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.process.DoubleProcessor;
import ij.IJ;

import java.util.Random;

public class Create_64bit_Random_Image_ implements PlugIn {

    @Override
    public void run(String arg) {
        int width = 256;
        int height = 256;
        double[] pixels = new double[width * height];

        Random rng = new Random(12345L);

        // Large base value: unit-scale variation survives in double but not in float
        double base = 1.0e16;
        double maxOffset = 1000.0;

        for (int i = 0; i < pixels.length; i++) {
            double offset = rng.nextDouble() * maxOffset;
            pixels[i] = base + offset;
        }

        DoubleProcessor dp = new DoubleProcessor(width, height, pixels);
        dp.setMinAndMax(base, base + maxOffset);

        ImagePlus imp = new ImagePlus("64-bit random doubles", dp);
        imp.show();

        // Log a few sample values to show float collapse
        IJ.log("\\Clear");
        IJ.log("Sample values from 64-bit image:");
        for (int i = 0; i < 5; i++) {
            double d = pixels[i];
            float f = (float)d;
            IJ.log("pixel[" + i + "] double=" + d + " float=" + f + " delta=" + (d - (double)f));
        }

        double v1 = base + 1.0;
        double v2 = base + 2.0;
        IJ.log("");
        IJ.log("double comparison: " + v1 + " != " + v2 + " -> " + (v1 != v2));
        IJ.log("float comparison:  (float)v1 == (float)v2 -> " + ((float)v1 == (float)v2));
    }
}
