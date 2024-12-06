package ij.gui;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import ij.WindowManager;

public class HTMLDialog extends Dialog implements ShellListener, KeyListener {

	private String message;
	private boolean modal;
	private String title;
	private boolean escapePressed;
	private Browser browserDialog;

	public HTMLDialog(String title, String message) {

		super(ij.IJ.getInstance().getShell());
		this.message = message;
		this.title = title;
		// if (!modal) {
		WindowManager.addWindow(this);
		modal = false;
		openSyncExec();
	}

	public HTMLDialog(Shell parentShell, String title, String message) {

		super(parentShell);
		this.message = message;
		// if (!modal) {
		WindowManager.addWindow(this);
		modal = false;
		openSyncExec();
	}

	public HTMLDialog(Shell parentShell, String title, String message, boolean modal) {

		super(parentShell);
		this.message = message;
		modal = true;
		openSyncExec();
	}

	public void openSyncExec() {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				open();
				GUI.centerOnImageJScreen(HTMLDialog.this.getShell());
			}
		});
	}

	@Override
	protected Control createDialogArea(Composite parent) {

		Composite composite = (Composite)super.createDialogArea(parent);
		GridLayout layout = new GridLayout(1, false);
		composite.setLayout(layout);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.widthHint = 600;
		data.heightHint = 300;
		composite.setLayoutData(data);
		browserDialog = new Browser(composite, SWT.NONE);
		browserDialog.addKeyListener(this);
		browserDialog.setText(message);
		browserDialog.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		return composite;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {

	}

	protected boolean isResizable() {

		return true;
	};

	@Override
	protected void configureShell(Shell shell) {

		super.configureShell(shell);
		if(title != null)
			shell.setText(title);
	}

	@Override
	public void okPressed() {

		close();
	}

	@Override
	public void shellActivated(ShellEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void shellClosed(ShellEvent e) {

		e.doit = false;
		if(!modal)
			WindowManager.removeWindow(this);
		e.doit = true;
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

	public void keyPressed(org.eclipse.swt.events.KeyEvent e) {

		int keyCode = e.keyCode;
		int character = e.character;
		ij.IJ.setKeyDown(keyCode);
		escapePressed = keyCode == SWT.ESC;
		if(character == 'c') {
			browserDialog.getText();
			Clipboard cb = new Clipboard(Display.getDefault());
			String textData = browserDialog.getText();
			TextTransfer textTransfer = TextTransfer.getInstance();
			cb.setContents(new Object[]{textData}, new Transfer[]{textTransfer});
		} else if(keyCode == SWT.CR || keyCode == SWT.LF || character == 'w' || escapePressed)
			close();
	}

	public boolean escapePressed() {

		return escapePressed;
	}

	@Override
	public void keyReleased(KeyEvent e) {
		// TODO Auto-generated method stub

	}
	
}
/** This is modal or non-modal dialog box that displays HTML formated text. */
/*
 * public class HTMLDialog extends JDialog implements ActionListener,
 * KeyListener, HyperlinkListener, WindowListener { private boolean
 * escapePressed; private JEditorPane editorPane; private boolean modal = true;
 * public HTMLDialog(String title, String message) { super(ij.IJ.getInstance(),
 * title, true); init(message); }
 * public HTMLDialog(Dialog parent, String title, String message) {
 * super(parent, title, true); init(message); }
 * public HTMLDialog(String title, String message, boolean modal) {
 * super(ij.IJ.getInstance(), title, modal); this.modal = modal; init(message);
 * }
 * private void init(String message) { ij.util.Java2.setSystemLookAndFeel();
 * Container container = getContentPane(); container.setLayout(new
 * BorderLayout()); if (message==null) message = ""; editorPane = new
 * JEditorPane("text/html",""); editorPane.setEditable(false); HTMLEditorKit kit
 * = new HTMLEditorKit(); editorPane.setEditorKit(kit); StyleSheet styleSheet =
 * kit.getStyleSheet(); styleSheet.
 * addRule("body{font-family:Verdana,sans-serif; font-size:11.5pt; margin:5px 10px 5px 10px;}"
 * ); //top right bottom left styleSheet.addRule("h1{font-size:18pt;}");
 * styleSheet.addRule("h2{font-size:15pt;}");
 * styleSheet.addRule("dl dt{font-face:bold;}"); editorPane.setText(message);
 * //display the html text with the above style
 * editorPane.getActionMap().put("insert-break", new AbstractAction(){ public
 * void actionPerformed(ActionEvent e) {} }); //suppress beep on <ENTER> key
 * JScrollPane scrollPane = new JScrollPane(editorPane);
 * container.add(scrollPane); JButton button = new JButton("OK");
 * button.addActionListener(this); button.addKeyListener(this);
 * editorPane.addKeyListener(this); editorPane.addHyperlinkListener(this);
 * JPanel panel = new JPanel(); panel.add(button); container.add(panel,
 * "South"); setForeground(Color.black); addWindowListener(this); pack();
 * Dimension screenD = IJ.getScreenSize(); Dimension dialogD = getSize(); int
 * maxWidth = (int)(Math.min(0.70*screenD.width, 800)); //max 70% of screen
 * width, but not more than 800 pxl if (maxWidth>400 && dialogD.width>maxWidth)
 * dialogD.width = maxWidth; if (dialogD.height > 0.80*screenD.height &&
 * screenD.height>400) //max 80% of screen height dialogD.height =
 * (int)(0.80*screenD.height); setSize(dialogD); GUI.centerOnImageJScreen(this);
 * if (!modal) { WindowManager.addWindow(this); show(); } final JScrollBar
 * verticalScrollBar = scrollPane.getVerticalScrollBar(); if
 * (verticalScrollBar!=null) { EventQueue.invokeLater(new Runnable() { public
 * void run() { verticalScrollBar.setValue(verticalScrollBar.getMinimum());
 * //start scrollbar at top } }); } if (modal) show(); }
 * public void actionPerformed(ActionEvent e) { dispose(); }
 * public void keyPressed(KeyEvent e) { int keyCode = e.getKeyCode();
 * ij.IJ.setKeyDown(keyCode); escapePressed = keyCode==KeyEvent.VK_ESCAPE; if
 * (keyCode==KeyEvent.VK_C) { if (editorPane.getSelectedText()==null ||
 * editorPane.getSelectedText().length()==0) editorPane.selectAll();
 * editorPane.copy(); editorPane.select(0,0); } else if
 * (keyCode==KeyEvent.VK_ENTER || keyCode==KeyEvent.VK_W || escapePressed)
 * dispose(); }
 * public void keyReleased(KeyEvent e) { int keyCode = e.getKeyCode();
 * ij.IJ.setKeyUp(keyCode); }
 * public void keyTyped(KeyEvent e) {}
 * public boolean escapePressed() { return escapePressed; }
 * public void hyperlinkUpdate(HyperlinkEvent e) { if (e.getEventType() ==
 * HyperlinkEvent.EventType.ACTIVATED) { String url = e.getDescription();
 * //getURL does not work for relative links within document such as "#top" if
 * (url==null) return; if (url.startsWith("#"))
 * editorPane.scrollToReference(url.substring(1)); else { String macro =
 * "run('URL...', 'url="+url+"');"; new MacroRunner(macro); } } }
 * public void dispose() { super.dispose(); if (!modal)
 * WindowManager.removeWindow(this); }
 * public void windowClosing(WindowEvent e) { dispose(); }
 * public void windowActivated(WindowEvent e) {} public void
 * windowOpened(WindowEvent e) {} public void windowClosed(WindowEvent e) {}
 * public void windowIconified(WindowEvent e) {} public void
 * windowDeiconified(WindowEvent e) {} public void windowDeactivated(WindowEvent
 * e) {}
 * }
 */
