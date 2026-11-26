package ij.plugin.frame;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

/**
 * A replacement for org.eclipse.swt.widgets.List that can hold hundreds of
 * thousands of rows on Windows (used by the RoiManager).
 *
 * Why this exists: SWT's List is a native LISTBOX. Its scroll position arrives
 * in the high word of WM_VSCROLL's wParam, which is 16 bits, so it wraps at
 * 65536 and the widget starts repainting from row 0. A Table created with
 * SWT.VIRTUAL is a SysListView32 in LVS_OWNERDATA mode, which is built for
 * large item counts and scrolls correctly.
 *
 * The strings live in this object, not in the widget. Row text is handed to the
 * Table only when Windows asks to paint that row (SWT.SetData), so populating
 * 100k rows costs almost nothing.
 *
 * Threads: the content (add, remove, setItem, getItem, getItemCount, ...) may be
 * used from any thread, e.g., the particle analysis adds its ROIs in its own
 * thread without a syncExec for each row. The widget is updated in the user
 * interface thread (asyncExec, many changes coalesced into one update). The
 * selection, the scrolling and the widget itself (getControl()) belong to the
 * user interface thread.
 *
 * The public methods mirror the subset of List's API that RoiManager uses. Where
 * a Control is required (layout, focus, listeners, dispose) use getControl().
 */
public class BigList {

	private final Table table;
	private final Display display;
	/* The texts (guarded by itself) */
	private final ArrayList<String> model = new ArrayList<String>();
	private final TableColumn column;

	/** Coalesces many add()/remove() calls into one native item-count update. */
	private final AtomicBoolean syncPending = new AtomicBoolean(false);
	private volatile int batchDepth;

	/** Creates the list (in the user interface thread). */
	public BigList(Composite parent, boolean multi) {
		int style = SWT.VIRTUAL | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER
				| SWT.FULL_SELECTION | (multi ? SWT.MULTI : SWT.SINGLE);
		table = new Table(parent, style);
		display = table.getDisplay();
		table.setHeaderVisible(false);
		table.setLinesVisible(false);

		column = new TableColumn(table, SWT.LEFT);
		column.setWidth(200);

		table.addListener(SWT.SetData, new Listener() {
			public void handleEvent(Event event) {
				TableItem item = (TableItem) event.item;
				int index = event.index;
				String text = getItem(index);
				if (text != null)
					item.setText(text);
			}
		});

		// Keep the single column as wide as the client area.
		table.addListener(SWT.Resize, new Listener() {
			public void handleEvent(Event event) {
				int w = table.getClientArea().width;
				if (w > 0)
					column.setWidth(w);
			}
		});
	}

	// ---------------------------------------------------------------- content

	public void add(String s) {
		synchronized (model) {
			model.add(s);
		}
		requestSync();
	}

	public void add(String s, int index) {
		synchronized (model) {
			if (index < 0 || index > model.size())
				index = model.size();
			model.add(index, s);
		}
		requestSync();
	}

	public void addAll(Collection<String> items) {
		synchronized (model) {
			model.addAll(items);
		}
		requestSync();
	}

	public void setItem(int index, String s) {
		synchronized (model) {
			if (index < 0 || index >= model.size())
				return;
			model.set(index, s);
		}
		if (isUiThread()) {
			if (!table.isDisposed() && index < table.getItemCount())
				table.clear(index); // force a re-request of this row's text
		} else
			requestSync();
	}

	public void remove(int index) {
		synchronized (model) {
			if (index < 0 || index >= model.size())
				return;
			model.remove(index);
		}
		requestSync();
	}

	/** Indices need not be sorted; duplicates are tolerated. */
	public void remove(int[] indices) {
		if (indices == null || indices.length == 0)
			return;
		int[] sorted = indices.clone();
		java.util.Arrays.sort(sorted);
		synchronized (model) {
			for (int i = sorted.length - 1; i >= 0; i--) {
				if (i < sorted.length - 1 && sorted[i] == sorted[i + 1])
					continue;
				if (sorted[i] >= 0 && sorted[i] < model.size())
					model.remove(sorted[i]);
			}
		}
		requestSync();
	}

	public void remove(String s) {
		synchronized (model) {
			int i = model.indexOf(s);
			if (i < 0)
				return;
			model.remove(i);
		}
		requestSync();
	}

	public void removeAll() {
		synchronized (model) {
			model.clear();
		}
		requestSync();
	}

	/** Replaces all texts (e.g., sorted). */
	public void setItems(Collection<String> items) {
		synchronized (model) {
			model.clear();
			model.addAll(items);
		}
		requestSync();
	}

	public String getItem(int index) {
		synchronized (model) {
			return (index >= 0 && index < model.size()) ? model.get(index) : null;
		}
	}

	public String[] getItems() {
		synchronized (model) {
			return model.toArray(new String[model.size()]);
		}
	}

	public int getItemCount() {
		synchronized (model) {
			return model.size();
		}
	}

	public int indexOf(String s) {
		synchronized (model) {
			return model.indexOf(s);
		}
	}

	// -------------------------------------------------------------- selection
	// (user interface thread)

	public int getSelectionIndex() {
		int[] sel = table.getSelectionIndices();
		return sel.length == 0 ? -1 : sel[0];
	}

	public int[] getSelectionIndices() {
		return table.getSelectionIndices();
	}

	public int getSelectionCount() {
		return table.getSelectionCount();
	}

	public boolean isSelected(int index) {
		return table.isSelected(index);
	}

	public String[] getSelection() {
		int[] sel = table.getSelectionIndices();
		String[] out = new String[sel.length];
		for (int i = 0; i < sel.length; i++)
			out[i] = getItem(sel[i]);
		return out;
	}

	public void setSelection(int index) {
		flush();
		table.setSelection(index);
	}

	public void setSelection(int[] indices) {
		flush();
		table.setSelection(indices);
	}

	public void select(int index) {
		flush();
		table.select(index);
	}

	public void deselect(int index) {
		table.deselect(index);
	}

	public void deselectAll() {
		table.deselectAll();
	}

	public void selectAll() {
		flush();
		table.selectAll();
	}

	// ------------------------------------------------------------- scrolling

	public void showSelection() {
		flush();
		table.showSelection();
	}

	public void setTopIndex(int index) {
		flush();
		table.setTopIndex(index);
	}

	public int getTopIndex() {
		return table.getTopIndex();
	}

	// ------------------------------------------------------------- delegation

	/** The real widget: use for setLayoutData, dispose, setFocus, listeners, etc. */
	public Table getControl() {
		return table;
	}

	public void addSelectionListener(SelectionListener l) {
		table.addSelectionListener(l);
	}

	public void removeSelectionListener(SelectionListener l) {
		table.removeSelectionListener(l);
	}

	public void addListener(int eventType, Listener l) {
		table.addListener(eventType, l);
	}

	public void setFont(Font font) {
		table.setFont(font);
	}

	public Font getFont() {
		return table.getFont();
	}

	public void setEnabled(boolean enabled) {
		table.setEnabled(enabled);
	}

	public void setMenu(Menu menu) {
		table.setMenu(menu);
	}

	public void redraw() {
		table.redraw();
	}

	/** Thread-safe. */
	public boolean isDisposed() {
		return table.isDisposed();
	}

	// ------------------------------------------------------------- batching

	/**
	 * Optional. Wrap a long add/remove loop in begin/endBatch to guarantee a
	 * single widget update. Without it, updates are coalesced automatically on
	 * the next pass of the event loop.
	 */
	public synchronized void beginBatch() {
		batchDepth++;
	}

	public synchronized void endBatch() {
		if (--batchDepth <= 0) {
			batchDepth = 0;
			flush();
		}
	}

	private boolean isUiThread() {
		return display.getThread() == Thread.currentThread();
	}

	/* One widget update for all changes until it runs (any thread) */
	private void requestSync() {
		if (batchDepth > 0 || table.isDisposed() || display.isDisposed())
			return;
		if (!syncPending.compareAndSet(false, true))
			return;
		display.asyncExec(this::update);
	}

	/**
	 * Push the model size into the widget: at once in the user interface thread,
	 * else as soon as the user interface thread is free.
	 */
	public void flush() {
		if (isUiThread())
			update();
		else {
			syncPending.set(false);
			requestSync();
		}
	}

	private void update() {
		syncPending.set(false);
		if (table.isDisposed())
			return;
		int size = getItemCount();
		if (table.getItemCount() != size)
			table.setItemCount(size);
		table.clearAll();
	}
}
