package com.ociweb.pronghorn.adapter.netty.impl;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.util.MemberHolderVisitor;
import com.ociweb.pronghorn.util.ServiceObjectHolder;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.concurrent.GenericFutureListener;

public class SubscriptionDistributor implements MemberHolderVisitor, GenericFutureListener<ChannelFuture>{

    private final AtomicInteger subs;
    private final int bytesWorkingTail; 
    private final long workingTail;
    private final Pipe pipe;
    private final ByteBuffer bba;
    private final ByteBuffer bbb;
    private final ServiceObjectHolder<Channel> channelHolder;
    
    private boolean beginShutdownProcess = false;
    
    
    public SubscriptionDistributor(Pipe pipe, int bytesWorkingTail, long workingTail, ByteBuffer bba, ByteBuffer bbb, ServiceObjectHolder<Channel> channelHolder) {
        this.bytesWorkingTail = bytesWorkingTail;
        this.workingTail = workingTail;
        this.pipe = pipe;
        this.subs = new AtomicInteger();
        this.bba = bba;
        this.bbb = bbb;
        this.channelHolder = channelHolder;
    }
    
    @Override
    public void visit(long channelId) {
        //write every one, this may block.
        Channel ch = channelHolder.getValid(channelId);
        if (null!=ch) {
            subs.incrementAndGet();        
            ch.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(bba,bbb))).addListener(this);
        }
    }
    
    @Override
    public void finished() {
        synchronized(subs) { //for shutdown must sync with operation complete to ensure the Release is not lost.
            if (0==subs.get()) {
                Pipe.batchedReleasePublish(pipe, bytesWorkingTail, workingTail);
                Pipe.releaseAllBatchedReads(pipe);
            }
            beginShutdownProcess = true;
        }
    }
    

    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
        synchronized(subs) {
            if (0==subs.decrementAndGet() && beginShutdownProcess) {
                //now release this point in the ring.
                Pipe.batchedReleasePublish(pipe,bytesWorkingTail, workingTail);  
                Pipe.releaseAllBatchedReads(pipe);
            }
        }
    }

}
