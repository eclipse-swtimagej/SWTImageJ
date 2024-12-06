package ij.plugin.frame;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.util.Tools;
import ij.plugin.frame.Recorder;
import ij.plugin.filter.*;
import ij.plugin.ChannelSplitter;
import ij.plugin.Thresholder;

/**
 * Adjusts the lower and upper threshold levels of the active image. This class
 * is multi-threaded to provide a more responsive user interface.
 */
public class ThresholdAdjuster extends PlugInDialog
		implements PlugIn, Measurements, Runnable, SelectionListener, org.eclipse.swt.events.FocusListener,
		org.eclipse.swt.events.KeyListener, org.eclipse.swt.events.MouseWheelListener, ImageListener {

	public static final String LOC_KEY = "threshold.loc";
	public static final String MODE_KEY = "threshold.mode";
	public static final String DARK_BACKGROUND = "threshold.dark";
	public static final String NO_RESET = "threshold.no-reset";
	public static final String RAW_VALUES = "threshold.raw";
	public static final String SIXTEEN_BIT = "threshold.16-bit";
	static final int RED = 0, BLACK_AND_WHITE = 1, OVER_UNDER = 2;
	static final String[] modes = { "Red", "B&W", "Over/Under" };
	static final double defaultMinThreshold = 0;// 85;
	static final double defaultMaxThreshold = 255;// 170;
	static final int DEFAULT = 0;
	static boolean fill1 = true;
	static boolean fill2 = true;
	static boolean useBW = true;
	static boolean backgroundToNaN = true;
	static ThresholdAdjuster instance;
	static int mode = RED;
	static String[] methodNames = AutoThresholder.getMethods();
	static String method = methodNames[DEFAULT];
	static AutoThresholder thresholder = new AutoThresholder();
	ThresholdPlot plot;
	Thread thread; // background thread calculating and applying the threshold

	int minValue = -1; // min slider, 0-255
	int maxValue = -1;
	int sliderRange = 256;
	boolean doAutoAdjust,doReset,doApplyLut,doStateChange,doSet; //actions required from user interface

	org.eclipse.swt.widgets.Composite panel;
	org.eclipse.swt.widgets.Button autoB, resetB, applyB, setB;
	int previousImageID;
	int previousImageType;
	int previousRoiHashCode;
	double previousMin, previousMax;
	int previousSlice;
	boolean imageWasUpdated;
	ImageJ ij;
	double minThreshold, maxThreshold; // 0-255
	org.eclipse.swt.widgets.Slider minSlider, maxSlider;
	org.eclipse.swt.widgets.Text minLabel, maxLabel; // for current threshold
	org.eclipse.swt.widgets.Label percentiles;
	boolean done;
	int lutColor;
	org.eclipse.swt.widgets.Combo methodChoice, modeChoice;
	org.eclipse.swt.widgets.Button darkBackgroundCheckbox, stackCheckbox, noResetCheckbox, rawValues, sixteenBitCheckbox;
	boolean firstActivation = true;
	boolean setButtonPressed;
	boolean noReset = true;
	boolean sixteenBit = false;
	boolean enterPressed;
	boolean windowActivated;
	protected boolean selection;

	public ThresholdAdjuster() {
		super("Threshold");
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				ImagePlus cimp = WindowManager.getCurrentImage();
				if (cimp != null && cimp.getBitDepth() == 24) {
					IJ.error("Threshold Adjuster",
							"Image>Adjust>Threshold only works with grayscale images.\n \n" + "You can:\n"
									+ "   Convert to grayscale: Image>Type>8-bit\n"
									+ "   Convert to RGB stack: Image>Type>RGB Stack\n"
									+ "   Convert to HSB stack: Image>Type>HSB Stack\n"
									+ "   Convert to 3 grayscale images: Image>Color>Split Channels\n"
									+ "   Do color thresholding: Image>Adjust>Color Threshold\n");
					return;
				}
				if (instance != null) {
					instance.firstActivation = true;
					// instance.shell.forceActive();
					instance.setup(cimp, true);
					instance.updateScrollBars();
					return;
				}

				WindowManager.addWindow(ThresholdAdjuster.this);
				instance = ThresholdAdjuster.this;
				mode = (int) Prefs.get(MODE_KEY, RED);
				if (mode < RED || mode > OVER_UNDER)
					mode = RED;
				setLutColor(mode);
				IJ.register(PasteController.class);

				ij = IJ.getInstance();
				// Font font = IJ.font12;
				org.eclipse.swt.graphics.Font font = IJ.font12Swt;
				/*
				 * GridBagLayout gridbag = new GridBagLayout(); GridBagConstraints c = new
				 * GridBagConstraints(); setLayout(gridbag);
				 */

				shell.setLayout(new org.eclipse.swt.layout.GridLayout(1, true));

				// plot
				/*
				 * int y = 0; c.gridx = 0; c.gridy = y++; c.gridwidth = 2; c.fill =
				 * GridBagConstraints.BOTH; c.anchor = GridBagConstraints.CENTER; c.insets = new
				 * Insets(10, 10, 0, 10); //top left bottom right
				 */
				plot = new ThresholdPlot(shell);
				plot.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				// add(plot, c);
				/* To do after port to SWT */
				// plot.addKeyListener(ij);

				// percentiles
				/*
				 * c.gridx = 0; c.gridy = y++; c.insets = new Insets(1, 10, 0, 10);
				 */

				percentiles = new org.eclipse.swt.widgets.Label(shell, SWT.NONE);
				percentiles.setText("");
				percentiles.setFont(font);
				// add(percentiles, c);

				// minThreshold slider
				// minSlider = new Scrollbar(Scrollbar.HORIZONTAL, sliderRange/3, 1, 0,
				// sliderRange);
				// GUI.fixScrollbar(minSlider);
				/*
				 * c.gridx = 0; c.gridy = y++; c.gridwidth = 1; c.weightx =
				 * IJ.isMacintosh()?90:100; c.fill = GridBagConstraints.HORIZONTAL; c.insets =
				 * new Insets(1, 10, 0, 0);
				 */
				// add(minSlider, c);
				// minSlider.addAdjustmentListener(this);
				// minSlider.addMouseWheelListener(this);
//		minSlider.addKeyListener(ij);
				// minSlider.setUnitIncrement(1);
				// minSlider.setFocusable(false);

				minSlider = new org.eclipse.swt.widgets.Slider(shell, SWT.HORIZONTAL);
				minSlider.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				minSlider.setValues(sliderRange / 3, 0, sliderRange, 1, 1, 1);
				minSlider.addSelectionListener(ThresholdAdjuster.this);
				minSlider.addMouseWheelListener(ThresholdAdjuster.this);
				minSlider.addKeyListener(ij);
				// addLabel("Minimum", null);

				// minThreshold slider label
				/*
				 * c.gridx = 1; c.gridwidth = 1; c.weightx = IJ.isMacintosh()?10:0; c.insets =
				 * new Insets(5, 0, 0, 10);
				 */
				String text = "000000";
				int columns = 4;
				minLabel = new org.eclipse.swt.widgets.Text(shell, SWT.SINGLE);
				minLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true, 1, 1));
				minLabel.setText(text);
				// minLabel.setFont(font);
				// add(minLabel, c);
				minLabel.addFocusListener(ThresholdAdjuster.this);
				minLabel.addMouseWheelListener(ThresholdAdjuster.this);
				minLabel.addKeyListener(ThresholdAdjuster.this);

				// maxThreshold slider
				// maxSlider = new Scrollbar(Scrollbar.HORIZONTAL, sliderRange*2/3, 1, 0,
				// sliderRange);
				/*
				 * GUI.fixScrollbar(maxSlider); c.gridx = 0; c.gridy = y++; c.gridwidth = 1;
				 * c.weightx = 100; c.insets = new Insets(2, 10, 0, 0); add(maxSlider, c);
				 */
				// maxSlider.addAdjustmentListener(this);
				// maxSlider.addMouseWheelListener(this);
//		maxSlider.addKeyListener(ij);
				// maxSlider.setUnitIncrement(1);
				// maxSlider.setFocusable(false);

				maxSlider = new org.eclipse.swt.widgets.Slider(shell, SWT.HORIZONTAL);
				maxSlider.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				maxSlider.setValues(sliderRange * 2 / 3, 0, sliderRange, 1, 1, 1);
				maxSlider.addSelectionListener(ThresholdAdjuster.this);
				maxSlider.addMouseWheelListener(ThresholdAdjuster.this);
				maxSlider.addKeyListener(ij);

				// maxThreshold slider label
				/*
				 * c.gridx = 1; c.gridwidth = 1; c.weightx = 0; c.insets = new Insets(2, 0, 0,
				 * 10);
				 */
				/*
				 * maxLabel = new TextField(text,columns); maxLabel.setFont(font); add(maxLabel,
				 * c); maxLabel.addFocusListener(this); maxLabel.addMouseWheelListener(this);
				 * maxLabel.addKeyListener(this);
				 */

				maxLabel = new org.eclipse.swt.widgets.Text(shell, SWT.SINGLE);
				maxLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true, 1, 1));
				maxLabel.setText(text);
				// maxLabel.setFont(font);
				// add(minLabel, c);
				maxLabel.addFocusListener(ThresholdAdjuster.this);
				maxLabel.addMouseWheelListener(ThresholdAdjuster.this);
				maxLabel.addKeyListener(ThresholdAdjuster.this);

				// choices
				// panel = new Panel();
				org.eclipse.swt.widgets.Composite panel2 = new org.eclipse.swt.widgets.Composite(shell, SWT.NONE);
				panel2.setLayout(new org.eclipse.swt.layout.GridLayout(2, true));
				panel2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				// methodChoice = new Choice();
				int selIndex = 0;
				methodChoice = new org.eclipse.swt.widgets.Combo(panel2, SWT.DROP_DOWN | SWT.READ_ONLY);
				methodChoice.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				for (int i = 0; i < methodNames.length; i++) {
					methodChoice.add(methodNames[i]);
					if (methodNames[i].equals(method)) {
						selIndex = i;
					}
				}

				methodChoice.select(selIndex);
				methodChoice.addSelectionListener(ThresholdAdjuster.this);
				// methodChoice.addKeyListener(ij);
				// panel.add(methodChoice);
				// modeChoice = new Choice();
				modeChoice = new org.eclipse.swt.widgets.Combo(panel2, SWT.DROP_DOWN | SWT.READ_ONLY);
				modeChoice.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				for (int i = 0; i < modes.length; i++)
					modeChoice.add(modes[i]);
				modeChoice.select(mode);
				modeChoice.addSelectionListener(ThresholdAdjuster.this);
				// modeChoice.addKeyListener(ij);
				// panel.add(modeChoice);
				/*
				 * c.gridx = 0; c.gridy = y++; c.gridwidth = 2; c.insets = new Insets(8, 5, 0,
				 * 5); c.anchor = GridBagConstraints.CENTER; c.fill = GridBagConstraints.NONE;
				 * add(panel, c);
				 */

				// checkboxes
				// panel = new Panel();
				org.eclipse.swt.widgets.Composite panel3 = new org.eclipse.swt.widgets.Composite(shell, SWT.NONE);
				panel3.setLayout(new org.eclipse.swt.layout.GridLayout(2, true));
				// panel.setLayout(new GridLayout(2, 2));
				boolean db = Prefs.get(DARK_BACKGROUND, Prefs.blackBackground ? true : false);
				// darkBackground = new Checkbox("Dark background");
				darkBackgroundCheckbox  = new org.eclipse.swt.widgets.Button(panel3, SWT.CHECK);
				darkBackgroundCheckbox .setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				darkBackgroundCheckbox .setText("Dark background");
				darkBackgroundCheckbox .setSelection(db);
				darkBackgroundCheckbox .addSelectionListener(ThresholdAdjuster.this);
				// panel.add(darkBackground);
				// stackHistogram = new Checkbox("Stack histogram");

				stackCheckbox = new org.eclipse.swt.widgets.Button(panel3, SWT.CHECK);
				stackCheckbox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				stackCheckbox.setText("Stack histogram");
				stackCheckbox.setSelection(false);
				stackCheckbox.addSelectionListener(ThresholdAdjuster.this);
				
				noReset = Prefs.get(NO_RESET, true);
				sixteenBit = Prefs.get(SIXTEEN_BIT, false);
				if (sixteenBit)
					noReset = true;
				
							
				sixteenBitCheckbox = new org.eclipse.swt.widgets.Button(panel3, SWT.CHECK);
				sixteenBitCheckbox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				sixteenBitCheckbox.setText("16-bit histogram");
				sixteenBitCheckbox.setSelection(sixteenBit);
				sixteenBitCheckbox.addSelectionListener(ThresholdAdjuster.this);
				
				noResetCheckbox  = new org.eclipse.swt.widgets.Button(panel3, SWT.CHECK);
				noResetCheckbox .setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				noResetCheckbox .setText("Don't reset range");
				noResetCheckbox .setSelection(noReset);
				noResetCheckbox .addSelectionListener(ThresholdAdjuster.this);
				
				rawValues = new org.eclipse.swt.widgets.Button(panel3, SWT.CHECK);
				rawValues.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				rawValues.setText("Raw values");
				rawValues.setSelection(Prefs.get(RAW_VALUES, false));
				rawValues.addSelectionListener(ThresholdAdjuster.this);
				
				
				// panel.add(stackHistogram);
				// noReset = Prefs.get(NO_RESET, false);
				// noResetButton = new Checkbox("Don't reset range");

				

				// panel.add(noResetButton);
				/*
				 * c.gridx = 0; c.gridy = y++; c.gridwidth = 2; c.insets = new Insets(5, 5, 0,
				 * 0); add(panel, c);
				 */

				// buttons
				int trim = IJ.isMacOSX() ? 11 : 0;
				// panel = new Panel();
				org.eclipse.swt.widgets.Composite panel4 = new org.eclipse.swt.widgets.Composite(shell, SWT.NONE);
				panel4.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				panel4.setLayout(new org.eclipse.swt.layout.GridLayout(2, true));
				// int hgap = IJ.isMacOSX()?1:5;
				// panel.setLayout(new FlowLayout(FlowLayout.RIGHT,hgap,0));
				// autoB = new TrimmedButton("Auto",trim);
				autoB = new org.eclipse.swt.widgets.Button(panel4, SWT.NONE);
				autoB.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				autoB.setText("Auto");
				autoB.addSelectionListener(ThresholdAdjuster.this);
				autoB.addKeyListener(ij);
				// panel.add(autoB);
				// applyB = new TrimmedButton("Apply",trim);
				// applyB.addActionListener(this);
				applyB = new org.eclipse.swt.widgets.Button(panel4, SWT.NONE);
				applyB.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				applyB.setText("Apply");
				applyB.addSelectionListener(ThresholdAdjuster.this);
				applyB.addKeyListener(ij);
				// panel.add(applyB);
				// resetB = new TrimmedButton("Reset",trim);
				// resetB.addActionListener(this);

				resetB = new org.eclipse.swt.widgets.Button(panel4, SWT.NONE);
				resetB.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				resetB.setText("Reset");
				resetB.addSelectionListener(ThresholdAdjuster.this);
				resetB.addKeyListener(ij);
				// panel.add(resetB);

				// setB = new TrimmedButton("Set",trim);
				// setB.addActionListener(this);
				setB = new org.eclipse.swt.widgets.Button(panel4, SWT.NONE);
				setB.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				setB.setText("Set");
				setB.addSelectionListener(ThresholdAdjuster.this);
				setB.addKeyListener(ij);
				// panel.add(setB);
				/*
				 * c.gridx = 0; c.gridy = y++; c.gridwidth = 2; c.insets = new Insets(0, 5, 10,
				 * 5); add(panel, c);
				 */
				/* To do after port to SWT */
				// addKeyListener(ij); // ImageJ handles keyboard shortcuts
				// GUI.scale(this);
				shell.layout(true);
				shell.pack();
				Point loc = Prefs.getLocation(LOC_KEY);
				if (loc != null)
					shell.setLocation(loc.x, loc.y);
				else
					GUI.centerOnImageJScreen(shell);
				// if (IJ.isMacOSX()) setResizable(false);
				// show();
				shell.setVisible(true);
				ImagePlus imp = WindowManager.getCurrentImage();
				ImagePlus.addImageListener(ThresholdAdjuster.this);
				if (imp != null) {
					setup(imp, true);
					updateScrollBars();
				}

			}
		});
		thread = new Thread(this, "ThresholdAdjuster");
		// thread.setPriority(thread.getPriority()-1);
		thread.start();
		
	}

	public Shell getShell() {
		return shell;
	}

	public synchronized void adjustmentValueChanged(SelectionEvent e) {
		org.eclipse.swt.widgets.Slider b = (org.eclipse.swt.widgets.Slider) e.widget;
		if (b == minSlider)
			minValue = minSlider.getSelection();
		else
			maxValue = maxSlider.getSelection();
		enterPressed = false;
		notify();
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		if (e.widget instanceof org.eclipse.swt.widgets.Slider) {
			adjustmentValueChanged(e);
		} else if (e.widget instanceof org.eclipse.swt.widgets.Combo) {
			itemStateChanged(e);
		}

		else if (e.widget instanceof org.eclipse.swt.widgets.Button) {
			org.eclipse.swt.widgets.Button bu = (org.eclipse.swt.widgets.Button) e.widget;
			int style = bu.getStyle();
			if ((style & SWT.CHECK) != 0) {
				itemStateChanged(e);
			} else {
				actionPerformed(e);
			}
		}

	}

	public synchronized void actionPerformed(SelectionEvent e) {
		org.eclipse.swt.widgets.Button b = (org.eclipse.swt.widgets.Button) e.widget;
		if (b == null)
			return;
		if (b == resetB)
			doReset = true;
		else if (b == autoB)
			doAutoAdjust = true;
		else if (b == applyB)
			doApplyLut = true;
		else if (b == setB) {
			doSet = true;
			setButtonPressed = true;
		}
		notify();
	}

	public synchronized void focusLost(FocusEvent e) {
		doSet = true;
		notify();
	}

	public void mouseScrolled(org.eclipse.swt.events.MouseEvent e) {

		mouseWheelMoved(e);

	}

	public synchronized void mouseWheelMoved(org.eclipse.swt.events.MouseEvent e) {
		if (e.getSource() == minSlider || e.getSource() == minLabel) {
			minSlider.setSelection(minSlider.getSelection() + e.count);
			minValue = minSlider.getSelection();
		} else {
			maxSlider.setSelection(maxSlider.getSelection() + e.count);
			maxValue = maxSlider.getSelection();
		}
		notify();
	}

	public synchronized void keyPressed(org.eclipse.swt.events.KeyEvent e) {
		if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
			doSet = true;
			enterPressed = true;
		} else if (e.keyCode == SWT.DOWN) {
			if (e.getSource() == minLabel) {
				minSlider.setSelection(minSlider.getSelection() - 1);
				minValue = minSlider.getSelection();
			} else {
				maxSlider.setSelection(maxSlider.getSelection() - 1);
				maxValue = maxSlider.getSelection();
			}
		} else if (e.keyCode == KeyEvent.VK_UP) {
			if (e.getSource() == minLabel) {
				minSlider.setSelection(minSlider.getSelection() + 1);
				minValue = minSlider.getSelection();
			} else {
				maxSlider.setSelection(maxSlider.getSelection() + 1);
				maxValue = maxSlider.getSelection();
			}
		} else
			return;
		notify();
	}

	public void keyReleased(KeyEvent e) {
	}

	public void keyTyped(KeyEvent e) {
	}

	public void imageUpdated(ImagePlus imp) {
		if (imp.getID() == previousImageID && Thread.currentThread() != thread)
			imageWasUpdated = true;
	}

	public void imageOpened(ImagePlus imp) {
	}

	public void imageClosed(ImagePlus imp) {
	}

	void setLutColor(int mode) {
		switch (mode) {
		case RED:
			lutColor = ImageProcessor.RED_LUT;
			break;
		case BLACK_AND_WHITE:
			lutColor = ImageProcessor.BLACK_AND_WHITE_LUT;
			break;
		case OVER_UNDER:
			lutColor = ImageProcessor.OVER_UNDER_LUT;
			break;
		}
	}

	public synchronized void itemStateChanged(SelectionEvent e) {
		Object source = e.getSource();
		boolean conditionalAutoAdjust = false;
		if (source == methodChoice) {
			method = methodChoice.getItem(methodChoice.getSelectionIndex());
			doAutoAdjust = true;
		} else if (source == modeChoice) {
			mode = modeChoice.getSelectionIndex();
			setLutColor(mode);
			doStateChange = true;
			if (IJ.recording()) {
				if (Recorder.scriptMode())
					Recorder.recordCall("ThresholdAdjuster.setMode(\"" + modes[mode] + "\");");
				else
					Recorder.recordString(
							"call(\"ij.plugin.frame.ThresholdAdjuster.setMode\", \"" + modes[mode] + "\");\n");
			}
		} else if (source==darkBackgroundCheckbox) {
			conditionalAutoAdjust = true;
		} else if (source==noResetCheckbox) {
			noReset = noResetCheckbox.getSelection();
			conditionalAutoAdjust = true;
		} else if (source == rawValues) {
			ThresholdAdjuster.update();

		} else if (source==sixteenBitCheckbox) {
			sixteenBit = sixteenBitCheckbox.getSelection();
			if (sixteenBit)
				noReset = true;
			conditionalAutoAdjust = true;
		} else if (source==stackCheckbox) {
			conditionalAutoAdjust = true;
		} else
			doAutoAdjust = true;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (conditionalAutoAdjust && imp!=null && imp.isThreshold())
			doAutoAdjust = true;
		notify();
	}

	/**
	 * Called before each user interface action. Auto-thresholding is performed if
	 * there is currently no threshold and 'enableAutoThreshold' is true. Returns
	 * the ImageProcessor of the image that should be used, or null if no
	 * appropriate image.
	 */
	ImageProcessor setup(ImagePlus imp, boolean enableAutoThreshold) {
		if (IJ.debugMode)
			IJ.log("ThresholdAdjuster.setup: enableAuto=" + enableAutoThreshold);
		if (imp == null)
			return null;
		ImageProcessor ip;
		int type = imp.getType();
		if (type == ImagePlus.COLOR_RGB || (imp.isComposite() && ((CompositeImage) imp).getMode() == IJ.COMPOSITE))
			return null;
		ip = imp.getProcessor();
		boolean minMaxChange = false;
		boolean not8Bits = type == ImagePlus.GRAY16 || type == ImagePlus.GRAY32;
		int slice = imp.getCurrentSlice();
		if (not8Bits) {
			if (ip.getMin() == plot.stackMin && ip.getMax() == plot.stackMax && !imageWasUpdated)
				minMaxChange = false;
			else if (ip.getMin() != previousMin || ip.getMax() != previousMax || imageWasUpdated) {
				minMaxChange = true;
				previousMin = ip.getMin();
				previousMax = ip.getMax();
			} else if (slice != previousSlice)
				minMaxChange = true;
		}
		int id = imp.getID();
		int roiHashCode = roiHashCode(imp.getRoi());
		if (minMaxChange || id != previousImageID || type != previousImageType || imageWasUpdated
				|| roiHashCode != previousRoiHashCode) {
			minThreshold = ip.getMinThreshold();
			maxThreshold = ip.getMaxThreshold();
			boolean isThreshold = minThreshold != ImageProcessor.NO_THRESHOLD
					&& ip.getCurrentColorModel() != ip.getColorModel(); // does not work???
			if (not8Bits && minMaxChange && (!noReset || mode == OVER_UNDER)) {
				double max1 = ip.getMax();
				if (!windowActivated)
					resetMinAndMax(ip);
				windowActivated = false;
				if (maxThreshold == max1)
					maxThreshold = ip.getMax();
			}
			boolean[] rawValuesSelection = new boolean[1];
			Display.getDefault().syncExec(new Runnable() {
				public void run() {
					rawValuesSelection[0] = rawValues.getSelection();
				}
			});
			ImageStatistics stats = plot.setHistogram(imp, entireStack(imp), rawValuesSelection[0]);

			if (stats == null)
				return null;
			if (isThreshold) {
				minThreshold = scaleDown(ip, minThreshold);
				maxThreshold = scaleDown(ip, maxThreshold);
			} else {
				if (enableAutoThreshold && !isThreshold) {
					Display.getDefault().syncExec(new Runnable() {
						public void run() {
							autoSetLevels(imp);
						}
					});
				} else
					minThreshold = ImageProcessor.NO_THRESHOLD; // may be an invisible threshold after 'apply'
			}
			scaleUpAndSet(ip, minThreshold, maxThreshold);
			updateLabels(imp, ip);
			updatePercentiles(imp, ip);
			updatePlot(ip);
			imp.updateAndDraw();
			imageWasUpdated = false;
		}
		previousImageID = id;
		previousImageType = type;
		previousRoiHashCode = roiHashCode;
		previousSlice = slice;
		firstActivation = false;
		return ip;
	}

	private void resetMinAndMax(ImageProcessor ip) {
		if (ip.getBitDepth() != 8 && (!noReset || mode == OVER_UNDER)) {
			ImageStatistics stats = ip.getStats();
			if (ip.getMin() != stats.min || ip.getMax() != stats.max) {
				ip.resetMinAndMax();
				ContrastAdjuster.update();
			}
		}
	}

	boolean entireStack(ImagePlus imp) {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				selection = stackCheckbox.getSelection();

			}
		});
		return stackCheckbox != null && selection && imp.getStackSize() > 1;
	}

	void autoSetLevels(ImagePlus imp) {
		int bitDepth = imp.getBitDepth();
		boolean darkb = darkBackgroundCheckbox!=null && darkBackgroundCheckbox.getSelection();
		boolean stack = entireStack(imp);
		boolean hist16 = sixteenBit && bitDepth!=32;
		String methodAndOptions = method+(darkb?" dark":"")+(hist16?" 16-bit":"")+(stack?" stack":"")+(noReset?" no-reset":"");
		imp.setAutoThreshold(methodAndOptions);
		ImageProcessor ip = imp.getProcessor();
		double level1 = ip.getMinThreshold();
		double level2 = ip.getMaxThreshold();
		ip.setThreshold(level1, level2, lutColor);
		imp.updateAndDraw();
		minThreshold = scaleDown(ip, level1);
		maxThreshold = scaleDown(ip, level2);
		//IJ.log("autoSetLevels: "+level1+" "+level2+" "+methodAndOptions);
		updateScrollBars();
		if (IJ.recording()) {
			if (noReset && ip.getBitDepth()!=8) {
				ImageStatistics stats2 = ip.getStats();
				if (ip.getMin()>stats2.min || ip.getMax()<stats2.max)
					ContrastAdjuster.recordSetMinAndMax(ip.getMin(),ip.getMax());
			}
			if (Recorder.scriptMode())
				Recorder.recordCall("imp.setAutoThreshold(\""+methodAndOptions+"\");");
			else
				Recorder.record("setAutoThreshold", methodAndOptions);
		}
		
		/*
		if (stats == null || stats.histogram == null) {
			minThreshold = defaultMinThreshold;
			maxThreshold = defaultMaxThreshold;
			return;
		}
		int modifiedModeCount = stats.histogram[stats.mode];
		if (!method.equals(methodNames[DEFAULT]))
			stats.histogram[stats.mode] = plot.originalModeCount;
		int threshold = thresholder.getThreshold(method, stats.histogram);
		stats.histogram[stats.mode] = modifiedModeCount;
		if (thresholdHigh(ip)) // dark background for non-inverting LUT, or bright background for inverting LUT
		{minThreshold=threshold+(addOne?1:0); maxThreshold=255;}
		 else {
			minThreshold = 0;
			maxThreshold = threshold;
		}
		if (minThreshold > 255)
			minThreshold = 255;
		*/
	}

	/**
	 * Whether the (auto)-thresholded pixels should be those with high values, i.e.,
	 * the background should be at low values. (E.g. dark background and
	 * non-inverting LUT)
	 */
	boolean thresholdHigh(ImageProcessor ip) {
		boolean darkb = darkBackgroundCheckbox!=null && darkBackgroundCheckbox.getSelection();
		boolean invertedLut = ip.isInvertedLut();
		return invertedLut ? !darkb : darkb;
	}

	/** Scales threshold levels in the range 0-255 to the actual levels. */
	void scaleUpAndSet(ImageProcessor ip, double lower, double upper) {
		ip.scaleAndSetThreshold(lower, upper, lutColor);
	}

	/** Scales a threshold level to the range 0-255. */
	double scaleDown(ImageProcessor ip, double threshold) {
		if (ip instanceof ByteProcessor)
			return threshold;
		double min = ip.getMin();
		double max = ip.getMax();
		if (max > min) {
			double scaledThr = ((threshold - min) / (max - min)) * 255.0;
			if (scaledThr < 0.0)
				scaledThr = 0.0;
			if (scaledThr > 255.0)
				scaledThr = 255.0;
			return scaledThr;
		} else
			return ImageProcessor.NO_THRESHOLD;
	}

	/** Scales a threshold level in the range 0-255 to the actual level. */
	double scaleUp(ImageProcessor ip, double threshold) {
		double min = ip.getMin();
		double max = ip.getMax();
		if (max > min)
			return min + (threshold / 255.0) * (max - min);
		else
			return ImageProcessor.NO_THRESHOLD;
	}

	void updatePlot(ImageProcessor ip) {
		int min = (int) Math.round(minThreshold);
		if (min < 0)
			min = 0;
		if (min > 255)
			min = 255;
		if (ip.getMinThreshold() == ImageProcessor.NO_THRESHOLD)
			min = -1;
		int max = (int) Math.round(maxThreshold);
		if (max < 0)
			max = 0;
		if (max > 255)
			max = 255;
		plot.setThreshold(min, max);
		plot.mode = mode;
		plot.repaint();
	}

	void updatePercentiles(ImagePlus imp, ImageProcessor ip) {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				if (percentiles == null)
					return;
				ImageStatistics stats = plot.stats;
				int minThresholdInt = (int) Math.round(minThreshold);
				if (minThresholdInt < 0)
					minThresholdInt = 0;
				if (minThresholdInt > 255)
					minThresholdInt = 255;
				int maxThresholdInt = (int) Math.round(maxThreshold);
				if (maxThresholdInt < 0)
					maxThresholdInt = 0;
				if (maxThresholdInt > 255)
					maxThresholdInt = 255;
				if (stats != null && stats.histogram != null && stats.histogram.length == 256
						&& ip.getMinThreshold() != ImageProcessor.NO_THRESHOLD) {
					int[] histogram = stats.histogram;
					int below = 0, inside = 0, above = 0;
					int minValue = 0, maxValue = 255;
					if (imp.getBitDepth() == 16 && !entireStack(imp)) { // 16-bit histogram for better accuracy
						ip.setRoi(imp.getRoi());
						histogram = ip.getHistogram();
						minThresholdInt = (int) Math.round(ip.getMinThreshold());
						if (minThresholdInt < 0)
							minThresholdInt = 0;
						maxThresholdInt = (int) Math.round(ip.getMaxThreshold());
						if (maxThresholdInt > 65535)
							maxThresholdInt = 65535;
						minValue = 0;
						maxValue = histogram.length - 1;
					}
					for (int i = minValue; i < minThresholdInt; i++)
						below += histogram[i];
					for (int i = minThresholdInt; i <= maxThresholdInt; i++)
						inside += histogram[i];
					for (int i = maxThresholdInt + 1; i <= maxValue; i++)
						above += histogram[i];
					int total = below + inside + above;
					// IJ.log("<"+minThresholdInt+":"+below+" in:"+inside+";
					// >"+maxThresholdInt+":"+above+" sum="+total);
					if (mode == OVER_UNDER)
						percentiles.setText("below: " + IJ.d2s(100. * below / total) + " %,  above: "
								+ IJ.d2s(100. * above / total) + " %");
					else
						percentiles.setText(IJ.d2s(100. * inside / total) + " %");
				} else
					percentiles.setText("");
			}
		});
	}

	void updateLabels(ImagePlus imp, ImageProcessor ip) {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				if (minLabel == null || maxLabel == null || enterPressed)
					return;
				double min = ip.getMinThreshold();
				double max = ip.getMaxThreshold();
				if (min == ImageProcessor.NO_THRESHOLD) {
					minLabel.setText("");
					maxLabel.setText("");
				} else {
					Calibration cal = imp.getCalibration();
					boolean calibrated = cal.calibrated() && !rawValues.getSelection();
					if (calibrated) {
						min = cal.getCValue((int) min);
						max = cal.getCValue((int) max);
					}
					if ((((int) min == min && (int) max == max && Math.abs(min) < 1e6 && Math.abs(max) < 1e6))
							|| (ip instanceof ShortProcessor && (cal.isSigned16Bit() || !calibrated))) {
						minLabel.setText(ResultsTable.d2s(min, 0));
						maxLabel.setText(ResultsTable.d2s(max, 0));
					} else {
						minLabel.setText(min == -1e30 ? "-1e30" : d2s(min));
						maxLabel.setText(max == 1e30 ? "1e30" : d2s(max));
					}
				}
			}
		});
	}

	/**
	 * Converts a number to a String, such that it should not take much space (for
	 * the minLabel, maxLabel TextFields)
	 */
	String d2s(double x) {
		int digits = 2;
		if (Math.abs(x)<100) digits=3;
		if (Math.abs(x)<10) digits=4;
		if (x<0 && digits==4) digits=3;
		return Math.abs(x)>=1e6 ? IJ.d2s(x,-2) : ResultsTable.d2s(x,digits);  //the latter uses exp notation also for small x
	}

	void updateScrollBars() {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				minSlider.setSelection((int) minThreshold);
				maxSlider.setSelection((int) maxThreshold);
			}
		});
	}

	/** Restore image outside non-rectangular roi. */
	void doMasking(ImagePlus imp, ImageProcessor ip) {
		ImageProcessor mask = imp.getMask();
		if (mask != null)
			ip.reset(mask);
	}

	void adjustMinThreshold(ImagePlus imp, ImageProcessor ip, double value) {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				if (IJ.altKeyDown() || IJ.shiftKeyDown()) {
					double width = maxThreshold - minThreshold;
					if (width < 1.0)
						width = 1.0;
					minThreshold = value;
					maxThreshold = minThreshold + width;
					if ((minThreshold + width) > 255) {
						minThreshold = 255 - width;
						maxThreshold = minThreshold + width;
						minSlider.setSelection((int) minThreshold);
					}
					maxSlider.setSelection((int) maxThreshold);
					scaleUpAndSet(ip, minThreshold, maxThreshold);
					return;
				}
				minThreshold = value;
				if (maxThreshold < minThreshold) {
					maxThreshold = minThreshold;
					maxSlider.setSelection((int) maxThreshold);
				}
				scaleUpAndSet(ip, minThreshold, maxThreshold);
			}
		});
	}

	void adjustMaxThreshold(ImagePlus imp, ImageProcessor ip, int cvalue) {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				maxThreshold = cvalue;
				if (minThreshold > maxThreshold) {
					minThreshold = maxThreshold;
					minSlider.setSelection((int) minThreshold);
				}
				if (minThreshold < 0) { // remove NO_THRESHOLD
					minThreshold = 0;
					minSlider.setSelection((int) minThreshold);
				}
				scaleUpAndSet(ip, minThreshold, maxThreshold);
				IJ.setKeyUp(KeyEvent.VK_ALT);
				IJ.setKeyUp(KeyEvent.VK_SHIFT);
			}
		});
	}

	void reset(ImagePlus imp, ImageProcessor ip) {
		//IJ.log("reset1: "+noReset+" "+sixteenBitChanged+" "+mode);
		ip.resetThreshold();
		if (!noReset)
			resetMinAndMax(ip);
		boolean[] rawValuesSelection = new boolean[1];
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				rawValuesSelection[0] = rawValues.getSelection();
			}
		});
		ImageStatistics stats = plot.setHistogram(imp, entireStack(imp), rawValuesSelection[0]);
		if (ip.getBitDepth() != 8 && entireStack(imp))
			ip.setMinAndMax(stats.min, stats.max);
		updateScrollBars();
		if (IJ.recording()) {
			if (Recorder.scriptMode())
				Recorder.recordCall("IJ.resetThreshold(imp);");
			else
				Recorder.record("resetThreshold");
		}
	}

	/** Numeric input via 'Set' dialog or minLabel, maxLabel TextFields */
	void doSet(ImagePlus imp, ImageProcessor ip) {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				double level1 = ip.getMinThreshold();
				double level2 = ip.getMaxThreshold();
				Calibration cal = imp.getCalibration();
				if (level1 == ImageProcessor.NO_THRESHOLD) {
					level1 = scaleUp(ip, defaultMinThreshold);
					level2 = scaleUp(ip, defaultMaxThreshold);
				}
				boolean calibrated = cal.calibrated() && !rawValues.getSelection();
				if (calibrated) {
					level1 = cal.getCValue(level1);
					level2 = cal.getCValue(level2);
				}
				if (setButtonPressed) {
					int digits = (ip instanceof FloatProcessor) || (calibrated && !cal.isSigned16Bit())
							? Math.max(Analyzer.getPrecision(), 4)
							: 0;
					GenericDialog gd = new GenericDialog("Set Threshold Levels");
					gd.addNumericField("Lower threshold level: ", level1, Math.abs(level1) < 1e7 ? digits : -4, 10,
							null);
					gd.addNumericField("Upper threshold level: ", level2, Math.abs(level2) < 1e7 ? digits : -4, 10,
							null);
					gd.showDialog();
					if (gd.wasCanceled()) {
						setButtonPressed = false;
						return;
					}
					level1 = gd.getNextNumber();
					level2 = gd.getNextNumber();
					setButtonPressed = false;
				} else {
					level1 = Tools.parseDouble(minLabel.getText(), level1);
					level2 = Tools.parseDouble(maxLabel.getText(), level2);
				}
				enterPressed = false;
				if (calibrated) {
					level1 = cal.getRawValue(level1);
					level2 = cal.getRawValue(level2);
				}
				if (level2 < level1)
					level2 = level1;
				resetMinAndMax(ip);
				double minValue = ip.getMin();
				double maxValue = ip.getMax();
				if (imp.getStackSize() == 1) {
					if (level1 < minValue)
						level1 = minValue;
					if (level2 > maxValue)
						level2 = maxValue;
				}
				IJ.wait(500);
				ip.setThreshold(level1, level2, lutColor);
				ip.setSnapshotPixels(null); // disable undo
				previousImageID = 0;
				setup(imp, false);
				updateScrollBars();
				if (IJ.recording()) {
					if (imp.getBitDepth() == 32) {
						if (Recorder.scriptMode())
							Recorder.recordCall("IJ.setThreshold(imp, " + IJ.d2s(ip.getMinThreshold(), 4) + ", "
									+ IJ.d2s(ip.getMaxThreshold(), 4) + ");");
						else
							Recorder.record("setThreshold", ip.getMinThreshold(), ip.getMaxThreshold());
					} else {
						int min = (int) ip.getMinThreshold();
						int max = (int) ip.getMaxThreshold();
						if (cal.isSigned16Bit() && calibrated) {
							min = (int) cal.getCValue(level1);
							max = (int) cal.getCValue(level2);
							if (Recorder.scriptMode())
								Recorder.recordCall("IJ.setThreshold(imp, " + min + ", " + max + ");");
							else
								Recorder.record("setThreshold", min, max);
						} else {
							if (Recorder.scriptMode())
								Recorder.recordCall("IJ.setRawThreshold(imp, " + min + ", " + max + ");");
							else
								Recorder.record("setThreshold", min, max, "raw");
						}
					}
				}
			}
		});
	}

	void changeState(ImagePlus imp, ImageProcessor ip) {
		scaleUpAndSet(ip, minThreshold, maxThreshold);
		updateScrollBars();
	}

	void autoThreshold(ImagePlus imp, ImageProcessor ip) {
		ip.resetThreshold();
		previousImageID = 0;
		setup(imp, true);
		updateScrollBars();
	}

	void apply(ImagePlus imp) {
		if (imp.getProcessor().getMinThreshold() == ImageProcessor.NO_THRESHOLD) {
			IJ.error("Thresholder", "Threshold is not set");
			return;
		}
		try {
			if (imp.getBitDepth() == 32) {
				YesNoCancelDialog d = new YesNoCancelDialog("Thresholder",
						"Convert to 8-bit mask or set background pixels to NaN?", "Convert to Mask", "Set to NaN");
				if (d.cancelPressed())
					return;
				else if (!d.yesPressed()) {
					Recorder.recordInMacros = true;
					IJ.run("NaN Background");
					Recorder.recordInMacros = false;
					return;
				}
			}
			runThresholdCommand();
		} catch (Exception e) {
		}
	}

	void runThresholdCommand() {
		Thresholder.setMethod(method);
		Thresholder.setBackground(darkBackgroundCheckbox.getSelection()?"Dark":"Light");
		if (IJ.recording()) {
			Recorder.setCommand("Convert to Mask");
			(new Thresholder()).run("mask");
			Recorder.saveCommand();
		} else
			(new Thresholder()).run("mask");
	}

	static final int RESET=0, AUTO=1, HIST=2, APPLY=3, STATE_CHANGE=4, MIN_THRESHOLD=5, MAX_THRESHOLD=6, SET=7;

	// Separate thread that does the potentially time-consuming processing
	public void run() {
		while (!done) {
			synchronized (this) {
				if (!doAutoAdjust && !doReset && !doApplyLut && !doStateChange && !doSet && minValue<0 &&  maxValue<0) {
					try {
						wait();
					} catch (InterruptedException e) {
					}
				}
			}
			doUpdate();
		}
	}

	/**
	 * Triggered by the user interface, with the corresponding boolean, e.g.,
	 * 'doAutoAdjust'
	 */
	void doUpdate() {
		ImagePlus imp;
		ImageProcessor ip;
		int action;
		int min = minValue;
		int max = maxValue;
		if (doReset) {
			action = RESET;
			doReset = false;
		} else if (doAutoAdjust) {
			action = AUTO;
			doAutoAdjust = false;
		} else if (doApplyLut) {
			action = APPLY;
			doApplyLut = false;
		} else if (doStateChange) {
			action = STATE_CHANGE;
			doStateChange = false;
		} else if (doSet) {
			action = SET;
			doSet = false;
		} 
		else if (minValue >= 0) {
			action = MIN_THRESHOLD;
			minValue = -1;
		} else if (maxValue >= 0) {
			action = MAX_THRESHOLD;
			maxValue = -1;
		} else
			return;
		imp = WindowManager.getCurrentImage();
		if (imp == null) {
			IJ.beep();
			IJ.showStatus("No image");
			return;
		}
		ip = setup(imp, false);
		if (ip == null) {
			imp.unlock();
			IJ.beep();
			if (imp.isComposite())
				IJ.showStatus("\"Composite\" mode images cannot be thresholded");
			else
				IJ.showStatus("RGB images cannot be thresholded");
			return;
		}
		switch (action) {
		case RESET:
			reset(imp, ip);
			break;
		case AUTO:
			autoThreshold(imp, ip);
			break;
		case APPLY:
			apply(imp);
			break;
		case STATE_CHANGE:
			changeState(imp, ip);
			break;
		case SET:
			doSet(imp, ip);
			break;
		case MIN_THRESHOLD:
			adjustMinThreshold(imp, ip, min);
			break;
		case MAX_THRESHOLD:
			adjustMaxThreshold(imp, ip, max);
			break;
		}
		updatePlot(ip);
		updateLabels(imp, ip);
		updatePercentiles(imp, ip);
		ip.setLutAnimation(true);
		imp.updateAndDraw();
	}

	/** Overrides shellClosed() in PlugInDialog. */
	public void shellClosed(ShellEvent e) {
		e.doit = false;
		/*
		 * We dispose the image os which is created in the paintContol method and
		 * disposed in the setHistogram method, too but not automatically when the shell
		 * is closed!
		 */
		if (plot.os != null && !plot.os.isDisposed()) {
			plot.os.dispose();
		}
		instance = null;
		done = true;
		Prefs.saveLocation(LOC_KEY, shell.getLocation());
		Prefs.set(MODE_KEY, mode);
		Prefs.set(DARK_BACKGROUND, darkBackgroundCheckbox.getSelection());
		Prefs.set(NO_RESET, noResetCheckbox.getSelection());
		Prefs.set(SIXTEEN_BIT, sixteenBitCheckbox.getSelection());
		Prefs.set(RAW_VALUES, rawValues.getSelection());
		synchronized (this) {
			notify();
		}
		super.shellClosed(e);
		// called in super class!
		// e.doit = true;
	}

	public void shellActivated(ShellEvent e) {
		super.shellActivated(e);
		plot.setFocus();
		ImagePlus imp = WindowManager.getCurrentImage();
		if (!firstActivation && imp != null) {
			windowActivated = true;
			setup(imp, false);
			updateScrollBars();
		}
	}

	// Returns a hashcode for the specified ROI that typically changes
	// if it is moved, even though is still the same object.
	int roiHashCode(Roi roi) {
		return roi != null ? roi.getHashCode() : 0;
	}

	/**
	 * Notifies the ThresholdAdjuster that the image has changed. If the image has
	 * no threshold, it does not autothreshold the image.
	 */
	public static void update() {
		if (instance != null) {
			ThresholdAdjuster ta = ((ThresholdAdjuster) instance);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp != null && ta.previousImageID == imp.getID()) {
				if ((imp.getCurrentSlice() != ta.previousSlice) && ta.entireStack(imp))
					return;
				ta.previousImageID = 0;
				ta.setup(imp, false);
				ta.updateScrollBars();
			}
		}
	}

	public static boolean isDarkBackground() {
		return instance!=null?instance.darkBackgroundCheckbox.getSelection():false;
	}

	/** Returns the current thresholding method ("Default", "Huang", etc). */
	public static String getMethod() {
		return method;
	}

	/** Sets the thresholding method ("Default", "Huang", etc). */
	public static void setMethod(String thresholdingMethod) {
		if (thresholdingMethod==null)
			return;
		boolean valid = false;
		for (int i = 0; i < methodNames.length; i++) {
			if (thresholdingMethod.startsWith(thresholdingMethod)) {
				valid = true;
				break;
			}
		}
		if (valid) {
			method = thresholdingMethod;
			int index = method.indexOf(" ");
			if (index>0)
				method = method.substring(0,index);
			if (instance != null) {
				for (int i = 0; i < instance.methodChoice.getItemCount(); i++) {
					final String value = instance.methodChoice.getItem(i);
					if (value.equals(method)) {
						instance.methodChoice.select(i);
					}
				}

			}
		}
	}

	/** Returns the current mode ("Red","B&W" or"Over/Under"). */
	public static String getMode() {
		return modes[mode];
	}

	/** Sets the current mode ("Red","B&W" or"Over/Under"). */
	public static void setMode(String tmode) {
		if (instance != null)
			synchronized (instance) {
				ThresholdAdjuster ta = ((ThresholdAdjuster) instance);
				if (modes[0].equals(tmode))
					mode = 0;
				else if (modes[1].equals(tmode))
					mode = 1;
				else if (modes[2].equals(tmode))
					mode = 2;
				else
					return;
				ta.setLutColor(mode);
				ta.doStateChange = true;
				ta.modeChoice.select(mode);
				ta.notify();
			}
	}

	@Override
	public void keyReleased(org.eclipse.swt.events.KeyEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		// TODO Auto-generated method stub

	}

} // ThresholdAdjuster class

class ThresholdPlot extends org.eclipse.swt.widgets.Canvas
		implements Measurements, org.eclipse.swt.events.MouseListener, PaintListener {
	double scale = Prefs.getGuiScale();
	int width = (int) Math.round(256 * scale);
	int height = (int) Math.round(48 * scale);
	int lowerThreshold = -1;
	int upperThreshold = (int) Math.round(170 * scale);

	ImageStatistics stats;
	int[] histogram;
	org.eclipse.swt.graphics.Color[] hColors;
	int hmax; // maximum of histogram to display
	public org.eclipse.swt.graphics.Image os;
	Graphics osg;
	int mode;
	int originalModeCount;
	double stackMin, stackMax;
	int imageID2; // ImageID of previous call
	boolean entireStack2; // 'entireStack' of previous call
	double mean2;
	private GC gcos;

	public ThresholdPlot(Shell shell) {
		super(shell, SWT.DOUBLE_BUFFERED);
		addMouseListener(this);
		addPaintListener(this);
		setSize(width + 2, height + 2);
	}

	/**
	 * Overrides Component getPreferredSize(). Added to work around a bug in Java
	 * 1.4.1 on Mac OS X.
	 */
	public Dimension getPreferredSize() {
		return new Dimension(width + 2, height + 2);
	}

	ImageStatistics setHistogram(ImagePlus imp, boolean entireStack, boolean rawValues) {
		if (IJ.debugMode)
			IJ.log("ThresholdAdjuster:setHistogram: " + entireStack + " " + entireStack2);
		double mean = entireStack ? imp.getProcessor().getStats().mean : 0.0;
		if (entireStack && stats != null && imp.getID() == imageID2 && entireStack == entireStack2 && mean == mean2)
			return stats;
		mean2 = mean;
		ImageProcessor ip = imp.getProcessor();
		ColorModel cm = ip.getColorModel();
		stats = null;
		if (entireStack) {
			if (imp.isHyperStack()) {
				ImageStack stack = ChannelSplitter.getChannel(imp, imp.getChannel());
				stats = new StackStatistics(new ImagePlus("", stack));
			} else
				stats = new StackStatistics(imp);
		}
		if (!(ip instanceof ByteProcessor)) {
			if (entireStack) {
				if (imp.getLocalCalibration().isSigned16Bit()) {
					stats.min += 32768;
					stats.max += 32768;
				}
				stackMin = stats.min;
				stackMax = stats.max;
				ip.setMinAndMax(stackMin, stackMax);
				imp.updateAndDraw();
			} else {
				stackMin = stackMax = 0.0;
				if (entireStack2) {
					ip.resetMinAndMax();
					imp.updateAndDraw();
				}
			}
			Calibration cal = imp.getCalibration();
			boolean calibrated = cal.calibrated() && !rawValues;
			if (ip instanceof FloatProcessor) {
				int digits = Math.max(Analyzer.getPrecision(), 2);
				IJ.showStatus("min=" + IJ.d2s(ip.getMin(), digits) + ", max=" + IJ.d2s(ip.getMax(), digits));
			} else {
				int digits = calibrated && !cal.isSigned16Bit() ? 2 : 0;
				double cmin = calibrated ? cal.getCValue(ip.getMin()) : ip.getMin();
				double cmax = calibrated ? cal.getCValue(ip.getMax()) : ip.getMax();
				IJ.showStatus(
						"min=" + IJ.d2s(cal.getCValue(cmin), digits) + ", max=" + IJ.d2s(cal.getCValue(cmax), digits));
			}
			ip = ip.convertToByte(true);
			ip.setColorModel(ip.getDefaultColorModel());
		}
		Roi roi = imp.getRoi();
		if (roi != null && !roi.isArea())
			roi = null;
		ip.setRoi(roi);
		if (stats == null)
			stats = ip.getStats();
		if (IJ.debugMode)
			IJ.log("  stats: " + stats);
		int maxCount2 = 0; // number of pixels in 2nd-highest bin, used for y scale if mode is too high
		histogram = stats.histogram;
		originalModeCount = histogram[stats.mode];
		for (int i = 0; i < stats.nBins; i++)
			if ((histogram[i] > maxCount2) && (i != stats.mode))
				maxCount2 = histogram[i];
		hmax = stats.maxCount;
		if ((hmax > (maxCount2 * 1.5)) && (maxCount2 != 0))
			hmax = (int) (maxCount2 * 1.2);
		/* For the paint method dispose the image! */
		if (os != null && !os.isDisposed())
			os.dispose();
		os = null;

		if (!(cm instanceof IndexColorModel))
			return null;
		IndexColorModel icm = (IndexColorModel) cm;
		int mapSize = icm.getMapSize();
		if (mapSize != 256)
			return null;
		byte[] r = new byte[256];
		byte[] g = new byte[256];
		byte[] b = new byte[256];
		icm.getReds(r);
		icm.getGreens(g);
		icm.getBlues(b);
		hColors = new org.eclipse.swt.graphics.Color[256];
		final int brightnessLimit = 1800; // 0 ... 2550 scale; brightness is reduced above
		for (int i = 0; i < 256; i++) { // avoid colors that are too bright (invisible)
			int sum = 4 * (r[i] & 255) + 5 * (g[i] & 255) + (b[i] & 255);
			if (sum > brightnessLimit) {
				r[i] = (byte) (((r[i] & 255) * brightnessLimit * 2) / (sum + brightnessLimit));
				g[i] = (byte) (((g[i] & 255) * brightnessLimit * 2) / (sum + brightnessLimit));
				b[i] = (byte) (((b[i] & 255) * brightnessLimit * 2) / (sum + brightnessLimit));
			}
			hColors[i] = new org.eclipse.swt.graphics.Color(r[i] & 255, g[i] & 255, b[i] & 255);
		}
		imageID2 = imp.getID();
		entireStack2 = entireStack;
		return stats;
	}

	/*
	 * public void update(Graphics g) { paint(g); }
	 */

	public void repaint() {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				redraw();
			}
		});
	}

	@Override
	public void paintControl(PaintEvent e) {
		GC gc = e.gc;
		if (histogram != null) {
			// os is disposed in the setHistogram method!
			if (os == null && hmax > 0) {
				/*
				 * os = createImage(width,height); osg = os.getGraphics();
				 */
				// os is disposed in the setHistogram method!
				os = new org.eclipse.swt.graphics.Image(Display.getDefault(), width, height);
				gcos = new GC(os);
				// if (scale > 1)
				// ((Graphics2D) osg).setStroke(new BasicStroke((float) scale));
				gcos.setForeground(ij.swt.Color.white);
				gcos.fillRectangle(0, 0, width, height);
				gcos.setForeground(ij.swt.Color.gray);
				double scale2 = width / 256.0;
				int barWidth = 1;
				if (scale > 1)
					barWidth = 2;
				if (scale > 2)
					barWidth = 3;
				for (int i = 0; i < 256; i++) {
					if (hColors != null)
						gcos.setForeground(hColors[i]);
					int x = (int) (i * scale2);
					for (int j = 0; j < barWidth; j++)
						gcos.drawLine(x + j, height, x + j, height - ((int) (height * histogram[i] + hmax - 1) / hmax));
				}
				// osg.dispose();
			}
			if (os == null)
				return;
			gc.drawImage(os, 1, 1);
			gcos.dispose();
		} else {
			gc.setBackground(ij.swt.Color.white);
			gc.fillRectangle(1, 1, width, height);
		}
		gc.setForeground(ij.swt.Color.black);
		gc.drawRectangle(0, 0, width + 1, height + 1);
		if (lowerThreshold == -1)
			return;
		if (mode == ThresholdAdjuster.OVER_UNDER) {
			gc.setForeground(ij.swt.Color.blue);
			gc.drawRectangle(0, 0, lowerThreshold, height + 1);
			gc.drawRectangle(0, 1, lowerThreshold, 1);
			gc.setForeground(ij.swt.Color.green);
			gc.drawRectangle(upperThreshold + 2, 0, width - upperThreshold - 1, height + 1);
			gc.drawLine(upperThreshold + 2, 1, width + 1, 1);
			return;
		}
		if (mode == ThresholdAdjuster.RED)
			gc.setForeground(ij.swt.Color.red);
		gc.drawRectangle(lowerThreshold + 1, 0, upperThreshold - lowerThreshold, height + 1);
		gc.drawLine(lowerThreshold + 1, 1, upperThreshold + 1, 1);

		//gc.dispose();

	}

	void setThreshold(int min, int max) {
		lowerThreshold = (int) Math.round(min * scale);
		upperThreshold = (int) Math.round(max * scale);
	}

	public void mousePressed(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}

	public void mouseClicked(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseDoubleClick(org.eclipse.swt.events.MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseDown(org.eclipse.swt.events.MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseUp(org.eclipse.swt.events.MouseEvent e) {
		// TODO Auto-generated method stub

	}

} // ThresholdPlot class