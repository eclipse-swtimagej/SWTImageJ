package ij.plugin.frame.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
/* Changed for Bio7! A modal dialog base on SWT as a replacement for the ImageJ message dialog! */

public class MessageDialog {

	private boolean escapePressed;
	protected int result;

	public MessageDialog(Shell parent, String title, String message) {

		Display display = Display.getDefault();
		display.syncExec(new Runnable() {
			public void run() {

				MessageBox messageBox = new MessageBox(parent, SWT.ICON_WARNING);
				messageBox.setText(title);
				messageBox.setMessage(message);
				result = messageBox.open();
				if(result == SWT.OK) {
					escapePressed = true;
				}
			}
		});
	}

	public boolean escapePressed() {

		return escapePressed;
	}
}
