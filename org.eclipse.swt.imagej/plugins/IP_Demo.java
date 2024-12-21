import java.awt.*;
import java.awt.event.*;
import java.io.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Display;

import ij.plugin.frame.*;
import ij.*;
import ij.process.*;
import ij.gui.*;

/**
 * Image Processing Demo. Demonstrates how to create a custom user interface,
 * how to use ImageJ's ImageProcessor class, and how to run commands in a
 * separate thread. 
 * This class was changed to work with SWT using the SWT GridLayout.
 */
public class IP_Demo extends PlugInFrame implements SelectionListener {

	private org.eclipse.swt.widgets.Canvas panel;
	private int previousID;
	private static IP_Demo instance;

	public IP_Demo() {

		super("IP Demo");
		if (instance != null) {
			instance.toFront();
			return;
		}
		instance = this;
		Display.getDefault().syncExec(() -> {
			shell.addKeyListener(IJ.getInstance());
			shell.setLayout(new org.eclipse.swt.layout.FillLayout());
			panel = new org.eclipse.swt.widgets.Canvas(IP_Demo.this.getShell(), SWT.NONE);
			panel.setLayout(new org.eclipse.swt.layout.GridLayout(4, true));
			addButton("Reset");
			addButton("Flip");
			addButton("Invert");
			addButton("Rotate");
			addButton("Lighten");
			addButton("Darken");
			addButton("Zoom In");
			addButton("Zoom Out");
			addButton("Smooth");
			addButton("Sharpen");
			addButton("Find Edges");
			addButton("Threshold");
			addButton("Add Noise");
			addButton("Reduce Noise");
			addButton("Macro1");
			addButton("Macro2");
			/* Shell is in the superclass! */
			shell.layout();
			shell.pack();
			GUI.centerOnImageJScreen(shell);
			shell.setVisible(true);
		});
	}

	void addButton(String label) {

		org.eclipse.swt.widgets.Button b = new org.eclipse.swt.widgets.Button(panel, SWT.NONE);
		b.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		b.setText(label);
		b.addSelectionListener(IP_Demo.this);
		b.addKeyListener(IJ.getInstance());
	}

	public void widgetSelected(SelectionEvent e) {

		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null) {
			IJ.beep();
			IJ.showStatus("No image");
			previousID = 0;
			return;
		}
		if (!imp.lock()) {
			previousID = 0;
			return;
		}
		int id = imp.getID();
		if (id != previousID)
			imp.getProcessor().snapshot();
		previousID = id;
		String label = ((org.eclipse.swt.widgets.Button) e.widget).getText();
		if (label == null)
			return;
		new Runner(label, imp);
	}

	/*
	 * Call a custom close method and then call the superclass shell closed method!
	 */
	public void shellClosed(ShellEvent e) {
		e.doit = false;
		instance = null;
		super.shellClosed(e);

	}

	class Runner extends Thread { // inner class

		private String command;
		private ImagePlus imp;

		Runner(String command, ImagePlus imp) {

			super(command);
			this.command = command;
			this.imp = imp;
			setPriority(Math.max(getPriority() - 2, MIN_PRIORITY));
			start();
		}

		public void run() {

			try {
				runCommand(command, imp);
			} catch (OutOfMemoryError e) {
				IJ.outOfMemory(command);
				if (imp != null)
					imp.unlock();
			} catch (Exception e) {
				CharArrayWriter caw = new CharArrayWriter();
				PrintWriter pw = new PrintWriter(caw);
				e.printStackTrace(pw);
				IJ.log(caw.toString());
				IJ.showStatus("");
				if (imp != null)
					imp.unlock();
			}
		}

		void runCommand(String command, ImagePlus imp) {

			ImageProcessor ip = imp.getProcessor();
			IJ.showStatus(command + "...");
			long startTime = System.currentTimeMillis();
			Roi roi = imp.getRoi();
			if (command.startsWith("Zoom") || command.startsWith("Macro") || command.equals("Threshold")) {
				roi = null;
				ip.resetRoi();
			}
			ImageProcessor mask = roi != null ? roi.getMask() : null;
			if (command.equals("Reset"))
				ip.reset();
			else if (command.equals("Flip"))
				ip.flipVertical();
			else if (command.equals("Invert"))
				ip.invert();
			else if (command.equals("Lighten")) {
				if (imp.isInvertedLut())
					ip.multiply(0.9);
				else
					ip.multiply(1.1);
			} else if (command.equals("Darken")) {
				if (imp.isInvertedLut())
					ip.multiply(1.1);
				else
					ip.multiply(0.9);
			} else if (command.equals("Rotate"))
				ip.rotate(30);
			else if (command.equals("Zoom In"))
				ip.scale(1.2, 1.2);
			else if (command.equals("Zoom Out"))
				ip.scale(.8, .8);
			else if (command.equals("Threshold"))
				ip.autoThreshold();
			else if (command.equals("Smooth"))
				ip.smooth();
			else if (command.equals("Sharpen"))
				ip.sharpen();
			else if (command.equals("Find Edges"))
				ip.findEdges();
			else if (command.equals("Add Noise"))
				ip.noise(20);
			else if (command.equals("Reduce Noise"))
				ip.medianFilter();
			else if (command.equals("Macro1"))
				macro1(imp, ip);
			else if (command.equals("Macro2"))
				macro2(imp, ip);
			if (mask != null)
				ip.reset(mask);
			imp.updateAndDraw();
			imp.unlock();
			IJ.showStatus((System.currentTimeMillis() - startTime) + " milliseconds");
		}

		void macro1(ImagePlus imp, ImageProcessor ip) {

			IJ.resetEscape();
			for (int i = 10; i <= 360; i += 10) {
				ip.reset();
				ip.rotate(i);
				imp.updateAndDraw();
				if (IJ.escapePressed())
					return;
				IJ.wait(10);
			}
		}

		void macro2(ImagePlus imp, ImageProcessor ip) {

			IJ.resetEscape();
			double scale = 1, m = 1.2;
			for (int i = 0; i < 20; i++) {
				ip.reset();
				scale *= m;
				ip.scale(scale, scale);
				imp.updateAndDraw();
				if (IJ.escapePressed())
					return;
				IJ.wait(10);
			}
			for (int i = 0; i < 20; i++) {
				ip.reset();
				scale /= m;
				ip.scale(scale, scale);
				imp.updateAndDraw();
				if (IJ.escapePressed())
					return;
				IJ.wait(10);
			}
		}
	} // Runner inner class

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {

		// TODO Auto-generated method stub
	}
} // IP_Demo class
