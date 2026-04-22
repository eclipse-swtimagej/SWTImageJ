import ij.*;
import ij.process.*;
import ij.plugin.*;
import java.awt.Color;
import java.util.Random;
import java.util.stream.IntStream;

public class Parallel_Plasma_Cloud implements PlugIn {
    private int w = 2048, h = 2048; // Potenzen von 2 funktionieren am besten
    private Random ran = new Random();
    private float[][] grid;

    public void run(String arg) {
        grid = new float[w + 1][h + 1];
        ColorProcessor cp = new ColorProcessor(w, h);
        int[] pixels = (int[]) cp.getPixels();

        // 1. Ecken initialisieren
        grid[0][0] = ran.nextFloat();
        grid[w][0] = ran.nextFloat();
        grid[0][h] = ran.nextFloat();
        grid[w][h] = ran.nextFloat();

        // 2. Diamond-Square Berechnung (Iterativ)
        double scale = 0.5;
        for (int side = w; side > 1; side /= 2) {
            int half = side / 2;
            double stddev = scale;

            // Diamond Step - Parallelisiert pro Zeile
            int finalSide = side;
            IntStream.range(0, (w / side)).parallel().forEach(i -> {
                int x = i * finalSide + half;
                for (int y = half; y < h; y += finalSide) {
                    float avg = (grid[x - half][y - half] + grid[x + half][y - half] +
                                 grid[x - half][y + half] + grid[x + half][y + half]) / 4.0f;
                    grid[x][y] = avg + (float)(ran.nextGaussian() * stddev);
                }
            });

            // Square Step - Ebenfalls parallelisiert
            IntStream.range(0, (w / half + 1)).parallel().forEach(i -> {
                int x = i * half;
                for (int y = (i % 2 == 0) ? half : 0; y <= h; y += finalSide) {
                    float sum = 0;
                    int count = 0;
                    if (x >= half) { sum += grid[x - half][y]; count++; }
                    if (x + half <= w) { sum += grid[x + half][y]; count++; }
                    if (y >= half) { sum += grid[x][y - half]; count++; }
                    if (y + half <= h) { sum += grid[x][y + half]; count++; }
                    grid[x][y] = (sum / count) + (float)(ran.nextGaussian() * stddev);
                }
            });
            scale /= 2.0;
        }

        // 3. Rendering: Grid -> Pixel-Array (Parallel)
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                // Nutze HSB für den typischen Plasma-Look
                pixels[y * w + x] = Color.HSBtoRGB(grid[x][y], 0.8f, 0.8f);
            }
        });

        new ImagePlus("Parallel Plasma", cp).show();
    }
}
