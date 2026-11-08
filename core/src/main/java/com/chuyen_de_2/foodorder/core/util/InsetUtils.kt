package com.chuyen_de_2.foodorder.core.util

import android.app.Activity
import android.util.TypedValue
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

/**
 * Tiện ích xử lý Window Insets cho giao diện Edge-to-Edge trên Android 14, 15+
 * Đảm bảo các thành phần UI (Header, Toolbar, Bottom Navigation) không bị trùng lặp
 * với thanh trạng thái (Status Bar: pin, mạng, giờ) và thanh điều hướng (Navigation Bar).
 */
object InsetUtils {

    /**
     * Bật chế độ Edge-to-Edge chuẩn và chỉnh màu icon status bar
     */
    fun initEdgeToEdge(activity: Activity, isLightStatusBar: Boolean = true) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        setLightStatusBar(activity, isLightStatusBar)
    }

    /**
     * Lấy chiều cao status bar dự phòng chuẩn xác nếu hệ thống chưa trả về Insets
     */
    fun getFallbackStatusBarHeight(context: android.content.Context): Int {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) {
            context.resources.getDimensionPixelSize(resId)
        } else {
            (36 * context.resources.displayMetrics.density).toInt()
        }
    }

    /**
     * Áp dụng padding top cho Header Bar (ví dụ: LinearLayout headerBar ở Home).
     * Giữ nguyên padding top ban đầu trong XML và cộng thêm chiều cao thanh trạng thái + cutout + extraPadding để không bị sát thanh pin và giờ.
     */
    fun applyStatusBarPadding(view: View, extraTopDp: Int = 8) {
        val initialPaddingTop = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val topInset = if (insets.top > 0) insets.top else getFallbackStatusBarHeight(v.context)
            val extraPx = (extraTopDp * v.resources.displayMetrics.density).toInt()
            v.updatePadding(top = initialPaddingTop + topInset + extraPx)
            windowInsets
        }
        requestApplyInsetsWhenAttached(view)
    }

    /**
     * Áp dụng Insets cho Toolbar / MaterialToolbar:
     * - Thêm padding top bằng chiều cao status bar + extraPadding (tránh cấn pin và giờ).
     * - Tăng chiều cao của Toolbar = actionBarSize + insets.top + extraPadding.
     */
    fun applyToolbarInsets(toolbar: View, extraTopDp: Int = 8) {
        val initialPaddingTop = toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val topInset = if (insets.top > 0) insets.top else getFallbackStatusBarHeight(v.context)
            val extraPx = (extraTopDp * v.resources.displayMetrics.density).toInt()

            v.updatePadding(top = initialPaddingTop + topInset + extraPx)

            val context = v.context
            val typedValue = TypedValue()
            val baseHeight = if (context.theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
                TypedValue.complexToDimensionPixelSize(typedValue.data, context.resources.displayMetrics)
            } else {
                (56 * context.resources.displayMetrics.density).toInt()
            }
            v.updateLayoutParams {
                height = baseHeight + topInset + extraPx
            }
            windowInsets
        }
        requestApplyInsetsWhenAttached(toolbar)
    }

    /**
     * Áp dụng padding bottom cho thanh điều hướng đáy (Bottom Navigation, Bottom Bar)
     * để tránh bị che bởi thanh điều hướng hệ thống (3 nút ảo hoặc thanh vuốt cử chỉ).
     */
    fun applyNavigationBarPadding(view: View) {
        val initialPaddingBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            if (insets.bottom > 0) {
                v.updatePadding(bottom = initialPaddingBottom + insets.bottom)
            }
            windowInsets
        }
        requestApplyInsetsWhenAttached(view)
    }

    /**
     * Điều chỉnh màu sắc icon của thanh trạng thái (Status Bar):
     * - isLight = true: Icon màu đen/xám tối (dùng cho màn hình nền trắng/xám sáng như Login, Register).
     * - isLight = false: Icon màu trắng (dùng cho màn hình có Header màu cam đậm Oishi Food).
     */
    fun setLightStatusBar(activity: Activity, isLight: Boolean) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars = isLight
    }

    private fun requestApplyInsetsWhenAttached(view: View) {
        if (view.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(view)
        } else {
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(v)
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
    }
}
