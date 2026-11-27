package com.example.everme.lab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.UUID;

/** Safe phase timing: never records prompts, response bodies, topics, or credentials. */
final class LabTiming implements AutoCloseable {
    private static final Logger log=LoggerFactory.getLogger(LabTiming.class);
    private final String operation,requestId,previousId;
    private final long start=System.nanoTime();
    private long last=start;
    private boolean successful;
    LabTiming(String operation){
        this.operation=operation;previousId=MDC.get("labRequestId");requestId=previousId==null?UUID.randomUUID().toString():previousId;MDC.put("labRequestId",requestId);
        log.info("lab_timing requestId={} operation={} event=start",requestId,operation);
    }
    void mark(String phase){long now=System.nanoTime();log.info("lab_timing requestId={} operation={} phase={} elapsedMs={} totalMs={}",requestId,operation,phase,(now-last)/1_000_000,(now-start)/1_000_000);last=now;}
    void success(){successful=true;}
    @Override public void close(){log.info("lab_timing requestId={} operation={} event=end outcome={} totalMs={}",requestId,operation,successful?"success":"failure",(System.nanoTime()-start)/1_000_000);if(previousId==null)MDC.remove("labRequestId");else MDC.put("labRequestId",previousId);}
}
