package ij.plugin;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.image.IndexColorModel;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Display;
import org.jfree.swt.SWTGraphics2D;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ColorChooser;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.measure.SplineFitter;
import ij.plugin.frame.Recorder;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;

public class LUT_Editor implements PlugIn, SelectionListener {
	private ImagePlus imp;
	org.eclipse.swt.widgets.Button openButton, saveButton, resizeButton, invertButton;
	ColorPanel colorPanel;
	int bitDepth;

	public void run(String args) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null) {
			IJ.showMessage("LUT Editor", "No images are open");
			return;
		}
		bitDepth = imp.getBitDepth();
		if (bitDepth == 24) {
			IJ.showMessage("LUT Editor", "RGB images do not use LUTs");
			return;
		}
		if (bitDepth != 8) {
			imp.getProcessor().resetMinAndMax();
			imp.updateAndDraw();
		}
		Recorder.suspendRecording();
		int red = 0, green = 0, blue = 0;
		GenericDialog gd = new GenericDialog("LUT Editor");
		Display.getDefault().syncExec(() -> {
			org.eclipse.swt.widgets.Composite panel = new org.eclipse.swt.widgets.Composite(gd.getShell(), SWT.NONE);
			panel.setLayout(new org.eclipse.swt.layout.GridLayout(2, true));

			colorPanel = new ColorPanel(panel, imp);
			GridData gd_composite_1 = new GridData(SWT.LEFT, SWT.CENTER, true, true, 1, 4);
			gd_composite_1.widthHint = 200;
			gd_composite_1.heightHint = 200;
			colorPanel.setLayoutData(gd_composite_1);

			if (colorPanel.getMapSize() != 256) {
				IJ.showMessage("LUT Editor", "LUT must have 256 entries");
				return;
			}
			org.eclipse.swt.widgets.Composite buttonPanel = new org.eclipse.swt.widgets.Composite(panel, SWT.NONE);
			buttonPanel.setLayout(new org.eclipse.swt.layout.GridLayout(1, true));
			openButton = new org.eclipse.swt.widgets.Button(buttonPanel, SWT.NONE);
			openButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
			openButton.setText("Open...");
			openButton.addSelectionListener(LUT_Editor.this);
			// buttonPanel.add(openButton);
			saveButton = new org.eclipse.swt.widgets.Button(buttonPanel, SWT.NONE);
			saveButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
			saveButton.setText("Save...");
			saveButton.addSelectionListener(LUT_Editor.this);
			// buttonPanel.add(saveButton);
			resizeButton = new org.eclipse.swt.widgets.Button(buttonPanel, SWT.NONE);
			resizeButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
			resizeButton.setText("Set...");
			resizeButton.addSelectionListener(LUT_Editor.this);
			// buttonPanel.add(resizeButton);
			invertButton = new org.eclipse.swt.widgets.Button(buttonPanel, SWT.NONE);
			invertButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
			invertButton.setText("Invert...");
			invertButton.addSelectionListener(LUT_Editor.this);
			// buttonPanel.add(invertButton);
			panel.pack();
			// panel.add(colorPanel);
			// panel.add(buttonPanel);
			// gd.addPanel(panel);
			gd.getShell().layout();
			gd.showDialog();
			Recorder.resumeRecording();
			if (gd.wasCanceled()) {
				colorPanel.cancelLUT();
				return;
			} else {
				colorPanel.applyLUT();
				String lutName = imp.getProp(LUT.nameKey);
				if (lutName != null && !lutName.endsWith(" (edited)"))
					imp.setProp(LUT.nameKey, lutName + " (edited)");
			}

		});
	}

	void save() {
		try {
			IJ.run("LUT...");
		} // File>Save As>Lut...
		catch (RuntimeException e) {
		}
	}

	public void actionPerformed(SelectionEvent e) {
		Object source = e.getSource();
		if (source == openButton)
			colorPanel.open();
		else if (source == saveButton)
			save();
		else if (source == resizeButton)
			colorPanel.resize();
		else if (source == invertButton)
			colorPanel.invert();
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		actionPerformed(e);

	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		// TODO Auto-generated method stub

	}
}

class ColorPanel extends org.eclipse.swt.widgets.Canvas implements org.eclipse.swt.events.PaintListener,
		org.eclipse.swt.events.MouseListener, org.eclipse.swt.events.MouseMoveListener {
	static final int entryWidth = 12, entryHeight = 12;
	int rows = 16;
	int columns = 16;
	Color c[] = new Color[256];
	Color b;
	ColorProcessor cp;
	IndexColorModel origin;
	private ImagePlus imp;
	private int[] xSize = new int[256], redY, greenY, blueY;
	private int mapSize, x, y, initialC = -1, finalC = -1;
	private byte[] reds, greens, blues;
	private boolean updateLut;
	private static String[] choices = { "Replication", "Interpolation", "Spline Fitting" };
	private static String scaleMethod = choices[1];
	private int bitDepth;
	private boolean drag;
	private boolean clickingStarted;

	ColorPanel(org.eclipse.swt.widgets.Composite panel, ImagePlus imp) {
		super(panel, SWT.DOUBLE_BUFFERED);
		setup(imp);
	}

	public void setup(ImagePlus imp) {
		if (imp == null) {
			IJ.noImage();
			return;
		}
		this.imp = imp;
		bitDepth = imp.getBitDepth();
		ImageProcessor ip = imp.getChannelProcessor();
		IndexColorModel cm = (IndexColorModel) ip.getColorModel();
		origin = cm;
		mapSize = cm.getMapSize();
		reds = new byte[256];
		greens = new byte[256];
		blues = new byte[256];
		cm.getReds(reds);
		cm.getGreens(greens);
		cm.getBlues(blues);
		addMouseListener(this);
		addMouseMoveListener(this);
		addPaintListener(this);
		for (int index = 0; index < mapSize; index++)
			c[index] = new Color(reds[index] & 255, greens[index] & 255, blues[index] & 255);
	}

	public Dimension getPreferredSize() {
		return new Dimension(columns * entryWidth, rows * entryHeight);
	}

	public Dimension getMinimumSize() {
		return new Dimension(columns * entryWidth, rows * entryHeight);
	}

	int getMouseZone(int x, int y) {
		int horizontal = (int) x / entryWidth;
		int vertical = (int) y / entryHeight;
		int index = (columns * vertical + horizontal);
		return index;
	}

	public void colorRamp() {
		if (initialC > finalC) {
			int tmp = initialC;
			initialC = finalC;
			finalC = tmp;
		}
		float difference = finalC - initialC + 1;
		int start = (byte) c[initialC].getRed() & 255;
		int end = (byte) c[finalC].getRed() & 255;
		float rstep = (end - start) / difference;
		for (int index = initialC; index <= finalC; index++)
			reds[index] = (byte) (start + (index - initialC) * rstep);

		start = (byte) c[initialC].getGreen() & 255;
		end = (byte) c[finalC].getGreen() & 255;
		float gstep = (end - start) / difference;
		for (int index = initialC; index <= finalC; index++)
			greens[index] = (byte) (start + (index - initialC) * gstep);

		start = (byte) c[initialC].getBlue() & 255;
		end = (byte) c[finalC].getBlue() & 255;
		float bstep = (end - start) / difference;
		for (int index = initialC; index <= finalC; index++)
			blues[index] = (byte) (start + (index - initialC) * bstep);
		for (int index = initialC; index <= finalC; index++)
			c[index] = new Color(reds[index] & 255, greens[index] & 255, blues[index] & 255);
		repaint();
	}

	@Override
	public void mouseMove(org.eclipse.swt.events.MouseEvent e) {
		if (drag) {

			mouseDragged(e);

		}

	}

	@Override
	public void mouseDoubleClick(org.eclipse.swt.events.MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseDown(org.eclipse.swt.events.MouseEvent e) {
		drag = true;
		clickingStarted = true;
		mousePressed(e);
	}

	@Override
	public void mouseUp(org.eclipse.swt.events.MouseEvent e) {
		drag = false;

		if (clickingStarted) {
			/* Not used here! */
		}
		clickingStarted = false;
		mouseReleased(e);
	}

	public void mousePressed(org.eclipse.swt.events.MouseEvent e) {
		x = (e.x);
		y = (e.y);
		initialC = getMouseZone(x, y);
	}

	public void mouseReleased(org.eclipse.swt.events.MouseEvent e) {
		x = (e.x);
		y = (e.y);
		finalC = getMouseZone(x, y);
		if (initialC >= mapSize && finalC >= mapSize) {
			initialC = finalC = -1;
			return;
		}
		if (initialC >= mapSize)
			initialC = mapSize - 1;
		if (finalC >= mapSize)
			finalC = mapSize - 1;
		if (finalC < 0)
			finalC = 0;
		if (initialC == finalC) {
			b = c[finalC];
			ColorChooser cc = new ColorChooser("Color at Entry " + (finalC), c[finalC], false);
			c[finalC] = cc.getColor();
			if (c[finalC] == null) {
				c[finalC] = b;
			}
			colorRamp();
		} else {
			b = c[initialC];
			ColorChooser icc = new ColorChooser("Initial Entry (" + (initialC) + ")", c[initialC], false);
			c[initialC] = icc.getColor();
			if (c[initialC] == null) {
				c[initialC] = b;
				initialC = finalC = -1;
				return;
			}
			b = c[finalC];
			ColorChooser fcc = new ColorChooser("Final Entry (" + (finalC) + ")", c[finalC], false);
			c[finalC] = fcc.getColor();
			if (c[finalC] == null) {
				c[finalC] = b;
				initialC = finalC = -1;
				return;
			}
			colorRamp();
		}
		initialC = finalC = -1;
		applyLUT();
	}

	public void mouseDragged(org.eclipse.swt.events.MouseEvent e) {
		x = (e.x);
		y = (e.y);
		finalC = getMouseZone(x, y);
		IJ.showStatus("index=" + getIndex(finalC));
		repaint();
	}

	public void mouseMoved(MouseEvent e) {
		x = (e.getX());
		y = (e.getY());
		int entry = getMouseZone(x, y);
		if (entry < mapSize) {
			int red = reds[entry] & 255;
			int green = greens[entry] & 255;
			int blue = blues[entry] & 255;
			IJ.showStatus("index=" + getIndex(entry) + ", color=" + red + "," + green + "," + blue);
		} else
			IJ.showStatus("");
	}

	final String getIndex(int index) {
		if (bitDepth == 8)
			return ("" + index);
		ImageProcessor ip = imp.getProcessor();
		double min = ip.getMin();
		double max = ip.getMax();
		Calibration cal = imp.getCalibration();
		min = cal.getCValue(min);
		max = cal.getCValue(max);
		double value = min + (index / 255.0) * (max - min);
		int digits = (max - min) < 100 ? 2 : 0;
		return (index + " (" + IJ.d2s(value, digits) + ")");
	}

	void open() {
		try {
			IJ.run("LUT... ");
		} // File>Import>Lut...
		catch (RuntimeException e) {
		}
		updateLut = true;
		repaint();
	}

	void updateLut() {
		IndexColorModel cm = (IndexColorModel) imp.getChannelProcessor().getColorModel();
		if (mapSize == 0)
			return;
		cm.getReds(reds);
		cm.getGreens(greens);
		cm.getBlues(blues);
		for (int i = 0; i < mapSize; i++)
			c[i] = new Color(reds[i] & 255, greens[i] & 255, blues[i] & 255);
	}

	void invert() {
		byte[] reds2 = new byte[mapSize];
		byte[] greens2 = new byte[mapSize];
		byte[] blues2 = new byte[mapSize];
		for (int i = 0; i < mapSize; i++) {
			reds2[i] = (byte) (reds[mapSize - i - 1] & 255);
			greens2[i] = (byte) (greens[mapSize - i - 1] & 255);
			blues2[i] = (byte) (blues[mapSize - i - 1] & 255);
		}
		reds = reds2;
		greens = greens2;
		blues = blues2;
		for (int i = 0; i < mapSize; i++)
			c[i] = new Color(reds[i] & 255, greens[i] & 255, blues[i] & 255);
		applyLUT();
		repaint();
	}

	void resize() {
		GenericDialog sgd = new GenericDialog("LUT Editor");
		sgd.addNumericField("Number of Colors:", mapSize, 0);
		sgd.addChoice("Scale Using:", choices, scaleMethod);
		sgd.showDialog();
		if (sgd.wasCanceled()) {
			cancelLUT();
			return;
		}
		int newSize = (int) sgd.getNextNumber();
		if (newSize < 2)
			newSize = 2;
		if (newSize > 256)
			newSize = 256;
		scaleMethod = sgd.getNextChoice();
		scale(reds, greens, blues, newSize);
		mapSize = newSize;
		for (int i = 0; i < mapSize; i++)
			c[i] = new Color(reds[i] & 255, greens[i] & 255, blues[i] & 255);
		applyLUT();
		repaint();
	}

	void scale(byte[] reds, byte[] greens, byte[] blues, int newSize) {
		if (newSize == mapSize)
			return;
		else if (newSize < mapSize || scaleMethod.equals(choices[0]))
			scaleUsingReplication(reds, greens, blues, newSize);
		else if (scaleMethod.equals(choices[1]))
			scaleUsingInterpolation(reds, greens, blues, newSize);
		else
			scaleUsingSplineFitting(reds, greens, blues, newSize);
	}

	void scaleUsingReplication(byte[] reds, byte[] greens, byte[] blues, int newSize) {
		byte[] reds2 = new byte[256];
		byte[] greens2 = new byte[256];
		byte[] blues2 = new byte[256];
		for (int i = 0; i < mapSize; i++) {
			reds2[i] = reds[i];
			greens2[i] = greens[i];
			blues2[i] = blues[i];
		}
		for (int i = 0; i < newSize; i++) {
			int index = (int) (i * ((double) mapSize / newSize));
			reds[i] = reds2[index];
			greens[i] = greens2[index];
			blues[i] = blues2[index];
		}
	}

	void scaleUsingInterpolation(byte[] reds, byte[] greens, byte[] blues, int newSize) {
		int[] r = new int[mapSize];
		int[] g = new int[mapSize];
		int[] b = new int[mapSize];
		for (int i = 0; i < mapSize; i++) {
			r[i] = reds[i] & 255;
			g[i] = greens[i] & 255;
			b[i] = blues[i] & 255;
		}
		double scale = (double) (mapSize - 1) / (newSize - 1);
		int i1, i2;
		double fraction;
		for (int i = 0; i < newSize; i++) {
			i1 = (int) (i * scale);
			i2 = i1 + 1;
			if (i2 == mapSize)
				i2 = mapSize - 1;
			fraction = i * scale - i1;
			// IJ.log(i+" "+i1+" "+i2+" "+fraction+" "+mapSize+" "+newSize);
			reds[i] = (byte) ((1.0 - fraction) * r[i1] + fraction * r[i2]);
			greens[i] = (byte) ((1.0 - fraction) * g[i1] + fraction * g[i2]);
			blues[i] = (byte) ((1.0 - fraction) * b[i1] + fraction * b[i2]);
		}
	}

	void scaleUsingSplineFitting(byte[] reds, byte[] greens, byte[] blues, int newSize) {
		// IJ.log("scaleUsingSplineFitting: "+mapSize+" "+newSize);
		int[] reds2 = new int[mapSize];
		int[] greens2 = new int[mapSize];
		int[] blues2 = new int[mapSize];
		for (int i = 0; i < mapSize; i++) {
			reds2[i] = reds[i] & 255;
			greens2[i] = greens[i] & 255;
			blues2[i] = blues[i] & 255;
		}
		int[] xValues = new int[mapSize];
		for (int i = 0; i < mapSize; i++) {
			xValues[i] = (int) (i * (double) newSize / (mapSize - 1));
			// IJ.log(i+" "+xValues[i]+" "+reds2[i]);
		}
		SplineFitter sfReds = new SplineFitter(xValues, reds2, mapSize);
		SplineFitter sfGreens = new SplineFitter(xValues, greens2, mapSize);
		SplineFitter sfBlues = new SplineFitter(xValues, blues2, mapSize);
		for (int i = 0; i < newSize; i++) {
			double v = Math.round(sfReds.evalSpline(xValues, reds2, mapSize, i));
			if (v < 0.0)
				v = 0.0;
			if (v > 255.0)
				v = 255.0;
			reds[i] = (byte) v;
			v = Math.round(sfGreens.evalSpline(xValues, greens2, mapSize, i));
			if (v < 0.0)
				v = 0.0;
			if (v > 255.0)
				v = 255.0;
			greens[i] = (byte) v;
			v = Math.round(sfBlues.evalSpline(xValues, blues2, mapSize, i));
			if (v < 0.0)
				v = 0.0;
			if (v > 255.0)
				v = 255.0;
			blues[i] = (byte) v;
		}
	}

	public void cancelLUT() {
		if (mapSize == 0)
			return;
		origin.getReds(reds);
		origin.getGreens(greens);
		origin.getBlues(blues);
		mapSize = 256;
		applyLUT();
	}

	public void applyLUT() {
		byte[] reds2 = reds, greens2 = greens, blues2 = blues;
		if (mapSize < 256) {
			reds2 = new byte[256];
			greens2 = new byte[256];
			blues2 = new byte[256];
			for (int i = 0; i < mapSize; i++) {
				reds2[i] = reds[i];
				greens2[i] = greens[i];
				blues2[i] = blues[i];
			}
			scale(reds2, greens2, blues2, 256);
		}
		IndexColorModel cm = new IndexColorModel(8, 256, reds2, greens2, blues2);
		ImageProcessor ip = imp.getChannelProcessor();
		ip.setColorModel(cm);
		if (imp.isComposite())
			((CompositeImage) imp).setChannelColorModel(cm);
		if (imp.getStackSize() > 1 && !imp.isComposite())
			imp.getStack().setColorModel(cm);
		imp.updateAndDraw();
	}

	public void repaint() {
		redraw();
	}

	@Override
	public void paintControl(PaintEvent e) {
		GC gc = e.gc;
		SWTGraphics2D g = new SWTGraphics2D(gc);
		paint(g);
		g.dispose();
		// gc.dispose();
	}

	public void paint(Graphics g) {
		if (updateLut) {
			updateLut();
			updateLut = false;
		}
		int index = 0;
		for (int y = 0; y < rows; y++) {
			for (int x = 0; x < columns; x++) {
				if (index >= mapSize) {
					g.setColor(Color.lightGray);
					g.fillRect(x * entryWidth, y * entryHeight, entryWidth, entryHeight);
				} else if (((index <= finalC) && (index >= initialC)) || ((index >= finalC) && (index <= initialC))) {
					g.setColor(c[index].brighter());
					g.fillRect(x * entryWidth, y * entryHeight, entryWidth, entryHeight);
					g.setColor(Color.white);
					g.drawRect((x * entryWidth), (y * entryHeight), entryWidth, entryHeight);
					g.setColor(Color.black);
					g.drawLine((x * entryWidth) + entryWidth - 1, (y * entryHeight), (x * entryWidth) + entryWidth - 1,
							(y * entryWidth) + entryHeight);
					g.drawLine((x * entryWidth), (y * entryHeight) + entryHeight - 1, (x * entryWidth) + entryWidth - 1,
							(y * entryHeight) + entryHeight - 1);
					g.setColor(Color.white);
				} else {
					g.setColor(c[index]);
					g.fillRect(x * entryWidth, y * entryHeight, entryWidth, entryHeight);
					g.setColor(Color.white);
					g.drawRect((x * entryWidth), (y * entryHeight), entryWidth - 1, entryHeight - 1);
					g.setColor(Color.black);
					g.drawLine((x * entryWidth), (y * entryHeight), (x * entryWidth) + entryWidth - 1,
							(y * entryWidth));
					g.drawLine((x * entryWidth), (y * entryHeight), (x * entryWidth),
							(y * entryHeight) + entryHeight - 1);
				}
				index++;
			}
		}
	}

	int getMapSize() {
		return mapSize;
	}

}
