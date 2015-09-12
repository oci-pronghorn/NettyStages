package com.ociweb.pronghorn.adapter.netty;

import java.util.concurrent.TimeUnit;

import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.stage.scheduling.ThreadPerStageScheduler;

public class RunDemo {

    static RunDemo instance;
    
    public static void main(String[] args) {
        instance = new RunDemo();
        instance.run();

    }

    private void run() {
        
        GraphManager gm = new GraphManager();
        
        boolean webSocketsDemo = true;
        
        if (webSocketsDemo) {
              StaticHTTPServerStage.setRelativeAppFolderRoot("/src/test/resource/DemoApp"); 
              WebSocketServerStage server2 = new WebSocketServerStage(gm);
            
            
        } else {    
            //The way this is written at the moment there is no need for pronghorn stages
            //we are only using it as an easy way to track startup and shutdown.
            
            //TODO: this needs to use the proxy
            //this could however be modified to send messages down a pipe when files are retrieved
            // TODO use the pipes for retrieval of the resources (this is the plan).
            // there are two types of messages:
            //                                 local files, (just the name so they can be zero copied)
            //                                 virtual files, (full content is in the pipe, to be sent in chunks)
            
            StaticHTTPServerStage.setRelativeAppFolderRoot("/src/test/resource/DemoApp2"); 
            StaticHTTPServerStage server = new StaticHTTPServerStage(gm);
        }
        
        
        
        StageScheduler scheduler = new ThreadPerStageScheduler(gm);
        scheduler.startup();
        
        
//        //TODO: rewrite to stay running until we request shutdown.
//        
//        try {
//            Thread.sleep(5000000);
//        } catch (InterruptedException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//        
//        
//        scheduler.shutdown();
        
        scheduler.awaitTermination(2, TimeUnit.DAYS); //stop if still running after two days.
        
        
        // TODO Auto-generated method stub
        
    }

}
