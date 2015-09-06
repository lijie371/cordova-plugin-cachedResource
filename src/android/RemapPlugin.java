package com.ataw.cordova.remap;

import org.apache.cordova.CordovaPlugin;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.lang.Override;

public class RemapPlugin extends CordovaPlugin {
    @Override
    public Uri remapUri(Uri uri) {
        String path = uri.getPath();
        File basePath = Environment.getDataDirectory();
        String newPath = basePath.getAbsolutePath()+ File.pathSeparator+ "demoLib.js";
        if(path.contains("lib/demoLib.js"))
            return Uri.parse("file://"+ newPath);
        //return null;
        return super.remapUri(uri);
    }
}