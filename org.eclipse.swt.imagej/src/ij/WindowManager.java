package ij;

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Vector;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import ij.gui.GenericDialog;
import ij.gui.HistogramWindow;
import ij.gui.ImageWindow;
import ij.gui.PlotWindow;
import ij.macro.Interpreter;
import ij.plugin.frame.Commands;
import ij.plugin.frame.Editor;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.swt.WindowSwt;
import ij.text.TextWindow;
import ij.util.Tools;

/** This class consists of static methods used to manage ImageJ's windows. */
public class WindowManager {

	public static boolean checkForDuplicateName;
	private static Vector imageList = new Vector(); // list of image windows
	private static Vector activations = new Vector(); // list of image windows, ordered by activation time
	private static Vector nonImageList = new Vector(); // list of non-image windows (Frames and Dialogs)
	private static ImageWindow currentWindow; // active image window
	private static Object frontWindow;
	private static Object frontTable;
	private static Object frontFrame;
	private static Shell frontShell;
	private static Hashtable tempImageTable = new Hashtable();

	private WindowManager() {

	}

	/** Makes the image contained in the specified window the active image. */
	public static void setCurrentWindow(ImageWindow win) {

		if (win == null || win.isClosed() || win.getImagePlus() == null) // deadlock-"wait to lock"
			return;
		// IJ.log("setCurrentWindow: "+win.getImagePlus().getTitle()+"
		// ("+(currentWindow!=null?currentWindow.getImagePlus().getTitle():"null") +
		// ")");
		setWindow(win);
		tempImageTable.remove(Thread.currentThread());
		if (win == currentWindow || imageList.size() == 0)
			return;
		if (currentWindow != null) {
			// free up pixel buffers used by current window
			ImagePlus imp = currentWindow.getImagePlus();
			if (imp != null) {
				if (!Prefs.keepUndoBuffers)
					imp.trimProcessor();
				imp.saveRoi();
			}
		}
		Undo.reset();
		currentWindow = win;
		activations.remove(win);
		activations.add(win);
		Menus.updateMenus();
		if (IJ.recording() && !IJ.isMacro())
			Recorder.record("selectImage", win.getImagePlus().getTitle());
	}

	/** Returns the active ImageWindow. */
	public static ImageWindow getCurrentWindow() {

		return currentWindow;
	}

	static int getCurrentIndex() {

		return imageList.indexOf(currentWindow);
	}

	/**
	 * Returns a reference to the active image or null if there isn't one.
	 * 
	 * @see ij.IJ#getImage
	 */
	public static ImagePlus getCurrentImage() {

		ImagePlus img = (ImagePlus) tempImageTable.get(Thread.currentThread());
		// String str = (img==null)?" null":"";
		if (img == null)
			img = getActiveImage();
		// if (img!=null) IJ.log("getCurrentImage: "+img.getTitle()+"
		// "+Thread.currentThread().hashCode()+str);
		return img;
	}

	/**
	 * Makes the specified image temporarily the active image for this thread. Call
	 * again with a null argument to revert to the previous active image.
	 */
	public static void setTempCurrentImage(ImagePlus img) {

		// IJ.log("setTempImage: "+(img!=null?""+img:"null")+"
		// "+Thread.currentThread().hashCode());
		if (img == null)
			tempImageTable.remove(Thread.currentThread());
		else
			tempImageTable.put(Thread.currentThread(), img);
	}

	/** Sets a temporary image for the specified thread. */
	public static void setTempCurrentImage(Thread thread, ImagePlus img) {

		if (thread == null)
			throw new RuntimeException("thread==null");
		if (img == null)
			tempImageTable.remove(thread);
		else
			tempImageTable.put(thread, img);
	}

	/** Returns the active ImagePlus. */
	private static ImagePlus getActiveImage() {

		if (currentWindow != null)
			return currentWindow.getImagePlus();
		else if (frontWindow != null && (frontWindow instanceof ImageWindow))
			return frontWindow != null ? ((ImageWindow) frontWindow).getImagePlus() : null;
		else if (imageList.size() > 0) {
			ImagePlus imp = getFocusManagerActiveImage();
			if (imp != null)
				return imp;
			ImageWindow win = (ImageWindow) imageList.get(imageList.size() - 1);
			return win.getImagePlus();
		} else
			return Interpreter.getLastBatchModeImage();
	}

	/* Not available in SWT? */
	private static ImagePlus getFocusManagerActiveImage() {

		/*
		 * if (IJ.isMacro()) return null; KeyboardFocusManager kfm =
		 * KeyboardFocusManager.getCurrentKeyboardFocusManager(); Window win =
		 * kfm.getActiveWindow(); ImagePlus imp = null; if (win != null && (win
		 * instanceof ImageWindow)) imp = ((ImageWindow) win).getImagePlus();
		 */
		// return imp;
		return null;
	}

	/** Returns the number of open image windows. */
	public static int getWindowCount() {

		int count = imageList.size();
		return count;
	}

	/** Returns the number of open images. */
	public static int getImageCount() {

		int count = imageList.size();
		count += Interpreter.getBatchModeImageCount();
		if (count == 0 && getCurrentImage() != null)
			count = 1;
		return count;
	}

	/** Returns the front most window or null. */
	public static Object getActiveWindow() {

		return frontWindow;
	}

	/**
	 * Returns the Window containing the active table, or null.
	 * 
	 * @see ij.measure.ResultsTable#getActiveTable
	 */
	public static Object getActiveTable() {

		return frontTable;
	}

	/** Obsolete; replaced by getActiveWindow. */
	public static Object getFrontWindow() {

		return frontFrame;
	}

	/**
	 * Returns a list of the IDs of open images. Returns null if no image windows
	 * are open.
	 */
	public synchronized static int[] getIDList() {

		int nWindows = imageList.size();
		int[] batchModeImages = Interpreter.getBatchModeImageIDs();
		int nBatchImages = batchModeImages.length;
		if ((nWindows + nBatchImages) == 0)
			return null;
		int[] list = new int[nWindows + nBatchImages];
		for (int i = 0; i < nWindows; i++) {
			ImageWindow win = (ImageWindow) imageList.get(i);
			list[i] = win.getImagePlus().getID();
		}
		int index = 0;
		for (int i = nWindows; i < nWindows + nBatchImages; i++)
			list[i] = batchModeImages[index++];
		// IJ.log("getIDList");
		// for (int i=0; i<list.length; i++) {
		// IJ.log(" "+i+" "+nWindows+" "+nBatchImages+" "+list[i]+"
		// "+getImage((list[i])));
		// }
		return list;
	}

	/** Returns a list of the titles of all open images. */
	public synchronized static String[] getImageTitles() {

		int[] list = getIDList();
		if (list == null)
			return new String[0];
		String[] titles = new String[list.length];
		for (int i = 0; i < list.length; i++) {
			ImagePlus img = getImage(list[i]);
			titles[i] = img.getTitle();
		}
		return titles;
	}

	/** Returns an array containing a list of the non-image Frames. */
	public synchronized static Object[] getNonImageWindows() {

		ArrayList list = new ArrayList();
		for (int i = 0; i < nonImageList.size(); i++) {
			Object win = nonImageList.get(i);
			if (win instanceof Frame)
				list.add(win);
			else if (win instanceof Shell) {
				list.add(win);
			} else if (win instanceof WindowSwt) {
				list.add(win);
			}
		}
		Object[] frames = new Object[list.size()];
		list.toArray(frames);
		return frames;
	}

	/** Returns an array containing a list of the non-image Frames and Dialogs. */
	public synchronized static WindowSwt[] getAllNonImageWindows() {

		ArrayList list = new ArrayList();
		for (int i = 0; i < nonImageList.size(); i++) {
			Object win = nonImageList.get(i);
			if (win instanceof WindowSwt)
				list.add(win);
		}
		WindowSwt[] windows = new WindowSwt[list.size()];
		list.toArray(windows);
		return windows;
	}

	/** Returns an array containing the titles of non-image Frames and Dialogs. */
	public synchronized static String[] getNonImageTitles() {

		ArrayList list = new ArrayList();
		for (int i = 0; i < nonImageList.size(); i++) {
			Object win = nonImageList.get(i);
			Display.getDefault().syncExec(() -> {

				String title = win instanceof WindowSwt ? ((WindowSwt) win).getShell().getText()
						: ((WindowSwt) win).getShell().getText();
				list.add(title);

			});
		}
		String[] titles = new String[list.size()];
		list.toArray(titles);
		return titles;
	}

	/**
	 * For IDs less than zero, returns the ImagePlus with the specified ID. Returns
	 * null if no open window has a matching ID or no images are open. For IDs
	 * greater than zero, returns the Nth ImagePlus. Returns null if the ID is zero.
	 */
	public synchronized static ImagePlus getImage(int imageID) {

		if (imageID > 0)
			imageID = getNthImageID(imageID);
		if (imageID == 0 || getImageCount() == 0)
			return null;
		ImagePlus imp2 = Interpreter.getBatchModeImage(imageID);
		if (imp2 != null)
			return imp2;
		ImagePlus imp = null;
		for (int i = 0; i < imageList.size(); i++) {
			ImageWindow win = (ImageWindow) imageList.get(i);
			imp2 = win.getImagePlus();
			if (imageID == imp2.getID()) {
				imp = imp2;
				break;
			}
		}
		imp2 = getCurrentImage();
		if (imp == null && imp2 != null && imp2.getID() == imageID)
			return imp2;
		return imp;
	}

	/**
	 * Returns the ID of the Nth open image. Returns zero if n<=0 or n greater than
	 * the number of open image windows.
	 */
	public synchronized static int getNthImageID(int n) {

		if (n <= 0)
			return 0;
		if (Interpreter.isBatchMode()) {
			int[] list = getIDList();
			if (n > list.length)
				return 0;
			else
				return list[n - 1];
		} else {
			if (n > imageList.size())
				return 0;
			ImageWindow win = (ImageWindow) imageList.get(n - 1);
			if (win != null)
				return win.getImagePlus().getID();
			else
				return 0;
		}
	}

	/**
	 * Returns the first image that has the specified title or null if it is not
	 * found.
	 */
	public synchronized static ImagePlus getImage(String title) {

		int[] wList = getIDList();
		if (wList == null)
			return null;
		for (int i = 0; i < wList.length; i++) {
			ImagePlus imp = getImage(wList[i]);
			if (imp != null && imp.getTitle().equals(title))
				return imp;
		}
		return null;
	}

	/** Adds the specified window to the Window menu. */
	public synchronized static void addWindow(Object win) {

		if (win == null)
			return;
		else if (win instanceof ImageWindow)
			addImageWindow((ImageWindow) win);
		/*
		 * else if (win instanceof ImageWindow) addShellWindow((Shell) win);
		 */
		else {
			if (win instanceof Window) {
				Menus.insertWindowMenuItem((Window) win);
			} else if (win instanceof Shell) {
				Menus.insertShellMenuItem((Shell) win);
			} else if (win instanceof GenericDialog) {
				Display.getDefault().syncExec(() -> {

					Menus.insertShellMenuItem(((GenericDialog) win).getShell());

				});
			} else if (win instanceof WindowSwt) {
				Display.getDefault().syncExec(() -> {

					Menus.insertShellMenuItem((WindowSwt) win);

				});
			}
			nonImageList.add(win);
		}
	}

	/** Adds the specified Frame to the Window menu. */
	public static void addWindow(Frame win) {

		addWindow((Window) win);
	}
	/*
	 * private static void addShellWindow(Shell win) { imageList.add(win); //
	 * Menus.addWindowMenuItem(imp); // setCurrentWindow(win); }
	 */

	private static void addImageWindow(ImageWindow win) {

		ImagePlus imp = win.getImagePlus();
		if (imp == null)
			return;
		checkForDuplicateName(imp);
		imageList.add(win);
		Menus.addWindowMenuItem(imp);
		setCurrentWindow(win);
	}

	static void checkForDuplicateName(ImagePlus imp) {

		if (checkForDuplicateName) {
			String name = imp.getTitle();
			if (isDuplicateName(name))
				imp.setTitle(getUniqueName(name));
		}
		checkForDuplicateName = false;
	}

	static boolean isDuplicateName(String name) {

		if (name == null)
			return false;
		int n = imageList.size();
		for (int i = 0; i < n; i++) {
			ImageWindow win = (ImageWindow) imageList.get(i);
			String name2 = win.getImagePlus().getTitle();
			if (name.equals(name2))
				return true;
		}
		return false;
	}
	/*
	 * static boolean isDuplicateBatchModeName(String name) { int[] list =
	 * getIDList(); for (int i=0; i<list.length; i++) { ImagePlus imp =
	 * getImage(list[i]); if (imp==null) return false; String name2 =
	 * imp.getTitle(); if (name.equals(name2)) return true; } return false; }
	 */

	/**
	 * Returns a unique name by adding, before the extension, -1, -2, etc. as
	 * needed.
	 */
	public static String getUniqueName(String name) {

		return getUniqueName(null, name);
	}

	public static String getUniqueName(ImagePlus imp, String name) {

		String name2 = name;
		String extension = "";
		int len = name2.length();
		int lastDot = name2.lastIndexOf(".");
		if (lastDot != -1 && len - lastDot < 6 && lastDot != len - 1) {
			extension = name2.substring(lastDot, len);
			name2 = name2.substring(0, lastDot);
		}
		int lastDash = name2.lastIndexOf("-");
		len = name2.length();
		if (imp != null && imp.getProp("UniqueName") == null)
			lastDash = -1;
		if (lastDash != -1 && len - lastDash < 4 && lastDash < len - 1 && Character.isDigit(name2.charAt(lastDash + 1))
				&& name2.charAt(lastDash + 1) != '0')
			name2 = name2.substring(0, lastDash);
		for (int i = 1; i <= 99; i++) {
			String name3 = name2 + "-" + i + extension;
			if (!isDuplicateName(name3))
				return name3;
		}
		return name;
	}

	/** If 'name' is not unique, adds -1, -2, etc. as needed to make it unique. */
	public static String makeUniqueName(String name) {

		return isDuplicateName(name) ? getUniqueName(name) : name;
	}

	/* Changed to an Object instead of ImageWindow to allow SWT objects (Shell)! */
	/** Removes the specified window from the Window menu. */
	public static void removeWindow(Object win) {

		if (win instanceof ImageWindow)
			removeImageWindow((ImageWindow) win);
		else {
			int index = nonImageList.indexOf(win);
			// System.out.println(win+" "+"Index: "+index);
			ImageJ ij = IJ.getInstance();
			if (index >= 0) {
				Menus.removeWindowMenuItem(index);
				nonImageList.removeElement(win);
			}
			if (win != null && win == frontTable)
				frontTable = null;
		}
		setWindow(null);
	}

	/** Removes the specified Frame from the Window menu. */
	public static void removeWindow(Frame win) {

		removeWindow((Window) win);
	}

	/** Removes the specified Frame from the Window menu. */
	public static void removeWindow(WindowSwt win) {

		removeWindow((Object) win);
	}

	/** Removes the specified Frame from the Window menu. */
	public static void removeWindow(Shell win) {

		removeWindow((Object) win);
	}

	/* Changed to an Object instead of ImageWindow to allow SWT objects (Shell)! */
	private static void removeImageWindow(Object win) {

		// System.out.println("remove "+win);
		int index = imageList.indexOf(win);
		if (index == -1)
			return; // not on the window list
		try {
			synchronized (WindowManager.class) {
				imageList.remove(win);
			}
			activations.remove(win);
			if (imageList.size() > 1 && !Prefs.closingAll) {
				ImageWindow win2 = activations.size() > 0 ? (ImageWindow) activations.get(activations.size() - 1)
						: null;
				setCurrentWindow(win2);
			} else
				currentWindow = null;
			setTempCurrentImage(null); // ???
			int nonImageCount = nonImageList.size();
			if (nonImageCount > 0)
				nonImageCount++;
			Menus.removeWindowMenuItem(nonImageCount + index);
			Menus.updateMenus();
			Undo.reset();
		} catch (Exception e) {
		}
	}

	/** The specified Window becomes the front window. */
	public static void setWindow(Object win) {

		// System.out.println("setWindow(W): "+win);
		frontWindow = win;
		if (win instanceof Frame)
			frontFrame = (Frame) win;
		else if (win instanceof Shell) {
			frontFrame = (Shell) win;
		} else if (win instanceof ImageWindow) {
			frontFrame = ((ImageWindow) win).getShell();
		} else if (win instanceof WindowSwt) {
			frontFrame = ((WindowSwt) win).getShell();
		}
	}

	/**
	 * The specified frame becomes the front window, the one returnd by
	 * getFrontWindow().
	 */
	public static void setWindow(Frame win) {

		frontWindow = win;
		frontFrame = win;
		/* Has to be changed for SWT later! */
		/*
		 * if (win != null && win instanceof TextWindow && !(win instanceof Editor) &&
		 * !"Log".equals(((TextWindow) win).getTitle())) frontTable = win;
		 */
		// System.out.println("Set window(F): "+(win!=null?win.getTitle():"null"));
	}

	/**
	 * Closes all windows. Stops and returns false if an image or Editor "save
	 * changes" dialog is canceled.
	 */
	public synchronized static boolean closeAllWindows() {

		Prefs.closingAll = true;
		while (imageList.size() > 0) {
			if (!((ImageWindow) imageList.get(0)).close()) {
				Prefs.closingAll = false;
				return false;
			}
			if (!quittingViaMacro())
				IJ.wait(100);
		}
		Prefs.closingAll = false;
		Object[] nonImages = getNonImageWindows();
		for (int i = 0; i < nonImages.length; i++) {
			Object frame = nonImages[i];
			if (frame != null && frame instanceof Commands)
				((Commands) frame).shell.close();
			else if (frame != null && (frame instanceof Editor)) {
				((Editor) frame).close();
				if (((Editor) frame).fileChanged())
					return false;
				if (!quittingViaMacro())
					IJ.wait(100);
			}
		}
		ImageJ ij = IJ.getInstance();
		if (ij != null && ij.quitting() && IJ.getApplet() == null)
			return true;
		for (int i = 0; i < nonImages.length; i++) {
			/* Changed for SWT */
			Object frame = nonImages[i];
			if ((frame instanceof WindowSwt) && !(frame instanceof Editor))
				((WindowSwt) frame).getShell().close();
			else if (frame instanceof TextWindow)
				((TextWindow) frame).close();
			else if (frame instanceof Shell) {
				// frame.setVisible(false);
				((Shell) frame).close();
			}
		}
		return true;
	}

	private static boolean quittingViaMacro() {

		ImageJ ij = IJ.getInstance();
		return ij != null && ij.quittingViaMacro();
	}

	/** Activates the next image window on the window list. */
	public static void putBehind() {

		if (IJ.debugMode)
			IJ.log("putBehind");
		if (imageList.size() < 1 || currentWindow == null)
			return;
		int index = imageList.indexOf(currentWindow);
		ImageWindow win = null;
		int count = 0;
		do {
			index--;
			if (index < 0)
				index = imageList.size() - 1;
			win = (ImageWindow) imageList.get(index);
			if (++count == imageList.size())
				return;
		} while (win instanceof HistogramWindow || win instanceof PlotWindow);
		if (win == null)
			return;
		ImagePlus imp = win.getImagePlus();
		if (imp != null)
			IJ.selectWindow(imp.getID());
	}

	/** Returns the temporary current image for this thread, or null. */
	public static ImagePlus getTempCurrentImage() {

		return (ImagePlus) tempImageTable.get(Thread.currentThread());
	}

	/**
	 * Returns the window (a Frame or a Dialog) with the specified title, or null if
	 * a window with that title is not found.
	 */
	public static Object getWindow(String title) {

		for (int i = 0; i < nonImageList.size(); i++) {
			Object win = nonImageList.get(i);
			/*
			 * Workaround for: Local variable name defined in an enclosing scope must be
			 * final or effectively final using an String array!
			 */
			String[] winTitle = new String[1];
			if (win instanceof Shell) {
				winTitle[0] = ((Shell) win).getText();
			} else if (win instanceof WindowSwt) {
				Display.getDefault().syncExec(() -> {

					Shell shell = ((WindowSwt) win).getShell();
					if (shell.isDisposed() == false)
						winTitle[0] = shell.getText();

				});
			} else if (win instanceof TextWindow) {
				/* getTitle is implemented in class TextWindow! */
				winTitle[0] = ((TextWindow) win).getTitle();
			}
			/* getTitle is implemented in class Editor! */
			else if (win instanceof Editor) {
				winTitle[0] = ((Editor) win).getTitle();
			}
			/*
			 * else { winTitle = win instanceof Frame ? ((Frame) win).getTitle() : ((Shell)
			 * win).getText(); }
			 */
			if (title.equals(winTitle[0]) && win instanceof Window)
				return (Window) win;
			else if (title.equals(winTitle[0]) && win instanceof Shell)
				return (Shell) win;
			else if (title.equals(winTitle[0]) && win instanceof WindowSwt)
				return (WindowSwt) win;
		}
		return getImageWindow(title);
	}

	/** Obsolete; replaced by getWindow(). */
	public static Frame getFrame(String title) {

		return null;
	}

	private static ImageWindow getImageWindow(String title) {

		int[] wList = getIDList();
		int len = wList != null ? wList.length : 0;
		for (int i = 0; i < len; i++) {
			ImagePlus imp = getImage(wList[i]);
			if (imp != null) {
				if (imp.getTitle().equals(title))
					return imp.getWindow();
			}
		}
		return null;
	}

	/** Activates a window selected from the Window menu. */
	public synchronized static void activateWindow(String menuItemLabel, org.eclipse.swt.widgets.MenuItem item) {

		for (int i = 0; i < nonImageList.size(); i++) {
			Object win = nonImageList.get(i);
			// String title = win instanceof Shell ? ((Shell) win).getText() : "No Shell
			// Title";
			String title;
			if (win instanceof Shell) {
				title = ((Shell) win).getText();
			} else if (win instanceof WindowSwt) {
				title = ((WindowSwt) win).getShell().getText();
			} else {
				title = "No Shell Title";
			}
			if (menuItemLabel.equals(title)) {
				if (win instanceof Frame)
					toFront((Frame) win);
				else if (win instanceof Shell) {
					toFront((Shell) win);
				} else if (win instanceof WindowSwt) {
					toFront((WindowSwt) win);
				} else
					((Dialog) win).toFront();
				((org.eclipse.swt.widgets.MenuItem) item).setSelection(false);
				if (IJ.recording() && !IJ.isMacro())
					Recorder.record("selectWindow", title);
				return;
			}
		}
		int lastSpace = menuItemLabel.lastIndexOf(' ');
		if (lastSpace > 0) // remove image size (e.g., " 90K")
			menuItemLabel = menuItemLabel.substring(0, lastSpace);
		String idString;
		String data = (String) item.getData("ActionCommand");
		if (data != null) {
			idString = data;
		} else {
			idString = item.getText();
		}
		// String idString = item.getActionCommand();
		int id = (int) Tools.parseDouble(idString, 0);
		ImagePlus imp = WindowManager.getImage(id);
		if (imp == null)
			return;
		ImageWindow win1 = imp.getWindow();
		if (win1 == null)
			return;
		setCurrentWindow(win1);
		toFront(win1);
		int index = imageList.indexOf(win1);
		int n = Menus.window.getItemCount();
		int start = Menus.WINDOW_MENU_ITEMS + Menus.windowMenuItems2;
		for (int j = start; j < n; j++) {
			org.eclipse.swt.widgets.MenuItem mi = Menus.window.getItem(j);
			if ((mi.getStyle() & SWT.CHECK) != 0)
				((org.eclipse.swt.widgets.MenuItem) mi).setSelection((j - start) == index);
		}
	}

	/** Repaints all open image windows. */
	public synchronized static void repaintImageWindows() {

		int[] list = getIDList();
		if (list == null)
			return;
		for (int i = 0; i < list.length; i++) {
			ImagePlus imp2 = getImage(list[i]);
			if (imp2 != null) {
				imp2.setTitle(imp2.getTitle()); // update "(G)" flag (global calibration)
				ImageWindow win = imp2.getWindow();
				if (win != null)
					win.repaint();
			}
		}
	}

	public static void showList() {

		for (int i = 0; i < imageList.size(); i++) {
			ImageWindow win = (ImageWindow) imageList.get(i);
			ImagePlus imp = win.getImagePlus();
			IJ.log(i + " " + imp.getTitle() + (win == currentWindow ? "*" : "") + " " + imp.getID());
		}
		for (int i = 0; i < activations.size(); i++) {
			ImageWindow win = (ImageWindow) activations.get(i);
			ImagePlus imp = win.getImagePlus();
			IJ.log(i + " " + imp.getTitle() + " " + imp.getID());
		}
		if (imageList.size() == 0)
			IJ.log("imageList is empty");
		if (activations.size() == 0)
			IJ.log("activations list is empty");
		IJ.log(" ");
	}

	public static void toFront(Object fram) {

		if (fram == null)
			return;
		if (fram instanceof Frame) {
			Frame frame = (Frame) fram;
			if (frame.getState() == Frame.ICONIFIED)
				frame.setState(Frame.NORMAL);
			frame.toFront();
		} else if (fram instanceof ImageWindow) {
			Display.getDefault().syncExec(() -> {

				ImageWindow imageWindow = (ImageWindow) fram;
				Shell shell = imageWindow.getShell();
				if (shell != null) {
					shell.forceActive();
				}

			});
		} else if (fram instanceof Shell) {
			Display.getDefault().syncExec(() -> {

				((Shell) fram).forceActive();

			});
		} else if (fram instanceof WindowSwt) {
			Display.getDefault().syncExec(() -> {

				((WindowSwt) fram).getShell().forceActive();

			});
		}
	}

	public static void toFront(Window window) {

		if (window == null)
			return;
		if (window instanceof Frame && ((Frame) window).getState() == Frame.ICONIFIED)
			((Frame) window).setState(Frame.NORMAL);
		window.toFront();
	}
}
