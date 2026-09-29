package ij.plugin.frame.swt;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/*
 * Since many ImageJ SWT GUI interfaces are no Frames anymore (shells are now created inside a class)
 * we need an interface to get access to the underlying shell to work, e.g., with the WindowManager and macros
 * of ImageJ! The Window manager, e.g., of ImageJ is the control center for all Image, ImageWindows
 * and Dialog activity! Some often used AWT Frame class methods have been implemented as default methods and are sometime
 * overwritten (e.g., in the ImageWindow class). if a macro expects a frame (shell) the call is delegated
 * to the default shell methods (if not overwritten) and are called here in a SWT runnable.
 */
public interface WindowSwt {

	/*
	 * This method should return a shell in each class implementing this interface!
	 * It is used in the default methods below.
	 */
	public Shell getShell();

	/**
	 * @return whether shell is non-null and not yet disposed - every default method below checks
	 *         this before touching the shell, since ij.WindowManager.closeAllWindows() (app-wide
	 *         shutdown) calls close()/getTitle() etc. on every registered window in turn, and by
	 *         then some of them may already have had their Shell disposed through a different
	 *         path (e.g. a parent Composite/Shell cascading its disposal first). Without this
	 *         guard, Display.syncExec() doesn't protect against that itself - it just runs the
	 *         given Runnable and rethrows whatever it throws, wrapped, so calling e.g.
	 *         shell.getText() inside it on an already-disposed shell still crashes with
	 *         "SWTException: Widget is disposed".
	 */
	default public boolean isShellUsable() {

		Shell shell = getShell();
		return shell != null && !shell.isDisposed();
	}

	default public void setTitle(String string) {

		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				getShell().setText(string);
			}
		});
	}

	default public String getTitle() {

		AtomicReference<String> title = new AtomicReference<String>("");
		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				title.set(getShell().getText());
			}
		});
		return title.get();
	}

	default public boolean isVisible() {

		AtomicReference<Boolean> visible = new AtomicReference<Boolean>(false);
		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				visible.set(getShell().isVisible());
			}
		});
		return visible.get();
	}

	default public void setVisible(boolean visible) {

		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				getShell().setVisible(true);
			}
		});
	}

	default public void show() {

		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				getShell().setVisible(true);
			}
		});
	}

	default public Point getLocation() {

		AtomicReference<Point> p = new AtomicReference<Point>(new Point(0, 0));
		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				p.set(getShell().getLocation());
			}
		});
		return p.get();
	}

	default public void setLocation(Point p) {

		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				getShell().setLocation(p);
			}
		});
	}

	default public Point getSize() {

		AtomicReference<Point> size = new AtomicReference<Point>(new Point(0, 0));
		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				size.set(getShell().getSize());
			}
		});
		return size.get();
	}

	default public Point getShellSize() {

		AtomicReference<Point> size = new AtomicReference<Point>(new Point(0, 0));
		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				size.set(getShell().getSize());
			}
		});
		return size.get();
	}

	default public Rectangle getBounds() {

		AtomicReference<Rectangle> rec = new AtomicReference<Rectangle>(new Rectangle(0, 0, 0, 0));
		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				rec.set(getShell().getBounds());
			}
		});
		return rec.get();
	}

	default public Rectangle getMaximumBounds() {

		AtomicReference<Rectangle> rec = new AtomicReference<Rectangle>(new Rectangle(0, 0, 0, 0));
		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				rec.set(getShell().getBounds());
			}
		});
		return rec.get();
	}

	default public void setResizable(boolean resizeable) {

	}

	default public void setShellSize(Point p, Composite embeddedParent, Shell shell) {

		Display.getDefault().syncExec(() -> {
			if(shell != null && !shell.isDisposed()) {
				shell.setSize(p);
			}
		});
	}

	default public void toFront(Composite embeddedParent) {

		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				getShell().setActive();
			}
		});
	}

	default public void toFront() {

		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				getShell().setActive();
			}
		});
	}

	/* Compatibility methods since we have no Frame here! */
	default public void setActive() {

		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				getShell().setActive();
			}
		});
	}

	default public void setSize(int x, int y) {

		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				getShell().setSize(x, y);
			}
		});
	}

	default public void validate() {

		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				getShell().layout(true);
			}
		});
	}

	default public void pack() {

		Display.getDefault().syncExec(() -> {
			if(isShellUsable()) {
				getShell().pack(true);
			}
		});
	}
}
