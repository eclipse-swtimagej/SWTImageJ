package ij.plugin.frame;

import java.awt.event.FocusEvent;
import java.awt.event.WindowEvent;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import ij.IJ;
import ij.ImageJ;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.plugin.frame.swt.WindowSwt;

/** This is a non-modal dialog that plugins can extend. */
public class PlugInDialog implements PlugIn, WindowSwt, ShellListener, org.eclipse.swt.events.FocusListener {

	protected Shell shell;
	protected String title;

	@Override
	public Shell getShell() {
		return shell;
	}

	public PlugInDialog(String title) {
		this(title, SWT.MODELESS | SWT.DIALOG_TRIM | SWT.RESIZE);
	}

	public PlugInDialog(String title, int style) {

		Display display = Display.getDefault();
		Display.getDefault().syncExec(() -> {
			shell = new Shell(display, style);

			shell.setText(title);

			PlugInDialog.this.title = title;
			ImageJ ij = IJ.getInstance();
			shell.addShellListener(PlugInDialog.this);
			shell.addFocusListener(PlugInDialog.this);

			if (ij != null && !IJ.isMacOSX()) {
				org.eclipse.swt.graphics.Image img = ij.getShell().getImage();
				if (img != null)
					try {
						shell.setImage(img);
					} catch (Exception e) {
					}
			}

		});
	}

	public void run(String arg) {
	}

	@Override
	public void shellClosed(ShellEvent e) {
		e.doit = false;
		if (e.getSource() == this) {
			if (IJ.recording())
				Recorder.record("run", "Close");
		}
		// dispose();
		WindowManager.removeWindow(this);
		e.doit = true;

	}

	/** Closes this window. */
	public void close() {

	}

	@Override
	public void shellActivated(ShellEvent e) {
		WindowManager.setWindow(this);

	}

	public void windowActivated(WindowEvent e) {
		// WindowManager.setWindow(this);
	}

	public void focusGained(FocusEvent e) {
		// WindowManager.setWindow(this);
	}

	public void windowOpened(WindowEvent e) {
	}

	public void windowClosed(WindowEvent e) {

	}

	public void windowIconified(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e) {
	}

	public void windowDeactivated(WindowEvent e) {
	}

	public void focusLost(FocusEvent e) {
	}

	@Override
	public void focusGained(org.eclipse.swt.events.FocusEvent e) {
		WindowManager.setWindow(this);

	}

	@Override
	public void focusLost(org.eclipse.swt.events.FocusEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellDeactivated(ShellEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellDeiconified(ShellEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellIconified(ShellEvent arg0) {
		// TODO Auto-generated method stub

	}

}
