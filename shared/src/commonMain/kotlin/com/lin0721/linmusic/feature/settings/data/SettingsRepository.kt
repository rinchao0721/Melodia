package com.lin0721.linmusic.feature.settings.data

import kotlinx.coroutines.flow.Flow

// 个人信息、等级与签到数据仓储（settings 业务域）
interface SettingsRepository {

    // 获取用户等级信息
    fun getUserLevel(): Flow<Result<UserLevelData>>

    // 获取 VIP 状态信息
    fun getVipInfo(): Flow<Result<VipInfoData>>

    // 获取账号绑定信息
    fun getUserBindings(uid: Long): Flow<Result<List<UserBindingItem>>>

    // 修改用户个人资料
    fun updateUserProfile(nickname: String, gender: Int, birthday: Long, province: Int, city: Int, signature: String): Flow<Result<Unit>>

    // 检查昵称可用性
    fun checkNickname(nickname: String): Flow<Result<Boolean>>

    // 每日签到
    fun dailySignin(type: Int): Flow<Result<Int>> // 返回签到获得的积分数

    // 上传并更换头像
    fun uploadAvatar(file: java.io.File): Flow<Result<String>>
}
