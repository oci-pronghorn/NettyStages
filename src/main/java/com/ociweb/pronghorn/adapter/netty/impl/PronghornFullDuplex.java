package com.ociweb.pronghorn.adapter.netty.impl;

import com.ociweb.pronghorn.pipe.Pipe;

import io.netty.buffer.ByteBuf;

public class PronghornFullDuplex {

    private final long channelIndex;
    private final Pipe toPronghorn;
    
    private static final int MSG_SIZE = 0;
    
    public PronghornFullDuplex(long channelIndex, Pipe toPronghorn) {
        this.channelIndex = channelIndex;
        this.toPronghorn = toPronghorn;        
    }

    public void write(boolean finalFragment, ByteBuf content) {
        
        //This block is here only for safety and is never expected to spin unless there is a logic failure somewhere
        while (!Pipe.roomToLowLevelWrite(toPronghorn, MSG_SIZE)) {
            Thread.yield();
        }

        Pipe.addMsgIdx(toPronghorn, 0);
        Pipe.addLongValue(channelIndex, toPronghorn);
        Pipe.addByteBuffer(content.nioBuffer(), toPronghorn);
        Pipe.publishWrites(toPronghorn);
        
    }
    
}
