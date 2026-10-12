package ij.swt;

import java.io.File;

/**
 * A text editor of the application that embeds SWTImageJ (e.g., an editor of
 * an Eclipse RCP application) which tools like assistants or code generators
 * can read and write. Editors are supplied by a {@link CodeEditorProvider}
 * registered with {@link CodeEditors#addProvider(CodeEditorProvider)}.
 * <p>
 * The methods can be called from any thread. Implementations must access their
 * widgets in the SWT UI thread (e.g., with Display.syncExec). Two instances for
 * the same editor should be equal (equals and hashCode).
 */
public interface CodeEditor {

	/** Returns the title (usually the file name with extension, e.g., "Test.java"). */
	String getTitle();

	/**
	 * Returns the language as a file extension without the dot (e.g., "ijm",
	 * "java", "py", "js", "bsh", "groovy", "r"). The default implementation uses
	 * the extension of the title.
	 */
	default String getLanguage() {

		String title = getTitle();
		int dot = title == null ? -1 : title.lastIndexOf('.');
		return dot < 0 ? "" : title.substring(dot + 1).toLowerCase();
	}

	/** Returns the file of the editor or null (e.g., not saved yet). */
	default File getFile() {

		return null;
	}

	/** Returns the complete text. */
	String getText();

	/** Returns the selected text or an empty string. */
	String getSelectedText();

	/** Replaces the complete text. */
	void setText(String text);

	/** Inserts the text at the caret position, replacing the selection if any. */
	void insertText(String text);

	/** Appends the text at the end. */
	void appendText(String text);

	/** Makes the editor visible (e.g., activates its tab or window). */
	void show();

	/** Returns false if the editor has been closed. */
	default boolean isOpen() {

		return true;
	}
}
