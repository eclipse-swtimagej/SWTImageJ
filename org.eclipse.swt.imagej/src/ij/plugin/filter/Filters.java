package ij.plugin.filter;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.process.ImageProcessor;

/**
 * This plugin implements the Invert, Smooth, Sharpen, Find Edges,
 * and Add Noise commands.
 */
public class Filters implements PlugInFilter {

	private static double sd = Prefs.getDouble(Prefs.NOISE_SD, 25.0);
	private String arg;
	private ImagePlus imp;
	private int slice;
	private boolean canceled;
	private boolean noRoi;

	public int setup(String arg, ImagePlus imp) {

		this.arg = arg;
		this.imp = imp;
		if(imp != null) {
			Roi roi = imp.getRoi();
			if(roi != null && !roi.isArea())
				noRoi = true;
		}
		// Add DOES_64 for 64-bit DoubleProcessor support. The five operations
		// in this filter (invert/smooth/sharpen/edge/noise) all dispatch via
		// the ImageProcessor API (ip.invert(), ip.smooth(), ip.sharpen(),
		// ip.findEdges(), ip.noise()), which DoubleProcessor implements
		// either directly or via inheritance.
		// DOES_8C is excluded for legacy reasons (8-bit color filtering quirks).
		int flags = IJ.setupDialog(imp, DOES_ALL - DOES_8C + DOES_64 + SUPPORTS_MASKING);
		return flags;
	}

	public void run(ImageProcessor ip) {

		if(noRoi)
			ip.resetRoi();
		if(arg.equals("invert")) {
			ip.invert();
			slice++;
			if(imp.getBitDepth() == 16 && imp.getStackSize() > 1 && slice == imp.getStackSize())
				imp.resetDisplayRange();
			return;
		}
		if(arg.equals("smooth")) {
			ip.setSnapshotCopyMode(true);
			ip.smooth();
			ip.setSnapshotCopyMode(false);
			return;
		}
		if(arg.equals("sharpen")) {
			ip.setSnapshotCopyMode(true);
			ip.sharpen();
			ip.setSnapshotCopyMode(false);
			return;
		}
		if(arg.equals("edge")) {
			ip.setSnapshotCopyMode(true);
			ip.findEdges();
			ip.setSnapshotCopyMode(false);
			return;
		}
		if(arg.equals("add")) {
			ip.noise(25.0);
			return;
		}
		if(arg.equals("noise")) {
			if(canceled)
				return;
			slice++;
			if(slice == 1) {
				GenericDialog gd = new GenericDialog("Gaussian Noise");
				gd.addNumericField("Standard Deviation:", sd, 2);
				gd.showDialog();
				if(gd.wasCanceled()) {
					canceled = true;
					return;
				}
				sd = gd.getNextNumber();
			}
			ip.noise(sd);
			IJ.register(Filters.class);
			return;
		}
	}

	/** Returns the default standard deviation used by Process/Noise/Add Specified Noise. */
	public static double getSD() {

		return sd;
	}
}
