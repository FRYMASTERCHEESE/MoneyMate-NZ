package nz.moneymate.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.TimeoutException;

public class OcrModelProvisionerTest {
    private static final class Clock implements OcrModelProvisioner.Clock {
        long time; boolean interrupt;
        public long now(){return time;}
        public void sleep(long millis) throws InterruptedException {
            if(interrupt)throw new InterruptedException("synthetic interruption");
            assertTrue(millis>0);time+=millis;
        }
    }
    private static class Source implements OcrModelProvisioner.Source {
        final Clock clock; final int[] errors; final long readyAt;
        int attempts,closed,reconnections; long changeAt=Long.MAX_VALUE,lastInstallGeneration; boolean requestFailure,preinstalled,replaceDuringRequest;
        public long generation(){return clock.time>=changeAt?2L:1L;}
        public void reconnect(){reconnections++;}
        Source(Clock clock,long readyAt,int...errors){this.clock=clock;this.readyAt=readyAt;this.errors=errors;}
        public boolean available(long timeout){
            assertTrue(timeout>0 && timeout<=8000);
            return preinstalled || attempts>0 && lastInstallGeneration==generation() && errors[Math.min(attempts-1,errors.length-1)]==0 && clock.time>=readyAt;
        }
        public OcrModelProvisioner.Installation install(long timeout) throws Exception {
            assertTrue(timeout>0 && timeout<=20000);
            lastInstallGeneration=generation();
            final int error=errors[Math.min(attempts++,errors.length-1)];
            if(replaceDuringRequest && attempts==1){clock.time=85000L;throw new TimeoutException("service request lost");}
            if(requestFailure && error!=0)throw new OcrModelProvisioner.Failure(error,"request failed",null);
            return new OcrModelProvisioner.Installation(){
                public OcrModelProvisioner.Failure failure(){return error==0?null:new OcrModelProvisioner.Failure(error,"install failed",null);}
                public void close(){closed++;}
            };
        }
    }
    @Test public void serviceReplacementReissuesAbandonedRequestWithoutFailureCallback() throws Exception {
        Clock clock=new Clock();Source source=new Source(clock,150000L,0);source.changeAt=85000L;
        OcrModelProvisioner.ensureReady(source,clock);
        assertEquals(2,source.attempts);assertEquals(2,source.closed);assertEquals(1,source.reconnections);
        assertTrue(clock.time>=150000L && clock.time<=240000L);
    }
    @Test public void inFlightRequestTimeoutRecoversOnlyAfterVerifiedReplacement() throws Exception {
        Clock clock=new Clock();Source source=new Source(clock,150000L,0);source.changeAt=85000L;source.replaceDuringRequest=true;
        OcrModelProvisioner.ensureReady(source,clock);
        assertEquals(2,source.attempts);assertEquals(1,source.closed);assertEquals(1,source.reconnections);
        assertTrue(clock.time>=150000L && clock.time<=240000L);
    }
    @Test public void serviceReplacementCannotResetTheWaitForever() throws Exception {
        Clock clock=new Clock();Source source=new Source(clock,Long.MAX_VALUE,0);source.changeAt=119000L;
        try{OcrModelProvisioner.ensureReady(source,clock);fail("Unavailable replacement accepted");}
        catch(TimeoutException expected){assertTrue(clock.time<=240000L);}
        assertEquals(2,source.attempts);assertEquals(2,source.closed);assertEquals(1,source.reconnections);
    }
    @Test public void cachedModelDoesNotRequestInstallOrWait() throws Exception {
        Clock clock=new Clock();Source source=new Source(clock,0L,0);source.preinstalled=true;
        OcrModelProvisioner.ensureReady(source,clock);
        assertEquals(0,source.attempts);assertEquals(0,source.closed);assertEquals(0L,clock.time);
    }
    @Test public void stalledInstallRequestReconnectsWithinExistingDeadline() throws Exception {
        Clock clock=new Clock();Source source=new Source(clock,0L,0){
            @Override public OcrModelProvisioner.Installation install(long timeout) throws Exception {
                if(reconnections==0){clock.time+=timeout;throw new TimeoutException("stalled install request");}
                return super.install(timeout);
            }
        };
        OcrModelProvisioner.ensureReady(source,clock);
        assertEquals(1,source.reconnections);assertEquals(1,source.closed);assertTrue(clock.time<120000L);
    }
    @Test public void repeatedRequestTimeoutsAreBoundedWithoutServiceReplacement() throws Exception {
        Clock clock=new Clock();final int[] requests={0};
        Source source=new Source(clock,Long.MAX_VALUE,0){
            @Override public OcrModelProvisioner.Installation install(long timeout) throws Exception {
                requests[0]++;clock.time+=timeout;throw new TimeoutException("stalled install request");
            }
        };
        try{OcrModelProvisioner.ensureReady(source,clock);fail("Timed out requests treated as ready");}
        catch(TimeoutException expected){assertEquals(3,requests[0]);assertEquals(2,source.reconnections);assertTrue(clock.time<=120000L);}
    }
    @Test public void transientFailureRetriesButNeverReturnsBeforeModelIsAvailable() throws Exception {
        Clock clock=new Clock();Source source=new Source(clock,4000L,8,0);
        OcrModelProvisioner.ensureReady(source,clock);
        assertEquals(2,source.attempts);assertEquals(2,source.closed);assertTrue(clock.time>=4000L);
    }
    @Test public void transientRequestFailureAlsoRequiresARealAvailabilityCheck() throws Exception {
        Clock clock=new Clock();Source source=new Source(clock,4000L,8,0);source.requestFailure=true;
        OcrModelProvisioner.ensureReady(source,clock);
        assertEquals(2,source.attempts);assertEquals(1,source.closed);assertTrue(clock.time>=4000L);
    }
    @Test public void repeatedInternalFailuresStopAfterFiveRequests() throws Exception {
        Clock clock=new Clock();Source source=new Source(clock,Long.MAX_VALUE,8);
        try{OcrModelProvisioner.ensureReady(source,clock);fail("Repeated failure accepted");}
        catch(OcrModelProvisioner.Failure expected){assertEquals(8,expected.code);}
        assertEquals(5,source.attempts);assertEquals(5,source.closed);
        assertEquals(55000L,clock.time);
    }
    @Test public void replacementInstallerGetsTimeToSettleBeforeRetryBudgetIsSpent() throws Exception {
        Clock clock=new Clock();Source source=new Source(clock,0L,0){
            @Override public OcrModelProvisioner.Installation install(long timeout) throws Exception {
                if(clock.time<30000L) {
                    attempts++;
                    throw new OcrModelProvisioner.Failure(8,"installer still starting",null);
                }
                return super.install(timeout);
            }
        };
        OcrModelProvisioner.ensureReady(source,clock);
        assertEquals(4,source.attempts);assertEquals(35000L,clock.time);
        assertEquals(1,source.closed);
    }
    @Test public void internalErrorBackoffCannotExtendReadinessDeadline() throws Exception {
        Clock clock=new Clock();Source source=new Source(clock,Long.MAX_VALUE,8){
            @Override public OcrModelProvisioner.Installation install(long timeout) throws Exception {
                clock.time+=timeout;return super.install(timeout);
            }
        };
        try{OcrModelProvisioner.ensureReady(source,clock);fail("Deadline extended");}
        catch(TimeoutException expected){assertEquals(120000L,clock.time);}
        assertEquals(source.attempts,source.closed);
    }
    @Test public void permanentAndCancelledFailuresAreNotRetried() throws Exception {
        for(int error:new int[]{16,46000,46001,46002,46003,46004}) {
            Clock clock=new Clock();Source source=new Source(clock,Long.MAX_VALUE,error);
            try{OcrModelProvisioner.ensureReady(source,clock);fail("Unavailable model accepted");}
            catch(OcrModelProvisioner.Failure expected){assertEquals(error,expected.code);}
            assertEquals(1,source.attempts);assertEquals(1,source.closed);assertEquals(0L,clock.time);
        }
    }
    @Test public void acceptedInstallWithoutAvailabilityTimesOutAndCloses() throws Exception {
        Clock clock=new Clock();Source source=new Source(clock,Long.MAX_VALUE,0);
        try{OcrModelProvisioner.ensureReady(source,clock);fail("Accepted request treated as ready");}
        catch(TimeoutException expected){assertTrue(expected.getMessage().contains("No records were added"));}
        assertEquals(120000L,clock.time);assertEquals(1,source.attempts);assertEquals(1,source.closed);
    }
    @Test public void interruptionStopsProvisioningAndClosesListener() throws Exception {
        Clock clock=new Clock();clock.interrupt=true;Source source=new Source(clock,Long.MAX_VALUE,0);
        try{OcrModelProvisioner.ensureReady(source,clock);fail("Interruption ignored");}
        catch(InterruptedException expected){assertEquals(1,source.attempts);assertEquals(1,source.closed);}
    }
}
