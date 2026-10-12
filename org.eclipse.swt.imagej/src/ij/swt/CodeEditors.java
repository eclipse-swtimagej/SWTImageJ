package ij.swt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of the {@link CodeEditorProvider}s of applications that embed
 * SWTImageJ. Tools (e.g., assistants or code generators) can list the
 * registered providers to read and write the editors of the application in
 * addition to the SWTImageJ editors.
 */
public final class CodeEditors {

	private static final List<CodeEditorProvider> providers = new CopyOnWriteArrayList<CodeEditorProvider>();

	private CodeEditors() {

	}

	/** Registers a provider (ignored if already registered). */
	public static void addProvider(CodeEditorProvider provider) {

		if(provider != null && !providers.contains(provider))
			providers.add(provider);
	}

	/** Removes a registered provider. */
	public static void removeProvider(CodeEditorProvider provider) {

		providers.remove(provider);
	}

	/** Returns the registered providers. */
	public static List<CodeEditorProvider> getProviders() {

		return new ArrayList<CodeEditorProvider>(providers);
	}
}
