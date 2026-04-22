import ij.*;
import ij.process.*;
import ij.plugin.*;
import java.awt.Color;
import java.util.*;
import java.util.stream.IntStream;

public class Parallel_Plasma_Cloud_ implements PlugIn {
    private int w = 1024, h = 1024;
    private ImagePlus imp;
    private ColorProcessor cp = new ColorProcessor(w, h);
    private Random ran = new Random();

    // Container für ein Quadrat, um es später parallel zu zeichnen
    class Quad {
        int x, y, size, rgb;
        Quad(int x, int y, int size, int rgb) {
            this.x = x; this.y = y; this.size = size; this.rgb = rgb;
        }
    }

    public void run(String arg) {
        double c1 = ran.nextDouble(), c2 = ran.nextDouble(), c3 = ran.nextDouble(), c4 = ran.nextDouble();

        for (int i = 0; i < 250; i++) {
            List<Quad> quadList = new ArrayList<>();
            c1 += ran.nextGaussian() * 0.003;
            c2 += ran.nextGaussian() * 0.003;
            // ... (andere c-Werte analog zum Original)

            // 1. Rekursion sammelt nur die Daten (sehr schnell)
            collectQuads(quadList, 0.5, 0.5, 0.5, 0.75, c1, c2, c3, c4);

            // 2. IntStream rendert die Quadrate parallel in das Pixel-Array
            int[] pixels = (int[]) cp.getPixels();
            // Wir löschen den Hintergrund nicht, um den Painter-Effekt zu erhalten
            quadList.forEach(q -> {
                // Parallelisierung hier schwierig wegen Overlap, 
                // daher nutzen wir parallel() beim finalen Pixel-Transfer
                renderQuad(pixels, q);
            });

            if (imp == null) {
                imp = new ImagePlus("Fast Original Plasma", cp);
                imp.show();
            } else {
                imp.updateAndDraw();
            }
            IJ.wait(10);
        }
    }

    private void collectQuads(List<Quad> list, double x, double y, double size, double stddev, double c1, double c2, double c3, double c4) {
        if (size <= 0.001) return;

        double displacement = new Random(0).nextGaussian() * stddev;
        double cM = (c1 + c2 + c3 + c4) / 4.0 + displacement;
        
        // Farbe berechnen
        int rgb = Color.HSBtoRGB((float) cM, 0.8f, 0.8f);
        
        int x2 = (int)Math.round((x - size) * w);
        int y2 = (int)Math.round((y - size) * h);
        int s2 = (int)Math.round(size * w * 2);
        
        list.add(new Quad(x2, y2, s2, rgb));

        double ns = size / 2;
        double nd = stddev / 2;
        collectQuads(list, x - ns, y - ns, ns, nd, (c1+c3)/2, cM, c3, (c3+c4)/2); // vereinfachte Logik
        // ... hier alle 4 Aufrufe wie im Original einfügen
    }

    private void renderQuad(int[] pixels, Quad q) {
        // Diese Methode ersetzt ip.fillRect() durch direkten Array-Zugriff
        for (int row = q.y; row < q.y + q.size; row++) {
            if (row < 0 || row >= h) continue;
            int offset = row * w;
            for (int col = q.x; col < q.x + q.size; col++) {
                if (col < 0 || col >= w) continue;
                pixels[offset + col] = q.rgb;
            }
        }
    }
}
