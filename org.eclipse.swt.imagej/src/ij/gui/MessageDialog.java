package ij.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

/* Changed for Bio7! A modal dialog base on SWT as a replacement for the ImageJ message dialog! */

public class MessageDialog {
	protected MultiLineLabel label;
	private boolean escapePressed;
	protected int result;
	protected Shell shell;

	public MessageDialog(String title, String message) {

		Display display = Display.getDefault();
		display.syncExec(() -> {
			shell = new Shell(display);
			MessageBox messageBox = new MessageBox(shell,

					SWT.ICON_WARNING);

			messageBox.setText(title);
			messageBox.setMessage(message);
			result = messageBox.open();
			if (result == SWT.OK) {
				escapePressed = true;
			}

		});

	}

	public Shell getShell() {

		return shell;
	}

	public boolean escapePressed() {
		return escapePressed;
	}

}
