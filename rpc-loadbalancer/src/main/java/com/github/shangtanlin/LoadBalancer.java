package com.github.shangtanlin;

import java.util.List;

public interface LoadBalancer {
    /**
     * 从地址列表中选择一个
     * @param addresses 可用服务地址列表
     * @return 选中的一个地址
     */
    String select(List<String> addresses);
}