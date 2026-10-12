package nz.moneymate.app;
import android.content.Context;
import android.graphics.*;
import android.view.View;
final class Illustration extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    Illustration(Context c){super(c);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas c){
        float scale=Math.min(getWidth()/320f,getHeight()/140f);c.save();c.translate((getWidth()-320*scale)/2,(getHeight()-140*scale)/2);c.scale(scale,scale);
        p.setColor(0xffe4f1dd);c.drawOval(52,18,268,138,p);
        p.setColor(0xffb7d6c2);c.drawRoundRect(103,38,215,119,16,16,p);
        p.setColor(0xff123f35);c.drawRoundRect(91,47,209,128,16,16,p);
        p.setColor(0xff69bc91);c.drawRoundRect(169,71,222,104,10,10,p);
        p.setColor(0xffffd773);c.drawCircle(137,35,23,p);p.setColor(0xffa87523);p.setTextSize(27);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("$",129,44,p);
        p.setColor(0xfff5f6ef);c.drawCircle(188,87,4,p);c.drawRoundRect(108,103,148,108,2,2,p);
        p.setColor(0xff08785b);c.drawOval(239,40,252,71,p);c.drawOval(228,65,241,91,p);c.restore();
    }
}
