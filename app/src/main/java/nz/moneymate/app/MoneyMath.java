package nz.moneymate.app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyMath {
    private MoneyMath() {}
    public static long toCents(String raw) {
        BigDecimal n = new BigDecimal(raw.trim().replace("$", "").replace(",", ""));
        return n.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }
    public static String format(long cents) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-NZ"));
        return f.format(BigDecimal.valueOf(cents, 2));
    }
    public static long remaining(long income, long expenses) { return Math.subtractExact(income, expenses); }
}
