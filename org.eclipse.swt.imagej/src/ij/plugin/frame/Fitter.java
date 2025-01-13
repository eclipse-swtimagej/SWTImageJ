package ij.plugin.frame;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.StringTokenizer;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Display;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.io.OpenDialog;
import ij.measure.CurveFitter;
import ij.measure.Minimizer;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.util.Tools;

/**
 * ImageJ plugin that does curve fitting using the modified CurveFitter class.
 * Includes simplex settings dialog option.
 *
 * @author Kieran Holland (email: holki659@student.otago.ac.nz)
 *
 *         2013-10-01: fit not in EventQueue, setStatusAndEsc, error if
 *         nonnumeric data
 */
public class Fitter extends PlugInFrame implements PlugIn, SelectionListener, org.eclipse.swt.events.KeyListener, ClipboardOwner {

	org.eclipse.swt.widgets.Combo fit;
	org.eclipse.swt.widgets.Button doIt, open, apply;
	org.eclipse.swt.widgets.Button settings;
	String fitTypeStr = CurveFitter.fitList[0];
	StyledText textArea;
	double[] dx = {0, 1, 2, 3, 4, 5};
	double[] dy = {0, .9, 4.5, 8, 18, 24};
	double[] x, y;
	protected String text;
	protected boolean selection;
	static CurveFitter cf;
	static int fitType = -1;
	static String equation = "y = a + b*x + c*x*x";
	static final int USER_DEFINED = -1;
	org.eclipse.swt.graphics.Font font;

	public Fitter() {

		this(new double[]{0, 1, 2, 3, 4, 5}, new double[]{0, .9, 4.5, 8, 18, 24});
	}

	public Fitter(double[] dx, double[] dy) {
		super("Curve Fitter");
		
		Display.getDefault().syncExec(new Runnable() {

			public void run() {
				Fitter.this.dx=dx;
				Fitter.this.dy=dy;
				WindowManager.addWindow(Fitter.this);
				shell.addKeyListener(Fitter.this);
				shell.setLayout(new org.eclipse.swt.layout.GridLayout(1, true));
				org.eclipse.swt.widgets.Composite panel = new org.eclipse.swt.widgets.Composite(shell, SWT.NONE);
				panel.setLayout(new org.eclipse.swt.layout.GridLayout(5, true));
				panel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
				fit = new org.eclipse.swt.widgets.Combo(panel, SWT.DROP_DOWN | SWT.READ_ONLY);
				fit.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				for(int i = 0; i < CurveFitter.fitList.length; i++)
					fit.add(CurveFitter.fitList[CurveFitter.sortedTypes[i]]);
				fit.add("*User-defined*");
				fit.addSelectionListener(Fitter.this);
				fit.select(0);
				// panel.add(fit);
				doIt = new org.eclipse.swt.widgets.Button(panel, SWT.NONE);
				doIt.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				doIt.setText(" Fit ");
				doIt.addSelectionListener(Fitter.this);
				doIt.addKeyListener(Fitter.this);
				// panel.add(doIt);
				open = new org.eclipse.swt.widgets.Button(panel, SWT.NONE);
				open.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				open.setText("Open");
				open.addSelectionListener(Fitter.this);
				// panel.add(open);
				apply = new org.eclipse.swt.widgets.Button(panel, SWT.NONE);
				apply.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				apply.setText("Apply");
				apply.addSelectionListener(Fitter.this);
				// panel.add(apply);
				settings = new org.eclipse.swt.widgets.Button(panel, SWT.CHECK);
				settings.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				settings.setText("Show settings");
				settings.setSelection(false);
				// panel.add(settings);
				// add("North", panel);
				String text = "";
				for(int i = 0; i < dx.length; i++)
					text += IJ.d2s(dx[i], 2) + "  " + IJ.d2s(dy[i], 2) + "\n";
				textArea = new StyledText(shell, SWT.NONE);
				textArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				//font = new org.eclipse.swt.graphics.Font(Display.getDefault(),"SansSerif",14, SWT.NORMAL);
				textArea.setFont(getFont());
				// if (IJ.isLinux()) textArea.setBackground(Color.white);
				textArea.append(text);
				// add("Center", textArea);
				// GUI.scale(this);
				shell.layout();
				shell.pack();
				shell.setSize(600, 600);
				GUI.centerOnImageJScreen(Fitter.this.shell);
				shell.setVisible(true);
			}
		});
		IJ.register(Fitter.class);
	} 
	
	public void close() {
		font.dispose();
		super.close();
	}
	
	org.eclipse.swt.graphics.Font getFont() {
		FontData[] fD = JFaceResources.getFont(JFaceResources.TEXT_FONT).getFontData();
		fD[0].setHeight(14);
		font = new org.eclipse.swt.graphics.Font(Display.getDefault(), fD[0]);
		return font;
		
	}

	/**
	 * Fit data in the textArea, show result in log and create plot.
	 * 
	 * @param fitType
	 *            as defined in CurveFitter constants
	 * @return false on error.
	 */
	public boolean doFit(int fitType) {

		if(!getData()) {
			IJ.beep();
			return false;
		}
		cf = new CurveFitter(x, y);
		cf.setStatusAndEsc("Optimization: Iteration ", true);
		try {
			Display.getDefault().syncExec(new Runnable() {

				public void run() {

					selection = settings.getSelection();
				}
			});
			if(fitType == USER_DEFINED) {
				String eqn = getEquation();
				if(eqn == null)
					return false;
				int params = cf.doCustomFit(eqn, null, selection);
				if(params == 0) {
					IJ.beep();
					IJ.log("Bad formula; should be:\n   y = function(x, a, ...)");
					return false;
				}
			} else
				cf.doFit(fitType, selection);
			if(cf.getStatus() == Minimizer.INITIALIZATION_FAILURE) {
				IJ.beep();
				IJ.showStatus(cf.getStatusString());
				IJ.log("Curve Fitting Error:\n" + cf.getStatusString());
				return false;
			}
			if(Double.isNaN(cf.getSumResidualsSqr())) {
				IJ.beep();
				IJ.showStatus("Error: fit yields Not-a-Number");
				return false;
			}
		} catch(Exception e) {
			IJ.handleException(e);
			return false;
		}
		IJ.log(cf.getResultString());
		plot(cf);
		this.fitType = fitType;
		return true;
	}

	String getEquation() {

		GenericDialog gd = new GenericDialog("Formula");
		gd.addStringField("Formula:", equation, 38);
		gd.showDialog();
		if(gd.wasCanceled())
			return null;
		equation = gd.getNextString();
		return equation;
	}

	public static void plot(CurveFitter cf) {

		plot(cf, false);
	}

	public static void plot(CurveFitter cf, boolean eightBitCalibrationPlot) {

		Plot plot = cf.getPlot(eightBitCalibrationPlot ? 256 : 100);
		plot.show();
	}

	double sqr(double x) {

		return x * x;
	}

	boolean getData() {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				textArea.selectAll();
				text = textArea.getText();
				text = zapGremlins(text);
				textArea.setSelection(0, 0);
			}
		});
		StringTokenizer st = new StringTokenizer(text, " \t\n\r,");
		int nTokens = st.countTokens();
		if(nTokens < 4 || (nTokens % 2) != 0) {
			IJ.showStatus("Data error: min. two (x,y) pairs needed");
			return false;
		}
		int n = nTokens / 2;
		x = new double[n];
		y = new double[n];
		for(int i = 0; i < n; i++) {
			String xString = st.nextToken();
			String yString = st.nextToken();
			x[i] = Tools.parseDouble(xString);
			y[i] = Tools.parseDouble(yString);
			if(Double.isNaN(x[i]) || Double.isNaN(y[i])) {
				IJ.showStatus("Data error:  Bad number at " + i + ": " + xString + " " + yString);
				return false;
			}
		}
		return true;
	}

	/**
	 * create a duplicate of an image where the fit function is applied to the pixel
	 * values
	 */
	void applyFunction() {

		if(cf == null || fitType < 0) {
			IJ.error("No function available");
			return;
		}
		ImagePlus img = WindowManager.getCurrentImage();
		if(img == null) {
			IJ.noImage();
			return;
		}
		if(img.getTitle().matches("y\\s=.*")) { // title looks like a fit function
			IJ.error("First select the image to be transformed");
			return;
		}
		double[] p = cf.getParams();
		int width = img.getWidth();
		int height = img.getHeight();
		int size = width * height;
		float[] data = new float[size];
		ImageProcessor ip = img.getProcessor();
		float value;
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				value = ip.getPixelValue(x, y);
				data[y * width + x] = (float)cf.f(p, value);
			}
		}
		ImageProcessor ip2 = new FloatProcessor(width, height, data, ip.getColorModel());
		new ImagePlus(img.getTitle() + "-transformed", ip2).show();
	}

	void open() {

		OpenDialog od = new OpenDialog("Open Text File...", "");
		String directory = od.getDirectory();
		String name = od.getFileName();
		if(name == null)
			return;
		String path = directory + name;
		textArea.selectAll();
		textArea.setText("");
		try {
			BufferedReader r = new BufferedReader(new FileReader(directory + name));
			while(true) {
				String s = r.readLine();
				if(s == null || (s.length() > 100))
					break;
				textArea.append(s + "\n");
			}
			r.close();
		} catch(Exception e) {
			IJ.error(e.getMessage());
			return;
		}
	}

	public void itemStateChanged(SelectionEvent e) {

		fitTypeStr = fit.getItem(fit.getSelectionIndex());
	}

	@Override
	public void widgetSelected(SelectionEvent e) {

		if(e.widget instanceof org.eclipse.swt.widgets.Combo) {
			itemStateChanged(e);
		} else {
			actionPerformed(e);
		}
	}

	public void actionPerformed(SelectionEvent e) {

		if(e.getSource() instanceof org.eclipse.swt.widgets.MenuItem) {
			org.eclipse.swt.widgets.MenuItem mItem = (org.eclipse.swt.widgets.MenuItem)e.getSource();
			String cmd = mItem.getText();
			if(cmd == null)
				return;
			if(cmd.equals("Cut"))
				cut();
			else if(cmd.equals("Copy"))
				copy();
			else if(cmd.equals("Paste"))
				paste();
			return;
		}
		try {
			if(e.getSource() == doIt) {
				final int fitType = CurveFitter.getFitCode(fit.getItem(fit.getSelectionIndex()));
				Thread thread = new Thread(new Runnable() {

					final public void run() {

						doFit(fitType);
					}
				}, "CurveFitting");
				thread.setPriority(Thread.currentThread().getPriority());
				thread.start();
			} else if(e.getSource() == apply)
				applyFunction();
			else {
				open();
			}
		} catch(Exception ex) {
			IJ.log("" + ex);
		}
	}

	String zapGremlins(String text) {

		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count = 0;
		for(int i = 0; i < chars.length; i++) {
			char c = chars[i];
			if(c != '\n' && c != '\t' && (c < 32 || c > 127)) {
				count++;
				chars[i] = ' ';
			}
		}
		if(count > 0)
			return new String(chars);
		else
			return text;
	}

	@Override
	public void keyPressed(org.eclipse.swt.events.KeyEvent e) {

		if(e.keyCode == SWT.ESC)
			IJ.getInstance().keyPressed(e);
	}

	@Override
	public void keyReleased(org.eclipse.swt.events.KeyEvent arg0) {
		// TODO Auto-generated method stub

	}

	private boolean copy() {

		String s = textArea.getSelectionText();
		org.eclipse.swt.dnd.Clipboard cb = new org.eclipse.swt.dnd.Clipboard(Display.getDefault());
		TextTransfer textTransfer = TextTransfer.getInstance();
		cb.setContents(new Object[]{s}, new Transfer[]{textTransfer});
		return true;
	}

	private void cut() {

		if(copy()) {
			int start = textArea.getSelectionRange().x;
			int end = textArea.getSelection().y;
			textArea.replaceTextRange(start, end, "");
		}
	}

	private void paste() {

		String s = textArea.getSelectionText();
		org.eclipse.swt.dnd.Clipboard cb = new org.eclipse.swt.dnd.Clipboard(Display.getDefault());
		TextTransfer transfer = TextTransfer.getInstance();
		String data = (String)cb.getContents(transfer);
		if(data != null) {
			s = data;
		}
		/*
		 * Clipboard clipboard = getToolkit().getSystemClipboard();
		 * Transferable clipData = clipboard.getContents(s);
		 * try {
		 * s = (String) (clipData.getTransferData(DataFlavor.stringFlavor));
		 * } catch (Exception e) {
		 * s = e.toString();
		 * }
		 */
		/*
		 * int start = textArea.getSelectionStart();
		 * int end = textArea.getSelectionEnd();
		 * textArea.replaceRange(s, start, end);
		 */
		int start = textArea.getSelectionRange().x;
		int end = textArea.getSelection().y;
		textArea.replaceTextRange(start, end, s);
		// if (IJ.isMacOSX())
		// textArea.setCaretPosition(start + s.length());
	}

	public void lostOwnership(Clipboard clip, Transferable cont) {

	}

	@Override
	public void widgetDefaultSelected(SelectionEvent arg0) {
		// TODO Auto-generated method stub

	}

	public static CurveFitter getCf() {

		return cf;
	}
}