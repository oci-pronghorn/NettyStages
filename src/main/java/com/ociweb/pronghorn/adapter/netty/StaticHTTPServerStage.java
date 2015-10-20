package com.ociweb.pronghorn.adapter.netty;

import java.security.cert.CertificateException;

import javax.net.ssl.SSLException;

import com.ociweb.pronghorn.adapter.netty.impl.HttpStaticFileServerHandler;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.internal.SystemPropertyUtil;

public class StaticHTTPServerStage extends PronghornStage{

    private static final boolean SSL = System.getProperty("ssl") != null;
    private static final int PORT = Integer.parseInt(System.getProperty("port", SSL? "8443" : "8080"));
    
    private ChannelFuture channelFuture;
    private Channel channel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    
    protected StaticHTTPServerStage(GraphManager graphManager) {
        super(graphManager, NONE, NONE);
        
        //enable shutdown to be called without looking for any pipes first
        GraphManager.addNota(graphManager, GraphManager.PRODUCER, GraphManager.PRODUCER, this);
                
        System.out.println("The AngularJS app must be in:"+SystemPropertyUtil.get("user.dir"));
                
        
    }

    public static void setRelativeAppFolderRoot(String appFolder) {
        
        //TODO: this is a big hack, the idea is to avoid the file system and put static/fixed js files in the resource folder
        //      this just shows what can work using the example povided by Netty.io
        System.setProperty("user.dir", SystemPropertyUtil.get("user.dir")+appFolder);
    }

    @Override
    public void startup() {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            try {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(SslProvider.JDK).build();
            } catch (CertificateException e) {
               throw new RuntimeException(e);
            } catch (SSLException e) {
                throw new RuntimeException(e);
            }
        } else {
            sslCtx = null;
        }

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
                        
        //for back pressure into the ring buffers
        b.childOption(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, 65536); //NOTE: should customize based on usage.
        b.childOption(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, 4096);
                
        //for reuse of ByteBuffers
        b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
                
        b.group(bossGroup, workerGroup)
         .channel(NioServerSocketChannel.class)
         .handler(new LoggingHandler(LogLevel.INFO))
         .childHandler(new PrivateChannelInitializer(sslCtx));   
        
        channelFuture = b.bind(PORT);

    }
    
    
    @Override
    public void run() {
        
        if (null==channel) {
            if (channelFuture.isSuccess()) {
                channel = channelFuture.channel();
                System.out.println("Open your web browser and navigate to " +
                        (SSL? "https" : "http") + "://127.0.0.1:" + PORT + '/');
            } else if (channelFuture.isDone()) {
                //Not success but done
                throw new UnsupportedOperationException();
            }
            
        } else {
            //this is just a static file server so we have no other work to do here.

        }        
    }
    
    @Override
    public void shutdown() {
        
        try {
            
            channel.closeFuture().sync();
            
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        } 
        
    }

    private class PrivateChannelInitializer extends ChannelInitializer<SocketChannel> {

        private final SslContext sslCtx;

        public PrivateChannelInitializer(SslContext sslCtx) {
            this.sslCtx = sslCtx;
        }

        @Override
        public void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            if (sslCtx != null) {
                pipeline.addLast(sslCtx.newHandler(ch.alloc()));
            }
            pipeline.addLast(new HttpServerCodec());
      //      pipeline.addLast(new HttpObjectAggregator(65536));
      //      pipeline.addLast(new ChunkedWriteHandler());
            pipeline.addLast(new HttpStaticFileServerHandler()); 
        }
    }
}
