package ij.plugin.frame;

import java.awt.event.FocusEvent;
import java.awt.event.WindowEvent;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import ij.IJ;
import ij.ImageJ;
import ij.Menus;
import ij.Prefs;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.plugin.frame.swt.WindowSwt;

/** This is a closeable window that plugins can extend. */
public class PlugInFrame implements PlugIn, WindowSwt, ShellListener, org.eclipse.swt.events.FocusListener {

	String title;
	public Shell shell;
	
	public PlugInFrame(String title) {
		this(title,SWT.DIALOG_TRIM | SWT.RESIZE);
	}

	public PlugInFrame(String title, int style) {

		Display display = Display.getDefault();
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				shell = new Shell(display, style);
				shell.setText(title);
				// enableEvents(TypedEvent.WINDOW_EVENT_MASK);
				PlugInFrame.this.title = title;
				ImageJ ij = IJ.getInstance();
				shell.addShellListener(PlugInFrame.this);
				shell.addFocusListener(PlugInFrame.this);
				// if (IJ.isLinux()) setBackground(ImageJ.backgroundColor);
				if (ij != null && !IJ.isMacOSX()) {
					org.eclipse.swt.graphics.Image img = ij.getShell().getImage();
					if (img != null)
						try {
							shell.setImage(img);
						} catch (Exception e) {
						}
				}
			}
		});

		// shell.open();
	}

	public void run(String arg) {

	}
	
	public Shell getShell() {
		return shell;
	}
	

	@Override
	public void shellActivated(ShellEvent e) {
		
		WindowManager.setWindow(this);

	}
	/**
	 * Closes this window. We use no close here because there exists a shell.close()
	 * function!
	 */
	public void close() {
		// setVisible(false);
		/**
		 * Closes this window. We use no close here because there exists a shell.close()
		 * function!
		 */
		// close();
		WindowManager.removeWindow(this);
	}

	@Override
	public void shellClosed(ShellEvent e) {
		e.doit = false;
		if (e.getSource() == this) {
			if (IJ.recording())
				Recorder.record("run", "Close");
		}
		/*Calls the subclass closeSwt method which then calls this closeSwt method!*/
		close();
		// dispose();
		//WindowManager.removeWindow(this);
		e.doit = true;
	}

	@Override
	public void shellDeactivated(ShellEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellDeiconified(ShellEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellIconified(ShellEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void focusGained(org.eclipse.swt.events.FocusEvent e) {
		WindowManager.setWindow(this);

	}

	@Override
	public void focusLost(org.eclipse.swt.events.FocusEvent e) {
		// TODO Auto-generated method stub

	}
}
