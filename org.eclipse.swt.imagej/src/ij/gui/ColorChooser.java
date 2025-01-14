package ij.gui;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.AdjustmentEvent;
import java.awt.event.TextEvent;
import java.util.Vector;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Text;
import org.jfree.swt.SWTGraphics2D;

import ij.Prefs;
import ij.plugin.Colors;
import ij.util.Java2;
import ij.util.Tools;


 /** Displays a dialog that allows the user to select a color using three sliders. */
public class ColorChooser implements ModifyListener, SelectionListener {
	Vector colors, sliders;
	ColorPanel panel;
	Color initialColor;
	int red, green, blue;
	boolean useHSB;
	String title;
	Frame frame;
	double scale = Prefs.getGuiScale();

	/** Constructs a ColorChooser using the specified title and initial color. */
	public ColorChooser(String title, Color initialColor, boolean useHSB) {
		this(title, initialColor, useHSB, null);
	}
	
	public ColorChooser(String title, Color initialColor, boolean useHSB, Frame frame) {
		this.title = title;
		if (initialColor==null) initialColor = Color.black;
		this.initialColor = initialColor;
		red = initialColor.getRed();
		green = initialColor.getGreen();
		blue = initialColor.getBlue();
		this.useHSB = useHSB;
		this.frame = frame;
	}

	/** Displays a color selection dialog and returns the color selected by the user. */
	public Color getColor() {
		//GenericDialog gd = frame!=null?new GenericDialog(title, frame):new GenericDialog(title);
		GenericDialog gd = new GenericDialog(title);
		gd.addSlider("Red:", 0, 255, red);
		gd.addSlider("Green:", 0, 255, green);
		gd.addSlider("Blue:", 0, 255, blue);
		panel = new ColorPanel(initialColor, scale);
		gd.addPanel(panel);
		colors = gd.getNumericFields();
		for (int i=0; i<colors.size(); i++)
			((org.eclipse.swt.widgets.Text)colors.elementAt(i)).addModifyListener(this);
		sliders = gd.getSliders();
		for (int i=0; i<sliders.size(); i++)
			((org.eclipse.swt.widgets.Slider)sliders.elementAt(i)).addSelectionListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) return null;
		int red = (int)gd.getNextNumber();
		int green = (int)gd.getNextNumber();
		int blue = (int)gd.getNextNumber();
		return new Color(red, green, blue);
	}

	public void textValueChanged(TextEvent e) {
		
	}

	public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
		
	}

	@Override
	public void modifyText(ModifyEvent e) {
		int red = (int)Tools.parseDouble(((org.eclipse.swt.widgets.Text)colors.elementAt(0)).getText());
		int green = (int)Tools.parseDouble(((org.eclipse.swt.widgets.Text)colors.elementAt(1)).getText());
		int blue = (int)Tools.parseDouble(((org.eclipse.swt.widgets.Text)colors.elementAt(2)).getText());
		if (red<0) red=0; if (red>255) red=255;
		if (green<0) green=0; if (green>255) green=255;
		if (blue<0) blue=0; if (blue>255) blue=255;
		panel.setColor(new Color(red, green, blue));
		panel.redraw();
		
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		Object source = e.getSource();
		for (int i=0; i<sliders.size(); i++) {
			if (source==sliders.elementAt(i)) {
				Slider sb = (Slider)source;
				Text tf = (Text)colors.elementAt(i);
			}
		}
		
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		// TODO Auto-generated method stub
		
	}

}

class ColorPanel extends org.eclipse.swt.widgets.Canvas implements PaintListener {
	private int width=150, height=50;
	private Font font;
	private Color c;
	 
	ColorPanel(Color c, double scale) {
		super(Display.getDefault().getActiveShell(),SWT.TOOL);
		this.c = c;
		width = (int)(width*scale);
		height = (int)(height*scale);
		font = new Font("Monospaced", Font.PLAIN, (int)(18*scale));
	}

	public Dimension getPreferredSize() {
		return new Dimension(width, height);
	}

	void setColor(Color c) {
		this.c = c;
	}

	public Dimension getMinimumSize() {
		return new Dimension(width, height);
	}

	@Override
	public void paintControl(PaintEvent e) {
		GC gc = e.gc;
		SWTGraphics2D g = new SWTGraphics2D(gc);
		g.setColor(c);
		g.fillRect(0, 0, width, height);
		int intensity = (c.getRed()+c.getGreen()+c.getBlue())/3;
		Color c2 = intensity<128?Color.white:Color.black;
		g.setColor(c2);
		g.setFont(font);
		Java2.setAntialiasedText(g, true);
		String s = Colors.colorToString(c);
		g.drawString(s, 5, height-5);
		g.setColor(Color.black);
		g.drawRect(0, 0, width-1, height-1);
		
	}

}
