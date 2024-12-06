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
	 * This method should return a shell in each class implementing this interface! It is used in the
	 * default methods below.
	 */
	public Shell getShell();

	default public void setTitle(String string) {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				shell.setText(string);
			}
		});
	}

	default public String getTitle() {

		AtomicReference<String> title = new AtomicReference<String>();
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				title.set(shell.getText());
			}
		});
		return title.get();
	}

	default public boolean isVisible() {

		AtomicReference<Boolean> visible = new AtomicReference<Boolean>();
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				visible.set(shell.isVisible());
			}
		});
		return visible.get();
	}
	
	default public void setVisible(boolean visible) {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				shell.setVisible(true);
			}
		});
	}

	default public void show() {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				shell.setVisible(true);
			}
		});
	}

	default public Point getLocation() {

		AtomicReference<Point> p = new AtomicReference<Point>();
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				p.set(shell.getLocation());
			}
		});
		Point point = p.get();
		return point;
	}

	default public void setLocation(Point p) {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				shell.setLocation(p);
			}
		});
	}

	default public Point getSize() {

		AtomicReference<Point> size = new AtomicReference<Point>();
		Display dis = Display.getDefault();
		dis.syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				size.set(shell.getSize());
			}
		});
		return size.get();
	}

	default public Point getShellSize() {

		AtomicReference<Point> size = new AtomicReference<Point>();
		Display dis = Display.getDefault();
		dis.syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				size.set(shell.getSize());
			}
		});
		return size.get();
	}

	default public Rectangle getBounds() {

		AtomicReference<Rectangle> rec = new AtomicReference<Rectangle>();
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				rec.set(shell.getBounds());
			}
		});
		return rec.get();
	}

	default public Rectangle getMaximumBounds() {

		AtomicReference<Rectangle> rec = new AtomicReference<Rectangle>();
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				rec.set(shell.getBounds());
			}
		});
		return rec.get();
	}

	default public void setResizable(boolean resizeable) {

	}

	default public void setShellSize(Point p, Composite embeddedParent, Shell shell) {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				shell.setSize(p);
			}
		});
	}

	default public void toFront(Composite embeddedParent) {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				shell.forceActive();
			}
		});
	}
	
	default public void toFront() {
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				shell.forceActive();
			}
		});
	}
	
	/* Compatibility methods since we have no Frame here! */
	default public void setActive() {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {
				Shell shell = getShell();
				shell.setActive();
			}
		});
	}

	default public void setSize(int x, int y) {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				shell.setSize(x, y);
			}
		});
	}

	default public void validate() {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				shell.layout(true);
			}
		});
	}

	default public void pack() {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				shell.pack(true);
			}
		});
	}
}
