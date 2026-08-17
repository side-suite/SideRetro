package fi.palonkorpi.sideretro.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.PathParser
import fi.palonkorpi.sideretro.emu.GameSystem
import fi.palonkorpi.sideretro.settings.Faceplate

/**
 * SPEC.md §5.3 — what fills the space around the picture.
 *
 * Sits *behind* the `GLRetroView`, which is a `SurfaceView` and therefore punches a hole in the
 * window wherever it lands. That is exactly the arrangement this wants: everything drawn here inside
 * the picture's own rectangle is hidden for free, so the well and the glow can be drawn as whole
 * shapes without any clipping arithmetic.
 *
 * ### It draws nothing when there is no room
 *
 * The gaps are measured every layout, and each element has a threshold. In landscape at Large, NES
 * and Genesis fill the panel exactly (§4.3) — the gaps are zero, and the faceplate silently draws
 * nothing rather than squeezing a wordmark into two pixels. This is deliberate: filling the screen
 * completely is the better outcome, and the dressing exists for the orientations that cannot.
 */
class FaceplateView(context: Context) : View(context) {

    /**
     * The one idle-ink token shared by the faceplate mark and the control legend.
     *
     * The legend is part of the same physical object as the frame, not an independent HUD.  Keep
     * this derivation here so Lit cannot accidentally end up with two near-but-not-quite matching
     * versions of the sampled colour.
     */
    val markInk: Int
        get() = when (mode) {
            Faceplate.OFF -> NEUTRAL_MARK_INK
            Faceplate.CONSOLE -> skin.ink
            Faceplate.LIT -> brightestGlowColour(glowTint)
        }

    /** Called whenever [markInk] changes, including the first listener attachment. */
    var onMarkInkChanged: ((Int) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(markInk)
        }

    var mode: Faceplate = Faceplate.OFF
        set(value) {
            if (field == value) return
            field = value
            invalidate()
            dispatchMarkInk()
        }

    var system: GameSystem = GameSystem.NES
        set(value) {
            if (field == value) return
            field = value
            skin = ConsoleSkin.of(value)
            plateShader = null
            glowTint = skin.glow
            invalidate()
            // In Lit, assigning glowTint above already dispatched the new live token. Console
            // and Off still depend on the system skin, so they need their own notification.
            if (mode != Faceplate.LIT) dispatchMarkInk()
        }

    private var skin = ConsoleSkin.of(system)

    /**
     * The colour the picture is throwing right now, sampled from the frame by [GameActivity] and
     * brightened. Falls back to the machine's own screen colour before the first sample arrives.
     */
    var glowTint: Int = skin.glow
        set(value) {
            if (field == value) return
            field = value
            if (mode == Faceplate.LIT) {
                invalidate()
                dispatchMarkInk()
            }
        }

    /**
     * Compact QWERTY and T9 need a larger lower-right reference. In portrait, the identity moves
     * to the opposite lower corner so the composition reads as one intentional base rather than a
     * header competing with a near-top game.
     */
    var useLowerPortraitMark: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private fun dispatchMarkInk() {
        onMarkInkChanged?.invoke(markInk)
    }

    /** Where the picture actually is, in this view's coordinates. */
    private val game = RectF()

    fun setGameRect(left: Float, top: Float, right: Float, bottom: Float) {
        if (game.left == left && game.top == top && game.right == right && game.bottom == bottom) return
        game.set(left, top, right, bottom)
        glowMask?.recycle()
        glowMask = null
        invalidate()
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val subtext = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        letterSpacing = 0.22f
    }
    private val glow = Paint(Paint.FILTER_BITMAP_FLAG)

    private val rect = RectF()
    private var plateShader: LinearGradient? = null

    /**
     * The glow, pre-blurred once into a quarter-scale alpha mask.
     *
     * `BlurMaskFilter` is ignored by the hardware renderer, so a live blur would force this whole
     * view onto a software layer and re-blur it every time the tint changes — several times a second
     * once the colour follows the frame. Baking the shape once and recolouring it with a colour
     * filter makes every subsequent redraw a single scaled bitmap blit.
     */
    private var glowMask: Bitmap? = null
    private val maskSrc = Rect()
    private val maskDst = Rect()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        plateShader = null
        glowMask?.recycle()
        glowMask = null
    }

    override fun onDraw(canvas: Canvas) {
        if (mode == Faceplate.OFF || game.width() <= 0f || game.height() <= 0f) return
        when (mode) {
            Faceplate.CONSOLE -> drawConsole(canvas)
            Faceplate.LIT -> drawLit(canvas)
            Faceplate.OFF -> Unit
        }
    }

    // ---------------------------------------------------------------------------------------
    // Console
    // ---------------------------------------------------------------------------------------

    private fun drawConsole(canvas: Canvas) {
        val shader = plateShader ?: LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            skin.plateTop, skin.plateBottom, Shader.TileMode.CLAMP,
        ).also { plateShader = it }

        fill.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = null

        val gapTop = game.top
        val gapBottom = height - game.bottom
        val gapSide = minOf(game.left, width - game.right)
        // Below this the plate is a hairline round the picture and reads as a rendering mistake
        // rather than a bezel, so nothing is drawn at all.
        if (maxOf(gapTop, gapBottom) < dp(10f)) return

        val pad = minOf(dp(9f), maxOf(gapTop, gapBottom) * 0.4f, gapSide.coerceAtLeast(0f))
        rect.set(game.left - pad, game.top - pad, game.right + pad, game.bottom + pad)
        val radius = dp(skin.wellRadiusDp)

        fill.color = skin.well
        canvas.drawRoundRect(rect, radius, radius, fill)
        stroke.color = skin.wellEdge
        stroke.strokeWidth = dp(1f)
        canvas.drawRoundRect(rect, radius, radius, stroke)

        val wellTop = rect.top
        val wellBottom = rect.bottom
        // In portrait the identity belongs above the picture. It counterweights the controls
        // reference at lower-right, so the plate reads as one intentional composition rather than
        // a screen with a caption bolted beneath it. At this scale there is room for either the
        // wordmark or decorative chrome, not both.
        val markBelow = drawPortraitWordmarkBelowLeft(canvas, skin.ink, skin.subInk)
        val markAbove = if (markBelow) false else drawPortraitWordmarkAbove(
            canvas = canvas,
            ink = skin.ink,
            modeInk = skin.subInk,
        )

        // The band above the well: printed stripes if this machine had them, a power lamp if not.
        // One or the other — both is clutter at this size, and no machine did both prominently.
        if (!markAbove && skin.stripes.isNotEmpty() && wellTop >= dp(20f)) {
            var y = wellTop - dp(8f)
            skin.stripes.forEach { colour ->
                fill.color = colour
                canvas.drawRect(rect.left, y, rect.right, y + dp(1.6f), fill)
                y -= dp(5f)
            }
        } else if (!markAbove && wellTop >= dp(18f)) {
            val cy = wellTop - dp(9f)
            fill.color = skin.led
            canvas.drawCircle(rect.left + dp(4f), cy, dp(2.4f), fill)
            subtext.color = skin.subInk
            subtext.textSize = dp(6.5f)
            canvas.drawText("POWER", rect.left + dp(11f), cy + dp(2.4f), subtext)
        }

        if (!markAbove && !markBelow && height - wellBottom >= dp(26f)) {
            drawWordmark(
                canvas,
                centreY = wellBottom + dp(13f),
                size = dp(15f),
                ink = skin.ink,
                modeInk = skin.subInk,
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // Lit
    // ---------------------------------------------------------------------------------------

    /**
     * The same picture, made to look emitted rather than placed — the light the screen would be
     * throwing onto a wall behind it, in the colour it is actually throwing.
     *
     * Five passes from very wide and faint to tight and bright. The wide passes are what make it
     * read as light rather than as a coloured border; the tight ones carry the intensity.
     */
    private fun drawLit(canvas: Canvas) {
        val mask = glowMask ?: buildGlowMask() ?: return
        val tint = glowTint or (0xFF shl 24)

        // An ALPHA_8 bitmap is a mask: the paint supplies the colour, the bitmap supplies coverage.
        // So recolouring the glow costs one field assignment, not a re-blur.
        glow.color = tint
        maskSrc.set(0, 0, mask.width, mask.height)
        maskDst.set(0, 0, width, height)
        canvas.drawBitmap(mask, maskSrc, maskDst, glow)

        rect.set(game)
        rect.inset(-dp(1f), -dp(1f))
        stroke.color = (tint and 0x00FFFFFF) or (0xA8 shl 24)
        stroke.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(rect, dp(2f), dp(2f), stroke)

        val mark = brightestGlowColour(glowTint)
        val markBelow = drawPortraitWordmarkBelowLeft(canvas, mark, withAlpha(mark, 0xB8))
        if (!markBelow && !drawPortraitWordmarkAbove(
                canvas = canvas,
                ink = mark,
                modeInk = withAlpha(mark, 0xB8),
            ) && height - game.bottom >= dp(24f)
        ) {
            drawWordmark(
                canvas,
                centreY = game.bottom + dp(11f),
                size = dp(13f),
                // The mark is the brightest stable expression of the sampled screen hue. It makes
                // Lit feel like one ambient-light system rather than a generic mark sat below a
                // coloured glow. `brightestGlowColour` retains a usable hue for very dark or grey
                // frames, so a fade-to-black cannot make the mark disappear or turn muddy.
                ink = mark,
                modeInk = withAlpha(mark, 0xB8),
            )
        }
    }

    /**
     * Converts a frame average into a readable lit-mark colour. The sampler already brightens its
     * result, but retaining a minimum saturation and full value here matters for sepia/white and
     * near-black frames: those should still read as the system's emitted light, never as dull grey
     * text. A highly saturated frame is kept expressive, capped just short of neon clipping.
     */
    private fun brightestGlowColour(sample: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(sample, hsv)
        if (hsv[2] < MIN_LIT_VALUE || hsv[1] < MIN_LIT_SATURATION) {
            Color.colorToHSV(skin.glow, hsv)
        }
        hsv[1] = hsv[1].coerceIn(MIN_MARK_SATURATION, MAX_MARK_SATURATION)
        hsv[2] = 1f
        return Color.HSVToColor(0xFF, hsv)
    }

    private fun withAlpha(colour: Int, alpha: Int): Int =
        (colour and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    /**
     * Blurs the picture's outline once, at quarter scale, into a white alpha mask. The downscale is
     * free quality here — the thing being stored is already blurred, and it makes the widest pass a
     * 34px blur instead of 136. The broader outer passes change only this one-time mask build, not
     * frame sampling or the cost of the live redraw.
     */
    private fun buildGlowMask(): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val w = (width / MASK_DIVISOR).toInt().coerceAtLeast(1)
        val h = (height / MASK_DIVISOR).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val small = RectF(
            game.left / MASK_DIVISOR, game.top / MASK_DIVISOR,
            game.right / MASK_DIVISOR, game.bottom / MASK_DIVISOR,
        )
        small.inset(-dp(1f) / MASK_DIVISOR, -dp(1f) / MASK_DIVISOR)
        GLOW_PASSES.forEach { (blurDp, alpha) ->
            paint.color = Color.argb(alpha, 255, 255, 255)
            paint.maskFilter = BlurMaskFilter(dp(blurDp) / MASK_DIVISOR, BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(small, dp(2f), dp(2f), paint)
        }
        glowMask = bitmap
        return bitmap
    }

    // ---------------------------------------------------------------------------------------
    // The mark
    // ---------------------------------------------------------------------------------------

    /**
     * Portrait has enough negative space for the identity to act as a header. The baseline is
     * vertically centred in the available upper headroom. This lets the identity counterweight the
     * lower-right control reference instead of reading as a caption pinned to the screen. Landscape
     * and full-bleed layouts retain their compact lower treatment (or no mark when space is gone).
     */
    private fun drawPortraitWordmarkAbove(
        canvas: Canvas,
        ink: Int,
        modeInk: Int,
    ): Boolean {
        if (width >= height || game.top < dp(PORTRAIT_MARK_MIN_TOP_GAP_DP)) return false
        val size = dp(PORTRAIT_MARK_SIZE_DP)
        // Centre the *stack* in the headroom. The descriptor now sits under the mark rather than
        // stretching the header sideways, so this puts the wordmark itself slightly higher while
        // retaining the deliberate upper/lower balance of the portrait composition.
        val centreY = game.top / 2f
        drawWordmark(
            canvas = canvas,
            centreY = centreY,
            size = size,
            ink = ink,
            modeInk = modeInk,
        )
        return true
    }

    /**
     * The dense keyboard/keypad composition puts the physical control reference at lower-right.
     * Put the identity in the opposite lower corner, where it cannot compete with either the game
     * or the legend. This is intentionally portrait-only; landscape has no stable corner surplus.
     */
    private fun drawPortraitWordmarkBelowLeft(
        canvas: Canvas,
        ink: Int,
        modeInk: Int,
    ): Boolean {
        if (!useLowerPortraitMark || width >= height) return false
        val lowerSpace = height - game.bottom
        if (lowerSpace < dp(LOWER_MARK_MIN_BOTTOM_GAP_DP)) return false

        val size = dp(LOWER_MARK_SIZE_DP)
        drawWordmark(
            canvas = canvas,
            centreX = width * LOWER_MARK_CENTRE_X_FRACTION,
            centreY = game.bottom + lowerSpace * LOWER_MARK_CENTRE_Y_FRACTION,
            size = size,
            ink = ink,
            modeInk = modeInk,
        )
        return true
    }

    /**
     * The supplied SideRetro SVG stacked over the machine's description, centred on the faceplate.
     *
     * The mark is deliberately drawn from its original vector paths rather than through a font:
     * that preserves the designer's exact stepped geometry at every size and keeps the drawable
     * tintable for Console and frame-reactive Lit modes. Keeping the descriptor below the logo is
     * both clearer at this scale and gives the header a calmer, more balanced silhouette.
     */
    private fun drawWordmark(
        canvas: Canvas,
        centreY: Float,
        size: Float,
        ink: Int,
        modeInk: Int,
        centreX: Float = width / 2f,
    ) {
        subtext.textSize = size * DESCRIPTOR_SIZE_RATIO
        val metrics = subtext.fontMetrics
        val descriptorHeight = metrics.descent - metrics.ascent
        val gap = size * DESCRIPTOR_GAP_RATIO
        val stackHeight = size + gap + descriptorHeight
        val logoWidth = size * (LOGO_VIEWPORT_WIDTH / LOGO_VIEWPORT_HEIGHT)
        val logoTop = centreY - stackHeight / 2f
        val logoLeft = centreX - logoWidth / 2f

        canvas.save()
        canvas.translate(logoLeft, logoTop)
        val scale = size / LOGO_VIEWPORT_HEIGHT
        canvas.scale(scale, scale)
        logoPaths.forEach { path -> canvas.drawPath(path, fill.apply { color = ink }) }
        canvas.restore()

        subtext.color = modeInk
        val descriptorX = centreX - subtext.measureText(skin.mode) / 2f
        val descriptorBaseline = logoTop + size + gap - metrics.ascent
        canvas.drawText(skin.mode, descriptorX, descriptorBaseline, subtext)
    }

    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private companion object {
        private const val NEUTRAL_MARK_INK = 0xFFF2F2F3.toInt()
        /** The artwork viewBox in tiles-imgs/sideretro-logo.svg. */
        const val LOGO_VIEWPORT_WIDTH = 1797.6f
        const val LOGO_VIEWPORT_HEIGHT = 217.4f
        const val MASK_DIVISOR = 4f
        private const val MIN_LIT_VALUE = 0.16f
        private const val MIN_LIT_SATURATION = 0.08f
        private const val MIN_MARK_SATURATION = 0.56f
        private const val MAX_MARK_SATURATION = 0.90f
        // Slightly reduced from the initial 21dp header. It keeps the pixel construction legible
        // without crowding the upper band now that the descriptor has its own centred line.
        private const val PORTRAIT_MARK_SIZE_DP = 17f
        private const val DESCRIPTOR_SIZE_RATIO = 0.50f
        private const val DESCRIPTOR_GAP_RATIO = 0.14f
        // The black face needs at least this much upper headroom to remain a header rather
        // than collide with the picture. With more room, it is centred in that headroom.
        private const val PORTRAIT_MARK_MIN_TOP_GAP_DP = 39f
        private const val LOWER_MARK_SIZE_DP = 17f
        private const val LOWER_MARK_MIN_BOTTOM_GAP_DP = 42f
        /** Keeps a ~140px logo lockup on the open half of the 480px portrait base. */
        private const val LOWER_MARK_CENTRE_X_FRACTION = 0.26f
        /** Centre the lockup within the free lower band, clear of the picture edge. */
        private const val LOWER_MARK_CENTRE_Y_FRACTION = 0.55f
        /**
         * Blur radius in dp, alpha. The outer 136dp pass deliberately reaches well beyond the old
         * 72dp halo; its low alpha keeps the surrounding dead space atmospheric. The inner passes
         * supply a visibly luminous edge without making a dark game scene look like a coloured box.
         */
        val GLOW_PASSES = listOf(136f to 0x20, 82f to 0x2A, 44f to 0x3A, 18f to 0x60, 6f to 0x96)

        /**
         * Exact paths from tiles-imgs/sideretro-logo.svg.  Keeping them as paths makes the mark a
         * single-colour, dynamically tintable vector without rasterising or substituting a font.
         */
        val logoPaths: List<Path> by lazy {
            LOGO_PATH_DATA.map { pathData ->
                checkNotNull(PathParser.createPathFromPathData(pathData)).apply {
                    // Illustrator exports counters as a second contour in the same path. Android's
                    // default winding fill can treat two same-direction contours as solid, which
                    // filled the final O. The SVG artwork uses the even-odd rule visually, so make
                    // that explicit for every glyph (including future counters).
                    fillType = Path.FillType.EVEN_ODD
                }
            }
        }

        val LOGO_PATH_DATA = listOf(
            "M31.1,217.4v-31.1H0v-31.1h62.1v31.1h93.2v-62.1H31.1v-31.1H0V31.1h31.1V0h155.3v31.1h31.1v31.1h-62.1v-31.1H62.1v62.1h124.3v31.1h31.1v62.1h-31.1v31.1H31.1Z",
            "M217.5,217.4v-31.1h62.1v-93.2h-31.1v-31.1h93.2v124.3h62.1v31.1h-186.4ZM279.6,31.1V0h62.1v31.1h-62.1Z",
            "M621.3,93.2V0h-62.1v62.1h-124.3v31.1h-31.1v93.2h31.1v31.1h186.4v-31.1h-31v-93.2h31ZM559.2,186.4h-93.2v-93.2h93.2v93.2Z",
            "M652.1,217.4v-31.1h-31.1v-93.2h31.1v-31.1h155.3v31.1h31.1v62.1h-155.3v31.1h124.3v31.1h-155.3ZM683.2,124.3h93.2v-31.1h-93.2v31.1Z",
            "M839.2,217.4V0h186.4v31.1h31.1v93.2h-62.1v31.1h31.1v31.1h31.1v31.1h-93.2v-31.1h-31.1v-31.1h-31.1v62.1h-62.1ZM901.3,124.3h62.1v-31.1h31.1V31.1h-93.2v93.2Z",
            "M1087.1,217.4v-31.1h-31.1v-93.2h31.1v-31.1h155.3v31.1h31.1v62.1h-155.3v31.1h124.3v31.1h-155.3ZM1118.1,124.3h93.2v-31.1h-93.2v31.1Z",
            "M1304.4,217.4v-124.3h-31v-31.1h31V0h62.1v62.1h62.1v31.1h-62.1v124.3h-62.1Z",
            "M1489.7,93.2h31.1v31.1h-31.1v93.2h-62.1V62.1h62.1v31.1ZM1613.9,62.1v31.1h-93.2v-31.1h93.2Z",
            "M1611.2,217.4v-31.1h-31.1v-93.2h31.1v-31.1h155.3v31.1h31.1v93.2h-31.1v31.1h-155.3ZM1642.3,186.4h93.2v-93.2h-93.2v93.2Z",
        )
    }
}
