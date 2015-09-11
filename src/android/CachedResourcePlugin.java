package com.ataw.cordova.plugin;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;

import android.net.Uri;

import java.io.File;
import java.lang.Override;
import java.util.HashMap;
import java.util.Map;

public class CachedResourcePlugin extends CordovaPlugin {
    private CachedResourceManager fManager;
    private HashMap<String,String> fBaseMaps;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        fBaseMaps = new HashMap<String, String>();
        fBaseMaps.put("cordova.js", "/android_assert/cordova.js");

        fManager = CachedResourceManager.setAndGetInstance(
                webView,preferences.getString("cachedResourceUpdateUrl", ""));
        fManager.checkUpdate();
    }

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
        String localFile = getFromMap(fBaseMaps, urlPath);
        if(localFile != null && !localFile.isEmpty())
            return localFile;

        CacheResourceManifestConfig manifest = fManager.getManifest();
        if(manifest!= null)
        {
            localFile = getFromMap(manifest.maps, urlPath);
            if(localFile != null && !localFile.isEmpty())
                return localFile;
        }
        return null;
    }

    private String getFromMap(HashMap<String,String> map, String urlPath)
    {
        if(map == null)
            return null;
        for (Map.Entry<String,String> entry: map.entrySet()) {
            if(urlPath.toLowerCase().contains(entry.getKey().toLowerCase()))
            {
                String value = entry.getValue();
                if(value.startsWith("/android_asset/"))
                {
                    return "file://"+ value;
                }
                else
                {
                    File localFile = new File(fManager.fBaseDir, value);
                    if(localFile.exists())
                    {
                        String newPath = localFile.getAbsolutePath();
                        return "file://"+ newPath;
                    }
                }
            }
        }
        return null;
    }
}