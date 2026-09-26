package com.lin0721.linmusic.core.comment.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentSortTypeTest {

    @Test
    fun `推荐排序首页游标为0`() {
        assertEquals("0", CommentSortType.RECOMMEND.cursorFor(pageNo = 1, pageSize = 20, previousCursor = null))
    }

    @Test
    fun `推荐排序第二页游标为已翻页数量`() {
        assertEquals("20", CommentSortType.RECOMMEND.cursorFor(pageNo = 2, pageSize = 20, previousCursor = null))
    }

    @Test
    fun `最热排序游标带normalHot前缀`() {
        assertEquals("normalHot#20", CommentSortType.HOT.cursorFor(pageNo = 2, pageSize = 20, previousCursor = null))
    }

    @Test
    fun `最新排序首页游标为0`() {
        assertEquals("0", CommentSortType.LATEST.cursorFor(pageNo = 1, pageSize = 20, previousCursor = null))
    }

    @Test
    fun `最新排序翻页游标沿用上一页返回值`() {
        assertEquals("1582191360636", CommentSortType.LATEST.cursorFor(pageNo = 2, pageSize = 20, previousCursor = "1582191360636"))
    }

    @Test
    fun `wireValue与网易云真实排序参数一致`() {
        assertEquals(99, CommentSortType.RECOMMEND.wireValue)
        assertEquals(2, CommentSortType.HOT.wireValue)
        assertEquals(3, CommentSortType.LATEST.wireValue)
    }
}
