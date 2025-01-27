package ij.plugin.frame;

import java.awt.Frame;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.widgets.Display;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.GenericDialog;
import ij.gui.HTMLDialog;
import ij.plugin.PlugIn;

/** Displays the ImageJ "Channels" dialog. */
public class Channels extends PlugInDialog implements PlugIn, SelectionListener {

	private static final String[] modes = { "Composite", "Color", "Grayscale", "---------", "Composite Max",
			"Composite Min", "Composite Invert" };
	private static final int COMP = 0, COLOR = 1, GRAY = 2, DIVIDER = 3, MAX = 4, MIN = 5, INVERT = 6;
	private static String[] menuItems = { "Make Composite", "Create RGB Image", "Split Channels", "Merge Channels...",
			"Show LUT", "Invert LUTs", "-", "Red", "Green", "Blue", "Cyan", "Magenta", "Yellow", "Grays" };
	private static String moreLabel = "More " + '\u00bb';
	public static final String help = "<html>" + "<h1>Composite Display Modes</h1>" + "<font size=+1>" + "<ul>"
			+ "<li> <u>Composite</u> -  Effectively creates an RGB image for each channel, based on its LUT, and then adds the red, green and blue values to create the displayed image. The values are clipped at 255, which can cause saturation. For an example, open the \"Neuron (5 channel)\" sample image and compare the <i>Composite</i> and <i>Composite Max</i> display modes. This is the original ImageJ composite mode.<br>"
			+ "<li> <u>Composite Max</u> - Similar to <i>Composite</i>, except uses the maximum of the red, green and blue values across all channels.<br>"
			+ "<li> <u>Composite Min</u> - Similar to <i>Composite</i>, except uses the minimum of the red, green and blue values across all channels. This mode, and <i>Composite Invert</i>, require that the channels have inverting (white background) LUTs. Linear non-inverting LUTs that use a single color are automatically inverted.<br>"
			+ "<li> <u>Composite Invert</u> - Similar to <i>Composite</i>, except the red, green and blue values are effectively subracted from 255. The values are clipped at 0, which can cause saturation.<br>"
			+ "</ul>" + "<h1>More" + '\u00bb' + "Commands</h1>" + "<font size=+1>" + "<ul>"
			+ "<li> <u>Make Composite</u> - Converts an RGB image into a three channel composite image.<br>"
			+ "<li> <u>Create RGB image</u> - Creates an RGB version of a multichannel image.<br>"
			+ "<li> <u>Split Channels</u> - Splits a multichannel image into separate images.<br>"
			+ "<li> <u>Merge Channels</u> - Combines multiple images into a single multichannel image.<br>"
			+ "<li> <u>Show LUT</u> - Displays a plot of the current channel's LUT. Click \"List\" to create a table of the RGB values for each of the LUT's 256 entries.<br>"
			+ "<li> <u>Invert LUTs</u> - Inverts the LUTs of all the channels of a composite image. Black background LUTs with ascending RGB values are converted to inverting LUTs (descending RGB values) with white backgrounds, or vis versa. Does nothing if the LUT is not linear or it uses more than one color. This command runs the macro at http://wsr.imagej.net/macros/Invert_All_LUTs.txt.<br>"
			+ "<li> <u>Red, Green, Blue, Cyan, Magenta, Yellow, Grays</u> - Updates the current channel's LUT so that it uses the selected color.<br>"
			+ "</ul>" + "<br>"
			+ "The <i>\"Channels & Colors\"</i> chapter of Pete Bankhead's \"<i>Introduction to Bioimage Analysis</i>\" (https://bioimagebook.github.io) is a good introduction to multichannel images and LUTs.<br>"
			+ "<br>"
			+ "The macro at  <a href=\"http://wsr.imagej.net/macros/CompositeProjection.ijm\" target=\"_blank\">http://wsr.imagej.net/macros/CompositeProjection.ijm</a> uses the \"Invert LUTs\", \"RGB Stack\", \"Z Project\" and \"Invert\" commands to reproduce the four composite display modes.<br>"
			+ "</font>";
	private org.eclipse.swt.widgets.Combo choice;
	private org.eclipse.swt.widgets.Button[] checkbox;
	private org.eclipse.swt.widgets.Button helpButton, moreButton;
	private static Channels instance;
	private int id;
	private static org.eclipse.swt.graphics.Point location;
	private org.eclipse.swt.widgets.Menu pm;

	public Channels() {

		super("Channels");
		if (instance != null) {
			Display.getDefault().syncExec(() -> {

				instance.shell.forceActive();

			});
			return;
		}
		ImageJ ij = IJ.getInstance();
		instance = this;
		/*
		 * GridBagLayout gridbag = new GridBagLayout(); GridBagConstraints c = new
		 * GridBagConstraints();
		 */
		Display.getDefault().syncExec(() -> {

			WindowManager.addWindow(Channels.this);
			shell.setLayout(new org.eclipse.swt.layout.GridLayout(1, true));
			int y = 0;
			/*
			 * c.gridx = 0; c.gridy = y++; c.gridwidth = 1; c.fill =
			 * GridBagConstraints.BOTH; c.anchor = GridBagConstraints.CENTER;
			 */
			/*
			 * int margin = 32; if (IJ.isMacOSX()) margin = 20; c.insets = new Insets(10,
			 * margin, 10, margin);
			 */
			choice = new org.eclipse.swt.widgets.Combo(shell, SWT.DROP_DOWN | SWT.READ_ONLY);
			for (int i = 0; i < modes.length; i++)
				choice.add(modes[i]);
			choice.select(0);
			choice.addSelectionListener(Channels.this);
			if (ij != null)
				choice.addKeyListener(ij);
			// add(choice, c);
			CompositeImage ci = getImage();
			int nCheckBoxes = ci != null ? ci.getNChannels() : 3;
			if (nCheckBoxes > CompositeImage.MAX_CHANNELS)
				nCheckBoxes = CompositeImage.MAX_CHANNELS;
			checkbox = new org.eclipse.swt.widgets.Button[nCheckBoxes];
			for (int i = 0; i < nCheckBoxes; i++) {
				checkbox[i] = new org.eclipse.swt.widgets.Button(shell, SWT.CHECK);
				checkbox[i].setText("Channel " + (i + 1));
				checkbox[i].setSelection(true);
				/*
				 * c.insets = new Insets(0, 25, i<nCheckBoxes-1?0:10, 5); c.gridy = y++;
				 * add(checkbox[i], c);
				 */
				checkbox[i].addSelectionListener(Channels.this);
				checkbox[i].addKeyListener(ij);
			}
			/*
			 * c.insets = new Insets(0, 15, 10, 15); c.fill = GridBagConstraints.NONE;
			 * c.gridy = y++;
			 */
			org.eclipse.swt.widgets.Composite panel = new org.eclipse.swt.widgets.Composite(shell, SWT.NONE);
			// int hgap = IJ.isMacOSX()?1:5;
			panel.setLayout(new org.eclipse.swt.layout.GridLayout(2, true));
			helpButton = new org.eclipse.swt.widgets.Button(panel, SWT.NONE);
			helpButton.setText("Help");
			helpButton.addSelectionListener(Channels.this);
			helpButton.addKeyListener(ij);
			// panel.add(helpButton, c);
			// add(panel, c);
			// moreButton = org.eclipse.swt.widgets.Button(panel, SWT.NONE);
			moreButton = new org.eclipse.swt.widgets.Button(panel, SWT.NONE);
			moreButton.setText(moreLabel);
			moreButton.addSelectionListener(Channels.this);
			moreButton.addKeyListener(ij);
			// add(moreButton, c);
			update();
			pm = new org.eclipse.swt.widgets.Menu(shell, SWT.POP_UP);
			// GUI.scalePopupMenu(pm);
			for (int i = 0; i < menuItems.length; i++)
				addPopupItem(menuItems[i]);
			// add(pm);
			shell.addKeyListener(ij); // ImageJ handles keyboard shortcuts
			// setResizable(false);
			// GUI.scale(this);
			shell.layout();
			shell.pack();
			if (location == null) {
				GUI.centerOnImageJScreen(Channels.this.shell);
				location = shell.getLocation();
			} else
				shell.setLocation(location);
			shell.setVisible(true);

		});
	}

	public void update() {

		CompositeImage ci = getImage();
		if (ci == null || checkbox == null)
			return;
		int n = checkbox.length;
		int nChannels = ci.getNChannels();
		if (nChannels != n && nChannels <= CompositeImage.MAX_CHANNELS) {
			instance = null;
			location = shell.getLocation();
			close();
			new Channels();
			return;
		}
		boolean[] active = ci.getActiveChannels();
		for (int i = 0; i < checkbox.length; i++)
			checkbox[i].setSelection(active[i]);
		int index = 0;
		String cmode = ci.getProp("CompositeProjection");
		int cindex = COMP;
		if (cmode != null) {
			if (cmode.contains("Max") || cmode.contains("max"))
				cindex = MAX;
			if (cmode.contains("Min") || cmode.contains("min"))
				cindex = MIN;
			if (cmode.contains("Invert") || cmode.contains("invert"))
				cindex = INVERT;
		}
		switch (ci.getMode()) {
		case IJ.COMPOSITE:
			index = cindex;
			break;
		case IJ.COLOR:
			index = COLOR;
			break;
		case IJ.GRAYSCALE:
			index = GRAY;
			break;
		}
		choice.select(index);
	}

	public static void updateChannels() {

		Display.getDefault().syncExec(() -> {

			if (instance != null)
				instance.update();

		});
	}

	void addPopupItem(String s) {

		if (s.equals("-")) {
			new org.eclipse.swt.widgets.MenuItem(pm, SWT.SEPARATOR);
		} else {
			org.eclipse.swt.widgets.MenuItem mi = new org.eclipse.swt.widgets.MenuItem(pm, SWT.PUSH);
			mi.setText(s);
			mi.addSelectionListener(this);
		}
		// pm.add(mi);
	}

	CompositeImage getImage() {

		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null || !imp.isComposite())
			return null;
		else
			return (CompositeImage) imp;
	}

	public void itemStateChanged(SelectionEvent e) {

		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null)
			return;
		if (!imp.isComposite()) {
			int channels = imp.getNChannels();
			if (channels == 1 && imp.getStackSize() <= 4)
				channels = imp.getStackSize();
			if (imp.getBitDepth() == 24 || (channels > 1 && channels < CompositeImage.MAX_CHANNELS)) {
				GenericDialog gd = new GenericDialog(imp.getTitle());
				gd.addMessage("Convert to multichannel composite image?");
				gd.showDialog();
				if (gd.wasCanceled())
					return;
				else
					IJ.doCommand("Make Composite");
			} else {
				IJ.error("Channels", "A composite image is required (e.g., " + moreLabel
						+ " Open HeLa Cells),\nor create one using " + moreLabel + " Make Composite.");
				return;
			}
		}
		if (!imp.isComposite())
			return;
		CompositeImage ci = (CompositeImage) imp;
		Object source = e.getSource();
		if (source == choice) {
			int index = ((org.eclipse.swt.widgets.Combo) source).getSelectionIndex();
			String cstr = null;
			int cmode = IJ.COMPOSITE;
			switch (index) {
			case COMP:
				cmode = IJ.COMPOSITE;
				cstr = "Sum";
				break;
			case COLOR:
				cmode = IJ.COLOR;
				break;
			case GRAY:
				cmode = IJ.GRAYSCALE;
				break;
			case DIVIDER:
				cmode = IJ.COMPOSITE;
				cstr = "Sum";
				break;
			case MAX:
				cmode = IJ.COMPOSITE;
				;
				cstr = "Max";
				break;
			case MIN:
				cmode = IJ.COMPOSITE;
				;
				cstr = "Min";
				break;
			case INVERT:
				cmode = IJ.COMPOSITE;
				;
				cstr = "Invert";
				break;
			}
			if (cstr != null && !(cstr.equals("Sum") && ci.getProp("CompositeProjection") == null))
				ci.setProp("CompositeProjection", cstr);
			// IJ.log(cmode+" "+cstr+" "+imp.isInvertedLut());
			if (cmode == IJ.COMPOSITE && (("Min".equals(cstr) || "Invert".equals(cstr)) && !imp.isInvertedLut())
					|| ("Max".equals(cstr) || "Sum".equals(cstr)) && imp.isInvertedLut())
				IJ.runMacroFile("ij.jar:InvertAllLuts", null);
			ci.setMode(cmode);
			ci.updateAndDraw();
			if (IJ.recording()) {
				String mode = null;
				if (index != DIVIDER && Recorder.scriptMode()) {
					switch (index) {
					case COMP:
					case MAX:
					case MIN:
					case INVERT:
						mode = "IJ.COMPOSITE";
						break;
					case COLOR:
						mode = "IJ.COLOR";
						break;
					case GRAY:
						mode = "IJ.GRAYSCALE";
						break;
					}
					cstr = "\"" + cstr + "\"";
					Recorder.recordCall("imp.setProp(\"CompositeProjection\", " + cstr + ");");
					Recorder.recordCall("imp.setDisplayMode(" + mode + ");");
				} else {
					switch (index) {
					case COMP:
					case MAX:
					case MIN:
					case INVERT:
						mode = "composite";
						break;
					case COLOR:
						mode = "color";
						break;
					case GRAY:
						mode = "grayscale";
						break;
					}
					Recorder.recordString("Property.set(\"CompositeProjection\", \"" + cstr + "\");\n");
					Recorder.record("Stack.setDisplayMode", mode);
				}
			}
		} else if (source instanceof org.eclipse.swt.widgets.Button) {
			for (int i = 0; i < checkbox.length; i++) {
				org.eclipse.swt.widgets.Button cb = (org.eclipse.swt.widgets.Button) source;
				if (cb == checkbox[i]) {
					if (ci.getMode() == IJ.COMPOSITE) {
						boolean[] active = ci.getActiveChannels();
						active[i] = cb.getSelection();
						if (IJ.recording()) {
							String str = "";
							for (int c = 0; c < ci.getNChannels(); c++)
								str += active[c] ? "1" : "0";
							if (Recorder.scriptMode())
								Recorder.recordCall("imp.setActiveChannels(\"" + str + "\");");
							else
								Recorder.record("Stack.setActiveChannels", str);
						}
					} else {
						imp.setPosition(i + 1, imp.getSlice(), imp.getFrame());
						if (IJ.recording()) {
							if (Recorder.scriptMode())
								Recorder.recordCall("imp.setC(" + (i + 1) + ");");
							else
								Recorder.record("Stack.setChannel", i + 1);
						}
					}
					ci.updateAndDraw();
					return;
				}
			}
		}
	}

	@Override
	public void widgetSelected(SelectionEvent e) {

		if (e.widget instanceof org.eclipse.swt.widgets.MenuItem) {
			actionPerformed(e);
		} else if (e.widget instanceof org.eclipse.swt.widgets.Button) {
			org.eclipse.swt.widgets.Button it = (org.eclipse.swt.widgets.Button) e.widget;
			if ((it.getStyle() & SWT.CHECK) != 0) {
				itemStateChanged(e);
			} else {
				actionPerformed(e);
			}
		} else {
			itemStateChanged(e);
		}
	}

	public void actionPerformed(SelectionEvent e) {

		String textCommand = null;
		if (e.widget instanceof org.eclipse.swt.widgets.Button) {
			org.eclipse.swt.widgets.Button it = (org.eclipse.swt.widgets.Button) e.widget;
			textCommand = it.getText();
		} else if (e.widget instanceof org.eclipse.swt.widgets.MenuItem) {
			org.eclipse.swt.widgets.MenuItem it = (org.eclipse.swt.widgets.MenuItem) e.widget;
			textCommand = it.getText();
		}
		String command = textCommand;
		if (command.equals("Help")) {
			new HTMLDialog("Channels", help);
			return;
		}
		if (command == null)
			return;
		if (command.equals(moreLabel)) {
			org.eclipse.swt.graphics.Point bloc = shell.toDisplay(moreButton.getLocation());
			// pm.show(this, bloc.x, bloc.y);
			pm.setLocation(bloc.x, bloc.y);
			pm.setVisible(true);
		} else if (command.equals("Create RGB Image"))
			IJ.doCommand("Stack to RGB");
		else {
			Display.getDefault().syncExec(() -> {

				IJ.doCommand(command);

			});
		}
	}

	/** Obsolete; always returns null. */
	public static Frame getInstance() {

		return null;
	}

	/** Overrides shellClosed() in PlugInDialog. */
	public void shellClosed(ShellEvent e) {

		// super.close();
		e.doit = false;
		instance = null;
		location = shell.getLocation();
		super.shellClosed(e);
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent arg0) {
		// TODO Auto-generated method stub

	}
}
