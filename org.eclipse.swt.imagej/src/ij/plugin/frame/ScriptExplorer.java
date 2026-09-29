package ij.plugin.frame;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import ij.IJ;
import ij.Menus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.frame.swt.WindowSwt;

/**
 * A file explorer (defaulting to ImageJ's plugins directory, see {@link Menus#getPlugInsPath()})
 * docked beside a tabbed editor area. Clicking a file opens it as a new {@link CTabItem} in the
 * {@link CTabFolder}, each hosting its own embedded {@link Editor} instance (re-using the same
 * "embedded" Editor mode and Shell/Composite reparenting technique already used elsewhere for
 * RoiManager/Recorder). The file explorer pane on the left is collapsible via the toggle button
 * above it.
 */
public class ScriptExplorer extends PlugInFrame implements WindowSwt {

	/** Matches Editor's own private "languages" array/RUN_BAR combo (see Editor.getOptions(String)). */
	private static final String[] LANGUAGES = {"Macro", "JavaScript", "BeanShell", "Python", "Java"};

	private Composite composite;
	private SashForm sashForm;
	private Tree tree;
	private CTabFolder tabFolder;
	private Button toggleButton;
	private Image sidebarIcon;
	private Image gearIcon;
	private Image folderIcon;
	private Image fileIcon;
	private final Map<String, Image> extensionIcons = new HashMap<>();
	private Combo languageCombo;
	private boolean explorerVisible = true;
	private boolean showAllFileTypes = false;
	private int untitledCount = 0;
	private File clipboardFile;
	private final Map<String, CTabItem> openTabsByPath = new HashMap<>();
	private final List<String> rootDirectories = new ArrayList<>();

	/** Persists which directories are expanded, across both a refreshTree() and whole SWTImageJ sessions (see EXPANDED_PATHS_KEY). */
	private static final String EXPANDED_PATHS_KEY = "scriptexplorer.expandedpaths";
	private final Set<String> expandedPaths = new LinkedHashSet<>();

	/** The tree's folder/file icon size in pixels - user-configurable (16 or 32) via the settings menu, persisted across sessions. */
	private static final String ICON_SIZE_KEY = "scriptexplorer.iconsize";
	private int treeIconSize;

	/** Opens the explorer rooted at both ImageJ's plugins and macros directories. */
	public ScriptExplorer() {

		this(Menus.getPlugInsPath(), Menus.getMacrosPath());
	}

	/** Opens the explorer rooted at the given directory only. */
	public ScriptExplorer(String rootDirectory) {

		this(rootDirectory, null);
	}

	/** Opens the explorer rooted at the given directories (either may be null to omit it). */
	public ScriptExplorer(String pluginsDirectory, String macrosDirectory) {

		/* SHELL_TRIM (unlike PlugInFrame's own DIALOG_TRIM|RESIZE default) adds MIN/MAX, which is what gives this window a native maximize/zoom button - and, on macOS, the "Enter Full Screen" affordance that comes with it. */
		super("Script Explorer", SWT.SHELL_TRIM);
		/* Restores which folders were left expanded last time (empty on the very first startup, so everything starts collapsed). */
		String storedExpandedPaths = Prefs.get(EXPANDED_PATHS_KEY, "");
		if(storedExpandedPaths.length() > 0) {
			expandedPaths.addAll(Arrays.asList(storedExpandedPaths.split("\n")));
		}
		/*
		 * Prefs.getInt() explicitly does NOT read back values written by Prefs.set() (its own
		 * Javadoc says so) - it reads raw IJ_Props/IJ_Prefs.txt keys instead. Prefs.get(key,
		 * double) is the one that actually round-trips through Prefs.set()/ijPrefs, which is
		 * why this was silently always falling back to the default on every restart.
		 */
		treeIconSize = (int)Prefs.get(ICON_SIZE_KEY, 16);
		if(treeIconSize != 16 && treeIconSize != 32) {
			treeIconSize = 16;
		}
		Display.getDefault().syncExec(() -> {
			WindowManager.addWindow(this);
			getShell().setLayout(new FillLayout());
			composite = new Composite(getShell(), SWT.NONE);
			composite.setLayout(new GridLayout(1, false));
			createToolbar(composite);
			sashForm = new SashForm(composite, SWT.HORIZONTAL);
			sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			tree = new Tree(sashForm, SWT.BORDER | SWT.MULTI);
			createIcons(getShell().getDisplay());
			tabFolder = new CTabFolder(sashForm, SWT.BORDER);
			tabFolder.setSimple(false);
			sashForm.setWeights(new int[]{25, 75});
			addRootDirectory(pluginsDirectory);
			addRootDirectory(macrosDirectory);
			hookTreeListeners();
			hookDragAndDrop();
			hookContextMenu();
			/*
			 * Unlike RoiManager/Editor when used in OpenChrom's "embedded" mode, nothing external
			 * reparents this composite into an already-visible container when opened from the
			 * Plugins menu - PlugInFrame's own constructor deliberately leaves shell.open()
			 * commented out, so without this the window is fully built but never actually shown.
			 */
			getShell().setSize(900, 600);
			getShell().open();
		});
	}

	/** @return the composite hosting this explorer, for embedding elsewhere (matching RoiManager/Editor's own getComposite()). */
	public Composite getComposite() {

		return composite;
	}

	/**
	 * Overrides close() in PlugInFrame: also closes every still-open tab's embedded Editor
	 * (each one self-registered with WindowManager on construction) before this window's own
	 * Shell/composite cascade-disposes them. PlugInFrame.shellClosed() calls close() BEFORE the
	 * native Shell disposal actually proceeds, so everything here is still fully alive/ordered
	 * safely - unlike a dispose listener, which would fire too late (after children are already
	 * disposed) to call editor.close() meaningfully.
	 */
	@Override
	public void close() {

		if(tree != null && !tree.isDisposed()) {
			captureExpandedPaths();
			Prefs.set(EXPANDED_PATHS_KEY, String.join("\n", expandedPaths));
			/*
			 * Prefs.set() only updates the in-memory preferences map - it's only ever written
			 * to IJ_Prefs.txt by Prefs.savePreferences(), which ij.ImageJ.run() calls once at
			 * its own quit()/dispose(). That never happens when this Editor/ImageJ instance is
			 * embedded in (and closed along with) a host application instead of being quit the
			 * normal standalone way, silently losing this setting between sessions. Saving
			 * right here instead of relying on that app-wide shutdown hook.
			 */
			Prefs.savePreferences();
		}
		if(tabFolder != null && !tabFolder.isDisposed()) {
			for(CTabItem item : tabFolder.getItems()) {
				if(item.getData() instanceof Editor editor) {
					editor.close();
				}
			}
		}
		if(sidebarIcon != null && !sidebarIcon.isDisposed()) {
			sidebarIcon.dispose();
		}
		if(gearIcon != null && !gearIcon.isDisposed()) {
			gearIcon.dispose();
		}
		disposeIcons();
		super.close();
	}

	private void createToolbar(Composite parent) {

		Composite bar = new Composite(parent, SWT.NONE);
		bar.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		bar.setLayout(new GridLayout(6, false));
		toggleButton = new Button(bar, SWT.PUSH);
		sidebarIcon = createSidebarIcon(bar.getDisplay());
		toggleButton.setImage(sidebarIcon);
		toggleButton.setToolTipText("Collapse/expand the file explorer");
		toggleButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				toggleExplorer();
			}
		});
		Button newButton = new Button(bar, SWT.PUSH);
		newButton.setText("New");
		newButton.setToolTipText("Create a new tab in the selected language");
		newButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				createNewFile(languageCombo.getText());
			}
		});
		languageCombo = new Combo(bar, SWT.DROP_DOWN | SWT.READ_ONLY);
		languageCombo.setToolTipText("Language for the next new file");
		languageCombo.setItems(LANGUAGES);
		languageCombo.select(0);
		Button saveButton = new Button(bar, SWT.PUSH);
		saveButton.setText("Save");
		saveButton.setToolTipText("Save the active tab");
		saveButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				saveActiveTab();
			}
		});
		Button runButton = new Button(bar, SWT.PUSH);
		runButton.setText("Run");
		runButton.setToolTipText("Run the active tab (compiles first for .java)");
		runButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				runActiveTab();
			}
		});
		Button settingsButton = new Button(bar, SWT.PUSH);
		gearIcon = createGearIcon(bar.getDisplay());
		settingsButton.setImage(gearIcon);
		settingsButton.setToolTipText("Explorer Settings");
		Menu settingsMenu = new Menu(settingsButton);
		MenuItem showAllItem = new MenuItem(settingsMenu, SWT.CHECK);
		showAllItem.setText("Show All File Types");
		showAllItem.setSelection(showAllFileTypes);
		showAllItem.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				showAllFileTypes = showAllItem.getSelection();
				refreshTree();
			}
		});
		new MenuItem(settingsMenu, SWT.SEPARATOR);
		MenuItem iconSizeItem = new MenuItem(settingsMenu, SWT.PUSH);
		iconSizeItem.setText("Icon Size...");
		iconSizeItem.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				openIconSizeDialog();
			}
		});
		settingsButton.setMenu(settingsMenu);
		settingsButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				settingsMenu.setVisible(true);
			}
		});
	}

	/** @return the Editor embedded in the currently selected tab, or null if there is none. */
	private Editor getActiveEditor() {

		if(tabFolder == null || tabFolder.isDisposed()) {
			return null;
		}
		CTabItem selected = tabFolder.getSelection();
		if(selected != null && selected.getData() instanceof Editor editor) {
			return editor;
		}
		return null;
	}

	private void saveActiveTab() {

		Editor editor = getActiveEditor();
		if(editor != null) {
			editor.save();
		}
	}

	/**
	 * Same dispatch Editor's own Run button uses internally (see Editor.widgetSelected(),
	 * "e.getSource() == runButton"): compile-and-run for .java, otherwise run as a macro/script.
	 * Called directly rather than relying on Editor's own per-tab RUN_BAR button, since that bar
	 * only ever gets added for .ijm/.js/.bsh/.py titles (see Editor.getOptions(String)), never
	 * for .java - this toolbar button works uniformly for every open tab instead.
	 */
	private void runActiveTab() {

		Editor editor = getActiveEditor();
		if(editor == null) {
			return;
		}
		if(editor.getTitle().endsWith(".java")) {
			editor.compileAndRun();
		} else {
			editor.runMacro(false);
		}
	}

	/** Creates a new, empty tab - named "UntitledN.<ext>" for the given language - matching Editor.getOptions(String)'s own extension-to-language mapping. */
	private void createNewFile(String language) {

		untitledCount++;
		String name = "Untitled" + untitledCount + extensionForLanguage(language);
		Editor editor = new Editor(24, 80, 0, Editor.MENU_BAR, true, true);
		editor.create(name, "");
		createTab(editor, name);
	}

	private static String extensionForLanguage(String language) {

		if("JavaScript".equals(language)) {
			return ".js";
		} else if("BeanShell".equals(language)) {
			return ".bsh";
		} else if("Python".equals(language)) {
			return ".py";
		} else if("Java".equals(language)) {
			return ".java";
		}
		return ".ijm"; // Macro
	}

	private void toggleExplorer() {

		explorerVisible = !explorerVisible;
		sashForm.setMaximizedControl(explorerVisible ? null : tabFolder);
	}

	/**
	 * Draws a small "toggle sidebar" glyph (a panel split into two, matching the icon
	 * convention used by most IDEs for this exact action) since no bundled icon resources
	 * exist in SWTImageJ to reuse for generic toolbar actions like this one.
	 */
	private static Image createSidebarIcon(Display display) {

		int size = 16;
		Image image = new Image(display, size, size);
		GC gc = new GC(image);
		gc.setAntialias(SWT.ON);
		gc.setBackground(display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		gc.fillRectangle(0, 0, size, size);
		gc.setForeground(display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND));
		gc.drawRectangle(1, 2, 13, 11);
		gc.drawLine(6, 2, 6, 12);
		gc.setBackground(display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND));
		gc.fillRectangle(2, 3, 4, 10);
		gc.dispose();
		return image;
	}

	/** Draws a simplified gear/"Zahnrad" glyph: a ring of small teeth around a hollow hub. */
	private static Image createGearIcon(Display display) {

		int size = 16;
		Image image = new Image(display, size, size);
		GC gc = new GC(image);
		gc.setAntialias(SWT.ON);
		gc.setBackground(display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		gc.fillRectangle(0, 0, size, size);
		Color fg = display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
		gc.setForeground(fg);
		gc.setBackground(fg);
		int cx = size / 2;
		int cy = size / 2;
		int outerR = 5;
		int toothD = 3;
		for(int i = 0; i < 8; i++) {
			double angle = Math.toRadians(i * 45);
			int tx = (int)Math.round(cx + outerR * Math.cos(angle)) - toothD / 2;
			int ty = (int)Math.round(cy + outerR * Math.sin(angle)) - toothD / 2;
			gc.fillOval(tx, ty, toothD, toothD);
		}
		gc.drawOval(cx - outerR + 1, cy - outerR + 1, (outerR - 1) * 2, (outerR - 1) * 2);
		gc.setBackground(display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		int innerR = 2;
		gc.fillOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);
		gc.dispose();
		return image;
	}

	/** Disposes and recreates folderIcon/fileIcon/extensionIcons at the current treeIconSize. */
	private void createIcons(Display display) {

		folderIcon = createFolderIcon(display, treeIconSize);
		fileIcon = createFileIcon(display, treeIconSize, null, null);
		/* One colored single-letter badge per language/type this explorer distinguishes; anything else (.txt, .jar, ...) just gets the plain fileIcon above. */
		extensionIcons.put(".java", createFileIcon(display, treeIconSize, "J", new Color(display, 0xE7, 0x6F, 0x00)));
		extensionIcons.put(".ijm", createFileIcon(display, treeIconSize, "M", new Color(display, 0x2B, 0x6C, 0xB0)));
		extensionIcons.put(".py", createFileIcon(display, treeIconSize, "P", new Color(display, 0x30, 0x69, 0x98)));
		extensionIcons.put(".bsh", createFileIcon(display, treeIconSize, "B", new Color(display, 0x5A, 0x9B, 0x3F)));
		extensionIcons.put(".js", createFileIcon(display, treeIconSize, "S", new Color(display, 0xB8, 0x8A, 0x00)));
	}

	/** Disposes whatever folderIcon/fileIcon/extensionIcons currently hold, so createIcons() can safely rebuild them at a new size. */
	private void disposeIcons() {

		if(folderIcon != null && !folderIcon.isDisposed()) {
			folderIcon.dispose();
		}
		if(fileIcon != null && !fileIcon.isDisposed()) {
			fileIcon.dispose();
		}
		for(Image icon : extensionIcons.values()) {
			if(icon != null && !icon.isDisposed()) {
				icon.dispose();
			}
		}
		extensionIcons.clear();
	}

	/** Applies and persists a new icon size (16 or 32), rebuilding every icon and the tree so the change is visible immediately. */
	private void setTreeIconSize(int size) {

		if(size == treeIconSize) {
			return;
		}
		treeIconSize = size;
		Prefs.set(ICON_SIZE_KEY, size);
		/* See the matching comment in close() - Prefs.set() alone is never actually written to disk when this ImageJ instance is embedded rather than quit the normal standalone way. */
		Prefs.savePreferences();
		disposeIcons();
		createIcons(tree.getDisplay());
		refreshTree();
	}

	/** Small modal dialog (Settings > "Icon Size...") letting the user pick 16x16 or 32x32 tree icons; the choice is applied and persisted immediately on OK. */
	private void openIconSizeDialog() {

		Shell dialog = new Shell(getShell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		dialog.setText("Icon Size");
		dialog.setLayout(new GridLayout(1, false));
		Button size16Button = new Button(dialog, SWT.RADIO);
		size16Button.setText("16 x 16");
		size16Button.setSelection(treeIconSize == 16);
		Button size32Button = new Button(dialog, SWT.RADIO);
		size32Button.setText("32 x 32");
		size32Button.setSelection(treeIconSize == 32);
		Composite buttons = new Composite(dialog, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
		buttons.setLayout(new GridLayout(2, false));
		Button okButton = new Button(buttons, SWT.PUSH);
		okButton.setText("OK");
		okButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				setTreeIconSize(size16Button.getSelection() ? 16 : 32);
				dialog.close();
			}
		});
		Button cancelButton = new Button(buttons, SWT.PUSH);
		cancelButton.setText("Cancel");
		cancelButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				dialog.close();
			}
		});
		dialog.setDefaultButton(okButton);
		dialog.pack();
		Point parentLocation = getShell().getLocation();
		Point parentSize = getShell().getSize();
		Point dialogSize = dialog.getSize();
		dialog.setLocation(parentLocation.x + (parentSize.x - dialogSize.x) / 2, parentLocation.y + (parentSize.y - dialogSize.y) / 2);
		dialog.open();
		Display display = dialog.getDisplay();
		while(!dialog.isDisposed()) {
			if(!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	/**
	 * Approximates each OS's own default folder-icon color, since SWT has no cross-platform way
	 * to fetch the real native folder icon: modern macOS (Big Sur onward) uses a light blue
	 * Finder folder, while Windows/Linux file browsers still use the classic yellow/manila one.
	 * This is the darker "back" tone; createFolderIcon derives a lighter "front flap" tone from it.
	 */
	private static Color getPlatformFolderColor(Display display) {

		if(IJ.isMacOSX()) {
			return new Color(display, 70, 150, 220);
		}
		return new Color(display, 200, 150, 80);
	}

	/** @return a copy of color with each channel pushed towards white by amount (clamped to 255). */
	private static Color lighten(Display display, Color color, int amount) {

		int r = Math.min(255, color.getRed() + amount);
		int g = Math.min(255, color.getGreen() + amount);
		int b = Math.min(255, color.getBlue() + amount);
		return new Color(display, r, g, b);
	}

	/**
	 * Draws a flat, modern (macOS Big Sur-style) folder glyph for directory tree items, since no
	 * bundled icon resources exist in SWTImageJ: a small rounded tab peeking out top-left, a
	 * rounded body behind it, and a lighter rounded "front flap" layered over most of the body
	 * (with a faint highlight near its top edge) to give it the same two-tone, glossy look as the
	 * reference. Every coordinate below is designed for size=32 and scaled for whatever size is
	 * actually requested (currently 16 or 32, see treeIconSize).
	 */
	private static Image createFolderIcon(Display display, int size) {

		double scale = size / 32.0;
		Image image = new Image(display, size, size);
		GC gc = new GC(image);
		gc.setAntialias(SWT.ON);
		gc.setBackground(display.getSystemColor(SWT.COLOR_LIST_BACKGROUND));
		gc.fillRectangle(0, 0, size, size);
		Color backColor = getPlatformFolderColor(display);
		Color frontColor = lighten(display, backColor, 45);
		Color highlightColor = lighten(display, frontColor, 40);
		int tabX = scaled(3, scale), tabY = scaled(6, scale),
				tabW = scaled(11, scale), tabH = scaled(7, scale),
				tabArc = scaled(3, scale);
		gc.setBackground(backColor);
		gc.fillRoundRectangle(tabX, tabY, tabW, tabH, tabArc, tabArc);
		int bodyX = scaled(2, scale), bodyY = scaled(9, scale),
				bodyW = scaled(28, scale), bodyH = scaled(20, scale),
				bodyArc = scaled(5, scale);
		gc.fillRoundRectangle(bodyX, bodyY, bodyW, bodyH, bodyArc, bodyArc);
		int frontX = bodyX, frontY = bodyY + scaled(4, scale), frontW = bodyW,
				frontH = bodyH - scaled(4, scale);
		gc.setBackground(frontColor);
		gc.fillRoundRectangle(frontX, frontY, frontW, frontH, bodyArc, bodyArc);
		gc.setForeground(highlightColor);
		gc.drawLine(frontX + scaled(3, scale), frontY + scaled(2, scale), frontX + frontW - scaled(3, scale), frontY + scaled(2, scale));
		gc.dispose();
		backColor.dispose();
		frontColor.dispose();
		highlightColor.dispose();
		return image;
	}

	/**
	 * Draws a simple document glyph (a page with a folded top-right corner) for file tree
	 * items. When label/labelColor are given, a small bold single-letter badge is drawn near
	 * the bottom of the page in that color instead of the plain "text lines" placeholder, so
	 * java/macro/python/beanshell/javascript files can be told apart at a glance. labelColor
	 * (if given) is disposed here, since the caller only needs it for this one drawing call.
	 * Every coordinate below is designed for size=32 and scaled for whatever size is actually
	 * requested (currently 16 or 32, see treeIconSize).
	 */
	private static Image createFileIcon(Display display, int size, String label, Color labelColor) {

		double scale = size / 32.0;
		Image image = new Image(display, size, size);
		GC gc = new GC(image);
		gc.setAntialias(SWT.ON);
		gc.setBackground(display.getSystemColor(SWT.COLOR_LIST_BACKGROUND));
		gc.fillRectangle(0, 0, size, size);
		gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
		gc.setForeground(display.getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
		int left = scaled(6, scale), top = scaled(3, scale),
				right = scaled(24, scale), bottom = scaled(29, scale),
				fold = scaled(8, scale);
		int[] outline = {left, top, right - fold, top, right, top + fold, right, bottom, left, bottom};
		gc.fillPolygon(outline);
		gc.drawPolygon(outline);
		gc.drawLine(right - fold, top, right - fold, top + fold);
		gc.drawLine(right - fold, top + fold, right, top + fold);
		if(label != null && labelColor != null) {
			int fontSize = Math.max(6, (int)Math.round(14 * scale));
			Font labelFont = new Font(display, "Arial", fontSize, SWT.BOLD);
			gc.setFont(labelFont);
			gc.setForeground(labelColor);
			org.eclipse.swt.graphics.Point extent = gc.textExtent(label);
			int tx = left + ((right - left) - extent.x) / 2;
			int ty = bottom - extent.y - 1;
			gc.drawText(label, tx, ty, true);
			labelFont.dispose();
			labelColor.dispose();
		} else {
			gc.setForeground(display.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
			int lineLeft = left + scaled(3, scale);
			int lineRight = right - scaled(3, scale);
			int lineRightShort = right - scaled(6, scale);
			gc.drawLine(lineLeft, top + scaled(11, scale), lineRight, top + scaled(11, scale));
			gc.drawLine(lineLeft, top + scaled(16, scale), lineRight, top + scaled(16, scale));
			gc.drawLine(lineLeft, top + scaled(21, scale), lineRightShort, top + scaled(21, scale));
		}
		gc.dispose();
		return image;
	}

	/** Scales one of the size=32-design coordinates above to the actual requested icon size, never below 1px. */
	private static int scaled(int base32Value, double scale) {

		return Math.max(1, (int)Math.round(base32Value * scale));
	}

	/** @return the folder icon for a directory, the matching language badge icon for a recognized extension, or the plain fileIcon otherwise. */
	private Image getIconForFile(File file) {

		if(file.isDirectory()) {
			return folderIcon;
		}
		String name = file.getName().toLowerCase();
		for(Map.Entry<String, Image> entry : extensionIcons.entrySet()) {
			if(name.endsWith(entry.getKey())) {
				return entry.getValue();
			}
		}
		return fileIcon;
	}

	/** Adds another top-level root to the tree, remembering it so {@link #refreshTree()} can rebuild it later (e.g. after toggling "Show All File Types"). */
	private void addRootDirectory(String rootDirectory) {

		if(rootDirectory == null) {
			return;
		}
		rootDirectories.add(rootDirectory);
		buildRootTreeItem(rootDirectory);
	}

	private void buildRootTreeItem(String rootDirectory) {

		File root = new File(rootDirectory);
		if(!root.exists()) {
			return;
		}
		TreeItem rootItem = new TreeItem(tree, SWT.NONE);
		rootItem.setText(root.getName().isEmpty() ? root.getAbsolutePath() : root.getName());
		rootItem.setData(root);
		rootItem.setImage(folderIcon);
		addChildren(rootItem, root);
		restoreExpansionState(rootItem);
	}

	/** Rebuilds the whole tree from the remembered root directories - used when the file-type filter changes. */
	private void refreshTree() {

		captureExpandedPaths();
		tree.removeAll();
		for(String rootDirectory : rootDirectories) {
			buildRootTreeItem(rootDirectory);
		}
	}

	/**
	 * Re-expands a directory item - and recursively whichever of its children were themselves
	 * previously expanded - if its own path is in {@link #expandedPaths}. Used both to restore
	 * state across SWTImageJ sessions (loaded from {@link Prefs} in the constructor) and to
	 * preserve the current expand/collapse state across a {@link #refreshTree()}.
	 */
	private void restoreExpansionState(TreeItem item) {

		if(item.getData() instanceof File file && file.isDirectory() && expandedPaths.contains(file.getAbsolutePath())) {
			loadChildrenIfNeeded(item);
			item.setExpanded(true);
			for(TreeItem child : item.getItems()) {
				restoreExpansionState(child);
			}
		}
	}

	/** Replaces a directory item's lazy-load dummy placeholder with its real children, unless that has already happened. */
	private void loadChildrenIfNeeded(TreeItem item) {

		if(item.getItemCount() == 1 && item.getItem(0).getData() == null && item.getData() instanceof File dir) {
			item.getItem(0).dispose();
			addChildren(item, dir);
		}
	}

	/** Recomputes {@link #expandedPaths} from the tree's current, live expand/collapse state. */
	private void captureExpandedPaths() {

		expandedPaths.clear();
		for(TreeItem rootItem : tree.getItems()) {
			collectExpandedPaths(rootItem, expandedPaths);
		}
	}

	private static void collectExpandedPaths(TreeItem item, Set<String> result) {

		if(item.getExpanded() && item.getData() instanceof File file) {
			result.add(file.getAbsolutePath());
		}
		for(TreeItem child : item.getItems()) {
			collectExpandedPaths(child, result);
		}
	}

	/**
	 * File extensions Editor can actually open/run (see Editor.getOptions(String)/runMacro()),
	 * plus plain ".txt" (macros are commonly saved with that extension too).
	 */
	private static final String[] SUPPORTED_EXTENSIONS = {".ijm", ".js", ".bsh", ".py", ".java", ".txt", ".jar", ".zip", ".tar.gz", ".tgz"};

	/** Archive extensions that can be unpacked (see handleArchiveClick()); a subset of SUPPORTED_EXTENSIONS. */
	private static final String[] ARCHIVE_EXTENSIONS = {".jar", ".zip", ".tar.gz", ".tgz"};

	private static boolean isArchiveFile(File file) {

		String name = file.getName().toLowerCase();
		for(String extension : ARCHIVE_EXTENSIONS) {
			if(name.endsWith(extension)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSupportedFile(File file) {

		String name = file.getName().toLowerCase();
		for(String extension : SUPPORTED_EXTENSIONS) {
			if(name.endsWith(extension)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Populates a directory's children, tagging sub-directories with a lazy-load placeholder
	 * child. Directories are always shown (needed for navigation); files are filtered to
	 * {@link #SUPPORTED_EXTENSIONS} only.
	 */
	private void addChildren(TreeItem parentItem, File dir) {

		File[] files = dir.listFiles();
		if(files == null) {
			return;
		}
		Arrays.sort(files, (a, b) -> {
			if(a.isDirectory() != b.isDirectory()) {
				return a.isDirectory() ? -1 : 1;
			}
			return a.getName().compareToIgnoreCase(b.getName());
		});
		for(File file : files) {
			if(file.isFile() && !showAllFileTypes && !isSupportedFile(file)) {
				continue;
			}
			TreeItem item = new TreeItem(parentItem, SWT.NONE);
			item.setText(file.getName());
			item.setData(file);
			item.setImage(getIconForFile(file));
			if(file.isDirectory()) {
				/* Dummy placeholder child so the expand arrow shows; replaced on first expand. */
				new TreeItem(item, SWT.NONE);
			}
		}
	}

	private void hookTreeListeners() {

		tree.addListener(SWT.Expand, new Listener() {

			@Override
			public void handleEvent(Event event) {

				loadChildrenIfNeeded((TreeItem)event.item);
			}
		});
		/*
		 * DefaultSelection (double-click, or Enter on the selected item) rather than plain
		 * Selection - a single click now just selects the item, since selection alone also drives
		 * the Copy/Delete context menu's enabled-state and shouldn't have the side effect of
		 * opening a tab or popping the "unpack jar?" dialog.
		 */
		tree.addListener(SWT.DefaultSelection, new Listener() {

			@Override
			public void handleEvent(Event event) {

				if(event.item instanceof TreeItem item && item.getData() instanceof File file && file.isFile()) {
					if(isArchiveFile(file)) {
						handleArchiveClick(file);
					} else {
						openFile(file);
					}
				}
			}
		});
	}

	/**
	 * Lets external files (e.g. dragged from the OS file browser) be dropped onto a tree item -
	 * the file is always copied (the original at the drag source is left untouched) into
	 * whatever directory that item represents (its own path if it's a folder, otherwise its
	 * parent folder), then the tree is refreshed so the new file shows up immediately.
	 */
	private void hookDragAndDrop() {

		DropTarget dropTarget = new DropTarget(tree, DND.DROP_COPY);
		dropTarget.setTransfer(new Transfer[]{FileTransfer.getInstance()});
		dropTarget.addDropListener(new DropTargetAdapter() {

			@Override
			public void dragOver(DropTargetEvent event) {

				event.detail = DND.DROP_COPY;
			}

			@Override
			public void drop(DropTargetEvent event) {

				event.detail = DND.DROP_COPY;
				if(!(event.data instanceof String[] paths)) {
					return;
				}
				Point point = tree.toControl(event.x, event.y);
				TreeItem targetItem = tree.getItem(point);
				File targetDirectory = resolveDropTargetDirectory(targetItem);
				if(targetDirectory == null) {
					return;
				}
				for(String path : paths) {
					copyFileInto(new File(path), targetDirectory);
				}
				refreshTree();
			}
		});
		/*
		 * The reverse direction: let the currently selected tree item be dragged OUT to an OS
		 * location (Finder/Explorer, another app, ...). DND.DROP_COPY only, so the OS always
		 * copies the file to wherever it's dropped rather than moving/removing it here.
		 */
		DragSource dragSource = new DragSource(tree, DND.DROP_COPY);
		dragSource.setTransfer(new Transfer[]{FileTransfer.getInstance()});
		dragSource.addDragListener(new DragSourceAdapter() {

			private File draggedFile;

			@Override
			public void dragStart(DragSourceEvent event) {

				TreeItem[] selection = tree.getSelection();
				if(selection.length == 1 && selection[0].getData() instanceof File file) {
					draggedFile = file;
					event.doit = true;
				} else {
					draggedFile = null;
					event.doit = false;
				}
			}

			@Override
			public void dragSetData(DragSourceEvent event) {

				if(draggedFile != null && FileTransfer.getInstance().isSupportedType(event.dataType)) {
					event.data = new String[]{draggedFile.getAbsolutePath()};
				}
			}
		});
	}

	/** @return the directory a drop on the given tree item (or empty space) should land in, or null if that can't be determined. */
	private File resolveDropTargetDirectory(TreeItem item) {

		if(item == null) {
			return rootDirectories.isEmpty() ? null : new File(rootDirectories.get(0));
		}
		if(item.getData() instanceof File file) {
			return file.isDirectory() ? file : file.getParentFile();
		}
		return null;
	}

	/** Copies source (a file OR a whole folder, recursively) into targetDirectory, leaving the original untouched. */
	private void copyFileInto(File source, File targetDirectory) {

		if(source == null || !source.exists() || targetDirectory == null) {
			return;
		}
		File destination = new File(targetDirectory, source.getName());
		try {
			if(source.isDirectory()) {
				copyDirectoryRecursively(source.toPath(), destination.toPath());
			} else {
				Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch(IOException e) {
			IJ.error("Script Explorer", "Could not copy \"" + source.getName() + "\" to " + targetDirectory.getAbsolutePath());
		}
	}

	/** Recursively copies an entire directory tree from source to target, creating target and its sub-folders as needed. */
	private void copyDirectoryRecursively(Path source, Path target) throws IOException {

		try (Stream<Path> walk = Files.walk(source)) {
			for(Path path : (Iterable<Path>)walk::iterator) {
				Path relative = source.relativize(path);
				Path destination = target.resolve(relative);
				if(Files.isDirectory(path)) {
					Files.createDirectories(destination);
				} else {
					Files.createDirectories(destination.getParent());
					Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	/** Recursively deletes a file or a whole folder (its contents first, then itself). */
	private void deleteRecursively(File file) {

		try {
			if(file.isDirectory()) {
				try (Stream<Path> walk = Files.walk(file.toPath())) {
					for(Path path : (Iterable<Path>)walk.sorted(Comparator.reverseOrder())::iterator) {
						Files.delete(path);
					}
				}
			} else {
				Files.delete(file.toPath());
			}
		} catch(IOException e) {
			IJ.error("Script Explorer", "Could not delete \"" + file.getName() + "\"");
		}
	}

	/**
	 * Right-click context menu on the tree: Copy remembers the selected file/folder, Paste
	 * copies it (recursively, if a folder) into whichever item is currently selected (its own
	 * path if a folder, otherwise its parent - same resolution as drag & drop), and Delete
	 * removes the selected file/folder after confirmation. Enabled state is refreshed every
	 * time the menu is about to show, based on the current selection/clipboard.
	 */
	private void hookContextMenu() {

		Menu contextMenu = new Menu(tree);
		tree.setMenu(contextMenu);
		MenuItem newFolderItem = new MenuItem(contextMenu, SWT.PUSH);
		newFolderItem.setText("New Folder...");
		newFolderItem.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				TreeItem[] selection = tree.getSelection();
				TreeItem target = selection.length == 1 ? selection[0] : null;
				File targetDirectory = resolveDropTargetDirectory(target);
				if(targetDirectory == null) {
					return;
				}
				GenericDialog gd = new GenericDialog("New Folder");
				gd.addStringField("Folder name:", "New Folder");
				gd.showDialog();
				if(gd.wasCanceled()) {
					return;
				}
				String folderName = gd.getNextString().trim();
				if(folderName.isEmpty()) {
					return;
				}
				File newFolder = new File(targetDirectory, folderName);
				if(newFolder.exists()) {
					IJ.error("New Folder", "\"" + folderName + "\" already exists in " + targetDirectory.getAbsolutePath());
					return;
				}
				if(!newFolder.mkdirs()) {
					IJ.error("New Folder", "Could not create folder \"" + folderName + "\"");
					return;
				}
				refreshTree();
			}
		});
		new MenuItem(contextMenu, SWT.SEPARATOR);
		MenuItem copyItem = new MenuItem(contextMenu, SWT.PUSH);
		copyItem.setText("Copy");
		copyItem.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				TreeItem[] selection = tree.getSelection();
				if(selection.length == 1 && selection[0].getData() instanceof File file) {
					clipboardFile = file;
				}
			}
		});
		MenuItem pasteItem = new MenuItem(contextMenu, SWT.PUSH);
		pasteItem.setText("Paste\tCmd+V");
		pasteItem.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				pasteClipboard();
			}
		});
		/* Cmd+V (Ctrl+V on Windows/Linux) - same paste as the context menu item above, without having to open it first. */
		tree.addListener(SWT.KeyDown, new Listener() {

			@Override
			public void handleEvent(Event event) {

				if((event.stateMask & SWT.MOD1) != 0 && (event.character == 'v' || event.character == 'V')) {
					pasteClipboard();
				}
			}
		});
		MenuItem renameItem = new MenuItem(contextMenu, SWT.PUSH);
		renameItem.setText("Rename...");
		renameItem.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				TreeItem[] selection = tree.getSelection();
				if(selection.length != 1 || !(selection[0].getData() instanceof File file)) {
					return;
				}
				GenericDialog gd = new GenericDialog("Rename");
				gd.addStringField("New name:", file.getName());
				gd.showDialog();
				if(gd.wasCanceled()) {
					return;
				}
				String newName = gd.getNextString().trim();
				if(newName.isEmpty() || newName.equals(file.getName())) {
					return;
				}
				File renamed = new File(file.getParentFile(), newName);
				if(renamed.exists()) {
					IJ.error("Rename", "\"" + newName + "\" already exists in " + file.getParentFile().getAbsolutePath());
					return;
				}
				if(!file.renameTo(renamed)) {
					IJ.error("Rename", "Could not rename \"" + file.getName() + "\" to \"" + newName + "\"");
					return;
				}
				refreshTree();
			}
		});
		new MenuItem(contextMenu, SWT.SEPARATOR);
		MenuItem deleteItem = new MenuItem(contextMenu, SWT.PUSH);
		deleteItem.setText("Delete");
		deleteItem.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				List<File> files = new ArrayList<>();
				for(TreeItem item : tree.getSelection()) {
					if(item.getData() instanceof File file) {
						files.add(file);
					}
				}
				if(files.isEmpty()) {
					return;
				}
				String message;
				if(files.size() == 1) {
					message = "Delete \"" + files.get(0).getName() + "\"?";
					if(files.get(0).isDirectory()) {
						message += " This will delete the folder and everything inside it.";
					}
				} else {
					message = "Delete " + files.size() + " selected items?";
					boolean anyFolders = false;
					for(File file : files) {
						anyFolders = anyFolders || file.isDirectory();
					}
					if(anyFolders) {
						message += " This will delete any selected folders and everything inside them.";
					}
				}
				if(!IJ.showMessageWithCancel("Delete", message)) {
					return;
				}
				for(File file : files) {
					deleteRecursively(file);
				}
				refreshTree();
			}
		});
		new MenuItem(contextMenu, SWT.SEPARATOR);
		MenuItem createPluginJarItem = new MenuItem(contextMenu, SWT.PUSH);
		createPluginJarItem.setText("Create Plugin Jar...");
		createPluginJarItem.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				createPluginJar();
			}
		});
		contextMenu.addMenuListener(new MenuAdapter() {

			@Override
			public void menuShown(MenuEvent e) {

				TreeItem[] selection = tree.getSelection();
				boolean hasSingleSelection = selection.length == 1 && selection[0].getData() instanceof File;
				boolean hasAnySelection = selection.length >= 1;
				for(TreeItem item : selection) {
					hasAnySelection = hasAnySelection && item.getData() instanceof File;
				}
				copyItem.setEnabled(hasSingleSelection);
				renameItem.setEnabled(hasSingleSelection);
				deleteItem.setEnabled(hasAnySelection);
				createPluginJarItem.setEnabled(hasAnySelection);
				String systemClipboardText = getSystemClipboardText();
				pasteItem.setEnabled(clipboardFile != null || getSystemClipboardFiles() != null || (systemClipboardText != null && !systemClipboardText.isEmpty()));
			}
		});
	}

	/**
	 * Packages the selected files (any selected folders are expanded recursively) into an
	 * ImageJ plugin jar: any *.java sources are compiled together (so a main class plus its own
	 * helper classes all end up in the same jar), *.class files are included as-is, and
	 * anything else is bundled as a resource. A dialog lets the user pick which class is the
	 * plugin's entry point and configure the plugins.config entry (menu location + label) that
	 * makes it show up in a menu once the jar is dropped into the plugins folder.
	 */
	private void createPluginJar() {

		List<File> selectedFiles = new ArrayList<>();
		for(TreeItem item : tree.getSelection()) {
			if(item.getData() instanceof File file) {
				selectedFiles.add(file);
			}
		}
		if(selectedFiles.isEmpty()) {
			return;
		}
		List<File> allFiles = new ArrayList<>();
		for(File file : selectedFiles) {
			collectFilesRecursively(file, allFiles);
		}
		List<File> javaSources = new ArrayList<>();
		List<File> rawClassFiles = new ArrayList<>();
		List<File> resourceFiles = new ArrayList<>();
		for(File file : allFiles) {
			String name = file.getName().toLowerCase();
			if(name.endsWith(".java")) {
				javaSources.add(file);
			} else if(name.endsWith(".class")) {
				rawClassFiles.add(file);
			} else {
				resourceFiles.add(file);
			}
		}
		/*
		 * Also pull in every OTHER .java file sitting in the same folder as anything selected,
		 * regardless of its own package declaration - a package statement alone doesn't mean the
		 * file actually lives in a matching package-path subdirectory (Script Explorer's flat
		 * tree doesn't require that Maven-style layout), so relying on javac to auto-discover an
		 * unselected sibling class via -sourcepath (see compileJavaSources()) only works when
		 * the physical folder layout happens to match the declared package. Always including
		 * every .java neighbor directly is simpler and doesn't depend on that ever being true.
		 */
		Set<File> javaSourceDirs = new LinkedHashSet<>();
		for(File source : javaSources) {
			File parent = source.getParentFile();
			if(parent != null) {
				javaSourceDirs.add(parent);
			}
		}
		Set<File> allJavaSources = new LinkedHashSet<>(javaSources);
		for(File dir : javaSourceDirs) {
			File[] siblings = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".java"));
			if(siblings != null) {
				for(File sibling : siblings) {
					allJavaSources.add(sibling);
				}
			}
		}
		javaSources = new ArrayList<>(allJavaSources);
		if(javaSources.isEmpty() && rawClassFiles.isEmpty()) {
			IJ.error("Create Plugin Jar", "No .java or .class file found in the selection.");
			return;
		}
		File tempOutputDir = null;
		try {
			List<String> candidateClassNames = new ArrayList<>();
			/*
			 * Classes that actually look like a real ImageJ plugin entry point (implements
			 * PlugIn/PlugInFilter) are pulled to the front, so the dialog defaults to one of
			 * those instead of an arbitrary helper/utility class that happens to compile first -
			 * picking one of those as "main" would silently produce a jar that ImageJ can't
			 * actually run as a plugin.
			 */
			List<String> pluginEntryPointClassNames = new ArrayList<>();
			/* fully qualified class name -> its compiled .class file (under tempOutputDir), for every compiled java source. */
			Map<String, File> compiledClassFiles = new LinkedHashMap<>();
			if(!javaSources.isEmpty()) {
				tempOutputDir = Files.createTempDirectory("scriptexplorer_pluginjar").toFile();
				if(!compileJavaSources(javaSources, tempOutputDir)) {
					return; // compileJavaSources() already reported the error
				}
				for(File source : javaSources) {
					String className = extractFullyQualifiedClassName(source);
					if(className == null) {
						continue;
					}
					File classFile = new File(tempOutputDir, className.replace('.', File.separatorChar) + ".class");
					if(classFile.exists()) {
						if(looksLikePluginEntryPoint(source)) {
							pluginEntryPointClassNames.add(className);
						} else {
							candidateClassNames.add(className);
						}
						compiledClassFiles.put(className, classFile);
					}
				}
				candidateClassNames.addAll(0, pluginEntryPointClassNames);
			}
			for(File classFile : rawClassFiles) {
				String name = classFile.getName();
				String simpleName = name.substring(0, name.length() - ".class".length());
				if(!candidateClassNames.contains(simpleName)) {
					candidateClassNames.add(simpleName);
				}
			}
			if(candidateClassNames.isEmpty()) {
				IJ.error("Create Plugin Jar", "Could not determine any class to package (compilation produced no .class files).");
				return;
			}
			String defaultMainClass = candidateClassNames.get(0);
			GenericDialog gd = new GenericDialog("Create Plugin Jar");
			gd.addChoice("Main plugin class:", candidateClassNames.toArray(new String[0]), defaultMainClass);
			gd.addStringField("Menu location:", "Plugins");
			gd.addStringField("Menu label:", defaultMenuLabel(defaultMainClass));
			gd.addStringField("Jar file name:", defaultJarName(defaultMainClass));
			gd.showDialog();
			if(gd.wasCanceled()) {
				return;
			}
			String mainClassName = gd.getNextChoice();
			if(!pluginEntryPointClassNames.isEmpty() && !pluginEntryPointClassNames.contains(mainClassName)) {
				if(!IJ.showMessageWithCancel("Create Plugin Jar", "\"" + mainClassName + "\" doesn't appear to implement PlugIn or PlugInFilter, so ImageJ may not be able to run it as a plugin.\n\nUse it as the main class anyway?")) {
					return;
				}
			}
			String menuLocation = gd.getNextString().trim();
			String menuLabel = gd.getNextString().trim();
			String jarFileName = gd.getNextString().trim();
			if(menuLocation.isEmpty() || menuLabel.isEmpty() || jarFileName.isEmpty()) {
				return;
			}
			if(!jarFileName.toLowerCase().endsWith(".jar")) {
				jarFileName += ".jar";
			}
			/*
			 * Menus.getPlugins() (the actual Plugins-menu scanner) only even looks at a *.jar
			 * file if its OWN file name contains an underscore - without one, our
			 * plugins.config inside it would never be read at all, no matter how correct
			 * everything else is.
			 */
			if(jarFileName.indexOf('_') < 0) {
				String suggestedName = jarFileName.substring(0, jarFileName.length() - ".jar".length()) + "_.jar";
				if(!IJ.showMessageWithCancel("Create Plugin Jar", "ImageJ only scans plugin jars whose file name contains an underscore.\n\nName it \"" + suggestedName + "\" instead?")) {
					return;
				}
				jarFileName = suggestedName;
			}
			TreeItem[] selection = tree.getSelection();
			TreeItem target = selection.length == 1 ? selection[0] : null;
			File targetDirectory = resolveDropTargetDirectory(target);
			if(targetDirectory == null) {
				return;
			}
			String pluginsPath = Menus.getPlugInsPath();
			if(pluginsPath != null && !isWithin(targetDirectory, new File(pluginsPath))) {
				if(!IJ.showMessageWithCancel("Create Plugin Jar", "\"" + targetDirectory.getAbsolutePath() + "\" is not inside the plugins folder (" + pluginsPath + "), so ImageJ won't add it to the Plugins menu.\n\nCreate it here anyway?")) {
					return;
				}
			}
			File jarFile = new File(targetDirectory, jarFileName);
			if(jarFile.exists() && !IJ.showMessageWithCancel("Create Plugin Jar", "\"" + jarFileName + "\" already exists. Overwrite it?")) {
				return;
			}
			String pluginsConfig = menuLocation + ", \"" + menuLabel + "\", " + mainClassName + "\n";
			writePluginJar(jarFile, tempOutputDir, compiledClassFiles.values(), rawClassFiles, resourceFiles, pluginsConfig);
			refreshTree();
			/* Without this, the new jar sits on disk but stays invisible in the Plugins menu until the user runs Help > Refresh Menus (or restarts) themselves. */
			Menus.updateImageJMenus();
			IJ.showStatus("Created " + jarFile.getName() + " and refreshed the Plugins menu");
		} catch(IOException e) {
			IJ.error("Create Plugin Jar", "Could not create plugin jar:\n" + e.getMessage());
		} finally {
			if(tempOutputDir != null) {
				deleteRecursively(tempOutputDir);
			}
		}
	}

	/** Recursively collects every plain file under file (file itself, if it's not a directory). */
	private void collectFilesRecursively(File file, List<File> result) {

		if(file.isDirectory()) {
			File[] children = file.listFiles();
			if(children != null) {
				for(File child : children) {
					collectFilesRecursively(child, result);
				}
			}
		} else {
			result.add(file);
		}
	}

	/** @return whether child is ancestor itself, or is nested (at any depth) inside it. */
	private static boolean isWithin(File child, File ancestor) {

		File current = child.getAbsoluteFile();
		File target = ancestor.getAbsoluteFile();
		while(current != null) {
			if(current.equals(target)) {
				return true;
			}
			current = current.getParentFile();
		}
		return false;
	}

	/**
	 * Compiles every given .java source together into outputDir (javac preserves each class's
	 * own package directory structure there), using the currently running JVM's own classpath
	 * so ij.* (and anything else already loaded) references resolve without extra setup - plus
	 * an explicit -sourcepath covering each source's own package root, so a helper class in the
	 * same folder/package that the user didn't happen to select too (e.g. a plugin's main class
	 * referencing a sibling "ChatWindow" class) still gets found and compiled automatically,
	 * instead of failing with "symbol not found".
	 */
	private boolean compileJavaSources(List<File> javaSources, File outputDir) {

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if(compiler == null) {
			IJ.error("Create Plugin Jar", "No system Java compiler available (running on a JRE rather than a JDK?).");
			return false;
		}
		Set<String> sourceRoots = new LinkedHashSet<>();
		for(File source : javaSources) {
			File root = packageRootDirectory(source);
			if(root != null) {
				sourceRoots.add(root.getAbsolutePath());
			}
		}
		String sourcePath = String.join(File.pathSeparator, sourceRoots);
		List<String> options = new ArrayList<>();
		options.add("-d");
		options.add(outputDir.getAbsolutePath());
		options.add("-classpath");
		options.add(sourceRoots.isEmpty() ? System.getProperty("java.class.path") : System.getProperty("java.class.path") + File.pathSeparator + sourcePath);
		if(!sourceRoots.isEmpty()) {
			options.add("-sourcepath");
			options.add(sourcePath);
		}
		List<String> sourcePaths = new ArrayList<>();
		for(File source : javaSources) {
			sourcePaths.add(source.getAbsolutePath());
		}
		StringWriter diagnosticsOutput = new StringWriter();
		boolean success;
		try(StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
			Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(sourcePaths);
			JavaCompiler.CompilationTask task = compiler.getTask(diagnosticsOutput, fileManager, null, options, null, compilationUnits);
			success = task.call();
		} catch(IOException e) {
			IJ.error("Create Plugin Jar", "Could not compile:\n" + e.getMessage());
			return false;
		}
		if(!success) {
			IJ.error("Create Plugin Jar", "Compilation failed:\n" + diagnosticsOutput);
		}
		return success;
	}

	/**
	 * Best-effort check for whether a .java source declares a class that implements
	 * ij.plugin.PlugIn or ij.plugin.filter.PlugInFilter - i.e. whether it could actually work as
	 * an ImageJ plugin's entry point, as opposed to a helper/utility class. Used only to pick a
	 * sensible default (and warn on an unusual choice) in the "Create Plugin Jar" dialog, so a
	 * simple text scan is enough - it doesn't need to be a real type-aware check.
	 */
	private static boolean looksLikePluginEntryPoint(File javaSource) {

		try {
			String text = Files.readString(javaSource.toPath());
			return Pattern.compile("(?m)^\\s*public\\s+(?:final\\s+|abstract\\s+)*class\\s+\\w+[^{;]*\\bimplements\\b[^{;]*\\bPlugIn(Filter)?\\b").matcher(text).find();
		} catch(IOException e) {
			return false;
		}
	}

	/** Best-effort extraction of a .java source's own "package ...;" declaration, or null if it's in the default package - a simple text scan, no real parser needed for this. */
	private static String extractPackageName(File javaSource) {

		try {
			String text = Files.readString(javaSource.toPath());
			Matcher packageMatcher = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(text);
			return packageMatcher.find() ? packageMatcher.group(1) : null;
		} catch(IOException e) {
			return null;
		}
	}

	/**
	 * @return the directory that a .java source's package structure is rooted at - i.e. its
	 *         parent directory, walked back up once per package segment (e.g. a class in
	 *         "com.example" two directories below its source root walks back up two levels) -
	 *         or just its parent directory directly if it's in the default package. This is
	 *         what javac's -sourcepath needs to actually find the file by its class name.
	 */
	private static File packageRootDirectory(File javaSource) {

		String packageName = extractPackageName(javaSource);
		File dir = javaSource.getParentFile();
		if(packageName != null && !packageName.isEmpty()) {
			int segments = packageName.split("\\.").length;
			for(int i = 0; i < segments && dir != null; i++) {
				dir = dir.getParentFile();
			}
		}
		return dir;
	}

	/** Best-effort extraction of a .java source's fully qualified (package + public class) name - a simple text scan, no real parser needed for this. */
	private static String extractFullyQualifiedClassName(File javaSource) {

		try {
			String text = Files.readString(javaSource.toPath());
			String packageName = extractPackageName(javaSource);
			Matcher classMatcher = Pattern.compile("(?m)^\\s*public\\s+(?:final\\s+|abstract\\s+)*class\\s+(\\w+)").matcher(text);
			String className;
			if(classMatcher.find()) {
				className = classMatcher.group(1);
			} else {
				/* No public class found (e.g. a package-private helper class) - Java requires its file name to match anyway. */
				String fileName = javaSource.getName();
				className = fileName.substring(0, fileName.length() - ".java".length());
			}
			return packageName == null ? className : packageName + "." + className;
		} catch(IOException e) {
			return null;
		}
	}

	private static String defaultJarName(String mainClassName) {

		int lastDot = mainClassName.lastIndexOf('.');
		String simpleName = lastDot >= 0 ? mainClassName.substring(lastDot + 1) : mainClassName;
		return simpleName + ".jar";
	}

	/** Derives a human-friendly menu label from a class name, matching ImageJ's own "Foo_Bar_" -> "Foo Bar" convention. */
	private static String defaultMenuLabel(String mainClassName) {

		int lastDot = mainClassName.lastIndexOf('.');
		String simpleName = lastDot >= 0 ? mainClassName.substring(lastDot + 1) : mainClassName;
		while(simpleName.endsWith("_")) {
			simpleName = simpleName.substring(0, simpleName.length() - 1);
		}
		return simpleName.replace('_', ' ').trim();
	}

	/**
	 * Writes a new plugin jar containing: every compiled class under compiledClassFilesRoot (at
	 * its own package-derived path), every raw *.class file the user selected directly (at the
	 * jar root), every other selected file as a plain resource (at the jar root), and a
	 * plugins.config entry so the plugin shows up in a menu once this jar is in the plugins
	 * folder.
	 */
	private void writePluginJar(File jarFile, File compiledClassFilesRoot, Collection<File> compiledClassFiles, List<File> rawClassFiles, List<File> resourceFiles, String pluginsConfig) throws IOException {

		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		Set<String> writtenEntries = new HashSet<>();
		try(JarOutputStream jar = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarFile)), manifest)) {
			jar.putNextEntry(new JarEntry("plugins.config"));
			jar.write(pluginsConfig.getBytes(StandardCharsets.UTF_8));
			jar.closeEntry();
			writtenEntries.add("plugins.config");
			for(File classFile : compiledClassFiles) {
				String entryName = compiledClassFilesRoot.toPath().relativize(classFile.toPath()).toString();
				addJarEntry(jar, entryName, classFile, writtenEntries);
			}
			for(File classFile : rawClassFiles) {
				addJarEntry(jar, classFile.getName(), classFile, writtenEntries);
			}
			for(File resource : resourceFiles) {
				addJarEntry(jar, resource.getName(), resource, writtenEntries);
			}
		}
	}

	private static void addJarEntry(JarOutputStream jar, String entryName, File sourceFile, Set<String> writtenEntries) throws IOException {

		entryName = entryName.replace(File.separatorChar, '/');
		if(!writtenEntries.add(entryName)) {
			return;
		}
		jar.putNextEntry(new JarEntry(entryName));
		Files.copy(sourceFile.toPath(), jar);
		jar.closeEntry();
	}

	/**
	 * Pastes clipboardFile (copied via this tree's own "Copy") if there is one; otherwise falls
	 * back to whatever file(s) are currently on the OS clipboard (e.g. copied in Finder); and
	 * failing that, to plain copied text (e.g. from a text editor), which is written out as a
	 * new file (see pasteTextAsNewFile()). Always targets whichever item is currently selected
	 * (its own path if a folder, otherwise its parent - same resolution as drag & drop). Shared
	 * by the "Paste" context menu item and the Cmd+V key binding.
	 */
	private void pasteClipboard() {

		TreeItem[] selection = tree.getSelection();
		TreeItem target = selection.length == 1 ? selection[0] : null;
		File targetDirectory = resolveDropTargetDirectory(target);
		if(targetDirectory == null) {
			return;
		}
		if(clipboardFile != null) {
			copyFileInto(clipboardFile, targetDirectory);
			refreshTree();
			return;
		}
		File[] systemClipboardFiles = getSystemClipboardFiles();
		if(systemClipboardFiles != null) {
			for(File file : systemClipboardFiles) {
				copyFileInto(file, targetDirectory);
			}
			refreshTree();
			return;
		}
		String text = getSystemClipboardText();
		if(text != null && !text.isEmpty()) {
			pasteTextAsNewFile(text, targetDirectory);
		}
	}

	/** Prompts for a file name, then writes text into a new file with that name in targetDirectory. */
	private void pasteTextAsNewFile(String text, File targetDirectory) {

		GenericDialog gd = new GenericDialog("Paste as New File");
		gd.addStringField("File name:", "Untitled.txt");
		gd.showDialog();
		if(gd.wasCanceled()) {
			return;
		}
		String fileName = gd.getNextString().trim();
		if(fileName.isEmpty()) {
			return;
		}
		File newFile = new File(targetDirectory, fileName);
		if(newFile.exists()) {
			IJ.error("Paste as New File", "\"" + fileName + "\" already exists in " + targetDirectory.getAbsolutePath());
			return;
		}
		try {
			Files.writeString(newFile.toPath(), text);
		} catch(IOException e) {
			IJ.error("Paste as New File", "Could not create \"" + fileName + "\":\n" + e.getMessage());
			return;
		}
		refreshTree();
	}

	/** @return the file paths currently on the OS clipboard (e.g. copied in Finder/Explorer), or null if there are none. */
	private File[] getSystemClipboardFiles() {

		Clipboard clipboard = new Clipboard(tree.getDisplay());
		try {
			Object contents = clipboard.getContents(FileTransfer.getInstance());
			if(!(contents instanceof String[] paths) || paths.length == 0) {
				return null;
			}
			File[] files = new File[paths.length];
			for(int i = 0; i < paths.length; i++) {
				files[i] = new File(paths[i]);
			}
			return files;
		} finally {
			clipboard.dispose();
		}
	}

	/** @return the plain text currently on the OS clipboard (e.g. copied in a text editor), or null if there is none. */
	private String getSystemClipboardText() {

		Clipboard clipboard = new Clipboard(tree.getDisplay());
		try {
			Object contents = clipboard.getContents(TextTransfer.getInstance());
			return contents instanceof String text ? text : null;
		} finally {
			clipboard.dispose();
		}
	}

	/** Strips a recognized archive extension off a filename (".tar.gz" specially, since it has two dots) for the destination folder's name. */
	private static String stripArchiveExtension(String fileName) {

		if(fileName.toLowerCase().endsWith(".tar.gz")) {
			return fileName.substring(0, fileName.length() - ".tar.gz".length());
		}
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

	/**
	 * Asks whether to unpack the clicked archive (*.jar/*.zip/*.tar.gz/*.tgz), and if confirmed,
	 * extracts it into a new sibling folder (named after the archive, without the extension) in
	 * the same directory.
	 */
	private void handleArchiveClick(File archiveFile) {

		boolean unpack = IJ.showMessageWithCancel("Unpack Archive", "Unpack \"" + archiveFile.getName() + "\"?");
		if(!unpack) {
			return;
		}
		File destination = new File(archiveFile.getParentFile(), stripArchiveExtension(archiveFile.getName()));
		if(!destination.exists() && !destination.mkdir()) {
			IJ.error("Unpack Archive", "Could not create folder " + destination.getAbsolutePath());
			return;
		}
		String name = archiveFile.getName().toLowerCase();
		if(name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
			unpackTarGz(archiveFile, destination);
		} else {
			unpackZip(archiveFile, destination);
		}
		refreshTree();
	}

	/** Extracts every entry of the given jar/zip archive into destination, creating sub-folders as needed. */
	private void unpackZip(File zipFile, File destination) {

		byte[] buffer = new byte[8192];
		try(ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
			ZipEntry entry;
			while((entry = zip.getNextEntry()) != null) {
				File outFile = new File(destination, entry.getName());
				if(entry.isDirectory()) {
					outFile.mkdirs();
					continue;
				}
				File parent = outFile.getParentFile();
				if(parent != null && !parent.exists()) {
					parent.mkdirs();
				}
				try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
					int read;
					while((read = zip.read(buffer)) != -1) {
						out.write(buffer, 0, read);
					}
				}
				zip.closeEntry();
			}
		} catch(IOException e) {
			IJ.error("Unpack Archive", "Could not unpack \"" + zipFile.getName() + "\":\n" + e.getMessage());
		}
	}

	/**
	 * Extracts every entry of a gzip-compressed tar archive (*.tar.gz/*.tgz) into destination.
	 * The JDK has no built-in tar reader (unlike zip/jar), so this is a small, self-contained
	 * reader for the classic (POSIX/USTAR) 512-byte-block tar format: a fixed-size header per
	 * entry (name, size, type, and - for names too long for the 100-byte name field - a USTAR
	 * "prefix" field) followed by the entry's content, both padded up to the next 512-byte
	 * boundary.
	 */
	private void unpackTarGz(File archiveFile, File destination) {

		byte[] header = new byte[512];
		byte[] buffer = new byte[8192];
		try(InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(archiveFile.toPath())))) {
			while(true) {
				if(readFully(in, header) < header.length || isAllZero(header)) {
					break; // truncated stream, or the two all-zero end-of-archive blocks
				}
				String name = readTarString(header, 0, 100);
				String prefix = readTarString(header, 345, 155);
				if(!prefix.isEmpty()) {
					name = prefix + "/" + name;
				}
				long size = readTarOctal(header, 124, 12);
				char typeFlag = (char)header[156];
				File outFile = new File(destination, name);
				if(typeFlag == '5' || name.endsWith("/")) {
					outFile.mkdirs();
				} else if(typeFlag == '0' || typeFlag == 0) {
					File parent = outFile.getParentFile();
					if(parent != null && !parent.exists()) {
						parent.mkdirs();
					}
					try(OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
						long remaining = size;
						while(remaining > 0) {
							int n = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
							if(n < 0) {
								break;
							}
							out.write(buffer, 0, n);
							remaining -= n;
						}
					}
				} else {
					skipFully(in, size); // symlink/hardlink/other entry types: skip content, nothing to extract
				}
				skipFully(in, (512 - (size % 512)) % 512); // content is padded up to the next 512-byte boundary
			}
		} catch(IOException e) {
			IJ.error("Unpack Archive", "Could not unpack \"" + archiveFile.getName() + "\":\n" + e.getMessage());
		}
	}

	private static int readFully(InputStream in, byte[] buf) throws IOException {

		int total = 0;
		while(total < buf.length) {
			int n = in.read(buf, total, buf.length - total);
			if(n < 0) {
				break;
			}
			total += n;
		}
		return total;
	}

	private static void skipFully(InputStream in, long count) throws IOException {

		byte[] discard = new byte[8192];
		long remaining = count;
		while(remaining > 0) {
			int n = in.read(discard, 0, (int)Math.min(discard.length, remaining));
			if(n < 0) {
				break;
			}
			remaining -= n;
		}
	}

	private static boolean isAllZero(byte[] buf) {

		for(byte b : buf) {
			if(b != 0) {
				return false;
			}
		}
		return true;
	}

	private static String readTarString(byte[] header, int offset, int length) {

		int end = offset;
		int limit = offset + length;
		while(end < limit && header[end] != 0) {
			end++;
		}
		return new String(header, offset, end - offset, java.nio.charset.StandardCharsets.UTF_8);
	}

	private static long readTarOctal(byte[] header, int offset, int length) {

		String s = readTarString(header, offset, length).trim();
		if(s.isEmpty()) {
			return 0;
		}
		try {
			return Long.parseLong(s, 8);
		} catch(NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * Opens the given file as a new tab (embedding a fresh {@link Editor} instance), or just
	 * activates its tab if it's already open.
	 */
	private void openFile(File file) {

		String path = file.getAbsolutePath();
		CTabItem existing = openTabsByPath.get(path);
		if(existing != null && !existing.isDisposed()) {
			tabFolder.setSelection(existing);
			return;
		}
		/*
		 * Uses the explicit-options constructor (MENU_BAR only), not new Editor(name, ...),
		 * which would call the private getOptions(name) and add Editor's own internal Run/
		 * Install/language-combo bar for .ijm/.js/.bsh/.py titles - redundant now that this
		 * explorer's own toolbar Run/Save buttons work uniformly across every open tab.
		 * contextMenu=true additionally builds the same File/Edit/Font/Macros/Debug menu
		 * structure as a right-click popup on the text widget (see Editor's constructor,
		 * "New Constructor to set a StyledText context menu and embedded!") - the Editor's own
		 * top-level Shell (and its normal menu bar) is invisible/off-screen once its composite
		 * is reparented into this explorer's CTabFolder, so the popup is the only way to reach
		 * those menus from an embedded tab.
		 */
		Editor editor = new Editor(24, 80, 0, Editor.MENU_BAR, true, true);
		editor.open(file.getParent() + File.separator, file.getName());
		CTabItem tabItem = createTab(editor, file.getName());
		openTabsByPath.put(path, tabItem);
		/* File-backed tabs also need untracking from openTabsByPath once closed, in addition to createTab's own generic Editor cleanup. */
		tabItem.addDisposeListener(_ -> openTabsByPath.remove(path));
	}

	/**
	 * Wraps the given Editor (already loaded with whatever content it should show) in a new,
	 * closeable tab and selects it.
	 */
	private CTabItem createTab(Editor editor, String tabLabel) {

		/*
		 * Editor (like RoiManager) always creates its own top-level Shell, even in "embedded"
		 * mode - reparenting its composite into a different Composite (here: the CTabFolder
		 * itself, as CTabItem.setControl requires) is the same technique ImageJToolView.
		 * createView() already relies on elsewhere in this codebase to embed RoiManager/Editor/
		 * Recorder into OpenChrom views.
		 */
		Composite editorComposite = editor.getComposite();
		editorComposite.setParent(tabFolder);
		CTabItem tabItem = new CTabItem(tabFolder, SWT.CLOSE);
		tabItem.setText(tabLabel);
		tabItem.setControl(editorComposite);
		tabItem.setData(editor);
		tabFolder.setSelection(tabItem);
		/*
		 * CTabFolder never automatically disposes a tab's control when its CTabItem is
		 * disposed (a common SWT gotcha), so without this the Editor's own top-level Shell -
		 * now invisible/off-screen since its composite was reparented here - would leak as an
		 * orphaned Shell every time a tab is closed.
		 * Calling editor.close() first (rather than disposing the Shell directly) matters: it's
		 * what actually calls WindowManager.removeWindow(this) (every Editor self-registers
		 * there on construction, embedded or not). Skipping it left a disposed-but-still-
		 * registered Editor in WindowManager's list, which later crashed with "Widget is
		 * disposed" the next time anything (e.g. ImageJ's own quit/shutdown routine) iterated
		 * the non-image window list and called getTitle()/fileChanged() on it. close() also
		 * prompts to save unsaved changes first, same as closing an Editor normally would.
		 */
		tabItem.addDisposeListener(_ -> {
			editor.close();
			if(!editor.getShell().isDisposed()) {
				editor.getShell().dispose();
			}
		});
		return tabItem;
	}
}
