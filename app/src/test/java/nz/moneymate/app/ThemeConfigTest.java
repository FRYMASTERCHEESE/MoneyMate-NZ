package nz.moneymate.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ThemeConfigTest {
    @Test public void acceptsSolidRgb() { assertEquals(0xff06785b,ThemeConfig.hex("#06785B")); }
    @Test(expected=IllegalArgumentException.class) public void rejectsCssCode() {ThemeConfig.hex("red; background:blue");}
    @Test(expected=IllegalArgumentException.class) public void rejectsTransparentColor() {ThemeConfig.hex("#8006785B");}
    @Test public void checksReadableContrast() {
        assertTrue(ThemeConfig.readable(0xffffffff,0xff06785b));
        assertFalse(ThemeConfig.readable(0xffffffff,0xfff5f6ef));
    }
}
