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

Rasband, W.S., ImageJ, U. S. National Institutes of Health, Bethesda, Maryland, USA, https://imagej.net/ij/, 1997-2018.

Schneider, C.A., Rasband, W.S., Eliceiri, K.W. "NIH Image to ImageJ: 25 years of image analysis". Nature Methods 9, 671-675, 2012. (This article is available online.)

Abramoff, M.D., Magalhaes, P.J., Ram, S.J. "Image Processing with ImageJ". Biophotonics International, volume 11, issue 7, pp. 36-42, 2004. (This article is available as a PDF.)
