package com.ociweb.pronghorn.adapter.netty.impl;

import java.util.concurrent.TimeUnit;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.util.MemberHolder;
import com.ociweb.pronghorn.util.ServiceObjectHolder;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;

public class PronghornPipeToChannel implements Runnable {

    private final Pipe fromPronghorn;
    private final Pipe toPronghorn;
    private final EventLoop eventLoop;
    private final int maxPipeContentLimit;
    private int iteration;
    
    private final ServiceObjectHolder<Channel> channelHolder;
    private final MemberHolder subscriptionHolder;
    
    
    public PronghornPipeToChannel(ServiceObjectHolder<Channel> channelHolder, Pipe fromPronghorn, Pipe toPronghorn, MemberHolder subscriptionHolder, EventLoop eventLoop) {
        this.fromPronghorn = fromPronghorn;
        this.toPronghorn = toPronghorn;
        this.channelHolder = channelHolder;
        this.subscriptionHolder = subscriptionHolder;
        this.eventLoop = eventLoop;
        
        int reqFreeSpace = 2*FieldReferenceOffsetManager.maxFragmentSize(Pipe.from(toPronghorn));        
        this.maxPipeContentLimit = toPronghorn.sizeOfStructuredLayoutRingBuffer - reqFreeSpace;
        
        FieldReferenceOffsetManager from = Pipe.from(fromPronghorn);
  
        
    }

    @Override
    public void run() {
        ///////////////////////////
        //read from Pipe and write out to network
        //////////////////////////
        
        while (Pipe.contentToLowLevelRead(fromPronghorn, 1) ) {
  
            //peek all the fields because we may need to abandon this if there is no room 
            final int msgId      = Pipe.takeMsgIdx(fromPronghorn);
            
            System.out.println("from pronghorn message "+msgId + WebSocketFROM.FROM.fieldNameScript[msgId]);
                       
            if (msgId==WebSocketFROM.subscriptionMessageIdx) { //send to all the  clients on this pipe who have this subscription
                
                        int subId      = Pipe.takeValue(fromPronghorn);
                        int meta       = Pipe.takeValue(fromPronghorn);
                        int len        = Pipe.takeValue(fromPronghorn);
                        
                        SubscriptionDistributor sd = new SubscriptionDistributor(fromPronghorn,
                                Pipe.bytesWorkingTailPosition(fromPronghorn),Pipe.getWorkingTailPosition(fromPronghorn),
                                Pipe.wrappedUnstructuredLayoutBufferA(fromPronghorn, meta, len),
                                Pipe.wrappedUnstructuredLayoutBufferB(fromPronghorn, meta, len), channelHolder);
                        
                        subscriptionHolder.visit(subId, sd);
             } if (msgId==WebSocketFROM.singleMessageIdx) { //send to single client
                        long channelId = Pipe.takeLong(fromPronghorn);
                        int meta       = Pipe.takeValue(fromPronghorn);
                        int len        = Pipe.takeValue(fromPronghorn);  
                        
                        SubscriptionDistributor sd = new SubscriptionDistributor(fromPronghorn,
                                Pipe.bytesWorkingTailPosition(fromPronghorn),Pipe.getWorkingTailPosition(fromPronghorn),
                                Pipe.wrappedUnstructuredLayoutBufferA(fromPronghorn, meta, len),
                                Pipe.wrappedUnstructuredLayoutBufferB(fromPronghorn, meta, len), channelHolder);
                        
                        sd.visit(channelId);
                        sd.finished();
              } else {
                  
                  //Reflection?
                  
                  throw new UnsupportedOperationException();
                  //unknown?
              }
     
             Pipe.confirmLowLevelRead(fromPronghorn, WebSocketFROM.FROM.fragDataSize[msgId]);
             
             //this enables the next block to be read from the right offset, without waiting for the above write
             Pipe.markBytesReadBase(fromPronghorn, Pipe.takeValue(fromPronghorn));               
             
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

}


