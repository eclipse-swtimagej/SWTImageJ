package ij.plugin.frame;

import java.awt.*;
import java.awt.event.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;

import ij.*;
import ij.plugin.*;
import ij.gui.*;
import ij.process.*;

/** Implements ImageJ's Paste Control window. */
public class PasteController extends PlugInFrame implements PlugIn, SelectionListener {

	private Panel panel;
	private Combo pasteMode;
	private static PasteController instance;

	public PasteController() {
		super("Paste Control");
		if (instance != null) {
			WindowManager.toFront(instance);
			return;
		}
		WindowManager.addWindow(this);
		instance = this;
		IJ.register(PasteController.class);
		// setLayout(new FlowLayout(FlowLayout.CENTER, 2, 5));
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				shell.setLayout(new org.eclipse.swt.layout.GridLayout(1, true));
				org.eclipse.swt.widgets.Label lab = new org.eclipse.swt.widgets.Label(shell, SWT.NONE);
				lab.setText(" Transfer Mode:");
				pasteMode = new org.eclipse.swt.widgets.Combo(shell, SWT.DROP_DOWN | SWT.READ_ONLY);
				pasteMode.add("Copy");
				pasteMode.add("Blend");
				pasteMode.add("Difference");
				pasteMode.add("Transparent-white");
				pasteMode.add("Transparent-zero");
				pasteMode.add("AND");
				pasteMode.add("OR");
				pasteMode.add("XOR");
				pasteMode.add("Add");
				pasteMode.add("Subtract");
				pasteMode.add("Multiply");
				pasteMode.add("Divide");
				pasteMode.add("Min");
				pasteMode.add("Max");
				pasteMode.select(1);

				pasteMode.addSelectionListener(PasteController.this);

				// add(pasteMode);
				Roi.setPasteMode(Blitter.COPY);

				// GUI.scale(this);
				shell.pack();
				GUI.centerOnImageJScreen(PasteController.this.shell);
				// setResizable(false);
				shell.setVisible(true);
			}
		});
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		itemStateChanged(e);

	}

	public void itemStateChanged(SelectionEvent e) {
		int index = pasteMode.getSelectionIndex();
		int mode = Blitter.COPY;
		switch (index) {
		case 0:
			mode = Blitter.COPY;
			break;
		case 1:
			mode = Blitter.AVERAGE;
			break;
		case 2:
			mode = Blitter.DIFFERENCE;
			break;
		case 3:
			mode = Blitter.COPY_TRANSPARENT;
			break;
		case 4:
			mode = Blitter.COPY_ZERO_TRANSPARENT;
			break;
		case 5:
			mode = Blitter.AND;
			break;
		case 6:
			mode = Blitter.OR;
			break;
		case 7:
			mode = Blitter.XOR;
			break;
		case 8:
			mode = Blitter.ADD;
			break;
		case 9:
			mode = Blitter.SUBTRACT;
			break;
		case 10:
			mode = Blitter.MULTIPLY;
			break;
		case 11:
			mode = Blitter.DIVIDE;
			break;
		case 12:
			mode = Blitter.MIN;
			break;
		case 13:
			mode = Blitter.MAX;
			break;
		}
		Roi.setPasteMode(mode);
		if (IJ.recording())
			Recorder.record("setPasteMode", pasteMode.getItem(pasteMode.getSelectionIndex()));
		ImagePlus imp = WindowManager.getCurrentImage();
	}

	/** Overrides shellClosed() in PlugInDialog. */
	public void shellClosed(ShellEvent e) {
		e.doit = false;
		// super.close();
		instance = null;
		super.shellClosed(e);
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent arg0) {
		// TODO Auto-generated method stub

	}

}
