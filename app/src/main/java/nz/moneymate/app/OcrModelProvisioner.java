package nz.moneymate.app;

import java.util.concurrent.TimeoutException;

/** Bounded recovery of transient model-service failures, independent of PDF data. */
final class OcrModelProvisioner {
    private OcrModelProvisioner() {}
    interface Clock {
        long now();
        void sleep(long millis) throws InterruptedException;
    }
    interface Source {
        boolean available(long timeoutMillis) throws Exception;
        Installation install(long timeoutMillis) throws Exception;
    }
    interface Installation {
        Failure failure();
        void close();
    }
    static final class Failure extends Exception {
        final int code;
        Failure(int code,String message,Throwable cause) {super(message,cause);this.code=code;}
    }
    private static long remaining(Clock clock,long deadline,long limit) throws TimeoutException {
        long time=Math.min(limit,deadline-clock.now());
        if(time<=0)throw new TimeoutException("OCR model is not ready yet. Keep an internet connection and retry. No records were added.");
        return time;
    }
    static void ensureReady(Source source,Clock clock) throws Exception {
        final long deadline=clock.now()+120000L;
        for(int attempt=0;attempt<3;attempt++) {
            Installation installation=null;
            try {
                if(source.available(remaining(clock,deadline,8000L)))return;
                installation=source.install(remaining(clock,deadline,20000L));
                for(;;) {
                    // Even a completed install or accepted request is not proof
                    // that the API is available after a Play services restart.
                    if(source.available(remaining(clock,deadline,8000L)))return;
                    Failure failure=installation.failure();
                    if(failure!=null)throw failure;
                    clock.sleep(remaining(clock,deadline,1000L));
                }
            }catch(Failure failure) {
                // ModuleInstallStatusCodes inherits INTERNAL_ERROR (8). Google
                // documents retrying this transient error. Do not retry unknown,
                // forbidden, missing-module or cancellation errors.
                if(failure.code!=8 || attempt==2 || deadline-clock.now()<=2500L)throw failure;
            }finally {
                if(installation!=null)installation.close();
            }
            clock.sleep(remaining(clock,deadline,2500L));
        }
        throw new IllegalStateException("OCR setup did not complete. No records were added.");
    }
}
