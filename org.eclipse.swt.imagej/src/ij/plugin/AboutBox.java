package ij.plugin;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.net.MalformedURLException;

import org.eclipse.jface.resource.ImageDescriptor;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

	/** This plugin implements the Help/About ImageJ command by opening
	 * about.jpg in ij.jar, scaling it 600% and adding text to an overlay.
	*/
	public class AboutBox implements PlugIn {
		private static final int SMALL_FONT=20, LARGE_FONT=45;
		private static final Color TEXT_COLOR = new Color(255,255,80);

	public void run(String arg) {
		System.gc();
		int lines = 7;
		String[] text = new String[lines];
		text[0] = "ImageJ "+ImageJ.VERSION+ImageJ.BUILD;
		text[1] = "Wayne Rasband and contributors";
		text[2] = "National Institutes of Health, USA";
		text[3] = "http://imagej.org";
		text[4] = "Java "+System.getProperty("java.version")+(IJ.is64Bit()?" (64-bit)":" (32-bit)");
		text[5] = IJ.freeMemory();
		text[6] = "ImageJ is in the public domain";
		ImageProcessor ip = null;
		ImageJ ij = IJ.getInstance();
		//URL url = ij .getClass() .getResource("/about.jpg");
		File fi=new File(ImageJ.getImageJPath(ImageJ.SWT_MODE)+"/about.jpg");
		org.eclipse.swt.graphics.Image img = null;
		try {
			img = ImageDescriptor.createFromURL(fi.toURI().toURL()).createImage();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// org.eclipse.swt.graphics.Image img = createImage((ImageProducer)
		if (fi!=null) {
			/*Image img = null;
			try {img = ij.createImage((ImageProducer)url.getContent());}
			catch(Exception e) {}*/
			if (img!=null) {
				ImagePlus sImp = new ImagePlus("", img);
				ip = sImp.getProcessor();
			}
		}
		if (ip==null) 
			ip =  new ColorProcessor(55,45);
		ip = ip.resize(ip.getWidth()*6, ip.getHeight()*6);
		ImagePlus imp = new ImagePlus("About ImageJ", ip);
		int width = imp.getWidth();
		Overlay overlay = new Overlay();
		Font font = new Font("SansSerif", Font.PLAIN, LARGE_FONT);
		int y  = 60;
		add(text[0], width-20, y, font, TextRoi.RIGHT, overlay);
		int xcenter = 410;
		font = new Font("SansSerif", Font.PLAIN, SMALL_FONT);
		y += 45;
		add(text[1], xcenter, y, font, TextRoi.CENTER, overlay);
		y += 27;
		add(text[2], xcenter, y, font, TextRoi.CENTER, overlay);
		y += 27;
		add(text[3], xcenter, y, font, TextRoi.CENTER, overlay);
		y += 27;
		add(text[4], xcenter, y, font, TextRoi.CENTER, overlay);
		if (IJ.maxMemory()>0L) {
			y += 27;
			add(text[5], xcenter, y, font, TextRoi.CENTER, overlay);
		}
		add(text[6], width-10, ip.getHeight()-10, font, TextRoi.RIGHT, overlay);
		imp.setOverlay(overlay);
		ImageWindow.centerNextImage();
		imp.show();
	}
	
	private void add(String text, int x, int y, Font font, int justification, Overlay overlay) {
		TextRoi roi = new TextRoi(text, x, y, font);
		roi.setStrokeColor(TEXT_COLOR);
		roi.setJustification(justification);
		overlay.add(roi);
	}

}
