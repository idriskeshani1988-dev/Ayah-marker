package com.idris.ayahmarker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * Audio folder in the MAIN storage list:  /storage/emulated/0/Ayah-marker
 * Needs the "All files access" permission (the person turns it on once in Settings).
 *  - status():        { supported, granted, path }
 *  - requestAccess(): opens the Settings page for this app, resolves { granted }
 *  - ensureFolder():  creates Ayah-marker when it is missing
 *  - listAudio():     { granted, files:[{name, path, size}] }
 *  - saveFile():      saves a base64 file (the db) into Ayah-marker
 */
@CapacitorPlugin(name = "AudioFolder")
public class AudioFolderPlugin extends Plugin {

    private static final String FOLDER = "Ayah-marker";

    private static boolean supported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    private static boolean granted() {
        return supported() && Environment.isExternalStorageManager();
    }

    private static File folder() {
        return new File(Environment.getExternalStorageDirectory(), FOLDER);
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("supported", supported());
        ret.put("granted", granted());
        ret.put("path", folder().getAbsolutePath());
        call.resolve(ret);
    }

    @PluginMethod
    public void requestAccess(PluginCall call) {
        if (!supported()) {
            call.reject("Android 11 or newer is needed");
            return;
        }
        if (granted()) {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
            return;
        }
        Context ctx = getContext();
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + ctx.getPackageName()));
        if (intent.resolveActivity(ctx.getPackageManager()) == null) {
            intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        }
        startActivityForResult(call, intent, "accessResult");
    }

    @ActivityCallback
    private void accessResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject ret = new JSObject();
        ret.put("granted", granted());
        call.resolve(ret);
    }

    @PluginMethod
    public void ensureFolder(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("path", folder().getAbsolutePath());
        if (!granted()) {
            ret.put("created", false);
            call.resolve(ret);
            return;
        }
        File f = folder();
        boolean ok = f.exists() || f.mkdirs();
        ret.put("created", ok);
        call.resolve(ret);
    }

    // saveFile({name, data(base64)}): writes /storage/emulated/0/Ayah-marker/<name> (replaces the file)
    @PluginMethod
    public void saveFile(PluginCall call) {
        String name = call.getString("name");
        String data = call.getString("data");
        if (name == null || data == null) {
            call.reject("name and data are needed");
            return;
        }
        if (!granted()) {
            call.reject("All files access is not allowed");
            return;
        }
        name = name.replaceAll("[\\/:*?\"<>|]", "_");
        File dir = folder();
        if (!dir.exists() && !dir.mkdirs()) {
            call.reject("Could not create " + dir.getAbsolutePath());
            return;
        }
        File out = new File(dir, name);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out, false);
            fos.write(Base64.decode(data, Base64.DEFAULT));
            fos.flush();
        } catch (Exception e) {
            call.reject("Could not save: " + e.getMessage());
            return;
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignore) {}
        }
        JSObject ret = new JSObject();
        ret.put("saved", true);
        ret.put("path", out.getAbsolutePath());
        call.resolve(ret);
    }

    @PluginMethod
    public void listAudio(PluginCall call) {
        JSObject ret = new JSObject();
        JSArray arr = new JSArray();
        ret.put("files", arr);
        if (!granted()) {
            ret.put("granted", false);
            call.resolve(ret);
            return;
        }
        ret.put("granted", true);
        File dir = folder();
        File[] list = dir.listFiles();
        if (list != null) {
            Arrays.sort(list, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            for (File f : list) {
                if (!f.isFile()) continue;
                String low = f.getName().toLowerCase(Locale.ROOT);
                boolean audio = low.endsWith(".mp3") || low.endsWith(".m4a") || low.endsWith(".wav")
                    || low.endsWith(".aac") || low.endsWith(".ogg") || low.endsWith(".flac");
                if (!audio) continue;
                JSObject o = new JSObject();
                o.put("name", f.getName());
                o.put("path", f.getAbsolutePath());
                o.put("size", f.length());
                arr.put(o);
            }
        }
        call.resolve(ret);
    }
}
