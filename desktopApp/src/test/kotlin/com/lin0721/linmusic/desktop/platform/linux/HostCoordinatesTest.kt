package com.lin0721.linmusic.desktop.platform.linux

import com.lin0721.linmusic.desktop.platform.native.linux.WaylandOutputInfo
import com.lin0721.linmusic.desktop.platform.native.linux.sni.hostPointToAwt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.awt.Point
import java.awt.Rectangle

// 托盘菜单锚点换算：用本机实测数据锁定两种宿主坐标系（规范逻辑坐标 / Plasma 混合坐标）。
// 真实数据：双 4K/1.5 缩放，图标物理位置约 (7076,2129)，Plasma 报 (5785,2108)。
class HostCoordinatesTest {

    // 左屏 DP-3 逻辑 (0,0)，右屏 DP-2 逻辑 x=2560；物理上左屏 x=0、右屏 x=3840
    private val outputs = listOf(
        WaylandOutputInfo(3840, 2160, logicalX = 0, logicalY = 0, logicalWidth = 2560, logicalHeight = 1440),
        WaylandOutputInfo(3840, 2160, logicalX = 2560, logicalY = 0, logicalWidth = 2560, logicalHeight = 1440),
    )
    private val screens = listOf(
        Rectangle(0, 0, 3840, 2160),
        Rectangle(3840, 0, 3840, 2160),
    )

    @Test
    fun `逻辑原点加输出内物理偏移的混合坐标换算回物理像素`() {
        // 指针滞留在进入面板处，但仍在右屏范围内
        val mapped = hostPointToAwt(Point(5785, 2108), outputs, screens, pointer = Point(6980, 2093))
        assertEquals(Point(7065, 2108), mapped)
    }

    @Test
    fun `规范逻辑坐标按缩放比例换算`() {
        // 同一图标的逻辑坐标 = (2560 + (7076-3840)/1.5, 2129/1.5) ≈ (4717,1419)
        val mapped = hostPointToAwt(Point(4717, 1419), outputs, screens, pointer = Point(7076, 2129))
        assertEquals(Point(7076, 2129), mapped)
    }

    @Test
    fun `换算结果必须落在屏幕内`() {
        // 混合模型下该点在右屏物理范围内，换算有效
        assertEquals(Point(7679, 2159), hostPointToAwt(Point(6399, 2159), outputs, screens, pointer = Point(7000, 2100)))
        // 无任何输出包含该宿主坐标时放弃换算
        assertNull(hostPointToAwt(Point(-500, -500), outputs, screens, pointer = Point(100, 100)))
    }

    @Test
    fun `缺少输出或屏幕信息时不换算`() {
        assertNull(hostPointToAwt(Point(10, 10), emptyList(), screens, pointer = null))
        assertNull(hostPointToAwt(Point(10, 10), outputs, emptyList(), pointer = null))
    }

    @Test
    fun `无指针时按排布同序配对并优先规范模型`() {
        // (4500,1000) 在右屏逻辑矩形内：无指针时选同序配对的右屏，并按规范比例换算
        assertEquals(Point(6750, 1500), hostPointToAwt(Point(4500, 1000), outputs, screens, pointer = null))
    }

    @Test
    fun `指针所在屏决定候选屏`() {
        // 指针在左屏时候选收窄到左屏（避免把菜单放到另一块屏上）
        assertEquals(Point(3225, 2108), hostPointToAwt(Point(5785, 2108), outputs, screens, pointer = Point(2000, 800)))
    }
}
