package com.seafile.seadroid2.loopimages;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.DirentCache;
import com.seafile.seadroid2.data.SeafDirent;
import com.seafile.seadroid2.util.Utils;

import java.io.File;

class ImageInfo{
    private DirInfo dirInfo;
    private String filePath;
    private long fileSize;
    private static final int MAX_BITMAP_EDGE = 1024;

    public ImageInfo(DirInfo dirInfo, String filePath, long fileSize){
        this.dirInfo = dirInfo;
        this.filePath = filePath;
        this.fileSize = fileSize;
    }

    public DirInfo getDirInfo() {
        return dirInfo;
    }


    public long getFileSize(){
        return fileSize;
    }

    public String getFilePath(){
        return filePath;
    }

    public String getRemoteFilePath(){
        return Utils.pathJoin(getDirInfo().getDirPath(), Utils.fileNameFromPath(filePath));
    }

    public File getFile(){
        if(filePath == null){
            return null;
        }
        File file = new File(filePath);
        return file;
    }

    public boolean exist(){
        return getFile() != null;
    }

    public Bitmap getBitMap(){
        File file = getFile();
        if(file == null || !file.exists()){
            return null;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if(bitmap == null){
            return null;
        }
        if(bitmap.getHeight() > MAX_BITMAP_EDGE || bitmap.getWidth() > MAX_BITMAP_EDGE){
            float scale = MAX_BITMAP_EDGE/(float)Math.max(bitmap.getHeight(), bitmap.getWidth());
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
        return bitmap;
    }

    public boolean deleteStorage(){
        File file = getFile();
        if(file == null){
            return true;
        }
        return file.delete();
    }
}