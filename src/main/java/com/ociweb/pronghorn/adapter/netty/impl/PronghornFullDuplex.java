package com.ociweb.pronghorn.adapter.netty.impl;

import java.nio.ByteBuffer;

import com.ociweb.pronghorn.pipe.Pipe;

import io.netty.buffer.ByteBuf;

public class PronghornFullDuplex {
    ///////////////////////////////////////////////////////////
    //Keep this small there is one for every channel/connection
    ///////////////////////////////////////////////////////////
    public final long channelIndex; //Needed to send to pronghorn for round trip.
    public final int pipeId; //Needed to registers new subscriptions on this connection & lookup the right pipe 
    
    
    private static final int MSG_SIZE = 0;
    
    public PronghornFullDuplex(long channelIndex, Pipe toPronghorn, int pipeId) {
        this.channelIndex = channelIndex;
        this.pipeId = pipeId; //NOTE: this is only needed for subscription.
    }
    
    public void partialSendToPipe(ByteBuf content, boolean continuationInProgress, Pipe toPronghorn) {
        
        if (!continuationInProgress) {
            System.out.println("continuation started");
            
            if (isSubscription(content)) {
                registerSubscription(toPronghorn); 
            }
                        
            //This block is here only for safety and is never expected to spin unless there is a logic failure somewhere
            while (!Pipe.roomToLowLevelWrite(toPronghorn, MSG_SIZE)) {
                Thread.yield();
            }
            Pipe.addMsgIdx(toPronghorn, 0);
            Pipe.addLongValue(channelIndex, toPronghorn);            
        }        
        
        ByteBuffer nioBuffer = content.nioBuffer();      
        int len = nioBuffer.remaining();
        Pipe.copyByteBuffer(content.nioBuffer(), len, toPronghorn);
    }
    
    private boolean isSubscription(ByteBuf content) {
        // TODO Auto-generated method stub
        return false;
    }

    private void registerSubscription(Pipe toPronghorn) {

        
        //  we have channelIndex which is memberId
        //  content[0] subcription group id
        //  what is the pipe id? where is the list
                          
         
        
    }

    private void endPartialSendToPipe(ByteBuf content, int continuationDataPos, Pipe toPronghorn) {

        ByteBuffer nioBuffer = content.nioBuffer();
        
        int len = nioBuffer.remaining();
        Pipe.copyByteBuffer(content.nioBuffer(), len, toPronghorn);
        
        //compute length from starting position and new position after copy of all the bytes        
        int curWorkingHeadPos = Pipe.bytesWorkingHeadPosition(toPronghorn);
        int length = curWorkingHeadPos-continuationDataPos;
        while (length<0) {
            length += (Pipe.BYTES_WRAP_MASK+1); //if it wraps
        }
        
        System.err.println("tested continuation");//remove once this is printed and tested.
        
        Pipe.addBytePosAndLen(toPronghorn, continuationDataPos, length);
        Pipe.publishWrites(toPronghorn);
        
    }
    
    
    public void sendToPipe(ByteBuf content, int continuationDataPos, boolean continuationInProgress, Pipe toPronghorn) {
        
        //This block is here only for safety and is never expected to spin unless there is a logic failure somewhere
        while (!Pipe.roomToLowLevelWrite(toPronghorn, MSG_SIZE)) {
            Thread.yield();
        }

        if (!continuationInProgress) {        
        
            if (isSubscription(content)) {
                registerSubscription(toPronghorn); 
            }
            
            Pipe.addMsgIdx(toPronghorn, 0);
            Pipe.addLongValue(channelIndex, toPronghorn);
            Pipe.addByteBuffer(content.nioBuffer(), toPronghorn);
            Pipe.publishWrites(toPronghorn);
        } else {
            endPartialSendToPipe(content, continuationDataPos, toPronghorn);
        }
        
    }
    
}
