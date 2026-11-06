package com.idris.ayahmarker;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * Audio folder: Documents/Ayahmarker
 *  - ensureFolder(): creates the folder (visible in the Files app) by putting a small README.txt in it
 *  - pickFolder():   the person chooses that folder ONCE; the permission is kept for good
 *  - listAudio():    lists the audio files inside it (name + content uri) every time the app opens
 *  - forgetFolder(): releases the folder
 */
@CapacitorPlugin(name = "AudioFolder")
public class AudioFolderPlugin extends Plugin {

    private static final String PREFS = "audio_folder";
    private static final String KEY_TREE = "tree_uri";
    private static final String FOLDER = "Ayahmarker";

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Creates Documents/Ayahmarker (Android 10+, no permission needed). */
    @PluginMethod
    public void ensureFolder(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("path", "Documents/" + FOLDER);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ret.put("created", false);
            ret.put("supported", false);
            call.resolve(ret);
            return;
        }
        try {
            ContentResolver resolver = getContext().getContentResolver();
            Uri files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            String rel = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER + "/";

            boolean exists = false;
            Cursor c = resolver.query(
                files,
                new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                new String[]{"README.txt", rel},
                null
            );
            if (c != null) {
                try { exists = c.moveToFirst(); } finally { c.close(); }
            }
            if (!exists) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, "README.txt");
                v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
                Uri uri = resolver.insert(files, v);
                if (uri != null) {
                    OutputStream os = resolver.openOutputStream(uri);
                    if (os != null) {
                        try {
                            os.write("Put the surah audio files here (for example 002.mp3 = Al-Baqarah).\n".getBytes("UTF-8"));
                            os.flush();
                        } finally {
                            os.close();
                        }
                    }
                }
            }
            ret.put("created", true);
            ret.put("supported", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage() == null ? "could not create the folder" : e.getMessage());
        }
    }

    /** Opens the system folder chooser (starting at Documents/Ayahmarker) and keeps the permission. */
    @PluginMethod
    public void pickFolder(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Uri initial = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Documents/" + FOLDER);
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial);
            } catch (Exception ignored) {
            }
        }
        startActivityForResult(call, intent, "pickFolderResult");
    }

    @ActivityCallback
    private void pickFolderResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result == null || result.getResultCode() != Activity.RESULT_OK
            || result.getData() == null || result.getData().getData() == null) {
            call.reject("cancelled");
            return;
        }
        Uri tree = result.getData().getData();
        try {
            getContext().getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            call.reject(e.getMessage() == null ? "could not keep the permission" : e.getMessage());
            return;
        }
        prefs().edit().putString(KEY_TREE, tree.toString()).apply();
        JSObject ret = new JSObject();
        ret.put("uri", tree.toString());
        call.resolve(ret);
    }

    /** Lists the audio files of the chosen folder. */
    @PluginMethod
    public void listAudio(PluginCall call) {
        JSObject ret = new JSObject();
        JSArray arr = new JSArray();
        String s = prefs().getString(KEY_TREE, null);
        if (s == null) {
            ret.put("hasFolder", false);
            ret.put("files", arr);
            call.resolve(ret);
            return;
        }
        try {
            ContentResolver cr = getContext().getContentResolver();
            Uri tree = Uri.parse(s);

            boolean held = false;
            List<UriPermission> perms = cr.getPersistedUriPermissions();
            for (UriPermission p : perms) {
                if (p.getUri().equals(tree) && p.isReadPermission()) { held = true; break; }
            }
            if (!held) {
                prefs().edit().remove(KEY_TREE).apply();
                ret.put("hasFolder", false);
                ret.put("files", arr);
                call.resolve(ret);
                return;
            }

            String treeId = DocumentsContract.getTreeDocumentId(tree);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
            Cursor c = cr.query(
                children,
                new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                },
                null, null, null
            );
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        String id = c.getString(0);
                        String name = c.getString(1);
                        String mime = c.getString(2);
                        long size = c.isNull(3) ? 0 : c.getLong(3);
                        if (name == null) continue;
                        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) continue;
                        String low = name.toLowerCase(Locale.ROOT);
                        boolean audio = (mime != null && mime.startsWith("audio/"))
                            || low.endsWith(".mp3") || low.endsWith(".m4a") || low.endsWith(".wav")
                            || low.endsWith(".aac") || low.endsWith(".ogg") || low.endsWith(".flac");
                        if (!audio) continue;
                        Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                        JSObject o = new JSObject();
                        o.put("name", name);
                        o.put("uri", doc.toString());
                        o.put("size", size);
                        arr.put(o);
                    }
                } finally {
                    c.close();
                }
            }
            ret.put("hasFolder", true);
            ret.put("files", arr);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage() == null ? "could not read the folder" : e.getMessage());
        }
    }

    /** Releases the chosen folder. */
    @PluginMethod
    public void forgetFolder(PluginCall call) {
        try {
            String s = prefs().getString(KEY_TREE, null);
            if (s != null) {
                try {
                    getContext().getContentResolver().releasePersistableUriPermission(
                        Uri.parse(s), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
            }
            prefs().edit().remove(KEY_TREE).apply();
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage() == null ? "failed" : e.getMessage());
        }
    }
}
