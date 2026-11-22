package com.lin0721.linmusic.core.player

import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PlayerListenerBindingTest {

    private class RecordingPlayer {
        val listeners = mutableListOf<Player.Listener>()
        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "addListener" -> { listeners += args[0] as Player.Listener; null }
                "removeListener" -> { listeners.remove(args[0] as Player.Listener); null }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                "toString" -> "RecordingPlayer"
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Player
    }

    private val listener = object : Player.Listener {}

    // 联动唤醒先拉起服务：本机播放器早于监听就位
    @Test fun localPlayerAttachedBeforeListenerStillReceivesListener() {
        val binding = PlayerListenerBinding()
        val local = RecordingPlayer()
        val controller = RecordingPlayer()

        binding.bindTo(local.player)
        assertNull(binding.target)

        binding.setListener(listener)
        binding.bindTo(local.player)

        assertEquals(listOf(listener), local.listeners)
        assertEquals(emptyList<Player.Listener>(), controller.listeners)
    }

    @Test fun rebindingSameTargetDoesNotDuplicate() {
        val binding = PlayerListenerBinding()
        val local = RecordingPlayer()
        binding.setListener(listener)
        repeat(3) { binding.bindTo(local.player) }
        assertEquals(1, local.listeners.size)
    }

    @Test fun switchingTargetMovesListener() {
        val binding = PlayerListenerBinding()
        val local = RecordingPlayer()
        val controller = RecordingPlayer()
        binding.setListener(listener)

        binding.bindTo(controller.player)
        binding.bindTo(local.player)
        assertEquals(emptyList<Player.Listener>(), controller.listeners)
        assertEquals(listOf(listener), local.listeners)

        binding.bindTo(controller.player)
        assertEquals(listOf(listener), controller.listeners)
        assertEquals(emptyList<Player.Listener>(), local.listeners)
        assertSame(controller.player, binding.target)
    }

    @Test fun clearingListenerDetachesFromTarget() {
        val binding = PlayerListenerBinding()
        val local = RecordingPlayer()
        binding.setListener(listener)
        binding.bindTo(local.player)

        binding.setListener(null)
        assertEquals(emptyList<Player.Listener>(), local.listeners)
        assertNull(binding.target)
    }

    @Test fun bindingNullDetaches() {
        val binding = PlayerListenerBinding()
        val local = RecordingPlayer()
        binding.setListener(listener)
        binding.bindTo(local.player)

        binding.bindTo(null)
        assertEquals(emptyList<Player.Listener>(), local.listeners)
        assertNull(binding.target)
    }
}
