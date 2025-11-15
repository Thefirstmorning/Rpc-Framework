package com.github.shangtanlin;

/**
 * 心跳包定义
 * 使用枚举，高效、安全、单例
 */
public enum HeartbeatPacket {
    /**
     * PING 包 (客户端发给服务端)
     */
    PING,

    /**
     * PONG 包 (服务端回复客户端)
     */
    PONG
}
