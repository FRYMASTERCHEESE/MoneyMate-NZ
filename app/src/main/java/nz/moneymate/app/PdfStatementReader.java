package nz.moneymate.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest;
import com.google.android.gms.common.moduleinstall.ModuleInstallClient;
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.moduleinstall.InstallStatusListener;
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutionException;

/** Private, on-device OCR of the PDF pages selected by the Android document picker.
 * Uses Android PdfRenderer + Google Play services on-device Latin text recognition.
 * Model provisioning needs a network connection once; downloaded models can run offline.
 * Do not log/retain financial statement contents or send it to a remote server.
 */
public final class PdfStatementReader {
    private PdfStatementReader(){}
    private static final class Piece {
        final int x,y;final String text;
        Piece(int x,int y,String text){this.x=x;this.y=y;this.text=text;}
    }
    /** Never read financial PDFs before the optional on-device OCR model is ready.
     * This runs on PdfImport's background worker, not the Android UI thread.
     */
    private static <T> T moduleResult(com.google.android.gms.tasks.Task<T> task,long timeout) throws Exception {
        try{return Tasks.await(task,timeout,TimeUnit.MILLISECONDS);}
        catch(ExecutionException failed){
            Throwable cause=failed.getCause();
            if(cause instanceof ApiException){
                int code=((ApiException)cause).getStatusCode();
                android.util.Log.w("MoneyMateOcr","Model service error: "+code+" "+ModuleInstallStatusCodes.getStatusCodeString(code));
                throw new OcrModelProvisioner.Failure(code,"OCR model service failed (code "+code+", "+ModuleInstallStatusCodes.getStatusCodeString(code)+"). No records were added.",cause);
            }
            throw failed;
        }
    }
    private static void waitForOcrModel(Context context, TextRecognizer recognizer) throws Exception {
        try {
            OcrModelProvisioner.ensureReady(new OcrModelProvisioner.Source(){
                private ModuleInstallClient client=ModuleInstall.getClient(context);
                public long generation(){
                    try{
                        android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo("com.google.android.gms",0);
                        return android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
                    }catch(android.content.pm.PackageManager.NameNotFoundException missing){return -1L;}
                }
                public void reconnect(){
                    client=ModuleInstall.getClient(context);
                    android.util.Log.i("MoneyMateOcr","Reconnecting model delivery; Play services version="+generation());
                }
                public boolean available(long timeout) throws Exception {
                    return moduleResult(client.areModulesAvailable(recognizer),timeout).areModulesAvailable();
                }
                public OcrModelProvisioner.Installation install(long timeout) throws Exception {
                    final ModuleInstallClient requestClient=client;
                    // A new listener per request keeps old-session callbacks from
                    // contaminating a retry after Play services replaces itself.
                    final AtomicReference<ModuleInstallStatusUpdate> latest=new AtomicReference<>();
                    final InstallStatusListener listener=latest::set;
                    try {
                        moduleResult(requestClient.installModules(ModuleInstallRequest.newBuilder()
                                .addApi(recognizer).setListener(listener).build()),timeout);
                    }catch(Exception failed){requestClient.unregisterListener(listener);throw failed;}
                    return new OcrModelProvisioner.Installation(){
                        public OcrModelProvisioner.Failure failure(){
                            ModuleInstallStatusUpdate update=latest.get();
                            if(update==null)return null;
                            if(update.getInstallState()==ModuleInstallStatusUpdate.InstallState.STATE_CANCELED)
                                return new OcrModelProvisioner.Failure(16,"OCR model installation was cancelled. No records were added.",null);
                            if(update.getInstallState()!=ModuleInstallStatusUpdate.InstallState.STATE_FAILED)return null;
                            int code=update.getErrorCode();
                            android.util.Log.w("MoneyMateOcr","Model install error: "+code+" "+ModuleInstallStatusCodes.getStatusCodeString(code)+", session="+update.getSessionId());
                            return new OcrModelProvisioner.Failure(code,"OCR model installation failed (code "+code+", "+ModuleInstallStatusCodes.getStatusCodeString(code)+", session "+update.getSessionId()+"). No records were added.",null);
                        }
                        public void close(){requestClient.unregisterListener(listener);}
                    };
                }
            },new OcrModelProvisioner.Clock(){
                public long now(){return SystemClock.elapsedRealtime();}
                public void sleep(long millis) throws InterruptedException {Thread.sleep(millis);}
            });
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OCR setup was interrupted. No records were added.",interrupted);
        }catch(OcrModelProvisioner.Failure failed){
            throw new IllegalStateException(failed.getMessage(),failed);
        }catch(java.util.concurrent.TimeoutException timeout){
            throw new IllegalStateException("OCR model is not ready yet. Keep an internet connection and retry the PDF import. No records were added.",timeout);
        }catch(Exception unavailable){
            throw new IllegalStateException("Google Play services OCR is unavailable. Check Google Play services and connect to the internet, then retry. No records were added.",unavailable);
        }
    }

    public static List<String> read(Context context, Uri uri) throws Exception {
        if(uri==null)throw new IllegalArgumentException("Choose a PDF statement");
        File privateCopy=File.createTempFile("bank-statement-",".pdf",context.getCacheDir());
        try {
            try(InputStream in=context.getContentResolver().openInputStream(uri);
                FileOutputStream out=new FileOutputStream(privateCopy)){
                if(in==null)throw new IllegalArgumentException("Could not read PDF");
                byte[] b=new byte[8192];int n;long size=0;
                while((n=in.read(b))!=-1){
                    size+=n;if(size>4_000_000)throw new IllegalArgumentException("PDF exceeds 4 MB");
                    out.write(b,0,n);
                }
                if(size<5)throw new IllegalArgumentException("PDF is empty");
            }
            try(java.io.RandomAccessFile verify=new java.io.RandomAccessFile(privateCopy,"r")) {
                byte[] header=new byte[4];verify.readFully(header);
                if(header[0]!='%'||header[1]!='P'||header[2]!='D'||header[3]!='F')
                    throw new IllegalArgumentException("This file is not a PDF");
            }
            TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(privateCopy,ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer=new PdfRenderer(fd)) {
                waitForOcrModel(context,recognizer);
                if(renderer.getPageCount()>8)throw new IllegalArgumentException("For safety import PDFs of eight pages or fewer");
                List<String> lines=new ArrayList<>();
                for(int i=0;i<renderer.getPageCount();i++) {
                    try(PdfRenderer.Page page=renderer.openPage(i)){
                        if(page.getWidth()<=0||page.getHeight()<=0)throw new IllegalArgumentException("Unsupported PDF page");
                        float scale=Math.min(2.4f,Math.min(1800f/page.getWidth(),2400f/page.getHeight()));
                        int w=Math.max(1,Math.round(page.getWidth()*scale)),h=Math.max(1,Math.round(page.getHeight()*scale));
                        Bitmap picture=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                        try{
                            picture.eraseColor(Color.WHITE);
                            page.render(picture,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                            Text result=Tasks.await(recognizer.process(InputImage.fromBitmap(picture,0)),45,TimeUnit.SECONDS);
                            List<Piece> pieces=new ArrayList<>();
                            for(Text.TextBlock block:result.getTextBlocks())
                                for(Text.Line line:block.getLines()){
                                    Rect r=line.getBoundingBox();
                                    if(r!=null&&!line.getText().trim().isEmpty())
                                        pieces.add(new Piece(r.left,r.top,line.getText().trim()));
                                }
                            pieces.sort(Comparator.<Piece>comparingInt(a->a.y).thenComparingInt(a->a.x));
                            List<List<Piece>> grouped=new ArrayList<>();
                            for(Piece piece:pieces){
                                List<Piece> row=null;
                                for(List<Piece> group:grouped)
                                    if(Math.abs(group.get(0).y-piece.y)<13){row=group;break;}
                                if(row==null){row=new ArrayList<>();grouped.add(row);}
                                row.add(piece);
                            }
                            for(List<Piece> group:grouped){
                                group.sort(Comparator.comparingInt(a->a.x));
                                StringBuilder text=new StringBuilder();
                                for(Piece item:group) {if(text.length()>0)text.append(' ');text.append(item.text);}
                                lines.add(text.toString());
                                if(lines.size()>6000)throw new IllegalArgumentException("PDF has too many lines");
                            }
                        }finally{picture.recycle();}
                    }
                }
                if(lines.isEmpty())throw new IllegalArgumentException("No text could be recognised on PDF pages");
                return lines;
            }finally{recognizer.close();}
        }finally{if(privateCopy.exists()&&!privateCopy.delete())privateCopy.deleteOnExit();}
    }
}
