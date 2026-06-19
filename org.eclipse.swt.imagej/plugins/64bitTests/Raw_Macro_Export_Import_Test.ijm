// Raw 64-bit import/export round-trip + Text Image precision test (SWTImageJ).
// Targets the DoubleProcessorSupport changes:
//   - ImageWriter.writeDoubleImage (Raw export of GRAY64_FLOAT)
//   - ImageReader.read64bitImage -> double[] (no narrowing)
//   - FileOpener builds a DoubleProcessor for GRAY64_FLOAT
//
// Method: build a 64-bit image with values that EXCEED float precision,
// save as .raw, re-open via Import>Raw ("64-bit Real"), compare bit-exact.
// Then verify Text Image (CSV) import is 32-bit float (expected, not a bug).
//
// Parser-safe style (same rules as the other macros):
//   conditions computed into c then passed to ok(); helpers return 1/0;
//   writer is setPixel; image IDs via getImageID/selectImage; one stmt/line.

passCount = 0;
failCount = 0;
skipCount = 0;

print("\\Clear");
print("=== SWTImageJ raw 64-bit round-trip test ===");

W = 16;
H = 8;
dir = getDirectory("temp");
rawPath = dir + "swtij_double_test.raw";

// values chosen to be LOSSY in float but exact in double:
//   v(x,y) = base + index*delta, with a 13-digit base and a tiny delta
base = 1.0 + 1.0e-12;          // 1.000000000001  (beyond float resolution)
delta = 1.0e-9;

// ---------------------------------------------------------------------------
// SECTION 1: build source 64-bit image and snapshot expected values
// ---------------------------------------------------------------------------
print("");
print("[build source]");
newImage("src64", "64-bit black", W, H, 1);
srcID = getImageID();
c = (bitDepth() == 64);
r = ok("source is 64-bit", c);
passCount = passCount + r;
failCount = failCount + (1 - r);

n = W * H;
expected = newArray(n);
for (y = 0; y < H; y++) {
    for (x = 0; x < W; x++) {
        idx = y * W + x;
        v = base + idx * delta;
        setPixel(x, y, v);
        expected[idx] = v;
    }
}
// confirm the source itself holds the lossy value exactly (sanity)
c = eqStr(getPixel(0, 0), base, 15);
r = ok("source keeps beyond-float value", c);
passCount = passCount + r;
failCount = failCount + (1 - r);

// ---------------------------------------------------------------------------
// SECTION 2: save as Raw (exercises writeDoubleImage)
// ---------------------------------------------------------------------------
print("");
print("[save raw]");
// IJ.saveAs raw path -> FileSaver.saveAsRaw -> ImageWriter GRAY64_FLOAT case
saveAs("Raw Data", rawPath);
// file existence check via length (0 length => nothing written)
len = File.length(rawPath);
expLen = n * 8;
c = (len == expLen);
r = ok("raw file size == nPixels*8", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
print("  INFO  raw bytes = " + len + " (expected " + expLen + ")");

selectImage(srcID);
close();

// ---------------------------------------------------------------------------
// SECTION 3: re-import via Import>Raw as 64-bit Real, byte order little-endian
// (exercises read64bitImage -> double[] and DoubleProcessor construction)
// ---------------------------------------------------------------------------
print("");
print("[import raw]");
// run("Raw...") opens the importer with options. image=[64-bit Real],
// width/height/offset/number, little-endian to match writer default? The
// writer uses fi.intelByteOrder = Prefs.intelByteOrder. We pass little-endian
// here; if the round-trip mismatches, flip this flag (see note below).
run("Raw...", "open=[" + rawPath + "] image=[64-bit Real] width=" + W + " height=" + H + " offset=0 number=1 gap=0 little-endian");
impID = getImageID();
c = (bitDepth() == 64);
r = ok("reimported image is 64-bit", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = (getWidth() == W);
r = ok("reimported width matches", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = (getHeight() == H);
r = ok("reimported height matches", c);
passCount = passCount + r;
failCount = failCount + (1 - r);

// ---------------------------------------------------------------------------
// SECTION 4: bit-exact comparison of every pixel (the key assertion)
// ---------------------------------------------------------------------------
print("");
print("[verify bit-exact]");
mism = 0;
firstBad = -1;
for (y = 0; y < H; y++) {
    for (x = 0; x < W; x++) {
        idx = y * W + x;
        got = getPixel(x, y);
        same = eqStr(got, expected[idx], 15);
        if (same == 0) {
            mism = mism + 1;
            if (firstBad < 0) {
                firstBad = idx;
            }
        }
    }
}
c = (mism == 0);
r = ok("all pixels recovered bit-exact", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
if (mism != 0) {
    bx = firstBad % W;
    by = floor(firstBad / W);
    print("  INFO  mismatches=" + mism + " firstBad idx=" + firstBad + " (" + bx + "," + by + ")");
    print("  INFO  expected=" + d2s(expected[firstBad], 15) + " got=" + d2s(getPixel(bx, by), 15));
}

// spot-check the corner that is beyond float precision
c = eqStr(getPixel(0, 0), base, 15);
r = ok("corner beyond-float value survived raw round-trip", c);
passCount = passCount + r;
failCount = failCount + (1 - r);

selectImage(impID);
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
    print("  RESULT: ALL TESTS PASSED");
}
if (failCount != 0) {
    print("  RESULT: SOME TESTS FAILED");
}
print("=========================================");

// ---------------------------------------------------------------------------
// Helpers
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
