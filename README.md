## Eclipse SWTImageJ

Eclipse SWTImageJ is a port of AWT [ImageJ](https://github.com/imagej/ImageJ) to [SWT](https://www.eclipse.org/swt/) to enhance its performance, usability, and possible integration within SWT based applications, 
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

A former port of the AWT interface to [SWT_AWT](https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Freference%2Fapi%2Forg%2Feclipse%2Fswt%2Fawt%2FSWT_AWT.html) (for embedding AWT/Swing applications in SWT) has already been created (released 2007 within an RCP application) and is freely available
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

To start SWTImageJ as an Desktop application simply import the plugin project into Eclipse (It might be important to use the RCP distribution of Eclipse if you don't get the required SWT, JFace... libraries added by default to the Plugins classpath): 

[Eclipse IDE for RCP and RAP Developers](https://www.eclipse.org/downloads/packages/release/2024-12/r/eclipse-ide-rcp-and-rap-developers).

Then select the [ImageJ.java](https://github.com/eclipse-swtimagej/SWTImageJ/tree/main/org.eclipse.swt.imagej/src/ij) file (package ij) and execute the action "Run As->Java Application".

In Eclipse the needed SWT and Eclipe libraries should be available automatically.

If you want to start it with another IDE put the required libraries on the Java classpath see [info.txt](https://github.com/eclipse-swtimagej/SWTImageJ/blob/main/org.eclipse.swt.imagej/libs/info.txt) in the folder "libs".

### Built SWTImageJ as a *.jar (not runnable)

To built SWTImageJ as a *.jar library use Maven in the SWTImageJ directory with:

mvn -f org.eclipse.swt.imagej.cbi/pom.xml clean install

To integrate more libraries with Maven and the target platform , see: [https://www.vogella.com/tutorials/EclipseJarToPlugin/article.html](https://www.vogella.com/tutorials/EclipseJarToPlugin/article.html)

### ImageJ Macros and Scripts

Available ImageJ Macros and Scripts (Beanshell, Jython, JavaScript - if interpreter is available in the plugins folder) using the default ImageJ macro functions or ImageJ API should run. However sometimes source corrections for SWT are necessary.


In SWTImageJ the Java and Macro/Script editor was ported to the SWT StyledText widget with code templates (press control-space) and syntax highlighting.
Left and right rulers for markers are available, too, for a possibly later implementation of error markers.

### Compile Java Plugins

The compilation of plugins is supported. The default ImageJ plugin templates ("Plugins->New->Plugin XXX") were ported to SWT. 

In the plugins folder itself a two class template is available, too, where the dependent canvas class can directly opened and edited with the [Eclipse WindowBuilder](https://eclipse.dev/windowbuilder/) (SWTComposite.java). Use the action ["Plugins->Compile And Run..."](https://imagej.net/ij/docs/guide/146-31.html#toc-Subsection-31.5) and compile the main class (Plugin_Shell.java) for the composite in the plugins folder of SwtImageJ (the plugins folder is on the ImageJ classpath).

As known from ImageJ newly compiled plugins can be loaded dynamically with the menu ["Help->Refresh Menus"](https://imagej.net/ij/docs/guide/146-33.html).

### IJPluginWindowBuilderExample

A simple example has been added to create Graphical User Interfaces easily with the [WindowBuilder](https://eclipse.dev/windowbuilder/) plugin of Eclipse.
The SWTComposite class can be opened and edited with the WindowBuilder plugin.
To compile the plugin the SWTImage jar has to be added to the classpath (simply built and add org.eclipse.swt.imagej-x.x.x-SNAPSHOT.jar to the Java classpath).
Copy the compiled plugin classes to the plugins folder of SWTImageJ thus it can be loaded as a plugin and extend the Plugins menu.

### General SWT implementation

In general the SWTImageJ implementation follows the general ImageJ layout of packages, classes and folders familiar to ImageJ developers. 

For some parts (selection API) the SWTGraphics2D library is used to convert Java2D calls to SWT graphic calls, see:
See: https://github.com/jfree/swtgraphics2d

For a fast image display the processor classes of ImageJ have been translated to SWT. For this I tried out several known described implementations but found myself a more effective method with a direct pixel transfer similar to the creation of a BufferedImage (see the different processor classes).

In ImageJ the user interface or the image display (ImageWindow) are AWT Frames or Dialogs. In SWT the shells are created within the classes with some custom methods.
The reasons are noted in the API description (see API description 1, 2) and on StackOverflow: 

[Link API description 1](https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Freference%2Fapi%2Forg%2Feclipse%2Fswt%2Fwidgets%2FWidget.html)
, [Link API description 2](https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Freference%2Fapi%2Forg%2Feclipse%2Fswt%2Fwidgets%2FWidget.html&anchor=checkSubclass())
, [Stack Overflow](https://stackoverflow.com/questions/4264983/why-is-subclassing-not-allowed-for-many-of-the-swt-controls)

To identify them in the WindowManager (responsible to address open images, etc.) an interface has been created (ij.plugin.frame.swt.WindowSwt) implemented by dialogs, etc., 
which were former Frame and Dialog instances. Some typical Frame events have been integrated in the interface as default methods.

The main image display will not resize the shell when zooming in or out occurs (embedded concept). When the visible zoomed display of the image
is larger then the shell (parent canvas fills the shell) the zoom indicator will be displayed.

For efficiency the painting area is restricted to the visible shell or editor area.

In the original ImageJ implementation the AWTEvent is used as an argument with the ij.gui.DialogListener interface method dialogItemChanged. This was ported to a [TypedEvent](https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Freference%2Fapi%2Forg%2Feclipse%2Fswt%2Fevents%2FTypedEvent.html) argument for SWT.

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

#### Mouse Listeners

Some mouse events are not available in SWT. You have to change the following listeners:
mousePressed = mouseDown
mouseReleased = mouseUp

#### ImageJ GenericDialog

In AWT the GenericDialog widgets are not explicitly disposed when closed. Some widget are still accessed after the close event in AWT. This is not possible
in SWT. To mimic this behaviour the SWT dialog is hidden when closed and stored in a list. 
It will be disposed when a new GenericDialog is opened.

### How to convert existing ImageJ Plugins

#### ImageJ PluginTool

You have to import SWT Listeners:

import org.eclipse.swt.events.*;

See the Plugin Tool template which was ported to SWT.
