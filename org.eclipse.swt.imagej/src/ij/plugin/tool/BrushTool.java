package ij.plugin.tool;

import java.awt.Choice;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.util.Vector;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.widgets.Display;
// Versions
// 2012-07-22 shift to confine horizontally or vertically, ctrl-shift to resize, ctrl to pick

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.Undo;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GUI;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.ImageRoi;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.Colors;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * This class implements the Paintbrush Tool, which allows the user to draw on
 * an image, or on an Overlay if "Paint on overlay" is enabled.
 */
public class BrushTool extends PlugInTool implements Runnable {

	private final static int UNCONSTRAINED = 0, HORIZONTAL = 1, VERTICAL = 2, RESIZING = 3, RESIZED = 4, IDLE = 5; // mode
																													// flags
	private static String BRUSH_WIDTH_KEY = "brush.width";
	private static String PENCIL_WIDTH_KEY = "pencil.width";
	private static String CIRCLE_NAME = "brush-tool-overlay";
	private static final String LOC_KEY = "brush.loc";
	private static final String OVERLAY_KEY = "brush.overlay";
	private String widthKey;
	private int width;
	private ImageProcessor ip;
	private int mode; // resizing brush or motion constrained horizontally or vertically
	private int xStart, yStart;
	private int oldWidth;
	private boolean isPencil;
	private Overlay overlay;
	private Options options;
	private GenericDialog gd;
	private ImageRoi overlayImage;
	private boolean paintOnOverlay;
	private static BrushTool brushInstance;

	public void run(String arg) {

		isPencil = "pencil".equals(arg);
		widthKey = isPencil ? PENCIL_WIDTH_KEY : BRUSH_WIDTH_KEY;
		width = (int) Prefs.get(widthKey, isPencil ? 1 : 5);
		paintOnOverlay = Prefs.get(OVERLAY_KEY, false);
		Toolbar.addPlugInTool(this);
		if (!isPencil)
			brushInstance = this;
	}

	public void mouseDown(ImagePlus imp, MouseEvent e) {

		ImageCanvas ic = imp.getCanvas();
		int x = ic.offScreenX((int) (e.x * ic.aspectRatioX));
		int y = ic.offScreenY((int) (e.y * ic.aspectRatioY));
		xStart = x;
		yStart = y;
		checkForOverlay(imp);
		if (overlayImage != null)
			ip = overlayImage.getProcessor();
		else
			ip = imp.getProcessor();
		// int ctrlMask = IJ.isMacintosh() ? InputEvent.META_MASK :
		// InputEvent.CTRL_MASK;
		// int resizeMask = InputEvent.SHIFT_MASK | ctrlMask;
		if (((e.stateMask & SWT.SHIFT) == SWT.SHIFT) && ((e.stateMask & SWT.CTRL) == SWT.CTRL)) {
			mode = RESIZING;
			oldWidth = width;
			return;
		} else if (((e.stateMask & SWT.CTRL) == SWT.CTRL)) {
			boolean altKeyDown = ((e.stateMask & SWT.ALT) == SWT.ALT);
			ic.setDrawingColor(x, y, altKeyDown); // pick color from image (ignore overlay)
			if (!altKeyDown)
				setColor(Toolbar.getForegroundColor());
			mode = IDLE;
			return;
		}
		mode = UNCONSTRAINED;
		ip.snapshot();
		Undo.setup(Undo.FILTER, imp);
		ip.setLineWidth(width);
		if (((e.stateMask & SWT.ALT) == SWT.ALT)) {
			if (overlayImage != null)
				ip.setColor(0); // erase
			else
				ip.setColor(Toolbar.getBackgroundColor());
		} else
			ip.setColor(Toolbar.getForegroundColor());
		ip.moveTo(x, y);
		if (!((e.stateMask & SWT.SHIFT) == SWT.SHIFT)) {
			ip.lineTo(x, y);
			if (overlayImage != null) {
				overlayImage.setProcessor(ip);
				imp.draw();
			} else
				imp.updateAndDraw();
		}
	}

	private void checkForOverlay(ImagePlus imp) {

		overlayImage = getOverlayImage(imp);
		if (overlayImage == null && paintOnOverlay) {
			ImageProcessor overlayIP = new ColorProcessor(imp.getWidth(), imp.getHeight());
			ImageRoi imageRoi = new ImageRoi(0, 0, overlayIP);
			imageRoi.setZeroTransparent(true);
			imageRoi.setName("[Brush]");
			Overlay overlay = imp.getOverlay();
			if (overlay == null)
				overlay = new Overlay();
			overlay.add(imageRoi);
			overlay.selectable(false);
			imp.setOverlay(overlay);
			overlayImage = imageRoi;
		}
	}

	private ImageRoi getOverlayImage(ImagePlus imp) {

		if (!paintOnOverlay)
			return null;
		Overlay overlay = imp.getOverlay();
		if (overlay == null)
			return null;
		Roi roi = overlay.get("[Brush]");
		if (roi == null || !(roi instanceof ImageRoi))
			return null;
		Rectangle bounds = roi.getBounds();
		if (bounds.x != 0 || bounds.y != 0 || bounds.width != imp.getWidth() || bounds.height != imp.getHeight())
			return null;
		return (ImageRoi) roi;
	}

	public void mouseDragged(ImagePlus imp, MouseEvent e) {

		if (mode == IDLE)
			return;
		ImageCanvas ic = imp.getCanvas();
		int x = ic.offScreenX((int) (e.x * ic.aspectRatioX));
		int y = ic.offScreenY((int) (e.y * ic.aspectRatioY));
		if (mode == RESIZING) {
			showToolSize(x - xStart, imp);
			return;
		}
		if (((e.stateMask & SWT.SHIFT) == SWT.SHIFT)) { // shift constrains
			if (mode == UNCONSTRAINED) { // first movement with shift down determines direction
				if (Math.abs(x - xStart) > Math.abs(y - yStart))
					mode = HORIZONTAL;
				else if (Math.abs(x - xStart) < Math.abs(y - yStart))
					mode = VERTICAL;
				else
					return; // constraint direction still unclear
			}
			if (mode == HORIZONTAL)
				y = yStart;
			else if (mode == VERTICAL)
				x = xStart;
		} else {
			xStart = x;
			yStart = y;
			mode = UNCONSTRAINED;
		}
		ip.setLineWidth(width);
		ip.lineTo(x, y);
		if (overlayImage != null) {
			overlayImage.setProcessor(ip);
			imp.draw();
		} else
			imp.updateAndDraw();
	}

	public void mouseUp(ImagePlus imp, MouseEvent e) {

		if (mode == RESIZING) {
			if (overlay != null && overlay.size() > 0
					&& CIRCLE_NAME.equals(overlay.get(overlay.size() - 1).getName())) {
				overlay.remove(overlay.size() - 1);
				imp.setOverlay(overlay);
			}
			overlay = null;
			if (((e.stateMask & SWT.SHIFT) == SWT.SHIFT)) {
				setWidth(width);
				Prefs.set(widthKey, width);
			}
		}
	}

	private void setWidth(int width) {

		if (gd == null)
			return;
		Vector numericFields = gd.getNumericFields();
		TextField widthField = (TextField) numericFields.elementAt(0);
		widthField.setText("" + width);
		Vector sliders = gd.getSliders();
		Scrollbar sb = (Scrollbar) sliders.elementAt(0);
		sb.setValue(width);
	}

	private void setColor(Color c) {

		if (gd == null)
			return;
		String name = Colors.colorToString2(c);
		if (name.length() > 0) {
			Vector choices = gd.getChoices();
			Choice ch = (Choice) choices.elementAt(0);
			ch.select(name);
		}
	}

	private void showToolSize(int deltaWidth, ImagePlus imp) {

		if (deltaWidth != 0) {
			width = oldWidth + deltaWidth;
			if (width < 1)
				width = 1;
			Roi circle = new OvalRoi(xStart - width / 2, yStart - width / 2, width, width);
			circle.setName(CIRCLE_NAME);
			circle.setStrokeColor(Color.red);
			overlay = imp.getOverlay();
			if (overlay == null)
				overlay = new Overlay();
			else if (overlay.size() > 0 && CIRCLE_NAME.equals(overlay.get(overlay.size() - 1).getName()))
				overlay.remove(overlay.size() - 1);
			overlay.add(circle);
			imp.setOverlay(overlay);
		}
		IJ.showStatus((isPencil ? "Pencil" : "Brush") + " width: " + width);
	}

	public void showOptionsDialog() {

		Thread thread = new Thread(this, "Brush Options");
		thread.setPriority(Thread.NORM_PRIORITY);
		thread.start();
	}

	public String getToolName() {

		if (isPencil)
			return "Pencil Tool";
		else
			return "Paintbrush Tool";
	}

	public String getToolIcon() {

		// C123 is the foreground color
		if (isPencil)
			return "C037L4990L90b0Lc1c3L82a4Lb58bL7c4fDb4L494fC123L5a5dL6b6cD7b";
		else
			return "N02 C123H2i2g3e5c6b9b9e8g6h4i2i0 C037Lc07aLf09b P2i3e5c6b9b9e8g6h4i2i0";
	}

	public void run() {

		new Options();
	}

	class Options implements DialogListener {

		Options() {

			Display.getDefault().syncExec(() -> {

				if (gd != null) {
					gd.getShell().setVisible(true);
					return;
				}
				options = Options.this;
				showDialog();

			});
		}

		public void showDialog() {

			Color color = Toolbar.getForegroundColor();
			String colorName = Colors.colorToString2(color);
			String name = isPencil ? "Pencil" : "Brush";
			gd = GUI.newNonBlockingDialog(name + " Options");
			gd.addSlider(name + " width:", 1, 50, width);
			// gd.addSlider("Transparency (%):", 0, 100, transparency);
			gd.addChoice("Color:", Colors.getColors(colorName), colorName);
			gd.addCheckbox("Paint on overlay", paintOnOverlay);
			gd.addDialogListener(this);
			gd.addHelp(getHelp());
			Point loc = Prefs.getLocation(LOC_KEY);
			if (loc != null) {
				gd.centerDialog(false);
				gd.setLocation(loc.x, loc.y);
			}
			gd.showDialog();
			org.eclipse.swt.graphics.Point p = gd.getShell().getLocation();
			Prefs.saveLocation(LOC_KEY, new java.awt.Point(p.x, p.y));
			gd = null;
		}

		public boolean dialogItemChanged(GenericDialog gd, TypedEvent e) {

			if (e != null && e.toString().contains("Undo")) {
				ImagePlus imp = WindowManager.getCurrentImage();
				if (imp != null)
					IJ.run("Undo");
				return true;
			}
			width = (int) gd.getNextNumber();
			if (gd.invalidNumber() || width < 0)
				width = (int) Prefs.get(widthKey, 1);
			// transparency = (int)gd.getNextNumber();
			// if (gd.invalidNumber() || transparency<0 || transparency>100)
			// transparency = 100;
			String colorName = gd.getNextChoice();
			paintOnOverlay = gd.getNextBoolean();
			Color color = Colors.decode(colorName, null);
			Toolbar.setForegroundColor(color);
			Prefs.set(widthKey, width);
			Prefs.set(OVERLAY_KEY, paintOnOverlay);
			return true;
		}
	}

	public static void setBrushWidth(int width) {

		if (brushInstance != null) {
			Color c = Toolbar.getForegroundColor();
			brushInstance.setWidth(width);
			Toolbar.setForegroundColor(c);
		}
	}

	private String getHelp() {

		String ctrlString = IJ.isMacintosh() ? "<i>cmd</i>" : "<i>ctrl</i>";
		return "<html>" + "<font size=+1>" + "<b>Key modifiers</b>" + "<ul>"
				+ "<li> <i>shift</i> to draw horizontal or vertical lines<br>"
				+ "<li> <i>alt</i> to draw in background color (or<br>to erase if painting on overlay<br>" + "<li>"
				+ ctrlString + "<i>-shift-drag</i> to change " + (isPencil ? "pencil" : "brush") + " width<br>" + "<li>"
				+ ctrlString + "<i>-click</i> to change (\"pick up\") the<br>"
				+ "drawing color, or use the Color<br>Picker (<i>shift-k</i>)<br>" + "</ul>"
				+ "Use <i>Edit&gt;Selection&gt;Create Mask</i> to create<br>a mask from the painted overlay. "
				+ "Use<br><i>Image&gt;Overlay&gt;Remove Overlay</i> to remove<br>the painted overlay.<br>" + " <br>"
				+ "</font>";
	}
}