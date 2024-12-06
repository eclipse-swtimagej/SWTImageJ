package ij.plugin;

import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.ChannelSplitter;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

/**
 * This plugin implements the Image/Colors/Arrange Channels command, which
 * allows the user to change the order of channels.
 *
  * @author Norbert Vischer, 3-sep-2012
 */
public class ChannelArranger implements PlugIn, ModifyListener {
	private ThumbnailsCanvas thumbNails;
	private String patternString;
	private String allowedDigits;
	private org.eclipse.swt.widgets.Text orderField;
	private int nChannels;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		nChannels = imp.getNChannels();
		if (nChannels == 1) {
			IJ.error("Channel Arranger", "Image must have more than one channel");
			return;
		}
		if (nChannels > 9) {
			IJ.error("Channel Arranger", "This command does not work with more than 9 channels.");
			return;
		}
		patternString = "1234567890".substring(0, nChannels);
		allowedDigits = patternString;
		GenericDialog gd = new GenericDialog("Arrange Channels");
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				org.eclipse.swt.widgets.Composite panel = new org.eclipse.swt.widgets.Composite(gd.getShell(),
						SWT.NONE);
				panel.setLayout(new GridLayout(1, true));
				panel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

				thumbNails = new ThumbnailsCanvas(panel, imp);
				thumbNails.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				Point size = thumbNails.getSize();
				panel.setSize(size.x, size.y + 200);
				// panel.add(thumbNails);
				gd.addPanel(panel);

				// gd.setInsets(20, 20, 5);
				gd.addStringField("New channel order:", allowedDigits);
				Vector v = gd.getStringFields();
				orderField = (org.eclipse.swt.widgets.Text) v.elementAt(0);
				orderField.addModifyListener(ChannelArranger.this);
				gd.addHelp(IJ.URL2 + "/docs/menus/image.html#arrange");
				gd.setShellSize(new Point(size.x, size.y * 2));
			}
		});
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		String newOrder = gd.getNextString();
		int nChannels2 = newOrder.length();
		if (nChannels2 == 0)
			return;
		for (int i = 0; i < nChannels2; i++) {
			if (!Character.isDigit(newOrder.charAt(i))) {
				IJ.error("Channel Arranger", "Non-digit in new order string: \"" + newOrder + "\"");
				return;
			}
		}
		if (nChannels2 < nChannels) {
			String msg = "The number of channels will be reduced from " + nChannels + " to " + nChannels2 + ".";
			if (!IJ.showMessageWithCancel("Reduce Number of Channels?", msg))
				return;
		}
		org.eclipse.swt.graphics.Point location = imp.getWindow() != null ? imp.getWindow().getLocation() : null;
		int[] newOrder2 = new int[nChannels2];
		for (int i = 0; i < nChannels2; i++)
			newOrder2[i] = newOrder.charAt(i) - 48;
		/*Avoid a deadlock in the run method with SWT!*/
		AtomicReference<ImagePlus> imp2 = new AtomicReference<ImagePlus>();
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				imp2.set(ChannelArranger.run(imp, newOrder2));
			}
		});
		imp2.get().copyAttributes(imp);
		if (location != null)
			ImageWindow.setNextLocation(location);
		imp2.get().changes = true;
		imp2.get().show();
	}

	/**
	 * Changes the order of the channels in a hyperstack.
	 * 
	 * @param img      source hyperstack
	 * @param newOrder the new channel order
	 * @return a hyperstack with channels in the specified order
	 *         <p>
	 *         The following example opens the FluorescentCells sample image and
	 *         reverses the order of the channels.
	 * 
	 *         <pre>
	 *         ImagePlus img = IJ.openImage("http://imagej.net/ij/images/FluorescentCells.zip");
	 *         int[] order = { 3, 2, 1 };
	 *         ImagePlus img2 = ChannelArranger.run(img, order);
	 *         img2.setDisplayMode(IJ.COLOR);
	 *         img2.show();
	 *         </pre>
	 */
	public static ImagePlus run(ImagePlus img, int[] newOrder) {
		int channel = img.getChannel();
		int slice = img.getSlice();
		int frame = img.getFrame();
		ImagePlus[] channels = ChannelSplitter.split(img);
		int nChannels2 = newOrder.length;
		if (nChannels2 > channels.length)
			nChannels2 = channels.length;
		ImagePlus[] channels2 = new ImagePlus[nChannels2];
		for (int i = 0; i < nChannels2; i++) {
			int index = newOrder[i] - 1;
			if (index < 0 || index >= channels.length)
				throw new IllegalArgumentException("value out of range:" + newOrder[i]);
			channels2[i] = channels[index];
		}
		ImagePlus img2 = null;
		if (nChannels2 == 1)
			img2 = channels2[0];
		else
			img2 = RGBStackMerge.mergeChannels(channels2, false);
		int mode2 = IJ.COLOR;
		if (img.isComposite())
			mode2 = ((CompositeImage) img).getMode();
		if (img2.isComposite())
			((CompositeImage) img2).setMode(mode2);
		if (channel <= nChannels2) {
			int channel2 = newOrder[channel - 1];
			img2.setPosition(channel2, slice, frame);
		}
		Overlay overlay = img.getOverlay();
		if (overlay != null) {
			for (int i = 0; i < overlay.size(); i++) {
				Roi roi = overlay.get(i);
				int c = roi.getCPosition();
				int z = roi.getZPosition();
				int t = roi.getTPosition();
				if (c >= 1 && c <= nChannels2)
					roi.setPosition(newOrder[c - 1], z, t);
			}
			img2.setOverlay(overlay);
		}
		img2.setProperty("Info", img.getProperty("Info"));
		img.changes = false;
		img.close();
		return img2;
	}

	@Override
	public void modifyText(ModifyEvent e) {
		textValueChanged(e);

	}

	public void textValueChanged(ModifyEvent e) {
		org.eclipse.swt.widgets.Text tf = (org.eclipse.swt.widgets.Text) e.getSource();
		String typed = tf.getText();

		if (typed.length() > nChannels) {
			orderField.setText(patternString);
			return;
		}
		for (int jj = 0; jj < typed.length(); jj++) {
			String digit = typed.substring(jj, jj + 1);
			int found = typed.indexOf(digit, jj + 1);
			if (found != -1) {
				orderField.setText(patternString);
				return;
			}
			if (allowedDigits.indexOf(digit) == -1) {
				orderField.setText(patternString);
				return;
			}
		}
		patternString = typed;
		thumbNails.setSequence(patternString);
		thumbNails.redraw();
		// orderField.setText(patternString);
	}

}

class ThumbnailsCanvas extends Canvas implements MouseListener, MouseMoveListener, SelectionListener, PaintListener {

	// protected static Cursor handCursor = new Cursor(Cursor.HAND_CURSOR);
	// protected static Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
	protected org.eclipse.swt.graphics.Cursor defaultCursor = new org.eclipse.swt.graphics.Cursor(Display.getDefault(),
			SWT.CURSOR_ARROW);
	protected org.eclipse.swt.graphics.Cursor handCursor = new org.eclipse.swt.graphics.Cursor(Display.getDefault(),
			SWT.CURSOR_HAND);

	public Image os;
	public org.eclipse.swt.graphics.Image bImg;
	GC osg;
	CompositeImage cImp;
	int iconSize = 100;
	int iconWidth = iconSize, iconHeight = iconSize;
	int dx = 0, dy = 0;
	int separatorY = 6;
	int marginY = 10;
	int marginX = 44;
	int nChannels;
	int channelUnderCursor = 0;
	String seq = "1234567890";
	int currentChannel, currentSlice, currentFrame;
	private Composite panel;

	public ThumbnailsCanvas(Composite panel, ImagePlus imp) {
		super(panel, SWT.DOUBLE_BUFFERED);
		this.panel = panel;
		if (!imp.isComposite())
			return;
		cImp = (CompositeImage) imp;
		addMouseListener(this);
		addMouseMoveListener(this);
		addPaintListener(this);
		currentChannel = cImp.getChannel();
		currentSlice = cImp.getSlice();
		currentFrame = cImp.getFrame();
		channelUnderCursor = currentChannel;
		int ww = cImp.getWidth();
		int hh = cImp.getHeight();
		if (ww > hh) {
			iconHeight = iconWidth * hh / ww;
			dy = (iconWidth - iconHeight) / 2;
		}
		if (ww < hh) {
			iconWidth = iconHeight * ww / hh;
			dx = (iconHeight - iconWidth) / 2;
		}
		nChannels = cImp.getNChannels();
		seq = seq.substring(0, nChannels);
		setSize((nChannels + 1) * iconSize, 2 * iconSize + 30);

	}

	/*
	 * public void update(Graphics g) { paint(g); }
	 */

	/* Only for compatibility! */
	public void repaint() {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				redraw();
			}
		});
	}

	public void setSequence(String seq) {
		this.seq = seq;
	}

	public int[] getStackPos() {
		return new int[] { currentChannel, currentSlice, currentFrame };
	}

	@Override
	public void paintControl(PaintEvent e) {
		GC gc = e.gc;
		paint(gc);
		//gc.dispose();

	}

	public void paint(GC gc) {
		if (gc == null)
			return;
		int savedMode = cImp.getMode();
		if (savedMode == IJ.COMPOSITE)
			cImp.setMode(IJ.COLOR);
		ImageProcessor ipSmall;
		// os = createImage((nChannels + 1) * iconSize, 2 * iconSize + 30);
		os = new org.eclipse.swt.graphics.Image(Display.getDefault(), (nChannels + 1) * iconSize, 2 * iconSize + 30);
		osg = new GC(os);
		// osg.setFont(IJ.font12Swt);
		int y1;
		for (int chn = 1; chn <= nChannels; chn++) {
			cImp.setPositionWithoutUpdate(chn, currentSlice, currentFrame);
			cImp.updateImage();
			ipSmall = cImp.getProcessor().resize(iconWidth, iconHeight, true);
			bImg = ipSmall.createSwtImage();
			int index = chn - 1;
			y1 = 0;
			for (int row = 0; row < 2; row++) {
				if (index >= 0) {
					int xx = index * iconSize + marginX;
					osg.drawImage(bImg, xx + dx, y1 + dy);
					osg.setForeground(ij.swt.Color.lightGray);
					osg.drawRectangle(xx, y1, iconSize, iconSize);
					osg.fillRoundRectangle(xx + iconSize / 2 - 4, y1 + iconSize - 22, 18, 18, 6, 6);
					osg.setForeground(ij.swt.Color.black);
					osg.drawRoundRectangle(xx + iconSize / 2 - 4, y1 + iconSize - 22, 18, 18, 6, 6);
					osg.drawString("" + chn, xx + 52, y1 + iconSize - 22);
					index = seq.indexOf("" + chn, 0);
					if (seq.indexOf("" + chn, index) == -1) {// char must not occur twice
						index = -1;
					}

				}
				y1 += (iconSize + separatorY);
			}
			bImg.dispose();
		}
		y1 = marginY + iconSize - 7;
		osg.drawString("Old:", 6, y1);
		y1 += (iconSize + separatorY);
		osg.drawString("New:", 6, y1);
		// osg.dispose();
		if (os == null)
			return;
		gc.drawImage(os, 0, 0);
		if (savedMode == IJ.COMPOSITE)
			cImp.setMode(savedMode);
		cImp.setPosition(currentChannel, currentSlice, currentFrame);
		cImp.updateImage();

		os.dispose();
	}

	protected void handlePopupMenu(MouseEvent e) {
		int x = e.x;
		int y = e.y;
		Menu popup = new org.eclipse.swt.widgets.Menu(panel.getShell(), SWT.POP_UP);
		String[] colors = "Grays,-,Red,Green,Blue,Yellow,Magenta,Cyan,-,Fire,Ice,Spectrum,3-3-2 RGB,Red/Green"
				.split(",");
		for (int jj = 0; jj < colors.length; jj++) {
			if (colors[jj].equals("-"))
				new org.eclipse.swt.widgets.MenuItem(popup, SWT.SEPARATOR);
			else {
				MenuItem mi = new MenuItem(popup, SWT.PUSH);
				mi.setText(colors[jj]);
				// popup.add(mi);
				mi.addSelectionListener(this);
			}
		}
		// add(popup);
		if (IJ.isMacOSX())
			IJ.wait(10);
		// popup.show(this, x, y);
		popup.setVisible(true);
		setCursor(defaultCursor);
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		actionPerformed(e);

	}

	public void actionPerformed(SelectionEvent e) {
		org.eclipse.swt.widgets.MenuItem it = (org.eclipse.swt.widgets.MenuItem) e.widget;
		// String cmd = e.getActionCommand();
		String cmd = it.getText();
		cImp.setPosition(currentChannel, currentSlice, currentFrame);
		CompositeImage cImp = (CompositeImage) this.cImp;
		IJ.run(cmd);
		repaint();
		setCursor(defaultCursor);
	}

	@Override
	public void mouseMove(MouseEvent e) {
		int x = e.x - marginX;
		int y = e.y - marginY;
		if (x < 0 || x > nChannels * iconSize || y < 0 || y > iconSize * 2 + separatorY) {
			setCursor(defaultCursor);
			channelUnderCursor = 0;
		} else {
			int chn = x / iconSize + 1;
			if (y > iconSize) {
				if (chn <= seq.length()) {
					String digit = seq.substring(chn - 1, chn);
					chn = "1234567890".indexOf(digit) + 1;
				} else {
					chn = 0;
				}
			}
			if (y > 2 * iconSize + separatorY) {
				chn = 0;
			}
			channelUnderCursor = chn;
		}
		if (channelUnderCursor > 0)
			setCursor(handCursor);
		else
			setCursor(defaultCursor);
	}

	@Override
	public void mouseDoubleClick(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseDown(MouseEvent e) {
		if (channelUnderCursor > 0) {
			currentChannel = channelUnderCursor;
			handlePopupMenu(e);
			repaint();
		}

	}

	@Override
	public void mouseUp(MouseEvent e) {
		mouseMove(e);

	}

	public void mouseEntered(MouseEvent e) {
	}

	/*
	 * public void mousePressed(MouseEvent e) { if (channelUnderCursor > 0) {
	 * currentChannel = channelUnderCursor; handlePopupMenu(e); repaint(); } }
	 */
	/*
	 * public void mouseReleased(MouseEvent e) { mouseMoved(e); }
	 */

	@Override
	public void widgetDefaultSelected(SelectionEvent arg0) {
		// TODO Auto-generated method stub

	}

}
