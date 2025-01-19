package ij.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.IndexColorModel;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.events.MouseWheelListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.imagej.editor.ExtendedPopupMenu;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.jfree.swt.SWTGraphics2D;
import org.jfree.swt.SWTUtils;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Menus;
import ij.Prefs;
import ij.macro.MacroRunner;
import ij.measure.ResultsTable;
import ij.plugin.PointToolOptions;
import ij.plugin.WandToolOptions;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.RoiManager;
import ij.plugin.tool.PlugInTool;
import ij.process.FloatPolygon;
import ij.util.Tools;

public class ImageCanvas extends Canvas implements MouseListener, MouseWheelListener, MouseTrackListener, Cloneable,
		MouseMoveListener, PaintListener, DisposeListener {

	protected int startX;
	protected int startY;
	protected boolean drag;
	protected int endX;
	protected int endY;
	private Display shellDisplay;
	protected org.eclipse.swt.graphics.Cursor defaultCursor = new org.eclipse.swt.graphics.Cursor(shellDisplay,
			SWT.CURSOR_ARROW);
	protected org.eclipse.swt.graphics.Cursor handCursor = new org.eclipse.swt.graphics.Cursor(shellDisplay,
			SWT.CURSOR_HAND);
	protected org.eclipse.swt.graphics.Cursor moveCursor = new org.eclipse.swt.graphics.Cursor(shellDisplay,
			SWT.CURSOR_SIZEALL);
	protected org.eclipse.swt.graphics.Cursor crosshairCursor = new org.eclipse.swt.graphics.Cursor(shellDisplay,
			SWT.CURSOR_CROSS);
	public static boolean usePointer = Prefs.usePointerCursor;
	protected ImagePlus imp;
	protected Image img; // the image to display!
	protected boolean imageUpdated;
	protected Rectangle srcRect;
	protected int imageWidth, imageHeight;
	protected int xMouse; // current cursor offscreen x location
	protected int yMouse; // current cursor offscreen y location
	private boolean showCursorStatus = true;
	private int sx2, sy2;
	private boolean disablePopupMenu;
	private static org.eclipse.swt.graphics.Color zoomIndicatorColor;
	private static Font smallFont, largeFont;
	private Font font;
	private Rectangle[] labelRects;
	private boolean maxBoundsReset;
	private Overlay showAllOverlay;
	private static final int LIST_OFFSET = 100000;
	private static Color showAllColor = Prefs.getColor(Prefs.SHOW_ALL_COLOR, new Color(0, 255, 255));
	private Color defaultColor = showAllColor;
	private static Color labelColor, bgColor;
	private int resetMaxBoundsCount;
	private Roi currentRoi;
	private int mousePressedX, mousePressedY;
	private long mousePressedTime;
	private boolean overOverlayLabel;
	private boolean isClicked;
	public float aspectRatioX = 1.0f;
	public float aspectRatioY = 1.0f;
	public boolean fitToParent = false;
	/**
	 * If the mouse moves less than this in screen pixels, successive zoom
	 * operations are on the same image pixel
	 */
	protected final static int MAX_MOUSEMOVE_ZOOM = 10;
	/**
	 * Screen coordinates where the last zoom operation was done (initialized to
	 * impossible value)
	 */
	protected int lastZoomSX = -9999999;
	protected int lastZoomSY = -9999999;
	/** Image (=offscreen) coordinates where the cursor was moved to for zooming */
	protected int zoomTargetOX = -1;
	protected int zoomTargetOY;
	protected ImageJ ij;
	protected double magnification;
	public int dstWidth;
	public int dstHeight;
	protected int xMouseStart;
	protected int yMouseStart;
	protected int xSrcStart;
	protected int ySrcStart;
	protected int flags;
	private boolean mouseExited = true;
	private boolean customRoi;
	private boolean drawNames;
	private AtomicBoolean paintPending;
	private boolean scaleToFit;
	private boolean painted;
	private boolean hideZoomIndicator;
	private boolean flattening;
	private Timer pressTimer;
	private Menu roiPopupMenu;
	private Rectangle imageBoundPos;
	private static int longClickDelay = 1000; // ms
	public GC gc;
	public Composite parent;
	private Image screenImage;
	private Menu embeddedPopupMenu;
	public Rectangle zoomRectangle;

	public Menu getEmbeddedPopupMenu() {

		return embeddedPopupMenu;
	}

	public void setEmbeddedPopupMenu(Menu embeddedPopupMenu) {

		this.embeddedPopupMenu = embeddedPopupMenu;
	}

	/*
	 * public ImageCanvasSwt(final Composite parent) { this(parent, SWT.NULL); }
	 */
	public static Composite createComposite(ImagePlus imp) {

		Shell parent = new Shell(Display.getDefault());
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout(1, true));
		comp.setSize(imp.getWidth(), imp.getHeight());
		return comp;
	}

	/* For Comapat. */
	public ImageCanvas(ImagePlus imp) {

		this(createComposite(imp), imp);
	}

	public ImageCanvas(final Composite parent, ImagePlus imp) {

		super(parent, SWT.TRANSPARENT | SWT.DOUBLE_BUFFERED);
		this.parent = parent;
		// super(parent, SWT.NULL | SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED);
		this.imp = imp;// implement later!
		// gc =new GC(this);
		paintPending = new AtomicBoolean(false);
		ij = IJ.getInstance();
		int width = imp.getWidth();
		int height = imp.getHeight();
		imageWidth = width;
		imageHeight = height;
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		setSize(imageWidth, imageHeight);
		magnification = 1.0;
		// addMouseMotionListener(this);
		// addKeyListener(ij); // ImageJ handles keyboard shortcuts
		// setFocusTraversalKeysEnabled(false);
		// setScaleToFit(true);
		/* SWT listener! */
		ij = IJ.getInstance();
		addKeyListener(ij);// by ImageJ?
		addMouseListener(this);
		addMouseWheelListener(this);
		// addDragDetectListener(this);
		addMouseMoveListener(this);
		addPaintListener(this);
		addMouseTrackListener(this);
		addDisposeListener(this);
		setCursor(crosshairCursor);
	}

	/* Are we in the plot modus (anisotropic view)? */
	public boolean isFitToParent() {

		return fitToParent;
	}

	public void setFitToParent(boolean fitToParent) {

		this.fitToParent = fitToParent;
	}

	/*
	 * Extra method for the SyncWindows class (in SWT the Window is not a frame
	 * object!)
	 */
	public ImageWindow getImageWindow() {

		return imp.getWindow();
	}

	public void updateImage(ImagePlus imp) {

		this.imp = imp;
		int width = imp.getWidth();
		int height = imp.getHeight();
		imageWidth = width;
		imageHeight = height;
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		setSize(imageWidth, imageHeight);
		magnification = 1.0;
	}

	/**
	 * Update this ImageCanvas to have the same zoom and scale settings as the one
	 * specified.
	 */
	public void update(ImageCanvas ic) {

		if (ic == null || ic == this || ic.imp == null)
			return;
		if (ic.imp.getWidth() != imageWidth || ic.imp.getHeight() != imageHeight)
			return;
		srcRect = new Rectangle(ic.srcRect.x, ic.srcRect.y, ic.srcRect.width, ic.srcRect.height);
		setMagnification(ic.magnification);
		setSize(ic.dstWidth, ic.dstHeight);
	}

	/** Sets the region of the image (in pixels) to be displayed. */
	public void setSourceRect(Rectangle r) {

		if (r == null)
			return;
		r = new Rectangle(r.x, r.y, r.width, r.height);
		imageWidth = imp.getWidth();
		imageHeight = imp.getHeight();
		if (r.x < 0)
			r.x = 0;
		if (r.y < 0)
			r.y = 0;
		if (r.width < 1)
			r.width = 1;
		if (r.height < 1)
			r.height = 1;
		if (r.width > imageWidth)
			r.width = imageWidth;
		if (r.height > imageHeight)
			r.height = imageHeight;
		if (r.x + r.width > imageWidth)
			r.x = imageWidth - r.width;
		if (r.y + r.height > imageHeight)
			r.y = imageHeight - r.height;
		if (srcRect == null)
			srcRect = r;
		else {
			srcRect.x = r.x;
			srcRect.y = r.y;
			srcRect.width = r.width;
			srcRect.height = r.height;
		}
		if (dstWidth == 0) {
			/* Changed for SWT! */
			Point sizePoint = getSize();
			Dimension size = new Dimension(sizePoint.x, sizePoint.y);// width and height?
			dstWidth = size.width;
			dstHeight = size.height;
		}
		magnification = (double) dstWidth / srcRect.width;
		imp.setTitle(imp.getTitle());
		if (IJ.debugMode)
			IJ.log("setSourceRect: " + magnification + " " + (int) (srcRect.height * magnification + 0.5) + " "
					+ dstHeight + " " + srcRect);
	}

	public void setSrcRect(Rectangle srcRect) {

		setSourceRect(srcRect);
	}

	public void setSrcRect(java.awt.Rectangle srcRect) {

		setSourceRect(SWTUtils.toSwtRectangle(srcRect));
	}

	public java.awt.Rectangle getSrcRect() {

		return SWTUtils.toAwtRectangleSimple(srcRect);
	}

	public org.eclipse.swt.graphics.Rectangle getSrcRectSwt() {

		return srcRect;
	}

	/** Obsolete; replaced by setSize() */
	public void setDrawingSize(int width, int height) {

		dstWidth = width;
		dstHeight = height;
		setSize(dstWidth, dstHeight);
	}

	/* Compatibility method for a thread access! */
	public Point getCanvasSize() {

		AtomicReference<Point> size = new AtomicReference<Point>();
		Display.getDefault().syncExec(() -> {

			size.set(ImageCanvas.this.getSize());

		});
		return size.get();
	}

	public void setSize(int width, int height) {

		Display.getDefault().syncExec(() -> {

			ImageCanvas.super.setSize(width, height);
			dstWidth = width;
			dstHeight = height;

		});
	}

	/**
	 * ImagePlus.updateAndDraw calls this method to force the paint() method to
	 * update the image from the ImageProcessor.
	 */
	public void setImageUpdated() {

		imageUpdated = true;
	}

	public void setPaintPending(boolean state) {

		paintPending.set(state);
	}

	public boolean getPaintPending() {

		return paintPending.get();
	}
	/*
	 * Changed for SWT Not needed for SWT Canvas! public void update(Graphics g) {
	 * paint(g); }
	 */

	@Override
	public void paintControl(PaintEvent event) {

		paint(event);
	}

	public SWTGraphics2D getGraphics() {

		SWTGraphics2D g = new SWTGraphics2D(gc);
		return g;
	}

	/* For SWT compatibility! */
	public void repaint() {

		Display.getDefault().syncExec(() -> {

			if (ImageCanvas.this.isDisposed() == false)
				redraw();

		});
	}

	/* For SWT compatibility! */
	public void repaint(int x, int y, int width, int height) {

		Display.getDefault().syncExec(() -> {

			if (ImageCanvas.this.isDisposed() == false)
				redraw(x, y, width, height, false);

		});
	}

	/* Changed for SWT! */
	public void requestFocus() {

		this.forceFocus();
		// requestFocus();
	}

	/*
	 * Add a paint method (with a graphic context GC argument) again for the
	 * flatten() command!
	 */
	public void paint(GC gc) {

		Roi roi = imp.getRoi();
		Overlay overlay = imp.getOverlay();
		SWTGraphics2D g = new SWTGraphics2D(gc);
		if (overlay != null)
			drawOverlay(overlay, g);
		if (showAllOverlay != null)
			drawOverlay(showAllOverlay, g);
		if (roi != null)
			drawRoi(roi, g);
		g.dispose();
		gc.dispose();
	}

	/* Paint function without temp image (see next function below)! */
	public void paint(PaintEvent event) {

		if (IJ.isMacOSX()) {
			// DPIUtil.setDeviceZoom(100);
		}
		this.gc = event.gc;
		/* ImageJ paints starts here! */
		painted = true;
		Roi roi = imp.getRoi();
		Overlay overlay = imp.getOverlay();
		if (roi != null || overlay != null || showAllOverlay != null || Prefs.paintDoubleBuffered
				|| (IJ.isLinux() && magnification < 0.25)) {
			if (roi != null)
				roi.updatePaste();
		}
		try {
			if (imageUpdated) {
				imageUpdated = false;
				if (img != null) {
					img.dispose();
				}
				imp.updateImage();
			}
			img = imp.getSwtImage();
			Rectangle max = parent.getClientArea();
			int width = max.width;
			int height = max.height;
			if (IJ.isMacOSX() || IJ.isLinux()) {
				/* On Windows drastically reduces the speed of display! */
				gc.setInterpolation(SWT.NONE);
			}
			SWTGraphics2D g = new SWTGraphics2D(gc);
			setInterpolation(g, Prefs.interpolateScaledImages);
			if (img != null) {
				if (fitToParent) {
					gc.drawImage(img, srcRect.x, srcRect.y, srcRect.width, srcRect.height, 0, 0, width, height);
				}
				/* Isotropic rendering of the image (as in ImageJ by default)! */
				else {
					gc.drawImage(img, srcRect.x, srcRect.y, srcRect.width, srcRect.height, 0, 0,
							(int) (srcRect.width * magnification + 0.5), (int) (srcRect.height * magnification + 0.5));
				}
			}
			if (overlay != null)
				drawOverlay(overlay, g);
			if (showAllOverlay != null)
				drawOverlay(showAllOverlay, g);
			if (roi != null)
				drawRoi(roi, g);
			if (fitToParent == false) {
				if (srcRect.width < imageWidth || srcRect.height < imageHeight) {
					drawZoomIndicator(gc);
				}
			}
			// if (IJ.debugMode) showFrameRate(g);
			g.dispose();
		} catch (OutOfMemoryError e) {
			IJ.outOfMemory("Paint");
		}
		setPaintPending(false);
		// gc.dispose();
		if (IJ.isMacOSX()) {
			// DPIUtil.setDeviceZoom(200);
		}
	}

	/* Paint function with intermediate image */
	public void paint2(PaintEvent event) {

		if (IJ.isMacOSX()) {
			DPIUtil.setDeviceZoom(100);
		}
		this.gc = event.gc;
		/* ImageJ paints starts here! */
		painted = true;
		Roi roi = imp.getRoi();
		Overlay overlay = imp.getOverlay();
		if (roi != null || overlay != null || showAllOverlay != null || Prefs.paintDoubleBuffered
				|| (IJ.isLinux() && magnification < 0.25)) {
			if (roi != null)
				roi.updatePaste();
		}
		// gc.setAntialias(SWT.DEFAULT);
		// gc.setAdvanced(true);
		// gc.setAntialias(SWT.OFF);
		// gc.setInterpolation(SWT.OFF);
		// SWTGraphics2D g = new SWTGraphics2D(gc);
		/* Is clipping of visible area here necessary? */
		// gc.setClipping(srcRect);
		/*
		 * Alternative: all paint operations on one image (screenImage) as noted here:
		 * https://www.eclipse.org/articles/Article-Image-Viewer/Image_viewer.html
		 * However the polygon drawing is the most expensive operation.
		 */
		try {
			if (imageUpdated) {
				imageUpdated = false;
				if (img != null) {
					img.dispose();
				}
				imp.updateImage();
			}
			img = imp.getSwtImage();
			Rectangle max = parent.getClientArea();
			int width = max.width;
			int height = max.height;
			screenImage = new Image(Display.getDefault(), width, height);
			GC newGC = new GC(screenImage);
			// Transform transformOld = new Transform(newGC.getDevice());
			// newGC.getTransform(transformOld);
			// Transform transform = new Transform(newGC.getDevice());
			// transform.scale(2.0f, 1.0f);
			// newGC.setTransform(transform);
			if (IJ.isMacOSX() || IJ.isLinux()) {
				/* On Windows drastically reduces the speed of display! */
				newGC.setInterpolation(SWT.NONE);
			}
			SWTGraphics2D g = new SWTGraphics2D(newGC);
			setInterpolation(g, Prefs.interpolateScaledImages);
			// newGC.setClipping(this.getClientArea());
			if (img != null) {
				/*
				 * The source and destination coordinates are different from AWT (first comes
				 * the destination! Also the SWT API difference srcRect.width and srcRect.height
				 * where we give the width and height and not the calculated coordinate (e.g.,
				 * srcRect.y + srcRect.height)!)
				 */
				/*
				 * Anisotropic rendering of the image (like a plot - image is stretched to the
				 * current canvas size)!
				 */
				if (fitToParent) {
					gc.drawImage(img, srcRect.x, srcRect.y, srcRect.width, srcRect.height, 0, 0, width, height);
				}
				/* Isotropic rendering of the image (as in ImageJ by default)! */
				else {
					newGC.drawImage(img, srcRect.x, srcRect.y, srcRect.width, srcRect.height, 0, 0,
							(int) (srcRect.width * magnification + 0.5), (int) (srcRect.height * magnification + 0.5));
				}
			}
			if (overlay != null)
				drawOverlay(overlay, g);
			if (showAllOverlay != null)
				drawOverlay(showAllOverlay, g);
			if (roi != null)
				drawRoi(roi, g);
			if (fitToParent == false) {
				if (srcRect.width < imageWidth || srcRect.height < imageHeight) {
					drawZoomIndicator(newGC);
				}
			}
			// if (IJ.debugMode) showFrameRate(g);
			g.dispose();
			/* ImageJ methods stop here! */
			newGC.dispose();
			// transform.dispose();
			// transformOld.dispose();
			gc.drawImage(screenImage, 0, 0);
			screenImage.dispose();
		} catch (OutOfMemoryError e) {
			IJ.outOfMemory("Paint");
		}
		setPaintPending(false);
		// gc.dispose();
		if (IJ.isMacOSX()) {
			DPIUtil.setDeviceZoom(200);
		}
	}

	private void setInterpolation(Graphics g, boolean interpolate) {

		if (magnification == 1)
			return;
		else if (magnification < 1.0 || interpolate) {
			Object value = RenderingHints.VALUE_RENDER_QUALITY;
			((Graphics2D) g).setRenderingHint(RenderingHints.KEY_RENDERING, value);
		} else if (magnification > 1.0) {
			Object value = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
			((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, value);
		}
	}

	private void drawRoi(Roi roi, Graphics g) {

		if (roi == currentRoi) {
			Color lineColor = roi.getStrokeColor();
			Color fillColor = roi.getFillColor();
			float lineWidth = roi.getStrokeWidth();
			roi.setStrokeColor(null);
			roi.setFillColor(null);
			boolean strokeSet = roi.getStroke() != null;
			if (strokeSet)
				roi.setStrokeWidth(1);
			roi.draw(g);
			roi.setStrokeColor(lineColor);
			if (strokeSet)
				roi.setStrokeWidth(lineWidth);
			roi.setFillColor(fillColor);
			currentRoi = null;
		} else
			roi.draw(g);
	}

	public int getSliceNumber(String label) {

		if (label == null)
			return 0;
		int slice = 0;
		if (label.length() >= 14 && label.charAt(4) == '-' && label.charAt(9) == '-')
			slice = (int) Tools.parseDouble(label.substring(0, 4), 0);
		else if (label.length() >= 17 && label.charAt(5) == '-' && label.charAt(11) == '-')
			slice = (int) Tools.parseDouble(label.substring(0, 5), 0);
		else if (label.length() >= 20 && label.charAt(6) == '-' && label.charAt(13) == '-')
			slice = (int) Tools.parseDouble(label.substring(0, 6), 0);
		return slice;
	}

	private void drawOverlay(Overlay overlay, Graphics g) {

		if (imp != null && imp.getHideOverlay() && overlay != showAllOverlay)
			return;
		flattening = imp != null && ImagePlus.flattenTitle.equals(imp.getTitle());
		if (imp != null && showAllOverlay != null && overlay != showAllOverlay)
			overlay.drawLabels(false);
		Color labelColor = overlay.getLabelColor();
		if (labelColor == null)
			labelColor = Color.white;
		initGraphics(overlay, g, labelColor, Roi.getColor());
		int n = overlay.size();
		// if (IJ.debugMode) IJ.log("drawOverlay: "+n);
		int currentImage = imp != null ? imp.getCurrentSlice() : -1;
		int stackSize = imp.getStackSize();
		if (stackSize == 1)
			currentImage = -1;
		int channel = 0, slice = 0, frame = 0;
		boolean hyperstack = imp.isHyperStack();
		if (imp.getNChannels() > 1)
			hyperstack = true;
		if (hyperstack) {
			channel = imp.getChannel();
			slice = imp.getSlice();
			frame = imp.getFrame();
		}
		drawNames = overlay.getDrawNames() && overlay.getDrawLabels();
		boolean drawLabels = drawNames || overlay.getDrawLabels();
		if (drawLabels)
			labelRects = new Rectangle[n];
		else
			labelRects = null;
		font = overlay.getLabelFont();
		if (overlay.scalableLabels() && font != null) {
			double mag = getMagnification();
			if (mag != 1.0)
				font = font.deriveFont((float) (font.getSize() * mag));
		}
		Roi activeRoi = imp.getRoi();
		boolean roiManagerShowAllMode = overlay == showAllOverlay && !Prefs.showAllSliceOnly;
		for (int i = 0; i < n; i++) {
			if (overlay == null)
				break;
			Roi roi = overlay.get(i);
			if (roi == null)
				break;
			int c = roi.getCPosition();
			int z = roi.getZPosition();
			int t = roi.getTPosition();
			if (hyperstack) {
				int position = roi.getPosition();
				// IJ.log(c+" "+z+" "+t+" "+channel+" "+position+" "+roiManagerShowAllMode);
				if (position > 0) {
					if (z == 0 && imp.getNSlices() > 1)
						z = position;
					else if (t == 0)
						t = position;
				}
				if (((c == 0 || c == channel) && (z == 0 || z == slice) && (t == 0 || t == frame))
						|| roiManagerShowAllMode)
					drawRoi(g, roi, drawLabels ? i + LIST_OFFSET : -1);
			} else {
				int position = stackSize > 1 ? roi.getPosition() : 0;
				if (stackSize > 1 && position == 0 && c == 1) {
					if (z == 1)
						position = t;
					else if (t == 1)
						position = z;
				}
				if (position == 0 && stackSize > 1)
					position = getSliceNumber(roi.getName());
				if (position > 0 && imp.getCompositeMode() == IJ.COMPOSITE)
					position = 0;
				if (position == PointRoi.POINTWISE_POSITION)
					position = 0;
				if ((roi instanceof PointRoi) && Prefs.showAllPoints)
					position = 0;
				// IJ.log(c+" "+z+" "+t+" p="+position+"getP="+roi.getPosition()+"
				// "+roiManagerShowAllMode);
				if (position == 0 || position == currentImage || roiManagerShowAllMode)
					drawRoi(g, roi, drawLabels ? i + LIST_OFFSET : -1);
			}
		}
		((Graphics2D) g).setStroke(Roi.onePixelWide);
		drawNames = false;
		font = null;
	}

	void drawOverlay(Graphics g) {

		drawOverlay(imp.getOverlay(), g);
	}

	private void initGraphics(Overlay overlay, Graphics g, Color textColor, Color defaultColor) {

		if (smallFont == null) {
			smallFont = new Font("SansSerif", Font.PLAIN, 9);
			largeFont = IJ.font12;
		}
		if (textColor != null) {
			labelColor = textColor;
			if (overlay != null && overlay.getDrawBackgrounds()) {
				double brightness = (labelColor.getRed() + labelColor.getGreen() + labelColor.getBlue()) / 3.0;
				if (labelColor == Color.green)
					brightness = 255;
				bgColor = brightness <= 85 ? Color.white : Color.black;
			} else
				bgColor = null;
		} else {
			int red = defaultColor.getRed();
			int green = defaultColor.getGreen();
			int blue = defaultColor.getBlue();
			if ((red + green + blue) / 3 < 128)
				labelColor = Color.white;
			else
				labelColor = Color.black;
			bgColor = defaultColor;
		}
		this.defaultColor = defaultColor;
		Font font = overlay != null ? overlay.getLabelFont() : null;
		if (font != null && font.getSize() > 12)
			((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(defaultColor);
	}

	void drawRoi(Graphics g, Roi roi, int index) {

		ImagePlus imp2 = roi.getImage();
		roi.setImage(imp);
		Color saveColor = roi.getStrokeColor();
		if (saveColor == null && roi.getFillColor() == null)
			roi.setStrokeColor(defaultColor);
		if (roi.getStroke() == null)
			((Graphics2D) g).setStroke(Roi.onePixelWide);
		if (roi instanceof TextRoi)
			((TextRoi) roi).drawText(g);
		else
			roi.drawOverlay(g);
		roi.setStrokeColor(saveColor);
		if (index >= 0) {
			if (roi == currentRoi)
				g.setColor(Roi.getColor());
			else
				g.setColor(defaultColor);
			drawRoiLabel(g, index, roi);
		}
		if (imp2 != null)
			roi.setImage(imp2);
		else
			roi.setImage(null);
	}

	void drawRoiLabel(Graphics g, int index, Roi roi) {

		if (roi.isCursor())
			return;
		boolean pointRoi = roi instanceof PointRoi;
		java.awt.Rectangle r = roi.getBounds();
		calculateAspectRatio();
		int x = (int) (screenX(r.x) * aspectRatioX);
		int y = (int) (screenY(r.y) * aspectRatioY);
		double mag = getMagnification();
		int width = (int) (r.width * mag * aspectRatioX);
		int height = (int) (r.height * mag * aspectRatioY);
		int size = width > 40 || height > 40 ? 12 : 9;
		int pointSize = 0;
		int crossSize = 0;
		if (pointRoi) {
			pointSize = ((PointRoi) roi).getSize();
			switch (pointSize) {
			case 0:
			case 1:
				size = 9;
				break;
			case 2:
			case 3:
				size = 10;
				break;
			case 4:
				size = 12;
				break;
			}
			crossSize = pointSize + 10 + 2 * pointSize;
		}
		if (font != null) {
			g.setFont(font);
			size = font.getSize();
		} else if (size == 12)
			g.setFont(largeFont);
		else
			g.setFont(smallFont);
		boolean drawingList = index >= LIST_OFFSET;
		if (drawingList)
			index -= LIST_OFFSET;
		String label = "" + (index + 1);
		if (drawNames)
			label = roi.getName();
		if (label == null)
			return;
		FontMetrics metrics = g.getFontMetrics();
		int w = metrics.stringWidth(label);
		x = x + width / 2 - w / 2;
		y = y + height / 2 + Math.max(size / 2, 6);
		int h = metrics.getAscent() + metrics.getDescent();
		int xoffset = 0, yoffset = 0;
		if (pointRoi) {
			xoffset = 6 + pointSize;
			yoffset = h - 6 + pointSize;
		}
		if (bgColor != null) {
			int h2 = h;
			if (font != null && font.getSize() > 14)
				h2 = (int) (h2 * 0.8);
			g.setColor(bgColor);
			g.fillRoundRect(x - 1 + xoffset, y - h2 + 2 + yoffset, w + 1, h2 - 2, 5, 5);
		}
		if (labelRects != null && index < labelRects.length) {
			if (pointRoi) {
				int x2 = screenX(r.x);
				int y2 = screenY(r.y);
				int crossSize2 = crossSize / 2;
				labelRects[index] = new Rectangle(x2 - crossSize2, y2 - crossSize2, crossSize, crossSize);
			} else
				labelRects[index] = new Rectangle(x - 3, y - h + 1, w + 4, h);
		}
		// IJ.log("drawRoiLabel: "+" "+label+" "+x+" "+y+" "+flattening);
		g.setColor(labelColor);
		g.drawString(label, x + xoffset, y - 2 + yoffset);
		g.setColor(defaultColor);
	}

	/* Converted to SWT! */
	void drawZoomIndicator(GC gc) {

		if (hideZoomIndicator)
			return;
		int x1 = 10;
		int y1 = 10;
		double aspectRatio = (double) imageHeight / imageWidth;
		int w1 = 64;
		if (aspectRatio > 1.0)
			w1 = (int) (w1 / aspectRatio);
		int h1 = (int) (w1 * aspectRatio);
		if (w1 < 4)
			w1 = 4;
		if (h1 < 4)
			h1 = 4;
		int w2 = (int) (w1 * ((double) srcRect.width / imageWidth));
		int h2 = (int) (h1 * ((double) srcRect.height / imageHeight));
		if (w2 < 1)
			w2 = 1;
		if (h2 < 1)
			h2 = 1;
		int x2 = (int) (w1 * ((double) srcRect.x / imageWidth));
		int y2 = (int) (h1 * ((double) srcRect.y / imageHeight));
		if (zoomIndicatorColor == null)
			zoomIndicatorColor = getDisplay().getSystemColor(SWT.COLOR_BLUE);
		gc.setForeground(zoomIndicatorColor);
		gc.setBackground(zoomIndicatorColor);
		gc.setLineWidth(1);
		gc.drawRectangle(x1, y1, w1, h1);
		if (w2 * h2 <= 200 || w2 < 10 || h2 < 10)
			gc.fillRectangle(x1 + x2, y1 + y2, w2, h2);
		else
			gc.drawRectangle(x1 + x2, y1 + y2, w2, h2);
	}

	long firstFrame;
	int frames, fps;

	void showFrameRate(Graphics g) {

		frames++;
		if (System.currentTimeMillis() > firstFrame + 1000) {
			firstFrame = System.currentTimeMillis();
			fps = frames;
			frames = 0;
		}
		g.setColor(Color.white);
		g.fillRect(10, 12, 50, 15);
		g.setColor(Color.black);
		g.drawString((int) (fps + 0.5) + " fps", 10, 25);
	}

	public Dimension getPreferredSize() {

		return new Dimension(dstWidth, dstHeight);
	}

	/** Returns the current cursor location in image coordinates. */
	public Point getCursorLocSwt() {

		return new Point(xMouse, yMouse);
	}

	/** Returns the current cursor location in image coordinates. */
	public java.awt.Point getCursorLoc() {

		return new java.awt.Point(xMouse, yMouse);
	}

	/** Returns 'true' if the cursor is over this image. */
	public boolean cursorOverImage() {

		return !mouseExited;
	}

	/** Returns the mouse event modifiers. */
	public int getModifiers() {

		return flags;
	}

	/** Returns the ImagePlus object that is associated with this ImageCanvas. */
	public ImagePlus getImage() {

		return imp;
	}

	/** Sets the cursor based on the current tool and cursor location. */
	public void setCursor(int sx, int sy, int ox, int oy) {

		xMouse = ox;
		yMouse = oy;
		mouseExited = false;
		Roi roi = imp.getRoi();
		ImageWindow win = imp.getWindow();
		overOverlayLabel = false;
		if (win == null)
			return;
		if (IJ.spaceBarDown()) {
			setCursor(handCursor);
			return;
		}
		int id = Toolbar.getToolId();
		switch (id) {
		case Toolbar.MAGNIFIER:
			setCursor(moveCursor);
			break;
		case Toolbar.HAND:
			setCursor(handCursor);
			break;
		default: // selection tool
			PlugInTool tool = Toolbar.getPlugInTool();
			boolean arrowTool = roi != null && (roi instanceof Arrow) && tool != null
					&& "Arrow Tool".equals(tool.getToolName());
			if ((id >= Toolbar.CUSTOM1) && !arrowTool) {
				if (Prefs.usePointerCursor)
					setCursor(defaultCursor);
				else
					setCursor(crosshairCursor);
			} else if (roi != null && roi.getState() != roi.CONSTRUCTING && roi.isHandle(sx, sy) >= 0) {
				setCursor(handCursor);
			} else if ((imp.getOverlay() != null || showAllOverlay != null) && overOverlayLabel(sx, sy, ox, oy)
					&& (roi == null || roi.getState() != roi.CONSTRUCTING)) {
				overOverlayLabel = true;
				setCursor(handCursor);
			} else if (Prefs.usePointerCursor
					|| (roi != null && roi.getState() != roi.CONSTRUCTING && roi.contains(ox, oy)))
				setCursor(defaultCursor);
			else
				setCursor(crosshairCursor);
		}
	}

	private boolean overOverlayLabel(int sx, int sy, int ox, int oy) {

		Overlay o = showAllOverlay;
		if (o == null)
			o = imp.getOverlay();
		if (o == null || !o.isSelectable() || !o.isDraggable() || !o.getDrawLabels() || labelRects == null)
			return false;
		for (int i = o.size() - 1; i >= 0; i--) {
			/*
			 * For the particle analysis we reverse the aspect ratio scaling (division) for
			 * the label selections (rectangle contains), e.g., for the ROI Manager!
			 */
			if (labelRects != null && labelRects[i] != null
					&& labelRects[i].contains((int) (sx * aspectRatioX), (int) (sy * aspectRatioY))) {
				Roi roi = imp.getRoi();
				if (roi == null || !roi.contains(ox, oy))
					return true;
				else
					return false;
			}
		}
		return false;
	}

	/**
	 * Converts a screen x-coordinate to an offscreen x-coordinate (nearest pixel
	 * center).
	 */
	public int offScreenX(int sx) {

		return srcRect.x + (int) (sx / magnification);
	}

	/**
	 * Converts a screen y-coordinate to an offscreen y-coordinate (nearest pixel
	 * center).
	 */
	public int offScreenY(int sy) {

		return srcRect.y + (int) (sy / magnification);
	}

	/**
	 * Converts a screen x-coordinate to an offscreen x-coordinate (Roi coordinate
	 * of nearest pixel border).
	 */
	public int offScreenX2(int sx) {

		return srcRect.x + (int) Math.round(sx / magnification);
	}

	/**
	 * Converts a screen y-coordinate to an offscreen y-coordinate (Roi coordinate
	 * of nearest pixel border).
	 */
	public int offScreenY2(int sy) {

		return srcRect.y + (int) Math.round(sy / magnification);
	}

	/**
	 * Converts a screen x-coordinate to a floating-point offscreen x-coordinate.
	 */
	public double offScreenXD(int sx) {

		return srcRect.x + sx / magnification;
	}

	/**
	 * Converts a screen y-coordinate to a floating-point offscreen y-coordinate.
	 */
	public double offScreenYD(int sy) {

		return srcRect.y + sy / magnification;
	}

	/** Converts an offscreen x-coordinate to a screen x-coordinate. */
	public int screenX(int ox) {

		return (int) ((ox - srcRect.x) * magnification);
	}

	/** Converts an offscreen y-coordinate to a screen y-coordinate. */
	public int screenY(int oy) {

		return (int) ((oy - srcRect.y) * magnification);
	}

	/**
	 * Converts a floating-point offscreen x-coordinate to a screen x-coordinate.
	 */
	public int screenXD(double ox) {

		return (int) ((ox - srcRect.x) * magnification);
	}

	/**
	 * Converts a floating-point offscreen x-coordinate to a screen x-coordinate.
	 */
	public int screenYD(double oy) {

		return (int) ((oy - srcRect.y) * magnification);
	}

	public double getMagnification() {

		return magnification;
	}

	public void setMagnification(double magnification) {

		setMagnification2(magnification);
	}

	void setMagnification2(double magnification) {

		if (magnification > 32.0)
			magnification = 32.0;
		if (magnification < zoomLevels[0])
			magnification = zoomLevels[0];
		this.magnification = magnification;
		imp.setTitle(imp.getTitle());
	}

	/** Resizes the canvas when the user resizes the window. */
	void resizeCanvas(int width, int height) {

		ImageWindow win = imp.getWindow();
		// IJ.log("resizeCanvas: "+srcRect+" "+imageWidth+" "+imageHeight+"
		// "+width+" "+height+" "+dstWidth+" "+dstHeight+" "+win.maxBounds);
		if (!maxBoundsReset && (width > dstWidth || height > dstHeight) && win != null && win.maxBounds != null
				&& width != win.maxBounds.width - 10) {
			if (resetMaxBoundsCount != 0)
				resetMaxBounds(); // Works around problem that prevented window
			// from being larger than maximized size
			resetMaxBoundsCount++;
		}
		if (scaleToFit || IJ.altKeyDown()) {
			fitToWindow();
			return;
		}
		if (width > imageWidth * magnification)
			width = (int) (imageWidth * magnification);
		if (height > imageHeight * magnification)
			height = (int) (imageHeight * magnification);
		Point size = getSize();
		if (srcRect.width < imageWidth || srcRect.height < imageHeight
				|| (painted && (width != size.x || height != size.y))) {
			setSize(width, height);
			srcRect.width = (int) (dstWidth / magnification);
			srcRect.height = (int) (dstHeight / magnification);
			if ((srcRect.x + srcRect.width) > imageWidth)
				srcRect.x = imageWidth - srcRect.width;
			if ((srcRect.y + srcRect.height) > imageHeight)
				srcRect.y = imageHeight - srcRect.height;
			repaint();
		}
		// IJ.log("resizeCanvas2: "+srcRect+" "+dstWidth+" "+dstHeight+"
		// "+width+" "+height);
	}

	/* Changed for SWT! */
	public void fitToWindow() {

		ImageWindow window = imp.getWindow();
		AtomicReference<Rectangle> clientArea = new AtomicReference<Rectangle>();
		Display.getDefault().syncExec(() -> {

			clientArea.set(window.parentComposite.getClientArea());

		});
		double w = clientArea.get().width;
		double h = clientArea.get().height;
		double wRec = srcRect.width;
		double hRec = srcRect.height;
		double xmag = (double) (w / wRec);
		double ymag = (double) (h / hRec);
		setMagnification(Math.min(xmag, ymag));
		int width = (int) (imageWidth * magnification);
		int height = (int) (imageHeight * magnification);
		if (width == dstWidth && height == dstHeight)
			return;
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		setSize(width, height);
		window.scResizeEvent(width, height);
		Display.getDefault().syncExec(() -> {

			layout();

		});
	}

	public double outofBoundsMag() {

		ImageWindow window = imp.getWindow();
		Rectangle bounds = window.parentComposite.getClientArea();
		double w = bounds.width;
		double h = bounds.height;
		double magX = (w / imageWidth);
		double magY = (h / imageHeight);
		double maxMag = Math.max(magX, magY);
		return maxMag;
	}

	public double outWidthBounds() {

		ImageWindow window = imp.getWindow();
		Rectangle bounds = window.parentComposite.getClientArea();
		double w = bounds.width;
		double magX = (w / imageWidth);
		return magX;
	}

	public double outofHeightBounds() {

		ImageWindow window = imp.getWindow();
		Rectangle bounds = window.parentComposite.getClientArea();
		double h = bounds.height;
		double magY = (h / imageHeight);
		return magY;
	}

	void setMaxBounds() {

		if (maxBoundsReset) {
			maxBoundsReset = false;
			ImageWindow win = imp.getWindow();
			if (win != null && !IJ.isLinux() && win.maxBounds != null) {
				win.getShell().setMaximumSize(win.maxBounds.x, win.maxBounds.y);
				win.setMaxBoundsTime = System.currentTimeMillis();
			}
		}
	}

	void resetMaxBounds() {

		/*
		 * ImageWindow win = imp.getWindow(); if (win!=null &&
		 * (System.currentTimeMillis()-win.setMaxBoundsTime)>500L) {
		 * win.setMaximizedBounds(win.maxWindowBounds); maxBoundsReset = true; }
		 */
	}

	private static final double[] zoomLevels = { 1 / 72.0, 1 / 48.0, 1 / 32.0, 1 / 24.0, 1 / 16.0, 1 / 12.0, 1 / 8.0,
			1 / 6.0, 1 / 4.0, 1 / 3.0, 1 / 2.0, 0.75, 1.0, 2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 16.0, 24.0, 32.0 };
	/*
	 * private static final double[] zoomLevels = { 1 / 72.0, 1 / 48.0, 1 / 32.0, 1/
	 * 24.0, 1 / 16.0, 1 / 12.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0,
	 * 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 2.1, 2.2, 2.3, 2.4, 2.5,
	 * 2.6, 2.7, 2.8, 2.9, 3.0, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 4.0,
	 * 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 5.0, 5.1, 5.2, 5.3, 5.4, 5.5,
	 * 5.6, 5.7, 5.8, 5.9, 6.0, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9, 7.0,
	 * 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 8.0, 8.1, 8.2, 8.3, 8.4, 8.5,
	 * 8.6, 8.7, 8.8, 8.9, 9.0, 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8, 9.9, 10.0,
	 * 12.0, 16.0, 24.0, 32.0 };
	 */
	/*
	 * private static final double[] zoomLevels = {
	 * 0.01,0.02,0.05,0.06,0.07,0.08,0.09,0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8,
	 * 0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 2.1, 2.2, 2.3,
	 * 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 3.0, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8,
	 * 3.9, 4.0, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 5.0, 5.1, 5.2, 5.3,
	 * 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 6.0, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8,
	 * 6.9, 7.0, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 8.0, 8.1, 8.2, 8.3,
	 * 8.4, 8.5, 8.6, 8.7, 8.8, 8.9, 9.0, 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8,
	 * 9.9, 10.0, 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8, 10.9, 11.0, 11.1,
	 * 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.8, 11.9, 12.0, 12.1, 12.2, 12.3, 12.4,
	 * 12.5, 12.6, 12.7, 12.8, 12.9, 13.0, 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7,
	 * 13.8, 13.9, 14.0, 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8, 14.9, 15.0,
	 * 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7, 15.8, 15.9, 16.0, 24.0, 32.0 };
	 */

	public static double getLowerZoomLevel(double currentMag) {

		double newMag = zoomLevels[0];
		for (int i = 0; i < zoomLevels.length; i++) {
			if (zoomLevels[i] < currentMag)
				newMag = zoomLevels[i];
			else
				break;
		}
		return newMag;
	}

	public static double getHigherZoomLevel(double currentMag) {

		double newMag = 32.0;
		for (int i = zoomLevels.length - 1; i >= 0; i--) {
			if (zoomLevels[i] > currentMag)
				newMag = zoomLevels[i];
			else
				break;
		}
		return newMag;
	}

	/**
	 * Zooms in by making the window bigger. If it can't be made bigger, then makes
	 * the source rectangle (srcRect) smaller and centers it on the position in the
	 * image where the cursor was when zooming has started. Note that sx and sy are
	 * screen coordinates.
	 */
	public void zoomIn(int sx, int sy) {

		if (magnification >= 32)
			return;
		scaleToFit = false;
		boolean mouseMoved = sqr(sx - lastZoomSX) + sqr(sy - lastZoomSY) > MAX_MOUSEMOVE_ZOOM * MAX_MOUSEMOVE_ZOOM;
		lastZoomSX = sx;
		lastZoomSY = sy;
		if (mouseMoved || zoomTargetOX < 0) {
			boolean cursorInside = sx >= 0 && sy >= 0 && sx < dstWidth && sy < dstHeight;
			zoomTargetOX = offScreenX(cursorInside ? sx : dstWidth / 2); // where to zoom, offscreen (image) coordinates
			zoomTargetOY = offScreenY(cursorInside ? sy : dstHeight / 2);
		}
		double newMag = getHigherZoomLevel(magnification);
		int newWidth = (int) (imageWidth * newMag * aspectRatioX);
		int newHeight = (int) (imageHeight * newMag * aspectRatioY);
		Dimension newSize = canEnlarge(newWidth, newHeight);
		Rectangle[] clientArea = new Rectangle[1];
		Display.getDefault().syncExec(() -> {

			ImageWindow window = imp.getWindow();
			clientArea[0] = window.parentComposite.getClientArea();

		});
		// Rectangle clientArea = imp.getWindow().parentComposite.getClientArea();
		if (newSize != null) {
			setSize(newSize.width, newSize.height);
			imp.getWindow().scResizeEvent(newSize.width, newSize.height);
			if (newSize.width != newWidth || newSize.height != newHeight) {
				/*
				 * Changed for the SWT parent. If the next zoom level runs out of the max width
				 * we increase the size to the max of the parent!
				 */
				if (newSize.width != newWidth) {
					newSize = new Dimension(clientArea[0].width, newSize.height);
					setSize(newSize.width, newSize.height);
					imp.getWindow().scResizeEvent(newSize.width, newSize.height);
				}
				/*
				 * If the next zoom level runs out of the max height we increase the height to
				 * the max of the parent!
				 */
				else {
					/* Changed for the SWT parent. Resize to max parent! */
					newSize = new Dimension(newSize.width, clientArea[0].height);
					setSize(newSize.width, newSize.height);
					imp.getWindow().scResizeEvent(newSize.width, newSize.height);
				}
				adjustSourceRect(newMag, zoomTargetOX, zoomTargetOY);
			} else {
				setMagnification(newMag);
			}
			Display.getDefault().syncExec(() -> {

				imp.getWindow().parentComposite.layout();

			});
		} else { // can't enlarge window. Maximize it!
			newSize = new Dimension(clientArea[0].width, clientArea[0].height);
			setSize(newSize.width, newSize.height);
			imp.getWindow().scResizeEvent(newSize.width, newSize.height);
			adjustSourceRect(newMag, zoomTargetOX, zoomTargetOY);
		}
		repaint();
	}

	/** Centers the viewable area on offscreen (image) coordinates x, y */
	void adjustSourceRect(double newMag, int x, int y) {

		// IJ.log("adjustSourceRect1: "+newMag+" "+dstWidth+" "+dstHeight);
		int w = (int) Math.round(dstWidth / newMag / aspectRatioX);
		if (w * newMag < dstWidth)
			w++;
		int h = (int) Math.round(dstHeight / newMag / aspectRatioY);
		if (h * newMag < dstHeight)
			h++;
		Rectangle r = new Rectangle(x - w / 2, y - h / 2, w, h);
		if (r.x < 0)
			r.x = 0;
		if (r.y < 0)
			r.y = 0;
		if (r.x + w > imageWidth)
			r.x = imageWidth - w;
		if (r.y + h > imageHeight)
			r.y = imageHeight - h;
		srcRect = r;
		setMagnification(newMag);
		// IJ.log("adjustSourceRect2: "+srcRect+" "+dstWidth+" "+dstHeight);
	}

	/**
	 * Returns the size to which the window can be enlarged, or null if it can't be
	 * enlarged. <code>newWidth, newHeight</code> is the size needed for showing the
	 * full image at the magnification needed
	 */
	protected Dimension canEnlarge(int newWidth, int newHeight) {

		if (IJ.altKeyDown())
			return null;
		/*
		 * ImageWindow win = imp.getWindow(); if (win==null) return null; Rectangle r1 =
		 * imageWindowSwt.sComp.getBounds(); Insets insets = win.getInsets(); Point loc
		 * = getLocation(); if (loc.x>insets.left+5 || loc.y>insets.top+5) { r1.width =
		 * newWidth+insets.left+insets.right+ImageWindow.HGAP*2; r1.height =
		 * newHeight+insets.top+insets.bottom+ImageWindow.VGAP*2+win.getSliderHeight();
		 * } else { r1.width = r1.width - dstWidth + newWidth; r1.height = r1.height -
		 * dstHeight + newHeight; } Rectangle max = new Rectangle(0,0,500,500); boolean
		 * fitsHorizontally = r1.x+r1.width<max.x+max.width; boolean fitsVertically =
		 * r1.y+r1.height<max.y+max.height; if (fitsHorizontally && fitsVertically)
		 * return new Dimension(newWidth, newHeight); else if (fitsVertically &&
		 * newHeight<dstWidth) return new Dimension(dstWidth, newHeight); else if
		 * (fitsHorizontally && newWidth<dstHeight) return new Dimension(newWidth,
		 * dstHeight); else return null;
		 */
		Rectangle[] max = new Rectangle[1];
		Display.getDefault().syncExec(() -> {

			ImageWindow window = imp.getWindow();
			max[0] = window.parentComposite.getClientArea();

		});
		// Rectangle max = Display.getDefault().getPrimaryMonitor().getBounds();
		// Rectangle max =SWTUtils.toSwtRectangle( GUI.getMaxWindowBounds(new
		// JFrame()));
		// Rectangle r1 = getBounds();
		// System.out.println("Rectangle:" + newWidth + " " + newHeight + " Max
		// Rectangle: " + max.width + " " + max.height
		// + " Current ImageCanvas: " + r1.width + " " + r1.height);
		// r1.width = newWidth;
		// r1.height = newHeight;
		// System.out.println("ImageCanvas: "+r1);
		// System.out.println(newWidth+" "+ newHeight);
		/*
		 * boolean fitsHorizontally = newWidth <= max.width; boolean fitsVertically =
		 * newHeight <= max.height; if (fitsHorizontally && fitsVertically) { return new
		 * Dimension(newWidth, newHeight); } else return null;
		 */
		boolean fitsHorizontally = newWidth <= max[0].width;
		boolean fitsVertically = newHeight <= max[0].height;
		if (fitsHorizontally && fitsVertically)
			return new Dimension(newWidth, newHeight);
		if (fitsVertically)
			return new Dimension(dstWidth, newHeight);
		else if (fitsHorizontally)
			return new Dimension(newWidth, dstHeight);
		else
			return null;
	}

	/**
	 * Zooms out by making the source rectangle (srcRect) larger and centering it on
	 * (x,y). If we can't make it larger, then make the window smaller. Note that sx
	 * and sy are screen coordinates.
	 */
	public void zoomOut(int sx, int sy) {

		/* If we have an anisotropic view we don't allow zooming! */
		if (fitToParent) {
			return;
		}
		if (magnification <= zoomLevels[0])
			return;
		boolean mouseMoved = sqr(sx - lastZoomSX) + sqr(sy - lastZoomSY) > MAX_MOUSEMOVE_ZOOM * MAX_MOUSEMOVE_ZOOM;
		lastZoomSX = sx;
		lastZoomSY = sy;
		if (mouseMoved || zoomTargetOX < 0) {
			boolean cursorInside = sx >= 0 && sy >= 0 && sx < dstWidth && sy < dstHeight;
			zoomTargetOX = offScreenX(cursorInside ? sx : dstWidth / 2); // where to zoom, offscreen (image) coordinates
			zoomTargetOY = offScreenY(cursorInside ? sy : dstHeight / 2);
		}
		double oldMag = magnification;
		double newMag = getLowerZoomLevel(magnification);
		double srcRatio = (double) srcRect.width / srcRect.height;
		double imageRatio = (double) imageWidth / imageHeight;
		double initialMag = getMagnification();
		if (Math.abs(srcRatio - imageRatio) > 0.05) {
			double scale = oldMag / newMag;
			int newSrcWidth = (int) Math.round(srcRect.width * scale);
			int newSrcHeight = (int) Math.round(srcRect.height * scale);
			if (newSrcWidth > imageWidth)
				newSrcWidth = imageWidth;
			if (newSrcHeight > imageHeight)
				newSrcHeight = imageHeight;
			int newSrcX = srcRect.x - (newSrcWidth - srcRect.width) / 2;
			int newSrcY = srcRect.y - (newSrcHeight - srcRect.height) / 2;
			if (newSrcX + newSrcWidth > imageWidth)
				newSrcX = imageWidth - newSrcWidth;
			if (newSrcY + newSrcHeight > imageHeight)
				newSrcY = imageHeight - newSrcHeight;
			if (newSrcX < 0)
				newSrcX = 0;
			if (newSrcY < 0)
				newSrcY = 0;
			srcRect = new Rectangle(newSrcX, newSrcY, newSrcWidth, newSrcHeight);
			// IJ.log(newMag+" "+srcRect+" "+dstWidth+" "+dstHeight);
			int newDstWidth = (int) (srcRect.width * newMag);
			int newDstHeight = (int) (srcRect.height * newMag);
			setMagnification(newMag);
			setMaxBounds();
			// IJ.log(newDstWidth+" "+dstWidth+" "+newDstHeight+" "+dstHeight);
			if (newDstWidth < dstWidth || newDstHeight < dstHeight) {
				setSize(newDstWidth, newDstHeight);
				imp.getWindow().scResizeEvent(newDstWidth, newDstHeight);
				// imp.getWindow().pack();
				// imageWindowSwt.sComp.layout();
			} else
				/* SWT redraw! */
				repaint();
			// redraw();
			Display.getDefault().syncExec(() -> {

				imp.getWindow().parentComposite.layout();

			});
			return;
		}
		if (imageWidth * newMag > dstWidth) {
			int w = (int) Math.round(dstWidth / newMag);
			if (w * newMag < dstWidth)
				w++;
			int h = (int) Math.round(dstHeight / newMag);
			if (h * newMag < dstHeight)
				h++;
			Rectangle r = new Rectangle(zoomTargetOX - w / 2, zoomTargetOY - h / 2, w, h);
			if (r.x < 0)
				r.x = 0;
			if (r.y < 0)
				r.y = 0;
			if (r.x + w > imageWidth)
				r.x = imageWidth - w;
			if (r.y + h > imageHeight)
				r.y = imageHeight - h;
			srcRect = r;
			setMagnification(newMag);
		} else {
			srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
			setSize((int) (imageWidth * newMag), (int) (imageHeight * newMag));
			imp.getWindow().scResizeEvent((int) (imageWidth * newMag), (int) (imageHeight * newMag));
			setMagnification(newMag);
			// getParent().layout();
			// imp.getWindow().pack();
		}
		setMaxBounds();
		// repaint();
		/* SWT redraw! */
		repaint();
		// redraw();
	}

	int sqr(int x) {

		return x * x;
	}

	/** Implements the Image/Zoom/Original Scale command. */
	public void unzoom() {

		Display.getDefault().syncExec(() -> {

			double imag = imp.getWindow().getInitialMagnification();
			if (magnification == imag)
				return;
			srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
			ImageWindow win = imp.getWindow();
			setSize((int) (imageWidth * imag), (int) (imageHeight * imag));
			setMagnification(imag);
			setMaxBounds();
			// win.pack();
			setMaxBounds();
			// repaint();
			/* SWT redraw! */
			redraw();

		});
	}

	/** Implements the Image/Zoom/View 100% command. */
	public void zoom100Percent() {

		if (magnification == 1.0)
			return;
		double imag = imp.getWindow().getInitialMagnification();
		if (magnification != imag)
			unzoom();
		if (magnification == 1.0)
			return;
		if (magnification < 1.0) {
			while (magnification < 1.0)
				zoomIn(imageWidth / 2, imageHeight / 2);
		} else if (magnification > 1.0) {
			while (magnification > 1.0)
				zoomOut(imageWidth / 2, imageHeight / 2);
		} else
			return;
		int x = xMouse, y = yMouse;
		if (mouseExited) {
			x = imageWidth / 2;
			y = imageHeight / 2;
		}
		int sx = screenX(x);
		int sy = screenY(y);
		adjustSourceRect(1.0, sx, sy);
		/* SWT redraw! */
		repaint();
		// redraw();
	}

	protected void scroll(int sx, int sy) {

		int ox = xSrcStart + (int) (sx / magnification); // convert to offscreen coordinates
		int oy = ySrcStart + (int) (sy / magnification);
		// IJ.log("scroll: "+ox+" "+oy+" "+xMouseStart+" "+yMouseStart);
		int newx = xSrcStart + (xMouseStart - ox);
		int newy = ySrcStart + (yMouseStart - oy);
		if (newx < 0)
			newx = 0;
		if (newy < 0)
			newy = 0;
		if ((newx + srcRect.width) > imageWidth)
			newx = imageWidth - srcRect.width;
		if ((newy + srcRect.height) > imageHeight)
			newy = imageHeight - srcRect.height;
		srcRect.x = newx;
		srcRect.y = newy;
		// IJ.log(sx+" "+sy+" "+newx+" "+newy+" "+srcRect);
		imp.draw();
		Thread.yield();
	}

	Color getColor(int index) {

		IndexColorModel cm = (IndexColorModel) imp.getProcessor().getColorModel();
		return new Color(cm.getRGB(index));
	}

	/**
	 * Sets the foreground drawing color (or background color if 'setBackground' is
	 * true) to the color of the pixel at (ox,oy).
	 */
	public void setDrawingColor(int ox, int oy, boolean setBackground) {

		// IJ.log("setDrawingColor: "+setBackground+this);
		int type = imp.getType();
		int[] v = imp.getPixel(ox, oy);
		switch (type) {
		case ImagePlus.GRAY8: {
			if (setBackground)
				setBackgroundColor(getColor(v[0]));
			else
				setForegroundColor(getColor(v[0]));
			break;
		}
		case ImagePlus.GRAY16:
		case ImagePlus.GRAY32: {
			double min = imp.getProcessor().getMin();
			double max = imp.getProcessor().getMax();
			double value = (type == ImagePlus.GRAY32) ? Float.intBitsToFloat(v[0]) : v[0];
			int index = (int) (255.0 * ((value - min) / (max - min)));
			if (index < 0)
				index = 0;
			if (index > 255)
				index = 255;
			if (setBackground)
				setBackgroundColor(getColor(index));
			else
				setForegroundColor(getColor(index));
			break;
		}
		case ImagePlus.COLOR_RGB:
		case ImagePlus.COLOR_256: {
			Color c = new Color(v[0], v[1], v[2]);
			if (setBackground)
				setBackgroundColor(c);
			else
				setForegroundColor(c);
			break;
		}
		}
		Color c;
		if (setBackground)
			c = Toolbar.getBackgroundColor();
		else {
			c = Toolbar.getForegroundColor();
			imp.setColor(c);
		}
		IJ.showStatus("(" + c.getRed() + ", " + c.getGreen() + ", " + c.getBlue() + ")");
	}

	private void setForegroundColor(Color c) {

		Toolbar.setForegroundColor(c);
		if (IJ.recording())
			Recorder.record("setForegroundColor", c.getRed(), c.getGreen(), c.getBlue());
	}

	private void setBackgroundColor(Color c) {

		Toolbar.setBackgroundColor(c);
		if (IJ.recording())
			Recorder.record("setBackgroundColor", c.getRed(), c.getGreen(), c.getBlue());
	}

	@Override
	public void mouseScrolled(MouseEvent e) {

		/* MouseWheel ported to SWT! */
		int rotation = e.count;
		int amount = e.count;
		amount = (int) Math.ceil(amount / 3.0f);
		// int rotation = e.getWheelRotation();
		// int amount = e.getScrollAmount();
		boolean ctrl = (e.stateMask & SWT.CONTROL) != 0;
		// flags = 507;
		// flags=java.awt.event.MouseEvent.MOUSE_WHEEL;
		/*
		 * if (IJ.debugMode) { IJ.log("mouseWheelMoved: "+e);
		 * IJ.log("  type: "+e.getScrollType()); IJ.log("  ctrl: "+ctrl);
		 * IJ.log("  rotation: "+rotation); IJ.log("  amount: "+amount); }
		 */
		/**/
		if (fitToParent) {
			return;
		}
		if (amount < 1)
			amount = 1;
		if (rotation == 0)
			return;
		int width = imp.getWidth();
		int height = imp.getHeight();
		// Rectangle srcRect = getSrcRect();
		int xstart = srcRect.x;
		int ystart = srcRect.y;
		if ((ctrl || IJ.shiftKeyDown()) && this != null) {
			java.awt.Point pLoc = getCursorLoc();
			Point loc = new org.eclipse.swt.graphics.Point(pLoc.x, pLoc.y);
			int x = screenX(loc.x);
			int y = screenY(loc.y);
			if (rotation < 0)
				zoomIn(x, y);
			else
				zoomOut(x, y);
			return;
		} else if (IJ.spaceBarDown() || srcRect.height == height) {
			srcRect.x += rotation * amount * Math.max(width / 200, 1);
			if (srcRect.x < 0)
				srcRect.x = 0;
			if (srcRect.x + srcRect.width > width)
				srcRect.x = width - srcRect.width;
		} else {
			srcRect.y += rotation * amount * Math.max(height / 200, 1);
			if (srcRect.y < 0)
				srcRect.y = 0;
			if (srcRect.y + srcRect.height > height)
				srcRect.y = height - srcRect.height;
		}
		if (srcRect.x != xstart || srcRect.y != ystart)
			redraw();
	}

	@Override
	public void mouseDoubleClick(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseDown(MouseEvent e) {

		/* Converting SWT to AWT MouseEvents! */
		drag = true;
		isClicked = true;
		// java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		// flags = 501;
		flags = java.awt.event.MouseEvent.MOUSE_PRESSED;
		// System.out.println(flags);
		java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		calculateAspectRatio();
		startX = (int) (e.x / aspectRatioX);
		startY = (int) (e.y / aspectRatioY);
		// System.out.println(flags);
		/* ImageJ starts here (mousePressed)! */
		showCursorStatus = true;
		int toolID = Toolbar.getToolId();
		ImageWindow win = imp.getWindow();
		if (win != null && win.running2 && toolID != Toolbar.MAGNIFIER) {
			if (win instanceof StackWindow)
				((StackWindow) win).setAnimate(false);
			else
				win.running2 = false;
			return;
		}
		// int x = e.getX();
		// int y = e.getY();
		/* Using SWT! */
		calculateAspectRatio();
		int x = (int) (e.x / aspectRatioX);
		int y = (int) (e.y / aspectRatioY);
		// if ((e.stateMask & SWT.SHIFT) != 0 && e.button == 1)
		// flags = e.getModifiers();
		// if (toolID != Toolbar.MAGNIFIER
		// && (e.isPopupTrigger() || (!IJ.isMacintosh() && (flags & Event.META_MASK) !=
		// 0))) {
		/* Using SWT! */
		if (toolID != Toolbar.MAGNIFIER && (e.button == 3 || (!IJ.isMacintosh() && (flags & SWT.COMMAND) != 0))) {
			handlePopupMenu(e);
			return;
		}
		int ox = offScreenX(x);
		int oy = offScreenY(y);
		xMouse = ox;
		yMouse = oy;
		if (IJ.spaceBarDown()) {
			// temporarily switch to "hand" tool of space bar down
			setupScroll(ox, oy);
			return;
		}
		if (overOverlayLabel && (imp.getOverlay() != null || showAllOverlay != null)) {
			if (activateOverlayRoi(ox, oy))
				return;
		}
		if ((System.currentTimeMillis() - mousePressedTime) < 300L && !drawingTool()) {
			if (activateOverlayRoi(ox, oy))
				return;
		}
		mousePressedX = ox;
		mousePressedY = oy;
		mousePressedTime = System.currentTimeMillis();
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool != null) {
			tool.mouseDown(imp, e);
			if (evt.isConsumed())
				return;
		}
		if (customRoi && imp.getOverlay() != null)
			return;
		if (toolID >= Toolbar.CUSTOM1) {
			if (tool != null && "Arrow Tool".equals(tool.getToolName()))
				handleRoiMouseDown(e);
			else
				Toolbar.getInstance().runMacroTool(toolID);
			return;
		}
		final Roi roi1 = imp.getRoi();
		/* Convert to SWT Rectangle */
		Rectangle rec = null;
		if (roi1 != null) {
			java.awt.Rectangle bounds = roi1.getBounds();
			rec = SWTUtils.toSwtRectangle(bounds);
		}
		final int size1 = roi1 != null ? roi1.size() : 0;
		final Rectangle r1 = roi1 != null ? rec : null;
		switch (toolID) {
		case Toolbar.MAGNIFIER:
			if (IJ.shiftKeyDown())
				zoomToSelection(ox, oy);
			else if ((e.stateMask & (SWT.ALT | SWT.ALT | SWT.CTRL)) != 0) {
				zoomOut(x, y);
				if (getMagnification() < 1.0) {
					// imp.repaintWindow();
				}
			} else {
				zoomIn(x, y);
				if (getMagnification() <= 1.0) {
					// imp.repaintWindow();
				}
			}
			break;
		case Toolbar.HAND:
			setupScroll(ox, oy);
			break;
		case Toolbar.DROPPER:
			setDrawingColor(ox, oy, IJ.altKeyDown());
			break;
		case Toolbar.WAND:
			double tolerance = WandToolOptions.getTolerance();
			Roi roi = imp.getRoi();
			if (roi != null && (tolerance == 0.0 || imp.isThreshold()) && roi.contains(ox, oy)) {
				/* Convert to SWT Rectangle */
				Rectangle r = SWTUtils.toSwtRectangle(roi.getBounds());
				// Rectangle r = roi.getBounds();
				if (r.width == imageWidth && r.height == imageHeight)
					imp.deleteRoi();
				else if (!evt.isAltDown()) {
					handleRoiMouseDown(e);
					return;
				}
			}
			if (roi != null) {
				int handle = roi.isHandle(x, y);
				if (handle >= 0) {
					roi.mouseDownInHandle(handle, x, y);
					return;
				}
			}
			if (!imp.okToDeleteRoi())
				break;
			setRoiModState(evt, roi, -1);
			String mode = WandToolOptions.getMode();
			if (Prefs.smoothWand)
				mode = mode + " smooth";
			int npoints = IJ.doWand(imp, ox, oy, tolerance, mode);
			if (IJ.recording() && npoints > 0) {
				if (Recorder.scriptMode())
					Recorder.recordCall("IJ.doWand(imp, " + ox + ", " + oy + ", " + tolerance + ", \"" + mode + "\");");
				else {
					if (tolerance == 0.0 && mode.equals("Legacy"))
						Recorder.record("doWand", ox, oy);
					else
						Recorder.recordString("doWand(" + ox + ", " + oy + ", " + tolerance + ", \"" + mode + "\");\n");
				}
			}
			break;
		case Toolbar.OVAL:
			if (Toolbar.getBrushSize() > 0)
				new RoiBrush();
			else
				handleRoiMouseDown(e);
			break;
		default: // selection tool
			handleRoiMouseDown(e);
		}
		if (longClickDelay > 0) {
			if (pressTimer == null)
				pressTimer = new java.util.Timer();
			final Point cursorLoc = getCursorLocSwt();
			pressTimer.schedule(new TimerTask() {

				public void run() {

					if (pressTimer != null) {
						pressTimer.cancel();
						pressTimer = null;
					}
					Roi roi2 = imp.getRoi();
					int size2 = roi2 != null ? roi2.size() : 0;
					java.awt.Rectangle r2 = roi2 != null ? roi2.getBounds() : null;
					boolean empty = r2 != null && r2.width == 0 && r2.height == 0;
					int state = roi2 != null ? roi2.getState() : -1;
					boolean unchanged = state != Roi.MOVING_HANDLE && r1 != null && r2 != null && r2.x == r1.x
							&& r2.y == r1.y && r2.width == r1.width && r2.height == r1.height && size2 == size1
							&& !(size2 > 1 && state == Roi.CONSTRUCTING);
					boolean cursorMoved = !getCursorLoc().equals(cursorLoc);
					// IJ.log(size2+" "+empty+" "+unchanged+" "+state+" "+roi1+" "+roi2);
					if ((roi1 == null && (size2 <= 1 || empty)) || unchanged) {
						if (roi1 == null)
							imp.deleteRoi();
						if (!cursorMoved && Toolbar.getToolId() != Toolbar.HAND)
							handlePopupMenu(e);
					}
				}
			}, longClickDelay);
		}
		// redraw();
	}

	private boolean drawingTool() {

		int id = Toolbar.getToolId();
		return id == Toolbar.POLYLINE || id == Toolbar.FREELINE || id >= Toolbar.CUSTOM1;
	}

	void zoomToSelection(int x, int y) {

		IJ.setKeyUp(IJ.ALL_KEYS);
		String macro = "args = split(getArgument);\n" + "x1=parseInt(args[0]); y1=parseInt(args[1]); flags=20;\n"
				+ "while (flags&20!=0) {\n" + "getCursorLoc(x2, y2, z, flags);\n" + "if (x2>=x1) x=x1; else x=x2;\n"
				+ "if (y2>=y1) y=y1; else y=y2;\n" + "makeRectangle(x, y, abs(x2-x1), abs(y2-y1));\n" + "wait(10);\n"
				+ "}\n" + "run('To Selection');\n";
		new MacroRunner(macro, x + " " + y);
	}

	protected void setupScroll(int ox, int oy) {

		xMouseStart = ox;
		yMouseStart = oy;
		xSrcStart = srcRect.x;
		ySrcStart = srcRect.y;
	}

	protected void handlePopupMenu(MouseEvent e) {

		/* Converting SWT to AWT MouseEvents! */
		// java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		if (disablePopupMenu)
			return;
		// if (IJ.debugMode)
		// IJ.log("show popup: " + (e.isPopupTrigger() ? "true" : "false"));
		// int sx = e.getX();
		// int sy = e.getY();
		calculateAspectRatio();
		int sx = (int) (e.x / aspectRatioX);
		int sy = (int) (e.y / aspectRatioY);
		// int sx = e.x;
		// int sy = e.y;
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		Roi roi = imp.getRoi();
		if (roi != null && roi.getType() == Roi.COMPOSITE && Toolbar.getToolId() == Toolbar.OVAL
				&& Toolbar.getBrushSize() > 0)
			return; // selection brush tool
		if (roi != null && (roi.getType() == Roi.POLYGON || roi.getType() == Roi.POLYLINE || roi.getType() == Roi.ANGLE)
				&& roi.getState() == roi.CONSTRUCTING) {
			roi.handleMouseUp(sx, sy); // simulate double-click to finalize
			roi.handleMouseUp(sx, sy); // polygon or polyline selection
			return;
		}
		if (roi != null && !(((e.stateMask & SWT.ALT) == SWT.ALT) || (e.stateMask & SWT.SHIFT) == SWT.SHIFT)) {
			// if(roi != null && !(evt.isAltDown() || evt.isShiftDown())) { // show ROI
			// popup?
			if (roi.contains(ox, oy)) {
				if (roiPopupMenu == null)
					addRoiPopupMenu();
				if (IJ.isMacOSX())
					// IJ.wait(10);
					roiPopupMenu.setVisible(true);
				return;
				/* Must be changed for SWT! */
				// roiPopupMenu.show(this, sx, sy);
				/*
				 * ImageJ instance = IJ.getInstance(); ImageJContextMenuAction
				 * imageContextMenuAction = instance.getImageContextMenuAction();
				 * imageContextMenuAction.swtPopupMenu(e, roiPopupMenu, this); return;
				 */
			}
		}
		/* If embedded we create a custom menu! */
		if (ImageJ.SWT_MODE == 1) {
			ImageJ ij = IJ.getInstance();
			if (ij == null)
				return;
			if (embeddedPopupMenu != null && embeddedPopupMenu.isDisposed() == false) {
				embeddedPopupMenu.dispose();
			}
			embeddedPopupMenu = new org.eclipse.swt.widgets.Menu(this.getShell(), SWT.POP_UP);
			new ExtendedPopupMenu(embeddedPopupMenu, this);
			// GUI.scalePopupMenu(roiPopupMenu);
			if (embeddedPopupMenu != null && embeddedPopupMenu.isDisposed() == false) {
				embeddedPopupMenu.setVisible(true);
			}
			/* Must be changed for SWT! */
			if (IJ.isMacOSX() == false)
				setMenu(roiPopupMenu);
			return;
		} else {
			org.eclipse.swt.widgets.Menu popup = Menus.getPopupMenu();
			// Must be changed for SWT!
			if (popup != null) {
				popup.setVisible(true);
			}
		}
	}

	@Override
	public void mouseExit(MouseEvent e) {

		/* Converting SWT to AWT MouseEvents! */
		// java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		// flags = 505;
		// flags=java.awt.event.MouseEvent.MOUSE_EXITED;
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool != null) {
			tool.mouseExit(imp, e);
			if (evt.isConsumed())
				return;
		}
		ImageWindow win = imp.getWindow();
		if (win != null)
			setCursor(defaultCursor);
		IJ.showStatus("");
		mouseExited = true;
		// For the SWT on canvas tooltip if enabled!
		win.mouseExited();
		// redraw();
	}

	@Override
	public void mouseHover(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	/* In SWT not available. Implemented here from mouse down an up! */
	public void mouseDragged(MouseEvent e) {

		/* AWT dragging flag value! */
		// flags = 506;
		flags = java.awt.event.MouseEvent.MOUSE_DRAGGED;
		// int x = e.getX();
		// int y = e.getY();
		// int x = e.getX();
		// int y = e.getY();
		/* Using SWT! */
		// java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		calculateAspectRatio();
		int x = (int) (e.x / aspectRatioX);
		int y = (int) (e.y / aspectRatioY);
		xMouse = offScreenX(x);
		yMouse = offScreenY(y);
		// System.out.println(flags);
		mousePressedX = mousePressedY = -1;
		// IJ.log("mouseDragged: "+flags);
		if (flags == 0) // workaround for Mac OS 9 bug
			flags = SWT.BUTTON1;
		if (Toolbar.getToolId() == Toolbar.HAND || IJ.spaceBarDown())
			scroll(x, y);
		else {
			PlugInTool tool = Toolbar.getPlugInTool();
			if (tool != null) {
				tool.mouseDragged(imp, e);
				if (evt.isConsumed())
					return;
			}
			IJ.setInputEvent(evt);
			Roi roi = imp.getRoi();
			if (roi != null)
				roi.handleMouseDrag(x, y, 0);
		}
		// redraw();
	}

	protected void handleRoiMouseDown(MouseEvent e) {
		// int sx = e.getX();
		// int sy = e.getY();

		/* Converting SWT to AWT MouseEvents! */
		java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		calculateAspectRatio();
		int sx = (int) (e.x / aspectRatioX);
		int sy = (int) (e.y / aspectRatioY);
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		Roi roi = imp.getRoi();
		int tool = Toolbar.getToolId();
		int handle = roi != null ? roi.isHandle(sx, sy) : -1;
		boolean multiPointMode = roi != null && (roi instanceof PointRoi) && handle == -1 && tool == Toolbar.POINT
				&& Toolbar.getMultiPointMode();
		if (multiPointMode) {
			double oxd = roi.offScreenXD(sx);
			double oyd = roi.offScreenYD(sy);
			if (evt.isShiftDown() && !IJ.isMacro()) {
				FloatPolygon points = roi.getFloatPolygon();
				if (points.npoints > 0) {
					double x0 = points.xpoints[0];
					double y0 = points.ypoints[0];
					double slope = Math.abs((oxd - x0) / (oyd - y0));
					if (slope >= 1.0)
						oyd = points.ypoints[0];
					else
						oxd = points.xpoints[0];
				}
			}
			((PointRoi) roi).addUserPoint(imp, oxd, oyd);
			imp.setRoi(roi);
			return;
		}
		if (roi != null && (roi instanceof PointRoi)) {
			int npoints = ((PolygonRoi) roi).getNCoordinates();
			if (npoints > 1 && handle == -1 && !IJ.altKeyDown()
					&& !(tool == Toolbar.POINT && !Toolbar.getMultiPointMode() && IJ.shiftKeyDown())) {
				String msg = "Type shift-a (Edit>Selection>Select None) to delete\npoints. Use multi-point tool to add points.";
				GenericDialog gd = new GenericDialog("Point Selection");
				gd.addMessage(msg);
				gd.addHelp(PointToolOptions.help);
				gd.hideCancelButton();
				gd.showDialog();
				return;
			}
		}
		setRoiModState(evt, roi, handle);
		if (roi != null) {
			if (handle >= 0) {
				roi.mouseDownInHandle(handle, sx, sy);
				return;
			}
			/* Convert to SWT Rectangle */
			Rectangle rec = SWTUtils.toSwtRectangle(roi.getBounds());
			Rectangle r = rec;
			int type = roi.getType();
			if (type == Roi.RECTANGLE && r.width == imp.getWidth() && r.height == imp.getHeight()
					&& roi.getPasteMode() == Roi.NOT_PASTING && !(roi instanceof ImageRoi)) {
				imp.deleteRoi();
				return;
			}
			if (roi.contains(ox, oy)) {
				if (roi.modState == Roi.NO_MODS)
					roi.handleMouseDown(sx, sy);
				else {
					imp.deleteRoi();
					imp.createNewRoi(sx, sy);
				}
				return;
			}
			boolean segmentedTool = tool == Toolbar.POLYGON || tool == Toolbar.POLYLINE || tool == Toolbar.ANGLE;
			if (segmentedTool && (type == Roi.POLYGON || type == Roi.POLYLINE || type == Roi.ANGLE)
					&& roi.getState() == roi.CONSTRUCTING)
				return;
			if (segmentedTool && !(IJ.shiftKeyDown() || IJ.altKeyDown())) {
				imp.deleteRoi();
				return;
			}
		}
		imp.createNewRoi(sx, sy);
	}

	void setRoiModState(java.awt.event.MouseEvent evt, Roi roi, int handle) {

		if (roi == null || (handle >= 0 && roi.modState == Roi.NO_MODS))
			return;
		if (roi.state == Roi.CONSTRUCTING)
			return;
		int tool = Toolbar.getToolId();
		if (tool > Toolbar.FREEROI && tool != Toolbar.WAND && tool != Toolbar.POINT) {
			roi.modState = Roi.NO_MODS;
			return;
		}
		if (evt.isShiftDown())
			roi.modState = Roi.ADD_TO_ROI;
		else if (evt.isAltDown())
			roi.modState = Roi.SUBTRACT_FROM_ROI;
		else
			roi.modState = Roi.NO_MODS;
		// IJ.log("setRoiModState: "+roi.modState+" "+ roi.state);
	}

	/** Disable/enable popup menu. */
	public void disablePopupMenu(boolean status) {

		disablePopupMenu = status;
	}

	public void setShowAllList(Overlay showAllList) {

		this.showAllOverlay = showAllList;
		labelRects = null;
	}

	public Overlay getShowAllList() {

		return showAllOverlay;
	}

	/** Obsolete */
	public void setShowAllROIs(boolean showAllROIs) {

		RoiManager rm = RoiManager.getInstance();
		if (rm != null)
			rm.runCommand(showAllROIs ? "show all" : "show none");
	}

	/** Obsolete */
	public boolean getShowAllROIs() {

		return getShowAllList() != null;
	}

	/** Obsolete */
	public static Color getShowAllColor() {

		if (showAllColor != null && showAllColor.getRGB() == 0xff80ffff)
			showAllColor = Color.cyan;
		return showAllColor;
	}

	/** Obsolete */
	public static void setShowAllColor(Color c) {

		if (c == null)
			return;
		showAllColor = c;
		labelColor = null;
	}

	/** Experimental - Disabled for SWT at the moment! */
	public static void setCursor(java.awt.Cursor cursor, int type) {

		// crosshairCursor = cursor;
	}

	/** To SWT cursor! */
	public void setCursor(Cursor cursor) {

		switch (cursor.getType()) {
		case Cursor.DEFAULT_CURSOR:
			setCursor(defaultCursor);
			break;
		case Cursor.HAND_CURSOR:
			setCursor(handCursor);
			break;
		case Cursor.MOVE_CURSOR:
			setCursor(moveCursor);
			break;
		case Cursor.CROSSHAIR_CURSOR:
			setCursor(crosshairCursor);
			break;
		default:
			setCursor(defaultCursor);
			break;
		}
		// crosshairCursor = cursor;
	}

	/** Use ImagePlus.setOverlay(ij.gui.Overlay). */
	public void setOverlay(Overlay overlay) {

		imp.setOverlay(overlay);
	}

	/** Use ImagePlus.getOverlay(). */
	public Overlay getOverlay() {

		return imp.getOverlay();
	}

	/**
	 * @deprecated replaced by ImagePlus.setOverlay(ij.gui.Overlay)
	 */
	public void setDisplayList(Vector list) {

		if (list != null) {
			Overlay list2 = new Overlay();
			list2.setVector(list);
			imp.setOverlay(list2);
		} else
			imp.setOverlay(null);
		Overlay overlay = imp.getOverlay();
		if (overlay != null)
			overlay.drawLabels(overlay.size() > 0 && overlay.get(0).getStrokeColor() == null);
		else
			customRoi = false;
		/* Changed for SWT! */
		// repaint();
		redraw();
	}

	/**
	 * @deprecated replaced by ImagePlus.setOverlay(Shape, Color, BasicStroke)
	 */
	public void setDisplayList(Shape shape, Color color, BasicStroke stroke) {

		imp.setOverlay(shape, color, stroke);
	}

	/**
	 * @deprecated replaced by ImagePlus.setOverlay(Roi, Color, int, Color)
	 */
	public void setDisplayList(Roi roi, Color color) {

		roi.setStrokeColor(color);
		Overlay list = new Overlay();
		list.add(roi);
		imp.setOverlay(list);
	}

	/**
	 * @deprecated replaced by ImagePlus.getOverlay()
	 */
	public Vector getDisplayList() {

		Overlay overlay = imp.getOverlay();
		if (overlay == null)
			return null;
		Vector displayList = new Vector();
		for (int i = 0; i < overlay.size(); i++)
			displayList.add(overlay.get(i));
		return displayList;
	}

	/**
	 * Allows plugins (e.g., Orthogonal_Views) to create a custom ROI using a
	 * display list.
	 */
	public void setCustomRoi(boolean customRoi) {

		this.customRoi = customRoi;
	}

	public boolean getCustomRoi() {

		return customRoi;
	}

	/**
	 * Called by IJ.showStatus() to prevent status bar text from being overwritten
	 * until the cursor moves at least 12 pixels.
	 */
	public void setShowCursorStatus(boolean status) {

		showCursorStatus = status;
		if (status == true)
			sx2 = sy2 = -1000;
		else {
			sx2 = screenX(xMouse);
			sy2 = screenY(yMouse);
		}
	}

	@Override
	/* SWT of mouseReleased and mouseClicked! */
	public void mouseUp(MouseEvent e) {

		/* Converting SWT to AWT MouseEvents! */
		// java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		// System.out.println(flags);
		drag = false;
		/*
		 * For flags see:
		 * https://docs.oracle.com/javase%2F7%2Fdocs%2Fapi%2F%2F/constant-values.html#
		 * java.awt.event.MouseEvent.MOUSE_CLICKED
		 */
		// flags = 502;
		flags = java.awt.event.MouseEvent.MOUSE_RELEASED;
		java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		/* mouseClicked implementation! */
		if (isClicked) {
			mouseClicked(e);
		}
		isClicked = false;
		calculateAspectRatio();
		endX = (int) (e.x / aspectRatioX);
		endY = (int) (e.y / aspectRatioY);
		/* ImageJ starts here! */
		if (pressTimer != null) {
			pressTimer.cancel();
			pressTimer = null;
		}
		// int ox = offScreenX(e.getX());
		// int oy = offScreenY(e.getY());
		/* SWT mouse events! */
		calculateAspectRatio();
		int ox = offScreenX((int) (e.x / aspectRatioX));
		int oy = offScreenY((int) (e.x / aspectRatioX));
		Overlay overlay = imp.getOverlay();
		if ((overlay != null || showAllOverlay != null) && ox == mousePressedX && oy == mousePressedY) {
			boolean cmdDown = IJ.isMacOSX() && evt.isMetaDown();
			Roi roi = imp.getRoi();
			if (roi != null && roi.getBounds().width == 0)
				roi = null;
			if ((evt.isAltDown() || evt.isControlDown() || cmdDown) && roi == null) {
				if (activateOverlayRoi(ox, oy))
					return;
			} else if ((System.currentTimeMillis() - mousePressedTime) > 250L && !drawingTool()) { // long press
				if (activateOverlayRoi(ox, oy))
					return;
			}
		}
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool != null) {
			tool.mouseUp(imp, e);
			if (evt.isConsumed())
				return;
		}
		flags &= ~SWT.BUTTON1; // make sure button 1 bit is not set
		flags &= ~SWT.BUTTON2; // make sure button 2 bit is not set
		flags &= ~SWT.BUTTON3; // make sure button 3 bit is not set
		Roi roi = imp.getRoi();
		if (roi != null) {
			/* Convert to SWT Rectangle */
			Rectangle r = SWTUtils.toSwtRectangle(roi.getBounds());
			// Rectangle r = roi.getBounds();
			int type = roi.getType();
			if ((r.width == 0 || r.height == 0)
					&& !(type == Roi.POLYGON || type == Roi.POLYLINE || type == Roi.ANGLE || type == Roi.LINE)
					&& !(roi instanceof TextRoi) && roi.getState() == roi.CONSTRUCTING && type != roi.POINT)
				imp.deleteRoi();
			else
				roi.handleMouseUp(evt.getX(), evt.getY());
		}
		// redraw();
	}

	private boolean activateOverlayRoi(int ox, int oy) {

		int currentImage = -1;
		int stackSize = imp.getStackSize();
		if (stackSize > 1)
			currentImage = imp.getCurrentSlice();
		int channel = 0, slice = 0, frame = 0;
		boolean hyperstack = imp.isHyperStack();
		if (hyperstack) {
			channel = imp.getChannel();
			slice = imp.getSlice();
			frame = imp.getFrame();
		}
		Overlay o = showAllOverlay;
		if (o == null)
			o = imp.getOverlay();
		if (o == null || !o.isSelectable())
			return false;
		boolean roiManagerShowAllMode = o == showAllOverlay && !Prefs.showAllSliceOnly;
		boolean labels = o.getDrawLabels();
		int sx = screenX(ox);
		int sy = screenY(oy);
		for (int i = o.size() - 1; i >= 0; i--) {
			Roi roi = o.get(i);
			if (roi == null)
				continue;
			// IJ.log(".isAltDown: "+roi.contains(ox, oy));
			boolean containsMousePoint = false;
			if (roi instanceof Line) { // grab line roi near its center
				double grabLineWidth = 1.1 + 5. / magnification;
				containsMousePoint = (((Line) roi).getFloatPolygon(grabLineWidth)).contains(ox, oy);
			} else
				containsMousePoint = roi.contains(ox, oy);
			if (containsMousePoint
					|| (labels && labelRects != null && labelRects[i] != null && labelRects[i].contains(sx, sy))) {
				if (hyperstack && roi.getPosition() == 0) {
					int c = roi.getCPosition();
					int z = roi.getZPosition();
					int t = roi.getTPosition();
					if (!((c == 0 || c == channel) && (z == 0 || z == slice) && (t == 0 || t == frame)
							|| roiManagerShowAllMode))
						continue;
				} else {
					int position = stackSize > 1 ? roi.getPosition() : 0;
					if (!(position == 0 || position == currentImage || roiManagerShowAllMode))
						continue;
				}
				if (!IJ.altKeyDown() && roi.getType() == Roi.COMPOSITE && roi.getBounds().width == imp.getWidth()
						&& roi.getBounds().height == imp.getHeight())
					return false;
				// if (Toolbar.getToolId()==Toolbar.OVAL && Toolbar.getBrushSize()>0)
				// Toolbar.getInstance().setTool(Toolbar.RECTANGLE);
				roi.setImage(null);
				imp.setRoi(roi);
				roi.handleMouseDown(sx, sy);
				roiManagerSelect(roi, false);
				ResultsTable.selectRow(roi);
				return true;
			}
		}
		return false;
	}

	public boolean roiManagerSelect(Roi roi, boolean delete) {

		RoiManager rm = RoiManager.getInstance();
		if (rm == null)
			return false;
		int index = rm.getRoiIndex(roi);
		if (index < 0)
			return false;
		if (delete) {
			rm.select(imp, index);
			rm.runCommand("delete");
		} else
			rm.selectAndMakeVisible(imp, index);
		return true;
	}

	@Override
	public void mouseMove(MouseEvent e) {

		/* Converting SWT to AWT MouseEvents! */
		// gc = new GC((Canvas) e.widget);
		// java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		// flags=503; This flag doesn't work well in the overlay editing tools!
		flags = java.awt.event.MouseEvent.MOUSE_MOVED;
		if (drag) {
			calculateAspectRatio();
			endX = (int) (e.x / aspectRatioX);
			endY = (int) (e.y / aspectRatioY);
			mouseDragged(e);
			return;
		}
		//
		/*
		 * Image image = new Image(e.display, 1, 1); GC gc = new GC((Canvas) e.widget);
		 * gc.copyArea(image, e.x, e.y); ImageData imageData = image.getImageData(); int
		 * pixelValue = imageData.getPixel(0, 0); PaletteData palette =
		 * imageData.palette; RGB rgb = palette.getRGB(pixelValue);
		 * System.out.println(rgb); System.out.println("Coordinates" + e.x + "  " +
		 * e.y);
		 */
		/* ImageJ starts here! */
		// if (ij==null) return;
		// int sx = e.getX();
		// int sy = e.getY();
		calculateAspectRatio();
		int sx = (int) (e.x / aspectRatioX);
		int sy = (int) (e.y / aspectRatioY);
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		// System.out.println(flags);
		setCursor(sx, sy, ox, oy);
		mousePressedX = mousePressedY = -1;
		IJ.setInputEvent(evt);
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool != null) {
			tool.mouseMove(imp, e);
			if (evt.isConsumed())
				return;
		}
		Roi roi = imp.getRoi();
		int type = roi != null ? roi.getType() : -1;
		if (type > 0 && (type == Roi.POLYGON || type == Roi.POLYLINE || type == Roi.ANGLE || type == Roi.LINE)
				&& roi.getState() == roi.CONSTRUCTING)
			roi.mouseMoved(evt);
		else {
			if (ox < imageWidth && oy < imageHeight) {
				ImageWindow win = imp.getWindow();
				// Cursor must move at least 12 pixels before text
				// displayed using IJ.showStatus() is overwritten.
				if ((sx - sx2) * (sx - sx2) + (sy - sy2) * (sy - sy2) > 144)
					showCursorStatus = true;
				if (win != null && showCursorStatus)
					win.mouseMoved(ox, oy);
			} else
				IJ.showStatus("");
		}
		// redraw();
	}

	@Override
	public void mouseEnter(MouseEvent e) {

		/* Converting SWT to AWT MouseEvents! */
		// java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		// flags = 504;
		// java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool != null)
			tool.mouseEnter(imp, e);
	}

	/* In SWT as mouseClicked used. Activated from MouseDown! */
	public void mouseClicked(MouseEvent e) {

		/* Converting SWT to AWT MouseEvents! */
		flags = 0;
		// java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		java.awt.event.MouseEvent evt = SWTUtils.toAwtMouseEvent(e);
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool != null)
			tool.mouseClicked(imp, e);
	}

	public void setScaleToFit(boolean scaleToFit) {

		this.scaleToFit = scaleToFit;
	}

	public boolean getScaleToFit() {

		return scaleToFit;
	}

	public boolean hideZoomIndicator(boolean hide) {

		boolean hidden = this.hideZoomIndicator;
		if (!(srcRect.width < imageWidth || srcRect.height < imageHeight))
			return hidden;
		this.hideZoomIndicator = hide;
		setPaintPending(true);
		// repaint();
		/* Changed for SWT! */
		redraw();
		long t0 = System.currentTimeMillis();
		while (getPaintPending() && (System.currentTimeMillis() - t0) < 500L)
			IJ.wait(10);
		return hidden;
	}

	public void repaintOverlay() {

		labelRects = null;
		// repaint();
		/* SWT paint! */
		if (ImageCanvas.this.isDisposed() == false)
			Display.getDefault().syncExec(() -> {

				redraw();

			});
	}

	/**
	 * Sets the context menu long click delay in milliseconds (default is 1000). Set
	 * to 0 to disable long click triggering.
	 */
	public static void setLongClickDelay(int delay) {

		longClickDelay = delay;
	}

	/* SWT Popup! */
	void addRoiPopupMenu() {

		ImageJ ij = IJ.getInstance();
		if (ij == null)
			return;
		if (roiPopupMenu != null && roiPopupMenu.isDisposed() == false) {
			roiPopupMenu.dispose();
		}
		roiPopupMenu = new org.eclipse.swt.widgets.Menu(this.getShell(), SWT.POP_UP);
		// GUI.scalePopupMenu(roiPopupMenu);
		addPopupItem("ROI Properties... ", "Properties... ", roiPopupMenu, ij);
		addPopupItem("Roi Defaults...", null, roiPopupMenu, ij);
		addPopupItem("Add to Overlay", "Add Selection...", roiPopupMenu, ij);
		addPopupItem("Add to ROI Manager", "Add to Manager", roiPopupMenu, ij);
		addPopupItem("Duplicate...", null, roiPopupMenu, ij);
		addPopupItem("Fit Spline", null, roiPopupMenu, ij);
		addPopupItem("Create Mask", null, roiPopupMenu, ij);
		addPopupItem("Measure", null, roiPopupMenu, ij);
		/* Must be changed for SWT! */
		if (IJ.isMacOSX() == false)
			setMenu(roiPopupMenu);
	}

	private void addPopupItem(String label, String command, org.eclipse.swt.widgets.Menu pm, ImageJ ij) {

		org.eclipse.swt.widgets.MenuItem mi = new org.eclipse.swt.widgets.MenuItem(pm, SWT.PUSH);
		if (command != null) {
			// String actionCommand=(String)mi.getData("ActionCommand");
			mi.setData("ActionCommand", command);
		}
		mi.setText(label);
		mi.addSelectionListener(ij);
		// pm.add(mi);
	}

	/**
	 * Dispose the garbage here called from the dispose listener!
	 */
	public void dispose() {

		removeMouseListener(this);
		removeMouseWheelListener(this);
		removeMouseMoveListener(this);
		removePaintListener(this);
		removeMouseTrackListener(this);
		// removeDragDetectListener(this);
		defaultCursor.dispose();
		handCursor.dispose();
		moveCursor.dispose();
		crosshairCursor.dispose();
		if (img != null && !img.isDisposed()) {
			img.dispose();
		}
	}

	@Override
	public void widgetDisposed(DisposeEvent e) {

		dispose();
	}

	/*
	 * A method to calculate the aspectRatio with zoom for an anistropic display of
	 * images if enabled (e.g., heatmap).
	 */
	public void calculateAspectRatio() {

		Display.getDefault().syncExec(() -> {

			if (fitToParent) {
				float ix = getBounds().width;
				float iy = getBounds().height;
				float px = 0;
				float py = 0;
				px = srcRect.width;
				py = srcRect.height;
				/* Store the display aspect ratio for Mouse events! */
				aspectRatioX = (float) ((ix / px) / magnification);
				aspectRatioY = (float) ((iy / py) / magnification);
			} else {
				aspectRatioX = 1.0f;
				aspectRatioY = 1.0f;
			}

		});
	}
	// @Override
	// public void keyPressed(KeyEvent e) {
	/*
	 * CommandLister hotkeys = new CommandLister(); String[] shortcuts =
	 * hotkeys.getShortcuts(); for (int i = 0; i < shortcuts.length; i++) { if
	 * (shortcuts[i].contains("\t^")) { shortcuts[i] += " (macro)"; } } for (int i =
	 * 0; i < shortcuts.length; i++) { String[] splitShortcut =
	 * shortcuts[i].split("\t"); splitShortcut[0] = splitShortcut[0].trim(); if
	 * (splitShortcut[0].equals("" + e.character)) { IJ.doCommand(splitShortcut[1]);
	 * } Also allow the F keys for a shortcut! if (splitShortcut[0].equals("F1") &&
	 * e.keyCode == SWT.F1) { IJ.doCommand(splitShortcut[1]); } else if
	 * (splitShortcut[0].equals("F2") && e.keyCode == SWT.F2) {
	 * IJ.doCommand(splitShortcut[1]); } else if (splitShortcut[0].equals("F3") &&
	 * e.keyCode == SWT.F3) { IJ.doCommand(splitShortcut[1]); } else if
	 * (splitShortcut[0].equals("F4") && e.keyCode == SWT.F4) {
	 * IJ.doCommand(splitShortcut[1]); } else if (splitShortcut[0].equals("F5") &&
	 * e.keyCode == SWT.F5) { IJ.doCommand(splitShortcut[1]); } else if
	 * (splitShortcut[0].equals("F6") && e.keyCode == SWT.F6) {
	 * IJ.doCommand(splitShortcut[1]); } else if (splitShortcut[0].equals("F7") &&
	 * e.keyCode == SWT.F7) { IJ.doCommand(splitShortcut[1]); } else if
	 * (splitShortcut[0].equals("F8") && e.keyCode == SWT.F8) {
	 * IJ.doCommand(splitShortcut[1]); } else if (splitShortcut[0].equals("F9") &&
	 * e.keyCode == SWT.F9) { IJ.doCommand(splitShortcut[1]); } else if
	 * (splitShortcut[0].equals("F10") && e.keyCode == SWT.F10) {
	 * IJ.doCommand(splitShortcut[1]); } else if (splitShortcut[0].equals("F11") &&
	 * e.keyCode == SWT.F11) { IJ.doCommand(splitShortcut[1]); } else if
	 * (splitShortcut[0].equals("F12") && e.keyCode == SWT.F12) {
	 * IJ.doCommand(splitShortcut[1]); } else if (splitShortcut[0].equals("F13") &&
	 * e.keyCode == SWT.F13) { IJ.doCommand(splitShortcut[1]); } else if
	 * (splitShortcut[0].equals("F14") && e.keyCode == SWT.F14) {
	 * IJ.doCommand(splitShortcut[1]); } else if (splitShortcut[0].equals("F15") &&
	 * e.keyCode == SWT.F15) { IJ.doCommand(splitShortcut[1]); } else if
	 * (splitShortcut[0].equals("F16") && e.keyCode == SWT.F16) {
	 * IJ.doCommand(splitShortcut[1]); } else if (splitShortcut[0].equals("F17") &&
	 * e.keyCode == SWT.F17) { IJ.doCommand(splitShortcut[1]); } else if
	 * (splitShortcut[0].equals("F18") && e.keyCode == SWT.F18) {
	 * IJ.doCommand(splitShortcut[1]); } else if (splitShortcut[0].equals("F19") &&
	 * e.keyCode == SWT.F19) { IJ.doCommand(splitShortcut[1]); } else if
	 * (splitShortcut[0].equals("F20") && e.keyCode == SWT.F20) {
	 * IJ.doCommand(splitShortcut[1]); } }
	 */
	// ij.keyPressed(e);
	// }
	// @Override
	/*
	 * public void keyReleased(KeyEvent e) {
	 * ij.keyReleased(SWTUtils.toAwtKeyEvent(e)); }
	 */
}
