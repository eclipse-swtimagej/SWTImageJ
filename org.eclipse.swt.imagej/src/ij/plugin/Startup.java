package ij.plugin;

import java.util.Vector;

import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;

import ij.IJ;
import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.macro.Interpreter;

/** This plugin implements the Edit/Options/Startup command. */
public class Startup implements PlugIn, SelectionListener {

	private static String NAME = "RunAtStartup.ijm";
	private GenericDialog gd;
	private static final String[] code = {"[Select from list]", "Black background", "Set default directory", "Debug mode", "10-bit (0-1023) range", "12-bit (0-4095) range", "Splash Screen", "Bolder selections", "Add to overlay", "Flip FITS images", "Calibrate conversions"};
	private String macro = "";
	private int originalLength;

	public void run(String arg) {

		macro = getStartupMacro();
		String macro2 = macro;
		if(!showDialog())
			return;
		if(!macro.equals(macro2)) {
			if(!runMacro(macro))
				return;
			saveStartupMacro(macro);
		}
	}

	public String getStartupMacro() {

		String macro = IJ.openAsString(IJ.getDirectory("macros") + NAME);
		if(macro == null || macro.startsWith("Error:"))
			return null;
		else
			return macro;
	}

	private void saveStartupMacro(String macro) {

		IJ.saveString(macro, IJ.getDirectory("macros") + NAME);
	}

	private boolean showDialog() {

		gd = new GenericDialog("Startup Macro");
		String text = "Macro code contained in this text area\nexecutes when ImageJ starts up.";
		org.eclipse.swt.graphics.Font font = ImageJ.SansSerif14Swt;
		gd.setInsets(5, 15, 0);
		gd.addMessage(text, font);
		gd.setInsets(5, 10, 0);
		gd.addTextAreas(macro, null, 15, 50);
		gd.addChoice("Add code:", code, code[0]);
		Vector choices = gd.getChoices();
		if(choices != null) {
			Combo choice = (Combo)choices.elementAt(0);
			Display.getDefault().syncExec(new Runnable() {

				public void run() {

					choice.addSelectionListener(Startup.this);
				}
			});
		}
		gd.showDialog();
		macro = gd.getNextText();
		return !gd.wasCanceled();
	}

	private boolean runMacro(String macro) {

		Interpreter interp = new Interpreter();
		interp.run(macro, null);
		if(interp.wasError())
			return false;
		else
			return true;
	}

	public void itemStateChanged(SelectionEvent e) {

		Combo choice = (Combo)e.getSource();
		String item = choice.getItem(choice.getSelectionIndex());
		String statement = null;
		if(item.equals(code[1]))
			statement = "setOption(\"BlackBackground\", true);\n";
		else if(item.equals(code[2]))
			statement = "File.setDefaultDir(getDir(\"downloads\"));\n";
		else if(item.equals(code[3]))
			statement = "setOption(\"DebugMode\", true);\n";
		else if(item.equals(code[4]))
			statement = "call(\"ij.ImagePlus.setDefault16bitRange\", 10);\n";
		else if(item.equals(code[5]))
			statement = "call(\"ij.ImagePlus.setDefault16bitRange\", 12);\n";
		else if(item.equals(code[6]))
			statement = "run(\"About ImageJ...\");\nwait(3000);\nclose(\"About ImageJ\");\n";
		else if(item.equals(code[7]))
			statement = "Roi.setDefaultStrokeWidth(2);\n";
		else if(item.equals(code[8]))
			statement = "setOption(\"Add to overlay\", true);\n";
		else if(item.equals(code[9]))
			statement = "setOption(\"FlipFitsImages\", false);\n";
		else if(item.equals(code[10]))
			statement = "setOption(\"CalibrateConversions\", true);\n";
		if(statement != null) {
			org.eclipse.swt.widgets.Text ta = gd.getTextArea1();
			int caret = ta.getCaretPosition();
			ta.setSelection(caret);
			ta.insert(statement);
			if(IJ.isMacOSX())
				ta.setFocus();
		}
	}

	@Override
	public void widgetSelected(SelectionEvent e) {

		itemStateChanged(e);
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {

		// TODO Auto-generated method stub
	}
}
