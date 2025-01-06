package ij.gui;

import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.FocusEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseWheelListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Menus;
import ij.Prefs;
import ij.WindowManager;
import ij.macro.Interpreter;
import ij.measure.Calibration;
import ij.plugin.PointToolOptions;
import ij.plugin.frame.Channels;
import ij.util.Tools;

/**
 * SWT port of the ImageWindow. Please note that the ImageWindows is not a shell
 * itself but creates a shell if we don't use the embedded mode to embed the
 * ImageWindows into another display. This is different from the original
 * implementation whereat the window is a AWT Frame.
 */
public class ImageWindow implements MouseWheelListener {

	private static String file;
	public Display display;
	private ScrollBar vBar1 = null;
	private ScrollBar hBar1 = null;
	private ImageJ imagej;
	public Composite parentComposite;
	private GridData gd_composite;
	// public Composite fillComp;
	public static final int MIN_WIDTH = 128;
	public static final int MIN_HEIGHT = 32;
	public static final int HGAP = 5;
	public static final int VGAP = 5;
	public static final String LOC_KEY = "image.loc";
	protected ImagePlus imp;
	protected ImageJ ij;
	protected ImageCanvas ic;
	private double initialMagnification = 1;
	private int newWidth, newHeight;
	protected boolean closed;
	private boolean newCanvas;
	private boolean unzoomWhenMinimizing = true;
	Rectangle maxWindowBounds; // largest possible window on this screen
	Rectangle maxBounds; // Size of this window after it is maximized
	long setMaxBoundsTime;
	private int sliderHeight;
	private static final int XINC = 12;
	private static final int YINC = 16;
	private final double SCALE = Prefs.getGuiScale();
	private int TEXT_GAP = 11;
	private static int xbase = -1;
	private static int ybase;
	private static int xloc;
	private static int yloc;
	private static int count;
	private static boolean centerOnScreen;
	private static Point nextLocation;
	public static long setMenuBarTime;
	private int textGap = centerOnScreen ? 0 : TEXT_GAP;
	private int screenHeight, screenWidth;
	public Shell shell;
	private Composite embeddedParent;
	private Label infoLabel;
	// public static org.eclipse.swt.graphics.Color white =
	// Display.getDefault().getSystemColor(SWT.COLOR_WHITE);
	/**
	 * This variable is set false if the user presses the escape key or closes the
	 * window.
	 */
	public boolean running;
	/**
	 * This variable is set false if the user clicks in this window, presses the
	 * escape key, or closes the window.
	 */
	public boolean running2;

	/**
	 * Runs the application
	 */
	public void run() {

	}

	public Label getImageWindowInfoLabel() {

		return infoLabel;
	}

	public void setTitle(String string) {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				if (shell != null && !shell.isDisposed())
					shell.setText(string);
			}
		});
	}

	/* Compatibility methods since we have no Frame here! */
	public String getTitle() {

		return imp.getTitle();
	}

	/* Compatibility methods since we have no Frame here! */
	public boolean isVisible() {

		AtomicReference<Boolean> visible = new AtomicReference<Boolean>();
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				visible.set(shell.isVisible());
			}
		});
		return visible.get();
	}

	public void setVisible(boolean visible) {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				Shell shell = getShell();
				shell.setVisible(true);
			}
		});
	}

	/* Compatibility methods since we have no Frame here! */
	public void show() {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				shell.setVisible(true);
			}
		});
	}

	/* Compatibility methods since we have no Frame here! */
	public Point getLocation() {

		AtomicReference<Point> p = new AtomicReference<Point>();
		if (embeddedParent == null) {
			Display.getDefault().syncExec(new Runnable() {

				public void run() {

					p.set(shell.getLocation());
				}
			});
		} else {
			if (embeddedParent.isDisposed() == false) {
				Display.getDefault().syncExec(new Runnable() {

					public void run() {

						p.set(embeddedParent.toDisplay(embeddedParent.getLocation()));
					}
				});
			}
		}
		Point point = p.get();
		if (point == null) {
			return new Point(0, 0);
		}
		return point;
	}

	/* Compatibility methods since we have no Frame here! */
	public void setLocation(Point p) {

		if (embeddedParent == null) {
			Display.getDefault().syncExec(new Runnable() {

				public void run() {

					shell.setLocation(p);
				}
			});
		}
	}

	/* Compatibility methods since we have no Frame here! */
	public Point getSize() {

		if (embeddedParent != null) {
			return null;
		}
		AtomicReference<Point> size = new AtomicReference<Point>();
		Display dis = Display.getDefault();
		dis.syncExec(new Runnable() {

			public void run() {

				size.set(parentComposite.getSize());
			}
		});
		return size.get();
	}

	/* Compatibility methods since we have no Frame here! */
	public Point getShellSize() {

		if (shell == null) {
			return null;
		}
		AtomicReference<Point> size = new AtomicReference<Point>();
		Display dis = Display.getDefault();
		dis.syncExec(new Runnable() {

			public void run() {

				size.set(shell.getSize());
			}
		});
		return size.get();
	}

	/* Compatibility methods since we have no Frame here! */
	public int getWidth() {

		return getSize().x;
	}

	/* Compatibility methods since we have no Frame here! */
	public int getHeight() {

		return getSize().y;
	}

	/* Compatibility methods since we have no Frame here! */
	public Rectangle getBounds() {

		AtomicReference<Rectangle> rec = new AtomicReference<Rectangle>();
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				rec.set(parentComposite.getBounds());
			}
		});
		return rec.get();
	}

	/* Compatibility methods since we have no Frame here! */
	public void setResizable(boolean resizeable) {

	}

	/* Compatibility methods since we have no Frame here! */
	public void setShellSize(Point p) {

		if (embeddedParent == null) {
			Display.getDefault().syncExec(new Runnable() {

				public void run() {

					shell.setSize(p);
				}
			});
		}
	}

	/* Compatibility methods since we have no Frame here! */
	public void toFront() {

		if (embeddedParent == null) {
			Display.getDefault().syncExec(new Runnable() {

				public void run() {

					shell.forceActive();
				}
			});
		}
	}

	/* Compatibility methods since we have no Frame here! */
	public void setSize(int x, int y) {

		if (embeddedParent == null) {
			Display.getDefault().syncExec(new Runnable() {

				public void run() {

					shell.setSize(x, y);
				}
			});
		}
	}

	/* Compatibility methods since we have no Frame here! */
	public void validate() {

		if (embeddedParent == null) {
			Display.getDefault().syncExec(new Runnable() {

				public void run() {

					shell.layout(true);
				}
			});
		}
	}

	/* Compatibility methods since we have no Frame here! */
	public void pack() {

		if (embeddedParent == null) {
			Display.getDefault().syncExec(new Runnable() {

				public void run() {

					shell.pack(true);
				}
			});
		}
	}

	public Shell getShell() {

		return shell;
	}

	public ImageCanvas getImageCanvas() {

		return ic;
	}

	public void scResizeEvent(int width, int height) {

		gd_composite.widthHint = width;
		gd_composite.heightHint = height;
		// imageCanvas.setSourceRect(new Rectangle(0, 0, width, height));
		Display dis = Display.getDefault();
		dis.syncExec(new Runnable() {

			public void run() {

				parentComposite.layout();
			}
		});
	}
	/* ImageWindow from ImageJ starts here! */

	public ImageWindow(String title) {

		// super(title);
	}
	/*
	 * public ImageWindow(ImagePlus imp) { this(imp, null); }
	 */

	public ImageWindow(ImagePlus imp) {

		this(imp, null);
	}

	public ImageWindow(ImagePlus imp, ImageCanvas ic) {

		ij = IJ.getInstance();
		this.imp = imp;
		boolean openAsHyperStack = imp.getOpenAsHyperStack();
		embeddedParent = imp.getEditorParent();
		/* We create a shell if we don't have another parent! */
		if (embeddedParent == null) {
			createShell();
		}
		/*
		 * We create the main composite (in a grid layout in which the image canvas is
		 * embedded and then embed it in a given parent (editor, view, etc.)!
		 */
		createMainComposite(imp, embeddedParent);
		if (ic == null) {
			ic = (this instanceof PlotWindow) ? new PlotCanvas(parentComposite, imp)
					: new ImageCanvas(parentComposite, imp);
			newCanvas = true;
		}
		/*
		 * This is a SWT adaption for: if (previousWindow != null) - see below and:
		 * ij.plugin.GelAnalyzer
		 */
		else {
			ic.setParent(parentComposite);
		}
		if (ic.isDisposed() == false) {
			ic.setLayoutData(gd_composite);
			// ic.fitToWindow();
			if (embeddedParent == null) {
				shell.setText(imp.getTitle());
			}
		}
		/*
		 * if (ic==null) { //ic = (this instanceof PlotWindow) ? new PlotCanvas(imp) :
		 * new ImageCanvas(imp); /*Changed for SWT. PlotCanvas, etc. are not supported
		 * at the moment! ic=new ImageCanvas(imp); newCanvas=true; }
		 */
		this.ic = ic;
		// Rectangle max = Display.getDefault().getPrimaryMonitor().getBounds();
		// super(title);
		// super(imp.getTitle());
		if (SCALE > 1.0) {
			TEXT_GAP = (int) (TEXT_GAP * SCALE);
			textGap = centerOnScreen ? 0 : TEXT_GAP;
		}
		/*
		 * if (Prefs.blackCanvas && getClass().getName().equals("ij.gui.ImageWindow")) {
		 * setForeground(Color.white); setBackground(Color.black); } else {
		 * setForeground(Color.black); if (IJ.isLinux())
		 * setBackground(ImageJ.backgroundColor); else setBackground(Color.white); }
		 */
		ImageWindow previousWindow = imp.getWindow();
		/*
		 * setLayout(new ImageLayout(ic)); add(ic); addFocusListener(this);
		 * addWindowListener(this); addWindowStateListener(this); addKeyListener(ij);
		 * setFocusTraversalKeysEnabled(false); if (!(this instanceof StackWindow))
		 * addMouseWheelListener(this); setResizable(true);
		 */
		if (!(this instanceof HistogramWindow && IJ.isMacro() && Interpreter.isBatchMode())) {
			WindowManager.addWindow(this);
			imp.setWindow(this);
		}
		if (previousWindow != null) {
			if (newCanvas)
				setLocationAndSize(false);
			else
				ic.update(previousWindow.getCanvas());
			// Point loc = previousWindow.getLocation();
			// setLocation(loc.x, loc.y);
			if (!(this instanceof StackWindow || this instanceof PlotWindow)) { // layout now unless components will be
				if (IJ.isMacro())
					imp.setDeactivated(); // prepare for waitTillActivated (imp may have been activated before if it
											// gets a new Window now)
				if (embeddedParent == null) {
					shell.setText(imp.getTitle());
					shell.setSize(600, 600);
					shell.layout();
					shell.open();
				}
				if (ic.isDisposed() == false) {
					ic.setLayoutData(gd_composite);
					if (this.getCanvas().isFitToParent() == false) {
						ic.fitToWindow();
						ic.parent.layout();
					}
				}
			}
			if (ic.getMagnification() != 0.0)
				imp.setTitle(imp.getTitle());
			boolean unlocked = imp.lockSilently();
			boolean changes = imp.changes;
			imp.changes = false;
			/* For SWT we have to set the parent! Changed, see above! */
			// ic.setParent(parentComposite);
			previousWindow.close(false);
			imp.changes = changes;
			if (unlocked)
				imp.unlock();
			if (this.imp != null)
				this.imp.setOpenAsHyperStack(openAsHyperStack);
			WindowManager.setCurrentWindow(this);
		} else {
			setLocationAndSize(false);
			if (ij != null && !IJ.isMacintosh()) {
				if (embeddedParent == null) {
					org.eclipse.swt.graphics.Image img = ij.getShell().getImage();
					if (img != null)
						try {
							shell.setImage(img);
						} catch (Exception e) {
						}
				}
			}
			if (nextLocation != null)
				setLocation(nextLocation);
			else if (centerOnScreen)
				GUI.center(this.getShell());
			nextLocation = null;
			centerOnScreen = false;
			if (Interpreter.isBatchMode() || (IJ.getInstance() == null && this instanceof HistogramWindow)) {
				WindowManager.setTempCurrentImage(imp);
				Interpreter.addBatchModeImage(imp);
			} else {
				if (IJ.isMacro())
					imp.setDeactivated(); // prepare for waitTillActivated (imp may have been activated previously and
											// gets a new Window now)
				// show();
				if (embeddedParent == null) {
					// if (!(this instanceof StackWindow)) {
					shell.setText(imp.getTitle());
					shell.setSize(600, 600);
					shell.open();
					if (ic.isDisposed() == false) {
						ic.setLayoutData(gd_composite);
						/* Stretch to parent composite? */
						if (this.getCanvas().isFitToParent() == false) {
							ic.fitToWindow();
						}
						shell.layout();
					}
				} else {
					if (ic.isDisposed() == false) {
						ic.setLayoutData(gd_composite);
						embeddedParent.layout();
						/* Stretch to parent composite? */
						if (this.getCanvas().isFitToParent() == false) {
							ic.fitToWindow();
						}
					}
				}
			}
		}
	}

	public void createShell() {

		/* SWT Shell init! */
		// display = new Display();
		/* A subshell from a default display in the ImageJ class! */
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				shell = new Shell(Display.getDefault());
				shell.addListener(SWT.Close, new Listener() {

					public void handleEvent(Event event) {

						event.doit = false;
						boolean closeIt = ImageWindow.this.close();
						if (closeIt == false) {
							return;
						}
						/* Save the ImageJ preferences when the view is closed! */
						try {
							Prefs.savePreferences();
						} catch (RuntimeException ex) {
							ex.printStackTrace();
						}
						event.doit = true;
						// dispose not necessary here - see window close above!
						// shell.dispose();
						// IJ.getInstance().quit();
					}
				});
				shell.addListener(SWT.Activate, new Listener() {

					public void handleEvent(Event event) {

						windowActivated(null);
					}
				});
				shell.addListener(SWT.FocusIn, new Listener() {

					public void handleEvent(Event event) {

						windowActivated(null);
					}
				});
				GridLayout layout = new GridLayout(1, true);
				/* No margin for the shell! */
				layout.marginHeight = 0;
				layout.marginWidth = 0;
				shell.setLayout(layout);
				layout.verticalSpacing = 0;
				shell.setText("Image Viewer");
			}
		});
	}

	/**
	 * @param imp
	 * @param embeddedParent
	 */
	public void createMainComposite(ImagePlus imp, Composite embeddedParent) {

		final FocusListener listener = new FocusListener() {

			@Override
			public void focusGained(org.eclipse.swt.events.FocusEvent e) {

				if (IJ.debugMode)
					IJ.log("windowActivated: " + imp.getTitle());
				if (IJ.isMacOSX())
					setImageJMenuBar(ImageWindow.this);
				if (imp == null)
					return;
				ImageJ ij = IJ.getInstance();
				if (ij != null && !closed && !ij.quitting() && !Interpreter.isBatchMode())
					WindowManager.setCurrentWindow(ImageWindow.this);
				Roi roi = imp.getRoi();
				if (roi != null && (roi instanceof PointRoi))
					PointToolOptions.update();
				if (imp.isComposite())
					Channels.updateChannels();
				imp.setActivated(); // notify ImagePlus that image has been activated
			}

			@Override
			public void focusLost(org.eclipse.swt.events.FocusEvent e) {
				// TODO Auto-generated method stub

			}
		};
		/*
		 * workaround for bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=84532
		 */
		Listener deactivateListener = new Listener() {

			@Override
			public void handleEvent(Event event) {

				listener.focusLost(new org.eclipse.swt.events.FocusEvent(event));
			}
		};
		Listener activateListener = new Listener() {

			@Override
			public void handleEvent(Event event) {

				listener.focusGained(new org.eclipse.swt.events.FocusEvent(event));
			}
		};
		/* If we have no editor composite parent or view parent we create a shell! */
		if (embeddedParent == null) {
			infoLabel = new Label(shell, SWT.NONE);
			// infoLabel.setBackground(white);
			infoLabel.addPaintListener(paintEvent -> {
				GC gc = paintEvent.gc;
				drawInfo(gc);
			});
			GridData gd_columnheader = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
			infoLabel.setLayoutData(gd_columnheader);
			parentComposite = new Composite(shell, SWT.NONE);
			parentComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		} else {
			/* If we have an editor composite (set in the editor) we fill the parent! */
			parentComposite = new Composite(embeddedParent, SWT.NONE);
			parentComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		}
		parentComposite.addListener(SWT.Deactivate, deactivateListener);
		parentComposite.addListener(SWT.Activate, activateListener);
		// shell.layout();
		// fillComp = new Composite(sComp, SWT.NONE);
		// vBar1 = sComp.getVerticalBar();
		// hBar1 = sComp.getHorizontalBar();
		/* We use the grid layout for the parent composite! */
		GridLayout layout = new GridLayout(1, true);
		parentComposite.setLayout(layout);
		gd_composite = new GridData(SWT.CENTER, SWT.CENTER, true, true, 1, 1);
		gd_composite.widthHint = imp.getWidth();
		gd_composite.heightHint = imp.getHeight();
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		parentComposite.addControlListener(new ControlAdapter() {

			@Override
			public void controlResized(ControlEvent e) {

				resizeControl();
			}
		});
	}

	public void resizeControl() {

		int width = parentComposite.getBounds().width;
		int height = parentComposite.getBounds().height;
		// ImageWindow.this.ic.resizeCanvas(width, height);
		// ImageWindow.this.ic.setSize(width, height);
		// parentComposite.redraw();
		/* Stretch to parent composite? */
		if (this.getCanvas().isFitToParent()) {
			gd_composite.widthHint = parentComposite.getSize().x;
			gd_composite.heightHint = parentComposite.getSize().y;
		}
		/* Adjust the layout boundaries to center the image in the parent composite! */
		else {
			ImageWindow.this.ic.resizeCanvas(width, height);
			gd_composite.widthHint = (int) (imp.getWidth() * ic.magnification);
			gd_composite.heightHint = (int) (imp.getHeight() * ic.magnification);
		}
		// Call layout for size calculation!
		parentComposite.layout();
		int ids[] = WindowManager.getIDList();
		if (ids != null) {
			for (int i = 0; i < ids.length; i++) {
				ImagePlus ip = WindowManager.getImage(ids[i]);
				// JPanel panel=(JPanel)win.getCanvas().getParent();
				if (ip.getWindow() instanceof PlotWindow) {
					if (ip.getID() == imp.getID())
						plotWindowResize(ip.getWindow());
				}
			}
		}
	}

	private void plotWindowResize(ImageWindow win) {

		if (win instanceof PlotWindow) {
			PlotWindow plo = (PlotWindow) win;
			if (plo != null) {
				Rectangle rec = parentComposite.getClientArea();
				Plot plot = plo.getPlot();
				if (plot != null) {
					int correctionX = plot.leftMargin + plot.rightMargin;
					int correctionY = plot.topMargin + plot.bottomMargin;
					plot.getImagePlus().getCanvas().setSize(rec.width + correctionX, rec.height + correctionY);
					plot.setFrameSize(rec.width, rec.height);
					int buttonHeight = plo.bottomPanel.getLocation().y;
					int locsize = rec.height - buttonHeight;
					plot.setSize(rec.width, rec.height - locsize - 10);
					ic.layout();
				}
			}
		}
	}
	// private void openShell() {
	// shell.open();
	/*
	 * while (!shell.isDisposed()) { if (!display.readAndDispatch()) {
	 * display.sleep(); } } display.dispose();
	 */
	// }

	private void setLocationAndSize(boolean updating) {

		/* If we have no shell return! */
		if (embeddedParent != null) {
			return;
		}
		if (imp == null)
			return;
		int width = imp.getWidth();
		int height = imp.getHeight();
		java.awt.Rectangle bounds = null;
		Point loc = null;
		// load preferences file location
		java.awt.Point locTemp = Prefs.getLocation(LOC_KEY);
		if (locTemp != null) {
			loc = new Point(locTemp.x, locTemp.y);
			bounds = GUI.getMaxWindowBounds(loc);
			if (bounds != null && (loc.x > bounds.x + bounds.width / 3 || loc.y > bounds.y + bounds.height / 3)
					&& (loc.x + width > bounds.x + bounds.width || loc.y + height > bounds.y + bounds.height)) {
				loc = null;
				bounds = null;
			}
		}
		// if loc not valid, use screen bounds of visible window (this) or of main
		// window (ij) if not visible yet (updating == false)
		java.awt.Rectangle maxWindowTemp = bounds != null ? bounds
				: GUI.getMaxWindowBounds(updating ? this.getShell() : ij.getShell());
		Rectangle maxWindow = new Rectangle(maxWindowTemp.x, maxWindowTemp.y, maxWindowTemp.width,
				maxWindowTemp.height);
		if (WindowManager.getWindowCount() <= 1)
			xbase = -1;
		if (width > maxWindow.width / 2 && xbase > maxWindow.x + 5 + XINC * 6)
			xbase = -1;
		if (xbase == -1) {
			count = 0;
			if (loc != null) {
				xbase = loc.x;
				ybase = loc.y;
			} else if (ij != null) {
				Rectangle ijBounds = ij.getBounds();
				if (ijBounds.y - maxWindow.x < maxWindow.height / 8) {
					xbase = ijBounds.x;
					if (xbase + width > maxWindow.x + maxWindow.width) {
						xbase = maxWindow.x + maxWindow.width - width - 10;
						if (xbase < maxWindow.x)
							xbase = maxWindow.x + 5;
						;
					}
					ybase = ijBounds.y + ijBounds.height + 5;
				} else {
					xbase = maxWindow.x + (maxWindow.width - width) / 2;
					ybase = maxWindow.y + (maxWindow.height - height) / 4;
				}
			} else {
				xbase = maxWindow.x + (maxWindow.width - width) / 2;
				ybase = maxWindow.y + (maxWindow.height - height) / 4;
			}
			xbase = Math.max(xbase, maxWindow.x);
			ybase = Math.max(ybase, maxWindow.y);
			// if (IJ.debugMode) IJ.log("ImageWindow.xbase: "+xbase);
			xloc = xbase;
			yloc = ybase;
		}
		int x = xloc;
		int y = yloc;
		xloc += XINC;
		yloc += YINC;
		count++;
		if (count % 6 == 0) {
			xloc = xbase;
			yloc = ybase;
		}
		screenHeight = maxWindow.y + maxWindow.height - sliderHeight;
		screenWidth = maxWindow.x + maxWindow.width;
		double mag = 1;
		if (!(this instanceof PlotWindow)) { // unless a plot (always at 100%), zoom out to show all of image
			while (xbase + width * mag > screenWidth || ybase + height * mag >= screenHeight) {
				double mag2 = ImageCanvas.getLowerZoomLevel(mag);
				if (mag2 == mag)
					break;
				mag = mag2;
			}
		}
		if (mag < 1.0) {
			initialMagnification = mag;
			ic.setSize((int) (width * mag), (int) (height * mag));
		}
		ic.setMagnification(mag);
		if (y + height * mag > screenHeight)
			y = ybase;
		if (Prefs.open100Percent && ic.getMagnification() < 1.0) {
			while (ic.getMagnification() < 1.0)
				ic.zoomIn(0, 0);
			setSize(Math.min(width, screenWidth - x), Math.min(height, screenHeight - y));
			validate();
		} // else
			// pack (shell.pack() will cause a stackoverflow because the plotWindowResize
			// will be called which calls this function!
			// pack();
		if (!updating)
			setLocation(x, y);
	}

	Rectangle getMaxWindow(int xloc, int yloc) {

		return null;
	}

	public double getInitialMagnification() {

		return initialMagnification;
	}

	/**
	 * Override Container getInsets() to make room for some text above the image.
	 */
	public Insets getInsets() {

		/*
		 * Insets insets = super.getInsets(); if (imp==null) return insets; double mag =
		 * ic.getMagnification(); int extraWidth = (int)((MIN_WIDTH -
		 * imp.getWidth()*mag)/2.0); if (extraWidth<0) extraWidth = 0; int extraHeight =
		 * (int)((MIN_HEIGHT - imp.getHeight()*mag)/2.0); if (extraHeight<0) extraHeight
		 * = 0; insets = new Insets(insets.top+textGap+extraHeight,
		 * insets.left+extraWidth, insets.bottom+extraHeight, insets.right+extraWidth);
		 * return insets;
		 */
		return new Insets(0, 0, 0, 0);
	}

	/* Compatibility method for SWT! */
	public Insets getInsets(boolean includeSmallImageMargins) {

		return new Insets(0, 0, 0, 0);
	}

	/** Draws the subtitle. */
	public void drawInfo(GC gc) {

		if (imp == null)
			return;
		if (textGap != 0) { // Insets insets = super.getInsets();
			if (imp.isComposite()) {
				CompositeImage ci = (CompositeImage) imp;
				if (ci.getMode() == IJ.COMPOSITE) {
					org.eclipse.swt.graphics.Color c = ci.getChannelColor();
					// if (Color.green.equals(c))
					// c = new Color(0, 180, 0);
					gc.setForeground(c);
				}
			}
			// Java2.setAntialiasedText(g, true);
			/*
			 * if (SCALE > 1.0) { Font font = new Font("SansSerif", Font.PLAIN, (int) (12 *
			 * SCALE)); g.setFont(font); }
			 */
			gc.drawString(createSubtitle(), 0, 0);
		}
	}

	/** Creates the subtitle. */
	public String createSubtitle() {

		String s = "";
		if (imp == null)
			return s;
		int stackSize = imp.getStackSize();
		if (stackSize > 1) {
			ImageStack stack = imp.getStack();
			int currentSlice = imp.getCurrentSlice();
			s += currentSlice + "/" + stackSize;
			String label = stack.getShortSliceLabel(currentSlice);
			if (label != null && label.length() > 0) {
				if (imp.isHyperStack())
					label = label.replace(';', ' ');
				s += " (" + label + ")";
			}
			if ((this instanceof StackWindow) && running2) {
				return s;
			}
			s += "; ";
		} else {
			String label = imp.getProp("Slice_Label");
			if (label == null && imp.hasImageStack())
				label = imp.getStack().getSliceLabel(1);
			if (label != null && label.length() > 0) {
				int newline = label.indexOf('\n');
				if (newline > 0)
					label = label.substring(0, newline);
				int len = label.length();
				if (len > 4 && label.charAt(len - 4) == '.' && !Character.isDigit(label.charAt(len - 1)))
					label = label.substring(0, len - 4);
				if (label.length() > 60)
					label = label.substring(0, 60) + "...";
				s = "\"" + label + "\"; ";
			}
		}
		int type = imp.getType();
		Calibration cal = imp.getCalibration();
		if (cal.scaled()) {
			boolean unitsMatch = cal.getXUnit().equals(cal.getYUnit());
			double cwidth = imp.getWidth() * cal.pixelWidth;
			double cheight = imp.getHeight() * cal.pixelHeight;
			int digits = Tools.getDecimalPlaces(cwidth, cheight);
			if (digits > 2)
				digits = 2;
			if (unitsMatch) {
				s += IJ.d2s(cwidth, digits) + "x" + IJ.d2s(cheight, digits) + " " + cal.getUnits() + " ("
						+ imp.getWidth() + "x" + imp.getHeight() + "); ";
			} else {
				s += d2s(cwidth) + " " + cal.getXUnit() + " x " + d2s(cheight) + " " + cal.getYUnit() + " ("
						+ imp.getWidth() + "x" + imp.getHeight() + "); ";
			}
		} else
			s += imp.getWidth() + "x" + imp.getHeight() + " pixels; ";
		switch (type) {
		case ImagePlus.GRAY8:
		case ImagePlus.COLOR_256:
			s += "8-bit";
			break;
		case ImagePlus.GRAY16:
			s += "16-bit";
			break;
		case ImagePlus.GRAY32:
			s += "32-bit";
			break;
		case ImagePlus.COLOR_RGB:
			s += imp.isRGB() ? "RGB" : "32-bit (int)";
			break;
		}
		if (imp.isInvertedLut())
			s += " (inverting LUT)";
		return s + "; " + getImageSize(imp);
	}

	public static String getImageSize(ImagePlus imp) {

		if (imp == null)
			return null;
		double size = imp.getSizeInBytes() / 1024.0;
		String s2 = null, s3 = null;
		if (size < 1024.0) {
			s2 = IJ.d2s(size, 0);
			s3 = "K";
		} else if (size < 10000.0) {
			s2 = IJ.d2s(size / 1024.0, 1);
			s3 = "MB";
		} else if (size < 1048576.0) {
			s2 = IJ.d2s(Math.round(size / 1024.0), 0);
			s3 = "MB";
		} else {
			s2 = IJ.d2s(size / 1048576.0, 1);
			s3 = "GB";
		}
		if (s2.endsWith(".0"))
			s2 = s2.substring(0, s2.length() - 2);
		return s2 + s3;
	}

	private String d2s(double n) {

		int digits = Tools.getDecimalPlaces(n);
		if (digits > 2)
			digits = 2;
		return IJ.d2s(n, digits);
	}

	/*
	 * public void paint(Graphics g) { drawInfo(g); Rectangle r = ic.getBounds();
	 * int extraWidth = MIN_WIDTH - r.width; int extraHeight = MIN_HEIGHT -
	 * r.height; if (extraWidth <= 0 && extraHeight <= 0 && !Prefs.noBorder &&
	 * !IJ.isLinux()) g.drawRect(r.x - 1, r.y - 1, r.width + 1, r.height + 1); }
	 */
	public boolean close() {

		return close(true);
	}

	/**
	 * Removes this window from the window list and disposes of it. Returns false if
	 * the user cancels the "save changes" dialog.
	 */
	public boolean close(boolean disposeShell) {

		boolean isRunning = running || running2;
		running = running2 = false;
		if (imp == null)
			return true;
		boolean virtual = imp.getStackSize() > 1 && imp.getStack().isVirtual();
		if (isRunning)
			IJ.wait(500);
		if (imp == null)
			return true;
		boolean changes = imp.changes;
		Roi roi = imp.getRoi();
		if (roi != null && (roi instanceof PointRoi) && ((PointRoi) roi).promptBeforeDeleting())
			changes = true;
		if (ij == null || ij.quittingViaMacro() || IJ.getApplet() != null || Interpreter.isBatchMode()
				|| IJ.macroRunning() || virtual)
			changes = false;
		if (changes) {
			String msg;
			String name = imp.getTitle();
			if (name.length() > 22)
				msg = "Save changes to\n" + "\"" + name + "\"?";
			else
				msg = "Save changes to \"" + name + "\"?";
			if (imp.isLocked())
				msg += "\nWARNING: This image is locked.\nProbably, processing is unfinished (slow or still previewing).";
			// toFront();
			if (embeddedParent == null) {
				if (shell != null && !shell.isDisposed()) {
					shell.forceActive();
				}
				YesNoCancelDialog d = new YesNoCancelDialog("ImageJ", msg);
				if (d.cancelPressed())
					return false;
				else if (d.yesPressed()) {
					ij.io.FileSaver fs = new ij.io.FileSaver(imp);
					if (!fs.save())
						return false;
				}
			}
		}
		closed = true;
		if (WindowManager.getWindowCount() == 0) {
			xloc = 0;
			yloc = 0;
		}
		WindowManager.removeWindow(this);
		if (ij != null && ij.quitting()) // this may help avoid thread deadlocks
			return true;
		Display.getDefault().syncExec(() -> {

			Rectangle bounds = getBounds();
			// dispose();
			if (embeddedParent == null) {
				if (!IJ.isMacro() && bounds.y < screenHeight / 3 && (bounds.y + bounds.height) <= screenHeight
						&& (bounds.x + bounds.width) <= screenWidth) {
					Prefs.saveLocation(LOC_KEY, new Point(bounds.x, bounds.y));
					xbase = -1;
				}
				if (disposeShell) {
					shell.dispose();
				} else {
					// Will be opened again when an image is replaced (e.g., RGB to RGB stack)
					shell.setVisible(false);
				}
			}
			if (imp != null)
				imp.flush();
			imp = null;

		});
		return true;
	}

	public ImagePlus getImagePlus() {

		return imp;
	}

	public void setImage(ImagePlus imp2) {

		ImageCanvas ic = getCanvas();
		if (ic == null || imp2 == null)
			return;
		imp = imp2;
		imp.setWindow(this);
		ic.updateImage(imp);
		ic.setImageUpdated();
		ic.repaint();
		// repaint();
	}

	public void updateImage(ImagePlus imp) {

		if (imp != this.imp)
			throw new IllegalArgumentException("imp!=this.imp");
		this.imp = imp;
		ic.updateImage(imp);
		setLocationAndSize(true);
		if (this instanceof StackWindow) {
			StackWindow sw = (StackWindow) this;
			int stackSize = imp.getStackSize();
			int nScrollbars = sw.getNScrollbars();
			if (stackSize == 1 && nScrollbars > 0)
				sw.removeScrollbars();
			else if (stackSize > 1 && nScrollbars == 0)
				sw.addScrollbars(imp);
		}
		Display display = Display.getDefault();
		display.syncExec(() -> {

			gd_composite.widthHint = imp.getWidth();
			gd_composite.heightHint = imp.getHeight();
			ImageWindow.this.ic.redraw();
			ImageWindow.this.ic.layout();
			parentComposite.layout();
			// pack();
			maxBounds = getMaximumBounds();
			// setMaximizedBounds(maxBounds);
			setMaxBoundsTime = System.currentTimeMillis();

		});
		if (embeddedParent != null) {
			display.syncExec(() -> {

				embeddedParent.layout();

			});
		} else {
			display.syncExec(() -> {

				shell.layout();

			});
		}
	}

	public ImageCanvas getCanvas() {

		return ic;
	}

	static ImagePlus getClipboard() {

		return ImagePlus.getClipboard();
	}

	/* To be implemented for SWT! */
	public void setExtendedState(int state) {

	}

	public Rectangle getMaximumBounds() {

		/*
		 * Rectangle maxWindow = GUI.getMaxWindowBounds(this); if (imp==null) return
		 * maxWindow; double width = imp.getWidth(); double height = imp.getHeight();
		 * double iAspectRatio = width/height; maxWindowBounds = maxWindow; if
		 * (iAspectRatio/((double)maxWindow.width/maxWindow.height)>0.75) { maxWindow.y
		 * += 22; // uncover ImageJ menu bar maxWindow.height -= 22; } Dimension
		 * extraSize = getExtraSize(); double maxWidth =
		 * maxWindow.width-extraSize.width; double maxHeight =
		 * maxWindow.height-extraSize.height; double mAspectRatio = maxWidth/maxHeight;
		 * int wWidth, wHeight; double mag; if (iAspectRatio>=mAspectRatio) { mag =
		 * maxWidth/width; wWidth = maxWindow.width; wHeight =
		 * (int)(height*mag+extraSize.height); } else { mag = maxHeight/height; wHeight
		 * = maxWindow.height; wWidth = (int)(width*mag+extraSize.width); } int xloc =
		 * (int)(maxWidth-wWidth)/2; if (xloc<maxWindow.x) xloc = maxWindow.x; wWidth =
		 * Math.min(wWidth, maxWindow.x-xloc+maxWindow.width); wHeight =
		 * Math.min(wHeight, maxWindow.height); return new Rectangle(xloc, maxWindow.y,
		 * wWidth, wHeight);
		 */
		return parentComposite.getBounds();
	}
	/*
	 * Dimension getExtraSize() { Insets insets = getInsets(); int extraWidth =
	 * insets.left+insets.right + 10; int extraHeight = insets.top+insets.bottom +
	 * 10; if (extraHeight==20) extraHeight = 42; int members = getComponentCount();
	 * for (int i=1; i<members; i++) { Component m = getComponent(i); Dimension d =
	 * m.getPreferredSize(); extraHeight += d.height + 5; if (IJ.debugMode)
	 * IJ.log(i+"  "+d.height+" "+extraHeight); } return new Dimension(extraWidth,
	 * extraHeight); }
	 */
	/*
	 * public Component add(Component comp) { comp = super.add(comp); maxBounds =
	 * getMaximumBounds(); setMaximizedBounds(maxBounds); setMaxBoundsTime =
	 * System.currentTimeMillis(); return comp; }
	 */

	public void maximize() {

		Display.getDefault().syncExec(() -> {

			if (GenericDialog.getInstance() != null && IJ.isMacOSX() && IJ.isJava18())
				return; // workaround for OSX/Java 8 maximize bug
			Rectangle rect = getMaximumBounds();
			if (IJ.debugMode)
				IJ.log("maximize: " + rect);
			setLocationAndSize(rect.x, rect.y, rect.width, rect.height);
			resizeControl();

		});
	}

	public void minimize() {

		Display.getDefault().syncExec(() -> {

			if (IJ.debugMode)
				IJ.log("minimize: " + unzoomWhenMinimizing);
			if (unzoomWhenMinimizing)
				ic.unzoom();
			unzoomWhenMinimizing = true;
			resizeControl();

		});
	}

	/** Has this window been closed? */
	public boolean isClosed() {

		return closed;
	}

	public void focusGained(FocusEvent e) {

		if (!Interpreter.isBatchMode() && ij != null && !ij.quitting() && imp != null) {
			// if (IJ.debugMode) IJ.log("focusGained: "+imp);
			WindowManager.setCurrentWindow(this);
		}
	}

	public void windowActivated(WindowEvent e) {

		if (IJ.debugMode)
			IJ.log("windowActivated: " + imp.getTitle());
		if (IJ.isMacOSX() && Prefs.setIJMenuBar && !IJ.isMacro())
			setImageJMenuBar(this);
		if (imp == null)
			return;
		ImageJ ij = IJ.getInstance();
		if (ij != null && !closed && !ij.quitting() && !Interpreter.isBatchMode())
			WindowManager.setCurrentWindow(this);
		Roi roi = imp.getRoi();
		if (roi != null && (roi instanceof PointRoi))
			PointToolOptions.update();
		if (imp.isComposite())
			Channels.updateChannels();
		imp.setActivated(); // notify ImagePlus that image has been activated
	}

	public void windowClosing(WindowEvent e) {

		if (closed)
			return;
		if (ij != null) {
			WindowManager.setCurrentWindow(this);
			IJ.doCommand("Close");
		} else {
			// dispose();
			// WindowManager.removeWindow(this);
		}
	}

	public void windowStateChanged(WindowEvent e) {

		int oldState = e.getOldState();
		int newState = e.getNewState();
		if (IJ.debugMode)
			IJ.log("windowStateChanged: " + oldState + " " + newState);
		if ((oldState & Frame.MAXIMIZED_BOTH) == 0 && (newState & Frame.MAXIMIZED_BOTH) != 0)
			maximize();
	}

	public void windowClosed(WindowEvent e) {

	}

	public void windowDeactivated(WindowEvent e) {

	}

	public void focusLost(FocusEvent e) {

	}

	public void windowDeiconified(WindowEvent e) {

	}

	public void windowIconified(WindowEvent e) {

	}

	public void windowOpened(WindowEvent e) {

	}

	public synchronized void mouseWheelMoved(MouseWheelEvent e) {

		/*
		 * This event is now in the ImageCanvas class! int rotation =
		 * e.getWheelRotation(); int amount = e.getScrollAmount(); boolean ctrl =
		 * (e.getModifiers()&Event.CTRL_MASK)!=0; if (IJ.debugMode) {
		 * IJ.log("mouseWheelMoved: "+e); IJ.log("  type: "+e.getScrollType());
		 * IJ.log("  ctrl: "+ctrl); IJ.log("  rotation: "+rotation);
		 * IJ.log("  amount: "+amount); } if (amount<1) amount=1; if (rotation==0)
		 * return; int width = imp.getWidth(); int height = imp.getHeight(); Rectangle
		 * srcRect = ic.getSrcRect(); int xstart = srcRect.x; int ystart = srcRect.y; if
		 * ((ctrl||IJ.shiftKeyDown()) && ic!=null) { Point loc = ic.getCursorLoc(); int
		 * x = ic.screenX(loc.x); int y = ic.screenY(loc.y); if (rotation<0)
		 * ic.zoomIn(x, y); else ic.zoomOut(x, y); return; } else if (IJ.spaceBarDown()
		 * || srcRect.height==height) { srcRect.x += rotation*amount*Math.max(width/200,
		 * 1); if (srcRect.x<0) srcRect.x = 0; if (srcRect.x+srcRect.width>width)
		 * srcRect.x = width-srcRect.width; } else { srcRect.y +=
		 * rotation*amount*Math.max(height/200, 1); if (srcRect.y<0) srcRect.y = 0; if
		 * (srcRect.y+srcRect.height>height) srcRect.y = height-srcRect.height; } if
		 * (srcRect.x!=xstart || srcRect.y!=ystart) ic.repaint();
		 */
	}

	/**
	 * Copies the current ROI to the clipboard. The entire image is copied if there
	 * is no ROI.
	 */
	public void copy(boolean cut) {

		imp.copy(cut);
	}

	public void paste() {

		imp.paste();
	}

	/**
	 * This method is called by ImageCanvas.mouseMoved(MouseEvent).
	 * 
	 * @see ij.gui.ImageCanvas#mouseMoved
	 */
	public void mouseMoved(int x, int y) {

		imp.mouseMoved(x, y);
	}

	/**
	 * This method is called by ImageCanvas.mouseExited(MouseEvent). Added for SWT
	 * on image canvas tooltip.
	 * 
	 * @see ij.gui.ImageCanvas#mouseExited
	 */
	public void mouseExited() {

		imp.mouseExited();
	}

	public String toString() {

		return imp != null ? imp.getTitle() : "";
	}

	/**
	 * Causes the next image to be opened to be centered on the screen and displayed
	 * without informational text above the image.
	 */
	public static void centerNextImage() {

		centerOnScreen = true;
	}

	/**
	 * Causes the next image to be displayed at the specified location. For AWT
	 * compat.!
	 */
	public static void setNextLocation(java.awt.Point loc) {

		// nextLocation = loc;
	}

	/** Causes the next image to be displayed at the specified location. */
	public static void setNextLocation(Point loc) {

		nextLocation = loc;
	}

	/** Causes the next image to be displayed at the specified location. */
	public static void setNextLocation(int x, int y) {

		nextLocation = new Point(x, y);
	}

	/**
	 * Moves and resizes this window. Changes the magnification so the image fills
	 * the window.
	 */
	public void setLocationAndSize(int x, int y, int width, int height) {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				if (shell != null && shell.isDisposed() == false) {
					shell.setLocation(x, y);
					shell.setSize(width, height);
					getCanvas().fitToWindow();
				}
			}
		});
	}

	public void setLocation(int x, int y) {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				if (shell != null && shell.isDisposed() == false)
					shell.setLocation(x, y);
			}
		});
		/*
		 * super.setLocation(x, y); initialLoc = null;
		 */
	}

	public void setSliderHeight(int height) {

		sliderHeight = height;
	}

	public int getSliderHeight() {

		return sliderHeight;
	}

	public static void setImageJMenuBar(ImageWindow win) {

		ImageJ ij = IJ.getInstance();
		boolean setMenuBar = true;
		ImagePlus imp = win.getImagePlus();
		if (imp != null)
			setMenuBar = imp.setIJMenuBar();
		Menu mb = Menus.getMenuBar();
		/* if (mb!=null && mb==win.getMenuBar()) */
		setMenuBar = false;
		setMenuBarTime = 0L;
		if (setMenuBar && ij != null && !ij.quitting()) {
			IJ.wait(10); // may be needed for Java 1.4 on OS X
			long t0 = System.currentTimeMillis();
			// win.setMenuBar(mb);
			long time = System.currentTimeMillis() - t0;
			setMenuBarTime = time;
			Menus.setMenuBarCount++;
			if (time > 2000L)
				Prefs.setIJMenuBar = false;
		}
		if (imp != null)
			imp.setIJMenuBar(true);
	}

	/* For SWT compatibility! */
	public void repaint() {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				if (infoLabel != null && !infoLabel.isDisposed()) {
					infoLabel.redraw();
				}
			}
		});
		getCanvas().setImageUpdated();
	}

	public org.eclipse.swt.graphics.Rectangle getSwtParentBounds() {

		return parentComposite.getClientArea();
	}

	@Override
	public void mouseScrolled(MouseEvent e) {
		// TODO Auto-generated method stub

	}
}