package ij.swt;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;

/**
 * A utility class for the ImageJ plugin.
 * 
 * @author M. Austenfeld
 *
 */
public class Util {

	/**
	 * A method to detect the current OS.
	 * 
	 * @return the OS name as a string
	 */
	public static String getOS() {
		String OS = null;
		String osname = System.getProperty("os.name");
		if (osname.startsWith("Windows")) {
			OS = "Windows";
		} else if (osname.equals("Linux")) {
			OS = "Linux";
		} else if (osname.startsWith("Mac")) {
			OS = "Mac";
		}
		return OS;
	}

	

	/**
	 * A method to get the dpi of the display.
	 * 
	 * @return the dpi as type Point.
	 */
	public static Point getDpi() {
		Display dis = getDisplay();
		return dis.getDPI();
	}

	/**
	 * A method to return the primary monitor zoom.
	 * 
	 * @return the zoom value as integer.
	 */
	public static int getZoom() {
		Display dis = getDisplay();
		Monitor primary = dis.getPrimaryMonitor();
		return primary.getZoom();
	}

	/**
	 * A method to return the scale factor (1.0, 2.0).
	 * 
	 * @return the scale factor as type double
	 */
	public static double getScale() {

		int deviceZoom = DPIUtil.getDeviceZoom();
		double scale = deviceZoom / 100.0;
		return scale;

	}

	/**
	 * Returns a default display.
	 * 
	 * @return a display
	 */
	public static Display getDisplay() {
		Display display = Display.getCurrent();
		// may be null if outside the UI thread
		if (display == null)
			display = Display.getDefault();
		return display;
	}

	

	
	// The source for the following method from:
	// https://stackoverflow.com/questions/20767708/how-do-you-detect-a-retina-display-in-java#20767802
	public static boolean isMacRetinaDisplay() {
		if (getZoom() == 200) {
			return true;
		}
		return false;
	}

	/**
	


	//Method source from: https://github.com/archimatetool/archi/

	/**
	 * Compare two version numbers with the format 1, 1.1, or 1.1.1
	 * 
	 * From
	 * http://stackoverflow.com/questions/6701948/efficient-way-to-compare-version-strings-in-java
	 * 
	 * @param newer The version string considered to be newer
	 * @param older The version string considered to be older
	 * @return -1 if newer < older <br/>
	 *         0 if newer == older <br/>
	 *         1 if newer > older
	 */
	public static int compareVersionNumbers(String newer, String older) {
		return Integer.compare(versionNumberAsInt(newer), versionNumberAsInt(older));
	}

	/**
	 * Convert a version number to an integer
	 * 
	 * @param version
	 * @return integer
	 */

	//Method source from: https://github.com/archimatetool/archi/
	public static int versionNumberAsInt(String version) {
		String[] vals = version.split("\\.");

		if (vals.length == 1) {
			return Integer.parseInt(vals[0]);
		}
		if (vals.length == 2) {
			return (Integer.parseInt(vals[0]) << 16) + (Integer.parseInt(vals[1]) << 8);
		}
		if (vals.length == 3) {
			return (Integer.parseInt(vals[0]) << 16) + (Integer.parseInt(vals[1]) << 8) + Integer.parseInt(vals[2]);
		}

		return 0;
	}

	//Method source from: https://github.com/archimatetool/archi/

	/**
	 * Compare given version to current OS version and see if the current OS version
	 * is greater than the given version
	 * 
	 * @param version The version string to compare to system OS version
	 * @return -1 if newer < older <br/>
	 *         0 if newer == older <br/>
	 *         1 if newer > older
	 */
	public static int compareOSVersion(String version) {
		String current = System.getProperty("os.version"); //$NON-NLS-1$
		return compareVersionNumbers(current, version);
	}

}
