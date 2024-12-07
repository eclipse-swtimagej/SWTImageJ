# SWTImageJ

To start SwtImageJ as an Desktop application import the project into Eclipse. Then select the ImageJ.java file (package ij) 
and then execute the action "Run As->Java Application".
In Eclipse the needed SWT and Eclipe libraries should be available automatically.
If you want to start it with another IDE put the required libraries on the Java classpath (see info.txt in the folder "libs").


## General SWT differences

In ImageJ the user interface or the image display (ImageWindow) are AWT Frames or Dialogs. In SWT the shells are created within the classes with some custom methods.
The reasons are noted in the API description and here: 

https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Freference%2Fapi%2Forg%2Feclipse%2Fswt%2Fwidgets%2FWidget.html

https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Freference%2Fapi%2Forg%2Feclipse%2Fswt%2Fwidgets%2FWidget.html&anchor=checkSubclass()

https://stackoverflow.com/questions/4264983/why-is-subclassing-not-allowed-for-many-of-the-swt-controls

To identify them in the WindowManager (responsible to address open images, etc.) an interface has been created (ij.plugin.frame.swt.WindowSwt) implemented by dialogs, etc., 
which were former Frame and Dialog instances. Some typical Frame events have been integrated in the interface as default methods.

The main image display will not resize the shell when zooming in or out occurs (embedded concept). When the visible zoomed display of the image
is larger then the shell (parent canvas fills the shell) the zoom indicator will be displayed.

For efficiency the painting area is restricted to the visible shell or editor area.

Many classes used the AWTEvent as an argument in the interface ij.gui.DialogListener method dialogItemChanged. This was ported to a SWT TypedEvent for SWT.

## List of some typical SWT replacements

#### Listeners:

AWTEvent -> TypedEvent (used in ij.gui.DialogListener)

actionPerformed -> widgetSelected

For the different SWT widgets the 'widgetSelected' action has to be differentiated. 
Here an example with different former AWT actions in combination:

```Java
public void widgetSelected(SelectionEvent e) {
	if (e.widget instanceof org.eclipse.swt.widgets.Slider) {
		adjustmentValueChanged(e);
	} else if (e.widget instanceof org.eclipse.swt.widgets.Combo) {
		 itemStateChanged(e);
	}

	else if (e.widget instanceof org.eclipse.swt.widgets.Button) {
		org.eclipse.swt.widgets.Button bu = (org.eclipse.swt.widgets.Button) e.widget;
		int style = bu.getStyle();
		if ((style & SWT.CHECK) != 0) {
			itemStateChanged(e);
		} else {
			actionPerformed(e);
		}
	}
}
```

#### Widgets:

Checkbox -> Button (SWT.CHECK)

Choice -> List

addRadioButtonGroup -> Group with Button(SWT.RADIO)

#### PluginTool

You have to import SWT Listeners:
import org.eclipse.swt.events.*;

Some mouse events are not available in SWT. You have to change the following listeners:
mousePressed = mouseDown
mouseReleased = mouseUp

#### Dialogs

The HTML dialog uses the SWT Browser
Some dialog uses  JFace

#### ImageJ GenericDialog

The GenericDialog widgets are not explicitly disposed when closed. Some widgets are still accessed after the close event in AWT. This is
not possible in SWT. To mimic this behaviour the SWT dialog is hidden when closed and stored in a list. 
It will be disposed when a new GenericDialog is opened.

#### Layout differences

Instead of the BorderLayout most of the times the SWT GridLayout is used which can be applied very flexible to components.


