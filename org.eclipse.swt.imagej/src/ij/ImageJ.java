/*******************************************************************************
ImageJ is being developed, since 1997, by Wayne Rasband and numerous contributors
The original ImageJ is public domain software, see: https://github.com/imagej/ImageJ/blob/master/LICENSE.txt
 *******************************************************************************/
/*
/*******************************************************************************
 * SWT distribution of ImageJ.
 * Copyright (c) 2021 Lablicate GmbH.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 * Marcel Austenfeld - initial API and implementation
 *******************************************************************************/
package ij;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Panel;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.image.ImageObserver;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.DeviceData;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.osgi.framework.Bundle;

import ij.gui.GUI;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.ProgressBar;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.gui.Toolbar;
import ij.macro.Interpreter;
import ij.plugin.GelAnalyzer;
import ij.plugin.JavaProperties;
import ij.plugin.MacroInstaller;
import ij.plugin.Orthogonal_Views;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.ContrastAdjuster;
import ij.plugin.frame.Editor;
import ij.plugin.frame.RoiManager;
import ij.plugin.frame.ThresholdAdjuster;
import ij.text.TextWindow;
import ij.util.Tools;

/**
 * This frame is the main ImageJ class.
 * <p>
 * ImageJ is a work of the United States Government. It is in the public domain
 * and open source. There is no copyright. You are free to do anything you want
 * with this source but I like to get credit for my work and I would like you to
 * offer your changes to me so I can possibly add them to the "official"
 * version.
 * 
 * <pre>
The following command line options are recognized by ImageJ:

  "file-name"
     Opens a file
     Example 1: blobs.tif
     Example 2: /Users/wayne/images/blobs.tif
     Example 3: e81*.tif

  -macro path [arg]
     Runs a macro or script (JavaScript, BeanShell or Python), passing an
     optional string argument, which the macro or script can be retrieve
     using the getArgument() function. The macro or script is assumed to 
     be in the ImageJ/macros folder if 'path' is not a full directory path.
     Example 1: -macro analyze.ijm
     Example 2: -macro script.js /Users/wayne/images/stack1
     Example 2: -macro script.py '1.2 2.4 3.8'

  -batch path [arg]
    Runs a macro or script (JavaScript, BeanShell or Python) in
    batch (no GUI) mode, passing an optional argument.
    ImageJ exits when the macro finishes.

  -eval "macro code"
     Evaluates macro code
     Example 1: -eval "print('Hello, world');"
     Example 2: -eval "return getVersion();"

  -run command
     Runs an ImageJ menu command
     Example: -run "About ImageJ..."
     
  -ijpath path
     Specifies the path to the directory containing the plugins directory
     Example: -ijpath /Applications/ImageJ

  -port<n>
     Specifies the port ImageJ uses to determine if another instance is running
     Example 1: -port1 (use default port address + 1)
     Example 2: -port2 (use default port address + 2)
     Example 3: -port0 (don't check for another instance)

  -debug
     Runs ImageJ in debug mode
 * </pre>
 * 
 * @author Wayne Rasband (rasband@gmail.com)
 */
public class ImageJ implements ImageObserver, ShellListener, org.eclipse.swt.events.MouseListener,
		org.eclipse.swt.events.MouseMoveListener, SelectionListener, ActionListener, org.eclipse.swt.events.KeyListener,
		ItemListener, Runnable {

	/**
	 * Plugins should call IJ.getVersion() or IJ.getFullVersion() to get the version
	 * string.
	 */
	public static final String VERSION = "1.54n";
	public static final String BUILD = "4";
	public static org.eclipse.swt.graphics.Color backgroundColor = new org.eclipse.swt.graphics.Color(
			Display.getCurrent(), 237, 237, 237);
	/** SansSerif, 12-point, plain font. */
	public static final Font SansSerif12 = new Font("SansSerif", Font.PLAIN, 12);
	/** SansSerif, 14-point, plain font. */
	public static final Font SansSerif14 = new Font("SansSerif", Font.PLAIN, 14);
	/** Address of socket where Image accepts commands */
	public static org.eclipse.swt.graphics.Font SansSerif14Swt = new org.eclipse.swt.graphics.Font(Display.getDefault(),
			new FontData("SansSerif", 14, SWT.NORMAL));
	public static final int DEFAULT_PORT = 57294;
	/** Run as normal application. */
	public static final int STANDALONE = 0;
	/** Run embedded in another application. */
	public static final int EMBEDDED = 1;
	/** Run embedded and invisible in another application. */
	public static final int NO_SHOW = 2;
	/* General test mode for Eclipse and SWT! */
	public static int SWT_MODE = EMBEDDED;
	// public static boolean fitToParent = true;// Stretch to parent composite?
	/** Run as the ImageJ application. */
	public static final int IMAGEJ_APP = 3;
	/** Run ImageJ in debug mode. */
	public static final int DEBUG = 256;
	private static final String IJ_X = "ij.x", IJ_Y = "ij.y";
	private static int port = DEFAULT_PORT;
	private static String[] arguments;
	private Toolbar toolbar;
	private org.eclipse.swt.widgets.Composite statusBar;
	private ProgressBar progressBar;
	private Label statusLine;
	private boolean firstTime = true;
	@SuppressWarnings("removal")
	private java.applet.Applet applet; // null if not running as an applet
	private Vector classes = new Vector();
	public boolean exitWhenQuitting;
	private boolean quitting;
	private boolean quitMacro;
	private long keyPressedTime, actionPerformedTime;
	private String lastKeyCommand;
	public boolean embedded;
	private boolean windowClosed;
	private static String commandName;
	private static boolean batchMode;
	private static String file;
	boolean hotkey;
	private Display display;
	/** A dummy JPanel used to provide font metrics. */
	public static final Panel DUMMY_PANEL = new Panel();
	// protected Menu menuBar;
	public Shell shell;
	private org.eclipse.swt.graphics.Font font;
	volatile boolean closeFinally;
	private org.eclipse.swt.graphics.Image img;
	private Composite composite;

	/* The composite which embeds the toolbar (and status bar, etc.)! */
	public Composite getComposite() {

		return composite;
	}

	public org.eclipse.swt.graphics.Image getIconImage() {

		return img;
	}

	public Shell getShell() {

		return shell;
	}

	/* Compatibility methods since we have no Frame here! */
	public Rectangle getBounds() {

		AtomicReference<Rectangle> rec = new AtomicReference<Rectangle>();
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				rec.set(getShell().getBounds());
			}
		});
		return rec.get();
	}

	/** Creates a new ImageJ frame that runs as an application. */
	public ImageJ() {

		this(null, STANDALONE);
	}

	/**
	 * Creates a new ImageJ frame that runs as an application in the specified mode.
	 */
	public ImageJ(int mode) {

		this(null, mode);
	}

	/** Creates a new ImageJ frame that runs as an applet. */
	@SuppressWarnings("removal")
	public ImageJ(java.applet.Applet applet) {

		this(applet, STANDALONE);
	}
	/*
	 * public ImageJ(java.applet.Applet applet, int mode) { this(applet,
	 * STANDALONE); }
	 */

	/**
	 * If 'applet' is not null, creates a new ImageJ frame that runs as an applet.
	 * If 'mode' is ImageJ.EMBEDDED and 'applet is null, creates an embedded
	 * (non-standalone) version of ImageJ.
	 */
	@SuppressWarnings("removal")
	public ImageJ(java.applet.Applet applet, int mode) {

		Prefs.setHomeDir(getImageJPath(ImageJ.SWT_MODE));
		// System.out.println("Path set by constructor!" +
		// getImageJPath(ImageJ.SWT_MODE));
		// super(,SWT.NONE);
		// super("ImageJ");
		if ((mode & DEBUG) != 0)
			IJ.setDebugMode(true);
		mode = mode & 255;
		boolean useExceptionHandler = false;
		if (mode == IMAGEJ_APP) {
			mode = STANDALONE;
			useExceptionHandler = true;
		}
		if (IJ.debugMode)
			IJ.log("ImageJ starting in debug mode: " + mode);
		embedded = applet == null && (mode == EMBEDDED || mode == NO_SHOW);
		this.applet = applet;
		String err1 = Prefs.load(this, applet);
		Point loc = getPreferredLocation();
		// shell.setCursor(Cursor.getDefaultCursor()); // work-around for JDK 1.1.8 bug
		if (mode != NO_SHOW) {
			if (IJ.isWindows())
				try {
					setIcon();
				} catch (Exception e) {
				}
		}
		display = getDisplay();
		if (SWT_MODE == EMBEDDED) {
			shell = new Shell(display, SWT.ON_TOP | SWT.SHELL_TRIM | SWT.TOOL);
		} else {
			if (Prefs.alwaysOnTop) {
				shell = new Shell(display, SWT.ON_TOP | SWT.SHELL_TRIM | SWT.TOOL);
			} else {
				shell = new Shell(display);
			}
		}
		shell.setLayout(new FillLayout());
		composite = new Composite(shell, SWT.NONE);
		composite.setLayout(new GridLayout(1, true));
		// Menu menuBar = new Menu(shell, SWT.BAR);
		// createShellMenu(shell, menuBar);
		Menus m = new Menus(this, applet);
		String err2 = m.addMenuBar();
		m.installPopupMenu(this);
		m.installStartupMacroSet(); // add custom tools
		// shell.setMenuBar(menuBar);
		/* To do for SWT! */
		toolbar = new Toolbar(composite);
		toolbar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		GridData tollbar_canvas = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
		tollbar_canvas.heightHint = 32;
		toolbar.setLayoutData(tollbar_canvas);
		toolbar.addKeyListener(this);
		statusBar = new org.eclipse.swt.widgets.Composite(composite, SWT.NORMAL);
		statusBar.setLayout(new GridLayout(2, false));
		statusBar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		// statusBar.setForeground(ij.swt.Color.black);
		// statusBar.setBackground(backgroundColor);
		statusLine = new org.eclipse.swt.widgets.Label(statusBar, SWT.NORMAL);
		GridData gd_lblNewLabel = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
		gd_lblNewLabel.widthHint = 500;
		statusLine.setLayoutData(gd_lblNewLabel);
		statusLine.addKeyListener(this);
		statusLine.addMouseListener(this);
		progressBar = new ProgressBar(statusBar, (int) (ProgressBar.WIDTH), (int) (ProgressBar.HEIGHT));
		GridData gd_canvas = new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1);
		gd_canvas.heightHint = (int) (ProgressBar.HEIGHT);
		gd_canvas.widthHint = (int) (ProgressBar.WIDTH);
		progressBar.setLayoutData(gd_canvas);
		progressBar.addKeyListener(this);
		progressBar.addMouseListener(this);
		/* Set's the instance in ImageJ! */
		IJ.init(this, applet);
		if (mode != NO_SHOW) {
			try {
				setIcon();
			} catch (Exception e) {
				e.printStackTrace();
			}
			shell.setLocation(loc.x, loc.y);
			shell.setText("ImageJ");
			shell.addShellListener(this);
			shell.layout(true, true);
			shell.pack();
			Dimension tbSize = toolbar.getPreferredSize();
			shell.setSize(tbSize.width + 30, 130);
			if (applet == null)
				IJ.runPlugIn("ij.plugin.DragAndDrop", "");
		}
		if (err1 != null)
			IJ.error(err1);
		if (err2 != null) {
			IJ.error(err2);
			// IJ.runPlugIn("ij.plugin.ClassChecker", "");
		}
		if (IJ.isMacintosh() && applet == null) {
			try {
				if (IJ.javaVersion() > 8) // newer JREs use different drag-drop, about mechanism
					IJ.runPlugIn("ij.plugin.MacAdapter9", "");
				else
					IJ.runPlugIn("ij.plugin.MacAdapter", "");
			} catch (Throwable e) {
			}
		}
		if (!shell.getText().contains("Fiji") && useExceptionHandler) {
			Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());
			System.setProperty("sun.awt.exception.handler", ExceptionHandler.class.getName());
		}
		String str = m.getMacroCount() == 1 ? " macro" : " macros";
		configureProxy();
		if (applet == null)
			// loadCursors();
			(new ij.macro.StartupRunner()).run(batchMode); // run RunAtStartup and AutoRun macros
		IJ.showStatus(version() + m.getPluginCount() + " commands; " + m.getMacroCount() + str);
		// startSleak();
	}

	/*
	 * Sleak is a simple tool that monitors SWT graphics resources. Source:
	 * https://www.eclipse.org/articles/swt-design-2/Sleak.java.htm
	 * https://www.eclipse.org/articles/swt-design-2/sleak.htm
	 */
	public void startSleak() {

		DeviceData data = new DeviceData();
		data.tracking = true;
		Display.getCurrent().setData(data);
		// Enable Sleak for Debugging SWT resources!
		// Sleak sleak = new Sleak();
		// sleak.open();
	}

	public static Display getDisplay() {

		/*
		 * API information: Returns the default display. One is created (making the
		 * thread that invokes this method its user-interface thread) if it did not
		 * already exist.
		 */
		Display display = Display.getCurrent();
		// may be null if outside the UI thread
		if (display == null)
			display = Display.getDefault();
		return display;
	}

	/**
	 * Opens a file-open dialog.
	 * 
	 * @return a file path as a string from the file dialog.
	 */
	public static String openFile() {

		file = null;
		final Display display = Display.getDefault();
		display.syncExec(() -> {

			Shell s = new Shell(SWT.ON_TOP);
			FileDialog fd = new FileDialog(s, SWT.OPEN);
			fd.setText("Load");
			String[] filterExt = { "*.*" };
			fd.setFilterExtensions(filterExt);
			file = fd.open();

		});
		return file;
	}
	/*
	 * Not necessary. In SWT a crosshair cursor exists! private void loadCursors()
	 * {}
	 */

	void configureProxy() {

		if (Prefs.useSystemProxies) {
			try {
				System.setProperty("java.net.useSystemProxies", "true");
			} catch (Exception e) {
			}
		} else {
			String server = Prefs.get("proxy.server", null);
			if (server == null || server.equals(""))
				return;
			int port = (int) Prefs.get("proxy.port", 0);
			if (port == 0)
				return;
			Properties props = System.getProperties();
			props.put("proxySet", "true");
			props.put("http.proxyHost", server);
			props.put("http.proxyPort", "" + port);
			props.put("https.proxyHost", server);
			props.put("https.proxyPort", "" + port);
		}
		// new ProxySettings().logProperties();
	}

	void setIcon() throws Exception {

		if (SWT_MODE == EMBEDDED) {
			return;
		}
		URL url = this.getClass().getResource("/microscope.gif");
		if (url == null)
			return;
		img = ImageDescriptor.createFromURL(url).createImage();
		// org.eclipse.swt.graphics.Image img = createImage((ImageProducer)
		// url.getContent());
		if (img != null)
			shell.setImage(img);
	}

	public Point getPreferredLocation() {

		int ijX = Prefs.getInt(IJ_X, -99);
		int ijY = Prefs.getInt(IJ_Y, -99);
		java.awt.Rectangle maxBounds = GUI.getMaxWindowBounds();
		// System.out.println("getPreferredLoc1: "+ijX+" "+ijY+" "+maxBounds);
		if (ijX >= maxBounds.x && ijY >= maxBounds.y && ijX < (maxBounds.x + maxBounds.width - 75)
				&& ijY < (maxBounds.y + maxBounds.height - 75))
			return new Point(ijX, ijY);
		if (toolbar == null)
			return new Point(ijX, ijY);
		Dimension tbsize = toolbar.getPreferredSize();
		int ijWidth = tbsize.width + 10;
		double percent = maxBounds.width > 832 ? 0.8 : 0.9;
		ijX = (int) (percent * (maxBounds.width - ijWidth));
		if (ijX < 10)
			ijX = 10;
		return new Point(ijX, maxBounds.y);
	}

	void showStatus(String s) {

		Display.getDefault().syncExec(() -> {

			statusLine.setText(s);

		});
	}

	public ProgressBar getProgressBar() {

		return progressBar;
	}

	public org.eclipse.swt.widgets.Composite getStatusBar() {

		return statusBar;
	}

	public static String getStatusBarText() {

		ImageJ ij = IJ.getInstance();
		return ij != null ? ij.statusLine.getText() : "";
	}

	/** Starts executing a menu command in a separate thread. */
	void doCommand(String name) {

		new Executer(name, null);
	}

	public void runFilterPlugIn(Object theFilter, String cmd, String arg) {

		new PlugInFilterRunner(theFilter, cmd, arg);
	}

	public Object runUserPlugIn(String commandName, String className, String arg, boolean createNewLoader) {

		return IJ.runUserPlugIn(commandName, className, arg, createNewLoader);
	}

	/** Return the current list of modifier keys. */
	public static String modifiers(int flags) { // ?? needs to be moved

		String s = " [ ";
		if (flags == 0)
			return "";
		if ((flags & SWT.SHIFT) != 0)
			s += "Shift ";
		if ((flags & SWT.CONTROL) != 0)
			s += "Control ";
		if ((flags & SWT.COMMAND) != 0)
			s += "Meta ";
		if ((flags & SWT.ALT) != 0)
			s += "Alt ";
		s += "] ";
		return s;
	}

	@Override
	public void widgetSelected(SelectionEvent e) {

		if ((e.getSource() instanceof MenuItem)) {
			org.eclipse.swt.widgets.MenuItem item = (org.eclipse.swt.widgets.MenuItem) e.getSource();
			if (item.getStyle() == SWT.CHECK) {
				itemStateChanged(e);
			} else {
				actionPerformed(e);
			}
		}
	}

	/** Handle menu events. */
	public void actionPerformed(SelectionEvent e) {

		if ((e.getSource() instanceof MenuItem)) {
			org.eclipse.swt.widgets.MenuItem item = (org.eclipse.swt.widgets.MenuItem) e.getSource();
			String cmd;
			Object data = item.getData("ActionCommand");
			if (data != null) {
				cmd = (String) data;
			} else {
				cmd = item.getText();
			}
			// System.out.println("Command: " + cmd);
			/* Changed for SWT */
			/*
			 * Frame frame = WindowManager.getFrontWindow(); if (frame!=null && (frame
			 * instanceof Fitter)) { ((Fitter)frame).actionPerformed(e); return; }
			 */
			commandName = cmd;
			ImagePlus imp = null;
			/* Changed for SWT. To Do! */
			/*
			 * if (item.getParent() == Menus.getOpenRecentMenu().getLabel()) { new
			 * RecentOpener(cmd); // open image in separate thread return; } else if
			 * (item.getParent() == Menus.getPopupMenu()) { Object parent =
			 * Menus.getPopupMenu().getParent(); if (parent instanceof ImageCanvas) imp =
			 * ((ImageCanvas) parent).getImage(); }
			 */
			// int flags = e.getModifiers();
			hotkey = false;
			actionPerformedTime = System.currentTimeMillis();
			long ellapsedTime = actionPerformedTime - keyPressedTime;
			if (cmd != null && (ellapsedTime >= 200L || !cmd.equals(lastKeyCommand))) {
				if ((e.stateMask & SWT.ALT) != 0)
					IJ.setKeyDown(SWT.ALT);
				if ((e.stateMask & SWT.SHIFT) != 0)
					IJ.setKeyDown(SWT.SHIFT);
				new Executer(cmd, imp);
			}
			lastKeyCommand = null;
			if (IJ.debugMode)
				IJ.log("actionPerformed: time=" + ellapsedTime + ", " + e);
		}
	}

	/** Handles CheckboxMenuItem state changes. */
	public void itemStateChanged(SelectionEvent e) {

		org.eclipse.swt.widgets.MenuItem item = (org.eclipse.swt.widgets.MenuItem) e.widget;
		org.eclipse.swt.widgets.Menu parent = (org.eclipse.swt.widgets.Menu) item.getParent();
		String cmd;
		Object data = item.getData("ActionCommand");
		if (data != null) {
			cmd = (String) data;
		} else {
			cmd = item.getText();
		}
		if ("Autorun Examples".equals(cmd)) { // Examples>Autorun Examples
			Prefs.autoRunExamples = item.getSelection();
		} else if ((org.eclipse.swt.widgets.Menu) parent == Menus.window) {
			WindowManager.activateWindow(cmd, item);
		} else
			doCommand(cmd);
	}

	public String getInfo() {

		return version() + System.getProperty("os.name") + " " + System.getProperty("os.version") + "; "
				+ IJ.freeMemory();
	}

	private String version() {

		return "ImageJ " + VERSION + BUILD + "; " + "Java " + System.getProperty("java.version")
				+ (IJ.is64Bit() ? " [64-bit]; " : " [32-bit]; ");
	}

	public void keyPressed(org.eclipse.swt.events.KeyEvent e) {

		// System.out.println(e);
		// KeyEvent e = SWTUtils.toAwtKeyEvent(evt);
		// if (e.isConsumed())
		// return;
		int keyCode = e.keyCode;
		IJ.setKeyDown(keyCode);
		hotkey = false;
		if (keyCode == SWT.CTRL || keyCode == SWT.SHIFT)
			return;
		char keyChar = e.character;
		int flags = e.stateMask;
		// if (IJ.debugMode)
		// IJ.log("keyPressed: code=" + keyCode + " (" + KeyEvent.getKeyText(keyCode) +
		// "), char=\"" + keyChar + "\" ("
		// + (int) keyChar + "), flags=" + KeyEvent.getKeyModifiersText(flags));
		boolean shift = (flags & SWT.SHIFT) != 0;
		boolean control = (flags & SWT.CTRL) != 0;
		boolean alt = (flags & SWT.ALT) != 0;
		boolean meta = (flags & SWT.COMMAND) != 0;
		if (keyChar == 'h' && meta && IJ.isMacOSX())
			return; // Allow macOS to run ImageJ>Hide ImageJ command
		String cmd = null;
		ImagePlus imp = WindowManager.getCurrentImage();
		boolean isStack = (imp != null) && (imp.getStackSize() > 1);
		/* Detect return key for SWT */
		if (keyCode == SWT.CR || keyCode == SWT.LF) {
			keyChar = '\n';
		}
		if (imp != null && !meta && ((keyChar >= 32 && keyChar <= 255) || keyChar == '\b' || keyChar == '\n')) {
			Roi roi = imp.getRoi();
			if (roi != null && roi instanceof TextRoi) {
				if (imp.getOverlay() != null && (control || alt || meta)
						&& (keyCode == SWT.SPACE || keyCode == SWT.DEL)) {
					if (deleteOverlayRoi(imp))
						return;
				}
				if ((flags & SWT.COMMAND) != 0 && IJ.isMacOSX())
					return;
				if (alt) {
					switch (keyChar) {
					case 'u':
					case 'm':
						keyChar = IJ.micronSymbol;
						break;
					case 'A':
						keyChar = IJ.angstromSymbol;
						break;
					default:
					}
				}
				((TextRoi) roi).addChar(keyChar);
				return;
			}
		}
		// Handle one character macro shortcuts
		if (!control && !meta) {
			Hashtable macroShortcuts = Menus.getMacroShortcuts();
			if (macroShortcuts.size() > 0) {
				if (shift) {
					/*
					 * We cannot use the Uppercase here so e must calculate the right value (-32)!.
					 * See Menus method convertShortcutToCode!
					 */
					cmd = (String) macroShortcuts.get(Integer.valueOf(keyCode + 200));
				} else {
					cmd = (String) macroShortcuts.get(Integer.valueOf(keyCode));
				}
				if (cmd != null) {
					commandName = cmd;
					MacroInstaller.runMacroShortcut(cmd);
					return;
				}
			}
		}
		if (keyCode == SWT.SEPARATOR)
			keyCode = SWT.KEYPAD_DECIMAL;
		boolean functionKey = keyCode >= SWT.F1 && keyCode <= SWT.F12;
		boolean numPad = keyCode == SWT.KEYPAD_DIVIDE || keyCode == SWT.KEYPAD_MULTIPLY || keyCode == SWT.KEYPAD_DECIMAL
				|| (keyCode >= SWT.KEYPAD_0 && keyCode <= SWT.KEYPAD_9);
		if ((!Prefs.requireControlKey || control || meta || functionKey || numPad) && keyChar != '+') {
			Hashtable shortcuts = Menus.getShortcuts();
			if (shift && !functionKey) {
				/*
				 * We cannot use the Uppercase here so e must calculate the right value (-32)!.
				 * See Menus method convertShortcutToCode!
				 */
				cmd = (String) shortcuts.get(Integer.valueOf(keyCode + 200));
			} else {
				cmd = (String) shortcuts.get(Integer.valueOf(keyCode));
			}
		}
		if (cmd == null) {
			switch (keyChar) {
			case '<':
			case ',':
				if (isStack)
					cmd = "Previous Slice [<]";
				break;
			case '>':
			case '.':
			case ';':
				if (isStack)
					cmd = "Next Slice [>]";
				break;
			case '+':
			case '=':
				cmd = "In [+]";
				break;
			case '-':
				cmd = "Out [-]";
				break;
			case '/':
				cmd = "Reslice [/]...";
				break;
			default:
			}
		}
		if (cmd == null) {
			switch (keyCode) {
			case SWT.TAB:
				WindowManager.putBehind();
				return;
			// case SWT.SPACE:
			case 32:// Space?
				return;
			case SWT.DEL:
				if (!(shift || control || alt || meta)) {
					if (deleteOverlayRoi(imp))
						return;
					if (imp != null && imp.getOverlay() != null && imp == GelAnalyzer.getGelImage())
						return;
					cmd = "Clear";
					hotkey = true;
				}
				break;
			// case KeyEvent.VK_BACK_SLASH: cmd=IJ.altKeyDown()?"Animation
			// Options...":"Start Animation"; break;
			case SWT.KEYPAD_EQUAL:
				cmd = "In [+]";
				break;
			case SWT.KEYPAD_SUBTRACT:
				cmd = "Out [-]";
				break;
			// case SWT.VK_SLASH:
			case 0xbf:
				cmd = "Reslice [/]...";
				break;
			// case SWT.C:
			case 0xbc:
				if (isStack)
					cmd = "Previous Slice [<]";
				break;
			// case SWT.VK_PERIOD:
			case 0xbe:
				if (isStack)
					cmd = "Next Slice [>]";
				break;
			case SWT.LEFT:
			case SWT.RIGHT:
			case SWT.UP:
			case SWT.DOWN: // arrow keys
				if (imp == null)
					return;
				Roi roi = imp.getRoi();
				if (shift && imp == Orthogonal_Views.getImage())
					return;
				if (IJ.isMacOSX() && IJ.isJava18()) {
					RoiManager rm = RoiManager.getInstance();
					boolean rmActive = rm != null;
					if (rmActive && (keyCode == SWT.DOWN || keyCode == SWT.UP))
						rm.shell.redraw();
				}
				boolean stackKey = imp.getStackSize() > 1 && (roi == null || shift);
				boolean zoomKey = roi == null || shift || control;
				if (stackKey && keyCode == SWT.RIGHT)
					cmd = "Next Slice [>]";
				else if (stackKey && keyCode == SWT.LEFT)
					cmd = "Previous Slice [<]";
				else if (zoomKey && keyCode == SWT.DOWN && !ignoreArrowKeys(imp)
						&& Toolbar.getToolId() < Toolbar.SPARE6)
					cmd = "Out [-]";
				else if (zoomKey && keyCode == SWT.UP && !ignoreArrowKeys(imp) && Toolbar.getToolId() < Toolbar.SPARE6)
					cmd = "In [+]";
				else if (roi != null) {
					if ((flags & SWT.ALT) != 0 || (flags & SWT.CTRL) != 0)
						roi.nudgeCorner(keyCode);
					else
						roi.nudge(keyCode);
					return;
				}
				break;
			case SWT.ESC:
				abortPluginOrMacro(imp);
				return;
			case SWT.CR:
				WindowManager.toFront(this);
				return;
			default:
				break;
			}
		}
		if (cmd != null && !cmd.equals("")) {
			commandName = cmd;
			if (!control && !meta && (cmd.equals("Fill") || cmd.equals("Draw")))
				hotkey = true;
			if (cmd.charAt(0) == MacroInstaller.commandPrefix)
				MacroInstaller.runMacroShortcut(cmd);
			else {
				doCommand(cmd);
				keyPressedTime = System.currentTimeMillis();
				lastKeyCommand = cmd;
			}
		}
	}

	private boolean deleteOverlayRoi(ImagePlus imp) {

		if (imp == null)
			return false;
		Overlay overlay = null;
		ImageCanvas ic = imp.getCanvas();
		if (ic != null)
			overlay = ic.getShowAllList();
		if (overlay == null)
			overlay = imp.getOverlay();
		if (overlay == null)
			return false;
		Roi roi = imp.getRoi();
		for (int i = 0; i < overlay.size(); i++) {
			Roi roi2 = overlay.get(i);
			if (roi2 == roi) {
				overlay.remove(i);
				imp.deleteRoi();
				ic = imp.getCanvas();
				if (ic != null)
					ic.roiManagerSelect(roi, true);
				return true;
			}
		}
		return false;
	}

	private boolean ignoreArrowKeys(ImagePlus imp) {

		// Channels dialog?
		/*
		 * Window window = WindowManager.getActiveWindow(); title =
		 * window!=null&&(window instanceof Dialog)?((Dialog)window).getTitle():null; if
		 * (title!=null && title.equals("Channels")) return true;
		 */
		/*
		 * Frame frame = WindowManager.getFrontWindow(); String title =
		 * frame!=null?frame.getTitle():null; if (title!=null &&
		 * title.equals("ROI Manager")) return true; // Control Panel? if (frame!=null
		 * && frame instanceof javax.swing.JFrame) return true; ImageWindow win =
		 * imp.getWindow(); // LOCI Data Browser window? if (imp.getStackSize()>1 &&
		 * win!=null && win.getClass().getName().startsWith("loci")) return true;
		 */
		return false;
	}

	public void keyDown(org.eclipse.swt.events.KeyEvent e) {

		char keyChar = e.character;
		// if (IJ.debugMode) IJ.log("keyTyped: char=\"" + keyChar + "\" (" +
		// (int)keyChar
		// + "), flags= "+Integer.toHexString(flags)+ "
		// ("+KeyEvent.getKeyModifiersText(flags)+")");
		if (keyChar == '\\' || keyChar == 171 || keyChar == 223) {
			if (((e.stateMask & SWT.ALT) != 0))
				doCommand("Animation Options...");
			else
				doCommand("Start Animation [\\]");
		}
	}
	/*
	 * public void keyReleased(KeyEvent e) { IJ.setKeyUp(e.getKeyCode()); }
	 */

	/** called when escape pressed */
	void abortPluginOrMacro(ImagePlus imp) {

		if (imp != null) {
			ImageWindow win = imp.getWindow();
			if (win != null) {
				Roi roi = imp.getRoi();
				if (roi != null && roi.getState() != Roi.NORMAL) {
					roi.abortModification(imp);
					return;
				} else {
					win.running = false;
					win.running2 = false;
				}
			}
		}
		Macro.abort();
		Interpreter.abort();
		if (Interpreter.getInstance() != null)
			IJ.beep();
	}

	public void windowClosing(ShellEvent e) {

		if (closeFinally) {
			e.doit = true;
			closeFinally = false;
			return;
		}
		/* Avoid closing the Shell! */
		e.doit = false;
		if (Executer.getListenerCount() > 0)
			doCommand("Quit");
		else {
			quit();
			windowClosed = true;
		}
	}

	public void windowActivated(WindowEvent e) {

		/*
		 * if (IJ.isMacintosh() && !quitting) { IJ.wait(10); // may be needed for Java
		 * 1.4 on OS X MenuBar mb = Menus.getMenuBar(); if (mb!=null && mb!=getMenuBar()
		 * && !IJ.isMacro()) { setMenuBar(mb); Menus.setMenuBarCount++; if
		 * (IJ.debugMode) IJ.log("setMenuBar: " + Menus.setMenuBarCount); } }
		 */
	}

	public void windowClosed(WindowEvent e) {

	}

	public void windowDeactivated(WindowEvent e) {

	}

	public void windowDeiconified(WindowEvent e) {

	}

	public void windowIconified(WindowEvent e) {

	}

	public void windowOpened(WindowEvent e) {

	}

	/**
	 * Adds the specified class to a Vector to keep it from being garbage collected,
	 * causing static fields to be reset.
	 */
	public void register(Class c) {

		if (!classes.contains(c))
			classes.addElement(c);
	}

	/** Called by ImageJ when the user selects Quit. */
	public void quit() {

		quitMacro = IJ.macroRunning();
		Thread thread = new Thread(this, "Quit");
		thread.setPriority(Thread.NORM_PRIORITY);
		thread.start();
		IJ.wait(10);
	}

	/** Returns true if ImageJ is exiting. */
	public boolean quitting() {

		return quitting;
	}

	/**
	 * Returns true if ImageJ is quitting as a result of a run("Quit") macro call.
	 */
	public boolean quittingViaMacro() {

		return quitting && quitMacro;
	}

	/** Called once when ImageJ quits. */
	public void savePreferences(Properties prefs) {

		org.eclipse.swt.graphics.Point location = shell.getLocation();
		Point loc = new Point(location.x, location.y);
		prefs.put(IJ_X, Integer.toString(loc.x));
		prefs.put(IJ_Y, Integer.toString(loc.y));
	}

	public static String getImageJPath(int mode) {

		String path;
		if (ImageJ.EMBEDDED == mode) {
			Bundle bundle = Platform.getBundle("org.eclipse.swt.imagej");
			URL locationUrl = FileLocator.find(bundle, new org.eclipse.core.runtime.Path("/"), null);
			URL fileUrl = null;
			try {
				fileUrl = FileLocator.toFileURL(locationUrl);
			} catch (IOException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			path = new File(fileUrl.getFile()).toString();
		} else {
			path = Path.of("").toAbsolutePath().toString();
		}
		return path;
	}

	public static void main(String args[]) {

		/* Set the ImageJ path here! */
		// Prefs.setHomeDir(Path.of("").toAbsolutePath().toString());
		/*
		 * When we call this application by the main method we choose the Standalone
		 * attribute!
		 */
		SWT_MODE = STANDALONE;
		/* Don't use here the anisotropic view! */
		// fitToParent = false;
		Prefs.setHomeDir(getImageJPath(ImageJ.SWT_MODE));
		// System.out.println("Path " + getImageJPath(ImageJ.SWT_MODE));
		boolean noGUI = false;
		int mode = IMAGEJ_APP;
		arguments = args;
		int nArgs = args != null ? args.length : 0;
		boolean commandLine = false;
		for (int i = 0; i < nArgs; i++) {
			String arg = args[i];
			if (arg == null)
				continue;
			if (arg.startsWith("-batch")) {
				noGUI = true;
				batchMode = true;
			} else if (arg.startsWith("-macro") || arg.endsWith(".ijm") || arg.endsWith(".txt"))
				batchMode = true;
			else if (arg.startsWith("-debug"))
				IJ.setDebugMode(true);
			else if (arg.startsWith("-ijpath") && i + 1 < nArgs) {
				if (IJ.debugMode)
					IJ.log("-ijpath: " + args[i + 1]);
				Prefs.setHomeDir(args[i + 1]);
				commandLine = true;
				args[i + 1] = null;
			} else if (arg.startsWith("-port")) {
				int delta = (int) Tools.parseDouble(arg.substring(5, arg.length()), 0.0);
				commandLine = true;
				if (delta == 0)
					mode = EMBEDDED;
				else if (delta > 0 && DEFAULT_PORT + delta < 65536)
					port = DEFAULT_PORT + delta;
			}
		}
		// If existing ImageJ instance, pass arguments to it and quit.
		boolean passArgs = (mode == IMAGEJ_APP || mode == STANDALONE) && !noGUI;
		if (IJ.isMacOSX() && !commandLine)
			passArgs = false;
		if (passArgs && isRunning(args))
			return;
		ImageJ ij = IJ.getInstance();
		/* Changed for SWT no */
		// if (!noGUI && (ij==null || (ij!=null && !ij.isShowing()))) {
		if (!noGUI && (ij == null || (ij != null))) {
			ij = new ImageJ(null, mode);
			ij.exitWhenQuitting = true;
		} else if (batchMode && noGUI)
			Prefs.load(null, null);
		int macros = 0;
		for (int i = 0; i < nArgs; i++) {
			String arg = args[i];
			if (arg == null)
				continue;
			if (arg.startsWith("-")) {
				if ((arg.startsWith("-macro") || arg.startsWith("-batch")) && i + 1 < nArgs) {
					String arg2 = i + 2 < nArgs ? args[i + 2] : null;
					Prefs.commandLineMacro = true;
					if (noGUI && args[i + 1].endsWith(".js"))
						Interpreter.batchMode = true;
					IJ.runMacroFile(args[i + 1], arg2);
					break;
				} else if (arg.startsWith("-eval") && i + 1 < nArgs) {
					String rtn = IJ.runMacro(args[i + 1]);
					if (rtn != null)
						System.out.print(rtn);
					args[i + 1] = null;
				} else if (arg.startsWith("-run") && i + 1 < nArgs) {
					IJ.run(args[i + 1]);
					args[i + 1] = null;
				}
			} else if (macros == 0 && (arg.endsWith(".ijm") || arg.endsWith(".txt"))) {
				IJ.runMacroFile(arg);
				macros++;
			} else if (arg.length() > 0 && arg.indexOf("ij.ImageJ") == -1) {
				File file = new File(arg);
				IJ.open(file.getAbsolutePath());
			}
		}
		if (IJ.debugMode && IJ.getInstance() == null && !GraphicsEnvironment.isHeadless())
			new JavaProperties().run("");
		if (noGUI)
			System.exit(0);
		/*
		 * Startup the shell at the end to not block the main method execution, see
		 * ij.exitWhenQuitting = true - above!
		 */
		if (mode != NO_SHOW) {
			ij.getShell().open();
			Display display = ij.getDisplay();
			while (!display.isDisposed()) {
				// ===================================================
				// Wrap each event dispatch in an exception handler
				// so that if any event causes an exception it does
				// not break the main UI loop
				// ===================================================
				try {
					if (!display.readAndDispatch()) {
						display.sleep();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	// Is there another instance of ImageJ? If so, send it the arguments and quit.
	static boolean isRunning(String args[]) {

		return OtherInstance.sendArguments(args);
	}

	/**
	 * Returns the port that ImageJ checks on startup to see if another instance is
	 * running.
	 * 
	 * @see ij.OtherInstance
	 */
	public static int getPort() {

		return port;
	}

	/** Returns the command line arguments passed to ImageJ. */
	public static String[] getArgs() {

		return arguments;
	}

	/** ImageJ calls System.exit() when qutting when 'exitWhenQuitting' is true. */
	public void exitWhenQuitting(boolean ewq) {

		exitWhenQuitting = ewq;
	}

	/** Quit using a separate thread, hopefully avoiding thread deadlocks. */
	public void run() {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				quitting = true;
				boolean changes = false;
				int[] wList = WindowManager.getIDList();
				if (wList != null) {
					for (int i = 0; i < wList.length; i++) {
						ImagePlus imp = WindowManager.getImage(wList[i]);
						if (imp != null && imp.changes == true) {
							changes = true;
							break;
						}
					}
				}
				Object[] frames = WindowManager.getNonImageWindows();
				if (frames != null) {
					for (int i = 0; i < frames.length; i++) {
						if (frames[i] != null && (frames[i] instanceof Editor)) {
							if (((Editor) frames[i]).fileChanged()) {
								changes = true;
								break;
							}
						}
					}
				}
				/* If we use the ImageJ shell embedded we avoid closing here! */
				if (SWT_MODE == EMBEDDED) {
					closeFinally = false;
					return;
				}
				if (windowClosed && !changes && Menus.window.getItemCount() > Menus.WINDOW_MENU_ITEMS
						&& !(IJ.macroRunning() && WindowManager.getImageCount() == 0)) {
					/* Changed for SWT! */
					// GenericDialog gd = new GenericDialog("ImageJ", this);
					GenericDialog gd = new GenericDialog("ImageJ");
					gd.addMessage("Are you sure you want to quit ImageJ?");
					gd.showDialog();
					quitting = !gd.wasCanceled();
					windowClosed = false;
				}
				if (!quitting) {
					closeFinally = false;
					return;
				}
				if (!WindowManager.closeAllWindows()) {
					closeFinally = false;
					quitting = false;
					return;
				}
				if (applet == null) {
					saveWindowLocations();
					Prefs.set(ImageWindow.LOC_KEY, null); // don't save image window location
					Prefs.savePreferences();
				}
				IJ.cleanup();
				/* Will set e.doit to true! */
				closeFinally = true;
				shell.close();
				if (exitWhenQuitting)
					System.exit(0);
			}
		});
	}

	void saveWindowLocations() {

		Object win = WindowManager.getWindow("B&C");
		if (win != null) {
			ij.plugin.frame.swt.WindowSwt winSwt = (ij.plugin.frame.swt.WindowSwt) win;
			Shell shell = winSwt.getShell();
			Prefs.saveLocation(ContrastAdjuster.LOC_KEY, shell.getLocation());
		}
		win = WindowManager.getWindow("Threshold");
		if (win != null) {
			ij.plugin.frame.swt.WindowSwt winSwt = (ij.plugin.frame.swt.WindowSwt) win;
			Shell shell = winSwt.getShell();
			Prefs.saveLocation(ThresholdAdjuster.LOC_KEY, shell.getLocation());
		}
		win = WindowManager.getWindow("Results");
		if (win != null) {
			ij.plugin.frame.swt.WindowSwt winSwt = (ij.plugin.frame.swt.WindowSwt) win;
			Shell shell = winSwt.getShell();
			Prefs.saveLocation(TextWindow.LOC_KEY, shell.getLocation());
			org.eclipse.swt.graphics.Point d = shell.getSize();
			Prefs.set(TextWindow.WIDTH_KEY, d.x);
			Prefs.set(TextWindow.HEIGHT_KEY, d.y);
		}
		win = WindowManager.getWindow("Log");
		if (win != null) {
			ij.plugin.frame.swt.WindowSwt winSwt = (ij.plugin.frame.swt.WindowSwt) win;
			Shell shell = winSwt.getShell();
			Prefs.saveLocation(TextWindow.LOG_LOC_KEY, shell.getLocation());
			org.eclipse.swt.graphics.Point d = shell.getSize();
			Prefs.set(TextWindow.LOG_WIDTH_KEY, d.x);
			Prefs.set(TextWindow.LOG_HEIGHT_KEY, d.y);
		}
		win = WindowManager.getWindow("ROI Manager");
		if (win != null) {
			ij.plugin.frame.swt.WindowSwt winSwt = (ij.plugin.frame.swt.WindowSwt) win;
			Shell shell = winSwt.getShell();
			Prefs.saveLocation(RoiManager.LOC_KEY, shell.getLocation());
		}
	}

	public static String getCommandName() {

		return commandName != null ? commandName : "null";
	}

	public static void setCommandName(String name) {

		commandName = name;
	}

	public void resize() {

		double scale = Prefs.getGuiScale();
		toolbar.init();
		org.eclipse.swt.graphics.Font font = new org.eclipse.swt.graphics.Font(Display.getDefault(),
				new FontData("SansSerif", 13, SWT.NORMAL));
		statusLine.setFont(font);
		progressBar.init((int) (ProgressBar.WIDTH * scale), (int) (ProgressBar.HEIGHT));
		shell.layout();
	}

	/** Handles exceptions on the EDT. */
	public static class ExceptionHandler implements Thread.UncaughtExceptionHandler {

		// for EDT exceptions
		public void handle(Throwable thrown) {

			handleException(Thread.currentThread().getName(), thrown);
		}

		// for other uncaught exceptions
		public void uncaughtException(Thread thread, Throwable thrown) {

			handleException(thread.getName(), thrown);
		}

		protected void handleException(String tname, Throwable e) {

			if (Macro.MACRO_CANCELED.equals(e.getMessage()))
				return;
			CharArrayWriter caw = new CharArrayWriter();
			PrintWriter pw = new PrintWriter(caw);
			e.printStackTrace(pw);
			String s = caw.toString();
			if (s != null && s.contains("ij.")) {
				if (IJ.getInstance() != null)
					s = IJ.getInstance().getInfo() + "\n" + s;
				IJ.log(s);
			}
		}
	} // inner class ExceptionHandler

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseMove(org.eclipse.swt.events.MouseEvent e) {
		// TODO Auto-generated method stub

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

		Undo.reset();
		if (!Prefs.noClickToGC)
			System.gc();
		IJ.showStatus(version() + IJ.freeMemory());
		if (IJ.debugMode)
			IJ.log("Windows: " + WindowManager.getWindowCount());
	}

	@Override
	public void keyReleased(org.eclipse.swt.events.KeyEvent evt) {

		// KeyEvent e = SWTUtils.toAwtKeyEvent(evt);
		IJ.setKeyUp(evt.keyCode);
	}

	public org.eclipse.swt.graphics.Font getFont() {

		return font;
	}

	/*
	 * For SWT compatibility! Not used at the moment. Called by
	 * ImagePlus.setImage()!
	 */
	public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {

		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellActivated(ShellEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellClosed(ShellEvent e) {

		windowClosing(e);
	}

	@Override
	public void shellDeactivated(ShellEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellDeiconified(ShellEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellIconified(ShellEvent e) {
		// TODO Auto-generated method stub

	}
}
