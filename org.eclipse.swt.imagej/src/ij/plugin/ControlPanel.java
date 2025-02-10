package ij.plugin;

import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.events.TreeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
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
import ij.ImageJ;
import ij.Menus;

/**
 * ControlPanel. This plugin displays a panel with ImageJ commands in a
 * hierarchical tree structure. Base on the Swing version of:
 * 
 * @author Cezar M. Tigaret <c.tigaret@ucl.ac.uk> Shortened for SWT which
 *         already offers tree functions!
 */
public class ControlPanel implements PlugIn, ShellListener {

	/** The platform-specific file separator string. */
	private static final String fileSeparator = System.getProperty("file.separator");
	/** The platform-specific file separator character. */
	private static final char sep = fileSeparator.charAt(0);
	private Hashtable panels = new Hashtable();
	private Vector visiblePanels = new Vector();
	private Vector expandedNodes = new Vector();
	private String defaultArg = "";
	private boolean savePropsUponClose = true;
	private boolean propertiesChanged = true;
	private boolean closeChildPanelOnExpand = true;
	private boolean requireDoubleClick;
	private boolean quitting = true;
	Vector menus = new Vector();
	Vector allMenus = new Vector();
	Hashtable commands = new Hashtable();
	Hashtable menuCommands = new Hashtable();
	String[] pluginsArray;
	Hashtable treeCommands = new Hashtable();
	int argLength;
	private String path = null;
	private Tree root;
	MenuItem reloadMI = null;
	private Shell shell;
	private Composite composite;
	private boolean hasShell = true;

	public Composite getComposite() {

		return composite;
	}

	public ControlPanel() {

	}

	public void createShell(boolean hasShell) {

		this.hasShell = hasShell;
	}

	public Shell getShell() {

		return shell;
	}

	/**
	 * Creates a panel with the hierarchical tree structure of ImageJ's commands.
	 */
	public void run(String arg) {

		Display.getDefault().syncExec(() -> {

			load();

		});
	}
	/* *********************************************************************** */
	/* Tree logic */
	/* *********************************************************************** */

	synchronized void load() {

		ImageJ imageJ = IJ.getInstance();
		shell = new Shell(Display.getDefault());
		shell.setText("Command Finder");
		// shell.setLayout(new GridLayout(1, true));
		if (imageJ != null && !IJ.isMacOSX()) {
			Image img = imageJ.getIconImage();
			if (img != null)
				try {
					shell.setImage(img);
				} catch (Exception e) {
				}
		}
		// Composite northPanel = new Composite(shell, SWT.NONE);
		// northPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1,
		// 1));
		shell.setLayout(new FillLayout());
		composite = new Composite(shell, SWT.NONE);
		composite.setLayout(new GridLayout(1, true));
		Tree tree = new Tree(composite, SWT.BORDER);
		tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		tree.addTreeListener(new TreeListener() {

			public void treeCollapsed(TreeEvent e) {

			}

			public void treeExpanded(TreeEvent e) {

				/*
				 * TreeItem item = (TreeItem) e.item; TreeItem[] children = item.getItems(); for
				 * (int i = 0; i < children.length; i++) if (children[i].getData() == null) //
				 * Removes dummy items. children[i].dispose(); else // Child files already added
				 * to the tree. return; doRootFromMenus(tree);
				 */
			}
		});
		tree.addListener(SWT.MouseDoubleClick, new Listener() {

			public void handleEvent(Event event) {

				Point point = new Point(event.x, event.y);
				TreeItem item = tree.getItem(point);
				if (item != null) {
					actionPerformed(item);
				}
			}
		});
		tree.addSelectionListener(new SelectionListener() {

			public void widgetSelected(SelectionEvent e) {

			}

			public void widgetDefaultSelected(SelectionEvent e) {

			}
		});
		// shell.addShellListener(this);
		commands = Menus.getCommands();
		pluginsArray = Menus.getPlugins();
		doRootFromMenus(tree);
		/*
		 * if (root == null || root.getChildCount() == 0) return; // do nothing if
		 * there's no tree or a root w/o children
		 */
		// loadProperties();
		// restoreVisiblePanels();
		/*
		 * if (panels.isEmpty()) newPanel(root);
		 */
		tree.setRedraw(false); // Stop redraw until operation complete
		TreeItem[] items = tree.getItems();
		for (TreeItem item : items) {
			item.setExpanded(true);
		}
		tree.setRedraw(true);
		if (hasShell) {
			shell.setSize(300, 600);
			shell.open();
		}
	}

	/**
	 * Builds up a root tree from ImageJ's menu bar. The root tree replicates
	 * ImageJ's menu bar with its menus and their submenus. Delegates to the
	 * {@link recursesubMenu(Menu, DefaultMutableTreeNode} method to gather the root
	 * children.
	 * 
	 * @param root
	 * 
	 * @return
	 *
	 */
	private synchronized void doRootFromMenus(Tree root) {

		if (root.getItemCount() > 0) {
			TreeItem[] items = root.getItems();
			for (int y = 0; y < items.length; y++) {
				items[y].dispose();
			}
		}
		// TreeItem node = new TreeItem(root, SWT.NONE);
		// node.setText("ImageJ Menus");
		// if(argLength==0) node.setUserObject("Control Panel");
		Menu menuBar = Menus.getMenuBar();
		for (int i = 0; i < menuBar.getItemCount(); i++) {
			MenuItem menu = menuBar.getItem(i);
			TreeItem menuNode = new TreeItem(root, SWT.NONE);
			menuNode.setText(menu.getText());
			Object data = menu.getData("ActionCommand");
			menuNode.setData("ActionCommand", data);
			if ((menu.getStyle() & SWT.CASCADE) == 0) {
				/* Simply set a arbitrary string if we don't have a Cascade menu!! */
				menuNode.setData("IsCascade", "false");
			} else {
				/* For all other cases we null it! */
				menuNode.setData("IsCascade", null);
				// Image img =IJ.getInstance().getIconImage();
				// menuNode.setImage(img);
			}
			recurseSubMenu(menu.getMenu(), menuNode);
			// node.add(menuNode);
		}
		// return node;
	}

	/**
	 * Recursively builds up a tree structure from the Menu argument, by populating
	 * the TreeNode argument with children TreeNode objects constructed on the menu
	 * items. Descendants can be intermediate-level nodes (submenus) or leaf nodes
	 * (i.e., no children). Leaf nodes will only be added if there are any commands
	 * associated with them, i.e. their labels correspond to keys in the hashtable
	 * returned by <code>ij.Menus.getCommands()</code> except for the "Reload
	 * Plugins" menu item, for which a local action command string is assigned to
	 * avoid clashes with the action fired from ImageJ Plugins->Utilties->Reload
	 * Plugins menu item.<br>
	 * <strong>Note: </strong> this method bypasses the tree buildup based on the
	 * {@link populateNode(Hashtable,DefaultMutableTreeNode)} method.
	 * 
	 * @param menu The Menu instance to be searched recursively for menu items
	 * @param node The DefaultMutableTreeNode corresponding to the
	 *             <code>Menu menu</code> argument.
	 */
	private void recurseSubMenu(Menu menu, TreeItem node) {

		int items = menu.getItemCount();
		if (items == 0)
			return;
		for (int i = 0; i < items; i++) {
			MenuItem mItem = menu.getItem(i);
			String label = mItem.getText();
			Object data = mItem.getData("ActionCommand");
			if (mItem.getMenu() != null) {
				TreeItem subNode = new TreeItem(node, SWT.NONE);
				subNode.setText(mItem.getText());
				subNode.setData("ActionCommand", data);
				// Image img =IJ.getInstance().getIconImage();
				// subNode.setImage(img);
				recurseSubMenu(mItem.getMenu(), subNode);
				// node.add(subNode);
			} else if (mItem instanceof MenuItem) {
				if (!(mItem.getStyle() == SWT.SEPARATOR)) {
					TreeItem leaf = new TreeItem(node, SWT.NONE);
					leaf.setText(mItem.getText());
					leaf.setData("ActionCommand", data);
					if ((menu.getStyle() & SWT.CASCADE) == 0) {
						/* Simply set a arbitrary string if we don't have a Cascade menu!! */
						leaf.setData("IsCascade", "false");
					} else {
						/* For all other cases we null it! */
						leaf.setData("IsCascade", null);
					}
					// node.add(leaf);
					if (treeCommands == null)
						treeCommands = new Hashtable();
					if (label.equals("Reload Plugins")) {
						reloadMI = mItem;
						treeCommands.put(label, "Reload Plugins From Panel");
					}
				}
			}
		}
	}

	void showHelp() {

		IJ.showMessage("About Control Panel...",
				"This plugin displays a panel with ImageJ commands in a hierarchical tree structure.\n" + " \n"
						+ "Usage:\n" + " \n"
						+ "     Click on a leaf node to launch the corresponding ImageJ command (or plugin)\n"
						+ "     (double-click on X Window Systems)\n" + " \n"
						+ "     Double-click on a tree branch node (folder) to expand or collapse it\n" + " \n"
						+ "     Click and drag on a tree branch node (folder) to display its descendants,\n"
						+ "     in a separate (child) panel (\"tear-off\" mock-up)\n" + " \n"
						+ "     In a child panel, use the \"Show Parent\" menu item to re-open the parent panel\n"
						+ "     if it was accidentally closed\n" + " \n"
						+ "Author: Cezar M. Tigaret (c.tigaret@ucl.ac.uk)\n" + "This code is in the public domain.");
	}

	// 1. trim away the eclosing brackets
	// 2. replace comma-space with dots
	// 3. replace spaces with underscores
	String pStr2Key(String pathString) {

		String keyword = pathString;
		if (keyword.startsWith("["))
			keyword = keyword.substring(keyword.indexOf("[") + 1, keyword.length());
		if (keyword.endsWith("]"))
			keyword = keyword.substring(0, keyword.lastIndexOf("]"));
		StringTokenizer st = new StringTokenizer(keyword, ",");
		String result = "";
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			if (token.startsWith(" "))
				token = token.substring(1, token.length()); // remove leading space
			result += token + ".";
		}
		result = result.substring(0, result.length() - 1);// remove trailing dot
		result = result.replace(' ', '_');
		return result;
	}

	String key2pStr(String keyword) {

		// keyword = keyword.replace('_',' '); // restore the spaces from underscores
		StringTokenizer st = new StringTokenizer(keyword, ".");
		String result = "";
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			result += token + ", ";
		}
		result = result.substring(0, result.length() - 2); // trim away the ending comma-space
		result = "[" + result + "]";
		result = result.replace('_', ' ');
		return result;
	}

	// Thank you, Wayne!
	/**
	 * Breaks the specified string into an array of ints. Returns null if there is
	 * an error.
	 */
	public int[] s2ints(String s) {

		StringTokenizer st = new StringTokenizer(s, ", \t");
		int nInts = st.countTokens();
		if (nInts == 0)
			return null;
		int[] ints = new int[nInts];
		for (int i = 0; i < nInts; i++) {
			try {
				ints[i] = Integer.parseInt(st.nextToken());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return ints;
	}

	public void actionPerformed(TreeItem item) {

		String cmd = item.getText();
		if (cmd == null)
			return;
		if (cmd.equals("Help")) {
			showHelp();
			return;
		}
		if (cmd.equals("Show Parent")) {
			/*
			 * DefaultMutableTreeNode parent = (DefaultMutableTreeNode) root.getParent(); if
			 * (parent != null) { TreePanel panel = pcp.getPanelForNode(parent); if (panel
			 * == null) panel = pcp.newPanel(parent); if (panel != null) panel.setVisible();
			 * }
			 */
			return;
		}
		if (cmd.equals("Reload Plugins From Panel")) {// cmd fired by clicking on tree leaf
			// pcp.closeAll(false);
			IJ.doCommand("Reload Plugins");
		} else {
			if (cmd.equals("Reload Plugins")) { // cmd fired from ImageJ menu; don't propagate it further
				// pcp.closeAll(false);
			} else {
				String data = (String) item.getData("ActionCommand");
				String isCascade = (String) item.getData("IsCascade");
				if (isCascade != null) {
					if (data != null) {
						IJ.doCommand(data);
					} else {
						IJ.doCommand(cmd);
					}
				}
			}
			return;
		}
	}

	@Override
	public void shellActivated(ShellEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellClosed(ShellEvent e) {

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
