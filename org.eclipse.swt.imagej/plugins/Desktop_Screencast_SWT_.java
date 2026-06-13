import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * ImageJ / SWTImageJ desktop screencast plugin.
 *
 * File:
 *   Desktop_Screencast_SWT_.java
 *
 * Capture modes:
 *   1. Whole desktop
 *   2. Selected area with editable SWT selection overlay
 *   3. Opened SWT application frame / Shell
 *
 * Usage:
 *   Run once to start capturing.
 *   Run again to stop capturing.
 */
public class Desktop_Screencast_SWT_ implements PlugIn {

    private static volatile boolean running = false;
    private static Thread worker;

    private volatile ImagePlus imp;

    private static final String MODE_WHOLE_DESKTOP = "Whole desktop";
    private static final String MODE_SELECTED_AREA = "Selected area";
    private static final String MODE_APPLICATION_FRAME = "Opened application frame";

    @Override
    public void run(String arg) {
        if (running) {
            stopCapture();
            return;
        }

        GenericDialog gd = new GenericDialog("Desktop Screencast SWT");

        gd.addChoice(
                "Capture mode",
                new String[]{
                        MODE_WHOLE_DESKTOP,
                        MODE_SELECTED_AREA,
                        MODE_APPLICATION_FRAME
                },
                MODE_WHOLE_DESKTOP
        );

        gd.addNumericField("Target FPS", 30, 0);
        gd.addCheckbox("Prefer SWT capture", true);

        gd.showDialog();

        if (gd.wasCanceled()) {
            return;
        }

        String mode = gd.getNextChoice();
        final int fps = Math.max(1, (int) gd.getNextNumber());
        final boolean preferSwt = gd.getNextBoolean();

        Rectangle captureBounds;

        try {
            captureBounds = resolveCaptureBounds(mode);
        } catch (Throwable t) {
            IJ.handleException(t);
            return;
        }

        if (captureBounds == null || captureBounds.width <= 0 || captureBounds.height <= 0) {
            IJ.error("Desktop Screencast", "No valid capture area selected.");
            return;
        }

        final Rectangle finalBounds = new Rectangle(
                captureBounds.x,
                captureBounds.y,
                captureBounds.width,
                captureBounds.height
        );

        running = true;

        worker = new Thread(() -> {
            try {
                captureLoop(fps, finalBounds, preferSwt);
            } catch (Throwable t) {
                IJ.handleException(t);
            } finally {
                running = false;
                IJ.showStatus("Desktop screencast stopped");
            }
        }, "ImageJ-Desktop-Screencast-SWT");

        worker.setDaemon(true);
        worker.start();

        IJ.showStatus("Desktop screencast started. Run plugin again to stop.");
    }

    private Rectangle resolveCaptureBounds(String mode) {
        Display display = getSwtDisplay();

        if (MODE_WHOLE_DESKTOP.equals(mode)) {
            if (display != null && !display.isDisposed()) {
                final Rectangle[] box = new Rectangle[1];

                runOnDisplay(display, () -> {
                    Rectangle b = display.getBounds();
                    box[0] = new Rectangle(b.x, b.y, b.width, b.height);
                });

                return box[0];
            }

            java.awt.Rectangle awt = getAwtVirtualDesktopBounds();
            return new Rectangle(awt.x, awt.y, awt.width, awt.height);
        }

        if (MODE_SELECTED_AREA.equals(mode)) {
            if (display == null || display.isDisposed()) {
                IJ.error(
                        "Desktop Screencast",
                        "Selected-area mode requires an SWT Display."
                );
                return null;
            }

            RectangleSelectionDialog dialog = new RectangleSelectionDialog(display);
            return dialog.open();
        }

        if (MODE_APPLICATION_FRAME.equals(mode)) {
            if (display == null || display.isDisposed()) {
                IJ.error(
                        "Desktop Screencast",
                        "Application-frame mode requires an SWT Display."
                );
                return null;
            }

            ApplicationFrameSelectionDialog dialog =
                    new ApplicationFrameSelectionDialog(display);

            return dialog.open();
        }

        return null;
    }

    private void captureLoop(
            int fps,
            Rectangle captureBounds,
            boolean preferSwt
    ) throws Exception {

        final long framePeriodNs = 1_000_000_000L / fps;

        CaptureBackend backend = null;

        if (preferSwt) {
            try {
                backend = new SwtCaptureBackend(captureBounds);
                IJ.log("Desktop Screencast: using SWT capture backend");
            } catch (Throwable t) {
                IJ.log("Desktop Screencast: SWT capture unavailable, falling back to AWT Robot: " + t);
            }
        }

        if (backend == null) {
            backend = new RobotCaptureBackend(captureBounds);
            IJ.log("Desktop Screencast: using AWT Robot capture backend");
        }

        int[] pixels = new int[backend.width() * backend.height()];
        ColorProcessor cp = new ColorProcessor(backend.width(), backend.height(), pixels);

        imp = new ImagePlus("Desktop Screencast", cp);
        imp.show();

        long nextFrame = System.nanoTime();

        while (running) {
            if (imp == null) {
                break;
            }

            if (imp.getImage() == null) {
                break;
            }

            long start = System.nanoTime();

            backend.captureInto(pixels);

            cp.setPixels(pixels);
            imp.setProcessor(cp);
            imp.updateAndDraw();

            long elapsed = System.nanoTime() - start;

            IJ.showStatus(
                    "Desktop screencast: "
                            + backend.width()
                            + "x"
                            + backend.height()
                            + ", frame "
                            + String.format("%.2f", elapsed / 1_000_000.0)
                            + " ms"
            );

            nextFrame += framePeriodNs;
            long sleepNs = nextFrame - System.nanoTime();

            if (sleepNs > 0) {
                Thread.sleep(
                        sleepNs / 1_000_000L,
                        (int) (sleepNs % 1_000_000L)
                );
            } else {
                nextFrame = System.nanoTime();
                Thread.yield();
            }
        }

        backend.dispose();
    }

    private static void stopCapture() {
        running = false;

        if (worker != null) {
            worker.interrupt();
        }

        IJ.showStatus("Stopping desktop screencast...");
    }

    private interface CaptureBackend {
        int width();

        int height();

        void captureInto(int[] dst);

        void dispose();
    }

    private static final class SwtCaptureBackend implements CaptureBackend {
        private final Display display;
        private final Rectangle captureBounds;
        private final Image image;
        private final int width;
        private final int height;

        SwtCaptureBackend(Rectangle captureBounds) {
            Display d = getSwtDisplay();

            if (d == null || d.isDisposed()) {
                throw new IllegalStateException("No SWT Display is available");
            }

            this.display = d;
            this.captureBounds = new Rectangle(
                    captureBounds.x,
                    captureBounds.y,
                    captureBounds.width,
                    captureBounds.height
            );

            this.width = captureBounds.width;
            this.height = captureBounds.height;

            final Image[] imageBox = new Image[1];

            runOnDisplay(display, () ->
                    imageBox[0] = new Image(display, this.width, this.height)
            );

            this.image = imageBox[0];
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public void captureInto(int[] dst) {
            final ImageData[] dataBox = new ImageData[1];

            runOnDisplay(display, () -> {
                GC gc = null;

                try {
                    gc = new GC(display);
                    gc.copyArea(image, captureBounds.x, captureBounds.y);
                    dataBox[0] = image.getImageData();
                } finally {
                    if (gc != null && !gc.isDisposed()) {
                        gc.dispose();
                    }
                }
            });

            imageDataToIntRgb(dataBox[0], dst);
        }

        @Override
        public void dispose() {
            runOnDisplay(display, () -> {
                if (image != null && !image.isDisposed()) {
                    image.dispose();
                }
            });
        }
    }

    private static final class RobotCaptureBackend implements CaptureBackend {
        private final Robot robot;
        private final java.awt.Rectangle rect;
        private final int width;
        private final int height;

        RobotCaptureBackend(Rectangle captureBounds) throws AWTException {
            this.robot = new Robot();

            this.rect = new java.awt.Rectangle(
                    captureBounds.x,
                    captureBounds.y,
                    captureBounds.width,
                    captureBounds.height
            );

            this.width = rect.width;
            this.height = rect.height;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public void captureInto(int[] dst) {
            BufferedImage bi = robot.createScreenCapture(rect);

            if (bi.getRaster().getDataBuffer() instanceof DataBufferInt
                    && bi.getType() == BufferedImage.TYPE_INT_RGB) {

                int[] src = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();
                System.arraycopy(src, 0, dst, 0, Math.min(src.length, dst.length));

            } else {
                bi.getRGB(0, 0, width, height, dst, 0, width);
            }
        }

        @Override
        public void dispose() {
            // Nothing to dispose.
        }
    }

/**
 * macOS-safe editable selection rectangle.
 *
 * This version does NOT create a full-screen transparent overlay shell.
 * A full-screen SWT shell can black out the desktop on macOS because SWT does
 * not provide reliable per-pixel transparency for that use case.
 *
 * Instead, this creates a floating rectangle shell that can be moved and resized
 * using 8 anchor handles. Only the rectangle shell is drawn; the rest of the
 * desktop is untouched.
 */
private static final class RectangleSelectionDialog {
    private final Display display;

    private Rectangle result;
    private Rectangle selected;

    private boolean done;
    private boolean canceled;

    private Shell selectionShell;
    private Canvas canvas;

    private Shell controls;

    private Text xText;
    private Text yText;
    private Text wText;
    private Text hText;

    private int dragStartX;
    private int dragStartY;
    private Rectangle dragOriginal;

    private int dragMode = DRAG_NONE;

    private static final int MIN_SIZE = 32;
    private static final int HANDLE_SIZE = 12;
    private static final int BORDER = 6;

    private static final int DRAG_NONE = 0;
    private static final int DRAG_MOVE = 1;
    private static final int DRAG_N = 2;
    private static final int DRAG_NE = 3;
    private static final int DRAG_E = 4;
    private static final int DRAG_SE = 5;
    private static final int DRAG_S = 6;
    private static final int DRAG_SW = 7;
    private static final int DRAG_W = 8;
    private static final int DRAG_NW = 9;

    RectangleSelectionDialog(Display display) {
        this.display = display;
    }

    Rectangle open() {
        final Rectangle[] resultBox = new Rectangle[1];

        runOnDisplay(display, () -> resultBox[0] = openOnSwtThread());

        return resultBox[0];
    }

    private Rectangle openOnSwtThread() {
        done = false;
        canceled = false;
        result = null;

        Rectangle desktop = display.getBounds();

        selected = new Rectangle(
                desktop.x + desktop.width / 4,
                desktop.y + desktop.height / 4,
                desktop.width / 2,
                desktop.height / 2
        );

        createSelectionShell();
        createControlsShell();

        updateSelectionShellBounds();
        updateTextFields();

        selectionShell.open();
        controls.pack();
        controls.open();

        while (!done && !selectionShell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        if (controls != null && !controls.isDisposed()) {
            controls.dispose();
        }

        if (selectionShell != null && !selectionShell.isDisposed()) {
            selectionShell.dispose();
        }

        return canceled ? null : result;
    }

    private void createSelectionShell() {
    selectionShell = new Shell(display, SWT.NO_TRIM | SWT.ON_TOP | SWT.TOOL);
    selectionShell.setText("Capture Area");

    /*
     * This shell covers only the selected rectangle, not the whole desktop.
     * Therefore shell alpha is safe here, including on macOS.
     *
     * Lower value = more transparent.
     * Good values: 120 - 180.
     */
    selectionShell.setAlpha(145);

    selectionShell.setLayout(new GridLayout(1, false));

    canvas = new Canvas(selectionShell, SWT.DOUBLE_BUFFERED);
    canvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    canvas.setCursor(display.getSystemCursor(SWT.CURSOR_SIZEALL));

    /*
     * The shell/canvas background becomes the translucent selection fill,
     * because the whole small shell is alpha-blended.
     */
    canvas.setBackground(display.getSystemColor(SWT.COLOR_GRAY));

    canvas.addPaintListener(this::paintSelectionShell);

    canvas.addListener(SWT.MouseDown, e -> {
        dragStartX = selectionShell.getBounds().x + e.x;
        dragStartY = selectionShell.getBounds().y + e.y;
        dragOriginal = copyRect(selected);

        dragMode = hitTestLocal(e.x, e.y);

        updateCursorLocal(e.x, e.y);
    });

    canvas.addListener(SWT.MouseMove, e -> {
        if ((e.stateMask & SWT.BUTTON1) != 0 && dragMode != DRAG_NONE) {
            int gx = selectionShell.getBounds().x + e.x;
            int gy = selectionShell.getBounds().y + e.y;

            updateSelectionDuringDrag(gx, gy);
            updateSelectionShellBounds();
            updateTextFields();
        } else {
            updateCursorLocal(e.x, e.y);
        }
    });

    canvas.addListener(SWT.MouseUp, e -> {
        dragMode = DRAG_NONE;
        dragOriginal = null;
        updateCursorLocal(e.x, e.y);
    });

    selectionShell.addListener(SWT.KeyDown, e -> {
        if (e.keyCode == SWT.ESC) {
            cancel();
        }
    });
}
    private void createControlsShell() {
        controls = new Shell(selectionShell, SWT.ON_TOP | SWT.TOOL);
        controls.setText("Capture Area");
        controls.setLayout(new GridLayout(10, false));

        Rectangle desktop = display.getBounds();
        controls.setLocation(desktop.x + 40, desktop.y + 40);

        new Label(controls, SWT.NONE).setText("X");
        xText = new Text(controls, SWT.BORDER);
        xText.setLayoutData(textGridData());

        new Label(controls, SWT.NONE).setText("Y");
        yText = new Text(controls, SWT.BORDER);
        yText.setLayoutData(textGridData());

        new Label(controls, SWT.NONE).setText("W");
        wText = new Text(controls, SWT.BORDER);
        wText.setLayoutData(textGridData());

        new Label(controls, SWT.NONE).setText("H");
        hText = new Text(controls, SWT.BORDER);
        hText.setLayoutData(textGridData());

        Button apply = new Button(controls, SWT.PUSH);
        apply.setText("Apply");
        apply.addListener(SWT.Selection, e -> {
            applyTextFields();
            updateSelectionShellBounds();
            canvas.redraw();
        });

        Button ok = new Button(controls, SWT.PUSH);
        ok.setText("Start Capture");
        ok.addListener(SWT.Selection, e -> {
            applyTextFields();

            result = new Rectangle(
                    selected.x,
                    selected.y,
                    selected.width,
                    selected.height
            );

            done = true;

            if (!controls.isDisposed()) {
                controls.close();
            }

            if (!selectionShell.isDisposed()) {
                selectionShell.close();
            }
        });

        Button cancel = new Button(controls, SWT.PUSH);
        cancel.setText("Cancel");
        cancel.addListener(SWT.Selection, e -> cancel());
    }

    private void cancel() {
        canceled = true;
        done = true;

        if (controls != null && !controls.isDisposed()) {
            controls.close();
        }

        if (selectionShell != null && !selectionShell.isDisposed()) {
            selectionShell.close();
        }
    }

    private void updateSelectionShellBounds() {
        if (selectionShell == null || selectionShell.isDisposed()) {
            return;
        }

        selectionShell.setBounds(
                selected.x - BORDER,
                selected.y - BORDER,
                selected.width + BORDER * 2,
                selected.height + BORDER * 2
        );

        if (canvas != null && !canvas.isDisposed()) {
            canvas.redraw();
        }
    }

    private void paintSelectionShell(PaintEvent e) {
    GC gc = e.gc;

    int w = canvas.getBounds().width;
    int h = canvas.getBounds().height;

    int rx = BORDER;
    int ry = BORDER;
    int rw = Math.max(1, w - BORDER * 2);
    int rh = Math.max(1, h - BORDER * 2);

    Color gray = display.getSystemColor(SWT.COLOR_GRAY);
    Color red = display.getSystemColor(SWT.COLOR_RED);
    Color yellow = display.getSystemColor(SWT.COLOR_YELLOW);
    Color white = display.getSystemColor(SWT.COLOR_WHITE);
    Color black = display.getSystemColor(SWT.COLOR_BLACK);

    /*
     * Fill the small shell area. The transparency comes from
     * selectionShell.setAlpha(...), not from GC alpha.
     */
    gc.setAlpha(255);
    gc.setBackground(gray);
    gc.fillRectangle(0, 0, w, h);

    gc.setForeground(red);
    gc.setLineWidth(3);
    gc.drawRectangle(rx, ry, rw, rh);

    drawHandle(gc, rx, ry, yellow, red);
    drawHandle(gc, rx + rw / 2, ry, yellow, red);
    drawHandle(gc, rx + rw, ry, yellow, red);
    drawHandle(gc, rx + rw, ry + rh / 2, yellow, red);
    drawHandle(gc, rx + rw, ry + rh, yellow, red);
    drawHandle(gc, rx + rw / 2, ry + rh, yellow, red);
    drawHandle(gc, rx, ry + rh, yellow, red);
    drawHandle(gc, rx, ry + rh / 2, yellow, red);

    gc.setForeground(black);
    gc.drawText(
            selected.width + " x " + selected.height,
            rx + 9,
            ry + 9,
            true
    );

    gc.setForeground(white);
    gc.drawText(
            selected.width + " x " + selected.height,
            rx + 8,
            ry + 8,
            true
    );
}

    private void updateSelectionDuringDrag(int gx, int gy) {
        if (dragOriginal == null) {
            return;
        }

        int left = dragOriginal.x;
        int top = dragOriginal.y;
        int right = dragOriginal.x + dragOriginal.width;
        int bottom = dragOriginal.y + dragOriginal.height;

        int dx = gx - dragStartX;
        int dy = gy - dragStartY;

        switch (dragMode) {
            case DRAG_MOVE:
                selected = new Rectangle(
                        dragOriginal.x + dx,
                        dragOriginal.y + dy,
                        dragOriginal.width,
                        dragOriginal.height
                );
                break;

            case DRAG_N:
                selected = normalizeRect(left, gy, right, bottom);
                break;

            case DRAG_NE:
                selected = normalizeRect(left, gy, gx, bottom);
                break;

            case DRAG_E:
                selected = normalizeRect(left, top, gx, bottom);
                break;

            case DRAG_SE:
                selected = normalizeRect(left, top, gx, gy);
                break;

            case DRAG_S:
                selected = normalizeRect(left, top, right, gy);
                break;

            case DRAG_SW:
                selected = normalizeRect(gx, top, right, gy);
                break;

            case DRAG_W:
                selected = normalizeRect(gx, top, right, bottom);
                break;

            case DRAG_NW:
                selected = normalizeRect(gx, gy, right, bottom);
                break;

            default:
                break;
        }
    }

    private int hitTestLocal(int lx, int ly) {
        int w = canvas.getBounds().width;
        int h = canvas.getBounds().height;

        int rx = BORDER;
        int ry = BORDER;
        int rw = Math.max(1, w - BORDER * 2);
        int rh = Math.max(1, h - BORDER * 2);

        if (hitHandle(lx, ly, rx, ry)) {
            return DRAG_NW;
        }

        if (hitHandle(lx, ly, rx + rw / 2, ry)) {
            return DRAG_N;
        }

        if (hitHandle(lx, ly, rx + rw, ry)) {
            return DRAG_NE;
        }

        if (hitHandle(lx, ly, rx + rw, ry + rh / 2)) {
            return DRAG_E;
        }

        if (hitHandle(lx, ly, rx + rw, ry + rh)) {
            return DRAG_SE;
        }

        if (hitHandle(lx, ly, rx + rw / 2, ry + rh)) {
            return DRAG_S;
        }

        if (hitHandle(lx, ly, rx, ry + rh)) {
            return DRAG_SW;
        }

        if (hitHandle(lx, ly, rx, ry + rh / 2)) {
            return DRAG_W;
        }

        if (lx >= rx && lx <= rx + rw && ly >= ry && ly <= ry + rh) {
            return DRAG_MOVE;
        }

        return DRAG_NONE;
    }

    private void updateCursorLocal(int lx, int ly) {
        int mode = hitTestLocal(lx, ly);

        switch (mode) {
            case DRAG_N:
            case DRAG_S:
                canvas.setCursor(display.getSystemCursor(SWT.CURSOR_SIZENS));
                break;

            case DRAG_E:
            case DRAG_W:
                canvas.setCursor(display.getSystemCursor(SWT.CURSOR_SIZEWE));
                break;

            case DRAG_NE:
            case DRAG_SW:
                canvas.setCursor(display.getSystemCursor(SWT.CURSOR_SIZENESW));
                break;

            case DRAG_NW:
            case DRAG_SE:
                canvas.setCursor(display.getSystemCursor(SWT.CURSOR_SIZENWSE));
                break;

            case DRAG_MOVE:
                canvas.setCursor(display.getSystemCursor(SWT.CURSOR_SIZEALL));
                break;

            default:
                canvas.setCursor(display.getSystemCursor(SWT.CURSOR_ARROW));
                break;
        }
    }

    private boolean hitHandle(int x, int y, int hx, int hy) {
        int half = HANDLE_SIZE / 2;

        return x >= hx - half
                && x <= hx + half
                && y >= hy - half
                && y <= hy + half;
    }

    private Rectangle normalizeRect(int x1, int y1, int x2, int y2) {
        int x = Math.min(x1, x2);
        int y = Math.min(y1, y2);
        int w = Math.max(MIN_SIZE, Math.abs(x2 - x1));
        int h = Math.max(MIN_SIZE, Math.abs(y2 - y1));

        return new Rectangle(x, y, w, h);
    }

    private Rectangle copyRect(Rectangle r) {
        if (r == null) {
            return null;
        }

        return new Rectangle(r.x, r.y, r.width, r.height);
    }

    private void drawHandle(GC gc, int cx, int cy, Color fill, Color outline) {
        int half = HANDLE_SIZE / 2;

        gc.setAlpha(255);
        gc.setBackground(fill);
        gc.setForeground(outline);
        gc.setLineWidth(1);

        gc.fillRectangle(
                cx - half,
                cy - half,
                HANDLE_SIZE,
                HANDLE_SIZE
        );

        gc.drawRectangle(
                cx - half,
                cy - half,
                HANDLE_SIZE,
                HANDLE_SIZE
        );
    }

    private GridData textGridData() {
        GridData gd = new GridData();
        gd.widthHint = 70;
        return gd;
    }

    private void updateTextFields() {
        if (selected == null) {
            return;
        }

        if (xText == null || xText.isDisposed()) {
            return;
        }

        xText.setText(Integer.toString(selected.x));
        yText.setText(Integer.toString(selected.y));
        wText.setText(Integer.toString(selected.width));
        hText.setText(Integer.toString(selected.height));
    }

    private void applyTextFields() {
        try {
            int x = Integer.parseInt(xText.getText().trim());
            int y = Integer.parseInt(yText.getText().trim());
            int w = Math.max(MIN_SIZE, Integer.parseInt(wText.getText().trim()));
            int h = Math.max(MIN_SIZE, Integer.parseInt(hText.getText().trim()));

            selected = new Rectangle(x, y, w, h);

        } catch (NumberFormatException ex) {
            IJ.error(
                    "Invalid capture area",
                    "Please enter valid integer values for X, Y, W, and H."
            );
        }
    }
}

    /**
     * Selects an opened SWT Shell/frame from the current SWT application.
     *
     * Important:
     * This does not enumerate arbitrary external native application windows.
     * For that, platform-specific native APIs or JNA would be needed.
     */
    private static final class ApplicationFrameSelectionDialog {
        private final Display display;

        private Rectangle result;

        ApplicationFrameSelectionDialog(Display display) {
            this.display = display;
        }

        Rectangle open() {
            final Rectangle[] resultBox = new Rectangle[1];

            runOnDisplay(display, () -> resultBox[0] = openOnSwtThread());

            return resultBox[0];
        }

        private Rectangle openOnSwtThread() {
            result = null;

            final Shell[] shells = display.getShells();
            final java.util.List<Shell> candidates = new ArrayList<>();

            for (Shell shell : shells) {
                if (shell != null
                        && !shell.isDisposed()
                        && shell.isVisible()
                        && shell.getBounds().width > 0
                        && shell.getBounds().height > 0) {

                    candidates.add(shell);
                }
            }

            if (candidates.isEmpty()) {
                IJ.error(
                        "Desktop Screencast",
                        "No visible SWT application frames were found."
                );
                return null;
            }

            Shell dialog = new Shell(display, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
            dialog.setText("Select application frame");
            dialog.setLayout(new GridLayout(2, false));

            Label label = new Label(dialog, SWT.NONE);
            label.setText("Opened SWT frames:");
            label.setLayoutData(
                    new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1)
            );

            List list = new List(dialog, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);

            GridData listData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
            listData.widthHint = 520;
            listData.heightHint = 240;
            list.setLayoutData(listData);

            for (int i = 0; i < candidates.size(); i++) {
                Shell shell = candidates.get(i);
                Rectangle b = shell.getBounds();
                String title = shell.getText();

                if (title == null || title.trim().isEmpty()) {
                    title = "<untitled SWT frame>";
                }

                list.add(
                        i
                                + ": "
                                + title
                                + "  ["
                                + b.x
                                + ","
                                + b.y
                                + " "
                                + b.width
                                + "x"
                                + b.height
                                + "]"
                );
            }

            if (list.getItemCount() > 0) {
                list.select(0);
            }

            Button ok = new Button(dialog, SWT.PUSH);
            ok.setText("Start Capture");
            ok.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
            ok.addListener(SWT.Selection, e -> {
                int idx = list.getSelectionIndex();

                if (idx >= 0) {
                    Rectangle b = candidates.get(idx).getBounds();

                    result = new Rectangle(
                            b.x,
                            b.y,
                            b.width,
                            b.height
                    );
                }

                if (!dialog.isDisposed()) {
                    dialog.close();
                }
            });

            Button cancel = new Button(dialog, SWT.PUSH);
            cancel.setText("Cancel");
            cancel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
            cancel.addListener(SWT.Selection, e -> {
                result = null;

                if (!dialog.isDisposed()) {
                    dialog.close();
                }
            });

            dialog.addListener(SWT.KeyDown, e -> {
                if (e.keyCode == SWT.ESC) {
                    result = null;

                    if (!dialog.isDisposed()) {
                        dialog.close();
                    }
                }
            });

            dialog.pack();
            centerOnDesktop(display, dialog);
            dialog.open();

            while (!dialog.isDisposed()) {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            }

            return result;
        }
    }

    private static Display getSwtDisplay() {
        Display d = Display.getCurrent();

        if (d == null) {
            try {
                d = Display.getDefault();
            } catch (Throwable ignored) {
                d = null;
            }
        }

        return d;
    }

    private static void runOnDisplay(Display display, Runnable r) {
        if (display == null) {
            throw new SWTException("SWT Display is null");
        }

        if (display.isDisposed()) {
            throw new SWTException("SWT Display was disposed");
        }

        if (Thread.currentThread() == display.getThread()) {
            r.run();
        } else {
            display.syncExec(r);
        }
    }

    private static void centerOnDesktop(Display display, Shell shell) {
        Rectangle desktop = display.getBounds();
        Rectangle bounds = shell.getBounds();

        int x = desktop.x + (desktop.width - bounds.width) / 2;
        int y = desktop.y + (desktop.height - bounds.height) / 2;

        shell.setLocation(x, y);
    }

    private static java.awt.Rectangle getAwtVirtualDesktopBounds() {
        java.awt.Rectangle union = new java.awt.Rectangle();

        java.awt.GraphicsDevice[] devices =
                GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getScreenDevices();

        for (java.awt.GraphicsDevice device : devices) {
            union = union.union(device.getDefaultConfiguration().getBounds());
        }

        return union;
    }

    private static void imageDataToIntRgb(ImageData data, int[] dst) {
        PaletteData palette = data.palette;

        if (palette == null) {
            throw new IllegalArgumentException("Unsupported SWT ImageData: no palette");
        }

        if (palette.isDirect) {
            imageDataDirectToIntRgb(data, dst);
        } else {
            imageDataIndexedToIntRgb(data, dst);
        }
    }

    private static void imageDataDirectToIntRgb(ImageData data, int[] dst) {
        PaletteData palette = data.palette;

        int width = data.width;
        int height = data.height;

        int i = 0;

        for (int yy = 0; yy < height; yy++) {
            for (int xx = 0; xx < width; xx++) {
                int pixel = data.getPixel(xx, yy);

                int r = extractDirectColor(pixel, palette.redMask, palette.redShift);
                int g = extractDirectColor(pixel, palette.greenMask, palette.greenShift);
                int b = extractDirectColor(pixel, palette.blueMask, palette.blueShift);

                dst[i++] = (r << 16) | (g << 8) | b;
            }
        }
    }

    private static int extractDirectColor(int pixel, int mask, int shift) {
        int v = pixel & mask;

        if (shift < 0) {
            v >>>= -shift;
        } else {
            v <<= shift;
        }

        if (v < 0) {
            return 0;
        }

        if (v > 255) {
            return 255;
        }

        return v;
    }

    private static void imageDataIndexedToIntRgb(ImageData data, int[] dst) {
        int width = data.width;
        int height = data.height;

        int i = 0;

        for (int yy = 0; yy < height; yy++) {
            for (int xx = 0; xx < width; xx++) {
                RGB rgb = data.palette.getRGB(data.getPixel(xx, yy));

                dst[i++] = (rgb.red << 16) | (rgb.green << 8) | rgb.blue;
            }
        }
    }
}
