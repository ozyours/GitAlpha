package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.Debug;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.EFileLoadGuard;
import com.gitalpha.Type.FileChange;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a coloured unified-diff view for a single file change.
 * Each diff line shows old/new line numbers, a +/-/space prefix, and the line
 * content, with green/red highlighting for additions and removals.
 * <p>
 * The rows live in a virtualized {@link ListView}: only the rows visible in the
 * viewport are materialized as JavaFX nodes, so node count stays O(visible)
 * regardless of the diff size (the previous ScrollPane + VBox created one HBox
 * per row and froze on large files). The list always fills the pane, so its
 * vertical scrollbar stays pinned to the right edge of the viewport; rows wider
 * than the pane are panned by a bottom horizontal {@link ScrollBar} whose value
 * drives {@link #PanOffset}, and each visible cell translates its row content
 * by {@code PanOffset.negate()} so only the content slides inside the cell.
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

	/** Fallback uniform row height (px) used if the sample measurement fails */
	private static final double DEFAULT_ROW_HEIGHT = 22.0;
	/** Extra height (px) added to the measured row height to cover the default {@code .list-cell} vertical padding */
	private static final double LIST_CELL_VERTICAL_PADDING = 8.0;
	/** Width (px) of the colour-coded left bar of each diff row */
	private static final double BAR_WIDTH = 4.0;
	/** Horizontal buffer (px) added to the widest-row measurement so the content never clips */
	private static final double CELL_H_PADDING = 12.0;

	/** Advance width (px) of a single character in the mono font (cached for row-width math) */
	private static final double MONO_CHAR_WIDTH = MeasureMonoCharWidth();

	/** Measures the advance width of one character in the mono font. */
	private static double MeasureMonoCharWidth()
	{
		Text __Meter = new Text("M");
		__Meter.setFont(MONO_FONT);
		return __Meter.getLayoutBounds().getWidth();
	}

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
	 * Contains everything {@link #CreateRowBox} needs to create the JavaFX nodes.
	 */
	private static record PreparedRow(
		char prefix,
		Integer oldLineNumber,
		Integer newLineNumber,
		String text,
		List<StyledSegment> intraSegments
	) {}

	/** Virtualized diff list — VirtualFlow materializes only the visible rows */
	private final ListView<PreparedRow> DiffListView;
	/**
	 * Bottom horizontal scrollbar that drives {@link #PanOffset}; visible only
	 * when a content row is wider than the pane.
	 */
	private final ScrollBar DiffScrollBar = new ScrollBar();
	/**
	 * Horizontal pan (px) applied to the row content of every visible cell;
	 * bound to the bottom scrollbar's value so the pan follows the thumb.
	 */
	private final DoubleProperty PanOffset = new SimpleDoubleProperty(0);
	/** Overlay on top of the diff list for loading / guard messages / the large-file prompt / error messages */
	private final StackPane OverlayPane;
	/**
	 * The file change whose diff is currently displayed; null if none.
	 * Volatile because it is written on the JavaFX thread and read on the
	 * ForkJoinPool thread (stale-response checks).
	 */
	private volatile FileChange FileChangeTarget = null;
	/** Format string (with padding) for line numbers, matching the widest number in the current diff */
	private String NumFormat = "%d";
	/** Blank placeholder with the same width as the widest line number */
	private String EmptyNum = " ";
	/**
	 * Width (px) needed by the widest row of the current diff. It drives the
	 * bottom scrollbar's pan range: the list itself always fills the pane, and
	 * content only pans when this exceeds the pane width.
	 */
	private final DoubleProperty DiffContentWidth = new SimpleDoubleProperty(0);

	/**
	 * Builds the viewer: a virtualized {@link ListView} pinned to a fixed cell
	 * size that always fills the pane (its vertical scrollbar therefore stays at
	 * the right edge of the visible viewport), with a bottom horizontal
	 * scrollbar that pans wide rows by translating the row content inside each
	 * visible cell, and a {@link StackPane} overlay for the loading / guard /
	 * prompt / error states. The diff itself is populated via {@link #SetFileChange}.
	 */
	public TextViewerWidget(GitDir _GitDirTarget, GitDirWidget _GitDirWidgetTarget)
	{
		super(_GitDirTarget, _GitDirWidgetTarget);

		// Virtualized diff view: the ListView's VirtualFlow creates cells only for
		// the visible rows, so the node count is O(visible) no matter the diff size.
		DiffListView = new ListView<>();
		DiffListView.setFixedCellSize(ComputeFixedCellSize());
		DiffListView.setCellFactory(__List -> new DiffRowCell());
		DiffListView.setFocusTraversable(false);

		// Flat white viewport: the default ListView theme shades alternate rows
		// (:odd cells paint -fx-control-inner-background-alt). Pinning both
		// inner-background colours to white removes the alternating stripes so the
		// whole viewer reads as a single white surface; the green/red diff rows
		// keep their inline backgrounds (see RowBackgroundStyle).
		DiffListView.setStyle("-fx-control-inner-background: white; -fx-control-inner-background-alt: white;");

		// The list always fills the pane, so its vertical scrollbar stays pinned
		// to the right edge of the visible viewport. Wide lines are panned by
		// translating the row content inside each visible cell (see DiffRowCell),
		// driven by the bottom horizontal scrollbar — the list itself is never
		// wider than the pane (so its content width can't inflate the GridPane
		// column either).
		DiffScrollBar.setOrientation(Orientation.HORIZONTAL);
		DiffScrollBar.setMin(0);
		DiffScrollBar.setUnitIncrement(MONO_CHAR_WIDTH * 4);
		DiffScrollBar.setVisible(false);
		DiffScrollBar.setManaged(false);
		PanOffset.bind(DiffScrollBar.valueProperty());

		// Wheel tilt pans horizontally when the bar is shown; vertical wheel
		// events stay with VirtualFlow (never consumed here).
		DiffListView.addEventFilter(ScrollEvent.SCROLL, __Event ->
		{
			if (__Event.getDeltaX() != 0 && DiffScrollBar.isVisible())
			{
				DiffScrollBar.setValue(DiffScrollBar.getValue() + __Event.getDeltaX());
				if (__Event.getDeltaY() == 0)
					__Event.consume();
			}
		});

		// Re-fit the pan range when the pane is resized.
		DiffListView.widthProperty().addListener((__Obs, __Old, __New) -> UpdateScrollRange());

		// Stack the list above the pan scrollbar; the list grows to fill the
		// remaining height while the bar stays pinned to the bottom edge.
		VBox __Container = new VBox(DiffListView, DiffScrollBar);
		VBox.setVgrow(DiffListView, Priority.ALWAYS);
		getChildren().add(__Container);

		OverlayPane = new StackPane();
		OverlayPane.setPickOnBounds(false);
		HideOverlay();
		getChildren().add(OverlayPane);
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
	 * Loads and displays the diff for the given file change: shows a
	 * "Loading..." indicator, then replaces it with the rendered diff, a
	 * large-file prompt (with an explicit "Load file" button), a binary-file
	 * message, or an error message. Stale responses (for a superseded target)
	 * are dropped. A {@code null} target clears the viewport (see
	 * {@link #ClearDiffView}) and hides the overlay.
	 * <p>
	 * Intra-line diff computation (LCS) runs <em>off</em> the JavaFX thread so the
	 * UI stays responsive even for large files.  Only the actual JavaFX node
	 * creation happens on the JavaFX thread, via a single {@link Platform#runLater}
	 * flush.
	 * <p>
	 * <strong>Must be called on the JavaFX Application Thread.</strong>
	 */
	public void SetFileChange(FileChange _FileChangeTarget)
	{
		Debug.Log(Debug.ChangesCategory, "[Changes] SetFileChange(%s)\n", _FileChangeTarget == null ? "<null>" : _FileChangeTarget.GetFilePath().toString());
		FileChangeTarget = _FileChangeTarget;

		// Clear the previous file's rows and the horizontal pan state up front:
		// the loading indicator and any guard message (binary / large file) then
		// render over an empty viewport instead of overlapping the previous
		// file's diff and a stale scrollbar.
		ClearDiffView();

		if (FileChangeTarget == null)
		{
			HideOverlay();
			return;
		}

		ShowLoadingIndicator();
		LoadDiff(_FileChangeTarget, false);
	}

	/**
	 * Empties the diff list and resets the horizontal pan state (content width,
	 * pan position and scrollbar visibility). Called when switching targets so
	 * no stale rows or a stale scrollbar linger behind the overlay; also called
	 * for a {@code null} target to blank the viewport entirely.
	 * <p>
	 * <strong>Must be called on the JavaFX Application Thread.</strong>
	 */
	private void ClearDiffView()
	{
		DiffListView.getItems().clear();
		DiffContentWidth.set(0);
		DiffScrollBar.setValue(0);
		UpdateScrollRange();
	}

	/**
	 * Kicks off a (possibly guarded) diff load for {@code _Target} and renders
	 * the outcome. {@code _Force} true bypasses the large-file guard — used when
	 * the user explicitly asks to load a large file; the binary guard always applies.
	 */
	private void LoadDiff(FileChange _Target, boolean _Force)
	{
		CompletableFuture<FileChange.DiffLoadResult> __Load = _Force
			? _Target.GetDiffLinesForce()
			: _Target.GetDiffLines();

		__Load.thenAcceptAsync(result -> HandleDiffResult(_Target, result))
			.exceptionally(ex ->
			{
				Platform.runLater(() ->
				{
					if (FileChangeTarget == _Target)
						RenderErrorMessage(ex);
				});
				return null;
			});
	}

	/**
	 * Routes a completed diff load: renders the parsed lines, or shows the
	 * large-file prompt / binary message when a guard blocked the load.
	 * Runs off the JavaFX thread; stale responses are dropped.
	 */
	private void HandleDiffResult(FileChange _Target, FileChange.DiffLoadResult _Result)
	{
		if (FileChangeTarget != _Target)
			return;							// stale — user switched to another file

		switch (_Result.Guard())
		{
			case BINARY -> Platform.runLater(() ->
			{
				if (FileChangeTarget != _Target)
					return;
				RenderGuardMessage("This file appears to be binary and cannot be displayed.");
			});
			case LARGE_FILE -> Platform.runLater(() ->
			{
				if (FileChangeTarget != _Target)
					return;
				RenderLargeFilePrompt(_Target);
			});
			case NONE -> RenderDiffLines(_Target, _Result.Lines());
		}
	}

	/**
	 * Pairs and renders the parsed diff lines for the given target.
	 * Phase 1 (pairing + intra-line LCS) runs on the current (non-JavaFX) thread;
	 * Phase 2 flushes the created nodes to the JavaFX thread in one shot.
	 */
	private void RenderDiffLines(FileChange _Target, List<FileChange.LineChange> _DiffLines)
	{
		List<PreparedRow> prepared = PrepareDiffRows(_DiffLines);

		Platform.runLater(() ->
		{
			if (FileChangeTarget != _Target)
				return;						// stale by the time we got to FX

			SetDiffRows(prepared);
		});
	}

	/** Shows a centered "Loading..." overlay on top of the diff list. */
	private void ShowLoadingIndicator()
	{
		Text loadingText = new Text("Loading...");
		loadingText.setFont(MONO_FONT);
		ShowOverlay(loadingText);
	}

	/**
	 * Shows a non-interactive guard message (e.g. binary files) centered over the
	 * diff list.
	 */
	private void RenderGuardMessage(String _Message)
	{
		Text messageText = new Text(_Message);
		messageText.setFont(MONO_FONT);
		messageText.setTextAlignment(TextAlignment.CENTER);
		ShowOverlay(messageText);
	}

	/**
	 * Shows the large-file prompt: an explanation plus a "Load file" button that
	 * force-loads the diff (explicit user action). Binary files never reach this.
	 */
	private void RenderLargeFilePrompt(FileChange _Target)
	{
		Text messageText = new Text("This file is large and will not be loaded automatically.");
		messageText.setFont(MONO_FONT);
		messageText.setTextAlignment(TextAlignment.CENTER);
		messageText.setWrappingWidth(400);

		Button loadButton = new Button("Load file");
		loadButton.setOnAction(__Event ->
		{
			loadButton.setDisable(true);
			ShowLoadingIndicator();
			LoadDiff(_Target, true);
		});

		VBox promptBox = new VBox(12);
		promptBox.setAlignment(Pos.CENTER);
		promptBox.getChildren().addAll(messageText, loadButton);

		ShowOverlay(promptBox);
	}

	/** Renders an error message (from a failed diff load) centered over the diff list. */
	private void RenderErrorMessage(Throwable _Exception)
	{
		Text errorText = new Text("Error: " + ExtractErrorMessage(_Exception));
		errorText.setFont(MONO_FONT);
		ShowOverlay(errorText);
	}

	/** Walks the cause chain to the deepest non-blank message (user-facing text). */
	private static String ExtractErrorMessage(Throwable _Exception)
	{
		Throwable __Deepest = _Exception;
		while (__Deepest.getCause() != null)
			__Deepest = __Deepest.getCause();
		String __Message = __Deepest.getMessage();
		return (__Message != null && !__Message.isBlank()) ? __Message : __Deepest.toString();
	}

	/** Shows a centered overlay on top of the diff list (loading / messages / prompt). */
	private void ShowOverlay(Node _Content)
	{
		OverlayPane.getChildren().setAll(_Content);
		OverlayPane.setVisible(true);
		OverlayPane.setManaged(true);
	}

	/** Hides the overlay so only the diff list is visible. */
	private void HideOverlay()
	{
		OverlayPane.getChildren().clear();
		OverlayPane.setVisible(false);
		OverlayPane.setManaged(false);
	}

	/**
	 * Pairs {@code -} / {@code +} diff lines and computes intra-line (word-level)
	 * diffs for each pair.  Returns a list of plain-data {@link PreparedRow}
	 * records that can be handed to {@link #SetDiffRows}.
	 * <p>
	 * This method performs <strong>no JavaFX operations</strong> — it is safe
	 * to call from any thread (and is intentionally invoked off the JavaFX thread
	 * via {@link java.util.concurrent.CompletableFuture#thenAcceptAsync}).
	 */
	private static List<PreparedRow> PrepareDiffRows(List<FileChange.LineChange> diffLines)
	{
		List<PreparedRow> result = new ArrayList<>();

		if (diffLines == null || diffLines.isEmpty())
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
	 * Publishes the prepared rows to the virtualized diff list. The widest line
	 * number is computed once here (a cheap pass over the plain-data records) so
	 * every row aligns to the same padding, {@link #DiffContentWidth} is set to
	 * the widest-row width, and the bottom scrollbar is reset to the un-panned
	 * position before {@link #UpdateScrollRange} re-derives its pan range and
	 * visibility. Only the visible rows are materialized by VirtualFlow, so a huge
	 * diff renders instantly.
	 * <p>
	 * <strong>Must be called on the JavaFX Application Thread.</strong>
	 */
	private void SetDiffRows(List<PreparedRow> _Prepared)
	{
		// Determine the widest line number for padding alignment
		int __MaxNum = 0;
		for (var __Row : _Prepared)
		{
			if (__Row.oldLineNumber() != null)
				__MaxNum = Math.max(__MaxNum, __Row.oldLineNumber());
			if (__Row.newLineNumber() != null)
				__MaxNum = Math.max(__MaxNum, __Row.newLineNumber());
		}
		int __NumWidth = Math.max(1, String.valueOf(__MaxNum).length());
		NumFormat = "%" + __NumWidth + "d";
		EmptyNum = " ".repeat(__NumWidth);

		// Publish the widest-row width; the bottom scrollbar's range and
		// visibility are derived from it vs the pane width.
		DiffContentWidth.set(ComputeContentWidth(_Prepared, __NumWidth));
		DiffScrollBar.setValue(0);   // a new file starts un-panned
		UpdateScrollRange();

		DiffListView.getItems().setAll(_Prepared);
		// refresh() forces VirtualFlow to rebuild the visible cells even when the
		// new diff has the same row count — otherwise recycled cells would keep
		// showing the previous file's rows (setAll alone only rebuilds on count change).
		DiffListView.refresh();
		DiffListView.scrollTo(0);
		HideOverlay();
	}

	/**
	 * Recomputes the horizontal pan range from the current content width and the
	 * pane width, and shows/hides the bottom scrollbar accordingly.
	 * <p>
	 * <strong>Must be called on the JavaFX Application Thread.</strong>
	 */
	private void UpdateScrollRange()
	{
		double __MaxPan = Math.max(0, DiffContentWidth.get() - DiffListView.getWidth());
		boolean __NeedsBar = __MaxPan > 0;
		DiffScrollBar.setVisible(__NeedsBar);
		DiffScrollBar.setManaged(__NeedsBar);
		DiffScrollBar.setMax(__MaxPan);
		DiffScrollBar.setBlockIncrement(Math.max(50, DiffListView.getWidth() * 0.8));
		if (DiffScrollBar.getValue() > __MaxPan)
			DiffScrollBar.setValue(__MaxPan);
	}

	/**
	 * Computes the pixel width needed to show the widest diff row without clipping:
	 * fixed-width text columns (line numbers, prefix, spacer) plus the longest
	 * content line, scaled by the mono font's character advance. The result is
	 * published to {@link #DiffContentWidth}, which drives the bottom scrollbar's
	 * pan range (the list itself always fills the pane).
	 */
	private static double ComputeContentWidth(List<PreparedRow> _Rows, int _NumWidth)
	{
		int __MaxContentChars = 0;
		for (var __Row : _Rows)
		{
			String __Text = __Row.text();
			if (__Text != null)
				__MaxContentChars = Math.max(__MaxContentChars, __Text.length());
		}

		// Fixed text columns: old number (numWidth+1), new number (numWidth+1),
		// prefix (2), content spacer (1).
		int __FixedChars = 2 * (_NumWidth + 1) + 2 + 1;
		return BAR_WIDTH + (__FixedChars + __MaxContentChars) * MONO_CHAR_WIDTH + CELL_H_PADDING;
	}

	/**
	 * Builds the JavaFX nodes for one diff row (bar + line numbers + prefix +
	 * content text) from a pre-computed {@link PreparedRow}. The green/red row
	 * background is not set here — it goes on the {@link ListCell} so it spans the
	 * full cell width and height.
	 *
	 * @param _Row       the pre-computed row data (produced off-thread)
	 * @param _NumFormat format string for line numbers
	 * @param _EmptyNum  blank placeholder when a line number is absent
	 */
	private static HBox CreateRowBox(PreparedRow _Row, String _NumFormat, String _EmptyNum)
	{
		HBox rowBox = new HBox();
		boolean added = _Row.prefix() == '+';
		boolean removed = _Row.prefix() == '-';

		// Colour-coded left bar
		Pane bar = new Pane();
		bar.setMinWidth(4);
		bar.setPrefWidth(4);
		if (added)
			bar.setStyle("-fx-background-color: " + ADDED_BAR + ";");
		else if (removed)
			bar.setStyle("-fx-background-color: " + REMOVED_BAR + ";");
		else
			bar.setStyle("-fx-background-color: transparent;");

		// Line numbers
		String oldStr = _Row.oldLineNumber() == null ? _EmptyNum : String.format(_NumFormat, _Row.oldLineNumber());
		String newStr = _Row.newLineNumber() == null ? _EmptyNum : String.format(_NumFormat, _Row.newLineNumber());

		Text oldNum = new Text(" " + oldStr);
		oldNum.setFont(MONO_FONT);
		oldNum.setFill(Color.GRAY);

		Text newNum = new Text(" " + newStr);
		newNum.setFont(MONO_FONT);
		newNum.setFill(Color.GRAY);

		// Prefix character
		Text prefixText = new Text(" " + _Row.prefix());
		prefixText.setFont(MONO_FONT);
		if (added)
			prefixText.setFill(Color.rgb(45, 164, 78));
		else if (removed)
			prefixText.setFill(Color.rgb(207, 34, 46));
		else
			prefixText.setFill(Color.GRAY);

		// Content — either intra-line segments or plain text
		HBox content = CreateContentNode(_Row.prefix(), _Row.text(), _Row.intraSegments());

		rowBox.getChildren().addAll(bar, oldNum, newNum, prefixText, content);
		return rowBox;
	}

	/** Inline cell background for the row type, or empty for context rows. */
	private static String RowBackgroundStyle(char _Prefix)
	{
		if (_Prefix == '+')
			return "-fx-background-color: " + ADDED_BG + ";";
		if (_Prefix == '-')
			return "-fx-background-color: " + REMOVED_BG + ";";
		return "";
	}

	/**
	 * Computes the uniform ListView row height by measuring a sample diff row.
	 * Pinning a fixed cell size makes VirtualFlow's scroll-range math exact — the
	 * same workaround used by {@link ChangesWidget} for the JDK VirtualFlow
	 * size-estimation regression (JDK-8296871 / JDK-8301375 / JDK-8328167).
	 */
	private double ComputeFixedCellSize()
	{
		try
		{
			HBox __Sample = CreateRowBox(new PreparedRow(' ', 1, 1, "Ag", null), "%d", " ");
			__Sample.applyCss();
			double __Height = Math.ceil(__Sample.prefHeight(-1) + LIST_CELL_VERTICAL_PADDING);
			return __Height >= DEFAULT_ROW_HEIGHT ? __Height : DEFAULT_ROW_HEIGHT;
		}
		catch (Exception __Ex)
		{
			return DEFAULT_ROW_HEIGHT;
		}
	}

	/**
	 * ListCell used for diff rows. Cells are recycled by VirtualFlow, so
	 * {@code updateItem} resets every property and re-derives the row graphic
	 * from the {@link PreparedRow}. The inline background overrides the
	 * {@code :selected} / {@code :hover} cell backgrounds so the green/red row
	 * colour stays stable.
	 * <p>
	 * The row HBox is wrapped in a minimal-width {@link StackPane} so the cell's
	 * <em>preferred</em> width stays tiny: VirtualFlow shows the ListView's own
	 * horizontal scrollbar whenever a cell's preferred width exceeds the
	 * viewport, which would duplicate the pan scrollbar below the list. The
	 * wrapper keeps the built-in bar suppressed while the row still lays out at
	 * its natural width inside the viewport-sized cell (see {@code updateItem}).
	 */
	private final class DiffRowCell extends ListCell<PreparedRow>
	{
		@Override
		protected void updateItem(PreparedRow _Row, boolean _Empty)
		{
			super.updateItem(_Row, _Empty);
			setText(null);

			if (_Empty || _Row == null)
			{
				setGraphic(null);
				setStyle("");
				return;
			}

			setStyle(RowBackgroundStyle(_Row.prefix()));
			HBox __RowBox = CreateRowBox(_Row, NumFormat, EmptyNum);
			// Pan wide rows by translating the row content (bar + numbers + prefix
			// + text) inside the viewport-sized cell; the ListView itself never
			// exceeds the pane, so its vertical scrollbar stays visible.
			__RowBox.translateXProperty().bind(PanOffset.negate());
			// Wrap the row in a pane that reports a minimal preferred width so
			// VirtualFlow never shows the ListView's own horizontal scrollbar
			// (which would sit above the pan scrollbar — a duplicated bar).
			// The cell sizes the wrapper to at most the viewport width and the
			// row inside lays out at its natural width, overflowing to the right
			// where it is clipped by the viewport and revealed by the pan offset.
			StackPane __PanViewport = new StackPane(__RowBox);
			__PanViewport.setAlignment(Pos.TOP_LEFT);
			__PanViewport.setMinWidth(0);
			__PanViewport.setPrefWidth(1);
			setGraphic(__PanViewport);
		}
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
