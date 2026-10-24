package nz.moneymate.app;

import android.database.Cursor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A forward-looking planning summary, not a bank account balance or payment.
 * Reads the existing, user-owned unpaid bills and repeating bookkeeping rules.
 * Never modifies stored transactions, cloud data or recurring next dates.
 */
final class FortnightPlan {
    private static final long MAX_SAFE_CENTS=9_007_199_254_740_991L;

    static final class Event {
        final LocalDate date;
        final String type,name,kind;
        final long cents;
        Event(LocalDate date,String type,String name,String kind,long cents) {
            this.date=date;this.type=type;this.name=name;this.kind=kind;this.cents=cents;
        }
    }

    static final class Summary {
        final LocalDate start,end;
        final long incoming,outgoing,net;
        final List<Event> events;
        Summary(LocalDate start,LocalDate end,long incoming,long outgoing,List<Event> events) {
            this.start=start;this.end=end;this.incoming=incoming;this.outgoing=outgoing;
            this.net=Math.subtractExact(incoming,outgoing);
            this.events=Collections.unmodifiableList(events);
        }
    }

    static Summary calculate(Store store,LocalDate start,int days) {
        if(store==null||start==null||days<1||days>31)
            throw new IllegalArgumentException("Invalid planning window");
        LocalDate end=start.plusDays(days-1);
        List<Event> events=new ArrayList<>();
        try(Cursor c=store.query(
                "SELECT name,cents,due FROM bills WHERE due>=? AND due<=? ORDER BY due,id",
                start.toString(),end.toString())) {
            while(c.moveToNext())events.add(new Event(LocalDate.parse(c.getString(2)),
                "Bill",c.getString(0),"Expense",c.getLong(1)));
        }
        try(Cursor c=store.query(
                "SELECT category,cents,next_date,frequency,kind FROM recurring WHERE next_date>=? AND next_date<=? ORDER BY next_date,id",
                start.toString(),end.toString())) {
            while(c.moveToNext()) {
                LocalDate due=LocalDate.parse(c.getString(2));
                String frequency=c.getString(3),kind=c.getString(4);
                long cents=c.getLong(1);
                int safety=0;
                while(!due.isAfter(end)) {
                    if(++safety>6)throw new IllegalArgumentException("Invalid repeating schedule");
                    events.add(new Event(due,"Repeating",c.getString(0),kind,cents));
                    switch(frequency) {
                        case "Weekly":due=due.plusWeeks(1);break;
                        case "Fortnightly":due=due.plusWeeks(2);break;
                        case "Monthly":due=due.plusMonths(1);break;
                        default:throw new IllegalArgumentException("Invalid repeating schedule");
                    }
                }
            }
        }
        events.sort(Comparator.comparing((Event e)->e.date)
            .thenComparing(e->e.type).thenComparing(e->e.name));
        long incoming=0,outgoing=0;
        for(Event e:events) {
            if(e.cents<=0||e.cents>MAX_SAFE_CENTS)
                throw new IllegalArgumentException("Invalid planned amount");
            if("Income".equals(e.kind))incoming=Math.addExact(incoming,e.cents);
            else if("Expense".equals(e.kind))outgoing=Math.addExact(outgoing,e.cents);
            else throw new IllegalArgumentException("Invalid planned direction");
            if(incoming>MAX_SAFE_CENTS||outgoing>MAX_SAFE_CENTS)
                throw new IllegalArgumentException("Planned amount is too large");
        }
        if(Math.abs(incoming-outgoing)>MAX_SAFE_CENTS)
            throw new IllegalArgumentException("Planned amount is too large");
        return new Summary(start,end,incoming,outgoing,events);
    }
}
