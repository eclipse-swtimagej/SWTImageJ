package ij;
import org.eclipse.swt.SWT;

import ij.io.Opener;

/** Opens, in a separate thread, files selected from the File/Open Recent submenu.*/
public class RecentOpener implements Runnable {
	private String path;

	RecentOpener(String path) {
		this.path = path;
		Thread thread = new Thread(this, "RecentOpener");
		thread.start();
	}

	/** Open the file and move the path to top of the submenu. */
	public void run() {
		Opener o = new Opener();
		o.open(path);
		org.eclipse.swt.widgets.Menu menu = Menus.getOpenRecentMenu();
		int n = menu.getItemCount();
		int index = 0;
		for (int i=0; i<n; i++) {
			if (menu.getItem(i).getText().equals(path)) {
				index = i;
				break;
			}
		}
		if (index>0) {
			org.eclipse.swt.widgets.MenuItem item = menu.getItem(index);
			String text=item.getText();
			item.dispose();
			org.eclipse.swt.widgets.MenuItem itemNew=new org.eclipse.swt.widgets.MenuItem(menu,SWT.PUSH,0);
			itemNew.setText(text);
			//menu.insert(item, 0);
		}
	}

}

