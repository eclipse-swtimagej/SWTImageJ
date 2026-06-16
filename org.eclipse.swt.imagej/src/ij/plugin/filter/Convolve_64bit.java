package ij.plugin.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.swt.events.TypedEvent;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.process.DoubleProcessor;
import ij.process.ImageProcessor;

/**
 * GUI command for double-precision convolution on DoubleProcessor images.
 *
 * Menu: Plugins > Filters > Convolve (64-bit)
 *
 * Mirrors the standard "Process > Filters > Convolve..." dialog, but routes
 * through DoubleProcessor.convolve(double[], int, int) so kernel coefficients
 * AND pixel state stay in IEEE-754 double precision end-to-end. Useful for
 * analytically-computed kernels (precise Gaussians, derivative-of-Gaussian
 * filters, custom PSFs) where the ~7-digit float32 ceiling of the stock
 * convolver matters.
 *
 * Behaviour:
 * - Accepts a free-form kernel as whitespace-separated numbers. Newlines
 * delimit rows. The kernel must be square OR rectangular with both
 * dimensions odd; the dialog rejects anything else.
 * - Optional "Normalize Kernel" checkbox: divides every coefficient by the
 * sum of all coefficients (skipped when the sum is zero, e.g. for
 * edge-detection kernels).
 * - Honours the current ROI / mask (delegated to DoubleProcessor.convolve).
 * - Supports stacks via the standard PlugInFilter "do all slices?" prompt.
 * - Supports macro recording: "run('Convolve (64-bit)...', 'kernel=[...] normalize')"
 *
 * Restriction: only operates on 64-bit images. For 8/16/32-bit images, point
 * the user at the standard Process > Filters > Convolve... command.
 */
public class Convolve_64bit implements ExtendedPlugInFilter, DialogListener {

	/** Default 3x3 sharpen kernel, identical to the stock Convolve dialog. */
	private static final String DEFAULT_KERNEL = "-1 -1 -1\n" + "-1  9 -1\n" + "-1 -1 -1";
	private ImagePlus imp;
	private String kernelText = DEFAULT_KERNEL;
	private boolean normalize = false;
	// Parsed kernel (cached between previewDialog() and run()).
	private double[] kernel;
	private int kernelWidth;
	private int kernelHeight;
	private PlugInFilterRunner pfr;
	private int nPasses = 1;
	private int pass = 0;

	@Override
	public int setup(String arg, ImagePlus imp) {

		this.imp = imp;
		// We require a 64-bit image. We accept stacks via DOES_STACKS.
		// Snapshot is needed for preview cancellation.
		return DOES_64 | SUPPORTS_MASKING | KEEP_PREVIEW | PARALLELIZE_STACKS;
	}

	@Override
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {

		this.pfr = pfr;
		GenericDialog gd = new GenericDialog(command);
		gd.addTextAreas(kernelText, null, 8, 30);
		gd.addCheckbox("Normalize Kernel", normalize);
		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.addHelp("https://imagej.net/ij/docs/menus/process.html#convolve");
		gd.showDialog();
		if(gd.wasCanceled())
			return DONE;
		// Parse one final time to confirm before running.
		try {
			parseAndPrepareKernel(kernelText, normalize);
		} catch(IllegalArgumentException ex) {
			IJ.error("Convolve (64-bit)", ex.getMessage());
			return DONE;
		}
		return IJ.setupDialog(imp, DOES_64 | SUPPORTS_MASKING);
	}

	@Override
	public boolean dialogItemChanged(GenericDialog gd, TypedEvent e) {

		// Read the dialog state.
		kernelText = gd.getNextText();
		normalize = gd.getNextBoolean();
		// Validate eagerly so the preview can't run with a broken kernel.
		try {
			parseAndPrepareKernel(kernelText, normalize);
			return true;
		} catch(IllegalArgumentException ex) {
			IJ.showStatus(ex.getMessage());
			return false;
		}
	}

	@Override
	public void setNPasses(int nPasses) {

		this.nPasses = Math.max(1, nPasses);
		this.pass = 0;
	}

	@Override
	public void run(ImageProcessor ip) {

		if(!(ip instanceof DoubleProcessor)) {
			// DOES_64 should already restrict us to 64-bit images, but be defensive.
			IJ.error("Convolve (64-bit)", "This command requires a 64-bit (DoubleProcessor) image.\n" + "Use 'Process > Filters > Convolve...' for 8/16/32-bit images.");
			return;
		}
		DoubleProcessor dp = (DoubleProcessor)ip;
		// Re-parse on every slice so a macro caller can supply the kernel
		// via Macro.getOptions() and we honour it without dialog state.
		String options = Macro.getOptions();
		if(options != null) {
			String macroKernel = parseMacroKernel(options);
			if(macroKernel != null)
				kernelText = macroKernel;
			normalize = options.toLowerCase().contains("normalize");
		}
		try {
			parseAndPrepareKernel(kernelText, normalize);
		} catch(IllegalArgumentException ex) {
			IJ.error("Convolve (64-bit)", ex.getMessage());
			return;
		}
		pass++;
		if(nPasses > 1)
			IJ.showStatus("Convolve (64-bit) " + pass + "/" + nPasses);
		dp.convolve(kernel, kernelWidth, kernelHeight);
		if(pfr != null && pfr.getSliceNumber() == imp.getStackSize())
			IJ.showProgress(1.0);
		// Record macro invocation on the last pass of a non-stack run.
		if(pass == 1 && nPasses == 1)
			recordMacro();
	}
	// --------------------------------------------------------------------
	// Parsing helpers
	// --------------------------------------------------------------------

	/**
	 * Parses the kernel text into a flat row-major double[] and validates
	 * that:
	 * - each row has the same number of columns,
	 * - the kernel has odd width and height,
	 * - the entries are valid finite doubles.
	 *
	 * Stores the result in {@link #kernel}, {@link #kernelWidth} and
	 * {@link #kernelHeight}. Applies normalization when requested.
	 *
	 * @throws IllegalArgumentException
	 *             with a user-facing message on any
	 *             validation failure.
	 */
	private void parseAndPrepareKernel(String text, boolean normalize) {

		if(text == null)
			throw new IllegalArgumentException("Empty kernel");
		String[] rows = splitRows(text);
		if(rows.length == 0)
			throw new IllegalArgumentException("Empty kernel");
		List<double[]> parsedRows = new ArrayList<double[]>(rows.length);
		int width = -1;
		for(int r = 0; r < rows.length; r++) {
			double[] row = parseRow(rows[r], r);
			if(row.length == 0)
				continue; // tolerate trailing blank lines
			if(width < 0)
				width = row.length;
			else if(row.length != width)
				throw new IllegalArgumentException("Row " + (r + 1) + " has " + row.length + " coefficients; expected " + width);
			parsedRows.add(row);
		}
		int height = parsedRows.size();
		if(width <= 0 || height <= 0)
			throw new IllegalArgumentException("Empty kernel");
		if((width & 1) == 0 || (height & 1) == 0)
			throw new IllegalArgumentException("Kernel dimensions must be odd; got " + width + "x" + height);
		double[] flat = new double[width * height];
		double sum = 0.0;
		int idx = 0;
		for(double[] row : parsedRows) {
			for(int c = 0; c < width; c++) {
				double v = row[c];
				if(Double.isNaN(v) || Double.isInfinite(v))
					throw new IllegalArgumentException("Kernel entry at row " + (idx / width + 1) + ", column " + (c + 1) + " is not a finite number: " + v);
				flat[idx++] = v;
				sum += v;
			}
		}
		if(normalize && sum != 0.0) {
			for(int i = 0; i < flat.length; i++)
				flat[i] /= sum;
		}
		this.kernel = flat;
		this.kernelWidth = width;
		this.kernelHeight = height;
	}

	/** Splits free-form kernel text into rows. Accepts \n, \r\n, or \r. */
	private static String[] splitRows(String text) {

		// Normalise newlines and trim leading/trailing blank lines.
		String norm = text.replace("\r\n", "\n").replace('\r', '\n').trim();
		if(norm.length() == 0)
			return new String[0];
		return norm.split("\n");
	}

	/** Parses a single whitespace-separated row of coefficients. */
	private static double[] parseRow(String row, int rowIndex) {

		StringTokenizer tok = new StringTokenizer(row);
		double[] out = new double[tok.countTokens()];
		int i = 0;
		while(tok.hasMoreTokens()) {
			String s = tok.nextToken();
			try {
				out[i++] = Double.parseDouble(s);
			} catch(NumberFormatException nfe) {
				throw new IllegalArgumentException("Row " + (rowIndex + 1) + ": '" + s + "' is not a number");
			}
		}
		return out;
	}

	/**
	 * Extracts a {@code kernel=[ ... ]} block from a macro options string,
	 * mirroring the format used by Process > Filters > Convolve... for
	 * macro recording. Returns null if no kernel argument is present.
	 */
	private static String parseMacroKernel(String options) {

		String key = "kernel=";
		int p = options.toLowerCase().indexOf(key);
		if(p < 0)
			return null;
		int q = p + key.length();
		// Accept either kernel=[ ... ] or kernel=' ... '
		if(q >= options.length())
			return null;
		char open = options.charAt(q);
		char close;
		if(open == '[')
			close = ']';
		else if(open == '\'')
			close = '\'';
		else if(open == '"')
			close = '"';
		else
			return null;
		int end = options.indexOf(close, q + 1);
		if(end < 0)
			return null;
		return options.substring(q + 1, end).trim();
	}

	/** Records a macro-friendly invocation. */
	private void recordMacro() {

		if(!ij.plugin.frame.Recorder.record)
			return;
		StringBuilder sb = new StringBuilder();
		sb.append("kernel=[");
		// Reconstruct row-major text from the (possibly normalized) flat kernel.
		for(int r = 0; r < kernelHeight; r++) {
			if(r > 0)
				sb.append('\n');
			for(int c = 0; c < kernelWidth; c++) {
				if(c > 0)
					sb.append(' ');
				sb.append(kernel[r * kernelWidth + c]);
			}
		}
		sb.append("]");
		if(normalize)
			sb.append(" normalize");
		ij.plugin.frame.Recorder.record("run", "Convolve (64-bit)...", sb.toString());
	}
}