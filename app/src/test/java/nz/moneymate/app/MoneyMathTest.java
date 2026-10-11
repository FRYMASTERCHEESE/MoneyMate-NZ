package nz.moneymate.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class MoneyMathTest {
    @Test public void parsesNzdExactly() { assertEquals(123456, MoneyMath.toCents("$1,234.56")); }
    @Test public void handlesZeroAndNegative() { assertEquals(0, MoneyMath.toCents("0")); assertEquals(-205, MoneyMath.toCents("-2.05")); }
    @Test(expected = ArithmeticException.class) public void disallowsFractionalCents() { MoneyMath.toCents("1.234"); }
    @Test public void balancesPrecisely() { assertEquals(999, MoneyMath.remaining(2000,1001)); }
    @Test public void formatsNzd() { assertTrue(MoneyMath.format(150).contains("1.50")); }
}
