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
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Drawable;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import ij.IJ;
import ij.util.Tools;

public class CompletionEditor {

	private SourceViewer textViewer;
	private WordTracker wordTracker;
	public ContentAssistant assistant;
	private String[] commands;
	private Map<String, String> descriptions = new HashMap<String, String>();
	private static final int MAX_QUEUE_SIZE = 5000;
	/* Safety cap: how many lines after a <b>...</b> heading we'll scan for its
	 * description text, in case a malformed entry never hits a stop marker. */
	private static final int MAX_DESCRIPTION_LINES = 15;

	public CompletionEditor(SourceViewer textViewer, Editor editor) {
		this.textViewer = textViewer;
		wordTracker = new WordTracker(MAX_QUEUE_SIZE);
		buildControls(textViewer);
	}

	private void buildControls(SourceViewer textViewer) {
		
		assistant = new ContentAssistant();
		assistant.setContentAssistProcessor(new ImageJMacroWordContentAssistProcessor(wordTracker), IDocument.DEFAULT_CONTENT_TYPE);
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
				return new DefaultInformationControl(parent, new WrappingInformationPresenter());
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
	        if (isWhitespaceString(e.getText())) {
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
				/* Non-null additional info makes ContentAssistant automatically show
				 * a description panel next to the proposal list - see functions.html,
				 * parsed in buildControls()/extractDescription(). */
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
			GC gc = new GC(display);
			try {
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