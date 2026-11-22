package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.UnifiedComment
import com.lin0721.linmusic.core.platform.UnifiedCreator
import com.lin0721.linmusic.core.platform.UnifiedRepliedComment
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.serialization.Serializable

enum class KugouCommentResourceType(val code: String) {
    SONG("fc4be23b4e972707f36b8a828a93ba8a"),
    ALBUM("94f1792ced1df89aa68a7939eaf2efca"),
    PLAYLIST("ca53b96fe5a1d9c22d71c8f522ef7c4f")
}

@Serializable
data class KugouCommentItemDto(
    val id: String,
    val tid: String = id,
    val specialId: String = "",
    val specialName: String = "",
    val userId: String = "",
    val userName: String = "",
    val userAvatar: String = "",
    val content: String = "",
    val addTimeText: String = "",
    val addTimeMs: Long = 0L,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val parentCommentId: String? = null,
    val repliedUserName: String? = null,
    val repliedContent: String? = null
) {
    fun toUnifiedComment(): UnifiedComment {
        val replied = if (!repliedContent.isNullOrBlank() || !repliedUserName.isNullOrBlank()) {
            listOf(
                UnifiedRepliedComment(
                    author = repliedUserName?.takeIf { it.isNotBlank() }?.let {
                        UnifiedCreator(nickname = it)
                    },
                    content = repliedContent
                )
            )
        } else {
            emptyList()
        }
        return UnifiedComment(
            id = id,
            platform = MusicPlatform.KUGOU,
            author = UnifiedCreator(
                id = userId.takeIf { it.isNotBlank() && it != "0" }?.let { AccountId.kugou(it) },
                nickname = userName,
                avatarUrl = userAvatar
            ),
            content = content,
            timeMs = addTimeMs,
            timeText = addTimeText.takeIf { it.isNotBlank() },
            likedCount = likeCount,
            liked = false,
            replyCount = replyCount,
            parentCommentId = parentCommentId?.takeIf { it.isNotBlank() && it != "0" },
            repliedTo = replied
        )
    }
}

@Serializable
data class KugouCommentPage(
    val comments: List<KugouCommentItemDto> = emptyList(),
    val weightComments: List<KugouCommentItemDto> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val specialId: String = "",
    val specialName: String = ""
)
