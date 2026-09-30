import ij.plugin.PlugIn;
import ij.plugin.frame.Editor;

/**
 * Demonstrates Editor.addMarker()/clearMarkers(): opens a small macro with a couple of
 * intentional issues and marks them up the way a real compiler/parser integration would -
 * one whole-line warning (Editor.addMarker(line, severity, message)), one precise-range
 * error found by a trivial substring search standing in for a real parser
 * (Editor.addMarker(startOffset, endOffset, severity, message)), and one whole-line info
 * marker. check() clears and re-adds all three from scratch, the way a real integration
 * would do after every edit/recompile.
 *
 * Hover over any of the three markers (in the ruler dot or the squiggly-underlined text) to
 * see its message. The "undefinedVariable" error also carries a QuickFix - its hover mentions
 * "Double-click to apply fix"; double-clicking that underlined text replaces it with "x".
 */
public class Marker_Annotation_Demo implements PlugIn {

	private static final String DEMO_MACRO = "// Marker Annotation Demo\n" + "// Run this plugin again to see the markers re-appear after clearMarkers().\n" + "\n" + "x = 10;\n" + "y = \"not a number\";\n" + "z = x / undefinedVariable;\n" + "\n" + "print(\"Demo complete\");\n";

	@Override
	public void run(String arg) {

		Editor editor = new Editor();
		editor.create("MarkerDemo.ijm", DEMO_MACRO);
		check(editor);
	}

	/** Stands in for a real compiler/parser pass: clears old markers, then reports the same made-up issues again. */
	private static void check(Editor editor) {

		editor.clearMarkers();
		String[] lines = editor.getText().split("\n", -1);
		int offset = 0;
		for(int i = 0; i < lines.length; i++) {
			String line = lines[i];
			int lineNumber = i + 1;
			if(line.contains("not a number")) {
				editor.addMarker(lineNumber, Editor.MARKER_WARNING, "Suspicious assignment: a string is assigned to what looks like a numeric variable.");
			}
			int undefinedVariableIndex = line.indexOf("undefinedVariable");
			if(undefinedVariableIndex >= 0) {
				int startOffset = offset + undefinedVariableIndex;
				int endOffset = startOffset + "undefinedVariable".length();
				Editor.QuickFix quickFix = new Editor.QuickFix("Replace with \"x\"", "x");
				editor.addMarker(startOffset, endOffset, Editor.MARKER_ERROR, "Undefined variable: undefinedVariable", quickFix);
			}
			if(line.startsWith("print(")) {
				editor.addMarker(lineNumber, Editor.MARKER_INFO, "This line prints the demo's completion message.");
			}
			offset += line.length() + 1; // +1 for the '\n' that split(...,-1) stripped off
		}
	}
}
