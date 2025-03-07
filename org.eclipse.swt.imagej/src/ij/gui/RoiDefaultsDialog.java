package ij.gui;

import java.awt.Color;
import java.util.Vector;

import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.widgets.Display;

import ij.plugin.Colors;
import ij.plugin.PlugIn;

/** This plugin implements the Edit/Options/Roi Defaults command. */
public class RoiDefaultsDialog implements PlugIn, DialogListener {
	private boolean nameChanges;

	public void run(String arg) {
		Display.getDefault().syncExec(() -> {
				showDialog();
			
		});
	}

	private void showDialog() {
		String groupNames = Roi.getGroupNames();
		Color color = Roi.getColor();
		String cname = Colors.getColorName(color, "yellow");
		int group = Roi.getDefaultGroup();
		int strokeWidth = (int) Roi.getDefaultStrokeWidth();
		String gname = getGroupName(group);
		GenericDialog gd = new GenericDialog("ROI Defaults");
		gd.addChoice("Color:", Colors.colors, cname);
		gd.addNumericField("Stroke width:", strokeWidth, 0, 3, "");
		gd.setInsets(18, 0, 5);
		gd.addNumericField("Group:", group, 0, 3, "");
		gd.setInsets(2, 0, 5);
		gd.addStringField("Name:", gname, 15);
		gd.setInsets(2, 50, 5);
		gd.addMessage("Color predefined if group>0", null, ij.swt.Color.gray);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
			Roi.setGroupNames(groupNames);
			Roi.setColor(color);
			Roi.setDefaultStrokeWidth(strokeWidth);
			Roi.setDefaultGroup(group);
			return;
		}
		if (nameChanges)
			Roi.saveGroupNames();
	}

	public boolean dialogItemChanged(GenericDialog gd, TypedEvent e) {
		int currentGroup = Roi.getDefaultGroup();
		String cname = gd.getNextChoice();
		Color color = Colors.getColor(cname, Color.yellow);
		Roi.setColor(color);
		if (color.equals(Roi.getColor())) {
			Toolbar.repaintTool(Toolbar.POINT);
			Toolbar.repaintTool(Toolbar.WAND);
		}
		Roi.setDefaultStrokeWidth(gd.getNextNumber());
		int group = (int) gd.getNextNumber();
		Vector stringFields = gd.getStringFields();
		org.eclipse.swt.widgets.Text nameField = (org.eclipse.swt.widgets.Text) (stringFields.get(0));
		if (group >= 0 && group <= 255 && group != currentGroup) {
			Roi.setDefaultGroup(group);
			String name = getGroupName(group);
			nameField.setText(name);
		} else {
			String name = getGroupName(group);
			String name2 = nameField.getText();
			if (name2 != null && !name2.equals(name)) {
				Roi.setGroupName(group, name2);
				nameChanges = true;
			}
		}
		return true;
	}

	private String getGroupName(int group) {
		String gname = Roi.getGroupName(group);
		if (group == 0)
			gname = "0 = no group";
		else if (gname == null)
			gname = "unnamed";
		return gname;
	}

}
