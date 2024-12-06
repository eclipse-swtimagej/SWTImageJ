package ij.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;

public class Font {
	public static org.eclipse.swt.graphics.Font font12 = new org.eclipse.swt.graphics.Font(Display.getDefault(),
			new FontData("Courier New", 12, SWT.NORMAL));
	
	public static org.eclipse.swt.graphics.Font font = new org.eclipse.swt.graphics.Font(Display.getDefault(),
			new FontData("SansSerif", 9, SWT.NORMAL));
	

}
