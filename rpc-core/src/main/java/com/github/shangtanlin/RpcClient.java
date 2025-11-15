package com.github.shangtanlin;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RpcClient {

    private final EventLoopGroup group = new NioEventLoopGroup();
    // 存储 Channel，(host:port -> Channel)
    private final ConcurrentHashMap<String, Channel> channelMap = new ConcurrentHashMap<>();

    // 存储 (requestId -> 对应的 Future)
    // RpcClientHandler 会从这里取 Future 并设置结果
    public static final ConcurrentHashMap<Integer, CompletableFuture<RpcResponse>> PENDING_FUTURES = new ConcurrentHashMap<>();

    // 用于生成全局唯一的 requestId
    private static final AtomicInteger REQUEST_ID_GENERATOR = new AtomicInteger(1);

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> serviceClass, String host, int port) {
        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class<?>[]{serviceClass},
                new RpcInvocationHandler(host, port, serviceClass)
        );
    }

    private class RpcInvocationHandler implements InvocationHandler {
        private final String host;
        private final int port;
        private final Class<?> serviceClass;

        public RpcInvocationHandler(String host, int port, Class<?> serviceClass) {
            this.host = host;
            this.port = port;
            this.serviceClass = serviceClass;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 1. 获取或创建 Channel
            Channel channel = getOrCreateChannel(host, port);

            // 2. 创建 RpcRequest
            RpcRequest request = new RpcRequest();
            request.setRequestId(REQUEST_ID_GENERATOR.getAndIncrement()); // 获取唯一ID
            request.setInterfaceName(serviceClass.getName());
            request.setMethodName(method.getName());
            request.setParameterTypes(method.getParameterTypes());
            request.setParameters(args);

            // 3. 创建一个 CompletableFuture 来等待异步结果
            CompletableFuture<RpcResponse> future = new CompletableFuture<>();
            PENDING_FUTURES.put(request.getRequestId(), future);

            // 4. 发送 RpcRequest (异步)
            channel.writeAndFlush(request);

            System.out.println("客户端发起调用: " + request);

            // 5. 阻塞等待结果 (同步等待异步)
            // (可以设置超时)
            RpcResponse response = future.get(); // .get(5, TimeUnit.SECONDS);

            // 6. 处理响应
            if (response.hasException()) {
                throw response.getException();
            } else {
                return response.getResult();
            }
        }
    }

    // 获取或创建到服务端的连接 (Channel)
    private Channel getOrCreateChannel(String host, int port) throws InterruptedException {
        String address = host + ":" + port;
        Channel channel = channelMap.get(address);

        if (channel != null && channel.isActive()) {
            return channel;
        }

        // Channel 不可用，需要新建
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                // (*** 新增 ***)
                                // IdleStateHandler: 30秒内未发送任何数据(写空闲)，触发 userEventTriggered
                                .addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS))
                                .addLast(new RpcDecoder()) // 入站：解码
                                .addLast(new RpcEncoder()) // 出站：编码
                                // (*** 新增 ***)
                                .addLast(new HeartbeatClientHandler()) // 心跳处理器
                                .addLast(new RpcClientHandler()); // 业务处理器
                    }
                });

        ChannelFuture future = b.connect(host, port).sync();
        channel = future.channel();
        channelMap.put(address, channel);
        return channel;
    }

    // (需要一个方法来关闭 group)
    public void shutdown() {
        group.shutdownGracefully();
    }
}