package com.lin0721.linmusic.core.plugin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCommandsTest {

    private class FakeCommand(override val title: String, private val result: Boolean = true) : CommandExtension {
        val received = mutableListOf<JsonElement>()

        override suspend fun execute(args: JsonElement): Boolean {
            received += args
            return result
        }
    }

    private val registry = ExtensionRegistry()
    private val notices = PluginNotices()
    private val commands = PluginCommands(registry, notices, CoroutineScope(Dispatchers.Unconfined))

    private fun command(id: String, command: CommandExtension) =
        registry.register(Extension(ExtensionPoints.COMMAND, "c.a", "插件", id, command))

    private fun menu(id: String, location: PluginMenuLocation) =
        registry.register(Extension(ExtensionPoints.MENU, "c.a", "插件", "${location.key}/$id", PluginMenuEntry(id, location)))

    @Test
    fun `菜单只显示已注册的命令并按位置区分`() {
        command("share", FakeCommand("分享"))
        menu("share", PluginMenuLocation.SONG)
        menu("pending", PluginMenuLocation.SONG)
        menu("share", PluginMenuLocation.TOOLBAR)

        assertEquals(listOf("分享"), commands.itemsNow(PluginMenuLocation.SONG).map { it.title })
        assertEquals(1, commands.itemsNow(PluginMenuLocation.TOOLBAR).size)
    }

    @Test
    fun `执行命令时传入参数`() {
        val share = FakeCommand("分享")
        command("share", share)
        menu("share", PluginMenuLocation.SONG)
        val args = buildJsonObject { put("title", "晴天") }

        commands.run(commands.itemsNow(PluginMenuLocation.SONG).single(), args)

        assertEquals(listOf<JsonElement>(args), share.received)
    }

    @Test
    fun `执行失败时提示用户`() = runBlocking {
        command("share", FakeCommand("分享", result = false))
        menu("share", PluginMenuLocation.SONG)
        val item = commands.itemsNow(PluginMenuLocation.SONG).single()
        val message = async(Dispatchers.Unconfined) { withTimeout(2_000) { notices.messages.first() } }

        commands.run(item)

        assertEquals("插件：命令执行失败", message.await())
    }

    private fun field(type: PluginSettingType, default: JsonElement, vararg options: PluginOption) =
        PluginSettingField("key", type, "标题", default, options.toList(), min = 0f, max = 10f, step = 2f)

    @Test
    fun `设置项取值按类型校验`() {
        val toggle = field(PluginSettingType.TOGGLE, JsonPrimitive(true))
        assertEquals(JsonPrimitive(false), toggle.normalize(JsonPrimitive(false)))
        assertEquals(JsonPrimitive(true), toggle.normalize(JsonPrimitive("否")))

        val select = field(PluginSettingType.SELECT, JsonPrimitive("a"), PluginOption("a", "甲"), PluginOption("b", "乙"))
        assertEquals(JsonPrimitive("b"), select.normalize(JsonPrimitive("b")))
        assertEquals(JsonPrimitive("a"), select.normalize(JsonPrimitive("c")))

        val number = field(PluginSettingType.NUMBER, JsonPrimitive(0f))
        assertEquals(JsonPrimitive(4f), number.normalize(JsonPrimitive(3.2f)))
        assertEquals(JsonPrimitive(10f), number.normalize(JsonPrimitive(99)))
        assertEquals(JsonPrimitive(0f), number.normalize(JsonPrimitive("5")))

        val text = field(PluginSettingType.TEXT, JsonPrimitive(""))
        assertEquals(JsonPrimitive(""), text.normalize(JsonNull))
        assertEquals(JsonPrimitive(""), text.normalize(buildJsonArray { }))
        assertEquals(500, (text.normalize(JsonPrimitive("x".repeat(600))) as JsonPrimitive).content.length)
    }

    @Test
    fun `步长吸附不超出范围`() {
        assertEquals(0.5f, snapToStep(0.4f, 0f, 1f, 0.5f))
        assertEquals(1f, snapToStep(2f, 0f, 1f, 0.5f))
        assertEquals(0.3f, snapToStep(0.3f, 0f, 1f, 0f))
        assertTrue(snapToStep(5.1f, 1f, 9f, 2f) == 5f)
    }
}
