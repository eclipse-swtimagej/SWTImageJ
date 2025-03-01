package ij.plugin.frame;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.PrintGraphics;
import java.awt.PrintJob;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.CharArrayReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.text.source.AnnotationRulerColumn;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.IOverviewRuler;
import org.eclipse.jface.text.source.LineNumberRulerColumn;
import org.eclipse.jface.text.source.OverviewRuler;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotationModel;
import org.eclipse.jface.text.source.projection.ProjectionSupport;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.printing.PrintDialog;
import org.eclipse.swt.printing.Printer;
import org.eclipse.swt.printing.PrinterData;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

import ij.Executer;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Menus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.YesNoCancelDialog;
import ij.io.SaveDialog;
import ij.macro.Debugger;
import ij.macro.FunctionFinder;
import ij.macro.Interpreter;
import ij.macro.MacroConstants;
import ij.macro.MacroRunner;
import ij.macro.Program;
import ij.plugin.JavaScriptEvaluator;
import ij.plugin.MacroInstaller;
import ij.plugin.Macro_Runner;
import ij.plugin.frame.swt.WindowSwt;
import ij.swt.Color;
import ij.text.TextWindow;
import ij.util.Tools;

/** This is a simple TextArea based editor for editing and compiling plugins. */
public class Editor extends PlugInFrame implements WindowSwt, SelectionListener, org.eclipse.swt.events.ModifyListener,
		org.eclipse.swt.events.KeyListener, org.eclipse.swt.events.MouseListener, ClipboardOwner, MacroConstants,
		Runnable, Debugger {

	/**
	 * ImportPackage statements added in front of scripts. Contains no newlines so
	 * that lines numbers in error messages are not changed.
	 */
	public static String JavaScriptIncludes = "importPackage(Packages.ij);" + "importPackage(Packages.ij.gui);"
			+ "importPackage(Packages.ij.process);" + "importPackage(Packages.ij.measure);"
			+ "importPackage(Packages.ij.util);" + "importPackage(Packages.ij.macro);"
			+ "importPackage(Packages.ij.plugin);" + "importPackage(Packages.ij.io);"
			+ "importPackage(Packages.ij.text);" + "importPackage(Packages.ij.plugin.filter);"
			+ "importPackage(Packages.ij.plugin.frame);" + "importPackage(Packages.ij.plugin.tool);"
			+ "importPackage(java.lang);" + "importPackage(java.awt);" + "importPackage(java.awt.image);"
			+ "importPackage(org.eclipse.swt.graphics.Image);" + "importPackage(java.awt.geom);"
			+ "importPackage(org.eclipse.swt.widgets.Event);" + "importPackage(java.util);" + "importPackage(java.io);"
			+ "function print(s) {IJ.log(s);};";
	private static String JS_EXAMPLES = "img = IJ.openImage(\"http://imagej.net/images/blobs.gif\")\n"
			+ "img = IJ.createImage(\"Untitled\", \"16-bit ramp\", 500, 500, 1)\n" + "img.show()\n"
			+ "ip = img.getProcessor()\n" + "ip.getStats()\n" + "IJ.setAutoThreshold(img, \"IsoData\")\n"
			+ "IJ.run(img, \"Analyze Particles...\", \"show=Overlay display clear\")\n" + "ip.invert()\n"
			+ "ip.blurGaussian(5)\n" + "ip.get(10,10)\n" + "ip.set(10,10,222)\n"
			+ "(To run, move cursor to end of a line and press 'enter'.\n"
			+ "Visible images are automatically updated.)\n";
	public static final int MAX_SIZE = 28000, XINC = 10, YINC = 18;
	public static final int MONOSPACED = 1, MENU_BAR = 2, RUN_BAR = 4, INSTALL_BUTTON = 8;
	public static final int MACROS_MENU_ITEMS = 15;
	public static final String INTERACTIVE_NAME = "Interactive Interpreter";
	static final String FONT_SIZE = "editor.font.size";
	static final String FONT_MONO = "editor.font.mono";
	static final String CASE_SENSITIVE = "editor.case-sensitive";
	static final String DEFAULT_DIR = "editor.dir";
	static final String INSERT_SPACES = "editor.spaces";
	static final String TAB_INC = "editor.tab-inc";
	private final static int MACRO = 0, JAVASCRIPT = 1, BEANSHELL = 2, PYTHON = 3;
	private final static String[] languages = { "Macro", "JavaScript", "BeanShell", "Python" };
	private final static String[] extensions = { ".ijm", ".js", ".bsh", ".py" };
	public static Editor currentMacroEditor;
	private StyledText ta;
	private String path;
	protected boolean changes;
	private static String searchString = "";
	private static boolean caseSensitive = Prefs.get(CASE_SENSITIVE, true);
	private static int lineNumber = 1;
	private static int xoffset, yoffset;
	private static int nWindows;
	private org.eclipse.swt.widgets.Menu fileMenu, editMenu;
	private Properties p = new Properties();
	private int[] macroStarts;
	private String[] macroNames;
	private org.eclipse.swt.widgets.Menu mb;
	private org.eclipse.swt.widgets.Menu macrosMenu;
	private int nMacros;
	private Program pgm;
	private int eventCount;
	private String shortcutsInUse;
	private int inUseCount;
	private MacroInstaller installer;
	private static String defaultDir = Prefs.get(DEFAULT_DIR, null);;
	private boolean dontShowWindow;
	private int[] sizes = { 9, 10, 11, 12, 13, 14, 16, 18, 20, 24, 36, 48, 60, 72 };
	private int fontSizeIndex = (int) Prefs.get(FONT_SIZE, 6); // defaults to 16-point
	private org.eclipse.swt.widgets.MenuItem monospaced;
	private static boolean wholeWords;
	private boolean isMacroWindow;
	private int debugStart, debugEnd;
	private static TextWindow debugWindow;
	private boolean step;
	private int previousLine;
	private static Editor instance;
	private int runToLine;
	private String downloadUrl;
	private boolean downloading;
	private FunctionFinder functionFinder;
	private ArrayList undoBuffer = new ArrayList();
	private boolean performingUndo;
	private boolean checkForCurlyQuotes;
	private static int tabInc = (int) Prefs.get(TAB_INC, 3);
	private static boolean insertSpaces = Prefs.get(INSERT_SPACES, false);
	private org.eclipse.swt.widgets.MenuItem insertSpacesItem;
	private boolean interactiveMode;
	private Interpreter interpreter;
	private JavaScriptEvaluator evaluator;
	private int messageCount;
	private String rejectMacrosMsg;
	private org.eclipse.swt.widgets.Button runButton, installButton;
	private org.eclipse.swt.widgets.Combo language;
	private boolean visible;
	protected String text;
	protected String titleEditorShell;
	private org.eclipse.swt.graphics.Font font;
	protected ProjectionViewer sourceViewer;// Extends TextViewer!
	protected AnnotationModel _annotationModel;
	private org.eclipse.swt.graphics.Font fontNew;
	protected JavaLineStyler lineStyler;
	protected MacroLineStyler lineMacroStyler;
	protected CompletionEditor completionEditor;
	protected Composite composite;
	boolean okayToClose;
	protected ProjectionSupport projectionSupport;
	protected ProjectionAnnotationModel projectionAnnotationModel;
	protected Document document;
	protected ProjectionAnnotation projectionAnnotation;
	protected Timer timer;

	public Editor() {

		this(24, 80, 0, MENU_BAR);
	}

	public Editor(String name) {

		this(24, 80, 0, getOptions(name));
	}

	public Editor(int rows, int columns, int fontSize, int options) {

		super("Editor", SWT.DIALOG_TRIM | SWT.RESIZE);
		Display.getDefault().syncExec(() -> {

			WindowManager.addWindow(Editor.this);
			getShell().setLayout(new FillLayout());
			composite = new Composite(shell, SWT.NONE);
			composite.setLayout(new GridLayout(1, true));
			addMenuBar(options);
			boolean addRunBar = (options & RUN_BAR) != 0;
			ImageJ ij = IJ.getInstance();
			if (addRunBar) {
				org.eclipse.swt.widgets.Composite panel = new org.eclipse.swt.widgets.Composite(composite, SWT.NONE);
				panel.addKeyListener(ij);
				panel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1));
				panel.setLayout(new GridLayout(3, false));
				runButton = new org.eclipse.swt.widgets.Button(panel, SWT.NONE);
				runButton.addKeyListener(ij);
				runButton.setText("Run");
				runButton.addSelectionListener(Editor.this);
				font = new org.eclipse.swt.graphics.Font(Display.getDefault(),
						new FontData("SansSerif", sizes[fontSizeIndex], SWT.NORMAL));
				if ((options & INSTALL_BUTTON) != 0) {
					installButton = new org.eclipse.swt.widgets.Button(panel, SWT.NONE);
					installButton.addKeyListener(ij);
					installButton.setText("Install");
					installButton.addSelectionListener(Editor.this);
				}
				language = new org.eclipse.swt.widgets.Combo(panel, SWT.DROP_DOWN | SWT.READ_ONLY);
				language.addKeyListener(ij);
				for (int i = 0; i < languages.length; i++)
					language.add(languages[i]);
				language.addSelectionListener(Editor.this);
				language.select(0);
			}
			/* The ruler on the right side! */
			IOverviewRuler overviewRuler = new OverviewRuler(null, 15, null);
			/* The ruler on the left side with two columns (line number, annotations)! */
			CompositeRuler ruler = new CompositeRuler();
			LineNumberRulerColumn lnrc = new LineNumberRulerColumn();
			AnnotationRulerColumn annotationRuler = new AnnotationRulerColumn(15);
			ruler.addDecorator(0, annotationRuler);
			ruler.addDecorator(1, lnrc);
			/* Create a JFace Projection TextViewer! */
			sourceViewer = new ProjectionViewer(composite, ruler, overviewRuler, true,
					SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
			projectionSupport = new ProjectionSupport(sourceViewer, null, new EditorSharedTextColors());
			projectionSupport.install();
			// turn projection mode on
			sourceViewer.doOperation(ProjectionViewer.TOGGLE);
			document = new Document();
			projectionAnnotationModel = new ProjectionAnnotationModel();
			// projectionAnnotation.setType(Annotation.);
			// Document document = new Document();
			sourceViewer.setDocument(document);
			completionEditor = new CompletionEditor(sourceViewer, Editor.this);
			annotationRuler.getControl().setBackground(Color.lightGray);
			overviewRuler.getControl().setBackground(Color.white);
			sourceViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 7, 1));
			ta = sourceViewer.getTextWidget();
			// ta.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 7, 1));
			ta.addModifyListener(Editor.this);
			ta.addKeyListener(Editor.this);
			ta.addMouseListener(Editor.this); // ImageJ handles keyboard shortcuts
			Editor.this.getShell().addKeyListener(ij); // ImageJ handles keyboard shortcuts
			composite.layout();
			// getShell().layout();
			setFont();
			positionWindow();
			if (addRunBar)
				ta.forceFocus(); // needed for selections to show
			if (!IJ.isJava18() && !IJ.isLinux())
				insertSpaces = false;

		});
		document.addDocumentListener(new IDocumentListener() {

			/* Adding a reconciler for the document! */
			public void documentChanged(final DocumentEvent event) {

				if (timer != null) {
					timer.cancel();
				}
				timer = new Timer();
				TimerTask timerTask = new TimerTask() {

					@Override
					public void run() {

						new Thread(new Runnable() {

							@Override
							public void run() {

								Display.getDefault().syncExec(() -> {

									/*
									 * if (projectionAnnotation != null)
									 * projectionAnnotationModel.removeAllAnnotations();
									 * System.out.println("Changed..."); projectionAnnotation = new
									 * org.eclipse.jface.text.source.projection.ProjectionAnnotation(); //
									 * projectionAnnotation.setRangeIndication(true); //
									 * projectionAnnotation.setText("Annotation");
									 * projectionAnnotationModel.addAnnotation(projectionAnnotation, new Position(0,
									 * 200)); projectionAnnotationModel.expandAll(0, 200);
									 * sourceViewer.setDocument(document, projectionAnnotationModel);
									 */

								});
							}
						}).start();
					}
				};
				timer.schedule(timerTask, 500); // 1 minute
			}

			@Override
			public void documentAboutToBeChanged(DocumentEvent event) {
				// TODO Auto-generated method stub

			}
		});
	}

	public Composite getComposite() {

		return composite;
	}

	private static int getOptions(String name) {

		int options = MENU_BAR;
		if (name == null)
			return options;
		if (name.endsWith(".ijm") || name.endsWith(".js") || name.endsWith(".bsh") || name.endsWith(".py"))
			options |= RUN_BAR;
		if (name.endsWith(".ijm"))
			options |= INSTALL_BUTTON;
		return options;
	}

	void addMenuBar(int options) {

		mb = new org.eclipse.swt.widgets.Menu(getShell(), SWT.BAR);
		org.eclipse.swt.widgets.MenuItem m = new org.eclipse.swt.widgets.MenuItem(mb, SWT.CASCADE);
		m.setText("File");
		fileMenu = new org.eclipse.swt.widgets.Menu(getShell(), SWT.DROP_DOWN);
		m.setMenu(fileMenu);
		org.eclipse.swt.widgets.MenuItem newItem = new org.eclipse.swt.widgets.MenuItem(fileMenu, SWT.PUSH);
		newItem.setText("New...");
		int cmdOrCtrl = IJ.isMacOSX() ? SWT.COMMAND : SWT.CTRL;
		newItem.setAccelerator(cmdOrCtrl + SWT.SHIFT+ 'n');
		newItem.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem openItem = new org.eclipse.swt.widgets.MenuItem(fileMenu, SWT.PUSH);
		openItem.setText("Open...");
		openItem.setAccelerator(cmdOrCtrl + 'o');
		openItem.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem saveItem = new org.eclipse.swt.widgets.MenuItem(fileMenu, SWT.PUSH);
		saveItem.setText("Save");
		saveItem.setAccelerator(cmdOrCtrl + 's');
		saveItem.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem saveAsItem = new org.eclipse.swt.widgets.MenuItem(fileMenu, SWT.PUSH);
		saveAsItem.setText("Save As...");
		saveAsItem.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem revertItem = new org.eclipse.swt.widgets.MenuItem(fileMenu, SWT.PUSH);
		revertItem.setText("Revert");
		revertItem.setAccelerator(SWT.ALT + 's');
		revertItem.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem printItem = new org.eclipse.swt.widgets.MenuItem(fileMenu, SWT.PUSH);
		printItem.setText("Print");
		printItem.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem medit = new org.eclipse.swt.widgets.MenuItem(mb, SWT.CASCADE);
		medit.setText("Edit");
		editMenu = new org.eclipse.swt.widgets.Menu(getShell(), SWT.DROP_DOWN);
		medit.setMenu(editMenu);
		org.eclipse.swt.widgets.MenuItem undoItem = new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.PUSH);
		undoItem.addSelectionListener(Editor.this);
		if (IJ.isWindows()) {
			undoItem.setText("Undo  Ctrl+Z");
			undoItem.setAccelerator(cmdOrCtrl + 'z');
		} else {
			undoItem.setText("Undo");
			undoItem.setAccelerator(cmdOrCtrl + 'z');
		}
		new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.SEPARATOR);
		org.eclipse.swt.widgets.MenuItem cutItem = new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.PUSH);
		cutItem.addSelectionListener(Editor.this);
		if (IJ.isWindows())
			cutItem.setText("Cut  Ctrl+X");
		else {
			cutItem.setText("Cut");
			cutItem.setAccelerator(cmdOrCtrl + 'x');
		}
		org.eclipse.swt.widgets.MenuItem copyItem = new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.PUSH);
		copyItem.addSelectionListener(Editor.this);
		if (IJ.isWindows()) {
			copyItem.setText("Copy  Ctrl+C");
			copyItem.setAccelerator(cmdOrCtrl + 'c');
		} else {
			copyItem.setText("Copy");
			copyItem.setAccelerator(cmdOrCtrl + 'c');
		}
		org.eclipse.swt.widgets.MenuItem pasteItem = new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.PUSH);
		pasteItem.addSelectionListener(Editor.this);
		if (IJ.isWindows()) {
			pasteItem.setText("Paste  Ctrl+V");
			pasteItem.setAccelerator(cmdOrCtrl + 'v');
		} else {
			pasteItem.setText("Paste");
			pasteItem.setAccelerator(cmdOrCtrl + 'v');
		}
		new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.SEPARATOR);
		org.eclipse.swt.widgets.MenuItem findItem = new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.PUSH);
		findItem.setText("Find...");
		findItem.setAccelerator(cmdOrCtrl + 'f');
		findItem.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem findNextItem = new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.PUSH);
		findNextItem.setText("Find Next");
		findNextItem.setAccelerator(cmdOrCtrl + 'g');
		findNextItem.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem goToLineItem = new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.PUSH);
		goToLineItem.setText("Go to Line...");
		goToLineItem.setAccelerator(cmdOrCtrl + 'l');
		goToLineItem.addSelectionListener(Editor.this);
		new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.SEPARATOR);
		org.eclipse.swt.widgets.MenuItem selectAllItem = new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.PUSH);
		selectAllItem.setText("Select All");
		selectAllItem.setAccelerator(SWT.SHIFT + 'a');
		selectAllItem.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem balanceItem = new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.PUSH);
		balanceItem.setText("Balance");
		balanceItem.setAccelerator(cmdOrCtrl + 'b');
		balanceItem.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem detabItem = new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.PUSH);
		detabItem.setText("Detab...");
		detabItem.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem insertSpacesItem = new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.CHECK);
		insertSpacesItem.setText("Tab Key Inserts Spaces");
		insertSpacesItem.addSelectionListener(Editor.this);
		insertSpacesItem.addSelectionListener(Editor.this);
		insertSpacesItem.setSelection(insertSpaces);
		org.eclipse.swt.widgets.MenuItem zapGremlinsItem = new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.PUSH);
		zapGremlinsItem.setText("Zap Gremlins");
		zapGremlinsItem.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem copyToImageInfoItem = new org.eclipse.swt.widgets.MenuItem(editMenu, SWT.PUSH);
		copyToImageInfoItem.setText("Copy to Image Info");
		copyToImageInfoItem.addSelectionListener(Editor.this);
		/* Implement for SWT! */
		/*
		 * if ((options&MENU_BAR)!=0) setMenuBar(mb);
		 */
		org.eclipse.swt.widgets.MenuItem font = new org.eclipse.swt.widgets.MenuItem(mb, SWT.CASCADE);
		font.setText("Font");
		org.eclipse.swt.widgets.Menu fontMenu = new org.eclipse.swt.widgets.Menu(getShell(), SWT.DROP_DOWN);
		font.setMenu(fontMenu);
		org.eclipse.swt.widgets.MenuItem makeTextSmallerItem = new org.eclipse.swt.widgets.MenuItem(fontMenu, SWT.PUSH);
		makeTextSmallerItem.setText("Make Text Smaller");
		makeTextSmallerItem.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem makeTextLargerItem = new org.eclipse.swt.widgets.MenuItem(fontMenu, SWT.PUSH);
		makeTextLargerItem.setText("Make Text Larger");
		makeTextLargerItem.addSelectionListener(Editor.this);
		new org.eclipse.swt.widgets.MenuItem(fontMenu, SWT.SEPARATOR);
		monospaced = new org.eclipse.swt.widgets.MenuItem(fontMenu, SWT.CHECK);
		monospaced.setSelection(Prefs.get(FONT_MONO, false));
		monospaced.setText("Monospaced Font");
		if ((options & MONOSPACED) != 0)
			monospaced.setSelection(true);
		monospaced.addSelectionListener(Editor.this);
		org.eclipse.swt.widgets.MenuItem saveSettingsItem = new org.eclipse.swt.widgets.MenuItem(fontMenu, SWT.PUSH);
		saveSettingsItem.setText("Save Settings");
		saveSettingsItem.addSelectionListener(Editor.this);
		getShell().setMenuBar(mb);
	}

	public void positionWindow() {

		Dimension screen = IJ.getScreenSize();
		Point window = getShell().getSize();
		if (window.x == 0)
			return;
		int left = screen.width / 2 - window.x / 2;
		int top = screen.height / (IJ.isWindows() ? 6 : 5);
		if (IJ.isMacOSX())
			top = (screen.height - window.y) / 4;
		if (top < 0)
			top = 0;
		if (nWindows <= 0 || xoffset > 8 * XINC) {
			xoffset = 0;
			yoffset = 0;
		}
		getShell().setLocation(left + xoffset, top + yoffset);
		xoffset += XINC;
		yoffset += YINC;
		nWindows++;
	}

	void setWindowTitle(String title) {

		Menus.updateWindowMenuItem(getShell().getText(), title);
		getShell().setText(title);
	}

	public void create(String name, String text) {
		int cmdOrCtrl = IJ.isMacOSX() ? SWT.COMMAND : SWT.CTRL;
		Display.getDefault().syncExec(() -> {

			ta.append(text);
			if (IJ.isMacOSX())
				IJ.wait(25); // needed to get setCaretPosition() on OS X
			ta.setCaretOffset(0);
			setWindowTitle(name);
			/* Add a Java styler to the document! */
			if (name.endsWith(".java")) {
				lineStyler = new JavaLineStyler();
				lineStyler.parseBlockComments(text);
				ta.addLineStyleListener(lineStyler);
				ta.redraw();
			} else if (name.endsWith(".txt") || name.endsWith(".ijm")) {
				lineMacroStyler = new MacroLineStyler();
				lineMacroStyler.parseBlockComments(text);
				ta.addLineStyleListener(lineMacroStyler);
				ta.redraw();
			}
			/* Original ImageJ extension cases! */
			boolean macroExtension = name.endsWith(".txt") || name.endsWith(".ijm");
			if (macroExtension || name.endsWith(".js") || name.endsWith(".bsh") || name.endsWith(".py")
					|| name.indexOf(".") == -1) {
				org.eclipse.swt.widgets.MenuItem macroItem = new org.eclipse.swt.widgets.MenuItem(mb, SWT.CASCADE);
				macroItem.setText("Macros");
				org.eclipse.swt.widgets.Menu macroMenu = new org.eclipse.swt.widgets.Menu(getShell(), SWT.DROP_DOWN);
				macroItem.setMenu(macroMenu);
				org.eclipse.swt.widgets.MenuItem runMacroItem = new org.eclipse.swt.widgets.MenuItem(macroMenu,
						SWT.PUSH);
				runMacroItem.setText("Run Macro");
				runMacroItem.setAccelerator(cmdOrCtrl + 'r');
				runMacroItem.addSelectionListener(Editor.this);
				org.eclipse.swt.widgets.MenuItem evaluateLineItem = new org.eclipse.swt.widgets.MenuItem(macroMenu,
						SWT.PUSH);
				evaluateLineItem.setText("Evaluate Line");
				evaluateLineItem.setAccelerator(cmdOrCtrl + 'y');
				evaluateLineItem.addSelectionListener(Editor.this);
				org.eclipse.swt.widgets.MenuItem abortMacroItem = new org.eclipse.swt.widgets.MenuItem(macroMenu,
						SWT.PUSH);
				abortMacroItem.setText("Abort Macro");
				abortMacroItem.addSelectionListener(Editor.this);
				org.eclipse.swt.widgets.MenuItem installMacrosItem = new org.eclipse.swt.widgets.MenuItem(macroMenu,
						SWT.PUSH);
				installMacrosItem.setText("Install Macros");
				installMacrosItem.setAccelerator(cmdOrCtrl + 'i');
				installMacrosItem.addSelectionListener(Editor.this);
				org.eclipse.swt.widgets.MenuItem macrosFunctionsItem = new org.eclipse.swt.widgets.MenuItem(macroMenu,
						SWT.PUSH);
				macrosFunctionsItem.setText("Macro Functions...");
				macrosFunctionsItem.setAccelerator(cmdOrCtrl + SWT.SHIFT + 'm');
				macrosFunctionsItem.addSelectionListener(Editor.this);
				org.eclipse.swt.widgets.MenuItem functionFinderItem = new org.eclipse.swt.widgets.MenuItem(macroMenu,
						SWT.PUSH);
				functionFinderItem.setText("Function Finder...");
				functionFinderItem.setAccelerator(cmdOrCtrl + SWT.SHIFT + 'f');
				functionFinderItem.addSelectionListener(Editor.this);
				org.eclipse.swt.widgets.MenuItem enterInteractiveModeItem = new org.eclipse.swt.widgets.MenuItem(
						macroMenu, SWT.PUSH);
				enterInteractiveModeItem.setText("Enter Interactive Mode");
				enterInteractiveModeItem.addSelectionListener(Editor.this);
				org.eclipse.swt.widgets.MenuItem assignToRepeatCmdItem = new org.eclipse.swt.widgets.MenuItem(macroMenu,
						SWT.PUSH);
				assignToRepeatCmdItem.setText("Assign to Repeat Cmd");
				assignToRepeatCmdItem.setAccelerator(cmdOrCtrl + SWT.SHIFT + 'a');
				assignToRepeatCmdItem.addSelectionListener(Editor.this);
				new org.eclipse.swt.widgets.MenuItem(macroMenu, SWT.SEPARATOR);
				org.eclipse.swt.widgets.MenuItem evaluateMacroItem = new org.eclipse.swt.widgets.MenuItem(macroMenu,
						SWT.PUSH);
				evaluateMacroItem.setText("Evaluate Macro");
				evaluateMacroItem.addSelectionListener(Editor.this);
				org.eclipse.swt.widgets.MenuItem evaluateJavaScriptItem = new org.eclipse.swt.widgets.MenuItem(
						macroMenu, SWT.PUSH);
				evaluateJavaScriptItem.setText("Evaluate JavaScript");
				evaluateJavaScriptItem.setAccelerator(cmdOrCtrl + 'j');
				evaluateJavaScriptItem.addSelectionListener(Editor.this);
				org.eclipse.swt.widgets.MenuItem evaluateBeanShellItem = new org.eclipse.swt.widgets.MenuItem(macroMenu,
						SWT.PUSH);
				evaluateBeanShellItem.setText("Evaluate BeanShell");
				evaluateBeanShellItem.setAccelerator(cmdOrCtrl + SWT.SHIFT + 'b');
				evaluateBeanShellItem.addSelectionListener(Editor.this);
				org.eclipse.swt.widgets.MenuItem evaluatePythonItem = new org.eclipse.swt.widgets.MenuItem(macroMenu,
						SWT.PUSH);
				evaluatePythonItem.setText("Evaluate Python");
				evaluatePythonItem.setAccelerator(cmdOrCtrl + 'p');
				evaluatePythonItem.addSelectionListener(Editor.this);
				org.eclipse.swt.widgets.MenuItem showLogWindowItem = new org.eclipse.swt.widgets.MenuItem(macroMenu,
						SWT.PUSH);
				showLogWindowItem.setText("Show Log Window");
				showLogWindowItem.setAccelerator(cmdOrCtrl + SWT.SHIFT + 'a');
				showLogWindowItem.addSelectionListener(Editor.this);
				new org.eclipse.swt.widgets.MenuItem(macroMenu, SWT.SEPARATOR);
				// MACROS_MENU_ITEMS must be updated if items are added to Editor.this menu
				if (!(name.endsWith(".js") || name.endsWith(".bsh") || name.endsWith(".py"))) {
					org.eclipse.swt.widgets.MenuItem debugItem = new org.eclipse.swt.widgets.MenuItem(mb, SWT.CASCADE);
					debugItem.setText("Debug");
					org.eclipse.swt.widgets.Menu debugMenu = new org.eclipse.swt.widgets.Menu(getShell(),
							SWT.DROP_DOWN);
					debugItem.setMenu(debugMenu);
					org.eclipse.swt.widgets.MenuItem debugMacroItem = new org.eclipse.swt.widgets.MenuItem(debugMenu,
							SWT.PUSH);
					debugMacroItem.setText("Debug Macro");
					debugMacroItem.setAccelerator(SWT.MOD1 + 'd');
					debugMacroItem.addSelectionListener(Editor.this);
					org.eclipse.swt.widgets.MenuItem stepItem = new org.eclipse.swt.widgets.MenuItem(debugMenu,
							SWT.PUSH);
					stepItem.setText("Step");
					stepItem.setAccelerator(SWT.MOD1 + 'e');
					stepItem.addSelectionListener(Editor.this);
					org.eclipse.swt.widgets.MenuItem traceItem = new org.eclipse.swt.widgets.MenuItem(debugMenu,
							SWT.PUSH);
					traceItem.setText("Trace");
					traceItem.setAccelerator(SWT.MOD1 + 't');
					traceItem.addSelectionListener(Editor.this);
					org.eclipse.swt.widgets.MenuItem fastTraceItem = new org.eclipse.swt.widgets.MenuItem(debugMenu,
							SWT.PUSH);
					fastTraceItem.setText("Fast Trace");
					fastTraceItem.setAccelerator(SWT.MOD1 + SWT.SHIFT + 't');
					fastTraceItem.addSelectionListener(Editor.this);
					org.eclipse.swt.widgets.MenuItem runItem = new org.eclipse.swt.widgets.MenuItem(debugMenu,
							SWT.PUSH);
					runItem.setText("Run");
					runItem.addSelectionListener(Editor.this);
					org.eclipse.swt.widgets.MenuItem runToInsertionPointItem = new org.eclipse.swt.widgets.MenuItem(
							debugMenu, SWT.PUSH);
					runToInsertionPointItem.setText("Run to Insertion Point");
					runToInsertionPointItem.setAccelerator(SWT.MOD1 + SWT.SHIFT + 'e');
					runToInsertionPointItem.addSelectionListener(Editor.this);
					org.eclipse.swt.widgets.MenuItem abortItem = new org.eclipse.swt.widgets.MenuItem(debugMenu,
							SWT.PUSH);
					abortItem.setText("Abort");
					abortItem.addSelectionListener(Editor.this);
				}
			} else {
				new org.eclipse.swt.widgets.MenuItem(fileMenu, SWT.SEPARATOR);
				org.eclipse.swt.widgets.MenuItem compileAndRunItem = new org.eclipse.swt.widgets.MenuItem(fileMenu,
						SWT.PUSH);
				compileAndRunItem.setText("Compile and Run");
				compileAndRunItem.setAccelerator(cmdOrCtrl + 'r');
				compileAndRunItem.addSelectionListener(Editor.this);
			}
			if (language != null) {
				for (int i = 0; i < languages.length; i++) {
					if (name.endsWith(extensions[i])) {
						// language.select(languages[i]);
						language.select(i);
						break;
					}
				}
			}
			if (text.startsWith("//@AutoInstall") && (name.endsWith(".ijm") || name.endsWith(".txt"))) {
				boolean installInPluginsMenu = !name.contains("Tool.");
				installMacros(text, installInPluginsMenu);
				if (text.startsWith("//@AutoInstallAndHide"))
					dontShowWindow = true;
			}
			if (IJ.getInstance() != null && !dontShowWindow)
				// show();
				getShell().setVisible(true);
			if (dontShowWindow) {
				// dispose();
				getShell().close();
				dontShowWindow = false;
			}
			if (name.equals(INTERACTIVE_NAME)) {
				enterInteractiveMode();
				String txt = ta.getText();
				ta.setCaretOffset(txt.length());
			}
			checkForCurlyQuotes = true;
			changes = false;

		});
		WindowManager.setWindow(this);
	}

	public void createMacro(String name, String text) {

		create(name, text);
	}

	public void setRejectMacrosMsg(String msg) {

		rejectMacrosMsg = msg;
	}

	public String getRejectMacrosMsg() {

		return rejectMacrosMsg;
	}

	void installMacros(String text, boolean installInPluginsMenu) {

		if (rejectMacrosMsg != null) {
			if (rejectMacrosMsg.length() > 0)
				IJ.showMessage("", rejectMacrosMsg);
			return;
		}
		String functions = Interpreter.getAdditionalFunctions();
		if (functions != null && text != null) {
			if (!(text.endsWith("\n") || functions.startsWith("\n")))
				text = text + "\n" + functions;
			else
				text = text + functions;
		}
		installer = new MacroInstaller();
		installer.setFileName(getShell().getText());
		int nShortcuts = installer.install(text, macrosMenu);
		if (installInPluginsMenu || nShortcuts > 0)
			installer.install(null);
		dontShowWindow = installer.isAutoRunAndHide();
		currentMacroEditor = this;
	}

	/** Opens a file and replaces the text (if any) by the contents of the file. */
	public void open(String dir, String name) {

		path = dir + name;
		File file = new File(path);
		if (!file.exists()) {
			IJ.error("File not found: " + path);
			return;
		}
		try {
			StringBuffer sb = new StringBuffer(5000);
			BufferedReader r = new BufferedReader(new FileReader(file));
			while (true) {
				String s = r.readLine();
				if (s == null)
					break;
				else
					sb.append(s + "\n");
			}
			r.close();
			AtomicReference<String> textTa = new AtomicReference<String>();
			Display.getDefault().syncExec(() -> {

				textTa.set(ta.getText());

			});
			String text2 = textTa.get();
			if (ta != null && text2.length() > 0) {
				ta.setText(""); // delete previous contents (if any)
				eventCount = 0;
			}
			create(name, new String(sb));
			changes = false;
		} catch (Exception e) {
			IJ.handleException(e);
			return;
		}
	}

	public String getText() {

		if (ta == null)
			return "";
		else
			return ta.getText();
	}

	public StyledText getTextArea() {

		return ta;
	}

	public void display(String title, String text) {

		Display.getDefault().syncExec(() -> {

			ta.selectAll();
			// x is the offset of the first selected character, relative to the first
			// character of the widget content. y is the length of the selection.
			ta.replaceTextRange(ta.getSelectionRange().x, ta.getSelectionRange().y, text);
			ta.setCaretOffset(0);
			setWindowTitle(title);

		});
		changes = false;
		if (IJ.getInstance() != null)
			setVisible(true);
		WindowManager.setWindow(this);
	}

	void save() {

		if (path == null) {
			saveAs();
			return;
		}
		File f = new File(path);
		if (f.exists() && !f.canWrite()) {
			IJ.showMessage("Editor", "Unable to save because file is write-protected. \n \n" + path);
			return;
		}
		String text = ta.getText();
		char[] chars = new char[text.length()];
		text.getChars(0, text.length(), chars, 0);
		try {
			BufferedReader br = new BufferedReader(new CharArrayReader(chars));
			BufferedWriter bw = new BufferedWriter(new FileWriter(path));
			while (true) {
				String s = br.readLine();
				if (s == null)
					break;
				bw.write(s, 0, s.length());
				bw.newLine();
			}
			bw.close();
			IJ.showStatus(text.length() + " chars saved to " + path);
			changes = false;
		} catch (IOException e) {
		}
	}

	void compileAndRun() {

		if (path == null)
			saveAs();
		if (path != null) {
			save();
			String text = ta.getText();
			if (text.contains("implements PlugInFilter") && text.contains("IJ.run("))
				IJ.log("<<Plugins that call IJ.run() should probably implement PlugIn, not PlugInFilter.>>");
			IJ.runPlugIn("ij.plugin.Compiler", path);
		}
	}

	final void runMacro(boolean debug) {

		AtomicReference<String> rec = new AtomicReference<String>();
		Display.getDefault().syncExec(() -> {

			if (path != null)
				Macro_Runner.setFilePath(path);
			if (getShell().getText().endsWith(".js")) {
				evaluateJavaScript();
				// We need to set the values so we can return (we are in a anonymous class).
				rec.set("return");
				return;
			} else if (getShell().getText().endsWith(".bsh")) {
				evaluateScript(".bsh");
				rec.set("return");
				return;
			} else if (getShell().getText().endsWith(".py")) {
				evaluateScript(".py");
				rec.set("return");
				return;
			}
			int start = ta.getSelectionRange().x;
			int end = start + ta.getSelectionRange().y;
			String text;
			if (start == end)
				text = ta.getText();
			else
				text = ta.getSelectionText();
			Interpreter.abort(); // abort any currently running macro
			if (checkForCurlyQuotes) {
				// replace curly quotes with standard quotes
				text = text.replaceAll("\u201C", "\"");
				text = text.replaceAll("\u201D", "\"");
				if (start == end)
					ta.setText(text);
				else {
					String text2 = ta.getText();
					text2 = text2.replaceAll("\u201C", "\"");
					text2 = text2.replaceAll("\u201D", "\"");
					ta.setText(text2);
				}
				checkForCurlyQuotes = false;
			}
			currentMacroEditor = Editor.this;
			if (text.startsWith("// include ")) { // include additional functions
				String path = text.substring(11, text.indexOf("\n"));
				boolean isURL = path.startsWith("http://") || path.startsWith("https://");
				if (!isURL) {
					boolean fullPath = path.startsWith("/") || path.startsWith("\\") || path.indexOf(":\\") == 1
							|| path.indexOf(":/") == 1;
					if (!fullPath) {
						String macrosDir = Menus.getMacrosPath();
						if (macrosDir != null)
							path = Menus.getMacrosPath() + path;
					}
					File f = new File(path);
					if (!f.exists())
						IJ.error("Include file not found:\n" + path);
				}
				if (isURL)
					text = text + IJ.openUrlAsString(path);
				else
					text = text + IJ.openAsString(path);
			}
			rec.set(text);

		});
		// new MacroRunner(rec.get(), debug ? this : null);
		// We need to set the values so we can return (we are in a anonymous class).
		String recResult = rec.get();
		if (recResult.equals("return")) {
			return;
		}
		text = doInclude(recResult);
		MacroRunner mr = new MacroRunner();
		if (debug)
			mr.setEditor(this);
		mr.run(text);
	}

	/** Process optional #include statment at begining of macro. */
	public static String doInclude(String code) {

		if (code.startsWith("#include ") || code.startsWith("// include ")) {
			if (IJ.isWindows())
				code = code.replaceAll("\r\n", "\n");
			int offset = code.startsWith("#include ") ? 9 : 11;
			int eol = code.indexOf("\n");
			String path = code.substring(offset, eol);
			boolean isURL = path.startsWith("http://") || path.startsWith("https://");
			if (!isURL) {
				boolean fullPath = path.startsWith("/") || path.startsWith("\\") || path.indexOf(":\\") == 1
						|| path.indexOf(":/") == 1;
				if (!fullPath) {
					String macrosDir = Menus.getMacrosPath();
					if (macrosDir != null)
						path = Menus.getMacrosPath() + path;
				}
				File f = new File(path);
				if (!f.exists())
					IJ.error("Include file not found:\n" + path);
			}
			code = code.substring(eol + 1, code.length());
			if (isURL)
				code = "//\n" + code + IJ.openUrlAsString(path);
			else
				code = "//\n" + code + IJ.openAsString(path);
		}
		return code;
	}

	void evaluateMacro() {

		String title = getTitle();
		if (title.endsWith(".js") || title.endsWith(".bsh") || title.endsWith(".py"))
			setWindowTitle(title.substring(0, title.length() - 3) + ".ijm");
		runMacro(false);
	}

	void evaluateJavaScript() {

		if (!getTitle().endsWith(".js"))
			setWindowTitle(SaveDialog.setExtension(getTitle(), ".js"));
		int start = ta.getSelectionRange().x;
		int end = start + ta.getSelectionRange().y;
		String text;
		if (start == end)
			text = ta.getText();
		else
			text = ta.getSelectionText();
		if (text.equals(""))
			return;
		boolean strictMode = false;
		if (IJ.isJava18()) {
			// text.matches("^( |\t)*(\"use strict\"|'use strict')");
			String text40 = text.substring(0, Math.min(40, text.length()));
			strictMode = text40.contains("'use strict'") || text40.contains("\"use strict\"");
		}
		text = getJSPrefix("") + text;
		if (IJ.isJava18()) {
			text = "load(\"nashorn:mozilla_compat.js\");" + text;
			if (strictMode)
				text = "'use strict';" + text;
		}
		if (!(IJ.isMacOSX() && !IJ.is64Bit())) {
			// Use JavaScript engine built into Java 6 and later.
			IJ.runPlugIn("ij.plugin.JavaScriptEvaluator", text);
		} else {
			Object js = IJ.runPlugIn("JavaScript", text);
			if (js == null)
				download("/download/tools/JavaScript.jar");
		}
	}

	public void evaluateScript(String ext) {

		if (downloading) {
			IJ.beep();
			IJ.showStatus("Download in progress");
			return;
		}
		if (ext.endsWith(".js")) {
			evaluateJavaScript();
			return;
		}
		if (!getTitle().endsWith(ext))
			setWindowTitle(SaveDialog.setExtension(getTitle(), ext));
		int start = ta.getSelectionRange().x;
		int end = start + ta.getSelectionRange().y;
		String text;
		if (start == end)
			text = ta.getText();
		else
			text = ta.getSelectionText();
		if (text.equals(""))
			return;
		String plugin, url;
		if (ext.equals(".bsh")) {
			plugin = "bsh";
			url = "/plugins/bsh/BeanShell.jar";
		} else {
			// download Jython from http://imagej.net/ij/plugins/jython/
			plugin = "Jython";
			url = "/plugins/jython/Jython.jar";
		}
		Object obj = IJ.runPlugIn(plugin, text);
		if (obj == null)
			download(url);
	}

	private void download(String url) {

		this.downloadUrl = url;
		Thread thread = new Thread(this, "Downloader");
		thread.setPriority(Math.max(thread.getPriority() - 2, Thread.MIN_PRIORITY));
		thread.start();
	}

	void evaluateLine() {

		int start = ta.getSelectionRange().x;
		int end = start + ta.getSelectionRange().y;
		if (end > start) {
			runMacro(false);
			return;
		}
		String text = ta.getText();
		while (start > 0) {
			start--;
			if (text.charAt(start) == '\n') {
				start++;
				break;
			}
		}
		while (end < text.length() - 1) {
			end++;
			if (text.charAt(end) == '\n')
				break;
		}
		ta.setSelection(start, end);
		// ta.setSelectionEnd(end);
		runMacro(false);
	}

	void print() {

		/*
		 * PrintJob pjob = Toolkit.getDefaultToolkit().getPrintJob(this, "Cool Stuff",
		 * p); if (pjob != null) { Graphics pg = pjob.getGraphics( ); if (pg != null) {
		 * String s = ta.getText(); printString(pjob, pg, s); pg.dispose( ); } pjob.end(
		 * ); }
		 */
		PrintDialog dialog = new PrintDialog(getShell());
		PrinterData printerData = dialog.open();
		if (printerData != null) {
			// Create the printer
			Printer printer = new Printer(printerData);
			try {
				// Print the contents of the file
				ta.print(printer);
				/*
				 * Or with options: StyledTextPrintOptions options = new
				 * StyledTextPrintOptions(); options.footer = "\t\t<page>"; options.jobName =
				 * "Example"; options.printLineBackground = true; Runnable runnable =
				 * styledText.print(new Printer(), options); runnable.run();
				 */
			} catch (Exception e) {
				MessageBox mb = new MessageBox(getShell(), SWT.ICON_ERROR | SWT.OK);
				mb.setMessage(e.getMessage());
				mb.open();
			}
			// Dispose the printer
			printer.dispose();
		}
	}

	void printString(PrintJob pjob, Graphics pg, String s) {

		int pageNum = 1;
		int linesForThisPage = 0;
		int linesForThisJob = 0;
		int topMargin = 30;
		int leftMargin = 30;
		int bottomMargin = 30;
		if (!(pg instanceof PrintGraphics))
			throw new IllegalArgumentException("Graphics contextt not PrintGraphics");
		if (IJ.isMacintosh()) {
			topMargin = 0;
			leftMargin = 0;
			bottomMargin = 0;
		}
		StringReader sr = new StringReader(s);
		LineNumberReader lnr = new LineNumberReader(sr);
		String nextLine;
		int pageHeight = pjob.getPageDimension().height - bottomMargin;
		Font helv = new Font(getFontName(), Font.PLAIN, 10);
		pg.setFont(helv);
		FontMetrics fm = pg.getFontMetrics(helv);
		int fontHeight = fm.getHeight();
		int fontDescent = fm.getDescent();
		int curHeight = topMargin;
		try {
			do {
				nextLine = lnr.readLine();
				if (nextLine != null) {
					nextLine = detabLine(nextLine);
					if ((curHeight + fontHeight) > pageHeight) {
						// New Page
						pageNum++;
						linesForThisPage = 0;
						pg.dispose();
						pg = pjob.getGraphics();
						if (pg != null)
							pg.setFont(helv);
						curHeight = topMargin;
					}
					curHeight += fontHeight;
					if (pg != null) {
						pg.drawString(nextLine, leftMargin, curHeight - fontDescent);
						linesForThisPage++;
						linesForThisJob++;
					}
				}
			} while (nextLine != null);
		} catch (EOFException eof) {
			// Fine, ignore
		} catch (Throwable t) { // Anything else
			IJ.handleException(t);
		}
	}

	String detabLine(String s) {

		if (s.indexOf('\t') < 0)
			return s;
		int tabSize = 4;
		StringBuffer sb = new StringBuffer((int) (s.length() * 1.25));
		char c;
		for (int i = 0; i < s.length(); i++) {
			c = s.charAt(i);
			if (c == '\t') {
				for (int j = 0; j < tabSize; j++)
					sb.append(' ');
			} else
				sb.append(c);
		}
		return sb.toString();
	}

	void undo() {

		/*
		 * if (IJ.isWindows()) { IJ.showMessage("Editor", "Press Ctrl-Z to undo");
		 * return; }
		 */
		if (IJ.debugMode)
			IJ.log("Undo1: " + undoBuffer.size());
		int position = ta.getCaretOffset();
		if (undoBuffer.size() > 1) {
			undoBuffer.remove(undoBuffer.size() - 1);
			String text = (String) undoBuffer.get(undoBuffer.size() - 1);
			performingUndo = true;
			ta.setText(text);
			if (position <= text.length())
				ta.setCaretOffset(0);
			if (IJ.debugMode)
				IJ.log("Undo2: " + undoBuffer.size() + " " + text);
		}
	}

	boolean copy() {

		String s;
		s = ta.getSelectionText();
		/*
		 * Clipboard clip = getToolkit().getSystemClipboard(); if (clip!=null) {
		 * StringSelection cont = new StringSelection(s); clip.setContents(cont,this);
		 */
		// StringSelection cont = new StringSelection(s);
		if (s.isEmpty()) {
			return false;
		}
		org.eclipse.swt.dnd.Clipboard clip = new org.eclipse.swt.dnd.Clipboard(Display.getDefault());
		if (clip != null) {
			TextTransfer textTransfer = TextTransfer.getInstance();
			clip.setContents(new Object[] { s }, new Transfer[] { textTransfer });
			return true;
		} else
			return false;
	}

	void cut() {

		if (copy()) {
			int start = ta.getSelectionRange().x;
			int length = ta.getSelectionRange().y;
			ta.replaceTextRange(start, length, "");
			if (IJ.isMacOSX())
				ta.setCaretOffset(start);
		}
	}

	private void assignToRepeatCommand() {

		String title = getTitle();
		if (!(title.endsWith(".ijm") || title.endsWith(".txt") || !title.contains("."))) {
			IJ.error("Assign to Repeat Command", "One or more lines of macro code required.");
			return;
		}
		int start = ta.getSelectionRange().x;
		int end = ta.getSelectionRange().x + ta.getSelectionRange().y;
		String text;
		if (start == end)
			text = ta.getText();
		else
			text = ta.getSelectionText();
		Executer.setAsRepeatCommand(text);
	}

	void paste() {

		String s;
		s = ta.getSelectionText();
		org.eclipse.swt.dnd.Clipboard cb = new org.eclipse.swt.dnd.Clipboard(Display.getDefault());
		TextTransfer transfer = TextTransfer.getInstance();
		s = (String) cb.getContents(transfer);
		if (s != null) {
			int start = ta.getSelectionRange().x;
			int length = ta.getSelectionRange().y;
			ta.replaceTextRange(start, length, s);
			// if (IJ.isMacOSX())
			ta.setCaretOffset(start + s.length());
			checkForCurlyQuotes = true;
		}
	}

	// workaround for TextArea.getCaretPosition() bug on Windows
	private int offset(int pos) {

		if (!IJ.isWindows())
			return 0;
		String text = ta.getText();
		int rcount = 0;
		for (int i = 0; i <= pos; i++) {
			if (text.charAt(i) == '\r')
				rcount++;
		}
		if (IJ.debugMode)
			IJ.log("offset: " + pos + " " + rcount);
		return pos - rcount >= 0 ? rcount : 0;
	}

	void copyToInfo() {

		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null) {
			IJ.noImage();
			return;
		}
		int start = ta.getSelectionRange().x;
		int end = ta.getSelectionRange().x + ta.getSelectionRange().y;
		String text;
		if (start == end)
			text = ta.getText();
		else
			text = ta.getSelectionText();
		imp.setProperty("Info", text);
	}

	@Override
	public void widgetSelected(SelectionEvent e) {

		if (e.widget instanceof Combo) {
			itemStateChanged(e);
		} else {
			actionPerformed(e);
		}
	}

	public void actionPerformed(SelectionEvent e) {

		String what;
		if (e.widget instanceof org.eclipse.swt.widgets.Button) {
			org.eclipse.swt.widgets.Button b = (org.eclipse.swt.widgets.Button) e.widget;
			what = b.getText();// e.getActionCommand();
		} else if (e.widget instanceof org.eclipse.swt.widgets.Combo) {
			org.eclipse.swt.widgets.Combo m = (org.eclipse.swt.widgets.Combo) e.widget;
			what = m.getText();// e.getActionCommand();
			itemStateChanged(e);
		} else {
			org.eclipse.swt.widgets.MenuItem m = (org.eclipse.swt.widgets.MenuItem) e.widget;
			what = m.getText();// e.getActionCommand();
		}
		// int flags = e.getModifiers();
		boolean altKeyDown = (e.stateMask & SWT.ALT) != 0;
		if (e.getSource() == runButton) {
			runMacro(false);
			return;
		} else if (e.getSource() == installButton) {
			String text = ta.getText();
			if (text.contains("macro \"") || text.contains("macro\""))
				installMacros(text, true);
			else
				IJ.error("Editor", "File must contain at least one macro or macro tool.");
			return;
		}
		if ("Save".equals(what))
			save();
		else if ("Compile and Run".equals(what)) {
			compileAndRun();
		} else if ("Run Macro".equals(what)) {
			if (altKeyDown) {
				enableDebugging();
				runMacro(true);
			} else
				runMacro(false);
		} else if ("Debug Macro".equals(what)) {
			enableDebugging();
			runMacro(true);
		} else if ("Step".equals(what))
			setDebugMode(STEP);
		else if ("Trace".equals(what))
			setDebugMode(TRACE);
		else if ("Fast Trace".equals(what))
			setDebugMode(FAST_TRACE);
		else if ("Run".equals(what))
			setDebugMode(RUN_TO_COMPLETION);
		else if ("Run to Insertion Point".equals(what))
			runToInsertionPoint();
		else if ("Abort".equals(what) || "Abort Macro".equals(what)) {
			Interpreter.abort();
			IJ.beep();
		} else if ("Evaluate Line".equals(what))
			evaluateLine();
		else if ("Install Macros".equals(what))
			installMacros(ta.getText(), true);
		else if ("Macro Functions...".equals(what))
			showMacroFunctions();
		else if ("Function Finder...".equals(what))
			functionFinder = new FunctionFinder(this);
		else if ("Evaluate Macro".equals(what))
			evaluateMacro();
		else if ("Evaluate JavaScript".equals(what))
			evaluateJavaScript();
		else if ("Evaluate BeanShell".equals(what))
			evaluateScript(".bsh");
		else if ("Evaluate Python".equals(what))
			evaluateScript(".py");
		else if ("Show Log Window".equals(what))
			showLogWindow();
		else if ("Revert".equals(what))
			revert();
		else if ("Print...".equals(what))
			print();
		else if (what.startsWith("Undo"))
			undo();
		else if (what.startsWith("Paste"))
			paste();
		else if (what.equals("Copy to Image Info"))
			copyToInfo();
		else if (what.startsWith("Copy"))
			copy();
		else if (what.startsWith("Cut"))
			cut();
		else if ("Save As...".equals(what))
			saveAs();
		else if ("Select All".equals(what))
			selectAll();
		else if ("Find...".equals(what))
			find(null);
		else if ("Find Next".equals(what))
			find(searchString);
		else if ("Go to Line...".equals(what))
			gotoLine();
		else if ("Balance".equals(what))
			balance();
		else if ("Detab...".equals(what))
			detab();
		else if ("Zap Gremlins".equals(what))
			zapGremlins();
		else if ("Make Text Larger".equals(what))
			changeFontSize(true);
		else if ("Make Text Smaller".equals(what))
			changeFontSize(false);
		else if ("Monospaced Font".equals(what)) {
			setFont();
		} else if ("Save Settings".equals(what))
			saveSettings();
		else if ("New...".equals(what))
			IJ.run("Text Window");
		else if ("Open...".equals(what))
			IJ.open();
		else if (what.equals("Enter Interactive Mode"))
			enterInteractiveMode();
		else if (what.equals("Assign to Repeat Cmd"))
			assignToRepeatCommand();
		else if (what.endsWith(".ijm") || what.endsWith(".java") || what.endsWith(".js") || what.endsWith(".bsh")
				|| what.endsWith(".py"))
			openExample(what);
		else {
			if (altKeyDown) {
				enableDebugging();
				installer.runMacro(what, this);
			} else
				installer.runMacro(what, null);
		}
	}

	/**
	 * Opens an example from the Help/Examples menu and runs if "Autorun Exampes" is
	 * checked.
	 */
	public static boolean openExample(String name) {

		boolean isJava = name.endsWith(".java");
		boolean isJavaScript = name.endsWith(".js");
		boolean isBeanShell = name.endsWith(".bsh");
		boolean isPython = name.endsWith(".py");
		boolean isMacro = name.endsWith(".ijm");
		if (!(isMacro || isJava || isJavaScript || isBeanShell || isPython))
			return false;
		boolean run = !isJava && !name.contains("_Tool") && Prefs.autoRunExamples;
		int rows = 24;
		int columns = 70;
		int options = MENU_BAR + RUN_BAR;
		if (isMacro)
			options += INSTALL_BUTTON;
		String text = null;
		Editor ed = new Editor(rows, columns, 0, options);
		String dir = "Macro/";
		if (isJava)
			dir = "Java/";
		else if (isJavaScript)
			dir = "JavaScript/";
		else if (isBeanShell)
			dir = "BeanShell/";
		else if (isPython)
			dir = "Python/";
		String url = "http://wsr.imagej.net/download/Examples/" + dir + name;
		text = IJ.openUrlAsString(url);
		if (text.startsWith("<Error: ")) {
			IJ.error("Open Example", text);
			return true;
		}
		ed.create(name, text);
		if (run)
			ed.runMacro(false);
		return true;
	}

	protected void showMacroFunctions() {

		String url = "/developer/macro/functions.html";
		String selText = ta.getSelectionText().replace("\n", " ");
		String[] selectedWords = Tools.split(selText, "/,(,[\"\'&+");
		if (selectedWords.length == 1 && selectedWords[0].length() > 0)
			url += "#" + selectedWords[0];// append selection as hash tag
		IJ.runPlugIn("ij.plugin.BrowserLauncher", IJ.URL2 + url);
	}

	final void runToInsertionPoint() {

		Interpreter interp = Interpreter.getInstance();
		if (interp == null)
			IJ.beep();
		else {
			runToLine = getCurrentLine();
			setDebugMode(RUN_TO_CARET);
		}
	}

	final int getCurrentLine() {

		int pos = ta.getCaretOffset();
		int currentLine = 0;
		String text = ta.getText();
		if (IJ.isWindows())
			text = text.replaceAll("\r\n", "\n");
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count = 0;
		int start = 0, end = 0;
		int len = chars.length;
		for (int i = 0; i < len; i++) {
			if (chars[i] == '\n') {
				count++;
				start = end;
				end = i;
				if (pos >= start && pos < end) {
					currentLine = count;
					break;
				}
			}
		}
		if (currentLine == 0 && pos > end)
			currentLine = count;
		return currentLine;
	}

	final void enableDebugging() {

		step = true;
		int start = ta.getSelectionRange().x;
		int end = ta.getSelectionRange().x + ta.getSelectionRange().y;
		if (start == debugStart && end == debugEnd)
			ta.setSelection(start, start);
	}

	final void setDebugMode(int mode) {

		step = true;
		Interpreter interp = Interpreter.getInstance();
		if (interp != null) {
			if (interp.getDebugger() == null)
				fixLineEndings();
			interp.setDebugger(this);
			interp.setDebugMode(mode);
		}
	}

	@Override
	public void modifyText(ModifyEvent e) {

		textValueChanged(e);
	}

	public void textValueChanged(ModifyEvent e) {

		String text = ta.getText();
		// if (undo2==null || text.length()!=undo2.length()+1 ||
		// text.charAt(text.length()-1)=='\n')
		int length = 0;
		if (!performingUndo) {
			for (int i = 0; i < undoBuffer.size(); i++)
				length += ((String) undoBuffer.get(i)).length();
			if (length < 2000000)
				undoBuffer.add(text);
			else {
				for (int i = 1; i < undoBuffer.size(); i++)
					undoBuffer.set(i - 1, undoBuffer.get(i));
				undoBuffer.set(undoBuffer.size() - 1, text);
			}
		}
		performingUndo = false;
		if (isMacroWindow)
			return;
		// first few textValueChanged events may be bogus
		eventCount++;
		if (eventCount > 2 || !IJ.isMacOSX() && eventCount > 1)
			changes = true;
		if (IJ.isMacOSX()) // screen update bug work around
			ta.setCaretOffset(ta.getCaretOffset());
	}

	public void keyPressed(KeyEvent e) {

	}

	public void mousePressed(MouseEvent e) {

	}

	public void mouseExited(MouseEvent e) {

	}

	public void mouseEntered(MouseEvent e) {

	}

	public void mouseReleased(MouseEvent e) {

	}

	public void mouseClicked(MouseEvent e) {

	}

	private void showLinePos() { // show line numbers in status bar (Norbert Vischer)

		char[] chars = ta.getText().toCharArray();
		if (chars.length > 1e6)
			return;
		int selStart = ta.getSelectionRange().x;
		int selEnd = ta.getSelectionRange().x + ta.getSelectionRange().y;
		int line = 0;
		int startLine = 1;
		int endLine = 1;
		for (int i = 1; i <= chars.length; i++) {
			if (chars[i - 1] == '\n')
				line++;
			if (i == selStart)
				startLine = line + 1;
			if (i <= selEnd)
				endLine = line + 1;
			if (i >= selEnd)
				break;
		}
		String msg = "Line " + startLine;
		if (startLine != endLine) {
			msg += "-" + endLine;
		}
		IJ.showStatus(msg);
	}

	public void keyReleased(org.eclipse.swt.events.KeyEvent e) {

		int pos = ta.getCaretOffset();
		// showLinePos();
		if (insertSpaces && pos > 0 && e.keyCode == SWT.TAB) {
			String spaces = " ";
			for (int i = 1; i < tabInc; i++)
				spaces += " ";
			ta.replaceTextRange(pos - 1, pos, spaces);
		}
		char chara = e.character;
		if (chara == SWT.CR || chara == SWT.LF)
			chara = '\n';
		if (interactiveMode && chara == '\n')
			runMacro(e);
	}

	private void runMacro(org.eclipse.swt.events.KeyEvent e) {

		boolean isScript = getTitle().endsWith(".js");
		String text = ta.getText();
		int pos2 = ta.getCaretOffset() - 2;
		if (pos2 < 0)
			pos2 = 0;
		int pos1 = 0;
		for (int i = pos2; i >= 0; i--) {
			if (i == 0 || text.charAt(i) == '\n') {
				pos1 = i;
				break;
			}
		}
		if (isScript) {
			if (evaluator == null) {
				interpreter = null;
				evaluator = new JavaScriptEvaluator();
			}
		} else {
			if (interpreter == null) {
				evaluator = null;
				interpreter = new Interpreter();
			}
		}
		String code = text.substring(pos1, pos2 + 1);
		if (code.length() == 0 || code.equals("\n"))
			return;
		else if (code.length() <= 6 && code.contains("help")) {
			ta.append("  Type a statement (e.g., \"run('Invert')\") to run it.\n");
			ta.append("  Enter an expression (e.g., \"x/2\" or \"log(2)\") to evaluate it.\n");
			ta.append("  Move cursor to end of line and press 'enter' to repeat.\n");
			ta.append("  \"quit\" - exit interactive mode\n");
			ta.append("  " + (IJ.isMacOSX() ? "cmd" : "ctrl") + "+M - enter interactive mode\n");
			if (isScript) {
				ta.append("  \"macro\" - switch language to macro\n");
				ta.append("  \"examples\" - show JavaScript examples\n");
			} else {
				ta.append("  " + (IJ.isMacOSX() ? "cmd" : "ctrl") + "+shift+F - open Function Finder\n");
				ta.append("  \"js\" - switch language to JavaScript\n");
			}
		} else if (isScript && code.length() == 9 && code.contains("examples")) {
			ta.append(JS_EXAMPLES);
		} else if (code.length() <= 3 && code.contains("js")) {
			interactiveMode = false;
			interpreter = null;
			evaluator = null;
			changeExtension(".js");
			enterInteractiveMode();
		} else if (code.length() <= 6 && code.contains("macro")) {
			interactiveMode = false;
			interpreter = null;
			evaluator = null;
			changeExtension(".txt");
			enterInteractiveMode();
		} else if (code.length() <= 6 && code.contains("quit")) {
			interactiveMode = false;
			interpreter = null;
			evaluator = null;
			ta.append("[Exiting interactive mode.]\n");
		} else if (isScript) {
			boolean updateImage = code.contains("ip.");
			code = "load(\"nashorn:mozilla_compat.js\");" + JavaScriptIncludes + code;
			String rtn = evaluator.eval(code);
			if (rtn != null && rtn.length() > 0) {
				int index = rtn.indexOf("at line number ");
				if (index > -1)
					rtn = rtn.substring(0, index);
				insertText(rtn);
			}
			if (updateImage && (rtn == null || rtn.length() == 0)) {
				ImagePlus imp = WindowManager.getCurrentImage();
				if (imp != null)
					imp.updateAndDraw();
			}
		} else if (!code.startsWith("[Macro ") && !code.contains("waitForUser")) {
			String rtn = interpreter.eval(code);
			if (rtn != null)
				insertText(rtn);
		}
		ta.setSelection(ta.getText().length());
	}

	private void changeExtension(String ext) {

		String title = getTitle();
		int index = title.indexOf(".");
		if (index > -1)
			title = title.substring(0, index);
		getShell().setText(title + ext);
	}

	private void enterInteractiveMode() {

		if (interactiveMode)
			return;
		String title = getTitle();
		if (ta != null && ta.getText().length() > 400
				&& !(title.startsWith("Untitled") || title.startsWith(INTERACTIVE_NAME))) {
			GenericDialog gd = new GenericDialog("Enter Interactive Mode");
			gd.addMessage("Enter mode that supports interactive\nediting and running of macros and scripts?");
			gd.setOKLabel("Enter");
			gd.showDialog();
			if (gd.wasCanceled())
				return;
		}
		String language = title.endsWith(".js") ? "JavaScript " : "Macro ";
		messageCount++;
		String help = messageCount <= 2 ? " Type \"help\" for info." : "";
		ta.append("[" + language + "interactive mode." + help + "]\n");
		interactiveMode = true;
	}

	public void insertText(String text) {

		if (ta == null)
			return;
		int start = ta.getSelectionRange().x;
		String tex = text + "\n";
		ta.replaceTextRange(start, 0, "  " + tex);
		// ta.setSelection(ta.getText().length());
	}

	public void keyTyped(KeyEvent e) {

	}

	public void itemStateChanged(SelectionEvent e) {

		org.eclipse.swt.widgets.Combo m = (org.eclipse.swt.widgets.Combo) e.widget;
		String cmd = m.getText();// e.getActionCommand();
		// String cmd = e.text;
		if (e.getSource() == language) {
			setExtension(cmd);
			return;
		}
		// Combo item = (Combo) e.getSource();
		/*
		 * if ("Tab Key Inserts Spaces".equals(cmd)) { insertSpaces = e.getStateChange()
		 * == 1; Prefs.set(INSERT_SPACES, insertSpaces); } else setFont();
		 */
	}

	private void setExtension(String language) {

		String title = getTitle();
		int dot = title.lastIndexOf(".");
		if (dot >= 0)
			title = title.substring(0, dot);
		for (int i = 0; i < languages.length; i++) {
			if (language.equals(languages[i])) {
				title += extensions[i];
				break;
			}
		}
		setWindowTitle(title);
	}

	/**
	 * Override windowActivated in PlugInFrame to prevent Mac menu bar from being
	 * installed.
	 */
	public void windowActivated(WindowEvent e) {

		if (IJ.debugMode)
			IJ.log("Editor.windowActivated");
		WindowManager.setWindow(this);
		instance = this;
	}

	/** Overrides shellClosed() in PlugInFrame. */
	public void shellClosed(ShellEvent e) {

		e.doit = false;
		close();
		if (okayToClose) {
			e.doit = true;
			super.shellClosed(e);
		}
	}

	/** Overrides close() in PlugInFrame. */
	public void close() {

		okayToClose = true;
		ImageJ ij = IJ.getInstance();
		if (!getTitle().equals("Errors") && changes && !IJ.isMacro() && ij != null && !ij.quittingViaMacro()) {
			String msg = "Save changes to \"" + getTitle() + "\"?";
			YesNoCancelDialog d = new YesNoCancelDialog("Editor", msg);
			if (d.cancelPressed())
				okayToClose = false;
			else if (d.yesPressed())
				save();
		}
		if (okayToClose) {
			// setVisible(false);
			// dispose();
			WindowManager.removeWindow(this);
			nWindows--;
			instance = null;
			changes = false;
			if (functionFinder != null)
				functionFinder.close();
			if (lineStyler != null) {
				lineStyler.disposeColors();
			}
			// Necessary to dispose the listener for the styling of keywords, etc.?
			// ta.removeLineStyleListener(lineStyler);
		}
		if (font != null && !font.isDisposed())
			font.dispose();
		if (fontNew != null && !fontNew.isDisposed())
			fontNew.dispose();
	}

	public void saveAs() {

		String name1 = getTitle();
		if (name1.indexOf(".") == -1)
			name1 += ".txt";
		if (defaultDir != null && name1.endsWith(".java") && !defaultDir.startsWith(Menus.getPlugInsPath())) {
			defaultDir = null;
		}
		if (defaultDir == null) {
			if (name1.endsWith(".txt") || name1.endsWith(".ijm"))
				defaultDir = Menus.getMacrosPath();
			else
				defaultDir = Menus.getPlugInsPath();
		}
		SaveDialog sd = new SaveDialog("Save As...", defaultDir, name1, null);
		String name2 = sd.getFileName();
		String dir = sd.getDirectory();
		if (name2 != null) {
			if (name2.endsWith(".java"))
				updateClassName(name1, name2);
			path = dir + name2;
			save();
			changes = false;
			setWindowTitle(name2);
			setDefaultDirectory(dir);
			if (defaultDir != null)
				Prefs.set(DEFAULT_DIR, defaultDir);
			if (IJ.recording())
				Recorder.record("saveAs", "Text", path);
		}
	}

	protected void revert() {

		if (!changes)
			return;
		String title = getTitle();
		if (path == null || !(new File(path).exists()) || !path.endsWith(title)) {
			IJ.showStatus("Cannot revert, no file " + getTitle());
			return;
		}
		if (!IJ.showMessageWithCancel("Revert?", "Revert to saved version of\n\"" + getTitle() + "\"?"))
			return;
		String directory = path.substring(0, path.length() - title.length());
		open(directory, title);
		undoBuffer = new ArrayList();
	}

	/** Changes a plugins class name to reflect a new file name. */
	public void updateClassName(String oldName, String newName) {

		if (newName.indexOf("_") < 0)
			IJ.showMessage("Plugin Editor", "Plugins without an underscore in their name will not\n"
					+ "be automatically installed when ImageJ is restarted.");
		if (oldName.equals(newName) || !oldName.endsWith(".java") || !newName.endsWith(".java"))
			return;
		oldName = oldName.substring(0, oldName.length() - 5);
		newName = newName.substring(0, newName.length() - 5);
		String text1 = ta.getText();
		int index = text1.indexOf("public class " + oldName);
		if (index < 0)
			return;
		String text2 = text1.substring(0, index + 13) + newName
				+ text1.substring(index + 13 + oldName.length(), text1.length());
		ta.setText(text2);
	}

	void find(String s) {

		if (s == null) {
			GenericDialog gd = new GenericDialog("Find");
			gd.addStringField("Find: ", searchString, 20);
			String[] labels = { "Case Sensitive", "Whole Words" };
			boolean[] states = { caseSensitive, wholeWords };
			gd.addCheckboxGroup(1, 2, labels, states);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			s = gd.getNextString();
			caseSensitive = gd.getNextBoolean();
			wholeWords = gd.getNextBoolean();
			Prefs.set(CASE_SENSITIVE, caseSensitive);
		}
		if (s.equals(""))
			return;
		String text = ta.getText();
		String s2 = s;
		if (!caseSensitive) {
			text = text.toLowerCase(Locale.US);
			s = s.toLowerCase(Locale.US);
		}
		int index = -1;
		if (wholeWords) {
			int position = ta.getCaretOffset() + 1;
			while (true) {
				index = text.indexOf(s, position);
				if (index == -1)
					break;
				if (isWholeWordMatch(text, s, index))
					break;
				position = index + 1;
				if (position >= text.length() - 1) {
					index = -1;
					break;
				}
			}
		} else
			index = text.indexOf(s, ta.getCaretOffset() + 1);
		searchString = s2;
		if (index < 0) {
			IJ.beep();
			return;
		}
		ta.setSelection(index, index + s.length());
	}

	boolean isWholeWordMatch(String text, String word, int index) {

		char c = index == 0 ? ' ' : text.charAt(index - 1);
		if (Character.isLetterOrDigit(c) || c == '_')
			return false;
		c = index + word.length() >= text.length() ? ' ' : text.charAt(index + word.length());
		if (Character.isLetterOrDigit(c) || c == '_')
			return false;
		return true;
	}

	void gotoLine() {

		GenericDialog gd = new GenericDialog("Go to Line");
		gd.addNumericField("Go to line number: ", lineNumber, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int n = (int) gd.getNextNumber();
		if (n < 1)
			return;
		String text = ta.getText();
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count = 1, loc = 0;
		for (int i = 0; i < chars.length; i++) {
			if (chars[i] == '\n')
				count++;
			if (count == n) {
				loc = i + 1;
				break;
			}
		}
		ta.setCaretOffset(loc);
		lineNumber = n;
	}

	// extracts characters "({[]})" as string and removes inner pairs
	private void balance() { // modified: N.Vischer

		String text = ta.getText();
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		maskComments(chars);
		maskQuotes(chars);
		int position = ta.getCaretOffset();
		if (position == 0) {
			IJ.error("Balance",
					"This command locates the pair of brackets, curly braces or\nparentheses that surround the insertion point.");
			return;
		}
		int start = -1;
		int stop = -1;
		String leftBows = "";
		for (int i = position - 1; i >= 0; i--) {
			char ch = chars[i];
			if ("({[]})".indexOf(ch) >= 0) {
				leftBows = ch + leftBows;
				leftBows = leftBows.replace("[]", "");// skip nested pairs
				leftBows = leftBows.replace("()", "");
				leftBows = leftBows.replace("{}", "");
				if (leftBows.equals("[") || leftBows.equals("{") || leftBows.equals("(")) {
					start = i;
					break;
				}
			}
		}
		String rightBows = "";
		for (int i = position; i < chars.length; i++) {
			char ch = chars[i];
			if ("({[]})".indexOf(ch) >= 0) {
				rightBows += ch;
				rightBows = rightBows.replace("[]", "");// skip nested pairs
				rightBows = rightBows.replace("()", "");
				rightBows = rightBows.replace("{}", "");
				String pair = leftBows + rightBows;
				if (pair.equals("[]") || pair.equals("{}") || pair.equals("()")) {
					stop = i;
					break;
				}
			}
		}
		if (start == -1 || stop == -1) {
			IJ.beep();
			return;
		}
		// ta.setSelectionStart(start);
		// ta.setSelectionEnd(stop + 1);
		ta.setSelection(start, stop + 1);
		IJ.showStatus(chars.length + " " + position + " " + start + " " + stop);
	}

	// replaces contents of comments with blanks
	private void maskComments(char[] chars) {

		int n = chars.length;
		boolean inSlashSlashComment = false;
		boolean inSlashStarComment = false;
		for (int i = 0; i < n - 1; i++) {
			if (chars[i] == '/' && chars[i + 1] == '/')
				inSlashSlashComment = true;
			if (chars[i] == '\n')
				inSlashSlashComment = false;
			if (!inSlashSlashComment) {
				if (chars[i] == '/' && chars[i + 1] == '*')
					inSlashStarComment = true;
				if (chars[i] == '*' && chars[i + 1] == '/')
					inSlashStarComment = false;
			}
			if (inSlashSlashComment || inSlashStarComment)
				chars[i] = ' ';
		}
	}

	// replaces contents of single and double quotes with blanks - N. Vischer
	private void maskQuotes(char[] chars) {

		int n = chars.length;
		char quote = '\'';// single quote
		for (int loop = 1; loop <= 2; loop++) {
			if (loop == 2)
				quote = '"';// double quote
			boolean inQuotes = false;
			int startMask = 0;
			int stopMask = 0;
			for (int i = 0; i < n - 1; i++) {
				boolean escaped = i > 0 && chars[i - 1] == '\\';
				if (chars[i] == '\n')
					inQuotes = false;
				if (chars[i] == quote && !escaped) {
					if (!inQuotes) {
						startMask = i;
						inQuotes = true;
					} else {
						stopMask = i;
						for (int jj = startMask; jj <= stopMask; jj++) {
							chars[jj] = ' ';
						}
						inQuotes = false;
					}
				}
			}
		}
	}

	// replaces contents of comments with blanks
	private void rmaskComments(char[] chars) {

		int n = chars.length;
		boolean inSlashSlashComment = false;
		boolean inSlashStarComment = false;
		for (int i = 0; i < n - 1; i++) {
			if (chars[i] == '/' && chars[i + 1] == '/')
				inSlashSlashComment = true;
			if (chars[i] == '\n')
				inSlashSlashComment = false;
			if (chars[i] == '/' && chars[i + 1] == '*')
				inSlashStarComment = true;
			if (chars[i] == '*' && chars[i + 1] == '/')
				inSlashStarComment = false;
			if (inSlashSlashComment || inSlashStarComment)
				chars[i] = ' ';
		}
	}

	void zapGremlins() {

		String text = ta.getText();
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count = 0;
		boolean inQuotes = false;
		char quoteChar = 0;
		for (int i = 0; i < chars.length; i++) {
			char c = chars[i];
			if (!inQuotes && (c == '"' || c == '\'')) {
				inQuotes = true;
				quoteChar = c;
			} else {
				if (inQuotes && (c == quoteChar || c == '\n'))
					inQuotes = false;
			}
			if (!inQuotes && c != '\n' && c != '\t' && (c < 32 || c > 127)) {
				count++;
				chars[i] = ' ';
			}
		}
		if (count > 0) {
			text = new String(chars);
			ta.setText(text);
		}
		if (count > 0)
			IJ.showMessage("Zap Gremlins", count + " invalid characters converted to spaces");
		else
			IJ.showMessage("Zap Gremlins", "No invalid characters found");
	}

	private void detab() {

		GenericDialog gd = new GenericDialog("Detab");
		gd.addNumericField("Spaces per tab: ", tabInc, 0);
		gd.addCheckbox("Tab key inserts spaces: ", insertSpaces);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int tabInc2 = tabInc;
		tabInc = (int) gd.getNextNumber();
		if (tabInc < 1)
			tabInc = 1;
		if (tabInc > 8)
			tabInc = 8;
		if (tabInc != tabInc2)
			Prefs.set(TAB_INC, tabInc);
		boolean insertSpaces2 = insertSpaces;
		insertSpaces = gd.getNextBoolean();
		if (insertSpaces != insertSpaces2) {
			Prefs.set(INSERT_SPACES, insertSpaces);
			insertSpacesItem.setSelection(insertSpaces);
		}
		int nb = 0;
		int pos = 1;
		String text = ta.getText();
		if (text.indexOf('\t') < 0)
			return;
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		StringBuffer sb = new StringBuffer((int) (chars.length * 1.25));
		for (int i = 0; i < chars.length; i++) {
			char c = chars[i];
			if (c == '\t') {
				nb = tabInc - ((pos - 1) % tabInc);
				while (nb > 0) {
					sb.append(' ');
					++pos;
					--nb;
				}
			} else if (c == '\n') {
				sb.append(c);
				pos = 1;
			} else {
				sb.append(c);
				++pos;
			}
		}
		ta.setText(sb.toString());
	}

	void selectAll() {

		ta.selectAll();
		showLinePos();
	}

	void changeFontSize(boolean larger) {

		int in = fontSizeIndex;
		if (larger) {
			fontSizeIndex++;
			if (fontSizeIndex == sizes.length)
				fontSizeIndex = sizes.length - 1;
		} else {
			fontSizeIndex--;
			if (fontSizeIndex < 0)
				fontSizeIndex = 0;
		}
		IJ.showStatus(sizes[fontSizeIndex] + " point");
		setFont();
	}

	void saveSettings() {

		Prefs.set(FONT_SIZE, fontSizeIndex);
		Prefs.set(FONT_MONO, monospaced.getSelection());
		IJ.showStatus("Font settings saved (size=" + sizes[fontSizeIndex] + ", monospaced=" + monospaced.getSelection()
				+ ")");
	}

	void setFont() {

		FontData[] fD = JFaceResources.getFont(getFontName()).getFontData();
		fD[0].setHeight(sizes[fontSizeIndex]);
		// font = new org.eclipse.swt.graphics.Font(Display.getDefault(),
		// new FontData(getFontName(), sizes[fontSizeIndex], SWT.NORMAL));
		fontNew = new org.eclipse.swt.graphics.Font(Display.getDefault(), fD[0]);
		ta.setFont(fontNew);
		// font.dispose();
	}

	String getFontName() {

		return monospaced.getSelection() ? JFaceResources.TEXT_FONT : "SansSerif";
	}

	public void setFont(org.eclipse.swt.graphics.Font font) {

		Display.getDefault().syncExec(() -> {

			ta.setFont(font);

		});
	}

	public int getFontSize() {

		return sizes[fontSizeIndex];
	}

	public void append(String s) {

		ta.append(s);
	}

	public void setIsMacroWindow(boolean mw) {

		isMacroWindow = mw;
	}

	public static void setDefaultDirectory(String dir) {

		dir = IJ.addSeparator(dir);
		defaultDir = dir;
	}

	public void lostOwnership(Clipboard clip, Transferable cont) {

	}

	public int debug(Interpreter interp, int mode) {

		if (IJ.debugMode)
			IJ.log("debug: " + interp.getLineNumber() + "  " + mode + "  " + interp);
		if (mode == RUN_TO_COMPLETION)
			return 0;
		int n = interp.getLineNumber();
		if (mode == RUN_TO_CARET) {
			if (n == runToLine) {
				mode = STEP;
				interp.setDebugMode(mode);
			} else
				return 0;
		}
		if (!isShellVisible()) { // abort macro if user closes window
			interp.abortMacro();
			return 0;
		}
		if (n == previousLine) {
			previousLine = 0;
			return 0;
		}
		/* Changed for SWT! */
		Object win = WindowManager.getActiveWindow();
		if (win != this)
			IJ.wait(50);
		Display.getDefault().syncExec(() -> {

			getShell().forceActive();

		});
		previousLine = n;
		Display.getDefault().syncExec(() -> {

			text = ta.getText();
			if (IJ.isWindows())
				text = text.replaceAll("\r\n", "\n");
			ta.setText(text);

		});
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count = 1;
		debugStart = 0;
		int len = chars.length;
		debugEnd = len;
		for (int i = 0; i < len; i++) {
			if (chars[i] == '\n')
				count++;
			if (count == n && debugStart == 0)
				debugStart = i + 1;
			else if (count == n + 1) {
				debugEnd = i;
				break;
			}
		}
		// IJ.log("debug: "+debugStart+" "+debugEnd+" "+len+" "+count);
		if (debugStart == 1)
			debugStart = 0;
		if ((debugStart == 0 || debugStart == len) && debugEnd == len)
			return 0; // skip code added with Interpreter.setAdditionalFunctions()
		Display.getDefault().syncExec(() -> {

			ta.setSelection(debugStart, debugEnd);

		});
		if (debugWindow != null && !debugWindow.isVisible()) {
			interp.setDebugger(null);
			debugWindow = null;
		} else
			debugWindow = interp.updateDebugWindow(interp.getVariables(), debugWindow);
		if (debugWindow != null) {
			interp.updateArrayInspector();
			Display.getDefault().syncExec(() -> {

				getShell().forceActive();

			});
		}
		if (mode == STEP) {
			step = false;
			while (!step && !interp.done() && isShellVisible()) {
				IJ.wait(5);
			}
		} else {
			if (mode == FAST_TRACE)
				IJ.wait(5);
			else
				IJ.wait(150);
		}
		return 0;
	}

	private boolean isShellVisible() {

		visible = false;
		Display.getDefault().syncExec(() -> {

			Shell shellEd = getShell();
			if (shellEd != null && !shell.isDisposed()) {
				visible = shellEd.isVisible();
			}

		});
		return visible;
	}

	public static Editor getInstance() {

		return instance;
	}

	public static String getJSPrefix(String arg) {

		if (arg == null)
			arg = "";
		return JavaScriptIncludes + "function getArgument() {return \"" + arg + "\";};";
	}

	/** Changes Windows (CRLF) line separators to line feeds (LF). */
	public void fixLineEndings() {

		if (!IJ.isWindows())
			return;
		String text = ta.getText();
		text = text.replaceAll("\r\n", "\n");
		ta.setText(text);
	}

	public void showLogWindow() {

		Object log = WindowManager.getWindow("Log");
		if (log != null) {
			if (log instanceof Shell) {
				((Shell) log).forceActive();
			} else if (log instanceof WindowSwt) {
				((WindowSwt) log).getShell().forceActive();
			}
		} else
			IJ.log("");
	}

	public boolean fileChanged() {

		return changes;
	}

	/** Downloads BeanShell or Jython interpreter using a separate thread. */
	public void run() {

		if (downloading || downloadUrl == null)
			return;
		downloading = true;
		boolean ok = Macro_Runner.downloadJar(downloadUrl);
		downloading = false;
	}

	@Override
	public void mouseDoubleClick(org.eclipse.swt.events.MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseDown(org.eclipse.swt.events.MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseUp(org.eclipse.swt.events.MouseEvent arg0) {

		showLinePos();
		/* also for mouse clicked! */
		// showLinePos();
	}

	@Override
	public void keyPressed(org.eclipse.swt.events.KeyEvent evt) {

		// IJ.setKeyDown(evt.keyCode);
		completionEditor.keyPressed(evt);
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent arg0) {
		// TODO Auto-generated method stub

	}
}
