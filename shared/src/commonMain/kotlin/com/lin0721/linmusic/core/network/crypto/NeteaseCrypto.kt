package com.lin0721.linmusic.core.network.crypto

import java.util.Base64
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

// 网易云音乐加密工具类 (WeApi, LinuxApi, EApi)
object NeteaseCrypto {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val IV = "0102030405060708"
    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val PUBLIC_KEY = "010001"
    // 修正后的 RSA 模数 (Modulus)，用于 WeApi 加密中的 RSA 过程，原有的模数尾部存在拼写错误导致解密失败
    private const val MODULUS =
        "e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"

    private const val LINUX_API_KEY = "rpaWUfe92PZ4WjM9"
    private const val EAPI_KEY = "e82ckenh8dichen8"

    // WeApi 加密 (Web, 小程序)
    fun weapi(text: String): Map<String, String> {
        // 生成 16 位随机秘钥
        val secretKey = (1..16).map { "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }.joinToString("")
        
        // 致命坑点修复3：第二次 AES 加密，必须对第一次的结果（Base64 字符串）进行加密！
        val firstEncrypt = aesEncrypt(text, PRESET_KEY)
        val params = aesEncrypt(firstEncrypt, secretKey) 
        
        val encSecKey = rsaEncrypt(secretKey, PUBLIC_KEY, MODULUS)
        
        return mapOf("params" to params, "encSecKey" to encSecKey)
    }

    // 用于调试的固定秘钥 weapi
    fun testWeapi(text: String, secretKey: String): Map<String, String> {
        val firstEncrypt = aesEncrypt(text, PRESET_KEY)
        val params = aesEncrypt(firstEncrypt, secretKey)
        val encSecKey = rsaEncrypt(secretKey, PUBLIC_KEY, MODULUS)
        return mapOf("params" to params, "encSecKey" to encSecKey, "p1" to firstEncrypt)
    }

    // LinuxApi 加密
    fun linuxapi(text: String): Map<String, String> {
        return mapOf(
            "eparams" to aesEncryptHex(text, LINUX_API_KEY, mode = "AES/ECB/PKCS5Padding").uppercase()
        )
    }

    // EApi 加密 (移动端)
    fun eapi(url: String, params: Any): Map<String, String> {
        val text = if (params is String) params else json.encodeToString(params)
        val message = "nobody${url}use${text}md5forencrypt"
        val digest = md5(message)
        val data = "${url}-36cd479b6b5-${text}-36cd479b6b5-${digest}"
        return mapOf(
            "params" to aesEncryptHex(data, EAPI_KEY, mode = "AES/ECB/PKCS5Padding").uppercase()
        )
    }

    // AES 加密 (Base64)
    private fun aesEncrypt(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        val ivParameterSpec = IvParameterSpec(IV.toByteArray(Charsets.UTF_8))
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
        val encryptedBytes = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        // 致命坑点修复1：强制禁用换行符 (java.util.Base64 默认不包含换行符)
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    // AES 加密 (Hex)
    private fun aesEncryptHex(
        text: String,
        key: String,
        iv: String = IV,
        mode: String = "AES/CBC/PKCS5Padding"
    ): String {
        val cipher = Cipher.getInstance(mode)
        val keySpec = SecretKeySpec(key.toByteArray(), "AES")
        if (mode.contains("CBC")) {
            val ivSpec = IvParameterSpec(iv.toByteArray())
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        }
        val encrypted = cipher.doFinal(text.toByteArray())
        return encrypted.joinToString("") { "%02x".format(it) }
    }

    // RSA 加密 (网易特化版)
    private fun rsaEncrypt(text: String, pubKey: String, modulus: String): String {
        val reversedText = text.reversed()
        val m = BigInteger(1, reversedText.toByteArray(Charsets.UTF_8))
        val e = BigInteger(pubKey, 16)
        val n = BigInteger(modulus, 16)
        val c = m.modPow(e, n)
        // 致命坑点修复2：使用大数运算规避 Android Cipher Padding 问题，必须左侧补零至256位
        return c.toString(16).padStart(256, '0')
    }

    // 生成随机字符串
    private fun generateRandomString(length: Int): String {
        val charPool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { kotlin.random.Random.nextInt(0, charPool.length) }
            .map(charPool::get)
            .joinToString("")
    }

    // MD5 摘要
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray()))
            .toString(16)
            .padStart(32, '0')
    }
}
