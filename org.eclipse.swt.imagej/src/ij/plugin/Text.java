package ij.plugin;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.util.ArrayList;

import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.widgets.Display;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GUI;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.gui.Toolbar;

/**
 * This plugin implements the Edit/Options/Fonts command and the dialog
 * displayed when you double click on the text tool.
 */
public class Text implements PlugIn, DialogListener {
	private static final String LOC_KEY = "fonts.loc";
	private static final String[] styles = { "Plain", "Bold", "Italic", "Bold+Italic" };
	private static final String[] justifications = { "Left", "Center", "Right" };
	private static GenericDialog gd;
	private String font = TextRoi.getDefaultFontName();
	private int fontSize = TextRoi.getDefaultFontSize();
	private int style = TextRoi.getDefaultFontStyle();
	private int justification = TextRoi.getGlobalJustification();
	private int angle;
	private boolean antialiased = TextRoi.isAntialiased();
	private Color color = Toolbar.getForegroundColor();
	private String colorName;

	public synchronized void run(String arg) {
		Display dis = Display.getDefault();
		dis.syncExec(new Runnable() {
			public void run() {
				if (gd != null && gd.getShell().isVisible())
					gd.setActive();
				else
					showDialog();
			}
		});
	}

	private void showDialog() {
		ImagePlus imp = WindowManager.getCurrentImage();
		Roi roi = imp != null ? imp.getRoi() : null;
		TextRoi textRoi = roi != null && (roi instanceof TextRoi) ? (TextRoi) roi : null;
		String fillc = "None";
		TextRoi.setDefaultFillColor(null);
		TextRoi.setDefaultAngle(0.0);
		if (textRoi != null) {
			Font font = textRoi.getCurrentFont();
			fontSize = font.getSize();
			angle = (int) textRoi.getAngle();
			style = font.getStyle();
			justification = textRoi.getJustification();
			Color c = textRoi.getStrokeColor();
			if (c != null)
				color = c;
			fillc = Colors.colorToString2(textRoi.getFillColor());
			antialiased = textRoi.getAntiAlias();
		}
		colorName = Colors.colorToString2(color);
		gd = GUI.newNonBlockingDialog("Fonts");
		gd.addChoice("Font:", getFonts(), font);
		gd.addChoice("Style:", styles, styles[style]);
		gd.addChoice("Just:", justifications, justifications[justification]);
		gd.addChoice("Color:", Colors.getColors(colorName), colorName);
		gd.addChoice("Bkgd:", Colors.getColors("None", !"None".equals(fillc) ? fillc : null), fillc);
		gd.addSlider("Size:", 9, 200, fontSize);
		gd.addSlider("Angle:", -90, 90, angle);
		gd.addCheckbox("Antialiased text", antialiased);
		Point loc = Prefs.getLocation(LOC_KEY);
		if (IJ.debugMode) {
			Dimension screen = IJ.getScreenSize();
			IJ.log("Fonts: " + loc + " " + screen);
		}
		if (loc != null) {
			gd.centerDialog(false);
			gd.setLocation(new org.eclipse.swt.graphics.Point(loc.x, loc.y));
		}
		gd.addDialogListener(this);
		gd.setOKLabel("Close");
		gd.hideCancelButton();
		gd.showDialog();
		Display dis = Display.getDefault();
		dis.syncExec(new Runnable() {
			public void run() {
				org.eclipse.swt.graphics.Point location = gd.getLocation();
				Prefs.saveLocation(LOC_KEY, new Point(location.x, location.y));
			}
		});
	}

	String[] getFonts() {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		String[] fonts = ge.getAvailableFontFamilyNames();
		ArrayList names = new ArrayList();
		names.add("SansSerif");
		names.add("Serif");
		names.add("Monospaced");
		for (int i = 0; i < fonts.length; i++) {
			String f = fonts[i];
			if (f.length() <= 20 && !(f.equals("SansSerif") || f.equals("Serif") || f.equals("Monospaced")))
				names.add(f);
		}
		return (String[]) names.toArray(new String[names.size()]);
	}

	public boolean dialogItemChanged(GenericDialog gd, TypedEvent e) {
		ImagePlus imp = WindowManager.getCurrentImage();
		Roi roi = imp != null ? imp.getRoi() : null;
		TextRoi textRoi = roi != null && (roi instanceof TextRoi) ? (TextRoi) roi : null;
		font = gd.getNextChoice();
		style = gd.getNextChoiceIndex();
		justification = gd.getNextChoiceIndex();
		String colorName2 = gd.getNextChoice();
		String fillc = gd.getNextChoice();
		fontSize = (int) gd.getNextNumber();
		angle = (int) gd.getNextNumber();
		antialiased = gd.getNextBoolean();
		if (colorName != null && !colorName2.equals(colorName)) {
			Color color = Colors.decode(colorName2, null);
			Toolbar.setForegroundColor(color);
			colorName = colorName2;
		}
		TextRoi.setFont(font, fontSize, style, antialiased);
		TextRoi.setGlobalJustification(justification);
		Color fillColor = Colors.decode(fillc, null);
		TextRoi.setDefaultFillColor(fillColor);
		TextRoi.setDefaultAngle(angle);
		if (textRoi != null) {
			textRoi.setAngle(angle);
			textRoi.setJustification(justification);
			textRoi.setFillColor(fillColor);
			textRoi.setAntiAlias(antialiased);
		}
		return true;
	}

}
