// test_particle_analysis_precision.ijm
//
// Companion macro for the "Particle Precision Test" PlugIn.
// 1. Run Plugins > Particle Precision Test  (creates "Double64_Blobs",
//                                            populates "Results", logs verdict)
// 2. Run this macro                          (prints the per-particle table
//                                            with full double-precision digits)

setBatchMode(false);

if (nResults == 0) {
    print("\\Clear");
    print("Results table is empty.");
    print("Run 'Plugins > Particle Precision Test' first.");
    exit();
}

print("\\Clear");
print("=== Analyze Particles results (decimal=9 in table, 17 in log) ===");
print("ImageJ stores ResultsTable values as double; the table only rounds for *display*.");
print("Compare the 'mean' values below against the PlugIn's reference 'mean' values");
print("(printed earlier in the Log). They should agree to all 17 digits.");
print("");

for (i = 0; i < nResults; i++) {
    print("Particle " + (i+1)
        + "   area="  + d2s(getResult("Area",   i),  9)
        + "   mean="  + d2s(getResult("Mean",   i), 17)
        + "   std="   + d2s(getResult("StdDev", i), 17)
        + "   min="   + d2s(getResult("Min",    i), 17)
        + "   max="   + d2s(getResult("Max",    i), 17)
        + "   cx="    + d2s(getResult("X",      i),  6)
        + "   cy="    + d2s(getResult("Y",      i),  6));
}
