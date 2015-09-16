package com.ociweb.pronghorn.adapter.netty;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.schema.loader.TemplateHandler;
import com.ociweb.pronghorn.stage.monitor.MonitorConsoleStage;
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
        
        boolean pipesExample = true;
        
        if (pipesExample) {
            
            pipesExamples(gm);
            
        } else {
        
            noPipesExamples(gm);     
        }
        
        
        //TODO: for use with netty make a scheduler that runs in the netty event loop.
        
        StageScheduler scheduler = new ThreadPerStageScheduler(gm);
        scheduler.startup();
        
        //Call this method if we want to shut down early
        try {
            Thread.sleep(120000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        scheduler.shutdown();
        
        scheduler.awaitTermination(2, TimeUnit.DAYS); //stop if still running after two days.
                
    }


    private void noPipesExamples(GraphManager gm) {
        boolean webSocketsDemo = true;
        
        if (webSocketsDemo) {
              StaticHTTPServerStage.setRelativeAppFolderRoot("/src/test/resources/DemoApp");  //CarmaDemo
              WebSocketServerStage server2 = new WebSocketServerStage(gm);
              
        } else {    
            
            StaticHTTPServerStage.setRelativeAppFolderRoot("/src/test/resources/DemoApp2"); 
            StaticHTTPServerStage server = new StaticHTTPServerStage(gm);
        }
    }

    private void pipesExamples(GraphManager gm) {
        try {
            FieldReferenceOffsetManager webSocketFROM = TemplateHandler.loadFrom("/websocket.xml");
            
            PipeConfig toNetConfig = new PipeConfig(webSocketFROM,100,256);
            PipeConfig fromNetConfig = new PipeConfig(webSocketFROM,100,256);
            
            //more pipes are not helping because I do not have that many cores.
            
            Pipe[] toNetPipes = new Pipe[] {new Pipe(toNetConfig)};//,new Pipe(toNetConfig),new Pipe(toNetConfig),new Pipe(toNetConfig)};
            Pipe[] fromNetPipes = new Pipe[] {new Pipe(fromNetConfig)};//,new Pipe(fromNetConfig),new Pipe(toNetConfig),new Pipe(toNetConfig)};
            
            StaticHTTPServerStage.setRelativeAppFolderRoot("/src/test/resources/DemoApp"); 
            WebSocketServerPronghornStage serverStage = new WebSocketServerPronghornStage(gm, toNetPipes, fromNetPipes);       
            
            int i = toNetPipes.length;
            while (--i>=0) {
                
                //we know which pipe to send it back on based on the index position of the pipe it came in on.
                //for many use cases this number will need to be captured and sent down steam as part of the message.
                //the goal is parallel share nothing processing in that case the id will not be needed.
            
                ReflectionStage reflectStage = new ReflectionStage(gm, fromNetPipes[i], toNetPipes[i]);
            
            }
            
            MonitorConsoleStage.attach(gm);
          //  GraphManager.enableBatching(gm);
            
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }
}
