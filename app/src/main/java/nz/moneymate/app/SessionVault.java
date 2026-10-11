package nz.moneymate.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Tokens are encrypted using a non-exportable Android Keystore key, excluded from backup. */
final class SessionVault {
    private final AtomicFile file;
    SessionVault(Context context) {file=new AtomicFile(new File(context.getNoBackupFilesDir(),"auth-session"));}
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        String alias="MoneyMateAuthV1";
        if(!store.containsAlias(alias)) {
            KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (javax.crypto.SecretKey)store.getKey(alias,null);
    }
    String read() throws Exception {
        if(!file.getBaseFile().exists())return null;
        try(DataInputStream input=new DataInputStream(file.openRead())) {
            int size=input.readUnsignedByte();if(size!=12)throw new IOException("Invalid session");
            byte[] iv=new byte[size];input.readFully(iv);
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] block=new byte[4096];int count;
            while((count=input.read(block))!=-1){if(buffer.size()+count>65536)throw new IOException("Invalid session");buffer.write(block,0,count);}
            byte[] encrypted=buffer.toByteArray();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(encrypted),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    void write(String session) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(session.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        FileOutputStream output=file.startWrite();
        try {output.write(cipher.getIV().length);output.write(cipher.getIV());output.write(encrypted);file.finishWrite(output);}
        catch(Exception error){file.failWrite(output);throw error;}
    }
    void clear(){file.delete();}
}
