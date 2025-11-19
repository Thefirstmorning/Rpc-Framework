package com.github.shangtanlin.annotation;

import java.lang.annotation.*;

/**
 * 服务消费者注解
 * 作用在字段上，标识该字段需要注入 RPC 代理对象
 */
@Target(ElementType.FIELD) // 作用于字段
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcReference {
    // 可以扩展：指定版本、超时时间等
}