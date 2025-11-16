package com.github.shangtanlin;

import java.util.List;

public interface Registry {
    /**
     * 注册服务
     * @param serviceName 服务名 (e.g., "com.yourdomain.HelloService")
     * @param address 地址 (e.g., "127.0.0.1:8080")
     */
    void register(String serviceName, String address) throws Exception;

    /**
     * 发现服务
     * @param serviceName 服务名
     * @return 可用的服务地址列表
     */
    List<String> discover(String serviceName) throws Exception;

    /**
     * (可选) 关闭注册中心连接
     */
    void close();
}