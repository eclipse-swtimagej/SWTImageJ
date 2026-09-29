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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.text.source.ISharedTextColors;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

public class EditorSharedTextColors implements ISharedTextColors {

	/*
	 * This used to always return SWT.COLOR_DARK_GRAY regardless of the requested rgb - harmless
	 * for ProjectionSupport (folding doesn't use color, just a fixed image), but it meant
	 * OverviewRuler - which relies entirely on this to materialize its own computed per-annotation
	 * colors - rendered every marker in the exact same hardcoded dark grey, no matter what
	 * severity/color was actually registered via setAnnotationTypeColor().
	 */
	private final Map<RGB, Color> colors = new HashMap<>();

	@Override
	public Color getColor(RGB rgb) {

		if(rgb == null) {
			return null;
		}
		Color color = colors.get(rgb);
		if(color == null || color.isDisposed()) {
			color = new Color(Display.getDefault(), rgb);
			colors.put(rgb, color);
		}
		return color;
	}

	@Override
	public void dispose() {

		for(Color color : colors.values()) {
			if(!color.isDisposed()) {
				color.dispose();
			}
		}
		colors.clear();
	}
}
