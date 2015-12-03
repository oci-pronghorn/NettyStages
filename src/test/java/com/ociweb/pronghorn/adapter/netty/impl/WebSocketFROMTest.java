package com.ociweb.pronghorn.adapter.netty.impl;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ociweb.pronghorn.adapter.netty.WebSocketSchema;
import com.ociweb.pronghorn.pipe.util.build.FROMValidation;

public class WebSocketFROMTest {

    
    @Test
    public void validateIdRangesTemplate() {
        assertTrue(FROMValidation.testForMatchingFROMs("/websocket.xml", WebSocketSchema.instance));
        assertTrue(FROMValidation.testForMatchingLocators(WebSocketSchema.instance));
                
    }
    
}
