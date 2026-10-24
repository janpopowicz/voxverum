package pl.voxverum.app.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

enum class Tone(val background: Int) {
    NEUTRAL(Color.parseColor("#1F3A5F")),
    OK(Color.parseColor("#2E7D32")),
    WARN(Color.parseColor("#F9A825")),
    DANGER(Color.parseColor("#C62828")),
}

data class OverlayModel(val tone: Tone, val title: String, val detail: String)

/**
 * Baner wyświetlany nad rozmową jako `TYPE_ACCESSIBILITY_OVERLAY`.
 *
 * Ma dwie role: pokazuje użytkownikowi ostrzeżenia i spełnia warunek Androida
 * „UI usługi ułatwień dostępu na wierzchu”, bez którego nie dostaniemy mikrofonu
 * równolegle z rozmową VoIP. Dlatego po zwinięciu zostaje mała pigułka, a nie znika.
 */
class OverlayController(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private lateinit var title: TextView
    private lateinit var detail: TextView
    private var collapsed = false

    val isShown: Boolean get() = root != null

    fun show(model: OverlayModel) {
        if (root == null) attach()
        render(model)
    }

    fun render(model: OverlayModel) {
        val view = root ?: return
        (view.background as GradientDrawable).setColor(model.tone.background)
        title.text = model.title
        detail.text = model.detail
        val textColor = if (model.tone == Tone.WARN) Color.BLACK else Color.WHITE
        title.setTextColor(textColor)
        detail.setTextColor(textColor)
    }

    fun hide() {
        root?.let { windowManager.removeView(it) }
        root = null
        collapsed = false
    }

    private fun attach() {
        val layout = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat() }
            elevation = dp(6).toFloat()
        }
        title = TextView(service).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); paint.isFakeBoldText = true }
        detail = TextView(service).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) }
        layout.addView(title)
        layout.addView(detail)
        layout.setOnClickListener { toggleCollapsed() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }
        windowManager.addView(layout, params)
        root = layout
    }

    private fun toggleCollapsed() {
        collapsed = !collapsed
        detail.visibility = if (collapsed) View.GONE else View.VISIBLE
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            service.resources.displayMetrics,
        ).toInt()
}
