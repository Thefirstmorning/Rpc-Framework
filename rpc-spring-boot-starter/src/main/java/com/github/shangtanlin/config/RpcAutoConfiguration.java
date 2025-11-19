package com.github.shangtanlin.config;


import com.github.shangtanlin.*;
import com.github.shangtanlin.processor.RpcClientBeanPostProcessor;
import com.github.shangtanlin.processor.RpcServerBootstrap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RpcProperties.class)
public class RpcAutoConfiguration {

    private final RpcProperties properties;

    public RpcAutoConfiguration(RpcProperties properties) {
        this.properties = properties;
    }

    /**
     * 1. 自动创建注册中心 Registry Bean
     */
    @Bean
    public Registry registry() {
        return new ZooKeeperRegistry(properties.getZkAddr());
    }

    /**
     * 2. 自动创建负载均衡 LoadBalancer Bean
     */
    @Bean
    public LoadBalancer loadBalancer() {
        return new RandomLoadBalancer();
    }

    /**
     * 3. 自动创建 RpcServer Bean
     * 注意：这里只是创建对象，还没启动 Netty
     */
    @Bean
    public RpcServer rpcServer(Registry registry) {
        // 这里的 serverAddr 简单拼接了端口，实际生产中需要获取本机真实 IP
        String fullServerAddress = properties.getServerAddr() + ":" + properties.getServerPort();
        return new RpcServer(registry, fullServerAddress);
    }

    /**
     * 4. 自动创建 RpcClient Bean
     */
    @Bean
    public RpcClient rpcClient(Registry registry, LoadBalancer loadBalancer) {
        return new RpcClient(registry, loadBalancer);
    }

    // ... 接下来我们需要两个特殊的 Bean 来处理注解
    /**
     * 5. 注册客户端注入处理器
     */
    @Bean
    public RpcClientBeanPostProcessor rpcClientBeanPostProcessor(RpcClient rpcClient) {
        return new RpcClientBeanPostProcessor(rpcClient);
    }

    /**
     * 6. 注册服务端启动引导
     */
    @Bean
    public RpcServerBootstrap rpcServerBootstrap(ApplicationContext ctx, RpcServer server, RpcProperties properties) {
        return new RpcServerBootstrap(ctx, server, properties);
    }
}