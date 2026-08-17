package fi.palonkorpi.sideretro.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.Surface
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.ViewportAlignment
import fi.palonkorpi.sideretro.emu.GameSystem
import fi.palonkorpi.sideretro.emu.Scaling
import fi.palonkorpi.sideretro.input.InputRouter
import fi.palonkorpi.sideretro.input.Keytile
import fi.palonkorpi.sideretro.input.TileInference
import fi.palonkorpi.sideretro.library.Rom
import fi.palonkorpi.sideretro.library.RomLibrary
import fi.palonkorpi.sideretro.save.SaveStore
import fi.palonkorpi.sideretro.settings.Faceplate
import fi.palonkorpi.sideretro.settings.Orientation
import fi.palonkorpi.sideretro.settings.Prefs
import fi.palonkorpi.sideretro.settings.ScalingMode
import fi.palonkorpi.sideretro.settings.ScreenFilter
import kotlinx.coroutines.launch
import java.io.File

/**
 * The game. Starts and gets out of the way.
 *
 * Everything visible here is downstream of one fact: the SP-01's front keypad is swappable and its
 * identity is undetectable at runtime. That is why there is no keymap screen, no controller setup,
 * and why the legend exists at all.
 */
class GameActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var rom: Rom
    private lateinit var system: GameSystem
    private lateinit var saves: SaveStore
    private lateinit var router: InputRouter

    private var retroView: GLRetroView? = null
    private lateinit var gameArea: FrameLayout
    private lateinit var legendView: LegendView
    private lateinit var faceplateView: FaceplateView
    /** The transformed picture bounds, shared by the faceplate and the legend exclusion rule. */
    private var pictureRect = LegendLayout.Rect(0, 0, 0, 0)

    /**
     * Everything sits inside this, the legend and the menu included. It is deliberately a
     * FrameLayout: a MATCH_PARENT child of a vertical LinearLayout is a *row*, which once squeezed
     * the game to nothing and shoved the legend to the top of the screen.
     */
    private lateinit var root: FrameLayout
    private var menu: GameMenu? = null

    private var stateRestored = false
    private var restoreAttempts = 0

    // Touch gesture state (§5.2). GLRetroView consumes touch itself, so this is all driven from
    // dispatchTouchEvent on the Activity — a listener on the parent never fires.
    private var touchDownAt = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var longPressFired = false
    private val longPressRunnable = Runnable {
        longPressFired = true
        openMenu()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val path = intent.getStringExtra(EXTRA_ROM_PATH)
        val file = path?.let { File(it) }
        if (file == null || !file.isFile) {
            Log.e(TAG, "No playable ROM at $path")
            finish()
            return
        }
        val resolved = GameSystem.forExtension(file.extension)
        if (resolved == null) {
            Log.e(TAG, "Unsupported extension: ${file.name}")
            finish()
            return
        }
        system = resolved
        rom = RomLibrary(this).list().firstOrNull { it.file == file }
            ?: Rom(file, system, file.nameWithoutExtension)
        saves = SaveStore(this, rom)

        requestedOrientation = prefs.orientation.activityInfoValue
        router = InputRouter(system, TileInference()).apply {
            positionalFaceButtons = prefs.positionalFaceButtons
            onMenu = { openMenu() }
            onTileChanged = { refreshLegend() }
        }

        buildViews()
        startEmulation()
    }

    // ---------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------

    private fun buildViews() {
        // Transparent, not black: the faceplate is behind it, and an opaque game area would hide it.
        // The root is black, so with the faceplate off nothing changes.
        gameArea = FrameLayout(this)

        faceplateView = FaceplateView(this).apply {
            system = this@GameActivity.system
            mode = prefs.faceplate
        }
        legendView = LegendView(this)
        // The legend is printed on the same visual plane as the mark.  FaceplateView owns this
        // token because Lit's brightest usable frame hue must be derived exactly once.
        faceplateView.onMarkInkChanged = { legendView.inkTint = it }

        // The legend is a compact reference at the corner of the playfield, not a second control
        // plane. It stays clear of the faceplate wordmark and remains available in both rotations.
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            // Behind the game (§5.3). GLRetroView is a SurfaceView and punches a hole in the window
            // wherever it lands, so everything the faceplate draws inside the picture is hidden for
            // free — which is what lets the well and the glow be drawn as whole shapes.
            addView(faceplateView, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(gameArea, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(
                legendView,
                FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.BOTTOM),
            )
        }
        setContentView(root)

        // §4.1: immersive fullscreen is load-bearing, not a preference. With the status bar visible
        // landscape is 640×456, which is not 4:3, and the exact NES/Genesis fill collapses. Stated
        // here in code rather than left resting on a theme attribute.
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        gameArea.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyScaling() }
        refreshLegend()
    }

    private val isLandscape: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** These drawings carry enough real printing that the generic miniature floor is too small. */
    private val hasDenseLegend: Boolean
        get() = when (router.tile) {
            Keytile.COMPACT_QWERTY, Keytile.NUMPAD -> true
            else -> false
        }

    private fun legendReadableMinDp(): Int =
        if (hasDenseLegend) DENSE_LEGEND_READABLE_MIN_DP else LEGEND_READABLE_MIN_DP

    /**
     * SPEC.md §4.3. The picture's size comes from [Scaling], which is where the reasoning about the
     * renderer's letterboxing lives. Portrait faceplates use an optical centre: a little more of the
     * spare space sits below the picture for the mark and legend, without making the picture appear
     * pinned to the physical top edge of the SP-01.
     */
    private fun applyScaling() {
        val view = retroView ?: return
        // The game retains the selected scaling dimensions. A dense tile earns extra room by
        // moving that unchanged picture upward in portrait, never by silently shrinking it.
        val layout = Scaling.compute(system, prefs.scaling, gameArea.width, gameArea.height)
        if (layout.viewWidth <= 0 || layout.viewHeight <= 0) return

        // The picture's *displayed* size: the view's box after the aspect-correction transform,
        // which scales about the box's centre rather than its top-left.
        val shownWidth = layout.viewWidth * layout.scaleX
        val shownHeight = layout.viewHeight * layout.scaleY

        // §4.2, refined. Off and landscape are mathematically centred. In portrait, Console and Lit
        // reserve a modestly larger lower band (57% of spare height) for the faceplate mark and
        // bottom-right legend. Dense keyboard/keypad drawings have a complementary lower-left
        // mark. They keep the selected game size, but use an aspect-aware optical placement rather
        // than the former fixed 16dp top margin: handhelds retain their normal centre, while tall
        // console frames trade only the surplus needed to keep their lower map readable.
        // Fill has no spare height, so it remains edge-to-edge.
        val leftover = (gameArea.height - shownHeight).coerceAtLeast(0f)
        val gapTop = when {
            prefs.scaling == ScalingMode.FILL -> 0f
            prefs.legendEnabled && hasDenseLegend && !isLandscape ->
                PortraitGamePlacement.denseLegendTop(
                    rootHeightPx = gameArea.height,
                    pictureHeightPx = shownHeight,
                    density = resources.displayMetrics.density,
                    opticalTopFraction = PORTRAIT_FACEPLATE_TOP_FRACTION,
                    readableLegendDp = DENSE_LEGEND_READABLE_MIN_DP,
                    legendEdgeDp = LEGEND_EDGE_DP,
                    legendGapDp = LEGEND_GAP_DP,
                    visualTopFloorDp = PORTRAIT_DENSE_VISUAL_TOP_FLOOR_DP,
                )
            prefs.faceplate == Faceplate.OFF || isLandscape -> leftover / 2f
            else -> leftover * PORTRAIT_FACEPLATE_TOP_FRACTION
        }

        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
        params.width = layout.viewWidth
        params.height = layout.viewHeight
        params.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        params.topMargin = (gapTop + shownHeight / 2f - layout.viewHeight / 2f).toInt()
        view.layoutParams = params

        view.scaleX = layout.scaleX
        view.scaleY = layout.scaleY

        val centreX = gameArea.width / 2f
        faceplateView.setGameRect(
            centreX - shownWidth / 2f,
            gapTop,
            centreX + shownWidth / 2f,
            gapTop + shownHeight,
        )
        pictureRect = LegendLayout.Rect(
            kotlin.math.floor(centreX - shownWidth / 2f).toInt(),
            kotlin.math.floor(gapTop).toInt(),
            kotlin.math.ceil(centreX + shownWidth / 2f).toInt(),
            kotlin.math.ceil(gapTop + shownHeight).toInt(),
        )
        positionLegend()
    }

    private fun refreshLegend() {
        router.displayRotation = display?.rotation ?: Surface.ROTATION_0

        // §5.1: the reference must be there whenever the user asks for it.  A tap still peeks it
        // temporarily when disabled, but rotation and a full-height picture never suppress it.
        val showPersistently = prefs.legendEnabled
        // INVISIBLE still gets measured.  We need its real bounds before deciding whether there is
        // an empty faceplate region for it; a GONE view has no size and was the source of the
        // legend being blindly anchored over full-width console pictures.
        legendView.visibility = if (showPersistently || peeking) View.INVISIBLE else View.GONE

        legendView.landscape = isLandscape
        val art = Legend.artFor(router.tile)
        faceplateView.useLowerPortraitMark = prefs.legendEnabled && hasDenseLegend && !isLandscape
        legendView.art = art
        legendView.labels = art?.let {
            Legend.labels(it, system, router.tile, isLandscape, prefs.positionalFaceButtons)
        } ?: emptyMap()
        legendView.placeholder = if (art != null) null else {
            Legend.fallbackLine(router.tile, system, isLandscape, prefs.positionalFaceButtons)
                ?: Legend.UNKNOWN_TILE_HINT
        }
        // Tile inference can replace a sparse Mini/unknown reference with a dense keyboard after
        // the game has already been sized. Re-run the calculation so the new readable floor is
        // reflected in the picture rectangle before placing the legend.
        if (retroView != null) applyScaling() else positionLegend()
    }

    /** A small, frameless reference in a genuinely empty faceplate region — never on the picture. */
    private fun positionLegend() {
        if (!::root.isInitialized) return
        root.post {
            if (root.width == 0 || root.height == 0) return@post
            val params = legendView.layoutParams as? FrameLayout.LayoutParams ?: return@post
            val showPersistently = prefs.legendEnabled
            if (!showPersistently && !peeking) {
                legendView.visibility = View.GONE
                return@post
            }

            // LegendView's art reference is square; its fallback is deliberately shallow text.
            // Use its requested dimensions rather than the old fixed corner assumption.  The
            // placer continuously scales the whole reference down when a large portrait game
            // leaves only a shallow lower faceplate band; it must not disappear merely because
            // that band is under the old 80% floor.
            val desiredHeight = legendView.dockHeight
            val desiredWidth = if (legendView.art != null) desiredHeight else dp(164)
            val systemInsets = root.rootWindowInsets
                ?.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            val readableMinScale = (dp(legendReadableMinDp()).toFloat() /
                minOf(desiredWidth, desiredHeight).toFloat()).coerceIn(0f, 1f)
            val placement = if (legendView.art == null) {
                // Until a physical key identifies the tile, this is an instruction rather than a
                // miniature.  Centre it below the actual game picture, including for a narrow GB
                // or GBA frame, so it reads as a quiet prompt and not a misplaced corner legend.
                LegendLayout.placeBelowPicture(
                    rootWidth = root.width,
                    rootHeight = root.height,
                    picture = pictureRect,
                    desiredWidth = desiredWidth,
                    desiredHeight = desiredHeight,
                    edge = dp(LEGEND_EDGE_DP),
                    gap = dp(LEGEND_GAP_DP),
                    minScale = readableMinScale,
                    safeLeft = systemInsets?.left ?: 0,
                    safeTop = systemInsets?.top ?: 0,
                    safeRight = systemInsets?.right ?: 0,
                    safeBottom = systemInsets?.bottom ?: 0,
                )
            } else {
                LegendLayout.place(
                    rootWidth = root.width,
                    rootHeight = root.height,
                    picture = pictureRect,
                    desiredWidth = desiredWidth,
                    desiredHeight = desiredHeight,
                    edge = dp(LEGEND_EDGE_DP),
                    gap = dp(LEGEND_GAP_DP),
                    minScale = readableMinScale,
                    safeLeft = systemInsets?.left ?: 0,
                    safeTop = systemInsets?.top ?: 0,
                    safeRight = systemInsets?.right ?: 0,
                    safeBottom = systemInsets?.bottom ?: 0,
                )
            }
            if (placement == null) {
                // There is no external faceplate at all (normally Fill). A tap still retries
                // placement after any layout change, but it must never buy visibility by covering
                // play.
                legendView.visibility = View.GONE
                return@post
            }

            // Scale about the lower-right corner so the calculated visual bounds, not the larger
            // unscaled View box, sit exactly in the free rectangle.
            legendView.pivotX = desiredWidth.toFloat()
            legendView.pivotY = desiredHeight.toFloat()
            legendView.scaleX = placement.scale
            legendView.scaleY = placement.scale
            params.gravity = Gravity.TOP or Gravity.START
            params.leftMargin = placement.left + placement.width - desiredWidth
            params.topMargin = placement.top + placement.height - desiredHeight
            params.rightMargin = 0
            params.bottomMargin = 0
            legendView.layoutParams = params
            legendView.visibility = View.VISIBLE
        }
    }

    // ---------------------------------------------------------------------------------------
    // The lit faceplate's colour (§5.3)
    // ---------------------------------------------------------------------------------------

    private var glowSample: Bitmap? = null
    private var glowSmoothed: Int? = null
    /**
     * Kept separately from Lifecycle.State: ComponentActivity reports RESUMED *after* its
     * post-resume callback on this Android build.  Using the Lifecycle state as the gate made a
     * persisted Lit selection miss its only startup call to [updateGlowSampling]; changing the
     * setting later happened after that transition and therefore appeared to repair it.
     */
    private var glowHostResumed = false
    private val glowSampler = object : Runnable {
        override fun run() {
            sampleGlow()
            root.postDelayed(this, GLOW_SAMPLE_MS)
        }
    }

    private fun updateGlowSampling() {
        // onResume runs even when onCreate bailed out on an unplayable ROM, and the views are only
        // built on the path that survives.
        if (!::root.isInitialized) return
        root.removeCallbacks(glowSampler)
        if (prefs.faceplate == Faceplate.LIT && glowHostResumed) {
            // Give the core one presented frame before the first readback.  The runnable continues
            // at its normal cadence after this, so a slow core is still picked up without relying
            // on a settings change to restart sampling.
            root.postDelayed(glowSampler, GLOW_STARTUP_DELAY_MS)
        }
    }

    /**
     * Reads the frame back off the SurfaceView and averages it.
     *
     * `PixelCopy` is the only route: the core's framebuffer never passes through us, and
     * LibretroDroid exposes no capture. It scales the whole surface into a tiny bitmap, so the
     * averaging is over [GLOW_SAMPLE_PX]² pixels and the GPU does the reduction — sampling four
     * times a second, not per frame, because the ambient colour of a scene does not change faster
     * than that and a readback is a pipeline stall.
     */
    private fun sampleGlow() {
        val view = retroView ?: return
        if (view.width <= 0 || view.height <= 0 || !view.holder.surface.isValid) return
        val bitmap = glowSample
            ?: Bitmap.createBitmap(GLOW_SAMPLE_PX, GLOW_SAMPLE_PX, Bitmap.Config.ARGB_8888)
                .also { glowSample = it }

        PixelCopy.request(view, bitmap, { status ->
            if (status == PixelCopy.SUCCESS) applyGlow(bitmap)
        }, root.handler ?: return)
    }

    private fun applyGlow(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var r = 0L
        var g = 0L
        var b = 0L
        pixels.forEach {
            r += (it shr 16) and 0xFF
            g += (it shr 8) and 0xFF
            b += it and 0xFF
        }
        val n = pixels.size
        r /= n; g /= n; b /= n

        // Brightened, not normalised: a scene's average is always muddy, and the glow should be the
        // colour of the light rather than the colour of the picture. Capped so a nearly-black scene
        // throws almost no light — which is the physical answer, and the pretty one.
        val peak = maxOf(r, g, b)
        val boost = if (peak <= 0) 1f else (255f / peak).coerceIn(1f, 3f)
        val target = Color.rgb(
            (r * boost).toInt().coerceAtMost(255),
            (g * boost).toInt().coerceAtMost(255),
            (b * boost).toInt().coerceAtMost(255),
        )

        // Eased, so a flash or a scene cut fades the room rather than strobing it.
        val previous = glowSmoothed ?: target
        val mixed = Color.rgb(
            lerp(Color.red(previous), Color.red(target)),
            lerp(Color.green(previous), Color.green(target)),
            lerp(Color.blue(previous), Color.blue(target)),
        )
        glowSmoothed = mixed
        faceplateView.glowTint = mixed
    }

    private fun lerp(from: Int, to: Int) = (from + (to - from) * GLOW_EASE).toInt()

    private var peeking = false
    private val stopPeeking = Runnable {
        peeking = false
        refreshLegend()
    }

    /** §5.2: a tap peeks the legend. In portrait with the band already on, it is a no-op. */
    private fun peekLegend() {
        peeking = true
        refreshLegend()
        legendView.removeCallbacks(stopPeeking)
        legendView.postDelayed(stopPeeking, LEGEND_PEEK_MS)
    }

    // ---------------------------------------------------------------------------------------
    // Emulation
    // ---------------------------------------------------------------------------------------

    private fun startEmulation() {
        val corePath = File(applicationInfo.nativeLibraryDir, "lib${system.coreName}_libretro_android.so")
        if (!corePath.isFile) {
            Log.e(TAG, "Core missing: $corePath")
            finish()
            return
        }

        val data = GLRetroViewData(this).apply {
            coreFilePath = corePath.absolutePath
            gameFilePath = rom.file.absolutePath
            systemDirectory = filesDir.absolutePath
            savesDirectory = filesDir.absolutePath
            viewportAlignment = ViewportAlignment.CENTER
            shader = shaderFor(prefs.screenFilter)
            preferLowLatencyAudio = true
            // Off deliberately: it suppresses presentation of unchanged frames, which makes any
            // frame counter measure *unique* frames rather than emulated fps. It showed no latency
            // benefit and it obscured the SameBoy problem for a day.
            skipDuplicateFrames = false
            // §6: SaveRAM is handed over at construction so battery-backed in-game saves survive a
            // call arriving mid-session, independently of the emulator state.
            saveRAMState = saves.readSaveRam()
        }

        val view = GLRetroView(this, data)
        view.keepScreenOn = true
        lifecycle.addObserver(view)
        retroView = view

        gameArea.addView(
            view,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER),
        )
        applyScaling()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                view.getGLRetroEvents().collect { event ->
                    if (event is GLRetroView.GLRetroEvents.FrameRendered) restoreResumePoint()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                view.getGLRetroErrors().collect { code -> Log.e(TAG, "LibretroDroid error $code") }
            }
        }
    }

    /**
     * §6 — invisible auto-resume.
     *
     * Retried across the first frames rather than attempted once. Measured on the device: the first
     * `FrameRendered` arrives before the core will accept a state, and a single attempt there fails
     * silently — the game simply starts at its title screen, which looks like the feature was never
     * built rather than like a bug.
     */
    private fun restoreResumePoint() {
        if (stateRestored) return
        val state = saves.readResume() ?: run { stateRestored = true; return }

        restoreAttempts++
        if (retroView?.unserializeState(state) == true) {
            stateRestored = true
            Log.i(TAG, "Resumed ${rom.title} after $restoreAttempts frame(s)")
        } else if (restoreAttempts >= MAX_RESTORE_ATTEMPTS) {
            stateRestored = true
            Log.w(TAG, "Could not resume ${rom.title}; starting fresh")
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        // Do not use lifecycle.currentState here. On the SP-01's ComponentActivity implementation
        // it is still STARTED during this callback, which previously prevented a cold-launched Lit
        // faceplate from ever scheduling its sampler. This callback itself is the authoritative
        // signal that the host is ready to receive callbacks.
        glowHostResumed = true
        updateGlowSampling()
    }

    override fun onPause() {
        super.onPause()
        glowHostResumed = false
        if (::root.isInitialized) root.removeCallbacks(glowSampler)
        val view = retroView ?: return
        if (!stateRestored) return
        // Both serialisations, every time. See SaveStore for why only doing one loses in-game saves.
        saves.writeResume(view.serializeState(), view.serializeSRAM())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        router.displayRotation = display?.rotation ?: Surface.ROTATION_0
        applyScaling()
        refreshLegend()
        // An orientation setting can be changed while the menu is open. Re-measure after the new
        // window dimensions have landed so the card keeps its landscape height cap and scrolls.
        root.post { menu?.relayout() }
    }

    // ---------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        menu?.let { open ->
            // While the menu is up the tile drives the menu, not the game. Nothing reaches the core.
            // This is intentionally *not* gameplay resolution: menus follow the controls printed
            // on the tile, so A confirms, B goes back, and a named d-pad direction stays that
            // direction even when the picture is rotated or Positions is enabled.
            val logical = router.resolveForMenu(event) ?: return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_DOWN) open.onButton(logical)
            return true
        }
        // The legend lights up under the thumb. Fed the raw keycode rather than the logical button,
        // because what should light is the key the finger is on — the rotation transform must not
        // move the highlight to a different physical button than the one being held.
        if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
            legendView.setPressed(
                KeyEvent.keyCodeToString(event.keyCode),
                event.action == KeyEvent.ACTION_DOWN,
            )
        }

        if (router.dispatch(event, retroView)) return true
        return super.dispatchKeyEvent(event)
    }

    /**
     * §5.2 — long-press opens the menu, tap peeks the legend, no swipes.
     *
     * There is no button to give the menu: GBA needs ten inputs and the Mini Controller has exactly
     * ten, while all nine Sundial keys are bound under GBA. Touch is unusually cheap here because
     * none of the five systems has pointer input, so the core never wants a touch event and we can
     * claim all of them unambiguously.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (menu != null) return super.dispatchTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownAt = SystemClock.uptimeMillis()
                touchDownX = event.x
                touchDownY = event.y
                longPressFired = false
                root.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }

            MotionEvent.ACTION_MOVE -> {
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (kotlin.math.abs(event.x - touchDownX) > slop ||
                    kotlin.math.abs(event.y - touchDownY) > slop
                ) {
                    root.removeCallbacks(longPressRunnable)
                }
            }

            MotionEvent.ACTION_UP -> {
                root.removeCallbacks(longPressRunnable)
                if (!longPressFired) peekLegend()
            }

            MotionEvent.ACTION_CANCEL -> root.removeCallbacks(longPressRunnable)
        }
        return true
    }

    // ---------------------------------------------------------------------------------------
    // Menu
    // ---------------------------------------------------------------------------------------

    private fun openMenu() {
        if (menu != null) return
        val view = retroView

        // Freeze the game rather than let it run behind the menu — the whole reason the menu exists
        // is to be reachable mid-boss-fight.
        view?.frameSpeed = 0
        view?.audioEnabled = false

        menu = GameMenu(
            activity = this,
            container = root,
            system = system,
            tile = router.tile,
            prefs = prefs,
            hasQuickSave = saves.hasQuickSave,
            actions = MenuActions(),
        ).also { it.show() }
    }

    private fun closeMenu() {
        menu?.dismiss()
        menu = null
        retroView?.frameSpeed = 1
        retroView?.audioEnabled = true
    }

    /** Menu contents per §5.2: resume, quick-save, quick-load, restart, Select, settings, exit. */
    inner class MenuActions {
        fun resume() = closeMenu()

        fun quickSave() {
            saves.writeQuickSave(retroView?.serializeState())
            closeMenu()
        }

        fun quickLoad() {
            saves.readQuickSave()?.let { retroView?.unserializeState(it) }
            closeMenu()
        }

        fun restart() {
            saves.clearResume()
            retroView?.reset()
            closeMenu()
        }

        /**
         * The Sundial has nine buttons and GBA needs ten, so Select is the one that gets dropped —
         * L and R are live gameplay inputs while Select is almost never pressed mid-action. This is
         * where it comes back.
         */
        fun pressSelect() {
            val view = retroView ?: return
            closeMenu()
            view.sendKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_SELECT, 0)
            root.postDelayed({
                view.sendKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_SELECT, 0)
            }, 80)
        }

        // The four settings cycle in place and deliberately leave the menu open, so the effect is
        // visible behind it and a wrong guess costs one more press rather than a reopen.
        fun setOrientation(value: Orientation) {
            prefs.orientation = value
            requestedOrientation = value.activityInfoValue
        }

        fun setScaling(value: ScalingMode) {
            prefs.scaling = value
            applyScaling()
        }

        fun setFaceplate(value: Faceplate) {
            prefs.faceplate = value
            faceplateView.mode = value
            // The picture rides high under a faceplate and is centred without one, so this moves it.
            applyScaling()
            updateGlowSampling()
        }

        fun setFilter(value: ScreenFilter) {
            prefs.screenFilter = value
            retroView?.shader = shaderFor(value)
        }

        fun setLegend(enabled: Boolean) {
            prefs.legendEnabled = enabled
            refreshLegend()
        }

        fun setPositionalFaceButtons(enabled: Boolean) {
            prefs.positionalFaceButtons = enabled
            router.positionalFaceButtons = enabled
            refreshLegend()
        }

        fun exitToLibrary() {
            closeMenu()
            finish()
        }
    }

    private fun shaderFor(filter: ScreenFilter): ShaderConfig = when (filter) {
        ScreenFilter.OFF -> ShaderConfig.Default
        ScreenFilter.CRT -> ShaderConfig.CRT
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "SideRetro"
        private const val EXTRA_ROM_PATH = "rom_path"
        private const val LEGEND_PEEK_MS = 2500L
        private const val MAX_RESTORE_ATTEMPTS = 120

        /** §5.3, lit faceplate. */
        private const val GLOW_SAMPLE_MS = 250L
        /** Lets the first emulated frame reach the SurfaceView before its first PixelCopy. */
        private const val GLOW_STARTUP_DELAY_MS = 300L
        private const val GLOW_SAMPLE_PX = 16
        private const val GLOW_EASE = 0.3f
        /** Portrait Console/Lit: 43% of spare height above, 57% below. */
        private const val PORTRAIT_FACEPLATE_TOP_FRACTION = 0.43f
        /** Dense games must clear the SP-01's visible rounded-bezel zone, not merely its pixels. */
        private const val PORTRAIT_DENSE_VISUAL_TOP_FLOOR_DP = 32
        /** Keep the normal first pass readable; Mini may go smaller rather than disappear. */
        private const val LEGEND_READABLE_MIN_DP = 44
        /** Key labels inside the keyboard, T9 and Sundial drawings remain legible at this floor. */
        private const val DENSE_LEGEND_READABLE_MIN_DP = 96
        /** A real visual inset on the 480px panel, rather than the former near-edge 3dp. */
        private const val LEGEND_EDGE_DP = 12
        private const val LEGEND_GAP_DP = 6
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

        fun intent(context: Context, rom: Rom): Intent =
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_ROM_PATH, rom.file.absolutePath)
    }
}
