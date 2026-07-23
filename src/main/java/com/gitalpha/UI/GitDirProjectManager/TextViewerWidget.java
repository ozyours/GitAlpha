package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.FileChanges;
import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a coloured unified-diff view for a single file change inside a scrollable pane.
 * Each diff line shows old/new line numbers, a +/-/space prefix, and the line content,
 * with green/red highlighting for additions and removals.
 */
public class TextViewerWidget extends BaseWidget
{
	/** Monospaced font used for all diff text */
	private static final Font MONO_FONT = Font.font("Consolas", 13);

	/** Background colour for added lines */
	private static final String ADDED_BG = "#e6ffec";
	/** Left-side bar colour for added lines */
	private static final String ADDED_BAR = "#2da44e";
	/** Background colour for removed lines */
	private static final String REMOVED_BG = "#ffebe9";
	/** Left-side bar colour for removed lines */
	private static final String REMOVED_BAR = "#cf222e";

	/** Background for changed characters within an added line (deeper green) */
	private static final String ADDED_INTRA_BG = "#abf2bc";
	/** Background for changed characters within a removed line (deeper red) */
	private static final String REMOVED_INTRA_BG = "#fbbcb6";

	/** Pattern to tokenize a line into alternating whitespace and non-whitespace runs */
	private static final Pattern TOKEN_PATTERN = Pattern.compile("\\S+|\\s+");

	/**
	 * A segment of text with a flag indicating whether it is part of a changed
	 * (added/removed) span in an intra-line diff.
	 */
	private static record StyledSegment(String text, boolean highlighted) {}

	/**
	 * The result of an intra-line diff between an old (removed) line and a new (added) line.
	 * Each side carries its own list of styled segments.
	 */
	private static record IntraLineDiff(
		List<StyledSegment> oldSegments,
		List<StyledSegment> newSegments
	) {}

	/** Wraps the content container for scrolling */
	private final ScrollPane scrollPane;
	/** Holds the diff line rows (HBoxes) */
	private final VBox contentContainer;
	/** The file changes whose diff is currently displayed; null if none */
	private FileChanges FileChangesTarget = null;

	public TextViewerWidget(GitDir _GitDirTarget, GitDirProjectManager _GitDirProjectManagerTarget)
	{
		super(_GitDirTarget, _GitDirProjectManagerTarget);

		contentContainer = new VBox();
		scrollPane = new ScrollPane(contentContainer);
		scrollPane.setFitToWidth(true);
		getChildren().add(scrollPane);
	}

	// ------------------------------------------------------------------
	//  Intra-line (word-level) diff helpers
	// ------------------------------------------------------------------

	/**
	 * Splits {@code text} into tokens, each either a run of non-whitespace
	 * characters or a run of whitespace characters.  This preserves the
	 * original character positions so that tokens can be stitched back
	 * together without loss.
	 */
	private static List<String> tokenize(String text)
	{
		List<String> tokens = new ArrayList<>();
		Matcher m = TOKEN_PATTERN.matcher(text);
		while (m.find())
			tokens.add(m.group());
		return tokens;
	}

	/**
	 * Computes a word-level (token) diff between the old and new line texts
	 * using an LCS-based approach.  Returns a pair of segment lists:
	 * <ul>
	 *   <li>{@code oldSegments} — how the old (removed) line should be drawn
	 *   <li>{@code newSegments} — how the new (added) line should be drawn
	 * </ul>
	 * Each segment carries a {@code highlighted} flag that is {@code true}
	 * when the token was <em>not</em> part of the longest common subsequence
	 * (i.e. it was actually added or removed).
	 */
	private static IntraLineDiff computeIntraLineDiff(String oldText, String newText)
	{
		// ---- fast-path: identical texts (nothing changed inside the line) ----
		if (oldText.equals(newText))
			return new IntraLineDiff(
				List.of(new StyledSegment(oldText, false)),
				List.of(new StyledSegment(newText, false))
			);

		List<String> oldTokens = tokenize(oldText);
		List<String> newTokens = tokenize(newText);

		// ---- one side empty -> everything on the other side is "changed" ----
		if (oldTokens.isEmpty() && newTokens.isEmpty())
			return new IntraLineDiff(List.of(), List.of());
		if (oldTokens.isEmpty())
			return new IntraLineDiff(
				List.of(),
				List.of(new StyledSegment(newText, true))
			);
		if (newTokens.isEmpty())
			return new IntraLineDiff(
				List.of(new StyledSegment(oldText, true)),
				List.of()
			);

		// ---- LCS DP table ----
		int m = oldTokens.size();
		int n = newTokens.size();
		int[][] dp = new int[m + 1][n + 1];

		for (int i = 1; i <= m; i++)
		{
			String ot = oldTokens.get(i - 1);
			for (int j = 1; j <= n; j++)
			{
				if (ot.equals(newTokens.get(j - 1)))
					dp[i][j] = dp[i - 1][j - 1] + 1;
				else
					dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
			}
		}

		// ---- backtrack to find which tokens belong to the LCS ----
		boolean[] oldInLCS = new boolean[m];
		boolean[] newInLCS = new boolean[n];
		{
			int i = m, j = n;
			while (i > 0 && j > 0)
			{
				if (oldTokens.get(i - 1).equals(newTokens.get(j - 1)))
				{
					oldInLCS[i - 1] = true;
					newInLCS[j - 1] = true;
					i--;
					j--;
				}
				else if (dp[i - 1][j] > dp[i][j - 1])
					i--;
				else
					j--;
			}
		}

		// ---- build segment lists from the LCS flags ----
		return new IntraLineDiff(
			buildSegments(oldTokens, oldInLCS),
			buildSegments(newTokens, newInLCS)
		);
	}

	/**
	 * Converts a token list and a parallel boolean array (token is in LCS)
	 * into a list of {@link StyledSegment}s, collapsing consecutive tokens
	 * with the same <em>highlighted</em> state into a single segment.
	 */
	private static List<StyledSegment> buildSegments(List<String> tokens, boolean[] inLCS)
	{
		List<StyledSegment> segments = new ArrayList<>();
		StringBuilder buf = new StringBuilder();
		boolean prevInLCS = true;		// start in "unchanged" state

		for (int idx = 0; idx < tokens.size(); idx++)
		{
			boolean curInLCS = inLCS[idx];
			if (curInLCS == prevInLCS)
			{
				buf.append(tokens.get(idx));
			}
			else
			{
				if (buf.length() > 0)
					segments.add(new StyledSegment(buf.toString(), !prevInLCS));
				buf = new StringBuilder(tokens.get(idx));
				prevInLCS = curInLCS;
			}
		}
		if (buf.length() > 0)
			segments.add(new StyledSegment(buf.toString(), !prevInLCS));

		return segments;
	}

	/**
	 * If a file change is currently displayed, re-loads its diff.
	 * Called after a repository refresh to pick up any modifications.
	 */
	public void RefreshCurrentFileChanges()
	{
		if (FileChangesTarget != null)
			SetFileChanges(FileChangesTarget);
	}

	/**
	 * Loads and displays the diff for the given file change.
	 * Shows a "Loading..." indicator, then replaces it with the rendered diff
	 * or an error message. Stale responses (for a superseded target) are dropped.
	 */
	public void SetFileChanges(FileChanges _FileChangesTarget)
	{
		FileChangesTarget = _FileChangesTarget;
		contentContainer.getChildren().clear();

		if (FileChangesTarget == null)
			return;

		Text loadingText = new Text("Loading...");
		loadingText.setFont(MONO_FONT);
		contentContainer.getChildren().add(loadingText);

		FileChangesTarget.getDiffLines()
			.thenAccept(diffLines ->
				Platform.runLater(() ->
				{
					if (FileChangesTarget != _FileChangesTarget)
						return;
					renderDiffLines(diffLines);
				})
			)
			.exceptionally(ex ->
			{
				Platform.runLater(() ->
				{
					if (FileChangesTarget == _FileChangesTarget)
					{
						contentContainer.getChildren().clear();
						Text errorText = new Text("Error: " + ex.getCause().getMessage());
						errorText.setFont(MONO_FONT);
						contentContainer.getChildren().add(errorText);
					}
				});
				return null;
			});
	}

	/**
	 * Builds and displays the coloured diff rows from parsed LineChange entries.
	 * Pairs consecutive {@code -} / {@code +} lines to compute intra-line word-level
	 * highlighting, showing exactly which tokens were added or removed.
	 */
	private void renderDiffLines(List<FileChanges.LineChange> diffLines)
	{
		contentContainer.getChildren().clear();

		if (diffLines.isEmpty())
			return;

		int maxNum = 0;
		for (var e : diffLines)
		{
			if (e.oldLineNumber() != null)
				maxNum = Math.max(maxNum, e.oldLineNumber());
			if (e.newLineNumber() != null)
				maxNum = Math.max(maxNum, e.newLineNumber());
		}
		int numWidth = Math.max(1, String.valueOf(maxNum).length());
		String numFormat = "%" + numWidth + "d";
		String emptyNum = " ".repeat(numWidth);

		// Pending removal lines that may pair with following additions
		ArrayDeque<FileChanges.LineChange> pendingRemovals = new ArrayDeque<>();

		for (var line : diffLines)
		{
			char prefix = line.prefix();

			// Accumulate removals; don't render yet — wait for a matching addition.
			if (prefix == '-')
			{
				pendingRemovals.addLast(line);
				continue;
			}

			if (prefix == '+')
			{
				if (!pendingRemovals.isEmpty())
				{
					// Pair the oldest pending removal with this addition
					FileChanges.LineChange removedLine = pendingRemovals.removeFirst();
					IntraLineDiff diff = computeIntraLineDiff(removedLine.text(), line.text());
					renderDiffRow(removedLine, numFormat, emptyNum, diff.oldSegments());
					renderDiffRow(line, numFormat, emptyNum, diff.newSegments());
				}
				else
				{
					// Pure addition — no matching removal
					renderDiffRow(line, numFormat, emptyNum, null);
				}
				continue;
			}

			// Context (or other non-diff) line — flush any pending removals as pure removals
			while (!pendingRemovals.isEmpty())
				renderDiffRow(pendingRemovals.removeFirst(), numFormat, emptyNum, null);
			renderDiffRow(line, numFormat, emptyNum, null);
		}

		// Flush remaining unpaired removals at end
		while (!pendingRemovals.isEmpty())
			renderDiffRow(pendingRemovals.removeFirst(), numFormat, emptyNum, null);
	}

	/**
	 * Renders a single diff row (bar + line numbers + prefix + content text).
	 *
	 * @param line           the diff line to render
	 * @param numFormat      format string for line numbers
	 * @param emptyNum       blank placeholder when a line number is absent
	 * @param intraSegments  intra-line styled segments (nullable).
	 *                       When {@code null} the entire line text is drawn as a single
	 *                       plain {@link Text}; otherwise each segment is drawn separately
	 *                       with changed portions highlighted.
	 */
	private void renderDiffRow(
		FileChanges.LineChange line,
		String numFormat,
		String emptyNum,
		List<StyledSegment> intraSegments)
	{
		HBox row = new HBox();
		row.setMaxWidth(Double.MAX_VALUE);

		boolean added = line.prefix() == '+';
		boolean removed = line.prefix() == '-';

		// Colour-coded left bar
		Pane bar = new Pane();
		bar.setMinWidth(4);
		bar.setPrefWidth(4);

		if (added)
		{
			row.setStyle("-fx-background-color: " + ADDED_BG + ";");
			bar.setStyle("-fx-background-color: " + ADDED_BAR + ";");
		}
		else if (removed)
		{
			row.setStyle("-fx-background-color: " + REMOVED_BG + ";");
			bar.setStyle("-fx-background-color: " + REMOVED_BAR + ";");
		}
		else
		{
			bar.setStyle("-fx-background-color: transparent;");
		}

		// Line numbers
		String oldStr = line.oldLineNumber() == null ? emptyNum : String.format(numFormat, line.oldLineNumber());
		String newStr = line.newLineNumber() == null ? emptyNum : String.format(numFormat, line.newLineNumber());

		Text oldNum = new Text(" " + oldStr);
		oldNum.setFont(MONO_FONT);
		oldNum.setFill(Color.GRAY);

		Text newNum = new Text(" " + newStr);
		newNum.setFont(MONO_FONT);
		newNum.setFill(Color.GRAY);

		// Prefix character
		Text prefixText = new Text(" " + line.prefix());
		prefixText.setFont(MONO_FONT);
		if (added)
			prefixText.setFill(Color.rgb(45, 164, 78));
		else if (removed)
			prefixText.setFill(Color.rgb(207, 34, 46));
		else
			prefixText.setFill(Color.GRAY);

		// Content — either intra-line segments or plain text
		HBox content = createContentNode(line, intraSegments);

		row.getChildren().addAll(bar, oldNum, newNum, prefixText, content);
		contentContainer.getChildren().add(row);
	}

	/**
	 * Creates the content node for a diff row.
	 * <p>
	 * When {@code intraSegments} is non-null the content is an {@link HBox} with
	 * individual {@link Text} / {@link StackPane} nodes per segment so that
	 * changed tokens get a background highlight.  Otherwise a plain {@link Text}
	 * node is returned.
	 */
	private HBox createContentNode(FileChanges.LineChange line, List<StyledSegment> intraSegments)
	{
		boolean added = line.prefix() == '+';
		boolean removed = line.prefix() == '-';

		// Leading visual space (separates content from the prefix character)
		Text spacer = new Text(" ");
		spacer.setFont(MONO_FONT);

		if (intraSegments == null || intraSegments.isEmpty())
		{
			// Plain text (pure addition/removal or context line)
			Text plain = new Text(line.text());
			plain.setFont(MONO_FONT);
			// Combine spacer and text into an HBox so the return type is always a container
			HBox box = new HBox(0);
			box.getChildren().addAll(spacer, plain);
			return box;
		}

		// Styled segments with possible intra-line highlighting
		String intraBg = added ? ADDED_INTRA_BG : REMOVED_INTRA_BG;
		HBox box = new HBox(0);
		box.getChildren().add(spacer);

		for (StyledSegment seg : intraSegments)
		{
			Text segText = new Text(seg.text());
			segText.setFont(MONO_FONT);

			if (seg.highlighted())
			{
				StackPane highlightPane = new StackPane(segText);
				highlightPane.setStyle("-fx-background-color: " + intraBg + ";");
				box.getChildren().add(highlightPane);
			}
			else
			{
				box.getChildren().add(segText);
			}
		}
		return box;
	}
}
