package ij.plugin.tool;
import org.eclipse.swt.events.MouseEvent;
import org.jfree.swt.SWTUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Arrow;
import ij.gui.ImageCanvas;
import ij.gui.Roi;

public class ArrowTool extends PlugInTool {
	Roi arrow;
	private boolean drag;

	public void mouseDown(ImagePlus imp, MouseEvent e) {
		ImageCanvas ic = imp.getCanvas();
		ic.calculateAspectRatio();
		int sx = (int)(e.x*ic.aspectRatioX);
		int sy = (int)(e.y*ic.aspectRatioY);
		//int sx = e.getX();
		//int sy = e.getY();
		int ox = ic.offScreenX(sx);
		int oy = ic.offScreenY(sy);
		Roi roi = imp.getRoi();
		int handle = roi!=null?roi.isHandle(ox, oy):-1;
		if (!(roi!=null && (roi instanceof Arrow) && (handle>=0||roi.contains(ox,oy)))) {
			arrow = new Arrow(sx, sy, imp);
			if (imp.okToDeleteRoi())
				imp.setRoi(arrow, false);
			//e.consume();
		}
	}
   /*This is a created Event for SWT!*/
	public void mouseDragged(ImagePlus imp, MouseEvent e) {
		ImageCanvas ic = imp.getCanvas();
		ic.calculateAspectRatio();
		int sx = (int)(e.x*ic.aspectRatioX);
		int sy = (int)(e.y*ic.aspectRatioY);
		//int sx = e.getX();
		//int sy = e.getY();
		int ox = ic.offScreenX(sx);
		int oy = ic.offScreenY(sy);
		Roi roi = imp.getRoi();
		if (roi!=null && (roi instanceof Arrow) && roi.contains(ox,oy))
			roi.mouseDragged(SWTUtils.toAwtMouseEvent(e));
		else if (arrow!=null)
			arrow.mouseDragged(SWTUtils.toAwtMouseEvent(e));
		//e.consume();
	}
	
	public void mouseUp(ImagePlus imp, MouseEvent e) {
		
		ImageCanvas ic = imp.getCanvas();
		ic.calculateAspectRatio();
		int sx = (int)(e.x*ic.aspectRatioX);
		int sy = (int)(e.y*ic.aspectRatioY);
		//int sx = e.getX();
		//int sy = e.getY();
		int ox = ic.offScreenX(sx);
		int oy = ic.offScreenY(sy);
		Roi roi = imp.getRoi();
		if (arrow!=null && !(roi!=null && (roi instanceof Arrow) && roi.contains(ox,oy))) {
			arrow.mouseReleased(SWTUtils.toAwtMouseEvent(e));
			//e.consume();
		}
		
	}

	public void showOptionsDialog() {
		IJ.doCommand("Arrow Tool...");
	}

	public String getToolIcon() {
		return "C037L0ff0L74f0Lb8f0L74b8";
	}

	public String getToolName() {
		return "Arrow Tool";
	}
	
}


