package com.ociweb.pronghorn.adapter.netty;

import java.security.cert.CertificateException;

import javax.net.ssl.SSLException;

import com.ociweb.pronghorn.adapter.netty.impl.PronghornFullDuplexManager;
import com.ociweb.pronghorn.adapter.netty.impl.WebSocketServerHandler;
import com.ociweb.pronghorn.adapter.netty.impl.WebSocketServerHandlerPronghornAdapter;
import com.ociweb.pronghorn.pipe.Pipe;
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
import io.netty.util.internal.SystemPropertyUtil;
//import io.netty.handler.codec.http.websocketx.extensions.compression.*;

public class WebSocketServerPronghornStage extends PronghornStage{

    public static final boolean SSL = System.getProperty("ssl") != null;
    private static final int PORT = Integer.parseInt(System.getProperty("port", SSL? "8443" : "8088")); //NOTE: set to avoid 8080 needed by MQTT broker, not sure why moquette must have that port.
    
    private ChannelFuture channelFuture;
    private Channel channel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private PronghornFullDuplexManager manager;
    
    public WebSocketServerPronghornStage(GraphManager graphManager, Pipe[] inputPipes, Pipe[] outputPipes, EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        super(graphManager, inputPipes, outputPipes);
        
        //enable shutdown to be called without looking for any pipes first
        GraphManager.addAnnotation(graphManager, GraphManager.PRODUCER, GraphManager.PRODUCER, this);
        
        if (null!=SystemPropertyUtil.get("web.application.dir")) {
            System.out.println("The web application must be in:"+SystemPropertyUtil.get("web.application.dir"));
        } else {
            System.out.println("Using internal resources for web application");
        }
        this.manager = new PronghornFullDuplexManager(outputPipes, inputPipes);
        this.bossGroup = bossGroup;
        this.workerGroup = workerGroup;
    }

    public static void setRelativeAppFolderRoot(String appFolder) {
        
        System.setProperty("web.application.dir", appFolder);
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

        

        ServerBootstrap b = new ServerBootstrap();                
                        
        //for back pressure into the ring buffers
        b.childOption(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, 96*1024); //NOTE: should customize based on usage.
        b.childOption(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, 2*1024);
        
        //for reuse of ByteBuffers
        b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
                
        b.option(ChannelOption.SO_REUSEADDR, true);
        b.option(ChannelOption.TCP_NODELAY, true);
        b.option(ChannelOption.SO_KEEPALIVE, false);
        b.option(ChannelOption.SO_LINGER, 0);        
        
        b.childOption(ChannelOption.SO_REUSEADDR, true);
        b.childOption(ChannelOption.TCP_NODELAY, true);
        b.childOption(ChannelOption.SO_KEEPALIVE, false);
        b.childOption(ChannelOption.SO_LINGER, 0);
        
        b.childOption(ChannelOption.SO_SNDBUF, 128*1024);
        b.childOption(ChannelOption.SO_RCVBUF, 128*1024);
        
        
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
                throw new RuntimeException(channelFuture.cause());
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
            pipeline.addLast(new HttpObjectAggregator(65536));
         //   pipeline.addLast(new WebSocketServerCompressionHandler()); //TODO: class missing from jar
            
            pipeline.addLast(new WebSocketServerHandlerPronghornAdapter(manager)); //this works directly with pipes
        }
    }
}
