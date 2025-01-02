package ij.plugin.filter;

import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.ResultsTable;
import java.awt.image.IndexColorModel;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

/** Displays the active image's look-up table.
* Implements the Image/Color/Show LUT command.
*/
public class LutViewer implements PlugInFilter {

	private double guiScale = Prefs.getGuiScale();
	private ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {

		this.imp = imp;
		return DOES_ALL + NO_UNDO + NO_CHANGES;
	}

	public void run(ImageProcessor ip) {

		if(ip.getNChannels() == 3) {
			IJ.error("RGB images do not have LUTs.");
			return;
		}
		int xMargin = (int)(35 * guiScale);
		int yMargin = (int)(20 * guiScale);
		int width = (int)(256 * guiScale);
		int height = (int)(128 * guiScale);
		int x, y, x1, y1, x2, y2;
		int imageWidth, imageHeight;
		int barHeight = (int)(12 * guiScale);
		boolean isGray;
		ip = imp.getChannelProcessor();
		IndexColorModel cm = (IndexColorModel)ip.getColorModel();
		LookUpTable lut = new LookUpTable(cm);
		int mapSize = lut.getMapSize();
		byte[] reds = lut.getReds();
		byte[] greens = lut.getGreens();
		byte[] blues = lut.getBlues();
		isGray = lut.isGrayscale();
		imageWidth = width + 2 * xMargin;
		imageHeight = height + 3 * yMargin;
		/*
		 * int size = cm.getMapSize(); cm.getReds(reds); cm.getGreens(greens);
		 * cm.getBlues(blues); RGB[] rgbs = new RGB[size]; for (int i = 0; i <
		 * rgbs.length; i++) { rgbs[i] = new RGB(reds[i] & 0xFF, greens[i] & 0xFF,
		 * blues[i] & 0xFF); } PaletteData palette = new PaletteData(rgbs); ImageData
		 * data = new ImageData(imageWidth, imageHeight, cm.getPixelSize(), palette);
		 */
		// Image img = new Image(Display.getDefault(), data);
		// Image img = IJ.getInstance().createImage(imageWidth,
		// imageHeight);//java.awt.Component method!
		Image img = new Image(Display.getDefault(), imageWidth, imageHeight);
		GC gc = new GC(img);
		gc.setForeground(ij.swt.Color.white);
		gc.fillRectangle(-1, 0, imageWidth, imageHeight);
		gc.setForeground(ij.swt.Color.black);
		gc.drawRectangle(xMargin, yMargin-1, width+1, height+1);
		if(isGray)
			gc.setForeground(ij.swt.Color.black);
		else
			gc.setForeground(ij.swt.Color.red);
		boolean drawSteps = width > mapSize;		//more than 1 pxl per color
		double xShift = drawSteps ? 0.5 : 0.0;		//when drawing steps, vertical lines are at half-integer positions
		x1 = scaledX(-0.5, xMargin, width, mapSize);	//for drawing steps, we need the x where the previous color would end
		y1 = yMargin + height - 1 - (int)Math.round(((reds[0]&0xff)-0.5)*guiScale/2);
		for (int i=0; i<mapSize; i++) {				// R E D   or   G R A Y S C A L E
			x2 = scaledX(i+xShift, xMargin, width, mapSize);
			y2 = yMargin + height - 1 - (int)Math.round(((reds[i]&0xff)-0.5)*guiScale/2);
			if (drawSteps) {
				if (i>0) gc.drawLine(x1, y1, x1, y2);
				gc.drawLine(x1, y2, x2, y2);
			} else if (i>0)
				gc.drawLine(x1, y1, x2, y2);
			x1 = x2;
			y1 = y2;
		}
		if (!isGray) {								// G R E E N
			gc.setForeground(ij.swt.Color.green);
			x1 = scaledX(-0.5, xMargin, width, mapSize);
			y1 = yMargin + height - 1 - (int)Math.round(((greens[0]&0xff)-0.5)*guiScale/2);
			for (int i=0; i<mapSize; i++) {
			x2 = scaledX(i+xShift, xMargin, width, mapSize);
				y2 = yMargin + height - 1 - (int)Math.round(((greens[i]&0xff)-0.5)*guiScale/2);
				if (drawSteps) {
					if (i>0) gc.drawLine(x1, y1, x1, y2);
					gc.drawLine(x1, y2, x2, y2);
				} else if (i>0)
					gc.drawLine(x1, y1, x2, y2);
				x1 = x2;
				y1 = y2;
			}
		
			gc.setForeground(ij.swt.Color.blue);					// B L U E
			x1 = scaledX(-0.5, xMargin, width, mapSize);
			y1 = yMargin + height - 1 - (int)Math.round(((blues[0]&0xff)-0.5)*guiScale/2);
			for (int i=0; i<mapSize; i++) {
				x2 = scaledX(i+xShift, xMargin, width, mapSize);
				y2 = yMargin + height - 1 - (int)Math.round(((blues[i]&0xff)-0.5)*guiScale/2);
				if (drawSteps) {
					if (i>0) gc.drawLine(x1, y1, x1, y2);
					gc.drawLine(x1, y2, x2, y2);
				} else if (i>0)
					gc.drawLine(x1, y1, x2, y2);
				x1 = x2;
				y1 = y2;
			}
		}
		x = xMargin;
		y = yMargin + height + (int)Math.round(2*guiScale);
		lut.drawColorBar(gc, x, y, width, barHeight);
		// g.dispose();
		y += barHeight + (int)(15 * guiScale);
		gc.setForeground(ij.swt.Color.black);
		gc.drawString("0", x - (int)(4 * guiScale), y);
		gc.drawString("" + (mapSize - 1), x + width - (int)(10 * guiScale), y);
		gc.drawString("255", (int)(7 * guiScale), yMargin + (int)(4 * guiScale));
		ImagePlus imp = new ImagePlus("Look-Up Table", img);
		// imp.show();
		AtomicReference<ImageProcessor> ipp = new AtomicReference<ImageProcessor>();
		ipp.set(ip);
		Display.getDefault().syncExec(() -> {
				new LutWindow(imp, new ImageCanvas(imp), ipp.get());
			
		});
		gc.dispose();
	}
	private int scaledX(double x, int xMargin, int width, int mapSize) {
		return xMargin + (int)Math.round(0.5 + (x+0.5)*width*(1.0/mapSize));
	}
} // LutViewer class

class LutWindow extends ImageWindow implements SelectionListener {

	private Button button;
	private ImageProcessor ip;
	private Composite comp;

	LutWindow(ImagePlus imp, ImageCanvas ic, ImageProcessor ip) {

		super(imp);
		this.ip = ip;
		comp = super.parentComposite;
		addPanel();
		shell.open();
	}

	void addPanel() {

		Composite panel = new Composite(comp, SWT.NONE);
		panel.setLayout(new GridLayout(1, true));
		panel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		button = new Button(comp, SWT.NONE);
		button.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		button.setText(" List... ");
		button.addSelectionListener(this);
		// panel.add(button);
		// add(panel);
		shell.pack();
	}

	@Override
	public void widgetSelected(SelectionEvent e) {

		actionPerformed(e);
	}

	public void actionPerformed(SelectionEvent e) {

		Object b = e.getSource();
		if(b == button)
			list(ip);
	}

	void list(ImageProcessor ip) {

		IndexColorModel icm = (IndexColorModel)ip.getColorModel();
		int size = icm.getMapSize();
		byte[] r = new byte[size];
		byte[] g = new byte[size];
		byte[] b = new byte[size];
		icm.getReds(r);
		icm.getGreens(g);
		icm.getBlues(b);
		ResultsTable rt = new ResultsTable();
		for(int i = 0; i < size; i++) {
			rt.setValue("Index", i, i);
			rt.setValue("Red", i, r[i] & 255);
			rt.setValue("Green", i, g[i] & 255);
			rt.setValue("Blue", i, b[i] & 255);
		}
		rt.show("LUT");
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent arg0) {
		// TODO Auto-generated method stub

	}
} // LutWindow class
