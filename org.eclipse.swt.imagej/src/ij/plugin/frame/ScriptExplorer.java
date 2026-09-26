package ij.plugin.frame;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
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
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import ij.IJ;
import ij.Menus;
import ij.Prefs;
import ij.WindowManager;
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

		super("Script Explorer");
		/* Restores which folders were left expanded last time (empty on the very first startup, so everything starts collapsed). */
		String storedExpandedPaths = Prefs.get(EXPANDED_PATHS_KEY, "");
		if(storedExpandedPaths.length() > 0) {
			expandedPaths.addAll(Arrays.asList(storedExpandedPaths.split("\n")));
		}
		Display.getDefault().syncExec(() -> {
			WindowManager.addWindow(this);
			getShell().setLayout(new FillLayout());
			composite = new Composite(getShell(), SWT.NONE);
			composite.setLayout(new GridLayout(1, false));
			createToolbar(composite);
			sashForm = new SashForm(composite, SWT.HORIZONTAL);
			sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			tree = new Tree(sashForm, SWT.BORDER);
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
	private static final String[] SUPPORTED_EXTENSIONS = {".ijm", ".js", ".bsh", ".py", ".java", ".txt", ".jar"};

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
					if(file.getName().toLowerCase().endsWith(".jar")) {
						handleJarClick(file);
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

		try(Stream<Path> walk = Files.walk(source)) {
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
				try(Stream<Path> walk = Files.walk(file.toPath())) {
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
		pasteItem.setText("Paste");
		pasteItem.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				if(clipboardFile == null) {
					return;
				}
				TreeItem[] selection = tree.getSelection();
				TreeItem target = selection.length == 1 ? selection[0] : null;
				File targetDirectory = resolveDropTargetDirectory(target);
				if(targetDirectory == null) {
					return;
				}
				copyFileInto(clipboardFile, targetDirectory);
				refreshTree();
			}
		});
		new MenuItem(contextMenu, SWT.SEPARATOR);
		MenuItem deleteItem = new MenuItem(contextMenu, SWT.PUSH);
		deleteItem.setText("Delete");
		deleteItem.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				TreeItem[] selection = tree.getSelection();
				if(selection.length != 1 || !(selection[0].getData() instanceof File file)) {
					return;
				}
				String message = "Delete \"" + file.getName() + "\"?";
				if(file.isDirectory()) {
					message += " This will delete the folder and everything inside it.";
				}
				if(!IJ.showMessageWithCancel("Delete", message)) {
					return;
				}
				deleteRecursively(file);
				refreshTree();
			}
		});
		contextMenu.addMenuListener(new MenuAdapter() {

			@Override
			public void menuShown(MenuEvent e) {

				TreeItem[] selection = tree.getSelection();
				boolean hasSelection = selection.length == 1 && selection[0].getData() instanceof File;
				copyItem.setEnabled(hasSelection);
				deleteItem.setEnabled(hasSelection);
				pasteItem.setEnabled(clipboardFile != null);
			}
		});
	}

	/**
	 * Asks whether to unpack the clicked *.jar, and if confirmed, extracts it into a new
	 * sibling folder (named after the jar, without the extension) in the same directory.
	 */
	private void handleJarClick(File jarFile) {

		boolean unpack = IJ.showMessageWithCancel("Unpack Jar", "Unpack \"" + jarFile.getName() + "\"?");
		if(!unpack) {
			return;
		}
		String folderName = jarFile.getName();
		int dot = folderName.lastIndexOf('.');
		if(dot > 0) {
			folderName = folderName.substring(0, dot);
		}
		File destination = new File(jarFile.getParentFile(), folderName);
		if(!destination.exists() && !destination.mkdir()) {
			IJ.error("Unpack Jar", "Could not create folder " + destination.getAbsolutePath());
			return;
		}
		unpackJar(jarFile, destination);
		refreshTree();
	}

	/** Extracts every entry of the given jar (a plain zip archive) into destination, creating sub-folders as needed. */
	private void unpackJar(File jarFile, File destination) {

		byte[] buffer = new byte[8192];
		try(ZipInputStream zip = new ZipInputStream(Files.newInputStream(jarFile.toPath()))) {
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
				try(OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
					int read;
					while((read = zip.read(buffer)) != -1) {
						out.write(buffer, 0, read);
					}
				}
				zip.closeEntry();
			}
		} catch(IOException e) {
			IJ.error("Unpack Jar", "Could not unpack \"" + jarFile.getName() + "\":\n" + e.getMessage());
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
		tabItem.addDisposeListener(disposeEvent -> openTabsByPath.remove(path));
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
		 *
		 * Calling editor.close() first (rather than disposing the Shell directly) matters: it's
		 * what actually calls WindowManager.removeWindow(this) (every Editor self-registers
		 * there on construction, embedded or not). Skipping it left a disposed-but-still-
		 * registered Editor in WindowManager's list, which later crashed with "Widget is
		 * disposed" the next time anything (e.g. ImageJ's own quit/shutdown routine) iterated
		 * the non-image window list and called getTitle()/fileChanged() on it. close() also
		 * prompts to save unsaved changes first, same as closing an Editor normally would.
		 */
		tabItem.addDisposeListener(disposeEvent -> {
			editor.close();
			if(!editor.getShell().isDisposed()) {
				editor.getShell().dispose();
			}
		});
		return tabItem;
	}
}
