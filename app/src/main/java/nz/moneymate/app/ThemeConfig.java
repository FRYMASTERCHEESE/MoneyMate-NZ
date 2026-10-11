package nz.moneymate.app;

import org.json.JSONObject;

/** Safely limited remote presentation settings. No dynamic code, permissions, or finance access. */
public final class ThemeConfig {
    public static final ThemeConfig DEFAULT=new ThemeConfig(
        0xff06785b, 0xfff5f6ef, 0xff142928, 0xff123f35,
        "A little clarity. A little more confidence.");
    public final int accent, background, ink, hero;
    public final String tagline;

    private ThemeConfig(int accent,int background,int ink,int hero,String tagline) {
        this.accent=accent;this.background=background;this.ink=ink;this.hero=hero;
        this.tagline=tagline;
    }
    /** Only a six-digit RGB hex colour is accepted: never arbitrary CSS or code. */
    public static int hex(String value) {
        if(value==null||!value.matches("#[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("Expected #RRGGBB colour");
        return (int)(0xff000000L|Long.parseLong(value.substring(1),16));
    }
    private static double channel(int c) {
        double x=c/255.0;
        return x<=0.04045?x/12.92:Math.pow((x+0.055)/1.055,2.4);
    }
    private static double luminance(int color) {
        return 0.2126*channel((color>>16)&255)+0.7152*channel((color>>8)&255)+0.0722*channel(color&255);
    }
    public static boolean readable(int foreground,int background) {
        double a=luminance(foreground),b=luminance(background);
        return (Math.max(a,b)+0.05)/(Math.min(a,b)+0.05)>=4.5;
    }
    public static ThemeConfig parse(String json) throws Exception {
        JSONObject o=new JSONObject(json);
        if(o.getInt("version")!=1)throw new IllegalArgumentException("Unsupported theme version");
        int accent=hex(o.getString("accent"));
        int canvas=hex(o.getString("background"));
        int ink=hex(o.getString("text"));
        int hero=hex(o.getString("hero"));
        String tagline=o.getString("tagline").trim();
        if(tagline.isEmpty()||tagline.length()>90||tagline.matches("(?s).*[\\p{Cntrl}].*"))
            throw new IllegalArgumentException("Invalid tagline");
        // Keep contrast legible, including white text on coloured buttons and balance card.
        if(!readable(0xffffffff,accent)||!readable(0xffffffff,hero)
           ||!readable(ink,canvas)||!readable(ink,0xffffffff))
            throw new IllegalArgumentException("Theme fails text-contrast requirements");
        return new ThemeConfig(accent,canvas,ink,hero,tagline);
    }
}
