package com.ociweb.pronghorn.adapter.netty.impl;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.schema.loader.TemplateHandler;

public class WebSocketFROM {

    public static final FieldReferenceOffsetManager FROM;
    
    public static final int singleMessageIdx;
    public static final int subscriptionMessageIdx;
    
    public static final int startSubPublishIdx;
    public static final int startSubPublishIdSize;
    
    public static final int stopSubPublishIdx;
    public static final int stopSubPublishIdSize;
    
    
    static {
        
        try {
            
            //This should be a constant, but as we develop we will load it from the XML
            FROM = TemplateHandler.loadFrom("/websocket.xml");
   
            singleMessageIdx = FieldReferenceOffsetManager.lookupTemplateLocator(1, FROM);
            subscriptionMessageIdx = FieldReferenceOffsetManager.lookupTemplateLocator(2, FROM);
            startSubPublishIdx = FieldReferenceOffsetManager.lookupTemplateLocator(3, FROM);
            stopSubPublishIdx = FieldReferenceOffsetManager.lookupTemplateLocator(4, FROM);         
            
            startSubPublishIdSize = FROM.fragDataSize[startSubPublishIdx];
            stopSubPublishIdSize = FROM.fragDataSize[stopSubPublishIdx];
            
            
        } catch (Throwable e) {
           throw new RuntimeException(e);
        } 
        
    }
    
    
    
}
