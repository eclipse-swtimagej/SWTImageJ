/*******************************************************************************
 * Copyright (c) 2023, 2026 Lablicate GmbH.
 *
 * All rights reserved.
 * 
 * Contributors:
 * Marcel Austenfeld - initial API and implementation
 *******************************************************************************/


import java.awt.AWTEvent;
import java.util.stream.IntStream;

import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import org.eclipse.swt.events.*;

/**
 * Vibe coded ImageJ filter plugin to speed up thread safe filters!
 * 
 * Ultimate Scientific Filter Pro - Final Version (with DoG) Features: Huang
 * (8-bit), QuickSelect (16/32-bit), Difference of Gaussians (DoG), Sobel,
 * Variance, Wiener, Laplacian, Unsharp Mask, Top-Hats, Otsu Threshold.
 * The Median filter uses the fast Separable Median-Filter (Approximation) for RGB, 16 or 32-bit!
 * An exact method is available (no GUI option).
 * See e.g.: https://info.iut-bm.univ-fcomte.fr/staff/perrot/separable-median/
 */
public class ImageJ_Scientific_Filter_ implements ExtendedPlugInFilter, DialogListener {
	ImagePlus imp;
	int flags = DOES_8G + DOES_16 + DOES_32 + DOES_RGB + DOES_STACKS + PARALLELIZE_STACKS;

	String type = "Median";
	String edgeMode = "Mirror";
	String sobelDir = "Magnitude";
	int radius = 2;
	int radius2 = 4; // Speziell für DoG
	float noiseSigma = 10.0f;
	float gain = 1.0f;
	boolean autoThreshold = false;

	@Override
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return flags;
	}

	@Override
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		GenericDialog gd = new GenericDialog("ImageJ Scientific Filter");
		String[] filters = { "Median", "Min", "Max", "Gaussian (Fast)", "DoG (Diff. of Gaussians)", "Unsharp Mask",
				"White Top-Hat", "Black Top-Hat", "Wiener", "Laplacian", "Sobel", "Variance" };
		gd.addChoice("Filter_Type:", filters, type);
		gd.addChoice("Edge_Handling:", new String[] { "Mirror", "Clamp", "Wrap" }, edgeMode);
		gd.addChoice("Sobel_Direction:", new String[] { "Magnitude", "X-Axis", "Y-Axis" }, sobelDir);
		gd.addSlider("Radius (Sigma 1):", 1, 100, radius);
		gd.addSlider("Radius 2 (Sigma 2 - DoG):", 1, 100, radius2);
		gd.addNumericField("Noise_Sigma (Wiener):", noiseSigma, 1);
		gd.addSlider("Gain (Laplace/Unsharp):", 0.1, 5.0, gain);
		gd.addCheckbox("Auto_Threshold (Otsu)", autoThreshold);

		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.showDialog();
		return gd.wasCanceled() ? DONE : flags;
	}

	@Override
	public boolean dialogItemChanged(GenericDialog gd, TypedEvent e) {
		type = gd.getNextChoice();
		edgeMode = gd.getNextChoice();
		sobelDir = gd.getNextChoice();
		radius = (int) gd.getNextNumber();
		radius2 = (int) gd.getNextNumber();
		noiseSigma = (float) gd.getNextNumber();
		gain = (float) gd.getNextNumber();
		autoThreshold = gd.getNextBoolean();
		return true;
	}

	@Override
	public void setNPasses(int nPasses) {
	}

	@Override
	public void run(ImageProcessor ip) {
		if (Thread.currentThread().isInterrupted())
			return;
		final int r = this.radius, r2 = this.radius2;
		final String t = this.type;
		final float nV = this.noiseSigma * this.noiseSigma, g = this.gain;
		final String em = this.edgeMode;
		final int w = ip.getWidth(), h = ip.getHeight();

		if (ip instanceof ByteProcessor && (t.equals("Median") || t.equals("Min") || t.equals("Max"))) {
			runHuang8Bit((ByteProcessor) ip, t, r, em);
		} else if (ip instanceof ColorProcessor) {
			processRGB((ColorProcessor) ip, t, r, r2, em, nV, g);
		} else {
			float[] pix = (float[]) ip.toFloat(0, null).getPixels();
			applyFilterLogic(pix, w, h, t, r, r2, nV, g, em);
			if (autoThreshold && (t.equals("Sobel") || t.equals("Variance") || t.equals("Laplacian")
					|| t.equals("DoG (Diff. of Gaussians)")))
				applyOtsu(pix, w, h);
			fillProcessor(ip, pix, w, h);
		}
	}

	private void applyFilterLogic(float[] pix, int w, int h, String t, int r, int r2, float nV, float g, String em) {
		if (Thread.currentThread().isInterrupted())
			return;
		// String-Checks aus den Schleifen ziehen
		if (t.equals("Min")) {
			runMinMax(pix, w, h, r, true);
		} else if (t.equals("Max")) {
			runMinMax(pix, w, h, r, false);
		} else if (t.equals("Median")) {
			runSeparableMedian(pix, w, h, r, em);
		} else if (t.contains("Top-Hat")) {
			runTopHat(pix, w, h, t, r, em);
		} else if (t.equals("Gaussian (Fast)")) {
			float[] k = createK(r);
			blur(pix, w, h, k, true, r, em);
			blur(pix, w, h, k, false, r, em);
		} else if (t.equals("DoG (Diff. of Gaussians)"))
			runDoG(pix, w, h, r, r2, em);
		else if (t.equals("Unsharp Mask"))
			runUnsharpMask(pix, w, h, r, g, em);
		else if (t.equals("Wiener"))
			runWiener(pix, w, h, r, em, nV);
		else if (t.equals("Laplacian"))
			runLaplacian(pix, w, h, em, g);
		else if (t.equals("Sobel"))
			runSobel(pix, w, h, em, sobelDir);
		else if (t.equals("Variance"))
			runVariance(pix, w, h, r, em);
	}

	private void runDoG(float[] d, int w, int h, int r1, int r2, String em) {
		float[] blur1 = d.clone();
		float[] blur2 = d.clone();
		float[] k1 = createK(r1);
		float[] k2 = createK(r2);

		blur(blur1, w, h, k1, true, r1, em);
		blur(blur1, w, h, k1, false, r1, em);
		blur(blur2, w, h, k2, true, r2, em);
		blur(blur2, w, h, k2, false, r2, em);

		for (int i = 0; i < d.length; i++)
			d[i] = blur1[i] - blur2[i];
	}

	private void runSobel(float[] d, int w, int h, String em, String dir) {
		float[] out = new float[d.length];
		IntStream.range(0, h).parallel().forEach(y -> {
			for (int x = 0; x < w; x++) {
				float gx = (-1 * v(d, x - 1, y - 1, w, h, em) + 1 * v(d, x + 1, y - 1, w, h, em)
						- 2 * v(d, x - 1, y, w, h, em) + 2 * v(d, x + 1, y, w, h, em) - 1 * v(d, x - 1, y + 1, w, h, em)
						+ 1 * v(d, x + 1, y + 1, w, h, em));
				float gy = (-1 * v(d, x - 1, y - 1, w, h, em) - 2 * v(d, x, y - 1, w, h, em)
						- 1 * v(d, x + 1, y - 1, w, h, em) + 1 * v(d, x - 1, y + 1, w, h, em)
						+ 2 * v(d, x, y + 1, w, h, em) + 1 * v(d, x + 1, y + 1, w, h, em));
				if (dir.equals("X-Axis"))
					out[x + y * w] = Math.abs(gx);
				else if (dir.equals("Y-Axis"))
					out[x + y * w] = Math.abs(gy);
				else
					out[x + y * w] = (float) Math.sqrt(gx * gx + gy * gy);
			}
		});
		System.arraycopy(out, 0, d, 0, d.length);
	}

	private void runVariance(float[] d, int w, int h, int r, String em) {
		float[] out = new float[d.length];
		float n = (2 * r + 1) * (2 * r + 1);
		IntStream.range(0, h).parallel().forEach(y -> {
			for (int x = 0; x < w; x++) {
				float s = 0, sq = 0;
				for (int ky = -r; ky <= r; ky++) {
					for (int kx = -r; kx <= r; kx++) {
						float val = v(d, x + kx, y + ky, w, h, em);
						s += val;
						sq += val * val;
					}
				}
				out[x + y * w] = (sq - (s * s) / n) / (n - 1);
			}
		});
		System.arraycopy(out, 0, d, 0, d.length);
	}

	private void applyOtsu(float[] d, int w, int h) {
		int[] hist = new int[256];
		float minVal = Float.MAX_VALUE, maxVal = -Float.MAX_VALUE;
		for (float v : d) {
			if (v < minVal)
				minVal = v;
			if (v > maxVal)
				maxVal = v;
		}
		if (maxVal == minVal)
			return;
		for (float v : d)
			hist[Math.min(255, (int) ((v - minVal) / (maxVal - minVal) * 255))]++;
		int total = d.length;
		float sum = 0;
		for (int i = 0; i < 256; i++)
			sum += i * hist[i];
		float sumB = 0;
		int wB = 0, wF = 0;
		float varMax = 0, threshold = 0;
		for (int i = 0; i < 256; i++) {
			wB += hist[i];
			if (wB == 0)
				continue;
			wF = total - wB;
			if (wF == 0)
				break;
			sumB += (float) (i * hist[i]);
			float mB = sumB / wB, mF = (sum - sumB) / wF;
			float varBetween = (float) wB * (float) wF * (mB - mF) * (mB - mF);
			if (varBetween > varMax) {
				varMax = varBetween;
				threshold = i;
			}
		}
		float realT = minVal + (threshold / 255 * (maxVal - minVal));
		for (int i = 0; i < d.length; i++)
			d[i] = (d[i] >= realT) ? 255 : 0;
	}

	private void runHuang8Bit(ByteProcessor ip, String m, int r, String em) {
		byte[] s = (byte[]) ip.getPixels();
		byte[] d = new byte[s.length];
		int w = ip.getWidth(), h = ip.getHeight(), sz = (2 * r + 1) * (2 * r + 1);
		IntStream.range(0, h).parallel().forEach(y -> {
			int[] hist = new int[256];
			for (int x = 0; x < w; x++) {
				if (x == 0) {
					for (int ky = -r; ky <= r; ky++) {
						int py = getIdx(y + ky, h, em) * w;
						for (int kx = -r; kx <= r; kx++)
							hist[s[getIdx(kx, w, em) + py] & 0xff]++;
					}
				} else {
					int xO = getIdx(x - r - 1, w, em), xI = getIdx(x + r, w, em);
					for (int ky = -r; ky <= r; ky++) {
						int py = getIdx(y + ky, h, em) * w;
						hist[s[xO + py] & 0xff]--;
						hist[s[xI + py] & 0xff]++;
					}
				}
				d[x + y * w] = (byte) getRankH(hist, m, sz);
			}
		});
		System.arraycopy(d, 0, s, 0, s.length);
	}

	private int getRankH(int[] h, String m, int sz) {
		if (m.equals("Min")) {
			for (int i = 0; i < 256; i++)
				if (h[i] > 0)
					return i;
		} else if (m.equals("Max")) {
			for (int i = 255; i >= 0; i--)
				if (h[i] > 0)
					return i;
		} else {
			int c = 0, t = sz / 2;
			for (int i = 0; i < 256; i++) {
				c += h[i];
				if (c > t)
					return i;
			}
		}
		return 0;
	}

	private void processRGB(ColorProcessor cp, String t, int r, int r2, String em, float nV, float g) {
		int[] pix = (int[]) cp.getPixels();
		int w = cp.getWidth(), h = cp.getHeight();
		float[] red = new float[pix.length], gr = new float[pix.length], bl = new float[pix.length];
		IntStream.range(0, pix.length).parallel().forEach(i -> {
			red[i] = (pix[i] >> 16) & 0xff;
			gr[i] = (pix[i] >> 8) & 0xff;
			bl[i] = pix[i] & 0xff;
		});
		applyFilterLogic(red, w, h, t, r, r2, nV, g, em);
		applyFilterLogic(gr, w, h, t, r, r2, nV, g, em);
		applyFilterLogic(bl, w, h, t, r, r2, nV, g, em);
		if (autoThreshold && (t.equals("Sobel") || t.equals("Variance") || t.equals("DoG (Diff. of Gaussians)"))) {
			applyOtsu(red, w, h);
			applyOtsu(gr, w, h);
			applyOtsu(bl, w, h);
		}
		IntStream.range(0, pix.length).parallel().forEach(i -> pix[i] = (0xff << 24) | (((int) clamp(red[i])) << 16)
				| (((int) clamp(gr[i])) << 8) | ((int) clamp(bl[i])));
	}

	private void runRankQuick(float[] d, int w, int h, String m, int r, String em) {
		float[] out = new float[d.length];
		int sz = (2 * r + 1) * (2 * r + 1);
		IntStream.range(0, h).parallel().forEach(y -> {
			float[] win = new float[sz];
			for (int x = 0; x < w; x++) {
				int c = 0;
				for (int ky = -r; ky <= r; ky++) {
					int py = getIdx(y + ky, h, em) * w;
					for (int kx = -r; kx <= r; kx++)
						win[c++] = d[getIdx(x + kx, w, em) + py];
				}
				if (m.equals("Median"))
					out[x + y * w] = quickSelect(win, 0, sz - 1, sz / 2);
				else if (m.equals("Min")) {
					float mn = win[0];
					for (float v : win)
						if (v < mn)
							mn = v;
					out[x + y * w] = mn;
				} else {
					float mx = win[0];
					for (float v : win)
						if (v > mx)
							mx = v;
					out[x + y * w] = mx;
				}
			}
		});
		System.arraycopy(out, 0, d, 0, d.length);
	}

	private float quickSelect(float[] a, int l, int r, int k) {
		while (l < r) {
			int i = l, j = r;
			float p = a[(l + r) / 2];
			while (i <= j) {
				while (a[i] < p)
					i++;
				while (a[j] > p)
					j--;
				if (i <= j) {
					float t = a[i];
					a[i] = a[j];
					a[j] = t;
					i++;
					j--;
				}
			}
			if (k <= j)
				r = j;
			else if (k >= i)
				l = i;
			else
				break;
		}
		return a[k];
	}

	/**
	 * Van Herk/Gil-Werman Algorithmus für Min/Max in O(1) Zeit pro Pixel.
	 */
	private void runMinMax(float[] d, int w, int h, int r, boolean isMin) {
		int size = 2 * r + 1;
		// Horizontaler Durchlauf
		IntStream.range(0, h).parallel().forEach(y -> {
			float[] row = new float[w];
			System.arraycopy(d, y * w, row, 0, w);
			float[] res = computeVHGW1D(row, w, r, isMin);
			System.arraycopy(res, 0, d, y * w, w);
		});
		// Vertikaler Durchlauf
		float[] col = new float[h];
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++)
				col[y] = d[x + y * w];
			float[] res = computeVHGW1D(col, h, r, isMin);
			for (int y = 0; y < h; y++)
				d[x + y * w] = res[y];
		}
	}

	private float[] computeVHGW1D(float[] a, int n, int r, boolean isMin) {
		int k = 2 * r + 1;
		float[] g = new float[n];
		float[] h = new float[n];
		for (int i = 0; i < n; i++) {
			if (i % k == 0)
				g[i] = a[i];
			else
				g[i] = isMin ? Math.min(g[i - 1], a[i]) : Math.max(g[i - 1], a[i]);
			int j = n - 1 - i;
			if (j == n - 1 || (j + 1) % k == 0)
				h[j] = a[j];
			else
				h[j] = isMin ? Math.min(h[j + 1], a[j]) : Math.max(h[j + 1], a[j]);
		}
		float[] out = new float[n];
		for (int i = 0; i < n; i++) {
			int left = Math.max(0, i - r);
			int right = Math.min(n - 1, i + r);
			out[i] = isMin ? Math.min(h[left], g[right]) : Math.max(h[left], g[right]);
		}
		return out;
	}

	private void runExactMedian2D(float[] d, int w, int h, int r, String em) {
		float[] out = new float[d.length];
		int sz = (2 * r + 1) * (2 * r + 1);
		int medianIdx = sz / 2;

		IntStream.range(0, h).parallel().forEach(y -> {
			// Wir nutzen eine einfache sortierte Liste für das Fenster pro Zeile
			// Für extrem große Radien wäre ein T-Tree oder Treap noch schneller,
			// aber eine optimierte Float-Liste ist für r < 50 meist schneller wegen
			// Cache-Locality.
			FloatList window = new FloatList(sz);

			for (int x = 0; x < w; x++) {
				if (x == 0) {
					// Initiales Fenster für den Zeilenanfang
					window.clear();
					for (int ky = -r; ky <= r; ky++) {
						for (int kx = -r; kx <= r; kx++) {
							window.addSorted(v(d, x + kx, y + ky, w, h, em));
						}
					}
				} else {
					// Sliding Window: Links raus, rechts rein
					for (int ky = -r; ky <= r; ky++) {
						window.removeSingle(v(d, x - r - 1, y + ky, w, h, em));
						window.addSorted(v(d, x + r, y + ky, w, h, em));
					}
				}
				out[x + y * w] = window.get(medianIdx);
			}
		});
		System.arraycopy(out, 0, d, 0, d.length);
	}

	/**
	 * Hilfsklasse für eine sortierte Float-Liste mit O(N) Einfügen/Löschen, aber
	 * sehr schnellem Zugriff. Für Mediane bis r=20 oft schneller als Bäume.
	 */
	class FloatList {
		private float[] data;
		private int size = 0;

		public FloatList(int capacity) {
			data = new float[capacity];
		}

		public void clear() {
			size = 0;
		}

		public void addSorted(float val) {
			int i = size - 1;
			while (i >= 0 && data[i] > val) {
				data[i + 1] = data[i];
				i--;
			}
			data[i + 1] = val;
			size++;
		}

		public void removeSingle(float val) {
			// Binäre Suche nach dem Wert zum Löschen
			int low = 0, high = size - 1;
			while (low <= high) {
				int mid = (low + high) >>> 1;
				if (data[mid] == val) {
					System.arraycopy(data, mid + 1, data, mid, size - mid - 1);
					size--;
					return;
				} else if (data[mid] < val)
					low = mid + 1;
				else
					high = mid - 1;
			}
		}

		public float get(int index) {
			return data[index];
		}
	}

	/**
	 * Separabler Median-Filter (Approximation). Massiv schneller als
	 * 2D-QuickSelect.
	 */
	private void runSeparableMedian(float[] d, int w, int h, int r, String em) {
		float[] temp = new float[d.length];
		int sz = 2 * r + 1;
		// Horizontal
		IntStream.range(0, h).parallel().forEach(y -> {
			float[] win = new float[sz];
			for (int x = 0; x < w; x++) {
				for (int kx = -r; kx <= r; kx++)
					win[kx + r] = v(d, x + kx, y, w, h, em);
				temp[x + y * w] = quickSelect(win, 0, sz - 1, sz / 2);
			}
		});
		// Vertikal
		IntStream.range(0, w).parallel().forEach(x -> {
			float[] win = new float[sz];
			for (int y = 0; y < h; y++) {
				for (int ky = -r; ky <= r; ky++)
					win[ky + r] = v(temp, x, y + ky, w, h, em);
				d[x + y * w] = quickSelect(win, 0, sz - 1, sz / 2);
			}
		});
	}

	private void runTopHat(float[] d, int w, int h, String t, int r, String em) {
		float[] o = d.clone();
		runRankQuick(d, w, h, t.contains("White") ? "Min" : "Max", r, em);
		runRankQuick(d, w, h, t.contains("White") ? "Max" : "Min", r, em);
		for (int i = 0; i < d.length; i++)
			d[i] = t.contains("White") ? o[i] - d[i] : d[i] - o[i];
	}

	private void blur(float[] d, int w, int h, float[] k, boolean hz, int r, String em) {
		float[] out = new float[d.length];
		IntStream.range(0, h).parallel().forEach(y -> {
			for (int x = 0; x < w; x++) {
				float s = 0;
				for (int i = -r; i <= r; i++)
					s += d[(hz ? getIdx(x + i, w, em) : x) + (hz ? y : getIdx(y + i, h, em)) * w] * k[i + r];
				out[x + y * w] = s;
			}
		});
		System.arraycopy(out, 0, d, 0, d.length);
	}

	private void runUnsharpMask(float[] d, int w, int h, int r, float g, String em) {
		float[] b = d.clone();
		float[] k = createK(r);
		blur(b, w, h, k, true, r, em);
		blur(b, w, h, k, false, r, em);
		for (int i = 0; i < d.length; i++)
			d[i] += g * (d[i] - b[i]);
	}

	private void runWiener(float[] d, int w, int h, int r, String em, float nV) {
		float[] out = new float[d.length];
		int sz = (2 * r + 1) * (2 * r + 1);
		IntStream.range(0, h).parallel().forEach(y -> {
			for (int x = 0; x < w; x++) {
				float s = 0, sq = 0;
				for (int ky = -r; ky <= r; ky++) {
					int py = getIdx(y + ky, h, em) * w;
					for (int kx = -r; kx <= r; kx++) {
						float v = d[getIdx(x + kx, w, em) + py];
						s += v;
						sq += v * v;
					}
				}
				float m = s / sz, v = (sq / sz) - (m * m), f = (v <= nV) ? 0 : (v - nV) / (v == 0 ? 1e-6f : v);
				out[x + y * w] = m + f * (d[x + y * w] - m);
			}
		});
		System.arraycopy(out, 0, d, 0, d.length);
	}

	private void runLaplacian(float[] d, int w, int h, String em, float lG) {
		float[] out = new float[d.length];
		float[] k = { 0, -1, 0, -1, 4, -1, 0, -1, 0 };
		IntStream.range(0, h).parallel().forEach(y -> {
			for (int x = 0; x < w; x++) {
				float s = 0;
				for (int i = 0; i < 9; i++)
					s += d[getIdx(x + (i % 3) - 1, w, em) + getIdx(y + (i / 3) - 1, h, em) * w] * k[i];
				out[x + y * w] = d[x + y * w] + (s * lG);
			}
		});
		System.arraycopy(out, 0, d, 0, d.length);
	}

	private float v(float[] d, int x, int y, int w, int h, String em) {
		return d[getIdx(x, w, em) + getIdx(y, h, em) * w];
	}

	private int getIdx(int i, int max, String em) {
		if (i >= 0 && i < max)
			return i;
		if (em.equals("Mirror"))
			return (i < 0) ? -i : 2 * max - i - 2;
		if (em.equals("Wrap"))
			return (i % max + max) % max;
		return (i < 0) ? 0 : max - 1;
	}

	private float[] createK(int r) {
		float[] k = new float[2 * r + 1];
		float s = r / 2.5f, sum = 0;
		for (int i = -r; i <= r; i++) {
			k[i + r] = (float) Math.exp(-(i * i) / (2 * s * s));
			sum += k[i + r];
		}
		for (int i = 0; i < k.length; i++)
			k[i] /= sum;
		return k;
	}

	private void fillProcessor(ImageProcessor ip, float[] pix, int w, int h) {
		if (ip instanceof FloatProcessor)
			ip.setPixels(pix);
		else
			ip.insert(new FloatProcessor(w, h, pix).convertToByteProcessor(false), 0, 0);
	}

	private float clamp(float v) {
		return Math.min(255, Math.max(0, v));
	}
}
