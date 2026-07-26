package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.FileChange;
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

	/**
	 * Data-only carrier for a single diff row, produced off the JavaFX thread.
	 * Contains everything {@link #renderDiffRow} needs to create the JavaFX nodes.
	 */
	private static record PreparedRow(
		char prefix,
		Integer oldLineNumber,
		Integer newLineNumber,
		String text,
		List<StyledSegment> intraSegments
	) {}

	/** Wraps the content container for scrolling */
	private final ScrollPane ScrollPane;
	/** Holds the diff line rows (HBoxes) */
	private final VBox ContentContainer;
	/** The file changes whose diff is currently displayed; null if none */
	private FileChange FileChangeTarget = null;

	public TextViewerWidget(GitDir _GitDirTarget, GitDirProjectManagerWidget _GitDirProjectManagerWidgetTarget)
	{
		super(_GitDirTarget, _GitDirProjectManagerWidgetTarget);

		ContentContainer = new VBox();
		ScrollPane = new ScrollPane(ContentContainer);
		ScrollPane.setFitToWidth(true);
		getChildren().add(ScrollPane);
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
	private static List<String> Tokenize(String text)
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
	private static IntraLineDiff ComputeIntraLineDiff(String oldText, String newText)
	{
		// ---- fast-path: identical texts (nothing changed inside the line) ----
		if (oldText.equals(newText))
			return new IntraLineDiff(
				List.of(new StyledSegment(oldText, false)),
				List.of(new StyledSegment(newText, false))
			);

		List<String> oldTokens = Tokenize(oldText);
		List<String> newTokens = Tokenize(newText);

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
			BuildSegments(oldTokens, oldInLCS),
			BuildSegments(newTokens, newInLCS)
		);
	}

	/**
	 * Converts a token list and a parallel boolean array (token is in LCS)
	 * into a list of {@link StyledSegment}s, collapsing consecutive tokens
	 * with the same <em>highlighted</em> state into a single segment.
	 */
	private static List<StyledSegment> BuildSegments(List<String> tokens, boolean[] inLCS)
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
	public void RefreshCurrentFileChange()
	{
		if (FileChangeTarget != null)
			SetFileChange(FileChangeTarget);
	}

	/**
	 * Loads and displays the diff for the given file change.
	 * Shows a "Loading..." indicator, then replaces it with the rendered diff
	 * or an error message. Stale responses (for a superseded target) are dropped.
	 * <p>
	 * Intra-line diff computation (LCS) runs <em>off</em> the JavaFX thread so the
	 * UI stays responsive even for large files.  Only the actual JavaFX node
	 * creation happens on the JavaFX thread, via a single {@link Platform#runLater}
	 * flush.
	 */
	public void SetFileChange(FileChange _FileChangeTarget)
	{
		FileChangeTarget = _FileChangeTarget;
		ContentContainer.getChildren().clear();

		if (FileChangeTarget == null)
			return;

		StackPane loadingPane = new StackPane();
		Text loadingText = new Text("Loading...");
		loadingText.setFont(MONO_FONT);
		loadingPane.getChildren().add(loadingText);
		// Fill the viewport height so the StackPane centers the text vertically
		loadingPane.prefHeightProperty().bind(
			ScrollPane.viewportBoundsProperty()
				.map(b -> (b != null) ? b.getHeight() : ScrollPane.getHeight())
		);
		ContentContainer.getChildren().add(loadingPane);

		FileChangeTarget.GetDiffLines()
			.thenAcceptAsync(diffLines ->
			{
				// (runs on ForkJoinPool or cached-executor thread)
				if (FileChangeTarget != _FileChangeTarget)
					return;							// stale — user switched to another file

				// ---- Phase 1: pair + LCS (no JavaFX, pure data) ----
				List<PreparedRow> prepared = PrepareDiffRows(diffLines);

				// ---- Phase 2: single flush to JavaFX thread for node creation ----
				Platform.runLater(() ->
				{
					if (FileChangeTarget != _FileChangeTarget)
						return;						// stale by the time we got to FX

					RenderPreparedRows(prepared);
				});
			})
			.exceptionally(ex ->
			{
				Platform.runLater(() ->
				{
					if (FileChangeTarget == _FileChangeTarget)
					{
						ContentContainer.getChildren().clear();
						Text errorText = new Text("Error: " + ex.getCause().getMessage());
						errorText.setFont(MONO_FONT);
						ContentContainer.getChildren().add(errorText);
					}
				});
				return null;
			});
	}

	/**
	 * Pairs {@code -} / {@code +} diff lines and computes intra-line (word-level)
	 * diffs for each pair.  Returns a list of plain-data {@link PreparedRow}
	 * records that can be handed to {@link #renderPreparedRows}.
	 * <p>
	 * This method performs <strong>no JavaFX operations</strong> — it is safe
	 * to call from any thread (and is intentionally invoked off the JavaFX thread
	 * via {@link java.util.concurrent.CompletableFuture#thenAcceptAsync}).
	 */
	private static List<PreparedRow> PrepareDiffRows(List<FileChange.LineChange> diffLines)
	{
		List<PreparedRow> result = new ArrayList<>();

		if (diffLines.isEmpty())
			return result;

		// Pending removal lines that may pair with following additions
		ArrayDeque<FileChange.LineChange> pendingRemovals = new ArrayDeque<>();

		for (var line : diffLines)
		{
			char prefix = line.prefix();

			// Accumulate removals; wait for a matching addition.
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
					FileChange.LineChange removedLine = pendingRemovals.removeFirst();
					IntraLineDiff diff = ComputeIntraLineDiff(removedLine.text(), line.text());
					result.add(new PreparedRow(
						removedLine.prefix(), removedLine.oldLineNumber(), removedLine.newLineNumber(),
						removedLine.text(), diff.oldSegments()));
					result.add(new PreparedRow(
						line.prefix(), line.oldLineNumber(), line.newLineNumber(),
						line.text(), diff.newSegments()));
				}
				else
				{
					// Pure addition — no matching removal
					result.add(new PreparedRow(
						line.prefix(), line.oldLineNumber(), line.newLineNumber(),
						line.text(), null));
				}
				continue;
			}

			// Context (or other non-diff) line — flush any pending removals as pure removals
			while (!pendingRemovals.isEmpty())
			{
				FileChange.LineChange rm = pendingRemovals.removeFirst();
				result.add(new PreparedRow(
					rm.prefix(), rm.oldLineNumber(), rm.newLineNumber(),
					rm.text(), null));
			}
			result.add(new PreparedRow(
				line.prefix(), line.oldLineNumber(), line.newLineNumber(),
				line.text(), null));
		}

		// Flush remaining unpaired removals at end
		while (!pendingRemovals.isEmpty())
		{
			FileChange.LineChange rm = pendingRemovals.removeFirst();
			result.add(new PreparedRow(
				rm.prefix(), rm.oldLineNumber(), rm.newLineNumber(),
				rm.text(), null));
		}

		return result;
	}

	/**
	 * Creates and attaches JavaFX nodes for all rows that were previously
	 * prepared by {@link #prepareDiffRows}.
	 * <p>
	 * <strong>Must be called on the JavaFX Application Thread.</strong>
	 */
	private void RenderPreparedRows(List<PreparedRow> prepared)
	{
		ContentContainer.getChildren().clear();

		if (prepared.isEmpty())
			return;

		// Determine the widest line number for padding alignment
		int maxNum = 0;
		for (var row : prepared)
		{
			if (row.oldLineNumber() != null)
				maxNum = Math.max(maxNum, row.oldLineNumber());
			if (row.newLineNumber() != null)
				maxNum = Math.max(maxNum, row.newLineNumber());
		}
		int numWidth = Math.max(1, String.valueOf(maxNum).length());
		String numFormat = "%" + numWidth + "d";
		String emptyNum = " ".repeat(numWidth);

		for (var row : prepared)
			RenderDiffRow(row, numFormat, emptyNum);
	}

	/**
	 * Renders a single diff row (bar + line numbers + prefix + content text)
	 * from a pre-computed {@link PreparedRow}.
	 *
	 * @param row       the pre-computed row data (produced off-thread)
	 * @param numFormat format string for line numbers
	 * @param emptyNum  blank placeholder when a line number is absent
	 */
	private void RenderDiffRow(PreparedRow row, String numFormat, String emptyNum)
	{
		HBox rowBox = new HBox();
		rowBox.setMaxWidth(Double.MAX_VALUE);

		boolean added = row.prefix() == '+';
		boolean removed = row.prefix() == '-';

		// Colour-coded left bar
		Pane bar = new Pane();
		bar.setMinWidth(4);
		bar.setPrefWidth(4);

		if (added)
		{
			rowBox.setStyle("-fx-background-color: " + ADDED_BG + ";");
			bar.setStyle("-fx-background-color: " + ADDED_BAR + ";");
		}
		else if (removed)
		{
			rowBox.setStyle("-fx-background-color: " + REMOVED_BG + ";");
			bar.setStyle("-fx-background-color: " + REMOVED_BAR + ";");
		}
		else
		{
			bar.setStyle("-fx-background-color: transparent;");
		}

		// Line numbers
		String oldStr = row.oldLineNumber() == null ? emptyNum : String.format(numFormat, row.oldLineNumber());
		String newStr = row.newLineNumber() == null ? emptyNum : String.format(numFormat, row.newLineNumber());

		Text oldNum = new Text(" " + oldStr);
		oldNum.setFont(MONO_FONT);
		oldNum.setFill(Color.GRAY);

		Text newNum = new Text(" " + newStr);
		newNum.setFont(MONO_FONT);
		newNum.setFill(Color.GRAY);

		// Prefix character
		Text prefixText = new Text(" " + row.prefix());
		prefixText.setFont(MONO_FONT);
		if (added)
			prefixText.setFill(Color.rgb(45, 164, 78));
		else if (removed)
			prefixText.setFill(Color.rgb(207, 34, 46));
		else
			prefixText.setFill(Color.GRAY);

		// Content — either intra-line segments or plain text
		HBox content = CreateContentNode(row.prefix(), row.text(), row.intraSegments());

		rowBox.getChildren().addAll(bar, oldNum, newNum, prefixText, content);
		ContentContainer.getChildren().add(rowBox);
	}

	/**
	 * Creates the content node for a diff row.
	 * <p>
	 * When {@code intraSegments} is non-null the content is an {@link HBox} with
	 * individual {@link Text} / {@link StackPane} nodes per segment so that
	 * changed tokens get a background highlight.  Otherwise a plain {@link Text}
	 * node is returned.
	 *
	 * @param prefix        the diff prefix character ({@code '+'}, {@code '-'}, or {@code ' '})
	 * @param text          the raw line text
	 * @param intraSegments intra-line styled segments (nullable)
	 */
	private static HBox CreateContentNode(char prefix, String text, List<StyledSegment> intraSegments)
	{
		boolean added = prefix == '+';
		boolean removed = prefix == '-';

		// Leading visual space (separates content from the prefix character)
		Text spacer = new Text(" ");
		spacer.setFont(MONO_FONT);

		if (intraSegments == null || intraSegments.isEmpty())
		{
			// Plain text (pure addition/removal or context line)
			Text plain = new Text(text);
			plain.setFont(MONO_FONT);
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
