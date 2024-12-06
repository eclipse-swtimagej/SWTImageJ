/*******************************************************************************
 * Copyright (c) 2024 Lablicate GmbH.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 * Marcel Austenfeld - initial API and implementation
 *******************************************************************************/

package ij.plugin.frame;

import org.eclipse.jface.text.source.ISharedTextColors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

public class EditorSharedTextColors implements ISharedTextColors {

	public Color getColor(RGB rgb) {
		// TODO read out preference settings
		Color c= Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY);
		return c;
	}

	
}
