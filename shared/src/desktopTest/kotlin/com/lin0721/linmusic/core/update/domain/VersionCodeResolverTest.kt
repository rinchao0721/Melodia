package com.lin0721.linmusic.core.update.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// 断言与 .github/workflows/release.yml 的 versionCode 推导 shell 逻辑保持一致
class VersionCodeResolverTest {

    @Test
    fun `正式版tag无后缀subCode为99`() {
        assertEquals(1020399, VersionCodeResolver.resolve("v1.2.3"))
    }

    @Test
    fun `beta后缀subCode等于编号`() {
        assertEquals(1020301, VersionCodeResolver.resolve("v1.2.3-beta.1"))
        assertEquals(1020349, VersionCodeResolver.resolve("v1.2.3-beta.49"))
    }

    @Test
    fun `rc后缀subCode为50加编号`() {
        assertEquals(1020350, VersionCodeResolver.resolve("v1.2.3-rc.0"))
        assertEquals(1020398, VersionCodeResolver.resolve("v1.2.3-rc.48"))
    }

    @Test
    fun `同版本号下beta小于rc小于正式版`() {
        val beta = VersionCodeResolver.resolve("v1.2.3-beta.49")!!
        val rc = VersionCodeResolver.resolve("v1.2.3-rc.0")!!
        val stable = VersionCodeResolver.resolve("v1.2.3")!!
        assertEquals(true, beta < rc)
        assertEquals(true, rc < stable)
    }

    @Test
    fun `beta编号超出上限返回null`() {
        assertNull(VersionCodeResolver.resolve("v1.2.3-beta.50"))
    }

    @Test
    fun `rc编号超出上限返回null`() {
        assertNull(VersionCodeResolver.resolve("v1.2.3-rc.49"))
    }

    @Test
    fun `不支持的预发布类型返回null`() {
        assertNull(VersionCodeResolver.resolve("v1.2.3-alpha.1"))
    }

    @Test
    fun `非数字版本号返回null`() {
        assertNull(VersionCodeResolver.resolve("v1.2.x"))
    }

    @Test
    fun `段数不为三返回null`() {
        assertNull(VersionCodeResolver.resolve("v1.2"))
    }
}
