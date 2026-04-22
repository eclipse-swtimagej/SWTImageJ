/*
 * #%L
 * Bio-Formats SWT Importer Dialog
 * %%
 */

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import ij.IJ;
import ij.io.OpenDialog;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;

import ij.ImagePlus;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
/**
 * Bio-Formats Importer general options dialog box (SWT implementation).
 * This dialog provides comprehensive options for configuring Bio-Formats image import
 * with full file loading and ImageJ integration.
 */
public class FullBioFormatsImporter_ {

	private static final Logger LOGGER = Logger.getLogger(FullBioFormatsImporter_.class.getName());
	// -- Constants --
	/** Initial message to display in help text box. */
	public static final String INFO_DEFAULT = "";
	// -- Configuration Constants --
	public static final String VIEW_NONE = "None";
	public static final String VIEW_STANDARD = "Standard ImageJ";
	public static final String VIEW_HYPERSTACK = "Hyperstack";
	public static final String VIEW_BROWSER = "Data Browser";
	public static final String VIEW_IMAGE_5D = "Image5D";
	public static final String VIEW_VIEW_5D = "View5D";
	public static final String ORDER_DEFAULT = "Default";
	public static final String ORDER_XYCZT = "XYCZT";
	public static final String ORDER_XYZCT = "XYZCT";
	public static final String COLOR_MODE_DEFAULT = "Default";
	public static final String COLOR_MODE_COMPOSITE = "Composite";
	public static final String COLOR_MODE_GRAYSCALE = "Grayscale";
	public static final String ROIS_MODE_MANAGER = "ROI Manager";
	public static final String ROIS_MODE_OVERLAY = "Overlay";
	// -- Fields --
	protected Shell shell;
	protected boolean dialogCanceled = false;
	protected String selectedFilePath;
	protected IFormatReader reader;
	// File selection UI
	protected Text filePathText;
	protected Label fileInfoLabel;
	protected Button browseButton;
	// Stack viewing options
	protected Combo stackFormatChoice;
	protected Combo stackOrderChoice;
	// Metadata viewing options
	protected Button showMetadataBox;
	protected Button showOMEXMLBox;
	protected Button showROIsBox;
	protected Combo roisModeChoice;
	// Dataset organization options
	protected Button groupFilesBox;
	protected Button ungroupFilesBox;
	protected Button swapDimsBox;
	protected Button openAllSeriesBox;
	protected Button concatenateBox;
	protected Button stitchTilesBox;
	// Memory management options
	protected Button virtualBox;
	protected Button specifyRangesBox;
	protected Button cropBox;
	// Color options
	protected Combo colorModeChoice;
	protected Button autoscaleBox;
	// Split options
	protected Button splitCBox;
	protected Button splitZBox;
	protected Button splitTBox;
	// Advanced options
	protected Button forceThumbnailsBox;
	protected Button upgradeCheckBox;
	protected Button quietBox;
	// Info section
	protected Map<Control, String> infoTable;
	protected StyledText infoPane;
	// Options storage
	protected Map<String, Object> options;
	// -- Constructor --

	/** Creates a general options dialog for Bio-Formats import. */
	public FullBioFormatsImporter_() {

		this.options = new HashMap<>();
		initializeDefaults();
		Display.getDefault().syncExec(() -> {
			show();
		});
	}
	// -- Dialog methods --

	/**
	 * Display the dialog and harvest results.
	 * 
	 * @return true if dialog was accepted, false if canceled
	 */
	public boolean show() {

		shell = new Shell(Display.getDefault(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		shell.setText("Bio-Formats Importer");
		shell.setLayout(new GridLayout(1, false));
		shell.setSize(1100, 850);
		createDialogContent();
		shell.open();
		Display display = shell.getDisplay();
		while(!shell.isDisposed()) {
			if(!display.readAndDispatch()) {
				display.sleep();
			}
		}
		return !dialogCanceled;
	}

	/**
	 * Get the selected file path.
	 * 
	 * @return File path string, or null if no file selected
	 */
	public String getSelectedFile() {

		return selectedFilePath;
	}

	/**
	 * Get the options map after dialog is accepted.
	 * 
	 * @return Map of option names to values
	 */
	public Map<String, Object> getOptions() {

		return new HashMap<>(options);
	}

	/**
	 * Set initial option values.
	 * 
	 * @param optionMap
	 *            Map of option names to values
	 */
	public void setOptions(Map<String, Object> optionMap) {

		if(optionMap != null) {
			options.putAll(optionMap);
		}
	}

	/**
	 * Get information about the selected file.
	 * 
	 * @return String with file format information
	 */
	public String getFileInfo() {

		if(selectedFilePath == null || reader == null) {
			return "No file loaded";
		}
		StringBuilder info = new StringBuilder();
		try {
			info.append("Format: ").append(reader.getFormat()).append("\n");
			info.append("Series: ").append(reader.getSeriesCount()).append("\n");
			for(int s = 0; s < Math.min(reader.getSeriesCount(), 3); s++) {
				reader.setSeries(s);
				info.append("Series ").append(s).append(": ").append(reader.getSizeX()).append("x").append(reader.getSizeY()).append(" (").append(reader.getSizeZ()).append("Z, ").append(reader.getSizeT()).append("T, ").append(reader.getSizeC()).append("C)\n");
			}
		} catch(Exception e) {
			info.append("Error reading metadata: ").append(e.getMessage());
		}
		return info.toString();
	}
	// -- Private methods --

	private void initializeDefaults() {

		options.put("autoscale", false);
		options.put("colorMode", COLOR_MODE_DEFAULT);
		options.put("concatenate", false);
		options.put("crop", false);
		options.put("groupFiles", true);
		options.put("ungroupFiles", false);
		options.put("openAllSeries", false);
		options.put("showMetadata", false);
		options.put("showOMEXML", false);
		options.put("showROIs", false);
		options.put("roisMode", ROIS_MODE_MANAGER);
		options.put("specifyRanges", false);
		options.put("splitZ", false);
		options.put("splitT", false);
		options.put("splitC", false);
		options.put("stackFormat", VIEW_HYPERSTACK);
		options.put("stackOrder", ORDER_XYCZT);
		options.put("swapDimensions", false);
		options.put("virtual", false);
		options.put("stitchTiles", false);
		options.put("forceThumbnails", false);
		options.put("upgradeCheck", true);
		options.put("quiet", false);
	}

	private void createDialogContent() {

		// Create file selection section at the top
		createFileSelectionSection();
		// Create scrolled composite for main content
		ScrolledComposite scroll = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.H_SCROLL);
		scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scroll.setExpandHorizontal(true);
		scroll.setExpandVertical(true);
		// Main content composite with 2 columns
		Composite mainComposite = new Composite(scroll, SWT.NONE);
		mainComposite.setLayout(new GridLayout(2, false));
		// Create left and right columns
		Composite leftColumn = new Composite(mainComposite, SWT.NONE);
		leftColumn.setLayout(new GridLayout(1, false));
		leftColumn.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		Composite rightColumn = new Composite(mainComposite, SWT.NONE);
		rightColumn.setLayout(new GridLayout(1, false));
		rightColumn.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		// Initialize info table
		infoTable = new HashMap<>();
		// LEFT COLUMN SECTIONS
		createStackViewingSection(leftColumn);
		createDatasetOrganizationSection(leftColumn);
		createColorOptionsSection(leftColumn);
		// RIGHT COLUMN SECTIONS
		createMetadataViewingSection(rightColumn);
		createMemoryManagementSection(rightColumn);
		createSplitWindowsSection(rightColumn);
		createAdvancedOptionsSection(rightColumn);
		// Set up scroll composite
		scroll.setContent(mainComposite);
		scroll.setMinSize(mainComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		// Create button composite at the bottom
		Composite buttonComposite = new Composite(shell, SWT.NONE);
		buttonComposite.setLayout(new GridLayout(3, true));
		buttonComposite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		Button importButton = new Button(buttonComposite, SWT.PUSH);
		importButton.setText("Import");
		importButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		importButton.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				performImport();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {

				widgetSelected(e);
			}
		});
		Button okButton = new Button(buttonComposite, SWT.PUSH);
		okButton.setText("OK");
		okButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		okButton.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				harvestResults();
				dialogCanceled = false;
				shell.close();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {

				widgetSelected(e);
			}
		});
		Button cancelButton = new Button(buttonComposite, SWT.PUSH);
		cancelButton.setText("Cancel");
		cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		cancelButton.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				dialogCanceled = true;
				closeReader();
				shell.close();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {

				widgetSelected(e);
			}
		});
		// Initial verification
		verifyOptions(null);
	}

	private void createFileSelectionSection() {

		Group fileGroup = new Group(shell, SWT.NONE);
		fileGroup.setText("File Selection");
		fileGroup.setLayout(new GridLayout(3, false));
		fileGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		Label fileLabel = new Label(fileGroup, SWT.NONE);
		fileLabel.setText("Select File:");
		fileLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		filePathText = new Text(fileGroup, SWT.BORDER | SWT.READ_ONLY);
		filePathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		browseButton = new Button(fileGroup, SWT.PUSH);
		browseButton.setText("Browse...");
		browseButton.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				browseForFile();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {

				widgetSelected(e);
			}
		});
		fileInfoLabel = new Label(fileGroup, SWT.WRAP);
		GridData fileInfoData = new GridData(SWT.FILL, SWT.FILL, true, false);
		fileInfoData.horizontalSpan = 3;
		fileInfoData.heightHint = 80;
		fileInfoLabel.setLayoutData(fileInfoData);
		fileInfoLabel.setText("No file selected");
	}

	private void browseForFile() {

		// Use ImageJ's native file dialog
		OpenDialog dialog = new OpenDialog("Open Image File", "");
		if(dialog.getFileName() != null && !dialog.getFileName().isEmpty()) {
			selectedFilePath = dialog.getDirectory() + dialog.getFileName();
			filePathText.setText(selectedFilePath);
			// Load and display file information
			loadFileInfo();
		}
	}

	private void loadFileInfo() {

		if(selectedFilePath == null || selectedFilePath.isEmpty()) {
			fileInfoLabel.setText("No file selected");
			return;
		}
		try {
			// Close previous reader if open
			closeReader();
			// Create new reader and load file info
			reader = new ImageReader();
			try {
				reader.setId(selectedFilePath);
			} catch(FormatException e) {
			}
			fileInfoLabel.setText(getFileInfo());
			LOGGER.info("Loaded file: " + selectedFilePath);
		} catch(IOException e) {
			LOGGER.severe("Error reading file: " + e.getMessage());
			fileInfoLabel.setText("Error reading file: " + e.getMessage());
		}
	}

	private void performImport() {

		if(selectedFilePath == null || selectedFilePath.isEmpty()) {
			IJ.error("Error", "Please select a file first");
			return;
		}
		try {
			// Harvest current options
			harvestResults();
			// Log the import action
			LOGGER.info("Importing file: " + selectedFilePath);
			LOGGER.info("With options: " + options);
			// TODO: Implement actual Bio-Formats import logic here
			// This is where you would call the Bio-Formats reader
			// and load the image into ImageJ based on selected options
			// Example:
				// 1. Neues ImporterOptions-Objekt erstellen
    			ImporterOptions bfOptions = new ImporterOptions();
    bfOptions.setId(selectedFilePath);

    // Boolean Optionen
    bfOptions.setAutoscale((Boolean) options.get("autoscale"));
    bfOptions.setConcatenate((Boolean) options.get("concatenate"));
    bfOptions.setCrop((Boolean) options.get("crop"));
    bfOptions.setGroupFiles((Boolean) options.get("groupFiles"));
    bfOptions.setUngroupFiles((Boolean) options.get("ungroupFiles"));
    bfOptions.setOpenAllSeries((Boolean) options.get("openAllSeries"));
    bfOptions.setShowMetadata((Boolean) options.get("showMetadata"));
    bfOptions.setShowOMEXML((Boolean) options.get("showOMEXML"));
    bfOptions.setShowROIs((Boolean) options.get("showROIs"));
    bfOptions.setSpecifyRanges((Boolean) options.get("specifyRanges"));
   if ((Boolean) options.get("splitC")) {
    bfOptions.setSplitChannels(true);
}
if ((Boolean) options.get("splitZ")) {
    bfOptions.setSplitFocalPlanes(true);
}
if ((Boolean) options.get("splitT")) {
    bfOptions.setSplitTimepoints(true);
}

    bfOptions.setSwapDimensions((Boolean) options.get("swapDimensions"));
    bfOptions.setVirtual((Boolean) options.get("virtual"));
    bfOptions.setStitchTiles((Boolean) options.get("stitchTiles"));
    bfOptions.setForceThumbnails((Boolean) options.get("forceThumbnails"));
    bfOptions.setUpgradeCheck((Boolean) options.get("upgradeCheck"));
    bfOptions.setQuiet((Boolean) options.get("quiet"));

    // String/Combo Optionen konvertieren
    
    // 1. Color Mode
    String colorMode = (String) options.get("colorMode");
    if (COLOR_MODE_COMPOSITE.equals(colorMode)) bfOptions.setColorMode(ImporterOptions.COLOR_MODE_COMPOSITE);
    else if (COLOR_MODE_GRAYSCALE.equals(colorMode)) bfOptions.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
    else bfOptions.setColorMode(ImporterOptions.COLOR_MODE_DEFAULT);

    // 2. Stack Order
    String order = (String) options.get("stackOrder");
    if (ORDER_XYCZT.equals(order)) bfOptions.setStackOrder(ImporterOptions.ORDER_XYCZT);
    else if (ORDER_XYZCT.equals(order)) bfOptions.setStackOrder(ImporterOptions.ORDER_XYZCT);

    // 3. Stack Format (View)
    String view = (String) options.get("stackFormat");
    if (VIEW_STANDARD.equals(view)) bfOptions.setStackFormat(ImporterOptions.VIEW_STANDARD);
    else if (VIEW_HYPERSTACK.equals(view)) bfOptions.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
    else if (VIEW_BROWSER.equals(view)) bfOptions.setStackFormat(ImporterOptions.VIEW_BROWSER);

    // 4. ROI Mode
    String rois = (String) options.get("roisMode");
    if (ROIS_MODE_MANAGER.equals(rois)) bfOptions.setROIsMode(ImporterOptions.ROIS_MODE_MANAGER);
    else bfOptions.setROIsMode(ImporterOptions.ROIS_MODE_OVERLAY);

    // Bilder öffnen
    ImagePlus[] imps = BF.openImagePlus(bfOptions);
    
    if (imps != null) {
        for (ImagePlus imp : imps) {
            imp.show();
        }
    }
			// For now, show success message
			IJ.showMessage("Import Settings", "File: " + new File(selectedFilePath).getName() + "\nOptions configured successfully!\nReady for import.");
			dialogCanceled = false;
			shell.close();
		} catch(Exception e) {
			LOGGER.severe("Error during import: " + e.getMessage());
			IJ.error("Import Error", "Error: " + e.getMessage());
		}
	}

	private void closeReader() {

		if(reader != null) {
			try {
				reader.close();
			} catch(IOException e) {
				LOGGER.warning("Error closing reader: " + e.getMessage());
			}
			reader = null;
		}
	}
	// -- Section creation methods --

	private void createStackViewingSection(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		group.setText("Stack Viewing");
		group.setLayout(new GridLayout(2, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		Label stackFormatLabel = new Label(group, SWT.NONE);
		stackFormatLabel.setText("Stack Format:");
		stackFormatLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		stackFormatChoice = new Combo(group, SWT.DROP_DOWN | SWT.READ_ONLY);
		stackFormatChoice.setItems(getStackFormats());
		stackFormatChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		stackFormatChoice.addSelectionListener(createSelectionListener(() -> verifyOptions(stackFormatChoice)));
		stackFormatChoice.addFocusListener(createFocusListener(stackFormatChoice));
		infoTable.put(stackFormatChoice, "Choose how to display the image stack. Hyperstack is recommended for modern workflows.");
		infoTable.put(stackFormatLabel, "Choose how to display the image stack. Hyperstack is recommended for modern workflows.");
		Label stackOrderLabel = new Label(group, SWT.NONE);
		stackOrderLabel.setText("Stack Order:");
		stackOrderLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		stackOrderChoice = new Combo(group, SWT.DROP_DOWN | SWT.READ_ONLY);
		stackOrderChoice.setItems(getStackOrders());
		stackOrderChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		stackOrderChoice.addFocusListener(createFocusListener(stackOrderChoice));
		infoTable.put(stackOrderChoice, "Choose the order of dimensions in the stack (X, Y, C, Z, T).");
		infoTable.put(stackOrderLabel, "Choose the order of dimensions in the stack (X, Y, C, Z, T).");
	}

	private void createMetadataViewingSection(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		group.setText("Metadata Viewing");
		group.setLayout(new GridLayout(2, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		showMetadataBox = createCheckbox(group, "Show Metadata", "Display image metadata table after import.", 2);
		showMetadataBox.addSelectionListener(createSelectionListener(() -> verifyOptions(showMetadataBox)));
		showOMEXMLBox = createCheckbox(group, "Show OME-XML", "Display OME-XML metadata document.", 2);
		showROIsBox = createCheckbox(group, "Show ROIs", "Display region of interest (ROI) information.", 2);
		showROIsBox.addSelectionListener(createSelectionListener(() -> verifyOptions(showROIsBox)));
		Label roisModeLabel = new Label(group, SWT.NONE);
		roisModeLabel.setText("ROI Mode:");
		roisModeLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		roisModeChoice = new Combo(group, SWT.DROP_DOWN | SWT.READ_ONLY);
		roisModeChoice.setItems(getROIsModes());
		roisModeChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		roisModeChoice.addFocusListener(createFocusListener(roisModeChoice));
		infoTable.put(roisModeChoice, "Choose how to handle ROIs: Manager displays in ROI Manager, Overlay displays as image overlay.");
		infoTable.put(roisModeLabel, "Choose how to handle ROIs: Manager displays in ROI Manager, Overlay displays as image overlay.");
	}

	private void createDatasetOrganizationSection(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		group.setText("Dataset Organization");
		group.setLayout(new GridLayout(1, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		groupFilesBox = createCheckbox(group, "Group Files", "Automatically group related image files together.", 1);
		groupFilesBox.addSelectionListener(createSelectionListener(() -> verifyOptions(groupFilesBox)));
		ungroupFilesBox = createCheckbox(group, "Ungroup Files", "Treat each file as a separate image series.", 1);
		swapDimsBox = createCheckbox(group, "Swap Dimensions", "Exchange X and Y dimension mappings.", 1);
		openAllSeriesBox = createCheckbox(group, "Open All Series", "Import all image series found in the file.", 1);
		concatenateBox = createCheckbox(group, "Concatenate", "Combine multiple series into a single image.", 1);
		concatenateBox.addSelectionListener(createSelectionListener(() -> verifyOptions(concatenateBox)));
		stitchTilesBox = createCheckbox(group, "Stitch Tiles", "Automatically stitch together tiled image regions.", 1);
	}

	private void createMemoryManagementSection(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		group.setText("Memory Management");
		group.setLayout(new GridLayout(1, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		virtualBox = createCheckbox(group, "Use Virtual Stack", "Load image planes on-demand to conserve memory.", 1);
		virtualBox.addSelectionListener(createSelectionListener(() -> verifyOptions(virtualBox)));
		specifyRangesBox = createCheckbox(group, "Specify Ranges", "Choose which Z planes and timepoints to load.", 1);
		cropBox = createCheckbox(group, "Crop", "Crop the image to a specified region during import.", 1);
	}

	private void createColorOptionsSection(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		group.setText("Color Options");
		group.setLayout(new GridLayout(2, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		Label colorModeLabel = new Label(group, SWT.NONE);
		colorModeLabel.setText("Color Mode:");
		colorModeLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		colorModeChoice = new Combo(group, SWT.DROP_DOWN | SWT.READ_ONLY);
		colorModeChoice.setItems(getColorModes());
		colorModeChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		colorModeChoice.addSelectionListener(createSelectionListener(() -> verifyOptions(colorModeChoice)));
		colorModeChoice.addFocusListener(createFocusListener(colorModeChoice));
		infoTable.put(colorModeChoice, "Choose how to handle color channels: Composite, Grayscale, or Default.");
		infoTable.put(colorModeLabel, "Choose how to handle color channels: Composite, Grayscale, or Default.");
		autoscaleBox = createCheckbox(group, "Autoscale", "Automatically scale pixel intensities to the full range.", 2);
	}

	private void createSplitWindowsSection(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		group.setText("Split Into Separate Windows");
		group.setLayout(new GridLayout(1, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		splitCBox = createCheckbox(group, "Split Channels", "Open each color channel in a separate image window.", 1);
		splitZBox = createCheckbox(group, "Split Focal Planes", "Open each Z plane in a separate image window.", 1);
		splitTBox = createCheckbox(group, "Split Timepoints", "Open each timepoint in a separate image window.", 1);
	}

	private void createAdvancedOptionsSection(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		group.setText("Advanced Options");
		group.setLayout(new GridLayout(1, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		forceThumbnailsBox = createCheckbox(group, "Force Thumbnails", "Load thumbnail images if available.", 1);
		upgradeCheckBox = createCheckbox(group, "Check for Upgrade", "Check for newer versions of Bio-Formats at startup.", 1);
		quietBox = createCheckbox(group, "Quiet Mode", "Suppress informational log messages.", 1);
		// Create info pane
		Label infoLabel = new Label(group, SWT.NONE);
		infoLabel.setText("Information:");
		infoLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
		infoPane = new StyledText(group, SWT.BORDER | SWT.V_SCROLL | SWT.WRAP | SWT.READ_ONLY);
		GridData infoPaneData = new GridData(SWT.FILL, SWT.FILL, true, true);
		infoPaneData.heightHint = 150;
		infoPane.setLayoutData(infoPaneData);
		infoPane.setText(INFO_DEFAULT);
		infoPane.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));
	}
	// -- Helper methods --

	private Button createCheckbox(Composite parent, String label, String info, int colspan) {

		Button button = new Button(parent, SWT.CHECK);
		button.setText(label);
		GridData gd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		gd.horizontalSpan = colspan;
		button.setLayoutData(gd);
		button.addFocusListener(createFocusListener(button));
		infoTable.put(button, info);
		return button;
	}

	private FocusListener createFocusListener(Control control) {

		return new FocusListener() {

			@Override
			public void focusGained(FocusEvent e) {

				String text = infoTable.get(control);
				if(text != null && infoPane != null) {
					infoPane.setText(text);
				}
			}

			@Override
			public void focusLost(FocusEvent e) {

				// No action needed
			}
		};
	}

	private SelectionListener createSelectionListener(Runnable action) {

		return new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				action.run();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {

				widgetSelected(e);
			}
		};
	}
	// -- Result harvesting --

	private void harvestResults() {

		options.put("autoscale", autoscaleBox.getSelection());
		options.put("colorMode", colorModeChoice.getText());
		options.put("concatenate", concatenateBox.getSelection());
		options.put("crop", cropBox.getSelection());
		options.put("groupFiles", groupFilesBox.getSelection());
		options.put("ungroupFiles", ungroupFilesBox.getSelection());
		options.put("openAllSeries", openAllSeriesBox.getSelection());
		options.put("showMetadata", showMetadataBox.getSelection());
		options.put("showOMEXML", showOMEXMLBox.getSelection());
		options.put("showROIs", showROIsBox.getSelection());
		options.put("roisMode", roisModeChoice.getText());
		options.put("specifyRanges", specifyRangesBox.getSelection());
		options.put("splitZ", splitZBox.getSelection());
		options.put("splitT", splitTBox.getSelection());
		options.put("splitC", splitCBox.getSelection());
		options.put("stackFormat", stackFormatChoice.getText());
		options.put("stackOrder", stackOrderChoice.getText());
		options.put("swapDimensions", swapDimsBox.getSelection());
		options.put("virtual", virtualBox.getSelection());
		options.put("stitchTiles", stitchTilesBox.getSelection());
		options.put("forceThumbnails", forceThumbnailsBox.getSelection());
		options.put("upgradeCheck", upgradeCheckBox.getSelection());
		options.put("quiet", quietBox.getSelection());
		LOGGER.info("Import options: " + options);
	}
	// -- Option verification --

	private void verifyOptions(Control src) {

		// Record GUI state
		boolean autoscaleEnabled = autoscaleBox.isEnabled();
		boolean colorModeEnabled = colorModeChoice.isEnabled();
		boolean concatenateEnabled = concatenateBox.isEnabled();
		boolean cropEnabled = cropBox.isEnabled();
		boolean groupFilesEnabled = groupFilesBox.isEnabled();
		boolean ungroupFilesEnabled = ungroupFilesBox.isEnabled();
		boolean openAllSeriesEnabled = openAllSeriesBox.isEnabled();
		boolean showMetadataEnabled = showMetadataBox.isEnabled();
		boolean roisModeEnabled = roisModeChoice.isEnabled();
		boolean specifyRangesEnabled = specifyRangesBox.isEnabled();
		boolean splitZEnabled = splitZBox.isEnabled();
		boolean splitTEnabled = splitTBox.isEnabled();
		boolean splitCEnabled = splitCBox.isEnabled();
		boolean stackOrderEnabled = stackOrderChoice.isEnabled();
		boolean swapDimsEnabled = swapDimsBox.isEnabled();
		boolean virtualEnabled = virtualBox.isEnabled();
		// Get current values
		boolean isAutoscale = autoscaleBox.getSelection();
		String colorModeValue = colorModeChoice.getText();
		boolean isConcatenate = concatenateBox.getSelection();
		boolean isCrop = cropBox.getSelection();
		boolean isGroupFiles = groupFilesBox.getSelection();
		boolean isOpenAllSeries = openAllSeriesBox.getSelection();
		boolean isShowMetadata = showMetadataBox.getSelection();
		String stackFormatValue = stackFormatChoice.getText();
		String stackOrderValue = stackOrderChoice.getText();
		boolean isVirtual = virtualBox.getSelection();
		boolean isStackNone = stackFormatValue.equals(VIEW_NONE);
		boolean isStackStandard = stackFormatValue.equals(VIEW_STANDARD);
		boolean isStackHyperstack = stackFormatValue.equals(VIEW_HYPERSTACK);
		boolean isStackBrowser = stackFormatValue.equals(VIEW_BROWSER);
		boolean isStackImage5D = stackFormatValue.equals(VIEW_IMAGE_5D);
		boolean isStackView5D = stackFormatValue.equals(VIEW_VIEW_5D);
		// == Stack viewing ==
		stackOrderEnabled = isStackStandard;
		if(src == stackFormatChoice) {
			if(isStackHyperstack || isStackBrowser || isStackImage5D) {
				stackOrderValue = ORDER_XYCZT;
			} else if(isStackView5D) {
				stackOrderValue = ORDER_XYZCT;
			} else {
				stackOrderValue = ORDER_DEFAULT;
			}
			setComboSelection(stackOrderChoice, stackOrderValue);
		}
		// == Metadata viewing ==
		showMetadataEnabled = !isStackNone;
		// == Memory management ==
		virtualEnabled = !isStackNone && !isStackImage5D && !isStackView5D && !isConcatenate;
		if(!virtualEnabled)
			isVirtual = false;
		else if(src == stackFormatChoice && isStackBrowser)
			isVirtual = true;
		specifyRangesEnabled = !isStackNone && !isVirtual;
		cropEnabled = !isStackNone && !isVirtual;
		// == Color options ==
		colorModeEnabled = !isStackImage5D && !isStackView5D && !isStackStandard;
		if(!colorModeEnabled)
			colorModeValue = COLOR_MODE_DEFAULT;
		autoscaleEnabled = !isVirtual;
		// == Split into separate windows ==
		boolean splitEnabled = !isStackNone && !isStackBrowser && !isStackImage5D && !isStackView5D;
		splitCEnabled = splitEnabled;
		splitZEnabled = splitEnabled;
		splitTEnabled = splitEnabled;
		// Update state of each option
		autoscaleBox.setEnabled(autoscaleEnabled);
		colorModeChoice.setEnabled(colorModeEnabled);
		concatenateBox.setEnabled(concatenateEnabled);
		cropBox.setEnabled(cropEnabled);
		groupFilesBox.setEnabled(groupFilesEnabled);
		ungroupFilesBox.setEnabled(ungroupFilesEnabled);
		openAllSeriesBox.setEnabled(openAllSeriesEnabled);
		showMetadataBox.setEnabled(showMetadataEnabled);
		roisModeChoice.setEnabled(roisModeEnabled);
		specifyRangesBox.setEnabled(specifyRangesEnabled);
		splitZBox.setEnabled(splitZEnabled);
		splitTBox.setEnabled(splitTEnabled);
		splitCBox.setEnabled(splitCEnabled);
		stackOrderChoice.setEnabled(stackOrderEnabled);
		swapDimsBox.setEnabled(swapDimsEnabled);
		virtualBox.setEnabled(virtualEnabled);
		// Set states
		autoscaleBox.setSelection(isAutoscale);
		setComboSelection(colorModeChoice, colorModeValue);
		concatenateBox.setSelection(isConcatenate);
		cropBox.setSelection(isCrop);
		groupFilesBox.setSelection(isGroupFiles);
		openAllSeriesBox.setSelection(isOpenAllSeries);
		showMetadataBox.setSelection(isShowMetadata);
		setComboSelection(stackOrderChoice, stackOrderValue);
		virtualBox.setSelection(isVirtual);
	}

	private void setComboSelection(Combo combo, String value) {

		if(value == null)
			return;
		int index = -1;
		String[] items = combo.getItems();
		for(int i = 0; i < items.length; i++) {
			if(items[i].equals(value)) {
				index = i;
				break;
			}
		}
		if(index >= 0) {
			combo.select(index);
		}
	}
	// -- Configuration getters --

	private String[] getStackFormats() {

		return new String[]{VIEW_NONE, VIEW_STANDARD, VIEW_HYPERSTACK, VIEW_BROWSER, VIEW_IMAGE_5D, VIEW_VIEW_5D};
	}

	private String[] getStackOrders() {

		return new String[]{ORDER_DEFAULT, ORDER_XYCZT, ORDER_XYZCT};
	}

	private String[] getColorModes() {

		return new String[]{COLOR_MODE_DEFAULT, COLOR_MODE_COMPOSITE, COLOR_MODE_GRAYSCALE};
	}

	private String[] getROIsModes() {

		return new String[]{ROIS_MODE_MANAGER, ROIS_MODE_OVERLAY};
	}
}
