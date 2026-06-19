// DoubleProcessor operation coverage test for SWTImageJ (64-bit images)
// Companion to test_numeric_precision.ijm. Focuses on operations BEYOND
// basic get/setPixel: statistics, min/max, arithmetic, filters, geometry,
// duplicate/crop, fill/ROI, histogram, and 64<->32 conversion.
//
// Design: each check computes a condition into c on its own line, then
// passes c to ok(). Helpers return literal 1/0. Writer is setPixel
// (setValue does not exist in this build). Multi-image work uses image IDs
// (getImageID/selectImage), never selectWindow inside loops.
//
// Tolerances are deliberately loose for filters/geometry (interpolation,
// edge handling, and double-vs-float internals vary by build).

passCount = 0;
failCount = 0;
skipCount = 0;

print("\\Clear");
print("=== SWTImageJ DoubleProcessor ops test ===");

W = 48;
H = 32;

// ---------------------------------------------------------------------------
// SECTION 1: Statistics on 64-bit data
// ---------------------------------------------------------------------------
print("");
print("[stats]");
newImage("st", "64-bit black", W, H, 1);
stID = getImageID();
// fill with a known ramp: v(x,y) = x + y*W  -> values 0 .. W*H-1
for (y = 0; y < H; y++) {
    for (x = 0; x < W; x++) {
        setPixel(x, y, x + y * W);
    }
}
n = W * H;
expMean = (n - 1) / 2.0;
expMin = 0;
expMax = n - 1;

getStatistics(area, mean, min, max, std, histogram);
c = approx(mean, expMean, 0.0001);
r = ok("stats mean matches ramp", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = approx(min, expMin, 0.0001);
r = ok("stats min == 0", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = approx(max, expMax, 0.0001);
r = ok("stats max == n-1", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = (area == n);
r = ok("stats area == pixel count", c);
passCount = passCount + r;
failCount = failCount + (1 - r);

// getValue("Mean") path (uses Analyzer/measurements)
mv = getValue("Mean");
c = approx(mv, expMean, 0.001);
r = ok("getValue Mean matches", c);
passCount = passCount + r;
failCount = failCount + (1 - r);

// Array.getStatistics on a snapshot of the pixels
snap = newArray(n);
for (y = 0; y < H; y++) {
    for (x = 0; x < W; x++) {
        snap[y * W + x] = getPixel(x, y);
    }
}
Array.getStatistics(snap, aMin, aMax, aMean, aStd);
c = approx(aMean, expMean, 0.0001);
r = ok("Array.getStatistics mean", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = approx(aMax, expMax, 0.0001);
r = ok("Array.getStatistics max", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(stID);
close();

// ---------------------------------------------------------------------------
// SECTION 2: Min/Max display range round-trip
// ---------------------------------------------------------------------------
print("");
print("[min/max range]");
newImage("mm", "64-bit black", W, H, 1);
mmID = getImageID();
setPixel(0, 0, -5.0);
setPixel(1, 0, 250.0);
resetMinAndMax();
getMinAndMax(lo, hi);
c = (lo <= -5.0);
r = ok("resetMinAndMax low <= data min", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = (hi >= 250.0);
r = ok("resetMinAndMax high >= data max", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setMinAndMax(10, 90);
getMinAndMax(lo2, hi2);
c = (lo2 == 10);
r = ok("setMinAndMax low sticks", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = (hi2 == 90);
r = ok("setMinAndMax high sticks", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
// pixel data must be unchanged by display range changes
c = (getPixel(1, 0) == 250.0);
r = ok("display range does not alter pixels", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(mmID);
close();

// ---------------------------------------------------------------------------
// SECTION 3: Arithmetic operations (Add / Multiply / Sqrt / Log)
// These route to DoubleProcessor.add/multiply/sqrt/log
// ---------------------------------------------------------------------------
print("");
print("[arithmetic]");

// Add
newImage("ar", "64-bit black", W, H, 1);
arID = getImageID();
setPixel(2, 3, 100.0);
setPixel(4, 5, -50.0);
run("Add...", "value=25");
c = eqStr(getPixel(2, 3), 125.0, 6);
r = ok("Add 25 to 100 == 125", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = eqStr(getPixel(4, 5), -25.0, 6);
r = ok("Add 25 to -50 == -25", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(arID);
close();

// Multiply
newImage("mu", "64-bit black", W, H, 1);
muID = getImageID();
setPixel(1, 1, 3.5);
setPixel(2, 2, -2.0);
run("Multiply...", "value=4");
c = eqStr(getPixel(1, 1), 14.0, 6);
r = ok("Multiply 3.5 by 4 == 14", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = eqStr(getPixel(2, 2), -8.0, 6);
r = ok("Multiply -2 by 4 == -8", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(muID);
close();

// Sqrt
newImage("sq", "64-bit black", W, H, 1);
sqID = getImageID();
setPixel(0, 0, 144.0);
setPixel(1, 0, 2.0);
run("Square Root");
c = eqStr(getPixel(0, 0), 12.0, 6);
r = ok("Sqrt 144 == 12", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = approx(getPixel(1, 0), 1.4142135623730951, 0.000001);
r = ok("Sqrt 2 ~ 1.41421356", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(sqID);
close();

// ---------------------------------------------------------------------------
// SECTION 4: Fill / setColor on a ROI
// ---------------------------------------------------------------------------
print("");
print("[fill/ROI]");
newImage("fl", "64-bit black", W, H, 1);
flID = getImageID();
setColor(123.0);
makeRectangle(4, 4, 8, 6);
fill();
run("Select None");
// inside the rect -> 123, outside -> 0
c = eqStr(getPixel(5, 5), 123.0, 6);
r = ok("fill sets ROI interior", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = eqStr(getPixel(0, 0), 0.0, 6);
r = ok("fill leaves outside untouched", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = eqStr(getPixel(11, 9), 123.0, 6);
r = ok("fill covers far ROI corner", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(flID);
close();

// ---------------------------------------------------------------------------
// SECTION 5: Duplicate and Crop
// ---------------------------------------------------------------------------
print("");
print("[duplicate/crop]");
newImage("src", "64-bit black", W, H, 1);
srcID = getImageID();
for (y = 0; y < H; y++) {
    for (x = 0; x < W; x++) {
        setPixel(x, y, x * 1000.0 + y);
    }
}
run("Duplicate...", "title=dup");
dupID = getImageID();
c = (bitDepth() == 64);
r = ok("duplicate keeps 64-bit", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = eqStr(getPixel(10, 7), 10 * 1000.0 + 7, 6);
r = ok("duplicate preserves values", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
// crop the duplicate to a sub-rect and check origin mapping
makeRectangle(8, 4, 16, 12);
run("Crop");
c = (getWidth() == 16);
r = ok("crop width == 16", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = (getHeight() == 12);
r = ok("crop height == 12", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
// after crop, local (0,0) was global (8,4)
c = eqStr(getPixel(0, 0), 8 * 1000.0 + 4, 6);
r = ok("crop origin maps to (8,4)", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(dupID);
close();
selectImage(srcID);
close();

// ---------------------------------------------------------------------------
// SECTION 6: Histogram via getHistogram (256-bin path on float/double)
// ---------------------------------------------------------------------------
print("");
print("[histogram]");
newImage("hi", "64-bit black", W, H, 1);
hiID = getImageID();
// half the pixels = 10, half = 200
for (y = 0; y < H; y++) {
    for (x = 0; x < W; x++) {
        if (x < W / 2) {
            setPixel(x, y, 10.0);
        }
        if (x >= W / 2) {
            setPixel(x, y, 200.0);
        }
    }
}
resetMinAndMax();
nBins = 256;
getHistogram(histVals, counts, nBins);
total = 0;
for (i = 0; i < counts.length; i++) {
    total = total + counts[i];
}
c = (total == W * H);
r = ok("histogram counts sum to pixel count", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
nonEmpty = 0;
for (i = 0; i < counts.length; i++) {
    if (counts[i] > 0) {
        nonEmpty = nonEmpty + 1;
    }
}
c = (nonEmpty == 2);
r = ok("histogram has exactly 2 populated bins", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(hiID);
close();

// ---------------------------------------------------------------------------
// SECTION 7: Geometry - Translate (integer shift, exact)
// ---------------------------------------------------------------------------
print("");
print("[geometry translate]");
newImage("tr", "64-bit black", W, H, 1);
trID = getImageID();
setPixel(10, 10, 777.0);
run("Translate...", "x=5 y=3 interpolation=None");
// the bright pixel should move from (10,10) to (15,13)
c = eqStr(getPixel(15, 13), 777.0, 3);
r = ok("translate moves pixel by (5,3)", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = eqStr(getPixel(10, 10), 0.0, 3);
r = ok("translate clears original location", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(trID);
close();

// ---------------------------------------------------------------------------
// SECTION 8: Geometry - Scale up 2x (None interpolation, value preserved)
// ---------------------------------------------------------------------------
print("");
print("[geometry scale]");
newImage("sc", "64-bit black", 16, 16, 1);
scID = getImageID();
for (y = 0; y < 16; y++) {
    for (x = 0; x < 16; x++) {
        setPixel(x, y, x + y);
    }
}
run("Scale...", "x=2 y=2 width=32 height=32 interpolation=None create title=scaled");
scaledID = getImageID();
c = (getWidth() == 32);
r = ok("scaled width == 32", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = (getHeight() == 32);
r = ok("scaled height == 32", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = (bitDepth() == 64);
r = ok("scaled stays 64-bit", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
// with nearest-neighbor 2x, original value range is preserved (max == 30)
getStatistics(area2, mean2, min2, max2, std2, hist2);
c = approx(max2, 30, 0.5);
r = ok("scaled value range preserved (max ~30)", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(scaledID);
close();
selectImage(scID);
close();

// ---------------------------------------------------------------------------
// SECTION 9: Filters - Mean 3x3 on a constant region is identity
// (DoubleProcessor.filter / RankFilters path)
// ---------------------------------------------------------------------------
print("");
print("[filter mean]");
newImage("fm", "64-bit black", W, H, 1);
fmID = getImageID();
// constant interior value so a mean filter returns the same value
for (y = 0; y < H; y++) {
    for (x = 0; x < W; x++) {
        setPixel(x, y, 50.0);
    }
}
run("Mean...", "radius=1");
// interior pixel (far from edges) must remain 50
c = approx(getPixel(W / 2, H / 2), 50.0, 0.0001);
r = ok("mean filter identity on constant", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(fmID);
close();

// Mean filter on a step should produce an intermediate value at the boundary
newImage("fm2", "64-bit black", W, H, 1);
fm2ID = getImageID();
for (y = 0; y < H; y++) {
    for (x = 0; x < W; x++) {
        if (x < W / 2) {
            setPixel(x, y, 0.0);
        }
        if (x >= W / 2) {
            setPixel(x, y, 100.0);
        }
    }
}
midx = W / 2;
run("Mean...", "radius=1");
// a pixel right at the step should be between 0 and 100 (blurred)
bv = getPixel(midx, H / 2);
c = (bv > 0);
r = ok("mean filter blurs step (above 0)", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = (bv < 100);
r = ok("mean filter blurs step (below 100)", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(fm2ID);
close();

// ---------------------------------------------------------------------------
// SECTION 10: Gaussian Blur preserves mean (approximately) and stays 64-bit
// ---------------------------------------------------------------------------
print("");
print("[filter gaussian]");
newImage("gb", "64-bit black", W, H, 1);
gbID = getImageID();
for (y = 0; y < H; y++) {
    for (x = 0; x < W; x++) {
        setPixel(x, y, 40.0);
    }
}
run("Gaussian Blur...", "sigma=2");
c = (bitDepth() == 64);
r = ok("gaussian keeps 64-bit", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = approx(getPixel(W / 2, H / 2), 40.0, 0.001);
r = ok("gaussian identity on constant interior", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(gbID);
close();

// ---------------------------------------------------------------------------
// SECTION 11: 64-bit -> 32-bit conversion (precision behavior)
// ---------------------------------------------------------------------------
print("");
print("[convert 64 to 32]");
newImage("cv", "64-bit black", W, H, 1);
cvID = getImageID();
exactBig = 1.234567890123e12;
setPixel(0, 0, exactBig);
setPixel(1, 0, 12.5);
// confirm 64-bit holds it exactly first
c = eqStr(getPixel(0, 0), exactBig, 0);
r = ok("pre-convert 64-bit exact", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
run("32-bit");
c = (bitDepth() == 32);
r = ok("converted to 32-bit", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
// small exact-in-float value survives
c = eqStr(getPixel(1, 0), 12.5, 4);
r = ok("32-bit keeps 12.5 exactly", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
// the 13-digit value is now float-rounded (expected lossy)
c = neStr(getPixel(0, 0), exactBig, 0);
r = ok("32-bit rounds big value (expected lossy)", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(cvID);
close();

// ---------------------------------------------------------------------------
// Summary
// ---------------------------------------------------------------------------
print("");
print("=========================================");
print("  PASSED: " + passCount);
print("  FAILED: " + failCount);
print("  SKIPPED: " + skipCount);
if (failCount == 0) {
    print("  RESULT: ALL RUN TESTS PASSED");
}
if (failCount != 0) {
    print("  RESULT: SOME TESTS FAILED");
}
print("=========================================");

// ---------------------------------------------------------------------------
// Helper functions (at end). Return literal 1/0; never return a comparison.
// ---------------------------------------------------------------------------
function ok(label, cond) {
    if (cond) {
        print("  PASS  " + label);
        return 1;
    }
    print("  FAIL  " + label);
    return 0;
}

function eqStr(a, b, d) {
    s1 = d2s(a, d);
    s2 = d2s(b, d);
    if (s1 == s2) {
        return 1;
    }
    return 0;
}

function neStr(a, b, d) {
    s1 = d2s(a, d);
    s2 = d2s(b, d);
    if (s1 == s2) {
        return 0;
    }
    return 1;
}

function approx(a, b, relTol) {
    if (a == b) {
        return 1;
    }
    denom = abs(a);
    ab = abs(b);
    if (ab > denom) {
        denom = ab;
    }
    diff = abs(a - b);
    if (denom == 0) {
        if (diff <= relTol) {
            return 1;
        }
        return 0;
    }
    e = diff / denom;
    if (e <= relTol) {
        return 1;
    }
    return 0;
}
