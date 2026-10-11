package ij.swt;

import org.eclipse.swt.widgets.Composite;

import ij.ImagePlus;
import ij.gui.ImageWindow;

/**
 * Optional callbacks for applications that embed SWTImageJ (e.g., an Eclipse
 * RCP application). A host is registered with {@link ij.IJ#setHost(ImageJHost)}.
 * All methods have a default implementation so that a host only overrides what
 * it needs. The methods are called in the SWT UI thread unless noted otherwise.
 */
public interface ImageJHost {

	/**
	 * Called when an image window is created for an image without an editor
	 * parent (see {@link ImagePlus#setEditorParent(Composite)}).
	 *
	 * @return a parent composite (GridLayout recommended) to embed the image
	 *         window or null to open the image in a separate shell.
	 */
	default Composite getImageParent(ImagePlus imp) {

		return null;
	}

	/** Called when the title of an embedded image window changes. */
	default void titleChanged(ImageWindow win, String title) {

	}

	/**
	 * Called when an embedded image window should be brought to the front (e.g.,
	 * the host should select the tab or view containing the image).
	 */
	default void toFront(ImageWindow win) {

	}

	/**
	 * Called with each status message of {@link ij.IJ#showStatus(String)}. May be
	 * called from any thread.
	 */
	default void showStatus(String message) {

	}
}
