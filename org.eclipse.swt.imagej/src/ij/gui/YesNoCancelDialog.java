/*Yes, No, Cancel Dialog copied from the Bio7 project.
 * Author: Marcel Austenfeld*/
package ij.gui;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * A modal dialog box with a one line message and "Yes", "No" and "Cancel"
 * buttons.
 */
/*Changed for Bio7 using JFace!*/
public class YesNoCancelDialog {

	private boolean cancelPressed, yesPressed;

	private MessageDialog dialog;

	public YesNoCancelDialog(String title, String msg) {
		this( title, msg, "  Yes  ", "  No  ");
	}

	public YesNoCancelDialog( String title, String msg, String yesLabel, String noLabel) {
		//if (msg.contains("[NON_BLOCKING]")) {
				//	setModal(false);
				//	msg = msg.replace("[NON_BLOCKING]", "");
				//}
		Display dis = Display.getDefault();
		dis.syncExec(new Runnable() {

			public void run() {

				if (msg.startsWith("Save")) {
					dialog = new org.eclipse.jface.dialogs.MessageDialog(new Shell(Display.getDefault()), title, null, msg,
							org.eclipse.jface.dialogs.MessageDialog.CONFIRM,
							new String[] { "Save", "Don't Save", "Cancel" }, 0);
				} else {
					dialog = new org.eclipse.jface.dialogs.MessageDialog(new Shell(Display.getDefault()), title, null, msg,
							org.eclipse.jface.dialogs.MessageDialog.CONFIRM,
							new String[] { yesLabel, noLabel, "Cancel" }, 0);
				}
				int result = dialog.open();

				switch (result) {
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
