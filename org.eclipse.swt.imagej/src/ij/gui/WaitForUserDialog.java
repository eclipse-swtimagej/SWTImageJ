package ij.gui;

import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import ij.IJ;
import ij.plugin.frame.RoiManager;

/**
 * This is a non-modal dialog box used to ask the user to perform some task
 * while a macro or plugin is running. It implements the waitForUser() macro
 * function. It is based on Michael Schmid's Wait_For_User plugin.<br>
 * Example:
 * <code>new WaitForUserDialog("Use brush to draw on overlay").show();</code>
 */
public class WaitForUserDialog implements SelectionListener, KeyListener {

	private Shell shell;

	public Shell getShell() {
		return shell;
	}

	/* Compatibility methods since we have no Frame here! */
	public boolean isVisible() {

		AtomicReference<Boolean> visible = new AtomicReference<Boolean>();
		Display.getDefault().syncExec(new Runnable() {

			public void run() {
				if (shell != null && !shell.isDisposed()) {
					visible.set(shell.isVisible());
				} else {
					visible.set(false);
				}
			}
		});
		return visible.get();
	}

	public void close() {
		Display.getDefault().syncExec(new Runnable() {

			public void run() {
				shell.close();
			}
		});
	}

	static protected int xloc = -1, yloc = -1;
	private boolean escPressed;
	protected Button okButton;
	protected Button cancelButton;

	public WaitForUserDialog(String text) {
		this("Action Required", text);
	}

	public WaitForUserDialog(String title, String text) {
		AtomicReference<String> ref = new AtomicReference<String>();
		ref.set(text);
		Display display = Display.getDefault();
		display.syncExec(new Runnable() {
			public void run() {
				IJ.protectStatusBar(false);
				if (text != null && text.startsWith("IJ: "))
					ref.set(ref.get().substring(4));

				// if (!IJ.isLinux()) label.setFont(new Font("SansSerif", Font.PLAIN, 14));
				if (IJ.isMacOSX()) {
					RoiManager rm = RoiManager.getInstance();
					if (rm != null)
						rm.runCommand("enable interrupts");
				}

				shell = new Shell(display, SWT.TITLE | SWT.ON_TOP | SWT.ICON_INFORMATION);

				Image img = display.getSystemImage(SWT.ICON_INFORMATION);
				shell.setImage(img);
				shell.setText("Select");

				shell.setLayout(new FillLayout(SWT.HORIZONTAL));

				Listener l = new Listener() {
					Point origin;

					public void handleEvent(Event e) {
						switch (e.type) {
						case SWT.MouseDown:
							origin = new Point(e.x, e.y);
							break;
						case SWT.MouseUp:
							origin = null;
							break;
						case SWT.MouseMove:
							if (origin != null) {
								Point p = Display.getDefault().map(shell, null, e.x, e.y);
								shell.setLocation(p.x - origin.x, p.y - origin.y);
							}
							break;
						}
					}
				};
				shell.addListener(SWT.MouseDown, l);
				shell.addListener(SWT.MouseUp, l);
				shell.addListener(SWT.MouseMove, l);
				shell.addListener(SWT.Close, new Listener() {
					public void handleEvent(Event event) {
						event.doit = false;
						synchronized (this) {
							notify();
						}
						xloc = shell.getLocation().x;
						yloc = shell.getLocation().y;
						event.doit = true;
					}
				});

				Composite composite = new Composite(shell, SWT.NONE);
				composite.setLayout(new GridLayout(2, true));

				Label lblNewLabel = new Label(composite, SWT.WRAP);
				lblNewLabel.addKeyListener(WaitForUserDialog.this);

				lblNewLabel.setAlignment(SWT.CENTER);
				GridData gd_lblNewLabel = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
				// gd_lblNewLabel.heightHint = 204;
				lblNewLabel.setLayoutData(gd_lblNewLabel);
				lblNewLabel.setText(ref.get());

				okButton = new Button(composite, SWT.NONE);
				GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
				// layoutData.heightHint = 30;
				okButton.setLayoutData(layoutData);
				okButton.setText("OK");
				okButton.addSelectionListener(WaitForUserDialog.this);
				okButton.addKeyListener(WaitForUserDialog.this);

				if (IJ.isMacro()) {
					// cancelButton = new Button(" Cancel ");
					cancelButton = new Button(composite, SWT.NONE);
					GridData layoutDatacancel = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
					// layoutDatacancel.heightHint = 30;
					cancelButton.setLayoutData(layoutDatacancel);
					cancelButton.setText(" Cancel ");
					cancelButton.addSelectionListener(WaitForUserDialog.this);
					cancelButton.addKeyListener(WaitForUserDialog.this);
				}
				shell.pack();
				// Label label = new Label(text, 175);
				Rectangle parentSize = IJ.getInstance().getShell().getBounds();
				Rectangle shellSize = shell.getBounds();
				int locationX = (parentSize.width - shellSize.width) / 2 + parentSize.x;
				int locationY = (parentSize.height - shellSize.height) / 2 + parentSize.y;
				shell.setLocation(new Point(locationX, locationY));
				shell.open();
				while (!shell.isDisposed()) {
					if (!display.readAndDispatch())
						display.sleep();
				}
			}
		});
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		String s = ((Button) (e.widget)).getText();
		if (s.indexOf("Cancel") >= 0) {
			escPressed = true;
		}
		shell.close();

	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void keyPressed(KeyEvent e) {
		int keyCode = e.character;
		IJ.setKeyDown(keyCode);
		if (keyCode == SWT.LF || keyCode == SWT.ESC) {
			escPressed = keyCode == SWT.ESC;
			shell.close();
		}

	}

	public boolean escPressed() {
		return escPressed;
	}

	@Override
	public void keyReleased(KeyEvent e) {
		int keyCode = e.character;
		IJ.setKeyUp(keyCode);

	}

}
