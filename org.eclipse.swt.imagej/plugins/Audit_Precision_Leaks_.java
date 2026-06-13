import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static-source audit for 64-bit (double) precision leaks in an ImageJ-style codebase.
 *
 * Scans .java files under a chosen directory and flags lines like:
 *   - getPixelValue(...)           -> returns float per ImageProcessor contract
 *   - (float[])ip.getPixels()       -> hard-cast that breaks for DoubleProcessor
 *   - (float[])ip.getSnapshotPixels()
 *   - (float[])ip.getPixelsCopy()
 *   - new FloatProcessor(...)       -> output forced to 32-bit
 *   - bitDepth == 32 / != 32        -> often used as "else == float"
 *   - getInterpolatedValue(...)     -> returns double in IJ, but often round-tripped via float
 *   - instanceof FloatProcessor in else-chains without DoubleProcessor companion
 *
 * Each hit is assigned a severity:
 *   HIGH   - guaranteed precision loss or runtime ClassCastException for double pixels
 *   MEDIUM - likely precision loss or implicit type assumption
 *   LOW    - worth reviewing, often benign
 *
 * Output:
 *   - Summary in the Log window
 *   - precision-leak-report.md in the audited directory
 */
public class Audit_Precision_Leaks_ implements PlugIn {

    private static final String[] DEFAULT_ROOTS = {
            "org.eclipse.swt.imagej/src",
            "src",
            "."
    };

    // ----------------------------------------------------------------------
    // Patterns
    // ----------------------------------------------------------------------
    private static final Pattern P_GETPIXELVALUE = Pattern.compile(
            "\\.getPixelValue\\s*\\(");

    private static final Pattern P_FLOAT_CAST_PIXELS = Pattern.compile(
            "\\(\\s*float\\s*\\[\\s*\\]\\s*\\)\\s*[A-Za-z_][A-Za-z_0-9]*\\s*\\.\\s*getPixels\\s*\\(");

    private static final Pattern P_FLOAT_CAST_SNAPSHOT = Pattern.compile(
            "\\(\\s*float\\s*\\[\\s*\\]\\s*\\)\\s*[A-Za-z_][A-Za-z_0-9]*\\s*\\.\\s*getSnapshotPixels\\s*\\(");

    private static final Pattern P_FLOAT_CAST_COPY = Pattern.compile(
            "\\(\\s*float\\s*\\[\\s*\\]\\s*\\)\\s*[A-Za-z_][A-Za-z_0-9]*\\s*\\.\\s*getPixelsCopy\\s*\\(");

    private static final Pattern P_NEW_FLOAT_PROC = Pattern.compile(
            "new\\s+FloatProcessor\\s*\\(");

    private static final Pattern P_BITDEPTH_32 = Pattern.compile(
            "(?:bitDepth|getBitDepth\\s*\\(\\s*\\))\\s*(?:==|!=)\\s*32\\b");

    private static final Pattern P_GET_INTERPOLATED = Pattern.compile(
            "\\.getInterpolatedValue\\s*\\(");

    private static final Pattern P_INSTANCEOF_FLOAT = Pattern.compile(
            "instanceof\\s+FloatProcessor\\b");

    // ----------------------------------------------------------------------
    @Override
    public void run(String arg) {
        IJ.log("\\Clear");
        IJ.log("=== Precision leak audit ===");

        String defaultRoot = findDefaultRoot();
        GenericDialog gd = new GenericDialog("Audit 64-bit Precision Leaks");
        gd.addStringField("Source root:", defaultRoot, 60);
        gd.addCheckbox("Ignore lines containing \"// audit-ok\"", true);
        gd.showDialog();
        if (gd.wasCanceled()) return;

        String root = gd.getNextString();
        boolean honorIgnore = gd.getNextBoolean();

        File rootFile = new File(root);
        if (!rootFile.exists() || !rootFile.isDirectory()) {
            IJ.error("Audit", "Source root not found:\n" + rootFile.getAbsolutePath());
            return;
        }
        IJ.log("Scanning: " + rootFile.getAbsolutePath());

        List<File> javaFiles = new ArrayList<>();
        collectJavaFiles(rootFile, javaFiles);
        IJ.log("Java files found: " + javaFiles.size());

        List<Hit> hits = new ArrayList<>();
        for (File f : javaFiles) {
            scanFile(f, hits, honorIgnore);
        }

        // Rank by severity (HIGH first), then file, then line
        hits.sort(new Comparator<Hit>() {
            @Override
            public int compare(Hit a, Hit b) {
                int s = Integer.compare(b.severity.rank, a.severity.rank);
                if (s != 0) return s;
                int p = a.path.compareTo(b.path);
                if (p != 0) return p;
                return Integer.compare(a.line, b.line);
            }
        });

        Map<Severity, Integer> counts = new LinkedHashMap<>();
        counts.put(Severity.HIGH, 0);
        counts.put(Severity.MEDIUM, 0);
        counts.put(Severity.LOW, 0);
        for (Hit h : hits) counts.put(h.severity, counts.get(h.severity) + 1);

        // Top-N to Log
        IJ.log("");
        IJ.log("Total hits: " + hits.size());
        for (Map.Entry<Severity, Integer> e : counts.entrySet())
            IJ.log("  " + e.getKey() + ": " + e.getValue());

        int top = Math.min(40, hits.size());
        IJ.log("");
        IJ.log("Top " + top + " hits:");
        for (int i = 0; i < top; i++) {
            Hit h = hits.get(i);
            IJ.log(String.format("[%s] %s:%d  %s",
                    h.severity, h.relPath, h.line, h.kind));
        }

        // Markdown report
        File report = new File(rootFile, "precision-leak-report.md");
        try (PrintWriter pw = new PrintWriter(new FileWriter(report))) {
            writeReport(pw, rootFile, hits, counts);
            IJ.log("");
            IJ.log("Report written: " + report.getAbsolutePath());
        } catch (IOException ex) {
            IJ.log("Failed to write report: " + ex.getMessage());
        }
    }

    // ----------------------------------------------------------------------
    private void writeReport(PrintWriter pw, File rootFile,
                             List<Hit> hits, Map<Severity, Integer> counts) {
        pw.println("# 64-bit Precision Leak Audit");
        pw.println();
        pw.println("Root: `" + rootFile.getAbsolutePath() + "`");
        pw.println();
        pw.println("## Summary");
        pw.println();
        pw.println("| Severity | Count |");
        pw.println("|----------|------:|");
        for (Map.Entry<Severity, Integer> e : counts.entrySet())
            pw.printf("| %s | %d |%n", e.getKey(), e.getValue());
        pw.println();
        pw.println("Total: **" + hits.size() + "** hits");
        pw.println();

        pw.println("## Severity legend");
        pw.println();
        pw.println("- **HIGH**: guaranteed precision loss or `[D cannot be cast to [F` " +
                "at runtime for `DoubleProcessor` pixels.");
        pw.println("- **MEDIUM**: likely precision loss or unchecked type assumption.");
        pw.println("- **LOW**: worth reviewing; often benign but easy to verify.");
        pw.println();

        for (Severity sev : Severity.values()) {
            pw.println("## " + sev + " findings");
            pw.println();
            int n = 0;
            for (Hit h : hits) {
                if (h.severity != sev) continue;
                pw.printf("### %s:%d%n", h.relPath, h.line);
                pw.println();
                pw.println("**Pattern:** " + h.kind);
                pw.println();
                pw.println("**Why it matters:** " + h.rationale);
                pw.println();
                pw.println("```java");
                pw.println(h.snippet.trim());
                pw.println("```");
                pw.println();
                n++;
            }
            if (n == 0) {
                pw.println("_None._");
                pw.println();
            }
        }
    }

    // ----------------------------------------------------------------------
    private String findDefaultRoot() {
        for (String s : DEFAULT_ROOTS) {
            File f = new File(s);
            if (f.exists() && f.isDirectory()) return f.getAbsolutePath();
        }
        return new File(".").getAbsolutePath();
    }

    private void collectJavaFiles(File dir, List<File> into) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory()) {
                String n = c.getName();
                if (n.equals(".git") || n.equals("bin") ||
                        n.equals("build") || n.equals("target") ||
                        n.equals("out")) continue;
                collectJavaFiles(c, into);
            } else if (c.getName().endsWith(".java")) {
                into.add(c);
            }
        }
    }

    private void scanFile(File f, List<Hit> hits, boolean honorIgnore) {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            int n = 0;
            while ((line = br.readLine()) != null) {
                n++;
                String trim = line.trim();
                if (trim.isEmpty() || trim.startsWith("//") || trim.startsWith("*"))
                    continue;
                if (honorIgnore && line.contains("audit-ok")) continue;

                checkPattern(f, n, line, P_FLOAT_CAST_PIXELS,
                        "(float[]) <var>.getPixels()",
                        "Hard cast to float[]. For DoubleProcessor this throws " +
                                "`[D cannot be cast to [F` at runtime.",
                        Severity.HIGH, hits);

                checkPattern(f, n, line, P_FLOAT_CAST_SNAPSHOT,
                        "(float[]) <var>.getSnapshotPixels()",
                        "Same class as the getPixels() cast; will fail for DoubleProcessor snapshots.",
                        Severity.HIGH, hits);

                checkPattern(f, n, line, P_FLOAT_CAST_COPY,
                        "(float[]) <var>.getPixelsCopy()",
                        "Cast assumes float; breaks for double pixel arrays.",
                        Severity.HIGH, hits);

                checkPattern(f, n, line, P_BITDEPTH_32,
                        "bitDepth == 32 / != 32",
                        "Used as a proxy for `is FloatProcessor`. With 64-bit, " +
                                "this branch silently skips DoubleProcessor logic.",
                        Severity.HIGH, hits);

                checkPattern(f, n, line, P_GETPIXELVALUE,
                        "getPixelValue(...)",
                        "Returns float per ImageProcessor contract — for double pixels " +
                                "the value is downcast. Use `getPixelValueDouble(...)` " +
                                "(or read the double[] directly) where precision matters.",
                        Severity.MEDIUM, hits);

                checkPattern(f, n, line, P_GET_INTERPOLATED,
                        "getInterpolatedValue(...)",
                        "Often uses float internally even though it returns double; " +
                                "verify the implementation for DoubleProcessor.",
                        Severity.MEDIUM, hits);

                checkPattern(f, n, line, P_NEW_FLOAT_PROC,
                        "new FloatProcessor(...)",
                        "Constructs a 32-bit processor. If the source is 64-bit, " +
                                "the result silently loses precision.",
                        Severity.MEDIUM, hits);

                checkPattern(f, n, line, P_INSTANCEOF_FLOAT,
                        "instanceof FloatProcessor",
                        "If used in an else-chain without a DoubleProcessor companion, " +
                                "64-bit images fall into the wrong branch.",
                        Severity.LOW, hits);
            }
        } catch (IOException ignored) {
        }
    }

    private void checkPattern(File f, int lineNo, String line, Pattern p,
                              String kind, String rationale, Severity sev,
                              List<Hit> into) {
        Matcher m = p.matcher(line);
        if (m.find()) {
            into.add(new Hit(f, lineNo, line, kind, rationale, sev));
        }
    }

    // ----------------------------------------------------------------------
    private enum Severity {
        LOW(1), MEDIUM(2), HIGH(3);
        final int rank;
        Severity(int r) { this.rank = r; }
    }

    private static class Hit {
        final File file;
        final String path;
        final String relPath;
        final int line;
        final String snippet;
        final String kind;
        final String rationale;
        final Severity severity;

        Hit(File file, int line, String snippet,
            String kind, String rationale, Severity severity) {
            this.file = file;
            this.path = file.getAbsolutePath();
            this.relPath = relativize(file);
            this.line = line;
            this.snippet = snippet;
            this.kind = kind;
            this.rationale = rationale;
            this.severity = severity;
        }

        static String relativize(File f) {
            String abs = f.getAbsolutePath().replace('\\', '/');
            int idx = abs.indexOf("/src/");
            return idx >= 0 ? abs.substring(idx + 1) : abs;
        }
    }
}
