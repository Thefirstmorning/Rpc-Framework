package com.github.shangtanlin;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ZooKeeperRegistry implements Registry {

    private static final Logger log = LoggerFactory.getLogger(ZooKeeperRegistry.class);

    private final CuratorFramework client;
    private static final String ZK_ROOT = "/rpc"; // ZK 根路径

    // 本地缓存 (服务名 -> 地址列表)
    private final ConcurrentHashMap<String, List<String>> serviceCache = new ConcurrentHashMap<>();
    // 本地缓存 (服务名 -> ZK 路径缓存)
    private final ConcurrentHashMap<String, PathChildrenCache> pathCacheMap = new ConcurrentHashMap<>();


    public ZooKeeperRegistry(String zkConnectString) {
        // ZK 连接重试策略
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);
        // 创建 ZK 客户端
        this.client = CuratorFrameworkFactory.builder()
                .connectString(zkConnectString)
                .retryPolicy(retryPolicy)
                .sessionTimeoutMs(6000)
                .connectionTimeoutMs(3000)
                .build();
        this.client.start();
        log.info("ZooKeeper 客户端已启动...");
    }

    @Override
    public void register(String serviceName, String address) throws Exception {
        // 1. 创建服务的持久化根路径 (e.g., /rpc/com.yourdomain.HelloService)
        String servicePath = ZK_ROOT + "/" + serviceName;
        if (client.checkExists().forPath(servicePath) == null) {
            try {
                client.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT) // 持久化节点
                        .forPath(servicePath);
                log.info("创建服务根节点: {}", servicePath);
            } catch (Exception e) {
                // (可能并发创建，忽略“节点已存在”的异常)
                if (e.getMessage().contains("NodeExists")) {
                    log.warn("服务根节点 {} 已存在", servicePath);
                } else {
                    throw e;
                }
            }
        }

        // 2. 创建服务的临时节点 (e.g., /rpc/com.yourdomain.HelloService/127.0.0.1:8080)
        String addressPath = servicePath + "/" + address;
        if (client.checkExists().forPath(addressPath) != null) {
            log.warn("地址节点 {} 已存在，正在删除...", addressPath);
            client.delete().forPath(addressPath); // 确保是新的临时节点
        }

        client.create()
                .withMode(CreateMode.EPHEMERAL) // 临时节点 (关键！)
                .forPath(addressPath);

        log.info("服务注册成功: {} -> {}", serviceName, address);
    }

    @Override
    public List<String> discover(String serviceName) throws Exception {
        // 1. 优先从本地缓存获取
        List<String> cachedAddresses = serviceCache.get(serviceName);
        if (cachedAddresses != null && !cachedAddresses.isEmpty()) {
            return cachedAddresses;
        }

        // 2. 缓存中没有，查询 ZK
        String servicePath = ZK_ROOT + "/" + serviceName;
        List<String> addresses;
        try {
            addresses = client.getChildren().forPath(servicePath);
        } catch (Exception e) {
            log.error("无法从 ZK 发现服务: {}", serviceName, e);
            return null; // or empty list
        }

        // 3. 更新本地缓存
        serviceCache.put(serviceName, addresses);

        // 4. (核心) 注册 Watcher，监听该服务路径下的子节点变化
        registerWatcher(serviceName, servicePath);

        return addresses;
    }

    private void registerWatcher(String serviceName, String servicePath) throws Exception {
        // 避免重复注册
        if (pathCacheMap.containsKey(serviceName)) {
            return;
        }

        PathChildrenCache pathCache = new PathChildrenCache(client, servicePath, true);

        pathCache.getListenable().addListener((curatorClient, event) -> {
            if (event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED ||
                    event.getType() == PathChildrenCacheEvent.Type.CHILD_REMOVED ||
                    event.getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED) {

                log.info("服务 {} 的地址列表发生变化，更新缓存...", serviceName);
                // 重新获取最新的子节点列表 (即地址列表)
                List<String> newAddresses = curatorClient.getChildren().forPath(servicePath);
                // 更新本地缓存
                serviceCache.put(serviceName, newAddresses);
                log.info("服务 {} 缓存已更新: {}", serviceName, newAddresses);
            }
        });

        pathCache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
        pathCacheMap.put(serviceName, pathCache);
    }

    @Override
    public void close() {
        pathCacheMap.values().forEach(cache -> {
            try {
                cache.close();
            } catch (Exception e) {
                log.error("关闭 PathCache 失败", e);
            }
        });
        client.close();
        log.info("ZooKeeper 客户端已关闭。");
    }
}