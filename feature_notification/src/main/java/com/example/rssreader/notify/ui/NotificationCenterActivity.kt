package com.example.rssreader.notify.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.rssreader.core.data.RssRepository
import com.example.rssreader.core.util.TimeUtil
import com.example.rssreader.notify.NotificationHelper
import com.example.rssreader.notify.NotifyPrefs
import com.example.rssreader.notify.R
import com.example.rssreader.notify.ReminderScheduler
import com.google.android.material.appbar.MaterialToolbar

import kotlinx.coroutines.launch

/**
 * 通知提醒中心（伴生模块的独立入口页）。
 *
 * 它自己就能被独立唤起（见 AndroidManifest 里的自定义 action），
 * 从主 App 的「更多」菜单也可以进来，两条路径共用这一套界面。
 */
class NotificationCenterActivity : AppCompatActivity() {

    private lateinit var prefs: NotifyPrefs
    private lateinit var repository: RssRepository

    private lateinit var switchEnabled: SwitchCompat
    private lateinit var intervalGroup: RadioGroup
    private lateinit var textUnread: TextView
    private lateinit var textStatus: TextView
    private lateinit var textPermissionHint: TextView
    private lateinit var buttonRunNow: Button

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        renderPermissionState()
        if (granted) {
            Toast.makeText(this, "通知权限已开启，可以收到未读提醒了", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_center)

        prefs = NotifyPrefs(this)
        repository = RssRepository.get(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // targetSdk 37 强制 edge-to-edge，这里把状态栏高度补进工具栏的顶部 padding
        val originTop = toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(top = originTop + bars.top, left = bars.left, right = bars.right)
            insets
        }
        ViewCompat.requestApplyInsets(toolbar)

        switchEnabled = findViewById(R.id.switch_enabled)
        intervalGroup = findViewById(R.id.radio_group_interval)
        textUnread = findViewById(R.id.text_unread)
        textStatus = findViewById(R.id.text_status)
        textPermissionHint = findViewById(R.id.text_permission_hint)
        buttonRunNow = findViewById(R.id.button_run_now)

        switchEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
            if (checked) {
                ReminderScheduler.schedule(this, prefs.intervalHours)
                ensurePermission()
            } else {
                ReminderScheduler.cancel(this)
                NotificationHelper.cancel(this)
            }
            renderState()
        }

        intervalGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.intervalHours = when (checkedId) {
                R.id.radio_1h -> 1
                R.id.radio_2h -> 2
                R.id.radio_4h -> 4
                else -> 8
            }
            if (prefs.enabled) ReminderScheduler.schedule(this, prefs.intervalHours)
            renderState()
        }

        buttonRunNow.setOnClickListener { runCheckNow() }

        findViewById<Button>(R.id.button_test_notification).setOnClickListener {
            if (!NotificationHelper.canPost(this)) {
                ensurePermission()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val unread = repository.unreadCount()
                if (unread <= 0) {
                    Toast.makeText(
                        this@NotificationCenterActivity,
                        "当前没有未读文章，先去阅读器读几篇或刷新一下",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    NotificationHelper.showUnreadNotification(
                        this@NotificationCenterActivity, unread
                    )
                }
            }
        }

        findViewById<Button>(R.id.button_open_settings).setOnClickListener {
            openNotificationSettings()
        }

        renderState()
        renderPermissionState()

        // 首次进入如果发现该开但没开，顺手把周期任务补排上
        if (prefs.enabled) ReminderScheduler.schedule(this, prefs.intervalHours)
    }

    override fun onResume() {
        super.onResume()
        renderPermissionState()
        lifecycleScope.launch {
            val unread = repository.unreadCount()
            textUnread.text = "当前未读 $unread 篇"
        }
    }

    private fun runCheckNow() {
        if (!prefs.enabled) {
            Toast.makeText(this, "请先打开上面的开关", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.lastNotifiedUnread = -1
        ReminderScheduler.runOnce(this)
        Toast.makeText(this, "已提交一次检查，几秒后若仍有未读会收到通知", Toast.LENGTH_SHORT).show()
    }

    private fun renderState() {
        switchEnabled.isChecked = prefs.enabled

        when (prefs.intervalHours) {
            1 -> intervalGroup.check(R.id.radio_1h)
            2 -> intervalGroup.check(R.id.radio_2h)
            4 -> intervalGroup.check(R.id.radio_4h)
            else -> intervalGroup.check(R.id.radio_8h)
        }

        val lastTime = prefs.lastNotifyTime
        textStatus.text = if (lastTime > 0) {
            "上次提醒：${TimeUtil.absolute(lastTime)}　·　" +
                if (prefs.enabled) "每 ${prefs.intervalHours} 小时检查一次" else "提醒已关闭"
        } else {
            if (prefs.enabled) "每 ${prefs.intervalHours} 小时检查一次，尚未提醒过"
            else "提醒已关闭"
        }
    }

    private fun renderPermissionState() {
        val canPost = NotificationHelper.canPost(this)
        textPermissionHint.visibility = if (canPost) TextView.GONE else TextView.VISIBLE
        textPermissionHint.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "通知权限未开启：将收不到未读提醒。阅读、离线缓存、已读星标等功能不受影响，可随时到系统设置里开启。"
        } else {
            "系统已关闭本应用的通知：将收不到未读提醒，其它功能不受影响。"
        }
    }

    private fun ensurePermission() {
        if (NotificationHelper.canPost(this)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    private fun openNotificationSettings() {
        val intent = Intent().apply {
            action = "android.settings.APP_NOTIFICATION_SETTINGS"
            putExtra("android.provider.extra.APP_PACKAGE", packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }.onFailure {
            runCatching {
                startActivity(
                    Intent(
                        "android.settings.APPLICATION_DETAILS_SETTINGS",
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
    }

    companion object {
        /** 供外部（含 adb / 其它 App）独立唤起本页的自定义 action。 */
        const val ACTION_OPEN_CENTER = "com.example.rssreader.notify.action.OPEN_CENTER"

        fun intentFor(context: android.content.Context): Intent =
            Intent(context, NotificationCenterActivity::class.java).apply {
                putExtras(bundleOf())
            }
    }
}
