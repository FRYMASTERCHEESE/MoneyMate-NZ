package nz.moneymate.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercise bundled ML Kit/JNI after R8, using only a generated statement. */
@RunWith(AndroidJUnit4.class)
public class PdfOcrTest {
    @Test public void syntheticPdfOcrImportsAndRoundTripsWithoutDuplicates() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file=File.createTempFile("synthetic-statement-", ".pdf", context.getCacheDir());
        String owner="44444444-4444-4444-8444-444444444444";
        context.deleteDatabase(Store.databaseName(owner));
        try {
            try(PdfDocument document=new PdfDocument()) {
                PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(800,600,1).create());
                page.getCanvas().drawColor(Color.WHITE);
                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(24);
                String[] lines={"NZD statement", "Date Description Amount Balance",
                    "12/10/2026 Grocery -52.30 1125.70", "13/10/2026 Salary +1000.00 2125.70"};
                for(int i=0;i<lines.length;i++)page.getCanvas().drawText(lines[i],40,70+i*65,paint);
                document.finishPage(page);
                try(FileOutputStream out=new FileOutputStream(file)){document.writeTo(out);}
            }
            String csv=PdfStatement.toCsv(PdfStatementReader.read(context,Uri.fromFile(file)));
            java.util.List<BankCsv.Entry> rows=BankCsv.parse(csv,"Synthetic");
            assertEquals(2,rows.size());
            try(Store store=new Store(context,owner)) {
                store.importBankCsv(rows,false);
                assertEquals(5230,store.sum("Expense","2026-10"));
                assertEquals(100000,store.sum("Income","2026-10"));
                String backup=store.backupJson();store.restoreJson(backup);
                assertEquals(backup,store.backupJson());
                assertEquals(2,store.importBankCsv(rows,false).skipped);
                assertEquals(backup,store.backupJson());
            }
        } finally {
            context.deleteDatabase(Store.databaseName(owner));
            if(file.exists())assertTrue(file.delete());
        }
    }
}
