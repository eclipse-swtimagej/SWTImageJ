package ij.plugin;

import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.process.ImageProcessor;
import ij.plugin.frame.Recorder;
import java.io.*;
import java.awt.Point;
import java.util.*;
import java.util.Iterator;

import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;

import java.util.ArrayList;

/**
 * This class opens images, roi's, luts and text files dragged and dropped on
 * the "ImageJ" window. It is based on the Draw_And_Drop plugin by Eric Kischell
 * (keesh@ieee.org).
 * 
 * 10 November 2006: Albert Cardona added Linux support and an option to open
 * all images in a dragged folder as a stack.
 * 
 * Ported to SWT!
 */

public class DragAndDrop
		implements PlugIn, org.eclipse.swt.dnd.DropTargetListener, org.eclipse.swt.dnd.DragSourceListener, Runnable {
	private Iterator iterator;
	private static boolean convertToRGB;
	private static boolean virtualStack;
	private boolean openAsVirtualStack;
	private int operations;
	private Transfer[] types;
	final FileTransfer fileTransfer = FileTransfer.getInstance();
	final TextTransfer textTransfer = TextTransfer.getInstance();
	private String[] files;

	public void run(String arg) {
		ImageJ ij = IJ.getInstance();
		// ij.setDropTarget(null);
		operations = DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_DEFAULT;
		// new DropTarget(dropTable, operations);
		final FileTransfer fileTransfer = FileTransfer.getInstance();
		types = new Transfer[] { fileTransfer, textTransfer };

		DragSource s1 = new DragSource(ij.getShell(), operations);
		DragSource s2 = new DragSource(Toolbar.getInstance(), operations);
		DragSource s3 = new DragSource(ij.getStatusBar(), operations);
		
		s1.addDragListener(this);
		s2.addDragListener(this);
		s3.addDragListener(this);
		
		s1.setTransfer(types);
		s2.setTransfer(types);
		s3.setTransfer(types);
		
		DropTarget dt1 = new DropTarget(ij.getShell(), operations);
		DropTarget dt2 = new DropTarget(Toolbar.getInstance(), operations);
		DropTarget dt3 = new DropTarget(ij.getStatusBar(), operations);
		
		dt1.addDropListener(this);
		dt2.addDropListener(this);
		dt3.addDropListener(this);
		
		dt1.setTransfer(types);
		dt2.setTransfer(types);
		dt3.setTransfer(types);
	}

	@Override
	public void drop(org.eclipse.swt.dnd.DropTargetEvent event) {
		iterator = null;
		if (IJ.debugMode)
			IJ.log("DragAndDrop.drop: " + event.dataTypes.length + " flavors");
		for (int i = 0; i < event.dataTypes.length; i++) {
		
			//System.out.println(event.currentDataType.type);
			if (fileTransfer.isSupportedType(event.currentDataType)) {
				files = (String[]) event.data;
				File[]fil=new File[files.length];
	            for (int u = 0; u < fil.length; u++) {
	            	//System.out.println(files[u]);
	            	fil[u]=new File(files[u]);
	            }
				iterator = ((List) Arrays.asList(fil)).iterator();
			}
			if (textTransfer.isSupportedType(event.currentDataType)) {
				
				String text = (String) event.data;
				
				
				String s = text.trim();
				if (IJ.isLinux() && s.length() > 1 && (int) s.charAt(1) == 0)
					s = fixLinuxString(s);
				ArrayList list = new ArrayList();
				if (s.indexOf("href=\"") != -1 || s.indexOf("src=\"") != -1) {
					s = parseHTML(s);
					if (IJ.debugMode)
						IJ.log("  url: " + s);
					list.add(s);
					this.iterator = list.iterator();
					break;
				}
				BufferedReader br = new BufferedReader(new StringReader(s));
				String tmp;
				try {
					while (null != (tmp = br.readLine())) {
						try {
							tmp = java.net.URLDecoder.decode(tmp.replaceAll("\\+", "%2b"), "UTF-8");
						} catch (UnsupportedEncodingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						if (tmp.startsWith("file://"))
							tmp = tmp.substring(7);
						if (IJ.debugMode)
							IJ.log("  content: " + tmp);
						if (tmp.startsWith("https://"))
							list.add(s);
						else
							list.add(new File(tmp));
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				this.iterator = list.iterator();
				break;
				

			}
		}
		if (iterator != null) {
			Thread thread = new Thread(this, "DragAndDrop");
			thread.setPriority(Math.max(thread.getPriority() - 1, Thread.MIN_PRIORITY));
			thread.start();
		}

	}

	@Override
	public void dropAccept(org.eclipse.swt.dnd.DropTargetEvent e) {
		// TODO Auto-generated method stub

	}

	/*public void drop(DropTargetDropEvent dtde) {
		dtde.acceptDrop(DnDConstants.ACTION_COPY);
		DataFlavor[] flavors = null;
		try {
			Transferable t = dtde.getTransferable();
			iterator = null;
			flavors = t.getTransferDataFlavors();
			if (IJ.debugMode)
				IJ.log("DragAndDrop.drop: " + flavors.length + " flavors");
			for (int i = 0; i < flavors.length; i++) {
				if (IJ.debugMode)
					IJ.log("  flavor[" + i + "]: " + flavors[i].getMimeType());
				if (flavors[i].isFlavorJavaFileListType()) {
					Object data = t.getTransferData(DataFlavor.javaFileListFlavor);
					iterator = ((List) data).iterator();
					break;
				} else if (flavors[i].isFlavorTextType()) {
					Object ob = t.getTransferData(flavors[i]);
					if (!(ob instanceof String))
						continue;
					String s = ob.toString().trim();
					if (IJ.isLinux() && s.length() > 1 && (int) s.charAt(1) == 0)
						s = fixLinuxString(s);
					ArrayList list = new ArrayList();
					if (s.indexOf("href=\"") != -1 || s.indexOf("src=\"") != -1) {
						s = parseHTML(s);
						if (IJ.debugMode)
							IJ.log("  url: " + s);
						list.add(s);
						this.iterator = list.iterator();
						break;
					}
					BufferedReader br = new BufferedReader(new StringReader(s));
					String tmp;
					while (null != (tmp = br.readLine())) {
						tmp = java.net.URLDecoder.decode(tmp.replaceAll("\\+", "%2b"), "UTF-8");
						if (tmp.startsWith("file://"))
							tmp = tmp.substring(7);
						if (IJ.debugMode)
							IJ.log("  content: " + tmp);
						if (tmp.startsWith("http://"))
							list.add(s);
						else
							list.add(new File(tmp));
					}
					this.iterator = list.iterator();
					break;
				}
			}
			if (iterator != null) {
				Thread thread = new Thread(this, "DrawAndDrop");
				thread.setPriority(Math.max(thread.getPriority() - 1, Thread.MIN_PRIORITY));
				thread.start();
			}
		} catch (Exception e) {
			dtde.dropComplete(false);
			return;
		}
		dtde.dropComplete(true);
		if (flavors == null || flavors.length == 0) {
			if (IJ.isMacOSX())
				IJ.error("First drag and drop ignored. Please try again. You can avoid this\n"
						+ "problem by dragging to the toolbar instead of the status bar.");
			else
				IJ.error("Drag and drop failed");
		}
	}
	*/
	@Override
	public void dragFinished(DragSourceEvent arg0) {
		// TODO Auto-generated method stub

	}

	private String fixLinuxString(String s) {
		StringBuffer sb = new StringBuffer(200);
		for (int i = 0; i < s.length(); i += 2)
			sb.append(s.charAt(i));
		return new String(sb);
	}

	private String parseHTML(String s) {
		if (IJ.debugMode)
			IJ.log("parseHTML:\n" + s);
		int index1 = s.indexOf("src=\"");
		if (index1 >= 0) {
			int index2 = s.indexOf("\"", index1 + 5);
			if (index2 > 0)
				return s.substring(index1 + 5, index2);
		}
		index1 = s.indexOf("href=\"");
		if (index1 >= 0) {
			int index2 = s.indexOf("\"", index1 + 6);
			if (index2 > 0)
				return s.substring(index1 + 6, index2);
		}
		return s;
	}

	/*public void dragEnter(DropTargetDragEvent e) {
		IJ.showStatus("<<Drag and Drop>>");
		if (IJ.debugMode)
			IJ.log("DragEnter: " + e.getLocation());
		e.acceptDrag(DnDConstants.ACTION_COPY);
		openAsVirtualStack = false;
	}
	
	public void dragOver(DropTargetDragEvent e) {
		if (IJ.debugMode)
			IJ.log("DragOver: " + e.getLocation());
		Point loc = e.getLocation();
		int buttonSize = Toolbar.getButtonSize();
		int width = IJ.getInstance().getSize().width;
		openAsVirtualStack = width - loc.x <= (buttonSize + buttonSize / 3);
		if (openAsVirtualStack)
			IJ.showStatus("<<Open as virtual stack or text image>>");
		else
			IJ.showStatus("<<Drag and Drop>>");
	}
	
	public void dragExit(DropTargetEvent e) {
		IJ.showStatus("");
	}
	
	public void dropActionChanged(DropTargetDragEvent e) {
	}*/

	public void run() {
		Iterator iterator = this.iterator;
		while (iterator.hasNext()) {
			Object obj = iterator.next();
			String str = "" + obj;
			if (str != null && str.startsWith("https:/")) {
				if (!str.startsWith("https://"))
					str = str.replace("https:/", "https://");
				obj = str;
			}
			if (obj != null && (obj instanceof String))
				openURL((String) obj);
			else
				openFile((File) obj);
		}
	}

	/** Open a URL. */
	private void openURL(String url) {
		if (IJ.debugMode)
			IJ.log("DragAndDrop.openURL: " + url);
		if (url != null)
			IJ.open(url);
	}

	/**
	 * Open a file. If it's a directory, ask to open all images as a sequence in a
	 * stack or individually.
	 */
	public void openFile(File f) {
		if (IJ.debugMode)
			IJ.log("DragAndDrop.openFile: " + f);
		try {
			if (null == f)
				return;
			String path = f.getCanonicalPath();
			if (f.exists()) {
				if (f.isDirectory()) {
					if (openAsVirtualStack)
						IJ.run("Image Sequence...", "open=[" + path + "] sort use");
					else
						openDirectory(f, path);
				} else {
					if (openAsVirtualStack && (path.endsWith(".tif")||path.endsWith(".TIF")||path.endsWith(".tiff")))
						(new FileInfoVirtualStack()).run(path);
					else if (openAsVirtualStack && (path.endsWith(".avi") || path.endsWith(".AVI")))
						IJ.run("AVI...", "open=[" + path + "] use");
					else if (openAsVirtualStack && (path.endsWith(".txt"))) {
						ImageProcessor ip = (new TextReader()).open(path);
						if (ip != null)
							new ImagePlus(f.getName(), ip).show();
					} else {
						Recorder.recordOpen(path);
						(new Opener()).openAndAddToRecent(path);
					}
					OpenDialog.setLastDirectory(f.getParent() + File.separator);
					OpenDialog.setLastName(f.getName());
 					OpenDialog.setDefaultDirectory(f.getParent());
				}
			} else {
				IJ.log("File not found: " + path);
			}
		} catch (Throwable e) {
			if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
				IJ.handleException(e);
		}
	}

	private void openDirectory(File f, String path) {
		if (path == null)
			return;
		path = IJ.addSeparator(path);
		String[] names = f.list();
		names = (new FolderOpener()).trimFileList(names);
		if (names == null)
			return;
		FolderOpener fo = new FolderOpener();
		fo.setDirectory(path);
		fo.run("");
	}

	@Override
	public void dragEnter(org.eclipse.swt.dnd.DropTargetEvent e) {
		IJ.showStatus("<<Drag and Drop>>");
		if (IJ.debugMode)
			IJ.log("DragEnter: " + e.getSource());
		e.feedback=DND.DROP_COPY;
		openAsVirtualStack = false;

	}

	@Override
	public void dragLeave(org.eclipse.swt.dnd.DropTargetEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void dragOperationChanged(org.eclipse.swt.dnd.DropTargetEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void dragOver(org.eclipse.swt.dnd.DropTargetEvent e) {
		if (IJ.debugMode)
			IJ.log("DragOver: " + e.getSource());
		org.eclipse.swt.graphics.Point loc = new org.eclipse.swt.graphics.Point(e.x,e.y);
		org.eclipse.swt.graphics.Point rP = IJ.getInstance().getShell().toControl(loc);
		int buttonSize = Toolbar.getButtonSize();
		int width = IJ.getInstance().getShell().getSize().x;
		openAsVirtualStack = width - rP.x <= (buttonSize + buttonSize / 3);
		if (openAsVirtualStack)
			IJ.showStatus("<<Open as virtual stack or text image>>");
		else
			IJ.showStatus("<<Drag and Drop>>");

	}

	@Override
	public void dragSetData(DragSourceEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void dragStart(DragSourceEvent arg0) {
		// TODO Auto-generated method stub

	}

}