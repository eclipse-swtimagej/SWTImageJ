package ij.plugin.frame;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Text;

import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.frame.Recorder;
import ij.util.Tools;

/** Adjusts the width of line selections. */
public class LineWidthAdjuster extends PlugInFrame implements PlugIn, Runnable, SelectionListener, ModifyListener {

	public static final String LOC_KEY = "line.loc";
	int sliderRange = 300;
	Slider slider;
	int value;
	boolean setText;
	static LineWidthAdjuster instance;
	Thread thread;
	boolean done;
	Text tf;
	Button checkbox;
	int lineWidth0 = (int)Line.getWidth();

	public LineWidthAdjuster() {
		super("Line Width");
		if (instance != null) {
			WindowManager.toFront(instance);
			return;
		}
		shell.setLayout(new org.eclipse.swt.layout.GridLayout(1, true));
		WindowManager.addWindow(this);
		instance = this;

		// GUI.fixScrollbar(slider);
		// slider.setFocusable(false); // prevents blinking on Windows

		org.eclipse.swt.widgets.Composite panel = new org.eclipse.swt.widgets.Composite(shell, SWT.NONE);
		panel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		panel.setLayout(new org.eclipse.swt.layout.GridLayout(1, true));

		slider = new org.eclipse.swt.widgets.Slider(panel, SWT.HORIZONTAL);
		slider.setValues(Line.getWidth(), 1, sliderRange + 1, 1, 1, 1);
		slider.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		slider.addSelectionListener(this);
		/*int margin = IJ.isMacOSX()?5:0;
		GridBagLayout grid = new GridBagLayout();
		GridBagConstraints c  = new GridBagConstraints();*/
		/*panel.setLayout(grid);
		c.gridx = 0; c.gridy = 0;
		c.gridwidth = 1;
		c.ipadx = 100;
		c.insets = new Insets(margin, 15, margin, 5);
		c.anchor = GridBagConstraints.CENTER;
		grid.setConstraints(slider, c);
		panel.add(slider);
		c.ipadx = 0;  // reset
		c.gridx = 1;
		c.insets = new Insets(margin, 5, margin, 15);*/
		tf = new org.eclipse.swt.widgets.Text(panel, SWT.SINGLE);
		tf.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		tf.setText("" + Line.getWidth());
		tf.addModifyListener(this);
		/*grid.setConstraints(tf, c);
		panel.add(tf);
		
		c.gridx = 2;
		c.insets = new Insets(margin, 25, margin, 5);*/
		checkbox = new org.eclipse.swt.widgets.Button(panel, SWT.CHECK);
		checkbox.setText("Spline fit");
		checkbox.setSelection(isSplineFit());
		checkbox.addSelectionListener(this);
		// panel.add(checkbox);

		// add(panel, BorderLayout.CENTER);

		// slider.setUnitIncrement(1);

		// GUI.scale(this);
		shell.layout();
		shell.pack();
		Point loc = Prefs.getLocation(LOC_KEY);
		if (loc != null)
			shell.setLocation(loc.x, loc.y);
		else
			GUI.centerOnImageJScreen(this.shell);
		// shell.setResizable(false);
		shell.setVisible(true);
		thread = new Thread(this, "LineWidthAdjuster");
		thread.start();
		setup();
		shell.addKeyListener(IJ.getInstance());
	}

	public synchronized void adjustmentValueChanged(SelectionEvent e) {
		value = slider.getSelection();
		setText = true;
		notify();
	}

	@Override
	public void modifyText(ModifyEvent e) {
		textValueChanged(e);

	}

	public synchronized void textValueChanged(ModifyEvent e) {
		int width = (int) Tools.parseDouble(tf.getText(), -1);
		// IJ.log(""+width);
		if (width == -1)
			return;
		if (width < 0)
			width = 1;
		if (width != Line.getWidth()) {
			slider.setSelection(width);
			value = width;
			notify();
		}
	}

	void setup() {
	}

	// Separate thread that does the potentially time-consuming processing
	public void run() {
		while (!done) {
			synchronized (this) {
				try {
					wait();
				} catch (InterruptedException e) {
				}
				if (done)
					return;
			}
			if (setText) {
				Display.getDefault().syncExec(new Runnable() {
					public void run() {
						tf.setText("" + value);
					}
				});
			}
			setText = false;
			Line.setWidth(value);
			updateRoi();
		}
	}

	private static void updateRoi() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp != null) {
			Roi roi = imp.getRoi();
			if (roi != null && roi.isLine()) {
				roi.updateWideLine(Line.getWidth());
				imp.draw();
				return;
			}
		}
		Roi previousRoi = Roi.getPreviousRoi();
		if (previousRoi == null)
			return;
		int id = previousRoi.getImageID();
		if (id >= 0)
			return;
		imp = WindowManager.getImage(id);
		if (imp == null)
			return;
		Roi roi = imp.getRoi();
		if (roi != null && roi.isLine()) {
			roi.updateWideLine(Line.getWidth());
			imp.draw();
		}
	}

	boolean isSplineFit() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null)
			return false;
		Roi roi = imp.getRoi();
		if (roi == null)
			return false;
		if (!(roi instanceof PolygonRoi))
			return false;
		return ((PolygonRoi) roi).isSplineFit();
	}

	/** Overrides shellClosed() in PlugInDialog. */
	public void shellClosed(ShellEvent e) {
		// super.close();
		e.doit = false;
		instance = null;
		done = true;
		Prefs.saveLocation(LOC_KEY, shell.getLocation());
		int strokeWidth = -1;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			Roi roi = imp.getRoi();
			if (roi!=null && roi.isLine())
				strokeWidth = (int)roi.getStrokeWidth();
		}
		if (IJ.recording() && strokeWidth>=0 && strokeWidth!=lineWidth0) {
			if (Recorder.scriptMode()) {
				Recorder.recordCall("roi = imp.getRoi();");
				Recorder.recordCall("roi.setStrokeWidth("+strokeWidth+");");
				Recorder.recordCall("imp.draw();");
			} else {
				Recorder.record("Roi.setStrokeWidth", strokeWidth);
			}
			Recorder.disableCommandRecording();
		}
		synchronized (this) {
			notify();
		}
		super.shellClosed(e);
	}

	public void shellActivated(ShellEvent e) {
		super.shellActivated(e);
		checkbox.setSelection(isSplineFit());
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		if (e.widget instanceof org.eclipse.swt.widgets.Slider) {
			adjustmentValueChanged(e);
		} else if (e.widget instanceof org.eclipse.swt.widgets.Button) {
			itemStateChanged(e);
		}

	}

	public void itemStateChanged(SelectionEvent e) {
		org.eclipse.swt.widgets.Button but = (org.eclipse.swt.widgets.Button) e.widget;
		boolean selected = but.getSelection();
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null) {
			checkbox.setSelection(false);
			return;
		}
		;
		Roi roi = imp.getRoi();
		int type = roi != null ? roi.getType() : -1;

		if (roi == null || !(roi instanceof PolygonRoi) || type == Roi.FREEROI || type == Roi.FREELINE
				|| type == Roi.ANGLE) {
			checkbox.setSelection(false);
			return;
		}
		;
		PolygonRoi poly = (PolygonRoi) roi;
		boolean splineFit = poly.isSplineFit();
		if (selected && !splineFit) {
			poly.fitSpline(); // this must not call roi.notifyListeners (live plot would trigger it
								// continuously)
			Prefs.splineFitLines = true;
			imp.draw();
			roi.notifyListeners(RoiListener.MODIFIED);
		} else if (!selected && splineFit) {
			poly.removeSplineFit();
			Prefs.splineFitLines = false;
			imp.draw();
			roi.notifyListeners(RoiListener.MODIFIED);
		}
	}

	public static void update() {
		if (instance == null)
			return;
		instance.checkbox.setSelection(instance.isSplineFit());
		int sliderWidth = instance.slider.getSelection();
		int lineWidth = Line.getWidth();
		if (lineWidth != sliderWidth && lineWidth <= 200) {
			instance.slider.setSelection(lineWidth);
			instance.tf.setText("" + lineWidth);
		}
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent arg0) {
		// TODO Auto-generated method stub

	}

}
