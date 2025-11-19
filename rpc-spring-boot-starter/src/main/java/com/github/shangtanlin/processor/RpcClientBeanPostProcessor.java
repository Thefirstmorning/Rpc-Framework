package com.github.shangtanlin.processor;

import com.github.shangtanlin.RpcClient;
import com.github.shangtanlin.annotation.RpcReference;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;

/**
 * 客户端后置处理器
 * 扫描所有 Bean，如果字段上有 @RpcReference，就注入代理对象
 */
public class RpcClientBeanPostProcessor implements BeanPostProcessor {

    private final RpcClient rpcClient;

    public RpcClientBeanPostProcessor(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 反射获取所有字段
        Field[] fields = bean.getClass().getDeclaredFields();
        for (Field field : fields) {
            // 检查注解
            if (field.isAnnotationPresent(RpcReference.class)) {
                try {
                    // 1. 获取字段类型 (接口)
                    Class<?> interfaceClass = field.getType();

                    // 2. 通过 RpcClient 生成代理对象
                    Object proxy = rpcClient.getProxy(interfaceClass);

                    // 3. 暴力反射，注入字段
                    field.setAccessible(true);
                    field.set(bean, proxy);

                    System.out.println("RPC 代理注入成功: " + beanName + "." + field.getName());

                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return bean;
    }
}