package fi.palonkorpi.sideretro.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.PathParser
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The legend band (SPEC.md §5.1) — a miniature of the attached Keytile that lights up under the
 * thumb.
 *
 * ### Why a picture and not a list
 *
 * The first attempt was a line of text ("A · A  B · B  X · L"), which was illegible: "·" did double
 * duty as both the separator between entries and the joiner inside one, so it had to be parsed
 * before it could be read. The second was chips — better, but a chip reading "J → B" still asks the
 * player to go and *find* J. Drawing the tile removes the translation step: the thing on screen is
 * the thing in your hand, and the button you want is the lit one in that position.
 *
 * ### Four states, no extra artwork
 *
 * Dim outline = a real key that does nothing on this system. Bright outline + badge = bound.
 * Filled = held right now. Body = never changes. Everything but the body comes from live state, so
 * one drawing per tile covers every system, both orientations and both button modes.
 *
 * In landscape the whole tile rotates counter-clockwise to match how the phone is being held, while
 * the labels stay upright — again a transform rather than a second drawing.
 */
class LegendView(context: Context) : View(context) {

    /**
     * Idle ink supplied by [FaceplateView].  This intentionally colours every light element of
     * the miniature — authored lettering, outlines, grid, arrows and contextual labels — so the
     * legend reads as an extension of the SideRetro mark.  Press feedback stays SideCall teal.
     */
    var inkTint: Int = NEUTRAL_INK
        set(value) {
            val opaque = value or (0xFF shl 24)
            if (field == opaque) return
            field = opaque
            applyInkTint()
            invalidate()
        }

    var art: TileArt? = null
        set(value) {
            field = value
            badges = null
            requestLayout()
            invalidate()
        }

    /** Keycode name → what that button does now. Anything absent is drawn dim. */
    var labels: Map<String, String> = emptyMap()
        set(value) {
            field = value
            badges = null
            invalidate()
        }

    /** Rotate the tile to match a device held counter-clockwise. */
    var landscape: Boolean = false
        set(value) {
            field = value
            badges = null
            requestLayout()
            invalidate()
        }

    /**
     * Shown when there is no drawing: before the first keypress, and on the tiles whose artwork is
     * still being made. Wrapped across lines so a long fallback stays readable.
     */
    var placeholder: String? = null
        set(value) {
            field = value
            invalidate()
        }

    private val held = mutableSetOf<String>()

    /** Compact footprint for the frameless corner legend. */
    val dockHeight: Int
        get() = if (art == null) dp(FALLBACK_HEIGHT_DP).toInt() else dp(ART_HEIGHT_DP).toInt()

    fun setPressed(keyCodeName: String, down: Boolean) {
        val changed = if (down) held.add(keyCodeName) else held.remove(keyCodeName)
        if (changed) invalidate()
    }

    // ---------------------------------------------------------------------------------------
    // Paint
    // ---------------------------------------------------------------------------------------

    // The Mini Controller is a piece of hardware with generous, deliberately drawn outlines.
    // Keeping those outlines at rest (rather than filling every mapped cap white) gives it the
    // same quiet idle language as the Compact QWERTY: the accent is reserved for the thumb.
    private val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF23272D.toInt() }
    private val dimFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val miniLiveOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = MINI_OUTLINE_WIDTH
    }
    private val miniDimOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = MINI_OUTLINE_WIDTH
    }
    /**
     * A QWERTY control is a replacement cap, not an annotation.  Its tiny original printing
     * competes directly with a game action at this size, so an inset black cap intentionally
     * hides it while leaving the surrounding keyboard grid visible as positional context.
     */
    private val keyboardCap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B0B0D.toInt() }
    /** A press is the sole exception: the SideCall accent gives live feedback without noisy idle ink. */
    private val keyboardHeldCap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    /** A Sundial has no printed key labels to act as positional context, so its dormant corners remain visible. */
    private val sundialIdleOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = MINI_OUTLINE_WIDTH
    }
    private val printed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    /** The source artwork is white; tint that authored contrast with the faceplate's live ink. */
    private val vectorPrinted = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vectorDimPrinted = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vectorOnLiveCap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF14181D.toInt() }

    /** Sits on a filled cap, so it is dark. */
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF14181D.toInt()
        textAlign = Paint.Align.CENTER
        // Dynamic game actions must sit alongside the artist's light, outlined A/B/X/Y and
        // Start/Select lettering.  Medium/bold made the renderer look like it was shouting over
        // the physical key legends rather than annotating them.
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    /** A contextual Mini Controller mapping replaces—not competes with—the printed glyph. */
    private val miniBinding = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    /** Sits outside the tile, on whatever is behind — so it follows the faceplate mark. */
    private val outsideBadge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val keyboardBinding = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val keyboardHeldBinding = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0B0B0D.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val numpadArrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val numpadHeldArrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0B0B0D.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = sp(13f)
    }

    init {
        applyInkTint()
    }

    private fun applyInkTint() {
        val dim = withAlpha(inkTint, DIM_INK_ALPHA)
        val quiet = withAlpha(inkTint, QUIET_INK_ALPHA)
        // Lines which are literally part of the artwork's white construction follow the mark too.
        // This includes the keyboard/numpad grid and the Sundial's outer ring.
        dimFill.color = dim
        miniLiveOutline.color = inkTint
        miniDimOutline.color = dim
        sundialIdleOutline.color = quiet
        printed.color = inkTint
        vectorPrinted.color = inkTint
        vectorDimPrinted.color = dim
        miniBinding.color = inkTint
        outsideBadge.color = inkTint
        keyboardBinding.color = inkTint
        numpadArrow.color = inkTint
        hint.color = inkTint
    }

    private fun withAlpha(colour: Int, alpha: Int): Int =
        (colour and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private val rect = RectF()

    // Set during layout: how the art's coordinate space maps onto the band.
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var contentCentreX = 0f
    private var contentCentreY = 0f

    /** Where each label sits, in art coordinates. Recomputed only when the bindings change. */
    private var badges: Map<String, Pair<Float, Float>>? = null
    private var vectorPaths: Map<VectorGlyph, android.graphics.Path> = emptyMap()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (art == null) {
            // It is deliberately just text, not a second card competing with the game and mark.
            val available = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(
                minOf(dp(FALLBACK_WIDTH_DP).toInt(), (available - dp(16f)).toInt()),
                dockHeight,
            )
        } else {
            setMeasuredDimension(dp(ART_WIDTH_DP).toInt(), dockHeight)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val tile = art ?: return
        // Rotated, the tile's footprint swaps axes — but these drawings are square, so this only
        // matters if a future tile is not.
        val content = tile.contentBounds ?: ArtBounds(0f, 0f, tile.width, tile.height)
        val artW = if (landscape) content.height else content.width
        val artH = if (landscape) content.width else content.height
        // Leave room around the tile for badges that sit outside the plate: Start and Select both
        // hang off the corners, and clipping the one word that explains a button is self-defeating.
        // The Mini's Start/Select are exact, authored glyphs inside their own caps rather than
        // floating renderer badges. Its tight frame can therefore use the whole safe box.
        val room = if (tile.contentBounds != null) 1f else 1f - 2 * BADGE_MARGIN
        val contentHeight = h - bandPad * 2
        scale = minOf((w - bandPad * 2) / artW, contentHeight / artH) * room
        offsetX = w / 2f
        offsetY = h / 2f
        contentCentreX = content.centreX
        contentCentreY = content.centreY

        val text = tile.height * scale * 0.075f
        printed.textSize = text * 0.85f
        badge.textSize = text
        miniBinding.textSize = text
        outsideBadge.textSize = text * 0.92f
    }

    // ---------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val tile = art
        if (tile == null) {
            placeholder?.let {
                // The faceplate already carries the SideRetro mark. The empty state simply asks
                // for the one action that makes the controls concrete.
                val text = if (it == Legend.UNKNOWN_TILE_HINT) UNKNOWN_TILE_PROMPT else it
                val lines = wrap(text, width - bandPad * 4)
                val lineHeight = hint.textSize * 1.35f
                val totalHeight = lines.size * lineHeight
                var baseline = (height - totalHeight) / 2f - hint.ascent()
                lines.forEach { line ->
                    canvas.drawText(line, width / 2f, baseline, hint)
                    baseline += lineHeight
                }
            }
            return
        }

        val anchors = badges ?: computeBadgeAnchors(tile).also { badges = it }

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        // Counter-clockwise, matching §3.2 — the tile ends up on the right of the screen.
        if (landscape) canvas.rotate(-90f)
        canvas.translate(-contentCentreX, -contentCentreY)

        tile.body.forEach { piece ->
            // The Mini's Start outline is also one of its button overlays. Drawing the body copy
            // would make that one key look heavier than the rest, so buttons own all Mini strokes.
            if (!tile.usesReplacementCaps && piece.role == Role.OUTLINE) return@forEach
            draw(canvas, piece.shape, if (piece.role == Role.SOLID) plate else dimFill)
        }

        tile.buttons.forEach { (name, shape) ->
            if (!tile.usesReplacementCaps) {
                draw(
                    canvas,
                    shape,
                    when {
                        name in held -> heldFill
                        name in labels -> miniLiveOutline
                        else -> miniDimOutline
                    },
                )
            }
        }

        // Text is drawn after the rotation is undone, so labels stay upright in landscape — you read
        // a legend, you do not tilt your head at it.
        canvas.restore()

        // Outlined SVG lettering stays in the authored coordinate system.  This is particularly
        // important for the Mini Controller's diagonal START and hand-broken SELECT; a generic
        // badge can neither reproduce their angle nor their optical centring.
        if (tile.vectors.isNotEmpty()) {
            if (vectorPaths.size != tile.vectors.size) {
                vectorPaths = tile.vectors.associateWith { vector ->
                    checkNotNull(PathParser.createPathFromPathData(vector.pathData))
                }
            }
            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)
            if (landscape) canvas.rotate(-90f)
            canvas.translate(-contentCentreX, -contentCentreY)
            tile.vectors.forEach { vector ->
                if (!tile.usesReplacementCaps && replacesPrintedMiniGlyph(vector.owner)) return@forEach
                canvas.save()
                canvas.translate(vector.translateX, vector.translateY)
                canvas.rotate(vector.rotation)
                // A printed label needs to remain legible while its physical cap is lit.  The
                // authored shape and placement stay untouched; only its ink inverts with the cap.
                canvas.drawPath(
                    vectorPaths.getValue(vector),
                    when {
                        // Unlike a controller cap, a QWERTY cell is only tinted. Its physical
                        // printing must stay light, otherwise the live E/R or J/K key disappears.
                        !tile.usesReplacementCaps && vector.owner in held -> vectorOnLiveCap
                        !tile.usesReplacementCaps && vector.owner !in labels -> vectorDimPrinted
                        else -> vectorPrinted
                    },
                )
                canvas.restore()
            }
            canvas.restore()
        }

        canvas.save()
        canvas.translate(offsetX, offsetY)

        // A button showing what it does has no room left for what is printed on it — at this size a
        // circle holds one character. The printed glyph survives on buttons that are dim, so an
        // unused key is still identifiable.
        tile.legends.forEach { glyph ->
            if (!tile.usesReplacementCaps && replacesPrintedMiniGlyph(glyph.owner)) return@forEach
            // Illustrator anchors text at the glyph's left baseline; centre it on the button instead.
            val (x, y) = place(tile, glyph.x + glyph.size * 0.3f, glyph.y - glyph.size * 0.36f)
            val lines = glyph.text.split('\n')
            val lineHeight = printed.textSize * 1.02f
            var baseline = y - (lines.size - 1) * lineHeight / 2f -
                (printed.ascent() + printed.descent()) / 2f
            lines.forEach { line ->
                canvas.drawText(line, x, baseline, printed)
                baseline += lineHeight
            }
        }

        if (tile.usesReplacementCaps) {
            // A replacement cap is deliberately the final physical layer: keypad artwork includes
            // baked number and symbol glyphs, which otherwise end up on top of a direction arrow.
            // The cap is inset, so the original grid is still the positional context.
            drawReplacementCaps(canvas, tile)
            drawReplacementBindings(canvas, tile)
        } else labels.forEach { (name, label) ->
            if (label.isEmpty()) return@forEach
            // Start and Select are permanently printed on the Mini Controller. They are physical
            // names, not context-specific mappings, so keep the artwork's labels and do not draw
            // a second floating word beside the same cap.
            if (label.length > 1 && (
                tile.legends.any { it.owner == name && it.text.length > 1 } ||
                    tile.vectors.any { it.owner == name }
                )
            ) {
                return@forEach
            }
            val shape = tile.buttons[name] ?: return@forEach
            // One character sits on the cap; a word cannot, so it goes outside.
            val inside = label.length == 1
            val paint = when {
                !tile.usesReplacementCaps && name in held -> badge
                !tile.usesReplacementCaps && inside -> miniBinding
                inside -> badge
                else -> outsideBadge
            }
            val (x, y) = if (inside) {
                place(tile, shape.centreX, shape.centreY)
            } else {
                val anchor = anchors[name] ?: return@forEach
                place(tile, anchor.first, anchor.second)
            }
            // Keep the word inside the box. A label pushed outward for clearance can otherwise run
            // off the corner it was pushed towards, and half of "Select" explains nothing.
            val halfText = paint.measureText(label) / 2f
            val limitX = width / 2f - halfText - bandPad
            val limitY = height / 2f - paint.textSize * 0.6f - bandPad
            canvas.drawText(
                label,
                x.coerceIn(-limitX, limitX),
                y.coerceIn(-limitY, limitY) - (paint.ascent() + paint.descent()) / 2f,
                paint,
            )
        }

        canvas.restore()
    }

    /** Draw black replacement caps over mapped physical cells, leaving their grid visible. */
    private fun drawReplacementCaps(canvas: Canvas, tile: TileArt) {
        canvas.save()
        // Called from the label layer, where the canvas is already centred on the view.
        canvas.scale(scale, scale)
        if (landscape) canvas.rotate(-90f)
        canvas.translate(-contentCentreX, -contentCentreY)
        val caps = if (tile === TileArtwork.SUNDIAL) tile.buttons.keys else labels.keys
        caps.forEach { name ->
            val shape = tile.buttons[name] ?: return@forEach
            draw(
                canvas,
                shape.inset(KEYBOARD_CAP_INSET),
                if (name in held) keyboardHeldCap else keyboardCap,
            )
            // The corner buttons deliberately have no role on some systems. Keep their physical
            // positions quietly visible rather than letting a missing game binding erase a third
            // of the Sundial from the reference.
            if (tile === TileArtwork.SUNDIAL && name !in held) {
                draw(canvas, shape.inset(KEYBOARD_CAP_INSET), sundialIdleOutline)
            }
        }
        canvas.restore()
    }

    /**
     * QWERTY controls replace the physical key printing with game labels.  The black cap makes
     * the selected cell obvious; the remaining printed cells and grid establish where it is.
     */
    private fun drawReplacementBindings(canvas: Canvas, tile: TileArt) {
        labels.forEach { (name, label) ->
            val shape = tile.buttons[name] ?: return@forEach
            val binding = if (label.isEmpty()) directionArrow(name) else compactQwertyBinding(label)
            if (binding == null) return@forEach

            val smallestSide = when (shape) {
                is Shape.Circle -> shape.r * 2f
                is Shape.RoundRect -> minOf(shape.width, shape.height)
            }
            val (x, y) = place(tile, shape.centreX, shape.centreY)

            // A thin Unicode arrow becomes indistinct beside the keypad's dense source printing.
            // Use one deliberately drawn, centred arrow on numpad direction caps instead.
            if ((tile === TileArtwork.NUMPAD || tile === TileArtwork.SUNDIAL) && label.isEmpty()) {
                drawNumpadDirectionArrow(canvas, x, y, smallestSide * scale, binding, name in held)
                return@forEach
            }

            val textScale = when {
                label.isEmpty() -> 0.64f
                // Select and Start are peer system controls.  "SEL" must set their common size;
                // allowing ST to grow merely because it has one fewer character looks accidental.
                binding == "SEL" || binding == "ST" -> SYSTEM_CONTROL_LABEL_SCALE
                binding.length == 1 -> 0.52f
                binding.length <= 2 -> 0.44f
                binding.length <= 4 -> 0.30f
                else -> 0.25f
            }
            keyboardBinding.textSize = smallestSide * scale * textScale
            keyboardHeldBinding.textSize = keyboardBinding.textSize
            val paint = if (name in held) keyboardHeldBinding else keyboardBinding
            canvas.drawText(
                binding,
                x,
                y - (paint.ascent() + paint.descent()) / 2f,
                paint,
            )
        }
    }

    /** A high-contrast arrow with a proper stem and head, designed for a ~34px numpad cap. */
    private fun drawNumpadDirectionArrow(
        canvas: Canvas,
        x: Float,
        y: Float,
        keySide: Float,
        direction: String,
        isHeld: Boolean,
    ) {
        val rotation = when (direction) {
            "↑" -> 0f
            "→" -> 90f
            "↓" -> 180f
            "←" -> -90f
            else -> return
        }
        val paint = if (isHeld) numpadHeldArrow else numpadArrow
        paint.strokeWidth = keySide * NUMPAD_ARROW_STROKE_SCALE
        val half = keySide * NUMPAD_ARROW_HALF_SCALE
        val head = keySide * NUMPAD_ARROW_HEAD_SCALE

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        canvas.drawLine(0f, half, 0f, -half, paint)
        canvas.drawLine(0f, -half, -head, -half + head, paint)
        canvas.drawLine(0f, -half, head, -half + head, paint)
        canvas.restore()
    }

    /** Preserve the role while keeping the longer labels readable on a ~20px key. */
    private fun compactQwertyBinding(label: String): String = when (label.uppercase()) {
        "START" -> "ST"
        "SELECT" -> "SEL"
        else -> label.uppercase()
    }

    /** The D-pad key positions become the player's apparent directions after landscape rotation. */
    private fun directionArrow(name: String): String? {
        val portrait = when (name) {
            "KEYCODE_E", "KEYCODE_2", "KEYCODE_DPAD_UP" -> "↑"
            "KEYCODE_A", "KEYCODE_4", "KEYCODE_MEDIA_PREVIOUS" -> "←"
            "KEYCODE_C", "KEYCODE_8", "KEYCODE_DPAD_DOWN" -> "↓"
            "KEYCODE_G", "KEYCODE_6", "KEYCODE_MEDIA_NEXT" -> "→"
            else -> return null
        }
        if (!landscape) return portrait
        return when (portrait) {
            "↑" -> "←"
            "←" -> "↓"
            "↓" -> "→"
            else -> "↑"
        }
    }

    /**
     * The face-button lettering in the Mini Controller SVG is the physical key legend.  Leave it
     * alone when it agrees with the current game role; only a genuinely different role gets a
     * replacement glyph.  That is how an Xbox-layout X can truthfully say Genesis A without
     * showing a confusing X/A stack, while an ordinary GB A remains the artist's A.
     */
    private fun replacesPrintedMiniGlyph(owner: String?): Boolean {
        val label = owner?.let { labels[it] } ?: return false
        return label.length == 1 && label != printedMiniGlyph(owner)
    }

    private fun printedMiniGlyph(owner: String): String? = when (owner) {
        "KEYCODE_BUTTON_A" -> "A"
        "KEYCODE_BUTTON_B" -> "B"
        "KEYCODE_BUTTON_X" -> "X"
        "KEYCODE_BUTTON_Y" -> "Y"
        else -> null
    }

    /** Greedy, word-safe wrapping for the undrawn-tile fallback. */
    private fun wrap(text: String, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var line = ""
        words.forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && hint.measureText(candidate) > maxWidth) {
                lines += line
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines.ifEmpty { listOf("") }
    }

    /** Art coordinates → view coordinates relative to the view's centre, honouring the rotation. */
    private fun place(tile: TileArt, x: Float, y: Float): Pair<Float, Float> {
        val cx = (x - contentCentreX) * scale
        val cy = (y - contentCentreY) * scale
        return if (landscape) Pair(cy, -cx) else Pair(cx, cy)
    }

    private fun draw(canvas: Canvas, shape: Shape, paint: Paint) {
        when (shape) {
            is Shape.Circle -> canvas.drawCircle(shape.centreX, shape.centreY, shape.r, paint)
            is Shape.RoundRect -> {
                canvas.save()
                canvas.rotate(shape.rotation, shape.centreX, shape.centreY)
                rect.set(
                    shape.centreX - shape.width / 2f,
                    shape.centreY - shape.height / 2f,
                    shape.centreX + shape.width / 2f,
                    shape.centreY + shape.height / 2f,
                )
                canvas.drawRoundRect(rect, shape.cornerX, shape.cornerY, paint)
                canvas.restore()
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Where labels go
    // ---------------------------------------------------------------------------------------

    /**
     * Puts each label just outside its button, in whichever direction has the most room.
     *
     * The obvious rule — "point away from the middle of the tile" — fails on exactly the cluster
     * that matters: the diamond sits below and right of centre, so X's label would be pushed *down*,
     * straight onto A. Scoring candidate directions by how far they stay from every other button
     * gets X's label to the left, Y's above, A's below and B's right, and pushes Start and Select
     * off their corners into the empty band, without any of it being written down per tile.
     */
    private fun computeBadgeAnchors(tile: TileArt): Map<String, Pair<Float, Float>> {
        val others = tile.buttons.values
        return labels.filterValues { it.length > 1 }.keys.mapNotNull { name ->
            val shape = tile.buttons[name] ?: return@mapNotNull null
            var best: Pair<Float, Float>? = null
            var bestScore = -Float.MAX_VALUE
            for (step in 0 until DIRECTIONS) {
                val angle = 2.0 * Math.PI * step / DIRECTIONS
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                // Measured along this direction, not from a single radius — a pill reaches much
                // further along its length than across it.
                val out = shape.extentTowards(dx, dy) * 1.55f
                val x = shape.centreX + out * dx
                val y = shape.centreY + out * dy
                val score = others
                    .filter { it !== shape }
                    .minOfOrNull {
                        val toX = x - it.centreX
                        val toY = y - it.centreY
                        val distance = hypot(toX, toY)
                        if (distance == 0f) 0f
                        else distance - it.extentTowards(toX / distance, toY / distance)
                    }
                    ?: Float.MAX_VALUE
                if (score > bestScore) {
                    bestScore = score
                    best = Pair(x, y)
                }
            }
            best?.let { name to it }
        }.toMap()
    }

    private val bandPad get() = dp(5f)

    /**
     * Sized to fit the dead space under the screen rather than to take a band off gameplay.
     */
    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        const val ACCENT = 0xFF63D2C3.toInt()
        const val NEUTRAL_INK = 0xFFF2F2F3.toInt()
        /** Disabled real controls stay visible, but as the same hue rather than unrelated grey. */
        const val DIM_INK_ALPHA = 0x58
        const val QUIET_INK_ALPHA = 0x70
        const val DIRECTIONS = 24
        // Bottom-right is a reference, not a second screen.  160dp made the Mini Controller
        // compete with the game; 124dp keeps the physical pattern legible while returning the
        // corner to the faceplate and the picture.
        const val ART_WIDTH_DP = 124f
        const val ART_HEIGHT_DP = 124f
        const val FALLBACK_WIDTH_DP = 164f
        const val FALLBACK_HEIGHT_DP = 36f
        // The QWERTY's grid is 4 artwork units wide.  A 16-unit inset keeps it visible at 124dp
        // while still comfortably covering the key's baked lettering.
        const val KEYBOARD_CAP_INSET = 16f
        const val SYSTEM_CONTROL_LABEL_SCALE = 0.30f
        const val NUMPAD_ARROW_STROKE_SCALE = 0.105f
        const val NUMPAD_ARROW_HALF_SCALE = 0.28f
        const val NUMPAD_ARROW_HEAD_SCALE = 0.18f
        // Artwork is scaled to roughly 0.06x in the corner.  20 art units becomes a readable,
        // intentionally light 1.2px controller outline without making the caps look filled.
        const val MINI_OUTLINE_WIDTH = 20f
        const val UNKNOWN_TILE_PROMPT = "Press any key to identify controls"
        /**
         * Fraction of the band reserved on each side for labels that hang off the plate. Measured
         * against the Mini Controller's worst cases — A's badge below the plate and B's beyond its
         * right edge — which clear it by about 1% of the artwork's own box.
         */
        const val BADGE_MARGIN = 0.03f
    }
}

/** QWERTY and the numpad share the same quiet, contextual replacement-cap treatment. */
private val TileArt.usesReplacementCaps: Boolean
    get() = this === TileArtwork.COMPACT_QWERTY || this === TileArtwork.NUMPAD || this === TileArtwork.SUNDIAL

/** Leave a narrow margin so an inset cap preserves the keyboard's original grid lines. */
private fun Shape.inset(amount: Float): Shape = when (this) {
    is Shape.Circle -> copy(r = (r - amount).coerceAtLeast(0f))
    is Shape.RoundRect -> copy(
        width = (width - amount * 2f).coerceAtLeast(0f),
        height = (height - amount * 2f).coerceAtLeast(0f),
        cornerX = (cornerX - amount).coerceAtLeast(0f),
        cornerY = (cornerY - amount).coerceAtLeast(0f),
    )
}
