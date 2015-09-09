package com.ataw.cordova.plugin;

import org.apache.cordova.CordovaPlugin;
import android.net.Uri;

import java.io.File;
import java.lang.Override;

public class RemapPlugin extends CordovaPlugin {
    @Override
    public Uri remapUri(Uri uri) {
        String path = uri.getPath();
        String result =  map2StaticFile(path);
        if(result != null)
            return Uri.parse(result);
        return null;
    }

    private String map2StaticFile(String urlPath)
    {
        File rootPath = webView.getContext().getFilesDir();
        String newPath = new File(rootPath, "demoLib.js").getAbsolutePath();
        if(urlPath.contains("lib/demoLib.js"))
            return "file://"+ newPath;
        return null;
    }
}