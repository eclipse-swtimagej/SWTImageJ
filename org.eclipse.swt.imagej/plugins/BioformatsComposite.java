import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import ij.IJ;
import ij.ImagePlus;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;

public class BioformatsComposite extends Composite {

	/**
	 * Create the composite.
	 * 
	 * @param parent
	 * @param style
	 */
	public BioformatsComposite(Composite parent, int style) {

		super(parent, style);
		Button btnOpen = new Button(this, SWT.NONE);
		btnOpen.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent e) {

				String id = openFileDialog();
				if (id == null) {
					return;
				}
				openFile(id);
			}
		});
		btnOpen.setBounds(10, 10, 96, 27);
		btnOpen.setText("Open");
	}

	private void openFile(String id) {

		ImporterOptions options = null;
		try {
			options = new ImporterOptions();
		} catch (IOException e) {
		}
		options.setId(id);
		// options.setAutoscale(true);
		// options.setCrop(true);
		// options.setCropRegion(0, new Region(50, 50, 100, 100));
		options.setColorMode(ImporterOptions.COLOR_MODE_COMPOSITE);
		try {
			ImagePlus[] imps = BF.openImagePlus(options);
			for (ImagePlus imp : imps)
				imp.show();
		} catch (FormatException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		} catch (IOException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
	}

	@Override
	protected void checkSubclass() {

		// Disable the check that prevents subclassing of SWT components
	}

	public static String openFileDialog() {

		AtomicReference<String> fileRef = new AtomicReference<String>();
		Display.getDefault().syncExec(() -> {

			Shell s = new Shell(SWT.ON_TOP);
			FileDialog fd = new FileDialog(s, SWT.OPEN);
			fd.setText("Load");
			String[] filterExt = { "*.*" };
			fd.setFilterExtensions(filterExt);
			fileRef.set(fd.open());

		});
		return fileRef.get();
	}
}
