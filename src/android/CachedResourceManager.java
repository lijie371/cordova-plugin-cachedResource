package com.ataw.cordova.plugin;

import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.CordovaWebView;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class CachedResourceManager {
    private static final int STATUS_STOPPED = 0;
    private static final int STATUS_DOWNLOADING = 1;
    private static final int STATUS_EXTRACTING = 2;
    private static final int STATUS_COMPLETE = 3;

    private static final String PROP_STATUS = "status";
    private static final String PROP_PROGRESS = "progress";
    private static final String PROP_LOADED = "loaded";
    private static final String PROP_TOTAL = "total";

    private static final String LOG_TAG = "CachedResourceManager";
    private static String CACHE_RESOUCES_DATA_PATH = "__CachedResource";
    private static String CACHE_RESOURCE_MANIFEST = CACHE_RESOUCES_DATA_PATH+ File.separator + "manifest.json";

    static CachedResourceManager fInstance;

    static synchronized  CachedResourceManager setAndGetInstance(CordovaWebView webView, String serviceUrl)
    {
        if(fInstance == null)
            fInstance = new CachedResourceManager(webView,serviceUrl);
        return fInstance;
    }

    private CordovaWebView fWebView;
    private CacheResourceManifestConfig fManifest;
    private String fRootPath;
    File fBaseDir;
    private File fManifestFile;
    private String fCachedResourceUpdateUrl;

    private CachedResourceManager(CordovaWebView webView, String cachedResourceUpdateUrl)
    {
        fWebView = webView;
        fRootPath = webView.getContext().getFilesDir().getAbsolutePath();
        fManifestFile = new File(fRootPath, CACHE_RESOURCE_MANIFEST);
        fBaseDir = fManifestFile.getParentFile();
        fCachedResourceUpdateUrl = cachedResourceUpdateUrl;
        init();
    }

    public void checkUpdate()
    {
        if(fCachedResourceUpdateUrl != null && !fCachedResourceUpdateUrl.isEmpty())
        {
            boolean hasNewestFile = downloadAndReplace(fCachedResourceUpdateUrl);
            if(hasNewestFile)
            {
                updateCachedResource();
            }
        }
    }

    private void init()
    {
        if(!isCached())
        {
            //从assets下拷贝__CachedResource.zip（如果有的话）
            try{
                InputStream srcStream = fWebView.getContext().getResources().getAssets().open("__CachedResource.zip");
                copyFile(srcStream, new FileOutputStream(new File(fRootPath, "__CachedResource.zip")));

                updateCachedResource();
            }
            catch (Exception e)
            {
            }
        }
    }

    private boolean isCached()
    {
        return fManifestFile.exists();
    }

    private void updateCachedResource()
    {
        //TODO:记住__CachedResource.zip和__CachedResource.bak,如果更新失败的话记得把__CachedResource.bak还原一下
        try{
            File srcFile = new File(fRootPath, "__CachedResource.zip");
            if(srcFile.exists())
            {
                removeFolder(fBaseDir, false);
                unZip(srcFile, fBaseDir.getAbsolutePath());
                fManifest = null;
            }
        }
        catch (Exception e)
        {
        }
    }

    public CacheResourceManifestConfig getManifest()
    {
        if(fManifest == null)
        {
            synchronized (this){
                if(fManifest == null)
                {
                    //load from file
                    if(fManifestFile.exists())
                    {
                        try{
                            InputStream inputStream = new FileInputStream(fManifestFile);
                            int size = inputStream.available();
                            byte[] bytes = new byte[size];
                            inputStream.read(bytes);
                            inputStream.close();
                            String jsonString = new String(bytes, "UTF-8");
                            fManifest = new CacheResourceManifestConfig(new JSONObject(jsonString));
                        }
                        catch(Exception e){
                        }
                    }
                }
            }
        }
        return fManifest;
    }

    private Uri getUriForArg(String arg) {
        Uri tmpTarget = Uri.parse(arg);
        return fWebView.getResourceApi().remapUri(
                tmpTarget.getScheme() != null ? tmpTarget : Uri.fromFile(new File(arg)));
    }

    private boolean downloadAndReplace(String severUrl)
    {
        return false;
    }

    private boolean unZip(File srcFile, String targetDir) throws Exception
    {
        return doUnZip(srcFile, targetDir, new ProgressEvent());
    }

    private boolean doUnZip(File targetFile, String outputDirectory, ProgressEvent progress) throws Exception {
        Log.d(LOG_TAG, "unzipSync called");
        Log.d(LOG_TAG, "zip = " + targetFile.getAbsolutePath());
        InputStream inputStream = null;
        ZipFile zip = null;
        boolean anyEntries = false;
        try {
            synchronized (progress) {
                if (progress.isAborted()) {
                    return false;
                }
            }

            zip = new ZipFile(targetFile);

            Uri zipUri = getUriForArg(targetFile.getAbsolutePath());
            //Uri outputUri = getUriForArg(outputDirectory);

            CordovaResourceApi resourceApi = fWebView.getResourceApi();

            File tempFile = targetFile;
            if (tempFile == null || !tempFile.exists()) {
                throw new Exception("Zip文件不存在！");
            }

            File outputDir = new File(outputDirectory);//resourceApi.mapUriToFile(outputUri);
            //outputDirectory = outputDir.getAbsolutePath();
            outputDirectory += outputDirectory.endsWith(File.separator) ? "" : File.separator;
            if (outputDir == null || (!outputDir.exists() && !outputDir.mkdirs())){
                throw new Exception("文件夹创建失败！");
            }

            CordovaResourceApi.OpenForReadResult zipFile = resourceApi.openForRead(zipUri, true);
            progress.setStatus(STATUS_EXTRACTING);
            progress.setLoaded(0);
            progress.setTotal(zip.size());
            Log.d(LOG_TAG, "zip file len = " + zip.size());

            inputStream = new BufferedInputStream(zipFile.inputStream);
            inputStream.mark(10);
            int magic = readInt(inputStream);

            if (magic != 875721283) { // CRX identifier
                inputStream.reset();
            } else {
                // CRX files contain a header. This header consists of:
                //  * 4 bytes of magic number
                //  * 4 bytes of CRX format version,
                //  * 4 bytes of public key length
                //  * 4 bytes of signature length
                //  * the public key
                //  * the signature
                // and then the ordinary zip data follows. We skip over the header before creating the ZipInputStream.
                readInt(inputStream); // version == 2.
                int pubkeyLength = readInt(inputStream);
                int signatureLength = readInt(inputStream);

                inputStream.skip(pubkeyLength + signatureLength);
            }

            // The inputstream is now pointing at the start of the actual zip file content.
            ZipInputStream zis = new ZipInputStream(inputStream);
            inputStream = zis;

            ZipEntry ze;
            byte[] buffer = new byte[32 * 1024];

            while ((ze = zis.getNextEntry()) != null) {
                synchronized (progress) {
                    if (progress.isAborted()) {
                        return false;
                    }
                }

                anyEntries = true;
                String compressedName = ze.getName();

                if (ze.getSize() > getFreeSpace()) {
                    return false;
                }

                if (ze.isDirectory()) {
                    File dir = new File(outputDirectory + compressedName);
                    dir.mkdirs();
                } else {
                    File file = new File(outputDirectory + compressedName);
                    file.getParentFile().mkdirs();
                    if(file.exists() || file.createNewFile()){
                        Log.w(LOG_TAG, "extracting: " + file.getPath());
                        FileOutputStream fout = new FileOutputStream(file);
                        int count;
                        while ((count = zis.read(buffer)) != -1)
                        {
                            fout.write(buffer, 0, count);
                        }
                        fout.close();
                    }

                }
                progress.addLoaded(1);
                updateProgress(progress);
                zis.closeEntry();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            throw e;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
            }
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException e) {
                }
            }
        }

        if (anyEntries)
            return true;
        else
            return false;
    }

    private static int readInt(InputStream is) throws IOException {
        int a = is.read();
        int b = is.read();
        int c = is.read();
        int d = is.read();
        return a | b << 8 | c << 16 | d << 24;
    }

    private long getFreeSpace() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return availableBlocks * blockSize;
    }

    private void updateProgress(ProgressEvent progress) {
        try {
            if (progress.getLoaded() != progress.getTotal() || progress.getStatus() == STATUS_COMPLETE) {
                //TODO:进度处理下
                //PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, progress.toJSONObject());
                //pluginResult.setKeepCallback(true);
                //callbackContext.sendPluginResult(pluginResult);
            }
        } catch(Exception e){
            // never happens
        }
    }

    private static class ProgressEvent {
        private long loaded;
        private long total;
        private double percentage;
        private int status;
        private boolean aborted;
        private File targetFile;
        public ProgressEvent() {
            this.status = STATUS_STOPPED;
        }
        public long getLoaded() {
            return loaded;
        }
        public void setLoaded(long loaded) {
            this.loaded = loaded;
            updatePercentage();
        }
        public void addLoaded(long add) {
            this.loaded += add;
            updatePercentage();
        }
        public long getTotal() {
            return total;
        }
        public void setTotal(long total) {
            this.total = total;
            updatePercentage();
        }
        public int getStatus() {
            return status;
        }
        public void setStatus(int status) {
            this.status = status;
        }
        public boolean isAborted() {
            return aborted;
        }
        public void setAborted(boolean aborted) {
            this.aborted = aborted;
        }
        public File getTargetFile() {
            return targetFile;
        }
        public void setTargetFile(File targetFile) {
            this.targetFile = targetFile;
        }
        public JSONObject toJSONObject() throws JSONException {
            JSONObject jsonProgress = new JSONObject();
            jsonProgress.put(PROP_PROGRESS, this.percentage);
            jsonProgress.put(PROP_STATUS, this.getStatus());
            jsonProgress.put(PROP_LOADED, this.getLoaded());
            jsonProgress.put(PROP_TOTAL, this.getTotal());
            return jsonProgress;

        }
        private void updatePercentage() {
            double loaded = this.getLoaded();
            double total = this.getTotal();
            this.percentage = Math.floor((loaded / total * 100) / 2);
            if (this.getStatus() == STATUS_EXTRACTING) {
                this.percentage += 50;
            }
        }
    }

    private void removeFolder(File directory, boolean deleteSelf) {
        if (directory.exists() && directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                removeFolder(file, true);
            }
        }
        if(deleteSelf)
            directory.delete();
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[4096];

        int length;
        //copy the file content in bytes
        while ((length = in.read(buffer)) > 0){
            out.write(buffer, 0, length);
        }

        in.close();
        out.close();
    }
}

