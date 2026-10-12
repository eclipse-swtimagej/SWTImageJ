package ij.swt;

import java.util.List;

/**
 * Supplies the text editors of the application that embeds SWTImageJ (see
 * {@link CodeEditor}). Register a provider with
 * {@link CodeEditors#addProvider(CodeEditorProvider)}. The methods can be called
 * from any thread.
 */
public interface CodeEditorProvider {

	/** Returns a short name to display (e.g., the application name). */
	String getName();

	/** Returns the open editors. */
	List<CodeEditor> getEditors();

	/** Returns the active (last used) editor or null. */
	default CodeEditor getActiveEditor() {

		return null;
	}

	/**
	 * Opens a new editor with the given title and text. The extension of the
	 * title (e.g., ".java") selects the language.
	 *
	 * @return the new editor or null if the provider can't open new editors.
	 */
	default CodeEditor openEditor(String title, String text) {

		return null;
	}
}
