package ij.text;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import ij.IJ;
import ij.IJEventListener;
import ij.ImageJ;
import ij.Menus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.YesNoCancelDialog;
import ij.io.OpenDialog;
import ij.macro.Interpreter;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.swt.WindowSwt;

/**
 * Uses a TextPanel to displays text in a window.
 * 
 * @see TextPanel
 */
public class TextWindow implements WindowSwt, SelectionListener, ShellListener, org.eclipse.swt.events.FocusListener {

	public static final String LOC_KEY = "results.loc";
	public static final String WIDTH_KEY = "results.width";
	public static final String HEIGHT_KEY = "results.height";
	public static final String LOG_LOC_KEY = "log.loc";
	public static final String LOG_WIDTH_KEY = "log.width";
	public static final String LOG_HEIGHT_KEY = "log.height";
	public static final String DEBUG_LOC_KEY = "debug.loc";
	static final String FONT_SIZE = "tw.font.size";
	static final String FONT_ANTI = "tw.font.anti";
	TextPanel textPanel;
	MenuItem monospacedButton, antialiasedButton;
	int[] sizes = {9, 10, 11, 12, 13, 14, 16, 18, 20, 24, 36, 48, 60, 72};
	int fontSize = (int)Prefs.get(FONT_SIZE, 6);
	org.eclipse.swt.widgets.Menu mb;
	private Shell shell;
	protected String title;
	protected boolean isVisible;
	private boolean isResultsTable;
	private boolean okayToClose;
	private static org.eclipse.swt.graphics.Font font;
	private static boolean monospaced;

	public Shell getShell() {

		return shell;
	}

	/**
	 * Opens a new single-column text window.
	 * 
	 * @param title
	 *            the title of the window
	 * @param text
	 *            the text initially displayed in the window
	 * @param width
	 *            the width of the window in pixels
	 * @param height
	 *            the height of the window in pixels
	 */
	public TextWindow(String title, String text, int width, int height) {

		this(title, "", text, width, height);
	}

	/**
	 * Opens a new multi-column text window.
	 * 
	 * @param title
	 *            title of the window
	 * @param headings
	 *            the tab-delimited column headings
	 * @param text
	 *            text initially displayed in the window
	 * @param width
	 *            width of the window in pixels
	 * @param height
	 *            height of the window in pixels
	 */
	public TextWindow(String title, String headings, String text, int width, int height) {

		// super(title);
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				shell = new Shell(Display.getDefault(), SWT.MODELESS | SWT.DIALOG_TRIM | SWT.RESIZE);
				shell.setLayout(new ij.layout.BorderLayout());
				shell.setText(title);
				shell.setText(title);
				textPanel = new TextPanel(TextWindow.this, shell, title);
				textPanel.setColumnHeadings(headings);
				if(text != null && !text.equals(""))
					textPanel.append(text);
				create(title, textPanel, width, height);
			}
		});
	}

	/**
	 * Opens a new multi-column text window.
	 * 
	 * @param title
	 *            title of the window
	 * @param headings
	 *            tab-delimited column headings
	 * @param text
	 *            ArrayList containing the text to be displayed in the window
	 * @param width
	 *            width of the window in pixels
	 * @param height
	 *            height of the window in pixels
	 */
	public TextWindow(String title, String headings, ArrayList text, int width, int height) {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				// super(title);
				shell = new Shell(Display.getDefault(), SWT.MODELESS | SWT.DIALOG_TRIM | SWT.RESIZE);
				shell.setLayout(new ij.layout.BorderLayout());
				setTitle(title);
				textPanel = new TextPanel(TextWindow.this, shell, title);
				textPanel.setColumnHeadings(headings);
				if(text != null)
					textPanel.append(text);
				create(title, textPanel, width, height);
			}
		});
		WindowManager.setWindow(this);
	}

	private void create(String title, TextPanel textPanel, int width, int height) {

		// enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		// if (IJ.isLinux()) setBackground(ImageJ.backgroundColor);
		// add("Center", textPanel);
		shell.addKeyListener(textPanel);
		shell.addShellListener(TextWindow.this);
		ImageJ ij = IJ.getInstance();
		if(ij != null) {
			/* Changed for SWT to do! */
			/*
			 * textPanel.addKeyListener(ij); if (!IJ.isMacOSX()) { Image img =
			 * ij.getIconImage(); if (img!=null) try {setIconImage(img);} catch (Exception
			 * e) {} }
			 */
		}
		shell.addFocusListener(TextWindow.this);
		String title2 = getTitle();
		isResultsTable = title2.equals("Results");
		if(!isResultsTable && title2.endsWith("(Results)")) {
			isResultsTable = true;
			title2 = title2.substring(0, title2.length() - 9);
			setTitle(title2);
			textPanel.title = title2;
		}
		addMenuBar();
		setFont();
		/* Changed for SWT! */
		WindowManager.addWindow(TextWindow.this);
		org.eclipse.swt.graphics.Point loc = null;
		int w = 0, h = 0;
		if(title.equals("Results")) {
			loc = Prefs.getLocation(LOC_KEY, null);
			w = (int)Prefs.get(WIDTH_KEY, 0.0);
			h = (int)Prefs.get(HEIGHT_KEY, 0.0);
		} else if(title.equals("Log")) {
			loc = Prefs.getLocation(LOG_LOC_KEY, null);
			w = (int)Prefs.get(LOG_WIDTH_KEY, 0.0);
			h = (int)Prefs.get(LOG_HEIGHT_KEY, 0.0);
		} else if(title.equals("Debug")) {
			loc = Prefs.getLocation(DEBUG_LOC_KEY, null);
			w = width;
			h = height;
		}
		if(loc != null && w > 0 && h > 0) {
			shell.setSize(w, h);
			shell.setLocation(loc);
		} else {
			shell.setSize(width, height);
			if(!IJ.debugMode)
				GUI.centerOnImageJScreen(shell);
		}
		shell.setVisible(true);
	}

	/**
	 * Opens a new text window containing the contents of a text file.
	 * 
	 * @param path
	 *            the path to the text file
	 * @param width
	 *            the width of the window in pixels
	 * @param height
	 *            the height of the window in pixels
	 */
	public TextWindow(String path, int width, int height) {

		// super("");
		shell = new Shell(Display.getDefault(), SWT.NORMAL);
		shell.setLayout(new FillLayout());
		shell.setText("");
		// enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		textPanel = new TextPanel(this, shell);
		/* Changed for SWT */
		textPanel.addKeyListener(IJ.getInstance());
		// add("Center", textPanel);
		if(openFile(path)) {
			// Changed for SWT!
			WindowManager.addWindow(this);
			shell.setSize(width, height);
			shell.setVisible(true);
			WindowManager.setWindow(this);
		} else
			close();
	}

	void addMenuBar() {

		mb = new org.eclipse.swt.widgets.Menu(shell, SWT.BAR);
		if(Menus.getFontSize() != 0) {
			/* Necessary for SWT? */
			// mb.setFont(Menus.getFont());
		}
		// org.eclipse.swt.widgets.Menu m = new org.eclipse.swt.widgets.Menu("File");
		org.eclipse.swt.widgets.MenuItem m = new org.eclipse.swt.widgets.MenuItem(mb, SWT.CASCADE);
		m.setText("&File");
		final org.eclipse.swt.widgets.Menu filemenu = new org.eclipse.swt.widgets.Menu(shell, SWT.DROP_DOWN);
		m.setMenu(filemenu);
		org.eclipse.swt.widgets.MenuItem mit = new org.eclipse.swt.widgets.MenuItem(filemenu, SWT.PUSH);
		mit.setText("Save As...");
		mit.setAccelerator('s');
		mit.addSelectionListener(this);
		if(isResultsTable) {
			org.eclipse.swt.widgets.MenuItem renameItem = new org.eclipse.swt.widgets.MenuItem(filemenu, SWT.PUSH);
			renameItem.setText("Rename...");
			renameItem.addSelectionListener(this);
			org.eclipse.swt.widgets.MenuItem duplicateItem = new org.eclipse.swt.widgets.MenuItem(filemenu, SWT.PUSH);
			duplicateItem.setText("Duplicate...");
			duplicateItem.addSelectionListener(this);
		}
		// mb.add(m);
		/* Changed for SWT. We create it directly in the TextPanel! */
		// textPanel.fileMenu = filemenu;
		// m = new Menu("Edit");
		org.eclipse.swt.widgets.MenuItem edit = new org.eclipse.swt.widgets.MenuItem(mb, SWT.CASCADE);
		edit.setText("Edit");
		final org.eclipse.swt.widgets.Menu editmenu = new org.eclipse.swt.widgets.Menu(shell, SWT.DROP_DOWN);
		edit.setMenu(editmenu);
		edit.addSelectionListener(this);
		org.eclipse.swt.widgets.MenuItem cutItem = new org.eclipse.swt.widgets.MenuItem(editmenu, SWT.PUSH);
		cutItem.setText("Cut");
		cutItem.setAccelerator('x');
		cutItem.addSelectionListener(this);
		org.eclipse.swt.widgets.MenuItem copyItem = new org.eclipse.swt.widgets.MenuItem(editmenu, SWT.PUSH);
		copyItem.setText("Copy");
		copyItem.setAccelerator('c');
		copyItem.addSelectionListener(this);
		// m.add(new org.eclipse.swt.widgets.MenuItem("Copy", new
		// MenuShortcut(KeyEvent.VK_C)));
		org.eclipse.swt.widgets.MenuItem clearItem = new org.eclipse.swt.widgets.MenuItem(editmenu, SWT.PUSH);
		clearItem.setText("Clear");
		// cutItem.setAccelerator('x');
		clearItem.addSelectionListener(this);
		// m.add(new org.eclipse.swt.widgets.MenuItem("Clear"));
		org.eclipse.swt.widgets.MenuItem selectAllItem = new org.eclipse.swt.widgets.MenuItem(editmenu, SWT.PUSH);
		selectAllItem.setText("Select All");
		selectAllItem.setAccelerator('a');
		selectAllItem.addSelectionListener(this);
		// m.add(new org.eclipse.swt.widgets.MenuItem("Select All", new
		// MenuShortcut(KeyEvent.VK_A)));
		new org.eclipse.swt.widgets.MenuItem(editmenu, SWT.SEPARATOR);
		org.eclipse.swt.widgets.MenuItem findItem = new org.eclipse.swt.widgets.MenuItem(editmenu, SWT.PUSH);
		findItem.setText("Find...");
		findItem.setAccelerator('f');
		findItem.addSelectionListener(this);
		// m.add(new org.eclipse.swt.widgets.MenuItem("Find...", new
		// MenuShortcut(KeyEvent.VK_F)));
		org.eclipse.swt.widgets.MenuItem findNextItem = new org.eclipse.swt.widgets.MenuItem(editmenu, SWT.PUSH);
		findNextItem.setText("Find Next");
		findNextItem.setAccelerator('x');
		findNextItem.addSelectionListener(this);
		// m.add(new org.eclipse.swt.widgets.MenuItem("Find Next", new
		// MenuShortcut(KeyEvent.VK_G)));
		edit.addSelectionListener(this);
		// mb.add(m);
		/* Changed for SWT. We create it directly in the TextPanel! */
		// textPanel.editMenu = editmenu;
		// m = new Menu("Font");
		org.eclipse.swt.widgets.MenuItem font = new org.eclipse.swt.widgets.MenuItem(mb, SWT.CASCADE);
		font.setText("Font");
		final org.eclipse.swt.widgets.Menu fontmenu = new org.eclipse.swt.widgets.Menu(shell, SWT.DROP_DOWN);
		font.setMenu(fontmenu);
		org.eclipse.swt.widgets.MenuItem textSmallerItem = new org.eclipse.swt.widgets.MenuItem(fontmenu, SWT.PUSH);
		textSmallerItem.setText("Make Text Smaller");
		textSmallerItem.addSelectionListener(this);
		// m.add(new org.eclipse.swt.widgets.MenuItem("Make Text Smaller"));
		org.eclipse.swt.widgets.MenuItem textLargerItem = new org.eclipse.swt.widgets.MenuItem(fontmenu, SWT.PUSH);
		textLargerItem.setText("Make Text Larger");
		textLargerItem.addSelectionListener(this);
		// m.add(new org.eclipse.swt.widgets.MenuItem("Make Text Larger"));
		new org.eclipse.swt.widgets.MenuItem(fontmenu, SWT.SEPARATOR);
		monospacedButton = new org.eclipse.swt.widgets.MenuItem(fontmenu, SWT.CHECK);
		monospacedButton.setText("Monospaced");
		monospacedButton.setSelection(IJ.isMacOSX() ? true : false);
		// antialiased = new CheckboxMenuItem("Antialiased", Prefs.get(FONT_ANTI,
		// IJ.isMacOSX() ? true : false));
		monospacedButton.addSelectionListener(this);
		// m.add(antialiased);
		antialiasedButton = new org.eclipse.swt.widgets.MenuItem(fontmenu, SWT.CHECK);
		antialiasedButton.setText("Antialiased");
		antialiasedButton.setSelection(IJ.isMacOSX() ? true : false);
		// antialiased = new CheckboxMenuItem("Antialiased", Prefs.get(FONT_ANTI,
		// IJ.isMacOSX() ? true : false));
		antialiasedButton.addSelectionListener(this);
		org.eclipse.swt.widgets.MenuItem saveSettingsItem = new org.eclipse.swt.widgets.MenuItem(fontmenu, SWT.PUSH);
		saveSettingsItem.setText("Save Settings");
		saveSettingsItem.addSelectionListener(this);
		// m.add(new org.eclipse.swt.widgets.MenuItem("Save Settings"));
		font.addSelectionListener(this);
		// mb.add(m);
		if(isResultsTable) {
			// m = new Menu("Results");
			org.eclipse.swt.widgets.MenuItem results = new org.eclipse.swt.widgets.MenuItem(mb, SWT.CASCADE);
			results.setText("Results");
			final org.eclipse.swt.widgets.Menu resultsmenu = new org.eclipse.swt.widgets.Menu(shell, SWT.DROP_DOWN);
			results.setMenu(resultsmenu);
			results.addSelectionListener(this);
			org.eclipse.swt.widgets.MenuItem clearResultsItem = new org.eclipse.swt.widgets.MenuItem(resultsmenu, SWT.PUSH);
			clearResultsItem.setText("Clear Results");
			clearResultsItem.addSelectionListener(this);
			// m.add(new org.eclipse.swt.widgets.MenuItem("Clear Results"));
			org.eclipse.swt.widgets.MenuItem summarizeItem = new org.eclipse.swt.widgets.MenuItem(resultsmenu, SWT.PUSH);
			summarizeItem.setText("Summarize");
			summarizeItem.addSelectionListener(this);
			// m.add(new org.eclipse.swt.widgets.MenuItem("Distribution..."));
			org.eclipse.swt.widgets.MenuItem distributionItem = new org.eclipse.swt.widgets.MenuItem(resultsmenu, SWT.PUSH);
			distributionItem.setText("Distribution...");
			distributionItem.addSelectionListener(this);
			org.eclipse.swt.widgets.MenuItem setMeasurementItem = new org.eclipse.swt.widgets.MenuItem(resultsmenu, SWT.PUSH);
			setMeasurementItem.setText("Set Measurements...");
			setMeasurementItem.addSelectionListener(this);
			org.eclipse.swt.widgets.MenuItem sortItem = new org.eclipse.swt.widgets.MenuItem(resultsmenu, SWT.PUSH);
			sortItem.setText("Sort...");
			sortItem.addSelectionListener(this);
			org.eclipse.swt.widgets.MenuItem plotItem = new org.eclipse.swt.widgets.MenuItem(resultsmenu, SWT.PUSH);
			plotItem.setText("Plot...");
			plotItem.addSelectionListener(this);
			org.eclipse.swt.widgets.MenuItem optionsItem = new org.eclipse.swt.widgets.MenuItem(resultsmenu, SWT.PUSH);
			optionsItem.setText("Options...");
			optionsItem.addSelectionListener(this);
			results.addSelectionListener(this);
			// mb.add(m);
		}
		shell.setMenuBar(mb);
	}

	/**
	 * Adds one or more lines of text to the window.
	 * 
	 * @param text
	 *            The text to be appended. Multiple lines should be separated by
	 *            \n.
	 */
	public void append(String text) {

		textPanel.append(text);
	}

	void setFont() {

		if(font != null)
			textPanel.setFont(font, antialiasedButton.getSelection());
		else {
			font = new org.eclipse.swt.graphics.Font(Display.getDefault(), new FontData("SansSerif", sizes[fontSize], SWT.NORMAL));
			textPanel.setFont(font, antialiasedButton.getSelection());
		}
	}

	private String getFontName() {

		return monospacedButton.getSelection() ? "Monospaced" : "SansSerif";
	}

	boolean openFile(String path) {

		OpenDialog od = new OpenDialog("Open Text File...", path);
		String directory = od.getDirectory();
		String name = od.getFileName();
		if(name == null)
			return false;
		path = directory + name;
		IJ.showStatus("Opening: " + path);
		try {
			BufferedReader r = new BufferedReader(new FileReader(directory + name));
			load(r);
			r.close();
		} catch(Exception e) {
			IJ.error(e.getMessage());
			return true;
		}
		textPanel.setTitle(name);
		shell.setText(name);
		IJ.showStatus("");
		return true;
	}

	/** Returns a reference to this TextWindow's TextPanel. */
	public TextPanel getTextPanel() {

		return textPanel;
	}

	/** Returns the ResultsTable associated with this TextWindow, or null. */
	public ResultsTable getResultsTable() {

		return textPanel != null ? textPanel.getResultsTable() : null;
	}

	/** Appends the text in the specified file to the end of this TextWindow. */
	public void load(BufferedReader in) throws IOException {

		int count = 0;
		while(true) {
			String s = in.readLine();
			if(s == null)
				break;
			textPanel.appendLine(s);
		}
	}

	@Override
	public void widgetSelected(SelectionEvent e) {

		// System.out.println(e);
		String cmd = ((org.eclipse.swt.widgets.MenuItem)e.widget).getText();
		if(cmd.equals("Antialiased")) {
			itemStateChanged(e);
		} else {
			actionPerformed(e);
		}
	}

	public void actionPerformed(SelectionEvent evt) {

		String cmd = ((org.eclipse.swt.widgets.MenuItem)evt.widget).getText();
		if(cmd.equals("Make Text Larger"))
			changeFontSize(true);
		else if(cmd.equals("Make Text Smaller"))
			changeFontSize(false);
		else if(cmd.equals("Save Settings"))
			saveSettings();
		else
			textPanel.doCommand(cmd);
	}
	/*
	 * public void processWindowEvent(WindowEvent e) { super.processWindowEvent(e);
	 * int id = e.getID(); if (id == WindowEvent.WINDOW_CLOSING) close(); else if
	 * (id == WindowEvent.WINDOW_ACTIVATED && !"Log".equals(getText()))
	 * WindowManager.setWindow(this); }
	 */

	public void itemStateChanged(SelectionEvent e) {

		font = null;
		setFont();
		if(IJ.recording()) {
			boolean state = monospacedButton.getSelection();
			if(Recorder.scriptMode())
				Recorder.recordCall("TextWindow.setMonospaced(" + state + ");");
			else
				Recorder.recordString("setOption(\"MonospacedText\", " + state + ");\n");
		}
	}

	public void close() {

		close(true);
	}

	/**
	 * Closes this TextWindow. Display a "save changes" dialog if this is the
	 * "Results" window and 'showDialog' is true.
	 */
	public void close(boolean showDialog) {

		/*
		 * We have to return a value if we are canceling the shell closing. This methods is also called without using the boolean value.
		 * Then the shell is simply be closed!
		 */
		okayToClose = true;
		/* Default is true. Only if we have return values we set the okayToClose variable to false! */
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				textPanel.defaultCursor.dispose();
				textPanel.resizeCursor.dispose();
				if(shell.getText().equals("Results")) {
					if(showDialog && !Analyzer.resetCounter()) {
						okayToClose = false;
						return;
					}
					IJ.setTextPanel(null);
					Prefs.saveLocation(LOC_KEY, shell.getLocation());
					org.eclipse.swt.graphics.Point d = shell.getSize();
					Prefs.set(WIDTH_KEY, d.x);
					Prefs.set(HEIGHT_KEY, d.y);
				} else if(shell.getText().equals("Log")) {
					Prefs.saveLocation(LOG_LOC_KEY, shell.getLocation());
					org.eclipse.swt.graphics.Point d = shell.getSize();
					Prefs.set(LOG_WIDTH_KEY, d.x);
					Prefs.set(LOG_HEIGHT_KEY, d.y);
					IJ.setDebugMode(false);
					IJ.log("\\Closed");
					IJ.notifyEventListeners(IJEventListener.LOG_WINDOW_CLOSED);
				} else if(shell.getText().equals("Debug")) {
					Prefs.saveLocation(DEBUG_LOC_KEY, shell.getLocation());
				} else if(textPanel != null && textPanel.rt != null) {
					if(!saveContents()) {
						okayToClose = false;
						return;
					}
				}
				// setVisible(false);
				// Changed for SWT!
				WindowManager.removeWindow(TextWindow.this);
				shell.dispose();
				textPanel.flush();
			}
		});
	}

	public void rename(String title) {

		textPanel.rename(title);
	}

	boolean saveContents() {

		int lineCount = textPanel.getLineCount();
		if(!textPanel.unsavedLines)
			lineCount = 0;
		ImageJ ij = IJ.getInstance();
		boolean macro = IJ.macroRunning() || Interpreter.isBatchMode();
		boolean isResults = shell.getText().contains("Results");
		if(lineCount > 0 && !macro && ij != null && !ij.quitting() && isResults) {
			YesNoCancelDialog d = new YesNoCancelDialog(shell.getText(), "Save " + lineCount + " measurements?");
			if(d.cancelPressed())
				return false;
			else if(d.yesPressed()) {
				if(!textPanel.saveAs(""))
					return false;
			}
		}
		textPanel.rt.reset();
		return true;
	}

	void changeFontSize(boolean larger) {

		int in = fontSize;
		if(larger) {
			fontSize++;
			if(fontSize == sizes.length)
				fontSize = sizes.length - 1;
		} else {
			fontSize--;
			if(fontSize < 0)
				fontSize = 0;
		}
		IJ.showStatus(sizes[fontSize] + " point");
		font.dispose();
		font = null;
		setFont();
	}

	public static void setFont(String name, int style, int size) {

		font = new org.eclipse.swt.graphics.Font(Display.getDefault(), new FontData(name, size, style));
		Object log = WindowManager.getWindow("Log");
		if(log != null && (log instanceof TextWindow))
			((TextWindow)log).setFont();
	}

	public static void setMonospaced(boolean b) {

		monospaced = b;
		Object log = WindowManager.getWindow("Log");
		if(log != null && (log instanceof TextWindow)) {
			((TextWindow)log).monospacedButton.setSelection(monospaced);
			((TextWindow)log).setFont();
		}
	}

	public static void setAntialiased(boolean b) {

		IJ.log("Use Font>Antialiased command");
	}

	void saveSettings() {

		Prefs.set(FONT_SIZE, fontSize);
		Prefs.set(FONT_ANTI, antialiasedButton.getSelection());
		IJ.showStatus("Font settings saved (size=" + sizes[fontSize] + ")");
	}

	@Override
	public void focusGained(org.eclipse.swt.events.FocusEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void focusLost(org.eclipse.swt.events.FocusEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void widgetDefaultSelected(SelectionEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellActivated(ShellEvent arg0) {

		if(!"Log".equals(shell.getText()))
			WindowManager.setWindow(this);
	}

	@Override
	public void shellClosed(ShellEvent e) {
		e.doit = false;
		close();
		/* If we cancel (okayToClose==false) the dialog we have to prevent the shell closing! */
		if(okayToClose) {
			e.doit = true;
		}
	}

	@Override
	public void shellDeactivated(ShellEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellDeiconified(ShellEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellIconified(ShellEvent arg0) {
		// TODO Auto-generated method stub

	}
}
