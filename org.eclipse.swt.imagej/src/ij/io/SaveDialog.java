/**/
package ij.io;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.macro.Interpreter;
import ij.plugin.frame.Recorder;

/**
 * This class displays a dialog window from which the user can save a file.
 */
public class SaveDialog {

	private String dir;
	private String name;
	private String title;
	private String ext;
	protected FileDialog fd;

	/**
	 * Displays a file save dialog with 'title' as the title, 'defaultName' as the
	 * initial file name, and 'extension' (e.g. ".tif") as the default extension.
	 */
	public SaveDialog(String title, String defaultName, String extension) {

		this.title = title;
		ext = extension;
		if (isMacro())
			return;
		String defaultDir = OpenDialog.getDefaultDirectory();
		defaultName = setExtension(defaultName, extension);
		IJ.showStatus(title);
		/*
		 * if (Prefs.useJFileChooser) jSave(title, defaultDir, defaultName); else
		 */
		save(title, defaultDir, defaultName);
		IJ.showStatus("");
		if (name != null && dir != null)
			OpenDialog.setDefaultDirectory(dir);
		IJ.showStatus(title + ": " + dir + name);
	}

	/**
	 * Displays a file save dialog, using the specified default directory and file
	 * name and extension.
	 */
	public SaveDialog(String title, String defaultDir, String defaultName, String extension) {

		this.title = title;
		ext = extension;
		if (isMacro())
			return;
		defaultName = setExtension(defaultName, extension);
		// if (Prefs.useJFileChooser)
		// jSave(title, defaultDir, defaultName);
		// else
		save(title, defaultDir, defaultName);
		IJ.showStatus(title + ": " + dir + name);
	}

	boolean isMacro() {

		String macroOptions = Macro.getOptions();
		if (macroOptions != null) {
			String path = Macro.getValue(macroOptions, title, null);
			if (path == null)
				path = Macro.getValue(macroOptions, "path", null);
			if (path != null && path.indexOf(".") == -1 && !((new File(path)).exists())) {
				// Is 'path' a macro variable?
				if (path.startsWith("&"))
					path = path.substring(1);
				Interpreter interp = Interpreter.getInstance();
				String path2 = interp != null ? interp.getStringVariable(path) : null;
				if (path2 != null)
					path = path2;
			}
			if (path != null) {
				Opener o = new Opener();
				dir = o.getDir(path);
				name = o.getName(path);
				return true;
			}
		}
		return false;
	}

	public static String setExtension(String name, String extension) {

		if (name == null || extension == null || extension.length() == 0)
			return name;
		int dotIndex = name.lastIndexOf(".");
		if (dotIndex >= 0 && (name.length() - dotIndex) <= 5) {
			if (dotIndex + 1 < name.length() && Character.isDigit(name.charAt(dotIndex + 1)))
				name += extension;
			else
				name = name.substring(0, dotIndex) + extension;
		} else if (!name.endsWith(extension))
			name += extension;
		return name;
	}

	/* A SWT file dialog! */
	void save(String title, final String defaultDir, final String defaultName) {

		Display.getDefault().syncExec(() -> {

			Shell s = new Shell(SWT.ON_TOP);
			fd = new FileDialog(s, SWT.SAVE);
			fd.setText("Save");
			if (defaultName != null)
				fd.setFileName(defaultName);
			if (defaultDir != null)
				fd.setFilterPath(defaultDir);
			name = fd.open();
			if (name != null) {
				File file = new File(name);
				name = file.getName();
				dir = fd.getFilterPath() + File.separator;
			}
			if (name == null)
				Macro.abort();

		});
	}

	private boolean noExtension(String name) {

		if (name == null)
			return false;
		int dotIndex = name.indexOf(".");
		return dotIndex == -1 || (name.length() - dotIndex) > 5;
	}

	/** Returns the selected directory. */
	public String getDirectory() {

		OpenDialog.setLastDirectory(dir);
		return dir;
	}

	/** Returns the selected file name. */
	public String getFileName() {

		if (name != null) {
			if (IJ.recording() && dir != null)
				Recorder.recordPath(title, dir + name);
			OpenDialog.setLastName(name);
		}
		return name;
	}

	public static String getPath(ImagePlus imp, String extension) {

		String title = imp != null ? imp.getTitle() : "Untitled";
		SaveDialog sd = new SaveDialog("Save As", title, extension);
		if (sd.getFileName() == null)
			return null;
		else
			return sd.getDirectory() + sd.getFileName();
	}
}
