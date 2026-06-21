## Eclipse SWTImageJ

Eclipse SWTImageJ is a port of AWT [ImageJ](https://github.com/imagej/ImageJ) to [SWT](https://www.eclipse.org/swt/) to enhance its performance, usability, and possible integration within SWT based[...]
providing users with a more responsive and intuitive interface.
This project was made possible with the financial support of [Lablicate GmbH](https://lablicate.com/) and his owner Philip Wenig.

The SWTImageJ documentation is available here: 

[SWTImageJ Wiki](https://github.com/eclipse-swtimagej/SWTImageJ/wiki)

# 64-bit (Double-Precision) Image Support

SWTImageJ supports **64-bit double-precision floating-point images** in addition to the
standard 8-bit, 16-bit, 32-bit (float), and RGB types. A 64-bit image stores each pixel
as a Java `double`, giving the full IEEE-754 range and ~15–17 significant decimal digits
of precision.

This is useful when:

- Pixel values exceed the range or precision of 32-bit float (e.g. accumulated sums,
  high-dynamic-range measurements, scientific data imported as `double[]`).
- You need to avoid the cumulative rounding error of repeated float operations.
- You are importing data that is natively `double` and want to keep it lossless until
  an explicit conversion.

> **Reported bit depth:** `ImageProcessor.getBitDepth()` returns `64` for these images.
> For *why* and *when* to use 64-bit (including scientific domain examples), see
> 64-bit-use-cases below.

---

## Core class: `DoubleProcessor`

`ij.process.DoubleProcessor` is the 64-bit counterpart to `FloatProcessor`. It extends
`ImageProcessor` and backs the image with a `double[]` pixel array.

### Construction

```java
// Blank image
DoubleProcessor dp = new DoubleProcessor(width, height);

// From an existing double[] (length must equal width*height)
double[] data = ...;
DoubleProcessor dp = new DoubleProcessor(width, height, data);

// From a 2D array [x][y]
double[][] grid = ...;
DoubleProcessor dp = new DoubleProcessor(grid);

// Promoting int[] or float[] data
DoubleProcessor dp = new DoubleProcessor(width, height, intArray);
DoubleProcessor dp = new DoubleProcessor(width, height, floatArray);
```

### Full-precision pixel access

The standard `ImageProcessor` API is `int`/`float`-based, which would silently narrow
`double` values. `DoubleProcessor` therefore adds explicit 64-bit accessors that do **no
bounds checking** and preserve the value exactly:

| Method | Purpose |
| --- | --- |
| `double getd(int x, int y)` | Read a pixel as `double` (no narrowing). |
| `double getd(int index)` | Read by linear index as `double`. |
| `void setd(int x, int y, double v)` | Write a `double` pixel. |
| `void setd(int index, double v)` | Write by linear index. |
| `double getPixelValueDouble(int x, int y)` | Bounds-checked full-precision read. |

> ⚠️ **Precision note:** The inherited `getf()`, `getPixelValue()`, `getPixel()`, and
> `get()` methods still work but **narrow to `float` or `int`**. When you need the exact
> stored value, use `getd()` / `setd()` / `getPixelValueDouble()`.

### Display

A `DoubleProcessor` is rendered by linearly scaling the current display range
(`min`..`max`) to 8-bit (0–255), exactly like `FloatProcessor`. `findMinAndMax()`,
`setMinAndMax()`, and `resetMinAndMax()` behave the same way and are NaN-aware.

### Conversion bridges

- `toFloat(channel, fp)` returns a `FloatProcessor` view (narrowing to `float`).
- `setPixels(channel, fp)` writes back from a `FloatProcessor`.
- `convertToDoubleProcessor()` returns `this` (no-op; **use this to stay 64-bit**).
- `convertToFloat()` returns a **narrowed** `FloatProcessor` copy. This `@Override` exists
  so the precision contract is explicit: every pixel is cast to `float` (losing mantissa
  precision), while display range and color model are preserved. It is invoked by
  float-only consumers — `UnsharpMask`, the `CONVERT_TO_FLOAT` path in
  `PlugInFilterRunner`, and the **Image Calculator** "32-bit (float) result" option — which
  hard-cast the result to `FloatProcessor`. **Do not call it when you need to keep 64-bit
  precision; prefer `convertToDoubleProcessor()` or operate on the `DoubleProcessor`
  directly.**

These bridges are what let existing **float-based filters run unchanged** on 64-bit
images: the framework converts to float, runs the filter, and (where applicable) writes
the result back — see [Plugin/Filter Support](#pluginfilter-support).

---

## File I/O (Save & Open)

64-bit images **round-trip losslessly** through SWTImageJ's native TIFF format as well as
FITS. For TIFF, saving maps the image to `FileInfo.GRAY64_FLOAT`; the encoder writes
`bitsPerSample = 64` with the IEEE-754 *floating-point* sample format (8 bytes/pixel), and
`FileOpener` reads `GRAY64_FLOAT` straight back into a `DoubleProcessor`. Plain **text**
(Text Image) export/import is also lossless for 64-bit data — see
[64-bit Text Image](#64-bit-text-image-lossless-text-round-trip) below.

| Operation | Support |
| --- | --- |
| **File ▸ Save As ▸ Tiff…** | ✅ Full 64-bit (8-byte IEEE-754 samples). |
| **File ▸ Save As ▸ ZIP…** | ✅ (zipped TIFF, full 64-bit). |
| **File ▸ Save As ▸ Raw…** | ✅ Raw `double` stream. |
| **File ▸ Save As ▸ Text Image…** | ✅ Full 64-bit — shortest round-trip-exact decimals (`Double.toString`). |
| **File ▸ Save As ▸ FITS…** | ✅ Full 64-bit — writes `BITPIX = -64` (8-byte IEEE-754 doubles). |
| **File ▸ Open** (TIFF/ZIP) | ✅ Reopens as a 64-bit `DoubleProcessor`. |
| **File ▸ Open ▸ FITS** (`BITPIX = -64`) | ✅ Opens a 64-bit FITS file as a `DoubleProcessor`. |
| **File ▸ Import ▸ Text Image…** (64-bit opt-in) | ✅ Reads as a 64-bit `DoubleProcessor` when enabled; 32-bit float otherwise. |
| **Save ROI / XY Coordinates** | ✅ ROI and coordinate writers accept 64-bit images. |
| JPEG / GIF / BMP / PNG / PGM | ❌ Format-specific writers - not handled - this formats do not support 64-bit). |

> **FITS round-trip:** The FITS writer emits `BITPIX -64` and an 8-byte `writeDouble`
> pixel stream for a `DoubleProcessor`; the FITS reader maps `BITPIX -64` back to
> `GRAY64_FLOAT` → `DoubleProcessor`. RGB images are still rejected by the FITS writer.
>
> **Implementation note:** The generic `PlugInFilter` type gate treats `DOES_ALL` as the
> *legacy five* types only (8-bit gray, 8-bit color, 16-bit, 32-bit, RGB) and **never**
> implies 64-bit. Plugins that support 64-bit therefore OR in the explicit `DOES_64` flag.
> Before this was added, **File ▸ Save As ▸ Tiff…** on a 64-bit image raised a blocking
> "32-bit image required" dialog even though the pixel pipeline already supported it.

### 64-bit Text Image (lossless text round-trip)

A 64-bit image can be saved to a tab/comma-delimited text file and re-imported at full
precision, giving a **byte-exact** save → import → save round-trip.

**Saving** (`ij.io.TextEncoder`) writes each `DoubleProcessor` pixel via `getd(x, y)`
(no float narrowing) and formats it with `Double.toString(value)` — the shortest decimal
string that re-parses to the *exact* same `double`. Calibrated 64-bit images apply the
calibration table at double precision. Byte/Short uncalibrated images keep integer
formatting; all other types keep the legacy `IJ.d2s(value, precision)` formatting.

**Importing** (`ij.plugin.TextReader`) builds a 64-bit `DoubleProcessor` instead of the
legacy 32-bit `FloatProcessor` when 64-bit mode is enabled. There are **two** ways to
enable it:

1. **Persistent option (interactive menu).**
   **Edit ▸ Options ▸ Input/Output ▸ "Open text images as 64-bit (double)"**. When
   checked, **File ▸ Import ▸ Text Image…** opens text images as 64-bit. The state is
   persisted via `TextReader.setOpenAsDouble(...)`.

2. **Macro keyword (scripted).** Pass `use` in the options string:

   ```javascript
   run("Text Image... ", "open=[" + path + "] use");   // 64-bit (DoubleProcessor)
   run("Text Image... ", "open=[" + path + "]");        // 32-bit float (legacy)
   ```

> ⚠️ **The import command name has a trailing space:** `"Text Image... "` (note the space
> before the closing quote). Without it, `run("Text Image...", ...)` resolves to the
> **Save As** writer, not the importer. The two menu labels (File ▸ Import ▸ Text Image…
> and File ▸ Save As ▸ Text Image…) are intentionally distinct — one carries an extra
> space — because ImageJ requires every command label to be unique.
>
> The `use` keyword is matched as a **whole token**, so a path that merely contains the
> letters `use` (e.g. `/Users/me/data.txt`) does **not** accidentally trigger 64-bit mode.

**Round-trip example:**

```javascript
// Lossless 64-bit Text Image round-trip
dir   = getDirectory("temp");
src   = dir + "rt64_a.txt";
reimp = dir + "rt64_b.txt";

run("Text Image... ", "open=[" + src + "] use");   // import as 64-bit
print("bitDepth = " + bitDepth());                  // -> 64
saveAs("Text Image", reimp);                         // re-save
close();

a = File.openAsString(src);
b = File.openAsString(reimp);
print(a == b ? "ROUND-TRIP EXACT" : "DIFFERENCE");   // -> ROUND-TRIP EXACT
```

> **Related fix:** A `NullPointerException` in `ij.text.TextCanvas` (missing SWT font
> data) was fixed by falling back to the display's system font when no font is set, so
> text/table windows paint reliably.

---

## Supported Operations Reference

This section enumerates what works on a 64-bit `DoubleProcessor`, grouped by category.
Unless noted, operations execute **in double precision**. Where the inner loop is shared
with the 32-bit code path via the float bridge, it is marked **(float compute)** — the
pixel data stays `double`, but the intermediate arithmetic is performed in `float`.

### Point (per-pixel) math

All implemented natively in double precision (`DoubleProcessor.process(...)`):

| Operation | Method | Notes |
| --- | --- | --- |
| Add | `add(double)` / `add(int)` | |
| Subtract | via `add(-v)` | |
| Multiply | `multiply(double)` | |
| Set / fill value | `set(double)` | |
| Invert | `invert()` | Uses current display min/max as the inversion range. |
| Gamma | `gamma(double)` | `v ≤ 0 → 0`, else `exp(c·ln v)`. |
| Log | `log()` | |
| Exp | `exp()` | |
| Square | `sqr()` | |
| Square root | `sqrt()` | `v ≤ 0 → 0`. |
| Absolute value | `abs()` | |
| Minimum clamp | `min(double)` | |
| Maximum clamp | `max(double)` | |

**Process ▸ Math menu** (`ImageMath`) exposes these and they all handle 64-bit, including
`abs`, `NaN Background`, and the **Macro… (`v=...`)** expression evaluator (values passed
to/from the interpreter as `double`).

### Image-level filters (`Filters`)

The **Process** menu filters dispatch through the `ImageProcessor` API and run on 64-bit
(the `Filters` plugin declares `DOES_64`):

| Command | Method | Precision |
| --- | --- | --- |
| Invert | `ip.invert()` | Full 64-bit |
| Smooth | `ip.smooth()` | Float compute (3×3 mean) |
| Sharpen | `ip.sharpen()` | Float compute |
| Find Edges | `ip.findEdges()` | Float compute (Sobel) |
| Add Noise / Add Specified Noise | `ip.noise(sd)` | Full 64-bit |

### Bitwise operations

Operate on the `long` bit-pattern of each pixel:

| Operation | Method |
| --- | --- |
| AND | `and(int)` |
| OR | `or(int)` |
| XOR | `xor(int)` |

### Neighborhood / 3×3 filters

Implemented directly in double precision (`DoubleProcessor.filter3x3(...)`):

| Filter | Constant / method |
| --- | --- |
| Blur (3×3 mean) | `filter(BLUR_MORE)` |
| Find Edges (Sobel) | `filter(FIND_EDGES)` |
| 3×3 Convolution | `convolve3x3(int[])` |
| 3×3 Minimum | `filter(MIN)` |
| 3×3 Maximum | `filter(MAX)` |
| 3×3 Median | `medianFilter()` / `filter(MEDIAN_FILTER)` |
| Arbitrary kernel convolution | `convolve(float[], kw, kh)` |

### Geometry / transforms

All native double precision, with bilinear & bicubic interpolation supported where applicable.
The corresponding **Image ▸ Transform** menu commands declare `DOES_64`:

| Operation | Method / Menu |
| --- | --- |
| Rotate | `rotate(double)` · **Image ▸ Transform ▸ Rotate…** (`Rotator`) |
| Translate | `ip.translate(...)` · **Image ▸ Transform ▸ Translate…** (`Translator`) |
| Scale | `scale(double, double)` |
| Resize (bilinear/bicubic) | `resize(w, h)` |
| Downsize | `downsize(...)` |
| Crop | `crop()` |
| Duplicate | `duplicate()` |
| Flip vertical | `flipVertical()` |
| Interpolated pixel read | `getInterpolatedPixel(x, y)` (BILINEAR/BICUBIC) |

### Compositing / image arithmetic (`DoubleBlitter`)

`copyBits(...)` supports the full `Blitter` mode set in double precision. Any non-double
source is first widened via `convertToDoubleProcessor()`:

| Category | Modes |
| --- | --- |
| Copy | `COPY`, `COPY_INVERTED`, `COPY_TRANSPARENT`, `COPY_ZERO_TRANSPARENT` |
| Arithmetic | `ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE`, `AVERAGE`, `DIFFERENCE` |
| Bitwise | `AND`, `OR`, `XOR` (on `long` bit-pattern) |
| Min/Max | `MIN`, `MAX` |

> **Divide-by-zero:** controlled by the `Prefs.DIV_BY_ZERO_VALUE` preference; when set to a
> finite value, division by `0.0` yields that value, otherwise `+∞`.
>
> **Image Calculator:** the **"32-bit (float) result"** option narrows 64-bit operands to
> float via `convertToFloat()`. To keep 64-bit precision, operate on `DoubleProcessor`s
> directly (e.g. via `DoubleBlitter`) rather than requesting a float result.

### Statistics & measurements (`DoubleStatistics`)

Computed directly from the `double[]` data, so **Analyze ▸ Measure**, histograms, and
**Analyze Particles** are full-precision. Supported measurement options:

| Measurement | Notes |
| --- | --- |
| Area, Mean, Min, Max | Core statistics, threshold-aware. |
| Standard deviation | |
| Mode | From the computed histogram. |
| Median | Exact (full sort of in-ROI values). |
| Centroid | |
| Center of mass | |
| Skewness, Kurtosis | Via `calculateMoments`. |
| Area fraction | Threshold- or nonzero-based. |
| Ellipse fit / shape descriptors | |
| Histogram | `getHistogram()` / `getStatistics().histogram`. |

### Thresholding & masking

| Operation | Method | Notes |
| --- | --- | --- |
| Set display threshold | `setThreshold(min, max, lutUpdate)` | Scales thresholds into the display range. |
| Level threshold | `threshold(int level)` | Maps a 0–255 level into the display range. |
| Create binary mask | `createMask()` | Returns a `ByteProcessor`, or `null` if no threshold set. |

### ROI, fill, drawing & metadata

Standard `ImageProcessor` operations work: rectangular and mask-based `fill()`,
`setColor`/`setValue`, `drawPixel`, `snapshot`/`reset` (undo), `getPixelsCopy`,
`setRoi`/`resetRoi`, and `noise(double)`. **Image ▸ Properties…** (calibration, dimensions,
units, frame interval) also supports 64-bit images — it edits only metadata, never pixels.

---

## Precision Boundaries (where values are narrowed to float)

Some shared code paths run in `float` even on a 64-bit image. Pixel **data** stays
`double`, but the operation's intermediate math (or a float-typed sibling class) imposes a
float32 floor (~1e-7 relative error). A bundled diagnostic,
`plugins/64bitTests/Precision_Path_Probe_.java`, measures these boundaries.

| Path | Precision | Why |
| --- | --- | --- |
| `getf(x,y)` / `getPixelValue(int,int)` | float floor | These accessors return `float` by API contract. Use `getd()` / `getPixelValueDouble()` for exact reads. |
| Bicubic interpolation (rotate/scale/resize) | float floor | Bicubic sampling routes through `getf`. (Bilinear interior is exact.) |
| Smooth / Sharpen / Find Edges / Gaussian / rank filters | float compute | Share the 32-bit inner loop via the float bridge. |
| FFT / FHT (`ij.process.FHT`) | float | `FHT extends FloatProcessor`; a 64-bit input is narrowed to float before transform. Convert deliberately for FFT work. |
| Image Calculator "32-bit (float) result" | float | Narrows operands via `convertToFloat()` by design. |

> **Rule of thumb:** point math, bitwise ops, native 3×3 filters, compositing
> (`DoubleBlitter`), statistics, and **file save/open** (TIFF/ZIP/Raw/Text/FITS) are full
> 64-bit. Interpolated geometry, FFT, and the shared kernel/rank filters operate at float
> precision.

---

## Plugin / Filter Support

| Plugin / Filter | 64-bit behavior | Precision |
| --- | --- | --- |
| **Process ▸ Math** (`ImageMath`) | `add/sub/mul/div/set/log/exp/sqr/sqrt/gamma/min/max`, `abs`, `NaN Background`, macro **Code…** all handle `double`. | Full 64-bit |
| **Process ▸ Filters** (`Filters`) | Invert, Smooth, Sharpen, Find Edges, Add Noise (`DOES_64`). | Mixed (see table) |
| **Convolve** (`Convolver`) | True double-precision convolution. | Full 64-bit |
| **Gaussian Blur / Unsharp Mask** | Run via the float bridge / `convertToFloat()`. | Float compute |
| **Rank filters** (Mean, Min, Max, Median, Variance, Remove Outliers, Despeckle) | Read/write `double` pixels; display range via `DoubleStatistics`. | Float compute |
| **Find Maxima** (`MaximumFinder`) | Generic scan uses `getPixelValue`; EDM fast-path is float-only and gated. | Float compute |
| **Analyze Particles** (`ParticleAnalyzer`) | `DoubleProcessor` classified as the FLOAT type; statistics dispatched to `DoubleStatistics`. | Full 64-bit stats |
| **Image ▸ Transform ▸ Rotate / Translate** (`Rotator`, `Translator`) | `DOES_64`; bilinear exact, bicubic at float floor. | Mixed |
| **Image ▸ Properties** (`ImageProperties`) | `DOES_64`; edits metadata only. | N/A (no pixel change) |
| **File ▸ Save As** (`Writer`) | `DOES_64`; Tiff/Zip/Raw/Text save 64-bit. | Full 64-bit (Tiff/Zip/Raw/Text) |
| **File ▸ Import ▸ Text Image** (`TextReader`) | Opt-in 64-bit `DoubleProcessor` via the `use` keyword or the Input/Output option; 32-bit float otherwise. | Full 64-bit (opt-in) |
| **File ▸ Save As ▸ FITS / Open FITS** (`FITS_Writer` / `FITS_Reader`) | Writes/reads `BITPIX -64` ↔ `DoubleProcessor`. | Full 64-bit |

> **Why "Float compute" for some filters?** Kernel-based and rank filters share their
> inner loop with the 32-bit path through `toFloat()`/`convertToFloat()`/`setPixels()`. The
> image data is kept as `double`, but the intermediate filtering math is done in `float`.
> For exact 64-bit results, prefer operations listed as "Full 64-bit".

### Macro support

The **Process ▸ Math ▸ Macro…** expression evaluator (`v=...`) has a dedicated
64-bit branch: pixel values are passed to and read back from the macro interpreter as
`double`, so expressions like `v=v*v` on large values do not overflow or lose precision.

---

## ImageJ Macro Example (Pixel Access)

64-bit images are scriptable from the ImageJ macro language. Create one with
`newImage(...)` using the **"64-bit"** type string, then read and write pixels with the
standard `getValue()` / `getPixel()` / `setPixel()` functions. Values are handled as
doubles, so large magnitudes and fractional values are preserved.

```javascript
// --- Create a 64-bit image -------------------------------------------------
w = 256; h = 256;
newImage("Demo64", "64-bit black", w, h, 1);

// Confirm the type
print("bitDepth = " + bitDepth());          // -> 64

// --- Write pixels at full precision ----------------------------------------
// Fill with a high-dynamic-range pattern that would lose precision in 8/16-bit
for (y = 0; y < h; y++) {
    for (x = 0; x < w; x++) {
        v = 1.0e9 + sin(x/10.0) * cos(y/10.0) * 1.0e6;  // big base + fine detail
        setPixel(x, y, v);
    }
}
updateDisplay();    // refresh after direct pixel writes

// --- Read pixels back -------------------------------------------------------
// getValue() returns the calibrated/real value; getPixel() the raw value.
center = getValue(w/2, h/2);
print("center value      = " + center);
print("getPixel(0,0)     = " + getPixel(0, 0));
print("d2s(center,3)     = " + d2s(center, 3));

// --- Single-pixel round-trip check -----------------------------------------
big = 1.234567890123e12;       // ~12 significant digits, beyond float precision
setPixel(5, 5, big);
back = getPixel(5, 5);
print("stored = " + d2s(big, 0));
print("read   = " + d2s(back, 0));

// --- Use full-precision statistics -----------------------------------------
getStatistics(area, mean, min, max, std);
print("mean = " + mean + ", min = " + min + ", max = " + max);

// --- Point math runs in double precision (Process > Math) ------------------
run("Multiply...", "value=2");   // each pixel * 2, no overflow/rounding loss
run("Add...", "value=0.5");
resetMinAndMax();                // rescale display

// --- Save and reopen at full 64-bit precision (TIFF, FITS, or Text) ---------
saveAs("Tiff", getDirectory("temp") + "demo64.tif");
// saveAs("FITS", getDirectory("temp") + "demo64.fits");  // BITPIX -64, also lossless
// saveAs("Text Image", getDirectory("temp") + "demo64.txt");  // full-precision text
// run("Text Image... ", "open=[" + getDirectory("temp") + "demo64.txt] use");  // 64-bit import
```

> **Macro tips**
> - Use `newImage(..., "64-bit black", ...)` (or `"64-bit white"`/`"64-bit ramp"`) to
>   create a 64-bit image; check with `bitDepth() == 64`.
> - `getPixel(x, y)` returns the raw pixel value; `getValue(x, y)` returns the
>   calibrated value (identical when no calibration function is set).
> - Call `updateDisplay()` after a batch of `setPixel()` writes so the canvas refreshes.
> - For display scaling after large value changes, run `resetMinAndMax()` (or
>   `setMinAndMax(min, max)`).
> - `saveAs("Tiff", path)`, `saveAs("FITS", path)`, and `saveAs("Text Image", path)` all
>   preserve full 64-bit precision; reopening restores a `DoubleProcessor`. For text,
>   re-import with `run("Text Image... ", "open=[path] use")` (note the trailing space in
>   the command name and the `use` keyword). Avoid JPEG/PNG/GIF/BMP/PGM for 64-bit data.
> - When formatting big/fractional values for `print()`/logs, `d2s(value, decimals)`
>   avoids scientific-notation surprises.

---

## Limitations

Some operations are inherently integer-histogram or binary based and are **not** supported
on 64-bit images. They raise a clear error directing you to convert first
(**Image ▸ Type ▸ 8-bit / 16-bit**):

| Operation | Method | Reason |
| --- | --- | --- |
| Auto-threshold | `autoThreshold()` | Requires an integer histogram. |
| Erode | `erode()` | Morphological op defined for 8-bit (matches `FloatProcessor`). |
| Dilate | `dilate()` | Morphological op defined for 8-bit (matches `FloatProcessor`). |
| Convert to RGB stack | `RGBStackConverter` | Requires 8-/16-bit; error suggests **Image ▸ Type** to convert from 32-/64-bit first. |
| Save as JPEG/GIF/BMP/PNG/PGM | `FileSaver` | These formats have no 64-bit float representation. |
| Save as FITS for RGB | `FITS_Writer` | RGB images are rejected by the FITS writer (grayscale 8/16/32/**64**-bit are supported). |

Additional notes:

- **"Apply" in Brightness/Contrast** does not operate on 32-bit or 64-bit images
  (consistent with float behavior).
- **FFT** narrows to float (`FHT extends FloatProcessor`); convert deliberately if you need
  frequency-domain work.
- Operations that round to integer bins (e.g. integer-only histogram tools) reflect the
  display-range scaling, not the raw `double` values.

---

## Working With 64-bit Images — Quick Reference (Java)

```java
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.DoubleProcessor;

// Create a 64-bit image
DoubleProcessor dp = new DoubleProcessor(256, 256);
dp.setd(10, 10, 1.234567890123456e9);          // exact double write
double v = dp.getd(10, 10);                      // exact double read

ImagePlus imp = new ImagePlus("64-bit demo", dp);
imp.show();

// Full-precision point math
dp.multiply(2.0);
dp.add(0.5);
dp.resetMinAndMax();                             // rescale display
imp.updateAndDraw();

// Lossless save / reopen (TIFF, 8-byte IEEE-754 samples; FITS BITPIX -64 also works)
new FileSaver(imp).saveAsTiff("demo64.tif");
// new FileSaver(imp).saveAsFits("demo64.fits");
```

### Tips

- Use `getd`/`setd` (or `getPixelValueDouble`) instead of `getf`/`getPixelValue` whenever
  exactness matters.
- After modifying pixels programmatically, call `resetMinAndMax()` then
  `ImagePlus.updateAndDraw()` to refresh the display.
- To stay 64-bit, prefer `convertToDoubleProcessor()`; `convertToFloat()` **narrows** to
  float (used by Unsharp Mask, Image Calculator's float-result path, and CONVERT_TO_FLOAT).
- Save as **TIFF/ZIP/Raw/Text/FITS** for lossless 64-bit; convert to 8-/16-bit
  (**Image ▸ Type**) before thresholding/morphology/FFT that needs integers or float.

---

## Related Source Files

- `ij/process/DoubleProcessor.java` — the 64-bit image processor (incl. `convertToFloat()`
  narrowing override and `convertToDoubleProcessor()`).
- `ij/process/DoubleStatistics.java` — statistics for 64-bit images.
- `ij/process/DoubleBlitter.java` — `copyBits` / compositing for 64-bit images.
- `ij/ImagePlus.java` — `getFileInfo()` `GRAY64 → GRAY64_FLOAT` (enables Save As).
- `ij/io/TextEncoder.java` — writes 64-bit images as full-precision text using
  `DoubleProcessor.getd` + `Double.toString` (shortest round-trip-exact decimals).
- `ij/plugin/TextReader.java` — imports text images, with opt-in 64-bit (`DoubleProcessor`)
  via the `use` keyword or the Input/Output option; whole-token `use` match avoids paths
  that merely contain "use".
- `ij/plugin/Options.java` — adds the **Edit ▸ Options ▸ Input/Output** checkbox
  "Open text images as 64-bit (double)" (`TextReader.isOpenAsDouble()` /
  `TextReader.setOpenAsDouble(...)`).
- `ij/text/TextCanvas.java` — system-font fallback fixing a paint-time
  `NullPointerException` when no SWT font is set.
- `ij/plugin/FITS_Writer.java` — writes 64-bit FITS (`BITPIX -64`, 8-byte `writeDouble`).
- `ij/plugin/FITS_Reader.java` — opens 64-bit FITS (`BITPIX -64`) into a `DoubleProcessor`.
- `ij/plugin/filter/Writer.java`, `Filters.java`, `Rotator.java`, `Translator.java`,
  `ImageProperties.java`, `RoiWriter.java`, `XYWriter.java` — plugins declaring `DOES_64`.
- `ij/plugin/filter/ImageMath.java`, `ParticleAnalyzer.java`, `RankFilters.java`,
  `Convolver.java` — plugins with explicit 64-bit handling.
- `plugins/64bitTests/Precision_Path_Probe_.java` — diagnostic measuring float-floor paths.
- `plugins/64bitTests/Raw_Macro_Export_Import_Test.ijm` — raw/text 64-bit round-trip
  regression macro.


# Why 64-bit (Double-Precision) Image Support Is Useful

Most image processing works fine in 8-bit, 16-bit, or 32-bit float. **64-bit
double-precision** matters when the *values themselves* — not just the picture — carry
scientific meaning and must survive computation without loss. It stores each pixel as a
Java `double` (IEEE-754), giving the full floating-point range and ~15–17 significant
decimal digits, versus ~6–7 digits for 32-bit float.

## The core problem 64-bit solves

Every integer and float type has a point where it can no longer represent your data
faithfully:

| Type | Range / precision | Breaks down when… |
| --- | --- | --- |
| 8-bit | 0–255 | Any quantitative dynamic range. |
| 16-bit | 0–65,535 (integer) | Values are fractional or exceed 65k. |
| 32-bit float | ~±3.4×10³⁸, ~7 digits | Large magnitudes **and** small details coexist; many operations accumulate. |
| **64-bit double** | ~±1.8×10³⁰⁸, ~16 digits | Rarely — this is the "lossless until you say otherwise" type. |

The float limitation is subtle: it's not usually about *range* (10³⁸ is huge), it's about
**relative precision**. A 32-bit float near `1.0e9` can only resolve steps of roughly
`±64`. So a base level of a billion with meaningful detail at the level of single digits
simply cannot be represented — the detail is rounded away. A `double` resolves that same
value to a fraction of `0.001`.

## When 64-bit is the right choice

### 1. High-dynamic-range (HDR) data
When a single image contains both very large and very small meaningful values at once —
e.g. a bright source and faint surrounding structure measured in the same calibrated units.
Float forces a trade-off between the big numbers and the fine detail; double keeps both.

### 2. Accumulation and integration
Summing or averaging many frames (image stacks, long exposures, Monte-Carlo accumulation,
running integrals). Float rounding error **compounds** with every addition, so a long
accumulation drifts. Double's extra ~9 digits of headroom keeps the running total accurate
across millions of operations.

### 3. Iterative / multi-pass algorithms
Deconvolution, optimization, PDE solvers, repeated filtering, feedback loops. Each pass
feeds its output back as input, so any per-step rounding error is amplified over iterations.
Computing in double dramatically slows that error growth and improves convergence stability.

### 4. Lossless import of native `double` data
Scientific instruments, simulations, and analysis pipelines frequently produce `double[]`
arrays (physical quantities, probabilities, calibrated measurements, derivatives). Loading
them into a 64-bit image keeps the data **bit-exact** until you make a deliberate decision
to convert — no silent narrowing on import.

### 5. Derived and calibrated quantities
After ratios, logs, differences, or calibration functions, values often span many orders of
magnitude or land on tiny fractions (e.g. optical density, ΔF/F, log-ratios, spectral
indices). Keeping these in double avoids quantization artifacts in the results you actually
report.

### 6. Differences of large, nearly-equal numbers
Subtracting two large similar values ("catastrophic cancellation") destroys precision fast
in float — the leading digits cancel and you're left with float's coarse low-order bits.
Double provides far more significant digits to survive the subtraction meaningfully.

---

## Scientific Domains Where 64-bit Images Matter

The use cases above show up constantly in **quantitative science**, where each pixel is a
physical measurement that must survive computation without losing accuracy. The examples
below are grouped by field, and each notes the *specific* precision problem — high dynamic
range, accumulation, cancellation, or native-`double` import — that makes 64-bit the right
tool.

### Astronomy & Astrophysics

The classic high-dynamic-range domain — a single frame routinely spans an enormous brightness range.

- **Deep-sky imaging / HDR co-adds.** A galaxy core can be millions of times brighter than
  its faint outer halo or a background galaxy in the same frame. 32-bit float near a large
  base value can't resolve the faint structure (relative precision ~7 digits); double keeps
  both the bright nucleus and the faint wisps meaningful. *(HDR + relative precision)*
- **Stacking long exposures.** Combining hundreds or thousands of sub-exposures to beat
  read noise is pure accumulation. Float rounding compounds per frame and biases the
  co-add; double's headroom keeps the running sum faithful over very deep stacks.
  *(Accumulation)*
- **Photometry & flat-fielding.** Dividing science frames by flats, subtracting bias/dark,
  and computing calibrated flux involves differences of large, nearly-equal values and
  ratios spanning orders of magnitude — exactly where float loses digits.
  *(Cancellation + derived quantities)*
- **Radio / interferometry & FITS data.** FITS images are frequently stored and processed
  as 64-bit floats; importing them into a 64-bit image keeps the calibrated values
  bit-exact rather than narrowing on load. *(Native-double import)*

### Physics & Engineering

- **Detector accumulation (X-ray, neutron, particle imaging).** Long integrations and
  photon/event counting summed into a real-valued accumulator drift in float over millions
  of additions; double preserves the total. *(Accumulation)*
- **Interferometry & phase maps.** Optical path differences and unwrapped phase fields
  carry tiny fractional values on top of large offsets, and downstream subtraction causes
  catastrophic cancellation in float. *(Cancellation)*
- **Iterative reconstruction & deconvolution.** CT/tomographic reconstruction, Richardson–Lucy
  deconvolution, and PDE/FFT-based solvers feed each pass back as input, amplifying
  per-step rounding. Computing in double improves convergence and final accuracy.
  *(Iterative error growth)*
- **Simulation output as images.** CFD, electromagnetics, and finite-element results are
  natively `double` fields (pressure, temperature, |E|, stress). Visualizing/analyzing them
  as 64-bit images avoids quantizing the simulation before you've measured it.
  *(Native-double import)*

### Biology & Life Sciences

- **Quantitative fluorescence microscopy.** Ratiometric and normalized signals such as
  **ΔF/F** (calcium imaging), FRET ratios, and FRAP recovery curves produce small fractional
  values derived from differences of similar intensities — precision-sensitive and
  cancellation-prone. *(Derived + cancellation)*
- **Live-cell time-lapse integration.** Summing/averaging long time series or many z-slices
  for SNR accumulates rounding error in float across thousands of frames. *(Accumulation)*
- **High-dynamic-range specimens.** Bright labeled structures alongside dim diffuse signal
  in the same field (e.g. nucleus vs. cytoplasmic background) need both extremes represented
  faithfully. *(HDR)*
- **Quantitative phase imaging (QPI).** Optical-path/dry-mass maps are real-valued physical
  quantities, often produced as double, where small phase shifts matter.
  *(Native-double import)*

### Medical Imaging

- **CT Hounsfield units & PET/SPECT quantification.** Calibrated values (HU, SUV, kBq/mL)
  span wide ranges and are the basis of clinical measurement; preserving them through
  arithmetic and registration matters. *(Derived quantities)*
- **Iterative reconstruction (PET/SPECT/CT).** Statistical/iterative reconstructions
  amplify per-iteration error — double stabilizes them. *(Iterative error growth)*
- **Subtraction & perfusion imaging.** DSA (digital subtraction angiography) and perfusion
  maps subtract large, nearly-equal frames — a textbook cancellation case. *(Cancellation)*

### Earth, Climate & Remote Sensing

- **Multispectral indices.** NDVI and similar band-ratio indices are `(A−B)/(A+B)` forms:
  difference-over-sum on large reflectance values, sensitive to float precision loss.
  *(Cancellation + ratios)*
- **Elevation & geophysical models (DEMs, gravity, magnetics).** Stored and processed as
  `double` to keep sub-unit resolution over large absolute values; derivatives (slope,
  gradient) further demand precision. *(Native-double import + derived)*
- **Long-term temporal compositing.** Multi-year averages/anomalies accumulate many values;
  double keeps the running statistics accurate. *(Accumulation)*

### Chemistry & Spectroscopy / Spectral Imaging

- **Absorbance & optical density.** `OD = log10(I0/I)` combines a ratio of large values with
  a log — both precision-sensitive operations. *(Derived + cancellation)*
- **Hyperspectral cubes & background subtraction.** Per-pixel spectra often undergo baseline
  subtraction and normalization where leading digits cancel. *(Cancellation)*
- **Mass-spec / chromatography imaging.** Intensity maps integrated over time or scans
  accumulate, and dynamic range across peaks is large. *(Accumulation + HDR)*

### Pattern Summary

Across every field, the justification reduces to one (or more) of the same needs introduced
in [When 64-bit is the right choice](#when-64-bit-is-the-right-choice):

| Precision need | Representative examples |
| --- | --- |
| **High dynamic range** (large + small meaningful values together) | Galaxy core vs. halo; bright vs. faint fluorescence; spectral peaks |
| **Accumulation** (summing/averaging many values) | Exposure stacking; detector integration; long time-lapse; temporal composites |
| **Catastrophic cancellation** (differences of large, similar values) | Flat-fielding; DSA/perfusion; NDVI; ΔF/F; baseline subtraction |
| **Native-`double` import** (lossless ingest of computed/measured data) | FITS; simulation fields; DEMs; QPI/phase maps |

> These are illustrative, domain-standard examples of *where* double precision is
> scientifically justified — not claims about SWTImageJ-specific features. The actual
> supported and limited operations are documented in
> [`64-bit-support.md`](./64-bit-support.md) (e.g. thresholding and morphology still
> require an 8/16-bit conversion).

---

## When 64-bit is *not* worth it

64-bit doubles the memory footprint (8 bytes/pixel vs. 4 for float, 1 for 8-bit) and is
typically slower. Prefer a smaller type when:

- The image is **display-only** or qualitative (visualization, screenshots, presentation).
- Data is natively integer and within range (most cameras: 8-bit or 16-bit).
- You're memory- or throughput-bound on large stacks and the extra precision changes
  nothing measurable in your result.
- The operation reduces to integer bins anyway (e.g. integer-histogram thresholding,
  morphology) — these aren't supported in 64-bit and need an 8/16-bit conversion regardless.

> **Rule of thumb:** Use 64-bit while you are *computing on values that must stay exact*
> (accumulate, iterate, import, derive). Convert down to 8/16-bit only at the end, for
> display, segmentation, or storage — once you've decided the precision is no longer needed.

## Quick decision guide

```
Is the pixel a measured/derived NUMBER you'll analyze (not just shown)?
│
├─ No  → 8-bit / 16-bit / RGB is fine.
│
└─ Yes → Do large and small meaningful values coexist,
         OR do you accumulate / iterate / import double data?
         │
         ├─ No  → 32-bit float is usually enough.
         │
         └─ Yes → Use 64-bit (double) to stay lossless,
                  then convert down only at the end.
```
