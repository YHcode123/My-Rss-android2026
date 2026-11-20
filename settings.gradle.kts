pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RssReader2"

// 主模块：RSS 阅读器（B3）
include(":app")
// 数据层：Room 数据库 + 网络抓取解析，:app 与 :feature_notification 共同依赖
include(":core_data")
// 伴生模块：通知提醒中心（P3）
include(":feature_notification")
