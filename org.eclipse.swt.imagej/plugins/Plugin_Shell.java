import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import ij.gui.GUI;
import ij.plugin.frame.PlugInFrame;

public class Plugin_Shell extends PlugInFrame {

	public Plugin_Shell() {
		super("Plugin_Frame");
		Display.getDefault().syncExec(() -> {
		    /*Shell is already in superclass!*/
			shell.setText("Plugin_Shell");
			GridLayout layout = new GridLayout(1, true);
			shell.setLayout(layout);
			SWTComposite composite = new SWTComposite(shell, SWT.NONE);
			composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			GUI.center(shell);
			shell.setSize(300,300);
			shell.layout();
			shell.setVisible(true);
		});
	}
}
