package com.github.shangtanlin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置属性类
 * 对应 application.properties 中的 rpc.xxx 配置
 */
@ConfigurationProperties(prefix = "rpc")
public class RpcProperties {

    // 注册中心地址 (默认本地)
    private String zkAddr = "127.0.0.1:2181";

    // 服务端端口 (默认 8080)
    private int serverPort = 8080;

    // 本机地址 (用于注册到 ZK，默认自动获取 IP，这里简化处理先给个默认值)
    private String serverAddr = "127.0.0.1";

    // --- Getters and Setters (必须有！) ---

    public String getZkAddr() {
        return zkAddr;
    }

    public void setZkAddr(String zkAddr) {
        this.zkAddr = zkAddr;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }
}