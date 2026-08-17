package fi.palonkorpi.sideretro.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes

/**
 * SideRetro's small native-view design system.
 *
 * The dark neutrals and interaction colour deliberately match SideCall. Keeping the shared teal
 * here makes selection, primary actions, and the active controls read as one Sidephone family;
 * game-specific colour remains the job of the faceplate and legend.
 * FaceplateView and LegendView keep their independent, game-specific palettes.
 */
object RetroUi {
    const val INK = 0xFF0B0B0D.toInt()
    const val CARD = 0xFF141418.toInt()
    const val CHIP = 0xFF1C1C22.toInt()
    const val DIVIDER = 0xFF26262D.toInt()
    const val TEXT_PRIMARY = 0xFFF2F2F3.toInt()
    const val TEXT_SECONDARY = 0xFF9A9AA2.toInt()
    const val TEXT_MUTED = 0xFF6E6E76.toInt()
    /** SideCall's exact guard-ring and primary-control teal. */
    const val ACCENT = 0xFF63D2C3.toInt()
    /** A lighter teal for the short pressed state; it keeps the ink foreground legible. */
    const val ACCENT_PRESSED = 0xFF87E6DA.toInt()
    /** A calm, dark teal for low-emphasis selected affordances. */
    const val ACCENT_SUBTLE = 0xFF367C73.toInt()
    /** The same near-black SideCall uses inside teal buttons and selected chips. */
    const val ACCENT_CONTENT = INK
    /** Reserved for destructive actions; it is only used after an explicit confirmation. */
    const val DANGER = 0xFFDF737B.toInt()
    const val DANGER_PRESSED = 0xFFF09A9F.toInt()

    fun View.dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics,
    ).toInt()

    fun View.sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics,
    )

    fun rounded(color: Int, radius: Float, view: View): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = view.dp(radius).toFloat()
        }

    fun tint(icon: ImageView, color: Int) {
        icon.imageTintList = ColorStateList.valueOf(color)
    }

    fun icon(view: View, @DrawableRes drawable: Int, sizeDp: Float = 24f, tint: Int = TEXT_SECONDARY) =
        ImageView(view.context).apply {
            setImageResource(drawable)
            layoutParams = android.widget.LinearLayout.LayoutParams(view.dp(sizeDp), view.dp(sizeDp))
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            RetroUi.tint(this, tint)
        }

    fun alpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
