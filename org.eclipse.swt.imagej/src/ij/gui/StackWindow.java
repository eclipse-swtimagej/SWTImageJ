package ij.gui;

import java.awt.Point;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Slider;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.plugin.frame.SyncWindows;

/**
 * This class is an extended ImageWindow that displays stacks and hyperstacks.
 * In the SWT implementation we use composites with a Button instead of the
 * Scrollbar with Label!
 */
public class StackWindow extends ImageWindow
		implements Runnable, AdjustmentListener, org.eclipse.swt.events.MouseWheelListener {

	protected Slider sliceSelector; // for backward compatibity with Image5D
	protected Slider cSelector, zSelector, tSelector;
	protected Thread thread;
	protected volatile boolean done;
	protected volatile int slice;
	protected Slider animationSelector;
	boolean hyperStack;
	int nChannels = 1, nSlices = 1, nFrames = 1;
	int c = 1, z = 1, t = 1;
	Composite comp;
	private Button button;
	private Composite compositeCChannels;
	private Composite compositeZSlices;
	private Composite compositeTFrames;
	private Composite compositeSelector;

	public StackWindow(ImagePlus imp) {

		this(imp, null);
	}

	public StackWindow(ImagePlus imp, ImageCanvas ic) {

		super(imp, ic);
		ic = imp.getCanvas();
		comp = super.parentComposite.getParent();
		addScrollbars(imp);
		comp.layout();
		comp.addMouseWheelListener(this);
		if (sliceSelector == null && this.getClass().getName().indexOf("Image5D") != -1) {
			compositeSelector = new Composite(comp, SWT.NONE);
			compositeSelector.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1));
			GridLayout gl_composite = new GridLayout(2, false);
			gl_composite.verticalSpacing = 0;
			gl_composite.horizontalSpacing = 0;
			gl_composite.marginHeight = 0;
			gl_composite.marginWidth = 0;
			compositeSelector.setLayout(gl_composite);
			Button button = new Button(compositeSelector, SWT.NONE);
			button.setText(">");
			button.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {

				}
			});
			sliceSelector = new Slider(compositeSelector, SWT.NONE); // prevents Image5D from crashing
			sliceSelector.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		}
		if (ic != null)
			ic.setMaxBounds();
		if (IJ.isMacro() && !isVisible()) // 'super' may have called show()
			imp.setDeactivated(); // prepare for waitTillActivated (imp may have been activated before)
		// show();
		if (IJ.isMacro())
			imp.waitTillActivated();
		int previousSlice = imp.getCurrentSlice();
		if (previousSlice > 1 && previousSlice <= imp.getStackSize())
			imp.setSlice(previousSlice);
		else
			imp.setSlice(1);
		thread = new Thread(this, "zSelector");
		thread.start();
		if (shell != null) {
			shell.setText(imp.getTitle());
			shell.setSize(600, 600);
			shell.open();
			if (ic.isDisposed() == false) {
				ic.fitToWindow();
				shell.layout();
			}
		}
		/*
		 * while (!shell.isDisposed()) { if (!display.readAndDispatch()) {
		 * display.sleep(); } } display.dispose();
		 */
	}

	void addScrollbars(ImagePlus imp) {

		ImageStack s = imp.getStack();
		int stackSize = s.getSize();
		int sliderHeight = 0;
		nSlices = stackSize;
		hyperStack = imp.getOpenAsHyperStack();
		// imp.setOpenAsHyperStack(false);
		int[] dim = imp.getDimensions();
		int nDimensions = 2 + (dim[2] > 1 ? 1 : 0) + (dim[3] > 1 ? 1 : 0) + (dim[4] > 1 ? 1 : 0);
		if (nDimensions <= 3 && dim[2] != nSlices)
			hyperStack = false;
		if (hyperStack) {
			nChannels = dim[2];
			nSlices = dim[3];
			nFrames = dim[4];
		}
		if (nSlices == stackSize)
			hyperStack = false;
		if (nChannels * nSlices * nFrames != stackSize)
			hyperStack = false;
		if (cSelector != null || zSelector != null || tSelector != null)
			removeScrollbars();
		ImageJ ij = IJ.getInstance();
		// IJ.log("StackWindow: "+hyperStack+" "+nChannels+" "+nSlices+" "+nFrames+"
		// "+imp);
		if (nChannels > 1) {
			// cSelector = new Slider(comp, SWT.HORIZONTAL);
			compositeCChannels = new Composite(comp, SWT.NONE);
			compositeCChannels.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1));
			GridLayout gl_composite = new GridLayout(2, false);
			gl_composite.verticalSpacing = 0;
			gl_composite.horizontalSpacing = 0;
			gl_composite.marginHeight = 0;
			gl_composite.marginWidth = 0;
			compositeCChannels.setLayout(gl_composite);
			button = new Button(compositeCChannels, SWT.NONE);
			button.setText("c");
			cSelector = new Slider(compositeCChannels, SWT.NONE);
			cSelector.setThumb(1);
			cSelector.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			cSelector.setMinimum(1);
			cSelector.setMaximum(nChannels + 1);
			int blockIncrement = nFrames / 10;
			if (blockIncrement < 1)
				blockIncrement = 1;
			cSelector.setIncrement(1);
			cSelector.setPageIncrement(blockIncrement);
			cSelector.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {

					adjustmentValueChanged(e);
				}
			});
			// add(cSelector);
			// sliderHeight += cSelector.getPreferredSize().height + ImageWindow.VGAP;
			/*
			 * if (ij!=null) cSelector.addKeyListener(ij);
			 * cSelector.addAdjustmentListener(this); cSelector.setFocusable(false); //
			 * prevents scroll bar from blinking on Windows cSelector.setUnitIncrement(1);
			 * cSelector.setBlockIncrement(1);
			 */
		}
		if (nSlices > 1) {
			char label = nChannels > 1 || nFrames > 1 ? 'z' : 't';
			if (stackSize == dim[2] && imp.isComposite())
				label = 'c';
			compositeZSlices = new Composite(comp, SWT.NONE);
			compositeZSlices.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1));
			GridLayout gl_composite = new GridLayout(2, false);
			gl_composite.verticalSpacing = 0;
			gl_composite.horizontalSpacing = 0;
			gl_composite.marginHeight = 0;
			gl_composite.marginWidth = 0;
			compositeZSlices.setLayout(gl_composite);
			Button button = new Button(compositeZSlices, SWT.NONE);
			zSelector = new Slider(compositeZSlices, SWT.NONE);
			zSelector.setThumb(1);
			zSelector.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			zSelector.setMinimum(1);
			zSelector.setMaximum(nSlices + 1);
			zSelector.setIncrement(1);
			zSelector.setPageIncrement(1);
			zSelector.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {

					adjustmentValueChanged(e);
				}
			});
			sliceSelector = zSelector;
			button.setText("" + label);
			if (label == 't') {
				animationSelector = zSelector;
				button.setText(">");
				// button.setImage(null);
			}
			// if (label == 't') {
			button.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {

					Display.getDefault().syncExec(() -> {

						if ((e.stateMask & SWT.ALT) != 0) {
							IJ.doCommand("Animation Options...");
							return;
						}
						IJ.doCommand("Start Animation [\\]");
						if (getAnimate()) {
							button.setText(">");
						} else {
							button.setText("||");
						}

					});
				}
			});
			// }
			// add(zSelector);
			// sliderHeight += zSelector.getPreferredSize().height + ImageWindow.VGAP;
			/*
			 * if (ij!=null) zSelector.addKeyListener(ij);
			 * zSelector.addAdjustmentListener(this); zSelector.setFocusable(false); int
			 * blockIncrement = nSlices/10; if (blockIncrement<1) blockIncrement = 1;
			 * zSelector.setUnitIncrement(1); zSelector.setBlockIncrement(blockIncrement);
			 * sliceSelector = zSelector.bar;
			 */
		}
		if (nFrames > 1) {
			compositeTFrames = new Composite(comp, SWT.NONE);
			compositeTFrames.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1));
			GridLayout gl_composite = new GridLayout(2, false);
			gl_composite.verticalSpacing = 0;
			gl_composite.horizontalSpacing = 0;
			gl_composite.marginHeight = 0;
			gl_composite.marginWidth = 0;
			compositeTFrames.setLayout(gl_composite);
			Button button = new Button(compositeTFrames, SWT.NONE);
			button.setText("t");
			button.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {

					if ((e.stateMask & SWT.ALT) != 0) {
						IJ.doCommand("Animation Options...");
						return;
					}
					IJ.doCommand("Start Animation [\\]");
					if (getAnimate()) {
						button.setText(">");
					} else {
						button.setText("||");
					}
				}
			});
			animationSelector = tSelector = new Slider(compositeTFrames, SWT.NONE);
			tSelector.setThumb(1);
			tSelector.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			tSelector.setMinimum(1);
			tSelector.setMaximum(nFrames + 1);
			int blockIncrement = nFrames / 10;
			if (blockIncrement < 1)
				blockIncrement = 1;
			tSelector.setIncrement(blockIncrement);
			tSelector.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {

					adjustmentValueChanged(e);
				}
			});
			// sliderHeight += tSelector.getPreferredSize().height + ImageWindow.VGAP;
			/*
			 * if (ij!=null) tSelector.addKeyListener(ij);
			 * tSelector.addAdjustmentListener(this); tSelector.setFocusable(false); int
			 * blockIncrement = nFrames/10; if (blockIncrement<1) blockIncrement = 1;
			 * tSelector.setUnitIncrement(1); tSelector.setBlockIncrement(blockIncrement);
			 */
		}
		ImageWindow win = imp.getWindow();
		if (win != null)
			win.setSliderHeight(sliderHeight);
	}

	/** Enables or disables the sliders. Used when locking/unlocking an image. */
	public synchronized void setSlidersEnabled(final boolean b) {

		if (ic.isDisposed() == false) {
			IJ.getInstance();
			Display.getDefault().syncExec(() -> {

				if (sliceSelector != null && sliceSelector.isDisposed() == false)
					sliceSelector.setEnabled(b);
				if (cSelector != null && cSelector.isDisposed() == false)
					cSelector.setEnabled(b);
				if (zSelector != null && zSelector.isDisposed() == false)
					zSelector.setEnabled(b);
				if (tSelector != null && tSelector.isDisposed() == false)
					tSelector.setEnabled(b);
				if (animationSelector != null && animationSelector.isDisposed() == false)
					animationSelector.setEnabled(b);

			});
		}
	}

	@Override
	public void adjustmentValueChanged(AdjustmentEvent e) {

	}

	/* Replaces the above interface method! */
	public synchronized void adjustmentValueChanged(SelectionEvent e) {

		if (!running2 || imp.isHyperStack()) {
			if (e.getSource() == cSelector) {
				c = cSelector.getSelection();
				// if (c==imp.getChannel()&&e.getAdjustmentType()==AdjustmentEvent.TRACK)
				// return;
			} else if (e.getSource() == zSelector) {
				z = zSelector.getSelection();
				// int slice = hyperStack ? imp.getSlice() : imp.getCurrentSlice();
				// if (z==slice&&e.getAdjustmentType()==AdjustmentEvent.TRACK) return;
			} else if (e.getSource() == tSelector) {
				t = tSelector.getSelection();
				// if (t==imp.getFrame()&&e.getAdjustmentType()==AdjustmentEvent.TRACK) return;
			}
			slice = (t - 1) * nChannels * nSlices + (z - 1) * nChannels + c;
			notify();
		}
		if (!running)
			syncWindows(e.getSource());
	}

	/* Minor changes for SWT! */
	private void syncWindows(Object source) {

		if (SyncWindows.getInstance() == null)
			return;
		if (source == cSelector)
			SyncWindows.setC(this, cSelector.getSelection());
		else if (source == zSelector) {
			int stackSize = imp.getStackSize();
			if (imp.getNChannels() == stackSize)
				SyncWindows.setC(this, zSelector.getSelection());
			else if (imp.getNFrames() == stackSize)
				SyncWindows.setT(this, zSelector.getSelection());
			else
				SyncWindows.setZ(this, zSelector.getSelection());
		} else if (source == tSelector)
			SyncWindows.setT(this, tSelector.getSelection());
		else
			throw new RuntimeException("Unknownsource:" + source);
	}

	/* SWT event! the wheel event must be added to a canvas. Not implemented! */
	public void mouseScrolled(MouseEvent e) {

		synchronized (this) {
			int rotation = e.count;
			// boolean ctrl = (e.getModifiers() & Event.CTRL_MASK) != 0;
			boolean ctrl = (e.stateMask & SWT.CONTROL) != 0;
			if (hyperStack) // ctrl+scroll wheel adjusts hyperstack slice positions
				ctrl = false;
			if ((ctrl || IJ.shiftKeyDown()) && ic != null) {
				Point loc = ic.getCursorLoc();
				int x = ic.screenX(loc.x);
				int y = ic.screenY(loc.y);
				if (rotation < 0)
					ic.zoomIn(x, y);
				else if (rotation > 0)
					ic.zoomOut(x, y);
				return;
			}
			if (!Prefs.mouseWheelStackScrolling)
				return;
			if (hyperStack) {
				if (rotation > 0)
					IJ.run(imp, "Next Slice [>]", "");
				else if (rotation < 0)
					IJ.run(imp, "Previous Slice [<]", "");
			} else {
				int slice = imp.getCurrentSlice() + rotation;
				if (slice < 1)
					slice = 1;
				else if (slice > imp.getStack().getSize())
					slice = imp.getStack().getSize();
				setSlice(imp, slice);
				imp.updateStatusbarValue();
				SyncWindows.setZ(this, slice);
			}
		}
	}

	public boolean close() {

		if (!super.close())
			return false;
		synchronized (this) {
			done = true;
			notify();
		}
		return true;
	}

	/** Displays the specified slice and updates the stack scrollbar. */
	public void showSlice(int index) {

		if (imp != null && index >= 1 && index <= imp.getStackSize()) {
			setSlice(imp, index);
			SyncWindows.setZ(this, index);
		}
	}

	/** Updates the stack scrollbar. */
	public void updateSliceSelector() {

		if (hyperStack || zSelector == null || imp == null)
			return;
		int stackSize = imp.getStackSize();
		Display.getDefault().syncExec(() -> {

			int max = zSelector.getMaximum();
			if (max != (stackSize + 1))
				zSelector.setMaximum(stackSize + 1);
			if (imp != null && zSelector != null)
				zSelector.setSelection(imp.getCurrentSlice());

		});
	}

	public void run() {

		while (!done) {
			synchronized (this) {
				try {
					wait();
				} catch (InterruptedException e) {
				}
			}
			if (done)
				return;
			if (slice > 0) {
				int s = slice;
				slice = 0;
				if (s != imp.getCurrentSlice()) {
					/* Changed for SWT. Wrap for SWT in Runnable! */
					Display.getDefault().syncExec(new Runnable() {

						public void run() {

							imp.updatePosition(c, z, t);
							setSlice(imp, s);
						}
					});
				}
			}
		}
	}

	public String createSubtitle() {

		String subtitle = super.createSubtitle();
		if (!hyperStack || imp.getStackSize() == 1)
			return subtitle;
		String s = "";
		int[] dim = imp.getDimensions(false);
		int channels = dim[2], slices = dim[3], frames = dim[4];
		if (channels > 1) {
			s += "c:" + imp.getChannel() + "/" + channels;
			if (slices > 1 || frames > 1)
				s += " ";
		}
		if (slices > 1) {
			s += "z:" + imp.getSlice() + "/" + slices;
			if (frames > 1)
				s += " ";
		}
		if (frames > 1)
			s += "t:" + imp.getFrame() + "/" + frames;
		if (running2)
			return s;
		int index = subtitle.indexOf(";");
		if (index != -1) {
			int index2 = subtitle.indexOf("(");
			if (index2 >= 0 && index2 < index && subtitle.length() > index2 + 4
					&& !subtitle.substring(index2 + 1, index2 + 4).equals("ch:")) {
				index = index2;
				s = s + " ";
			}
			subtitle = subtitle.substring(index, subtitle.length());
		} else
			subtitle = "";
		return s + subtitle;
	}

	public boolean isHyperStack() {

		return hyperStack && getNScrollbars() > 0;
	}

	public void setPosition(int channel, int slice, int frame) {

		Display.getDefault().syncExec(() -> {

			if (cSelector != null && channel != c && cSelector.isDisposed() == false) {
				c = channel;
				cSelector.setSelection(channel);
				SyncWindows.setC(StackWindow.this, channel);
			}
			if (zSelector != null && slice != z && zSelector.isDisposed() == false) {
				z = slice;
				zSelector.setSelection(slice);
				SyncWindows.setZ(StackWindow.this, slice);
			}
			if (tSelector != null && frame != t && tSelector.isDisposed() == false) {
				t = frame;
				tSelector.setSelection(frame);
				SyncWindows.setT(StackWindow.this, frame);
			}
			StackWindow.this.slice = (t - 1) * nChannels * nSlices + (z - 1) * nChannels + c;
			imp.updatePosition(c, z, t);
			if (StackWindow.this.slice > 0) {
				int s = StackWindow.this.slice;
				StackWindow.this.slice = 0;
				if (s != imp.getCurrentSlice())
					imp.setSlice(s);
			}

		});
	}

	private void setSlice(ImagePlus imp, int n) {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				if (imp.isLocked()) {
					IJ.beep();
					IJ.showStatus("Image is locked");
				} else
					imp.setSlice(n);
			}
		});
	}

	public boolean validDimensions() {

		int c = imp.getNChannels();
		int z = imp.getNSlices();
		int t = imp.getNFrames();
		// IJ.log(c+" "+z+" "+t+" "+nChannels+" "+nSlices+" "+nFrames+"
		// "+imp.getStackSize());
		int size = imp.getStackSize();
		if (c == size && c * z * t == size && nSlices == size && nChannels * nSlices * nFrames == size)
			return true;
		if (c != nChannels || z != nSlices || t != nFrames || c * z * t != size)
			return false;
		else
			return true;
	}

	public void setAnimate(boolean b) {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				if (running2 != b && animationSelector != null)
					// System.out.println("running");
					// animationSelector.updatePlayPauseIcon();
					running2 = b;
			}
		});
	}

	public boolean getAnimate() {

		return running2;
	}

	public int getNScrollbars() {

		int n = 0;
		if (cSelector != null)
			n++;
		if (zSelector != null)
			n++;
		if (tSelector != null)
			n++;
		return n;
	}

	void removeScrollbars() {

		if (cSelector != null && cSelector.isDisposed() == false) {
			cSelector.dispose();
			compositeCChannels.dispose();
			comp.layout();
			// remove(cSelector);
			// cSelector.removeAdjustmentListener(this);
			cSelector = null;
		}
		if (zSelector != null && zSelector.isDisposed() == false) {
			zSelector.dispose();
			compositeZSlices.dispose();
			comp.layout();
			// remove(zSelector);
			// zSelector.removeAdjustmentListener(this);
			zSelector = null;
		}
		if (tSelector != null && tSelector.isDisposed() == false) {
			tSelector.dispose();
			compositeTFrames.dispose();
			comp.layout();
			// remove(tSelector);
			// tSelector.removeAdjustmentListener(this);
			tSelector = null;
		}
	}
}
