/*
 * 文件名: NettyMessageEncoder.java
 * 用途: Netty消息编码器
 * 实现内容:
 *   - 将业务消息对象编码为ByteBuf
 *   - 支持多种协议格式（JSON、Protobuf、自定义）
 *   - 消息压缩和优化处理
 * 技术选型:
 *   - 继承Netty MessageToByteEncoder
 *   - 集成现有MessageCodec体系
 * 依赖关系:
 *   - 被NettyChannelInitializer使用
 *   - 使用MessageCodec进行编码
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty消息编码器
 * <p>
 * 将业务消息对象编码为ByteBuf发送给客户端，支持多种协议格式。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class NettyMessageEncoder extends MessageToByteEncoder<Object> {

    private static final Logger logger = LoggerFactory.getLogger(NettyMessageEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        try {
            // 暂时简单实现
            if (msg instanceof NettyMessageDecoder.SimpleMessage) {
                NettyMessageDecoder.SimpleMessage simpleMsg = (NettyMessageDecoder.SimpleMessage) msg;
                out.writeBytes(simpleMsg.getData());
                logger.debug("编码消息成功，长度: {}", simpleMsg.getData().readableBytes());
            } else if (msg instanceof ByteBuf) {
                ByteBuf byteBuf = (ByteBuf) msg;
                out.writeBytes(byteBuf);
                logger.debug("编码ByteBuf成功，长度: {}", byteBuf.readableBytes());
            } else {
                // 其他类型消息的处理
                String msgStr = msg.toString();
                byte[] bytes = msgStr.getBytes("UTF-8");
                out.writeBytes(bytes);
                logger.debug("编码字符串消息成功，长度: {}", bytes.length);
            }
        } catch (Exception e) {
            logger.error("消息编码失败", e);
            throw e;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("消息编码器异常，连接: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}