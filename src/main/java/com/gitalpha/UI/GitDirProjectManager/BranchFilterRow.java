package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Type.EButtonVariant;
import com.gitalpha.UI.Components.AButton;
import com.gitalpha.UI.Components.ATextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.function.Consumer;

/**
 * The shared filter row of a branch view: a filter {@link ATextField} that
 * fires on every keystroke, plus expand-all / collapse-all glyph buttons
 * that act on the owning view's whole namespace tree. Used by
 * {@link BranchPickerPopover} (one row above its single list) and
 * {@link BranchManagerWidget} (one row feeding both columns — hence a single
 * filter and a single collapse set per window).
 * <p>
 * The row itself carries no background skin: the popup container or dialog
 * root behind it paints the surface, and the controls inside are
 * self-theming ({@link ATextField} / {@link AButton} bake their own skins).
 */
class BranchFilterRow extends HBox
{
	/** Width (px) of the expand/collapse-all buttons */
	private static final double TREE_BUTTON_WIDTH = 28;
	/** Horizontal gap (px) between the field and the buttons, and between the buttons */
	private static final double H_GAP = 4;

	/** The filter field (owned by this row) */
	private final ATextField FilterField;

	/**
	 * Build the row: field (growing) + {@code +} / {@code −} buttons pinned
	 * to a fixed width.
	 *
	 * @param _OnFilterChanged fired with the raw field text on every keystroke
	 * @param _OnExpandAll     fired by the {@code +} button
	 * @param _OnCollapseAll   fired by the {@code −} button
	 */
	BranchFilterRow(Consumer<String> _OnFilterChanged, Runnable _OnExpandAll, Runnable _OnCollapseAll)
	{
		super(H_GAP);

		FilterField = new ATextField();
		FilterField.setPromptText("Filter branches...");
		FilterField.textProperty().addListener((__Obs, __Old, __New) -> _OnFilterChanged.accept(__New));
		// The field must be allowed to shrink below its preferred width so the
		// two fixed-width buttons keep their size when the row narrows.
		FilterField.setMinWidth(0);
		HBox.setHgrow(FilterField, Priority.ALWAYS);

		AButton __BtnExpandAll = new AButton("+", EButtonVariant.GHOST);
		__BtnExpandAll.setTooltip(new Tooltip("Expand all namespaces"));
		__BtnExpandAll.setOnAction(__E -> _OnExpandAll.run());
		AButton __BtnCollapseAll = new AButton("\u2212", EButtonVariant.GHOST); // "−"
		__BtnCollapseAll.setTooltip(new Tooltip("Collapse all namespaces"));
		__BtnCollapseAll.setOnAction(__E -> _OnCollapseAll.run());
		// Pin min/pref/max to one width so both glyph buttons render at an
		// identical size regardless of each glyph's advance width (they would
		// otherwise be content-sized by their "+"/"−" text). The skin's 4/12
		// horizontal padding would leave only 4px inside a 28px button and
		// overrun-clip the glyph to nothing, so override the padding inline
		// to 4/6 (inline styles beat the skin stylesheet and survive
		// ApplySkin's stylesheet re-bake) — 16px of content room, enough for
		// "+" and "−" at the skin's 12px font.
		for (AButton __TreeButton : new AButton[] { __BtnExpandAll, __BtnCollapseAll })
		{
			__TreeButton.setMinWidth(TREE_BUTTON_WIDTH);
			__TreeButton.setPrefWidth(TREE_BUTTON_WIDTH);
			__TreeButton.setMaxWidth(TREE_BUTTON_WIDTH);
			__TreeButton.setStyle("-fx-padding: 4 6 4 6;");
		}

		getChildren().addAll(FilterField, __BtnExpandAll, __BtnCollapseAll);
	}

	/**
	 * @return the raw filter text (null-safe: "" for a null field value) —
	 *         normalization (trim/lower-case) happens in
	 *         {@link BranchTree#BuildRows}
	 */
	String GetFilterText()
	{
		String __Text = FilterField.getText();
		return __Text == null ? "" : __Text;
	}

	/** Move keyboard focus into the filter field (e.g. when the view opens). */
	void RequestFocus()
	{
		FilterField.requestFocus();
	}
}
