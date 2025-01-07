package org.eclipse.swt.imagej.editor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import ij.IJ;
import ij.ImageJ;
import ij.Menus;
import ij.gui.ImageCanvas;

public class ExtendedPopupMenu {

	protected Set<String> ignored;

	public ExtendedPopupMenu(Menu popup, ImageCanvas imageCanvas) {

		/* Dispose custom menus by name and recreate them dynamically! */
		for(int i = 0; i < popup.getItemCount(); i++) {
			MenuItem it = popup.getItem(i);
			it.dispose();
		}
		Menu menuSub = new Menu(popup);
		MenuItem menuItem = new MenuItem(popup, SWT.CASCADE);
		menuItem.setMenu(menuSub);
		menuItem.setText("ImageJ");
		Menu cascadeMenu = menuItem.getMenu();
		builtImageJContextMenu(cascadeMenu);
		/*
		 * To add a new Menu:
		 * new MenuItem(popup, SWT.SEPARATOR);
		 */
	}

	public void builtImageJContextMenu(Menu builtMenu) {

		Menu menuBar = Menus.getMenuBar();
		/*
		 * We can read in a properties file to ignore certain menus!
		 */
		ignored = new HashSet<String>();
		readFile("contextmenu.txt");
		recurseMenu(menuBar, builtMenu);
	}

	private void recurseMenu(Menu fromMenu, Menu menuToBuild) {

		int items = fromMenu.getItemCount();
		if(items == 0)
			return;
		for(int i = 0; i < items; i++) {
			MenuItem fromMenuItem = fromMenu.getItem(i);
			String label = fromMenuItem.getText();
			/* Ignore if the menu name is in the textfile! */
			if(ignored.contains(label)) {
				continue;
			}
			Object data = fromMenuItem.getData("ActionCommand");
			if((fromMenuItem.getStyle() & SWT.SEPARATOR) != 0) {
				new MenuItem(menuToBuild, SWT.SEPARATOR);
			} else if((fromMenuItem.getStyle() & SWT.CASCADE) == 0) {
				if((fromMenuItem.getStyle() & SWT.CHECK) != 0) {
					MenuItem menuItem = new MenuItem(menuToBuild, SWT.CHECK);
					menuItem.setText(label);
					boolean selectionState = fromMenuItem.getSelection();
					menuItem.setSelection(selectionState);
					menuItem.setData(data);
					menuItem.addSelectionListener(new SelectionListener() {

						public void widgetSelected(SelectionEvent e) {

							String cmd = (String)data;
							if(data != null) {
								IJ.doCommand(cmd);
							} else {
								IJ.doCommand(label);
							}
						}

						public void widgetDefaultSelected(SelectionEvent e) {

						}
					});
				} else {
					MenuItem menuItem = new MenuItem(menuToBuild, SWT.NONE);
					menuItem.setText(label);
					menuItem.setData(data);
					menuItem.addSelectionListener(new SelectionListener() {

						public void widgetSelected(SelectionEvent e) {

							String cmd = (String)data;
							if(data != null) {
								IJ.doCommand(cmd);
							} else {
								IJ.doCommand(label);
							}
						}

						public void widgetDefaultSelected(SelectionEvent e) {

						}
					});
				}
			} else {
				Menu menuSub = new Menu(menuToBuild);
				MenuItem menuItem = new MenuItem(menuToBuild, SWT.CASCADE);
				menuItem.setMenu(menuSub);
				menuItem.setText(label);
				menuItem.setData(data);
				Menu cascadeMenu = fromMenuItem.getMenu();
				recurseMenu(cascadeMenu, menuSub);
			}
		}
	}

	private void readFile(String fileName) {

		String path = ImageJ.getImageJPath(ImageJ.EMBEDDED);
		try {
			Scanner textFile = new Scanner(new File(path + "/" + fileName));
			while(textFile.hasNext()) {
				String next = textFile.next();
				ignored.add(next);
			}
			textFile.close();
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}
