package com.hchen.superlyricapi;

import com.hchen.superlyricapi.SuperLyricData;

interface ISuperLyricReceiver {
    void onLyric(in String publisher, in SuperLyricData data);
    void onStop(in String publisher, in SuperLyricData data);
}
