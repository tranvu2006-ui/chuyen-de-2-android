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
 * với thanh trạng thái (Status Bar: pin, mạng, giờ), bàn phím ảo (IME) và thanh điều hướng (Navigation Bar).
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
     * Tự động đẩy nội dung lên khi bàn phím ảo (IME) xuất hiện để không bị che các nút bấm/nội dung ở cuối.
     * - Khi bàn phím bật: Tăng padding bottom đúng bằng chiều cao bàn phím và cuộn mượt để thấy rõ phần đang nhập cùng nút hành động phía dưới.
     * - Khi bàn phím ẩn: Trả lại padding bottom bình thường (theo navigation bar).
     * - Đồng thời bảo vệ phần đỉnh (top) không bị che bởi thanh trạng thái (status bar / camera cutout).
     */
    fun applyImeAndSystemInsets(
        scrollView: View,
        applyTopInset: Boolean = true,
        autoScrollOnIme: Boolean = true
    ) {
        val initialPaddingTop = scrollView.paddingTop
        val initialPaddingBottom = scrollView.paddingBottom
        var previousImeBottom = 0

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, windowInsets ->
            val systemBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())

            val topPadding = if (applyTopInset) {
                val topInset = if (systemBars.top > 0) systemBars.top else getFallbackStatusBarHeight(v.context)
                initialPaddingTop + topInset
            } else {
                initialPaddingTop
            }

            val currentImeBottom = imeInsets.bottom
            val bottomPadding = if (currentImeBottom > 0) {
                initialPaddingBottom + currentImeBottom
            } else {
                initialPaddingBottom + navInsets.bottom
            }

            v.updatePadding(top = topPadding, bottom = bottomPadding)

            if (autoScrollOnIme && currentImeBottom > 0 && previousImeBottom == 0) {
                // Bàn phím vừa bật lên -> cuộn mượt để đưa nội dung và nút bấm phía dưới lên trên bàn phím
                v.postDelayed({
                    val focusedView = v.findFocus()
                    if (focusedView != null) {
                        val rect = android.graphics.Rect()
                        focusedView.getDrawingRect(rect)
                        try {
                            if (v is android.view.ViewGroup) {
                                v.offsetDescendantRectToMyCoords(focusedView, rect)
                            }
                            // Thêm khoảng đệm phía dưới để hiển thị cả nút bấm / countdown ngay bên dưới trường nhập
                            val extraBottomPx = (150 * v.resources.displayMetrics.density).toInt()
                            val targetBottom = rect.bottom + extraBottomPx
                            val visibleHeight = v.height - v.paddingTop - v.paddingBottom
                            if (visibleHeight > 0) {
                                val visibleBottom = v.scrollY + visibleHeight
                                if (targetBottom > visibleBottom) {
                                    val scrollDelta = targetBottom - visibleBottom
                                    if (v is android.widget.ScrollView) {
                                        v.smoothScrollBy(0, scrollDelta)
                                    } else if (v is androidx.core.widget.NestedScrollView) {
                                        v.smoothScrollBy(0, scrollDelta)
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            if (v is android.widget.ScrollView) {
                                val child = v.getChildAt(0)
                                if (child != null) v.smoothScrollTo(0, child.bottom)
                            } else if (v is androidx.core.widget.NestedScrollView) {
                                val child = v.getChildAt(0)
                                if (child != null) v.smoothScrollTo(0, child.bottom)
                            }
                        }
                    } else {
                        val child = (v as? android.view.ViewGroup)?.getChildAt(0)
                        if (child != null) {
                            if (v is android.widget.ScrollView) {
                                v.smoothScrollTo(0, child.bottom)
                            } else if (v is androidx.core.widget.NestedScrollView) {
                                v.smoothScrollTo(0, child.bottom)
                            }
                        }
                    }
                }, 120)
            }
            previousImeBottom = currentImeBottom
            windowInsets
        }
        requestApplyInsetsWhenAttached(scrollView)
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
