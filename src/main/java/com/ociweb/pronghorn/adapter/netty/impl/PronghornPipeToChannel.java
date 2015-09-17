package com.ociweb.pronghorn.adapter.netty.impl;

import java.util.concurrent.TimeUnit;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GenericFutureListener;

public class PronghornPipeToChannel implements Runnable {

    private final Pipe fromPronghorn;
    private final Pipe toPronghorn;
    private final EventLoop eventLoop;
    private final int maxPipeContentLimit;
    private final int messageSize;
    private int iteration;
    
    private final ServiceObjectHolder<Channel> channelHolder;
    
    public PronghornPipeToChannel(ServiceObjectHolder<Channel> channelHolder, Pipe fromPronghorn, Pipe toPronghorn, EventLoop eventLoop) {
        this.fromPronghorn = fromPronghorn;
        this.toPronghorn = toPronghorn;
        this.channelHolder = channelHolder;
        this.eventLoop = eventLoop;
        
        int reqFreeSpace = 2*FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(toPronghorn));        
        this.maxPipeContentLimit = toPronghorn.sizeOfStructuredLayoutRingBuffer - reqFreeSpace;
        
        FieldReferenceOffsetManager from = Pipe.from(fromPronghorn);
        
        messageSize = from.fragDataSize[0];
        
    }

    @Override
    public void run() {
        ///////////////////////////
        //read from Pipe and write out to network
        //////////////////////////
        
        while (Pipe.contentToLowLevelRead(fromPronghorn, messageSize) ) {
  
            //peek all the fields because we may need to abandon this if there is no room 
           // int msgId      = Pipe.peekInt(fromPronghorn,  0);
            long channelId = Pipe.peekLong(fromPronghorn, 1);
            int meta       = Pipe.peekInt(fromPronghorn,  3);
            int len        = Pipe.peekInt(fromPronghorn,  4);
                                    
            Channel ch = channelHolder.getValid(channelId);
            if (null == ch) {
                //May want to log this "connection closed and can not get response"
                //dump message and move on since caller is no longer attached.
                Pipe.confirmLowLevelRead(fromPronghorn, messageSize);
                Pipe.addAndGetWorkingTail(fromPronghorn, messageSize-1);
                Pipe.releaseReads(fromPronghorn);
            } else {
                //TODO: AAA  use of Text frame is wrong if we were doing round trip of array.!!!!
                
                //only write when we know there is enough room 
                if (len < ch.bytesBeforeUnwritable()) {
                    
//zero copy async                    
                    //Trips/sec 3403.5311635822163 mbps 169.01843523984257
                    //zero copy ByteBuf into PronghornRingBuffer
                    ByteBuf msg = Unpooled.copiedBuffer(Pipe.wrappedUnstructuredLayoutBufferA(fromPronghorn, meta, len),
                                                        Pipe.wrappedUnstructuredLayoutBufferB(fromPronghorn, meta, len) );
                                        
                    //our position which much be released after the write is complete.
                    ch.writeAndFlush(new BinaryWebSocketFrame(msg)).
                          addListener(new ReleaseBuffer(fromPronghorn, Pipe.bytesWorkingTailPosition(fromPronghorn),Pipe.getWorkingTailPosition(fromPronghorn)));
                    
                    Pipe.confirmLowLevelRead(fromPronghorn, messageSize);
                    Pipe.addAndGetWorkingTail(fromPronghorn, messageSize-1);
                    //this enables the next block to be read from the right offset, without waiting for the above write
                    Pipe.markBytesReadBase(fromPronghorn, Pipe.takeValue(fromPronghorn));
                    
                    
                                   
//full copy                    
//                    //Trips/sec 3204.7858134814655 mbps 159.14879501305282
//                    byte[] target = new byte[len]; //TODO. get from pool!!!
//                    Pipe.readBytes(fromPronghorn, target, 0, 0xFFFFFF, meta, len);
//                    ch.writeAndFlush(new TextWebSocketFrame(Unpooled.wrappedBuffer(target)),ch.voidPromise());
//                    //
//                    Pipe.confirmLowLevelRead(fromPronghorn, messageSize);
//                    Pipe.addAndGetWorkingTail(fromPronghorn, messageSize-1);
//                    //this enables the next block to be written from the right offset
//                    Pipe.markBytesReadBase(fromPronghorn, Pipe.takeValue(fromPronghorn));                    
//                    //our position which much be released after the write is complete.
//                    int bytesWorkingTail = Pipe.bytesWorkingTailPosition(fromPronghorn);
//                    long workingTail = Pipe.getWorkingTailPosition(fromPronghorn);   
//                    //this releases this portion of ring back for the next writer to use.
//                    Pipe.batchedReleasePublish(fromPronghorn,bytesWorkingTail,workingTail); 
                    
                    
                    
//zero copy and sync                    
//                    //Trips/sec 3318.675848336514 mbps 164.80454328019997
//                    try {
//                      ByteBuf msg = Unpooled.copiedBuffer( Pipe.wrappedUnstructuredLayoutBufferA(fromPronghorn, meta, len),
//                      Pipe.wrappedUnstructuredLayoutBufferB(fromPronghorn, meta, len) );
//                        ch.writeAndFlush(new TextWebSocketFrame(msg)).sync();
//                    } catch (InterruptedException e) {
//                       Thread.currentThread().interrupt();
//                       return;
//                    }
//                    //
//                    Pipe.confirmLowLevelRead(fromPronghorn, messageSize);
//                    Pipe.addAndGetWorkingTail(fromPronghorn, messageSize-1);
//                    //this enables the next block to be written from the right offset
//                    Pipe.markBytesReadBase(fromPronghorn, Pipe.takeValue(fromPronghorn));                    
//                    //our position which much be released after the write is complete.
//                    int bytesWorkingTail = Pipe.bytesWorkingTailPosition(fromPronghorn);
//                    long workingTail = Pipe.getWorkingTailPosition(fromPronghorn);   
//                    //this releases this portion of ring back for the next writer to use.
//                    Pipe.batchedReleasePublish(fromPronghorn,bytesWorkingTail,workingTail); 
                    
                                    
                } else {
                    break;
                }
            }
                        
        }
               
        
        ///////////////////////////
        //read from network and write out to Pipe
        ///////////////////////////
        if (Pipe.contentRemaining(toPronghorn) < maxPipeContentLimit ) { //if we loop here we die
            Channel ch = channelHolder.next();
            if (null!=ch) {
                ch.read(); //this can be a very slow call
            }
        }        
        
        //put back into loop to do this again        
        if (0==(++iteration&0xFFF) && Pipe.contentRemaining(fromPronghorn)==0 && Pipe.contentRemaining(toPronghorn)==0) {
            //slow down because there is nothing going on.
            eventLoop.schedule(this, 1, TimeUnit.MILLISECONDS);
        } else {
            eventLoop.execute(this);
        }

    }

    static class ReleaseBuffer implements GenericFutureListener<ChannelFuture> {
        int bytesWorkingTail; 
        long workingTail;
        Pipe pipe;
        public ReleaseBuffer(Pipe pipe, int bytesWorkingTail, long workingTail) {
            this.bytesWorkingTail = bytesWorkingTail;
            this.workingTail = workingTail;
            this.pipe = pipe;
        }
        
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            Pipe.batchedReleasePublish(pipe,bytesWorkingTail,workingTail);       
        }
        
    }

}


