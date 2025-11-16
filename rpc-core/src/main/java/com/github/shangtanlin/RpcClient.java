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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RpcClient {

    private Registry registry; // *** 新增 ***
    private LoadBalancer loadBalancer; // *** 新增 ***

    private final EventLoopGroup group = new NioEventLoopGroup();
    // 存储 Channel，(host:port -> Channel)
    private final ConcurrentHashMap<String, Channel> channelMap = new ConcurrentHashMap<>();

    // 存储 (requestId -> 对应的 Future)
    // RpcClientHandler 会从这里取 Future 并设置结果
    public static final ConcurrentHashMap<Integer, CompletableFuture<RpcResponse>> PENDING_FUTURES = new ConcurrentHashMap<>();

    // 用于生成全局唯一的 requestId
    private static final AtomicInteger REQUEST_ID_GENERATOR = new AtomicInteger(1);

    // *** 构造函数修改 ***
    public RpcClient(Registry registry, LoadBalancer loadBalancer) {
        this.registry = registry;
        this.loadBalancer = loadBalancer;
        // ... (其他初始化)
    }

    // *** getProxy 方法修改 (核心！) ***
    // (不再需要 host 和 port)
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> serviceClass) {
        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class<?>[]{serviceClass},
                new RpcInvocationHandler(serviceClass) // 传入 serviceClass
        );
    }

    private class RpcInvocationHandler implements InvocationHandler {
        private final Class<?> serviceClass;

        public RpcInvocationHandler(Class<?> serviceClass) {
            this.serviceClass = serviceClass;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // *** 1. 服务发现 (新增) ***
            String serviceName = serviceClass.getName();
            List<String> addresses = registry.discover(serviceName);
            if (addresses == null || addresses.isEmpty()) {
                throw new RuntimeException("没有可用的服务: " + serviceName);
            }

            // *** 2. 负载均衡 (新增) ***
            String targetAddress = loadBalancer.select(addresses); // e.g., "127.0.0.1:8080"
            String[] hostPort = targetAddress.split(":");
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);

            // *** 3. 获取 Channel (现在是动态的) ***
            Channel channel = getOrCreateChannel(host, port);

            // 4. 创建 RpcRequest
            RpcRequest request = new RpcRequest();
            request.setRequestId(REQUEST_ID_GENERATOR.getAndIncrement()); // 获取唯一ID
            request.setInterfaceName(serviceClass.getName());
            request.setMethodName(method.getName());
            request.setParameterTypes(method.getParameterTypes());
            request.setParameters(args);

            // 5. 创建一个 CompletableFuture 来等待异步结果
            CompletableFuture<RpcResponse> future = new CompletableFuture<>();
            PENDING_FUTURES.put(request.getRequestId(), future);

            // 6. 发送 RpcRequest (异步)
            channel.writeAndFlush(request);
            System.out.println("客户端发起调用 (to " + targetAddress + "): " + request);

            // 7. 阻塞等待结果 (同步等待异步)
            // (可以设置超时)
            RpcResponse response = future.get(); // .get(5, TimeUnit.SECONDS);

            // 8. 处理响应
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