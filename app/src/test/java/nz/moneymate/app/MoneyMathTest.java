package nz.moneymate.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class MoneyMathTest {
    @Test public void enteredAmountsRemainCompatibleWithWebsiteBackups() {
        assertEquals(9_007_199_254_740_991L,MoneyMath.toCents("90071992547409.91"));
        for(String amount:new String[]{"90071992547409.92","-90071992547409.92"}){
            try {MoneyMath.toCents(amount);fail("Unsafe amount accepted");}
            catch(IllegalArgumentException expected){}
        }
    }
    @Test public void savingsStayWithinWebAndCloudIntegerRange() {
        long limit=9_007_199_254_740_991L;
        assertEquals(limit,MoneyMath.addSavings(limit-1,1));
        for(long amount:new long[]{0,-1,2,Long.MAX_VALUE}){
            try {MoneyMath.addSavings(limit-1,amount);fail("Unsafe contribution accepted");}
            catch(IllegalArgumentException expected){}
        }
    }
    @Test public void parsesNzdExactly() { assertEquals(123456, MoneyMath.toCents("$1,234.56")); }
    @Test public void handlesZeroAndNegative() { assertEquals(0, MoneyMath.toCents("0")); assertEquals(-205, MoneyMath.toCents("-2.05")); }
    @Test(expected = ArithmeticException.class) public void disallowsFractionalCents() { MoneyMath.toCents("1.234"); }
    @Test public void balancesPrecisely() { assertEquals(999, MoneyMath.remaining(2000,1001)); }
    @Test public void formatsNzd() { assertTrue(MoneyMath.format(150).contains("1.50")); }
}
