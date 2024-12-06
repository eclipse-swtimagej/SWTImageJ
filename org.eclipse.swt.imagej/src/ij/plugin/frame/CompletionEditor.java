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
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextEvent;
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
import ij.IJ;
import ij.util.Tools;

public class CompletionEditor {

	private SourceViewer textViewer;
	private WordTracker wordTracker;
	public ContentAssistant assistant;
	private String[] commands;
	private static final int MAX_QUEUE_SIZE = 5000;

	public CompletionEditor(SourceViewer textViewer, Editor editor) {
		this.textViewer = textViewer;
		wordTracker = new WordTracker(MAX_QUEUE_SIZE);
		buildControls(textViewer);
	}

	private void buildControls(SourceViewer textViewer) {
		
		assistant = new ContentAssistant();
		assistant.setContentAssistProcessor(new ImageJMacroWordContentAssistProcessor(wordTracker), IDocument.DEFAULT_CONTENT_TYPE);
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
				c++;
			}
		}
		if(c == 0) {
			IJ.error("ImageJ/macros/functions.html is corrupted");
			return;
		}
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
				proposals[index] = new CompletionProposal(currSuggestion, offset, replacedWord.length(), currSuggestion.length());
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
}