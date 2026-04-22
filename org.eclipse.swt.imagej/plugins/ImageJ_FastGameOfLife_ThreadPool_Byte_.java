import ij.*;
import ij.process.*;
import ij.plugin.PlugIn;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.swt.widgets.Display;

/**
 * Fast Game of Life plugin for SWT-hosted ImageJ (Bio7):
 * - Uses ByteProcessor (8-bit)
 * - Reuses a fixed thread pool; contiguous row blocks per thread
 * - Double-buffered arrays swapped by reference
 * - GUI updates posted on SWT Display thread via asyncExec
 */
public class ImageJ_FastGameOfLife_ThreadPool_Byte_ implements PlugIn {
    int WIDTH = 10000, HEIGHT = 10000, n = 1000;
    // update display every this many iterations (set to 1 for max visual fidelity, >1 for speed)
    int updateInterval = 1;

    @Override
    public void run(String arg) {
        final int size = WIDTH * HEIGHT;

        // ByteProcessor uses byte[] internally (8-bit), values 0..255
        final ByteProcessor bp = new ByteProcessor(WIDTH, HEIGHT);
        final byte[] pixelsA = (byte[]) bp.getPixels();
        final byte[] pixelsB = new byte[size];

        // Randomize initial state (0 or 255)
        for (int i = 0; i < size; i++) {
            pixelsA[i] = (byte) (Math.random() > 0.5 ? 255 : 0);
        }

        final ImagePlus imp = new ImagePlus("Turbo Game of Life (Byte, ThreadPool, SWT)", bp);
        imp.setDisplayRange(0, 255);
        imp.show();

        // Precompute neighbor offsets (toroidal wrap)
        final int[] x_m = new int[WIDTH], x_p = new int[WIDTH];
        final int[] y_m = new int[HEIGHT], y_p = new int[HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            x_m[x] = (x - 1 + WIDTH) % WIDTH;
            x_p[x] = (x + 1) % WIDTH;
        }
        for (int y = 0; y < HEIGHT; y++) {
            y_m[y] = ((y - 1 + HEIGHT) % HEIGHT) * WIDTH;
            y_p[y] = ((y + 1) % HEIGHT) * WIDTH;
        }

        // buffers and swap
        final byte[][] buffers = new byte[][] { pixelsA, pixelsB };

        // Thread pool - reuse threads and avoid stream overhead
        final int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors());
        final ExecutorService pool = Executors.newFixedThreadPool(threadCount, new ThreadFactory() {
            final ThreadFactory df = Executors.defaultThreadFactory();
            public Thread newThread(Runnable r) {
                Thread t = df.newThread(r);
                t.setDaemon(true);
                return t;
            }
        });

        long totalComputeNanos = 0;
        int computeIterations = 0;

        try {
            for (int it = 0; it < n; it++) {
                if (IJ.escapePressed()) break;

                final byte[] current = buffers[it & 1];
                final byte[] next = buffers[(it + 1) & 1];

                final long startCompute = System.nanoTime();

                // divide workload into roughly equal contiguous row ranges per worker
                final int rowsPerTask = (HEIGHT + threadCount - 1) / threadCount;
                final CountDownLatch latch = new CountDownLatch(threadCount);

                for (int t = 0; t < threadCount; t++) {
                    final int rowStart = t * rowsPerTask;
                    final int rowEnd = Math.min(HEIGHT, rowStart + rowsPerTask);

                    pool.submit(() -> {
                        final int w = WIDTH;
                        final int[] xm = x_m;
                        final int[] xp = x_p;
                        final int[] ym = y_m;
                        final int[] yp = y_p;
                        final byte[] cur = current;
                        final byte[] nxt = next;

                        for (int y = rowStart; y < rowEnd; y++) {
                            final int yoff = y * w;
                            final int yoff_m = ym[y];
                            final int yoff_p = yp[y];

                            for (int x = 0; x < w; x++) {
                                int neighbors =
                                        (cur[xm[x] + yoff_m] & 0xff) + (cur[x + yoff_m] & 0xff) + (cur[xp[x] + yoff_m] & 0xff)
                                      + (cur[xm[x] + yoff] & 0xff)                          + (cur[xp[x] + yoff] & 0xff)
                                      + (cur[xm[x] + yoff_p] & 0xff) + (cur[x + yoff_p] & 0xff) + (cur[xp[x] + yoff_p] & 0xff);

                                int currentState = cur[x + yoff] & 0xff;
                                if (neighbors == 765 || (neighbors == 510 && currentState == 255)) {
                                    nxt[x + yoff] = (byte) 255;
                                } else {
                                    nxt[x + yoff] = (byte) 0;
                                }
                            }
                        }
                        latch.countDown();
                    });
                }

                // wait for compute done
                latch.await();
                final long endCompute = System.nanoTime();
                totalComputeNanos += (endCompute - startCompute);
                computeIterations++;

                // periodically update the display (reduce repaint frequency for faster runs)
                if ((it % updateInterval) == 0 || it == n - 1) {
                    final byte[] toShow = next;

                    // Post update to SWT UI thread
                    Display d = Display.getDefault();
                    if (d != null && !d.isDisposed()) {
                        d.asyncExec(new Runnable() {
                            @Override
                            public void run() {
                                // pointer swap into ByteProcessor and paint
                                bp.setPixels(toShow);
                                imp.updateAndDraw();
                            }
                        });
                    } else {
                        // Fallback (best-effort) if SWT Display not available in this context:
                        bp.setPixels(toShow);
                        imp.updateAndDraw();
                    }
                }

                IJ.showStatus(String.format("Iteration: %d / %d  (avg compute ms: %.3f)  Press Esc to stop",
                        it + 1, n, (totalComputeNanos / 1e6) / (double) computeIterations));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
    }
}
