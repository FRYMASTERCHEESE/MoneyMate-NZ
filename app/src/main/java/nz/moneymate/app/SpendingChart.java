package nz.moneymate.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.List;

final class SpendingChart extends View {
    static final int[] COLORS={0xff08785b,0xff397bc3,0xffb96428,0xff8760ac,0xff59763a,0xffba446c};
    private final List<Long> amounts;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    SpendingChart(Context context,List<Long> amounts){super(context);this.amounts=amounts;setContentDescription("Spending by category. Amounts and percentages are listed below.");}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.37f;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(r*.25f);
        long total=0;for(long n:amounts)total+=n;
        float start=-90;RectF ring=new RectF(cx-r,cy-r,cx+r,cy+r);
        for(int i=0;i<amounts.size();i++){float sweep=(float)(amounts.get(i)*360.0/total);paint.setColor(COLORS[i%COLORS.length]);canvas.drawArc(ring,start,sweep,false,paint);start+=sweep;}
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(0xff142928);
        paint.setTextSize(14*getResources().getDisplayMetrics().scaledDensity);canvas.drawText("TOTAL SPENT",cx,cy-12,paint);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));paint.setTextSize(22*getResources().getDisplayMetrics().scaledDensity);
        canvas.drawText(MoneyMath.format(total),cx,cy+24,paint);
    }
}
