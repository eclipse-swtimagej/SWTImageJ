package ij.plugin;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.io.OpenDialog;
import ij.plugin.frame.Recorder;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.util.Tools;

/**
 * This plugin opens a tab or comma delimeted text file as an image.
 * Modified to accept commas as delimiters on 4/22/08 by
 * Jay Unruh, Stowers Institute for Medical Research.
 */
public class TextReader implements PlugIn {

	int words = 0, chars = 0, lines = 0, width = 1;;
	String directory, name, path;
	boolean hideErrorMessages;
	String firstTok;
	// When true, File>Import>Text Image opens the file as a 64-bit
	// (DoubleProcessor) image, preserving full double precision. When false
	// (the default, matching stock ImageJ) it opens as a 32-bit FloatProcessor.
	private static boolean openAsDouble = Prefs.get("textreader.double", false);

	/**
	 * Enables or disables opening text images as 64-bit (double) images.
	 * The setting is persisted via Prefs. Default is false (32-bit float).
	 */
	public static void setOpenAsDouble(boolean b) {

		openAsDouble = b;
		Prefs.set("textreader.double", b);
	}

	/** Returns true if text images are opened as 64-bit (double) images. */
	public static boolean isOpenAsDouble() {

		return openAsDouble;
	}

	public void run(String arg) {

		// Honor macro options for headless/scripted use, e.g.:
		// run("Text Image... ", "open=[/path/file.txt] use");
		// 'use' (or the persisted Prefs flag) selects 64-bit (DoubleProcessor).
		String options = ij.Macro.getOptions();
		// Start from the persisted checkbox state (Edit>Options>Input/Output).
		boolean asDouble = openAsDouble;
		if(options != null) {
			String p = ij.Macro.getValue(options, "open", "");
			// Match the bare 'use' keyword as a whole token, NOT a substring:
			// otherwise a path such as "open=[/Users/me/data.txt]" (which
			// contains the letters "use") would wrongly force 64-bit mode.
			String optLower = (" " + options.toLowerCase() + " ").replace('\t', ' ');
			if(optLower.contains(" use ") || optLower.contains(" use="))
				asDouble = true;
			if(p != null && p.length() > 0) {
				path = p;
				java.io.File f = new java.io.File(p);
				name = f.getName();
				directory = f.getParent();
				IJ.showStatus("Opening: " + path);
				ImageProcessor ip = open(path, asDouble);
				if(ip != null)
					new ImagePlus(name, ip).show();
				if(IJ.recording() && Recorder.scriptMode()) {
					String path2 = Recorder.fixPath(path);
					Recorder.recordCall("imp = IJ.openImage(\"" + path2 + "\");");
				}
				return;
			}
		}
		// Interactive fallback (no usable macro options): show the open dialog.
		if(showDialog()) {
			IJ.showStatus("Opening: " + path);
			ImageProcessor ip = open(path, asDouble);
			if(ip != null)
				new ImagePlus(name, ip).show();
			if(IJ.recording() && Recorder.scriptMode()) {
				String path2 = Recorder.fixPath(path);
				Recorder.recordCall("imp = IJ.openImage(\"" + path2 + "\");");
			}
		}
	}

	boolean showDialog() {

		OpenDialog od = new OpenDialog("Open Text Image...", null);
		directory = od.getDirectory();
		name = od.getFileName();
		if(name != null)
			path = directory + name;
		return name != null;
	}

	/** Displays a file open dialog and opens the specified text file as a float image. */
	public ImageProcessor open() {

		if(showDialog())
			return open(path);
		else
			return null;
	}

	/**
	 * Opens the specified text file as a 32-bit float image, or as a
	 * 64-bit double image when {@link #setOpenAsDouble(boolean)} is enabled.
	 */
	public ImageProcessor open(String path) {

		return open(path, false);
	}

	/**
	 * Opens the specified text file as an image.
	 *
	 * @param asDouble
	 *            if true, pixels are read at full double precision into a
	 *            64-bit {@link ij.process.DoubleProcessor}; if false, the
	 *            legacy 32-bit {@link FloatProcessor} is produced.
	 */
	public ImageProcessor open(String path, boolean asDouble) {

		ImageProcessor ip = null;
		try {
			words = chars = lines = 0;
			Reader r = new BufferedReader(new FileReader(path));
			countLines(r);
			r.close();
			r = new BufferedReader(new FileReader(path));
			if(width * lines == 0) {
				r.close();
				return null;
			}
			int size = width * lines;
			if(asDouble) {
				// 64-bit: parse into double[] (no float cast) and build a DoubleProcessor.
				double[] pixels = new double[size];
				readDouble(r, size, pixels);
				r.close();
				ip = new ij.process.DoubleProcessor(width, lines, pixels);
				// Header-row detection mirrors the float path, using the double pixels.
				int firstRowNaNCount = 0;
				for(int i = 0; i < width; i++) {
					if(i < pixels.length && Double.isNaN(pixels[i]))
						firstRowNaNCount++;
				}
				if(firstRowNaNCount == width && !("NaN".equals(firstTok) || "nan".equals(firstTok))) {
					ip.setRoi(0, 1, width, lines - 1);
					ip = ip.crop();
				}
			} else {
				// Legacy 32-bit float path (unchanged behavior).
				float[] pixels = new float[size];
				ip = new FloatProcessor(width, lines, pixels, null);
				read(r, size, pixels);
				r.close();
				int firstRowNaNCount = 0;
				for(int i = 0; i < width; i++) {
					if(i < pixels.length && Float.isNaN(pixels[i]))
						firstRowNaNCount++;
				}
				if(firstRowNaNCount == width && !("NaN".equals(firstTok) || "nan".equals(firstTok))) {
					ip.setRoi(0, 1, width, lines - 1);
					ip = ip.crop();
				}
			}
			ip.resetMinAndMax();
		} catch(IOException e) {
			String msg = e.getMessage();
			if(msg == null || msg.equals(""))
				msg = "" + e;
			IJ.showProgress(1.0);
			if(!hideErrorMessages)
				IJ.error("Text Reader", msg);
			ip = null;
		}
		return ip;
	}

	public void hideErrorMessages() {

		hideErrorMessages = true;
	}

	/** Returns the file name. */
	public String getName() {

		return name;
	}

	void countLines(Reader r) throws IOException {

		StreamTokenizer tok = new StreamTokenizer(r);
		int wordsPerLine = 0, wordsInPreviousLine = 0;
		tok.resetSyntax();
		tok.wordChars(43, 43);
		tok.wordChars(45, 127);
		tok.whitespaceChars(0, 42);
		tok.whitespaceChars(44, 44);
		// tok.wordChars(33, 127);
		// tok.whitespaceChars(0, ' ');
		tok.whitespaceChars(128, 255);
		tok.eolIsSignificant(true);
		while(tok.nextToken() != StreamTokenizer.TT_EOF) {
			switch(tok.ttype) {
				case StreamTokenizer.TT_EOL:
					lines++;
					if(wordsPerLine == 0)
						lines--; // ignore empty lines
					if(lines == 1 && wordsPerLine > 0)
						width = wordsPerLine;
					if(lines > 1 && wordsPerLine != 0 && wordsPerLine != wordsInPreviousLine)
						throw new IOException("Line " + lines + " is not the same length as the first line.");
					if(wordsPerLine != 0)
						wordsInPreviousLine = wordsPerLine;
					wordsPerLine = 0;
					if(lines % 20 == 0 && width > 1 && lines <= width)
						IJ.showProgress(((double)lines / width) / 2.0);
					break;
				case StreamTokenizer.TT_WORD:
					words++;
					wordsPerLine++;
					break;
			}
		}
		if(wordsPerLine == width)
			lines++; // last line does not end with EOL
	}

	/** Reads tokens into a double[] at full precision (no float narrowing). */
	void readDouble(Reader r, int size, double[] pixels) throws IOException {

		StreamTokenizer tok = new StreamTokenizer(r);
		tok.resetSyntax();
		tok.wordChars(43, 43);
		tok.wordChars(45, 127);
		tok.whitespaceChars(0, 42);
		tok.whitespaceChars(44, 44);
		tok.whitespaceChars(128, 255);
		int i = 0;
		int inc = size / 20;
		if(inc < 1)
			inc = 1;
		while(tok.nextToken() != StreamTokenizer.TT_EOF) {
			if(tok.ttype == StreamTokenizer.TT_WORD) {
				if(i == 0)
					firstTok = tok.sval;
				pixels[i++] = Tools.parseDouble(tok.sval, Double.NaN); // full double, no (float) cast
				if(i == size)
					break;
				if(i % inc == 0)
					IJ.showProgress(0.5 + ((double)i / size) / 2.0);
			}
		}
		IJ.showProgress(1.0);
	}

	void read(Reader r, int size, float[] pixels) throws IOException {

		StreamTokenizer tok = new StreamTokenizer(r);
		tok.resetSyntax();
		tok.wordChars(43, 43);
		tok.wordChars(45, 127);
		tok.whitespaceChars(0, 42);
		tok.whitespaceChars(44, 44);
		// tok.wordChars(33, 127);
		// tok.whitespaceChars(0, ' ');
		tok.whitespaceChars(128, 255);
		// tok.parseNumbers();
		int i = 0;
		int inc = size / 20;
		if(inc < 1)
			inc = 1;
		while(tok.nextToken() != StreamTokenizer.TT_EOF) {
			if(tok.ttype == StreamTokenizer.TT_WORD) {
				if(i == 0)
					firstTok = tok.sval;
				pixels[i++] = (float)Tools.parseDouble(tok.sval, Double.NaN);
				if(i == size)
					break;
				if(i % inc == 0)
					IJ.showProgress(0.5 + ((double)i / size) / 2.0);
			}
		}
		IJ.showProgress(1.0);
	}
}
