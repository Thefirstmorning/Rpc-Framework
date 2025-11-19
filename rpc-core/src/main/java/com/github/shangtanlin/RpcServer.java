package com.github.shangtanlin;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class RpcServer {
    // 注册中心 (服务名 -> 服务实例)
    private final Map<String, Object> serviceRegistry = new HashMap<>();

    // 我们仍然需要一个业务线程池来执行反射调用，
    // 不应该占用 Netty 的 NioEventLoop 线程
    private final ExecutorService businessThreadPool = Executors.newCachedThreadPool();

    private Registry registry; // *** 新增 ***
    private String serverAddress; // *** 新增 *** (e.g., "127.0.0.1:8080")

    // *** 构造函数修改 ***
    public RpcServer(Registry registry, String serverAddress) {
        this.registry = registry;
        this.serverAddress = serverAddress;
        // ... (其他初始化)
    }

    // *** register 方法修改 ***
    // (原来的 register 方法用于内部存储，我们改个名)
    public void publishService(Object service) {
        String interfaceName = service.getClass().getInterfaces()[0].getName();
        serviceRegistry.put(interfaceName, service);
        System.out.println("服务已暂存: " + interfaceName);

        // *** 自动注册到 ZK ***
        try {
            registry.register(interfaceName, serverAddress);
        } catch (Exception e) {
            System.err.println("服务注册到 ZK 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void start(int port) throws Exception {
        // (*** 确保 serverAddress 中的端口和 start 端口一致 ***)
        // (在实际项目中，serverAddress 应该由 port 动态构造)
        if (!serverAddress.endsWith(":" + port)) {
            System.err.println("警告: serverAddress 端口与启动端口不一致!");
            // 简单修正
            this.serverAddress = serverAddress.split(":")[0] + ":" + port;
            System.err.println("修正为: " + this.serverAddress);
        }

        EventLoopGroup bossGroup = new NioEventLoopGroup(1); // 负责接受连接
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // 负责处理IO

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    // (*** 新增 ***)
                                    // IdleStateHandler: 60秒内未收到任何数据(读空闲)，触发 userEventTriggered
                                    .addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS))
                                    .addLast(new RpcDecoder()) // 入站：解码
                                    .addLast(new RpcEncoder()) // 出站：编码
                                    // (*** 新增 ***)
                                    .addLast(new HeartbeatServerHandler()) // 心跳处理器
                                    .addLast(new RpcServerHandler(serviceRegistry, businessThreadPool)); // 业务处理器
                        }
                    });

            ChannelFuture f = b.bind(port).sync();
            System.out.println("RPC 服务器已在端口 " + port + " 启动(已启用心跳)");
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            businessThreadPool.shutdown();
            registry.close(); // (在服务器关闭时关闭 ZK 连接)
        }
    }
}


