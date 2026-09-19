package com.hchen.superlyricapi;

import com.hchen.superlyricapi.SuperLyricData;
import com.hchen.superlyricapi.ISuperLyricReceiver;

interface ISuperLyricManager {
    void registerPublisher();
    void unregisterPublisher();
    boolean isPublisherRegistered();
    void sendLyric(in SuperLyricData data);
    void sendStop(in SuperLyricData data);
    void registerReceiver(in ISuperLyricReceiver receiver);
    void unregisterReceiver(in ISuperLyricReceiver receiver);
    boolean isReceiverRegistered(in ISuperLyricReceiver receiver);
    void setSystemPlayStateListenerEnabled(in boolean enabled);
}
