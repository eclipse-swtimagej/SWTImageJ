package ij.plugin.frame;

import java.awt.Point;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import ij.IJ;
import ij.ImageJ;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GUI;

/**
 * This plugin continuously plots ImageJ's memory utilization. Click on the plot
 * to force the JVM to do garbage collection.
 */
public class MemoryMonitor extends PlugInFrame {
	private static final double scale = Prefs.getGuiScale();
	private static final int width = (int) (250 * scale);
	private static final int height = (int) (90 * scale);
	private static final String LOC_KEY = "memory.loc";
	private static MemoryMonitor instance;
	private org.eclipse.swt.graphics.Image image;
	private int frames;
	private double[] mem;
	private int index;
	private long value;
	private double defaultMax = 20 * 1024 * 1024; // 20MB
	private double max = defaultMax;
	private long maxMemory = IJ.maxMemory();
	private boolean done;
	protected PlotCanvas ic;
	protected GC gc;

	public MemoryMonitor() {
		super("Memory");
		if (instance != null) {
			WindowManager.toFront(instance);
			return;
		} 
		instance = this;
		mem = new double[width + 1];
		Display display = Display.getDefault();
		display.syncExec(() -> {
			WindowManager.addWindow(MemoryMonitor.this);
			shell.setLayout(new org.eclipse.swt.layout.GridLayout(1, true));
			ic = new PlotCanvas(shell);
			ic.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			ic.setSize(width, height);
			// add(ic);
			// setResizable(false);
			shell.layout();
			shell.pack();
			shell.setSize(width, height + 100);
			Point loc = Prefs.getLocation(LOC_KEY);
			if (loc != null)
				shell.setLocation(loc.x, loc.y);
			else
				GUI.centerOnImageJScreen(MemoryMonitor.this.shell);

			shell.setVisible(true);

			ImageJ ij = IJ.getInstance();
			if (ij != null) {
				shell.addKeyListener(ij);
				ic.addKeyListener(ij);
				ic.addMouseListener(ij);
			}

		});

		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		while (!done) {

			display.syncExec(() -> {

				if (ic.isDisposed() == false)
					ic.redraw();
				IJ.wait(50);
				frames++;

			});

		}
	}

	void addText(GC gc) {
		double value2 = (double) value / 1048576L;
		String s = IJ.d2s(value2, value2 > 50 ? 0 : 2) + "MB";
		if (maxMemory > 0L) {
			double percent = value * 100 / maxMemory;
			s += " (" + (percent < 1.0 ? "<1" : IJ.d2s(percent, 0)) + "%)";
		}
		gc.drawString(s, 2, 15);
		String images = "" + WindowManager.getImageCount();
		gc.drawString(images, width - (5 + images.length() * 8), 15);
	}

	void updatePlot(GC gc) {
		double used = IJ.currentMemory();
		if (frames % 10 == 0)
			value = (long) used;
		if (used > 0.86 * max)
			max *= 2.0;
		mem[index++] = used;
		if (index == mem.length)
			index = 0;
		double maxmax = 0.0;
		for (int i = 0; i < mem.length; i++) {
			if (mem[i] > maxmax)
				maxmax = mem[i];
		}
		if (maxmax < defaultMax)
			max = defaultMax * 2;
		if (maxmax < defaultMax / 2)
			max = defaultMax;
		int index2 = index + 1;
		if (index2 == mem.length)
			index2 = 0;
		gc.setForeground(ij.swt.Color.white);
		gc.fillRectangle(0, 0, width, height);
		gc.setForeground(ij.swt.Color.black);
		double scale = height / max;
		int x1 = 0;
		int y1 = height - (int) (mem[index2] * scale);
		for (int x2 = 1; x2 < width; x2++) {
			index2++;
			if (index2 == mem.length)
				index2 = 0;
			int y2 = height - (int) (mem[index2] * scale);
			gc.drawLine(x1, y1, x2, y2);
			x1 = x2;
			y1 = y2;
		}
	}

	/** Overrides shellClosed() in PlugInDialog. */
	public void shellClosed(ShellEvent e) {
		e.doit = false;
		// super.close();
		instance = null;
		Prefs.saveLocation(LOC_KEY, shell.getLocation());
		done = true;
		super.shellClosed(e);
	}

	class PlotCanvas extends org.eclipse.swt.widgets.Canvas implements PaintListener {

		public PlotCanvas(Composite parent) {
			super(parent, SWT.DOUBLE_BUFFERED);
			addPaintListener(this);
			// TODO Auto-generated constructor stub
		}

		public void repaint() {
			Display.getDefault().syncExec(new Runnable() {
				public void run() {
					redraw();
				}
			});
		}

		/*
		 * public void update(Graphics g) { paint(g); }
		 */

		@Override
		public void paintControl(PaintEvent evt) {
			Display defaultDisplay = Display.getDefault();
			image = new org.eclipse.swt.graphics.Image(defaultDisplay, width, height);
			gc = new GC(image);
			// g = (Graphics2D)image.getGraphics();
			// gc.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			// RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			gc.setTextAntialias(SWT.ON);
			gc.setForeground(ij.swt.Color.white);
			gc.fillRectangle(0, 0, width, height);
			// gc.setFont(new Font("SansSerif",Font.PLAIN,(int)(12*Prefs.getGuiScale())));
			org.eclipse.swt.graphics.Font font = new org.eclipse.swt.graphics.Font(defaultDisplay,
					new FontData("SansSerif", 12, SWT.NORMAL));
			gc.setFont(font);
			updatePlot(gc);
			addText(gc);
			evt.gc.drawImage(image, 0, 0);
			image.dispose();
			font.dispose();

		}

	}

}
