package com.lin0721.linmusic.core.comment.presentation

import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.model.BeRepliedComment
import com.lin0721.linmusic.core.model.CommentItem
import java.util.Locale

private const val PREVIEW_COUNT = 2

// 点赞数缩写：千以上用 k+，万以上带一位小数的 w，十万以上取整的 w+
fun formatLikedCount(count: Int): String = when {
    count >= 100_000 -> "${count / 10_000}w+"
    count >= 10_000 -> String.format(Locale.US, "%.1fw", count / 10_000f)
    count >= 1_000 -> "${count / 1_000}k+"
    else -> count.toString()
}

// 评论流：热评在前，按评论 key/id 去重
fun allComments(state: CommentsState.Success): List<CommentItem> =
    (state.hotComments + state.comments).distinctBy { it.commentKey.ifBlank { it.commentId.toString() } }

// 面板预览卡里展示的前几条评论
fun previewComments(state: CommentsState.Success): List<CommentItem> = allComments(state).take(PREVIEW_COUNT)

// 被回复评论的引用文案
fun quotedReplyText(quoted: BeRepliedComment): String =
    "回复 ${quoted.user?.nickname.orEmpty()}：${quoted.content.orEmpty()}"
