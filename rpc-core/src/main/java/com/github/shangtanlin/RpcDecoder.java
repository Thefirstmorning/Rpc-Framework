package com.github.shangtanlin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder; // 使用 ReplayingDecoder 更简单
import java.util.List;

// 解码器：将 ByteBuf -> 解码为 -> RpcRequest/RpcResponse 对象
// ReplayingDecoder 无需我们自己管理 "readIndex" 和 "checkpoint"
public class RpcDecoder extends ReplayingDecoder<Void> {

    // 假设我们现在只有 Kryo 序列化
    private Serializer serializer = new KryoSerializer();

    // 头部长度 15 bytes = 4(magic) + 1(ver) + 1(seri) + 1(type) + 4(reqId) + 4(len)
    private static final int HEADER_LENGTH = 15;

    // 魔数
    private static final byte[] MAGIC_NUMBER = new byte[]{(byte) 'r', (byte) 'p', (byte) 'c', (byte) '!'};

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 1. 检查魔数
        for (int i = 0; i < 4; i++) {
            if (in.readByte() != MAGIC_NUMBER[i]) {
                ctx.close(); // 不是我们的协议，关闭连接
                return;
            }
        }

        // 2. 读取版本 (1 byte) - 暂时不用
        in.readByte();
        // 3. 读取序列化方式 (1 byte) - 暂时不用
        byte serializerType = in.readByte();
        // 4. 读取消息类型 (1 byte)
        byte messageType = in.readByte();
        // 5. 读取请求ID (4 bytes)
        int requestId = in.readInt();
        // 6. 读取数据长度 (4 bytes)
        int dataLength = in.readInt();

        // 7. (*** 新增 ***) 处理心跳包
        if (messageType == 3) { // 3: PING
            out.add(HeartbeatPacket.PING);
            return; // PING 没有数据体，直接返回
        }
        if (messageType == 4) { // 4: PONG
            out.add(HeartbeatPacket.PONG);
            return; // PONG 没有数据体，直接返回
        }

        // 8. (*** 修改点 ***) 处理 RpcRequest / RpcResponse
        if (dataLength <= 0) {
            // (非心跳包，但数据长度为0，可能是个无效包)
            return;
        }

        byte[] body = new byte[dataLength];
        in.readBytes(body);

        // 9. 反序列化
        Class<?> clazz = (messageType == 1) ? RpcRequest.class : RpcResponse.class;
        Object obj = serializer.deserialize(body, clazz);

        // (在 RpcRequest/Response 中设置 requestId，
        //  Kryo 反序列化时不会调用构造函数或 setter，
        //  所以我们在这里手动设置一下)
        if (obj instanceof RpcRequest) {
            ((RpcRequest) obj).setRequestId(requestId);
        } else if (obj instanceof RpcResponse) {
            ((RpcResponse) obj).setRequestId(requestId);
        }

        // 9. 将解码后的对象传递给下一个 Handler
        out.add(obj);
    }
}
