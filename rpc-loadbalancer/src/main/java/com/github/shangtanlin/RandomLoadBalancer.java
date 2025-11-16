package com.github.shangtanlin;

import java.util.List;
import java.util.Random;

public class RandomLoadBalancer implements LoadBalancer {

    private final Random random = new Random();

    @Override
    public String select(List<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        // 只有一个，直接返回
        if (addresses.size() == 1) {
            return addresses.get(0);
        }
        // 随机选择
        return addresses.get(random.nextInt(addresses.size()));
    }
}