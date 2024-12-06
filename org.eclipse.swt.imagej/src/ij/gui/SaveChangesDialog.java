package ij.gui;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * A modal dialog box with a one line message and
 * "Don't Save", "Cancel" and "Save" buttons.
 */
/* Changed using JFace! */
public class SaveChangesDialog {

	private boolean cancelPressed, yesPressed;
	private MessageDialog dialog;

	public SaveChangesDialog(Shell parent, String fileName) {

		this(parent, "Save?", fileName, "  Yes  ", "  No  ");
	}

	public SaveChangesDialog(Shell parent, String title, String fileName, String yesLabel, String noLabel) {
		Display dis = Display.getDefault();
		dis.syncExec(new Runnable() {

			public void run() {

				String msg;
				if(fileName.startsWith("Save "))
					msg = fileName;
				else {
					if(fileName.length() > 22)
						msg = "Save changes to\n" + "\"" + fileName + "\"?";
					else
						msg = "Save changes to \"" + fileName + "\"?";
				}
				dialog = new org.eclipse.jface.dialogs.MessageDialog(parent, title, null, msg, org.eclipse.jface.dialogs.MessageDialog.CONFIRM, new String[]{"Save", "Don't Save", "Cancel"}, 0);
				int result = dialog.open();
				switch(result) {
					case 0:
						yesPressed = true;
						break;
					case 1:
						break;
					case 2:
						cancelPressed = true;
						break;
					default:
						break;
				}
			}
		});
	}

	/** Returns true if the user dismissed dialog by pressing "Cancel". */
	public boolean cancelPressed() {

		return cancelPressed;
	}

	/** Returns true if the user dismissed dialog by pressing "Yes". */
	public boolean yesPressed() {

		return yesPressed;
	}

	void closeDialog() {

		// dispose();
	}
}
