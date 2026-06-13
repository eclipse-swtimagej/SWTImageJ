// Test_64bit.ijm
// Round-trip and validation tests for 64-bit images.

outDir = getDirectory("home") + "test64/";
File.makeDirectory(outDir);

print("\\Clear");
print("=== 64-bit test macro ===");
print("Output dir: " + outDir);

// ---------- 1. Single image ----------
newImage("test64-single", "64-bit black", 64, 48, 1);
w = getWidth(); h = getHeight();

for (y = 0; y < h; y++) {
    for (x = 0; x < w; x++) {
        v = 1.0e8 + (y * w + x) * 0.01;   // double-only resolution at 1e8
        setPixel(x, y, v);
    }
}
updateResults();

bd = bitDepth();
print("Single image bitDepth = " + bd + " (expected 64)");
if (bd != 64) exit("FAIL: single image bitDepth != 64");

v0 = getPixel(0, 0);
v1 = getPixel(1, 0);
print("pixel[0,0] = " + v0);
print("pixel[1,0] = " + v1);
print("delta      = " + (v1 - v0) + " (expected ~0.01)");

path = outDir + "test64-single.tif";
saveAs("Tiff", path);
print("Saved: " + path);

close();
open(path);

bd2 = bitDepth();
print("Reopened single image bitDepth = " + bd2 + " (expected 64)");
if (bd2 != 64) exit("FAIL: reopened single image bitDepth != 64");

v0r = getPixel(0, 0);
v1r = getPixel(1, 0);
print("reopened pixel[0,0] = " + v0r);
print("reopened pixel[1,0] = " + v1r);

if (v0r != v0) exit("FAIL: pixel[0,0] not preserved on reopen");
if (v1r != v1) exit("FAIL: pixel[1,0] not preserved on reopen");
close();

// ---------- 2. Stack ----------
newImage("test64-stack", "64-bit black", 32, 32, 5);
for (s = 1; s <= nSlices; s++) {
    setSlice(s);
    for (y = 0; y < getHeight(); y++) {
        for (x = 0; x < getWidth(); x++) {
            setPixel(x, y, 1.0e8 + s * 1000.0 + x * 0.01);
        }
    }
}
bd = bitDepth();
print("Stack bitDepth = " + bd + " (expected 64)");
if (bd != 64) exit("FAIL: stack bitDepth != 64");

setSlice(3);
v = getPixel(5, 5);
print("stack slice 3, pixel[5,5] = " + v);

stackPath = outDir + "test64-stack.tif";
saveAs("Tiff", stackPath);
print("Saved: " + stackPath);

close();
open(stackPath);

bd2 = bitDepth();
print("Reopened stack bitDepth = " + bd2 + " (expected 64)");
if (bd2 != 64) exit("FAIL: reopened stack bitDepth != 64");
if (nSlices != 5) exit("FAIL: expected 5 slices, got " + nSlices);

setSlice(3);
v2 = getPixel(5, 5);
print("reopened slice 3, pixel[5,5] = " + v2);
if (v2 != v) exit("FAIL: stack pixel value not preserved on reopen");
close();

// ---------- 3. Hyperstack ----------
newImage("test64-hyper", "64-bit black", 32, 32, 2 * 3 * 4); // c*z*t
Stack.setDimensions(2, 3, 4);

for (c = 1; c <= 2; c++) {
    for (z = 1; z <= 3; z++) {
        for (t = 1; t <= 4; t++) {
            Stack.setPosition(c, z, t);
            setPixel(0, 0, 1.0e8 + c*1e3 + z*1e1 + t*0.01);
        }
    }
}
Stack.setPosition(2, 3, 4);
v = getPixel(0, 0);
print("hyper c=2,z=3,t=4, pixel[0,0] = " + v);

hyperPath = outDir + "test64-hyper.tif";
saveAs("Tiff", hyperPath);
print("Saved: " + hyperPath);

close();
open(hyperPath);

bd2 = bitDepth();
if (bd2 != 64) exit("FAIL: reopened hyperstack bitDepth != 64");
Stack.getDimensions(w, h, ch, sl, fr);
print("reopened hyperstack dims = " + w + "x" + h + ", c=" + ch + ", z=" + sl + ", t=" + fr);
if (ch != 2 || sl != 3 || fr != 4) exit("FAIL: hyperstack dimensions not preserved");

Stack.setPosition(2, 3, 4);
v2 = getPixel(0, 0);
print("reopened hyper c=2,z=3,t=4, pixel[0,0] = " + v2);
if (v2 != v) exit("FAIL: hyperstack pixel not preserved on reopen");
close();

print("\\n=== ALL 64-BIT MACRO TESTS PASSED ===");
