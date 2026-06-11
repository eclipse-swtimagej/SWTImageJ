package ij.process;

import java.awt.image.ColorModel;

/** This class converts an ImageProcessor to another data type. */
public class TypeConverter {

	private static final int BYTE = 0, SHORT = 1, FLOAT = 2, RGB = 3, DOUBLE = 4;
	private ImageProcessor ip;
	private int type;
	boolean doScaling = true;
	int width, height;

	public TypeConverter(ImageProcessor ip, boolean doScaling) {

		this.ip = ip;
		this.doScaling = doScaling;
		if (ip instanceof ByteProcessor)
			type = BYTE;
		else if (ip instanceof ShortProcessor)
			type = SHORT;
		else if (ip instanceof DoubleProcessor)
			type = DOUBLE;
		else if (ip instanceof FloatProcessor)
			type = FLOAT;
		else
			type = RGB;
		width = ip.getWidth();
		height = ip.getHeight();
	}

	/** Converts processor to a ByteProcessor. */
	public ImageProcessor convertToByte() {

		switch (type) {
			case BYTE:   return ip;
			case SHORT:  return convertShortToByte();
			case FLOAT:  return convertFloatToByte();
			case DOUBLE: return convertDoubleToByte();
			case RGB:    return convertRGBToByte();
			default:     return null;
		}
	}

	/** Converts a ShortProcessor to a ByteProcessor. */
	ByteProcessor convertShortToByte() {

		int size = width * height;
		short[] pixels16 = (short[]) ip.getPixels();
		byte[] pixels8 = new byte[size];
		if (doScaling) {
			int value, min = (int) ip.getMin(), max = (int) ip.getMax();
			double scale = 256.0 / (max - min + 1);
			for (int i = 0; i < size; i++) {
				value = (pixels16[i] & 0xffff) - min;
				if (value < 0) value = 0;
				value = (int) (value * scale + 0.5);
				if (value > 255) value = 255;
				pixels8[i] = (byte) value;
			}
			return new ByteProcessor(width, height, pixels8, ip.getCurrentColorModel());
		} else {
			int value;
			for (int i = 0; i < size; i++) {
				value = pixels16[i] & 0xffff;
				if (value > 255) value = 255;
				pixels8[i] = (byte) value;
			}
			return new ByteProcessor(width, height, pixels8, ip.getColorModel());
		}
	}

	/** Converts a FloatProcessor to a ByteProcessor. */
	ByteProcessor convertFloatToByte() {

		if (doScaling) {
			byte[] pixels8 = ip.create8BitImage();
			ByteProcessor bp = new ByteProcessor(ip.getWidth(), ip.getHeight(), pixels8);
			bp.setColorModel(ip.getColorModel());
			return bp;
		} else {
			ByteProcessor bp = new ByteProcessor(width, height);
			bp.setPixels(0, (FloatProcessor) ip);
			bp.setColorModel(ip.getColorModel());
			bp.resetMinAndMax();
			return bp;
		}
	}

	/** Converts a DoubleProcessor to a ByteProcessor. */
	ByteProcessor convertDoubleToByte() {

		if (doScaling) {
			byte[] pixels8 = ip.create8BitImage();
			ByteProcessor bp = new ByteProcessor(ip.getWidth(), ip.getHeight(), pixels8);
			bp.setColorModel(ip.getColorModel());
			return bp;
		} else {
			// Narrow to float first, then re-use the float -> byte path.
			FloatProcessor fp = ((DoubleProcessor) ip).toFloat(0, null);
			ByteProcessor bp = new ByteProcessor(width, height);
			bp.setPixels(0, fp);
			bp.setColorModel(ip.getColorModel());
			bp.resetMinAndMax();
			return bp;
		}
	}

	/**
	 * Converts a ColorProcessor to a ByteProcessor.
	 * Grayscale formula: g = r/3 + g/3 + b/3 (call ColorProcessor.setRGBWeights()
	 * for weighted conversion).
	 */
	ByteProcessor convertRGBToByte() {

		if (ip.getNChannels() == 1 && doScaling) {
			byte[] pixels8 = ip.create8BitImage();
			ByteProcessor bp = new ByteProcessor(ip.getWidth(), ip.getHeight(), pixels8);
			bp.setColorModel(ip.getColorModel());
			return bp;
		}
		int[] pixels32 = (int[]) ip.getPixels();
		double[] w = ColorProcessor.getWeightingFactors();
		if (((ColorProcessor) ip).getRGBWeights() != null)
			w = ((ColorProcessor) ip).getRGBWeights();
		double rw = w[0], gw = w[1], bw = w[2];
		byte[] pixels8 = new byte[width * height];
		int c, r, g, b;
		for (int i = 0; i < width * height; i++) {
			c = pixels32[i];
			r = (c & 0xff0000) >> 16;
			g = (c & 0xff00) >> 8;
			b = c & 0xff;
			pixels8[i] = (byte) (r * rw + g * gw + b * bw + 0.5);
		}
		return new ByteProcessor(width, height, pixels8, null);
	}

	/** Converts a ColorProcessor to a FloatProcessor. */
	FloatProcessor convertRGBToFloat() {

		int[] pixels = (int[]) ip.getPixels();
		double[] w = ColorProcessor.getWeightingFactors();
		if (((ColorProcessor) ip).getRGBWeights() != null)
			w = ((ColorProcessor) ip).getRGBWeights();
		double rw = w[0], gw = w[1], bw = w[2];
		float[] pixels32 = new float[width * height];
		int c, r, g, b;
		for (int i = 0; i < width * height; i++) {
			c = pixels[i];
			r = (c & 0xff0000) >> 16;
			g = (c & 0xff00) >> 8;
			b = c & 0xff;
			pixels32[i] = (float) (r * rw + g * gw + b * bw);
		}
		return new FloatProcessor(width, height, pixels32);
	}

	/** Converts a ColorProcessor to a DoubleProcessor. */
	DoubleProcessor convertRGBToDouble() {

		int[] pixels = (int[]) ip.getPixels();
		double[] w = ColorProcessor.getWeightingFactors();
		if (((ColorProcessor) ip).getRGBWeights() != null)
			w = ((ColorProcessor) ip).getRGBWeights();
		double rw = w[0], gw = w[1], bw = w[2];
		double[] pixels64 = new double[width * height];
		int c, r, g, b;
		for (int i = 0; i < width * height; i++) {
			c = pixels[i];
			r = (c & 0xff0000) >> 16;
			g = (c & 0xff00) >> 8;
			b = c & 0xff;
			pixels64[i] = r * rw + g * gw + b * bw;
		}
		return new DoubleProcessor(width, height, pixels64, null);
	}

	/** Converts processor to a ShortProcessor. */
	public ImageProcessor convertToShort() {

		switch (type) {
			case BYTE:   return convertByteToShort();
			case SHORT:  return ip;
			case FLOAT:  return convertFloatToShort();
			case DOUBLE: return convertDoubleToShort();
			case RGB:
				ip = convertRGBToByte();
				return convertByteToShort();
			default:     return null;
		}
	}

	/** Converts a ByteProcessor to a ShortProcessor. */
	ShortProcessor convertByteToShort() {

		byte[] pixels8 = (byte[]) ip.getPixels();
		short[] pixels16 = new short[width * height];
		for (int i = 0; i < width * height; i++)
			pixels16[i] = (short) (pixels8[i] & 0xff);
		return new ShortProcessor(width, height, pixels16, ip.getColorModel());
	}

	/** Converts a FloatProcessor to a ShortProcessor. */
	ShortProcessor convertFloatToShort() {

		float[] pixels32 = (float[]) ip.getPixels();
		short[] pixels16 = new short[width * height];
		double min = ip.getMin();
		double max = ip.getMax();
		double scale = (max - min) == 0.0 ? 1.0 : 65535.0 / (max - min);
		double value;
		for (int i = 0; i < width * height; i++) {
			value = doScaling ? (pixels32[i] - min) * scale : pixels32[i];
			if (value < 0.0) value = 0.0;
			if (value > 65535.0) value = 65535.0;
			pixels16[i] = (short) (value + 0.5);
		}
		return new ShortProcessor(width, height, pixels16, ip.getColorModel());
	}

	/** Converts a DoubleProcessor to a ShortProcessor. */
	ShortProcessor convertDoubleToShort() {

		double[] pixels64 = (double[]) ip.getPixels();
		short[] pixels16 = new short[width * height];
		double min = ip.getMin();
		double max = ip.getMax();
		double scale = (max - min) == 0.0 ? 1.0 : 65535.0 / (max - min);
		double value;
		for (int i = 0; i < width * height; i++) {
			value = doScaling ? (pixels64[i] - min) * scale : pixels64[i];
			if (value < 0.0) value = 0.0;
			if (value > 65535.0) value = 65535.0;
			pixels16[i] = (short) (value + 0.5);
		}
		return new ShortProcessor(width, height, pixels16, ip.getColorModel());
	}

	/** Converts processor to a FloatProcessor. */
	public ImageProcessor convertToFloat(float[] ctable) {

		switch (type) {
			case BYTE:   return convertByteToFloat(ctable);
			case SHORT:  return convertShortToFloat(ctable);
			case FLOAT:  return ip;
			case DOUBLE: return convertDoubleToFloat();
			case RGB:    return convertRGBToFloat();
			default:     return null;
		}
	}

	/** Converts a ByteProcessor to a FloatProcessor (applies a calibration if cTable != null). */
	FloatProcessor convertByteToFloat(float[] cTable) {

		int n = width * height;
		byte[] pixels8 = (byte[]) ip.getPixels();
		float[] pixels32 = new float[n];
		if (cTable != null && cTable.length == 256)
			for (int i = 0; i < n; i++) pixels32[i] = cTable[pixels8[i] & 255];
		else
			for (int i = 0; i < n; i++) pixels32[i] = pixels8[i] & 255;
		ColorModel cm = ip.getColorModel();
		return new FloatProcessor(width, height, pixels32, cm);
	}

	/** Converts a ShortProcessor to a FloatProcessor (applies a calibration if cTable != null). */
	FloatProcessor convertShortToFloat(float[] cTable) {

		short[] pixels16 = (short[]) ip.getPixels();
		float[] pixels32 = new float[width * height];
		if (cTable != null && cTable.length == 65536)
			for (int i = 0; i < width * height; i++)
				pixels32[i] = cTable[pixels16[i] & 0xffff];
		else
			for (int i = 0; i < width * height; i++)
				pixels32[i] = pixels16[i] & 0xffff;
		return new FloatProcessor(width, height, pixels32, ip.getColorModel());
	}

	/** Converts a DoubleProcessor to a FloatProcessor (narrowing). */
	FloatProcessor convertDoubleToFloat() {

		double[] pixels64 = (double[]) ip.getPixels();
		float[] pixels32 = new float[width * height];
		for (int i = 0; i < width * height; i++)
			pixels32[i] = (float) pixels64[i];
		FloatProcessor fp = new FloatProcessor(width, height, pixels32, ip.getColorModel());
		fp.setMinAndMax(ip.getMin(), ip.getMax());
		return fp;
	}

	/** Converts processor to a DoubleProcessor. */
	public ImageProcessor convertToDouble(float[] ctable) {

		switch (type) {
			case BYTE:   return convertByteToDouble(ctable);
			case SHORT:  return convertShortToDouble(ctable);
			case FLOAT:  return convertFloatToDouble();
			case DOUBLE: return ip;
			case RGB:    return convertRGBToDouble();
			default:     return null;
		}
	}

	/** Converts a ByteProcessor to a DoubleProcessor. */
	DoubleProcessor convertByteToDouble(float[] cTable) {

		int n = width * height;
		byte[] pixels8 = (byte[]) ip.getPixels();
		double[] pixels64 = new double[n];
		if (cTable != null && cTable.length == 256)
			for (int i = 0; i < n; i++) pixels64[i] = cTable[pixels8[i] & 255];
		else
			for (int i = 0; i < n; i++) pixels64[i] = pixels8[i] & 255;
		return new DoubleProcessor(width, height, pixels64, ip.getColorModel());
	}

	/** Converts a ShortProcessor to a DoubleProcessor. */
	DoubleProcessor convertShortToDouble(float[] cTable) {

		short[] pixels16 = (short[]) ip.getPixels();
		double[] pixels64 = new double[width * height];
		if (cTable != null && cTable.length == 65536)
			for (int i = 0; i < width * height; i++)
				pixels64[i] = cTable[pixels16[i] & 0xffff];
		else
			for (int i = 0; i < width * height; i++)
				pixels64[i] = pixels16[i] & 0xffff;
		return new DoubleProcessor(width, height, pixels64, ip.getColorModel());
	}

	/** Converts a FloatProcessor to a DoubleProcessor (widening). */
	DoubleProcessor convertFloatToDouble() {

		float[] pixels32 = (float[]) ip.getPixels();
		double[] pixels64 = new double[width * height];
		for (int i = 0; i < width * height; i++)
			pixels64[i] = pixels32[i];
		DoubleProcessor dp = new DoubleProcessor(width, height, pixels64, ip.getColorModel());
		dp.setMinAndMax(ip.getMin(), ip.getMax());
		return dp;
	}

	/** Converts processor to a ColorProcessor. */
	public ImageProcessor convertToRGB() {

		if (type == RGB)
			return ip;
		else {
			ImageProcessor ip2 = ip.convertToByte(doScaling);
			return new ColorProcessor(ip2.createSwtImage());
		}
	}
}