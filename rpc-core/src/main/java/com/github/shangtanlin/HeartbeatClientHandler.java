package com.github.shangtanlin;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * 客户端心跳处理器
 * 1. 捕获写空闲事件，如果超时未发送数据，则发送 PING 包。
 * 2. 捕获 PONG 包，打印日志。
 */
public class HeartbeatClientHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.WRITER_IDLE) {
                // 写空闲事件
                System.out.println("客户端: 30秒内未发送数据(写空闲)，发送 PING 包...");
                ctx.writeAndFlush(HeartbeatPacket.PING);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg == HeartbeatPacket.PONG) {
            // 收到 PONG 包
            System.out.println("客户端: 收到服务端心跳 PONG");
            // 收到 PONG，说明连接正常，不需要做任何事，也不需要传递给 RpcClientHandler
        } else {
            // 不是 PONG 包，传递给下一个 handler (RpcClientHandler)
            ctx.fireChannelRead(msg);
        }
    }
}