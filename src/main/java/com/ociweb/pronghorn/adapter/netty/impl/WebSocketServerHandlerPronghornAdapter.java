package com.ociweb.pronghorn.adapter.netty.impl;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.concurrent.atomic.AtomicInteger;

import com.ociweb.pronghorn.adapter.netty.WebSocketServerStage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.SystemPropertyUtil;

/**
 * Handles handshakes and messages
 */
public class WebSocketServerHandlerPronghornAdapter extends SimpleChannelInboundHandler<Object> {

    private static final String WEBSOCKET_PATH = "/websocket";
    static final AttributeKey<PronghornFullDuplex> PRONGHORN_KEY = AttributeKey.newInstance("Pipes");
    
    private final PronghornFullDuplexManager pfdm;
    private WebSocketServerHandshaker handshaker;
       
    public WebSocketServerHandlerPronghornAdapter(PronghornFullDuplexManager pfdm) {
        this.pfdm = pfdm;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        } else if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } 
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        // Handle a bad request.
        if (!req.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }

        // Allow only GET methods.
        if (req.method() != GET) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }
            
        String root = SystemPropertyUtil.get("web.application.dir");
        String path = HttpStaticFileServerHandler.sanitizeUri(req.uri(), root);

        
        if (null!=path && !path.endsWith(WEBSOCKET_PATH)) {
            
            System.out.println("PATH:"+path);
            
            if (null==root) {
                //pull from resources
                try {
                     HttpStaticFileServerHandler.sendResource(ctx, req, path);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                
            } else {
            
                File file = new File(path);
                try {
                    HttpStaticFileServerHandler.sendFile(ctx, req, file);
                } catch (ParseException e) {
                   throw new RuntimeException(e);
                } catch (IOException e) {
                   throw new RuntimeException(e);
                }
                
                return;
            }
                        
        }
         

        // Handshake
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(req), null, true);
        handshaker = wsFactory.newHandshaker(req);
        
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            //NOTE: possible performance improvement, rewrite the pipe to streamline now that we have completed the hand shake?
            GenericFutureListener<ChannelFuture> webSocketIsOpen = new  GenericFutureListener<ChannelFuture>() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    
                    //the runnable will call read as needed based on how much data is on the outgoing pipe
                    future.channel().config().setAutoRead(false);
                    
                    Attribute<PronghornFullDuplex> attrib = future.channel().attr(PRONGHORN_KEY);
                    assert(null == attrib.get()) : "This new connection should not already have anything set";
                    
                    attrib.set(pfdm.buildNewDuplexObject(future.channel()));
                          
                }
                
            };
            handshaker.handshake(ctx.channel(), req).addListener(webSocketIsOpen);
        }
    }

   

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        //  System.out.println("new content to send");

        // Check for closing frame
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        Attribute<PronghornFullDuplex> attrib = ctx.channel().attr(PRONGHORN_KEY);
        if (!frame.isFinalFragment()) {
            attrib.get().partialSendToPipe(frame.content());         
        } else {
            attrib.get().sendToPipe(frame.content());
        }      
        
    }

    private static void sendHttpResponse(
            ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        // Generate an error page if response getStatus code is not OK (200).
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
            HttpUtil.setContentLength(res, res.content().readableBytes());
        }

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!HttpUtil.isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private static String getWebSocketLocation(FullHttpRequest req) {
        String location =  req.headers().get(HttpHeaderNames.HOST) + WEBSOCKET_PATH;
        if (WebSocketServerStage.SSL) {
            return "wss://" + location;
        } else {
            return "ws://" + location;
        }
    }
}
