package com.ociweb.pronghorn.adapter.netty.impl;

import java.util.concurrent.TimeUnit;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.util.MemberHolder;
import com.ociweb.pronghorn.util.ServiceObjectHolder;

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
    private final MemberHolder subscriptionHolder;
    
    private final int singleMsgId;
    private final int subscriptMsgId;
    
    
    public PronghornPipeToChannel(ServiceObjectHolder<Channel> channelHolder, Pipe fromPronghorn, Pipe toPronghorn, MemberHolder subscriptionHolder, EventLoop eventLoop) {
        this.fromPronghorn = fromPronghorn;
        this.toPronghorn = toPronghorn;
        this.channelHolder = channelHolder;
        this.subscriptionHolder = subscriptionHolder;
        this.eventLoop = eventLoop;
        
        int reqFreeSpace = 2*FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(toPronghorn));        
        this.maxPipeContentLimit = toPronghorn.sizeOfStructuredLayoutRingBuffer - reqFreeSpace;
        
        FieldReferenceOffsetManager from = Pipe.from(fromPronghorn);
        
        messageSize = from.fragDataSize[0];
        
        FieldReferenceOffsetManager FROM = Pipe.from(fromPronghorn);
        singleMsgId = FieldReferenceOffsetManager.lookupTemplateLocator(1, FROM);
        subscriptMsgId = FieldReferenceOffsetManager.lookupTemplateLocator(2, FROM);         
        
    }

    @Override
    public void run() {
        ///////////////////////////
        //read from Pipe and write out to network
        //////////////////////////
        
        while (Pipe.contentToLowLevelRead(fromPronghorn, messageSize) ) {
  
            //peek all the fields because we may need to abandon this if there is no room 
            final int msgId      = Pipe.peekInt(fromPronghorn,  0);
           
            
            //singleMsgId
            
            if (msgId == subscriptMsgId) {
              //  subscriptionHolder.visit(listId, visitor);
                
                
            }
            
            
            long channelId = Pipe.peekLong(fromPronghorn, 1);
            int meta       = Pipe.peekInt(fromPronghorn,  3);
            int len        = Pipe.peekInt(fromPronghorn,  4);
            
            //Must look up each chanel for the group subscription.
            
            
            Channel ch = channelHolder.getValid(channelId);
            
            
            
            if (null == ch) {
                //May want to log this "connection closed and can not get response"
                //dump message and move on since caller is no longer attached.
                Pipe.confirmLowLevelRead(fromPronghorn, messageSize);
                Pipe.addAndGetWorkingTail(fromPronghorn, messageSize-1);
                Pipe.releaseReads(fromPronghorn);
            } else {

                //only write when we know there is enough room 
                if (len < ch.bytesBeforeUnwritable()) {
                    
                    //zero copy async                    
                    ByteBuf msg = Unpooled.copiedBuffer(Pipe.wrappedUnstructuredLayoutBufferA(fromPronghorn, meta, len),
                                                        Pipe.wrappedUnstructuredLayoutBufferB(fromPronghorn, meta, len) );
                                        
                    //our position which much be released after the write is complete.
                    ch.writeAndFlush(new BinaryWebSocketFrame(msg)).
                          addListener(new ReleaseBuffer(fromPronghorn, Pipe.bytesWorkingTailPosition(fromPronghorn),Pipe.getWorkingTailPosition(fromPronghorn)));
                    
                    Pipe.confirmLowLevelRead(fromPronghorn, messageSize);
                    Pipe.addAndGetWorkingTail(fromPronghorn, messageSize-1);
                    //this enables the next block to be read from the right offset, without waiting for the above write
                    Pipe.markBytesReadBase(fromPronghorn, Pipe.takeValue(fromPronghorn));               
   
                                    
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


