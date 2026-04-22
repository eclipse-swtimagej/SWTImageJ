import ij.*;
import ij.process.*;
import ij.plugin.PlugIn;
import java.util.stream.IntStream;

public class ImageJ_FastGameOfLife_Parallel_Caching_ implements PlugIn {

    int WIDTH = 10000, HEIGHT = 10000, n = 1000;

    public void run(String arg) {
        ShortProcessor ip = new ShortProcessor(WIDTH, HEIGHT);
        short[] pixelsA = (short[]) ip.getPixels();
        short[] pixelsB = new short[WIDTH * HEIGHT];
        for (int i = 0; i < pixelsA.length; i++) pixelsA[i] = (short)(Math.random() > 0.5 ? 255 : 0);

        ImagePlus imp = new ImagePlus("Turbo Game of Life", ip);
        imp.show();

        // Standard boundary offsets
        int[] xm = new int[WIDTH], xp = new int[WIDTH];
        for (int i = 0; i < WIDTH; i++) { xm[i] = (i - 1 + WIDTH) % WIDTH; xp[i] = (i + 1) % WIDTH; }
        int[] ym = new int[HEIGHT], yp = new int[HEIGHT];
        for (int i = 0; i < HEIGHT; i++) { ym[i] = ((i - 1 + HEIGHT) % HEIGHT) * WIDTH; yp[i] = ((i + 1) % HEIGHT) * WIDTH; }

        short[][] buffers = {pixelsA, pixelsB};

        for (int it = 0; it < n; it++) {
            if (IJ.escapePressed()) break;
            final short[] src = buffers[it % 2];
            final short[] dst = buffers[(it + 1) % 2];

            IntStream.range(0, HEIGHT).parallel().forEach(y -> {
                int offset = y * WIDTH;
                // CACHE ROW OFFSETS: Massive speedup by reducing array arithmetic
                int topRow = ym[y], midRow = offset, botRow = yp[y];

                for (int x = 0; x < WIDTH; x++) {
                    int l = xm[x], r = xp[x];
                    
                    // Direct sum: avoids masks if you know values are only 0 or 255
                    int neighbors = (src[l + topRow] & 0xff) + (src[x + topRow] & 0xff) + (src[r + topRow] & 0xff)
                                  + (src[l + midRow] & 0xff)                         + (src[r + midRow] & 0xff)
                                  + (src[l + botRow] & 0xff) + (src[x + botRow] & 0xff) + (src[r + botRow] & 0xff);

                    int self = src[x + midRow] & 0xff;
                    // Logic simplified: Birth on 3 neighbors, stay alive on 2
                    dst[x + offset] = (neighbors == 765 || (neighbors == 510 && self == 255)) ? (short)255 : 0;
                }
            });

            ip.setPixels(dst);
            imp.updateAndDraw();
            IJ.showStatus("Iteration: " + it);
        }
    }
}
