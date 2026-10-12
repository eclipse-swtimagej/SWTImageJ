package ij.swt;

/**
 * A marker (error, warning or info) with an optional quick fix which a tool
 * places in a {@link CodeEditor} (see {@link CodeEditor#addMarker(CodeMarker)}).
 * The marker covers the character range [start, end) of the text at the time it
 * is added. The editor moves the marker with later text changes. The quick fix
 * replaces the marked text with the replacement.
 * <p>
 * Markers are identified by their instance: pass the same instance to
 * {@link CodeEditor#applyQuickFix(CodeMarker)},
 * {@link CodeEditor#hasMarker(CodeMarker)} and
 * {@link CodeEditor#removeMarker(CodeMarker)}.
 */
public final class CodeMarker {

	/** The severities, the same values as Editor.MARKER_ERROR, MARKER_WARNING and MARKER_INFO. */
	public static final int ERROR = 0, WARNING = 1, INFO = 2;
	private final int start;
	private final int end;
	private final int severity;
	private final String message;
	private final String fixDescription;
	private final String replacement;

	/** Creates a marker without a quick fix. */
	public CodeMarker(int start, int end, int severity, String message) {

		this(start, end, severity, message, null, null);
	}

	/**
	 * Creates a marker with a quick fix.
	 *
	 * @param start
	 *            the start offset (inclusive)
	 * @param end
	 *            the end offset (exclusive), must be greater than start
	 * @param severity
	 *            {@link #ERROR}, {@link #WARNING} or {@link #INFO}
	 * @param message
	 *            the message shown to the user
	 * @param fixDescription
	 *            the description of the quick fix (may be null)
	 * @param replacement
	 *            the text which replaces the marked text, null for no quick fix
	 */
	public CodeMarker(int start, int end, int severity, String message, String fixDescription, String replacement) {

		if(end <= start || start < 0) {
			throw new IllegalArgumentException("Invalid range: " + start + "-" + end);
		}
		this.start = start;
		this.end = end;
		this.severity = severity < ERROR || severity > INFO ? ERROR : severity;
		this.message = message == null ? "" : message;
		this.fixDescription = fixDescription;
		this.replacement = replacement;
	}

	/** Returns the start offset when the marker was created. */
	public int getStart() {

		return start;
	}

	/** Returns the end offset (exclusive) when the marker was created. */
	public int getEnd() {

		return end;
	}

	public int getSeverity() {

		return severity;
	}

	public String getMessage() {

		return message;
	}

	/** Returns the description of the quick fix, a default text if not set or null if there is no quick fix. */
	public String getFixDescription() {

		if(replacement == null) {
			return null;
		}
		return fixDescription == null || fixDescription.isBlank() ? "Apply suggested fix" : fixDescription;
	}

	/** Returns the replacement text of the quick fix or null. */
	public String getReplacement() {

		return replacement;
	}

	public boolean hasQuickFix() {

		return replacement != null;
	}
}
