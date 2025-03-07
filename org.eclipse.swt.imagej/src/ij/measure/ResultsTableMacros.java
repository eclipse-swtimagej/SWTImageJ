package ij.measure;

import java.awt.event.KeyEvent;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;

import ij.IJ;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.Recorder;

/**
 * This class implements the Apply Macro command in tables.
 * 
 * @author Michael Schmid
 */
public class ResultsTableMacros implements Runnable, DialogListener, org.eclipse.swt.events.SelectionListener,
		org.eclipse.swt.events.KeyListener {

	private static String NAME = "TableMacro.ijm";
	private String defaultMacro = "Sin=sin(row*0.1);\nCos=cos(row*0.1);\nSqr=Sin*Sin+Cos*Cos;";
	private GenericDialog gd;
	private ResultsTable rt, rtBackup;
	private org.eclipse.swt.widgets.Button runButton, resetButton, openButton, saveButton;
	private String title;
	private int runCount;
	private org.eclipse.swt.widgets.Text ta;

	public ResultsTableMacros(ResultsTable rt) {

		this.rt = rt;
		title = rt != null ? rt.getTitle() : null;
		Thread thread = new Thread(this, "ResultTableMacros");
		thread.start();
	}

	private void showDialog() {

		if (rt == null)
			rt = Analyzer.getResultsTable();
		if (rt == null || rt.size() == 0) {
			IJ.error("Results Table required");
			return;
		}
		String[] temp = rt.getHeadingsAsVariableNames();
		String[] variableNames = new String[temp.length + 2];
		variableNames[0] = "Insert...";
		variableNames[1] = "row";
		for (int i = 2; i < variableNames.length; i++)
			variableNames[i] = temp[i - 2];
		String dialogTitle = "Apply Macro to " + (title != null ? "\"" + title + "\"" : "Table");
		Object parent = title != null ? WindowManager.getWindow(title) : null;
		if (parent != null)
			// gd = new GenericDialog(dialogTitle, parent);
			gd = new GenericDialog(dialogTitle);
		else
			gd = new GenericDialog(dialogTitle);
		gd.setInsets(5, 5, 0);
		gd.addTextAreas(getMacro(), null, 12, 48);
		ta = gd.getTextArea1();
		ta.addKeyListener(this);
		org.eclipse.swt.widgets.Composite panel = new org.eclipse.swt.widgets.Composite(gd.getShell(), SWT.NONE);
		// if (IJ.isMacOSX())
		// panel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
		runButton = new org.eclipse.swt.widgets.Button(panel, SWT.NONE);
		runButton.setText("Run");
		runButton.addSelectionListener(this);
		// panel.add(runButton);
		resetButton = new org.eclipse.swt.widgets.Button(panel, SWT.NONE);
		resetButton.setText("Reset");
		resetButton.addSelectionListener(this);
		// panel.add(resetButton);
		openButton = new org.eclipse.swt.widgets.Button(panel, SWT.NONE);
		openButton.setText("Open");
		openButton.addSelectionListener(this);
		// panel.add(openButton);
		saveButton = new org.eclipse.swt.widgets.Button(panel, SWT.NONE);
		saveButton.setText("Save");
		saveButton.addSelectionListener(this);
		// panel.add(saveButton);
		gd.addPanel(panel);
		gd.addToSameRow();
		gd.addChoice("", variableNames, variableNames[0]);
		gd.addHelp("<html><body><h1>Macro Equations for Results Tables</h1><ul>"
				+ "<li>The macro, or a selection, is applied to each row of the table."
				+ "<li>A new variable starting with an Uppercase character creates<br>a new column."
				+ "<li>A new variable starting with a lowercase character is temporary."
				+ "<li>The variable <b>row</b> (row index) is pre-defined.\n"
				+ "<li>String operations are supported for the 'Label' column only (if<br>enabled"
				+ "with Analyze&gt;Set Measurements&gt;Display Label)." + "<li>Click \"<b>Run</b>\", or press "
				+ (IJ.isMacOSX() ? "cmd" : "ctrl") + "-r, to apply the macro code to the table."
				+ "<li>Select a line and press " + (IJ.isMacOSX() ? "cmd" : "ctrl")
				+ "-r to apply a line of macro code."
				+ "<li>Click \"<b>Reset</b>\" to revert to the original version of the table."
				+ "<li>The code is saved at <b>macros/TableMacro.ijm</b>, and the<br>\"Apply Macro\" command is recorded, when you click \"<b>OK</b>\"."
				+ "<li>All <b>Table.</b> macro functions (such as Table.size) refer to the<br>current (frontmost) table unless the table name is given."
				+ "</ul></body></html>");
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) { // dialog cancelled?
			rt = rtBackup;
			updateDisplay();
			return;
		}
		if (runCount == 0)
			applyMacro();
		if (IJ.recording()) {
			String macro = getMacroCode();
			macro = macro.replaceAll("\n", " ");
			if (Recorder.scriptMode()) {
				Recorder.recordCall("title = \"" + title + "\";");
				Recorder.recordCall("frame = WindowManager.getFrame(title);");
				Recorder.recordCall("rt = frame.getResultsTable();");
				Recorder.recordCall("rt.applyMacro(\"" + macro + "\");");
				Recorder.recordCall("rt.show(title);");
			} else {
				if (title.equals("Results"))
					Recorder.record("Table.applyMacro", macro);
				else
					Recorder.record("Table.applyMacro", macro, title);
			}
		}
		if (ta != null && !ta.isDisposed()) {
			IJ.saveString(ta.getText(), IJ.getDir("macros") + NAME);
		}
	}

	private void applyMacro() {

		String code = getMacroCode();
		rt.applyMacro(code);
		updateDisplay();
		runCount++;
	}

	private String getMacroCode() {

		// int start = ta.getSelectionStart();
		int start = ta.getSelection().x;
		int end = ta.getSelection().y;
		return start == end ? ta.getText() : ta.getSelectionText();
	}

	public boolean dialogItemChanged(GenericDialog gd, TypedEvent e) {

		final String variableName = gd.getNextChoice();
		if (e != null && (e.getSource() instanceof Combo) && !variableName.equals("Insert...")) {
			final int pos = ta.getCaretPosition();
			((Combo) e.getSource()).select(0);
			final org.eclipse.swt.widgets.Text textArea = ta;
			new Thread(new Runnable() {

				public void run() {

					Display.getDefault().syncExec(() -> {

						IJ.wait(100);
						textArea.setSelection(pos, pos);
						textArea.insert(variableName);
						// textArea.insert(variableName, pos);
						textArea.setSelection(pos + variableName.length());
						textArea.setFocus();

					});
				}
			}).start();
		}
		return true;
	}

	@Override
	public void widgetSelected(SelectionEvent e) {

		actionPerformed(e);
	}

	public void actionPerformed(SelectionEvent e) {

		Object source = e.getSource();
		if (source == runButton) {
			applyMacro();
		} else if (source == resetButton) {
			rt = (ResultsTable) rtBackup.clone();
			updateDisplay();
		} else if (source == openButton) {
			String macro = IJ.openAsString(null);
			if (macro == null)
				return;
			if (macro.startsWith("Error: ")) {
				IJ.error(macro);
				return;
			} else
				ta.setText(macro);
		} else if (source == saveButton) {
			ta.selectAll();
			String macro = ta.getText();
			ta.setSelection(0, 0);
			IJ.saveString(macro, null);
		}
	}

	public void keyPressed(KeyEvent e) {

		int flags = e.getModifiers();
		boolean control = (flags & KeyEvent.CTRL_MASK) != 0;
		boolean meta = (flags & KeyEvent.META_MASK) != 0;
		int keyCode = e.getKeyCode();
		if (keyCode == KeyEvent.VK_R && (control || meta))
			applyMacro();
		if (keyCode == KeyEvent.VK_Z && (control || meta)) {
			rt = (ResultsTable) rtBackup.clone();
			updateDisplay();
		}
	}

	private void updateDisplay() {

		if (title != null)
			rt.show(title);
	}

	public void keyReleased(KeyEvent e) {

	}

	public void keyTyped(KeyEvent e) {

	}

	private String getMacro() {

		String macro = IJ.openAsString(IJ.getDir("macros") + NAME);
		if (macro == null || macro.startsWith("Error:"))
			return defaultMacro;
		else {
			macro = macro.replaceAll("rowNumber", "row");
			macro = macro.replaceAll("rowIndex", "row");
			return macro;
		}
	}

	public void run() {

		rtBackup = (ResultsTable) rt.clone();
		Display.getDefault().syncExec(() -> {

			showDialog();

		});
	}

	@Override
	public void keyPressed(org.eclipse.swt.events.KeyEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void keyReleased(org.eclipse.swt.events.KeyEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void widgetDefaultSelected(SelectionEvent arg0) {
		// TODO Auto-generated method stub

	}
}
