package com.lin0721.linmusic.core.player.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// 歌词过滤规则，也是导入导出文件的格式
@Serializable
data class LyricFilterRules(
    @SerialName("keywords") val keywords: List<String> = emptyList(),
    @SerialName("regexes") val regexes: List<String> = emptyList()
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        val DEFAULT = LyricFilterRules(DEFAULT_LYRIC_FILTER_KEYWORDS, DEFAULT_LYRIC_FILTER_REGEXES)

        fun encode(rules: LyricFilterRules): String = json.encodeToString(serializer(), rules)

        // 无法解析时返回 null，由调用方提示
        fun decodeOrNull(raw: String): LyricFilterRules? = try {
            json.decodeFromString(serializer(), raw).normalized()
        } catch (_: Exception) {
            null
        }
    }

    fun normalized(): LyricFilterRules = LyricFilterRules(
        keywords = keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        regexes = regexes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    )

    // 导入的文件可能带有本平台不支持的正则写法
    fun withoutInvalidRegexes(): LyricFilterRules = copy(regexes = regexes.filter(::isValidLyricFilterRegex))
}

// 歌词过滤总配置
data class LyricFilterConfig(
    val enabled: Boolean = true,
    val filterAmll: Boolean = false,
    val filterLocal: Boolean = false,
    val rules: LyricFilterRules = LyricFilterRules.DEFAULT
)

// 正则校验：空串与语法错误都视为无效
fun isValidLyricFilterRegex(pattern: String): Boolean {
    if (pattern.isBlank()) return false
    return try {
        Regex(pattern)
        true
    } catch (_: Exception) {
        false
    }
}

// 默认规则来自 issue #151 提供的 morphe 过滤规则
val DEFAULT_LYRIC_FILTER_REGEXES: List<String> = listOf(
    ".?(?:未经.?(?:不得|禁止)|本作品声明.?著作权权利保留.?不得|本字幕由TME AI技术生成|部分素材源自网络|酷我音乐.?特别出品|" +
        "酷狗.?星曜计划|酷狗.?国潮|酷狗音乐.?就是歌多|听国潮.?酷狗|未经许可.?(?:翻唱|盗版)|本作品.?授权|已获得.?授权|" +
        "星曜计划.?企划|黑胶复刻|此歌曲为没有填词的纯音乐|纯音乐，请欣赏|此歌曲由Vemus未音APP.制作 音乐创作如此简单！|" +
        "酷狗音乐『万物皆可dj』企划|『听dj, 到中国酷狗』|本歌曲来自〖飓风计划〗|10亿现金激励，千亿流量扶持！|" +
        "版权所有\\s未经\\s许可\\s请勿\\s使用|想听的歌在评\\s论区).*"
)

val DEFAULT_LYRIC_FILTER_KEYWORDS: List<String> = (
    "A&R,A.Guita,AU,Additional Drums Engineering,Administer,Administered,Administered By,Administering,Administers," +
        "Agency,Album,All Instruments,Arranged,Arranged By,Arranger,Arrangers,Arranging,Artist,Artists," +
        "Assistant Engineer,Assistant Engineers,Assistant Mix Engineer,Assistant Mix Engineers,Author,Authoring,Authors," +
        "Autotune,Backed,Background,Background Vocal,Background Vocals,Backing,Bass,Bass Guitar,COS,CV,Child," +
        "Child Choir,Child Choir Instruction,Child Lead,Children,Composed By,Composer,Composers,Composing,Copyright," +
        "Cover,Credit,DJ,Digital Edited,Digital Edited By,Digital Editing,Directed,Directed By,Directing,Director," +
        "Directors,Drum,Drum programming,Drums,Duration,E.Guitar,Edited,Edited By,Editing Engineer,Editing Engineers," +
        "Editor,Editors,Engineer,Engineered,Engineered By,Engineering,Engineers,Executed,Executing,Executive,Guitar," +
        "Group,Harmony,ISRC,Keyboard,LA,Lang,Language,Lead,Leader,Leaders,Length,Lyric,Lyricist,Lyricists,Lyrics," +
        "Lyrics By,MV,Main Sample,Manufactory,Manufactured,Manufactured By,Manufacturing,Master,Mastered,Mastered By," +
        "Mastering,Mastering Engineer,Mastering Engineers,Masters,Mix Engineer,Mix Engineered by,Mixed,Mixed By,Mixer," +
        "Mixers,Mixing,Mixing Engineer,Mixing Studio,Music,OA,OC,OP,OT,Original Lyrics by,Original Title," +
        "Original Publisher,Original Writer,PGM,PV,Percussion,Performed,Performed By,Performer,Performers,Performing," +
        "Pro-Tools Editing,Produced,Produced By,Producer,Producers,Producing,Program,Programming by,Published," +
        "Published By,Publisher,Publishers,Publishing,Publishing Group,Publishing Group Administered By,QQ,RE,Rap," +
        "Record,Recorded,Recorded At,Recorded By,Recorder,Recorders,Recording,Recording Engineer,Recordings,Records," +
        "SP,Sample,Sampled,Samples,Sampling,Singer,Singers,Singing,Song,Strings,Studio,Sub,Sub Publisher,Subs," +
        "Subscribe,Subscribed,Subscriber,Subscribers,Surround,Synthesizer,Synthesizers,TA,Title,VE,Ver,Version,Vocal," +
        "Vocal Arrangement,Vocal Directed,Vocal Directed By,Vocal Director,Vocal Engineer,Vocal Engineering," +
        "Vocal Produced,Vocal Produced By,Vocal Producer,Vocal Producers,Vocals,Vocals Arrangement,Voice,Written," +
        "Written By,Writter,专辑,业务联系,业务邮箱,中提,中提琴,中提琴手,中文,中文词SA,主催,主唱,乐器,乐团,乐队,书法," +
        "竖琴吉他,二胡,人声,企业宣传,企划,企宣,伴唱,伴奏,伴舞,低音提琴,低音吉他,作曲,作画,作者,作词,修音,修音师,公司," +
        "出品,出品人,创作,创作者,指挥,创意,制作,制作人,前置混音,剧情,剪纸艺术家,助力推广,助理,协力,厂牌,原唱,原曲," +
        "原歌名,原版,原画,原编曲,原翻,原著,原词,原词曲,发型,发布,发布者,发行,口琴,口风琴,古筝,合作,合作伙伴,合作者," +
        "合声,合成,合成器,合音,合唱,吉他,后期,吟唱,和声,和音,唢呐,商务,团队,图,图片,图画,地址,场景,场景提供,填词,声乐," +
        "声音,处理,大提,大提琴,大提琴手,女声,妆造,官方,官方指定音乐合作伙伴,宣传,宣发,宣推,富鲁格,导演,封设,封面,小号," +
        "小提,小提琴,小提琴手,第一小提琴,第二小提琴,工作室,工程,工程师,平面设计,平台,弦乐,弦乐团,录音,录音室,录音师," +
        "录音棚,录制,微信,微博,快手,念白,总企划,总监,总监制,总策划,总顾问,手碟,手风琴,打击乐,执行,抖音,短视频平台总统筹," +
        "短视频平台宣推,短视频宣推,短视频统筹,拍摄,指定音乐合作伙伴,指导,指导老师,推广,摄影,改编,改编词,文案,时长,曲," +
        "曲Composer,曲Music,曲名,曲绘,曲编,曲协力,木吉他,木管,杜比全景声,板胡,校准,次中音萨克斯,歌名,歌声,歌手,歌曲," +
        "歌词改编,歌唱指导,母带,海外配唱执行,海报,混缩,混缩室,混音,混音室,混音师,混音棚,滤镜,演唱,演奏,漫画,灯光,版本," +
        "版权,版权归属,特别鸣谢,班卓琴,琵琶,电吉他,电脑工程,电钢琴,男声,画,画师,监制,监唱,私人,童声,笛,笛子,笛萧,策划," +
        "管乐,管弦,管弦乐,箫,粤语,经纪,统筹,编,编写,编剧,编唱,编导,编曲,编舞,编舞师,编著,编辑,缩混,网易音乐人商务合作," +
        "美工,美术,美术设计,翻唱,翻译,翻译者,联合,联系,联系方式,舞台,舞团,舞曲,舞蹈,艺人制作统筹,艺人经纪,艺人经纪公司," +
        "艺人统筹,艺术家,艺术指导,艺术指导老师,艺统,花脸,英文,营销,萧笛,萨克斯,视觉,记,设计,词,词Lyricist,词Lyrics," +
        "词曲作者,词曲提供,词协力,译者,语言,语言代码,说唱,说唱词,调校,调音,谱曲,贝斯,贴唱,造型,邮件,邮箱地址,配唱,采样," +
        "钢琴,铜管,键盘,键盘手,长号,长笛,队长,附加,音乐,音乐人,音准调校,音响,音效,音编,音频,项目企划,项目协力,项目总企划," +
        "项目总监,项目统筹,项目营销,顾问,领唱,领舞,题字,题记,飓风计划商务合作,马头琴,鸣谢,鼓,鼓手,鼓录音,鼓录音室,鼓录制," +
        "鼓机,鼓组编程Drums Arrangement,鼓组音频编辑,运营"
    ).split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
