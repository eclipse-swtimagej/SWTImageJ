import ij.ImagePlus;
import ij.process.ShortProcessor;
import ij.plugin.PlugIn;
import ij.IJ;

public class ImageJ_FastGameOfLife_16bit_ implements PlugIn {
    int WIDTH = 3000;
    int HEIGHT = 3000;
    int n = 1000;
    ImagePlus imp;

    public void run(String arg) {
        // Create a 16-bit processor (ShortProcessor)
        ShortProcessor ip = new ShortProcessor(WIDTH, HEIGHT);
        short[] pixels = (short[]) ip.getPixels(); // Standard method to get raw array

        // Initialize with random noise
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (short) (Math.random() > 0.5 ? 255 : 0);
        }

        imp = new ImagePlus("Game of Life 16-bit", ip);
        imp.show();

        for (int it = 0; it < n; it++) {
            if (IJ.escapePressed()) {
                IJ.beep();
                break;
            }

            int w = ip.getWidth();
            int h = ip.getHeight();

            // SINGLE PASS: Calculate next state and store in bits 8-15
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int xm = (x - 1 + w) % w;
                    int xp = (x + 1) % w;
                    int ym = (y - 1 + h) % h;
                    int yp = (y + 1) % h;

                    // Read current state from lower 8 bits (& 0xff)
                    int count = (ip.get(xm, ym) & 0xff) + (ip.get(x, ym) & 0xff) + (ip.get(xp, ym) & 0xff)
                              + (ip.get(xm, y)  & 0xff)                         + (ip.get(xp, y)  & 0xff)
                              + (ip.get(xm, yp) & 0xff) + (ip.get(x, yp) & 0xff) + (ip.get(xp, yp) & 0xff);
                    
                    int neighbors = count / 255;
                    int currentState = ip.get(x, y) & 0xff;
                    int nextState = 0;

                    if (currentState == 255) {
                        if (neighbors == 2 || neighbors == 3) nextState = 255;
                    } else {
                        if (neighbors == 3) nextState = 255;
                    }

                    // Encode: Next gen in bits 8-15 | Current gen in bits 0-7
                    ip.set(x, y, (nextState << 8) | currentState);
                }
            }

            // Efficiently shift all pixels to the right to advance generations
            for (int i = 0; i < pixels.length; i++) {
                // Masking with 0xffff ensures correct unsigned 16-bit behavior before shift
                pixels[i] = (short) ((pixels[i] & 0xffff) >> 8);
            }

            imp.updateAndDraw();
            IJ.showStatus("Iteration: " + (it + 1));
        }
    }
}
