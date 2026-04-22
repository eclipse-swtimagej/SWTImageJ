import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import loci.plugins.BF;
import loci.plugins.util.ImporterOptions;

public class FullBioFormatsImporter_ implements PlugIn {

	@Override
	public void run(String arg) {

		try {
			ImporterOptions options = new ImporterOptions();
			Display display = Display.getDefault();
			Shell shell = new Shell(display, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
			shell.setText("Bio-Formats SWT Importer - All Options");
			shell.setLayout(new FillLayout());
			ScrolledComposite sc = new ScrolledComposite(shell, SWT.V_SCROLL);
			Composite container = new Composite(sc, SWT.NONE);
			container.setLayout(new GridLayout(2, false));
			// --- 1. DATA HANDLING & STACK SETTINGS ---
			addHeader(container, "Stack & Data Handling");
			Button virtual = createCheck(container, "Use virtual stack", options.isVirtual());
			Button group = createCheck(container, "Group files with similar names", options.isGroupFiles());
			Button stitch = createCheck(container, "Stitch tiles", options.isStitchTiles());
			Button concatenate = createCheck(container, "Concatenate series", options.isConcatenate());
			// --- 2. METADATA & ROIs ---
			addHeader(container, "Metadata & ROIs");
			Button showMeta = createCheck(container, "Display metadata", options.isShowMetadata());
			Button showOME = createCheck(container, "Display OME-XML metadata", options.isShowOMEXML());
			Button importROI = createCheck(container, "Import ROIs", options.isShowROIs());
			// --- 3. DIMENSIONS & COORDINATES ---
			addHeader(container, "Dimensions & Order");
			Combo stackOrder = createCombo(container, "Stack order:", new String[]{"XYCZT", "XYTCZ", "XYZCT", "XYZTC", "XYCZT"}, 0);
			Button swapDim = createCheck(container, "Swap dimensions", options.isSwapDimensions());
			Button splitC = createCheck(container, "Split channels", options.isSplitChannels());
			Button splitZ = createCheck(container, "Split focal planes", options.isSplitFocalPlanes());
			Button splitT = createCheck(container, "Split timepoints", options.isSplitTimepoints());
			// --- 4. COLOR & DISPLAY ---
			addHeader(container, "Color & Display");
			Combo colorMode = createCombo(container, "Color mode:", new String[]{ImporterOptions.COLOR_MODE_DEFAULT, ImporterOptions.COLOR_MODE_COMPOSITE, ImporterOptions.COLOR_MODE_COLORIZED, ImporterOptions.COLOR_MODE_GRAYSCALE}, 0);
			Button autoscale = createCheck(container, "Autoscale (adjust contrast)", options.isAutoscale());
			// --- 5. SERIES & CROPPING ---
			addHeader(container, "Advanced Selections");
			Button openAll = createCheck(container, "Open all series", options.isOpenAllSeries());
			Button crop = createCheck(container, "Crop on import", options.isCrop());
			// --- DIALOG BUTTONS ---
			Button ok = new Button(container, SWT.PUSH);
			ok.setText("Open File");
			ok.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
			ok.addListener(SWT.Selection, e -> {
				options.setVirtual(virtual.getSelection());
				options.setGroupFiles(group.getSelection());
				options.setStitchTiles(stitch.getSelection());
				options.setConcatenate(concatenate.getSelection());
				options.setShowMetadata(showMeta.getSelection());
				options.setShowOMEXML(showOME.getSelection());
				options.setShowROIs(importROI.getSelection());
				options.setStackOrder(stackOrder.getText());
				options.setSwapDimensions(swapDim.getSelection());
				options.setSplitChannels(splitC.getSelection());
				options.setSplitFocalPlanes(splitZ.getSelection());
				options.setSplitTimepoints(splitT.getSelection());
				options.setColorMode(colorMode.getText());
				options.setAutoscale(autoscale.getSelection());
				options.setOpenAllSeries(openAll.getSelection());
				options.setCrop(crop.getSelection());
				shell.close();
			});
			// Run File Dialog
			FileDialog fd = new FileDialog(shell, SWT.OPEN);
			String file = fd.open();
			if(file == null) {
				shell.dispose();
				return;
			}
			options.setId(file);
			container.setSize(container.computeSize(SWT.DEFAULT, SWT.DEFAULT));
			sc.setContent(container);
			shell.setSize(450, 600);
			shell.open();
			while(!shell.isDisposed()) {
				if(!display.readAndDispatch())
					display.sleep();
			}
			// Execute Import
			ImagePlus[] imps = BF.openImagePlus(options);
			for(ImagePlus imp : imps)
				imp.show();
		} catch(Exception e) {
			IJ.error("Bio-Formats Error", e.getMessage());
		}
	}

	private void addHeader(Composite parent, String text) {

		Label label = new Label(parent, SWT.NONE);
		label.setText(text);
		label.setFont(new org.eclipse.swt.graphics.Font(parent.getDisplay(), "Arial", 10, SWT.BOLD));
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
		new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL).setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
	}

	private Button createCheck(Composite parent, String text, boolean state) {

		Button b = new Button(parent, SWT.CHECK);
		b.setText(text);
		b.setSelection(state);
		b.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
		return b;
	}

	private Combo createCombo(Composite parent, String labelText, String[] items, int selection) {

		new Label(parent, SWT.NONE).setText(labelText);
		Combo c = new Combo(parent, SWT.READ_ONLY);
		c.setItems(items);
		c.select(selection);
		return c;
	}
}
