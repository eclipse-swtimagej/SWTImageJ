/*
 * ===========================================
 * SWTGraphics2D : a bridge from Java2D to SWT
 * ===========================================
 * (C) Copyright 2006-2021, by Object Refinery Limited and Contributors.
 * Project Info: https://github.com/jfree/swtgraphics2d
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 * ------------------
 * SWTGraphics2D.java
 * ------------------
 * (C) Copyright 2006-2021, by Henry Proudhon and Contributors.
 * Original Author: Henry Proudhon (henry.proudhon AT mines-paristech.fr);
 * Contributor(s): David Gilbert (for Object Refinery Limited);
 * Cedric Chabanois (cchabanois AT no-log.org, resource pools);
 * Ronnie Duan (https://sourceforge.net/p/jfreechart/bugs/914/);
 * Kevin Xu (parts of patch https://sourceforge.net/p/jfreechart/patches/297/);
 */
package org.jfree.swt;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;

/**
 * A graphics device for SWTGraphics2D.
 */
public class SWTGraphicsDevice extends GraphicsDevice {

	private final String id;
	GraphicsConfiguration defaultConfig;

	/**
	 * Creates a new instance.
	 *
	 * @param id
	 *            the id.
	 * @param defaultConfig
	 *            the default configuration.
	 */
	public SWTGraphicsDevice(String id, GraphicsConfiguration defaultConfig) {

		this.id = id;
		this.defaultConfig = defaultConfig;
	}

	/**
	 * Returns the device type.
	 *
	 * @return The device type.
	 */
	@Override
	public int getType() {

		return GraphicsDevice.TYPE_RASTER_SCREEN;
	}

	/**
	 * Returns the id string (defined in the constructor).
	 *
	 * @return The id string.
	 */
	@Override
	public String getIDstring() {

		return this.id;
	}

	/**
	 * Returns all configurations for this device.
	 *
	 * @return All configurations for this device.
	 */
	@Override
	public GraphicsConfiguration[] getConfigurations() {

		return new GraphicsConfiguration[]{getDefaultConfiguration()};
	}

	/**
	 * Returns the default configuration for this device.
	 *
	 * @return The default configuration for this device.
	 */
	@Override
	public GraphicsConfiguration getDefaultConfiguration() {

		return this.defaultConfig;
	}
}
