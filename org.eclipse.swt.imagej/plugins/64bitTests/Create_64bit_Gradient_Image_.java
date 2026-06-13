import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.process.DoubleProcessor;

public class Create_64bit_Gradient_Image_ implements PlugIn {

    @Override
    public void run(String arg) {
        int width = 16;
        int height = 1;
        double[] pixels = new double[width * height];

        double base = 1.0e8;
        double step = 0.01;

        for (int x = 0; x < width; x++) {
            pixels[x] = base + x * step;
        }

        DoubleProcessor dp = new DoubleProcessor(width, height, pixels);
        dp.setMinAndMax(base, base + (width - 1) * step);

        ImagePlus imp = new ImagePlus("64-bit gradient doubles", dp);
        imp.show();

        IJ.log("\\Clear");
        for (int x = 0; x < width; x++) {
            IJ.log("pixel[" + x + "] = " + IJ.d2s(pixels[x], 12));
        }
    }
}
