package com.github.shangtanlin.processor;

import com.github.shangtanlin.RpcServer;
import com.github.shangtanlin.annotation.RpcService;
import com.github.shangtanlin.config.RpcProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;

/**
 * 服务端启动引导类
 * Spring Boot 启动完毕后执行
 */
public class RpcServerBootstrap implements CommandLineRunner {

    private final ApplicationContext applicationContext;
    private final RpcServer rpcServer;
    private final RpcProperties properties;

    public RpcServerBootstrap(ApplicationContext applicationContext, RpcServer rpcServer, RpcProperties properties) {
        this.applicationContext = applicationContext;
        this.rpcServer = rpcServer;
        this.properties = properties;
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. 获取所有带 @RpcService 注解的 Bean
        // Map<BeanName, BeanInstance>
        java.util.Map<String, Object> beans = applicationContext.getBeansWithAnnotation(RpcService.class);

        if (beans.isEmpty()) {
            // 如果没有服务提供者，就不启动 Netty 了
            return;
        }

        // 2. 逐个发布服务
        for (Object bean : beans.values()) {
            rpcServer.publishService(bean);
        }

        // 3. 启动 Netty 服务器 (必须异步！否则会阻塞 Spring Boot 主线程)
        new Thread(() -> {
            try {
                rpcServer.start(properties.getServerPort());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
