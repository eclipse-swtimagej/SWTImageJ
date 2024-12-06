import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import ij.gui.GUI;
import ij.plugin.frame.PlugInFrame;

public class Bioformats_ extends PlugInFrame {

	public Bioformats_() {
		super("Bioformats");
		Display.getDefault().syncExec(() -> {
		    /*Shell is already in superclass!*/
			shell.setText("Bioformats");
			GridLayout layout = new GridLayout(1, true);
			shell.setLayout(layout);
			BioformatsComposite composite = new BioformatsComposite(shell, SWT.NONE);
			composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			GUI.center(shell);
			shell.setSize(400,400);
			shell.layout();
			shell.setVisible(true);
		});
	}
}
