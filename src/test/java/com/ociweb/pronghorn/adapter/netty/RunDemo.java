package com.ociweb.pronghorn.adapter.netty;

import java.util.concurrent.TimeUnit;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.schema.loader.TemplateHandler;
import com.ociweb.pronghorn.stage.monitor.MonitorConsoleStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.stage.scheduling.ThreadPerStageScheduler;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.internal.SystemPropertyUtil;

public class RunDemo {

    static RunDemo instance;
    
    public static void main(String[] args) {
        instance = new RunDemo();
        instance.run();

    }

    private void run() {
        
        GraphManager gm = new GraphManager();
        
        buildApplicationGraph(gm);            
        
        StageScheduler scheduler = new ThreadPerStageScheduler(gm);  //TODO: Something cool would be a scheduler that runs in the netty event loop.
        scheduler.startup();
        
        //Call this method if we want to shut down early
        try {
            Thread.sleep(1000*60*60*12);//run for 12 hours then shutdown
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        scheduler.shutdown();   
        
        scheduler.awaitTermination(2, TimeUnit.SECONDS); //shutdown process should not take longer than 2 seconds or something is wrong
                
    }

    private void buildApplicationGraph(GraphManager gm) {
        try {
            FieldReferenceOffsetManager webSocketFROM = TemplateHandler.loadFrom("/websocket.xml");
            
            final int pipeLength = 40;
            final int maxFieldLength = 42*1024;
            
            PipeConfig<WebSocketSchema> toNetConfig = new PipeConfig<WebSocketSchema>(WebSocketSchema.instance, pipeLength, maxFieldLength);
            PipeConfig<WebSocketSchema> fromNetConfig = new PipeConfig<WebSocketSchema>(WebSocketSchema.instance, pipeLength, maxFieldLength);
            
            //more pipes are not helping because I do not have that many cores.
            
            @SuppressWarnings("unchecked")
            Pipe<WebSocketSchema>[] toNetPipes = new Pipe[] {new Pipe<WebSocketSchema>(toNetConfig), new Pipe<WebSocketSchema>(toNetConfig)};
            @SuppressWarnings("unchecked")
            Pipe<WebSocketSchema>[] fromNetPipes = new Pipe[] {new Pipe<WebSocketSchema>(fromNetConfig), new Pipe<WebSocketSchema>(fromNetConfig)};

            ///////////////////////////////
            //NOTE: Remove this one line and the html/js files will be loaded from inside the jar in the resources folder
            /////////
            WebSocketServerPronghornStage.setRelativeAppFolderRoot(SystemPropertyUtil.get("user.dir")+"/src/test/resources/DemoApp"); 
            ////////
            
            WebSocketServerPronghornStage serverStage = new WebSocketServerPronghornStage(gm, toNetPipes, fromNetPipes,new NioEventLoopGroup(1), new NioEventLoopGroup(fromNetPipes.length));       
            
            int i = toNetPipes.length;
            while (--i >= 0) {
                
                //we know which pipe to send it back on based on the index position of the pipe it came in on.
                //for many use cases this number will need to be captured and sent down steam as part of the message.
                //the goal is parallel share nothing processing in that case the id will not be needed.
            
                ReflectionStage reflectStage = new ReflectionStage(gm, fromNetPipes[i], toNetPipes[i]);
            
            }
            
       //     MonitorConsoleStage.attach(gm);
       //     GraphManager.enableBatching(gm);
            
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }
}
