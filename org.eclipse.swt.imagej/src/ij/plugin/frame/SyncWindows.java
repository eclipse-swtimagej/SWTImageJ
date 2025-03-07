package ij.plugin.frame;

import java.awt.AWTEventMulticaster;
import java.awt.geom.GeneralPath;
import java.util.EventListener;
import java.util.EventObject;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Widget;

import ij.CommandListener;
import ij.Executer;
import ij.IJ;
import ij.ImageJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.Toolbar;
import ij.measure.Calibration;

/**
 * This class "synchronizes" mouse input in multiple windows. Once several
 * windows are synchronized, mouse events in any one of the synchronized windows
 * are propagated to the others.
 * 
 * Note, the notion of synchronization use by the SyncWindows class here (i.e.
 * multiple windows that all get the same mouse input) is somewhat different
 * than the use of the synchronize keyword in the Java language. (In Java,
 * synchronize has to do w/ critical section access by multiple threads.)
 * <p>
 * Optionally passes on change of z-slice of a stack to other stacks;
 * 
 * Optionally translates positions to different windows via offscreen
 * coordinates, i.e. correctly translates coordinates to windows with a
 * different zoom;
 * 
 * Updates the list of windows by click of a button;
 * 
 * @author Patrick Kelly <phkelly@ucsd.edu>; Improved GUI, support of image
 *         coordinates and z-slices by Joachim Walter
 *         <correspondence@walter-witzenhausen.de>
 * 
 */
public class SyncWindows extends PlugInFrame implements SelectionListener, MouseTrackListener, MouseListener,
		MouseMoveListener, DisplayChangeListener, ImageListener, CommandListener {

	/** Indices of synchronized image windows are maintained in this Vector. */
	protected Vector vwins = null;
	/*
	 * Manage mouse information. The mouse coordinates x and y are only changed by
	 * the methods of the MousMotionListener Interface. They are used by the
	 * MouseListener methods. This way, the coordinates that were valid before a
	 * MouseListener event (e.g. a Zoom) happened can be accessed.
	 */
	protected int oldX, oldY;
	protected int x = 0;
	protected int y = 0;
	/**
	 * List of currently displayed windows retrieved from ImageJ window manager.
	 */
	protected org.eclipse.swt.widgets.List wList;
	/** Panel for GUI */
	protected org.eclipse.swt.widgets.Composite panel;
	/** Checkboxes for user control. */
	protected Button cCursor, cSlice, cChannel, cFrame, cCoords, cScaling;
	/** Buttons for user control. */
	protected Button bSyncAll, bUnsyncAll;
	/** Hashtable to map list ids to image window ids. */
	protected Vector vListMap;
	/**
	 * reference to current instance of ImageJ (to avoid repeated IJ.getInstance()
	 * s)
	 */
	protected final ImageJ ijInstance;
	/*
	 * Variables to store display values of current window. Translation by
	 * screenX/Y() and offScreenX/Y() does not work, because current window receives
	 * events (e.g. zooming) before this plugin.
	 */
	private double currentMag = 1;
	private Rectangle currentSrcRect = new Rectangle(0, 0, 400, 400);
	private Composite p;
	private boolean drag;
	// Control size of cursor box and clipping region. These could be
	// changed to tune performance.
	static final int RSZ = 16;
	static final int SZ = RSZ / 2;
	static final int SCALE = 3;
	private static SyncWindows instance;
	private static Point location;

	// --------------------------------------------------
	/**
	 * Create window sync frame. Frame is shown via call to show() or by invoking
	 * run method.
	 */
	public SyncWindows() {

		this("Synchronize Windows");
	}

	public SyncWindows(String s) {

		super(s);
		ijInstance = IJ.getInstance();
		if (instance != null) {
			WindowManager.toFront(instance);
			return;
		}
		instance = this;
		Display.getDefault().syncExec(() -> {

				shell.setLayout(new GridLayout(1, true));
				panel = controlPanel();
				panel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				// add(panel);
				// GUI.scale(this);
				shell.pack();
				// setResizable(false);
				IJ.register(this.getClass());
				if (location == null)
					location = shell.getLocation();
				else
					shell.setLocation(location);
				updateWindowList();
				WindowManager.addWindow(SyncWindows.this);
				ImagePlus.addImageListener(SyncWindows.this);
				Executer.addCommandListener(SyncWindows.this);
				shell.setVisible(true);
			
		});
	}

	public static void setC(ImageWindow source, int channel) {

		SyncWindows syncWindows = instance;
		if (syncWindows == null || !syncWindows.synced(source))
			return;
		DisplayChangeEvent event = new DisplayChangeEvent(source, DisplayChangeEvent.CHANNEL, channel);
		syncWindows.displayChanged(event);
	}

	public static void setZ(ImageWindow source, int slice) {

		SyncWindows syncWindows = instance;
		if (syncWindows == null || !syncWindows.synced(source))
			return;
		DisplayChangeEvent event = new DisplayChangeEvent(source, DisplayChangeEvent.Z, slice);
		syncWindows.displayChanged(event);
	}

	public static void setT(ImageWindow source, int frame) {

		SyncWindows syncWindows = instance;
		if (syncWindows == null || !syncWindows.synced(source))
			return;
		DisplayChangeEvent event = new DisplayChangeEvent(source, DisplayChangeEvent.T, frame);
		syncWindows.displayChanged(event);
	}

	private boolean synced(ImageWindow source) {

		if (source == null || vwins == null)
			return false;
		ImagePlus imp = source.getImagePlus();
		if (imp == null)
			return false;
		return vwins.contains(Integer.valueOf(imp.getID()));
	}

	// --------------------------------------------------
	/**
	 * Method to pass on changes of the z-slice of a stack.
	 */
	public void displayChanged(DisplayChangeEvent e) {

		// if (e!=null) throw new IllegalArgumentException();
		// IJ.log("displayChanged: "+e);
		if (vwins == null)
			return;
		Object source = e.getSource();
		int type = e.getType();
		int value = e.getValue();
		ImagePlus imp;
		ImageWindow iw;
		// Current imagewindow
		ImageWindow iwc = WindowManager.getCurrentImage().getWindow();
		// pass on only if event comes from current window
		if (!iwc.equals(source))
			return;
		// Change channel in other synchronized windows.
		if (cChannel.getSelection() && type == DisplayChangeEvent.CHANNEL) {
			for (int n = 0; n < vwins.size(); ++n) {
				imp = getImageFromVector(n);
				if (imp != null) {
					iw = imp.getWindow();
					if (!iw.equals(source))
						imp.setC(value);
				}
			}
		}
		// Change slice in other synchronized windows.
		if (cSlice.getSelection() && type == DisplayChangeEvent.Z) {
			for (int n = 0; n < vwins.size(); ++n) {
				imp = getImageFromVector(n);
				if (imp != null) {
					iw = imp.getWindow();
					if (!iw.equals(source)) {
						if (imp.getNSlices() == 1 && imp.getNFrames() > 1)
							imp.setT(value);
						else
							imp.setZ(value);
					}
				}
			}
		}
		// Change frame in other synchronized windows.
		if (cFrame.getSelection() && type == DisplayChangeEvent.T) {
			for (int n = 0; n < vwins.size(); ++n) {
				imp = getImageFromVector(n);
				if (imp != null) {
					iw = imp.getWindow();
					if (!iw.equals(source))
						imp.setT(value);
				}
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		ImageCanvas icc = iwc.getCanvas();
		storeCanvasState(icc);
	}
	// --------------------------------------------------
	//
	// MouseMotionListener interface methods.
	//

	// --------------------------------------------------
	/**
	 * Draws the "synchronize" cursor in each of the synchronized windows.
	 */
	public void mouseMoved(MouseEvent e) {

		if (!cCursor.getSelection())
			return;
		if (vwins == null)
			return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas ic;
		Point p;
		Point oldp;
		oldX = x;
		oldY = y;
		x = e.x;
		y = e.y;
		p = new Point(x, y);
		// get ImageCanvas that received event
		ImageCanvas icc = (ImageCanvas) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getImageWindow();
		// Draw new cursor box in each synchronized window.
		// and pass on mouse moved event
		for (int n = 0; n < vwins.size(); ++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {
				iw = imp.getWindow();
				ic = iw.getCanvas();
				if (cCoords.getSelection() && iw != iwc) {
					p = getMatchingCoords(ic, icc, x, y);
					oldp = getMatchingCoords(ic, icc, oldX, oldY);
				} else {
					p.x = x;
					p.y = y;
				}
				// For PolygonRoi the cursor would overwrite the indicator lines.
				Roi roi = imp.getRoi();
				if (!(roi != null && roi instanceof PolygonRoi && roi.getState() == Roi.CONSTRUCTING))
					drawSyncCursor(imp, p.x, p.y);
				if (iw != iwc)
					ic.mouseMove(adaptEvent(e, ic, p));
			}
		}
		// Display correct values in ImageJ statusbar
		iwc.getImagePlus().mouseMoved(icc.offScreenX(x), icc.offScreenY(y));
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}

	// --------------------------------------------------
	/** Propagate mouse dragged events to all synchronized windows. */
	public void mouseDragged(MouseEvent e) {

		if (!cCursor.getSelection())
			return;
		if (vwins == null)
			return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas ic;
		Point p;
		Point oldp;
		oldX = x;
		oldY = y;
		x = e.x;
		y = e.y;
		p = new Point(x, y);
		// get ImageCanvas that received event
		ImageCanvas icc = (ImageCanvas) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getImageWindow();
		// Draw new cursor box in each synchronized window.
		// and pass on mouse dragged event
		for (int n = 0; n < vwins.size(); ++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {
				iw = imp.getWindow();
				ic = iw.getCanvas();
				if (cCoords.getSelection() && iw != iwc) {
					p = getMatchingCoords(ic, icc, x, y);
					oldp = getMatchingCoords(ic, icc, oldX, oldY);
				} else {
					p = new Point(x, y);
				}
				// For PolygonRoi the cursor would overwrite the indicator lines.
				Roi roi = imp.getRoi();
				if (!(roi != null && roi instanceof PolygonRoi && roi.getState() == Roi.CONSTRUCTING))
					drawSyncCursor(imp, p.x, p.y);
				if (iw != iwc)
					ic.mouseDragged(adaptEvent(e, ic, p));
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}
	// --------------------------------------------------
	//
	// MouseListener interface
	//

	// --------------------------------------------------
	@Override
	public void mouseDoubleClick(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseUp(MouseEvent e) {

		drag = false;
		mouseReleased(e);
		if (!cCursor.getSelection())
			return;
		if (vwins == null)
			return;
		// prevent popups popping up in all windows on right mouseclick
		if (Toolbar.getToolId() != Toolbar.MAGNIFIER && (e.button == 3 || (e.stateMask & SWT.CTRL) != 0)
				|| (e.stateMask & SWT.SHIFT) != 0 || (e.stateMask & SWT.ALT) != 0)
			return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas ic;
		Point p;
		p = new Point(x, y);
		// get ImageCanvas that received event
		ImageCanvas icc = (ImageCanvas) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getImageWindow();
		for (int n = 0; n < vwins.size(); ++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {
				iw = imp.getWindow();
				if (iw != iwc) {
					ic = iw.getCanvas();
					if (cCoords.getSelection()) {
						p = getMatchingCoords(ic, icc, x, y);
					}
					ic.mouseUp(adaptEvent(e, ic, p));
				}
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}

	@Override
	public void mouseMove(MouseEvent e) {

		if (drag) {
			mouseDragged(e);
		}
		mouseMoved(e);
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent arg0) {
		// TODO Auto-generated method stub

	}

	/** Propagate mouse clicked events to all synchronized windows. */
	/** Propagate mouse clicked events to all synchronized windows. */
	public void mouseDown(MouseEvent e) {

		mousePressed(e);
		drag = true;
		if (!cCursor.getSelection())
			return;
		if (vwins == null)
			return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas ic;
		Point p;
		p = new Point(x, y);
		// get ImageCanvas that received event
		ImageCanvas icc = (ImageCanvas) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getImageWindow();
		for (int n = 0; n < vwins.size(); ++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {
				iw = imp.getWindow();
				if (iw != iwc) {
					ic = iw.getCanvas();
					if (cCoords.getSelection()) {
						p = getMatchingCoords(ic, icc, x, y);
					}
					ic.mouseDown(adaptEvent(e, ic, p));
				}
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}

	// --------------------------------------------------
	/** Propagate mouse entered events to all synchronized windows. */
	public void mouseEnter(MouseEvent e) {

		if (!cCursor.getSelection())
			return;
		if (vwins == null)
			return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas ic;
		Point p;
		p = new Point(x, y);
		// get ImageCanvas that received event
		ImageCanvas icc = (ImageCanvas) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getImageWindow();
		for (int n = 0; n < vwins.size(); ++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {
				iw = imp.getWindow();
				if (iw != iwc) {
					ic = iw.getCanvas();
					if (cCoords.getSelection()) {
						p = getMatchingCoords(ic, icc, x, y);
					}
					ic.mouseEnter(adaptEvent(e, ic, p));
				}
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}

	@Override
	public void mouseHover(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	// --------------------------------------------------
	/** Propagate mouse exited events to all synchronized windows. */
	public void mouseExit(MouseEvent e) {

		if (!cCursor.getSelection())
			return;
		if (vwins == null)
			return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas ic;
		Point p;
		p = new Point(x, y);
		// get ImageCanvas that received event
		ImageCanvas icc = (ImageCanvas) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getImageWindow();
		for (int n = 0; n < vwins.size(); ++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {
				iw = imp.getWindow();
				ic = iw.getCanvas();
				if (cCoords.getSelection() && iw != iwc)
					p = getMatchingCoords(ic, icc, x, y);
				else {
					p.x = x;
					p.y = y;
				}
				setCursor(imp, null);
				if (iw != iwc)
					ic.mouseExit(adaptEvent(e, ic, p));
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}

	// --------------------------------------------------
	/** Propagate mouse pressed events to all synchronized windows. */
	public void mousePressed(MouseEvent e) {

		if (!cCursor.getSelection())
			return;
		if (vwins == null)
			return;
		// prevent popups popping up in all windows on right mouseclick
		if (Toolbar.getToolId() != Toolbar.MAGNIFIER && (e.button == 3 || (e.stateMask & SWT.CTRL) != 0)
				|| (e.stateMask & SWT.SHIFT) != 0 || (e.stateMask & SWT.ALT) != 0)
			return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas ic;
		Point p;
		p = new Point(x, y);
		// Current window already received mouse event.
		// get ImageCanvas that received event
		ImageCanvas icc = (ImageCanvas) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getImageWindow();
		for (int n = 0; n < vwins.size(); ++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {
				iw = imp.getWindow();
				ic = iw.getCanvas();
				// Repaint to get rid of sync indicator.
				// ic.paint(ic.getGraphics());
				ic.redraw();
				if (iw != iwc) {
					ic = iw.getCanvas();
					if (cCoords.getSelection()) {
						p = getMatchingCoords(ic, icc, x, y);
					}
					ic.mouseClicked(adaptEvent(e, ic, p));
				}
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}

	// --------------------------------------------------
	/**
	 * Propagate mouse released events to all synchronized windows.
	 */
	public void mouseReleased(MouseEvent e) {

		if (!cCursor.getSelection())
			return;
		if (vwins == null)
			return;
		// prevent popups popping up in all windows on right mouseclick
		if (Toolbar.getToolId() != Toolbar.MAGNIFIER && (e.button == 3 || (e.stateMask & SWT.CTRL) != 0)
				|| (e.stateMask & SWT.SHIFT) != 0 || (e.stateMask & SWT.ALT) != 0)
			return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas ic;
		int xloc = e.x;
		int yloc = e.y;
		Point p = new Point(xloc, yloc);
		// get ImageCanvas that received event
		ImageCanvas icc = (ImageCanvas) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getImageWindow();
		for (int n = 0; n < vwins.size(); ++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {
				iw = imp.getWindow();
				ic = iw.getCanvas();
				if (cCoords.getSelection())
					p = getMatchingCoords(ic, icc, xloc, yloc);
				// Redraw to make sure sync cursor is drawn.
				// For PolygonRoi the cursor would overwrite the indicator lines.
				Roi roi = imp.getRoi();
				if (!(roi != null && roi instanceof PolygonRoi && roi.getState() == Roi.CONSTRUCTING))
					drawSyncCursor(imp, p.x, p.y);
				if (iw != iwc)
					// ic.mouseUp(adaptEvent(e, ic, p));
					ic.mouseUp(adaptEvent(e, ic, p));
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}

	// --------------------------------------------------
	/** Implementation of ActionListener interface. */
	public void actionPerformed(SelectionEvent e) {

		// Determine which button was pressed.
		Object source = e.getSource();
		if (source instanceof Button) {
			Button bpressed = (Button) source;
			if (bpressed == bSyncAll) {
				if (wList == null)
					return;
				// Select all items on list.
				Vector v = new Vector();
				Integer I;
				for (int i = 0; i < wList.getItemCount(); ++i) {
					wList.select(i);
					I = (Integer) vListMap.elementAt(i);
					v.addElement(I);
				}
				addWindows(v);
			} else if (bpressed == bUnsyncAll) {
				removeAllWindows();
			}
		} else if (wList != null && source == wList) {
			// Doubleclick on entry in wList
			addSelections();
		}
	}

	@Override
	public void widgetSelected(SelectionEvent e) {

		if (e.widget instanceof org.eclipse.swt.widgets.Button) {
			org.eclipse.swt.widgets.Button it = (org.eclipse.swt.widgets.Button) e.widget;
			if ((it.getStyle() & SWT.CHECK) != 0) {
				itemStateChanged(e);
			} else {
				actionPerformed(e);
			}
		} else if (e.widget instanceof org.eclipse.swt.widgets.List) {
			itemStateChanged(e);
			actionPerformed(e);
		}
	}

	// --------------------------------------------------
	/**
	 * Item Listener method
	 */
	public void itemStateChanged(SelectionEvent e) {
		// safest way to get matching of selected windows in list and in plugin:
		// deselect all windows in plugin and then select all in list
		// A List often does not do what you expect.

		if (wList != null && e.getSource() == wList) {
			if (vwins != null) {
				// unsynchronize all windows and remove from window list
				Integer I;
				for (int n = 0; n < vwins.size(); ++n) {
					I = (Integer) vwins.elementAt(n);
					removeWindow(I);
				}
				vwins.removeAllElements();
			}
			addSelections();
		}
		if (e.widget instanceof org.eclipse.swt.widgets.Button) {
			org.eclipse.swt.widgets.Button but = (org.eclipse.swt.widgets.Button) e.widget;
			if (cCoords != null && e.getSource() == cCoords) {
				if (cScaling != null && but.getSelection() == false)
					cScaling.setSelection(false);
			}
			if (cScaling != null && e.getSource() == cScaling) {
				if (cCoords != null && but.getSelection() == true)
					cCoords.setSelection(true);
			}
		} else {
			org.eclipse.swt.widgets.List list = (org.eclipse.swt.widgets.List) e.widget;
		}
	}
	// --------------------------------------------------
	/**
	 * Override parent windowClosing method to clean up synchronized resources on
	 * exit.
	 */

	// public void windowClosing(WindowEvent e) {
	/** Overrides shellClosed() in PlugInDialog. */
	public void shellClosed(ShellEvent e) {

		e.doit = false;
		// if (e.getSource() == this) {
		removeAllWindows();
		ImagePlus.removeImageListener(this);
		Executer.removeCommandListener(this);
		// close();
		instance = null;
		location = shell.getLocation();
		// }
		super.shellClosed(e);
	}

	/**
	 * Implementation of ImageListener interface: update window list, if image is
	 * opened or closed
	 */
	public void imageOpened(ImagePlus imp) {

		updateWindowList();
	}

	/**
	 * Implementation of ImageListener interface: update window list, if image is
	 * opened or closed
	 */
	public void imageClosed(ImagePlus imp) {

		Display.getDefault().syncExec(() -> {

				updateWindowList();
			
		});
	}

	public void imageUpdated(ImagePlus imp) {

	}

	// --------------------------------------------------
	/**
	 * Build window list display and button controls. Create Hashtable that connects
	 * list entries to window IDs.
	 */
	protected Composite controlPanel() {

		Display.getDefault().syncExec(() -> {

				p = new org.eclipse.swt.widgets.Composite(shell, SWT.NONE);
				p.setLayout(new GridLayout(2, true));
				p.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				buildWindowList();
				buildControlPanel();
				// p.add(buildWindowList(), ij.layout.BorderLayout.NORTH,0);
				// p.add(buildControlPanel(), ij.layout.BorderLayout.CENTER,1);
			
		});
		return p;
	}

	// --------------------------------------------------
	/**
	 * Builds list of open ImageWindows
	 * 
	 * @return
	 */
	protected Widget buildWindowList() {

		ImagePlus img;
		ImageWindow iw;
		// get IDList from WindowManager
		int[] imageIDs = WindowManager.getIDList();
		if (imageIDs != null) {
			int size;
			if (imageIDs.length < 10) {
				size = imageIDs.length;
			} else {
				size = 10;
			}
			// Initialize window list and vector that maps list entries to window IDs.
			wList = new org.eclipse.swt.widgets.List(p, SWT.MULTI);
			wList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			vListMap = new Vector();
			// Add Windows to list, select windows, that previously were selected
			for (int n = 0; n < imageIDs.length; ++n) {
				ImagePlus imp = WindowManager.getImage(imageIDs[n]);
				if (imp == null)
					continue; // image may have been closed in the meanwhile (e.g. 'Close All')
				vListMap.addElement(Integer.valueOf(imageIDs[n]));
				wList.add(imp.getTitle());
				if (vwins != null && vwins.contains(Integer.valueOf(imageIDs[n]))) {
					wList.select(n);
				}
			}
			// clean vector of selected images (vwins) from images that have been closed,
			if (vwins != null && vwins.size() != 0) {
				for (int n = 0; n < vwins.size(); ++n) {
					if (!vListMap.contains(vwins.elementAt(n))) {
						vwins.removeElementAt(n);
						n -= 1;
					}
				}
			}
			wList.addSelectionListener(this);
			// wList.addActionListener(this);
			// wList.addKeyListener(ijInstance); //would cause ImageJ to zoom when up/down
			// arrows are pressed
			return wList;
		} else {
			Label label = new Label(p, SWT.NONE);
			label.setText("No windows to select.");
			wList = null;
			vListMap = null;
			vwins = null;
			p.layout();
			return (Widget) label;
		}
	}

	/** Builds panel containing control buttons. */
	protected Composite buildControlPanel() {

		/*
		 * GridLayout layout = new GridLayout(4,2); layout.setVgap(2);
		 * layout.setHgap(2);
		 */
		Composite co = new Composite(p, SWT.NONE);
		co.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		co.setLayout(new GridLayout(1, true));
		// Checkbox: synchronize cursor
		cCursor = new Button(co, SWT.CHECK);
		cCursor.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		cCursor.setText("Sync cursor");
		cCursor.setSelection(true);
		cCursor.addSelectionListener(this);
		cCursor.addKeyListener(ijInstance);
		// p.add(cCursor);
		// Checkbox: propagate slice
		cSlice = new Button(co, SWT.CHECK);
		cSlice.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		cSlice.setText("Sync z-slices");
		cSlice.setSelection(true);
		cSlice.addSelectionListener(this);
		cSlice.addKeyListener(ijInstance);
		// p.add(cSlice);
		// TODO: Give functionality to Synchronize Channels and Synchronize t-Frames
		// checkboxes.
		// Checkbox: synchronize channels (for hyperstacks)
		cChannel = new Button(co, SWT.CHECK);
		cChannel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		cChannel.setText("Sync channels");
		cChannel.setSelection(true);
		cChannel.addSelectionListener(this);
		cChannel.addKeyListener(ijInstance);
		// p.add(cChannel);
		// Checkbox: synchronize time-frames (for hyperstacks)
		cFrame = new Button(co, SWT.CHECK);
		cFrame.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		cFrame.setText("Sync t-frames");
		cFrame.setSelection(true);
		cFrame.addSelectionListener(this);
		cFrame.addKeyListener(ijInstance);
		// p.add(cFrame);
		// Checkbox: image coordinates
		cCoords = new Button(co, SWT.CHECK);
		cCoords.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		cCoords.setText("Image coordinates");
		cCoords.setSelection(true);
		cCoords.addSelectionListener(this);
		cCoords.addKeyListener(ijInstance);
		// p.add(cCoords);
		// Checkbox: image scaling (take pixel scale and offset into account)
		cScaling = new Button(co, SWT.CHECK);
		cScaling.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		cScaling.setText("Image scaling");
		cScaling.setSelection(false);
		cScaling.addSelectionListener(this);
		cScaling.addKeyListener(ijInstance);
		// cScaling.addItemListener(this);
		// p.add(cScaling);
		// Synchronize all windows.
		bSyncAll = new Button(co, SWT.NONE);
		bSyncAll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		bSyncAll.setText("Synchronize All");
		bSyncAll.addSelectionListener(this);
		bSyncAll.addKeyListener(ijInstance);
		// p.add(bSyncAll);
		// Unsynchronize all windows.
		bUnsyncAll = new Button(co, SWT.NONE);
		bUnsyncAll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		bUnsyncAll.setText("Unsynchronize All");
		bUnsyncAll.addSelectionListener(this);
		bUnsyncAll.addKeyListener(ijInstance);
		// p.add(bUnsyncAll);
		return co;
	}

	// --------------------------------------------------
	/**
	 * Compute bounding rectangle given current and old cursor locations. This is
	 * used to determine what part of image to redraw.
	 */
	protected Rectangle boundingRect(int x, int y, int oldX, int oldY) {

		int dx = Math.abs(oldX - x) / 2;
		int dy = Math.abs(oldY - y) / 2;
		int xOffset = dx + SCALE * SZ;
		int yOffset = dy + SCALE * SZ;
		int xCenter = (x + oldX) / 2;
		int yCenter = (y + oldY) / 2;
		int xOrg = Math.max(xCenter - xOffset, 0);
		int yOrg = Math.max(yCenter - yOffset, 0);
		int w = 2 * xOffset;
		int h = 2 * yOffset;
		return new Rectangle(xOrg, yOrg, w, h);
	}

	/* Update the List of Windows in the GUI. Used by the "Update" button. */
	protected void updateWindowList() {

		// Don't build a new window list, while the old one is removed.
		// When an StackWindow is replaced by an OpenStackWindow, updateWindowList
		// is called again and the other components in the panel are removed, also.
		Display.getDefault().syncExec(() -> {
				if (wList != null && !wList.isDisposed()) {
					wList.dispose();
				}

				// Widget newWindowList = buildWindowList();
				buildWindowList();
				// GUI.scale(newWindowList);
				// panel.remove(0);
				// panel.add(newWindowList,ij.layout.BorderLayout.NORTH,0);
				panel.layout(true);
				// shell.layout();
				shell.pack();
				shell.setSize(300, 350);
			
		});
	}

	// --------------------------------------------------
	private void addSelections() {

		if (wList == null)
			return; // nothing to select
		int[] listIndexes = wList.getSelectionIndices();
		Integer I;
		Vector v = new Vector();
		for (int n = 0; n < listIndexes.length; ++n) {
			I = (Integer) vListMap.elementAt(listIndexes[n]);
			v.addElement(I);
		}
		addWindows(v);
	}

	// --------------------------------------------------
	/**
	 * Adds "this" object as mouse listener and mouse motion listener to each of the
	 * windows in input array.
	 */
	private void addWindows(Vector v) {

		Integer I;
		ImagePlus imp;
		ImageWindow iw;
		// Handle initial case of no windows.
		if (vwins == null && v.size() > 0)
			vwins = new Vector();
		// Add all windows in vector to synchronized window list.
		for (int n = 0; n < v.size(); ++n) {
			I = (Integer) v.elementAt(n);
			// Make sure input window is not already on list.
			if (!vwins.contains(I)) {
				imp = WindowManager.getImage(I.intValue());
				if (imp != null) {
					iw = imp.getWindow();
					iw.getCanvas().addMouseMoveListener(this);
					iw.getCanvas().addMouseListener(this);
					iw.getCanvas().addMouseTrackListener(this);
					vwins.addElement(I);
				}
			}
		}
	}

	// --------------------------------------------------
	private void removeAllWindows() {

		if (vwins != null) {
			Integer I;
			for (int n = 0; n < vwins.size(); ++n) {
				I = (Integer) vwins.elementAt(n);
				removeWindow(I);
			}
			// Remove all windows from window list.
			vwins.removeAllElements();
		}
		// Deselect all elements on list (if present).
		if (wList == null)
			return;
		for (int n = 0; n < wList.getItemCount(); ++n)
			wList.deselect(n);
	}

	// --------------------------------------------------
	/**
	 * Remove "this" object as mouse listener and mouse motion listener from the
	 * window with ID I.
	 */
	private void removeWindow(Integer I) {

		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas ic;
		imp = WindowManager.getImage(I.intValue());
		if (imp != null) {
			iw = imp.getWindow();
			if (iw != null) {
				ic = iw.getCanvas();
				if (ic != null) {
					ic.removeMouseListener(this);
					ic.removeMouseMoveListener(this);
					ic.removeMouseTrackListener(this);
					// Repaint to get rid of sync indicator.
					ic.redraw();
					// ic.paint(ic.getGraphics());
				}
			}
		}
	}

	/** Draw cursor that indicates windows are synchronized. */
	private void drawSyncCursor(ImagePlus imp, int x, int y) {

		ImageCanvas ic = imp.getCanvas();
		if (ic == null)
			return;
		double xpSZ = ic.offScreenXD(x + SZ);
		double xmSZ = ic.offScreenXD(x - SZ);
		double ypSZ = ic.offScreenYD(y + SZ);
		double ymSZ = ic.offScreenYD(y - SZ);
		double xp2 = ic.offScreenXD(x + 2);
		double xm2 = ic.offScreenXD(x - 2);
		double yp2 = ic.offScreenYD(y + 2);
		double ym2 = ic.offScreenYD(y - 2);
		GeneralPath path = new GeneralPath();
		path.moveTo(xmSZ, ymSZ);
		path.lineTo(xm2, ym2);
		path.moveTo(xpSZ, ypSZ);
		path.lineTo(xp2, yp2);
		path.moveTo(xpSZ, ymSZ);
		path.lineTo(xp2, ym2);
		path.moveTo(xmSZ, ypSZ);
		path.lineTo(xm2, yp2);
		setCursor(imp, new ShapeRoi(path));
	}

	public synchronized void setCursor(ImagePlus imp, Roi cursor) {

		Overlay overlay2 = imp.getOverlay();
		if (overlay2 != null) {
			for (int i = overlay2.size() - 1; i >= 0; i--) {
				Roi roi2 = overlay2.get(i);
				if (roi2.isCursor())
					overlay2.remove(i);
			}
			if (cursor == null) {
				imp.setOverlay(overlay2);
				return;
			}
		} else
			overlay2 = new Overlay();
		if (cursor != null) {
			overlay2.add(cursor);
			cursor.setStrokeColor(java.awt.Color.red);
			cursor.setStrokeWidth(2);
			cursor.setNonScalable(true);
			cursor.setIsCursor(true);
			imp.setOverlay(overlay2);
		}
	}

	/** Store srcRect and Magnification of the currently active ImageCanvas ic */
	private void storeCanvasState(ImageCanvas ic) {

		currentMag = ic.getMagnification();
		currentSrcRect = new org.eclipse.swt.graphics.Rectangle(ic.getSrcRect().x, ic.getSrcRect().y,
				ic.getSrcRect().width, ic.getSrcRect().height);
	}

	// --------------------------------------------------
	/** Get ImagePlus from Windows-Vector vwins. */
	public ImagePlus getImageFromVector(int n) {

		if (vwins == null || n < 0 || vwins.size() < n + 1)
			return null;
		ImagePlus imp;
		imp = WindowManager.getImage(((Integer) vwins.elementAt(n)).intValue());
		if (imp.isLocked())
			return null; // must not touch locked windows
		return imp;
	}

	/**
	 * Get the title of image n from Windows-Vector vwins. If the image ends with
	 * .tif, the extension is removed.
	 */
	public String getImageTitleFromVector(int n) {

		if (vwins == null || n < 0 || vwins.size() < n + 1)
			return "";
		ImagePlus imp;
		imp = WindowManager.getImage(((Integer) vwins.elementAt(n)).intValue());
		String title = imp.getTitle();
		if (title.length() >= 4 && (title.substring(title.length() - 4)).equalsIgnoreCase(".tif")) {
			title = title.substring(0, title.length() - 4);
		} else if (title.length() >= 5 && (title.substring(title.length() - 5)).equalsIgnoreCase(".tiff")) {
			title = title.substring(0, title.length() - 5);
		}
		return title;
	}

	/**
	 * Get index of "image" in vector of synchronized windows, if image is in
	 * vector. Else return -1.
	 */
	public int getIndexOfImage(ImagePlus image) {

		int index = -1;
		ImagePlus imp;
		if (vwins == null || vwins.size() == 0)
			return index;
		for (int n = 0; n < vwins.size(); n++) {
			imp = WindowManager.getImage(((Integer) vwins.elementAt(n)).intValue());
			if (imp == image) {
				index = n;
				break;
			}
		}
		return index;
	}

	// --------------------------------------------------
	/**
	 * Get Screen Coordinates for ImageCanvas ic matching the OffScreen Coordinates
	 * of the current ImageCanvas. (srcRect and magnification stored after each
	 * received event.) Input: The target ImageCanvas, the current ImageCanvas,
	 * x-ScreenCoordinate for current Canvas, y-ScreenCoordinate for current Canvas
	 * If the "ImageScaling" checkbox is selected, Scaling and Offset of the images
	 * are taken into account.
	 */
	protected Point getMatchingCoords(ImageCanvas ic, ImageCanvas icc, int x, int y) {

		double xOffScreen = currentSrcRect.x + (x / currentMag);
		double yOffScreen = currentSrcRect.y + (y / currentMag);
		if (cScaling.getSelection()) {
			Calibration cal = ((ImageWindow) ic.getImageWindow()).getImagePlus().getCalibration();
			Calibration curCal = ((ImageWindow) icc.getImageWindow()).getImagePlus().getCalibration();
			xOffScreen = ((xOffScreen - curCal.xOrigin) * curCal.pixelWidth) / cal.pixelWidth + cal.xOrigin;
			yOffScreen = ((yOffScreen - curCal.yOrigin) * curCal.pixelHeight) / cal.pixelHeight + cal.yOrigin;
		}
		int xnew = ic.screenXD(xOffScreen);
		int ynew = ic.screenYD(yOffScreen);
		return new Point(xnew, ynew);
	}
	// --------------------------------------------------
	/**
	 * Makes a new mouse event from MouseEvent e with the Canvas c as source and the
	 * coordinates of Point p as X and Y.
	 */

	/*
	 * private MouseEvent adaptEvent(MouseEvent e, Widget c, Point p) { return new
	 * MouseEvent(c, e.getID(), e.getWhen(), e.getModifiers(), p.x, p.y,
	 * e.getClickCount(), e.isPopupTrigger()); }
	 */
	/**
	 * Makes a new mouse event from MouseEvent e with the Canvas c as source and the
	 * coordinates of Point p as X and Y.
	 */
	private MouseEvent adaptEvent(org.eclipse.swt.events.MouseEvent e, Widget c, Point p) {

		Event evt = new Event();
		evt.widget = c;
		evt.stateMask = e.stateMask;
		evt.button = e.button;
		evt.x = p.x;
		evt.y = p.y;
		evt.count = e.count;
		return new org.eclipse.swt.events.MouseEvent(evt);
	}
	/*
	 * public Insets getInsets() { Insets i = super.getInsets(); return new
	 * Insets(i.top+10, i.left+10, i.bottom+10, i.right+10); }
	 */

	/*
	 * public void close() { super.close(); instance = null; location =
	 * getLocation(); }
	 */
	public static SyncWindows getInstance() {

		return instance;
	}

	public String commandExecuting(String command) {

		AtomicReference<Boolean> cselect = new AtomicReference<Boolean>();
		Display.getDefault().syncExec(() -> {
				cselect.set(cScaling.getSelection());
			
		});
		if (vwins != null && cScaling != null && cselect.get()
				&& ("In [+]".equals(command) || "Out [-]".equals(command))) {
			ImagePlus imp = WindowManager.getCurrentImage();
			ImageCanvas cic = imp != null ? imp.getCanvas() : null;
			if (cic == null)
				return command;
			Point loc = cic.getCursorLocSwt();
			if (!cic.cursorOverImage()) {
				Rectangle srcRect = cic.getSrcRectSwt();
				loc.x = srcRect.x + srcRect.width / 2;
				loc.y = srcRect.y + srcRect.height / 2;
			}
			int sx = cic.screenX(loc.x);
			int sy = cic.screenY(loc.y);
			for (int i = 0; i < vwins.size(); i++) {
				imp = getImageFromVector(i);
				if (imp != null) {
					ImageCanvas ic = imp.getCanvas();
					if (ic != cic) {
						if ("In [+]".equals(command))
							ic.zoomIn(sx, sy);
						else
							ic.zoomOut(sx, sy);
						if (ic.getMagnification() <= 1.0)
							imp.repaintWindow();
					}
				}
			}
		}
		return command;
	}
} // SyncWindows_

/**
 * The Listener interface for receiving DisplayChange events. The listener can
 * be registered to an Object issuing DisplayChange events by its
 * addDisplayChangeListener method. So far only OpenStackWindow used by
 * SyncWindows is such an Object.
 */
interface DisplayChangeListener extends java.util.EventListener {

	public void displayChanged(DisplayChangeEvent e);
}
/*
 * ------------------------------------------------------------------------- /*
 * /* CLASS DisplayChangeEvent /* /*
 * -------------------------------------------------------------------------
 */

/** To be raised when a property of the image display has been changed */
class DisplayChangeEvent extends EventObject {

	/**
	 * Type of change in display: Coordinate X, Y, Z, the Zoom, time, color channel.
	 * So far there is no need for properties other than Z.
	 */
	public static final int X = 1;
	public static final int Y = 2;
	public static final int Z = 3;
	public static final int ZOOM = 4;
	public static final int T = 5;
	public static final int CHANNEL = 6;
	private int type;
	private int value;

	public DisplayChangeEvent(Object source, int type, int value) {

		super(source);
		this.type = type;
		this.value = value;
	}

	public int getType() {

		return type;
	}

	public void setType(int type) {

		this.type = type;
	}

	public int getValue() {

		return value;
	}

	public void setValue(int value) {

		this.value = value;
	}
}
/*
 * ------------------------------------------------------------------------- /*
 * /* CLASS IJEventMulticaster /* /*
 * -------------------------------------------------------------------------
 */

/**
 * Multicaster for events special to ImageJ
 * <p>
 * Example how to implement an object, which fires DisplayChangeEvents using the
 * IJEventMulticaster:
 *
 * <pre>
 * <code>
 * public mySpecialWindow extends StackWindow {
 *
 *		DisplayChangeListener dclistener = null;
 *
 *		public synchronized void addDisplayChangeListener(DisplayChangeListener l) {
 *			dclistener = IJEventMulticaster.add(dclistener, l);
 *		}
 *
 *		public synchronized void removeDisplayChangeListener(DisplayChangeListener l) {
 *			dclistener = IJEventMulticaster.remove(dclistener, l);
 *		}
 *
 *		public void myEventFiringMethod(arguments) {
 *			... code ...
 *			if (dclistener != null) {
 *				DisplayChangeEvent dcEvent = new DisplayChangeEvent(this, DisplayChangeEvent.Z, zSlice);
 *				dclistener.displayChanged(dcEvent);
 *			}
 *			... code ...
 *		}
 *
 *		... other methods ...
 * }
 * </code>
 * </pre>
 *
 * To put in a new event-listener (by changing this class or extending it):
 * <p>
 * - Add the listener to the "implements" list.
 * <p>
 * - Add the methods of this listener to pass on the events (like
 * displayChanged).
 * <p>
 * - Add the methods "add" and "remove" with the corresponding listener type.
 * <p>
 *
 * @author code taken from Sun's AWTEventMulticaster by J. Walter 2002-03-07
 */
class IJEventMulticaster extends AWTEventMulticaster implements DisplayChangeListener {

	IJEventMulticaster(EventListener a, EventListener b) {

		super(a, b);
	}

	/**
	 * Handles the DisplayChange event by invoking the displayChanged methods on
	 * listener-a and listener-b.
	 * 
	 * @param e the DisplayChange event
	 */
	public void displayChanged(DisplayChangeEvent e) {

		((DisplayChangeListener) a).displayChanged(e);
		((DisplayChangeListener) b).displayChanged(e);
	}

	/**
	 * Adds DisplayChange-listener-a with DisplayChange-listener-b and returns the
	 * resulting multicast listener.
	 * 
	 * @param a DisplayChange-listener-a
	 * @param b DisplayChange-listener-b
	 */
	public static DisplayChangeListener add(DisplayChangeListener a, DisplayChangeListener b) {

		return (DisplayChangeListener) addInternal(a, b);
	}

	/**
	 * Removes the old DisplayChange-listener from DisplayChange-listener-l and
	 * returns the resulting multicast listener.
	 * 
	 * @param l    DisplayChange-listener-l
	 * @param oldl the DisplayChange-listener being removed
	 */
	public static DisplayChangeListener remove(DisplayChangeListener l, DisplayChangeListener oldl) {

		return (DisplayChangeListener) removeInternal(l, oldl);
	}
}
