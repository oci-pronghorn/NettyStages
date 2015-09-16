package com.ociweb.pronghorn.adapter.netty.impl;

import java.util.concurrent.TimeUnit;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.WrappedByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

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
                System.out.println("connection closed and can not get response");
                Pipe.confirmLowLevelRead(fromPronghorn, messageSize);
                Pipe.addAndGetWorkingTail(fromPronghorn, messageSize-1);
                Pipe.releaseReads(fromPronghorn);
            } else {
            
                long bytesCount = ch.bytesBeforeUnwritable();
                
                //only write when we know there is enough room 
                if (len < bytesCount) {
                    
                    //still testing all the ways of doing this to find the best.
                    
              //      ByteBuf msg = Unpooled.copiedBuffer( Pipe.wrappedUnstructuredLayoutBufferA(fromPronghorn, meta, len),
                //                                          Pipe.wrappedUnstructuredLayoutBufferB(fromPronghorn, meta, len) );
                 
                    
                    
                   byte[] target = new byte[len]; //TODO. get from pool!!!
                   Pipe.readBytes(fromPronghorn, target, 0, 0xFFFFFF, meta, len);
                                
                    //7028
                    ch.writeAndFlush(new TextWebSocketFrame(Unpooled.wrappedBuffer(target)),ch.voidPromise());
                    
                    //
                    //6814  slower to sync.
                  //  try {
                       // ch.writeAndFlush(new TextWebSocketFrame(msg)).sync();
                    //} catch (InterruptedException e) {
                      //  Thread.currentThread().interrupt();
                        //return;
                    //}
                     
                    //7484 but dangeours must use the future to do release
                   //   ch.writeAndFlush(new TextWebSocketFrame(msg));
                    
                    //OR: release all but batch them and release the batch position on Future  TODO: may be the best solution.
                    
                    Pipe.confirmLowLevelRead(fromPronghorn, messageSize);
                    Pipe.addAndGetWorkingTail(fromPronghorn, messageSize-1);
                    Pipe.releaseReads(fromPronghorn);
                                    
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
                ch.read();
            }
        }        
        
        //put back into loop to do this again        
        if (0==(++iteration&0x3FFFF) && Pipe.contentRemaining(fromPronghorn)==0 && Pipe.contentRemaining(toPronghorn)==0) {
            //slow down because there is nothing going on.
            eventLoop.schedule(this, 1, TimeUnit.MILLISECONDS);
        } else {
            eventLoop.execute(this);
        }

    }

}
