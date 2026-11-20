package com.example.rssreader.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * targetSdk 37 起系统强制 edge-to-edge：布局会一直画到状态栏、挖孔和导航栏下面。
 * 结果是标题被状态栏压住、右上角菜单图标和系统电量图标叠在一起——看起来像"刷新按钮没了"。
 *
 * 这里把系统栏高度补成 padding，让顶部栏真正落在状态栏下方。
 * 用 statusBars + displayCutout 一起取，是为了 Pixel 的左上挖孔也能让出位置。
 */
fun View.applySystemBarTopPadding() {
    val originTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updatePadding(top = originTop + bars.top, left = bars.left, right = bars.right)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/** 给可滚动内容底部留出导航栏高度，避免最后一条被系统手势条盖住。 */
fun View.applySystemBarBottomPadding() {
    val originBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updatePadding(bottom = originBottom + bars.bottom, left = bars.left, right = bars.right)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
