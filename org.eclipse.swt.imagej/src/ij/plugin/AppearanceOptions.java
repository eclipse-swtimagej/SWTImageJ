package ij.plugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Menus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.measure.Calibration;
import ij.plugin.frame.ContrastAdjuster;
import ij.plugin.frame.Recorder;
import ij.process.LUT;

/** This plugin implements the Edit/Options/Appearance command. */
public class AppearanceOptions implements PlugIn, DialogListener {
	private boolean interpolate = Prefs.interpolateScaledImages;
	private boolean open100 = Prefs.open100Percent;
	private boolean black = Prefs.blackCanvas;
	private boolean noBorder = Prefs.noBorder;
	private boolean inverting = Prefs.useInvertingLut;
	private int rangeIndex = ContrastAdjuster.get16bitRangeIndex();
	private LUT[] luts = getLuts();
	private int menuFontSize = Menus.getFontSize();
	private double saveScale = Prefs.getGuiScale();
	private boolean redrawn, repainted;

 	public void run(String arg) {
 		showDialog();
 	}
		
	void showDialog() {
		String[] ranges = ContrastAdjuster.getSixteenBitRanges();
		GenericDialog gd = new GenericDialog("Appearance");
		gd.addCheckbox("Interpolate zoomed images", Prefs.interpolateScaledImages);
		gd.addCheckbox("Open images at 100%", Prefs.open100Percent);
		gd.addCheckbox("Black canvas", Prefs.blackCanvas);
		gd.addCheckbox("No image border", Prefs.noBorder);
		gd.addCheckbox("Use inverting lookup table", Prefs.useInvertingLut);
		gd.addCheckbox("Auto contrast stacks", Prefs.autoContrast);
		gd.addCheckbox("IJ window always on top", Prefs.alwaysOnTop);
		if (IJ.isLinux())
			gd.addCheckbox("Cancel button on right", Prefs.dialogCancelButtonOnRight);
		gd.addChoice("16-bit range:", ranges, ranges[rangeIndex]);
		org.eclipse.swt.graphics.Font font = new org.eclipse.swt.graphics.Font(Display.getDefault(),
				new FontData("SansSerif", 9, SWT.NORMAL));
		//Font font = new Font("SansSerif", Font.PLAIN, 9);
		if (!IJ.isMacOSX()) {
			gd.setInsets(0, 0, 0);
			gd.addNumericField("Menu font size:", Menus.getFontSize(), 0, 4, "points");
			if (IJ.isWindows()) {
				gd.setInsets(2,30,5);
				gd.addMessage("Setting size>17 may not work on Windows", font);
			}
		}
		gd.setInsets(0, 0, 0);
		gd.addNumericField("GUI scale (0.5-3.0):", Prefs.getGuiScale(), 2, 5, "");
		gd.setInsets(2,20,0);
		gd.addMessage("Set to 1.5 to double size of tool icons, or 2.5 to triple", font);
		gd.addHelp(IJ.URL2+"/docs/menus/edit.html#appearance");
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
			Prefs.interpolateScaledImages = interpolate;
			Prefs.open100Percent = open100;
			Prefs.blackCanvas = black;
			Prefs.noBorder = noBorder;
			Prefs.useInvertingLut = inverting;
			Prefs.setGuiScale(saveScale);
			if (redrawn) draw();
			if (repainted) repaintWindow();
			Prefs.open100Percent = open100;
			if (rangeIndex!=ContrastAdjuster.get16bitRangeIndex()) {
				ContrastAdjuster.set16bitRange(rangeIndex);
				ImagePlus imp = WindowManager.getCurrentImage();
				Calibration cal = imp!=null?imp.getCalibration():null;
				if (imp!=null && imp.getType()==ImagePlus.GRAY16 && !cal.isSigned16Bit()) {
					imp.resetDisplayRange();
					if (rangeIndex==0 && imp.isComposite() && luts!=null)
						((CompositeImage)imp).setLuts(luts);
					imp.updateAndDraw();
				}
			}
			return;
		}
		boolean messageShown = false;
		double scale =  Prefs.getGuiScale();
		if (scale!=saveScale) {
			if (!IJ.isMacOSX()) {
				IJ.showMessage("Appearance", "Restart ImageJ to resize \"ImageJ\" window");
				messageShown = true;
			} else {
				ImageJ ij = IJ.getInstance();
				if (ij!=null)
					ij.resize();
			}	
		}
		boolean fontSizeChanged = menuFontSize!=Menus.getFontSize();
		if (fontSizeChanged)
			Menus.setFontSize(menuFontSize);
		if (!messageShown && fontSizeChanged && !IJ.isMacOSX())
			IJ.showMessage("Appearance", "Restart ImageJ to use the new font size");
		if (Prefs.useInvertingLut) {
			IJ.showMessage("Appearance",
				"The \"Use inverting lookup table\" option is set. Newly opened\n"+
				"8-bit images will use an inverting LUT (white=0, black=255).");
		}
		int range = ImagePlus.getDefault16bitRange();
		if (range>0 && Recorder.record) {
			if (Recorder.scriptMode())
				Recorder.recordCall("ImagePlus.setDefault16bitRange("+range+");");
			else
				Recorder.recordString("call(\"ij.ImagePlus.setDefault16bitRange\", "+range+");\n");
		}

	}
	
	public boolean dialogItemChanged(GenericDialog gd, TypedEvent e) {
		
		if (IJ.isMacOSX()) IJ.wait(100);
		boolean interpolate = gd.getNextBoolean();
		Prefs.open100Percent = gd.getNextBoolean();
		boolean blackCanvas = gd.getNextBoolean();
		boolean noBorder = gd.getNextBoolean();
		Prefs.useInvertingLut = gd.getNextBoolean();
		boolean alwaysOnTop = Prefs.alwaysOnTop;
		Prefs.autoContrast = gd.getNextBoolean();
		Prefs.alwaysOnTop = gd.getNextBoolean();
		//System.out.println(Prefs.alwaysOnTop);
		if (IJ.isLinux())
			Prefs.dialogCancelButtonOnRight = gd.getNextBoolean();
		if (!IJ.isMacOSX())
			menuFontSize = (int)gd.getNextNumber();
		Prefs.setGuiScale(gd.getNextNumber());
		if (interpolate!=Prefs.interpolateScaledImages) {
			Prefs.interpolateScaledImages = interpolate;
			draw();
		}
		if (blackCanvas!=Prefs.blackCanvas) {
			Prefs.blackCanvas = blackCanvas;
			repaintWindow();
		}
		if (noBorder!=Prefs.noBorder) {
			Prefs.noBorder = noBorder;
			repaintWindow();
		}
		if (alwaysOnTop!=Prefs.alwaysOnTop) {
			
			//In SWT only at startup possible!
			//ImageJ ij = IJ.getInstance();
		}
		int rangeIndex2 = gd.getNextChoiceIndex();
		int range1 = ImagePlus.getDefault16bitRange();
		int range2 = ContrastAdjuster.set16bitRange(rangeIndex2);
		ImagePlus imp = WindowManager.getCurrentImage();
		Calibration cal = imp!=null?imp.getCalibration():null;
		if (range1!=range2 && imp!=null && imp.getType()==ImagePlus.GRAY16 && !cal.isSigned16Bit()) {
			imp.resetDisplayRange();
			if (rangeIndex2==0 && imp.isComposite() && luts!=null)
				((CompositeImage)imp).setLuts(luts);
			imp.updateAndDraw();
		}
		return true;
    }
    
    private LUT[] getLuts() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null || imp.getBitDepth()!=16 || !imp.isComposite())
			return null;
		return ((CompositeImage)imp).getLuts();
    }
    
    void draw() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null)
			imp.draw();
		redrawn = true;
    }

	void repaintWindow() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			ImageWindow win = imp.getWindow();
			if (win!=null) {
				if (Prefs.blackCanvas) {
					win.getShell().setForeground(ij.swt.Color.white);
					win.getShell().setBackground(ij.swt.Color.black);
				} else {
					win.getShell().setForeground(ij.swt.Color.black);
					win.getShell().setBackground(ij.swt.Color.white);
				}
				imp.repaintWindow();
			}
		}
		repainted = true;
	}
		
}