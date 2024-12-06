package ij.plugin;

import ij.*;
import ij.process.*;
import ij.swt.Util;
import ij.gui.*;
import java.awt.*;
import java.awt.image.MultiResolutionImage;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * This plugin implements the Plugins/Utilities/Capture Screen and
 * Plugins/Utilities/Capture Image commands. Note that these commands may not
 * work on Linux if windows translucency or special effects are enabled in the
 * windows manager.
 */
public class ScreenGrabber implements PlugIn {

	private static int delay = 10;
	protected boolean wasHidden;

	public void run(String arg) {

		ImagePlus imp2 = null;
		if(arg.equals("image") || arg.equals("flatten"))
			imp2 = captureImage();
		else if(arg.equals("delay"))
			imp2 = captureDelayed();
		else
			imp2 = captureScreen();
		if(imp2 != null)
			imp2.show();
	}

	private ImagePlus captureDelayed() {

		GenericDialog gd = new GenericDialog("Delayed Capture");
		gd.addNumericField("Delay (seconds):", delay, 0);
		gd.showDialog();
		if(gd.wasCanceled())
			return null;
		int delay = (int)gd.getNextNumber();
		if(delay < 0)
			return null;
		if(delay > 60)
			delay = 60;
		for(int i = 0; i < delay; i++) {
			IJ.wait(1000);
			IJ.showStatus("Delayed capture: " + (i + 1) + "/" + delay);
			if(delay > 4 && i == delay - 2)
				IJ.beep();
		}
		return captureScreen();
	}

	/** Captures the entire screen and returns it as an ImagePlus. */
	public ImagePlus captureScreen() {

		// ImagePlus imp = null;
		AtomicReference<ImagePlus> imp = new AtomicReference<ImagePlus>();
		Display dis = Display.getDefault();
		dis.syncExec(new Runnable() {

			public void run() {

				// im.set(imp);
				try {
					/*
					 * Robot robot = new Robot(); Rectangle r =
					 * GUI.getScreenBounds(IJ.getInstance().getShell()); // screen showing "ImageJ"
					 * window
					 * //MultiResolutionImage img = robot.createMultiResolutionScreenCapture(r);
					 * Image img; MultiResolutionImage mrImage =
					 * robot.createMultiResolutionScreenCapture(r); java.util.List<Image>
					 * resolutionVariants = mrImage.getResolutionVariants(); if
					 * (resolutionVariants.size() > 1) { img = resolutionVariants.get(1); } else {
					 * img = resolutionVariants.get(0); }
					 * if (img != null) imp = new ImagePlus("Screenshot", img);
					 */
					/* Works only on Windows!? */
					GC screenGC = new GC(Display.getDefault());
					if(Util.getOS().equals("Mac")) {
						screenGC.setAntialias(SWT.OFF);
						screenGC.setInterpolation(SWT.OFF);
					}
					org.eclipse.swt.graphics.Rectangle bounds = Display.getDefault().getBounds();
					org.eclipse.swt.graphics.Image tempImage = new org.eclipse.swt.graphics.Image(Display.getDefault(), bounds);
					screenGC.copyArea(tempImage, 0, 0);
					screenGC.dispose();
					org.eclipse.swt.graphics.Image imageScreen;
					if(DPIUtil.getDeviceZoom() > 150) {
						imageScreen = new org.eclipse.swt.graphics.Image(Display.getDefault(), tempImage.getImageData(200));
					} else {
						imageScreen = new org.eclipse.swt.graphics.Image(Display.getDefault(), tempImage.getImageData(100));
					}
					// gc.drawImage(tempImage, 0, 0);
					imp.set(new ImagePlus("Screenshot", imageScreen));
					tempImage.dispose();
					imageScreen.dispose(); // don't forget about me!
					// gc.dispose();
				} catch(Exception e) {
				}
			}
		});
		return imp.get();
	}

	/** Captures the active image window and returns it as an ImagePlus. */
	public ImagePlus captureImage() {

		ImagePlus imp = IJ.getImage();
		if(imp == null) {
			IJ.noImage();
			return null;
		}
		ImageWindow win = imp.getWindow();
		if(win == null)
			return null;
		win.toFront();
		/* Hide the Zoom indicator! */
		Display dis = Display.getDefault();
		dis.syncExec(new Runnable() {

			public void run() {

				ImageCanvas ic = win.getImageCanvas();
				wasHidden = ic.hideZoomIndicator(true);
			}
		});
		AtomicReference<ImagePlus> imp2 = new AtomicReference<ImagePlus>();
		dis.syncExec(new Runnable() {

			public void run() {

				ImageCanvas ic = win.getImageCanvas();
				org.eclipse.swt.graphics.Point bounds = ic.toDisplay(0, 0);
				org.eclipse.swt.graphics.Rectangle cla = ic.getClientArea();
				org.eclipse.swt.graphics.Rectangle r = new org.eclipse.swt.graphics.Rectangle(bounds.x, bounds.y, cla.width, cla.height);
				GC screenGC = new GC(Display.getDefault());
				if(Util.getOS().equals("Mac")) {
					screenGC.setAntialias(SWT.OFF);
					screenGC.setInterpolation(SWT.OFF);
				}
				// org.eclipse.swt.graphics.Rectangle bounds = Display.getDefault().getBounds();
				org.eclipse.swt.graphics.Image tempImage = new org.eclipse.swt.graphics.Image(Display.getDefault(), r);
				screenGC.copyArea(tempImage, bounds.x, bounds.y);
				screenGC.dispose();
				org.eclipse.swt.graphics.Image imageScreen;
				if(DPIUtil.getDeviceZoom() > 150) {
					imageScreen = new org.eclipse.swt.graphics.Image(Display.getDefault(), tempImage.getImageData(200));
				} else {
					imageScreen = new org.eclipse.swt.graphics.Image(Display.getDefault(), tempImage.getImageData(100));
				}
				// gc.drawImage(tempImage, 0, 0);
				String title = WindowManager.getUniqueName(imp.getTitle());
				imp2.set(new ImagePlus(title, imageScreen));
				tempImage.dispose();
				imageScreen.dispose(); // don't forget about me!
			}
		});
		/* Show the Zoom indicator! */
		dis.syncExec(new Runnable() {

			public void run() {

				ImageCanvas ic = win.getImageCanvas();
				ic.hideZoomIndicator(wasHidden);
			}
		});
		return imp2.get();
	}
}
