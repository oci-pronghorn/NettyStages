package com.ociweb.pronghorn.adapter.netty.impl;

import java.nio.ByteBuffer;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.util.MemberHolder;

import io.netty.buffer.ByteBuf;

public class ContentToPronghornPipe {
    ///////////////////////////////////////////////////////////
    //Keep this small there is one for every channel/connection
    ///////////////////////////////////////////////////////////
    public final long channelIndex; //Needed to send to pronghorn for round trip.
    public final int pipeId; //Needed to registers new subscriptions on this connection & lookup the right pipe 
    
    
    private static final int MSG_SIZE = 0;
    
    
    public ContentToPronghornPipe(long channelIndex, Pipe toPronghorn, int pipeId) {
        this.channelIndex = channelIndex;
        this.pipeId = pipeId; //NOTE: this is only needed for subscription.
    }
    
    public void partialSendToPipe(ByteBuf content, boolean continuationInProgress, Pipe toPronghorn, MemberHolder subscriptionHolder) {
        
        if (!continuationInProgress) {        
            //This block is here only for safety and is never expected to spin unless there is a logic failure somewhere
            while (!Pipe.hasRoomForWrite(toPronghorn, MSG_SIZE)) {
                Thread.yield();
            }
            Pipe.addMsgIdx(toPronghorn, 0);
            Pipe.addLongValue(channelIndex, toPronghorn);            
        }        
        
        ByteBuffer nioBuffer = content.nioBuffer();      
        int len = nioBuffer.remaining();
        Pipe.copyByteBuffer(content.nioBuffer(), len, toPronghorn);
    }
    
    private boolean isSubscriptionAdmin(ByteBuf content) {
        //subscriptions are 2 bytes long, first byte is small and second is 1 or 0
        return (2==content.readableBytes() && (content.getByte(0)>=0) && (content.getByte(0)<40) && ((content.getByte(1)==0) || (content.getByte(1)==1)) ); 
    }

    private void processSubscription(ByteBuf content, MemberHolder subscriptionHolder, Pipe pipe) {

                
        boolean wasEmpty = subscriptionHolder.isEmpty(content.getByte(0));
        if (0==content.getByte(1)) {
            subscriptionHolder.removeMember(content.getByte(0), channelIndex);            
            boolean nowEmpty = subscriptionHolder.isEmpty(content.getByte(0));
            
            if (nowEmpty && !wasEmpty) {
                //send stop message    
                while (!Pipe.hasRoomForWrite(pipe, WebSocketFROM.stopSubPublishIdSize)) {
                    assert(false) : "Just for safety the caller should have checked for space first.";
                }
                
           //     System.out.println("stop publish "+WebSocketFROM.FROM.fieldNameScript[WebSocketFROM.stopSubPublishIdx]);
                
                Pipe.addMsgIdx(pipe, WebSocketFROM.stopSubPublishIdx);
                Pipe.addIntValue(content.getByte(0), pipe);
                Pipe.publishWrites(pipe);                
                Pipe.confirmLowLevelWrite(pipe, WebSocketFROM.stopSubPublishIdSize);
            }
            
        } else {
            
            if (0==subscriptionHolder.containsCount(content.getByte(0), channelIndex)) {
                subscriptionHolder.addMember(content.getByte(0), channelIndex);        
                if (wasEmpty) {
                    //send start message
                    while (!Pipe.roomToLowLevelWrite(pipe, WebSocketFROM.startSubPublishIdSize)) {
                        assert(false) : "Just for safety the caller should have checked for space first.";
                    }
                    
           //         System.out.println("start publish");
                    Pipe.addMsgIdx(pipe, WebSocketFROM.startSubPublishIdx);
                    Pipe.addIntValue(content.getByte(0), pipe);
                    Pipe.publishWrites(pipe);                    
                    Pipe.confirmLowLevelWrite(pipe,  WebSocketFROM.startSubPublishIdSize);
                }
            }
        }
         
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
    
    
    public void sendToPipe(ByteBuf content, int continuationDataPos, boolean continuationInProgress, Pipe toPronghorn, MemberHolder subscriptionHolder) {
        
        //This block is here only for safety and is never expected to spin unless there is a logic failure somewhere
        while (!Pipe.hasRoomForWrite(toPronghorn, MSG_SIZE)) {
            Thread.yield();
        }

        if (!continuationInProgress) {        
            //subscription admin must never be part of a continuation, these are very short.
            if (isSubscriptionAdmin(content)) {
                processSubscription(content, subscriptionHolder, toPronghorn); 
            } else {
                //send content                
                Pipe.addMsgIdx(toPronghorn, WebSocketFROM.forSingleChannelMessageIdx);
                Pipe.addLongValue(channelIndex, toPronghorn);
                Pipe.addByteBuffer(content.nioBuffer(), toPronghorn);
                Pipe.publishWrites(toPronghorn);
            }
        } else {
            endPartialSendToPipe(content, continuationDataPos, toPronghorn);
        }
        
    }
    
}
