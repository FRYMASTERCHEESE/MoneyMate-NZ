package nz.moneymate.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import java.util.List;
import java.util.function.Supplier;

/** User-confirmed import of a locally selected PDF into the existing profile. */
final class PdfImport {
    private PdfImport(){}
    static void start(Activity activity,Store store,Supplier<String> currentOwner,
                      Intent selected,Runnable refresh){
        final String owner=currentOwner.get();
        final String baseline;
        try{baseline=store.backupJson();}
        catch(Exception e){error(activity,"Could not prepare a safety snapshot. Nothing was added.");return;}
        final EditText nickname=new EditText(activity);
        nickname.setSingleLine(true);nickname.setText(R.string.default_account_nickname);
        nickname.setHint("Account nickname, NOT account number");
        new AlertDialog.Builder(activity).setTitle("Read NZD bank PDF for free")
            .setMessage("Choose the same nickname for future statements from this account. Text recognition runs on this phone, but PDF figures may be misread. You must check every transaction before saving.")
            .setView(nickname).setNegativeButton("Cancel",null)
            .setPositiveButton("Read PDF",(d,w)->{
                final String label=nickname.getText().toString().trim();
                if(!owner.equals(currentOwner.get())){error(activity,"Profile changed. Choose the PDF again.");return;}
                final AlertDialog progress=new AlertDialog.Builder(activity)
                    .setMessage("Preparing PDF text recognition on this phone. First use may need internet to download the text model. Your PDF stays on this phone. Please wait...").setCancelable(false).create();
                progress.show();
                final Uri uri=selected.getData();
                new Thread(()->{
                    List<BankCsv.Entry> entries=null;String problem=null;
                    try{
                        List<String> lines=PdfStatementReader.read(activity.getApplicationContext(),uri);
                        String csv=PdfStatement.toCsv(lines);
                        entries=BankCsv.parse(csv,label);
                        if(entries.size()>200)throw new IllegalArgumentException("Import 200 rows or fewer");
                    }catch(Exception ex){
                        problem=ex.getMessage()!=null?ex.getMessage():"Unsupported PDF statement";
                    }
                    final List<BankCsv.Entry> parsed=entries;final String failure=problem;
                    activity.runOnUiThread(()->{
                        progress.dismiss();
                        if(activity.isFinishing()||activity.isDestroyed())return;
                        if(failure!=null){
                            error(activity,"Could not safely read this PDF: "+failure+"\nNo records were added. Some bank layouts and scanned files need additional work.");
                            return;
                        }
                        if(!owner.equals(currentOwner.get())){error(activity,"Account changed. Nothing was added.");return;}
                        try{
                            if(!baseline.equals(store.backupJson()))
                                throw new IllegalArgumentException("Records changed during PDF reading. Try again.");
                            Store.BankImportResult preview=store.importBankCsv(parsed,true);
                            if(preview.inserted==0){error(activity,"No new rows; "+preview.skipped+" recognised duplicates.");return;}
                            showReview(activity,store,parsed,preview,owner,currentOwner,baseline,refresh);
                        }catch(Exception ex){error(activity,"PDF review stopped: "+ex.getMessage());}
                    });
                },"moneymate-pdf-ocr").start();
            }).show();
    }
    private static void showReview(Activity activity,Store store,List<BankCsv.Entry> entries,
                                   Store.BankImportResult preview,String owner,
                                   Supplier<String> currentOwner,String baseline,Runnable refresh){
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(activity.getResources().getDisplayMetrics().density*15);
        box.setPadding(pad,pad,pad,pad);
        TextView warning=new TextView(activity);
        warning.setText(activity.getResources().getQuantityString(R.plurals.pdf_review_warning, entries.size(), entries.size(), preview.inserted, preview.skipped));
        box.addView(warning);
        ScrollView scroll=new ScrollView(activity);
        LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<entries.size();i++){
            BankCsv.Entry row=entries.get(i);
            TextView item=new TextView(activity);
            item.setPadding(0,pad/3,0,pad/3);
            item.setText(activity.getString(R.string.pdf_review_row, i+1, row.date, row.kind, MoneyMath.format(row.cents), row.note));
            list.addView(item);
        }
        scroll.addView(list);
        box.addView(scroll,new LinearLayout.LayoutParams(-1,(int)(320*activity.getResources().getDisplayMetrics().density)));
        CheckBox accepted=new CheckBox(activity);
        accepted.setText(R.string.pdf_review_acceptance);
        box.addView(accepted);
        AlertDialog confirm=new AlertDialog.Builder(activity).setTitle("Confirm PDF transactions")
            .setView(box).setNegativeButton("Cancel",null)
            .setPositiveButton("Add transactions",(d,w)->{
                try{
                    if(!owner.equals(currentOwner.get())||!baseline.equals(store.backupJson()))
                        throw new IllegalArgumentException("The profile changed during review. Please retry.");
                    Store.BankImportResult done=store.importBankCsv(entries,false);
                    refresh.run();
                    error(activity,"Added "+done.inserted+" reviewed NZD transactions. Skipped "+done.skipped+" duplicates.");
                }catch(Exception ex){error(activity,"No PDF entries added: "+ex.getMessage());}
            }).create();
        confirm.setOnShowListener(d->{
            Button yes=confirm.getButton(AlertDialog.BUTTON_POSITIVE);
            yes.setEnabled(false);
            accepted.setOnCheckedChangeListener((button,checked)->yes.setEnabled(checked));
        });
        confirm.show();
    }
    private static void error(Activity a,String text){
        if(!a.isFinishing()&&!a.isDestroyed())
            new AlertDialog.Builder(a).setMessage(text).setPositiveButton("OK",null).show();
    }
}
