import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.filter.*;
import java.util.stream.IntStream;

public class Parallel_Gaussian_Blur_ implements PlugInFilter {
    private double sigma = 2.0;

    public int setup(String arg, ImagePlus imp) {
        if (imp == null) return DONE;
        // Prompt user for Sigma value
        GenericDialog gd = new GenericDialog("Gaussian Blur");
        gd.addNumericField("Sigma (Radius):", sigma, 2);
        gd.showDialog();
        if (gd.wasCanceled()) return DONE;
        sigma = gd.getNextNumber();
        return DOES_ALL;
    }

    public void run(ImageProcessor ip) {
        int width = ip.getWidth();
        int height = ip.getHeight();
        float[] pixels = (float[]) ip.convertToFloatProcessor().getPixels();
        float[] result = new float[pixels.length];

        // 1. Generate Gaussian Kernel
        int radius = (int) Math.ceil(sigma * 3);
        float[] kernel = createGaussianKernel(sigma, radius);

        // 2. Parallel Processing (Horizontal Pass)
        // We use IntStream.parallel() to process rows in separate threads
        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                float sum = 0;
                for (int k = -radius; k <= radius; k++) {
                    int nx = Math.min(Math.max(x + k, 0), width - 1);
                    sum += pixels[y * width + nx] * kernel[k + radius];
                }
                result[y * width + x] = sum;
            }
        });

        // 3. Parallel Processing (Vertical Pass on Horizontal Result)
        float[] finalPixels = new float[pixels.length];
        IntStream.range(0, width).parallel().forEach(x -> {
            for (int y = 0; y < height; y++) {
                float sum = 0;
                for (int k = -radius; k <= radius; k++) {
                    int ny = Math.min(Math.max(y + k, 0), height - 1);
                    sum += result[ny * width + x] * kernel[k + radius];
                }
                finalPixels[y * width + x] = sum;
            }
        });

        // Update original ImageProcessor
        ip.setPixels(finalPixels);
    }

    private float[] createGaussianKernel(double sigma, int radius) {
        float[] kernel = new float[2 * radius + 1];
        double sigma2 = 2 * sigma * sigma;
        double sum = 0;
        for (int i = -radius; i <= radius; i++) {
            kernel[i + radius] = (float) Math.exp(-(i * i) / sigma2);
            sum += kernel[i + radius];
        }
        for (int i = 0; i < kernel.length; i++) kernel[i] /= sum;
        return kernel;
    }
}
