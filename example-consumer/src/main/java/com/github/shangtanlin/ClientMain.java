package com.github.shangtanlin;

/**
 * Hello world!
 *
 */
public class ClientMain {
    // 假设 ZK 在本地 2181 端口
    private static final String ZK_CONNECT_STRING = "127.0.0.1:2181";

    public static void main (String[] args) throws InterruptedException {
        // 1. 创建注册中心
        Registry registry = new ZooKeeperRegistry(ZK_CONNECT_STRING);

        // 2. 创建负载均衡器
        LoadBalancer loadBalancer = new RandomLoadBalancer();

        // 3. 创建 RpcClient
        RpcClient client = new RpcClient(registry, loadBalancer);

        // 4. 获取 HelloService 的代理对象
        // *** 注意！不再需要 IP 和 端口 ***
        HelloService helloService = client.getProxy(HelloService.class);

        // 5. 像调用本地方法一样调用 RPC
        for (int i = 0; i < 10; i++) {
            String result = helloService.hello("RPC (call " + i + ")");
            System.out.println("客户端收到响应: " + result);
            Thread.sleep(2000); // 2 秒
        }

        client.shutdown();
        registry.close();
    }
}