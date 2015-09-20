package com.ociweb.pronghorn.adapter.netty;

import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class ReflectionStage extends PronghornStage {

    private Pipe input; 
    private Pipe output;
    private final int msgSize;
    
    public ReflectionStage(GraphManager gm, Pipe input, Pipe output) {
        super(gm,input, output);
        this.input = input;
        this.output = output;
        
        this.msgSize = Pipe.from(input).fragDataSize[0];
        
    }

    @Override
    public void run() {
        
        if (Pipe.contentToLowLevelRead(input, msgSize) && Pipe.roomToLowLevelWrite(output, msgSize)) {
            
            Pipe.addMsgIdx(output, Pipe.takeMsgIdx(input));
            Pipe.addLongValue(Pipe.takeLong(input), output);
                      
            
            int meta       = Pipe.takeRingByteMetaData(input);
            int len        = Pipe.takeRingByteLen(input);
            int mask       = Pipe.byteMask(input);
            byte[] backing = Pipe.byteBackingArray(meta, input);
            int offset     = Pipe.bytePosition(meta, input, len);
            
            
            //assumes UTF8 data
        //    StringBuilder builder = (StringBuilder)Pipe.readUTF8(input, new StringBuilder(), meta, len);            
        //    Pipe.addUTF8(builder, output);         
        //   System.out.println("ReflectionStage bounce:"+builder);
                         
            
            //assumes nothing about the data
            Pipe.addByteArrayWithMask(output, mask, len, backing, offset);
            
            
            Pipe.confirmLowLevelWrite(output, msgSize);
            Pipe.publishWrites(output);
            
            Pipe.confirmLowLevelRead(input, msgSize);
            Pipe.releaseReads(input);
            
        
        }        
            
        
        
    }

    
    
    
}
