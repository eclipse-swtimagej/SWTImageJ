package ij.plugin.filter;

import java.awt.geom.Rectangle2D;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.Roi;
import ij.io.FileOpener;
import ij.plugin.frame.Recorder;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.util.Tools;

/** Implements the Analyze/Set Scale command. */
public class ScaleDialog implements PlugInFilter {

	private ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		IJ.register(ScaleDialog.class);
		return DOES_ALL + NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		double measured = 0.0;
		double known = 0.0;
		double aspectRatio = 1.0;
		String unit = "pixel";
		boolean global1 = imp.getGlobalCalibration() != null;
		boolean global2;
		Calibration cal = imp.getCalibration();
		Calibration calOrig = cal.copy();
		boolean isCalibrated = cal.scaled();
		String length = "0.00";

		String scale = "<no scale>";
		int digits = 2;
		Roi roi = imp.getRoi();
		if (roi != null) {
			if (roi instanceof Line) {
				measured = ((Line) roi).getRawLength();
				length = IJ.d2s(measured, 2);
			} else if (roi.getType() == Roi.RECTANGLE) {
				Rectangle2D r = roi.getFloatBounds();
				measured = Math.max(r.getWidth(), r.getHeight());
				length = IJ.d2s(measured, 2);
			}
		}
		if (isCalibrated) {
			if (measured != 0.0)
				known = measured * cal.pixelWidth;
			else {
				measured = 1.0 / cal.pixelWidth;
				known = 1.0;
			}
			double dscale = measured / known;
			digits = Tools.getDecimalPlaces(dscale);
			unit = cal.getUnit();
			scale = IJ.d2s(dscale, digits) + " pixels/" + unit;
			aspectRatio = cal.pixelHeight / cal.pixelWidth;
		}

		digits = Tools.getDecimalPlaces(measured);
		int asDigits = aspectRatio == 1.0 ? 1 : 3;
		SetScaleDialog gd = new SetScaleDialog("Set Scale", scale, length);
		gd.addNumericField("Distance in pixels:", measured, digits, 8, null);
		gd.addNumericField("Known distance:", known, 2, 8, null);
		gd.addNumericField("Pixel aspect ratio:", aspectRatio, asDigits, 8, null);
		gd.addStringField("Unit of length:", unit);
		gd.addPanel(makeButtonPanel(gd));
		gd.setInsets(0, 30, 0);
		gd.addCheckbox("Global", global1);
		gd.setInsets(10, 0, 0);
		gd.addMessage("Scale: " + "12345.789 pixels per centimeter");
		gd.addHelp(IJ.URL2 + "/docs/menus/analyze.html#scale");
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		measured = gd.getNextNumber();
		known = gd.getNextNumber();
		if (aspectRatio == 1.0)
			gd.setSmartRecording(true);
		aspectRatio = gd.getNextNumber();
		gd.setSmartRecording(false);
		unit = gd.getNextString();
		if (unit.equals("A"))
			unit = "" + IJ.angstromSymbol;
		global2 = gd.getNextBoolean();
		if (measured == known && unit.equals("unit"))
			unit = "pixel";
		if (measured <= 0.0 || known <= 0.0 || unit.startsWith("pixel") || unit.startsWith("Pixel")
				|| unit.equals("")) {
			cal.pixelWidth = 1.0;
			cal.pixelHeight = 1.0;
			cal.pixelDepth = 1.0;
			cal.setUnit("pixel");
		} else {
			if (gd.scaleChanged || IJ.macroRunning()) {
				cal.pixelWidth = known / measured;
				if (cal.pixelDepth == 1.0)
					cal.pixelDepth = cal.pixelWidth;
			}
			if (aspectRatio != 0.0)
				cal.pixelHeight = cal.pixelWidth * aspectRatio;
			else
				cal.pixelHeight = cal.pixelWidth;
			cal.setUnit(unit);
		}
		if (!cal.equals(calOrig)) {
			imp.setCalibration(cal);
			imp.changes = true;
		}
		imp.setGlobalCalibration(global2 ? cal : null);
		if (global2 || global2 != global1)
			WindowManager.repaintImageWindows();
		else
			imp.repaintWindow();
		if (global2 && global2 != global1)
			FileOpener.setShowConflictMessage(true);
	}

	/** Creates a panel containing an "Unscale" button. */
	Composite makeButtonPanel(SetScaleDialog gd) {
		// Panel panel = new Panel();
		AtomicReference<Composite> panel = new AtomicReference<Composite>();
		Display.getDefault().syncExec(() -> {
			panel.set(new Composite(gd.getShell(), SWT.NONE));
			GridLayout layout = new GridLayout(1, true);
			panel.get().setLayout(layout);
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			panel.get().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			gd.unscaleButton = new org.eclipse.swt.widgets.Button(panel.get(), SWT.NONE);

			gd.unscaleButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			gd.unscaleButton.setText("Click to Remove Scale");
			// gd.unscaleButton.addActionListener(gd);
			gd.unscaleButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {

					gd.actionPerformed(e);
				}
			});
			// panel.add(gd.unscaleButton);

		});
		return panel.get();
	}

}

class SetScaleDialog extends GenericDialog {
	static final String NO_SCALE = "<no scale>";
	String initialScale;
	org.eclipse.swt.widgets.Button unscaleButton;
	String length;
	boolean scaleChanged;

	public SetScaleDialog(String title, String scale, String length) {
		super(title);
		initialScale = scale;
		this.length = length;
	}

	protected void setup() {
		initialScale += "                   ";
		setScale(initialScale);
	}

	public void textValueChanged(ModifyEvent e) {
		Object source = e.getSource();
		if (source == numberField.elementAt(0) || source == numberField.elementAt(1))
			scaleChanged = true;
		Double d = getValue(((org.eclipse.swt.widgets.Text) numberField.elementAt(0)).getText());
		if (d == null) {
			setScale(NO_SCALE);
			return;
		}
		double measured = d.doubleValue();
		d = getValue(((org.eclipse.swt.widgets.Text) numberField.elementAt(1)).getText());
		if (d == null) {
			setScale(NO_SCALE);
			return;
		}
		double known = d.doubleValue();
		String theScale;
		String unit = ((org.eclipse.swt.widgets.Text) stringField.elementAt(0)).getText();
		boolean noUnit = unit.startsWith("pixel") || unit.startsWith("Pixel") || unit.equals("");
		if (known > 0.0 && noUnit && e.getSource() == numberField.elementAt(1)) {
			unit = "unit";
			((org.eclipse.swt.widgets.Text) stringField.elementAt(0)).setText(unit);
		}
		boolean noScale = measured <= 0 || known <= 0 || noUnit;
		if (noScale)
			theScale = NO_SCALE;
		else {
			double scale = measured / known;
			int digits = Tools.getDecimalPlaces(scale);
			theScale = IJ.d2s(scale, digits) + (scale == 1.0 ? " pixel/" : " pixels/") + unit;
		}
		setScale(theScale);
	}

	public void actionPerformed(TypedEvent e) {
		super.actionPerformed(e);
		if (e.getSource() == unscaleButton) {
			((org.eclipse.swt.widgets.Text) numberField.elementAt(0)).setText(length);
			((org.eclipse.swt.widgets.Text) numberField.elementAt(1)).setText("0.00");
			((org.eclipse.swt.widgets.Text) numberField.elementAt(2)).setText("1.0");
			((org.eclipse.swt.widgets.Text) stringField.elementAt(0)).setText("pixel");
			setScale(NO_SCALE);
			scaleChanged = true;
			// if (IJ.isMacOSX())
			// {setVisible(false); setVisible(true);}
			if (IJ.recording()) {
				Recorder.disableCommandRecording();
				if (Recorder.scriptMode())
					Recorder.recordCall("imp.removeScale();");
				else
					Recorder.record("Image.removeScale");
			}
		}
	}

	void setScale(String theScale) {
		((org.eclipse.swt.widgets.Label) theLabel).setText("Scale: " + theScale);
	}

}
