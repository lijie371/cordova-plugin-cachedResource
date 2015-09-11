package com.ataw.cordova.plugin;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class CacheResourceManifestConfig{
    public String version;
    public HashMap<String, String> maps;

    public CacheResourceManifestConfig(JSONObject fromObj)
    {
        version = fromObj.optString("version", "0.0.0.1");
        JSONObject files = fromObj.optJSONObject("files");
        maps = new HashMap<String, String>();
        Iterator<String> iterator = files.keys();
        while(iterator.hasNext())
        {
            String from = iterator.next();
            String to = files.optString(from);
            if(from != null && !from.isEmpty())
            {
                if(!maps.containsKey(from))
                    maps.put(from, to);
            }
        }
    }
}
