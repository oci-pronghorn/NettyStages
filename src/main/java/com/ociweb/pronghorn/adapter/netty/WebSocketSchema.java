package com.ociweb.pronghorn.adapter.netty;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;

public class WebSocketSchema extends MessageSchema {
 
    public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
            new int[]{0xc0400003,0x90800000,0xb8000000,0xc0200003,0xc0400003,0x80800000,0xb8000000,0xc0200003,0xc0400002,0x80800000,0xc0200002,0xc0400002,0x80800000,0xc0200002},
            (short)0,
            new String[]{"WebSocketFragment","ChannelId","Payload",null,"WebSocketFragmentForSubscribers","SubscriptionId","Payload",null,"StartPublishSubsription","SubscriptionId",null,"StopPublishSubscription","SubscriptionId",null},
            new long[]{1, 10, 12, 0, 2, 11, 12, 0, 3, 11, 0, 4, 11, 0},
            new String[]{"global",null,null,null,"global",null,null,null,"global",null,null,"global",null,null},
            "websocket.xml");
    
    public static final int forSingleChannelMessageIdx;
    public static final int forSingleChannelMessageSize;
        
    public static final int forSubscribersMessageIdx;
    public static final int forSubscribersMessageSize;
        
    public static final int startSubPublishIdx;
    public static final int startSubPublishIdSize;
    
    public static final int stopSubPublishIdx;
    public static final int stopSubPublishIdSize;
    
    public static final int fieldForSingleChannelMessageChannelId;
    public static final int fieldForSingleChannelMessagePayload;
    
    
    public static final WebSocketSchema instance = new WebSocketSchema();
        
    public static final int MSG_WEBSOCKETFRAGMENT_1 = 0x00000000;
    public static final int MSG_WEBSOCKETFRAGMENT_1_FIELD_CHANNELID_10 = 0x00800001;
    public static final int MSG_WEBSOCKETFRAGMENT_1_FIELD_PAYLOAD_12 = 0x01C00003;
    public static final int MSG_WEBSOCKETFRAGMENTFORSUBSCRIBERS_2 = 0x00000004;
    public static final int MSG_WEBSOCKETFRAGMENTFORSUBSCRIBERS_2_FIELD_SUBSCRIPTIONID_11 = 0x00000001;
    public static final int MSG_WEBSOCKETFRAGMENTFORSUBSCRIBERS_2_FIELD_PAYLOAD_12 = 0x01C00002;
    public static final int MSG_STARTPUBLISHSUBSRIPTION_3 = 0x00000008;
    public static final int MSG_STARTPUBLISHSUBSRIPTION_3_FIELD_SUBSCRIPTIONID_11 = 0x00000001;
    public static final int MSG_STOPPUBLISHSUBSCRIPTION_4 = 0x0000000B;
    public static final int MSG_STOPPUBLISHSUBSCRIPTION_4_FIELD_SUBSCRIPTIONID_11 = 0x00000001;
    
    protected WebSocketSchema() {
        super(FROM);
    }
    
    static {
        
        try {
            //TODO: move these into unit tests and use constants above.
            
            forSingleChannelMessageIdx = FieldReferenceOffsetManager.lookupTemplateLocator(1, FROM);
            forSubscribersMessageIdx = FieldReferenceOffsetManager.lookupTemplateLocator(2, FROM);//WebSocketFragmentForSubscribers
            
            forSingleChannelMessageSize =  FROM.fragDataSize[forSingleChannelMessageIdx];
            forSubscribersMessageSize =  FROM.fragDataSize[forSubscribersMessageIdx];
            
            startSubPublishIdx = FieldReferenceOffsetManager.lookupTemplateLocator(3, FROM);
            stopSubPublishIdx = FieldReferenceOffsetManager.lookupTemplateLocator(4, FROM);         
            
            startSubPublishIdSize = FROM.fragDataSize[startSubPublishIdx];
            stopSubPublishIdSize = FROM.fragDataSize[stopSubPublishIdx];
            
            fieldForSingleChannelMessageChannelId =FieldReferenceOffsetManager.lookupFieldLocator(10, forSingleChannelMessageIdx, FROM);
            fieldForSingleChannelMessagePayload = FieldReferenceOffsetManager.lookupFieldLocator(12, forSingleChannelMessageIdx, FROM);
            
            
                                    
            
        } catch (Throwable e) {
           throw new RuntimeException(e);
        } 
        
    }
    
    
    
}
