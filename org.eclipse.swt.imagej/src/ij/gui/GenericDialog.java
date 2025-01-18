package ij.gui;

import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.TextField;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Macro;
import ij.Prefs;
import ij.WindowManager;
import ij.io.OpenDialog;
import ij.macro.Interpreter;
import ij.macro.MacroRunner;
import ij.plugin.ScreenGrabber;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.swt.WindowSwt;
import ij.util.Tools;

/**
 * This class is a customizable modal dialog box. Here is an example
 * GenericDialog with one string field and two numeric fields:
 * 
 * <pre>
 * public class Generic_Dialog_Example implements PlugIn {
 * 	static String title = "Example";
 * 	static int width = 512, height = 512;
 * 
 * 	public void run(String arg) {
 * 		GenericDialog gd = new GenericDialog("New Image");
 * 		gd.addStringField("Title: ", title);
 * 		gd.addNumericField("Width: ", width, 0);
 * 		gd.addNumericField("Height: ", height, 0);
 * 		gd.showDialog();
 * 		if (gd.wasCanceled())
 * 			return;
 * 		title = gd.getNextString();
 * 		width = (int) gd.getNextNumber();
 * 		height = (int) gd.getNextNumber();
 * 		IJ.newImage(title, "8-bit", width, height, 1);
 * 	}
 * }
 * </pre>
 * 
 * To work with macros, the first word of each component label must be unique.
 * If this is not the case, add underscores, which will be converted to spaces
 * when the dialog is displayed. For example, change the checkbox labels "Show
 * Quality" and "Show Residue" to "Show_Quality" and "Show_Residue".
 */
public class GenericDialog implements WindowSwt, org.eclipse.swt.events.ModifyListener,
		org.eclipse.swt.events.FocusListener, org.eclipse.swt.events.KeyListener, org.eclipse.swt.events.ShellListener,
		org.eclipse.swt.events.SelectionListener {

	protected Vector numberField, stringField, checkbox, choice, slider, radioButtonGroups;
	protected org.eclipse.swt.widgets.Text textArea1, textArea2;
	protected Vector defaultValues, defaultText, defaultStrings, defaultChoiceIndexes;
	protected Label theLabel;
	private org.eclipse.swt.widgets.Button okay;
	private org.eclipse.swt.widgets.Button cancel;
	private org.eclipse.swt.widgets.Button no, help;
	private String helpLabel = "Help";
	private boolean wasCanceled, wasOKed;
	private int nfIndex, sfIndex, cbIndex, choiceIndex, textAreaIndex, radioButtonIndex;
	private GridBagConstraints c;
	private boolean firstNumericField = true;
	private boolean firstSlider = true;
	private boolean invalidNumber;
	private String errorMessage;
	private Hashtable labels;
	private boolean macro;
	private String macroOptions;
	private boolean addToSameRow;
	private boolean addToSameRowCalled;
	private int topInset, leftInset, bottomInset;
	private boolean customInsets;
	private Vector sliderIndexes, sliderScales, sliderDigits;
	private org.eclipse.swt.widgets.Button previewCheckbox; // the "Preview" Checkbox, if any
	private Vector dialogListeners; // the Objects to notify on user input
	private PlugInFilterRunner pfr; // the PlugInFilterRunner for automatic preview
	private String previewLabel = " Preview";
	private final static String previewRunning = "wait...";
	private boolean recorderOn; // whether recording is allowed (after the dialog is closed)
	private char echoChar;
	private boolean hideCancelButton;
	private boolean centerDialog = true;
	private String helpURL;
	private boolean smartRecording;
	private Vector imagePanels;
	protected static GenericDialog instance;
	private boolean firstPaint = true;
	private boolean fontSizeSet;
	private boolean showDialogCalled;
	private boolean optionsRecorded; // have dialogListeners been called to record options?
	private org.eclipse.swt.widgets.Label lastLabelAdded;
	private int[] windowIDs;
	private String[] windowTitles;
	protected Shell shell;
	private static ArrayList<Shell> oldShell = new ArrayList<Shell>();
	protected Object result;
	private String cancelButtonText = "Cancel";
	private String okayButtonText = "  OK  ";
	public boolean closeFinally = false;
	private boolean createNoButton;
	private String noButtonText;
	private Point customShellSize = null;

	/**
	 * Creates a new GenericDialog with the specified title. Uses the current image
	 * image window as the parent frame or the ImageJ frame if no image windows are
	 * open. Dialog parameters are recorded by ImageJ's command recorder but this
	 * requires that the first word of each label be unique.
	 */
	public GenericDialog(String title) {

		this(title, null, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
	}

	public GenericDialog(String title, int mode) {

		this(title, null, mode);
	}

	public GenericDialog(String title, Shell parent) {

		this(title, parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
	}

	public Shell getShell() {

		return shell;
	}

	/** Creates a new GenericDialog using the specified title and parent frame. */
	public GenericDialog(String title, Shell parent, int style) {

		macroOptions = Macro.getOptions();
		macro = macroOptions != null;
		Display.getDefault().syncExec(() -> {

			customShellSize = null;
			if (parent != null) {
				shell = new Shell(shell, style);
			} else {
				shell = new Shell(Display.getDefault(), style);
			}
			/* Delete old GenericDialog which were made invisible only! */
			if (oldShell.isEmpty() == false) {
				Shell shell2 = oldShell.get(0);
				shell2.close();
				oldShell.clear();
			}
			GenericDialog.this.instance = GenericDialog.this;
			shell.setText(title);
			ImageJ ij = IJ.getInstance();
			if (ij != null) {
				org.eclipse.swt.graphics.Font font = ij.getFont();
				setFont(font);
			}
			GridLayout layout = new GridLayout();
			layout.numColumns = 1;
			layout.makeColumnsEqualWidth = true;
			shell.setLayout(layout);
			shell.addKeyListener(GenericDialog.this);
			shell.addShellListener(GenericDialog.this);

		});
	}

	public void setShellSize(Point size) {

		customShellSize = size;
	}

	/**
	 * Adds a numeric field. The first word of the label must be unique or command
	 * recording will not work.
	 * 
	 * @param label        the label
	 * @param defaultValue value to be initially displayed
	 */
	public void addNumericField(String label, double defaultValue) {

		int decimalPlaces = (int) defaultValue == defaultValue ? 0 : 3;
		int columnWidth = decimalPlaces == 3 ? 8 : 6;
		addNumericField(label, defaultValue, decimalPlaces, columnWidth, null);
	}

	/**
	 * Adds a numeric field. The first word of the label must be unique or command
	 * recording will not work.
	 * 
	 * @param label        the label
	 * @param defaultValue value to be initially displayed
	 * @param digits       number of digits to right of decimal point
	 */
	public void addNumericField(String label, double defaultValue, int digits) {

		addNumericField(label, defaultValue, digits, 6, null);
	}

	/**
	 * Adds a numeric field. The first word of the label must be unique or command
	 * recording will not work.
	 * 
	 * @param label        the label
	 * @param defaultValue value to be initially displayed
	 * @param digits       number of digits to right of decimal point
	 * @param columns      width of field in characters
	 * @param units        a string displayed to the right of the field
	 */
	public void addNumericField(String label, double defaultValue, int digitss, int columns, String units) {

		// int[] digits = new int[] { digitss };
		AtomicReference<Integer> digits = new AtomicReference<Integer>();
		digits.set(digitss);
		Display.getDefault().syncExec(() -> {

			String label2 = label;
			if (label2.indexOf('_') != -1)
				label2 = label2.replace('_', ' ');
			org.eclipse.swt.widgets.Label fieldLabel = makeLabel(label2);
			GenericDialog.this.lastLabelAdded = fieldLabel;
			if (addToSameRow) {
				// c.insets.left = 0;
				addToSameRow = false;
			}
			if (numberField == null) {
				numberField = new Vector(5);
				defaultValues = new Vector(5);
				defaultText = new Vector(5);
			}
			boolean scientificNotationAsNeeded = false;
			if (digits.get() < 0) {
				digits.set(-digits.get());
				scientificNotationAsNeeded = true;
			}
			String defaultString = IJ.d2s(defaultValue, digits.get());
			if (scientificNotationAsNeeded)
				defaultString = ij.measure.ResultsTable.d2s(defaultValue, digits.get());
			if (Double.isNaN(defaultValue))
				defaultString = "";
			// if (firstNumericField) tf.selectAll();
			firstNumericField = false;
			Text tf;
			if (units == null || units.equals("")) {
				tf = new Text(shell, SWT.SINGLE | SWT.BORDER);
				tf.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
				tf.setText(defaultString);
				tf.setMessage(label);
				// if (IJ.isLinux()) tf.setBackground(Color.white);
				// tf.addActionListener(this);
				tf.addModifyListener(GenericDialog.this);
				tf.addFocusListener(GenericDialog.this);
				tf.addKeyListener(GenericDialog.this);
				numberField.addElement(tf);
				defaultValues.addElement(Double.valueOf(defaultValue));
				defaultText.addElement(tf.getText());
				tf.setEditable(true);
			} else {
				Composite panel = new Composite(shell, SWT.NONE);
				GridLayout layout = new GridLayout(2, true);
				panel.setLayout(layout);
				layout.marginHeight = 0;
				layout.marginWidth = 0;
				panel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				tf = new Text(panel, SWT.BORDER | SWT.SINGLE);
				tf.setText(defaultString);
				tf.setMessage(label);
				tf.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				tf.addModifyListener(GenericDialog.this);
				tf.addFocusListener(GenericDialog.this);
				tf.addKeyListener(GenericDialog.this);
				numberField.addElement(tf);
				defaultValues.addElement(Double.valueOf(defaultValue));
				defaultText.addElement(tf.getText());
				tf.setEditable(true);
				Label lab = new Label(panel, SWT.NONE);
				lab.setText(" " + units);
			}
			if (IJ.recording() || macro)
				saveLabel(tf, label);

		});
	}

	private Label makeLabel(String label) {

		if (IJ.isMacintosh())
			label += " ";
		// return new Label(label);
		Label lab = new Label(shell, SWT.NONE);
		lab.setText(label);
		return lab;
	}

	private Label makeLabel(String label, Composite comp) {

		if (IJ.isMacintosh())
			label += " ";
		// return new Label(label);
		Label lab = new Label(comp, SWT.NONE);
		lab.setText(label);
		return lab;
	}

	/**
	 * Saves the label for given component, for macro recording and for accessing
	 * the component in macros.
	 */
	private void saveLabel(Object component, String label) {

		if (labels == null)
			labels = new Hashtable();
		if (label.length() > 0)
			label = Macro.trimKey(label.trim());
		if (label.length() > 0 && hasLabel(label)) { // not a unique label?
			label += "_0";
			for (int n = 1; hasLabel(label); n++) { // while still not a unique label
				label = label.substring(0, label.lastIndexOf('_')); // remove counter
				label += "_" + n;
			}
		}
		labels.put(component, label);
	}

	/**
	 * Returns whether the list of labels for macro recording or macro creation
	 * contains a given label.
	 */
	private boolean hasLabel(String label) {

		for (Object o : labels.keySet())
			if (labels.get(o).equals(label))
				return true;
		return false;
	}

	/**
	 * Adds an 8 column text field.
	 * 
	 * @param label       the label
	 * @param defaultText the text initially displayed
	 */
	public void addStringField(String label, String defaultText) {

		addStringField(label, defaultText, 8);
	}

	/**
	 * Adds a text field.
	 * 
	 * @param label       the label
	 * @param defaultText text initially displayed
	 * @param columns     width of the text field. If columns is 8 or more,
	 *                    additional items may be added to this line with
	 *                    addToSameRow()
	 */
	public void addStringField(String label, String defaultText, int columns) {

		addStringField(null, label, defaultText, columns);
	}

	/**
	 * Adds a text field.
	 * 
	 * @param panel       a SWT Composite
	 * @param label       the label
	 * @param defaultText text initially displayed
	 * @param columns     width of the text field. If columns is 8 or more,
	 *                    additional items may be added to this line with
	 *                    addToSameRow() Note: In SWT an exception is thrown if the
	 *                    setText(defaultText) is is null. In AWT a TextField will
	 *                    add a "" String if the default text is null!!!
	 */
	public void addStringField(Composite panelAdd, String labell, String defaultTextt, int columns) {

		AtomicReference<String> label = new AtomicReference<String>();
		AtomicReference<String> defaultText = new AtomicReference<String>();
		label.set(labell);
		defaultText.set(defaultTextt);
		Display.getDefault().syncExec(() -> {

			Composite panel;
			if (panelAdd == null) {
				panel = new Composite(shell, SWT.NONE);
				GridData gd_composite = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 8);
				panel.setLayoutData(gd_composite);
			} else {
				panel = panelAdd;
			}
			if (addToSameRow && label.get().equals("_"))
				label.set("");
			String label2 = label.get();
			if (label2.indexOf('_') != -1)
				label2 = label2.replace('_', ' ');
			if (addToSameRow) {
				Label fieldLabel = makeLabel(label2, panel);
				GenericDialog.this.lastLabelAdded = fieldLabel;
				GridLayout layout = new GridLayout(2, true);
				panel.setLayout(layout);
				layout.marginHeight = 0;
				layout.marginWidth = 0;
			} else {
				Label fieldLabel = makeLabel(label2, panel);
				GenericDialog.this.lastLabelAdded = fieldLabel;
				GridLayout layout = new GridLayout(1, true);
				panel.setLayout(layout);
				layout.marginHeight = 0;
				layout.marginWidth = 0;
			}
			if (stringField == null) {
				stringField = new Vector(4);
				defaultStrings = new Vector(4);
			}
			Text tf = null;
			if (panel != null) {
				tf = new Text(panel, SWT.SINGLE | SWT.BORDER);
				tf.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
			}
			/*
			 * Important note: In SWT an exception is thrown if the setText(defaultText) is
			 * is null. In AWT a TextField will add a "" String if the default text is
			 * null!!! See:
			 * https://docs.oracle.com/javase/9/docs/api/java/awt/TextField.html#TextField-
			 * java.lang.String- When using dragAndDrop this causes an SWT error(the string
			 * is null) using drag and drop of folders using the virtual stack option (drag
			 * onto the right expansion arrows of the toolbar!)
			 **/
			if (defaultText.get() == null) {
				defaultText.set("");
			}
			tf.setText(defaultText.get());
			tf.setMessage(label.get());
			tf.addModifyListener(GenericDialog.this);
			tf.setEchoChar(echoChar);
			echoChar = 0;
			tf.addFocusListener(GenericDialog.this);
			tf.addKeyListener(GenericDialog.this);
			tf.setEditable(true);
			stringField.addElement(tf);
			defaultStrings.addElement(defaultText.get());
			new DragAndDropMacro(tf);
			if (IJ.recording() || macro)
				saveLabel(tf, label.get());

		});
	}

	/** Sets the echo character for the next string field. */
	public void setEchoChar(char echoChar) {

		this.echoChar = echoChar;
	}

	/**
	 * Adds a directory text field and "Browse" button, where the field width is
	 * determined by the length of 'defaultPath', with a minimum of 25 columns. Use
	 * getNextString to retrieve the * directory path. Call
	 * OpenDialog.setDefaultDirectory() to set the default directory used when the
	 * user clicks on "Browse". Based on the addDirectoryField() method in Fiji's
	 * GenericDialogPlus class.
	 * 
	 * @see ij.io.OpenDialog#setDefaultDirectory(String)
	 */
	public void addDirectoryField(String label, String defaultPath) {

		int columns = defaultPath != null ? Math.max(defaultPath.length(), 25) : 25;
		if (columns > 60)
			columns = 60;
		addDirectoryField(label, defaultPath, columns);
	}

	public void addDirectoryField(String label, String defaultPathh, int columns) {

		String defaultPath[] = new String[] { defaultPathh };
		Display.getDefault().syncExec(() -> {

			defaultPath[0] = IJ.addSeparator(defaultPath[0]);
			Composite panel = new Composite(shell, SWT.NONE);
			panel.setLayout(new RowLayout(SWT.HORIZONTAL));
			addStringField(panel, label, defaultPath[0], columns);
			if (GraphicsEnvironment.isHeadless())
				return;
			Text text = (Text) stringField.lastElement();
			Button btnBrowse = new Button(panel, SWT.NONE);
			btnBrowse.setText("Browse");
			btnBrowse.addSelectionListener(new BrowseButtonListener(label, text, "dir"));
			if (IJ.recording() || macro)
				saveLabel(panel, label);

		});
	}

	/**
	 * Adds a file text field and "Browse" button, where the field width is
	 * determined by the length of 'defaultPath', with a minimum of 25 columns. Use
	 * getNextString to retrieve the file path. Based on the addFileField() method
	 * in Fiji's GenericDialogPlus class.
	 */
	public void addFileField(String label, String defaultPath) {

		int columns = defaultPath != null ? Math.max(defaultPath.length(), 25) : 25;
		if (columns > 60)
			columns = 60;
		addFileField(label, defaultPath, columns);
	}

	/**
	 * Add button to the dialog
	 * 
	 * @param label    button label
	 * @param listener listener to handle the action when pressing the button
	 */
	public void addButton(String label, SelectionListener listener) {

		Display.getDefault().syncExec(() -> {

			if (GraphicsEnvironment.isHeadless())
				return;
			Composite panel = new Composite(shell, SWT.NONE);
			panel.setLayout(new RowLayout(SWT.HORIZONTAL));
			org.eclipse.swt.widgets.Button button = new org.eclipse.swt.widgets.Button(panel, SWT.NONE);
			button.addSelectionListener(listener);
			button.addKeyListener(GenericDialog.this);

		});
	}

	public void addFileField(String label, String defaultPath, int columns) {

		Display.getDefault().syncExec(() -> {

			addStringField(label, defaultPath, columns);
			Composite panel = new Composite(shell, SWT.NONE);
			if (GraphicsEnvironment.isHeadless())
				return;
			RowLayout rl_composite_1 = new RowLayout(SWT.HORIZONTAL);
			rl_composite_1.fill = true;
			panel.setLayout(rl_composite_1);
			Text text = (Text) stringField.lastElement();
			Button btnBrowse = new Button(panel, SWT.NONE);
			btnBrowse.setText("Browse");
			btnBrowse.addSelectionListener(new BrowseButtonListener(label, text, "file"));
			if (IJ.recording() || macro)
				saveLabel(panel, label);

		});
	}

	/**
	 * Adds a popup menu that lists the currently open images. Call getNextImage()
	 * to retrieve the selected image. Based on the addImageChoice() method in
	 * Fiji's GenericDialogPlus class.
	 * 
	 * @param label        the label
	 * @param defaultImage the image title initially selected in the menu or the
	 *                     first image if null
	 */
	public void addImageChoice(String label, String defaultImage) {

		if (windowTitles == null) {
			windowIDs = WindowManager.getIDList();
			if (windowIDs == null)
				windowIDs = new int[0];
			windowTitles = new String[windowIDs.length];
			for (int i = 0; i < windowIDs.length; i++) {
				ImagePlus image = WindowManager.getImage(windowIDs[i]);
				windowTitles[i] = image == null ? "" : image.getTitle();
			}
		}
		addChoice(label, windowTitles, defaultImage);
	}

	public ImagePlus getNextImage() {

		int index = getNextChoiceIndex();
		return index < 0 ? null : WindowManager.getImage(windowIDs[index]);
	}

	/**
	 * Adds a group of choices to the dialog with menu items taken from the
	 * <code>enum</code> class of the specified default item (enum constant). Calls
	 * the original (string-based)
	 * {@link GenericDialogSwt#addChoice(String, String[], String)} method. * Usage
	 * example:
	 * 
	 * <pre>
	 * import ij.process.AutoThresholder.Method;
	 * ...
	 * Method method = Method.Otsu;
	 * 
	 * GenericDialog gd = new GenericDialog("Select AutoThresholder Method");
	 * gd.addEnumChoice("All threshold methods", method);
	 * ...
	 * gd.showDialog();
	 * ...
	 * method = gd.getNextEnumChoice(Method.class);
	 * </pre>
	 * 
	 * @param <E>         the generic enum type containing the items to chose from
	 * @param label       the label displayed for this choice group
	 * @param defaultItem the menu item initially selected
	 * 
	 * @see #addEnumChoice(String, Enum[], Enum)
	 * @see #getNextEnumChoice(Class)
	 */
	public <E extends Enum<E>> void addEnumChoice(String label, E defaultItem) {

		Class<E> enumClass = defaultItem.getDeclaringClass();
		E[] enums = enumClass.getEnumConstants();
		String[] items = new String[enums.length];
		for (int i = 0; i < enums.length; i++) {
			items[i] = enums[i].name();
		}
		this.addChoice(label, items, defaultItem.name());
	}

	/**
	 * Adds a group of choices to the dialog with menu items taken from the supplied
	 * array of <code>enum</code> elements. This allows to present only a subset of
	 * enum choices in a specified order. A default item (enum constant) must be
	 * specified which, if {@code null} of not contained in the enum array, is
	 * replaced by the first element of the enum array. Calls the original
	 * (string-based) {@link GenericDialog#addChoice(String, String[], String)}
	 * method. Usage example:
	 * 
	 * <pre>
	 * import ij.process.AutoThresholder.Method;
	 * ...
	 * Method[] selectMethods = {Method.Triangle, Method.Otsu, Method.Huang};
	 * Method method = Method.Otsu;
	 * 
	 * GenericDialog gd = new GenericDialog("Select AutoThresholder Method");
	 * gd.addEnumChoice("Select threshold methods", selectMethods, method);
	 * ...
	 * gd.showDialog();
	 * ...
	 * method = gd.getNextEnumChoice(Method.class);
	 * </pre>
	 * 
	 * @param <E>         the generic enum type containing the items to choose from
	 * @param label       the label displayed for this choice group
	 * @param enumArray   an array of enum items (of type E)
	 * @param defaultItem the menu item initially selected (of type E, may be
	 *                    {@code null})
	 * 
	 * @see #addEnumChoice(String, Enum)
	 * @see #getNextEnumChoice(Class)
	 */
	public <E extends Enum<E>> void addEnumChoice(String label, E[] enumArray, E defaultItem) {

		String[] items = new String[enumArray.length];
		boolean contained = false; // to check if defaultItem is contained in enumArray
		for (int i = 0; i < enumArray.length; i++) {
			if (enumArray[i] == defaultItem) {
				contained = true;
			}
			items[i] = enumArray[i].name();
		}
		if (!contained) {
			defaultItem = enumArray[0];
		}
		this.addChoice(label, items, defaultItem.name());
	}

	/**
	 * Returns the selected item in the next enum choice menu. Note that 'enumClass'
	 * is required to infer the proper enum type. Throws
	 * {@code IllegalArgumentException} if the selected item is not a defined
	 * constant in the specified enum class.
	 * 
	 * @param <E>       the generic enum type
	 * @param enumClass the enum type
	 * @return the selected item
	 */
	public <E extends Enum<E>> E getNextEnumChoice(Class<E> enumClass) {

		String choiceString = this.getNextChoice();
		return Enum.valueOf(enumClass, choiceString);
	}

	/**
	 * Adds a checkbox.
	 * 
	 * @param label        the label
	 * @param defaultValue the initial state
	 */
	public void addCheckbox(String label, boolean defaultValue) {

		addCheckbox(label, defaultValue, false);
	}

	/**
	 * Adds a checkbox; does not make it recordable if isPreview is true. With
	 * isPreview true, the checkbox can be referred to as previewCheckbox from
	 * hereon.
	 */
	private void addCheckbox(String label, boolean defaultValue, boolean isPreview) {

		Display.getDefault().syncExec(() -> {

			String label2 = label;
			if (label2.indexOf('_') != -1)
				label2 = label2.replace('_', ' ');
			if (checkbox == null)
				checkbox = new Vector(4);
			Button cb = new Button(shell, SWT.CHECK);
			cb.setText(label2);
			cb.setSelection(defaultValue);
			cb.addSelectionListener(GenericDialog.this);
			cb.addKeyListener(GenericDialog.this);
			checkbox.addElement(cb);
			if (!isPreview && (Recorder.record || macro)) // preview checkbox is not recordable
				saveLabel(cb, label);
			if (isPreview)
				previewCheckbox = cb;

		});
	}

	/**
	 * Adds a checkbox labelled "Preview" for "automatic" preview. The reference to
	 * this checkbox can be retrieved by getPreviewCheckbox() and it provides the
	 * additional method previewRunning for optical feedback while preview is
	 * prepared. PlugInFilters can have their "run" method automatically called for
	 * preview under the following conditions: - the PlugInFilter must pass a
	 * reference to itself (i.e., "this") as an argument to the AddPreviewCheckbox -
	 * it must implement the DialogListener interface and set the filter parameters
	 * in the dialogItemChanged method. - it must have DIALOG and PREVIEW set in its
	 * flags. A previewCheckbox is always off when the filter is started and does
	 * not get recorded by the Macro Recorder.
	 *
	 * @param pfr A reference to the PlugInFilterRunner calling the PlugInFilter if
	 *            automatic preview is desired, null otherwise.
	 */
	public void addPreviewCheckbox(PlugInFilterRunner pfr) {

		if (previewCheckbox != null)
			return;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp != null && imp.isComposite() && ((CompositeImage) imp).getMode() == IJ.COMPOSITE)
			return;
		this.pfr = pfr;
		addCheckbox(previewLabel, false, true);
	}

	/**
	 * Add the preview checkbox with user-defined label; for details see the
	 * addPreviewCheckbox method with standard "Preview" label. Adds the checkbox
	 * when the current image is a CompositeImage in "Composite" mode, unlike the
	 * one argument version. Note that a GenericDialog can have only one
	 * PreviewCheckbox.
	 */
	public void addPreviewCheckbox(PlugInFilterRunner pfr, String label) {

		if (previewCheckbox != null)
			return;
		previewLabel = label;
		this.pfr = pfr;
		addCheckbox(previewLabel, false, true);
	}

	/**
	 * Adds a group of checkboxs using a grid layout.
	 * 
	 * @param rows          the number of rows
	 * @param columns       the number of columns
	 * @param labels        the labels
	 * @param defaultValues the initial states
	 */
	public void addCheckboxGroup(int rows, int columns, String[] labels, boolean[] defaultValues) {

		addCheckboxGroup(rows, columns, labels, defaultValues, null);
	}

	/**
	 * Adds a group of checkboxs using a grid layout.
	 * 
	 * @param rows          the number of rows
	 * @param columns       the number of columns
	 * @param labels        the labels
	 * @param defaultValues the initial states
	 * @param headings      the column headings Example:
	 *                      http://imagej.net/ij/plugins/multi-column-dialog/index.html
	 */
	public void addCheckboxGroup(int rows, int columns, String[] labels, boolean[] defaultValues, String[] headings) {

		// Group group = new Group(shell, SWT.NONE);
		// group.setLayout(new GridLayout(columns, false));
		// group.setText(label);
		Display.getDefault().syncExec(() -> {

			Composite panel = new Composite(shell, SWT.NONE);
			panel.setLayout(new GridLayout(columns, false));
			int nRows = headings != null ? rows + 1 : rows;
			int startCBIndex = cbIndex;
			if (checkbox == null)
				checkbox = new Vector(12);
			if (headings != null) {
				org.eclipse.swt.graphics.Font font = new org.eclipse.swt.graphics.Font(Display.getDefault(),
						new FontData("SansSerif", 12, SWT.BOLD));
				for (int i = 0; i < columns; i++) {
					if (i > headings.length - 1 || headings[i] == null)
						new Label(panel, SWT.NONE).setText("");
					else {
						Label label = new Label(panel, SWT.NONE);
						label.setText(headings[i]);
						label.setFont(font);
					}
				}
			}
			int i1 = 0;
			int[] index = new int[labels.length];
			for (int row = 0; row < rows; row++) {
				for (int col = 0; col < columns; col++) {
					int i2 = col * rows + row;
					if (i2 >= labels.length)
						break;
					index[i1] = i2;
					String label = labels[i1];
					if (label == null || label.length() == 0) {
						Label lbl = new Label(panel, SWT.NONE);
						i1++;
						continue;
					}
					if (label.indexOf('_') != -1)
						label = label.replace('_', ' ');
					Button cb = new Button(panel, SWT.CHECK);
					cb.setText(label);
					checkbox.addElement(cb);
					cb.setSelection(defaultValues[i1]);
					cb.addSelectionListener(GenericDialog.this);
					if (IJ.recording() || macro)
						saveLabel(cb, labels[i1]);
					i1++;
				}
			}
			addToSameRow = false;

		});
	}

	/**
	 * Adds a radio button group.
	 * 
	 * @param label       group label (or null)
	 * @param items       radio button labels
	 * @param rows        number of rows
	 * @param columns     number of columns
	 * @param defaultItem button initially selected
	 */
	public void addRadioButtonGroup(String labell, String[] items, int rows, int columns, String defaultItem) {

		AtomicReference<String> label = new AtomicReference<String>();
		label.set(labell);
		Display.getDefault().syncExec(() -> {

			Group cg = new Group(shell, SWT.NONE);
			cg.setLayout(new GridLayout(columns, false));
			addToSameRow = false;
			int n = items.length;
			for (int i = 0; i < n; i++) {
				Button cb = new Button(cg, SWT.RADIO);
				cb.setText(items[i]);
				if (items[i].equals(defaultItem)) {
					cb.setSelection(true);
				}
			}
			if (radioButtonGroups == null)
				radioButtonGroups = new Vector();
			radioButtonGroups.addElement(cg);
			if (label.get() == null || label.get().equals("")) {
				label.set("rbg" + radioButtonGroups.size());
			} else {
				cg.setText(label.get());
			}
			if (IJ.recording() || macro)
				saveLabel(cg, label.get());

		});
	}

	/**
	 * Adds a popup menu.
	 * 
	 * @param label       the label
	 * @param items       the menu items
	 * @param defaultItem the menu item initially selected
	 */
	public void addChoice(String label, String[] items, String defaultItem) {

		Display.getDefault().syncExec(() -> {

			String label2 = label;
			if (label2.indexOf('_') != -1)
				label2 = label2.replace('_', ' ');
			Label fieldLabel = makeLabel(label2);
			GenericDialog.this.lastLabelAdded = fieldLabel;
			if (choice == null) {
				choice = new Vector(4);
				defaultChoiceIndexes = new Vector(4);
			}
			Combo thisChoice = new Combo(shell, SWT.DROP_DOWN | SWT.READ_ONLY);
			thisChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			thisChoice.addSelectionListener(GenericDialog.this);
			thisChoice.setItems(items);
			thisChoice.select(0);
			for (int i = 0; i < items.length; i++) {
				if (defaultItem != null) {
					if (thisChoice.getItem(i).equals(defaultItem)) {
						thisChoice.select(i);
					}
				}
			}
			choice.addElement(thisChoice);
			int index = thisChoice.getSelectionIndex();
			defaultChoiceIndexes.addElement(Integer.valueOf(index));
			if (IJ.recording() || macro)
				saveLabel(thisChoice, label);

		});
	}

	/** Adds a message consisting of one or more lines of text. */
	public void addMessage(String text) {

		addMessage(text, null, null);
	}

	/**
	 * Adds a message consisting of one or more lines of text, which will be
	 * displayed using the specified font.
	 */
	public void addMessage(String text, org.eclipse.swt.graphics.Font font) {

		addMessage(text, font, null);
	}

	/**
	 * Adds a message consisting of one or more lines of text, which will be
	 * displayed using the specified font and color.
	 */
	public void addMessage(String text, org.eclipse.swt.graphics.Font font, org.eclipse.swt.graphics.Color color) {

		Display.getDefault().syncExec(() -> {

			theLabel = null;
			if (text.indexOf('\n') >= 0) {
				theLabel = new Label(shell, SWT.WRAP);
				GridData gd_composite = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 8);
				theLabel.setLayoutData(gd_composite);
				theLabel.setText(text);
			} else {
				theLabel = new Label(shell, SWT.NONE);
				GridData gd_composite = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 8);
				theLabel.setLayoutData(gd_composite);
				theLabel.setText(text);
			}
			if (addToSameRow) {
				addToSameRow = false;
			} else {
			}
			if (font != null) {
				theLabel.setFont(font);
			}
			if (color != null)
				theLabel.setForeground(color);

		});
	}

	/**
	 * Adds one or two (side by side) text areas. Append "SCROLLBARS_VERTICAL_ONLY"
	 * to the text of the first text area to get vertical scrollbars and
	 * "SCROLLBARS_BOTH" to get both vertical and horizontal scrollbars.
	 * 
	 * @param text1   initial contents of the first text area
	 * @param text2   initial contents of the second text area or null
	 * @param rows    the number of rows
	 * @param columns the number of columns
	 */
	public void addTextAreas(String text11, String text2, int rows, int columns) {

		String[] text1 = new String[] { text11 };
		Display.getDefault().syncExec(() -> {

			if (textArea1 != null)
				return;
			Composite panel = new Composite(shell, SWT.NONE);
			GridData gd_composite = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 8);
			panel.setLayoutData(gd_composite);
			if (text2 != null) {
				panel.setLayout(new GridLayout(2, true));
			} else {
				panel.setLayout(new GridLayout(1, true));
			}
			textArea1 = new Text(panel, SWT.BORDER | SWT.MULTI);
			gd_composite.heightHint = rows * textArea1.getLineHeight();
			textArea1.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			textArea1.addModifyListener(GenericDialog.this);
			if (text1[0] == null) {
				text1[0] = "";
			}
			textArea1.setText(text1[0]);
			if (text1 != null && text1[0].endsWith("SCROLLBARS_BOTH")) {
				textArea1.dispose();
				textArea1 = new Text(panel, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI);
				textArea1.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				textArea1.addModifyListener(GenericDialog.this);
				text1[0] = text1[0].substring(0, text1[0].length() - 15);
				textArea1.setText(text1[0]);
			}
			if (text1 != null && text1[0].endsWith("SCROLLBARS_VERTICAL_ONLY")) {
				textArea1.dispose();
				textArea1 = new Text(panel, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
				textArea1.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				textArea1.addModifyListener(GenericDialog.this);
				text1[0] = text1[0].substring(0, text1[0].length() - 24);
				textArea1.setText(text1[0]);
			}
			if (text2 != null) {
				textArea2 = new Text(panel, SWT.BORDER | SWT.MULTI);
				textArea2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				textArea2.addModifyListener(GenericDialog.this);
				textArea2.setText(text2);
			}

		});
	}

	/**
	 * Adds a slider (scroll bar) to the dialog box. Floating point values are used
	 * if (maxValue-minValue)<=5.0 and either defaultValue or minValue are
	 * non-integer.
	 * 
	 * @param label        the label
	 * @param minValue     the minimum value of the slider
	 * @param maxValue     the maximum value of the slider
	 * @param defaultValue the initial value of the slider
	 */
	public void addSlider(String label, double minValue, double maxValue, double defaultValue) {

		if (defaultValue < minValue)
			defaultValue = minValue;
		if (defaultValue > maxValue)
			defaultValue = maxValue;
		int digits = 0;
		double scale = 1.0;
		if ((maxValue - minValue) <= 5.0
				&& (minValue != (int) minValue || maxValue != (int) maxValue || defaultValue != (int) defaultValue)) {
			scale = 50.0;
			minValue *= scale;
			maxValue *= scale;
			defaultValue *= scale;
			digits = 2;
		}
		addSlider(label, minValue, maxValue, defaultValue, scale, digits);
	}

	/**
	 * This vesion of addSlider() adds a 'stepSize' argument.<br>
	 * Example: http://wsr.imagej.net/macros/SliderDemo.txt
	 */
	public void addSlider(String label, double minValue, double maxValue, double defaultValue, double stepSize) {

		if (stepSize <= 0)
			stepSize = 1;
		int digits = digits(stepSize);
		if (digits == 1 && "Angle:".equals(label))
			digits = 2;
		double scale = 1.0 / Math.abs(stepSize);
		if (scale <= 0)
			scale = 1;
		if (defaultValue < minValue)
			defaultValue = minValue;
		if (defaultValue > maxValue)
			defaultValue = maxValue;
		minValue *= scale;
		maxValue *= scale;
		defaultValue *= scale;
		addSlider(label, minValue, maxValue, defaultValue, scale, digits);
	}

	/** Author: Michael Kaul */
	private static int digits(double d) {

		if (d == (int) d)
			return 0;
		String s = Double.toString(d);
		int ePos = s.indexOf("E");
		if (ePos == -1)
			ePos = s.indexOf("e");
		int dotPos = s.indexOf(".");
		int digits = 0;
		if (ePos == -1)
			digits = s.substring(dotPos + 1).length();
		else {
			String number = s.substring(dotPos + 1, ePos);
			if (!number.equals("0"))
				digits += number.length();
			digits = digits - Integer.valueOf(s.substring(ePos + 1));
		}
		return digits;
	}

	private void addSlider(String label, double minValue, double maxValue, double defaultValue, double scale,
			int digits) {

		Display.getDefault().syncExec(() -> {

			int columns = 4 + digits - 2;
			if (columns < 4)
				columns = 4;
			if (minValue < 0.0)
				columns++;
			String mv = IJ.d2s(maxValue, 0);
			if (mv.length() > 4 && digits == 0)
				columns += mv.length() - 4;
			String label2 = label;
			if (label2.indexOf('_') != -1)
				label2 = label2.replace('_', ' ');
			Label fieldLabel = makeLabel(label2);
			GenericDialog.this.lastLabelAdded = fieldLabel;
			if (addToSameRow) {
			} else {
			}
			Composite panel = new Composite(shell, SWT.NONE);
			panel.setLayout(new GridLayout(3, false));
			if (slider == null) {
				slider = new Vector(5);
				sliderIndexes = new Vector(5);
				sliderScales = new Vector(5);
				sliderDigits = new Vector(5);
			}
			Slider s = new Slider(panel, SWT.NONE);
			s.setThumb(1);
			s.setSelection((int) defaultValue);
			s.setMinimum((int) minValue);
			s.setMaximum((int) maxValue + 1);
			s.setIncrement(1);
			s.setPageIncrement(1);
			if (IJ.debugMode)
				IJ.log("Scrollbar: " + scale + " " + defaultValue + " " + minValue + " " + maxValue);
			s.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {

					adjustmentValueChanged(e);
				}
			});
			slider.addElement(s);
			if (IJ.isMacOSX())
				s.addKeyListener(GenericDialog.this);
			s.addMouseWheelListener(new org.eclipse.swt.events.MouseWheelListener() {

				public void mouseScrolled(MouseEvent e) {

					Slider sb = (Slider) e.getSource();
					int value = sb.getSelection() + e.count;
					sb.setSelection(value);
					for (int i = 0; i < slider.size(); i++) {
						if (sb == slider.elementAt(i)) {
							int index = ((Integer) sliderIndexes.get(i)).intValue();
							TextField tf = (TextField) numberField.elementAt(index);
							double scale = ((Double) sliderScales.get(i)).doubleValue();
							int digits = ((Integer) sliderDigits.get(i)).intValue();
							tf.setText("" + IJ.d2s(sb.getSelection() / scale, digits));
						}
					}
				}
			});
			if (numberField == null) {
				numberField = new Vector(5);
				defaultValues = new Vector(5);
				defaultText = new Vector(5);
			}
			if (IJ.isWindows())
				columns -= 2;
			if (columns < 1)
				columns = 1;
			// IJ.log("scale=" + scale + ", columns=" + columns + ", digits=" + digits);
			Text tf = new Text(panel, SWT.BORDER | SWT.MULTI);
			tf.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			tf.setText(IJ.d2s(defaultValue / scale));
			tf.addModifyListener(GenericDialog.this);
			tf.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {

					actionPerformed(e);
				}
			});
			tf.addFocusListener(GenericDialog.this);
			tf.addKeyListener(GenericDialog.this);
			numberField.addElement(tf);
			sliderIndexes.add(Integer.valueOf(numberField.size() - 1));
			sliderScales.add(Double.valueOf(scale));
			sliderDigits.add(Integer.valueOf(digits));
			defaultValues.addElement(Double.valueOf(defaultValue / scale));
			defaultText.addElement(tf.getText());
			tf.setEditable(true);
			firstSlider = false;
			if (IJ.recording() || macro)
				saveLabel(tf, label);

		});
	}
	// Necessary for SWT?
	/*
	 * private TextField newTextField(String txt, int columns) { if (IJ.isLinux())
	 * return new TrimmedTextField(txt,columns); else return new
	 * TextField(txt,columns); }
	 */

	public void addPanel(org.eclipse.swt.widgets.Composite composite) {

		Display.getDefault().syncExec(() -> {

			composite.setParent(shell);

		});
	}
	/** Adds a Panel to the dialog. */
	/*
	 * public void addPanel(Panel panel) { addPanel(panel, GridBagConstraints.WEST,
	 * addToSameRow ? c.insets : getInsets(5,0,0,0)); }
	 */
	/**
	 * Adds a Panel to the dialog with custom contraint and insets. The defaults are
	 * GridBagConstraints.WEST (left justified) and "new Insets(5, 0, 0, 0)" (5
	 * pixels of padding at the top).
	 */
	/*
	 * public void addPanel(Panel panel, int constraints, Insets insets) { if
	 * (addToSameRow) { c.gridx = GridBagConstraints.RELATIVE; addToSameRow = false;
	 * } else { c.gridx = 0; c.gridy++; } c.gridwidth = 2; c.anchor = constraints;
	 * c.insets = insets; add(panel, c); }
	 */

	/** Adds an SWT image to the dialog. */
	public void addImage(ImagePlus image) {

		if (image == null)
			return;
		Display.getDefault().syncExec(() -> {

			ImagePanelSwt imagePanel = new ImagePanelSwt(shell, image);
			imagePanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			// addPanel(imagePanel);
			if (imagePanels == null)
				imagePanels = new Vector();
			imagePanels.add(imagePanel);

		});
	}

	/**
	 * Set the insets (margins), in pixels, that will be used for the next component
	 * added to the dialog (except components added to the same row with
	 * addToSameRow)
	 * 
	 * <pre>
	 Default insets:
	     addMessage: 0,20,0 (empty string) or 10,20,0
	     addCheckbox: 15,20,0 (first checkbox) or 0,20,0
	     addCheckboxGroup: 10,0,0
	     addRadioButtonGroup: 5,10,0
	     addNumericField: 5,0,3 (first field) or 0,0,3
	     addStringField: 5,0,5 (first field) or 0,0,5
	     addChoice: 5,0,5 (first field) or 0,0,5
	 * </pre>
	 */
	public void setInsets(int top, int left, int bottom) {

		/*
		 * topInset = top; leftInset = left; bottomInset = bottom; customInsets = true;
		 */
	}

	/**
	 * Makes the next item appear in the same row as the previous. May be used for
	 * addNumericField, addSlider, addChoice, addCheckbox, addStringField,
	 * addMessage, addPanel, and before the showDialog() method (in the latter case,
	 * the buttons appear to the right of the previous item). Note that addMessage
	 * (and addStringField, if its column width is more than 8) use the remaining
	 * width, so it must be the last item of a row.
	 */
	public void addToSameRow() {

		addToSameRow = true;
		addToSameRowCalled = true;
	}

	/** Sets a replacement label for the "OK" button. */
	public void setOKLabel(String label) {

		okayButtonText = label;
	}

	/** Sets a replacement label for the "Cancel" button. */
	public void setCancelLabel(String label) {

		cancelButtonText = label;
	}

	/** Sets a replacement label for the "Help" button. */
	public void setHelpLabel(String label) {

		helpLabel = label;
	}

	/** Unchanged parameters are not recorder in 'smart recording' mode. */
	public void setSmartRecording(boolean smartRecording) {

		this.smartRecording = smartRecording;
	}

	/** Make this a "Yes No Cancel" dialog. */
	public void enableYesNoCancel() {

		enableYesNoCancel(" Yes ", " No ");
	}

	/**
	 * Make this a "Yes No Cancel" dialog with custom labels. Here is an example:
	 * 
	 * <pre>
	 * GenericDialog gd = new GenericDialog("YesNoCancel Demo");
	 * gd.addMessage("This is a custom YesNoCancel dialog");
	 * gd.enableYesNoCancel("Do something", "Do something else");
	 * gd.showDialog();
	 * if (gd.wasCanceled())
	 * 	IJ.log("User clicked 'Cancel'");
	 * else if (gd.wasOKed())
	 * 	IJ.log("User clicked 'Yes'");
	 * else
	 * 	IJ.log("User clicked 'No'");
	 * </pre>
	 */
	public void enableYesNoCancel(String yesLabel, String noLabel) {

		okayButtonText = yesLabel;
		if (no != null)
			noButtonText = noLabel;
		else if (noLabel != null) {
			createNoButton = true;
			noButtonText = noLabel;
		}
	}

	/** Do not display "Cancel" button. */
	public void hideCancelButton() {

		hideCancelButton = true;
	}
	/*
	 * Insets getInsets(int top, int left, int bottom, int right) { if
	 * (customInsets) { customInsets = false; return new Insets(topInset, leftInset,
	 * bottomInset, 0); } else return new Insets(top, left, bottom, right); }
	 */

	/**
	 * Add an Object implementing the DialogListener interface. This object will be
	 * notified by its dialogItemChanged method of input to the dialog. The first
	 * DialogListener will be also called after the user has typed 'OK' or if the
	 * dialog has been invoked by a macro; it should read all input fields of the
	 * dialog. For other listeners, the OK button will not cause a call to
	 * dialogItemChanged; the CANCEL button will never cause such a call.
	 * 
	 * @param dl the Object that wants to listen.
	 */
	public void addDialogListener(DialogListener dl) {

		if (dialogListeners == null)
			dialogListeners = new Vector();
		dialogListeners.addElement(dl);
	}

	/** Returns true if the user clicked on "Cancel". */
	public boolean wasCanceled() {

		if (wasCanceled && !Thread.currentThread().getName().endsWith("Script_Macro$"))
			Macro.abort();
		return wasCanceled;
	}

	/** Returns true if the user has clicked on "OK" or a macro is running. */
	public boolean wasOKed() {

		return wasOKed || macro;
	}

	/**
	 * Returns the contents of the next numeric field, or NaN if the field does not
	 * contain a number.
	 */
	public double getNextNumber() {

		AtomicReference<String> theText = new AtomicReference<String>();
		if (numberField == null)
			return -1.0;
		Text tf = (Text) numberField.elementAt(nfIndex);
		Display.getDefault().syncExec(() -> {

			theText.set(tf.getText());

		});
		String label = null;
		if (macro) {
			label = (String) labels.get((Object) tf);
			theText.set(Macro.getValue(macroOptions, label, theText.get()));
		}
		String originalText = (String) defaultText.elementAt(nfIndex);
		double defaultValue = ((Double) (defaultValues.elementAt(nfIndex))).doubleValue();
		double value;
		boolean skipRecording = false;
		if (theText.get().equals(originalText)) {
			value = defaultValue;
			if (smartRecording)
				skipRecording = true;
		} else if (theText.get().startsWith("0x")) {
			value = parseHex(theText.get().substring(2));
		} else {
			Double d = getValue(theText.get());
			if (d != null)
				value = d.doubleValue();
			else {
				// Is the value a macro variable?
				if (theText.get().startsWith("&"))
					theText.set(theText.get().substring(1));
				Interpreter interp = Interpreter.getInstance();
				value = interp != null ? interp.getVariable2(theText.get()) : Double.NaN;
				if (Double.isNaN(value)) {
					invalidNumber = true;
					errorMessage = "\"" + theText + "\" is an invalid number";
					value = Double.NaN;
					if (macro) {
						String[] text = new String[1];
						Display.getDefault().syncExec(() -> {

							text[0] = shell.getText();

						});
						IJ.error("Macro Error",
								"Numeric value expected in run() function\n \n" + "   Dialog box title: \"" + text[0]
										+ "\"\n" + "   Key: \"" + label.toLowerCase(Locale.US) + "\"\n"
										+ "   Value or variable name: \"" + theText + "\"");
					}
				}
			}
		}
		if (recorderOn && !skipRecording) {
			recordOption(tf, trim(theText.get()));
		}
		nfIndex++;
		return value;
	}

	int parseHex(String hexString) {

		int n = 0;
		;
		try {
			n = Integer.parseInt(hexString, 16);
		} catch (NumberFormatException e) {
		}
		return n;
	}

	private String trim(String value) {

		if (value.endsWith(".0"))
			value = value.substring(0, value.length() - 2);
		if (value.endsWith(".00"))
			value = value.substring(0, value.length() - 3);
		return value;
	}

	private void recordOption(Object component, String value) {

		if (labels == null)
			return;
		String label = (String) labels.get(component);
		if (value.equals(""))
			value = "[]";
		Recorder.recordOption(label, value);
	}

	private void recordCheckboxOption(Button cb) {

		String label = (String) labels.get((Object) cb);
		if (label != null) {
			AtomicReference<Boolean> selection = new AtomicReference<Boolean>();
			Display.getDefault().syncExec(() -> {

				selection.set(cb.getSelection());

			});
			if (selection.get()) // checked
				Recorder.recordOption(label);
			else if (Recorder.getCommandOptions() == null)
				Recorder.recordOption(" ");
		}
	}

	protected Double getValue(String text) {

		Double d;
		try {
			d = Double.valueOf(text);
		} catch (NumberFormatException e) {
			d = null;
		}
		return d;
	}

	public double parseDouble(String s) {

		if (s == null)
			return Double.NaN;
		double value = Tools.parseDouble(s);
		if (Double.isNaN(value)) {
			if (s.startsWith("&"))
				s = s.substring(1);
			Interpreter interp = Interpreter.getInstance();
			value = interp != null ? interp.getVariable2(s) : Double.NaN;
		}
		return value;
	}

	/**
	 * Returns true if one or more of the numeric fields contained an invalid
	 * number. Must be called after one or more calls to getNextNumber().
	 */
	public boolean invalidNumber() {

		boolean wasInvalid = invalidNumber;
		invalidNumber = false;
		return wasInvalid;
	}

	/**
	 * Returns an error message if getNextNumber was unable to convert a string into
	 * a number, otherwise, returns null.
	 */
	public String getErrorMessage() {

		return errorMessage;
	}

	/** Returns the contents of the next text field. */
	public String getNextString() {

		AtomicReference<String> theText = new AtomicReference<String>();
		if (stringField == null)
			return "";
		Text tf = (Text) (stringField.elementAt(sfIndex));
		Display.getDefault().syncExec(() -> {

			theText.set(tf.getText());

		});
		String label = labels != null ? (String) labels.get((Object) tf) : "";
		boolean numberExpected = theText != null && theText.get().length() > 0
				&& (Character.isDigit(theText.get().charAt(0)) || theText.get().startsWith("-"));
		if (macro) {
			theText.set(Macro.getValue(macroOptions, label, theText.get()));
			if (theText != null && (theText.get().startsWith("&") || numberExpected
					|| label.toLowerCase(Locale.US).startsWith(theText.get()))) {
				// Is the value a macro variable?
				if (theText.get().startsWith("&"))
					theText.set(theText.get().substring(1));
				Interpreter interp = Interpreter.getInstance();
				String s = interp != null ? interp.getVariableAsString(theText.get()) : null;
				if (s != null)
					theText.set(s);
			}
		}
		if (recorderOn && !label.equals("")) {
			String s = theText.get();
			if (s != null && s.length() >= 3 && Character.isLetter(s.charAt(0)) && s.charAt(1) == ':'
					&& s.charAt(2) == '\\')
				s = s.replaceAll("\\\\", "/"); // replace "\" with "/" in Windows file paths
			s = Recorder.fixString(s);
			if (!smartRecording || !s.equals((String) defaultStrings.elementAt(sfIndex)))
				recordOption(tf, s);
			else if (Recorder.getCommandOptions() == null)
				Recorder.recordOption(" ");
		}
		sfIndex++;
		return theText.get();
	}

	/** Returns the state of the next checkbox. */
	public boolean getNextBoolean() {

		AtomicReference<Boolean> state = new AtomicReference<Boolean>();
		if (checkbox == null)
			return false;
		Button cb = (Button) (checkbox.elementAt(cbIndex));
		if (recorderOn)
			recordCheckboxOption(cb);
		Display.getDefault().syncExec(() -> {

			state.set(cb.getSelection());

		});
		if (macro) {
			String label = (String) labels.get((Object) cb);
			String key = Macro.trimKey(label);
			state.set(isMatch(macroOptions, key + " "));
		}
		cbIndex++;
		return state.get();
	}

	// Returns true if s2 is in s1 and not in a bracketed literal (e.g.,
	// "[literal]")
	boolean isMatch(String s1, String s2) {

		if (s1.startsWith(s2))
			return true;
		s2 = " " + s2;
		int len1 = s1.length();
		int len2 = s2.length();
		boolean match, inLiteral = false;
		char c;
		for (int i = 0; i < len1 - len2 + 1; i++) {
			c = s1.charAt(i);
			if (inLiteral && c == ']')
				inLiteral = false;
			else if (c == '[')
				inLiteral = true;
			if (c != s2.charAt(0) || inLiteral || (i > 1 && s1.charAt(i - 1) == '='))
				continue;
			match = true;
			for (int j = 0; j < len2; j++) {
				if (s2.charAt(j) != s1.charAt(i + j)) {
					match = false;
					break;
				}
			}
			if (match)
				return true;
		}
		return false;
	}

	/** Returns the selected item in the next popup menu. */
	public String getNextChoice() {

		AtomicReference<String> item = new AtomicReference<String>();
		if (choice == null)
			return "";
		Combo thisChoice = (Combo) (choice.elementAt(choiceIndex));
		Display.getDefault().syncExec(() -> {

			item.set(thisChoice.getText());

		});
		if (macro) {
			String label = (String) labels.get((Object) thisChoice);
			item.set(Macro.getValue(macroOptions, label, item.get()));
			if (item != null && item.get().startsWith("&")) // value is macro variable
				item.set(getChoiceVariable(item.get()));
		}
		if (recorderOn)
			recordOption(thisChoice, item.get());
		choiceIndex++;
		return item.get();
	}

	/** Returns the index of the selected item in the next popup menu. */
	public int getNextChoiceIndex() {

		if (choice == null)
			return -1;
		AtomicReference<Integer> index = new AtomicReference<Integer>();
		Display.getDefault().syncExec(() -> {

			Combo thisChoice = (Combo) (choice.elementAt(choiceIndex));
			int selectionIndex = thisChoice.getSelectionIndex();
			index.set(selectionIndex);
			if (macro) {
				String label = (String) labels.get((Object) thisChoice);
				String oldItem = thisChoice.getItem(selectionIndex);
				int oldIndex = thisChoice.getSelectionIndex();
				String item = Macro.getValue(macroOptions, label, oldItem);
				if (item != null && item.startsWith("&")) // value is macro variable
					item = getChoiceVariable(item);
				/* We must find the index for SWT! */
				for (int i = 0; i < thisChoice.getItemCount(); i++) {
					if (thisChoice.getItem(i).equals(item)) {
						thisChoice.select(i);
					}
				}
				index.set(thisChoice.getSelectionIndex());
				if (index.get() == oldIndex && !item.equals(oldItem)) {
					// is value a macro variable?
					Interpreter interp = Interpreter.getInstance();
					String s = interp != null ? interp.getStringVariable(item) : null;
					if (s == null)
						IJ.error(shell.getText(), "\"" + item + "\" is not a valid choice for \"" + label + "\"");
					else
						item = s;
				}
			}
			if (recorderOn) {
				int defaultIndex = ((Integer) (defaultChoiceIndexes.elementAt(choiceIndex))).intValue();
				if (!(smartRecording && index.get() == defaultIndex)) {
					String item = thisChoice.getItem(selectionIndex);
					if (!(item.equals("*None*") && shell.getText().equals("Merge Channels")))
						recordOption(thisChoice, thisChoice.getItem(selectionIndex));
				}
			}
			choiceIndex++;

		});
		return index.get();
	}

	/** Returns the selected item in the next radio button group. */
	public String getNextRadioButton() {

		if (radioButtonGroups == null)
			return null;
		AtomicReference<String> itemToReturn = new AtomicReference<String>();
		Display.getDefault().syncExec(() -> {

			Composite cg = (Composite) (radioButtonGroups.elementAt(radioButtonIndex));
			radioButtonIndex++;
			Control[] controls = cg.getChildren();
			Button checkbox = null;
			for (int i = 0; i < controls.length; i++) {
				if (controls[i] instanceof Button) {
					Button b = (Button) controls[i];
					if (b.getSelection()) {
						checkbox = b;
					}
				}
			}
			String item = "null";
			if (checkbox != null) {
				item = checkbox.getText();
				itemToReturn.set(item);
			}
			if (macro) {
				String label = (String) labels.get((Object) cg);
				item = Macro.getValue(macroOptions, label, item);
				itemToReturn.set(item);
			}
			if (recorderOn)
				recordOption(cg, itemToReturn.get());

		});
		return itemToReturn.get();
	}

	private String getChoiceVariable(String item) {

		item = item.substring(1);
		Interpreter interp = Interpreter.getInstance();
		String s = interp != null ? interp.getStringVariable(item) : null;
		if (s == null) {
			double value = interp != null ? interp.getVariable2(item) : Double.NaN;
			if (!Double.isNaN(value)) {
				if ((int) value == value)
					s = "" + (int) value;
				else
					s = "" + value;
			}
		}
		if (s != null)
			item = s;
		return item;
	}

	/** Returns the contents of the next text area. */
	public String getNextText() {

		AtomicReference<String> text = new AtomicReference<String>();
		Display.getDefault().syncExec(() -> {

			String textt = null;
			text.set(textt);
			//
			String key = "text1";
			if (textAreaIndex == 0 && textArea1 != null) {
				text.set(textArea1.getText());
				if (macro)
					text.set(Macro.getValue(macroOptions, "text1", text.get()));
			} else if (textAreaIndex == 1 && textArea2 != null) {
				text.set(textArea2.getText());
				if (macro)
					text.set(Macro.getValue(macroOptions, "text2", text.get()));
				key = "text2";
			}
			textAreaIndex++;
			if (recorderOn && text != null) {
				String text2 = text.get();
				String cmd = Recorder.getCommand();
				if (cmd != null && cmd.equals("Calibrate..."))
					text2 = text2.replace('\n', ' ');
				if (cmd != null && cmd.equals("Convolve...")) {
					if (!text2.endsWith("\n"))
						text2 += "\n";
				}
				text2 = Recorder.fixString(text2);
				Recorder.recordOption(key, text2);
			}

		});
		return text.get();
	}

	/** Displays this dialog box. */
	public void showDialog() {

		Display.getDefault().syncExec(() -> {

			Composite buttons = new Composite(shell, SWT.NONE);
			showDialogCalled = true;
			addToSameRow = false;
			if (macro) {
				/*
				 * Important to set the variable finally execute the close operation (see
				 * closeShell method) in macro mode!
				 */
				closeFinally = true;
				/*
				 * Add the current shell in macro mode to the list so that it can be disposed at
				 * next startup of a GenericDialog. Else a handle error will be the consequences
				 * if too many shells are opened invisible in macro mode!
				 */
				oldShell.add(shell);
				/*
				 * We change this for SWT if this is a macro normally the Frame is disposed.
				 * However in SWT we set the visibility to false and later dispose it! See
				 * shellClosed action!
				 */
				/* We call the same methods as in the closeShell method for a macro! */
				resetCounters();
				/* Generate a typed event! */
				finalizeRecording();
				resetCounters();
				recorderOn = Recorder.record && Recorder.recordInMacros;
				/*
				 * Deprecated (will be generated by the below resetCounters() method)!:
				 * Workaround for the macro execution. No event is generated since we don't do
				 * anything with the SWT shell - Dispose is not an option for this SWT
				 * implementation (see shellClose())!. So we generate a SWT TypedEvent and
				 * notify the listener. In the implementation a TypedEvent is generated to
				 * read-in the default macro values. Necessary, e.g., for the ImageMath class
				 * implementation: Event event = new Event(); event.type = SWT.Activate;
				 * event.widget = shell; TypedEvent e = new TypedEvent(event);
				 * notifyListeners(e);
				 */
			} else {
				if (pfr != null) // prepare preview (not in macro mode): tell the PlugInFilterRunner to listen
					/* To do in SWT! */
					pfr.setDialog(GenericDialog.this);
				if (!hideCancelButton) {
					// cancel.addActionListener(this);
					/*
					 * cancel = new org.eclipse.swt.widgets.Button(buttons, SWT.NONE);
					 * cancel.setText(cancelButtonText); cancel.addSelectionListener(new
					 * SelectionAdapter() {
					 * 
					 * @Override public void widgetSelected(SelectionEvent e) { actionPerformed(e);
					 * } }); cancel.addKeyListener(this);
					 */
				}
				if (no != null) {
					no = new org.eclipse.swt.widgets.Button(buttons, SWT.NONE);
					no.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
					no.setText("No");
					// no.addActionListener(this);
					no.addSelectionListener(GenericDialog.this);
					no.addKeyListener(GenericDialog.this);
				}
				boolean addHelp = helpURL != null;
				/*
				 * if (addHelp) { // help = new Button(helpLabel); help = new
				 * org.eclipse.swt.widgets.Button(buttons, SWT.NONE); help.setText(helpLabel);
				 * // help.addActionListener(this); help.addSelectionListener(new
				 * SelectionAdapter() {
				 * 
				 * @Override public void widgetSelected(SelectionEvent e) { actionPerformed(e);
				 * } }); help.addKeyListener(this); }
				 */
				if (IJ.isWindows() || Prefs.dialogCancelButtonOnRight) {
					okay = new org.eclipse.swt.widgets.Button(buttons, SWT.NONE);
					okay.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
					okay.setText(okayButtonText);
					okay.addSelectionListener(GenericDialog.this);
					okay.addKeyListener(GenericDialog.this);
					if (no != null) {
						no = new org.eclipse.swt.widgets.Button(buttons, SWT.NONE);
						no.setText("No");
						no.addSelectionListener(GenericDialog.this);
						no.addKeyListener(GenericDialog.this);
					}
					if (!hideCancelButton) {
						cancel = new org.eclipse.swt.widgets.Button(buttons, SWT.NONE);
						cancel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
						cancel.setText(cancelButtonText);
						cancel.addSelectionListener(GenericDialog.this);
						cancel.addKeyListener(GenericDialog.this);
					}
					if (addHelp) {
						help = new org.eclipse.swt.widgets.Button(buttons, SWT.NONE);
						help.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
						help.setText(helpLabel);
						help.addSelectionListener(GenericDialog.this);
						help.addKeyListener(GenericDialog.this);
					}
				} else {
					if (addHelp) {
						help = new org.eclipse.swt.widgets.Button(buttons, SWT.NONE);
						help.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
						help.setText(helpLabel);
						help.addSelectionListener(GenericDialog.this);
						help.addKeyListener(GenericDialog.this);
					}
					if (no != null) {
						no = new org.eclipse.swt.widgets.Button(buttons, SWT.NONE);
						no.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
						no.setText("No");
						no.addSelectionListener(GenericDialog.this);
						no.addKeyListener(GenericDialog.this);
					}
					if (!hideCancelButton) {
						cancel = new org.eclipse.swt.widgets.Button(buttons, SWT.NONE);
						cancel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
						cancel.setText(cancelButtonText);
						cancel.addSelectionListener(GenericDialog.this);
					}
					okay = new org.eclipse.swt.widgets.Button(buttons, SWT.NONE);
					okay.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
					okay.setText(okayButtonText);
					okay.addSelectionListener(GenericDialog.this);
					okay.addKeyListener(GenericDialog.this);
				}
				/*
				 * Post event from enableYesNoCancel which is called sometimes before the
				 * buttons are created! They are stord in a string and boolean for SWT!
				 */
				okay.setText(okayButtonText);
				if (no != null)
					no.setText(noButtonText);
				if (createNoButton) {
					no = new org.eclipse.swt.widgets.Button(buttons, SWT.NONE);
					no.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
					no.setText("No");
					no.addSelectionListener(GenericDialog.this);
					no.addKeyListener(GenericDialog.this);
					shell.setText(noButtonText);
				}
				/*
				 * Calculate grid columns for the layout according to the amount of buttons of
				 * the canvas!
				 */
				buttons.setLayout(new GridLayout(buttons.getChildren().length, true));
				buttons.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1));
				instance = GenericDialog.this;
				if (okay != null && numberField == null && stringField == null && checkbox == null && choice == null
						&& slider == null && radioButtonGroups == null && textArea1 == null)
					okay.setFocus();
				setup();
				if (centerDialog) {
					org.eclipse.swt.graphics.Rectangle screenSize = Display.getDefault().getPrimaryMonitor()
							.getBounds();
					shell.setLocation((screenSize.width - shell.getBounds().width) / 2,
							(screenSize.height - shell.getBounds().height) / 2);
				}
				resetCounters();
				/* Open the shell! */
				open();
				GUI.centerOnImageJScreen(GenericDialog.this.shell);
			}

		});
	}

	/**
	 * Open the dialog.
	 * 
	 * @return
	 * 
	 * @return
	 * 
	 * @return the result
	 */
	public void open() {

		shell.open();
		// shell.layout(true, true);
		org.eclipse.swt.graphics.Point newSize;
		if (customShellSize == null) {
			newSize = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
		} else {
			newSize = customShellSize;
		}
		shell.setSize(newSize);
		Display display = shell.getDisplay();
		/* We monitor the visibility instead of a disposed shell! */
		while (shell.isVisible()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	/**
	 * For plugins that read their input only via dialogItemChanged, call it at
	 * least once, then stop recording
	 */
	void finalizeRecording() {

		if (optionsRecorded)
			return;
		optionsRecorded = true;
		if (!wasCanceled && dialogListeners != null && dialogListeners.size() > 0) {
			try {
				resetCounters();
				((DialogListener) dialogListeners.elementAt(0)).dialogItemChanged(this, null);
			} catch (Exception err) { // for exceptions, don't cover the input by a window
				IJ.beep(); // but show them at in the "Log"
				IJ.log("ERROR: " + err + "\nin DialogListener of " + dialogListeners.elementAt(0) + "\nat "
						+ (err.getStackTrace()[0]) + "\nfrom " + (err.getStackTrace()[1]));
			}
			recorderOn = false;
		}
		resetCounters();
	}

	public void setFont(org.eclipse.swt.graphics.Font font) {

		shell.setFont(font);
		fontSizeSet = true;
	}

	/** Reset the counters before reading the dialog parameters */
	public void resetCounters() {

		nfIndex = 0; // prepare for readout
		sfIndex = 0;
		cbIndex = 0;
		choiceIndex = 0;
		textAreaIndex = 0;
		radioButtonIndex = 0;
		invalidNumber = false;
	}

	/** Returns the Vector containing the numeric TextFields. */
	public Vector getNumericFields() {

		return numberField;
	}

	/** Returns the Vector containing the string TextFields. */
	public Vector getStringFields() {

		return stringField;
	}

	/** Returns the Vector containing the Checkboxes. */
	public Vector getCheckboxes() {

		return checkbox;
	}

	/** Returns the Vector containing the Choices. */
	public Vector getChoices() {

		return choice;
	}

	/** Returns the Vector containing the sliders (Scrollbars). */
	public Vector getSliders() {

		return slider;
	}

	/** Returns the Vector that contains the RadioButtonGroups. */
	public Vector getRadioButtonGroups() {

		return radioButtonGroups;
	}

	/** Returns a reference to textArea1. */
	public Text getTextArea1() {

		return textArea1;
	}

	/** Returns a reference to textArea2. */
	public Text getTextArea2() {

		return textArea2;
	}

	/**
	 * Returns a reference to the Label or MultiLineLabel created by the last
	 * addMessage() call. Otherwise returns null.
	 */
	public Label getMessage() {

		return theLabel;
	}

	/** Returns a reference to the Preview checkbox. */
	public Button getPreviewCheckbox() {

		return previewCheckbox;
	}

	/** Returns 'true' if this dialog has a "Preview" checkbox and it is enabled. */
	public boolean isPreviewActive() {

		return previewCheckbox != null && previewCheckbox.getSelection();
	}

	/**
	 * Returns references to the "OK" ("Yes"), "Cancel", "No", and "Help" buttons as
	 * an array of length 4. If a button is not present, the corresponding array
	 * element is null.
	 */
	public Button[] getButtons() {

		return new Button[] { okay, cancel, no, help };
	}

	/**
	 * Used by PlugInFilterRunner to provide visable feedback whether preview is
	 * running or not by switching from "Preview" to "wait..."
	 */
	public void previewRunning(boolean isRunning) {

		if (previewCheckbox != null) {
			Display.getDefault().syncExec(() -> {

				previewCheckbox.setText(isRunning ? previewRunning : previewLabel);

			});
			// if (IJ.isMacOSX()) repaint(); //workaround OSX 10.4 refresh bug
		}
	}

	/** Display dialog centered on the primary screen. */
	public void centerDialog(boolean b) {

		centerDialog = b;
	}

	/* Display the dialog at the specified location. */
	public void setLocation(int x, int y) {

		Display.getDefault().syncExec(() -> {

			shell.setLocation(new org.eclipse.swt.graphics.Point(x, y));
			centerDialog = false;

		});
	}

	public void setDefaultString(int index, String str) {

		if (defaultStrings != null && index >= 0 && index < defaultStrings.size())
			defaultStrings.set(index, str);
	}

	protected void setup() {

	}

	/*
	 * Call both in SWT, the itemChanged and actionPerformed action (both call the
	 * notifyListeners(e)). Called from one SelectionListener interface because a
	 * SWT combo (in AWT a Choice) has no itemChanged method!
	 */
	public void actionPerformed(TypedEvent e) {

		if (e.widget instanceof Button) {
			Button source = (Button) e.widget;
			if (source == okay || source == cancel | source == no) {
				// System.out.println("Source: " + source);
				wasCanceled = source == cancel;
				wasOKed = source == okay;
				dispose();
			} else if (source == help) {
				if (hideCancelButton) {
					if (helpURL != null && helpURL.equals("")) {
						notifyListeners(e);
						return;
					} else {
						wasOKed = true;
						dispose();
					}
				}
				showHelp();
			} else
				notifyListeners(e);
		} else {
			notifyListeners(e);
		}
	}

	@Override
	public void modifyText(ModifyEvent e) {

		textValueChanged(e);
	}

	public void textValueChanged(ModifyEvent e) {

		// TextEvent e
		notifyListeners(e);
		if (slider == null)
			return;
		Object source = e.getSource();
		for (int i = 0; i < slider.size(); i++) {
			int index = ((Integer) sliderIndexes.get(i)).intValue();
			if (source == numberField.elementAt(index)) {
				Text tf = (Text) numberField.elementAt(index);
				double value = Tools.parseDouble(tf.getText());
				if (!Double.isNaN(value)) {
					Slider sb = (Slider) slider.elementAt(i);
					double scale = ((Double) sliderScales.get(i)).doubleValue();
					sb.setSelection((int) Math.round(value * scale));
				}
			}
		}
	}

	public void itemStateChanged(SelectionEvent e) {

		notifyListeners(e);
	}

	public void focusGained(org.eclipse.swt.events.FocusEvent e) {

		Widget c = e.widget;
		// IJ.log("focusGained: "+c);
		if (c instanceof Text)
			((Text) c).selectAll();
	}

	public void focusLost(org.eclipse.swt.events.FocusEvent e) {

		Widget c = e.widget;
		if (c instanceof Text)
			((Text) c).setSelection(0, 0);
	}

	public void keyPressed(org.eclipse.swt.events.KeyEvent e) {

		Widget component = e.widget;
		int keyCode = e.keyCode;
		int stateMask = e.stateMask;
		IJ.setKeyDown(keyCode);
		if ((component instanceof Slider) && (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT)) {
			Slider sb = (Slider) component;
			int value = sb.getSelection();
			if (keyCode == KeyEvent.VK_RIGHT)
				sb.setSelection(value + 1);
			else
				sb.setSelection(value - 1);
			for (int i = 0; i < slider.size(); i++) {
				if (sb == slider.elementAt(i)) {
					int index = ((Integer) sliderIndexes.get(i)).intValue();
					Text tf = (Text) numberField.elementAt(index);
					double scale = ((Double) sliderScales.get(i)).doubleValue();
					int digits = ((Integer) sliderDigits.get(i)).intValue();
					tf.setText("" + IJ.d2s(sb.getSelection() / scale, digits));
				}
			}
			notifyListeners(e);
			return;
		}
		if (keyCode == SWT.CR && textArea1 == null && okay != null && okay.isEnabled()) {
			wasOKed = true;
			if (IJ.isMacOSX())
				accessTextFields();
			dispose();
		} else if (keyCode == SWT.ESC) {
			wasCanceled = true;
			dispose();
			IJ.resetEscape();
		} else if ((e.character == 'W') && (stateMask & SWT.CTRL) == SWT.CTRL) {
			wasCanceled = true;
			dispose();
		}
	}

	void accessTextFields() {

		if (stringField != null) {
			for (int i = 0; i < stringField.size(); i++)
				((Text) (stringField.elementAt(i))).getText();
		}
		if (numberField != null) {
			for (int i = 0; i < numberField.size(); i++)
				((Text) (numberField.elementAt(i))).getText();
		}
	}

	public void keyReleased(org.eclipse.swt.events.KeyEvent e) {

		int keyCode = e.keyCode;
		int stateMask = e.stateMask;
		IJ.setKeyUp(keyCode);
		// int flags = e.getModifiers();
		boolean control = (stateMask & SWT.CTRL) == SWT.CTRL;
		boolean meta = (stateMask & SWT.ALT) == SWT.ALT;
		boolean shift = (stateMask & SWT.SHIFT) == SWT.SHIFT;
		if (e.character == 'G' && shift && (control || meta))
			new ScreenGrabber().run("");
	}

	public void keyTyped(KeyEvent e) {

	}
	/*
	 * public Insets getInsets() { Insets i= super.getInsets(); return new
	 * Insets(i.top+10, i.left+10, i.bottom+10, i.right+10); }
	 */

	/** Callback for sliders */
	public void adjustmentValueChanged(SelectionEvent e) {

		Object source = e.getSource();
		for (int i = 0; i < slider.size(); i++) {
			if (source == slider.elementAt(i)) {
				Slider sb = (Slider) source;
				int index = ((Integer) sliderIndexes.get(i)).intValue();
				Text tf = (Text) numberField.elementAt(index);
				double scale = ((Double) sliderScales.get(i)).doubleValue();
				int digits = ((Integer) sliderDigits.get(i)).intValue();
				tf.setText("" + IJ.d2s(sb.getSelection() / scale, digits));
			}
		}
	}

	/**
	 * Notify any DialogListeners of changes having occurred If a listener returns
	 * false, do not call further listeners and disable the OK button and preview
	 * Checkbox (if it exists). For PlugInFilters, this ensures that the
	 * PlugInFilterRunner, which listens as the last one, is not called if the
	 * PlugInFilter has detected invalid parameters. Thus, unnecessary calling the
	 * run(ip) method of the PlugInFilter for preview is avoided in that case.
	 */
	private void notifyListeners(TypedEvent e) {

		if (dialogListeners == null)
			return;
		boolean everythingOk = true;
		for (int i = 0; everythingOk && i < dialogListeners.size(); i++) {
			try {
				resetCounters();
				if (this instanceof NonBlockingGenericDialog)
					Recorder.resetCommandOptions();
				if (!((DialogListener) dialogListeners.elementAt(i)).dialogItemChanged(this, e))
					everythingOk = false; // disable further listeners if false (invalid parameters) returned
			} catch (Exception err) { // for exceptions, don't cover the input by a window but
				IJ.beep(); // show them at in the "Log"
				IJ.log("ERROR: " + err + "\nin DialogListener of " + dialogListeners.elementAt(i) + "\nat "
						+ (err.getStackTrace()[0]) + "\nfrom " + (err.getStackTrace()[1]));
			}
		}
		resetCounters();
		boolean workaroundOSXbug = IJ.isMacOSX() && okay != null && !okay.isEnabled() && everythingOk;
		if (everythingOk && recorderOn)
			optionsRecorded = true;
		if (previewCheckbox != null)
			previewCheckbox.setEnabled(everythingOk);
		if (okay != null)
			okay.setEnabled(everythingOk);
		// if (workaroundOSXbug)
		// repaint(); // OSX 10.4 bug delays update of enabled until the next input
	}

	public void repaint() {

		shell.redraw();
		if (imagePanels != null) {
			for (int i = 0; i < imagePanels.size(); i++)
				((ImagePanelSwt) imagePanels.get(i)).redraw();
		}
	}

	/**
	 * Adds a "Help" button that opens the specified URL in the default browser.
	 * With v1.46b or later, displays an HTML formatted message if 'url' starts with
	 * "<html>". There is an example at
	 * http://imagej.net/ij/macros/js/DialogWithHelp.js
	 */
	public void addHelp(String url) {

		helpURL = url;
	}

	void showHelp() {

		if (helpURL.startsWith("<html>")) {
			String title = shell.getText() + " " + helpLabel;
			if (this instanceof NonBlockingGenericDialog)
				new HTMLDialog(shell, title, helpURL, false); // non blocking
			else
				new HTMLDialog(shell, title, helpURL); // modal
		} else {
			String macro = "call('ij.plugin.BrowserLauncher.open', '" + helpURL + "');";
			new MacroRunner(macro); // open on separate thread using BrowserLauncher
		}
	}

	protected boolean isMacro() {

		return macro;
	}

	public static GenericDialog getInstance() {

		return instance;
	}

	@Override
	public void shellClosed(ShellEvent e) {

		/*
		 * Here the shell is closed after the values have been read! First the shell is
		 * hidden (else the values can't be read from a disposed dialog in SWT. When the
		 * GenericDialog is called again the old shell will be disposed (stored
		 * temporary in a list)!
		 */
		if (closeFinally) {
			e.doit = true;
			return;
		} else {
			e.doit = false;
		}
		/* We also need to catch the 'x' cancellation! */
		if (wasOKed == false) {
			shell.setVisible(false);
			wasCanceled = true;
			return;
		}
		instance = null;
		if (!macro) {
			recorderOn = Recorder.record;
			IJ.wait(25);
		}
		resetCounters();
		finalizeRecording();
		resetCounters();
		/* Add to a list to dispose when a new dialog is opened! */
		oldShell.add(shell);
		/*
		 * Do not close the shell with the widgets here. See while loop Shell open
		 * method!
		 */
		shell.setVisible(false);
		closeFinally = true;
		/*
		 * Another solution would be: We add time to close the shell after values have
		 * been read!
		 */
		/*
		 * Runnable runnable = new Runnable() { public void run() { closeFinally = true;
		 * oldShell.add(shell); //shell.close();
		 * //System.out.println(shell.isDisposed()); } };
		 * Display.getDefault().timerExec(1000, runnable);
		 */
	}

	/** Closes the dialog; records the options */
	public void dispose() {

		shell.close();
	}

	/**
	 * Returns a reference to the label of the most recently added numeric field,
	 * string field, choice or slider.
	 */
	public Label getLabel() {

		return lastLabelAdded;
	}

	@SuppressWarnings("unchecked")
	static String getString(DropTargetDropEvent event) throws IOException, UnsupportedFlavorException {

		String text = null;
		DataFlavor fileList = DataFlavor.javaFileListFlavor;
		if (event.isDataFlavorSupported(fileList)) {
			event.acceptDrop(DnDConstants.ACTION_COPY);
			java.util.List<File> list = (java.util.List<File>) event.getTransferable().getTransferData(fileList);
			text = list.get(0).getAbsolutePath();
		} else if (event.isDataFlavorSupported(DataFlavor.stringFlavor)) {
			event.acceptDrop(DnDConstants.ACTION_COPY);
			text = (String) event.getTransferable().getTransferData(DataFlavor.stringFlavor);
			if (text.startsWith("file://"))
				text = text.substring(7);
			text = stripSuffix(stripSuffix(text, "\n"), "\r").replaceAll("%20", " ");
		} else {
			event.rejectDrop();
			return null;
		}
		event.dropComplete(text != null);
		return text;
	}

	static String stripSuffix(String s, String suffix) {

		return !s.endsWith(suffix) ? s : s.substring(0, s.length() - suffix.length());
	}

	/* Drag&Drop SWT implementation for the textfield! */
	static class DragAndDropMacro {

		public DragAndDropMacro(Text text) {

			// Drag and drop adapted from article:
			// https://www.eclipse.org/articles/Article-SWT-DND/DND-in-SWT.html
			// Allow data to be copied or moved to the drop target
			int operations = DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_DEFAULT;
			DropTarget target = new DropTarget(text, operations);
			// Receive data in Text or File format
			final TextTransfer textTransfer = TextTransfer.getInstance();
			final FileTransfer fileTransfer = FileTransfer.getInstance();
			Transfer[] types = new Transfer[] { fileTransfer, textTransfer };
			target.setTransfer(types);
			target.addDropListener(new DropTargetListener() {

				public void dragEnter(DropTargetEvent event) {

					if (event.detail == DND.DROP_DEFAULT) {
						if ((event.operations & DND.DROP_COPY) != 0) {
							event.detail = DND.DROP_COPY;
						} else {
							event.detail = DND.DROP_NONE;
						}
					}
					// will accept text but prefer to have files dropped
					for (int i = 0; i < event.dataTypes.length; i++) {
						if (fileTransfer.isSupportedType(event.dataTypes[i])) {
							event.currentDataType = event.dataTypes[i];
							// files should only be copied
							if (event.detail != DND.DROP_COPY) {
								event.detail = DND.DROP_NONE;
							}
							break;
						}
					}
				}

				public void dragOver(DropTargetEvent event) {

					event.feedback = DND.FEEDBACK_SELECT | DND.FEEDBACK_SCROLL;
					if (textTransfer.isSupportedType(event.currentDataType)) {
						// NOTE: on unsupported platforms this will return null
						Object o = textTransfer.nativeToJava(event.currentDataType);
						String t = (String) o;
						if (t != null)
							System.out.println(t);
					}
				}

				public void dragOperationChanged(DropTargetEvent event) {

					if (event.detail == DND.DROP_DEFAULT) {
						if ((event.operations & DND.DROP_COPY) != 0) {
							event.detail = DND.DROP_COPY;
						} else {
							event.detail = DND.DROP_NONE;
						}
					}
					// allow text to be moved but files should only be copied
					if (fileTransfer.isSupportedType(event.currentDataType)) {
						if (event.detail != DND.DROP_COPY) {
							event.detail = DND.DROP_NONE;
						}
					}
				}

				public void dragLeave(DropTargetEvent event) {

				}

				public void dropAccept(DropTargetEvent event) {

				}

				public void drop(DropTargetEvent event) {

					if (textTransfer.isSupportedType(event.currentDataType)) {
						String path = (String) event.data;
						if (path.startsWith("file://"))
							path = path.substring(7);
						path = stripSuffix(stripSuffix(path, "\n"), "\r").replaceAll("%20", " ");
						try {
							path = Recorder.fixPath(path);
							if (!path.endsWith("/") && (new File(path)).isDirectory())
								path = path + "/";
							text.setText(path);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					if (fileTransfer.isSupportedType(event.currentDataType)) {
						if (fileTransfer.isSupportedType(event.currentDataType)) {
							String[] files = (String[]) event.data;
							for (int i = 0; i < files.length; i++) {
								File file = new File(files[i]);
								String path = file.getAbsolutePath();
								try {
									path = Recorder.fixPath(path);
									if (!path.endsWith("/") && (new File(path)).isDirectory())
										path = path + "/";
									text.setText(path);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}
					}
				}
			});
		}
	}

	private class BrowseButtonListener implements SelectionListener {

		private String label;
		private Text textField;
		private String mode;

		public BrowseButtonListener(String label, Text textField, String mode) {

			this.label = label;
			this.textField = textField;
			this.mode = mode;
		}

		@Override
		public void widgetSelected(SelectionEvent e) {

			String path = null;
			String dialogTitle = label;
			if (dialogTitle == null || dialogTitle.length() == 0)
				dialogTitle = mode.equals("dir") ? "a Folder" : "a File";
			else if (dialogTitle.endsWith(":")) // remove trailing colon
				dialogTitle = dialogTitle.substring(0, dialogTitle.length() - 1);
			dialogTitle = "Select " + dialogTitle;
			if (mode.equals("dir")) {
				String saveDefaultDir = OpenDialog.getDefaultDirectory();
				String dir = this.textField.getText();
				boolean setDefaultDir = dir != null && !dir.equals("");
				if (setDefaultDir)
					OpenDialog.setDefaultDirectory(dir);
				path = IJ.getDir(dialogTitle);
				if (setDefaultDir)
					OpenDialog.setDefaultDirectory(saveDefaultDir);
			} else {
				OpenDialog od = new OpenDialog(dialogTitle, null);
				String directory = od.getDirectory();
				String name = od.getFileName();
				if (name != null)
					path = directory + name;
			}
			if (path != null) {
				// if (IJ.isWindows())
				// path = path.replaceAll("\\\\", "/"); // replace "\" with "/"
				this.textField.setText(path);
			}
		}

		@Override
		public void widgetDefaultSelected(SelectionEvent e) {
			// TODO Auto-generated method stub

		}
	}
	// Necessary for SWT?
	/*
	 * private class TrimmedTextField extends TextField { public
	 * TrimmedTextField(String text, int columns) { super(text, columns); } public
	 * Dimension getMinimumSize() { Dimension d = super.getMinimumSize(); if
	 * (d!=null) { d.width = d.width; d.height = d.height*3/4; } return d; } public
	 * Dimension getPreferredSize() { return getMinimumSize(); } }
	 */

	@Override
	public void shellActivated(ShellEvent e) {
		// TODO Auto-generated method stub

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
	// @Override
	/*
	 * public boolean dialogItemChanged(GenericDialog gd, TypedEvent e) { // TODO
	 * Auto-generated method stub return false; }
	 */

	@Override
	/* This function has to be available! */
	public void widgetSelected(SelectionEvent e) {

		actionPerformed(e);
		itemStateChanged(e);// just notifies which actionPerformed does, too!
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		// TODO Auto-generated method stub

	}
}
