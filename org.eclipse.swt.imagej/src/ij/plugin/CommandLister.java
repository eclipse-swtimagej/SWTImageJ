package ij.plugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.eclipse.swt.SWT;

import ij.Menus;
import ij.text.TextWindow;

/** This class is used by the Plugins/Shortcuts/List Shortcuts 
	command to display a list keyboard shortcuts. */
public class CommandLister implements PlugIn {

	public void run(String arg) {
		if (arg.equals("shortcuts"))
			listShortcuts();
		else
			listCommands();
	}
	
	public void listCommands() {
		Hashtable commands = Menus.getCommands();
		Vector v = new Vector();
		int index = 1;
		for (Enumeration en=commands.keys(); en.hasMoreElements();) {
			String command = (String)en.nextElement();
			v.addElement(index+"\t"+command+"\t"+(String)commands.get(command));
			index++;
		}
		String[] list = new String[v.size()];
		v.copyInto((String[])list);
		showList("Commands", " \tCommand\tPlugin", list);
	}

	public void listShortcuts() {
		String[] shortcuts = getShortcuts();
		for (int i=0; i<shortcuts.length; i++) {
			if (shortcuts[i].contains("\t^"))
				shortcuts[i] += " (macro)";
		}
		showList("Keyboard Shortcuts", "Shortcut\tCommand", shortcuts);
	}
	
	public String[] getShortcuts() {
		Vector v = new Vector();
		Hashtable shortcuts = Menus.getShortcuts();
		addShortcutsToVector(shortcuts, v);
		Hashtable macroShortcuts = Menus.getMacroShortcuts();
		addShortcutsToVector(macroShortcuts, v);
		String[] list = new String[v.size()];
		v.copyInto((String[])list);
		return list;
	}

	void addShortcutsToVector(Hashtable shortcuts, Vector v) {
		for (Enumeration en=shortcuts.keys(); en.hasMoreElements();) {
			Integer key = (Integer)en.nextElement();
			int keyCode = key.intValue();
			boolean upperCase = false;
			//System.out.println(keyCode);
			if (keyCode>=200+90 && keyCode<=200+122) {
				upperCase = true;
				keyCode -= 200+32;
			}
			
			//String shortcut = KeyEvent.getKeyText(keyCode);
			String shortcut = keyCode(keyCode);
			
				shortcut = " " + shortcut; 
			v.addElement(shortcut+"\t"+(String)shortcuts.get(key));
		}
	}

	void showList(String title, String headings, String[] list) {
		Arrays.sort(list, String.CASE_INSENSITIVE_ORDER);
		ArrayList list2 = new ArrayList();
		for (int i=0; i<list.length; i++)
			list2.add(list[i]);
		TextWindow tw = new TextWindow(title, headings, list2, 600, 500);
	}
	/*******************************************************************************
	 * Copyright (c) 2000, 2004 IBM Corporation and others.
	 * All rights reserved. This program and the accompanying materials
	 * are made available under the terms of the Eclipse Public License v1.0
	 * which accompanies this distribution, and is available at
	 * http://www.eclipse.org/legal/epl-v10.html
	 *
	 * Contributors:
	 *     IBM Corporation - initial API and implementation
	 *******************************************************************************/
	//From: http://www.eclipse.org/swt/snippets/
	
	static String character(char character) {
	    switch (character) {
	    case 0:
	      return "'\\0'";
	    case SWT.BS:
	      return "'\\b'";
	    case SWT.CR:
	      return "'\\r'";
	    case SWT.DEL:
	      return "DEL";
	    case SWT.ESC:
	      return "ESC";
	    case SWT.LF:
	      return "'\\n'";
	    case SWT.TAB:
	      return "'\\t'";
	    }
	    	    
	    return String.valueOf(character);
	  }
	
	static String keyCode(int keyCode) {
	    switch (keyCode) {

	    /* Keyboard and Mouse Masks */
	    case SWT.ALT:
	      return "ALT";
	    case SWT.SHIFT:
	      return "SHIFT";
	    case SWT.CONTROL:
	      return "CONTROL";
	    case SWT.COMMAND:
	      return "COMMAND";

	    /* Non-Numeric Keypad Keys */
	    case SWT.ARROW_UP:
	      return "ARROW_UP";
	    case SWT.ARROW_DOWN:
	      return "ARROW_DOWN";
	    case SWT.ARROW_LEFT:
	      return "ARROW_LEFT";
	    case SWT.ARROW_RIGHT:
	      return "ARROW_RIGHT";
	    case SWT.PAGE_UP:
	      return "PAGE_UP";
	    case SWT.PAGE_DOWN:
	      return "PAGE_DOWN";
	    case SWT.HOME:
	      return "HOME";
	    case SWT.END:
	      return "END";
	    case SWT.INSERT:
	      return "INSERT";

	    /* Virtual and Ascii Keys */
	    case SWT.BS:
	      return "BS";
	    case SWT.CR:
	      return "CR";
	    case SWT.DEL:
	      return "DEL";
	    case SWT.ESC:
	      return "ESC";
	    case SWT.LF:
	      return "LF";
	    case SWT.TAB:
	      return "TAB";

	    /* Functions Keys */
	    case SWT.F1:
	      return "F1";
	    case SWT.F2:
	      return "F2";
	    case SWT.F3:
	      return "F3";
	    case SWT.F4:
	      return "F4";
	    case SWT.F5:
	      return "F5";
	    case SWT.F6:
	      return "F6";
	    case SWT.F7:
	      return "F7";
	    case SWT.F8:
	      return "F8";
	    case SWT.F9:
	      return "F9";
	    case SWT.F10:
	      return "F10";
	    case SWT.F11:
	      return "F11";
	    case SWT.F12:
	      return "F12";
	    case SWT.F13:
	      return "F13";
	    case SWT.F14:
	      return "F14";
	    case SWT.F15:
	      return "F15";

	    /* Numeric Keypad Keys */
	    case SWT.KEYPAD_ADD:
	      return "KEYPAD_ADD";
	    case SWT.KEYPAD_SUBTRACT:
	      return "KEYPAD_SUBTRACT";
	    case SWT.KEYPAD_MULTIPLY:
	      return "KEYPAD_MULTIPLY";
	    case SWT.KEYPAD_DIVIDE:
	      return "KEYPAD_DIVIDE";
	    case SWT.KEYPAD_DECIMAL:
	      return "KEYPAD_DECIMAL";
	    case SWT.KEYPAD_CR:
	      return "KEYPAD_CR";
	    case SWT.KEYPAD_0:
	      return "KEYPAD_0";
	    case SWT.KEYPAD_1:
	      return "KEYPAD_1";
	    case SWT.KEYPAD_2:
	      return "KEYPAD_2";
	    case SWT.KEYPAD_3:
	      return "KEYPAD_3";
	    case SWT.KEYPAD_4:
	      return "KEYPAD_4";
	    case SWT.KEYPAD_5:
	      return "KEYPAD_5";
	    case SWT.KEYPAD_6:
	      return "KEYPAD_6";
	    case SWT.KEYPAD_7:
	      return "KEYPAD_7";
	    case SWT.KEYPAD_8:
	      return "KEYPAD_8";
	    case SWT.KEYPAD_9:
	      return "KEYPAD_9";
	    case SWT.KEYPAD_EQUAL:
	      return "KEYPAD_EQUAL";

	    /* Other keys */
	    case SWT.CAPS_LOCK:
	      return "CAPS_LOCK";
	    case SWT.NUM_LOCK:
	      return "NUM_LOCK";
	    case SWT.SCROLL_LOCK:
	      return "SCROLL_LOCK";
	    case SWT.PAUSE:
	      return "PAUSE";
	    case SWT.BREAK:
	      return "BREAK";
	    case SWT.PRINT_SCREEN:
	      return "PRINT_SCREEN";
	    case SWT.HELP:
	      return "HELP";
	    }
	    return character((char) keyCode);
	  }
}
