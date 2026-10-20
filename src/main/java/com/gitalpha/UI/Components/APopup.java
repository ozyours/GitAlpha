package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import com.gitalpha.Type.EHideReason;
import com.gitalpha.Type.ThemeColor;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Reusable popup host: wraps a JavaFX {@link Popup} and centralises its
 * show/hide lifecycle so callers never touch the popup window itself.
 * <p>
 * The wrapped {@link Popup} is private — the only way in is
 * {@link #ShowAt(Node)}, and every way out funnels through one private
 * {@code RequestHide}. A single exit matters because a {@code Popup} is hidden
 * by three routes: an explicit {@link #Hide()} call, an outside click, and
 * Escape (both JavaFX auto-hide) — the last two toggled together by the
 * constructor's {@code _AutoHide} flag. All three converge on that funnel, so
 * an optional {@link #SetCanHide(Predicate) veto} and the single
 * {@link #SetOnHidden(Consumer) post-hide sink} apply uniformly to every
 * route. The sink receives an {@link EHideReason} so a host can tell an
 * app-requested hide from a user dismissal.
 * <p>
 * Content is supplied via {@link #SetContent(Node)} and hosted in a themed
 * container that draws the chrome (palette background + border, rounded).
 * Because a popup renders in its own scene, the chrome is baked as inlined hex
 * values — a node stylesheet cannot resolve the scene's {@code -gitalpha-*}
 * variables — and re-baked on palette switches through {@link IThemeChangeEvent}
 * (registered weakly with {@link ThemeManager}, so no unsubscribe is needed).
 * <p>
 * Threading: every member is FX-thread only; {@code ShowAt}, {@code Hide} and
 * the callbacks must be called on the FX thread.
 */
public class APopup implements IThemeChangeEvent
{
	/** Corner radius (px) of the themed container chrome */
	private static final double CONTAINER_RADIUS = 4;
	/** Vertical gap (px) between the anchor's bottom edge and the popup top */
	private static final double ANCHOR_GAP = 2;

	/** The wrapped popup window; never exposed so the funnel stays authoritative */
	private final Popup PopupInstance;
	/** Themed container that hosts the content and draws the chrome */
	private final StackPane Container;

	/** Post-hide sink, called once per hide with the reason (may be null) */
	private Consumer<EHideReason> OnHidden;
	/** Post-show hook, called after the popup becomes visible (may be null) */
	private Runnable OnShown;
	/** Optional veto: return false to refuse a hide of the given reason (may be null) */
	private Predicate<EHideReason> CanHide;

	/** True while a hide started here is in flight (blocks re-entry) */
	private boolean Hiding;
	/** True when the in-flight hide was started by {@link #Hide()} */
	private boolean HideInitiated;

	/**
	 * Create the popup host with the default behaviour: it dismisses itself on
	 * an outside click or Escape, and repositions itself to stay on screen.
	 * Equivalent to {@code APopup(true, true)}.
	 */
	public APopup()
	{
		this(true, true);
	}

	/**
	 * Create the popup host: build the themed container, wrap a {@link Popup},
	 * apply the given dismiss/positioning behaviour, wire the single post-hide
	 * sink and register as a theme listener. Supply content with
	 * {@link #SetContent(Node)} and display it with {@link #ShowAt(Node)}.
	 *
	 * @param _AutoHide     true to let the popup dismiss itself — an outside
	 *                      click and the Escape key both close it (this one flag
	 *                      drives JavaFX's {@code autoHide} and
	 *                      {@code hideOnEscape} together, since they are one
	 *                      user-facing "dismissable" concept); false leaves
	 *                      {@link #Hide()} as the only way out
	 * @param _KeepOnScreen true to reposition the popup when it would otherwise
	 *                      overflow a screen edge (JavaFX's {@code autoFix} —
	 *                      positioning only, it hides nothing); false to show at
	 *                      the exact computed coordinates
	 */
	public APopup(boolean _AutoHide, boolean _KeepOnScreen)
	{
		Container = new StackPane();
		ApplyContainerSkin(ThemeManager.Instance.GetPalette());

		PopupInstance = new Popup();
		// _AutoHide drives both dismiss routes (outside click + Escape) as one
		// user-facing concept; _KeepOnScreen is the positioning property
		// (JavaFX autoFix) and never hides the popup.
		PopupInstance.setAutoHide(_AutoHide);
		PopupInstance.setHideOnEscape(_AutoHide);
		PopupInstance.setAutoFix(_KeepOnScreen);
		PopupInstance.getContent().add(Container);

		// Auto-dismiss (outside click / Escape) routes through the same funnel
		// as an explicit Hide(): the event is consumed so JavaFX's own hide
		// stands down and RequestHide decides (this is what lets CanHide veto a
		// dismissal). Should JavaFX hide anyway, the post-hide sink still reports
		// DISMISSED through the HideInitiated flag.
		PopupInstance.setOnAutoHide(__E ->
		{
			__E.consume();
			RequestHide(EHideReason.DISMISSED);
		});

		// Single post-hide sink: every route lands here exactly once, so the
		// caller's cleanup runs regardless of how the popup closed.
		PopupInstance.setOnHidden(__E ->
		{
			EHideReason __Reason = HideInitiated ? EHideReason.PROGRAMMATIC : EHideReason.DISMISSED;
			HideInitiated = false;
			Hiding = false;
			if (OnHidden != null)
				OnHidden.accept(__Reason);
		});

		ThemeManager.Instance.AddIThemeChangeEvent(this);
	}

	// ------------------------------------------------------------------
	//  Content
	// ------------------------------------------------------------------

	/**
	 * Set (or replace) the popup's content node. The node is hosted in the
	 * themed container, which sizes itself to the content.
	 *
	 * @param _Content the content node to display (non-null)
	 */
	public void SetContent(Node _Content)
	{
		Container.getChildren().setAll(_Content);
	}

	// ------------------------------------------------------------------
	//  Show / hide
	// ------------------------------------------------------------------

	/**
	 * Show the popup directly below the given anchor node, left-aligned with
	 * it. No-op when the anchor is not yet in a showing window
	 * ({@code localToScreen} returns null). Fires the {@link #SetOnShown(Runnable)}
	 * hook afterwards, on the FX thread.
	 *
	 * @param _Anchor the node to anchor below
	 */
	public void ShowAt(Node _Anchor)
	{
		Point2D __TopLeft = _Anchor.localToScreen(0, 0);
		if (__TopLeft == null)
			return;
		PopupInstance.show(_Anchor.getScene().getWindow(),
				__TopLeft.getX(),
				__TopLeft.getY() + _Anchor.getBoundsInLocal().getHeight() + ANCHOR_GAP);
		if (OnShown != null)
			OnShown.run();
	}

	/**
	 * Hide the popup programmatically. No-op when already hidden. Equivalent to
	 * a user dismissal except that the post-hide sink receives
	 * {@link EHideReason#PROGRAMMATIC}.
	 */
	public void Hide()
	{
		RequestHide(EHideReason.PROGRAMMATIC);
	}

	/** @return true when the popup is currently visible */
	public boolean IsShowing()
	{
		return PopupInstance.isShowing();
	}

	/**
	 * The one funnel every hide route goes through. Idempotent (no-op unless
	 * showing) and re-entrancy safe, so a hide cannot be started while another
	 * is in flight. Consults {@link #SetCanHide(Predicate)} before hiding;
	 * a refused hide leaves the popup visible.
	 *
	 * @param _Reason the route requesting the hide; consulted by the veto
	 *                predicate and used to tag the post-hide sink's reason
	 */
	private void RequestHide(EHideReason _Reason)
	{
		if (!PopupInstance.isShowing() || Hiding)
			return;
		if (CanHide != null && !CanHide.test(_Reason))
			return;
		Hiding = true;
		HideInitiated = (_Reason == EHideReason.PROGRAMMATIC);
		PopupInstance.hide(); // triggers the onHidden sink wired above
	}

	// ------------------------------------------------------------------
	//  Callbacks
	// ------------------------------------------------------------------

	/**
	 * Set the post-hide sink, called once on every hide regardless of the
	 * route (explicit {@link #Hide()}, outside click, or Escape) with the
	 * reason it hid.
	 *
	 * @param _Handler the handler to run on hide (nullable)
	 */
	public void SetOnHidden(Consumer<EHideReason> _Handler)
	{
		OnHidden = _Handler;
	}

	/**
	 * Set the post-show hook, called after the popup becomes visible (e.g. to
	 * move focus into the content).
	 *
	 * @param _Handler the action to run on show (nullable)
	 */
	public void SetOnShown(Runnable _Handler)
	{
		OnShown = _Handler;
	}

	/**
	 * Set a veto predicate consulted before every hide. Returning false
	 * refuses the hide (the popup stays visible); used to block a dismissal
	 * while an interaction is still in progress.
	 *
	 * @param _Predicate decides per hide reason whether the hide may proceed
	 *                   (nullable to allow every hide)
	 */
	public void SetCanHide(Predicate<EHideReason> _Predicate)
	{
		CanHide = _Predicate;
	}

	// ------------------------------------------------------------------
	//  Theme
	// ------------------------------------------------------------------

	/**
	 * Theme-change push: re-bake the container chrome with the new palette's
	 * colors. Content controls re-bake their own skins independently.
	 *
	 * @param _Palette the palette to read colors from
	 */
	@Override
	public void Event(ColorPalette _Palette)
	{
		ApplyContainerSkin(_Palette);
	}

	/**
	 * Apply the palette-derived background and border to the container. Inlined
	 * hex values because the popup runs in its own scene, where the scene-level
	 * {@code -gitalpha-*} variables do not resolve. Protected so a subclass can
	 * extend the chrome.
	 *
	 * @param _Palette the palette to read colors from
	 */
	protected void ApplyContainerSkin(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		String __Bg = _Palette.GetBackgroundColor().GetHex(__Lookup);
		String __Border = _Palette.GetBorderColor().GetHex(__Lookup);
		Container.setStyle(
				"-fx-background-color: " + __Bg + ";"
				+ "-fx-background-insets: 0;"
				+ "-fx-background-radius: " + CONTAINER_RADIUS + ";"
				+ "-fx-border-color: " + __Border + ";"
				+ "-fx-border-width: 1;"
				+ "-fx-border-radius: " + CONTAINER_RADIUS + ";");
	}
}
