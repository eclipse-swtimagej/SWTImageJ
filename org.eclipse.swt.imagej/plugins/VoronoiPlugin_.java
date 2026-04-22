package ij.jts;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.FloatPolygon;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;

/**
 * Single-file ImageJ plugin that:
 *  - converts ImageJ ROIs <-> JTS Geometries (basic types),
 *  - computes Voronoi diagram from a PointRoi,
 *  - converts Voronoi polygons back to ImageJ PolygonRoi and adds them to an overlay,
 *  - optionally exports polygon ROIs to the ROI Manager.
 *
 * Usage:
 *  - Compile and place the JAR (with JTS on the classpath) into ImageJ/Fiji plugins folder.
 *  - Run the plugin; if an ImagePlus has a PointRoi selected it will use it, otherwise it can generate random points.
 */
public class VoronoiPlugin_ implements PlugIn {

    @Override
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        if (imp == null) {
            imp = IJ.createImage("Voronoi demo", "RGB black", 800, 600, 1);
        }

        GenericDialog gd = new GenericDialog("Voronoi Demo");
        gd.addMessage("Compute Voronoi diagram from PointRoi (if present) or random points.");
        gd.addNumericField("Random points (if no PointRoi selected):", 50, 0);
        gd.addNumericField("Random seed (0 = random):", 0, 0);
        gd.addCheckbox("Export Voronoi polygons to ROI Manager", true);
        gd.addCheckbox("Clear ROI Manager before adding", true);
        gd.showDialog();
        if (gd.wasCanceled()) return;
        int randomPoints = (int) gd.getNextNumber();
        int seed = (int) gd.getNextNumber();
        boolean exportToRoiManager = gd.getNextBoolean();
        boolean clearRoiManager = gd.getNextBoolean();
        if (seed == 0) seed = new Random().nextInt();

        // Obtain PointRoi if present
        Roi roi = imp.getRoi();
        PointRoi pts = null;
        if (roi instanceof PointRoi) {
            pts = (PointRoi) roi;
            IJ.log("Using existing PointRoi with " + pts.getNCoordinates() + " points.");
        } else {
            // create random points
            int cols = imp.getWidth();
            int rows = imp.getHeight();
            int n = Math.max(1, randomPoints);
            float[] xs = new float[n];
            float[] ys = new float[n];
            Random rnd = new Random(seed);
            for (int i = 0; i < n; i++) {
                xs[i] = rnd.nextInt(cols);
                ys[i] = rnd.nextInt(rows);
            }
            pts = new PointRoi(xs, ys, n);
            IJ.log("Generated " + n + " random points (seed=" + seed + ").");
        }

        // Build clipping envelope equal to image bounds
        Envelope clip = new Envelope(0, imp.getWidth(), 0, imp.getHeight());

        // Compute Voronoi diagram
        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), -1);
        Geometry voronoi;
        try {
            voronoi = voronoiFromPointRoi(pts, gf, clip);
            if (voronoi == null) {
                IJ.error("Voronoi builder returned null.");
                return;
            }
        } catch (Exception e) {
            IJ.error("Failed to compute Voronoi: " + e.getMessage());
            return;
        }

        // Convert Voronoi polygons to ROIs and add to overlay
        Overlay overlay = new Overlay();
        int nGeoms = voronoi.getNumGeometries();
        List<PolygonRoi> added = new ArrayList<>();
        for (int i = 0; i < nGeoms; i++) {
            Geometry cell = voronoi.getGeometryN(i);
            Roi cellRoi = geometryToRoi(cell);
            if (cellRoi instanceof PolygonRoi) {
                PolygonRoi pr = (PolygonRoi) cellRoi;
                java.awt.Color fill = new java.awt.Color(Color.HSBtoRGB((float) i / Math.max(1, nGeoms), 0.5f, 1.0f));
                pr.setFillColor(new java.awt.Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 80));
                pr.setStrokeColor(java.awt.Color.BLACK);
                pr.setName("voronoi_" + i);
                overlay.add(pr);
                added.add(pr);
            }
        }

        // also add the site points to overlay
        pts.setStrokeColor(java.awt.Color.RED);
        pts.setName("sites");
        overlay.add(pts);

        imp.setOverlay(overlay);
        imp.updateAndDraw();
        IJ.log("Voronoi diagram computed: " + added.size() + " cells added to overlay.");

        // Export to ROI Manager if requested
        if (exportToRoiManager) {
            RoiManager rm = RoiManager.getInstance();
            if (rm == null) {
                rm = new RoiManager();
            }
            if (clearRoiManager) {
                rm.reset();
            }
            for (int i = 0; i < added.size(); i++) {
                Roi r = added.get(i);
                try {
                    rm.addRoi(r);
                } catch (Exception e) {
                    IJ.log("Failed to add ROI to manager: " + e.getMessage());
                }
            }
            try {
                rm.addRoi(pts);
            } catch (Exception e) {
                IJ.log("Failed to add site points to ROI Manager: " + e.getMessage());
            }
            IJ.log("Exported " + added.size() + " polygon ROIs (+ sites) to ROI Manager.");
            rm.show();
        }
    }

    /* -------------------- Utility conversion & JTS helpers (all in this class) -------------------- */

    private static GeometryFactory defaultFactory() {
        return new GeometryFactory(new PrecisionModel(), -1);
    }

    /**
     * Convert ImageJ Roi -> JTS Geometry (basic support).
     */
    public static Geometry roiToGeometry(Roi roi, GeometryFactory gf) {
        if (roi == null) return null;
        if (gf == null) gf = defaultFactory();

        int type = roi.getType();

        // POINT (may contain many points)
        if (type == Roi.POINT) {
            FloatPolygon fp = roi.getFloatPolygon();
            if (fp == null) return null;
            Coordinate[] coords = floatPolygonToCoordinates(fp, false);
            return gf.createMultiPointFromCoords(coords);
        }

        // POLYGON-like
        if (type == Roi.POLYGON || type == Roi.FREEROI || type == Roi.TRACED_ROI) {
            FloatPolygon fp = roi.getFloatPolygon();
            if (fp == null) return null;
            Coordinate[] coords = floatPolygonToCoordinates(fp, true);
            if (coords.length < 4) {
                return gf.createLineString(coords);
            } else {
                LinearRing shell = gf.createLinearRing(coords);
                return gf.createPolygon(shell, null);
            }
        }

        // POLYLINE or LINE
        if (type == Roi.POLYLINE || type == Roi.FREELINE || roi instanceof Roi) {
            FloatPolygon fp = roi.getFloatPolygon();
            if (fp != null) {
                Coordinate[] coords = floatPolygonToCoordinates(fp, false);
                return gf.createLineString(coords);
            }
        }

        // RECTANGLE
        if (type == Roi.RECTANGLE) {
            Rectangle r = roi.getBounds();
            Coordinate[] coords = new Coordinate[] {
                    new Coordinate(r.x, r.y),
                    new Coordinate(r.x + r.width, r.y),
                    new Coordinate(r.x + r.width, r.y + r.height),
                    new Coordinate(r.x, r.y + r.height),
                    new Coordinate(r.x, r.y)
            };
            LinearRing shell = gf.createLinearRing(coords);
            return gf.createPolygon(shell, null);
        }

        // OVAL
        if (type == Roi.OVAL) {
            Rectangle r = roi.getBounds();
            return ovalBoundsToPolygon(r, 64, gf);
        }

        // fallback
        FloatPolygon fp = roi.getFloatPolygon();
        if (fp != null) {
            Coordinate[] coords = floatPolygonToCoordinates(fp, true);
            if (coords.length >= 4) {
                LinearRing shell = gf.createLinearRing(coords);
                return gf.createPolygon(shell, null);
            } else {
                return gf.createLineString(coords);
            }
        }
        return null;
    }

    /**
     * Convert JTS Geometry -> ImageJ Roi (basic support).
     */
    public static Roi geometryToRoi(Geometry geom) {
        if (geom == null) return null;

        if (geom instanceof Point) {
            Coordinate c = geom.getCoordinate();
            float[] xs = new float[] { (float) c.x };
            float[] ys = new float[] { (float) c.y };
            return new PointRoi(xs, ys, 1);
        }

        if (geom instanceof MultiPoint) {
            MultiPoint mp = (MultiPoint) geom;
            int n = mp.getNumGeometries();
            float[] xs = new float[n];
            float[] ys = new float[n];
            for (int i = 0; i < n; i++) {
                Point p = (Point) mp.getGeometryN(i);
                xs[i] = (float) p.getX();
                ys[i] = (float) p.getY();
            }
            return new PointRoi(xs, ys, n);
        }

        String type = geom.getGeometryType();
        if ("LineString".equals(type) || "MultiLineString".equals(type)) {
            Coordinate[] coords = geom.getCoordinates();
            float[] xs = new float[coords.length];
            float[] ys = new float[coords.length];
            for (int i = 0; i < coords.length; i++) {
                xs[i] = (float) coords[i].x;
                ys[i] = (float) coords[i].y;
            }
            return new PolygonRoi(xs, ys, coords.length, Roi.POLYLINE);
        }

        if ("Polygon".equals(type)) {
            Coordinate[] coords = geom.getCoordinates();
            float[] xs = new float[coords.length];
            float[] ys = new float[coords.length];
            for (int i = 0; i < coords.length; i++) {
                xs[i] = (float) coords[i].x;
                ys[i] = (float) coords[i].y;
            }
            return new PolygonRoi(xs, ys, coords.length, Roi.POLYGON);
        }

        // if multi-geometry, return first polygon found
        for (int i = 0; i < geom.getNumGeometries(); i++) {
            Geometry g2 = geom.getGeometryN(i);
            if ("Polygon".equals(g2.getGeometryType())) {
                return geometryToRoi(g2);
            }
        }

        return null;
    }

    /**
     * Build Voronoi diagram from a PointRoi (uses JTS VoronoiDiagramBuilder).
     */
    public static Geometry voronoiFromPointRoi(PointRoi pts, GeometryFactory gf, Envelope clip) {
        if (gf == null) gf = defaultFactory();
        if (pts == null) return null;
        FloatPolygon fp = pts.getFloatPolygon();
        if (fp == null) return null;
        Coordinate[] coords = floatPolygonToCoordinates(fp, false);
        List<Coordinate> coordsList = new ArrayList<>();
        for (Coordinate c : coords) coordsList.add(c);

        VoronoiDiagramBuilder builder = new VoronoiDiagramBuilder();
        builder.setSites(coordsList);
        if (clip != null) builder.setClipEnvelope(clip);

        return builder.getDiagram(gf);
    }

    /**
     * Delaunay edges from PointRoi (helper).
     */
    public static Geometry delaunayFromPointRoi(PointRoi pts, GeometryFactory gf) {
        if (gf == null) gf = defaultFactory();
        if (pts == null) return null;
        FloatPolygon fp = pts.getFloatPolygon();
        if (fp == null) return null;
        Coordinate[] coords = floatPolygonToCoordinates(fp, false);

        DelaunayTriangulationBuilder builder = new DelaunayTriangulationBuilder();
        builder.setSites(coords);
        return builder.getEdges(gf);
    }

    /* -------------------- internal helpers -------------------- */

    private static Coordinate[] floatPolygonToCoordinates(FloatPolygon fp, boolean close) {
        if (fp == null) return new Coordinate[0];
        int n = fp.npoints;
        if (n <= 0) return new Coordinate[0];
        boolean alreadyClosed = false;
        if (n > 1) {
            float x0 = fp.xpoints[0];
            float y0 = fp.ypoints[0];
            float xl = fp.xpoints[n - 1];
            float yl = fp.ypoints[n - 1];
            alreadyClosed = (x0 == xl && y0 == yl);
        }
        int outN = n + ((close && !alreadyClosed) ? 1 : 0);
        Coordinate[] coords = new Coordinate[outN];
        for (int i = 0; i < n; i++) {
            coords[i] = new Coordinate(fp.xpoints[i], fp.ypoints[i]);
        }
        if (outN > n) coords[outN - 1] = new Coordinate(fp.xpoints[0], fp.ypoints[0]);
        return coords;
    }

    private static Geometry ovalBoundsToPolygon(Rectangle r, int samples, GeometryFactory gf) {
        if (gf == null) gf = defaultFactory();
        if (r == null) return null;
        double cx = r.getX() + r.getWidth() / 2.0;
        double cy = r.getY() + r.getHeight() / 2.0;
        double rx = r.getWidth() / 2.0;
        double ry = r.getHeight() / 2.0;
        Coordinate[] coords = new Coordinate[samples + 1];
        for (int i = 0; i < samples; i++) {
            double theta = 2.0 * Math.PI * i / samples;
            double x = cx + rx * Math.cos(theta);
            double y = cy + ry * Math.sin(theta);
            coords[i] = new Coordinate(x, y);
        }
        coords[samples] = new Coordinate(coords[0]);
        LinearRing shell = gf.createLinearRing(coords);
        return gf.createPolygon(shell, null);
    }

    private static GeometryFactory defaultFactory() {
        return new GeometryFactory(new PrecisionModel(), -1);
    }
}
