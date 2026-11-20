# RSS 阅读器（B3 + P3 课程设计）

Kotlin + Room + WorkManager 的离线优先 RSS 阅读器，同一仓库内两个业务模块：

- `:app` —— RSS 阅读器主模块（B3），桌面图标入口
- `:core_data` —— Room 数据库 / 解析器 / 仓库，被上面两个模块共用
- `:feature_notification` —— 通知提醒中心（P3），可被主 App 唤起，也可独立启动

## 一、环境要求

| 项 | 版本 |
| --- | --- |
| Android Studio | Narwhal 及以上（AGP 9.x） |
| AGP | 9.4.0 |
| Gradle | 9.6.0（仓库自带 wrapper） |
| Kotlin | 2.2.10（AGP 9 内置，无需单独插件） |
| JDK | 使用 Android Studio 自带 JBR 即可 |
| compileSdk / targetSdk | 37 |
| minSdk | 24 |
| 依赖 | Room 2.7.1、WorkManager 2.10.0、AppCompat 1.6.1、Material 1.10.0、Coroutines 1.9.0 |

## 二、打开与运行

1. Android Studio → **Open** → 选择本目录（含 `settings.gradle.kts` 的这一层），**不要**选上层文件夹。
2. 首次打开会自动 Gradle Sync（要联网下载 Room / WorkManager 依赖，约 3~15 分钟）。
3. 首次打开若提示 SDK 路径不对，把 `local.properties` 里的 `sdk.dir` 改成自己的 Android SDK 路径，或直接删掉该文件让 AS 重新生成。
4. 运行 `app` 配置到模拟器 / 真机。

命令行构建（Windows，JAVA_HOME 指向 AS 内置 JBR）：

```powershell
$env:JAVA_HOME="D:\Android\android s\jbr"
$env:ANDROID_HOME="D:\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 三、第一次运行的预期表现

- 首启自动写入 5 个内置订阅源，其中第一个是打包在 `assets/sample_feed.xml` 的**本地示例源**，断网也有 5 篇文章可读。
- 若数据库里没有文章，会自动同步一次，列表出现文章。
- **自动更新（B+C）**：
  - C：进入 App / 回到前台时，若距上次同步超过 **30 分钟**，静默刷新一次（1 分钟内不重复判断）；
  - B：`SyncWorker` 每小时跑一轮（`auto_sync_periodic`，仅在联网时执行），与通知开关**无关**，用于息屏/未打开时也保持更新。
- 如果模拟器上曾装过同包名（`com.example.rssreader`）的旧版本，请先卸载或清除数据——新旧数据库文件不同名，但表结构校验更严格，混用可能报 `invalid schema`。

## 四、界面与功能入口

| 界面 | 类 | 入口 |
| --- | --- | --- |
| 文章列表（3 个 Tab：全部 / 未读 / 星标） | `ui.MainActivity` | 桌面图标 |
| 文章详情 | `ui.ArticleDetailActivity` | 点列表项 |
| 订阅源列表（按源筛选） | `ui.FeedListActivity` | 右上角菜单「订阅源」 |
| 订阅源管理（增删） | `ui.FeedManageActivity` | 右上角菜单「订阅源管理」 |
| 通知提醒中心（P3） | `notify.ui.NotificationCenterActivity` | 菜单「通知提醒中心」，或见下方 adb 命令 |

## 五、P3 通知中心的两种打开方式

```bash
# 方式一：显式组件
adb shell am start -n com.example.rssreader/com.example.rssreader.notify.ui.NotificationCenterActivity

# 方式二：隐式 action（证明它对内对外都可被唤起）
adb shell am start -a com.example.rssreader.notify.action.OPEN_CENTER
```

## 六、答辩演示脚本（约 3 分钟）

1. **列表三态**：进列表 → 看到文章；开飞行模式点刷新 → 出现错误条「N 个源更新失败……当前显示的是本地缓存」+ 重试按钮，列表数据不消失。
2. **离线读全文**：断网点进任意一篇 → 正文完整显示，底部提示「正文已缓存在本地」。
3. **已读 / 星标**：详情页点右上角星标 → 返回列表，首条变灰、星标变实心、Tab 计数变化。
4. **按源筛选**：菜单 → 订阅源 → 点某个源 → 标题变成源名，只显示该源文章。
5. **添加订阅源**：订阅源管理 → 右上「+」→ 名称留空、填 `https://www.infoq.cn/feed` → 自动回填源站标题并拉取文章。
6. **长按菜单**：长按列表项 → 收藏 / 标为未读 / 在浏览器中打开。
7. **全部标为已读**：菜单 → 全部标为已读 → 未读归零。
8. **通知提醒中心**：开关打开 → 选 2 小时 → 「发送一条测试通知」→ 下拉通知栏，通知上带「全部标为已读」按钮 → 点击后回到列表，未读数归零（这就是通知内直接改库）。
9. **权限降级**：`adb shell pm revoke com.example.rssreader android.permission.POST_NOTIFICATIONS` → 通知中心出现黄色提示条「通知权限未开启……阅读、离线缓存、已读星标等功能不受影响」，此时刷新、阅读一切正常。
10. **自动更新（C）**：把某个源的 `lastSync` 改成很久以前，再重新进入 App，列表会自己刷新一次。
    ```bash
    adb shell "run-as com.example.rssreader sqlite3 databases/rss_reader_kotlin.db \"update feeds set lastSync=1700000000000;\""
    ```
11. **自动更新（B）**：`adb shell dumpsys jobscheduler | findstr SyncWorker` 能看到 `#SyncWorker#` 任务挂着（网络约束已满足），即为排班成功。

## 七、目录结构

```
RssReader2/
├── app/                       # B3 主模块（4 个界面 + ViewModel + 协程）
│   └── src/main/java/com/example/rssreader/
│       ├── RssApp.kt          # 建通知渠道、播种内置源、补排周期任务
│       ├── work/              # SyncWorker + SyncScheduler（1 小时自动同步，与通知开关解耦）
│       └── ui/                # MainActivity / ArticleDetail / FeedList / FeedManage + VM + Adapter
├── core_data/                 # 共用数据层
│   └── src/main/
│       ├── assets/sample_feed.xml      # 离线示例源
│       └── java/com/example/rssreader/core/
│           ├── data/          # Entity / DAO / Database / Repository（Room + 事务 + 缓存策略）
│           ├── net/           # RssFetcher（编码判定）+ RssParser（RSS2.0/Atom/RSS1.0）
│           └── util/          # TimeUtil / HtmlText
├── feature_notification/      # P3 通知提醒中心
│   └── src/main/java/com/example/rssreader/notify/
│       ├── ReminderWorker.kt  # CoroutineWorker：同步 → 统计未读 → 去重 → 发通知
│       ├── ReminderScheduler.kt  # WorkManager 周期任务（2 小时默认）
│       ├── NotificationHelper.kt # 通知渠道 + PendingIntent + 权限判断
│       ├── MarkAllReadReceiver.kt# 通知内「全部标为已读」动作
│       └── ui/NotificationCenterActivity.kt
├── docs/screenshots/          # 模拟器实测截图
└── README.md
```

## 八、常见问题

- **`Cannot add extension with name 'kotlin'`**：AGP 9 已内置 Kotlin 支持，各模块 `build.gradle.kts` 里不能再加 `org.jetbrains.kotlin.android` 插件。
- **`Using kotlin.sourceSets DSL ... is not allowed`**：KSP 需要通过 `kotlin.sourceSets` 注入生成目录，根 `gradle.properties` 必须保留 `android.disallowKotlinSourceSets=false`。
- **构建日志出现 `Kotlin does not yet support 25 JDK target`**：JBR 是 JDK 25，Kotlin 自动回退到 JVM_24，只是提示，不影响编译。
- **`Pre-packaged database has an invalid schema`**：说明当前设备上残留了旧版数据库，卸载重装或清除数据即可；本工程的数据库文件名是 `rss_reader_kotlin.db`，正是为避开这个冲突。
- **标题被状态栏压住、右上角刷新图标"消失"**：`targetSdk 37` 起系统强制 edge-to-edge，布局会画到状态栏/挖孔下面，工具栏和系统电量图标叠在一起。处理在 `ui/WindowInsetsExt.kt`：把 `statusBars + displayCutout` 的高度补成 `AppBarLayout` 的顶部 padding，四个页面和通知中心都加了这一步。
