package com.lin0721.linmusic.core.ui.skin

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

// 皮肤与其已解码的背景图；没有背景图或解码失败时 backdrop 为 null
class ActiveSkin(val skin: Skin?, val backdrop: ImageBitmap?)

// 背景图先解码好再与皮肤一起下发，切换动画扩散出来的就是最终画面，不会先闪出无背景的配色
fun Flow<Skin?>.withBackdrop(codec: SkinImageCodec): Flow<ActiveSkin> =
    map { skin -> ActiveSkin(skin, skin?.let { SkinImages.loadBackdrop(it, codec) }) }
        .flowOn(Dispatchers.IO)
