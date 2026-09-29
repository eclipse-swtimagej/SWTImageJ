package ij.plugin;

import java.awt.Color;
import java.awt.Font;
import java.util.Vector;

import org.eclipse.swt.events.TypedEvent;

import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.Overlay;
import ij.plugin.filter.Analyzer;
import ij.util.Tools;

/** This plugin implements the Image/Overlay/Labels command. */
public class OverlayLabels implements PlugIn, DialogListener {

	private static final String[] fontSizes = {"7", "8", "9", "10", "12", "14", "18", "24", "28", "36", "48", "72"};
	/*
	 * Previously nothing here was ever persisted to Prefs (only ImageCanvas.suppressSmallLabels
	 * was, in run() below) - defaultOverlay's font size/color/bold/etc. lived only in this
	 * static field, in memory, resetting to the small built-in defaults (12pt, or as little as
	 * 9pt via ImageCanvas.drawRoiLabel()'s own per-ROI-size fallback whenever no font was set at
	 * all) every time the app restarted. Loading/saving through these keys is what makes a
	 * chosen label size an actual persistent default instead of a same-session-only tweak.
	 */
	private static final String FONT_SIZE_KEY = "overlay.labelfontsize";
	private static final String BOLD_KEY = "overlay.labelbold";
	private static final String COLOR_KEY = "overlay.labelcolor";
	private static final String SHOW_NAMES_KEY = "overlay.shownames";
	private static final String DRAW_BACKGROUNDS_KEY = "overlay.drawbackgrounds";
	private static Overlay defaultOverlay = new Overlay();
	static {
		int savedFontSize = (int)Prefs.get(FONT_SIZE_KEY, 14);
		boolean savedBold = Prefs.get(BOLD_KEY, false);
		defaultOverlay.setLabelFont(new Font("SansSerif", savedBold ? Font.BOLD : Font.PLAIN, savedFontSize));
		defaultOverlay.setLabelColor(Colors.getColor(Prefs.get(COLOR_KEY, "white"), Color.white));
		defaultOverlay.drawNames(Prefs.get(SHOW_NAMES_KEY, false));
		defaultOverlay.drawBackgrounds(Prefs.get(DRAW_BACKGROUNDS_KEY, false));
	}
	private ImagePlus imp;
	private Overlay overlay;
	private GenericDialog gd;
	private boolean showLabels;
	private boolean showNames;
	private boolean drawBackgrounds;
	private String colorName;
	private int fontSize;
	private boolean bold;
	private boolean hideSmallLabels;

	public void run(String arg) {

		imp = WindowManager.getCurrentImage();
		overlay = null;
		if(imp != null) {
			ImageCanvas ic = imp.getCanvas();
			if(ic != null)
				overlay = ic.getShowAllList();
			if(overlay == null)
				overlay = imp.getOverlay();
		}
		if(overlay == null)
			overlay = defaultOverlay;
		showDialog();
		if(!gd.wasCanceled()) {
			defaultOverlay.drawLabels(overlay.getDrawLabels());
			defaultOverlay.drawNames(overlay.getDrawNames());
			defaultOverlay.drawBackgrounds(overlay.getDrawBackgrounds());
			defaultOverlay.setLabelColor(overlay.getLabelColor());
			defaultOverlay.setLabelFont(overlay.getLabelFont());
			// Remember every one of these across sessions too, not just the speed option below.
			Prefs.set(FONT_SIZE_KEY, fontSize);
			Prefs.set(BOLD_KEY, bold);
			Prefs.set(COLOR_KEY, colorName);
			Prefs.set(SHOW_NAMES_KEY, showNames);
			Prefs.set(DRAW_BACKGROUNDS_KEY, drawBackgrounds);
			// Remember the speed option across sessions.
			Prefs.set(ImageCanvas.SUPPRESS_SMALL_LABELS_KEY, ImageCanvas.suppressSmallLabels);
			/*
			 * Prefs.set() alone only updates the in-memory preferences map; it's only ever
			 * flushed to IJ_Prefs.txt by Prefs.savePreferences(), which normally happens once at
			 * ij.ImageJ.run()'s own quit()/dispose() - which never fires when this ImageJ
			 * instance is embedded in (and closed along with) a host application instead of
			 * being quit the normal standalone way, silently losing these settings between
			 * sessions.
			 */
			Prefs.savePreferences();
		} else {
			// Restore, since dialogItemChanged applies changes live.
			ImageCanvas.suppressSmallLabels = hideSmallLabelsOnEntry;
			repaint();
		}
	}

	/** Value on dialog entry, so Cancel can put it back. */
	private boolean hideSmallLabelsOnEntry;

	public void showDialog() {

		showLabels = overlay.getDrawLabels();
		showNames = overlay.getDrawNames();
		drawBackgrounds = overlay.getDrawBackgrounds();
		colorName = Colors.getColorName(overlay.getLabelColor(), "white");
		fontSize = 12;
		Font font = overlay.getLabelFont();
		if(font != null) {
			fontSize = font.getSize();
			bold = font.getStyle() == Font.BOLD;
		}
		hideSmallLabels = ImageCanvas.suppressSmallLabels;
		hideSmallLabelsOnEntry = hideSmallLabels;
		gd = new GenericDialog("Labels");
		gd.addChoice("Color:", Colors.colors, colorName);
		gd.addChoice("Font size:", fontSizes, "" + fontSize);
		gd.addCheckbox("Show labels", showLabels);
		gd.addCheckbox("Use names as labels", showNames);
		gd.addCheckbox("Draw backgrounds", drawBackgrounds);
		gd.addCheckbox("Bold", bold);
		// Appended last on purpose: dialogItemChanged reads checkboxes in the
		// order they were added, and getCheckboxes().elementAt(0) below must
		// stay "Show labels".
		gd.addCheckbox("Hide labels on tiny ROIs", hideSmallLabels);
		gd.addDialogListener(this);
		gd.showDialog();
	}

	public boolean dialogItemChanged(GenericDialog gd, TypedEvent e) {

		if(gd.wasCanceled())
			return false;
		String colorName2 = colorName;
		boolean showLabels2 = showLabels;
		boolean showNames2 = showNames;
		boolean drawBackgrounds2 = drawBackgrounds;
		boolean bold2 = bold;
		boolean hideSmallLabels2 = hideSmallLabels;
		int fontSize2 = fontSize;
		colorName = gd.getNextChoice();
		fontSize = (int)Tools.parseDouble(gd.getNextChoice(), 12);
		showLabels = gd.getNextBoolean();
		showNames = gd.getNextBoolean();
		drawBackgrounds = gd.getNextBoolean();
		bold = gd.getNextBoolean();
		hideSmallLabels = gd.getNextBoolean();
		boolean colorChanged = !colorName.equals(colorName2);
		boolean sizeChanged = fontSize != fontSize2;
		boolean changes = showLabels != showLabels2 || showNames != showNames2 || drawBackgrounds != drawBackgrounds2 || colorChanged || sizeChanged || bold != bold2 || hideSmallLabels != hideSmallLabels2;
		if(changes) {
			if((showNames && !showNames2) || colorChanged || sizeChanged) {
				showLabels = true;
				Vector checkboxes = gd.getCheckboxes();
				((org.eclipse.swt.widgets.Button)checkboxes.elementAt(0)).setSelection(true);
			}
			overlay.drawLabels(showLabels);
			Analyzer.drawLabels(showLabels);
			overlay.drawNames(showNames);
			overlay.drawBackgrounds(drawBackgrounds);
			Color color = Colors.getColor(colorName, Color.white);
			overlay.setLabelColor(color);
			if(sizeChanged || bold || bold != bold2)
				overlay.setLabelFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, fontSize));
			ImageCanvas.suppressSmallLabels = hideSmallLabels;
			if(hideSmallLabels != hideSmallLabels2)
				repaint(); // affects drawing even when the image has no Overlay set
			if(imp != null) {
				Overlay o = imp.getOverlay();
				if(o == null) {
					ImageCanvas ic = imp.getCanvas();
					if(ic != null)
						o = ic.getShowAllList();
				}
				if(o != null)
					imp.draw();
			}
		}
		return true;
	}

	/** Forces a repaint of the current image, if there is one. */
	private void repaint() {

		if(imp != null)
			imp.draw();
	}

	/** Creates an empty Overlay that has the current label settings. */
	public static Overlay createOverlay() {

		return defaultOverlay.duplicate();
	}
}