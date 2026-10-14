package nz.moneymate.app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyMath {
    private MoneyMath() {}
    public static long toCents(String raw) {
        BigDecimal n = new BigDecimal(raw.trim().replace("$", "").replace(",", ""));
        long cents=n.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        if(cents>9_007_199_254_740_991L||cents< -9_007_199_254_740_991L)
            throw new IllegalArgumentException("Amount is too large for backup and sync");
        return cents;
    }
    public static String format(long cents) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-NZ"));
        return f.format(BigDecimal.valueOf(cents, 2));
    }
    public static long remaining(long income, long expenses) { return Math.subtractExact(income, expenses); }
    public static long addSavings(long saved, long contribution) {
        long limit=9_007_199_254_740_991L;
        if(saved<0||saved>limit||contribution<=0||contribution>limit-saved)
            throw new IllegalArgumentException("Contribution is too large or invalid");
        return saved+contribution;
    }
}
