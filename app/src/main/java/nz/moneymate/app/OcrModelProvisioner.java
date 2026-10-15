package nz.moneymate.app;

import java.util.concurrent.TimeoutException;

/** Bounded recovery of transient model-service failures, independent of PDF data. */
final class OcrModelProvisioner {
    private OcrModelProvisioner() {}
    interface Clock {long now();void sleep(long millis) throws InterruptedException;}
    interface Source {
        boolean available(long timeoutMillis) throws Exception;
        Installation install(long timeoutMillis) throws Exception;
        default long generation(){return 0L;}
        default void reconnect(){}
    }
    interface Installation {Failure failure();void close();}
    static final class Failure extends Exception {
        final int code;
        Failure(int code,String message,Throwable cause){super(message,cause);this.code=code;}
    }
    private static final class ServiceChanged extends Exception {}
    private static boolean changed(Source source,long generation){
        long current=source.generation();return current>0 && generation>0 && current!=generation;
    }
    private static void checkGeneration(Source source,long generation) throws ServiceChanged {
        if(changed(source,generation))throw new ServiceChanged();
    }
    private static TimeoutException timeout(){
        return new TimeoutException("OCR model is not ready yet. Keep an internet connection and retry. No records were added.");
    }
    private static long remaining(Clock clock,long deadline,long limit) throws TimeoutException {
        long time=Math.min(limit,deadline-clock.now());if(time<=0)throw timeout();return time;
    }
    static void ensureReady(Source source,Clock clock) throws Exception {
        final long hardDeadline=clock.now()+240000L;
        long deadline=clock.now()+120000L,generation=source.generation();
        int internalRetries=0,serviceRestarts=0,requestTimeouts=0;
        for(int attempt=0;attempt<4;attempt++) {
            Installation installation=null;boolean serviceChanged=false;
            try {
                checkGeneration(source,generation);
                boolean ready=source.available(remaining(clock,deadline,8000L));
                checkGeneration(source,generation);if(ready)return;
                installation=source.install(remaining(clock,deadline,20000L));
                for(;;) {
                    checkGeneration(source,generation);
                    ready=source.available(remaining(clock,deadline,8000L));
                    checkGeneration(source,generation);if(ready)return;
                    Failure failure=installation.failure();if(failure!=null)throw failure;
                    clock.sleep(remaining(clock,deadline,1000L));
                }
            }catch(ServiceChanged update) {
                serviceChanged=true;
            }catch(Failure failure) {
                serviceChanged=changed(source,generation);
                // Retry only documented INTERNAL_ERROR (8), or a verified
                // package-version change. Permanent errors still fail closed.
                if(!serviceChanged && (failure.code!=8 || internalRetries++>=2))throw failure;
            }catch(InterruptedException interrupted){
                throw interrupted;
            }catch(TimeoutException stalledRequest){
                serviceChanged=changed(source,generation);
                // An individual Play services Task can stall before the overall
                // readiness deadline. Reconnect and recheck availability, rather
                // than treating a request timeout as a permanently missing model.
                if(!serviceChanged && (++requestTimeouts>2 || clock.now()>=deadline))throw stalledRequest;
            }catch(Exception failure){
                // In-flight Tasks can time out during replacement without a
                // terminal install callback. Recover only a verified change.
                if(changed(source,generation))serviceChanged=true;else throw failure;
            }finally {
                if(installation!=null)installation.close();
            }
            if(serviceChanged) {
                if(++serviceRestarts>1)throw timeout();
                generation=source.generation();
                // A package replacement can abandon a pending install without
                // delivering a terminal callback. Request the model again for
                // the new service version, bounded by a four-minute total cap.
                deadline=Math.min(hardDeadline,clock.now()+120000L);
            }
            source.reconnect();
            clock.sleep(remaining(clock,deadline,2500L));
        }
        throw timeout();
    }
}
