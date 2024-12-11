## Eclipse SWTImageJ

Eclipse SWTImageJ is a port of AWT ImageJ to SWT to enhance its performance, usability, and possible integration within SWT based applications, 
providing users with a more responsive and intuitive interface.
This project was made possible with the financial support of [Lablicate GmbH](https://lablicate.com/) and his owner Philip Wenig.

### Background:

ImageJ is a powerful OpenSource scientific image processing and analysis software written in Java by Wayne Rasband
and widely used across various scientific domains (see References).
However, this software can not only be used as a standalone image application but can also serve as an image library
for the purpose of embedding it into different scientific or non-scientific image applications.
The application is well known to researchers and interested people in the domain of image analysis around the world.

Since the base application can easily be extended by plugins and macros in different languages a huge amount of OpenSource extensions for ImageJ already exists.
Since it’s long history the default Graphical User interface (GUI) is the AWT toolkit. 
While AWT has served ImageJ well, porting it to the Standard Widget Toolkit (SWT) offers several advantages, including a native look and feel
across different Operating Systems, regular updates, an active development team and a well established organization dedicated to OpenSource.

A former port of the AWT interface to SWT_AWT (for embedding AWT/Swing applications in SWT) has already been created and is freely available
as an Eclipse plugin (see https://marketplace.eclipse.org/content/imagej-plugin). 
However a pure SWT port simplifies the development efforts and avoids a lot of technical workarounds when dealing with embedded AWT/Swing applications.


### References:

Rasband, W.S., ImageJ, U. S. National Institutes of Health, Bethesda, Maryland, USA, https://imagej.net/ij/, 1997-2024.

Schneider, C.A., Rasband, W.S., Eliceiri, K.W. "NIH Image to ImageJ: 25 years of image analysis". Nature Methods 9, 671-675, 2012. (This article is available online.)

Abramoff, M.D., Magalhaes, P.J., Ram, S.J. "Image Processing with ImageJ". Biophotonics International, volume 11, issue 7, pp. 36-42, 2004. (This article is available as a PDF.)

### ImageJ User Guides and Books

User Guide: [https://imagej.net/ij/docs/guide/](https://imagej.net/ij/docs/guide/)

Book: [https://imagingbook.com/](https://imagingbook.com/)

### ImageJ Forum

Mailing List: [https://list.nih.gov/cgi-bin/wa.exe?A0=IMAGEJ](https://list.nih.gov/cgi-bin/wa.exe?A0=IMAGEJ)

Forum: [https://forum.image.sc/tag/imagej](https://forum.image.sc/tag/imagej)

Stack Overflow:   [https://stackoverflow.com/questions/tagged/imagej](https://stackoverflow.com/questions/tagged/imagej)

### Run SWTImageJ

To start SwtImageJ as an Desktop application simply import the plugin project into Eclipse.

Then select the ImageJ.java file (package ij) and execute the action "Run As->Java Application".

In Eclipse the needed SWT and Eclipe libraries should be available automatically.

If you want to start it with another IDE put the required libraries on the Java classpath see [info.txt](https://github.com/eclipse-swtimagej/SWTImageJ/blob/main/org.eclipse.swt.imagej/libs/info.txt) in the folder "libs".

### General SWT implementation

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

### List of some typical SWT replacements

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

#### SWT Widgets:

Checkbox -> Button (SWT.CHECK)

Choice -> List

addRadioButtonGroup -> Group with Button(SWT.RADIO)

#### Dialogs

The HTML dialog uses the SWT Browser.

Some dialogs are based on JFace.

#### Layout differences

Instead of the BorderLayout most of the times the SWT GridLayout is used which can be applied very flexible to components.

#### ImageJ PluginTool

You have to import SWT Listeners:
import org.eclipse.swt.events.*;

Some mouse events are not available in SWT. You have to change the following listeners:
mousePressed = mouseDown
mouseReleased = mouseUp

#### ImageJ GenericDialog

In AWT the GenericDialog widgets are not explicitly disposed when closed. Some widget are still accessed after the close event in AWT. This is not possible
in SWT. To mimic this behaviour the SWT dialog is hidden when closed and stored in a list. 
It will be disposed when a new GenericDialog is opened.



