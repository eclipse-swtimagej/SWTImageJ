package ij.text;

import java.awt.Color;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

class TextCanvas extends org.eclipse.swt.widgets.Canvas implements PaintListener {

	TextPanel tp;
	org.eclipse.swt.graphics.Font fFont;
	org.eclipse.swt.graphics.FontMetrics fMetrics;
	GC gImage;
	org.eclipse.swt.graphics.Image iImage;
	boolean antialiased;
	TextCanvas(TextPanel tp) {
		super(tp.getShell(), SWT.DOUBLE_BUFFERED);
		this.tp = tp;
		addMouseListener(tp);
		addMouseMoveListener(tp);
		addKeyListener(tp);
		addMouseWheelListener(tp);
		addMouseTrackListener(tp);
		addPaintListener(this);
	}

	public void setBounds(int x, int y, int width, int height) {
		super.setBounds(x, y, width, height);
		tp.adjustVScroll();
		tp.adjustHScroll();
		iImage = null;
	}

	/*public void update(Graphics g) {
		paint(g);
	}*/

	@Override
	public void paintControl(PaintEvent evt) {
		GC gc = evt.gc;
		paint(gc);
		//gc.dispose();
	}
    /*Only for compatibility!*/
	public void repaint() {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				redraw();
			}
		});
	}

	public void paint(GC gc) {
		if (tp == null || gc == null)
			return;
		Rectangle d = getBounds();
		int iWidth = d.width;
		int iHeight = d.height;

		if (iWidth <= 0 || iHeight <= 0)
			return;
		gc.setForeground(ij.swt.Color.foreGround);
		// if (iImage==null)
		makeImage(iWidth, iHeight);
		if (tp.iRowHeight == 0 || (tp.iColWidth.length > 0 && tp.iColWidth[0] == 0 && tp.iRowCount > 0)) {
			tp.iRowHeight = fMetrics.getHeight() + 2;
			for (int i = 0; i < tp.iColCount; i++)
				calcAutoWidth(i);
			tp.adjustHScroll();
			tp.adjustVScroll();
		}
		if(Display.isSystemDarkTheme()) {
			gImage.setBackground(ij.swt.Color.backGround);
		}
		gImage.setBackground(ij.swt.Color.backGround);
		gImage.fillRectangle(0, 0, iWidth, iHeight);
		if (tp.headings)
			drawColumnLabels(iWidth);
		int y = tp.iRowHeight + 1 - tp.iY;
		int j = 0;
		while (y < tp.iRowHeight + 1) {
			j++;
			y += tp.iRowHeight;
		}
		tp.iFirstRow = j;
		y = tp.iRowHeight + 1;
		for (; y < iHeight && j < tp.iRowCount; j++, y += tp.iRowHeight) {
			int x = -tp.iX;
			for (int i = 0; i < tp.iColCount; i++) {
				if (i>=tp.iColWidth.length) break;
				int w = tp.iColWidth[i];
				org.eclipse.swt.graphics.Color b = ij.swt.Color.white, t = ij.swt.Color.black;
				if (j >= tp.selStart && j <= tp.selEnd) {
					int w2 = w;
					if (tp.iColCount == 1)
						w2 = iWidth;
					b = ij.swt.Color.black;
					t = ij.swt.Color.white;
					gImage.setBackground(ij.swt.Color.backGround);
					gImage.fillRectangle(x, y, w2 - 1, tp.iRowHeight);
				}
				gImage.setBackground(ij.swt.Color.backGround);
				gImage.setForeground(ij.swt.Color.foreGround);
				char[] chars = tp.getChars(i,j);
				if (chars != null)
					gImage.drawString(String.valueOf(chars), x + 2, y + tp.iRowHeight - fMetrics.getHeight());
				x += w;
			}
		}
		if (iImage != null)
			gc.drawImage(iImage, 0, 0);
		gImage.dispose();
		iImage.dispose();
		//gc.dispose();
	}

	void makeImage(int iWidth, int iHeight) {
		/*Changed for SWT. We have no awt canvas here. So we create a dummy image!*/
		iImage = new org.eclipse.swt.graphics.Image(Display.getDefault(), iWidth, iHeight);
		// iImage=new BufferedImage(iWidth, iHeight,BufferedImage.TYPE_INT_RGB);
		// iImage=new java.awt.Canvas().createImage(iWidth, iHeight);
		if (gImage != null)
			gImage.dispose();
		gImage = new GC(iImage);
		// gImage=iImage.getGraphics();
		FontData fd = fFont.getFontData()[0];
		gImage.setFont(fFont);
		if (antialiased) {
			gImage.setAntialias(SWT.ON);
		} else {
			gImage.setAntialias(SWT.DEFAULT);
		}

		if (fMetrics==null)
			fMetrics = gImage.getFontMetrics(); 
	}

	void drawColumnLabels(int iWidth) {
		gImage.setForeground(ij.swt.Color.darkGray);
		gImage.drawLine(0, tp.iRowHeight, iWidth, tp.iRowHeight);
		int x = -tp.iX;
		for (int i = 0; i < tp.iColCount; i++) {
			int w = tp.iColWidth[i];
			gImage.setBackground(ij.swt.Color.darkGray);
			gImage.fillRectangle(x + 1, 0, w, tp.iRowHeight);
			gImage.setForeground(ij.swt.Color.foreGround);
			if (i<tp.sColHead.length && tp.sColHead[i]!=null)
				gImage.drawString(tp.sColHead[i], x + 2, tp.iRowHeight - fMetrics.getHeight());
			if (tp.iColCount > 0) {
				gImage.setForeground(ij.swt.Color.lightGray);
				gImage.drawLine(x + w - 1, 0, x + w - 1, tp.iRowHeight - 1);
				gImage.setBackground(ij.swt.Color.backGround);
				gImage.drawLine(x + w, 0, x + w, tp.iRowHeight - 1);
			}
			x += w;
		}
		gImage.setForeground(ij.swt.Color.darkGray);
		gImage.fillRectangle(0, 0, 1, tp.iRowHeight);
		gImage.fillRectangle(x + 1, 0, iWidth - x, tp.iRowHeight);
		// gImage.drawLine(0,0,0,iRowHeight-1);
		gImage.setForeground(ij.swt.Color.lightGray);
		gImage.drawLine(0, 0, iWidth, 0);
	}

	void calcAutoWidth(int column) {
		if (tp.sColHead == null || column >= tp.iColWidth.length || gImage == null)
			return;
		if (fMetrics == null)
			fMetrics = gImage.getFontMetrics();
		int w = 15;
		int maxRows = 20;
		if (column == 0 && tp.sColHead[0].equals(" "))
			w += 5;
		else {
			char[] chars = tp.sColHead[column].toCharArray();
			w = (int) Math.max(w, fMetrics.getAverageCharacterWidth() * chars.length);
		}
		int rowCount = Math.min(tp.iRowCount, maxRows);
		for (int row = 0; row < rowCount; row++) {
			char[] chars = tp.getChars(column,row);
			if (chars != null)
				w = (int) Math.max(w, fMetrics.getAverageCharacterWidth() * chars.length);
		}
		// System.out.println("calcAutoWidth: "+column+" "+tp.iRowCount);
		char[] chars = tp.iRowCount>0?tp.getChars(column, tp.iRowCount-1):null;
		if (chars != null)
			w = (int) Math.max(w, fMetrics.getAverageCharacterWidth() * chars.length);
		if (column < tp.iColWidth.length)
			tp.iColWidth[column] = w + 15;
	}

}
