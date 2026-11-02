package com.lin0721.linmusic.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// 生成启动与核心交互路径的 Baseline Profile
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateStartupAndNavigationProfile() {
        rule.collect(
            packageName = "com.lin0721.linmusic",
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    }

    @Test
    fun generateCriticalUserJourneysProfile() {
        rule.collect(
            packageName = "com.lin0721.linmusic",
            includeInStartupProfile = false
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            // 依次切换底部导航标签，预热各主页面组合与测量路径
            listOf("搜索", "音乐库", "本地音乐", "主页").forEach { tabText ->
                device.findObject(By.text(tabText))?.let { tab ->
                    tab.click()
                    device.waitForIdle()
                }
            }

            // 切换到音乐库并尝试进入首个歌单滑动列表
            device.findObject(By.text("音乐库"))?.let { tab ->
                tab.click()
                device.waitForIdle()
            }

            val scrollable = device.wait(Until.findObject(By.scrollable(true)), 2_000L)
            scrollable?.let {
                it.fling(Direction.DOWN)
                device.waitForIdle()
                it.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }
}
