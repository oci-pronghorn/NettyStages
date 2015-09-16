package com.ociweb.pronghorn.adapter.netty.impl;

import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.util.hash.LongHashTable;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;

public class PronghornFullDuplexManager {

    private final LongHashTable threadsToOrdinals;
    
    //the length of these 3 arrays must be the same as the number of unique threads
    private final Pipe[] toPronghorn;
    private final Pipe[] fromPronghorn;
    private final ServiceObjectHolder<Channel>[] channelLookup;
    
    private int assignedPipeCount = 0;

    private final static ServiceObjectValidator<Channel> validator = new ServiceObjectValidator<Channel>() {
                       @Override
                      public boolean isValid(Channel serviceObject) {
                           return null!=serviceObject && serviceObject.isOpen();
                       }
                };               
    
    
    @SuppressWarnings("unchecked")
    public PronghornFullDuplexManager(Pipe[] toPronghorn, Pipe[] fromPronghorn) {
        
        assert(toPronghorn.length == fromPronghorn.length);
        int maxThreads = fromPronghorn.length;
        this.threadsToOrdinals = new LongHashTable(2+(int)(Math.log(maxThreads)/Math.log(2)) );
        this.toPronghorn = toPronghorn;
        this.fromPronghorn = fromPronghorn;
        this.channelLookup = new ServiceObjectHolder[toPronghorn.length];
        
    }
            
         
    /**
     * Only called when we have established a NEW channel therefore it will use a simple
     * Incrementing counter to hand out unique identifiers for connections.
     * 
     * @param channel
     * @return
     */
    public PronghornFullDuplex buildNewDuplexObject(final Channel channel) {
        
        long threadId = Thread.currentThread().getId();        
        int pipeOrdinal = LongHashTable.getItem(threadsToOrdinals, threadId);
        int pipeIdx;                  
        if (0==pipeOrdinal) {
          //pick the next free pipes as ours, this thread has never seen a pipe before
          //threads are re-used across many connections so this sync code is only called when warming up
          synchronized(threadsToOrdinals) {              
              pipeOrdinal = ++assignedPipeCount;
              LongHashTable.setItem(threadsToOrdinals, threadId, pipeOrdinal);              
          } 
          pipeIdx = pipeOrdinal-1;  
          channelLookup[pipeIdx] = new ServiceObjectHolder<Channel>(Channel.class, validator);   
          
          EventLoop eventLoop = channel.eventLoop();
          eventLoop.execute(new PronghornPipeToChannel(channelLookup[pipeIdx], fromPronghorn[pipeIdx], toPronghorn[pipeIdx], eventLoop));
          
        } else {
            pipeIdx = pipeOrdinal-1; 
        }
        
        //we know this is only called by the same thread for this channelId instance.
        return new PronghornFullDuplex(channelLookup[pipeIdx].add(channel), toPronghorn[pipeIdx]);  
    }

}
