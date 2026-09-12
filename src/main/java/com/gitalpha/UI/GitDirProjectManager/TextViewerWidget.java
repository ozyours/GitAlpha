package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.Debug;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Engine.GitDirContainer.IScannedFilesUpdatedEvent;
import com.gitalpha.Type.ETextVariant;
import com.gitalpha.Type.FileChange;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import com.gitalpha.UI.Components.AText;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.flowless.VirtualizedScrollPane;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a coloured unified-diff view for a single file change.
 * Each diff line's old/new line numbers and +/-/space prefix are rendered as
 * non-selectable paragraph graphic nodes; the CodeArea body contains only the
 * selectable content text.
 * <p>
 * The diff is rendered in a {@link CodeArea} (RichTextFX) which provides native
 * text selection, virtualization, and horizontal/vertical scrolling. Line
 * backgrounds are applied via paragraph styles, text styles (intra-line
 * highlights) via inline style ranges, and a paragraph graphic factory
 * builds non-selectable line-number + prefix nodes for each line.
 * <p>
 * Theming: the stats header (added/removed counts) and the overlay messages
 * (loading / guard / large-file prompt / error) are {@link AText} nodes themed
 * via {@link ETextVariant}, so they re-theme on palette switches. The diff text
 * itself uses a fixed {@link #MONO_FONT} via CSS and stays plain-styled.
 */
public class TextViewerWidget extends BaseWidget
{
	/**
	 * Monospaced font used for diff content rows. Retained for reference; the
	 * actual CodeArea font is set via inline CSS in the constructor because
	 * RichTextFX's internal Text nodes require a stylesheet-level declaration.
	 */
	private static final Font MONO_FONT = Font.font("Consolas", 14);
	/**
	 * Stats header font size (px); the header counters use the MONO_* variants' Consolas family, larger than the 13px {@link #MONO_FONT} of the diff rows
	 */
	private static final double STATS_FONT_SIZE = 16;

	/**
	 * Pattern to tokenize a line into alternating whitespace and non-whitespace runs
	 */
	private static final Pattern TOKEN_PATTERN = Pattern.compile("\\S+|\\s+");

	/**
	 * Per-line data for the paragraph graphic factory. Stores the prefix
	 * character, old/new line numbers, and the numWidth so the factory can
	 * build non-selectable line-number + prefix nodes.
	 */
	private record GraphicLine(char prefix, Integer oldNum, Integer newNum, int numWidth) {}

	/**
	 * Stores per-line data for the paragraph graphic factory, populated in
	 * {@link #SetDiffRows}. The factory reads this list by paragraph index.
	 */
	private final List<GraphicLine> GraphicRows = new ArrayList<>();

	/**
	 * A segment of text with a flag indicating whether it is part of a changed
	 * (added/removed) span in an intra-line diff.
	 */
	private static record StyledSegment(String text, boolean highlighted)
	{
	}

	/**
	 * The result of an intra-line diff between an old (removed) line and a new (added) line.
	 * Each side carries its own list of styled segments.
	 */
	private static record IntraLineDiff(List<StyledSegment> oldSegments, List<StyledSegment> newSegments)
	{
	}

	/**
	 * Data-only carrier for a single diff row, produced off the JavaFX thread.
	 * Contains everything needed to render the row in the CodeArea.
	 */
	private static record PreparedRow(char prefix, Integer oldLineNumber, Integer newLineNumber, String text,
									  List<StyledSegment> intraSegments)
	{
	}

	/**
	 * RichTextFX code area that renders the diff text with inline styles.
	 * Handles text selection, virtualization, and native scrolling.
	 */
	private final CodeArea DiffCodeArea;
	/**
	 * Wraps the {@link CodeArea} in a {@link VirtualizedScrollPane} that
	 * provides the visible vertical and horizontal scrollbars. The raw
	 * {@code CodeArea} is a {@code Virtualized} component — it virtualizes
	 * content but does not render scrollbar widgets on its own.
	 */
	private final VirtualizedScrollPane<CodeArea> DiffScrollPane;
	/**
	 * Theme listener that re-applies the rich-text skin to {@link #DiffCodeArea}
	 * whenever the palette changes (the selection fill is palette-driven).
	 */
	private final IThemeChangeEvent RichTextSkinApplier;
	/**
	 * Last baked rich-text stylesheet URL on {@link #DiffCodeArea}, removed
	 * by exact URL before the fresh sheet is added on each re-bake.
	 */
	private String RichTextSkinUrl = null;
	/**
	 * Theme listener that re-applies the minimalist scrollbar skin to both
	 * scrollbars inside {@link #DiffScrollPane} whenever the palette changes.
	 */
	private final IThemeChangeEvent ScrollBarSkinApplier;
	/**
	 * Overlay on top of the diff area for loading / guard messages / the large-file prompt / error messages
	 */
	private final StackPane OverlayPane;
	/**
	 * Header bar above the diff area showing the current diff's added/removed
	 * line counts ({@code +N} in green, {@code -M} in red). Kept invisible and
	 * un-managed until a diff is rendered, so it collapses to no height.
	 */
	private final HBox hbox_StatsHeader;
	/**
	 * Added-line count text ({@code +N}) shown in {@link #hbox_StatsHeader}
	 */
	private final AText txt_AddedCount;
	/**
	 * Removed-line count text ({@code -M}) shown in {@link #hbox_StatsHeader}
	 */
	private final AText txt_RemovedCount;
	/**
	 * Static "Changes:" label shown in {@link #hbox_StatsHeader}
	 */
	private final AText txt_StatsLabel;
	/**
	 * The file change whose diff is currently displayed; null if none.
	 * Volatile because it is written on the JavaFX thread and read on the
	 * ForkJoinPool thread (stale-response checks).
	 */
	private volatile FileChange FileChangeTarget = null;
	/**
	 * Listener that re-renders the current diff when its {@link FileChange} is
	 * among the preserved entries whose scan timestamp was refreshed. Held
	 * strongly so the engine's weak reference stays alive as long as this
	 * widget does; no explicit removal needed (pruned on broadcast once this
	 * widget is collected).
	 */
	private final IScannedFilesUpdatedEvent ScannedFilesUpdatedEventListener;
	/**
	 * Token of the latest raw-diff request. Written on the JavaFX thread when a
	 * new diff is requested; the off-thread parse/prepare completion compares it
	 * against the captured token to drop stale responses (same role as
	 * {@link #FileChangeTarget} for file-change loads).
	 */
	private volatile String RawDiffToken = null;

	/**
	 * Builds a standalone viewer for a repository, used outside a project widget
	 * (e.g. the Stash window). The viewer needs no hosting {@link GitDirWidget}
	 * — it only reads the repo for row-height measurement.
	 */
	public TextViewerWidget(GitDir _GitDirTarget)
	{
		this(_GitDirTarget, null);
	}

	/**
	 * Builds the viewer: a {@link CodeArea} that renders the diff with selectable
	 * content text and a non-selectable paragraph graphic factory providing line
	 * numbers and prefix characters, with a {@link StackPane} overlay for the
	 * loading / guard / prompt / error states. The diff itself is populated via
	 * {@link #SetFileChange}.
	 */
	public TextViewerWidget(GitDir _GitDirTarget, GitDirWidget _GitDirWidgetTarget)
	{
		super(_GitDirTarget, _GitDirWidgetTarget);

		// RichTextFX code area — non-editable, monospace, virtualized with native
		// text selection and scrolling. Font is set via inline CSS rather than the
		// MONO_FONT constant (which is retained only for reference), because
		// CodeArea's internal Text nodes need a stylesheet-level font declaration.
		DiffCodeArea = new CodeArea();
		DiffCodeArea.setEditable(false);
		DiffCodeArea.setFocusTraversable(false);
		DiffCodeArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px;");

		// Apply the rich-text skin (line backgrounds, intra-line highlights,
		// primary selection fill) baked from the active palette. The previously
		// baked URL is removed by exact match before the fresh sheet is added.
		// Re-applies on palette switches via IThemeChangeEvent (held as a field
		// so the weak-reference list in ThemeManager keeps it alive). The event
		// argument is intentionally unread — the skin is always pulled from the
		// active palette through ThemeManager, the single bake source.
		RichTextSkinApplier = __Ignored ->
		{
			if (RichTextSkinUrl != null)
				DiffCodeArea.getStylesheets().remove(RichTextSkinUrl);
			var __Skin = ThemeManager.Instance.GetRichTextStylesheets();
			RichTextSkinUrl = __Skin.get(0);
			DiffCodeArea.getStylesheets().addAll(__Skin);
		};
		RichTextSkinApplier.Event(ThemeManager.Instance.GetPalette());
		ThemeManager.Instance.AddIThemeChangeEvent(RichTextSkinApplier);

		// Paragraph graphic factory: builds non-selectable line-number + prefix
		// nodes. The CodeArea body contains only the selectable content text.
		DiffCodeArea.setParagraphGraphicFactory(index ->
		{
			if (index < 0 || index >= GraphicRows.size())
				return null;
			GraphicLine __Line = GraphicRows.get(index);

			String __OldNum = __Line.oldNum() == null
				? " ".repeat(__Line.numWidth())
				: String.format("%" + __Line.numWidth() + "d", __Line.oldNum());
			String __NewNum = __Line.newNum() == null
				? " ".repeat(__Line.numWidth())
				: String.format("%" + __Line.numWidth() + "d", __Line.newNum());

			Text __OldNumText = new Text(" " + __OldNum);
			__OldNumText.setFont(Font.font("Consolas", 14));
			__OldNumText.setFill(Color.GRAY);

			Text __NewNumText = new Text(" " + __NewNum);
			__NewNumText.setFont(Font.font("Consolas", 14));
			__NewNumText.setFill(Color.GRAY);

			Text __PrefixText = new Text(" " + __Line.prefix() + " ");
			__PrefixText.setFont(Font.font("Consolas", 14));
			if (__Line.prefix() == '+')
				__PrefixText.setFill(Color.web("#2da44e"));
			else if (__Line.prefix() == '-')
				__PrefixText.setFill(Color.web("#cf222e"));
			else
				__PrefixText.setFill(Color.GRAY);

			HBox __Graphic = new HBox(__OldNumText, __NewNumText, __PrefixText);
			__Graphic.setAlignment(Pos.CENTER_LEFT);
			return __Graphic;
		});

		// VirtualizedScrollPane wraps the CodeArea to provide visible vertical
		// and horizontal scrollbars. The raw CodeArea is a Virtualized component
		// that virtualizes content but does not render scrollbar widgets.
		DiffScrollPane = new VirtualizedScrollPane<>(DiffCodeArea);

		// Tag the scrollbars inside the VirtualizedScrollPane with
		// .a-scroll-bar so the ScrollBarSkin (transparent track, thin rounded
		// thumb, no arrows) is applied. Re-applies on palette switches via
		// IThemeChangeEvent (held as a field so the weak-reference list in
		// ThemeManager keeps it alive). The scrollbars are found via CSS
		// lookup because VirtualizedScrollPane does not expose getter methods.
		ScrollBarSkinApplier = __Palette ->
		{
			var __Skin = ThemeManager.Instance.GetScrollBarStylesheets();
			for (var __Node : DiffScrollPane.lookupAll(".scroll-bar"))
			{
				if (__Node instanceof javafx.scene.control.ScrollBar __Bar)
				{
					if (!__Bar.getStyleClass().contains("a-scroll-bar"))
						__Bar.getStyleClass().add("a-scroll-bar");
					__Bar.getStylesheets().clear();
					__Bar.getStylesheets().addAll(__Skin);
				}
			}
		};
		ScrollBarSkinApplier.Event(null);
		ThemeManager.Instance.AddIThemeChangeEvent(ScrollBarSkinApplier);

		// Stats header bar (added/removed line counts). Starts hidden and
		// un-managed so it collapses to no height until a diff is rendered
		// (see UpdateStatsHeader); kept in the same VBox as the code area
		// so it sits above the diff.
		txt_StatsLabel = new AText("Changes:", ETextVariant.MONO_MUTED, STATS_FONT_SIZE);
		txt_AddedCount = new AText("+0", ETextVariant.MONO_ADDED, STATS_FONT_SIZE);
		txt_RemovedCount = new AText("-0", ETextVariant.MONO_REMOVED, STATS_FONT_SIZE);
		hbox_StatsHeader = new HBox(8, txt_StatsLabel, txt_AddedCount, txt_RemovedCount);
		hbox_StatsHeader.setAlignment(Pos.CENTER_LEFT);
		hbox_StatsHeader.setPadding(new Insets(4, 8, 4, 8));
		hbox_StatsHeader.setStyle("-fx-border-color: transparent transparent #d0d7de transparent; -fx-border-width: 0 0 1 0;");
		hbox_StatsHeader.setManaged(false);
		hbox_StatsHeader.setVisible(false);

		// Stack the stats header above the code area; the code area grows to fill
		// the remaining height. The VBox is given MAX_VALUE bounds so the parent
		// StackPane lets it fill the available space — without this, StackPane
		// uses the VBox's preferred size and the CodeArea overflows without
		// showing scrollbars.
		VBox __Container = new VBox(hbox_StatsHeader, DiffScrollPane);
		VBox.setVgrow(DiffScrollPane, Priority.ALWAYS);
		__Container.setMaxWidth(Double.MAX_VALUE);
		__Container.setMaxHeight(Double.MAX_VALUE);
		getChildren().add(__Container);

		OverlayPane = new StackPane();
		OverlayPane.setPickOnBounds(false);
		HideOverlay();
		getChildren().add(OverlayPane);

		// Re-render only when a refresh scan re-observes the displayed FileChange
		// (its scan timestamp was advanced); ignoring unrelated files keeps their
		// state intact. The broadcast fires on the operator runner thread so the
		// membership check runs there and the reload on the FX thread, re-checked
		// there in case the selection moved in between.
		ScannedFilesUpdatedEventListener = (_UpdatedFiles) ->
		{
			FileChange __Current = FileChangeTarget;

			if (__Current == null)
				return;

			for (var __FC : _UpdatedFiles)
			{
				if (__FC.CompareFile(__Current))
				{
					Platform.runLater(() ->
					{
						if (FileChangeTarget != null && FileChangeTarget.CompareFile(__Current))
							SetFileChange(FileChangeTarget);
					});
					return;
				}
			}
		};
		GetGitDirTarget().AddIScannedFilesUpdatedEvent(ScannedFilesUpdatedEventListener);
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
			return new IntraLineDiff(List.of(new StyledSegment(oldText, false)), List.of(new StyledSegment(newText, false)));

		List<String> oldTokens = Tokenize(oldText);
		List<String> newTokens = Tokenize(newText);

		// ---- one side empty -> everything on the other side is "changed" ----
		if (oldTokens.isEmpty() && newTokens.isEmpty())
			return new IntraLineDiff(List.of(), List.of());
		if (oldTokens.isEmpty())
			return new IntraLineDiff(List.of(), List.of(new StyledSegment(newText, true)));
		if (newTokens.isEmpty())
			return new IntraLineDiff(List.of(new StyledSegment(oldText, true)), List.of());

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
		return new IntraLineDiff(BuildSegments(oldTokens, oldInLCS), BuildSegments(newTokens, newInLCS));
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
		boolean prevInLCS = true;        // start in "unchanged" state

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

		// Clear the previous file's rows up front: the loading indicator and any
		// guard message (binary / large file) then render over an empty viewport
		// instead of overlapping the previous file's diff.
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
	 * Renders raw unified-diff text (e.g. {@code git diff <stash>^ <stash>} output)
	 * through the same highlighted pipeline as {@link #SetFileChange}. The diff text
	 * is parsed and prepared off the JavaFX thread; stale responses (a newer request
	 * superseding this one) are dropped. A {@code null} or blank payload clears the
	 * viewport.
	 * <p>
	 * <strong>Must be called on the JavaFX Application Thread.</strong>
	 */
	public void SetRawDiffText(String _DiffText)
	{
		String __Token = Long.toString(System.nanoTime());
		RawDiffToken = __Token;
		FileChangeTarget = null;   // raw diffs bypass the FileChange path
		ClearDiffView();

		if (_DiffText == null || _DiffText.isBlank())
		{
			HideOverlay();
			return;
		}

		ShowLoadingIndicator();
		CompletableFuture.supplyAsync(() -> PrepareDiffRows(FileChange.ParseDiff(_DiffText))).thenAcceptAsync(__Prepared -> Platform.runLater(() ->
		{
			if (!java.util.Objects.equals(RawDiffToken, __Token))
				return;   // stale — a newer diff replaced this request
			SetDiffRows(__Prepared);
			HideOverlay();
		})).exceptionally(__Ex ->
		{
			Platform.runLater(() ->
			{
				if (java.util.Objects.equals(RawDiffToken, __Token))
					RenderErrorMessage(__Ex);
			});
			return null;
		});
	}

	/**
	 * Clears the {@link #DiffCodeArea} text, resets the paragraph graphic
	 * factory data ({@link #GraphicRows}), and hides the stats header (back
	 * to {@code +0/-0}). Called when switching targets so no stale content
	 * lingers behind the overlay; also called for a {@code null} target to
	 * blank the viewport entirely.
	 * <p>
	 * <strong>Must be called on the JavaFX Application Thread.</strong>
	 */
	private void ClearDiffView()
	{
		DiffCodeArea.clear();
		GraphicRows.clear();
		UpdateStatsHeader(0, 0);
	}

	/**
	 * Kicks off a (possibly guarded) diff load for {@code _Target} and renders
	 * the outcome. {@code _Force} true bypasses the large-file guard — used when
	 * the user explicitly asks to load a large file; the binary guard always applies.
	 */
	private void LoadDiff(FileChange _Target, boolean _Force)
	{
		CompletableFuture<FileChange.DiffLoadResult> __Load = _Force ? _Target.GetDiffLinesForce() : _Target.GetDiffLines();

		__Load.thenAcceptAsync(result -> HandleDiffResult(_Target, result)).exceptionally(ex ->
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
			return;                            // stale — user switched to another file

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
	 * Phase 2 flushes the styled text to the CodeArea on the JavaFX thread.
	 */
	private void RenderDiffLines(FileChange _Target, List<FileChange.LineChange> _DiffLines)
	{
		List<PreparedRow> prepared = PrepareDiffRows(_DiffLines);

		Platform.runLater(() ->
		{
			if (FileChangeTarget != _Target)
				return;                        // stale by the time we got to FX

			SetDiffRows(prepared);
		});
	}

	/**
	 * Shows a centered "Loading..." overlay on top of the diff area.
	 */
	private void ShowLoadingIndicator()
	{
		AText loadingText = new AText("Loading...", ETextVariant.MONO);
		ShowOverlay(loadingText);
	}

	/**
	 * Shows a non-interactive guard message (e.g. binary files) centered over the
	 * diff area.
	 */
	private void RenderGuardMessage(String _Message)
	{
		AText messageText = new AText(_Message, ETextVariant.MONO);
		messageText.setTextAlignment(TextAlignment.CENTER);
		ShowOverlay(messageText);
	}

	/**
	 * Shows the large-file prompt: an explanation plus a "Load file" button that
	 * force-loads the diff (explicit user action). Binary files never reach this.
	 */
	private void RenderLargeFilePrompt(FileChange _Target)
	{
		AText messageText = new AText("This file is large and will not be loaded automatically.", ETextVariant.MONO);
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

	/**
	 * Renders an error message (from a failed diff load) centered over the diff area.
	 */
	private void RenderErrorMessage(Throwable _Exception)
	{
		AText errorText = new AText("Error: " + ExtractErrorMessage(_Exception), ETextVariant.MONO);
		ShowOverlay(errorText);
	}

	/**
	 * Walks the cause chain to the deepest non-blank message (user-facing text).
	 */
	private static String ExtractErrorMessage(Throwable _Exception)
	{
		Throwable __Deepest = _Exception;
		while (__Deepest.getCause() != null)
			__Deepest = __Deepest.getCause();
		String __Message = __Deepest.getMessage();
		return (__Message != null && !__Message.isBlank()) ? __Message : __Deepest.toString();
	}

	/**
	 * Shows a centered overlay on top of the diff area (loading / messages / prompt).
	 */
	private void ShowOverlay(Node _Content)
	{
		OverlayPane.getChildren().setAll(_Content);
		OverlayPane.setVisible(true);
		OverlayPane.setManaged(true);
	}

	/**
	 * Hides the overlay so only the diff area is visible.
	 */
	private void HideOverlay()
	{
		OverlayPane.getChildren().clear();
		OverlayPane.setVisible(false);
		OverlayPane.setManaged(false);
	}

	/**
	 * Renews the stats header with the given added/removed counts, hiding it
	 * (un-managed, so it collapses) when there is nothing to report.
	 */
	private void UpdateStatsHeader(int _Added, int _Removed)
	{
		boolean __HasStats = _Added > 0 || _Removed > 0;
		txt_AddedCount.setText("+" + _Added);
		txt_RemovedCount.setText("-" + _Removed);
		hbox_StatsHeader.setVisible(__HasStats);
		hbox_StatsHeader.setManaged(__HasStats);
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
					result.add(new PreparedRow(removedLine.prefix(), removedLine.oldLineNumber(), removedLine.newLineNumber(), removedLine.text(), diff.oldSegments()));
					result.add(new PreparedRow(line.prefix(), line.oldLineNumber(), line.newLineNumber(), line.text(), diff.newSegments()));
				}
				else
				{
					// Pure addition — no matching removal
					result.add(new PreparedRow(line.prefix(), line.oldLineNumber(), line.newLineNumber(), line.text(), null));
				}
				continue;
			}

			// Context (or other non-diff) line — flush any pending removals as pure removals
			while (!pendingRemovals.isEmpty())
			{
				FileChange.LineChange rm = pendingRemovals.removeFirst();
				result.add(new PreparedRow(rm.prefix(), rm.oldLineNumber(), rm.newLineNumber(), rm.text(), null));
			}
			result.add(new PreparedRow(line.prefix(), line.oldLineNumber(), line.newLineNumber(), line.text(), null));
		}

		// Flush remaining unpaired removals at end
		while (!pendingRemovals.isEmpty())
		{
			FileChange.LineChange rm = pendingRemovals.removeFirst();
			result.add(new PreparedRow(rm.prefix(), rm.oldLineNumber(), rm.newLineNumber(), rm.text(), null));
		}

		return result;
	}

	/**
	 * Renders the prepared rows into the {@link #DiffCodeArea}. The widest line
	 * number is computed once here (a cheap pass over the plain-data records) so
	 * every row aligns to the same padding in the paragraph graphic. The CodeArea
	 * body contains only the selectable content text; line numbers and prefix
	 * characters are rendered by the paragraph graphic factory.
	 * <p>
	 * After the text is replaced, paragraph styles (backgrounds) and intra-line
	 * text styles (intra-line highlight backgrounds) are applied.
	 * <p>
	 * <strong>Must be called on the JavaFX Application Thread.</strong>
	 */
	private void SetDiffRows(List<PreparedRow> _Prepared)
	{
		// Count added/removed for stats header
		int __Added = 0, __Removed = 0, __MaxNum = 0;
		for (var __Row : _Prepared)
		{
			if (__Row.prefix() == '+') __Added++;
			else if (__Row.prefix() == '-') __Removed++;
			if (__Row.oldLineNumber() != null) __MaxNum = Math.max(__MaxNum, __Row.oldLineNumber());
			if (__Row.newLineNumber() != null) __MaxNum = Math.max(__MaxNum, __Row.newLineNumber());
		}
		int __NumWidth = Math.max(1, String.valueOf(__MaxNum).length());
		UpdateStatsHeader(__Added, __Removed);

		// Populate GraphicRows for the paragraph graphic factory
		GraphicRows.clear();
		for (var __Row : _Prepared)
			GraphicRows.add(new GraphicLine(__Row.prefix(), __Row.oldLineNumber(), __Row.newLineNumber(), __NumWidth));

		// Build full text: ONLY content lines (no bar, no numbers, no prefix)
		StringBuilder __FullText = new StringBuilder();
		for (int i = 0; i < _Prepared.size(); i++)
		{
			if (i > 0) __FullText.append('\n');
			String __Content = _Prepared.get(i).text();
			__FullText.append(__Content != null ? __Content : "");
		}

		// Replace the CodeArea text
		DiffCodeArea.replaceText(0, DiffCodeArea.getLength(), __FullText.toString());

		// Apply paragraph styles (backgrounds) and intra-line text styles only.
		// Line numbers and prefix are in the paragraph graphic — not in the text.
		int __Offset = 0;
		for (int i = 0; i < _Prepared.size(); i++)
		{
			var __Row = _Prepared.get(i);

			// Paragraph background
			if (__Row.prefix() == '+')
				DiffCodeArea.setParagraphStyle(i, List.of("diff-line-added"));
			else if (__Row.prefix() == '-')
				DiffCodeArea.setParagraphStyle(i, List.of("diff-line-removed"));
			else
				DiffCodeArea.setParagraphStyle(i, List.of());

			// Intra-line highlights on content
			if (__Row.intraSegments() != null && !__Row.intraSegments().isEmpty())
			{
				String __IntraClass = __Row.prefix() == '+' ? "diff-intra-added" : "diff-intra-removed";
				int __Pos = __Offset;
				for (var __Seg : __Row.intraSegments())
				{
					if (__Seg.highlighted())
						DiffCodeArea.setStyle(__Pos, __Pos + __Seg.text().length(), List.of(__IntraClass));
					__Pos += __Seg.text().length();
				}
			}
			String __Content = __Row.text();
			__Offset += (__Content != null ? __Content.length() : 0);
			if (i < _Prepared.size() - 1) __Offset++; // newline
		}

		DiffCodeArea.moveTo(0, 0);
		HideOverlay();
	}
}
