package ij.macro;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;

import ij.IJ;
import ij.ImageJ;
import ij.WindowManager;
import ij.plugin.frame.Editor;
import ij.plugin.frame.swt.WindowSwt;
import ij.util.Tools;

/**
 * This class implements the text editor's Macros/Find Functions command. It was
 * written by jerome.mutterer at ibmp.fr, and is based on Mark Longair's
 * CommandFinder plugin.
 */
public class FunctionFinder
		implements org.eclipse.swt.events.ModifyListener, SelectionListener, ShellListener, KeyListener {
	private static String url = "http://wsr.imagej.net/developer/macro/functions.html";
	private static Shell dialog;
	private org.eclipse.swt.widgets.Text prompt;
	private List functions;
	private Button insertButton, infoButton, closeButton;
	private String[] commands;
	private Editor editor;

	public FunctionFinder(Editor editor) {

		this.editor = editor;

		String f = Tools.openFromIJJarAsString("/functions.html");
		if (f==null) {
			IJ.error("\"functions.html\" not found in ij.jar");
			return;
		}
		f = f.replaceAll("&quot;", "\"");
		String[] l = f.split("\n");
		commands = new String[l.length];
		int c = 0;
		for (int i = 0; i < l.length; i++) {
			String line = l[i];
			if (line.startsWith("<b>")) {
				commands[c] = line.substring(line.indexOf("<b>") + 3, line.indexOf("</b>"));
				c++;
			}
		}
		if (c == 0) {
			IJ.error("ImageJ/macros/functions.html is corrupted");
			return;
		}

		ImageJ imageJ = IJ.getInstance();
		if (dialog == null) {
			dialog = new Shell(Display.getDefault(), SWT.DIALOG_TRIM | SWT.RESIZE);
			dialog.setSize(300, 500);
			dialog.setText("Built-in Functions");
			dialog.setLayout(new GridLayout(1, true));
			dialog.addShellListener(this);
			// Composite all = new Composite(dialog,SWT.NONE);
			prompt = new org.eclipse.swt.widgets.Text(dialog, SWT.NONE);
			prompt.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
			prompt.addModifyListener(this);
			prompt.addKeyListener(this);
			// northPanel.add(prompt);
			// dialog.add(northPanel, BorderLayout.NORTH);
			functions = new List(dialog, SWT.VIRTUAL | SWT.V_SCROLL);
			GridData gd_list = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 18);
			gd_list.heightHint = 400;
			gd_list.widthHint = 200;
			functions.setLayoutData(gd_list);
			functions.addKeyListener(this);
			populateList("");
			// dialog.add(functions, BorderLayout.CENTER);
			Composite buttonPanel = new Composite(dialog, SWT.NONE);
			buttonPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
			buttonPanel.setLayout(new GridLayout(3, true));
			insertButton = new Button(buttonPanel, SWT.NONE);
			insertButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
			insertButton.setText("Insert");
			insertButton.addSelectionListener(this);
			// buttonPanel.add(insertButton);
			infoButton = new Button(buttonPanel, SWT.NONE);
			infoButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
			infoButton.setText("Info");
			infoButton.addSelectionListener(this);
			// buttonPanel.add(infoButton);
			closeButton = new Button(buttonPanel, SWT.NONE);
			closeButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
			closeButton.setText("Close");
			closeButton.addSelectionListener(this);
			// buttonPanel.add(closeButton);
			// dialog.add(buttonPanel, BorderLayout.SOUTH);
			// GUI.scale(dialog);
			dialog.pack();
		}

		Object frame = WindowManager.getFrontWindow();
		if (frame == null)
			return;
		Point posi = null;
		if (frame instanceof Shell) {
			posi = ((Shell) frame).getLocation();
		} else if (frame instanceof Shell) {
			posi = ((WindowSwt) frame).getShell().getLocation();
		}

		// java.awt.Point posi=frame.getLocationOnScreen();
		int initialX = (int) posi.x + 38;
		int initialY = (int) posi.y + 84;
		dialog.setLocation(initialX, initialY);
		dialog.setVisible(true);
		dialog.forceActive();
	}

	public FunctionFinder() {
		this(null);
	}

	public void populateList(String matchingSubstring) {
		String substring = matchingSubstring.toLowerCase();
		functions.removeAll();
		try {
			for (int i = 0; i < commands.length; ++i) {
				String commandName = commands[i];
				if (commandName.length() == 0)
					continue;
				String lowerCommandName = commandName.toLowerCase();
				if (lowerCommandName.indexOf(substring) >= 0) {
					functions.add(commands[i]);
				}
			}
		} catch (Exception e) {
		}
	}

	public void edPaste(String arg) {
		Object frame = WindowManager.getActiveWindow();
		if (!(frame instanceof Editor))
			return;

		try {
			StyledText ta = ((Editor) frame).getTextArea();
			editor = (Editor) frame;
			int start = ta.getSelectionRange().x;
			int length = ta.getSelectionRange().y;
			try {
				ta.replaceTextRange(start, length, arg.substring(0, arg.length()));
			} catch (Exception e) {
			}
			if (IJ.isMacOSX())
				ta.setCaretOffset(start + arg.length());
		} catch (Exception e) {
		}
	}

	public void itemStateChanged(SelectionEvent ie) {
		populateList(prompt.getText());
	}

	protected void runFromLabel(String listLabel) {
		edPaste(listLabel);
		closeAndRefocus();
	}

	public void close() {
		closeAndRefocus();
	}

	public void closeAndRefocus() {
		if (dialog != null) {
			if (dialog.isDisposed() == false) {
				dialog.dispose();
				dialog = null;
			}
		}
		if (editor != null)
			editor.getShell().forceActive();
	}

	public void keyPressed(KeyEvent ke) {
		int key = ke.keyCode;
		int items = functions.getItemCount();
		Object source = ke.getSource();
		if (source == prompt) {
			if (key == SWT.CR || key == SWT.KEYPAD_CR) {
				if (1 == items) {
					String selected = functions.getItem(0);
					edPaste(selected);
				}
			} else if (key == SWT.ARROW_UP) {
				functions.forceFocus();
				if (items > 0)
					functions.select(functions.getItemCount() - 1);
			} else if (key == SWT.ESC) {
				closeAndRefocus();
			} else if (key == SWT.ARROW_DOWN) {
				functions.forceFocus();
				if (items > 0)
					functions.select(0);
			}
		} else if (source == functions) {
			if (key == SWT.CR || key == SWT.KEYPAD_CR) {
				String selected = functions.getItem(functions.getSelectionIndex());
				if (selected != null)
					edPaste(selected);
			} else if (key == SWT.ESC) {
				closeAndRefocus();
			} else if (key == SWT.SPACE || key == SWT.DEL) {
				/*
				 * If someone presses backspace or delete they probably want to remove the last
				 * letter from the search string, so switch the focus back to the prompt:
				 */
				prompt.forceFocus();
			}
		}
	}

	public void keyReleased(KeyEvent ke) {
	}

	public void keyTyped(KeyEvent ke) {
	}

	@Override
	public void modifyText(ModifyEvent e) {
		textValueChanged(e);

	}

	public void textValueChanged(ModifyEvent te) {
		populateList(prompt.getText());
	}

	public void actionPerformed(SelectionEvent e) {
		String url2 = this.url;
		Object b = e.getSource();
		if (b == insertButton) {
			int index = functions.getSelectionIndex();
			if (index >= 0) {
				String selected = functions.getItem(index);
				edPaste(selected);
			}
		} else if (b == infoButton) {
			int index = functions.getSelectionIndex();
			if (index >= 0) {
				String selected = functions.getItem(index);
				int index2 = selected.indexOf("(");
				if (index2 == -1)
					index2 = selected.length();
				url2 = url2 + "#" + selected.substring(0, index2);
			}
			IJ.runPlugIn("ij.plugin.BrowserLauncher", url2);
		} else if (b == closeButton)
			closeAndRefocus();
	}

	/*
	 * public void windowClosing(WindowEvent e) { closeAndRefocus(); }
	 */

	@Override
	public void widgetSelected(SelectionEvent e) {
		actionPerformed(e);

	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellActivated(ShellEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellClosed(ShellEvent e) {
		closeAndRefocus();

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

}
