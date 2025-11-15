package com.github.shangtanlin;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * 服务端心跳处理器
 * 1. 捕获读空闲事件，如果超时未收到数据，则关闭连接。
 * 2. 捕获 PING 包，回复 PONG 包。
 */
public class HeartbeatServerHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                // 读空闲事件
                System.out.println("服务端: " + ctx.channel().remoteAddress() + " 60秒内未收到数据(读空闲)，关闭连接。");
                ctx.close();
            }
        } else {
            // 不是空闲事件，传递给下一个 handler
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg == HeartbeatPacket.PING) {
            // 收到 PING 包
            System.out.println("服务端: 收到 " + ctx.channel().remoteAddress() + " 的心跳 PING");

            // 回复一个 PONG 包
            ctx.writeAndFlush(HeartbeatPacket.PONG);
        } else {
            // 不是 PING 包，传递给下一个 handler (RpcServerHandler)
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 捕获那个 "Connection reset" 异常并安静处理
        if (cause instanceof java.io.IOException && "Connection reset by peer".equals(cause.getMessage())) {
            System.out.println("服务端: 监测到 " + ctx.channel().remoteAddress() + " 异常断开(Connection reset)");
            ctx.close(); // 安静关闭
        } else {
            // 其他异常，还是打印一下
            super.exceptionCaught(ctx, cause);
        }
    }
}