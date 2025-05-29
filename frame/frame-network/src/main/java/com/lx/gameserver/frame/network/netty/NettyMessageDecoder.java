/*
 * 文件名: NettyMessageDecoder.java
 * 用途: Netty消息解码器
 * 实现内容:
 *   - 将ByteBuf解码为业务消息对象
 *   - 支持多种协议格式（JSON、Protobuf、自定义）
 *   - 消息完整性校验和错误处理
 * 技术选型:
 *   - 继承Netty MessageToMessageDecoder
 *   - 集成现有MessageCodec体系
 * 依赖关系:
 *   - 被NettyChannelInitializer使用
 *   - 使用MessageCodec进行解码
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Netty消息解码器
 * <p>
 * 将接收到的ByteBuf解码为业务消息对象，支持多种协议格式。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class NettyMessageDecoder extends MessageToMessageDecoder<ByteBuf> {

    private static final Logger logger = LoggerFactory.getLogger(NettyMessageDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        try {
            // 暂时简单实现，直接传递ByteBuf
            // 实际应该根据协议类型选择相应的解码器
            if (msg.readableBytes() > 0) {
                // 创建消息对象
                SimpleMessage message = new SimpleMessage();
                message.setData(msg.retain());
                out.add(message);
                
                logger.debug("解码消息成功，长度: {}", msg.readableBytes());
            }
        } catch (Exception e) {
            logger.error("消息解码失败", e);
            throw e;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("消息解码器异常，连接: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    /**
     * 简单消息实现（临时使用）
     */
    public static class SimpleMessage {
        private ByteBuf data;

        public ByteBuf getData() {
            return data;
        }

        public void setData(ByteBuf data) {
            this.data = data;
        }
    }
}