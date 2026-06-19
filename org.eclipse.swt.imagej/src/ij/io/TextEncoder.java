package ij.io;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import ij.IJ;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.DoubleProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/** Saves an image described by an ImageProcessor object as a tab-delimited text file. */
public class TextEncoder {

	private ImageProcessor ip;
	private Calibration cal;
	private int precision;
	private String delimiter = "\t";

	/** Constructs a TextEncoder from an ImageProcessor and optional Calibration. */
	public TextEncoder(ImageProcessor ip, Calibration cal, int precision) {

		this.ip = ip;
		this.cal = cal;
		this.precision = precision;
	}

	/**
	 * Saves the image as a tab-delimited text file.
	 * <p>
	 * 64-bit (DoubleProcessor) images are written at full double precision
	 * using {@link Double#toString(double)}, which emits the shortest decimal
	 * string that parses back to the exact same double. This applies whether
	 * or not the image is calibrated, so a 64-bit text round-trip is lossless.
	 * Byte/Short uncalibrated images keep integer formatting; all other types
	 * keep the legacy {@code IJ.d2s(value, precision)} formatting.
	 */
	public void write(DataOutputStream out) throws IOException {

		PrintWriter pw = new PrintWriter(out);
		boolean calibrated = cal != null && cal.calibrated();
		if(calibrated)
			ip.setCalibrationTable(cal.getCTable());
		else
			ip.setCalibrationTable(null);
		boolean intData = !calibrated && ((ip instanceof ByteProcessor) || (ip instanceof ShortProcessor));
		// 64-bit path: write full double precision (calibrated or not).
		boolean doubleData = ip instanceof DoubleProcessor;
		DoubleProcessor dp = doubleData ? (DoubleProcessor)ip : null;
		// Calibration table to apply at double precision when present. For 32/64-bit
		// float images this is typically null, so the raw double value is written.
		float[] cTable = (doubleData && calibrated && cal != null) ? cal.getCTable() : null;
		int width = ip.getWidth();
		int height = ip.getHeight();
		int inc = height / 20;
		if(inc < 1)
			inc = 1;
		// IJ.showStatus("Exporting as text...");
		double value;
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				if(doubleData) {
					double dv = dp.getd(x, y); // full 64-bit, no float narrowing
					if(cTable != null) {
						int index = (int)dv;
						if(index >= 0 && index < cTable.length)
							dv = cTable[index]; // apply calibration at double precision
						// else: index out of table range -> write raw double value
					}
					pw.print(Double.toString(dv)); // shortest round-trip-exact decimal
				} else if(intData) {
					value = ip.getPixelValue(x, y);
					pw.print((int)value);
				} else {
					value = ip.getPixelValue(x, y);
					pw.print(IJ.d2s(value, precision));
				}
				if(x != (width - 1))
					pw.print(delimiter);
			}
			pw.println();
			if(y % inc == 0)
				IJ.showProgress((double)y / height);
		}
		pw.close();
		IJ.showProgress(1.0);
		// IJ.showStatus("");
	}

	public void setDelimiter(String delimiter) {

		this.delimiter = delimiter;
	}
}