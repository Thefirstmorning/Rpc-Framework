package com.github.shangtanlin.annotation;

import org.springframework.stereotype.Component;
import java.lang.annotation.*;

/**
 * 服务提供者注解
 * 作用在类上，标识该类是一个 RPC 服务实现类
 */
@Target(ElementType.TYPE) // 作用于类
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component // 关键！让 Spring 把它当成一个 Bean 管理
public @interface RpcService {
    // 可以扩展：指定服务版本、分组等
    // String version() default "1.0";
}