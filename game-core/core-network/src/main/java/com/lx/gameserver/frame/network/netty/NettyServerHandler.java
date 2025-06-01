/*
 * 文件名: NettyServerHandler.java
 * 用途: Netty服务器业务处理器
 * 实现内容:
 *   - 处理连接建立和断开事件
 *   - 处理消息接收和发送
 *   - 处理异常和空闲事件
 *   - 连接状态管理和统计
 * 技术选型:
 *   - 继承Netty ChannelInboundHandlerAdapter
 *   - 异步事件处理机制
 * 依赖关系:
 *   - 被NettyChannelInitializer使用
 *   - 与NettyConnection协作
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Netty服务器业务处理器
 * <p>
 * 处理连接生命周期事件和消息处理，与上层业务逻辑集成。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class NettyServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(NettyServerHandler.class);

    private final Consumer<Channel> onChannelActive;
    private final Consumer<Channel> onChannelInactive;

    /**
     * 构造函数
     *
     * @param onChannelActive   Channel激活回调
     * @param onChannelInactive Channel非激活回调
     */
    public NettyServerHandler(Consumer<Channel> onChannelActive, Consumer<Channel> onChannelInactive) {
        this.onChannelActive = onChannelActive;
        this.onChannelInactive = onChannelInactive;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("连接建立: {}", ctx.channel().remoteAddress());
        
        try {
            if (onChannelActive != null) {
                onChannelActive.accept(ctx.channel());
            }
        } catch (Exception e) {
            logger.error("处理连接建立事件失败", e);
        }
        
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("连接断开: {}", ctx.channel().remoteAddress());
        
        try {
            if (onChannelInactive != null) {
                onChannelInactive.accept(ctx.channel());
            }
        } catch (Exception e) {
            logger.error("处理连接断开事件失败", e);
        }
        
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        logger.debug("接收消息: {}, 来自: {}", msg.getClass().getSimpleName(), ctx.channel().remoteAddress());
        
        try {
            // 这里应该将消息传递给业务层处理
            // 暂时简单处理，直接回显
            if (msg instanceof NettyMessageDecoder.SimpleMessage) {
                // 回显消息
                ctx.writeAndFlush(msg);
            }
        } catch (Exception e) {
            logger.error("处理消息失败", e);
        } finally {
            // 释放消息资源
            if (msg instanceof NettyMessageDecoder.SimpleMessage) {
                NettyMessageDecoder.SimpleMessage simpleMsg = (NettyMessageDecoder.SimpleMessage) msg;
                if (simpleMsg.getData() != null) {
                    simpleMsg.getData().release();
                }
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleEvent = (IdleStateEvent) evt;
            logger.debug("连接空闲事件: {}, 连接: {}", idleEvent.state(), ctx.channel().remoteAddress());
            
            switch (idleEvent.state()) {
                case READER_IDLE:
                    logger.warn("连接读空闲，关闭连接: {}", ctx.channel().remoteAddress());
                    ctx.close();
                    break;
                case WRITER_IDLE:
                    // 发送心跳
                    logger.debug("连接写空闲，发送心跳: {}", ctx.channel().remoteAddress());
                    // ctx.writeAndFlush(createHeartbeatMessage());
                    break;
                case ALL_IDLE:
                    logger.warn("连接全空闲，关闭连接: {}", ctx.channel().remoteAddress());
                    ctx.close();
                    break;
            }
        }
        
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("连接异常: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}