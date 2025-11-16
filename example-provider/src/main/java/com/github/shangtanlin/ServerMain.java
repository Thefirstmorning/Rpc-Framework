package com.github.shangtanlin;



/**
 * Hello world!
 *
 */
public class ServerMain {
    // 假设 ZK 在本地 2181 端口
    private static final String ZK_CONNECT_STRING = "127.0.0.1:2181";
    // 假设本机 IP (注意：不能用 127.0.0.1，必须是局域网 IP)
    // 在真实环境中，这个 IP 应该自动获取
    private static final String SERVER_ADDRESS = "10.18.155.90:8080"; // *** 必须修改为你自己的局域网 IP ***
    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) {
        try {
            // 1. 创建注册中心
            Registry registry = new ZooKeeperRegistry(ZK_CONNECT_STRING);

            // 2. 创建 RpcServer
            RpcServer server = new RpcServer(registry, SERVER_ADDRESS);

            // 3. 发布我们的 HelloService
            server.publishService(new HelloServiceImpl());

            // 4. 启动服务器，监听端口
            server.start(SERVER_PORT);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}