package com.xsm.client.netty;

import com.xsm.common.protocol.RpcDecoder;
import com.xsm.common.protocol.RpcEncoder;
import com.xsm.common.protocol.RpcRequest;
import com.xsm.common.protocol.RpcResponse;
import com.xsm.common.protocol.serialize.JSONSerializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PreDestroy;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author xsm
 * @Date 2020/5/20 23:42
 * netty 客户端
 */
@Slf4j
public class NettyClient {

    private EventLoopGroup eventLoopGroup;

    private Channel channel;

    private ClientHandler clientHandler;

    private String host;

    private Integer port;

    private static final int MAX_RETRY = 5;

    public NettyClient(String host, Integer port) {
        this.host = host;
        this.port = port;
    }

    public void connect(){
        clientHandler = new ClientHandler();
        eventLoopGroup = new NioEventLoopGroup();
        // 启动类
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                // 指定传输使用的Channel
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 4));
                        pipeline.addLast(new RpcEncoder(RpcRequest.class, new JSONSerializer()));
                        pipeline.addLast(new RpcDecoder(RpcResponse.class, new JSONSerializer()));
                        pipeline.addLast(clientHandler);
                    }
                });
        connect(bootstrap, host, port, MAX_RETRY);
    }

    /**
     * 失败重连
     * @param bootstrap
     * @param host
     * @param port
     * @param maxRetry
     */
    private void connect(Bootstrap bootstrap, String host, Integer port, int retry) {
        ChannelFuture channelFuture = bootstrap.connect(host, port).addListener(future -> {
            if (future.isSuccess()) {
                log.info("连接服务端成功");
            }
            else if (retry == 0){
                log.error("重连次数已用完, 放弃链接");
            }
            else {
                // 第几次重连
                int order = (MAX_RETRY - retry) + 1;
                int delay = 1 << order;
                log.error("{} : 连接失败，第 {} 重连....", new Date(), order);
                bootstrap.config().group().schedule(() -> connect(bootstrap, host, port, retry - 1), delay, TimeUnit.SECONDS);
            }
        });
        channel = channelFuture.channel();
    }

    public RpcResponse send(final RpcRequest request){
        try {
            channel.writeAndFlush(request).await();
        } catch (InterruptedException e) {
            log.error("error", e);
        }
        return clientHandler.getRpcResponse(request.getRequestId());
    }

    /**
     * 销毁
     */
    @PreDestroy
    public void close(){
        eventLoopGroup.shutdownGracefully();
        channel.closeFuture().syncUninterruptibly();
    }
}
