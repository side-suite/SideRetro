package fi.palonkorpi.sideretro.ui

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import fi.palonkorpi.sideretro.R
import fi.palonkorpi.sideretro.emu.GameSystem
import fi.palonkorpi.sideretro.input.Keytile
import fi.palonkorpi.sideretro.input.LogicalButton
import fi.palonkorpi.sideretro.settings.Faceplate
import fi.palonkorpi.sideretro.settings.Orientation
import fi.palonkorpi.sideretro.settings.Prefs
import fi.palonkorpi.sideretro.settings.ScalingMode
import fi.palonkorpi.sideretro.settings.ScreenFilter

/**
 * The paused-game menu. Main actions and game settings are separate pages so the player can resume
 * without scanning a settings dump, while every setting remains reachable with the same logical
 * inputs as gameplay. Android focus is deliberately not involved: a Keytile's physical "up" can be
 * a numpad key or a letter, and changes with rotation.
 */
class GameMenu(
    private val activity: GameActivity,
    private val container: ViewGroup,
    private val system: GameSystem,
    private val tile: Keytile,
    private val prefs: Prefs,
    private val hasQuickSave: Boolean,
    private val actions: GameActivity.MenuActions,
) {

    private enum class Page { MAIN, SETTINGS }

    private data class Item(
        val title: String,
        val icon: Int,
        val value: (() -> String)? = null,
        val invoke: () -> Unit,
        val reverse: (() -> Unit)? = null,
        val primary: Boolean = false,
    )

    private data class RowView(
        val item: Item,
        val root: LinearLayout,
        val title: TextView,
        val value: TextView?,
        val leading: ImageView,
        val trailing: ImageView?,
    )

    private var page = Page.MAIN
    private var selected = 0
    private val items = mutableListOf<Item>()
    private val rowViews = mutableListOf<RowView>()

    private lateinit var overlay: FrameLayout
    private lateinit var card: FrameLayout
    private lateinit var scroller: ScrollView

    fun show() {
        overlay = FrameLayout(activity).apply {
            setBackgroundColor(0xCC000000.toInt())
            // A tap outside the card returns to the game. The card itself is clickable so it absorbs
            // that listener instead of forwarding a tap through the paused game.
            setOnClickListener { actions.resume() }
        }
        card = FrameLayout(activity).apply {
            background = RetroUi.rounded(RetroUi.CARD, 16f, this)
            isClickable = true
            setOnClickListener { }
        }
        scroller = ScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        card.addView(scroller, FrameLayout.LayoutParams(MATCH, WRAP))
        overlay.addView(card, FrameLayout.LayoutParams(menuWidth(), WRAP, Gravity.CENTER))
        container.addView(overlay, ViewGroup.LayoutParams(MATCH, MATCH))
        renderPage()
    }

    fun dismiss() = container.removeView(overlay)

    /** Re-measure the card after an in-menu orientation change. */
    fun relayout() = renderPage()

    // ---------------------------------------------------------------------------------------
    // Pages
    // ---------------------------------------------------------------------------------------

    private fun renderPage() {
        buildItems()
        rowViews.clear()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
        }
        content.addView(pageHeader())

        if (page == Page.MAIN) {
            addMainItems(content)
        } else {
            addSettingsItems(content)
        }

        scroller.removeAllViews()
        scroller.addView(content, ViewGroup.LayoutParams(MATCH, WRAP))
        selected = selected.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        redraw()

        // Game menus appear in 640×480 landscape as well as portrait. Measure against the fixed
        // menu width and cap the card below the screen edges; ScrollView takes over only when needed.
        content.measure(
            View.MeasureSpec.makeMeasureSpec(menuWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val maxHeight = (container.height.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels) - dp(24f)
        card.layoutParams = (card.layoutParams as FrameLayout.LayoutParams).apply {
            height = minOf(content.measuredHeight, maxHeight.coerceAtLeast(dp(180f)))
        }
        card.requestLayout()
    }

    private fun pageHeader(): View = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(44f)
        if (page == Page.SETTINGS) {
            val back = RetroUi.icon(this, R.drawable.ic_retro_arrow_left, 24f, RetroUi.TEXT_PRIMARY).apply {
                setPadding(0, 0, dp(8f), 0)
            }
            addView(back)
            setOnClickListener { showMain() }
            isClickable = true
            contentDescription = "Back to game menu"
        }
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            addView(text(if (page == Page.MAIN) "Game menu" else "Game settings", 20f, RetroUi.TEXT_PRIMARY, Typeface.BOLD))
            addView(text(
                if (page == Page.MAIN) "Paused" else "Changes apply to this game now and future games.",
                12f,
                RetroUi.TEXT_SECONDARY,
            ).apply { setPadding(0, dp(3f), 0, 0) })
        })
    }

    private fun addMainItems(content: LinearLayout) {
        addItem(content, items[0])
        content.addView(spacer(8f))

        // Save and load are peers, not a tall sequence. This is what keeps the useful menu inside
        // the 480px landscape screen without shrinking targets below a comfortable size.
        val quick = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        addItem(quick, items[1], compact = true)
        if (hasQuickSave) {
            quick.addView(spacer(8f, horizontal = true))
            addItem(quick, items[2], compact = true)
        }
        content.addView(quick, LinearLayout.LayoutParams(MATCH, dp(48f)))
        content.addView(spacer(8f))

        var index = if (hasQuickSave) 3 else 2
        while (index < items.size) {
            addItem(content, items[index])
            index++
            if (index < items.size) content.addView(divider())
        }
    }

    private fun addSettingsItems(content: LinearLayout) {
        var lastSection: String? = null
        items.forEach { item ->
            val section = when (item.title) {
                "Screen orientation", "Picture size" -> "Picture"
                "Frame", "Filter" -> "Appearance"
                else -> "Controls"
            }
            if (section != lastSection) {
                if (lastSection != null) content.addView(spacer(10f))
                content.addView(text(section.uppercase(), 11f, RetroUi.TEXT_MUTED, Typeface.BOLD).apply {
                    letterSpacing = 0.12f
                    setPadding(dp(12f), dp(4f), dp(12f), dp(5f))
                })
                lastSection = section
            }
            addItem(content, item)
            if (item !== items.last()) content.addView(divider())
        }
    }

    private fun buildItems() {
        items.clear()
        if (page == Page.MAIN) {
            items += Item("Resume", R.drawable.ic_retro_play, invoke = actions::resume, primary = true)
            items += Item("Quick save", R.drawable.ic_retro_save, invoke = actions::quickSave)
            if (hasQuickSave) items += Item("Quick load", R.drawable.ic_retro_load, invoke = actions::quickLoad)
            items += Item("Restart game", R.drawable.ic_retro_restart, invoke = actions::restart)
            if (system.hasShoulders && tile == Keytile.SUNDIAL) {
                items += Item("Press Select", R.drawable.ic_retro_key, invoke = actions::pressSelect)
            }
            items += Item("Settings", R.drawable.ic_retro_folder_settings, invoke = ::showSettings)
            items += Item("Exit to library", R.drawable.ic_retro_arrow_left, invoke = actions::exitToLibrary)
        } else {
            items += Item(
                "Screen orientation", R.drawable.ic_retro_frame, { prefs.orientation.label },
                invoke = { actions.setOrientation(prefs.orientation.next()); redraw() },
                reverse = { actions.setOrientation(prefs.orientation.previous()); redraw() },
            )
            items += Item(
                "Picture size", R.drawable.ic_retro_gamepad, { prefs.scaling.label },
                invoke = { actions.setScaling(prefs.scaling.next()); redraw() },
                reverse = { actions.setScaling(prefs.scaling.previous()); redraw() },
            )
            items += Item(
                "Frame", R.drawable.ic_retro_frame, { prefs.faceplate.label },
                invoke = { actions.setFaceplate(prefs.faceplate.next()); redraw() },
                reverse = { actions.setFaceplate(prefs.faceplate.previous()); redraw() },
            )
            items += Item(
                "Filter", R.drawable.ic_retro_filter, { prefs.screenFilter.label },
                invoke = { actions.setFilter(prefs.screenFilter.next()); redraw() },
                reverse = { actions.setFilter(prefs.screenFilter.previous()); redraw() },
            )
            items += Item(
                "Rotated buttons", R.drawable.ic_retro_key,
                { if (prefs.positionalFaceButtons) "Positions" else "Labels" },
                invoke = { actions.setPositionalFaceButtons(!prefs.positionalFaceButtons); redraw() },
                reverse = { actions.setPositionalFaceButtons(!prefs.positionalFaceButtons); redraw() },
            )
            items += Item(
                "Control legend", R.drawable.ic_retro_gamepad,
                { if (prefs.legendEnabled) "On" else "Off" },
                invoke = { actions.setLegend(!prefs.legendEnabled); redraw() },
                reverse = { actions.setLegend(!prefs.legendEnabled); redraw() },
            )
        }
    }

    private fun addItem(parent: LinearLayout, item: Item, compact: Boolean = false) {
        val row = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(if (item.primary) 52f else 48f)
            setPadding(dp(if (compact) 10f else 12f), 0, dp(if (compact) 8f else 12f), 0)
            isClickable = true
            isFocusable = true
            contentDescription = item.title
        }
        val icon = RetroUi.icon(row, item.icon, 22f, if (item.primary) RetroUi.ACCENT_CONTENT else RetroUi.TEXT_SECONDARY)
        val label = text(item.title, if (compact) 14f else 16f, if (item.primary) RetroUi.ACCENT_CONTENT else RetroUi.TEXT_PRIMARY, Typeface.BOLD)
        val value = item.value?.let {
            text(it(), 12f, RetroUi.TEXT_SECONDARY, Typeface.BOLD).apply {
                background = RetroUi.rounded(RetroUi.CHIP, 8f, this)
                setPadding(dp(8f), dp(5f), dp(8f), dp(5f))
            }
        }
        row.addView(icon)
        row.addView(label, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(10f) })
        if (value != null) row.addView(value)
        val trailing = if (item.value != null) {
            RetroUi.icon(row, R.drawable.ic_retro_arrow_right, 18f, RetroUi.TEXT_MUTED).also {
                it.setPadding(dp(5f), 0, 0, 0)
                row.addView(it)
            }
        } else null

        val rowView = RowView(item, row, label, value, icon, trailing)
        row.setOnClickListener { item.invoke() }
        rowViews += rowView
        val params = if (compact) {
            LinearLayout.LayoutParams(0, MATCH, 1f)
        } else {
            LinearLayout.LayoutParams(MATCH, if (item.primary) dp(52f) else dp(48f))
        }
        parent.addView(row, params)
    }

    // ---------------------------------------------------------------------------------------
    // Selection and logical input
    // ---------------------------------------------------------------------------------------

    fun onButton(button: LogicalButton) {
        when (button) {
            LogicalButton.UP -> move(-1)
            LogicalButton.DOWN -> move(+1)
            LogicalButton.RIGHT, LogicalButton.FACE_A, LogicalButton.START -> items[selected].invoke()
            LogicalButton.LEFT -> items[selected].reverse?.invoke()
            LogicalButton.FACE_B, LogicalButton.MENU -> if (page == Page.SETTINGS) showMain() else actions.resume()
            else -> Unit
        }
    }

    private fun showSettings() {
        page = Page.SETTINGS
        selected = 0
        renderPage()
    }

    private fun showMain() {
        page = Page.MAIN
        selected = 0
        renderPage()
    }

    private fun move(delta: Int) {
        if (items.isEmpty()) return
        selected = (selected + delta + items.size) % items.size
        redraw()
    }

    /** Repaint live values and put the unambiguous amber focus ring on the selected target. */
    private fun redraw() {
        rowViews.forEachIndexed { index, row ->
            row.value?.text = row.item.value?.invoke()
            val isSelected = index == selected
            val color = when {
                row.item.primary && isSelected -> RetroUi.ACCENT_PRESSED
                row.item.primary -> RetroUi.ACCENT
                isSelected -> RetroUi.CHIP
                else -> RetroUi.CARD
            }
            row.root.background = RetroUi.rounded(color, if (row.item.primary) 14f else 10f, row.root)
            val foreground = if (row.item.primary) RetroUi.ACCENT_CONTENT else RetroUi.TEXT_PRIMARY
            row.title.setTextColor(foreground)
            RetroUi.tint(row.leading, if (row.item.primary) RetroUi.ACCENT_CONTENT else RetroUi.TEXT_SECONDARY)
            row.trailing?.let { RetroUi.tint(it, if (isSelected) RetroUi.ACCENT else RetroUi.TEXT_MUTED) }
            if (isSelected) {
                row.root.post {
                    row.root.requestRectangleOnScreen(android.graphics.Rect(0, 0, row.root.width, row.root.height), false)
                }
            }
        }
    }

    private fun divider() = View(activity).apply {
        setBackgroundColor(RetroUi.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1f)).apply { marginStart = dp(12f); marginEnd = dp(12f) }
    }

    private fun spacer(size: Float, horizontal: Boolean = false) = View(activity).apply {
        layoutParams = if (horizontal) LinearLayout.LayoutParams(dp(size), MATCH) else LinearLayout.LayoutParams(MATCH, dp(size))
    }

    private fun text(text: String, size: Float, color: Int, style: Int = Typeface.NORMAL) = TextView(activity).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        typeface = Typeface.create("sans-serif", style)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun menuWidth(): Int {
        val max = dp(360f)
        val available = activity.resources.displayMetrics.widthPixels - dp(24f)
        return minOf(max, available)
    }

    private fun dp(value: Float) = (value * activity.resources.displayMetrics.density).toInt()

    private fun Orientation.next() = Orientation.entries[(ordinal + 1) % Orientation.entries.size]
    private fun Orientation.previous() = Orientation.entries[(ordinal - 1 + Orientation.entries.size) % Orientation.entries.size]
    private fun ScalingMode.next() = ScalingMode.entries[(ordinal + 1) % ScalingMode.entries.size]
    private fun ScalingMode.previous() = ScalingMode.entries[(ordinal - 1 + ScalingMode.entries.size) % ScalingMode.entries.size]
    private fun Faceplate.next() = Faceplate.entries[(ordinal + 1) % Faceplate.entries.size]
    private fun Faceplate.previous() = Faceplate.entries[(ordinal - 1 + Faceplate.entries.size) % Faceplate.entries.size]
    private fun ScreenFilter.next() = ScreenFilter.entries[(ordinal + 1) % ScreenFilter.entries.size]
    private fun ScreenFilter.previous() = ScreenFilter.entries[(ordinal - 1 + ScreenFilter.entries.size) % ScreenFilter.entries.size]

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
