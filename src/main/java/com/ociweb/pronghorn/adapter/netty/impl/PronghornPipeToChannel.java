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
        this.maxPipeContentLimit = toPronghorn.sizeOfSlabRing - reqFreeSpace;
        
        FieldReferenceOffsetManager from = Pipe.from(fromPronghorn);
  
        
    }

    @Override
    public void run() {
        ///////////////////////////
        //read from Pipe and write out to network
        //////////////////////////
                        
        while (Pipe.hasContentToRead(fromPronghorn) ) {
  
            //peek all the fields because we may need to abandon this if there is no room 
            final int msgId      = Pipe.takeMsgIdx(fromPronghorn);
            

            if (msgId==WebSocketFROM.forSubscribersMessageIdx) { //send to all the  clients on this pipe who have this subscription
                
                        int subId      = Pipe.takeValue(fromPronghorn);
                        int meta       = Pipe.takeValue(fromPronghorn);
                        int len        = Pipe.takeValue(fromPronghorn);
                        int bytesUsed  = Pipe.takeValue(fromPronghorn);
                        
                        //TODO: this is a questionable feature we should revisit, block has length just take the value instead of using this pos.
                        Pipe.addAndGetBytesWorkingTailPosition(fromPronghorn, len);//NOTE: for low level read we have to inc on our own.
                                                
                        SubscriptionDistributor sd = new SubscriptionDistributor(fromPronghorn,
                                
                                Pipe.bytesWorkingTailPosition(fromPronghorn),
                                Pipe.getWorkingTailPosition(fromPronghorn),
                                
                                Pipe.wrappedBlobRingA(fromPronghorn, meta, len),
                                Pipe.wrappedBlobRingB(fromPronghorn, meta, len), channelHolder);
                        
                        subscriptionHolder.visit(subId, sd);
                        
                        //this enables the next block to be read from the right offset, without waiting for the above write
                        Pipe.markBytesReadBase(fromPronghorn, bytesUsed); 
                        
                        
             } else if (msgId==WebSocketFROM.forSingleChannelMessageIdx) { //send to single client
                        
                        long channelId = Pipe.takeLong(fromPronghorn);
                        int meta       = Pipe.takeValue(fromPronghorn);
                        int len        = Pipe.takeValue(fromPronghorn);  
                        int bytesUsed  = Pipe.takeValue(fromPronghorn);
                 
                        //TODO: this is a questionable feature we should revisit, block has length just take the value instead of using this pos.
                        Pipe.addAndGetBytesWorkingTailPosition(fromPronghorn, len);//NOTE: for low level read we have to inc on our own.
                                                
                        SubscriptionDistributor sd = new SubscriptionDistributor(fromPronghorn,
                                
                                Pipe.bytesWorkingTailPosition(fromPronghorn), //already moved up to end
                                Pipe.getWorkingTailPosition(fromPronghorn),   //already moved up to end
                                
                                Pipe.wrappedBlobRingA(fromPronghorn, meta, len),
                                Pipe.wrappedBlobRingB(fromPronghorn, meta, len), channelHolder);

                        sd.visit(channelId);
                        sd.finished();                      
                        
                        //this enables the next block to be read from the right offset, without waiting for the above write
                        Pipe.markBytesReadBase(fromPronghorn, bytesUsed);  
                                                
              } else {
                  
                  throw new UnsupportedOperationException();

              }
     
             Pipe.confirmLowLevelRead(fromPronghorn, WebSocketFROM.FROM.fragDataSize[msgId]);
             
             
       }                    
               
        
        ///////////////////////////
        //read from network and write out to Pipe
        ///////////////////////////
        if (Pipe.contentRemaining(toPronghorn) < maxPipeContentLimit && (0==(iteration&0xF)) ) { //subscriptions are rare so do not check often
            //if we loop here we die
            Channel ch = channelHolder.next();
            if (null!=ch) {
                ch.read(); //this can be a very slow call
            }
        }        
        
        //put back into loop to do this again        
        if (0==(++iteration&0x3) && Pipe.contentRemaining(fromPronghorn)==0 && Pipe.contentRemaining(toPronghorn)==0) {
            //slow down because there is nothing going on.
            eventLoop.schedule(this, 1, TimeUnit.MILLISECONDS);
        } else {
            eventLoop.execute(this);
        }

    }

}


