package ij.plugin.frame;

import java.awt.Point;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Display;

import ij.CommandListener;
import ij.Executer;
import ij.IJ;
import ij.ImageJ;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.frame.swt.WindowSwt;

/** This plugin implements the Plugins>Utiltiees>Recent Commands command. */
public class Commands extends PlugInFrame implements SelectionListener, CommandListener, WindowSwt {

	public static final String LOC_KEY = "commands.loc";
	public static final String CMDS_KEY = "commands.cmds";
	public static final int MAX_COMMANDS = 20;
	private static Commands instance;
	private static final String divider = "---------------";
	private static final String[] commands = { "Blobs", "Open...", "Show Info...", "Close", "Close All",
			"Appearance...", "Histogram", "Gaussian Blur...", "Record...", "Capture Screen", "Find Commands..." };
	private org.eclipse.swt.widgets.List list;
	private String command;
	private org.eclipse.swt.widgets.Button button;

	public Commands() {

		super("Commands");
		if (instance != null) {
			WindowManager.toFront(instance);
			return;
		}
		instance = this;
		WindowManager.addWindow(Commands.this);
		Display.getDefault().syncExec(() -> {

			shell.setLayout(new org.eclipse.swt.layout.GridLayout(1, true));
			list = new org.eclipse.swt.widgets.List(shell, SWT.NONE);
			list.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			list.addSelectionListener(Commands.this);
			String cmds = Prefs.get(CMDS_KEY, null);
			if (cmds != null) {
				String[] cmd = cmds.split(",");
				int len = cmd.length <= MAX_COMMANDS ? cmd.length : MAX_COMMANDS;
				boolean isDivider = false;
				for (int i = 0; i < len; i++) {
					if (divider.equals(cmd[i])) {
						isDivider = true;
						break;
					}
				}
				if (isDivider) {
					for (int i = 0; i < len; i++)
						list.add(cmd[i]);
				} else
					cmds = null;
			}
			if (cmds == null)
				reset();
			ImageJ ij = IJ.getInstance();
			shell.addKeyListener(ij);
			Executer.addCommandListener(Commands.this);
			// GUI.scale(list);
			list.addKeyListener(ij);
			/*
			 * GridBagLayout gridbag = new GridBagLayout(); GridBagConstraints c = new
			 * GridBagConstraints();
			 */
			/*
			 * c.insets = new Insets(0, 0, 0, 0); c.gridx = 0; c.gridy = 0; c.anchor =
			 * GridBagConstraints.WEST; add(list,c);
			 */
			button = new org.eclipse.swt.widgets.Button(shell, SWT.NONE);
			button.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			button.setText("Edit");
			button.addSelectionListener(Commands.this);
			button.addKeyListener(ij);
			// c.insets = new Insets(2, 6, 6, 6);
			// c.gridx = 0; c.gridy = 2; c.anchor = GridBagConstraints.CENTER;
			// add(button, c);
			shell.layout();
			shell.pack();
			org.eclipse.swt.graphics.Point size = shell.getSize();
			Point loc = Prefs.getLocation(LOC_KEY);
			if (loc != null)
				shell.setLocation(loc.x, loc.y);
			shell.setVisible(true);

		});
	}

	@Override
	public void widgetSelected(SelectionEvent e) {

		if (e.widget instanceof org.eclipse.swt.widgets.Button) {
			actionPerformed(e);
		} else if (e.widget instanceof org.eclipse.swt.widgets.List) {
			itemStateChanged(e);
		}
	}

	public void actionPerformed(SelectionEvent e) {

		GenericDialog gd = new GenericDialog("Commands");
		int dividerIndex = getDividerIndex();
		StringBuilder sb = new StringBuilder(200);
		sb.append("| ");
		for (int i = 0; i < dividerIndex; i++) {
			String cmd = list.getItem(i);
			sb.append(cmd);
			sb.append(" | ");
		}
		sb.append("Debug Mode | Hyperstack |");
		String recentCommands = sb.toString();
		gd.setInsets(5, 0, 0);
		gd.addTextAreas(recentCommands, null, 5, 28);
		int index = dividerIndex + 1;
		int n = 1;
		for (int i = index; i < list.getItemCount(); i++) {
			gd.setInsets(2, 8, 0);
			gd.addStringField("Cmd" + IJ.pad(n++, 2) + ":", list.getItem(i), 20);
		}
		gd.enableYesNoCancel(" OK ", "Reset");
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		else if (!gd.wasOKed()) {
			boolean ok = IJ.showMessageWithCancel("Commands", "Are you sure you want to reset?");
			if (ok)
				reset();
		} else {
			for (int i = index; i < list.getItemCount(); i++) {
				list.remove(i);
				list.add(gd.getNextString(), i);
			}
		}
	}

	public void itemStateChanged(SelectionEvent e) {

		// IJ.log("itemStateChanged: "+e);
		// if (e.getStateChange() == ItemEvent.SELECTED) {
		int index = list.getSelectionIndex();
		if (index < 0)
			return;
		command = list.getItem(index);
		if (!command.equals(divider)) {
			if (command.equals("Debug Mode"))
				IJ.runMacro("setOption('DebugMode')");
			else if (command.equals("Hyperstack"))
				IJ.runMacro("newImage('HyperStack', '8-bit color label', 400, 300, 3, 4, 25)");
			else
				IJ.doCommand(command);
		}
		list.deselect(index);
		// }
	}

	public String commandExecuting(String cmd2) {

		if ("Quit".equals(cmd2))
			return cmd2;
		String cmd1 = command;
		if (cmd1 == null || !cmd1.equals(cmd2)) {
			try {
				list.remove(cmd2);
			} catch (Exception e) {
			}
			Display.getDefault().syncExec(() -> {

				if (list.getItemCount() >= MAX_COMMANDS)
					list.remove(getDividerIndex() - 1);
				list.add(cmd2, 0);

			});
		}
		command = null;
		return cmd2;
	}

	private int getDividerIndex() {

		int index = 0;
		for (int i = 0; i < MAX_COMMANDS; i++) {
			String cmd = list.getItem(i);
			if (divider.equals(cmd)) {
				index = i;
				break;
			}
		}
		return index;
	}

	private void reset() {

		list.removeAll();
		list.add(divider);
		int len = commands.length < MAX_COMMANDS ? commands.length : MAX_COMMANDS - 1;
		for (int i = 0; i < len; i++)
			list.add(commands[i]);
	}

	/** Overrides shellClosed() in PlugInDialog. */
	public void shellClosed(ShellEvent e) {

		// super.close();
		e.doit = false;
		instance = null;
		WindowManager.removeWindow(Commands.this);
		Executer.removeCommandListener(this);
		Prefs.saveLocation(LOC_KEY, shell.getLocation());
		StringBuilder sb = new StringBuilder(200);
		for (int i = 0; i < list.getItemCount(); i++) {
			String cmd = list.getItem(i);
			sb.append(cmd);
			sb.append(",");
		}
		String cmds = sb.toString();
		cmds = cmds.substring(0, cmds.length() - 1);
		// IJ.log("close: "+cmds); IJ.wait(5000);
		Prefs.set(CMDS_KEY, cmds);
		e.doit = true;
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent arg0) {
		// TODO Auto-generated method stub

	}
}
