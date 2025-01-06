package ij.plugin;

import java.io.File;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.io.Opener;
import ij.process.ImageProcessor;

/**
 * This plugin implements the File/ /Convert command, which converts the images
 * in a folder to a specified format.
 */
public class BatchConverter implements PlugIn, SelectionListener {

	private static final String[] formats = { "TIFF", "8-bit TIFF", "JPEG", "GIF", "PNG", "PGM", "BMP", "FITS",
			"Text Image", "ZIP", "Raw" };
	private static String format = formats[0];
	private static double scale = 1.0;
	private static boolean useBioFormats;
	private static int interpolationMethod = ImageProcessor.BILINEAR;
	private static boolean averageWhenDownSizing;
	private String[] methods = ImageProcessor.getInterpolationMethods();
	org.eclipse.swt.widgets.Button input;
	private org.eclipse.swt.widgets.Button output;
	org.eclipse.swt.widgets.Text inputDir;
	private Text outputDir;
	private GenericDialog gd;
	private String inputPath;
	private String outputPath;

	public void run(String arg) {

		if (!showDialog())
			return;
		Display.getDefault().syncExec(() -> {

			inputPath = inputDir.getText();

		});
		if (inputPath.equals("")) {
			IJ.error("Batch Converter", "Please choose an input folder");
			return;
		}
		Display.getDefault().syncExec(() -> {

			outputPath = outputDir.getText();

		});
		if (outputPath.equals("")) {
			IJ.error("Batch Converter", "Please choose an output folder");
			return;
		}
		File f1 = new File(inputPath);
		if (!f1.exists() || !f1.isDirectory()) {
			IJ.error("Batch Converter", "Input does not exist or is not a folder\n \n" + inputPath);
			return;
		}
		File f2 = new File(outputPath);
		if (!outputPath.equals("") && (!f2.exists() || !f2.isDirectory())) {
			IJ.error("Batch Converter", "Output does not exist or is not a folder\n \n" + outputPath);
			return;
		}
		String[] list = (new File(inputPath)).list();
		IJ.resetEscape();
		Opener opener = new Opener();
		opener.setSilentMode(true);
		long t0 = System.currentTimeMillis();
		for (int i = 0; i < list.length; i++) {
			if (IJ.escapePressed())
				break;
			if (IJ.debugMode)
				IJ.log(i + "  " + list[i]);
			String path = inputPath + list[i];
			if ((new File(path)).isDirectory())
				continue;
			if (list[i].startsWith(".") || list[i].endsWith(".avi") || list[i].endsWith(".AVI"))
				continue;
			IJ.showStatus(i + "/" + list.length);
			IJ.showProgress(i + 1, list.length);
			ImagePlus imp = null;
			IJ.redirectErrorMessages(true);
			if (useBioFormats)
				imp = Opener.openUsingBioFormats(path);
			else
				imp = opener.openImage(inputPath, list[i]);
			IJ.redirectErrorMessages(false);
			if (imp == null) {
				String reader = useBioFormats ? "Bio-Formats not found or" : "IJ.openImage()";
				IJ.log(reader + " returned null: " + path);
				continue;
			}
			if (scale != 1.0) {
				int width = (int) (scale * imp.getWidth());
				int height = (int) (scale * imp.getHeight());
				ImageProcessor ip = imp.getProcessor();
				ip.setInterpolationMethod(interpolationMethod);
				ip.setProgressBar(null);
				imp.setProcessor(null, ip.resize(width, height, averageWhenDownSizing));
				ip = null;
			}
			if (format.equals("8-bit TIFF") || format.equals("GIF")) {
				if (imp.getBitDepth() == 24)
					IJ.run(imp, "8-bit Color", "number=256");
				else
					IJ.run(imp, "8-bit", "");
			}
			IJ.saveAs(imp, format, outputPath + list[i]);
			imp.close();
			imp = null;
		}
		IJ.showStatus(list.length + " files converted in " + IJ.d2s((System.currentTimeMillis() - t0) / 1000.0, 2)
				+ " seconds");
		IJ.showProgress(1, 1);
		Display.getDefault().syncExec(() -> {

			Prefs.set("batch.input", inputDir.getText());
			Prefs.set("batch.output", outputDir.getText());

		});
	}

	private boolean showDialog() {

		gd = new GenericDialog("Batch Convert");
		addPanels(gd);
		gd.setInsets(15, 0, 5);
		gd.addChoice("Output_format:", formats, format);
		gd.addChoice("Interpolation:", methods, methods[interpolationMethod]);
		// gd.addStringField("Height (pixels): ", height==0?"\u2014":""+height, 6);
		gd.addNumericField("Scale factor:", scale, 2);
		gd.addCheckbox("Average when downsizing", averageWhenDownSizing);
		// gd.addCheckbox("Read images using Bio-Formats", useBioFormats);
		gd.setOKLabel("Convert");
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		format = gd.getNextChoice();
		interpolationMethod = gd.getNextChoiceIndex();
		// height = (int)Tools.parseDouble(gd.getNextString(), 0.0);
		scale = gd.getNextNumber();
		averageWhenDownSizing = gd.getNextBoolean();
		// useBioFormats = gd.getNextBoolean();
		return true;
	}

	void addPanels(GenericDialog gd) {

		Display defaultDis = Display.getDefault();
		defaultDis.syncExec(new Runnable() {

			public void run() {

				Composite p = new Composite(gd.getShell(), SWT.NONE);
				p.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				GridLayout layout = new GridLayout(2, true);
				p.setLayout(layout);
				input = new org.eclipse.swt.widgets.Button(p, SWT.NONE);
				input.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				input.setText("Input...");
				input.addSelectionListener(BatchConverter.this);
				// p.add(input);
				inputDir = new org.eclipse.swt.widgets.Text(p, SWT.SINGLE);
				inputDir.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
				inputDir.setText(Prefs.get("batch.input", ""));
				// p.add(inputDir);
				// gd.addPanel(p);
				p = new Composite(gd.getShell(), SWT.NONE);
				p.setLayout(layout);
				output = new org.eclipse.swt.widgets.Button(p, SWT.NONE);
				output.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				output.setText("Output...");
				output.addSelectionListener(BatchConverter.this);
				// p.add(output);
				outputDir = new org.eclipse.swt.widgets.Text(p, SWT.SINGLE);
				outputDir.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
				outputDir.setText(Prefs.get("batch.output", ""));
				// p.add(outputDir);
				gd.addPanel(p);
			}
		});
	}

	public void actionPerformed(SelectionEvent e) {

		Object source = e.getSource();
		String s = source == input ? "Input" : "Output";
		String path = IJ.getDirectory(s + " Folder");
		if (path == null)
			return;
		if (source == input)
			inputDir.setText(path);
		else
			outputDir.setText(path);
	}

	@Override
	public void widgetSelected(SelectionEvent e) {

		actionPerformed(e);
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		// TODO Auto-generated method stub

	}
}
