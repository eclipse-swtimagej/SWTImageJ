package ij.io;

import java.awt.EventQueue;
import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import ij.IJ;
import ij.Macro;
import ij.Prefs;
import ij.plugin.frame.Recorder;

/** This class displays a dialog box that allows the user can select a directory. */
public class DirectoryChooser {

	private String directory;
	private String title;

	/** Display a dialog using the specified title. */
	public DirectoryChooser(String title) {

		this.title = title;
		if(IJ.isMacOSX() && !Prefs.useJFileChooser)
			getDirectoryUsingFileDialog(title);
		else {
			String macroOptions = Macro.getOptions();
			if(macroOptions != null)
				directory = Macro.getValue(macroOptions, title, null);
			if(directory == null) {
				IJ.showStatus(title);
				if(EventQueue.isDispatchThread())
					getDirectoryUsingJFileChooserOnThisThread(title);
				else
					getDirectoryUsingJFileChooser(title);
				IJ.showStatus("");
			}
		}
	}

	// runs SWT Directory Chooser on event dispatch thread to avoid possible thread deadlocks
	void getDirectoryUsingJFileChooser(final String title) {

		// Java2.setSystemLookAndFeel();
		try {
			Display defaultDis = Display.getDefault();
			defaultDis.syncExec(new Runnable() {

				public void run() {

					// Shell must be created with style SWT.NO_TRIM
					Shell shell = new Shell(defaultDis, SWT.NO_TRIM | SWT.ON_TOP);
					DirectoryDialog dlg = new DirectoryDialog(shell);
					String defaultDir = OpenDialog.getDefaultDirectory();
					if(defaultDir != null) {
						File f = new File(defaultDir);
						if(IJ.debugMode)
							IJ.log("DirectoryChooser-setCurrentDir: " + f);
						dlg.setFilterPath(defaultDir);
					}
					dlg.setText(title);
					dlg.setMessage(title);
					directory = dlg.open();
					directory = IJ.addSeparator(directory);
					OpenDialog.setDefaultDirectory(directory);
					/* We print the path of the selected folder! */
					/*
					 * JFileChooser chooser = new JFileChooser();
					 * chooser.setDialogTitle(title);
					 * chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					 * String defaultDir = OpenDialog.getDefaultDirectory();
					 * if (defaultDir!=null) {
					 * File f = new File(defaultDir);
					 * if (IJ.debugMode)
					 * IJ.log("DirectoryChooser,setSelectedFile: "+f);
					 * chooser.setSelectedFile(f);
					 * }
					 * chooser.setApproveButtonText("Select");
					 * if (chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
					 * File file = chooser.getSelectedFile();
					 * directory = file.getAbsolutePath();
					 * directory = IJ.addSeparator(directory);
					 * OpenDialog.setDefaultDirectory(directory);
					 * }
					 */
				}
			});
		} catch(Exception e) {
		}
	}

	// runs SWT Directory Chooser
	void getDirectoryUsingJFileChooserOnThisThread(final String title) {

		// Java2.setSystemLookAndFeel();
		try {
			Shell shell = new Shell(Display.getDefault(), SWT.NO_TRIM | SWT.ON_TOP);
			DirectoryDialog dlg = new DirectoryDialog(shell);
			String defaultDir = OpenDialog.getDefaultDirectory();
			if(defaultDir != null) {
				File f = new File(defaultDir);
				if(IJ.debugMode)
					IJ.log("DirectoryChooser-setCurrentDir: " + f);
				dlg.setFilterPath(defaultDir);
			}
			dlg.setText(title);
			dlg.setMessage(title);
			directory = dlg.open();
			directory = IJ.addSeparator(directory);
			OpenDialog.setDefaultDirectory(directory);
			/*
			 * JFileChooser chooser = new JFileChooser();
			 * chooser.setDialogTitle(title);
			 * chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			 * String defaultDir = OpenDialog.getDefaultDirectory();
			 * if (defaultDir!=null) {
			 * File f = new File(defaultDir);
			 * if (IJ.debugMode)
			 * IJ.log("DirectoryChooser,setSelectedFile: "+f);
			 * chooser.setSelectedFile(f);
			 * }
			 * chooser.setApproveButtonText("Select");
			 * if (chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
			 * File file = chooser.getSelectedFile();
			 * directory = file.getAbsolutePath();
			 * directory = IJ.addSeparator(directory);
			 * OpenDialog.setDefaultDirectory(directory);
			 * }
			 */
		} catch(Exception e) {
		}
	}

	// (On Mac OS X, we can select directories using the native file open dialog) -> In SWT not necessary!
	void getDirectoryUsingFileDialog(String title) {

		Display defaultDis = Display.getDefault();
		defaultDis.syncExec(new Runnable() {

			public void run() {

				Shell shell = new Shell(Display.getDefault(), SWT.NO_TRIM | SWT.ON_TOP);
				DirectoryDialog dlg = new DirectoryDialog(shell);
				String defaultDir = OpenDialog.getDefaultDirectory();
				if(defaultDir != null) {
					File f = new File(defaultDir);
					if(IJ.debugMode)
						IJ.log("DirectoryChooser,setSelectedFile: " + f);
					dlg.setFilterPath(defaultDir);
				}
				dlg.setText(title);
				dlg.setMessage(title);
				directory = dlg.open();
				directory = IJ.addSeparator(directory);
				OpenDialog.setDefaultDirectory(directory);
				/*
				 * boolean saveUseJFC = Prefs.useJFileChooser;
				 * Prefs.useJFileChooser = false;
				 * System.setProperty("apple.awt.fileDialogForDirectories", "true");
				 * String dir=null, name=null;
				 * String defaultDir = OpenDialog.getDefaultDirectory();
				 * if (defaultDir!=null) {
				 * File f = new File(defaultDir);
				 * dir = f.getParent();
				 * name = f.getName();
				 * }
				 * if (IJ.debugMode)
				 * IJ.log("DirectoryChooser: dir=\""+dir+"\",  file=\""+name+"\"");
				 * OpenDialog od = new OpenDialog(title, dir, null);
				 * String odDir = od.getDirectory();
				 * if (odDir==null)
				 * directory = null;
				 * else {
				 * directory = odDir + od.getFileName() + "/";
				 * OpenDialog.setDefaultDirectory(directory);
				 * }
				 * System.setProperty("apple.awt.fileDialogForDirectories", "false");
				 * Prefs.useJFileChooser = saveUseJFC;
				 */
			}
		});
	}

	/** Returns the directory selected by the user. */
	public String getDirectory() {

		if(IJ.debugMode)
			IJ.log("DirectoryChooser.getDirectory: " + directory);
		if(IJ.recording() && !IJ.isMacOSX())
			Recorder.recordPath(title, directory);
		return directory;
	}

	/** Sets the default directory presented in the dialog. */
	public static void setDefaultDirectory(String dir) {

		if(dir == null || (new File(dir)).isDirectory())
			OpenDialog.setDefaultDirectory(dir);
	}
	// private void setSystemLookAndFeel() {
	// try {
	// UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
	// } catch(Throwable t) {}
	// }
}
