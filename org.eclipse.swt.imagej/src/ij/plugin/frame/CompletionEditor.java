package ij.plugin.frame;
/*
 * Source based on:
 * http://www.java2s.com/Code/Java/SWT-JFace-Eclipse/SWTCompletionEditor.htm
 */

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ContextInformationValidator;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.templates.GlobalTemplateVariables;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateCompletionProcessor;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Drawable;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;

import ij.IJ;
import ij.util.Tools;

public class CompletionEditor {

	private SourceViewer textViewer;
	private WordTracker wordTracker;
	public ContentAssistant assistant;
	private String[] commands;
	private Map<String, String> descriptions = new HashMap<String, String>();
	private static final int MAX_QUEUE_SIZE = 5000;
	/*
	 * Safety cap: how many lines after a <b>...</b> heading we'll scan for its
	 * description text, in case a malformed entry never hits a stop marker.
	 */
	private static final int MAX_DESCRIPTION_LINES = 15;

	public CompletionEditor(SourceViewer textViewer, Editor editor) {

		this.textViewer = textViewer;
		wordTracker = new WordTracker(MAX_QUEUE_SIZE);
		buildControls(textViewer);
		installFontScaling(textViewer);
	}

	/**
	 * Picks the completion behaviour to match the file being edited: ImageJ
	 * macro function names (parsed from functions.html) only make sense for
	 * actual macro files, and are otherwise just noise; Java files get basic
	 * keyword/statement snippets (if, for, while, ...) instead, since that's a
	 * different language with different completions; anything else (.js,
	 * .bsh, .py, the interactive interpreter, ...) gets no completion at all
	 * rather than incorrectly offering macro functions.
	 */
	public void configureForFile(String name) {

		IContentAssistProcessor processor;
		if(name.endsWith(".ijm") || name.endsWith(".txt")) {
			processor = new ImageJMacroWordContentAssistProcessor(wordTracker);
		} else if(name.endsWith(".java")) {
			processor = new JavaTemplateContentAssistProcessor();
		} else {
			processor = new NoCompletionContentAssistProcessor();
		}
		assistant.setContentAssistProcessor(processor, IDocument.DEFAULT_CONTENT_TYPE);
	}

	/**
	 * ContentAssistant hardcodes its proposal list's font to
	 * JFaceResources.getDefaultFont() (see CompletionProposalPopup) and exposes
	 * no API to override it, so the popup never followed Editor's Ctrl/Cmd +/-
	 * zooming. Since we can't be told about it, we watch for it: every content
	 * assist popup (the proposal list, and our own additional-info popup) is an
	 * SWT.ON_TOP Shell parented directly to this editor's shell, so a display-
	 * wide SWT.Show filter lets us catch each one as it appears and re-apply
	 * the editor's current font (whatever Editor.setFont() last set on ta) to
	 * it and its children, without touching any other window in the process.
	 */
	private void installFontScaling(SourceViewer textViewer) {

		StyledText ta = textViewer.getTextWidget();
		Display display = ta.getDisplay();
		Listener showListener = new Listener() {

			public void handleEvent(Event event) {

				if(!(event.widget instanceof Shell) || ta.isDisposed()) {
					return;
				}
				Shell shell = (Shell)event.widget;
				Shell editorShell = ta.getShell();
				if(shell.getParent() != editorShell || (shell.getStyle() & SWT.ON_TOP) == 0) {
					return;
				}
				if(!containsTableOrStyledText(shell)) {
					return;
				}
				applyFontRecursively(shell, ta.getFont());
				shell.layout(true, true);
			}
		};
		display.addFilter(SWT.Show, showListener);
		ta.getShell().addDisposeListener(_ -> display.removeFilter(SWT.Show, showListener));
	}

	private static boolean containsTableOrStyledText(Control control) {

		if(control instanceof Table || control instanceof StyledText) {
			return true;
		}
		if(control instanceof Composite) {
			for(Control child : ((Composite)control).getChildren()) {
				if(containsTableOrStyledText(child)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void applyFontRecursively(Control control, Font font) {

		control.setFont(font);
		if(control instanceof Composite) {
			for(Control child : ((Composite)control).getChildren()) {
				applyFontRecursively(child, font);
			}
		}
	}

	private void buildControls(SourceViewer textViewer) {

		assistant = new ContentAssistant();
		/*
		 * Which processor is active depends on the file being edited, and that
		 * isn't known yet at construction time (create(name, text) is called
		 * separately, afterwards) - start with completion disabled and let
		 * configureForFile(...) pick the right one once the name is known.
		 */
		assistant.setContentAssistProcessor(new NoCompletionContentAssistProcessor(), IDocument.DEFAULT_CONTENT_TYPE);
		/*
		 * ContentAssistant only shows a proposal's additional info (our function
		 * descriptions) if it has somewhere to put it: internally it only creates
		 * its AdditionalInfoController "if (fInformationControlCreator != null)".
		 * Without this, every CompletionProposal can carry a description and
		 * nothing will ever display it - no error, it's just silently never
		 * shown. DefaultInformationControl is a plain, text-only popup, so this
		 * doesn't depend on SWT's native Browser widget being available.
		 */
		assistant.setInformationControlCreator(new IInformationControlCreator() {

			public IInformationControl createInformationControl(Shell parent) {

				/*
				 * The no-presenter constructor shows the description exactly as given,
				 * with no width-aware line breaking at all - hence everything on one
				 * long line. WrappingInformationPresenter inserts real line breaks
				 * sized to the popup's actual width instead.
				 */
				return new DefaultInformationControl(parent, new WrappingInformationPresenter(textViewer.getTextWidget()));
			}
		});
		assistant.install(textViewer);
		String f = Tools.openFromIJJarAsString("/functions.html");
		if(f == null) {
			IJ.error("\"functions.html\" not found in ij.jar");
			return;
		}
		f = f.replaceAll("&quot;", "\"");
		String[] l = f.split("\n");
		commands = new String[l.length];
		int c = 0;
		for(int i = 0; i < l.length; i++) {
			String line = l[i];
			if(line.startsWith("<b>")) {
				commands[c] = line.substring(line.indexOf("<b>") + 3, line.indexOf("</b>"));
				wordTracker.add(commands[c]);
				String description = extractDescription(l, i);
				if(!description.isEmpty()) {
					descriptions.put(commands[c], description);
				}
				c++;
			}
		}
		if(c == 0) {
			IJ.error("ImageJ/macros/functions.html is corrupted");
			return;
		}
	}

	/**
	 * functions.html lists each function as "<b>signature</b>" immediately
	 * followed by its description, running over one or more lines up to the
	 * next "<a name=...>" anchor (or "<b>" heading). This collects that
	 * description text and strips it down to plain text, since the completion
	 * popup's additional-info panel renders plain text, not HTML.
	 */
	private String extractDescription(String[] lines, int startIndex) {

		StringBuilder text = new StringBuilder();
		String firstLine = lines[startIndex];
		int afterClosingTag = firstLine.indexOf("</b>") + 4;
		if(afterClosingTag < firstLine.length()) {
			text.append(firstLine.substring(afterClosingTag));
		}
		int limit = Math.min(lines.length, startIndex + 1 + MAX_DESCRIPTION_LINES);
		for(int i = startIndex + 1; i < limit; i++) {
			String next = lines[i].trim();
			if(next.startsWith("<a name=") || next.startsWith("<b>")) {
				break;
			}
			if(text.length() > 0) {
				text.append(' ');
			}
			text.append(next);
			if(next.equals("<p>") || next.contains("<br>")) {
				break;
			}
		}
		String result = text.toString();
		result = result.replaceAll("<[^>]+>", "");
		result = result.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
		result = result.replaceAll("\\s+", " ").trim();
		if(result.startsWith("- ")) {
			result = result.substring(2).trim();
		}
		return result;
	}

	public void keyPressed(KeyEvent e) {

		if(e.keyCode == SWT.SPACE && (e.stateMask & SWT.CTRL) != 0) {
			assistant.showPossibleCompletions();
		}
		// ignore everything else
	}

	public void textChanged(TextEvent e) {

		if(isWhitespaceString(e.getText())) {
			wordTracker.add(findMostRecentWord(e.getOffset() - 1));
		}
	}

	protected String findMostRecentWord(int startSearchOffset) {

		int currOffset = startSearchOffset;
		char currChar;
		String word = "";
		try {
			while(currOffset > 0 && !Character.isWhitespace(currChar = textViewer.getDocument().getChar(currOffset))) {
				word = currChar + word;
				currOffset--;
			}
			return word;
		} catch(BadLocationException e) {
			e.printStackTrace();
			return null;
		}
	}

	protected boolean isWhitespaceString(String string) {

		StringTokenizer tokenizer = new StringTokenizer(string);
		// if there is at least 1 token, this string is not whitespace
		return !tokenizer.hasMoreTokens();
	}

	class WordTracker {

		private int maxQueueSize;
		private List<String> wordBuffer;
		private Map<String, String> knownWords = new HashMap<String, String>();

		public WordTracker(int queueSize) {

			maxQueueSize = queueSize;
			wordBuffer = new LinkedList<String>();
		}

		public int getWordCount() {

			return wordBuffer.size();
		}

		public void add(String word) {

			if(wordIsNotKnown(word)) {
				flushOldestWord();
				insertNewWord(word);
			}
		}

		private void insertNewWord(String word) {

			wordBuffer.add(0, word);
			knownWords.put(word, word);
		}

		private void flushOldestWord() {

			if(wordBuffer.size() == maxQueueSize) {
				String removedWord = (String)wordBuffer.remove(maxQueueSize - 1);
				knownWords.remove(removedWord);
			}
		}

		private boolean wordIsNotKnown(String word) {

			return knownWords.get(word) == null;
		}

		public List<String> suggest(String word) {

			List<String> suggestions = new LinkedList<String>();
			for(Iterator<String> i = wordBuffer.iterator(); i.hasNext();) {
				String currWord = (String)i.next();
				if(currWord.startsWith(word)) {
					suggestions.add(currWord);
				}
			}
			return suggestions;
		}
	}

	/** No proposals, ever - used for file types that get neither macro nor Java completion. */
	private static final class NoCompletionContentAssistProcessor implements IContentAssistProcessor {

		public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {

			return new ICompletionProposal[0];
		}

		public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {

			return null;
		}

		public char[] getCompletionProposalAutoActivationCharacters() {

			return null;
		}

		public char[] getContextInformationAutoActivationCharacters() {

			return null;
		}

		public String getErrorMessage() {

			return null;
		}

		public IContextInformationValidator getContextInformationValidator() {

			return null;
		}
	}

	/**
	 * Standard Java control-structure completions (if/for/while/...) for
	 * .java files - a completely different language from ImageJ macros, so
	 * the macro function list (see ImageJMacroWordContentAssistProcessor)
	 * would just be wrong here.
	 * <p>
	 * Built on JFace's real template machinery (the same one Eclipse's own
	 * Java editor uses for its "for", "if", "sysout", ... templates) instead
	 * of plain text substitution, so each "${name}" placeholder becomes a
	 * genuinely editable, Tab-key-navigable position after insertion - and a
	 * placeholder used more than once (e.g. the loop variable "index" in the
	 * "for" template below) stays in sync everywhere it appears as you edit
	 * any one occurrence. "${cursor}" is the one reserved name marking where
	 * the caret finally lands once every other placeholder has been visited.
	 */
	private static final class JavaTemplateContentAssistProcessor extends TemplateCompletionProcessor {

		private static final String CONTEXT_TYPE_ID = "java";
		private static final TemplateContextType CONTEXT_TYPE = createContextType();
		private static final Template[] TEMPLATES = createTemplates();

		private static TemplateContextType createContextType() {

			TemplateContextType type = new TemplateContextType(CONTEXT_TYPE_ID, "Java");
			type.addResolver(new GlobalTemplateVariables.Cursor());
			return type;
		}

		private static Template[] createTemplates() {

			return new Template[]{template("if", "If statement", "if (${condition}) {\n\t${cursor}\n}"), template("ifelse", "If / else statement", "if (${condition}) {\n\t${cursor}\n} else {\n\t\n}"), template("else", "Else block", "else {\n\t${cursor}\n}"), template("for", "For loop over an array", "for (int ${index} = 0; ${index} < ${array}.length; ${index}++) {\n\t${cursor}\n}"), template("foreach", "For-each loop", "for (${type} ${element} : ${collection}) {\n\t${cursor}\n}"), template("while", "While loop", "while (${condition}) {\n\t${cursor}\n}"), template("dowhile", "Do / while loop", "do {\n\t${cursor}\n} while (${condition});"), template("switch", "Switch statement", "switch (${expression}) {\n\tcase ${value} :\n\t\t${cursor}\n\t\tbreak;\n\tdefault :\n\t\tbreak;\n}"), template("trycatch", "Try / catch block", "try {\n\t${cursor}\n} catch (${exception} e) {\n\t\n}"), template("class", "Class declaration", "class ${name} {\n\t${cursor}\n}"), template("main", "Main method", "public static void main(String[] args) {\n\t${cursor}\n}"), template("sysout", "Print to standard out", "System.out.println(${cursor});"), template("return", "Return statement", "return ${cursor};"),};
		}

		private static Template template(String name, String description, String pattern) {

			return new Template(name, description, CONTEXT_TYPE_ID, pattern, false);
		}

		protected TemplateContextType getContextType(ITextViewer viewer, IRegion region) {

			return CONTEXT_TYPE;
		}

		protected Image getImage(Template template) {

			return null;
		}

		protected Template[] getTemplates(String contextTypeId) {

			return TEMPLATES;
		}
	}

	class ImageJMacroWordContentAssistProcessor implements IContentAssistProcessor {

		private String lastError = null;
		private IContextInformationValidator contextInfoValidator;
		private WordTracker wordTracker;

		public ImageJMacroWordContentAssistProcessor(WordTracker tracker) {

			super();
			contextInfoValidator = new ContextInformationValidator(this);
			wordTracker = tracker;
		}

		public ICompletionProposal[] computeCompletionProposals(ITextViewer textViewer, int documentOffset) {

			IDocument document = textViewer.getDocument();
			int currOffset = documentOffset - 1;
			try {
				String currWord = "";
				char currChar;
				while(currOffset >= 0 && !Character.isWhitespace(currChar = document.getChar(currOffset))) {
					currWord = currChar + currWord;
					currOffset--;
				}
				List<?> suggestions = wordTracker.suggest(currWord);
				ICompletionProposal[] proposals = null;
				if(suggestions.size() > 0) {
					proposals = buildProposals(suggestions, currWord, documentOffset - currWord.length());
					lastError = null;
				}
				return proposals;
			} catch(BadLocationException e) {
				e.printStackTrace();
				lastError = e.getMessage();
				return null;
			}
		}

		private ICompletionProposal[] buildProposals(List<?> suggestions, String replacedWord, int offset) {

			ICompletionProposal[] proposals = new ICompletionProposal[suggestions.size()];
			int index = 0;
			for(Iterator<?> i = suggestions.iterator(); i.hasNext();) {
				String currSuggestion = (String)i.next();
				/*
				 * Non-null additional info makes ContentAssistant automatically show
				 * a description panel next to the proposal list - see functions.html,
				 * parsed in buildControls()/extractDescription().
				 */
				String info = descriptions.get(currSuggestion);
				proposals[index] = new CompletionProposal(currSuggestion, offset, replacedWord.length(), currSuggestion.length(), null, currSuggestion, null, info);
				index++;
			}
			return proposals;
		}

		public IContextInformation[] computeContextInformation(ITextViewer textViewer, int documentOffset) {

			lastError = "No Context Information available";
			return null;
		}

		public char[] getCompletionProposalAutoActivationCharacters() {

			// we always wait for the user to explicitly trigger completion
			return null;
		}

		public char[] getContextInformationAutoActivationCharacters() {

			// we have no context information
			return null;
		}

		public String getErrorMessage() {

			return lastError;
		}

		public IContextInformationValidator getContextInformationValidator() {

			return contextInfoValidator;
		}
	}

	/**
	 * Word-wraps a proposal's description to fit the additional-info popup's
	 * actual width. DefaultInformationControl's no-presenter constructor shows
	 * text completely unprocessed - no wrapping at all, hence a description
	 * rendered as one very long line. This measures words with a GC (the same
	 * technique JFace's own HTMLTextPresenter uses) and inserts real line
	 * breaks, so the underlying (non-wrapping) StyledText just renders them as
	 * ordinary multiple lines.
	 */
	private static final class WrappingInformationPresenter implements DefaultInformationControl.IInformationPresenter, DefaultInformationControl.IInformationPresenterExtension {

		private final StyledText editorTextWidget;

		WrappingInformationPresenter(StyledText editorTextWidget) {

			this.editorTextWidget = editorTextWidget;
		}

		public String updatePresentation(Display display, String hoverInfo, TextPresentation presentation, int maxWidth, int maxHeight) {

			return updatePresentation((Drawable)display, hoverInfo, presentation, maxWidth, maxHeight);
		}

		public String updatePresentation(Drawable drawable, String hoverInfo, TextPresentation presentation, int maxWidth, int maxHeight) {

			if(hoverInfo == null) {
				return null;
			}
			Display display = Display.getCurrent();
			if(display == null || maxWidth <= 0) {
				return hoverInfo;
			}
			/*
			 * AbstractInformationControlManager caches and reuses the same
			 * DefaultInformationControl/StyledText across many proposal sessions
			 * ("if (extension.canReuse(fInformationControl)) return fInformationControl;"),
			 * so the font we set on it when it was first created (in
			 * createInformationControl(...)) never gets touched again by JFace -
			 * it's stuck at whatever the editor's font was the very first time a
			 * description was ever shown. updatePresentation(...) is the one hook
			 * that reliably runs on every single display, reused control or not,
			 * so re-apply the editor's *current* font here instead, and measure
			 * word-wrapping with that same font (otherwise wrapping is computed
			 * against the old, usually-smaller font's character widths while the
			 * text actually renders in the new, wider one, so it would overflow
			 * again regardless of the font fix).
			 */
			Font font = null;
			if(drawable instanceof Control && !((Control)drawable).isDisposed() && editorTextWidget != null && !editorTextWidget.isDisposed()) {
				font = editorTextWidget.getFont();
				((Control)drawable).setFont(font);
			}
			GC gc = new GC(display);
			try {
				if(font != null) {
					gc.setFont(font);
				}
				StringBuilder wrapped = new StringBuilder();
				for(String paragraph : hoverInfo.split("\n", -1)) {
					wrapParagraph(gc, paragraph, maxWidth, wrapped);
				}
				return wrapped.toString();
			} finally {
				gc.dispose();
			}
		}

		private void wrapParagraph(GC gc, String paragraph, int maxWidth, StringBuilder out) {

			StringBuilder line = new StringBuilder();
			for(String word : paragraph.split(" ")) {
				String candidate = line.length() == 0 ? word : line + " " + word;
				if(line.length() > 0 && gc.textExtent(candidate).x > maxWidth) {
					appendLine(out, line.toString());
					line = new StringBuilder(word);
				} else {
					line = new StringBuilder(candidate);
				}
			}
			appendLine(out, line.toString());
		}

		private void appendLine(StringBuilder out, String line) {

			if(out.length() > 0) {
				out.append('\n');
			}
			out.append(line);
		}
	}
}