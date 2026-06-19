// Numeric correctness test for SWTImageJ macro pixel access
// Part A: per-type round-trip + clamp (8/16/signed-16/32/64-bit)
// Part B: 64-bit read / access / change / write
// Part C: DoubleProcessor loop tests (fill, RMW, index, stencil, transpose, sum)
//
// Writer is setPixel (setValue does NOT exist in this build).
// Reader is getPixel, except signed-16 uses getValue so calibration applies.
//
// Parser-safe rules:
//  * Conditions computed into c on their own line, then passed to ok().
//  * Helpers return literal 1/0 via if; never return a comparison.
//  * One statement per line. No += . Functions at end. Explicit braces.
//  * No backslash escapes in strings (use print("") for blank lines).
//  * No long plain-decimal literals (build them with + and e-notation).

passCount = 0;
failCount = 0;

print("\\Clear");
print("=== SWTImageJ numeric precision test ===");

imgW = 32;
imgH = 32;

print("");
print("[8-bit]");
newImage("t8", "8-bit black", imgW, imgH, 1);
c = (bitDepth() == 8);
r = ok("8-bit bitDepth 8", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setPixel(0, 0, 200);
c = (getPixel(0, 0) == 200);
r = ok("8-bit store read 200", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setPixel(1, 0, 255);
c = (getPixel(1, 0) == 255);
r = ok("8-bit max 255", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setPixel(2, 0, 300);
c = (getPixel(2, 0) == 255);
r = ok("8-bit clamp 300 to 255", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setPixel(3, 0, -10);
c = (getPixel(3, 0) == 0);
r = ok("8-bit clamp -10 to 0", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
close();

print("");
print("[16-bit unsigned]");
newImage("t16", "16-bit black", imgW, imgH, 1);
c = (bitDepth() == 16);
r = ok("16-bit bitDepth 16", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setPixel(0, 0, 12345);
c = (getPixel(0, 0) == 12345);
r = ok("16-bit store read 12345", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setPixel(1, 0, 65535);
c = (getPixel(1, 0) == 65535);
r = ok("16-bit max 65535", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setPixel(2, 0, 70000);
c = (getPixel(2, 0) == 65535);
r = ok("16-bit clamp 70000 to 65535", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setPixel(3, 0, -5);
c = (getPixel(3, 0) == 0);
r = ok("16-bit clamp -5 to 0", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
close();

print("");
print("[16-bit signed]");
newImage("t16s", "16-bit black", imgW, imgH, 1);
c = (bitDepth() == 16);
r = ok("signed-16 bitDepth 16", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setPixel(0, 0, 33768);
run("Calibrate...", "function=[Straight Line] text1=[0 65535] text2=[-32768 32767]");
sv = getValue(0, 0);
c = approx(sv, 1000, 0.0001);
r = ok("signed-16 reads about 1000", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
close();

print("");
print("[32-bit float]");
newImage("t32", "32-bit black", imgW, imgH, 1);
c = (bitDepth() == 32);
r = ok("32-bit bitDepth 32", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setPixel(0, 0, 3.14159265358979);
c = approx(getPixel(0, 0), 3.14159265358979, 0.000001);
r = ok("32-bit keeps fraction", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setPixel(1, 0, -123.5);
c = eqStr(getPixel(1, 0), -123.5, 1);
r = ok("32-bit negative", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setPixel(2, 0, 1.0e30);
c = approx(getPixel(2, 0), 1.0e30, 0.000001);
r = ok("32-bit large 1e30", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
bigF = 1.234567890123e12;
setPixel(3, 0, bigF);
c = neStr(getPixel(3, 0), bigF, 0);
r = ok("32-bit float floor expected lossy", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
close();

print("");
print("[64-bit double]");
newImage("t64", "64-bit black", imgW, imgH, 1);
c = (bitDepth() == 64);
r = ok("64-bit bitDepth 64", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
big = 1.234567890123e12;
setPixel(5, 5, big);
c = eqStr(getPixel(5, 5), big, 0);
r = ok("64-bit setPixel getPixel exact", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
hpval = 1.0 + 1.0e-12;
setPixel(6, 6, hpval);
c = eqStr(getPixel(6, 6), hpval, 15);
r = ok("64-bit keeps tiny detail", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
setPixel(7, 7, 5.0e9);
c = (getPixel(7, 7) == 5.0e9);
r = ok("64-bit no int clamp 5e9", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
c = (getPixel(7, 7) != 2147483647);
r = ok("64-bit not 2147483647", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
negv = -9876543210.0 - 0.125;
setPixel(8, 8, negv);
c = eqStr(getPixel(8, 8), negv, 3);
r = ok("64-bit negative fractional", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
idxA = 2 * imgW + 4;
setPixel(idxA, 7.7777777777e11);
c = eqStr(getPixel(idxA), 7.7777777777e11, 1);
r = ok("64-bit 1-arg index", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
close();

print("");
print("[64-bit read access change write]");
newImage("dp", "64-bit black", 16, 16, 1);
c = (bitDepth() == 64);
r = ok("is 64-bit", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
valB = 1.234567890123456e9;
setPixel(3, 4, valB);
rB = getPixel(3, 4);
c = eqStr(rB, valB, 6);
r = ok("write then read exact", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
oldB = getPixel(3, 4);
setPixel(3, 4, oldB * 2);
c = eqStr(getPixel(3, 4), oldB * 2, 3);
r = ok("read modify write x2", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
hp2 = 9.876543210987e11;
setPixel(5, 6, hp2);
c = eqStr(getPixel(5, 6), hp2, 1);
r = ok("beyond float precision lossless", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
resetMinAndMax();
updateDisplay();
close();

print("");
print("[64-bit loop tests]");
LW = 64;
LH = 48;
newImage("loop64", "64-bit black", LW, LH, 1);
c = (bitDepth() == 64);
r = ok("loop image is 64-bit", c);
passCount = passCount + r;
failCount = failCount + (1 - r);

base = 5.0e9;
for (y = 0; y < LH; y++) {
    for (x = 0; x < LW; x++) {
        setPixel(x, y, base + x * 1.0e6 + y);
    }
}
c1bad = 0;
for (y = 0; y < LH; y++) {
    for (x = 0; x < LW; x++) {
        wantC1 = base + x * 1.0e6 + y;
        chk = eqStr(getPixel(x, y), wantC1, 3);
        if (chk == 0) {
            c1bad = c1bad + 1;
        }
    }
}
c = (c1bad == 0);
r = ok("C1 fill exact all pixels", c);
passCount = passCount + r;
failCount = failCount + (1 - r);

for (y = 0; y < LH; y++) {
    for (x = 0; x < LW; x++) {
        vC2 = getPixel(x, y);
        setPixel(x, y, vC2 * 2 + 1);
    }
}
c2bad = 0;
for (y = 0; y < LH; y++) {
    for (x = 0; x < LW; x++) {
        prevC2 = base + x * 1.0e6 + y;
        wantC2 = prevC2 * 2 + 1;
        chk = eqStr(getPixel(x, y), wantC2, 3);
        if (chk == 0) {
            c2bad = c2bad + 1;
        }
    }
}
c = (c2bad == 0);
r = ok("C2 read modify write whole image", c);
passCount = passCount + r;
failCount = failCount + (1 - r);

count = LW * LH;
for (i = 0; i < count; i++) {
    setPixel(i, i * 1.5e9 + 0.25);
}
c3bad = 0;
for (y = 0; y < LH; y++) {
    for (x = 0; x < LW; x++) {
        ii = y * LW + x;
        wantC3 = ii * 1.5e9 + 0.25;
        chk = eqStr(getPixel(x, y), wantC3, 2);
        if (chk == 0) {
            c3bad = c3bad + 1;
        }
    }
}
c = (c3bad == 0);
r = ok("C3 index write then xy read agree", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
close();

newImage("srcC4", "64-bit black", LW, LH, 1);
for (y = 0; y < LH; y++) {
    for (x = 0; x < LW; x++) {
        setPixel(x, y, x * 1.0e8 + y * 1.0e5);
    }
}
newImage("srcC4", "64-bit black", LW, LH, 1);
for (y = 0; y < LH; y++) {
    for (x = 0; x < LW; x++) {
        setPixel(x, y, x * 1.0e8 + y * 1.0e5);
    }
}
// snapshot src into an array (index = y*LW + x) so we never switch windows mid-loop
srcArr = newArray(LW * LH);
for (y = 0; y < LH; y++) {
    for (x = 0; x < LW; x++) {
        srcArr[y * LW + x] = getPixel(x, y);
    }
}
newImage("dstC4", "64-bit black", LW, LH, 1);
for (y = 1; y < LH - 1; y++) {
    for (x = 1; x < LW - 1; x++) {
        s = srcArr[(y-1)*LW + (x-1)] + srcArr[(y-1)*LW + x] + srcArr[(y-1)*LW + (x+1)] + srcArr[y*LW + (x-1)] + srcArr[y*LW + x] + srcArr[y*LW + (x+1)] + srcArr[(y+1)*LW + (x-1)] + srcArr[(y+1)*LW + x] + srcArr[(y+1)*LW + (x+1)];
        setPixel(x, y, s);
    }
}
c4bad = 0;
want55 = 9 * 5 * 1.0e8 + 9 * 5 * 1.0e5;
chk = eqStr(getPixel(5, 5), want55, 2);
if (chk == 0) {
    c4bad = c4bad + 1;
}
want3020 = 9 * 30 * 1.0e8 + 9 * 20 * 1.0e5;
chk = eqStr(getPixel(30, 20), want3020, 2);
if (chk == 0) {
    c4bad = c4bad + 1;
}
c = (c4bad == 0);
r = ok("C4 stencil 3x3 neighbor sum", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectWindow("srcC4");
close();
selectWindow("dstC4");
close();

NN = 24;
newImage("srcC5", "64-bit black", NN, NN, 1);
for (y = 0; y < NN; y++) {
    for (x = 0; x < NN; x++) {
        setPixel(x, y, x * 1.0e7 + y);
    }
}
NN = 24;
newImage("srcC5", "64-bit black", NN, NN, 1);
srcID = getImageID();
for (y = 0; y < NN; y++) {
    for (x = 0; x < NN; x++) {
        setPixel(x, y, x * 1.0e7 + y);
    }
}
// snapshot src into an array so we don't switch windows mid-loop
src5 = newArray(NN * NN);
for (y = 0; y < NN; y++) {
    for (x = 0; x < NN; x++) {
        src5[y * NN + x] = getPixel(x, y);
    }
}
newImage("dstC5", "64-bit black", NN, NN, 1);
dstID = getImageID();
for (y = 0; y < NN; y++) {
    for (x = 0; x < NN; x++) {
        setPixel(y, x, src5[y * NN + x]);
    }
}
c5bad = 0;
selectImage(dstID);
for (y = 0; y < NN; y++) {
    for (x = 0; x < NN; x++) {
        wantC5 = y * 1.0e7 + x;
        chk = eqStr(getPixel(x, y), wantC5, 1);
        if (chk == 0) {
            c5bad = c5bad + 1;
        }
    }
}
c = (c5bad == 0);
r = ok("C5 transpose copy", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
selectImage(srcID);
close();
selectImage(dstID);
close();

MM = 40;
newImage("accC6", "64-bit black", MM, MM, 1);
for (y = 0; y < MM; y++) {
    for (x = 0; x < MM; x++) {
        setPixel(x, y, 1.0e6 + (y * MM + x));
    }
}
total = 0;
for (y = 0; y < MM; y++) {
    for (x = 0; x < MM; x++) {
        total = total + getPixel(x, y);
    }
}
nn = MM * MM;
wantTotal = nn * 1.0e6 + (nn - 1) * nn / 2;
c = eqStr(total, wantTotal, 1);
r = ok("C6 whole image accumulation", c);
passCount = passCount + r;
failCount = failCount + (1 - r);
close();

print("");
print("=========================================");
print("  PASSED: " + passCount);
print("  FAILED: " + failCount);
if (failCount == 0) {
    print("  RESULT: ALL TESTS PASSED");
}
if (failCount != 0) {
    print("  RESULT: SOME TESTS FAILED");
}
print("=========================================");

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
