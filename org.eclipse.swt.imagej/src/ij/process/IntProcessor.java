package ij.process;
import java.util.*;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import java.awt.*;
import java.awt.image.*;


/** This is an extended ColorProcessor that supports signed 32-bit int images. */
public class IntProcessor extends ColorProcessor {
	private byte[] pixels8;

	/** Creates a blank IntProcessor with the specified dimensions. */
	public IntProcessor(int width, int height) {
		this(width, height, new int[width*height]);
	}

	/** Creates an IntProcessor from a pixel array. */
	public IntProcessor(int width, int height, int[] pixels) {
		super(width, height, pixels);
		makeDefaultColorModel();
	}

	/** Create an 8-bit AWT image by scaling pixels in the range min-max to 0-255. */
	@Override
	public Image createImage() {
		if (!minMaxSet)
			findMinAndMax();
		boolean firstTime = pixels8==null;
		boolean thresholding = minThreshold!=NO_THRESHOLD && lutUpdateMode<NO_LUT_UPDATE;
		//ij.IJ.log("createImage: "+firstTime+"  "+lutAnimation+"  "+thresholding);
		if (firstTime || !lutAnimation)
			create8BitImage(thresholding&&lutUpdateMode==RED_LUT);
		if (cm==null)
			makeDefaultColorModel();
		if (thresholding) {
			int t1 = (int)minThreshold;
			int t2 = (int)maxThreshold;
			int size = width*height;
			int value;
			if (lutUpdateMode==BLACK_AND_WHITE_LUT) {
				for (int i=0; i<size; i++) {
					value = (pixels[i]&0xffff);
					if (value>=t1 && value<=t2)
						pixels8[i] = (byte)255;
					else
						pixels8[i] = (byte)0;
				}
			} else { // threshold red
				for (int i=0; i<size; i++) {
					value = (pixels[i]&0xffff);
					if (value>=t1 && value<=t2)
						pixels8[i] = (byte)255;
				}
			}
		}
		return createBufferedImage();
	}
	
/** Create an 8-bit SWT image by scaling pixels in the range min-max to 0-255. */
	
	public org.eclipse.swt.graphics.Image createSwtImage(Device device) {
		if (!minMaxSet)
			findMinAndMax();
		boolean firstTime = pixels8==null;
		boolean thresholding = minThreshold!=NO_THRESHOLD && lutUpdateMode<NO_LUT_UPDATE;
		//ij.IJ.log("createImage: "+firstTime+"  "+lutAnimation+"  "+thresholding);
		if (firstTime || !lutAnimation)
			create8BitImage(thresholding&&lutUpdateMode==RED_LUT);
		if (cm==null)
			makeDefaultColorModel();
		if (thresholding) {
			int t1 = (int)minThreshold;
			int t2 = (int)maxThreshold;
			int size = width*height;
			int value;
			if (lutUpdateMode==BLACK_AND_WHITE_LUT) {
				for (int i=0; i<size; i++) {
					value = (pixels[i]&0xffff);
					if (value>=t1 && value<=t2)
						pixels8[i] = (byte)255;
					else
						pixels8[i] = (byte)0;
				}
			} else { // threshold red
				for (int i=0; i<size; i++) {
					value = (pixels[i]&0xffff);
					if (value>=t1 && value<=t2)
						pixels8[i] = (byte)255;
				}
			}
		}
		return createImageSwt(device);
	}
	
	// creates 8-bit image by linearly scaling from float to 8-bits
	private byte[] create8BitImage(boolean thresholding) {
		int size = width*height;
		if (pixels8==null)
			pixels8 = new byte[size];
		double value;
		int ivalue;
		double min2 = getMin();
		double max2 = getMax();
		double scale = 255.0/(max2-min2);
		int maxValue = thresholding?254:255;
		for (int i=0; i<size; i++) {
			value = pixels[i]-min2;
			if (value<0.0) value=0.0;
			ivalue = (int)(value*scale+0.5);
			if (ivalue>maxValue) ivalue = maxValue;
			pixels8[i] = (byte)ivalue;
		}
		return pixels8;
	}

	@Override
	byte[] create8BitImage() {
		return create8BitImage(false);
	}
	
	public org.eclipse.swt.graphics.Image createImageSwt(Device device) {
		//if (raster==null) {
			//SampleModel sm = getIndexSampleModel();
			//DataBuffer db = new DataBufferByte(pixels8, width*height, 0);
			//raster = Raster.createWritableRaster(sm, db, null);
		if(imageSwt!=null) {
			if (imageSwt.isDisposed() == false) {
				imageSwt.dispose();
			}
			imageSwt = null;
		}
		//}
		if (imageSwt==null || cm!=cm2) {
			if (cm==null) cm = getDefaultColorModel();
			IndexColorModel colorModel = (IndexColorModel)cm;
			  int size = colorModel.getMapSize();
	          byte[] reds = new byte[size];
	          byte[] greens = new byte[size];
	          byte[] blues = new byte[size];
	          colorModel.getReds(reds);
	          colorModel.getGreens(greens);
	          colorModel.getBlues(blues);
	          RGB[] rgbs = new RGB[size];
	          for (int i = 0; i < rgbs.length; i++) {
	              rgbs[i] = new RGB(reds[i] & 0xFF, greens[i] & 0xFF,
	                      blues[i] & 0xFF);
	          }
	          PaletteData palette = new PaletteData(rgbs);
	          ImageData data = new ImageData(getWidth(),
	                  getHeight(), colorModel.getPixelSize(),
	                  palette);
	          data.setPixels(0, 0, width * height, pixels, 0);
	          /*imageSwt variable in abstract superclass!*/
	          imageSwt= new org.eclipse.swt.graphics.Image(device, data);
	          cm2 = cm;
	         // data.transparentPixel = colorModel.getTransparentPixel();
	         // WritableRaster raster = bufferedImage.getRaster();
	        /* int[] pixelArray = new int[1];
	          for (int y = 0; y < data.height; y++) {
	              for (int x = 0; x < data.width; x++) {
	            	  raster.getPixel(x, y, pixelArray);
	                  data.setPixel(x, y, pixelArray[0]);
	              }
	          }
	          imageSwt= new org.eclipse.swt.graphics.Image(device, data);*/
		}
		lutAnimation = false;
		return imageSwt;

	}


	Image createBufferedImage() {
		if (raster==null) {
			SampleModel sm = getIndexSampleModel();
			DataBuffer db = new DataBufferByte(pixels8, width*height, 0);
			raster = Raster.createWritableRaster(sm, db, null);
		}
		if (image==null || cm!=cm2) {
			if (cm==null) cm = getDefaultColorModel();
			image = new BufferedImage(cm, raster, false, null);
			cm2 = cm;
		}
		lutAnimation = false;
		return image;
	}

	/** Returns this image as an 8-bit BufferedImage . */
	public BufferedImage getBufferedImage() {
		return convertToByte(true).getBufferedImage();
	}
	
	@Override
	public void setColorModel(ColorModel cm) {
		if (cm!=null && !(cm instanceof IndexColorModel))
			throw new IllegalArgumentException("IndexColorModel required");
		if (cm!=null && cm instanceof LUT)
			cm = ((LUT)cm).getColorModel();
		this.cm = cm;
		baseCM = null;
		rLUT1 = rLUT2 = null;
		inversionTested = false;
		minThreshold = NO_THRESHOLD;
	}
	
	@Override
	public float getPixelValue(int x, int y) {
		if (x>=0 && x<width && y>=0 && y<height)
			return (float)pixels[y*width+x];
		else 
			return Float.NaN;
	}

	/** Returns the number of channels (1). */
	@Override
	public int getNChannels() {
		return 1;
	}
	
	public void findMinAndMax() {
		int size = width*height;
		int value;
		int min = pixels[0];
		int max = pixels[0];
		for (int i=1; i<size; i++) {
			value = pixels[i];
			if (value<min)
				min = value;
			else if (value>max)
				max = value;
		}
		this.min = min;
		this.max = max;
		minMaxSet = true;
	}

	@Override
	public void resetMinAndMax() {
		findMinAndMax();
		resetThreshold();
	}
	
	@Override
	public void setMinAndMax(double minimum, double maximum, int channels) {
		min = (int)minimum;
		max = (int)maximum;
		minMaxSet = true;
		resetThreshold();
	}

}