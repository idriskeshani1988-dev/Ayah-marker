package com.idris.ayahmarker;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.OutputStream;

@CapacitorPlugin(name = "SaveFile")
public class SaveFilePlugin extends Plugin {

    @PluginMethod
    public void saveToDownloads(PluginCall call) {
        String name = call.getString("name");
        String data = call.getString("data");
        String mime = call.getString("mime", "application/octet-stream");

        if (name == null || data == null) {
            call.reject("name and data are required");
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            call.reject("Downloads saving needs Android 10 or newer");
            return;
        }

        try {
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);
            ContentResolver resolver = getContext().getContentResolver();

            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, mime);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                call.reject("Could not create the file");
                return;
            }
            OutputStream os = resolver.openOutputStream(uri);
            if (os == null) {
                call.reject("Could not open the file");
                return;
            }
            try {
                os.write(bytes);
                os.flush();
            } finally {
                os.close();
            }

            JSObject ret = new JSObject();
            ret.put("path", "Download/" + name);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage() == null ? "save failed" : e.getMessage());
        }
    }
}
