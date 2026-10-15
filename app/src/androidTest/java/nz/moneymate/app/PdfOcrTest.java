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

/** Exercise Play services OCR after R8, using only generated statements. */
@RunWith(AndroidJUnit4.class)
public class PdfOcrTest {
    @Test public void syntheticAnzPdfReconcilesAndReimportDoesNotDuplicate() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file=File.createTempFile("synthetic-anz-", ".pdf",context.getCacheDir());
        String owner="77777777-7777-4777-8777-777777777777";
        context.deleteDatabase(Store.databaseName(owner));
        try {
            PdfDocument document=new PdfDocument();
            try {
                PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(1200,700,1).create());
                page.getCanvas().drawColor(Color.WHITE);
                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(24);
                String[] lines={"ANZ Bank New Zealand Limited", "Statement period 10 Sep 2024 - 09 Dec 2024",
                    "Date Transaction type and details Withdrawals Deposits Balance",
                    "10 Sep Opening balance 9,656.67", "10 Sep AP Transfer from family 5.00 9,661.67",
                    "10 Sep EP Countdown NZ 7.00 9,654.67", "11 Sep Fuel BP 7.95 9,646.72"};
                for(int i=0;i<lines.length;i++)page.getCanvas().drawText(lines[i],40,70+i*75,paint);
                document.finishPage(page);
                try(FileOutputStream out=new FileOutputStream(file)){document.writeTo(out);}
            }finally{document.close();}
            java.util.List<String> lines=PdfStatementReader.read(context,Uri.fromFile(file));
            assertTrue("OCR must recognise the ANZ table",AnzStatement.matches(lines));
            java.util.List<BankCsv.Entry> rows=BankCsv.parse(PdfStatement.toCsv(lines),"Synthetic ANZ");
            assertEquals(3,rows.size());
            assertEquals("Income",rows.get(0).kind);assertEquals(500L,rows.get(0).cents);
            assertEquals("2024-09-10",rows.get(0).date);
            try(Store store=new Store(context,owner)) {
                store.importBankCsv(rows,false);
                assertEquals(500L,store.sum("Income","2024-09"));
                assertEquals(1495L,store.sum("Expense","2024-09"));
                String backup=store.backupJson();store.restoreJson(backup);
                assertEquals(3,store.importBankCsv(rows,false).skipped);
                assertEquals(backup,store.backupJson());
            }
        }finally {
            context.deleteDatabase(Store.databaseName(owner));
            if(file.exists())assertTrue(file.delete());
        }
    }
    @Test public void syntheticPdfOcrImportsAndRoundTripsWithoutDuplicates() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file=File.createTempFile("synthetic-statement-", ".pdf", context.getCacheDir());
        String owner="44444444-4444-4444-8444-444444444444";
        context.deleteDatabase(Store.databaseName(owner));
        try {
            PdfDocument document=new PdfDocument();
            try {
                PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(800,600,1).create());
                page.getCanvas().drawColor(Color.WHITE);
                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(24);
                String[] lines={"NZD statement", "Date Description Amount Balance",
                    "12/10/2026 Grocery -52.30 1125.70", "13/10/2026 Salary +1000.00 2125.70"};
                for(int i=0;i<lines.length;i++)page.getCanvas().drawText(lines[i],40,70+i*65,paint);
                document.finishPage(page);
                try(FileOutputStream out=new FileOutputStream(file)){document.writeTo(out);}
            } finally {document.close();}
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
