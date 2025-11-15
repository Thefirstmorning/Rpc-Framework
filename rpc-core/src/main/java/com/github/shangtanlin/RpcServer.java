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
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class RpcServer {
    // 注册中心 (服务名 -> 服务实例)
    private final Map<String, Object> serviceRegistry = new HashMap<>();

    // 我们仍然需要一个业务线程池来执行反射调用，
    // 不应该占用 Netty 的 NioEventLoop 线程
    private final ExecutorService businessThreadPool = Executors.newCachedThreadPool();

    public RpcServer() {
        // Phase 1 的构造函数不需要了
    }

    public void register(Object service) {
        String interfaceName = service.getClass().getInterfaces()[0].getName();
        serviceRegistry.put(interfaceName, service);
        System.out.println("服务已注册: " + interfaceName);
    }

    public void start(int port) throws Exception {
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
        }
    }
}


