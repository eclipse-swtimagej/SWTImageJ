package ij.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;

public class Color {
	//public static Color darkGray=new org.eclipse.swt.graphics.Color (Display.getCurrent (), color.getRed(), color.getGreen(), color.getBlue());	
	public static org.eclipse.swt.graphics.Color backGround = Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
	public static org.eclipse.swt.graphics.Color foreGround = Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
	public static org.eclipse.swt.graphics.Color darkGray=Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY);
	public static org.eclipse.swt.graphics.Color lightGray=new org.eclipse.swt.graphics.Color (Display.getCurrent (),204, 204, 204);
	public static org.eclipse.swt.graphics.Color gray=Display.getDefault().getSystemColor(SWT.COLOR_GRAY);
	public static org.eclipse.swt.graphics.Color black=Display.getDefault().getSystemColor(SWT.COLOR_BLACK);
	public static org.eclipse.swt.graphics.Color red=Display.getDefault().getSystemColor(SWT.COLOR_RED);
	public static org.eclipse.swt.graphics.Color white=Display.getDefault().getSystemColor(SWT.COLOR_WHITE);
	public static org.eclipse.swt.graphics.Color blue=Display.getDefault().getSystemColor(SWT.COLOR_BLUE);
	public static org.eclipse.swt.graphics.Color green=Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
}
