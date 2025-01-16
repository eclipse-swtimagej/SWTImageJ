package ij;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.PixelGrabber;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;

import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/** This class represents a color look-up table. */
public class LookUpTable extends Object {
	private int width, height;
	private byte[] pixels;
	private int mapSize = 0;
	private ColorModel cm;
	private byte[] rLUT, gLUT, bLUT;

	/** Constructs a LookUpTable object from an AWT Image. */
	public LookUpTable(Image img) {
		PixelGrabber pg = new PixelGrabber(img, 0, 0, 1, 1, false);
		try {
			pg.grabPixels();
			cm = pg.getColorModel();
		} catch (InterruptedException e) {
		}
		;
		getColors(cm);
	}

	/** Constructs a LookUpTable object from an SWT Image. */
	public LookUpTable(org.eclipse.swt.graphics.Image img) {
		ImageData imageData = img.getImageData();
		int newWidth = img.getBounds().width;
		pixels = new byte[imageData.width * imageData.height];
		PaletteData palette = imageData.palette;
		if (palette.isDirect) {
			cm = new DirectColorModel(imageData.depth, palette.redMask, palette.greenMask, palette.blueMask);
			imageData.getPixels(0, 0, newWidth, pixels, 0);
		}

		else {
			RGB[] rgbs = palette.getRGBs();
			byte[] red = new byte[rgbs.length];
			byte[] green = new byte[rgbs.length];
			byte[] blue = new byte[rgbs.length];
			for (int i = 0; i < rgbs.length; i++) {
				RGB rgb = rgbs[i];
				red[i] = (byte) rgb.red;
				green[i] = (byte) rgb.green;
				blue[i] = (byte) rgb.blue;
			}
			if (imageData.transparentPixel != -1) {
				cm = new IndexColorModel(imageData.depth, rgbs.length, red, green, blue, imageData.transparentPixel);
			} else {
				cm = new IndexColorModel(imageData.depth, rgbs.length, red, green, blue);
			}
		}
		getColors(cm);
	}

	/** Constructs a LookUpTable object from a ColorModel. */
	public LookUpTable(ColorModel cm) {
		this.cm = cm;
		getColors(cm);
	}

	void getColors(ColorModel cm) {
		if (cm instanceof IndexColorModel) {
			IndexColorModel m = (IndexColorModel) cm;
			mapSize = m.getMapSize();
			rLUT = new byte[mapSize];
			gLUT = new byte[mapSize];
			bLUT = new byte[mapSize];
			m.getReds(rLUT);
			m.getGreens(gLUT);
			m.getBlues(bLUT);
		}
	}

	public int getMapSize() {
		return mapSize;
	}

	public byte[] getReds() {
		return rLUT;
	}

	public byte[] getGreens() {
		return gLUT;
	}

	public byte[] getBlues() {
		return bLUT;
	}

	public ColorModel getColorModel() {
		return cm;
	}

	/**
	 * Returns <code>true</code> if this is a 256 entry grayscale LUT.
	 * 
	 * @see ij.process.ImageProcessor#isColorLut
	 */
	public boolean isGrayscale() {
		boolean isGray = true;
		if (mapSize < 256)
			return false;
		for (int i = 0; i < mapSize; i++)
			if ((rLUT[i] != gLUT[i]) || (gLUT[i] != bLUT[i]))
				isGray = false;
		return isGray;
	}

	/**
	 * Draws a color bar with the LUT, with a black rectangle around. 'x' is the
	 * coordinate of the field for the lowest LUT color. Note that 'width' is the
	 * width of the area for the LUT colors; thus no colors will be omitted with a
	 * 256-entry LUT if width=256. The left line of the rectangle is at x, the right
	 * one at x+width+1.
	 */
	public void drawColorBar(GC gc, int x, int y, int width, int height) {
		double scale = width / (double) mapSize;
		ColorProcessor cp = new ColorProcessor(width, height);
		for (int i = 0; i < mapSize; i++) {
			cp.setColor(new Color(rLUT[i] & 0xff, gLUT[i] & 0xff, bLUT[i] & 0xff));
			int xloc0 = (int) Math.round(i * scale);
			int xloc1 = (int) Math.round((i + 1) * scale);
			cp.fillRect(xloc0, 0, xloc1 - xloc0, height);
		}
		gc.drawImage(cp.createImageSwt(), x + 1, y);
		gc.setForeground(ij.swt.Color.black);
		gc.drawRectangle(x, y, width + 1, height);
	}

	public void drawUnscaledColorBar(ImageProcessor ip, int x, int y, int width, int height) {
		ImageProcessor bar = null;
		if (ip instanceof ColorProcessor)
			bar = new ColorProcessor(width, height);
		else
			bar = new ByteProcessor(width, height);
		if (mapSize == 0) { // no color table; draw a grayscale bar
			for (int i = 0; i < 256; i++) {
				bar.setColor(new Color(i, i, i));
				bar.moveTo(i, 0);
				bar.lineTo(i, height);
			}
		} else {
			for (int i = 0; i < mapSize; i++) {
				bar.setColor(new Color(rLUT[i] & 0xff, gLUT[i] & 0xff, bLUT[i] & 0xff));
				bar.moveTo(i, 0);
				bar.lineTo(i, height);
			}
		}
		ip.insert(bar, x + 1, y);
		ip.setColor(Color.black);
		ip.drawRect(x, y, width + 2, height);
	}

	public static ColorModel createGrayscaleColorModel(boolean invert) {
		byte[] rLUT = new byte[256];
		byte[] gLUT = new byte[256];
		byte[] bLUT = new byte[256];
		if (invert)
			for (int i = 0; i < 256; i++) {
				rLUT[255 - i] = (byte) i;
				gLUT[255 - i] = (byte) i;
				bLUT[255 - i] = (byte) i;
			}
		else {
			for (int i = 0; i < 256; i++) {
				rLUT[i] = (byte) i;
				gLUT[i] = (byte) i;
				bLUT[i] = (byte) i;
			}
		}
		return (new IndexColorModel(8, 256, rLUT, gLUT, bLUT));
	}

}
