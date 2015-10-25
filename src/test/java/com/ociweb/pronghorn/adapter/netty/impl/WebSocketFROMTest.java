package com.ociweb.pronghorn.adapter.netty.impl;

import org.junit.Test;

import com.ociweb.pronghorn.adapter.netty.WebSocketSchema;
import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.util.build.FROMValidation;

public class WebSocketFROMTest {

    
    @Test
    public void validateIdRangesTemplate() {
        
        String templateFile = "/websocket.xml";
        String varName = "FROM";                
        FieldReferenceOffsetManager encodedFrom = WebSocketSchema.FROM;
        
        FROMValidation.testForMatchingFROMs(templateFile, varName, encodedFrom);
                
    }
    
}
