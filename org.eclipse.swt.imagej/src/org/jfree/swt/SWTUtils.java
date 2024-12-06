/*
 * ===========================================
 * SWTGraphics2D : a bridge from Java2D to SWT
 * ===========================================
 * (C) Copyright 2006-2021, by Object Refinery Limited and Contributors.
 * Project Info: https://github.com/jfree/swtgraphics2d
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 * ------------------
 * SWTGraphics2D.java
 * ------------------
 * (C) Copyright 2006-2021, by Henry Proudhon and Contributors.
 * Original Author: Henry Proudhon (henry.proudhon AT mines-paristech.fr);
 * Contributor(s): David Gilbert (for Object Refinery Limited);
 * Cedric Chabanois (cchabanois AT no-log.org, resource pools);
 * Ronnie Duan (https://sourceforge.net/p/jfreechart/bugs/914/);
 * Kevin Xu (parts of patch https://sourceforge.net/p/jfreechart/patches/297/);
 */
package org.jfree.swt;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

import ij.ImageJ;

/**
 * Utility class gathering some useful and general method. Mainly convert forth
 * and back graphical stuff between awt and swt.
 */
public class SWTUtils {

	private final static String Az = "ABCpqr";

	/**
	 * Create a {@code FontData} object which encapsulate the essential data to
	 * create a swt font. The data is taken from the provided awt Font.
	 * <p>
	 * Generally speaking, given a font size, the returned swt font will display
	 * differently on the screen than the awt one. Because the SWT toolkit use
	 * native graphical resources whenever it is possible, this fact is platform
	 * dependent. To address this issue, it is possible to enforce the method to
	 * return a font with the same size (or at least as close as possible) as the
	 * awt one.
	 * <p>
	 * When the object is no more used, the user must explicitly call the dispose
	 * method on the returned font to free the operating system resources (the
	 * garbage collector won't do it).
	 *
	 * @param device
	 *            The swt device to draw on (display or gc device).
	 * @param font
	 *            The awt font from which to get the data.
	 * @param ensureSameSize
	 *            A boolean used to enforce the same size (in pixels)
	 *            between the awt font and the newly created swt font.
	 * @return a {@code FontData} object.
	 */
	public static FontData toSwtFontData(Device device, java.awt.Font font, boolean ensureSameSize) {

		FontData fontData = new FontData();
		fontData.setName(font.getFamily());
		// SWT and AWT share the same style constants.
		fontData.setStyle(font.getStyle());
		// convert the font size (in pt for awt) to height in pixels for swt
		int height = (int)Math.round(font.getSize() * 72.0 / device.getDPI().y);
		fontData.setHeight(height);
		// Might be changed!
		// hack to ensure the newly created swt fonts will be rendered with the
		// same height as the awt one
		/*
		 * if (height > 0) {
		 * if (ensureSameSize) {
		 * GC tmpGC = new GC(device);
		 * Font tmpFont = new Font(device, fontData);
		 * tmpGC.setFont(tmpFont);
		 * if (tmpGC.textExtent(Az).x > ImageJ.DUMMY_PANEL.getFontMetrics(font).stringWidth(Az)) {
		 * while (tmpGC.textExtent(Az).x > ImageJ.DUMMY_PANEL.getFontMetrics(font).stringWidth(Az)) {
		 * if(height<=1) {
		 * break;
		 * }
		 * tmpFont.dispose();
		 * height--;
		 * fontData.setHeight(height);
		 * tmpFont = new Font(device, fontData);
		 * tmpGC.setFont(tmpFont);
		 * }
		 * } else if (tmpGC.textExtent(Az).x < ImageJ.DUMMY_PANEL.getFontMetrics(font).stringWidth(Az)) {
		 * while (tmpGC.textExtent(Az).x < ImageJ.DUMMY_PANEL.getFontMetrics(font).stringWidth(Az)) {
		 * tmpFont.dispose();
		 * height++;
		 * fontData.setHeight(height);
		 * tmpFont = new Font(device, fontData);
		 * tmpGC.setFont(tmpFont);
		 * }
		 * }
		 * tmpFont.dispose();
		 * tmpGC.dispose();
		 * }
		 * }
		 */
		return fontData;
	}

	/**
	 * Create an awt font by converting as much information as possible from the
	 * provided swt {@code FontData}.
	 * <p>
	 * Generally speaking, given a font size, an swt font will display differently
	 * on the screen than the corresponding awt one. Because the SWT toolkit use
	 * native graphical ressources whenever it is possible, this fact is platform
	 * dependent. To address this issue, it is possible to enforce the method to
	 * return an awt font with the same height as the swt one.
	 *
	 * @param device
	 *            The swt device being drawn on (display or gc device).
	 * @param fontData
	 *            The swt font to convert.
	 * @param ensureSameSize
	 *            A boolean used to enforce the same size (in pixels)
	 *            between the swt font and the newly created awt font.
	 * @return An awt font converted from the provided swt font.
	 */
	public static java.awt.Font toAwtFont(Device device, FontData fontData, boolean ensureSameSize) {

		int height = (int)Math.round(fontData.getHeight() * device.getDPI().y / 72.0);
		// hack to ensure the newly created awt fonts will be rendered with the
		// same height as the swt one
		if(ensureSameSize) {
			GC tmpGC = new GC(device);
			Font tmpFont = new Font(device, fontData);
			tmpGC.setFont(tmpFont);
			java.awt.Font tmpAwtFont = new java.awt.Font(fontData.getName(), fontData.getStyle(), height);
			if(ImageJ.DUMMY_PANEL.getFontMetrics(tmpAwtFont).stringWidth(Az) > tmpGC.textExtent(Az).x) {
				while(ImageJ.DUMMY_PANEL.getFontMetrics(tmpAwtFont).stringWidth(Az) > tmpGC.textExtent(Az).x) {
					height--;
					tmpAwtFont = new java.awt.Font(fontData.getName(), fontData.getStyle(), height);
				}
			} else if(ImageJ.DUMMY_PANEL.getFontMetrics(tmpAwtFont).stringWidth(Az) < tmpGC.textExtent(Az).x) {
				while(ImageJ.DUMMY_PANEL.getFontMetrics(tmpAwtFont).stringWidth(Az) < tmpGC.textExtent(Az).x) {
					height++;
					tmpAwtFont = new java.awt.Font(fontData.getName(), fontData.getStyle(), height);
				}
			}
			tmpFont.dispose();
			tmpGC.dispose();
		}
		return new java.awt.Font(fontData.getName(), fontData.getStyle(), height);
	}

	/**
	 * Create an awt font by converting as much information as possible from the
	 * provided swt {@code Font}.
	 *
	 * @param device
	 *            The swt device to draw on (display or gc device).
	 * @param font
	 *            The swt font to convert.
	 * @return An awt font converted from the provided swt font.
	 */
	public static java.awt.Font toAwtFont(Device device, Font font) {

		FontData fontData = font.getFontData()[0];
		return toAwtFont(device, fontData, true);
	}

	/**
	 * Creates an awt color instance to match the rgb values of the specified swt
	 * color.
	 *
	 * @param color
	 *            The swt color to match.
	 * @return an awt color abject.
	 */
	public static java.awt.Color toAwtColor(Color color) {

		return new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue());
	}

	/**
	 * Creates a swt color instance to match the rgb values of the specified awt
	 * paint. For now, this method test if the paint is a color and then return the
	 * adequate swt color. Otherwise plain black is assumed.
	 *
	 * @param device
	 *            The swt device to draw on (display or gc device).
	 * @param paint
	 *            The awt color to match.
	 * @return a swt color object.
	 */
	public static Color toSwtColor(Device device, java.awt.Paint paint) {

		java.awt.Color color;
		if(paint instanceof java.awt.Color) {
			color = (java.awt.Color)paint;
		} else {
			try {
				throw new Exception("only color is supported at present... " + "setting paint to uniform black color");
			} catch(Exception e) {
				e.printStackTrace();
				color = new java.awt.Color(0, 0, 0);
			}
		}
		return new org.eclipse.swt.graphics.Color(device, color.getRed(), color.getGreen(), color.getBlue());
	}

	/**
	 * Creates a swt color instance to match the rgb values of the specified awt
	 * color. alpha channel is not supported. Note that the dispose method will need
	 * to be called on the returned object.
	 *
	 * @param device
	 *            The swt device to draw on (display or gc device).
	 * @param color
	 *            The awt color to match.
	 * @return a swt color object.
	 */
	public static Color toSwtColor(Device device, java.awt.Color color) {

		return new org.eclipse.swt.graphics.Color(device, color.getRed(), color.getGreen(), color.getBlue());
	}

	/**
	 * Transform an awt Rectangle2d instance into a swt one. The coordinates are
	 * rounded to integer for the swt object.
	 * 
	 * @param rect2d
	 *            The awt rectangle to map.
	 * @return an swt {@code Rectangle} object.
	 */
	public static Rectangle toSwtRectangle(Rectangle2D rect2d) {

		return new Rectangle((int)Math.round(rect2d.getMinX()), (int)Math.round(rect2d.getMinY()), (int)Math.round(rect2d.getWidth()), (int)Math.round(rect2d.getHeight()));
	}

	/**
	 * Transform a swt Rectangle instance into an awt one.
	 * 
	 * @param rect
	 *            the swt {@code Rectangle}
	 * @return a Rectangle2D.Double instance with the eappropriate location and
	 *         size.
	 */
	public static Rectangle2D toAwtRectangle(Rectangle rect) {

		Rectangle2D rect2d = new Rectangle2D.Double();
		rect2d.setRect(rect.x, rect.y, rect.width, rect.height);
		return rect2d;
	}

	/**
	 * Transform a swt Rectangle instance into an awt one.
	 * 
	 * @param rect
	 *            the swt {@code Rectangle}
	 * @return a Rectangle2D.Double instance with the eappropriate location and
	 *         size.
	 */
	public static java.awt.Rectangle toAwtRectangleSimple(Rectangle rect) {

		java.awt.Rectangle rect2d = new java.awt.Rectangle();
		rect2d.setRect(rect.x, rect.y, rect.width, rect.height);
		return rect2d;
	}

	/**
	 * Returns an AWT point with the same coordinates as the specified SWT point.
	 *
	 * @param p
	 *            the SWT point ({@code null} not permitted).
	 *
	 * @return An AWT point with the same coordinates as {@code p}.
	 *
	 * @see #toSwtPoint(java.awt.Point)
	 */
	public static Point2D toAwtPoint(Point p) {

		return new java.awt.Point(p.x, p.y);
	}

	/**
	 * Returns an SWT point with the same coordinates as the specified AWT point.
	 *
	 * @param p
	 *            the AWT point ({@code null} not permitted).
	 *
	 * @return An SWT point with the same coordinates as {@code p}.
	 *
	 * @see #toAwtPoint(Point)
	 */
	public static Point toSwtPoint(java.awt.Point p) {

		return new Point(p.x, p.y);
	}

	/**
	 * Returns an SWT point with the same coordinates as the specified AWT point
	 * (rounded to integer values).
	 *
	 * @param p
	 *            the AWT point ({@code null} not permitted).
	 *
	 * @return An SWT point with the same coordinates as {@code p}.
	 *
	 * @see #toAwtPoint(Point)
	 */
	public static Point toSwtPoint(java.awt.geom.Point2D p) {

		return new Point((int)Math.round(p.getX()), (int)Math.round(p.getY()));
	}

	/**
	 * Creates an AWT {@code MouseEvent} from a swt event. This method helps passing
	 * SWT mouse event to awt components.
	 * 
	 * @param event
	 *            The swt event.
	 * @return A AWT mouse event based on the given SWT event.
	 */
	public static MouseEvent toAwtMouseEvent(org.eclipse.swt.events.MouseEvent event) {

		int button = MouseEvent.NOBUTTON;
		switch(event.button) {
			case 1:
				button = MouseEvent.BUTTON1;
				break;
			case 2:
				button = MouseEvent.BUTTON2;
				break;
			case 3:
				button = MouseEvent.BUTTON3;
				break;
		}
		int modifiers = 0;
		if((event.stateMask & SWT.CTRL) != 0) {
			modifiers |= InputEvent.CTRL_DOWN_MASK;
		}
		if((event.stateMask & SWT.SHIFT) != 0) {
			modifiers |= InputEvent.SHIFT_DOWN_MASK;
		}
		if((event.stateMask & SWT.ALT) != 0) {
			modifiers |= InputEvent.ALT_DOWN_MASK;
		}
		MouseEvent awtMouseEvent = new MouseEvent(ImageJ.DUMMY_PANEL, event.hashCode(), event.time, modifiers, event.x, event.y, 1, false, button);
		return awtMouseEvent;
	}

	public static KeyEvent toAwtKeyEvent(org.eclipse.swt.events.KeyEvent event) {

		KeyEvent awtKeyEvent = new KeyEvent(ImageJ.DUMMY_PANEL, event.hashCode(), event.time, event.stateMask, event.keyCode, event.character);
		return awtKeyEvent;
	}

	/* A method to set the BufferedImage type! */
	public static BufferedImage convertToAWT(ImageData imageData, int typeIntRgb) {// e.g.,: type = BufferedImage.TYPE_INT_RGB

		BufferedImage bufferedImage = convertToAWT(imageData);
		/* Changed to convert and set the datatype which is sometimes needed! */
		BufferedImage buff = new BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(), typeIntRgb);
		buff.getGraphics().drawImage(bufferedImage, 0, 0, null);
		return buff;
	}

	// from:
	// http://www.java2s.com/Code/Java/SWT-JFace-Eclipse/ConvertbetweenSWTImageandAWTBufferedImage.htm
	public static BufferedImage convertToAWT(ImageData data) {

		ColorModel colorModel = null;
		PaletteData palette = data.palette;
		if(palette.isDirect) {
			colorModel = new DirectColorModel(data.depth, palette.redMask, palette.greenMask, palette.blueMask);
			BufferedImage bufferedImage = new BufferedImage(colorModel, colorModel.createCompatibleWritableRaster(data.width, data.height), false, null);
			WritableRaster raster = bufferedImage.getRaster();
			int[] pixelArray = new int[3];
			for(int y = 0; y < data.height; y++) {
				for(int x = 0; x < data.width; x++) {
					int pixel = data.getPixel(x, y);
					RGB rgb = palette.getRGB(pixel);
					pixelArray[0] = rgb.red;
					pixelArray[1] = rgb.green;
					pixelArray[2] = rgb.blue;
					raster.setPixels(x, y, 1, 1, pixelArray);
				}
			}
			return bufferedImage;
		} else {
			RGB[] rgbs = palette.getRGBs();
			byte[] red = new byte[rgbs.length];
			byte[] green = new byte[rgbs.length];
			byte[] blue = new byte[rgbs.length];
			for(int i = 0; i < rgbs.length; i++) {
				RGB rgb = rgbs[i];
				red[i] = (byte)rgb.red;
				green[i] = (byte)rgb.green;
				blue[i] = (byte)rgb.blue;
			}
			if(data.transparentPixel != -1) {
				colorModel = new IndexColorModel(data.depth, rgbs.length, red, green, blue, data.transparentPixel);
			} else {
				colorModel = new IndexColorModel(data.depth, rgbs.length, red, green, blue);
			}
			BufferedImage bufferedImage = new BufferedImage(colorModel, colorModel.createCompatibleWritableRaster(data.width, data.height), false, null);
			WritableRaster raster = bufferedImage.getRaster();
			int[] pixelArray = new int[1];
			for(int y = 0; y < data.height; y++) {
				for(int x = 0; x < data.width; x++) {
					int pixel = data.getPixel(x, y);
					pixelArray[0] = pixel;
					raster.setPixel(x, y, pixelArray);
				}
			}
			return bufferedImage;
		}
	}

	/**
	 * Converts an AWT image to SWT.
	 *
	 * @param image
	 *            the image ({@code null} not permitted).
	 *
	 * @return Image data.
	 */
	public static ImageData convertAWTImageToSWT(Image image) {

		if(image == null) {
			throw new IllegalArgumentException("Null 'image' argument.");
		}
		int w = image.getWidth(null);
		int h = image.getHeight(null);
		if(w == -1 || h == -1) {
			return null;
		}
		BufferedImage bi;
		/* Could also be a VolatileImage! */
		if(image instanceof BufferedImage) {
			ColorModel colorModel = ((BufferedImage)image).getColorModel();
			if(colorModel.hasAlpha()) {
				bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			} else {
				bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			}
		} else {
			bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		}
		Graphics g = bi.getGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return convertToSWT(bi);
	}

	/**
	 * snippet 156: convert between SWT Image and AWT BufferedImage.
	 * <p>
	 * For a list of all SWT example snippets see
	 * http://www.eclipse.org/swt/snippets/
	 * Improvement from: https://stackoverflow.com/questions/6498467/conversion-from-bufferedimage-to-swt-image
	 */
	public static ImageData convertToSWT(BufferedImage bufferedImage) {

		if(bufferedImage.getColorModel() instanceof DirectColorModel) {
			/*
			 * DirectColorModel colorModel = (DirectColorModel)bufferedImage.getColorModel();
			 * PaletteData palette = new PaletteData(
			 * colorModel.getRedMask(),
			 * colorModel.getGreenMask(),
			 * colorModel.getBlueMask());
			 * ImageData data = new ImageData(bufferedImage.getWidth(), bufferedImage.getHeight(),
			 * colorModel.getPixelSize(), palette);
			 * WritableRaster raster = bufferedImage.getRaster();
			 * int[] pixelArray = new int[3];
			 * for (int y = 0; y < data.height; y++) {
			 * for (int x = 0; x < data.width; x++) {
			 * raster.getPixel(x, y, pixelArray);
			 * int pixel = palette.getPixel(new RGB(pixelArray[0], pixelArray[1], pixelArray[2]));
			 * data.setPixel(x, y, pixel);
			 * }
			 * }
			 */
			DirectColorModel colorModel = (DirectColorModel)bufferedImage.getColorModel();
			PaletteData palette = new PaletteData(colorModel.getRedMask(), colorModel.getGreenMask(), colorModel.getBlueMask());
			ImageData data = new ImageData(bufferedImage.getWidth(), bufferedImage.getHeight(), colorModel.getPixelSize(), palette);
			for(int y = 0; y < data.height; y++) {
				for(int x = 0; x < data.width; x++) {
					int rgb = bufferedImage.getRGB(x, y);
					int pixel = palette.getPixel(new RGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
					data.setPixel(x, y, pixel);
					if(colorModel.hasAlpha()) {
						data.setAlpha(x, y, (rgb >> 24) & 0xFF);
					}
				}
			}
			return data;
		} else if(bufferedImage.getColorModel() instanceof IndexColorModel) {
			IndexColorModel colorModel = (IndexColorModel)bufferedImage.getColorModel();
			int size = colorModel.getMapSize();
			byte[] reds = new byte[size];
			byte[] greens = new byte[size];
			byte[] blues = new byte[size];
			colorModel.getReds(reds);
			colorModel.getGreens(greens);
			colorModel.getBlues(blues);
			RGB[] rgbs = new RGB[size];
			for(int i = 0; i < rgbs.length; i++) {
				rgbs[i] = new RGB(reds[i] & 0xFF, greens[i] & 0xFF, blues[i] & 0xFF);
			}
			PaletteData palette = new PaletteData(rgbs);
			ImageData data = new ImageData(bufferedImage.getWidth(), bufferedImage.getHeight(), colorModel.getPixelSize(), palette);
			data.transparentPixel = colorModel.getTransparentPixel();
			WritableRaster raster = bufferedImage.getRaster();
			int[] pixelArray = new int[1];
			for(int y = 0; y < data.height; y++) {
				for(int x = 0; x < data.width; x++) {
					raster.getPixel(x, y, pixelArray);
					data.setPixel(x, y, pixelArray[0]);
				}
			}
			return data;
		} else if(bufferedImage.getColorModel() instanceof ComponentColorModel) {
			ComponentColorModel colorModel = (ComponentColorModel)bufferedImage.getColorModel();
			// ASSUMES: 3 BYTE BGR IMAGE TYPE
			PaletteData palette = new PaletteData(0x0000FF, 0x00FF00, 0xFF0000);
			ImageData data = new ImageData(bufferedImage.getWidth(), bufferedImage.getHeight(), colorModel.getPixelSize(), palette);
			// This is valid because we are using a 3-byte Data model with no transparent pixels
			data.transparentPixel = -1;
			WritableRaster raster = bufferedImage.getRaster();
			int[] pixelArray = new int[3];
			for(int y = 0; y < data.height; y++) {
				for(int x = 0; x < data.width; x++) {
					raster.getPixel(x, y, pixelArray);
					int pixel = palette.getPixel(new RGB(pixelArray[0], pixelArray[1], pixelArray[2]));
					data.setPixel(x, y, pixel);
				}
			}
			return data;
		}
		return null;
	}

	/**
	 * Returns a default display.
	 * 
	 * @return a display
	 */
	public static Display getDisplay() {

		Display display = Display.getCurrent();
		// may be null if outside the UI thread
		if(display == null)
			display = Display.getDefault();
		return display;
	}
}
