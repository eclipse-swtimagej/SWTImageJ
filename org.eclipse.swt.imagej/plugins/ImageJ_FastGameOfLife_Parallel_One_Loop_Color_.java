import ij.*;
import ij.process.*;
import ij.plugin.PlugIn;
import java.util.stream.IntStream;

/**
 * Fast Parallel Game of Life for ImageJ (ColorProcessor version)
 * - Replaces ByteProcessor/ShortProcessor with ColorProcessor/int[] for RGB display
 * - Alive pixels are white (0xFFFFFF), dead pixels are black (0x000000)
 * - Uses the same double-buffering and single-loop parallel per-row approach
 *
 * Notes:
 * - Neighbor counting is done as an integer count (0..8) instead of summing 0/255 values.
 * - Condition: birth when neighbors == 3; survive when neighbors == 2 and cell is alive.
 */
public class ImageJ_FastGameOfLife_Parallel_One_Loop_Color_ implements PlugIn {
    int WIDTH = 5000, HEIGHT = 5000, n = 1000;

    public void run(String arg) {
        // Initialize the ColorProcessor and buffers (int[] holds packed RGB)
        ColorProcessor cp = new ColorProcessor(WIDTH, HEIGHT);
        int[] pixelsA = (int[]) cp.getPixels();
        int[] pixelsB = new int[WIDTH * HEIGHT];

        // Randomize initial state: white = alive, black = dead
        for (int i = 0; i < pixelsA.length; i++) {
            pixelsA[i] = (Math.random() > 0.5) ? 0xFFFFFF : 0x000000;
        }

        ImagePlus imp = new ImagePlus("Turbo Game of Life (Color)", cp);
        imp.show();

        // Pre-calculate neighbor offsets for periodic boundaries (toroidal)
        int[] x_m = new int[WIDTH], x_p = new int[WIDTH];
        int[] y_m = new int[HEIGHT], y_p = new int[HEIGHT];
        for (int i = 0; i < WIDTH; i++) {
            x_m[i] = (i - 1 + WIDTH) % WIDTH;
            x_p[i] = (i + 1) % WIDTH;
        }
        for (int i = 0; i < HEIGHT; i++) {
            y_m[i] = ((i - 1 + HEIGHT) % HEIGHT) * WIDTH;
            y_p[i] = ((i + 1) % HEIGHT) * WIDTH;
        }

        int[][] buffers = { pixelsA, pixelsB };

        for (int it = 0; it < n; it++) {
            if (IJ.escapePressed()) break;

            final int[] currentGen = buffers[it % 2];
            final int[] nextGen = buffers[(it + 1) % 2];

            // Parallel per-row processing
            IntStream.range(0, HEIGHT).parallel().forEach(y -> {
                final int y_offset = y * WIDTH;
                final int ym = y_m[y];
                final int yp = y_p[y];

                for (int x = 0; x < WIDTH; x++) {
                    final int idx = x + y_offset;

                    // count alive neighbors (alive -> non-zero color)
                    int neighbors =
                            (currentGen[x_m[x] + ym] != 0 ? 1 : 0) + (currentGen[x + ym] != 0 ? 1 : 0) + (currentGen[x_p[x] + ym] != 0 ? 1 : 0)
                          + (currentGen[x_m[x] + y_offset] != 0 ? 1 : 0)                          + (currentGen[x_p[x] + y_offset] != 0 ? 1 : 0)
                          + (currentGen[x_m[x] + yp] != 0 ? 1 : 0) + (currentGen[x + yp] != 0 ? 1 : 0) + (currentGen[x_p[x] + yp] != 0 ? 1 : 0);

                    final boolean alive = currentGen[idx] != 0;

                    // Game of Life rules (count-based)
                    if (neighbors == 3 || (neighbors == 2 && alive)) {
                        nextGen[idx] = 0xFFFFFF; // white = alive
                    } else {
                        nextGen[idx] = 0x000000; // black = dead
                    }
                }
            });

            // Swap buffers by assigning pixel array to the processor and redraw
            cp.setPixels(nextGen);
            imp.updateAndDraw();

            IJ.showStatus("Iteration: " + (it + 1) + " (Press Esc to stop)");
        }
    }
}
