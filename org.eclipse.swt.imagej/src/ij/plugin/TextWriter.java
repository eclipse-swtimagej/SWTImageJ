package ij.plugin;
import ij.IJ;
import ij.WindowManager;
import ij.plugin.frame.Editor;
import ij.text.TextPanel;
import ij.text.TextWindow;

/** This plugin implements the File/Save As/Text command, which saves the
	contents of Editor windows and TextWindows (e.g., "Log" and "Results"). */
public class TextWriter implements PlugIn {
    
	public void run(String arg) {
		saveText();
	}
	
	void saveText() {
		Object frame = WindowManager.getActiveWindow();
		if (frame!=null && (frame instanceof TextWindow)) {
			TextPanel tp = ((TextWindow)frame).getTextPanel();
			tp.saveAs("");
		} else if (frame!=null && (frame instanceof Editor)) {
			Editor ed = (Editor)frame;
			ed.saveAs();
		} else {
			IJ.error("Save As Text",
				"This command requires a TextWindow, such\n"
				+ "as the \"Log\" window, or an Editor window. Use\n"
				+ "File>Save>Text Image to save an image as text.");
		}
	}
	
}

