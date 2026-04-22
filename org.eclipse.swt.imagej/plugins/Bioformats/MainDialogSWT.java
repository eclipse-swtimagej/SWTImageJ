/*
 * #%L
 * Bio-Formats Plugins for ImageJ: a collection of ImageJ plugins including the
 * Bio-Formats Importer, Bio-Formats Exporter, Bio-Formats Macro Extensions,
 * Data Browser and Stack Slicer.
 * %%
 * Copyright (C) 2006 - 2017 Open Microscopy Environment:
 * - Board of Regents of the University of Wisconsin-Madison
 * - Glencoe Software, Inc.
 * - University of Dundee
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import java.util.HashMap;
import java.util.Map;

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

/**
 * Bio-Formats Importer general options dialog box (SWT port).
 * This dialog provides comprehensive options for configuring Bio-Formats image import.
 */
public class MainDialogSWT {
	// -- Constants --

	/** Initial message to display in help text box. */
	public static final String INFO_DEFAULT = "Select an option for a detailed explanation. " + "Documentation written by Glen MacDonald and Curtis Rueden.";
	// -- Fields --
	protected Shell shell;
	protected ImportProcess process;
	protected ImporterOptions options;
	protected boolean dialogCanceled = false;
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
	// -- Constructor --

	/** Creates a general options dialog for the Bio-Formats Importer. */
	public MainDialogSWT(ImportProcess process) {

		this.process = process;
		this.options = process.getOptions();
	}
	// -- Dialog methods --

	/**
	 * Display the dialog and harvest results.
	 * 
	 * @return true if dialog was accepted, false if canceled
	 */
	public boolean show() {

		shell = new Shell(Display.getDefault(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		shell.setText("Bio-Formats Import Options");
		shell.setLayout(new GridLayout(1, false));
		shell.setSize(1000, 700);
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

	private void createDialogContent() {

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
		buttonComposite.setLayout(new GridLayout(2, true));
		buttonComposite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
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
		stackFormatChoice.setItems(options.getStackFormats());
		stackFormatChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		stackFormatChoice.addSelectionListener(createSelectionListener(() -> verifyOptions(stackFormatChoice)));
		stackFormatChoice.addFocusListener(createFocusListener(stackFormatChoice));
		infoTable.put(stackFormatChoice, options.getStackFormatInfo());
		infoTable.put(stackFormatLabel, options.getStackFormatInfo());
		Label stackOrderLabel = new Label(group, SWT.NONE);
		stackOrderLabel.setText("Stack Order:");
		stackOrderLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		stackOrderChoice = new Combo(group, SWT.DROP_DOWN | SWT.READ_ONLY);
		stackOrderChoice.setItems(options.getStackOrders());
		stackOrderChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		stackOrderChoice.addFocusListener(createFocusListener(stackOrderChoice));
		infoTable.put(stackOrderChoice, options.getStackOrderInfo());
		infoTable.put(stackOrderLabel, options.getStackOrderInfo());
	}

	private void createMetadataViewingSection(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		group.setText("Metadata Viewing");
		group.setLayout(new GridLayout(2, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		showMetadataBox = createCheckbox(group, "Show Metadata", options.getShowMetadataInfo(), 2);
		showMetadataBox.addSelectionListener(createSelectionListener(() -> verifyOptions(showMetadataBox)));
		showOMEXMLBox = createCheckbox(group, "Show OME-XML", options.getShowOMEXMLInfo(), 2);
		showROIsBox = createCheckbox(group, "Show ROIs", options.getShowROIsInfo(), 2);
		showROIsBox.addSelectionListener(createSelectionListener(() -> verifyOptions(showROIsBox)));
		Label roisModeLabel = new Label(group, SWT.NONE);
		roisModeLabel.setText("ROI Mode:");
		roisModeLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		roisModeChoice = new Combo(group, SWT.DROP_DOWN | SWT.READ_ONLY);
		roisModeChoice.setItems(options.getROIsModes());
		roisModeChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		roisModeChoice.addFocusListener(createFocusListener(roisModeChoice));
		infoTable.put(roisModeChoice, options.getROIsModeInfo());
		infoTable.put(roisModeLabel, options.getROIsModeInfo());
	}

	private void createDatasetOrganizationSection(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		group.setText("Dataset Organization");
		group.setLayout(new GridLayout(1, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		groupFilesBox = createCheckbox(group, "Group Files", options.getGroupFilesInfo(), 1);
		groupFilesBox.addSelectionListener(createSelectionListener(() -> verifyOptions(groupFilesBox)));
		ungroupFilesBox = createCheckbox(group, "Ungroup Files", options.getUngroupFilesInfo(), 1);
		swapDimsBox = createCheckbox(group, "Swap Dimensions", options.getSwapDimensionsInfo(), 1);
		openAllSeriesBox = createCheckbox(group, "Open All Series", options.getOpenAllSeriesInfo(), 1);
		concatenateBox = createCheckbox(group, "Concatenate", options.getConcatenateInfo(), 1);
		concatenateBox.addSelectionListener(createSelectionListener(() -> verifyOptions(concatenateBox)));
		stitchTilesBox = createCheckbox(group, "Stitch Tiles", options.getStitchTilesInfo(), 1);
	}

	private void createMemoryManagementSection(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		group.setText("Memory Management");
		group.setLayout(new GridLayout(1, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		virtualBox = createCheckbox(group, "Use Virtual Stack", options.getVirtualInfo(), 1);
		virtualBox.addSelectionListener(createSelectionListener(() -> verifyOptions(virtualBox)));
		specifyRangesBox = createCheckbox(group, "Specify Ranges", options.getSpecifyRangesInfo(), 1);
		cropBox = createCheckbox(group, "Crop", options.getCropInfo(), 1);
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
		colorModeChoice.setItems(options.getColorModes());
		colorModeChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		colorModeChoice.addSelectionListener(createSelectionListener(() -> verifyOptions(colorModeChoice)));
		colorModeChoice.addFocusListener(createFocusListener(colorModeChoice));
		infoTable.put(colorModeChoice, options.getColorModeInfo());
		infoTable.put(colorModeLabel, options.getColorModeInfo());
		autoscaleBox = createCheckbox(group, "Autoscale", options.getAutoscaleInfo(), 2);
	}

	private void createSplitWindowsSection(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		group.setText("Split Into Separate Windows");
		group.setLayout(new GridLayout(1, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		splitCBox = createCheckbox(group, "Split Channels", options.getSplitChannelsInfo(), 1);
		splitZBox = createCheckbox(group, "Split Focal Planes", options.getSplitFocalPlanesInfo(), 1);
		splitTBox = createCheckbox(group, "Split Timepoints", options.getSplitTimepointsInfo(), 1);
	}

	private void createAdvancedOptionsSection(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		group.setText("Advanced Options");
		group.setLayout(new GridLayout(1, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		forceThumbnailsBox = createCheckbox(group, "Force Thumbnails", "Force loading of thumbnail images.", 1);
		upgradeCheckBox = createCheckbox(group, "Check for Upgrade", "Check for newer versions of Bio-Formats.", 1);
		quietBox = createCheckbox(group, "Quiet Mode", "Suppress informational messages.", 1);
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

	protected void harvestResults() {

		options.setAutoscale(autoscaleBox.getSelection());
		options.setColorMode(options.getColorModes()[colorModeChoice.getSelectionIndex()]);
		options.setConcatenate(concatenateBox.getSelection());
		options.setCrop(cropBox.getSelection());
		options.setGroupFiles(groupFilesBox.getSelection());
		options.setUngroupFiles(ungroupFilesBox.getSelection());
		options.setOpenAllSeries(openAllSeriesBox.getSelection());
		options.setShowMetadata(showMetadataBox.getSelection());
		options.setShowOMEXML(showOMEXMLBox.getSelection());
		options.setShowROIs(showROIsBox.getSelection());
		options.setROIsMode(options.getROIsModes()[roisModeChoice.getSelectionIndex()]);
		options.setSpecifyRanges(specifyRangesBox.getSelection());
		options.setSplitFocalPlanes(splitZBox.getSelection());
		options.setSplitTimepoints(splitTBox.getSelection());
		options.setSplitChannels(splitCBox.getSelection());
		options.setStackFormat(options.getStackFormats()[stackFormatChoice.getSelectionIndex()]);
		options.setStackOrder(options.getStackOrders()[stackOrderChoice.getSelectionIndex()]);
		options.setSwapDimensions(swapDimsBox.getSelection());
		options.setVirtual(virtualBox.getSelection());
		options.setStitchTiles(stitchTilesBox.getSelection());
	}
	// -- Option verification --

	/** Ensures that the options dialog has no mutually exclusive options. */
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
		boolean showOMEXMLEnabled = showOMEXMLBox.isEnabled();
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
		boolean isUngroupFiles = ungroupFilesBox.getSelection();
		boolean isOpenAllSeries = openAllSeriesBox.getSelection();
		boolean isShowMetadata = showMetadataBox.getSelection();
		boolean isShowOMEXML = showOMEXMLBox.getSelection();
		boolean isShowROIs = showROIsBox.getSelection();
		String roisModeValue = roisModeChoice.getText();
		boolean isSpecifyRanges = specifyRangesBox.getSelection();
		boolean isSplitZ = splitZBox.getSelection();
		boolean isSplitT = splitTBox.getSelection();
		boolean isSplitC = splitCBox.getSelection();
		String stackFormatValue = stackFormatChoice.getText();
		boolean isStackNone = stackFormatValue.equals(ImporterOptions.VIEW_NONE);
		boolean isStackStandard = stackFormatValue.equals(ImporterOptions.VIEW_STANDARD);
		boolean isStackHyperstack = stackFormatValue.equals(ImporterOptions.VIEW_HYPERSTACK);
		boolean isStackBrowser = stackFormatValue.equals(ImporterOptions.VIEW_BROWSER);
		boolean isStackImage5D = stackFormatValue.equals(ImporterOptions.VIEW_IMAGE_5D);
		boolean isStackView5D = stackFormatValue.equals(ImporterOptions.VIEW_VIEW_5D);
		String stackOrderValue = stackOrderChoice.getText();
		boolean isSwap = swapDimsBox.getSelection();
		boolean isVirtual = virtualBox.getSelection();
		// == Stack viewing ==
		stackOrderEnabled = isStackStandard;
		if(src == stackFormatChoice) {
			if(isStackHyperstack || isStackBrowser || isStackImage5D) {
				stackOrderValue = ImporterOptions.ORDER_XYCZT;
			} else if(isStackView5D) {
				stackOrderValue = ImporterOptions.ORDER_XYZCT;
			} else {
				stackOrderValue = ImporterOptions.ORDER_DEFAULT;
			}
			setComboSelection(stackOrderChoice, stackOrderValue);
		}
		// == Metadata viewing ==
		showMetadataEnabled = !isStackNone;
		if(!showMetadataEnabled)
			isShowMetadata = true;
		roisModeEnabled = isShowROIs;
		if(!roisModeEnabled)
			roisModeValue = ImporterOptions.ROIS_MODE_MANAGER;
		// == Dataset organization ==
		if(src == stackFormatChoice && isStackBrowser) {
			isGroupFiles = true;
		} else if(!options.isLocal()) {
			isGroupFiles = false;
			groupFilesEnabled = false;
		}
		if(options.isOMERO()) {
			isUngroupFiles = false;
			ungroupFilesEnabled = false;
		}
		// == Memory management ==
		virtualEnabled = !isStackNone && !isStackImage5D && !isStackView5D && !isConcatenate;
		if(!virtualEnabled)
			isVirtual = false;
		else if(src == stackFormatChoice && isStackBrowser)
			isVirtual = true;
		specifyRangesEnabled = !isStackNone && !isVirtual;
		if(!specifyRangesEnabled)
			isSpecifyRanges = false;
		cropEnabled = !isStackNone && !isVirtual;
		if(!cropEnabled)
			isCrop = false;
		// == Color options ==
		colorModeEnabled = !isStackImage5D && !isStackView5D && !isStackStandard;
		if(!colorModeEnabled)
			colorModeValue = ImporterOptions.COLOR_MODE_DEFAULT;
		autoscaleEnabled = !isVirtual;
		if(!autoscaleEnabled)
			isAutoscale = false;
		// == Split into separate windows ==
		boolean splitEnabled = !isStackNone && !isStackBrowser && !isStackImage5D && !isStackView5D;
		splitCEnabled = splitEnabled;
		if(!splitCEnabled)
			isSplitC = false;
		splitZEnabled = splitEnabled;
		if(!splitZEnabled)
			isSplitZ = false;
		splitTEnabled = splitEnabled;
		if(!splitTEnabled)
			isSplitT = false;
		// Update state of each option
		autoscaleBox.setEnabled(autoscaleEnabled);
		colorModeChoice.setEnabled(colorModeEnabled);
		concatenateBox.setEnabled(concatenateEnabled);
		cropBox.setEnabled(cropEnabled);
		groupFilesBox.setEnabled(groupFilesEnabled);
		ungroupFilesBox.setEnabled(ungroupFilesEnabled);
		openAllSeriesBox.setEnabled(openAllSeriesEnabled);
		showMetadataBox.setEnabled(showMetadataEnabled);
		showOMEXMLBox.setEnabled(showOMEXMLEnabled);
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
		ungroupFilesBox.setSelection(isUngroupFiles);
		openAllSeriesBox.setSelection(isOpenAllSeries);
		showMetadataBox.setSelection(isShowMetadata);
		showOMEXMLBox.setSelection(isShowOMEXML);
		setComboSelection(roisModeChoice, roisModeValue);
		specifyRangesBox.setSelection(isSpecifyRanges);
		splitZBox.setSelection(isSplitZ);
		splitTBox.setSelection(isSplitT);
		splitCBox.setSelection(isSplitC);
		setComboSelection(stackOrderChoice, stackOrderValue);
		swapDimsBox.setSelection(isSwap);
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
}