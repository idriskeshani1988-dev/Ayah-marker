package com.idris.ayahmarker;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.OutputStream;

@CapacitorPlugin(name = "SaveFile")
public class SaveFilePlugin extends Plugin {

    /** Writes bytes into the phone's Downloads folder (Android 10+, no permission needed). */
    static void saveToDownloadsFolder(Context context, String name, String base64, String mime) throws Exception {
        if (name == null || base64 == null) {
            throw new Exception("name and data are required");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw new Exception("Downloads saving needs Android 10 or newer");
        }
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        ContentResolver resolver = context.getContentResolver();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.MIME_TYPE, mime == null ? "application/octet-stream" : mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new Exception("Could not create the file");
        }
        OutputStream os = resolver.openOutputStream(uri);
        if (os == null) {
            throw new Exception("Could not open the file");
        }
        try {
            os.write(bytes);
            os.flush();
        } finally {
            os.close();
        }
    }


    /** Like saveToDownloadsFolder, but replaces the file of the same name if this app already created it. */
    static void saveOverwritingFolder(Context context, String name, String base64, String mime) throws Exception {
        if (name == null || base64 == null) {
            throw new Exception("name and data are required");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw new Exception("Downloads saving needs Android 10 or newer");
        }
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        ContentResolver resolver = context.getContentResolver();

        Uri existing = null;
        Cursor cursor = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            new String[]{MediaStore.Downloads._ID},
            MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + " LIKE ?",
            new String[]{name, Environment.DIRECTORY_DOWNLOADS + "%"},
            null
        );
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    existing = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0));
                }
            } finally {
                cursor.close();
            }
        }

        if (existing != null) {
            try {
                OutputStream os = resolver.openOutputStream(existing, "wt");
                if (os != null) {
                    try {
                        os.write(bytes);
                        os.flush();
                    } finally {
                        os.close();
                    }
                    return;
                }
            } catch (Exception ignored) {
                // could not rewrite it (for example it belongs to another install): create a new file below
            }
        }
        saveToDownloadsFolder(context, name, base64, mime);
    }

    /** Direct JavaScript bridge: window.AndroidSave.saveToDownloads(name, base64, mime) */
    public static class SaveBridge {
        private final Context context;

        public SaveBridge(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public String saveToDownloadsOverwrite(String name, String base64, String mime) {
            try {
                saveOverwritingFolder(context, name, base64, mime);
                return "ok";
            } catch (Exception e) {
                return "error: " + (e.getMessage() == null ? "save failed" : e.getMessage());
            }
        }

        @JavascriptInterface
        public String saveToDownloads(String name, String base64, String mime) {
            try {
                saveToDownloadsFolder(context, name, base64, mime);
                return "ok";
            } catch (Exception e) {
                return "error: " + (e.getMessage() == null ? "save failed" : e.getMessage());
            }
        }
    }

    @Override
    public void load() {
        try {
            getBridge().getWebView().addJavascriptInterface(new SaveBridge(getContext()), "AndroidSave");
        } catch (Exception ignored) {
        }
    }

    @PluginMethod
    public void saveToDownloads(PluginCall call) {
        try {
            saveToDownloadsFolder(
                getContext(),
                call.getString("name"),
                call.getString("data"),
                call.getString("mime", "application/octet-stream")
            );
            JSObject ret = new JSObject();
            ret.put("path", "Download/" + call.getString("name"));
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage() == null ? "save failed" : e.getMessage());
        }
    }
}
