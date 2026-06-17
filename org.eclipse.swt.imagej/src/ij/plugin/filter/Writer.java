package ij.plugin.filter;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ImageProcessor;

/**
 * Obsolete
 * 
 * @deprecated
 */
public class Writer implements PlugInFilter {

	private String arg;
	private ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {

		this.arg = arg;
		this.imp = imp;
		// DOES_ALL covers the legacy 5 (8G, 8C, 16, 32, RGB) but, by design
		// (see PlugInFilterRunner: "DOES_ALL only adds the legacy 5; never
		// implies 64-bit"), it does NOT include DOES_64. We OR it in explicitly
		// so that File > Save As > {Tiff, Zip, Raw, Text, ...} accept 64-bit
		// DoubleProcessor images and dispatch them to FileSaver.
		//
		// Without this, clicking File > Save As > Tiff... on a 64-bit image
		// produced IJ.wrongType()'s blocking "requires 8/16/32-bit grayscale
		// or RGB" dialog — never reaching FileSaver.saveAsTiff(), which
		// already supports the GRAY64_FLOAT pixel format end-to-end
		// (ImagePlus.getFileInfo case GRAY64 -> TiffEncoder bitsPerSample=64
		// + FLOATING_POINT sample format -> ImageWriter.writeDoubleImage).
		//
		// Note: it is OK to set DOES_64 unconditionally even for the writers
		// that internally reject 64-bit (e.g. JPEG, GIF, BMP, FITS, PNG, PGM);
		// those writers have their own type guards and surface a clearer,
		// format-specific error message when invoked on a 64-bit image. The
		// purpose of this flag is to bypass the generic PlugInFilterRunner
		// type gate and let the writer's own logic decide.
		return DOES_ALL + DOES_64 + NO_CHANGES;
	}

	public void run(ImageProcessor ip) {

		if(arg.equals("tiff"))
			new FileSaver(imp).saveAsTiff();
		else if(arg.equals("gif"))
			new FileSaver(imp).saveAsGif();
		else if(arg.equals("jpeg"))
			new FileSaver(imp).saveAsJpeg();
		else if(arg.equals("text"))
			new FileSaver(imp).saveAsText();
		else if(arg.equals("lut"))
			new FileSaver(imp).saveAsLut();
		else if(arg.equals("raw"))
			new FileSaver(imp).saveAsRaw();
		else if(arg.equals("zip"))
			new FileSaver(imp).saveAsZip();
		else if(arg.equals("bmp"))
			new FileSaver(imp).saveAsBmp();
		else if(arg.equals("png"))
			new FileSaver(imp).saveAsPng();
		else if(arg.equals("pgm"))
			new FileSaver(imp).saveAsPgm();
		else if(arg.equals("fits"))
			new FileSaver(imp).saveAsFits();
	}
}