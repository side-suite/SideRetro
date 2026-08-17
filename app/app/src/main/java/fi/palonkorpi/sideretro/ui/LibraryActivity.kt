package fi.palonkorpi.sideretro.ui

import android.content.Intent
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import fi.palonkorpi.sideretro.R
import fi.palonkorpi.sideretro.library.DownloadsWatcher
import fi.palonkorpi.sideretro.library.Rom
import fi.palonkorpi.sideretro.library.RomLibrary
import fi.palonkorpi.sideretro.settings.Prefs
import fi.palonkorpi.sideretro.save.SaveStore

/**
 * SideRetro's library: a compact utility screen in the SideSuite family, rather than a generic
 * emulator browser. It deliberately has no cover art, catalogue, or network access.
 *
 * Navigation is kept explicit instead of delegating to Android focus search: a Keytile's numpad or
 * QWERTY cluster is not a d-pad as far as Android is concerned. Unlike the in-game menu, this page
 * must never consume the Sundial's media keys.
 */
class LibraryActivity : ComponentActivity() {

    private data class FocusTarget(
        val view: View,
        val activate: () -> Unit,
        val render: (Boolean) -> Unit,
    )

    private lateinit var library: RomLibrary
    private lateinit var downloads: DownloadsWatcher
    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout

    private var roms: List<Rom> = emptyList()
    private var selected = 0
    private val focusTargets = mutableListOf<FocusTarget>()
    private var rowActions: RowActions? = null
    private var deleteConfirmation: DeleteConfirmation? = null

    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> uris?.let(::importAll) }

    private val pickDownloadsFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            downloads.persist(treeUri)
            prefs.downloadsTree = treeUri.toString()
            offerDownloads()
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = RomLibrary(this)
        downloads = DownloadsWatcher(this)
        prefs = Prefs(this)

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(28f), dp(20f), dp(28f))
        }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(RetroUi.INK)
                isFillViewport = true
                clipToPadding = false
                addView(list, ViewGroup.LayoutParams(MATCH, WRAP))
            },
        )
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    override fun onResume() {
        super.onResume()
        offerDownloads()
        refresh()
    }

    // ---------------------------------------------------------------------------------------
    // Library screen
    // ---------------------------------------------------------------------------------------

    private fun refresh(selectionAfter: Int? = null) {
        roms = library.list()
        list.removeAllViews()
        focusTargets.clear()

        list.addView(appHeader())
        if (roms.isEmpty()) {
            list.addView(emptyState())
        } else {
            list.addView(gameCard())
            list.addView(spacer(16f))
        }

        list.addView(primaryAction(
            icon = R.drawable.ic_retro_folder_plus,
            label = "Add games",
            onClick = { pickFiles.launch(arrayOf("*/*")) },
        ))
        list.addView(spacer(10f))
        list.addView(secondaryAction(
            icon = R.drawable.ic_retro_folder_settings,
            label = if (grantedTree == null) "Watch a folder" else "Change watched folder",
            detail = if (grantedTree == null) {
                "Automatically add compatible games from a folder."
            } else {
                "Downloads folder connected"
            },
            onClick = { pickDownloadsFolder.launch(null) },
        ))

        selected = (selectionAfter ?: selected).coerceIn(0, (focusTargets.lastIndex).coerceAtLeast(0))
        redrawSelection()
    }

    private fun appHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4f), 0, dp(4f), dp(22f))
        addView(label("SideRetro", 24f, RetroUi.TEXT_PRIMARY, Typeface.BOLD))
        addView(label(
            if (roms.isEmpty()) "Your game library" else "${roms.size} ${if (roms.size == 1) "game" else "games"}",
            14f,
            RetroUi.TEXT_SECONDARY,
        ).apply { setPadding(0, dp(4f), 0, 0) })
    }

    private fun gameCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = RetroUi.rounded(RetroUi.CARD, 16f, this)
        setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
        roms.forEachIndexed { index, rom ->
            if (index > 0) addView(divider())
            addView(gameRow(rom))
        }
    }

    private fun gameRow(rom: Rom): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56f)
            setPadding(dp(12f), dp(8f), dp(10f), dp(8f))
            isClickable = true
            isFocusable = true
            contentDescription = "Play ${rom.title}"
            setOnLongClickListener {
                showRowActions(rom)
                true
            }
        }
        val detail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(12f) }
            addView(label(rom.title, 16f, RetroUi.TEXT_PRIMARY, Typeface.BOLD).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(systemBadge(rom.system.badge).apply {
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply {
                    topMargin = dp(4f)
                }
            })
        }
        row.addView(RetroUi.icon(row, R.drawable.ic_retro_arcade, 24f, RetroUi.TEXT_SECONDARY))
        row.addView(detail)
        row.addView(RetroUi.icon(row, R.drawable.ic_retro_play, 22f, RetroUi.TEXT_MUTED))
        addFocusTarget(row, { play(rom) }) { isSelected ->
            row.background = if (isSelected) RetroUi.rounded(RetroUi.ACCENT, 12f, row) else null
            val foreground = if (isSelected) RetroUi.ACCENT_CONTENT else RetroUi.TEXT_PRIMARY
            (detail.getChildAt(0) as TextView).setTextColor(foreground)
            RetroUi.tint(row.getChildAt(0) as ImageView, if (isSelected) RetroUi.ACCENT_CONTENT else RetroUi.TEXT_SECONDARY)
            RetroUi.tint(row.getChildAt(2) as ImageView, if (isSelected) RetroUi.ACCENT_CONTENT else RetroUi.TEXT_MUTED)
        }
        return row
    }

    /**
     * Destructive actions stay deliberately one step away from Play. A row has exactly one
     * navigation target: Right or a touch long-press opens this compact menu, then Confirm opens
     * the named destructive confirmation below.
     */
    private fun showRowActions(rom: Rom) {
        if (rowActions != null || deleteConfirmation != null) return
        val overlay = android.widget.FrameLayout(this).apply {
            isClickable = true
            contentDescription = "Dismiss game actions"
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = RetroUi.rounded(RetroUi.CARD, 14f, this)
            setPadding(dp(8f), dp(8f), dp(10f), dp(8f))
            elevation = dp(12f).toFloat()
            isClickable = true
        }
        val delete = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(44f))
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44f)
            setPadding(dp(10f), 0, dp(12f), 0)
            contentDescription = "Delete ${rom.title}"
            isClickable = true
            isFocusable = true
        }
        delete.addView(RetroUi.icon(delete, R.drawable.ic_retro_trash, 20f, RetroUi.DANGER))
        delete.addView(label("Delete game", 15f, RetroUi.TEXT_PRIMARY, Typeface.BOLD).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            setPadding(dp(10f), 0, 0, 0)
        })
        card.addView(delete)
        // Give the native view hierarchy a real width. On the SP-01 this overlay is attached to
        // DecorView (rather than a layout-owned parent); leaving both levels WRAP_CONTENT lets a
        // keypad-open menu occasionally measure as a one-pixel strip.
        overlay.addView(card, android.widget.FrameLayout.LayoutParams(dp(192f), WRAP, Gravity.CENTER))
        (window.decorView as ViewGroup).addView(overlay, ViewGroup.LayoutParams(MATCH, MATCH))

        lateinit var actions: RowActions
        fun dismiss() {
            (window.decorView as ViewGroup).removeView(overlay)
            rowActions = null
            redrawSelection()
        }
        fun redraw(isSelected: Boolean) {
            delete.background = if (isSelected) RetroUi.rounded(RetroUi.CHIP, 10f, delete) else null
            val colour = if (isSelected) RetroUi.DANGER_PRESSED else RetroUi.DANGER
            RetroUi.tint(delete.getChildAt(0) as ImageView, colour)
            (delete.getChildAt(1) as TextView).setTextColor(if (isSelected) RetroUi.TEXT_PRIMARY else RetroUi.TEXT_PRIMARY)
        }
        actions = RowActions(
            dismiss = ::dismiss,
            chooseDelete = {
                dismiss()
                showDeleteConfirmation(rom)
            },
            redraw = ::redraw,
        )
        rowActions = actions
        overlay.setOnClickListener { actions.dismiss() }
        card.setOnClickListener { }
        delete.setOnClickListener { actions.choose() }
        actions.redraw()
    }

    private class RowActions(
        private val dismiss: () -> Unit,
        private val chooseDelete: () -> Unit,
        private val redraw: (Boolean) -> Unit,
    ) {
        fun onKey(keyCode: Int): Boolean = when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_Q -> {
                dismiss(); true
            }
            in CHOOSE_KEYS -> { chooseDelete(); true }
            else -> false
        }

        fun dismiss() = dismiss.invoke()
        fun choose() = chooseDelete.invoke()
        fun redraw() = redraw(true)
    }

    /**
     * A small, app-owned confirmation surface instead of a platform AlertDialog.  That keeps the
     * destructive choice visually in the SideCall family and lets every Keytile cancel by default.
     */
    private fun showDeleteConfirmation(rom: Rom) {
        if (deleteConfirmation != null) return
        val overlay = android.widget.FrameLayout(this).apply {
            setBackgroundColor(0xC9000000.toInt())
            isClickable = true
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = RetroUi.rounded(RetroUi.CARD, 16f, this)
            setPadding(dp(20f), dp(20f), dp(20f), dp(16f))
            isClickable = true
        }
        card.addView(label("Delete game?", 21f, RetroUi.TEXT_PRIMARY, Typeface.BOLD))
        card.addView(label(
            "Remove ${rom.title} from SideRetro? Its save states and in-game save data will be removed too. The original download stays untouched.",
            14f,
            RetroUi.TEXT_SECONDARY,
        ).apply {
            setLineSpacing(dp(3f).toFloat(), 1f)
            setPadding(0, dp(10f), 0, dp(18f))
        })
        val buttons = LinearLayout(this).apply { gravity = Gravity.END }
        val cancel = confirmationButton("Cancel", primary = false)
        val remove = confirmationButton("Delete", primary = true)
        buttons.addView(cancel)
        buttons.addView(spacer(8f, horizontal = true))
        buttons.addView(remove)
        card.addView(buttons)
        overlay.addView(card, android.widget.FrameLayout.LayoutParams(dp(320f), WRAP, Gravity.CENTER))
        (window.decorView as ViewGroup).addView(overlay, ViewGroup.LayoutParams(MATCH, MATCH))

        lateinit var confirmation: DeleteConfirmation
        fun dismiss() {
            (window.decorView as ViewGroup).removeView(overlay)
            deleteConfirmation = null
        }
        fun redraw(which: Int) {
            cancel.background = RetroUi.rounded(if (which == 0) RetroUi.CHIP else RetroUi.CARD, 10f, cancel)
            cancel.setTextColor(RetroUi.TEXT_PRIMARY)
            remove.background = RetroUi.rounded(if (which == 1) RetroUi.DANGER_PRESSED else RetroUi.DANGER, 10f, remove)
            remove.setTextColor(RetroUi.INK)
        }
        fun confirm() {
            val index = roms.indexOfFirst { it.file == rom.file }
            if (library.delete(rom)) {
                SaveStore(this, rom).deleteAll()
                dismiss()
                // Keep the next game selected, or Add games when this was the final row, rather
                // than leaving focus on a view that has just vanished.
                refresh(LibraryNavigation.selectionAfterDelete(index, roms.size))
                toast("Deleted ${rom.title}")
            } else {
                dismiss()
                toast("Could not delete ${rom.title}")
            }
        }
        confirmation = DeleteConfirmation(
            dismiss = ::dismiss,
            confirm = ::confirm,
            redraw = ::redraw,
        )
        deleteConfirmation = confirmation
        overlay.setOnClickListener { confirmation.cancel() }
        card.setOnClickListener { }
        cancel.setOnClickListener { confirmation.cancel() }
        remove.setOnClickListener { confirmation.choose() }
        confirmation.redraw()
    }

    private fun confirmationButton(text: String, primary: Boolean): TextView = label(
        text,
        15f,
        if (primary) RetroUi.INK else RetroUi.TEXT_PRIMARY,
        Typeface.BOLD,
    ).apply {
        gravity = Gravity.CENTER
        minimumWidth = dp(86f)
        minimumHeight = dp(44f)
        setPadding(dp(14f), 0, dp(14f), 0)
        isClickable = true
        isFocusable = true
    }

    private class DeleteConfirmation(
        private val dismiss: () -> Unit,
        private val confirm: () -> Unit,
        private val redraw: (Int) -> Unit,
    ) {
        private var selected = 0 // Cancel is deliberately the default.

        fun onKey(keyCode: Int): Boolean = when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { cancel(); true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_Q -> { selected = 0; redraw(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_R -> { selected = 1; redraw(); true }
            in CHOOSE_KEYS -> { choose(); true }
            else -> false
        }

        fun cancel() = dismiss()
        fun choose() { if (selected == 0) dismiss() else confirm() }
        fun redraw() = redraw(selected)
    }

    private fun emptyState(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(20f), dp(24f), dp(20f), dp(30f))
        addView(RetroUi.icon(this, R.drawable.ic_retro_gamepad, 48f, RetroUi.ACCENT))
        addView(label("Your library is empty", 21f, RetroUi.TEXT_PRIMARY, Typeface.BOLD).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(18f), 0, 0)
        })
        addView(label(
            "Download a game in your browser and open it — SideRetro will add it. Or pick files you already have.",
            15f,
            RetroUi.TEXT_SECONDARY,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setLineSpacing(dp(3f).toFloat(), 1f)
            setPadding(0, dp(10f), 0, 0)
        })
        addView(label("Game Boy, Game Boy Color, Game Boy Advance, NES and Mega Drive.", 12f, RetroUi.TEXT_MUTED).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, dp(14f), 0, 0)
        })
        addView(spacer(14f))
    }

    private fun primaryAction(icon: Int, label: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            minimumHeight = dp(52f)
            background = RetroUi.rounded(RetroUi.ACCENT, 14f, this)
            isClickable = true
            isFocusable = true
            contentDescription = label
        }
        row.addView(RetroUi.icon(row, icon, 22f, RetroUi.ACCENT_CONTENT))
        row.addView(label(label, 16f, RetroUi.ACCENT_CONTENT, Typeface.BOLD).apply {
            setPadding(dp(10f), 0, 0, 0)
        })
        addFocusTarget(row, onClick) { isSelected ->
            row.background = RetroUi.rounded(if (isSelected) RetroUi.ACCENT_PRESSED else RetroUi.ACCENT, 14f, row)
        }
        return row
    }

    private fun secondaryAction(icon: Int, label: String, detail: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64f)
            setPadding(dp(14f), dp(10f), dp(12f), dp(10f))
            background = RetroUi.rounded(RetroUi.CARD, 14f, this)
            isClickable = true
            isFocusable = true
            contentDescription = label
        }
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(12f) }
            addView(label(label, 16f, RetroUi.TEXT_PRIMARY, Typeface.BOLD))
            addView(label(detail, 12f, RetroUi.TEXT_SECONDARY).apply { setPadding(0, dp(3f), 0, 0) })
        }
        row.addView(RetroUi.icon(row, icon, 24f, RetroUi.TEXT_SECONDARY))
        row.addView(copy)
        row.addView(RetroUi.icon(row, R.drawable.ic_retro_arrow_right, 22f, RetroUi.TEXT_MUTED))
        addFocusTarget(row, onClick) { isSelected ->
            row.background = RetroUi.rounded(if (isSelected) RetroUi.CHIP else RetroUi.CARD, 14f, row)
        }
        return row
    }

    private fun addFocusTarget(view: View, onClick: () -> Unit, render: (Boolean) -> Unit) {
        view.setOnClickListener { onClick() }
        focusTargets += FocusTarget(view, onClick, render)
    }

    private fun redrawSelection() {
        focusTargets.forEachIndexed { index, target ->
            val isSelected = index == selected
            target.render(isSelected)
            if (isSelected) {
                target.view.post {
                    target.view.requestRectangleOnScreen(Rect(0, 0, target.view.width, target.view.height), false)
                }
            }
        }
    }

    private fun systemBadge(text: String) = label(text, 11f, RetroUi.TEXT_SECONDARY, Typeface.BOLD).apply {
        background = RetroUi.rounded(RetroUi.CHIP, 8f, this)
        setPadding(dp(7f), dp(3f), dp(7f), dp(3f))
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(RetroUi.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1f)).apply { marginStart = dp(12f); marginEnd = dp(12f) }
    }

    private fun spacer(size: Float, horizontal: Boolean = false) = View(this).apply {
        layoutParams = if (horizontal) {
            LinearLayout.LayoutParams(dp(size), MATCH)
        } else {
            LinearLayout.LayoutParams(MATCH, dp(size))
        }
    }

    private fun label(text: String, size: Float, color: Int, style: Int = Typeface.NORMAL) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        typeface = Typeface.create("sans-serif", style)
    }

    // ---------------------------------------------------------------------------------------
    // Importing
    // ---------------------------------------------------------------------------------------

    private fun handleIncoming(incoming: Intent?) {
        val uri = when (incoming?.action) {
            Intent.ACTION_VIEW -> incoming.data
            Intent.ACTION_SEND -> incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        // Consumed: rotation/resume must not re-import the same file.
        setIntent(Intent(this, LibraryActivity::class.java))
        val imported = importAll(listOf(uri))
        if (imported.size == 1) play(imported.first())
    }

    private val grantedTree: Uri?
        get() = prefs.downloadsTree?.let(Uri::parse)?.takeIf { downloads.isGranted(it) }

    private fun offerDownloads() {
        val tree = grantedTree ?: return
        val candidates = downloads.unimported(tree, library)
        if (candidates.isEmpty()) return
        var imported = 0
        candidates.forEach { candidate -> if (downloads.import(candidate, library) != null) imported++ }
        if (imported > 0) {
            toast(if (imported == 1) "Added 1 game from Downloads" else "Added $imported games from Downloads")
        }
    }

    private fun importAll(uris: List<Uri>): List<Rom> {
        val imported = mutableListOf<Rom>()
        val rejections = mutableListOf<String>()
        var rejected = 0
        uris.forEach { uri ->
            val name = displayNameOf(uri)
            if (name == null || !library.isRecognised(name)) {
                rejected++
                rejections += "Not a game SideRetro can play"
                return@forEach
            }
            val result = try {
                contentResolver.openInputStream(uri)?.use { library.importResult(name, it) }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read $uri", e)
                null
            }
            when (result) {
                is RomLibrary.ImportResult.Imported -> imported += result.rom
                is RomLibrary.ImportResult.Rejected -> {
                    rejected++
                    rejections += result.message
                }
                null -> {
                    rejected++
                    rejections += "Could not read this game"
                }
            }
        }
        refresh()
        when {
            imported.isNotEmpty() && rejected == 0 -> toast("Added ${imported.size}")
            imported.isNotEmpty() -> toast("Added ${imported.size}, skipped $rejected")
            rejected > 0 -> toast(rejections.distinct().singleOrNull() ?: "Not a game SideRetro can play")
        }
        return imported
    }

    private fun displayNameOf(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun play(rom: Rom) = startActivity(GameActivity.intent(this, rom))

    // ---------------------------------------------------------------------------------------
    // Keytile navigation — deliberately no media keys on this non-game screen.
    // ---------------------------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        deleteConfirmation?.let { if (it.onKey(event.keyCode)) return true }
        rowActions?.let { if (it.onKey(event.keyCode)) return true }
        when (event.keyCode) {
            in UP_KEYS -> { moveSelection(-1); return true }
            in DOWN_KEYS -> { moveSelection(+1); return true }
            in RIGHT_KEYS -> {
                // The game rows occupy the first [roms.size] targets. Keep this deliberately
                // explicit: an inline Unit `let` here made the action easy to lose when Android
                // redispatched a hardware key through the focused row.
                val selectedRom = roms.getOrNull(selected)
                if (selectedRom != null) {
                    showRowActions(selectedRom)
                    return true
                }
            }
            in CHOOSE_KEYS -> {
                focusTargets.getOrNull(selected)?.activate?.invoke()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun moveSelection(delta: Int) {
        if (focusTargets.isEmpty()) return
        selected = (selected + delta + focusTargets.size) % focusTargets.size
        redrawSelection()
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "SideRetro"
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        val UP_KEYS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_E)
        val DOWN_KEYS = setOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_C)
        val RIGHT_KEYS = setOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_R)
        val CHOOSE_KEYS = setOf(
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_5,
            KeyEvent.KEYCODE_9,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_SPACE,
        )
    }
}
