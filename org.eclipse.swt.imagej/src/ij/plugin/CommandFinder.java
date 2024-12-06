/**
 * This plugin implements the Plugins/Utilities/Find Commands
 * command. It provides an easy user interface to finding commands
 * you might know the name of without having to go through
 * all the menus. If you type a part of a command name, the box
 * below will only show commands that match that substring (case
 * insensitively). If only a single command matches then that
 * command can be run by hitting Enter. If multiple commands match,
 * they can be selected by selecting with the mouse and clicking
 * "Run"; alternatively hitting the up or down arrows will move the
 * keyboard focus to the list and the selected command can be run
 * with Enter. When the list has focus, it is also possible to use
 * keyboard "scrolling": E.g., pressing "H" will select the first
 * command starting with the char "H". Pressing "H" again will select
 * the next row starting with the char "H", etc., looping between all
 * "H" starting commands. Double-clicking on a command in the list
 * should also run the appropriate command.
 * 
 * @author Mark Longair <mark-imagej@longair.net>
 * @author Johannes Schindelin <johannes.schindelin@gmx.de>
 * @author Curtis Rueden <ctrueden@wisc.edu>
 * @author Tiago Ferreira <tiago.ferreira@mail.mcgill.ca>
 * 
 */
package ij.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Menus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.HTMLDialog;
import ij.plugin.frame.Editor;
import ij.plugin.frame.swt.WindowSwt;
import ij.process.ImageProcessor;

public class CommandFinder implements WindowSwt, PlugIn, SelectionListener, ShellListener, ModifyListener, KeyListener, MouseListener {

	private static final int TABLE_WIDTH = 640;
	private static final int TABLE_ROWS = 18;
	private int multiClickInterval;
	private long lastClickTime;
	// private static Shell frame;
	private org.eclipse.swt.widgets.Text prompt;
	// private JScrollPane scrollPane;
	private Button runButton, sourceButton, closeButton, commandsButton,
			helpButton;
	private Button closeCheckBox;
	private Button lutCheckBox;
	private Hashtable commandsHash;
	private String[] commands;
	private static boolean closeWhenRunning = Prefs.get("command-finder.close", false);
	private static boolean applyLUT;
	private Table table;
	// private TableModel tableModel;
	TableColumn column1, column2, column3, column4;
	private int lastClickedRow;
	private Shell shell;
	protected boolean isVisible;
	protected String title;

	public CommandFinder() {

		// Toolkit toolkit = Toolkit.getDefaultToolkit();
		// Integer interval = (Integer)
		// toolkit.getDesktopProperty("awt.multiClickInterval");
		// if (interval == null)
		// Hopefully 300ms is a sensible default when the property
		// is not available.
		multiClickInterval = 300;
		/*
		 * else multiClickInterval = interval.intValue();
		 */
	}

	class CommandAction {

		CommandAction(String classCommand, MenuItem menuItem, String menuLocation) {

			this.classCommand = classCommand;
			this.menuItem = menuItem;
			this.menuLocation = menuLocation;
		}

		String classCommand;
		MenuItem menuItem;
		String menuLocation;

		public String toString() {

			return "classCommand: " + classCommand + ", menuItem: " + menuItem + ", menuLocation: " + menuLocation;
		}
	}

	protected String[] makeRow(String command, CommandAction ca) {

		String[] result = new String[table.getColumnCount()];
		result[0] = command;
		if(ca.menuLocation != null)
			result[1] = ca.menuLocation;
		if(ca.classCommand != null)
			result[2] = ca.classCommand;
		String jarFile = Menus.getJarFileForMenuEntry(command);
		if(jarFile != null)
			result[3] = jarFile;
		return result;
	}

	protected void populateList(String matchingSubstring) {

		table.removeAll();
		String[] words = matchingSubstring.toLowerCase().split("\\s+"); // Split the search string into words
		ArrayList list = new ArrayList();
		int count = 0;
		for(int i = 0; i < commands.length; ++i) {
			String commandName = commands[i];
			String command = commandName.toLowerCase();
			CommandAction ca = (CommandAction)commandsHash.get(commandName);
			String menuPath = (ca.menuLocation != null) ? ca.menuLocation.toLowerCase() : "";
			// Check if all words match either the command or the menu path
			boolean allWordsMatch = true;
			for(String word : words) {
				if(!(command.contains(word) || menuPath.contains(word))) {
					allWordsMatch = false;
					break;
				}
			}
			if(allWordsMatch) {
				String[] row = makeRow(commandName, ca);
				TableItem it1 = new TableItem(table, SWT.NONE);
				it1.setText(new String[]{row[0], row[1], row[2]});
				// list.add(row);
			}
		}
		// table.setData(list);
		prompt.forceFocus();
	}

	@Override
	public void widgetSelected(SelectionEvent e) {

		if(e.widget instanceof Button) {
			org.eclipse.swt.widgets.Button it = (org.eclipse.swt.widgets.Button)e.widget;
			if((it.getStyle() & SWT.CHECK) != 0) {
				itemStateChanged(e);
			} else {
				actionPerformed(e);
			}
		} else {
			actionPerformed(e);
		}
	}

	public void actionPerformed(SelectionEvent ae) {

		Object source = ae.getSource();
		if(source == runButton) {
			int row = table.getSelectionIndex();
			if(row < 0) {
				error("Please select a command to run");
				return;
			}
			runCommand(table.getItem(row).getText(0));
		} else if(source == sourceButton) {
			int row = table.getSelectionIndex();
			if(row < 0) {
				error("Please select a command");
				return;
			}
			showSource(table.getItem(row).getText(0));
		} else if(source == closeButton) {
			closeWindow();
		} else if(source == commandsButton) {
			IJ.doCommand("Commands...");
		} else if(source == helpButton) {
			String text = "<html>Shortcuts:<br>" + "&emsp;&uarr; &darr;&ensp; Select items<br>" + "&emsp;&crarr;&emsp; Open item<br>" + "&ensp;A-Z&ensp; Alphabetic scroll<br>" + "&emsp;&#9003;&emsp;Activate search field</html>";
			new HTMLDialog("", text);
		}
	}

	public void itemStateChanged(SelectionEvent ie) {

		populateList(prompt.getText());
		applyLUT = lutCheckBox.getSelection();
		if(applyLUT)
			prompt.setText("Lookup Tables");
	}

	public void mouseClicked(MouseEvent e) {

		long now = System.currentTimeMillis();
		int row = table.getSelectionIndex();
		TableItem[] selection = table.getSelection();
		StringBuffer buff = new StringBuffer();
		for(int i = 0; i < selection.length; i++) {
			buff.append(selection[i] + " ");
		}
		// Display cell contents in status bar - The following two lines not implemented in SWT!
		// if (tableModel==null)
		// return;
		String value = buff.toString();
		IJ.showStatus(value);
		// Is this fast enough to be a double-click?
		long thisClickInterval = now - lastClickTime;
		if(thisClickInterval < multiClickInterval) {
			if(row >= 0 && lastClickedRow >= 0 && row == lastClickedRow)
				runCommand(table.getItem(0).getText(0));
		}
		lastClickTime = now;
		lastClickedRow = row;
		if(lutCheckBox.getSelection())
			previewLUT();
	}

	public void mousePressed(MouseEvent e) {

	}

	public void mouseReleased(MouseEvent e) {

	}

	public void mouseEntered(MouseEvent e) {

	}

	public void mouseExited(MouseEvent e) {

	}

	void showSource(String cmd) {

		if(showMacro(cmd))
			return;
		Hashtable table = Menus.getCommands();
		String className = (String)table.get(cmd);
		if(IJ.debugMode)
			IJ.log("showSource: " + cmd + "   " + className);
		if(className == null) {
			error("No source associated with this command:\n  " + cmd);
			return;
		}
		int mstart = className.indexOf("ij.plugin.Macro_Runner(\"");
		if(mstart >= 0) { // macro or script
			int mend = className.indexOf("\")");
			if(mend == -1)
				return;
			String macro = className.substring(mstart + 24, mend);
			IJ.open(IJ.getDirectory("plugins") + macro);
			return;
		}
		if(className.endsWith("\")")) {
			int openParen = className.lastIndexOf("(\"");
			if(openParen > 0)
				className = className.substring(0, openParen);
		}
		if(className.startsWith("ij.")) {
			className = className.replaceAll("\\.", "/");
			IJ.runPlugIn("ij.plugin.BrowserLauncher", IJ.URL2 + "/source/" + className + ".java");
			return;
		}
		className = IJ.getDirectory("plugins") + className.replaceAll("\\.", "/");
		String path = className + ".java";
		File f = new File(path);
		if(f.exists()) {
			IJ.open(path);
			return;
		}
		error("Unable to display source for this plugin:\n  " + className);
	}

	private boolean showMacro(String cmd) {

		String name = null;
		if(cmd.equals("Display LUTs"))
			name = "ShowAllLuts.txt";
		else if(cmd.equals("Search..."))
			name = "Search.txt";
		if(name == null)
			return false;
		String code = BatchProcessor.openMacroFromJar(name);
		if(code != null) {
			Editor ed = new Editor();
			ed.getShell().setSize(700, 600);
			ed.create(name, code);
			return true;
		}
		return false;
	}

	private void error(String msg) {

		IJ.error("Command Finder", msg);
	}

	protected void runCommand(String command) {

		IJ.showStatus("Running command " + command);
		IJ.doCommand(command);
		closeWhenRunning = closeCheckBox.getSelection();
		if(closeWhenRunning)
			closeWindow();
	}

	public void keyPressed(KeyEvent ke) {

		int key = ke.keyCode;
		char ch = ke.character;
		int flags = ke.stateMask;
		int items = table.getItemCount();
		Object source = ke.getSource();
		boolean meta = ((flags & SWT.ALT) != 0) || ((flags & SWT.CTRL) != 0 || (flags & SWT.SHIFT) != 0);
		if(key == SWT.ESC || (ch == 'w' && meta)) {
			closeWindow();
		} else if(source == prompt) {
			/*
			 * If you hit enter in the text field, and there's only one command that
			 * matches, run that:
			 */
			if(key == SWT.CR || key == SWT.KEYPAD_CR) {
				if(1 == items) {
					// getValueAt(row, 0);
					runCommand(table.getItem(0).getText(0));
				}
			}
		}
		/*
		 * If you hit the up or down arrows in the text field, move the focus to the
		 * table and select the row at the bottom or top.
		 */
		int index = -1;
		if(key == SWT.UP) {
			index = table.getSelectionIndex() - 1;
			if(index < 0)
				index = items - 1;
		} else if(key == SWT.DOWN) {
			index = table.getSelectionIndex() + 1;
			if(index >= items)
				index = Math.min(items - 1, 0);
		}
		if(index >= 0) {
			table.forceFocus();
			// completions.ensureIndexIsVisible(index);
			table.setSelection(index, index);
			// table.setRowSelectionInterval(index, index);
		} else if(key == SWT.BS || key == SWT.DEL) {
			/*
			 * If someone presses backspace or delete they probably want to remove the last
			 * letter from the search string, so switch the focus back to the prompt:
			 */
			prompt.forceFocus();
		} else if(source == table) {
			/*
			 * If you hit enter with the focus in the table, run the selected command
			 */
			if(key == SWT.CR || key == SWT.KEYPAD_CR) {
				// ke.consume();
				int row = table.getSelectionIndex();
				if(row >= 0)
					runCommand(table.getItem(0).getText(0));
				/* Loop through the list using the arrow keys */
			} else if(key == SWT.UP) {
				if(table.getSelectionIndex() == 0)
					table.setSelection(table.getItemCount() - 1, table.getItemCount() - 1);
			} else if(key == SWT.DOWN) {
				if(table.getSelectionIndex() == table.getItemCount() - 1)
					table.setSelection(0, 0);
			}
		}
	}
	/*
	 * public void keyReleased(KeyEvent ke) { if (lutCheckBox.getSelection())
	 * previewLUT(); }
	 */

	public void previewLUT() {

		int row = table.getSelectionIndex();
		if(row >= 0) {
			// getValueAt(row, 0);
			String cmd = table.getItem(row).getText(0);
			// String mPath = (String) table.getValueAt(row, 1);
			String mPath = (String)table.getItem(row).getText(1);
			String cName = (String)table.getItem(row).getText(2);
			if((mPath.indexOf("Lookup Table") > 0) && ((null == cName) || (cName.indexOf("LutLoader") > 0))) {
				ImagePlus imp = WindowManager.getCurrentImage();
				if(null == imp) {
					imp = IJ.createImage("LUT Preview", "8-bit ramp", 256, 32, 1);
					imp.show();
				}
				if(imp.getBitDepth() != 24) {
					if(imp.isComposite())
						((CompositeImage)imp).setChannelColorModel(LutLoader.getLut(cmd));
					else {
						ImageProcessor ip = imp.getProcessor();
						ip.setColorModel(LutLoader.getLut(cmd));
						IJ.showStatus(cmd);
					}
					imp.updateAndDraw();
				}
			}
		}
	}

	public void keyTyped(KeyEvent ke) {

	}
	/*
	 * class PromptDocumentListener implements DocumentListener { public void
	 * insertUpdate(DocumentEvent e) { populateList(prompt.getText()); }
	 * public void removeUpdate(DocumentEvent e) { populateList(prompt.getText()); }
	 * public void changedUpdate(DocumentEvent e) { populateList(prompt.getText());
	 * } }
	 */
	/*
	 * This function recurses down through a menu, adding to commandsHash the
	 * location and MenuItem of any items it finds that aren't submenus.
	 */

	public void parseMenu(String path, org.eclipse.swt.widgets.Menu menu) {

		int n = menu.getItemCount();
		for(int i = 0; i < n; ++i) {
			MenuItem m = menu.getItem(i);
			// String label = m.getActionCommand();
			String label = m.getText();
			if(m.getMenu() != null) {
				org.eclipse.swt.widgets.Menu subMenu = (org.eclipse.swt.widgets.Menu)m.getMenu();
				parseMenu(path + ">" + label, subMenu);
			} else {
				String trimmedLabel = label.trim();
				if(trimmedLabel.length() == 0 || trimmedLabel.equals("-"))
					continue;
				CommandAction ca = (CommandAction)commandsHash.get(label);
				if(ca == null)
					commandsHash.put(label, new CommandAction(null, m, path));
				else {
					ca.menuItem = m;
					ca.menuLocation = path;
				}
				CommandAction caAfter = (CommandAction)commandsHash.get(label);
			}
		}
	}
	/*
	 * Finds all the top level menus from the menu bar and recurses down through
	 * each.
	 */

	public void findAllMenuItems() {

		int[] topLevelMenus = new int[1];
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Menu menuBar = Menus.getMenuBar();
				topLevelMenus[0] = menuBar.getItemCount();
				for(int i = 0; i < topLevelMenus[0]; ++i) {
					MenuItem topLevelMenu = menuBar.getItem(i);
					Menu menu = topLevelMenu.getMenu();
					parseMenu(topLevelMenu.getText(), menu);
				}
			}
		});
	}

	/**
	 * Displays the Command Finder dialog. If a Command Finder window is already
	 * being displayed and <tt>initialSearch</tt> contains a valid query, it will be
	 * closed and a new one displaying the new search will be rebuilt at the same
	 * screen location.
	 *
	 * @param initialSearch
	 *            The search string that populates Command Finder's search
	 *            field. It is ignored if contains an invalid query (ie,
	 *            if it is either <tt>null</tt> or <tt>empty</tt>).
	 */
	public void run(String initialSearchh) {

		String[] initialSearch = new String[]{initialSearchh};
		if(shell != null) {
			if(initialSearch[0] != null && !initialSearch[0].isEmpty()) {
				shell.dispose(); // Rebuild dialog with new search string
			} else {
				WindowManager.toFront(shell);
				return;
			}
		}
		commandsHash = new Hashtable();
		/*
		 * Find the "normal" commands; those which are registered plugins:
		 */
		Hashtable realCommandsHash = (Hashtable)(ij.Menus.getCommands().clone());
		Set realCommandSet = realCommandsHash.keySet();
		for(Iterator i = realCommandSet.iterator(); i.hasNext();) {
			String command = (String)i.next();
			// Some of these are whitespace only or separators - ignore them:
			String trimmedCommand = command.trim();
			if(trimmedCommand.length() > 0 && !trimmedCommand.equals("-")) {
				commandsHash.put(command, new CommandAction((String)realCommandsHash.get(command), null, null));
			}
		}
		/*
		 * There are some menu items that don't correspond to plugins, such as those
		 * added by RefreshScripts, so look through all the menus as well:
		 */
		findAllMenuItems();
		/*
		 * Sort the commands, generate list labels for each and put them into a hash:
		 */
		commands = (String[])commandsHash.keySet().toArray(new String[0]);
		Arrays.sort(commands);
		/* The code below just constructs the dialog: */
		ImageJ imageJ = IJ.getInstance();
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				shell = new Shell(Display.getDefault()) /*
														 * {
														 * @Override public void setActive() { // if (visible)
														 * WindowManager.addWindow(this); super.setActive(); }
														 * }
														 */;
				shell.setText("Command Finder");
				shell.setLayout(new GridLayout(1, true));
				shell.addShellListener(CommandFinder.this);
				WindowManager.addWindow(CommandFinder.this);
				// Set up the event loop.
				/*
				 * while (!shell.isDisposed()) { if (!display.readAndDispatch()) { // If no more
				 * entries in event queue display.sleep(); } } display.dispose();
				 */
				// }
				/*
				 * frame = new JFrame("Command Finder") { public void setVisible(boolean
				 * visible) { if (visible) WindowManager.addWindow(this);
				 * super.setVisible(visible); }
				 * public void dispose() { WindowManager.removeWindow(this);
				 * Prefs.set("command-finder.close", closeWhenRunning); frame = null;
				 * super.dispose(); } };
				 */ // Container contentPane = frame.getContentPane();
					// contentPane.setLayout(new BorderLayout());
					// frame.addWindowListener(this);
				if(imageJ != null && !IJ.isMacOSX()) {
					Image img = imageJ.getIconImage();
					if(img != null)
						try {
							shell.setImage(img);
						} catch(Exception e) {
						}
				}
				Composite northPanel = new Composite(shell, SWT.NONE);
				northPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
				northPanel.setLayout(new GridLayout(2, true));
				Label searchLabel = new Label(northPanel, SWT.NONE);
				searchLabel.setText(" Search:");
				// searchLabel.setLayoutData("WEST");
				searchLabel.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, false, 1, 1));
				// GUI.scale(searchLabel);
				// northPanel.add(searchLabel, BorderLayout.WEST);
				prompt = new org.eclipse.swt.widgets.Text(northPanel, SWT.SINGLE);
				prompt.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
				// prompt.setLayoutData("NORTH");
				// GUI.scale(prompt);
				prompt.addModifyListener(CommandFinder.this);
				prompt.addKeyListener(CommandFinder.this);
				// northPanel.add(prompt);
				// contentPane.add(northPanel, BorderLayout.NORTH);
				// searchLabel.setLayoutData("WEST");
				// tableModel = new TableModel();
				table = new Table(shell, SWT.BORDER | SWT.V_SCROLL | SWT.SCROLL_LINE | SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);
				table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 7));
				// table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				// table.setRowSelectionAllowed(true);
				// table.setColumnSelectionAllowed(false);
				// table.setAutoCreateRowSorter(true);
				// tableModel.setColumnWidths(table.getColumnModel());
				// GUI.scale(table);
				// Dimension dim = new Dimension(TABLE_WIDTH, table.getRowHeight() *
				// TABLE_ROWS);
				// table.setPreferredScrollableViewportSize(dim);
				table.addKeyListener(CommandFinder.this);
				table.addMouseListener(CommandFinder.this);
				{
					column1 = new TableColumn(table, SWT.LEFT);
					column1.setText("Command");
					column1.setToolTipText("The command!");
					column1.setWidth(100);
				}
				{
					column2 = new TableColumn(table, SWT.LEFT);
					column2.setText("Menu Path");
					column2.setToolTipText("The menu path!");
					column2.setWidth(100);
				}
				{
					column3 = new TableColumn(table, SWT.LEFT);
					column3.setText("Class");
					column3.setToolTipText("The class information!");
					column3.setWidth(100);
				}
				{
					column4 = new TableColumn(table, SWT.LEFT);
					column4.setText("File");
					column4.setToolTipText("The file information");
					column4.setWidth(100);
				}
				table.setHeaderVisible(true);
				table.setLinesVisible(true);
				// Show row header
				final TableEditor editor = new TableEditor(table);
				editor.horizontalAlignment = SWT.LEFT;
				editor.grabHorizontal = true;
				// createActions();
				// initializeToolBar();
				// initializeMenu();
				/* Resize column width if shell changes! */
				shell.addControlListener(new ControlAdapter() {

					public void controlResized(ControlEvent e) {

						if(table.isDisposed() == false) {
							Rectangle area = shell.getClientArea();
							Point preferredSize = table.computeSize(SWT.DEFAULT, SWT.DEFAULT);
							int width = area.width - 2 * table.getBorderWidth();
							if(preferredSize.y > area.height + table.getHeaderHeight()) {
								// Subtract the scrollbar width from the total column width
								// if a vertical scrollbar will be required
								Point vBarSize = table.getVerticalBar().getSize();
								width -= vBarSize.x;
							}
							Point oldSize = table.getSize();
							if(oldSize.x > area.width) {
								// table is getting smaller so make the columns
								// smaller first and then resize the table to
								// match the client area width
								column1.setWidth(width / 4);
								column2.setWidth(width / 4);
								column3.setWidth(width / 4);
								table.setSize(area.width, area.height);
							} else {
								// table is getting bigger so make the table
								// bigger first and then make the columns wider
								// to match the client area width
								table.setSize(area.width, area.height);
								column1.setWidth(width / 4);
								column2.setWidth(width / 4);
								column3.setWidth(width / 4);
							}
						}
						shell.layout();
					}
				});
				// Auto-scroll table using keystrokes
				table.addKeyListener(new KeyAdapter() {

					public void keyTyped(final KeyEvent evt) {

						if(evt.character == SWT.CTRL || evt.character == SWT.ALT || evt.character == SWT.SHIFT)
							return;
						final int nRows = table.getItemCount();
						final char ch = Character.toLowerCase(evt.character);
						if(!Character.isLetterOrDigit(ch)) {
							return; // Ignore searches for non alpha-numeric characters
						}
						final int sRow = table.getSelectionIndex();
						for(int row = (sRow + 1) % nRows; row != sRow; row = (row + 1) % nRows) {
							// final String rowData = tableModel.getValueAt(row, 0).toString();
							final String rowData = table.getItem(row).getText(0);
							final char rowCh = Character.toLowerCase(rowData.charAt(0));
							if(ch == rowCh) {
								table.setSelection(row, row);
								// table.scrollRectToVisible(table.getCellRect(row, 0, true));
								break;
							}
						}
					}
				});
				// scrollPane = new JScrollPane(table);
				if(initialSearch[0] == null)
					initialSearch[0] = "";
				prompt.setText(initialSearch[0]);
				populateList(initialSearch[0]);
				// contentPane.add(scrollPane, BorderLayout.CENTER);
				// Composite southPanel = new Composite(shell, SWT.NONE);
				// southPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				// JPanel southPanel = new JPanel();
				// southPanel.setLayout(new GridLayout(1,true));
				// southPanel.setLayoutData("SOUTH");
				Composite optionsPanel = new Composite(shell, SWT.NONE);
				// optionsPanel.setLayoutData("CENTER");
				optionsPanel.setLayout(new GridLayout(2, true));
				closeCheckBox = new Button(optionsPanel, SWT.CHECK);
				closeCheckBox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				closeCheckBox.setText("Close window after running command");
				closeCheckBox.setSelection(closeWhenRunning);
				// GUI.scale(closeCheckBox);
				closeCheckBox.addSelectionListener(CommandFinder.this);
				lutCheckBox = new Button(optionsPanel, SWT.CHECK);
				lutCheckBox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				lutCheckBox.setText("Apply LUTs");
				lutCheckBox.setSelection(applyLUT);
				// GUI.scale(lutCheckBox);
				lutCheckBox.addSelectionListener(CommandFinder.this);
				// JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
				// optionsPanel.add(closeCheckBox);
				// optionsPanel.add(lutCheckBox);
				Composite buttonsPanel = new Composite(shell, SWT.NONE);
				buttonsPanel.setLayout(new GridLayout(5, true));
				// buttonsPanel.setLayoutData("SOUTH");
				runButton = new Button(buttonsPanel, SWT.NONE);
				runButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				runButton.setText("Run");
				// GUI.scale(runButton);
				sourceButton = new Button(buttonsPanel, SWT.NONE);
				sourceButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				sourceButton.setText("Source");
				// new JButton("Source");
				// GUI.scale(sourceButton);
				closeButton = new Button(buttonsPanel, SWT.NONE);
				closeButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				closeButton.setText("Close");
				// GUI.scale(closeButton);
				commandsButton = new Button(buttonsPanel, SWT.NONE);
				commandsButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				commandsButton.setText("Commands");
				// GUI.scale(commandsButton);
				helpButton = new Button(buttonsPanel, SWT.NONE);
				helpButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				helpButton.setText("Help");
				// GUI.scale(helpButton);
				runButton.addSelectionListener(CommandFinder.this);
				sourceButton.addSelectionListener(CommandFinder.this);
				closeButton.addSelectionListener(CommandFinder.this);
				commandsButton.addSelectionListener(CommandFinder.this);
				helpButton.addSelectionListener(CommandFinder.this);
				runButton.addKeyListener(CommandFinder.this);
				sourceButton.addKeyListener(CommandFinder.this);
				closeButton.addKeyListener(CommandFinder.this);
				commandsButton.addKeyListener(CommandFinder.this);
				helpButton.addKeyListener(CommandFinder.this);
				/*
				 * buttonsPanel.add(runButton); buttonsPanel.add(sourceButton);
				 * buttonsPanel.add(closeButton); buttonsPanel.add(commandsButton);
				 * buttonsPanel.add(helpButton);
				 * southPanel.add(optionsPanel, BorderLayout.CENTER);
				 * southPanel.add(buttonsPanel, BorderLayout.SOUTH);
				 */
				// contentPane.add(southPanel, BorderLayout.SOUTH);
				java.awt.Rectangle screen = GUI.getMaxWindowBounds(IJ.getInstance().getShell());
				// frame.pack();
				int dialogWidth = shell.getSize().x;
				int dialogHeight = shell.getSize().y;
				Point pos = imageJ.getShell().getLocation();
				Point size = imageJ.getShell().getSize();
				/*
				 * Generally try to position the dialog slightly offset from the main ImageJ
				 * window, but if that would push the dialog off to the screen to any side,
				 * adjust it so that it's on the screen.
				 */
				int initialX = pos.x + 10;
				int initialY = pos.y + 10 + size.y;
				initialX = Math.max(screen.x, Math.min(initialX, screen.x + screen.width - dialogWidth));
				initialY = Math.max(screen.y, Math.min(initialY, screen.y + screen.height - dialogHeight));
				shell.layout();
				shell.setSize(600, 700);
				// shell.pack();
				shell.setLocation(initialX, initialY);
				// shell.setVisible(true);
				shell.open();
				// shell.forceActive();
			}
		});
	}

	@Override
	public void shellActivated(ShellEvent e) {
		// if (IJ.isMacOSX() && shell != null && Prefs.setIJMenuBar)
		// shell.setMenuBar(Menus.getMenuBar());

	}

	private void closeWindow() {

		/*
		 * if (frame != null) frame.dispose();
		 */
		if(shell != null) {
			shell.close();
		}
	}

	@Override
	public void shellClosed(ShellEvent e) {

		e.doit = false;
		WindowManager.removeWindow(this);
		Prefs.set("command-finder.close", closeWhenRunning);
		shell = null;
		// super.dispose();
		e.doit = true;
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
	/* Make sure that clicks on the close icon close the window: */
	/*
	 * public void windowClosing(WindowEvent e) { closeWindow(); }
	 * private void closeWindow() { if (frame != null) frame.dispose(); }
	 */
	/*
	 * public void windowActivated(WindowEvent e) { if (IJ.isMacOSX() && frame !=
	 * null) frame.setMenuBar(Menus.getMenuBar()); }
	 */
	/*
	 * public void windowDeactivated(WindowEvent e) { }
	 * public void windowClosed(WindowEvent e) { }
	 * public void windowOpened(WindowEvent e) { }
	 * public void windowIconified(WindowEvent e) { }
	 * public void windowDeiconified(WindowEvent e) { }
	 */
	/*
	 * private class TableModel extends AbstractTableModel { protected ArrayList
	 * list; public final static int COLUMNS = 4;
	 * public TableModel() { list = new ArrayList(); }
	 * public void setData(ArrayList list) { this.list = list;
	 * fireTableDataChanged(); }
	 * public int getColumnCount() { return COLUMNS; }
	 * public String getColumnName(int column) { switch (column) { case 0: return
	 * "Command"; case 1: return "Menu Path"; case 2: return "Class"; case 3: return
	 * "File"; } return null; }
	 * public int getRowCount() { return list.size(); }
	 * public Object getValueAt(int row, int column) { if (row >= list.size() ||
	 * column >= COLUMNS) return null; String[] strings = (String[]) list.get(row);
	 * return strings[column]; }
	 * public String getCommand(int row) { if (row < 0 || row >= list.size()) return
	 * ""; else return (String) getValueAt(row, 0); }
	 * public void setColumnWidths(TableColumnModel columnModel) { int[] widths = {
	 * 170, 150, 170, 30 }; for (int i = 0; i < widths.length; i++)
	 * columnModel.getColumn(i).setPreferredWidth(widths[i]); }
	 * }
	 */

	@Override
	public void mouseDoubleClick(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseDown(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseUp(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void keyReleased(KeyEvent e) {

		if(lutCheckBox.getSelection())
			previewLUT();
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void modifyText(ModifyEvent arg0) {

		populateList(prompt.getText());
	}

	@Override
	public Shell getShell() {

		return shell;
	}

	public boolean isVisible() {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				isVisible = shell.isVisible();
			}
		});
		return isVisible;
	}

	public String getTitle() {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				title = shell.getText();
			}
		});
		return title;
	}
}