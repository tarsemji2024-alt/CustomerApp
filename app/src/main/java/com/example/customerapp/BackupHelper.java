package com.example.customerapp;   // अपने package name से बदलें

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupHelper {

    private static final String DB_NAME = "customers.db";

    /** डेटाबेस + सारी आधार फ़ोटो एक ZIP में सेव करता है */
    public static void backup(Context ctx, DBHelper helper, Uri target) throws Exception {
        helper.close();   // डेटाबेस फ़ाइल सुरक्षित कॉपी के लिए बंद करें
        try (OutputStream os = ctx.getContentResolver().openOutputStream(target);
             ZipOutputStream zos = new ZipOutputStream(os)) {

            addFile(zos, ctx.getDatabasePath(DB_NAME), "db/" + DB_NAME);

            File picDir = ctx.getExternalFilesDir("Pictures");
            File[] pics = picDir != null ? picDir.listFiles() : null;
            if (pics != null) {
                for (File f : pics) addFile(zos, f, "pics/" + f.getName());
            }
        }
    }

    /** ZIP से डेटाबेस और फ़ोटो वापस लाता है (पुराना डेटा बदल जाएगा) */
    public static void restore(Context ctx, DBHelper helper, Uri source) throws Exception {
        helper.close();
        File picDir = ctx.getExternalFilesDir("Pictures");
        if (picDir != null && !picDir.exists()) picDir.mkdirs();

        try (InputStream is = ctx.getContentResolver().openInputStream(source);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                File out;
                if (e.getName().equals("db/" + DB_NAME)) {
                    out = ctx.getDatabasePath(DB_NAME);
                    new File(out.getPath() + "-journal").delete();
                } else if (e.getName().startsWith("pics/")) {
                    out = new File(picDir, new File(e.getName()).getName());
                } else {
                    continue;
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                }
            }
        }
        // फ़ोटो के रास्ते इस फ़ोन के हिसाब से ठीक करें
        helper.fixPhotoPaths(picDir.getAbsolutePath());
    }

    private static void addFile(ZipOutputStream zos, File f, String entryName) throws Exception {
        if (!f.exists()) return;
        zos.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) zos.write(buf, 0, n);
        }
        zos.closeEntry();
    }
}
