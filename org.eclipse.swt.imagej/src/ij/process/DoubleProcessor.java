package ij.process;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.util.Random;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

/** This is a 64-bit double-precision floating-point image and methods that operate on that image. */
public class DoubleProcessor extends ImageProcessor {

	private double min, max, snapshotMin, snapshotMax;
	private double[] pixels;
	protected byte[] pixels8;
	private double[] snapshotPixels = null;
	private double fillColor = Double.MAX_VALUE;
	private boolean fixedScale = false;
	private double bgValue;

	/** Creates a new DoubleProcessor using the specified pixel array. */
	public DoubleProcessor(int width, int height, double[] pixels) {

		this(width, height, pixels, null);
	}

	/** Creates a new DoubleProcessor using the specified pixel array and ColorModel. */
	public DoubleProcessor(int width, int height, double[] pixels, ColorModel cm) {

		if(pixels != null && width * height != pixels.length)
			throw new IllegalArgumentException(WRONG_LENGTH);
		this.width = width;
		this.height = height;
		this.pixels = pixels;
		this.cm = cm;
		resetRoi();
	}

	/** Creates a blank DoubleProcessor using the default grayscale LUT that displays zero as black. */
	public DoubleProcessor(int width, int height) {

		this(width, height, new double[width * height], null);
	}

	/** Creates a DoubleProcessor from an int array using the default grayscale LUT. */
	public DoubleProcessor(int width, int height, int[] pixels) {

		this(width, height);
		for(int i = 0; i < pixels.length; i++)
			this.pixels[i] = pixels[i];
	}

	/** Creates a DoubleProcessor from a float array using the default grayscale LUT. */
	public DoubleProcessor(int width, int height, float[] pixels) {

		this(width, height);
		for(int i = 0; i < pixels.length; i++)
			this.pixels[i] = pixels[i];
	}

	/** Creates a DoubleProcessor from a 2D double array using the default LUT. */
	public DoubleProcessor(double[][] array) {

		width = array.length;
		height = array[0].length;
		pixels = new double[width * height];
		int i = 0;
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				pixels[i++] = array[x][y];
			}
		}
		resetRoi();
	}

	/** Calculates the minimum and maximum pixel value for the entire image. */
	public void findMinAndMax() {

		if(fixedScale)
			return;
		double min = Double.NaN;
		double max = Double.NaN;
		int len = width * height;
		int i = 0;
		for(; i < len; i++)
			if(!Double.isNaN(pixels[i]))
				break;
		if(i < len) {
			min = pixels[i];
			max = pixels[i];
		}
		for(; i < len; i++) {
			double value = pixels[i];
			if(value < min)
				min = value;
			else if(value > max)
				max = value;
		}
		this.min = min;
		this.max = max;
		minMaxSet = true;
	}

	/** Sets the min and max variables that control how real pixel values are mapped to 0-255 screen values. */
	public void setMinAndMax(double minimum, double maximum) {

		if(minimum == 0.0 && maximum == 0.0) {
			resetMinAndMax();
			return;
		}
		min = minimum;
		max = maximum;
		fixedScale = true;
		minMaxSet = true;
		resetThreshold();
	}

	/** Recalculates the min and max values used to scale pixel values to 0-255 for display. */
	public void resetMinAndMax() {

		fixedScale = false;
		findMinAndMax();
		resetThreshold();
	}

	/** Returns the smallest displayed pixel value. */
	public double getMin() {

		if(!minMaxSet)
			findMinAndMax();
		return min;
	}

	/** Returns the largest displayed pixel value. */
	public double getMax() {

		if(!minMaxSet)
			findMinAndMax();
		return max;
	}

	/** Create an 8-bit AWT image by scaling pixels in the range min-max to 0-255. */
	public Image createImage() {

		if(!minMaxSet)
			findMinAndMax();
		boolean firstTime = pixels8 == null;
		boolean thresholding = minThreshold != NO_THRESHOLD && lutUpdateMode < NO_LUT_UPDATE;
		if(firstTime || !lutAnimation)
			create8BitImage(thresholding && lutUpdateMode == RED_LUT);
		if(cm == null)
			makeDefaultColorModel();
		if(thresholding) {
			int size = width * height;
			double value;
			if(lutUpdateMode == BLACK_AND_WHITE_LUT) {
				for(int i = 0; i < size; i++) {
					value = pixels[i];
					if(value >= minThreshold && value <= maxThreshold)
						pixels8[i] = (byte)255;
					else
						pixels8[i] = (byte)0;
				}
			} else { // threshold red
				for(int i = 0; i < size; i++) {
					value = pixels[i];
					if(value >= minThreshold && value <= maxThreshold)
						pixels8[i] = (byte)255;
				}
			}
		}
		return createBufferedImage();
	}

	/** Create an 8-bit SWT image by scaling pixels in the range min-max to 0-255. */
	public org.eclipse.swt.graphics.Image createSwtImage() {

		if(!minMaxSet)
			findMinAndMax();
		boolean firstTime = pixels8 == null;
		boolean thresholding = minThreshold != NO_THRESHOLD && lutUpdateMode < NO_LUT_UPDATE;
		if(firstTime || !lutAnimation)
			create8BitImage(thresholding && lutUpdateMode == RED_LUT);
		if(cm == null)
			makeDefaultColorModel();
		if(thresholding) {
			int size = width * height;
			double value;
			if(lutUpdateMode == BLACK_AND_WHITE_LUT) {
				for(int i = 0; i < size; i++) {
					value = pixels[i];
					if(value >= minThreshold && value <= maxThreshold)
						pixels8[i] = (byte)255;
					else
						pixels8[i] = (byte)0;
				}
			} else { // threshold red
				for(int i = 0; i < size; i++) {
					value = pixels[i];
					if(value >= minThreshold && value <= maxThreshold)
						pixels8[i] = (byte)255;
				}
			}
		}
		return createImageSwt();
	}

	// creates 8-bit image by linearly scaling from double to 8-bits
	private byte[] create8BitImage(boolean thresholding) {

		int size = width * height;
		if(pixels8 == null)
			pixels8 = new byte[size];
		double value;
		int ivalue;
		double min2 = getMin();
		double max2 = getMax();
		double scale = 255.0 / (max2 - min2);
		int maxValue = thresholding ? 254 : 255;
		for(int i = 0; i < size; i++) {
			value = pixels[i] - min2;
			if(value < 0.0)
				value = 0.0;
			ivalue = (int)(value * scale + 0.5);
			if(ivalue > maxValue)
				ivalue = maxValue;
			pixels8[i] = (byte)ivalue;
		}
		return pixels8;
	}

	@Override
	byte[] create8BitImage() {

		return create8BitImage(false);
	}

	public org.eclipse.swt.graphics.Image createImageSwt() {

		if(imageSwt != null) {
			if(!imageSwt.isDisposed())
				imageSwt.dispose();
			imageSwt = null;
		}
		if(imageSwt == null || cm != cm2) {
			if(cm == null)
				cm = getDefaultColorModel();
			IndexColorModel colorModel = (IndexColorModel)cm;
			int size = colorModel.getMapSize();
			RGB[] rgbs = new RGB[size];
			for(int i = 0; i < size; i++) {
				rgbs[i] = new RGB(colorModel.getRed(i) & 0xFF, colorModel.getGreen(i) & 0xFF, colorModel.getBlue(i) & 0xFF);
			}
			PaletteData palette = new PaletteData(rgbs);
			ImageData data = new ImageData(getWidth(), getHeight(), colorModel.getPixelSize(), palette);
			data.setPixels(0, 0, width * height, pixels8, 0);
			imageSwt = new org.eclipse.swt.graphics.Image(Display.getDefault(), data);
			cm2 = cm;
		}
		lutAnimation = false;
		return imageSwt;
	}

	Image createBufferedImage() {

		if(raster == null) {
			SampleModel sm = getIndexSampleModel();
			DataBuffer db = new DataBufferByte(pixels8, width * height, 0);
			raster = Raster.createWritableRaster(sm, db, null);
		}
		if(image == null || cm != cm2) {
			if(cm == null)
				cm = getDefaultColorModel();
			image = new BufferedImage(cm, raster, false, null);
			cm2 = cm;
		}
		lutAnimation = false;
		return image;
	}

	/** Returns this image as an 8-bit BufferedImage. */
	public BufferedImage getBufferedImage() {

		return convertToByte(true).getBufferedImage();
	}

	/** Returns a new, blank DoubleProcessor with the specified width and height. */
	public ImageProcessor createProcessor(int width, int height) {

		ImageProcessor ip2 = new DoubleProcessor(width, height, new double[width * height], getColorModel());
		ip2.setMinAndMax(getMin(), getMax());
		ip2.setInterpolationMethod(interpolationMethod);
		return ip2;
	}

	public void snapshot() {

		snapshotWidth = width;
		snapshotHeight = height;
		snapshotMin = getMin();
		snapshotMax = getMax();
		if(snapshotPixels == null || snapshotPixels.length != pixels.length)
			snapshotPixels = new double[width * height];
		System.arraycopy(pixels, 0, snapshotPixels, 0, width * height);
	}

	public void reset() {

		if(snapshotPixels == null)
			return;
		min = snapshotMin;
		max = snapshotMax;
		minMaxSet = true;
		System.arraycopy(snapshotPixels, 0, pixels, 0, width * height);
	}

	public void reset(ImageProcessor mask) {

		if(mask == null || snapshotPixels == null)
			return;
		if(mask.getWidth() != roiWidth || mask.getHeight() != roiHeight)
			throw new IllegalArgumentException(maskSizeError(mask));
		byte[] mpixels = (byte[])mask.getPixels();
		for(int y = roiY, my = 0; y < (roiY + roiHeight); y++, my++) {
			int i = y * width + roiX;
			int mi = my * roiWidth;
			for(int x = roiX; x < (roiX + roiWidth); x++) {
				if(mpixels[mi++] == 0)
					pixels[i] = snapshotPixels[i];
				i++;
			}
		}
	}

	/** Swaps the pixel and snapshot (undo) arrays. */
	public void swapPixelArrays() {

		if(snapshotPixels == null)
			return;
		double pixel;
		for(int i = 0; i < pixels.length; i++) {
			pixel = pixels[i];
			pixels[i] = snapshotPixels[i];
			snapshotPixels[i] = pixel;
		}
	}

	public void setSnapshotPixels(Object pixels) {

		snapshotPixels = (double[])pixels;
		snapshotWidth = width;
		snapshotHeight = height;
	}

	public Object getSnapshotPixels() {

		return snapshotPixels;
	}

	/** Returns the pixel value truncated to int for compatibility with the int-based API. */
	public int getPixel(int x, int y) {

		if(x >= 0 && x < width && y >= 0 && y < height)
			return (int)pixels[y * width + x];
		else
			return 0;
	}

	public final int get(int x, int y) {

		return (int)pixels[y * width + x];
	}

	public final void set(int x, int y, int value) {

		pixels[y * width + x] = value;
	}

	public final int get(int index) {

		return (int)pixels[index];
	}

	public final void set(int index, int value) {

		pixels[index] = value;
	}

	public final float getf(int x, int y) {

		return (float)pixels[y * width + x];
	}

	public final void setf(int x, int y, float value) {

		pixels[y * width + x] = value;
	}

	public final float getf(int index) {

		return (float)pixels[index];
	}

	public final void setf(int index, float value) {

		pixels[index] = value;
	}

	/**
	 * Returns the value of the pixel at (x,y) as a double, without narrowing to
	 * float. Use this instead of getPixelValue() or getf() when full 64-bit
	 * precision is required. Does no bounds checking.
	 */
	public final double getd(int x, int y) {

		return pixels[y * width + x];
	}

	/**
	 * Returns the value of the pixel at the given index as a double, without
	 * narrowing to float. Does no bounds checking.
	 */
	public final double getd(int index) {

		return pixels[index];
	}

	/**
	 * Sets the value of the pixel at (x,y) to a double, without narrowing to
	 * float. Does no bounds checking.
	 */
	public final void setd(int x, int y, double value) {

		pixels[y * width + x] = value;
	}

	/**
	 * Sets the value of the pixel at the given index to a double, without
	 * narrowing to float. Does no bounds checking.
	 */
	public final void setd(int index, double value) {

		pixels[index] = value;
	}

	/** Returns the value of the pixel at (x,y) in a one element int array. */
	public int[] getPixel(int x, int y, int[] iArray) {

		if(iArray == null)
			iArray = new int[1];
		iArray[0] = (int)getPixelValue(x, y);
		return iArray;
	}

	/** Sets a pixel in the image using a one element int array. */
	public final void putPixel(int x, int y, int[] iArray) {

		putPixelValue(x, y, iArray[0]);
	}

	/** Uses the current interpolation method (BILINEAR or BICUBIC) to calculate the pixel value at real coordinates (x,y). */
	public double getInterpolatedPixel(double x, double y) {

		if(interpolationMethod == BICUBIC)
			return getBicubicInterpolatedPixel(x, y, this);
		else {
			// Edge-clamp: only nudge a coordinate inside [0, width-1) when it
			// is genuinely past the edge, NOT when it lies exactly on the
			// last column / last row. The exact-integer case is handled
			// losslessly by getInterpolatedPixel(x, y, pixels[]) below.
			if(x < 0.0)
				x = 0.0;
			else if(x > width - 1.0) // strictly >, not >=
				x = width - 1.001;
			if(y < 0.0)
				y = 0.0;
			else if(y > height - 1.0) // strictly >, not >=
				y = height - 1.001;
			return getInterpolatedPixel(x, y, pixels);
		}
	}

	final public int getPixelInterpolated(double x, double y) {

		if(interpolationMethod == BILINEAR) {
			if(x < 0.0 || y < 0.0 || x >= width - 1 || y >= height - 1)
				return 0;
			else
				return (int)getInterpolatedPixel(x, y, pixels);
		} else if(interpolationMethod == BICUBIC)
			return (int)getBicubicInterpolatedPixel(x, y, this);
		else
			return getPixel((int)(x + 0.5), (int)(y + 0.5));
	}

	/** Stores the specified int value at (x,y). */
	public final void putPixel(int x, int y, int value) {

		if(x >= 0 && x < width && y >= 0 && y < height)
			pixels[y * width + x] = value;
	}

	/** Stores the specified real value at (x,y). */
	public void putPixelValue(int x, int y, double value) {

		if(x >= 0 && x < width && y >= 0 && y < height)
			pixels[y * width + x] = value;
	}

	/** Returns the value of the pixel at (x,y) as a float for API compatibility. */
	@Override
	public float getPixelValue(int x, int y) {

		if(x >= 0 && x < width && y >= 0 && y < height)
			return (float)pixels[y * width + x];
		else
			return 0f;
	}

	/** Full-precision 64-bit accessor; preserves double values exactly. */
	public double getPixelValueDouble(int x, int y) {

		if(x >= 0 && x < width && y >= 0 && y < height)
			return pixels[y * width + x];
		else
			return 0.0;
	}

	/** Draws a pixel in the current foreground color. */
	public void drawPixel(int x, int y) {

		if(x >= clipXMin && x <= clipXMax && y >= clipYMin && y <= clipYMax)
			putPixelValue(x, y, fillColor);
	}

	/** Returns a reference to the double array containing this image's pixel data. */
	public Object getPixels() {

		return pixels;
	}

	/** Returns a copy of the pixel data. */
	public Object getPixelsCopy() {

		if(snapshotCopyMode && snapshotPixels != null) {
			snapshotCopyMode = false;
			return snapshotPixels;
		} else {
			double[] pixels2 = new double[width * height];
			System.arraycopy(pixels, 0, pixels2, 0, width * height);
			return pixels2;
		}
	}

	public void setPixels(Object pixels) {

		this.pixels = (double[])pixels;
		resetPixels(pixels);
		if(pixels == null)
			snapshotPixels = null;
		if(pixels == null)
			pixels8 = null;
	}

	/** Copies the image contained in 'ip' to (xloc, yloc) using one of the transfer modes defined in the Blitter interface. */
	public void copyBits(ImageProcessor ip, int xloc, int yloc, int mode) {

		ip = ip.convertToDoubleProcessor();
		new DoubleBlitter(this).copyBits(ip, xloc, yloc, mode);
	}

	/**
	 * Transforms the image or ROI using an 8-bit (256 entry) lookup table.
	 * Real pixel values are mapped to 0-255 via the current display range,
	 * passed through the table, then mapped back into the display range.
	 */
	public void applyTable(int[] lut) {

		if(lut == null || lut.length < 256)
			return;
		double min2 = getMin();
		double range = getMax() - min2;
		if(range == 0.0)
			return;
		for(int y = roiY; y < (roiY + roiHeight); y++) {
			int i = y * width + roiX;
			for(int x = roiX; x < (roiX + roiWidth); x++, i++) {
				int index = (int)((pixels[i] - min2) / range * 255.0 + 0.5);
				if(index < 0)
					index = 0;
				if(index > 255)
					index = 255;
				pixels[i] = min2 + (lut[index] / 255.0) * range;
			}
		}
	}

	private void process(int op, double value) {

		double c, v1, v2;
		c = value;
		double min2 = 0.0, max2 = 0.0;
		if(op == INVERT) {
			min2 = getMin();
			max2 = getMax();
		}
		for(int y = roiY; y < (roiY + roiHeight); y++) {
			int i = y * width + roiX;
			for(int x = roiX; x < (roiX + roiWidth); x++) {
				v1 = pixels[i];
				switch(op) {
					case INVERT:
						v2 = max2 - (v1 - min2);
						break;
					case FILL:
						v2 = fillColor;
						break;
					case SET:
						v2 = c;
						break;
					case ADD:
						v2 = v1 + c;
						break;
					case MULT:
						v2 = v1 * c;
						break;
					case GAMMA:
						if(v1 <= 0.0)
							v2 = 0.0;
						else
							v2 = Math.exp(c * Math.log(v1));
						break;
					case LOG:
						v2 = Math.log(v1);
						break;
					case EXP:
						v2 = Math.exp(v1);
						break;
					case SQR:
						v2 = v1 * v1;
						break;
					case SQRT:
						if(v1 <= 0.0)
							v2 = 0.0;
						else
							v2 = Math.sqrt(v1);
						break;
					case ABS:
						v2 = Math.abs(v1);
						break;
					case MINIMUM:
						if(v1 < value)
							v2 = value;
						else
							v2 = v1;
						break;
					case MAXIMUM:
						if(v1 > value)
							v2 = value;
						else
							v2 = v1;
						break;
					default:
						v2 = v1;
				}
				pixels[i++] = v2;
			}
		}
	}

	public void invert() {

		process(INVERT, 0.0);
	}

	public void add(int value) {

		process(ADD, value);
	}

	public void add(double value) {

		process(ADD, value);
	}

	public void set(double value) {

		process(SET, value);
	}

	public void multiply(double value) {

		process(MULT, value);
	}

	/** Bitwise AND of the long-bit pattern of each pixel with 'value'. */
	public void and(int value) {

		for(int y = roiY; y < (roiY + roiHeight); y++) {
			int i = y * width + roiX;
			for(int x = roiX; x < (roiX + roiWidth); x++, i++)
				pixels[i] = (double)((long)pixels[i] & value);
		}
	}

	/** Bitwise OR of the long-bit pattern of each pixel with 'value'. */
	public void or(int value) {

		for(int y = roiY; y < (roiY + roiHeight); y++) {
			int i = y * width + roiX;
			for(int x = roiX; x < (roiX + roiWidth); x++, i++)
				pixels[i] = (double)((long)pixels[i] | value);
		}
	}

	/** Bitwise XOR of the long-bit pattern of each pixel with 'value'. */
	public void xor(int value) {

		for(int y = roiY; y < (roiY + roiHeight); y++) {
			int i = y * width + roiX;
			for(int x = roiX; x < (roiX + roiWidth); x++, i++)
				pixels[i] = (double)((long)pixels[i] ^ value);
		}
	}

	public void gamma(double value) {

		process(GAMMA, value);
	}

	public void log() {

		process(LOG, 0.0);
	}

	public void exp() {

		process(EXP, 0.0);
	}

	public void sqr() {

		process(SQR, 0.0);
	}

	public void sqrt() {

		process(SQRT, 0.0);
	}

	public void abs() {

		process(ABS, 0.0);
	}

	public void min(double value) {

		process(MINIMUM, value);
	}

	public void max(double value) {

		process(MAXIMUM, value);
	}

	/** Fills the current rectangular ROI. */
	public void fill() {

		process(FILL, 0.0);
	}

	/** Fills pixels that are within roi and part of the mask. */
	public void fill(ImageProcessor mask) {

		if(mask == null) {
			fill();
			return;
		}
		int roiWidth = this.roiWidth, roiHeight = this.roiHeight;
		int roiX = this.roiX, roiY = this.roiY;
		if(mask.getWidth() != roiWidth || mask.getHeight() != roiHeight)
			return;
		byte[] mpixels = (byte[])mask.getPixels();
		for(int y = roiY, my = 0; y < (roiY + roiHeight); y++, my++) {
			int i = y * width + roiX;
			int mi = my * roiWidth;
			for(int x = roiX; x < (roiX + roiWidth); x++) {
				if(mpixels[mi++] != 0)
					pixels[i] = fillColor;
				i++;
			}
		}
	}

	/** Does 3x3 convolution. */
	public void convolve3x3(int[] kernel) {

		filter3x3(CONVOLVE, kernel);
	}

	/** Filters using a 3x3 neighborhood. */
	public void filter(int type) {

		filter3x3(type, null);
	}

	void filter3x3(int type, int[] kernel) {

		double v1, v2, v3;
		double v4, v5, v6;
		double v7, v8, v9;
		double k1 = 0.0, k2 = 0.0, k3 = 0.0;
		double k4 = 0.0, k5 = 0.0, k6 = 0.0;
		double k7 = 0.0, k8 = 0.0, k9 = 0.0;
		double scale = 0.0;
		if(type == CONVOLVE) {
			k1 = kernel[0];
			k2 = kernel[1];
			k3 = kernel[2];
			k4 = kernel[3];
			k5 = kernel[4];
			k6 = kernel[5];
			k7 = kernel[6];
			k8 = kernel[7];
			k9 = kernel[8];
			for(int i = 0; i < kernel.length; i++)
				scale += kernel[i];
			if(scale == 0.0)
				scale = 1.0;
			scale = 1.0 / scale;
		}
		double[] pixels2 = (double[])getPixelsCopy();
		int xEnd = roiX + roiWidth;
		int yEnd = roiY + roiHeight;
		for(int y = roiY; y < yEnd; y++) {
			int p = roiX + y * width;
			int p6 = p - (roiX > 0 ? 1 : 0);
			int p3 = p6 - (y > 0 ? width : 0);
			int p9 = p6 + (y < height - 1 ? width : 0);
			v2 = pixels2[p3];
			v5 = pixels2[p6];
			v8 = pixels2[p9];
			if(roiX > 0) {
				p3++;
				p6++;
				p9++;
			}
			v3 = pixels2[p3];
			v6 = pixels2[p6];
			v9 = pixels2[p9];
			switch(type) {
				case BLUR_MORE:
					for(int x = roiX; x < xEnd; x++, p++) {
						if(x < width - 1) {
							p3++;
							p6++;
							p9++;
						}
						v1 = v2;
						v2 = v3;
						v3 = pixels2[p3];
						v4 = v5;
						v5 = v6;
						v6 = pixels2[p6];
						v7 = v8;
						v8 = v9;
						v9 = pixels2[p9];
						pixels[p] = (v1 + v2 + v3 + v4 + v5 + v6 + v7 + v8 + v9) * 0.1111111111111111;
					}
					break;
				case FIND_EDGES:
					for(int x = roiX; x < xEnd; x++, p++) {
						if(x < width - 1) {
							p3++;
							p6++;
							p9++;
						}
						v1 = v2;
						v2 = v3;
						v3 = pixels2[p3];
						v4 = v5;
						v5 = v6;
						v6 = pixels2[p6];
						v7 = v8;
						v8 = v9;
						v9 = pixels2[p9];
						double sum1 = v1 + 2 * v2 + v3 - v7 - 2 * v8 - v9;
						double sum2 = v1 + 2 * v4 + v7 - v3 - 2 * v6 - v9;
						pixels[p] = Math.sqrt(sum1 * sum1 + sum2 * sum2);
					}
					break;
				case CONVOLVE:
					for(int x = roiX; x < xEnd; x++, p++) {
						if(x < width - 1) {
							p3++;
							p6++;
							p9++;
						}
						v1 = v2;
						v2 = v3;
						v3 = pixels2[p3];
						v4 = v5;
						v5 = v6;
						v6 = pixels2[p6];
						v7 = v8;
						v8 = v9;
						v9 = pixels2[p9];
						double sum = k1 * v1 + k2 * v2 + k3 * v3 + k4 * v4 + k5 * v5 + k6 * v6 + k7 * v7 + k8 * v8 + k9 * v9;
						sum *= scale;
						pixels[p] = sum;
					}
					break;
				case MIN:
					for(int x = roiX; x < xEnd; x++, p++) {
						if(x < width - 1) {
							p3++;
							p6++;
							p9++;
						}
						v1 = v2;
						v2 = v3;
						v3 = pixels2[p3];
						v4 = v5;
						v5 = v6;
						v6 = pixels2[p6];
						v7 = v8;
						v8 = v9;
						v9 = pixels2[p9];
						double min = v1;
						if(v2 < min)
							min = v2;
						if(v3 < min)
							min = v3;
						if(v4 < min)
							min = v4;
						if(v5 < min)
							min = v5;
						if(v6 < min)
							min = v6;
						if(v7 < min)
							min = v7;
						if(v8 < min)
							min = v8;
						if(v9 < min)
							min = v9;
						pixels[p] = min;
					}
					break;
				case MAX:
					for(int x = roiX; x < xEnd; x++, p++) {
						if(x < width - 1) {
							p3++;
							p6++;
							p9++;
						}
						v1 = v2;
						v2 = v3;
						v3 = pixels2[p3];
						v4 = v5;
						v5 = v6;
						v6 = pixels2[p6];
						v7 = v8;
						v8 = v9;
						v9 = pixels2[p9];
						double max = v1;
						if(v2 > max)
							max = v2;
						if(v3 > max)
							max = v3;
						if(v4 > max)
							max = v4;
						if(v5 > max)
							max = v5;
						if(v6 > max)
							max = v6;
						if(v7 > max)
							max = v7;
						if(v8 > max)
							max = v8;
						if(v9 > max)
							max = v9;
						pixels[p] = max;
					}
					break;
				case MEDIAN_FILTER:
					for(int x = roiX; x < xEnd; x++, p++) {
						if(x < width - 1) {
							p3++;
							p6++;
							p9++;
						}
						v1 = v2;
						v2 = v3;
						v3 = pixels2[p3];
						v4 = v5;
						v5 = v6;
						v6 = pixels2[p6];
						v7 = v8;
						v8 = v9;
						v9 = pixels2[p9];
						pixels[p] = median9(v1, v2, v3, v4, v5, v6, v7, v8, v9);
					}
					break;
			}
		}
	}

	/** Returns the median of 9 values (used by the 3x3 median filter). */
	private double median9(double v1, double v2, double v3, double v4, double v5, double v6, double v7, double v8, double v9) {

		double[] a = {v1, v2, v3, v4, v5, v6, v7, v8, v9};
		// simple insertion sort of 9 elements
		for(int i = 1; i < 9; i++) {
			double tmp = a[i];
			int j = i - 1;
			while(j >= 0 && a[j] > tmp) {
				a[j + 1] = a[j];
				j--;
			}
			a[j + 1] = tmp;
		}
		return a[4];
	}

	/** Rotates the image or ROI 'angle' degrees clockwise. */
	public void rotate(double angle) {

		double[] pixels2 = (double[])getPixelsCopy();
		ImageProcessor ip2 = null;
		if(interpolationMethod == BICUBIC)
			ip2 = new DoubleProcessor(getWidth(), getHeight(), pixels2, null);
		double centerX = roiX + (roiWidth - 1) / 2.0;
		double centerY = roiY + (roiHeight - 1) / 2.0;
		int xMax = roiX + this.roiWidth - 1;
		double angleRadians = -angle / (180.0 / Math.PI);
		double ca = Math.cos(angleRadians);
		double sa = Math.sin(angleRadians);
		double tmp1 = centerY * sa - centerX * ca;
		double tmp2 = -centerX * sa - centerY * ca;
		double tmp3, tmp4, xs, ys;
		int index, ixs, iys;
		if(interpolationMethod == BICUBIC) {
			ip2.setBackgroundValue(getBackgroundValue());
			for(int y = roiY; y < (roiY + roiHeight); y++) {
				index = y * width + roiX;
				tmp3 = tmp1 - y * sa + centerX;
				tmp4 = tmp2 + y * ca + centerY;
				for(int x = roiX; x <= xMax; x++) {
					xs = x * ca + tmp3;
					ys = x * sa + tmp4;
					pixels[index++] = getBicubicInterpolatedPixel(xs, ys, ip2);
				}
			}
		} else {
			double dwidth = width, dheight = height;
			double xlimit = width - 1.0, xlimit2 = width - 1.001;
			double ylimit = height - 1.0, ylimit2 = height - 1.001;
			for(int y = roiY; y < (roiY + roiHeight); y++) {
				index = y * width + roiX;
				tmp3 = tmp1 - y * sa + centerX;
				tmp4 = tmp2 + y * ca + centerY;
				for(int x = roiX; x <= xMax; x++) {
					xs = x * ca + tmp3;
					ys = x * sa + tmp4;
					if((xs >= -0.01) && (xs < dwidth) && (ys >= -0.01) && (ys < dheight)) {
						if(interpolationMethod == BILINEAR) {
							// Edge-clamp: keep an exact-integer xs/ys at the
							// last column/row instead of nudging it down by
							// 0.001 (which blends the last two pixels).
							if(xs < 0.0)
								xs = 0.0;
							else if(xs > xlimit) // strictly > xlimit, not >=
								xs = xlimit2;
							if(ys < 0.0)
								ys = 0.0;
							else if(ys > ylimit) // strictly > ylimit, not >=
								ys = ylimit2;
							pixels[index++] = getInterpolatedPixel(xs, ys, pixels2);
						} else {
							ixs = (int)(xs + 0.5);
							iys = (int)(ys + 0.5);
							if(ixs >= width)
								ixs = width - 1;
							if(iys >= height)
								iys = height - 1;
							pixels[index++] = pixels2[width * iys + ixs];
						}
					} else
						pixels[index++] = bgValue;
				}
			}
		}
	}

	public void flipVertical() {

		int index1, index2;
		double tmp;
		for(int y = 0; y < roiHeight / 2; y++) {
			index1 = (roiY + y) * width + roiX;
			index2 = (roiY + roiHeight - 1 - y) * width + roiX;
			for(int i = 0; i < roiWidth; i++) {
				tmp = pixels[index1];
				pixels[index1++] = pixels[index2];
				pixels[index2++] = tmp;
			}
		}
	}

	public void noise(double standardDeviation) {

		if(rnd == null)
			rnd = new Random();
		if(!Double.isNaN(seed))
			rnd.setSeed((int)seed);
		seed = Double.NaN;
		for(int y = roiY; y < (roiY + roiHeight); y++) {
			int i = y * width + roiX;
			for(int x = roiX; x < (roiX + roiWidth); x++) {
				double randomBrightness = rnd.nextGaussian() * standardDeviation;
				pixels[i] = pixels[i] + randomBrightness;
				i++;
			}
		}
		resetMinAndMax();
	}

	public ImageProcessor crop() {

		ImageProcessor ip2 = createProcessor(roiWidth, roiHeight);
		double[] pixels2 = (double[])ip2.getPixels();
		for(int ys = roiY; ys < roiY + roiHeight; ys++) {
			int offset1 = (ys - roiY) * roiWidth;
			int offset2 = ys * width + roiX;
			for(int xs = 0; xs < roiWidth; xs++)
				pixels2[offset1++] = pixels[offset2++];
		}
		return ip2;
	}

	/** Returns a duplicate of this image. */
	public ImageProcessor duplicate() {

		ImageProcessor ip2 = createProcessor(width, height);
		double[] pixels2 = (double[])ip2.getPixels();
		System.arraycopy(pixels, 0, pixels2, 0, width * height);
		return ip2;
	}

	/** Uses bilinear interpolation to find the pixel value at real coordinates (x,y). */
	private final double getInterpolatedPixel(double x, double y, double[] pixels) {

		int xbase = (int)x;
		int ybase = (int)y;
		double xFraction = x - xbase;
		double yFraction = y - ybase;
		// Right- and bottom-edge guards: when the requested coordinate lies
		// exactly on the last column or last row (fraction == 0) the
		// neighbours pixels[offset+1] / pixels[offset+width] would index
		// off the array. Since their weight is zero anyway, just clamp the
		// neighbour index back to the edge pixel.
		boolean atRightEdge = (xbase >= width - 1);
		boolean atBottomEdge = (ybase >= height - 1);
		if(atRightEdge) {
			xbase = width - 1;
			xFraction = 0.0;
		}
		if(atBottomEdge) {
			ybase = height - 1;
			yFraction = 0.0;
		}
		int offset = ybase * width + xbase;
		double lowerLeft = pixels[offset];
		double lowerRight = atRightEdge ? lowerLeft : pixels[offset + 1];
		double upperLeft = atBottomEdge ? lowerLeft : pixels[offset + width];
		double upperRight;
		if(atRightEdge && atBottomEdge)
			upperRight = lowerLeft;
		else if(atRightEdge)
			upperRight = upperLeft;
		else if(atBottomEdge)
			upperRight = lowerRight;
		else
			upperRight = pixels[offset + width + 1];
		double upperAverage;
		if(Double.isNaN(upperLeft) && xFraction >= 0.5)
			upperAverage = upperRight;
		else if(Double.isNaN(upperRight) && xFraction < 0.5)
			upperAverage = upperLeft;
		else
			upperAverage = upperLeft + xFraction * (upperRight - upperLeft);
		double lowerAverage;
		if(Double.isNaN(lowerLeft) && xFraction >= 0.5)
			lowerAverage = lowerRight;
		else if(Double.isNaN(lowerRight) && xFraction < 0.5)
			lowerAverage = lowerLeft;
		else
			lowerAverage = lowerLeft + xFraction * (lowerRight - lowerLeft);
		if(Double.isNaN(lowerAverage) && yFraction >= 0.5)
			return upperAverage;
		else if(Double.isNaN(upperAverage) && yFraction < 0.5)
			return lowerAverage;
		else
			return lowerAverage + yFraction * (upperAverage - lowerAverage);
	}

	/** Creates a new DoubleProcessor containing a scaled copy of this image or selection. */
	public ImageProcessor resize(int dstWidth, int dstHeight) {

		if(roiWidth == dstWidth && roiHeight == dstHeight)
			return crop();
		if((width == 1 || height == 1) && interpolationMethod != NONE)
			return resizeLinearly(dstWidth, dstHeight);
		double srcCenterX = roiX + roiWidth / 2.0;
		double srcCenterY = roiY + roiHeight / 2.0;
		double dstCenterX = dstWidth / 2.0;
		double dstCenterY = dstHeight / 2.0;
		double xScale = (double)dstWidth / roiWidth;
		double yScale = (double)dstHeight / roiHeight;
		if(interpolationMethod != NONE) {
			if(dstWidth != width)
				dstCenterX += xScale / 4.0;
			if(dstHeight != height)
				dstCenterY += yScale / 4.0;
		}
		int inc = getProgressIncrement(dstWidth, dstHeight);
		ImageProcessor ip2 = createProcessor(dstWidth, dstHeight);
		double[] pixels2 = (double[])ip2.getPixels();
		double xs, ys;
		if(interpolationMethod == BICUBIC) {
			for(int y = 0; y <= dstHeight - 1; y++) {
				if(inc > 0 && y % inc == 0)
					showProgress((double)y / dstHeight);
				ys = (y - dstCenterY) / yScale + srcCenterY;
				int index = y * dstWidth;
				for(int x = 0; x <= dstWidth - 1; x++) {
					xs = (x - dstCenterX) / xScale + srcCenterX;
					pixels2[index++] = getBicubicInterpolatedPixel(xs, ys, this);
				}
			}
		} else {
			double xlimit = width - 1.0, xlimit2 = width - 1.001;
			double ylimit = height - 1.0, ylimit2 = height - 1.001;
			int index1, index2;
			for(int y = 0; y <= dstHeight - 1; y++) {
				if(inc > 0 && y % inc == 0)
					showProgress((double)y / dstHeight);
				ys = (y - dstCenterY) / yScale + srcCenterY;
				if(interpolationMethod == BILINEAR) {
					if(ys < 0.0)
						ys = 0.0;
					else if(ys > ylimit) // strictly >, not >=
						ys = ylimit2;
				}
				index1 = width * (int)ys;
				index2 = y * dstWidth;
				for(int x = 0; x <= dstWidth - 1; x++) {
					xs = (x - dstCenterX) / xScale + srcCenterX;
					if(interpolationMethod == BILINEAR) {
						if(xs < 0.0)
							xs = 0.0;
						else if(xs > xlimit) // strictly >, not >=
							xs = xlimit2;
						pixels2[index2++] = getInterpolatedPixel(xs, ys, pixels);
					} else
						pixels2[index2++] = pixels[index1 + (int)xs];
				}
			}
		}
		if(inc > 0)
			showProgress(1.0);
		return ip2;
	}

	DoubleProcessor downsize(int dstWidth, int dstHeight, String msg) {

		DoubleProcessor ip2 = this;
		if(msg != null)
			ij.IJ.showStatus("downsizing in x" + msg);
		if(dstWidth < roiWidth) {
			ip2 = ip2.downsize1D(dstWidth, roiHeight, true);
			ip2.setRoi(0, 0, dstWidth, roiHeight);
		}
		if(msg != null)
			ij.IJ.showStatus("downsizing in y" + msg);
		if(dstHeight < roiHeight)
			ip2 = ip2.downsize1D(ip2.getRoi().width, dstHeight, false);
		if(ip2.getWidth() != dstWidth || ip2.getHeight() != dstHeight)
			ip2 = (DoubleProcessor)ip2.resize(dstWidth, dstHeight);
		return ip2;
	}

	private DoubleProcessor downsize1D(int dstWidth, int dstHeight, boolean xDirection) {

		int srcPointInc = xDirection ? 1 : width;
		int srcLineInc = xDirection ? width : 1;
		int dstPointInc = xDirection ? 1 : dstWidth;
		int dstLineInc = xDirection ? dstWidth : 1;
		int srcLine0 = xDirection ? roiY : roiX;
		int dstLines = xDirection ? dstHeight : dstWidth;
		DownsizeTable dt = xDirection ? new DownsizeTable(getWidth(), roiX, roiWidth, dstWidth, interpolationMethod) : new DownsizeTable(getHeight(), roiY, roiHeight, dstHeight, interpolationMethod);
		DoubleProcessor ip2 = (DoubleProcessor)createProcessor(dstWidth, dstHeight);
		double[] pixels = (double[])getPixels();
		double[] pixels2 = (double[])ip2.getPixels();
		for(int srcLine = srcLine0,
				dstLine = 0; dstLine < dstLines; srcLine++, dstLine++) {
			int dstLineOffset = dstLine * dstLineInc;
			int tablePointer = 0;
			for(int srcPoint = dt.srcStart,
					p = srcPoint * srcPointInc + srcLine * srcLineInc; srcPoint <= dt.srcEnd; srcPoint++, p += srcPointInc) {
				double v = pixels[p];
				for(int i = 0; i < dt.kernelSize; i++, tablePointer++)
					pixels2[dstLineOffset + dt.indices[tablePointer] * dstPointInc] += v * dt.weights[tablePointer];
			}
		}
		return ip2;
	}

	/**
	 * Bicubic interpolation. When the source processor is also a DoubleProcessor,
	 * neighbour fetches go through getd(...) so the entire kernel sum stays in
	 * double precision. Falls back to ip2.getf(...) for legacy / mixed-type
	 * sources, matching the pre-patch behaviour bit-for-bit.
	 *
	 * Implementation based on Chapter 16 of "Digital Image Processing: An
	 * Algorithmic Introduction Using Java" (Burger & Burge).
	 */
	public double getBicubicInterpolatedPixel(double x0, double y0, ImageProcessor ip2) {

		int u0 = (int)Math.floor(x0);
		int v0 = (int)Math.floor(y0);
		if(u0 <= 0 || u0 >= width - 2 || v0 <= 0 || v0 >= height - 2)
			return ip2.getBilinearInterpolatedPixel(x0, y0);
		// Fast, lossless path: avoid the float hop when ip2 is a DoubleProcessor.
		if(ip2 instanceof DoubleProcessor) {
			DoubleProcessor d2 = (DoubleProcessor)ip2;
			double q = 0.0;
			for(int j = 0; j <= 3; j++) {
				int v = v0 - 1 + j;
				double p = 0.0;
				for(int i = 0; i <= 3; i++) {
					int u = u0 - 1 + i;
					p += d2.getd(u, v) * cubic(x0 - u);
				}
				q += p * cubic(y0 - v);
			}
			return q;
		}
		// Legacy / mixed-type fallback (unchanged from prior behaviour).
		double q = 0.0;
		for(int j = 0; j <= 3; j++) {
			int v = v0 - 1 + j;
			double p = 0.0;
			for(int i = 0; i <= 3; i++) {
				int u = u0 - 1 + i;
				p += ip2.getf(u, v) * cubic(x0 - u);
			}
			q += p * cubic(y0 - v);
		}
		return q;
	}

	/** Sets the foreground fill/draw color. */
	public void setColor(Color color) {

		drawingColor = color;
		int bestIndex = getBestIndex(color);
		if(bestIndex > 0 && getMin() == 0.0 && getMax() == 0.0) {
			fillColor = bestIndex;
			setMinAndMax(0.0, 255.0);
		} else if(bestIndex == 0 && getMin() > 0.0 && (color.getRGB() & 0xffffff) == 0)
			fillColor = 0.0;
		else
			fillColor = getMin() + (getMax() - getMin()) * (bestIndex / 255.0);
	}

	/** Sets the background fill/draw color. */
	public void setBackgroundColor(Color color) {

		int bestIndex = getBestIndex(color);
		double value = getMin() + (getMax() - getMin()) * (bestIndex / 255.0);
		setBackgroundValue(value);
	}

	/** Sets the default fill/draw value. */
	public void setValue(double value) {

		fillColor = value;
	}

	/** Returns the foreground fill/draw value. */
	public double getForegroundValue() {

		return fillColor;
	}

	public void setBackgroundValue(double value) {

		bgValue = value;
	}

	public double getBackgroundValue() {

		return bgValue;
	}

	public void setLutAnimation(boolean lutAnimation) {

		this.lutAnimation = false;
	}

	public void setThreshold(double minThreshold, double maxThreshold, int lutUpdate) {

		if(minThreshold == NO_THRESHOLD) {
			resetThreshold();
			return;
		}
		if(getMax() > getMin()) {
			if(lutUpdate == OVER_UNDER_LUT) {
				double minT = ((minThreshold - getMin()) / (getMax() - getMin()) * 255.0);
				double maxT = ((maxThreshold - getMin()) / (getMax() - getMin()) * 255.0);
				super.setThreshold(minT, maxT, lutUpdate);
			} else {
				lutUpdateMode = lutUpdate;
				if(rLUT1 == null) {
					if(cm == null)
						makeDefaultColorModel();
					baseCM = cm;
					IndexColorModel m = (IndexColorModel)cm;
					rLUT1 = new byte[256];
					gLUT1 = new byte[256];
					bLUT1 = new byte[256];
					m.getReds(rLUT1);
					m.getGreens(gLUT1);
					m.getBlues(bLUT1);
					rLUT2 = new byte[256];
					gLUT2 = new byte[256];
					bLUT2 = new byte[256];
				}
				if(lutUpdateMode == RED_LUT)
					cm = getThresholdColorModel(rLUT1, gLUT1, bLUT1);
				else
					cm = getDefaultColorModel();
			}
		} else
			super.resetThreshold();
		this.minThreshold = minThreshold;
		this.maxThreshold = maxThreshold;
	}

	/** Performs a convolution operation using the specified kernel. */
	public void convolve(float[] kernel, int kernelWidth, int kernelHeight) {

		snapshot();
		new ij.plugin.filter.Convolver().convolve(this, kernel, kernelWidth, kernelHeight);
	}

	/**
	 * Performs a convolution with a kernel given as double[] coefficients,
	 * preserving full 64-bit precision in BOTH the pixel data and the kernel.
	 *
	 * <p>
	 * Differs from {@link #convolve(float[], int, int)} in that the kernel
	 * is double-typed, so coefficients with more than ~7 significant digits
	 * (e.g. precisely-tuned Gaussians, derivative-of-Gaussian filters,
	 * analytically-computed PSFs) are not silently narrowed to float32.
	 *
	 * <p>
	 * Boundary handling: mirror reflection at the four edges, matching the
	 * convention used by {@link ij.plugin.filter.Convolver#convolveFloat}.
	 *
	 * <p>
	 * ROI/mask aware: only pixels inside the current rectangular ROI are
	 * written, and if a non-rectangular mask is set, only the masked-in
	 * pixels are updated. Snapshot semantics are preserved: a snapshot()
	 * is taken so that reset()/reset(mask) work as usual.
	 *
	 * @param kernel
	 *            flattened kernel of length kernelWidth*kernelHeight,
	 *            row-major; passed as double[] to avoid a float cast
	 * @param kernelWidth
	 *            odd integer >= 1
	 * @param kernelHeight
	 *            odd integer >= 1
	 * @throws IllegalArgumentException
	 *             if dimensions are invalid
	 */
	public void convolve(double[] kernel, int kernelWidth, int kernelHeight) {

		if(kernel == null)
			throw new IllegalArgumentException("kernel is null");
		if(kernelWidth < 1 || kernelHeight < 1 || (kernelWidth & 1) == 0 || (kernelHeight & 1) == 0)
			throw new IllegalArgumentException("kernel dimensions must be odd, got " + kernelWidth + "x" + kernelHeight);
		if(kernel.length != kernelWidth * kernelHeight)
			throw new IllegalArgumentException("kernel length " + kernel.length + " does not match " + kernelWidth + "x" + kernelHeight);
		// Snapshot for reset() / reset(mask) compatibility.
		snapshot();
		final int kw = kernelWidth;
		final int kh = kernelHeight;
		final int kxRadius = kw / 2;
		final int kyRadius = kh / 2;
		// Source: the snapshot we just took. Destination: the live pixels[].
		// Reading from a separate source guarantees correctness when kernel
		// support overlaps already-written destination pixels.
		final double[] src = (double[])snapshotPixels;
		final double[] dst = pixels;
		final int w = width;
		final int h = height;
		// Honour the current rectangular ROI and (optionally) a non-rectangular mask.
		final int x0 = roiX, y0 = roiY;
		final int x1 = roiX + roiWidth;
		final int y1 = roiY + roiHeight;
		final byte[] mask = getMaskArray(); // null if no mask
		for(int y = y0; y < y1; y++) {
			int rowOut = y * w;
			int maskRow = (mask != null) ? (y - y0) * roiWidth : 0;
			for(int x = x0; x < x1; x++) {
				if(mask != null && mask[maskRow + (x - x0)] == 0)
					continue;
				double sum = 0.0;
				int kIdx = 0;
				for(int j = 0; j < kh; j++) {
					int sy = y + j - kyRadius;
					// mirror reflection at top/bottom
					if(sy < 0)
						sy = -sy - 1;
					else if(sy >= h)
						sy = 2 * h - sy - 1;
					int rowIn = sy * w;
					for(int i = 0; i < kw; i++) {
						int sx = x + i - kxRadius;
						// mirror reflection at left/right
						if(sx < 0)
							sx = -sx - 1;
						else if(sx >= w)
							sx = 2 * w - sx - 1;
						sum += src[rowIn + sx] * kernel[kIdx++];
					}
				}
				dst[rowOut + x] = sum;
			}
		}
	}

	/**
	 * Convenience overload for a 3x3 double-precision kernel. Equivalent to
	 * {@code convolve(kernel, 3, 3)}; provided for API symmetry with
	 * {@link #convolve3x3(int[])}.
	 *
	 * @param kernel
	 *            a length-9 row-major 3x3 kernel
	 */
	public void convolve3x3(double[] kernel) {

		if(kernel == null || kernel.length != 9)
			throw new IllegalArgumentException("3x3 kernel must have length 9");
		convolve(kernel, 3, 3);
	}

	/** Returns a 256 bin histogram of the current ROI or of the entire image if there is no ROI. */
	public int[] getHistogram() {

		return getStatistics().histogram;
	}

	/**
	 * Sets pixels &le; the scaled threshold to the display min and the rest
	 * to the display max. 'level' is a 0-255 value mapped to the display range.
	 */
	public void threshold(int level) {

		double min2 = getMin();
		double max2 = getMax();
		double range = max2 - min2;
		double cut = min2 + (level / 255.0) * range;
		int size = width * height;
		for(int i = 0; i < size; i++)
			pixels[i] = (pixels[i] <= cut) ? min2 : max2;
	}

	/**
	 * Auto-thresholding requires an integer histogram, which is not available
	 * for 64-bit float images. Convert to 8/16-bit first (Image &gt; Type).
	 */
	public void autoThreshold() {

		ij.IJ.error("64-bit Image", "Auto-thresholding is not supported for 64-bit images.\n" + "Please convert to 8-bit or 16-bit first (Image > Type).");
	}

	/** 3x3 median filter. */
	public void medianFilter() {

		filter(MEDIAN_FILTER);
	}

	/**
	 * Not supported for 64-bit images, consistent with FloatProcessor and
	 * ShortProcessor. Convert to 8-bit (Image &gt; Type) to use morphological
	 * erosion.
	 */
	public void erode() {

		ij.IJ.error("64-bit Image", "Erode is not supported for 64-bit images.\n" + "Please convert to 8-bit first (Image > Type).");
	}

	/**
	 * Not supported for 64-bit images, consistent with FloatProcessor and
	 * ShortProcessor. Convert to 8-bit (Image &gt; Type) to use morphological
	 * dilation.
	 */
	public void dilate() {

		ij.IJ.error("64-bit Image", "Dilate is not supported for 64-bit images.\n" + "Please convert to 8-bit first (Image > Type).");
	}

	/** Returns a FloatProcessor view/copy of this DoubleProcessor. */
	public FloatProcessor toFloat(int channelNumber, FloatProcessor fp) {

		if(fp == null || fp.getWidth() != width || fp.getHeight() != height)
			fp = new FloatProcessor(width, height, new float[width * height], cm);
		float[] fPixels = (float[])fp.getPixels();
		for(int i = 0; i < pixels.length; i++)
			fPixels[i] = (float)pixels[i];
		fp.setMinAndMax(getMin(), getMax());
		return fp;
	}

	/** Sets pixels from a FloatProcessor. */
	public void setPixels(int channelNumber, FloatProcessor fp) {

		float[] fPixels = (float[])fp.getPixels();
		if(pixels == null || pixels.length != fPixels.length)
			pixels = new double[fPixels.length];
		for(int i = 0; i < fPixels.length; i++)
			pixels[i] = fPixels[i];
		setMinAndMax(fp.getMin(), fp.getMax());
	}

	/** Returns the smallest possible positive nonzero pixel value. */
	public double minValue() {

		return Double.MIN_VALUE;
	}

	/** Returns the largest possible positive finite pixel value. */
	public double maxValue() {

		return Double.MAX_VALUE;
	}

	public int getBitDepth() {

		return 64;
	}

	/** Returns a binary mask, or null if a threshold is not set. */
	public ByteProcessor createMask() {

		if(getMinThreshold() == NO_THRESHOLD)
			return null;
		double minThreshold = getMinThreshold();
		double maxThreshold = getMaxThreshold();
		ByteProcessor mask = new ByteProcessor(width, height);
		byte[] mpixels = (byte[])mask.getPixels();
		for(int i = 0; i < pixels.length; i++) {
			if(pixels[i] >= minThreshold && pixels[i] <= maxThreshold)
				mpixels[i] = (byte)255;
		}
		return mask;
	}

	/** Returns this DoubleProcessor. */
	public DoubleProcessor convertToDoubleProcessor() {

		return this;
	}

	/**
	 * Returns a 32-bit FloatProcessor copy of this image (narrowing).
	 *
	 * <p>
	 * Overrides the inherited ImageProcessor implementation so the
	 * narrowing is explicit and the precision contract is clear:
	 *
	 * <ul>
	 * <li>Every double pixel is cast to float (loses 24-29 bits of mantissa
	 * precision relative to the source double).</li>
	 * <li>The current display range (min/max) is preserved.</li>
	 * <li>The color model is preserved.</li>
	 * </ul>
	 *
	 * <p>
	 * <b>Callers that need to keep 64-bit precision must NOT use this
	 * method.</b> Instead:
	 *
	 * <ul>
	 * <li>Prefer {@link #convertToDoubleProcessor()} (returns {@code this}).</li>
	 * <li>Or keep operating on the {@code DoubleProcessor} directly via
	 * {@link #getd(int)} / {@link #setd(int, double)}.</li>
	 * </ul>
	 *
	 * <p>
	 * The {@code FloatProcessor} return type is mandated by the
	 * {@code ImageProcessor} API and cannot be widened without an API break;
	 * callers like {@code UnsharpMask.run} and the {@code CONVERT_TO_FLOAT}
	 * machinery in {@link ij.plugin.filter.PlugInFilterRunner} hard-cast the
	 * result to {@code FloatProcessor}.
	 *
	 * <p>
	 * Historical note (June 2026): until this override was added,
	 * {@code DoubleProcessor.convertToFloat()} inherited the default
	 * {@code ImageProcessor} implementation, which silently narrowed 64-bit
	 * pixels to float32 with no warning. The {@code Image Calculator}
	 * "32-bit (float) result" path was bitten by exactly that. Even though
	 * the behaviour of this override matches the inherited behaviour
	 * bit-for-bit, it now lives next to {@link #convertToDoubleProcessor()}
	 * with a comment so the precision contract is obvious to the next reader.
	 */
	@Override
	public FloatProcessor convertToFloat() {

		int n = width * height;
		float[] fp = new float[n];
		for(int i = 0; i < n; i++)
			fp[i] = (float)pixels[i];
		FloatProcessor out = new FloatProcessor(width, height, fp, cm);
		out.setMinAndMax(getMin(), getMax());
		return out;
	}

	public void scale(double xScale, double yScale) {

		double xCenter = roiX + roiWidth / 2.0;
		double yCenter = roiY + roiHeight / 2.0;
		int xmin, xmax, ymin, ymax;
		if((xScale > 1.0) && (yScale > 1.0)) {
			// expand roi
			xmin = (int)(xCenter - (xCenter - roiX) * xScale);
			if(xmin < 0)
				xmin = 0;
			xmax = xmin + (int)(roiWidth * xScale) - 1;
			if(xmax >= width)
				xmax = width - 1;
			ymin = (int)(yCenter - (yCenter - roiY) * yScale);
			if(ymin < 0)
				ymin = 0;
			ymax = ymin + (int)(roiHeight * yScale) - 1;
			if(ymax >= height)
				ymax = height - 1;
		} else {
			xmin = roiX;
			xmax = roiX + roiWidth - 1;
			ymin = roiY;
			ymax = roiY + roiHeight - 1;
		}
		double[] pixels2 = (double[])getPixelsCopy();
		ImageProcessor ip2 = null;
		if(interpolationMethod == BICUBIC)
			ip2 = new DoubleProcessor(getWidth(), getHeight(), pixels2, null);
		boolean checkCoordinates = (xScale < 1.0) || (yScale < 1.0);
		int index1, index2, xsi, ysi;
		double ys, xs;
		if(interpolationMethod == BICUBIC) {
			for(int y = ymin; y <= ymax; y++) {
				ys = (y - yCenter) / yScale + yCenter;
				index1 = y * width + xmin;
				for(int x = xmin; x <= xmax; x++) {
					xs = (x - xCenter) / xScale + xCenter;
					pixels[index1++] = getBicubicInterpolatedPixel(xs, ys, ip2);
				}
			}
		} else {
			double xlimit = width - 1.0, xlimit2 = width - 1.001;
			double ylimit = height - 1.0, ylimit2 = height - 1.001;
			for(int y = ymin; y <= ymax; y++) {
				ys = (y - yCenter) / yScale + yCenter;
				ysi = (int)ys;
				if(ys < 0.0)
					ys = 0.0;
				else if(ys > ylimit) // strictly >, not >=
					ys = ylimit2;
				index1 = y * width + xmin;
				index2 = width * (int)ys;
				for(int x = xmin; x <= xmax; x++) {
					xs = (x - xCenter) / xScale + xCenter;
					xsi = (int)xs;
					if(checkCoordinates && ((xsi < xmin) || (xsi > xmax) || (ysi < ymin) || (ysi > ymax)))
						pixels[index1++] = getMin();
					else {
						if(interpolationMethod == BILINEAR) {
							if(xs < 0.0)
								xs = 0.0;
							else if(xs > xlimit) // strictly >, not >=
								xs = xlimit2;
							pixels[index1++] = getInterpolatedPixel(xs, ys, pixels2);
						} else
							pixels[index1++] = pixels2[index2 + xsi];
					}
				}
			}
		}
	}
}