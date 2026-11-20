package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.lanlink.ConnectedPeerInfo
import com.lin0721.linmusic.core.lanlink.PlaybackTarget
import com.lin0721.linmusic.feature.settings.ui.SettingsSearchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanLinkMediaRouteStateTest {

    @Test
    fun volumePercentMapsToTwentyStepsAndBack() {
        assertEquals(0, LanLinkRemoteVolume.percentToStep(-10))
        assertEquals(0, LanLinkRemoteVolume.percentToStep(0))
        assertEquals(0, LanLinkRemoteVolume.percentToStep(2))
        assertEquals(1, LanLinkRemoteVolume.percentToStep(3))
        assertEquals(1, LanLinkRemoteVolume.percentToStep(5))
        assertEquals(10, LanLinkRemoteVolume.percentToStep(50))
        assertEquals(19, LanLinkRemoteVolume.percentToStep(97))
        assertEquals(20, LanLinkRemoteVolume.percentToStep(98))
        assertEquals(20, LanLinkRemoteVolume.percentToStep(100))
        assertEquals(20, LanLinkRemoteVolume.percentToStep(150))

        assertEquals(0, LanLinkRemoteVolume.stepToPercent(-1))
        assertEquals(0, LanLinkRemoteVolume.stepToPercent(0))
        assertEquals(25, LanLinkRemoteVolume.stepToPercent(5))
        assertEquals(100, LanLinkRemoteVolume.stepToPercent(20))
        assertEquals(100, LanLinkRemoteVolume.stepToPercent(25))
    }

    @Test
    fun volumeSteppingAndUnmuteRespectBounds() {
        assertEquals(1, LanLinkRemoteVolume.increaseStep(0))
        assertEquals(20, LanLinkRemoteVolume.increaseStep(98))
        assertEquals(20, LanLinkRemoteVolume.increaseStep(100))

        assertEquals(0, LanLinkRemoteVolume.decreaseStep(0))
        assertEquals(0, LanLinkRemoteVolume.decreaseStep(5))
        assertEquals(9, LanLinkRemoteVolume.decreaseStep(50))

        assertEquals(42, LanLinkRemoteVolume.unmutePercent(42))
        assertEquals(100, LanLinkRemoteVolume.unmutePercent(0))
        assertEquals(100, LanLinkRemoteVolume.unmutePercent(-5))
    }

    @Test
    fun publishedRouteRequiresLanLinkKeepaliveAndConnectedPeer() {
        val peer = ConnectedPeerInfo(deviceId = "pc-1", deviceName = "Workstation")

        assertNull(resolvePublishedLanLinkRoute(false, true, peer, 50))
        assertNull(resolvePublishedLanLinkRoute(true, false, peer, 50))
        assertNull(resolvePublishedLanLinkRoute(true, true, null, 50))

        val snapshot = resolvePublishedLanLinkRoute(true, true, peer, 65)
        assertNotNull(snapshot)
        assertEquals("lanlink_route_pc-1", snapshot?.routeId)
        assertEquals("pc-1", snapshot?.peerDeviceId)
        assertEquals("Workstation", snapshot?.peerDeviceName)
        assertEquals(13, snapshot?.volumeStep)

        val fallbackName = resolvePublishedLanLinkRoute(
            lanLinkEnabled = true,
            keepaliveEnabled = true,
            connectedPeer = ConnectedPeerInfo("pc-2", ""),
            volumePercent = 100
        )
        assertEquals("桌面端", fallbackName?.peerDeviceName)
    }

    @Test
    fun keepaliveGateControlsRemoteSessionAndTaskRemovedBehavior() {
        val remoteTarget = PlaybackTarget.Remote(ConnectedPeerInfo("pc-1", "PC"))

        assertTrue(shouldUseRemoteSessionPlayer(true, remoteTarget))
        assertFalse(shouldUseRemoteSessionPlayer(false, remoteTarget))
        assertFalse(shouldUseRemoteSessionPlayer(true, PlaybackTarget.Local))
        assertFalse(shouldUseRemoteSessionPlayer(false, PlaybackTarget.Local))

        assertTrue(shouldKeepServiceOnTaskRemoved(true, false, false, false, false))
        assertTrue(shouldKeepServiceOnTaskRemoved(false, true, true, true, false))
        assertTrue(shouldKeepServiceOnTaskRemoved(false, true, true, false, true))
        assertFalse(shouldKeepServiceOnTaskRemoved(false, false, true, true, true))
        assertFalse(shouldKeepServiceOnTaskRemoved(false, true, false, true, true))
    }

    @Test
    fun settingsSearchIndexContainsBackgroundKeepaliveEntry() {
        val results = SettingsSearchIndex.search("后台保持连接")
        assertTrue(results.any { it.title == "后台保持连接" })
    }
}
