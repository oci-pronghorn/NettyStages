package com.ociweb.pronghorn.adapter.netty.impl;

import java.nio.ByteBuffer;

import com.ociweb.pronghorn.pipe.Pipe;

import io.netty.buffer.ByteBuf;

public class PronghornFullDuplex {

    private final long channelIndex; //Needed to send to pronghorn for round trip.
    private final Pipe toPronghorn; //Needed to connect to pronghorn
    
    private int continuationLength;
    private int continuationPosition;
    
    private int pipeId; //Needed to registers new subscriptions on this connection??
    
    private static final int MSG_SIZE = 0;
    
    public PronghornFullDuplex(long channelIndex, Pipe toPronghorn, int pipeId) {
        this.channelIndex = channelIndex;
        this.toPronghorn = toPronghorn;    
        this.continuationLength = 0;
        this.pipeId = pipeId; //NOTE: this is only needed for subscription.
    }
    
    public void partialSendToPipe(ByteBuf content) {
        
        if (0==continuationLength) {
            
            //TODO: add subscription if possible
        //    if (isSubscription(content)) {
                
              //  we have channelIndex which is memberId
              //  content[0] subcription group id
              //  what is the pipe id? where is the list
                
                
                
        //    }
            
            
            //This block is here only for safety and is never expected to spin unless there is a logic failure somewhere
            while (!Pipe.roomToLowLevelWrite(toPronghorn, MSG_SIZE)) {
                Thread.yield();
            }
            Pipe.addMsgIdx(toPronghorn, 0);
            Pipe.addLongValue(channelIndex, toPronghorn);
            continuationPosition = Pipe.bytesWorkingHeadPosition(toPronghorn);  
            
        }        
        
        ByteBuffer nioBuffer = content.nioBuffer();      
        int len = nioBuffer.remaining();
        Pipe.copyByteBuffer(content.nioBuffer(), len, toPronghorn);
        continuationLength +=  len;
        assert(continuationLength>=0);
    }
    
    private void endPartialSendToPipe(ByteBuf content) {

        ByteBuffer nioBuffer = content.nioBuffer();
        
        int len = nioBuffer.remaining();
        Pipe.copyByteBuffer(content.nioBuffer(), len, toPronghorn);
        continuationLength +=  len;
        
        Pipe.addBytePosAndLen(toPronghorn, continuationPosition, continuationLength);
        continuationLength=0;
        Pipe.publishWrites(toPronghorn);
        
    }
    
    
    public void sendToPipe(ByteBuf content) {
        
        //This block is here only for safety and is never expected to spin unless there is a logic failure somewhere
        while (!Pipe.roomToLowLevelWrite(toPronghorn, MSG_SIZE)) {
            Thread.yield();
        }

        if (0==continuationLength) {        
        
            //TODO: add subscription if possible
            
            
            Pipe.addMsgIdx(toPronghorn, 0);
            Pipe.addLongValue(channelIndex, toPronghorn);
            Pipe.addByteBuffer(content.nioBuffer(), toPronghorn);
            Pipe.publishWrites(toPronghorn);
        } else {
            endPartialSendToPipe(content);
        }
        
    }
    
}
