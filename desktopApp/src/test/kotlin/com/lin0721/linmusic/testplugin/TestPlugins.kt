package com.lin0721.linmusic.testplugin

import com.lin0721.linmusic.plugin.api.MelodiaPlugin
import com.lin0721.linmusic.plugin.api.PluginApiException
import com.lin0721.linmusic.plugin.api.PluginContext
import kotlinx.coroutines.flow.first
import java.io.File

// 这些类打进临时 JAR，由插件类加载器加载，和测试只能通过文件与注册表交换结果

class NormalPlugin : MelodiaPlugin {
    private lateinit var directory: File

    override fun onLoad(context: PluginContext) {
        directory = context.dataDirectory
        context.extensions.command("run") { File(directory, "ran").writeText("yes") }
    }

    override fun onUnload() {
        File(directory, "unloaded").writeText("yes")
    }
}

class SpectrumProbePlugin : MelodiaPlugin {
    override fun onLoad(context: PluginContext) {
        context.extensions.command("run") {
            val result = try {
                "bands=${context.spectrum.frames().first().bands.size}"
            } catch (e: PluginApiException) {
                e.message.orEmpty()
            }
            File(context.dataDirectory, "spectrum").writeText(result)
        }
    }
}

class ThrowingPlugin : MelodiaPlugin {
    override fun onLoad(context: PluginContext) {
        context.extensions.command("run") {}
        throw IllegalStateException("加载炸了")
    }
}

class HangingPlugin : MelodiaPlugin {
    override fun onLoad(context: PluginContext) {
        Thread.sleep(60_000)
    }
}

// 超时后才注册，模拟放弃等待后还在运行的 onLoad
class LateRegisterPlugin : MelodiaPlugin {
    override fun onLoad(context: PluginContext) {
        Thread.sleep(300)
        context.extensions.command("run") {}
    }
}

class BlockingCommandPlugin : MelodiaPlugin {
    override fun onLoad(context: PluginContext) {
        context.extensions.command("run") { Thread.sleep(60_000) }
    }
}

class StorageProbePlugin : MelodiaPlugin {
    override fun onLoad(context: PluginContext) {
        context.extensions.command("run") {
            File(context.dataDirectory, "storage").writeText((context.storage.get("missing") == null).toString())
        }
    }
}

class UndeclaredPlugin : MelodiaPlugin {
    override fun onLoad(context: PluginContext) {
        context.extensions.command("ghost") {}
    }
}

class FailingCommandPlugin : MelodiaPlugin {
    override fun onLoad(context: PluginContext) {
        context.extensions.command("run") { throw NoClassDefFoundError("缺类") }
    }
}

// 宿主内部类不应该可见
class ProbePlugin : MelodiaPlugin {
    override fun onLoad(context: PluginContext) {
        val internal = runCatching { Class.forName("com.lin0721.linmusic.core.plugin.PluginStore", false, javaClass.classLoader) }
        val api = runCatching { Class.forName("com.lin0721.linmusic.plugin.api.PluginApiVersion", false, javaClass.classLoader) }
        File(context.dataDirectory, "probe").writeText("${internal.isSuccess},${api.isSuccess}")
        context.extensions.command("run") {}
    }
}

class ComposePagePlugin : MelodiaPlugin {
    override fun onLoad(context: PluginContext) {
        context.extensions.composePage("panel") {}
    }
}

class NoInterfacePlugin
