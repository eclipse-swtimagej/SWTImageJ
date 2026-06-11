package ij.process;

import java.awt.Color;
import java.awt.Rectangle;

import ij.Prefs;

/** This class does bit blitting of 64-bit double-precision floating-point images. */
public class DoubleBlitter implements Blitter {

	public static double divideByZeroValue;

	private DoubleProcessor ip;
	private int width, height;
	private double[] pixels;

	static {
		divideByZeroValue = Prefs.getDouble(Prefs.DIV_BY_ZERO_VALUE, Double.POSITIVE_INFINITY);
		if (divideByZeroValue == Double.MAX_VALUE)
			divideByZeroValue = Double.POSITIVE_INFINITY;
	}

	/** Constructs a DoubleBlitter from a DoubleProcessor. */
	public DoubleBlitter(DoubleProcessor ip) {
		this.ip = ip;
		width = ip.getWidth();
		height = ip.getHeight();
		pixels = (double[]) ip.getPixels();
	}

	public void setTransparentColor(Color c) {
	}

	/** Copies the source image in 'ip' to (x,y) using the specified mode. */
	public void copyBits(ImageProcessor ip, int xloc, int yloc, int mode) {
		Rectangle r1, r2;
		int srcIndex, dstIndex;
		double[] srcPixels;

		DoubleProcessor src;
		if (ip instanceof DoubleProcessor) {
			src = (DoubleProcessor) ip;
		} else {
			// Widen any other processor type into a DoubleProcessor via the
			// new ImageProcessor#convertToDoubleProcessor() helper.
			src = ip.convertToDoubleProcessor();
		}
		int srcWidth = src.getWidth();
		int srcHeight = src.getHeight();
		r1 = new Rectangle(srcWidth, srcHeight);
		r1.setLocation(xloc, yloc);
		r2 = new Rectangle(width, height);
		if (!r1.intersects(r2))
			return;
		srcPixels = (double[]) src.getPixels();
		r1 = r1.intersection(r2);
		boolean useDBZValue = !Double.isInfinite(divideByZeroValue);
		double srcV, dst;
		for (int y = r1.y; y < (r1.y + r1.height); y++) {
			srcIndex = (y - yloc) * srcWidth + (r1.x - xloc);
			dstIndex = y * width + r1.x;
			switch (mode) {
				case COPY: case COPY_INVERTED: case COPY_TRANSPARENT:
					for (int i = r1.width; --i >= 0;)
						pixels[dstIndex++] = srcPixels[srcIndex++];
					break;
				case COPY_ZERO_TRANSPARENT:
					for (int i = r1.width; --i >= 0;) {
						srcV = srcPixels[srcIndex++];
						dst = (srcV == 0.0) ? pixels[dstIndex] : srcV;
						pixels[dstIndex++] = dst;
					}
					break;
				case ADD:
					for (int i = r1.width; --i >= 0; srcIndex++, dstIndex++)
						pixels[dstIndex] = srcPixels[srcIndex] + pixels[dstIndex];
					break;
				case AVERAGE:
					for (int i = r1.width; --i >= 0;) {
						dst = (srcPixels[srcIndex++] + pixels[dstIndex]) / 2.0;
						pixels[dstIndex++] = dst;
					}
					break;
				case DIFFERENCE:
					for (int i = r1.width; --i >= 0; srcIndex++, dstIndex++) {
						dst = pixels[dstIndex] - srcPixels[srcIndex];
						pixels[dstIndex] = dst < 0 ? -dst : dst;
					}
					break;
				case SUBTRACT:
					for (int i = r1.width; --i >= 0; srcIndex++, dstIndex++)
						pixels[dstIndex] = pixels[dstIndex] - srcPixels[srcIndex];
					break;
				case MULTIPLY:
					for (int i = r1.width; --i >= 0; srcIndex++, dstIndex++)
						pixels[dstIndex] = srcPixels[srcIndex] * pixels[dstIndex];
					break;
				case DIVIDE:
					for (int i = r1.width; --i >= 0; srcIndex++, dstIndex++) {
						srcV = srcPixels[srcIndex];
						if (useDBZValue && srcV == 0.0)
							pixels[dstIndex] = divideByZeroValue;
						else
							pixels[dstIndex] = pixels[dstIndex] / srcV;
					}
					break;
				case AND:
					for (int i = r1.width; --i >= 0;) {
						dst = (long) srcPixels[srcIndex++] & (long) pixels[dstIndex];
						pixels[dstIndex++] = dst;
					}
					break;
				case OR:
					for (int i = r1.width; --i >= 0;) {
						dst = (long) srcPixels[srcIndex++] | (long) pixels[dstIndex];
						pixels[dstIndex++] = dst;
					}
					break;
				case XOR:
					for (int i = r1.width; --i >= 0;) {
						dst = (long) srcPixels[srcIndex++] ^ (long) pixels[dstIndex];
						pixels[dstIndex++] = dst;
					}
					break;
				case MIN:
					for (int i = r1.width; --i >= 0;) {
						srcV = srcPixels[srcIndex++];
						dst = pixels[dstIndex];
						if (srcV < dst) dst = srcV;
						pixels[dstIndex++] = dst;
					}
					break;
				case MAX:
					for (int i = r1.width; --i >= 0;) {
						srcV = srcPixels[srcIndex++];
						dst = pixels[dstIndex];
						if (srcV > dst) dst = srcV;
						pixels[dstIndex++] = dst;
					}
					break;
			}
		}
	}
}