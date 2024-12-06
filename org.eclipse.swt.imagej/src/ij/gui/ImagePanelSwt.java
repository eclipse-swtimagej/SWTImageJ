package ij.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import ij.*;

/** This class is used by GenericDialog to add images to dialogs. */
public class ImagePanelSwt extends org.eclipse.swt.widgets.Canvas implements PaintListener {
	private ImagePlus img;
	private int width, height;

	public ImagePanelSwt(org.eclipse.swt.widgets.Composite parent, ImagePlus img) {
		super(parent, SWT.TRANSPARENT | SWT.DOUBLE_BUFFERED);
		this.img = img;
		width = img.getWidth();
		height = img.getHeight();
		addPaintListener(this);

	}
	@Override
	public Point getSize() {
		return new Point(img.getWidth(), img.getHeight());
	}

	@Override
	public void paintControl(PaintEvent e) {
		Image createSwtImage = img.getProcessor().createSwtImage();
		e.gc.drawImage(createSwtImage, 0, 0);
		createSwtImage.dispose();
	}

}
