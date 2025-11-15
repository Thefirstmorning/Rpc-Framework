package com.github.shangtanlin;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;


// 编码器：将 RpcRequest/RpcResponse 对象 -> 编码为 -> ByteBuf
public class RpcEncoder extends MessageToByteEncoder<Object> {

    // 假设我们现在只有 Kryo 序列化
    private Serializer serializer = new KryoSerializer();

    // 魔数
    private static final byte[] MAGIC_NUMBER = new byte[]{(byte) 'r', (byte) 'p', (byte) 'c', (byte) '!'};

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        // 1. 写魔数
        out.writeBytes(MAGIC_NUMBER);

        // 2. 写版本
        out.writeByte(1);

        // 3. 写序列化方式
        out.writeByte(1); // 1:Kryo

        // 4. 写消息类型 (*** 修改点 ***)
        byte messageType;
        byte[] body = null;
        int requestId = 0;

        if (msg instanceof RpcRequest) {
            messageType = 1; // 1: Request
            body = serializer.serialize(msg);
            requestId = ((RpcRequest) msg).getRequestId();
        } else if (msg instanceof RpcResponse) {
            messageType = 2; // 2: Response
            body = serializer.serialize(msg);
            requestId = ((RpcResponse) msg).getRequestId();
        } else if (msg == HeartbeatPacket.PING) {
            messageType = 3; // 3: PING
            body = new byte[0]; // 心跳没有数据体
        } else if (msg == HeartbeatPacket.PONG) {
            messageType = 4; // 4: PONG
            body = new byte[0]; // 心跳没有数据体
        } else {
            System.err.println("不支持的消息类型: " + msg.getClass());
            return;
        }

        out.writeByte(messageType);

        // 5. 写请求ID
        out.writeInt(requestId); // (心跳包的 requestId 为 0)

        // 6. 写数据长度 (*** 修改点 ***)
        int dataLength = (body != null) ? body.length : 0;
        out.writeInt(dataLength);

        // 7. 写数据体 (*** 修改点 ***)
        if (dataLength > 0) {
            out.writeBytes(body);
        }
    }
}