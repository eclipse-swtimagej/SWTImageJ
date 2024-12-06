package ij.gui;

import ij.*;
import java.awt.event.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.widgets.Display;

import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.Frame;

/**
 * This is an extension of GenericDialog that is non-modal.
 * 
 * @author Johannes Schindelin
 */
public class NonBlockingGenericDialog extends GenericDialog {
	ImagePlus imp; // when non-null, this dialog gets closed when the image is closed
	// WindowListener windowListener; //checking for whether the associated window
	// gets closed
	private ShellListener shellListener;

	public NonBlockingGenericDialog(String title) {
		super(title, SWT.DIALOG_TRIM | SWT.MODELESS);
		// setModal(false);
		Display dis = Display.getDefault();
		dis.syncExec(new Runnable() {
			public void run() {
				NonBlockingGenericDialog.this.getShell().addShellListener(NonBlockingGenericDialog.this);
				NonBlockingGenericDialog.this.getShell().addKeyListener(NonBlockingGenericDialog.this);
			}
		});

		IJ.protectStatusBar(false);
		instance = this;
		//super.showDialog();
		//System.out.println("Non blocking");
		if (isMacro())
			return;
		if (!IJ.macroRunning()) { // add to Window menu on event dispatch thread
			final NonBlockingGenericDialog thisDialog = this;

			WindowManager.addWindow(thisDialog);

		}
	}

	/*
	 * if (imp != null) { ImageWindow win = imp.getWindow(); if (win != null) { //
	 * when the associated image closes, also close the dialog final
	 * NonBlockingGenericDialog gd = this; shellListener = new ShellListener() {
	 * 
	 * @Override public void shellActivated(ShellEvent e) { // TODO Auto-generated
	 * method stub
	 * 
	 * }
	 * 
	 * @Override public void shellClosed(ShellEvent e) { cancelDialogAndClose(e);
	 * 
	 * }
	 * 
	 * @Override public void shellDeactivated(ShellEvent e) { // TODO Auto-generated
	 * method stub
	 * 
	 * }
	 * 
	 * @Override public void shellDeiconified(ShellEvent e) { // TODO Auto-generated
	 * method stub
	 * 
	 * }
	 * 
	 * @Override public void shellIconified(ShellEvent e) { // TODO Auto-generated
	 * method stub
	 * 
	 * } }; win.shell.addShellListener(shellListener); } } try { wait(); } catch
	 * (InterruptedException e) { }
	 */

	/**
	 * Gets called if the associated image window is closed
	 * 
	 * @param e
	 */
	/*
	 * private void cancelDialogAndClose(ShellEvent e) { super.closeFinally=false;
	 * super.shellClosed(e); // sets wasCanceled=true and does dispose() }
	 */

	public void widgetSelected(SelectionEvent e) {
		super.actionPerformed(e);

		// if (!getShell().isVisible())
		// notify();
	}

	public synchronized void keyDown(org.eclipse.swt.events.KeyEvent e) {
		super.keyPressed(e);
		if (wasOKed() || wasCanceled()) {

		}
		// notify();
	}
	/*
	 * public void dispose() {
	 * 
	 * }
	 */

	/*
	 * public void dispose() { //super.dispose(); WindowManager.removeWindow(this);
	 * if (imp != null) { ImageWindow win = imp.getWindow(); if (win != null &&
	 * shellListener != null) win.shell.removeShellListener(shellListener); } }
	 */

	/** Obsolete, replaced by GUI.newNonBlockingDialog(String,ImagePlus). */
	public static GenericDialog newDialog(String title, ImagePlus imp) {
		return GUI.newNonBlockingDialog(title, imp);
	}

	/** Obsolete, replaced by GUI.newNonBlockingDialog(String). */
	public static GenericDialog newDialog(String title) {
		return GUI.newNonBlockingDialog(title);
	}

	@Override
	public void shellActivated(ShellEvent e) {
		/*
		 * if ((e.getWindow() instanceof ImageWindow) && e.getOppositeWindow()!=this)
		 * toFront();
		 */
		WindowManager.setWindow(this);

	}

	@Override
	public void shellClosed(ShellEvent e) {
		super.closeFinally = false;
		super.shellClosed(e);
		// e.doit=false;
		// this.getShell().setVisible(false);
		WindowManager.removeWindow(this);
		if (imp != null) {
			ImageWindow win = imp.getWindow();
			if (win != null && shellListener != null)
				win.shell.removeShellListener(shellListener);
		}

		if (wasOKed() || wasCanceled()) {
			// notify();
		}

	}

	@Override
	public void shellDeactivated(ShellEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellDeiconified(ShellEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellIconified(ShellEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void keyPressed(org.eclipse.swt.events.KeyEvent e) {
		super.keyPressed(e);
		if (wasOKed() || wasCanceled())
			notify();

	}

	@Override
	public void keyReleased(org.eclipse.swt.events.KeyEvent e) {
		// TODO Auto-generated method stub

	}

}
