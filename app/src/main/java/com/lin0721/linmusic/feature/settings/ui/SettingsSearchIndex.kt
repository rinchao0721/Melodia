package com.lin0721.linmusic.feature.settings.ui

import com.lin0721.linmusic.feature.settings.ui.SettingsSubMenu.*

// 设置搜索条目；条件显示的条目只索引到所属子页，不定位具体行
data class SettingsSearchEntry(
    val title: String,
    val subMenu: SettingsSubMenu,
    val section: String,
    val keywords: String = ""
)

object SettingsSearchIndex {

    private fun entry(subMenu: SettingsSubMenu, sectionIndex: Int, title: String, keywords: String = "") =
        SettingsSearchEntry(title, subMenu, subMenu.sectionTitles[sectionIndex], keywords)

    private val entries: List<SettingsSearchEntry> = run {
        listOf(
            entry(PLAYBACK, 0, "自动播放推荐新歌", "相似推荐 连续播放"),
            entry(PLAYBACK, 0, "与其他应用同时播放", "音频焦点 打断 混音"),
            entry(PLAYBACK, 0, "硬件音频解码", "offload 省电 后台"),
            entry(PLAYBACK, 0, "USB 独占输出", "usb dac 解码器 独占 外接声卡"),
            entry(PLAYBACK, 0, "外部音视频停止后自动恢复", "打断 暂停 继续"),
            entry(PLAYBACK, 0, "音量平衡", "响度 均衡 replaygain"),
            entry(PLAYBACK, 0, "切歌淡入淡出", "crossfade 交叠 过渡时长 智能过渡 静音"),

            entry(QUALITY_SOURCES, 0, "Wi-Fi 环境播放音质", "wifi 无线 码率"),
            entry(QUALITY_SOURCES, 0, "移动网络环境播放音质", "流量 蜂窝 码率"),
            entry(QUALITY_SOURCES, 1, "显示实际音质", "播放页 全屏"),
            entry(QUALITY_SOURCES, 2, "多平台聚合搜索", "酷狗 酷我 QQ 音乐"),
            entry(QUALITY_SOURCES, 2, "搜索框推荐词", "搜索 推荐 关键词 默认"),
            entry(QUALITY_SOURCES, 2, "热搜榜", "搜索 热搜 排行"),
            entry(QUALITY_SOURCES, 2, "无版权/VIP 自动换源", "试听 灰色 解灰 换源"),
            entry(QUALITY_SOURCES, 2, "社区源优先", "LX unm"),
            entry(QUALITY_SOURCES, 2, "插件优先于官方音源", "优先 官方"),
            entry(QUALITY_SOURCES, 3, "启用社区源插件", "LX 脚本 导入 洛雪"),
            entry(QUALITY_SOURCES, 4, "远程 UNM 服务", "UnblockNeteaseMusic 服务器 base url 自动选择"),

            entry(DOWNLOAD_STORAGE, 0, "边听边存", "缓存 在线播放 保存"),
            entry(DOWNLOAD_STORAGE, 0, "下载目录", "保存位置 文件夹 恢复默认"),
            entry(DOWNLOAD_STORAGE, 0, "识别已下载歌曲", "扫描 本地 重装 已有文件 重复下载"),
            entry(DOWNLOAD_STORAGE, 0, "内嵌歌词", "歌词 写入 音频文件"),
            entry(DOWNLOAD_STORAGE, 1, "清理缓存", "存储 空间 音频缓存 图片缓存 日志 临时文件 更新安装包 最大缓存"),

            entry(NETWORK, 0, "仅 Wi-Fi 网络下联网播放", "流量 移动网络"),
            entry(NETWORK, 0, "流量播放警告提示", "移动数据 提醒"),
            entry(NETWORK, 1, "使用国内 IP 地址", "海外 ip 限制"),
            entry(NETWORK, 1, "使用代理服务器", "proxy 代理"),

            entry(LYRICS, 0, "歌词字号大小", "全屏歌词 字体"),
            entry(LYRICS, 0, "歌词对齐方式", "居中 左对齐"),
            entry(LYRICS, 0, "逐字歌词流光动效", "卡拉ok 渐变"),
            entry(LYRICS, 1, "启用桌面悬浮歌词", "悬浮窗 桌面 锁定 阴影 粗体"),
            entry(LYRICS, 2, "SuperLyric 实时歌词", "状态栏 binder"),
            entry(LYRICS, 2, "LyricInfo 系统歌词", "锁屏岛 元数据"),
            entry(LYRICS, 2, "车载蓝牙歌词", "蓝牙 标题栏"),
            entry(LYRICS, 2, "Lyricon 词幕投屏", "词幕"),
            entry(LYRICS, 2, "状态栏歌词胶囊", "流体云 now bar 实时更新通知"),
            entry(LYRICS, 3, "启用歌词过滤", "屏蔽 作词 作曲 版权 正则 关键词"),
            entry(LYRICS, 3, "编辑过滤规则", "导入 导出 恢复默认"),
            entry(LYRICS, 3, "过滤 AMLL 歌词", "ttml"),
            entry(LYRICS, 3, "过滤本地歌词", "本地"),

            entry(APPEARANCE, 0, "主题与深浅色", "深色 浅色 跟随系统 主色 动态色 莫奈"),
            entry(APPEARANCE, 0, "皮肤", "主题包 换肤"),
            entry(APPEARANCE, 0, "界面缩放", "字体大小 显示大小"),
            entry(APPEARANCE, 0, "界面过渡动画", "动画 切页"),
            entry(APPEARANCE, 0, "歌单显示序号", "编号 列表"),
            entry(APPEARANCE, 1, "底栏自定义", "底部导航栏 标签 图标"),
            entry(APPEARANCE, 1, "启动默认打开音乐库", "冷启动 首页"),
            entry(APPEARANCE, 2, "播放页模式", "全屏式 侧边小窗 平板 横屏"),
            entry(APPEARANCE, 2, "侧边播放页位置", "左侧 右侧"),
            entry(APPEARANCE, 2, "侧边播放页钉住", "返回键"),

            entry(SYSTEM, 0, "启用系统锁屏显示", "锁屏 控制器"),
            entry(SYSTEM, 1, "车载模式蓝牙自动启动", "蓝牙 车机 自动播放"),
            entry(SYSTEM, 2, "全局沉浸", "状态栏 导航栏 全屏 平板"),
            entry(SYSTEM, 3, "插件", "扩展 命令 插件页面 目录"),

            entry(ABOUT, 0, "检查更新", "版本 github 测试版 beta 自动检查"),
            entry(ABOUT, 1, "项目地址", "github 仓库 源码"),
            entry(ABOUT, 1, "QQ 交流群", "qq 群号 反馈 加群"),
            entry(ABOUT, 1, "开源协议", "license mit libusb"),
            entry(ABOUT, 2, "日志级别", "诊断 调试"),
            entry(ABOUT, 2, "导出并分享日志", "反馈 故障"),
            entry(ABOUT, 3, "特别感谢", "致谢 开源项目")
        )
    }

    // 空白分词后要求每个词都命中标题、关键词或所属页名
    fun search(query: String): List<SettingsSearchEntry> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        return entries.filter { e ->
            val haystack = "${e.title} ${e.keywords} ${e.subMenu.title} ${e.section}".lowercase()
            terms.all { haystack.contains(it) }
        }
    }
}
