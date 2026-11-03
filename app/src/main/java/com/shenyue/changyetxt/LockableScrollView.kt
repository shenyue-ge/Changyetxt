package com.shenyue.changyetxt

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * 可锁定滑动的 ScrollView。
 * 锁定后忽略所有触摸滚动（拖动无效），但子 View 的点击/长按、
 * 以及代码调用的 scrollTo / smoothScrollBy 仍然正常工作。
 */
class LockableScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    var touchScrollEnabled: Boolean = true

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (!touchScrollEnabled) return false
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (!touchScrollEnabled) return false
        return super.onTouchEvent(ev)
    }
}
