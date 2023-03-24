package com.seafile.seadroid2.loopimages;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.common.collect.Lists;
import com.seafile.seadroid2.SeadroidApplication;
import com.seafile.seadroid2.gallery.Image;

import java.io.File;
import java.util.List;

public class LoopImagesDBHelper extends SQLiteOpenHelper {
    private static final String DEBUG_TAG = "LoopImagesDBHelper";
    private static LoopImagesDBHelper dbHelper = null;
    public static final int DATABASE_VERSION = 3;
    public static final String DATABASE_NAME = "LoopImagesUpload.db";

    private SQLiteDatabase database;

    private LoopImagesDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createAllTables(db);
    }

    public static synchronized LoopImagesDBHelper getInstance() {
        if(dbHelper == null) {
            dbHelper = new LoopImagesDBHelper(SeadroidApplication.getAppContext());
            dbHelper.database = dbHelper.getWritableDatabase();
//            dbHelper.createAllTables(dbHelper.database);
        }
        return dbHelper;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        dropAllTables(db);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    private void dropAllTables(SQLiteDatabase db){
        dropWidgetImageTable(db);
        dropImageInfoTable(db);
        dropDirInfoTable(db);
    }

    private void createAllTables(SQLiteDatabase db){
//        dropAllTables(db);
        createDirInfoTable(db);
        createImageInfoTable(db);
        createWidgetImageTable(db);
    }

    // If you change the database schema, you must increment the database
    // version.

    // dir info table
    private static final String DIR_INFO_TABLE_NAME = "DirInfo";
    private static final String DIR_INFO_COLUMN_ID = "id";
    private static final String DIR_INFO_COLUMN_ACCOUNT_SIGNATURE = "account_signature";
    private static final String DIR_INFO_COLUMN_REPO_ID = "repo_id";
    private static final String DIR_INFO_COLUMN_REPO_NAME = "repo_name";
    private static final String DIR_INFO_COLUMN_DIR_ID = "dir_id";
    private static final String DIR_INFO_COLUMN_DIR_PATH = "dir_path";
    private static final String[] DirInfoProjection = {
            DIR_INFO_COLUMN_ID,
            DIR_INFO_COLUMN_ACCOUNT_SIGNATURE,
            DIR_INFO_COLUMN_REPO_ID,
            DIR_INFO_COLUMN_REPO_NAME,
            DIR_INFO_COLUMN_DIR_ID,
            DIR_INFO_COLUMN_DIR_PATH
    };

    private void createDirInfoTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + DIR_INFO_TABLE_NAME + " ("
                + DIR_INFO_COLUMN_ID + " INTEGER PRIMARY KEY, "
                + DIR_INFO_COLUMN_ACCOUNT_SIGNATURE + " TEXT NOT NULL, "
                + DIR_INFO_COLUMN_REPO_ID + " TEXT NOT NULL, "
                + DIR_INFO_COLUMN_REPO_NAME + " TEXT NOT NULL, "
                + DIR_INFO_COLUMN_DIR_ID + " TEXT NOT NULL, "
                + DIR_INFO_COLUMN_DIR_PATH + " TEXT NOT NULL)");
        db.execSQL("CREATE INDEX " + DIR_INFO_COLUMN_ACCOUNT_SIGNATURE + "_dir_index ON " + DIR_INFO_TABLE_NAME
                + " (" + DIR_INFO_COLUMN_ACCOUNT_SIGNATURE + ");");
        db.execSQL("CREATE INDEX " + DIR_INFO_COLUMN_REPO_ID + "_dir_index ON " + DIR_INFO_TABLE_NAME
                + " (" + DIR_INFO_COLUMN_REPO_ID + ");");
        db.execSQL("CREATE INDEX " + DIR_INFO_COLUMN_REPO_NAME + "_dir_index ON " + DIR_INFO_TABLE_NAME
                + " (" + DIR_INFO_COLUMN_REPO_NAME + ");");
        db.execSQL("CREATE INDEX " + DIR_INFO_COLUMN_DIR_ID + "_dir_index ON " + DIR_INFO_TABLE_NAME
                + " (" + DIR_INFO_COLUMN_DIR_ID + ");");
        db.execSQL("CREATE INDEX " + DIR_INFO_COLUMN_DIR_PATH + "_dir_index ON " + DIR_INFO_TABLE_NAME
                + " (" + DIR_INFO_COLUMN_DIR_PATH + ");");
    }

    private void dropDirInfoTable(SQLiteDatabase db){
        db.execSQL("DROP TABLE IF EXISTS " + DIR_INFO_TABLE_NAME + ";");
    }

    public String getDirID(String accountSignature, String repoID, String dirPath){
        Cursor c = database.query(
                DIR_INFO_TABLE_NAME,
                DirInfoProjection,
                DIR_INFO_COLUMN_ACCOUNT_SIGNATURE + " = ? and " + DIR_INFO_COLUMN_REPO_ID + " = ? and " + DIR_INFO_COLUMN_DIR_PATH + " = ?" ,
                new String[] {accountSignature, repoID, dirPath },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );
        if(c.getCount() == 0){
            c.close();
            return null;
        }
        c.moveToNext();
        String dirID = c.getString(c.getColumnIndex(DIR_INFO_COLUMN_DIR_ID));
        c.close();
        return dirID;
    }

    public int getDirInfoID(String accountSignature, String repoID, String dirID){
        Cursor c = database.query(
                DIR_INFO_TABLE_NAME,
                DirInfoProjection,
                DIR_INFO_COLUMN_ACCOUNT_SIGNATURE + " = ? and " + DIR_INFO_COLUMN_REPO_ID + " = ? and " + DIR_INFO_COLUMN_DIR_ID + " = ?" ,
                new String[] {accountSignature, repoID, dirID },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );
        if(c.getCount() == 0){
            c.close();
            return -1;
        }
        c.moveToNext();
        int id = c.getInt(c.getColumnIndex(DIR_INFO_COLUMN_ID));
        c.close();
        return id;
    }

    public int addDirInfo(String accountSignature, String repoID, String repoName, String dirID, String dirPath){
        int id = getDirInfoID(accountSignature, repoID, dirID);
        if(id < 0){
            ContentValues value = new ContentValues();
            value.put(DIR_INFO_COLUMN_ACCOUNT_SIGNATURE, accountSignature);
            value.put(DIR_INFO_COLUMN_REPO_ID, repoID);
            value.put(DIR_INFO_COLUMN_REPO_NAME, repoName);
            value.put(DIR_INFO_COLUMN_DIR_ID, dirID);
            value.put(DIR_INFO_COLUMN_DIR_PATH, dirPath);
            database.insert(DIR_INFO_TABLE_NAME, null, value);
            id = getDirInfoID(accountSignature, repoID, dirID);
        }
        return id;
    }

    private void deleteDirInfo(int id){
        database.delete(DIR_INFO_TABLE_NAME, DIR_INFO_COLUMN_ID + " = ? ", new String[]{Integer.toString(id)});
    }

    private DirInfo getDirInfo(int id){
        Cursor c = database.query(
                DIR_INFO_TABLE_NAME,
                DirInfoProjection,
                DIR_INFO_COLUMN_ID + " = ? ",
                new String[] { Integer.toString(id) },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );
        if(c.getCount() == 0){
            c.close();
            return null;
        }
        c.moveToNext();
        String accountSignature = c.getString(c.getColumnIndex(DIR_INFO_COLUMN_ACCOUNT_SIGNATURE));
        String repoID = c.getString(c.getColumnIndex(DIR_INFO_COLUMN_REPO_ID));
        String repoName = c.getString(c.getColumnIndex(DIR_INFO_COLUMN_REPO_NAME));
        String dirID = c.getString(c.getColumnIndex(DIR_INFO_COLUMN_DIR_ID));
        String dirPath = c.getString(c.getColumnIndex(DIR_INFO_COLUMN_DIR_PATH));
        DirInfo dirInfo = new DirInfo(accountSignature, repoID, repoName, dirID, dirPath);
        c.close();
        return dirInfo;
    }

    // image info table
    private static final String IMAGE_INFO_TABLE_NAME = "ImageInfo";
    private static final String IMAGE_INFO_COLUMN_ID = "id";
    private static final String IMAGE_INFO_COLUMN_DIR_INFO_ID = "dir_info_id";
    private static final String IMAGE_INFO_COLUMN_FILE_PATH = "file_path";
    private static final String IMAGE_INFO_COLUMN_FILE_SIZE = "file_size";
    private static final String IMAGE_INFO_COLUMN_DOWNLOADED = "downloaded";
    private static final String[] ImageInfoProjection = {
            IMAGE_INFO_COLUMN_ID,
            IMAGE_INFO_COLUMN_DIR_INFO_ID,
            IMAGE_INFO_COLUMN_FILE_PATH,
            IMAGE_INFO_COLUMN_FILE_SIZE,
            IMAGE_INFO_COLUMN_DOWNLOADED,
    };

    private void createImageInfoTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + IMAGE_INFO_TABLE_NAME + " ("
                + IMAGE_INFO_COLUMN_ID + " INTEGER PRIMARY KEY, "
                + IMAGE_INFO_COLUMN_DIR_INFO_ID + " TEXT NOT NULL, "
                + IMAGE_INFO_COLUMN_FILE_PATH + " TEXT NOT NULL, "
                + IMAGE_INFO_COLUMN_FILE_SIZE + " BIGINT NOT NULL, "
                + IMAGE_INFO_COLUMN_DOWNLOADED + " INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX " + IMAGE_INFO_COLUMN_DIR_INFO_ID + "_image_index ON " + IMAGE_INFO_TABLE_NAME
                + " (" + IMAGE_INFO_COLUMN_DIR_INFO_ID + ");");
        db.execSQL("CREATE INDEX " + IMAGE_INFO_COLUMN_FILE_PATH + "_image_index ON " + IMAGE_INFO_TABLE_NAME
                + " (" + IMAGE_INFO_COLUMN_FILE_PATH + ");");
        db.execSQL("CREATE INDEX " + IMAGE_INFO_COLUMN_DOWNLOADED + "_image_index ON " + IMAGE_INFO_TABLE_NAME
                + " (" + IMAGE_INFO_COLUMN_DOWNLOADED + ");");
    }

    private class ImageDBInfo {
        int id;
        int dirInfoID;
        String filePath;
        long fileSize;
        int downloaded;
    }

    private void dropImageInfoTable(SQLiteDatabase db){
        db.execSQL("DROP TABLE IF EXISTS " + IMAGE_INFO_TABLE_NAME + ";");
    }

    private int getImageID(int dirInfoId, String filePath){
        Cursor c = database.query(
                IMAGE_INFO_TABLE_NAME,
                ImageInfoProjection,
                IMAGE_INFO_COLUMN_DIR_INFO_ID + " = ? and " + IMAGE_INFO_COLUMN_FILE_PATH + " = ? " ,
                new String[] { Integer.toString(dirInfoId), filePath },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );
        if(c.getCount() == 0){
            c.close();
            return -1;
        }
        c.moveToNext();
        int id = c.getInt(c.getColumnIndex(IMAGE_INFO_COLUMN_ID));
        c.close();
        return id;
    }

    private ImageDBInfo getImageInfo(int id){
        Cursor c = database.query(
                IMAGE_INFO_TABLE_NAME,
                ImageInfoProjection,
                IMAGE_INFO_COLUMN_ID + " = ? ",
                new String[] { Integer.toString(id)},
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );
        if(c.getCount() == 0){
            c.close();
            return null;
        }
        c.moveToNext();
        ImageDBInfo imageInfo = new ImageDBInfo();
        imageInfo.id = c.getInt(c.getColumnIndex(IMAGE_INFO_COLUMN_ID));
        imageInfo.dirInfoID = c.getInt(c.getColumnIndex(IMAGE_INFO_COLUMN_DIR_INFO_ID));
        imageInfo.filePath = c.getString(c.getColumnIndex(IMAGE_INFO_COLUMN_FILE_PATH));
        imageInfo.fileSize = c.getLong(c.getColumnIndex(IMAGE_INFO_COLUMN_FILE_SIZE));
        imageInfo.downloaded = c.getInt(c.getColumnIndex(IMAGE_INFO_COLUMN_DOWNLOADED));
        c.close();
        return imageInfo;
    }

    private ImageDBInfo getImageInfo(int dirInfoID, String filePath){
        Cursor c = database.query(
                IMAGE_INFO_TABLE_NAME,
                ImageInfoProjection,
                IMAGE_INFO_COLUMN_DIR_INFO_ID + " = ? and " + IMAGE_INFO_COLUMN_FILE_PATH + " = ? ",
                new String[] { Integer.toString(dirInfoID), filePath},
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );
        if(c.getCount() == 0){
            c.close();
            return null;
        }
        c.moveToNext();
        ImageDBInfo imageInfo = new ImageDBInfo();
        imageInfo.id = c.getInt(c.getColumnIndex(IMAGE_INFO_COLUMN_ID));
        imageInfo.dirInfoID = c.getInt(c.getColumnIndex(IMAGE_INFO_COLUMN_DIR_INFO_ID));
        imageInfo.filePath = c.getString(c.getColumnIndex(IMAGE_INFO_COLUMN_FILE_PATH));
        imageInfo.fileSize = c.getLong(c.getColumnIndex(IMAGE_INFO_COLUMN_FILE_SIZE));
        imageInfo.downloaded = c.getInt(c.getColumnIndex(IMAGE_INFO_COLUMN_DOWNLOADED));
        c.close();
        return imageInfo;
    }

    private List<ImageDBInfo> getDirImageInfo(int dirInfoID){
        Cursor c = database.query(
                IMAGE_INFO_TABLE_NAME,
                ImageInfoProjection,
                IMAGE_INFO_COLUMN_DIR_INFO_ID + " = ? ",
                new String[] { Integer.toString(dirInfoID)},
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );
        if(c.getCount() == 0){
            c.close();
            return null;
        }
        List<ImageDBInfo> res = Lists.newArrayList();
        while(c.moveToNext()) {
            ImageDBInfo imageInfo = new ImageDBInfo();
            imageInfo.id = c.getInt(c.getColumnIndex(IMAGE_INFO_COLUMN_ID));
            imageInfo.dirInfoID = c.getInt(c.getColumnIndex(IMAGE_INFO_COLUMN_DIR_INFO_ID));
            imageInfo.filePath = c.getString(c.getColumnIndex(IMAGE_INFO_COLUMN_FILE_PATH));
            imageInfo.fileSize = c.getLong(c.getColumnIndex(IMAGE_INFO_COLUMN_FILE_SIZE));
            imageInfo.downloaded = c.getInt(c.getColumnIndex(IMAGE_INFO_COLUMN_DOWNLOADED));
            res.add(imageInfo);
        }
        c.close();
        return res;
    }

//    private void addImage(String accountSignature, String repoID, String repoName, String dirID, String dirPath,  String filePath, long fileSize) {
//        addDirInfo(accountSignature, repoID, repoName, dirID, dirPath);
//        int dirInfoId = getDirInfoID(accountSignature, repoID, repoName);
//        if(dirInfoId < 0){
//            return;
//        }
//        addImage(dirInfoId, filePath, fileSize);
//    }
//    public int addImage(String accountSignature, String repoID, String repoName, String dirID, String dirPath, String filePath, long fileSize) {
//        int dirInfoID = getDirInfoID(accountSignature, repoID, dirID);
//        if(dirInfoID < 0){
//            addDirInfo(accountSignature, repoID, repoName, dirID, dirPath);
//            dirInfoID = getDirInfoID(accountSignature, repoID, dirID);
//        }
//        return addImage(dirInfoID, filePath, fileSize);
//    }

    public int addImage(int dirInfoID, String filePath, long fileSize){
        ImageDBInfo imageInfo = getImageInfo(dirInfoID, filePath);
        if(imageInfo == null) {
            ContentValues values = new ContentValues();
            values.put(IMAGE_INFO_COLUMN_DIR_INFO_ID, dirInfoID);
            values.put(IMAGE_INFO_COLUMN_FILE_PATH, filePath);
            values.put(IMAGE_INFO_COLUMN_FILE_SIZE, fileSize);
            database.insert(IMAGE_INFO_TABLE_NAME, null, values);
            imageInfo = getImageInfo(dirInfoID, filePath);
        }
        if(imageInfo == null){
            return -1;
        }
        return imageInfo.id;
    }


    private void deleteImageStorage(int imageID) {
        ImageDBInfo imageInfo = getImageInfo(imageID);
        setImageDownloadState(imageID, false);
        if(imageInfo == null) {
            return;
        }else {
            File file = new File(imageInfo.filePath);
            file.delete();
        }
    }

    private void deleteDir(int dirInfoID){
        List<ImageDBInfo> images = getDirImageInfo(dirInfoID);
        if(images != null){
            for(ImageDBInfo image : images){
                if(image == null){
                    continue;
                }
                if(image.downloaded > 0) {
                    File file = new File(image.filePath);
                    file.delete();
                }
            }
        }
        database.delete(IMAGE_INFO_TABLE_NAME, IMAGE_INFO_COLUMN_DIR_INFO_ID + " = ?" , new String[]{Integer.toString(dirInfoID)});
        deleteDirInfo(dirInfoID);
    }

    private void deleteUnusedDirs(List<Integer> usedDirInfoIDs){
        String sql = "";
        if(usedDirInfoIDs == null || usedDirInfoIDs.isEmpty()){
            sql = "select distinct " + DIR_INFO_COLUMN_ID + " from " + DIR_INFO_TABLE_NAME;
        }else{
            StringBuilder sb = new StringBuilder();
            boolean start = true;
            for(Integer dirInfoID: usedDirInfoIDs){
                if(start){
                    start = false;
                }else{
                    sb.append(",");
                }
                sb.append(dirInfoID);
            }
            sql = "select distinct " + DIR_INFO_COLUMN_ID + " from " + DIR_INFO_TABLE_NAME + " where " + DIR_INFO_COLUMN_ID + " not in (" + sb.toString()+");";
        }
        Cursor c = database.rawQuery(sql, null);
        if(c.getCount() == 0){
            return;
        }
        while(c.moveToNext()){
            int dirInfoID = c.getInt(c.getColumnIndex(DIR_INFO_COLUMN_ID));
            deleteDir(dirInfoID);
        }
        c.close();
    }

    public void setImageDownloadState(String accountSignature, String repoID, String dirID, String filePath, boolean downloaded){
        int dirInfoID = getDirInfoID(accountSignature, repoID, dirID);
        if(dirInfoID < 0){
            return;
        }
        ContentValues value = new ContentValues();
        value.put(IMAGE_INFO_COLUMN_DOWNLOADED, downloaded?1:0);
        database.update(IMAGE_INFO_TABLE_NAME, value,  IMAGE_INFO_COLUMN_DIR_INFO_ID + " = ? and " + IMAGE_INFO_COLUMN_FILE_PATH + " = ?", new String[]{Integer.toString(dirInfoID), filePath});
    }

    public void setImageDownloadState(int imageID, boolean downloaded){
        ContentValues value = new ContentValues();
        value.put(IMAGE_INFO_COLUMN_DOWNLOADED, downloaded?1:0);
        database.update(IMAGE_INFO_TABLE_NAME, value,  IMAGE_INFO_COLUMN_ID + " = ? ", new String[]{Integer.toString(imageID)});
    }

    // --------------------------------------------------------------------------------
    // --------------------------------------------------------------------------------
    // --------------------------------------------------------------------------------
    // --------------------------------------------------------------------------------
    // widget table
    private static final String WIDGET_IMAGE_TABLE_NAME = "WidgetImage";
    private static final String WIDGET_IMAGE_COLUMN_ID = "id";
    private static final String WIDGET_IMAGE_COLUMN_WIDGET_ID = "widget_id";
    private static final String WIDGET_IMAGE_COLUMN_DIR_INFO_ID = "dir_info_id";
    private static final String WIDGET_IMAGE_COLUMN_IMAGE_ID = "image_id";
    private static final String WIDGET_IMAGE_COLUMN_ACCESS_COUNT = "access_count";
    private static final String[] WidgetImageProjection = {
            WIDGET_IMAGE_COLUMN_ID,
            WIDGET_IMAGE_COLUMN_WIDGET_ID,
            WIDGET_IMAGE_COLUMN_DIR_INFO_ID,
            WIDGET_IMAGE_COLUMN_IMAGE_ID,
            WIDGET_IMAGE_COLUMN_ACCESS_COUNT
    };

    private void createWidgetImageTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + WIDGET_IMAGE_TABLE_NAME + " ("
                + WIDGET_IMAGE_COLUMN_ID + " INTEGER PRIMARY KEY, "
                + WIDGET_IMAGE_COLUMN_WIDGET_ID + " INTEGER NOT NULL, "
                + WIDGET_IMAGE_COLUMN_DIR_INFO_ID + " INTEGER NOT NULL, "
                + WIDGET_IMAGE_COLUMN_IMAGE_ID + " INTEGER NOT NULL, "
                + WIDGET_IMAGE_COLUMN_ACCESS_COUNT + " INTEGER NOT NULL DEFAULT 0, "
                + "FOREIGN KEY (" + WIDGET_IMAGE_COLUMN_IMAGE_ID + ") REFERENCES " + IMAGE_INFO_TABLE_NAME + "(" + IMAGE_INFO_COLUMN_ID + "), "
                + "FOREIGN KEY (" + WIDGET_IMAGE_COLUMN_DIR_INFO_ID + ") REFERENCES " + DIR_INFO_TABLE_NAME + "(" + DIR_INFO_COLUMN_ID + "))");

        db.execSQL("CREATE INDEX " + WIDGET_IMAGE_COLUMN_WIDGET_ID + "_widget_index ON " + WIDGET_IMAGE_TABLE_NAME
                + " (" + WIDGET_IMAGE_COLUMN_WIDGET_ID + ");");
        db.execSQL("CREATE INDEX " + WIDGET_IMAGE_COLUMN_DIR_INFO_ID + "_widget_index ON " + WIDGET_IMAGE_TABLE_NAME
                + " (" + WIDGET_IMAGE_COLUMN_DIR_INFO_ID + ");");
        db.execSQL("CREATE INDEX " + WIDGET_IMAGE_COLUMN_IMAGE_ID + "_widget_index ON " + WIDGET_IMAGE_TABLE_NAME
                + " (" + WIDGET_IMAGE_COLUMN_IMAGE_ID + ");");
    }

    private void dropWidgetImageTable(SQLiteDatabase db){
        db.execSQL("DROP TABLE IF EXISTS " + WIDGET_IMAGE_TABLE_NAME + ";");
    }

    private class WidgetImage{
        int id;
        int widgetID;
        int imageID;
        int dirInfoID;
        int accessCount;
    }

    public int getWidgetImageID(int widgetID, int dirInfoID, int fileID){
        Cursor c = database.query(
                WIDGET_IMAGE_TABLE_NAME,
                WidgetImageProjection,
                WIDGET_IMAGE_COLUMN_WIDGET_ID + " = ? and " + WIDGET_IMAGE_COLUMN_DIR_INFO_ID + " = ? and " + WIDGET_IMAGE_COLUMN_IMAGE_ID + " = ? ",
                new String[] { Integer.toString(widgetID), Integer.toString(dirInfoID), Integer.toString(fileID)},
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );
        if(c.getCount() == 0){
            c.close();
            return -1;
        }
        c.moveToNext();
        int id = c.getInt(c.getColumnIndex(WIDGET_IMAGE_COLUMN_ID));
        c.close();
        return id;
    }

    public WidgetImage getWidgetImageInfo(int widgetID, int dirInfoID, int fileID){
        Cursor c = database.query(
                WIDGET_IMAGE_TABLE_NAME,
                WidgetImageProjection,
                WIDGET_IMAGE_COLUMN_WIDGET_ID + " = ? and " + WIDGET_IMAGE_COLUMN_DIR_INFO_ID + " = ? and " + WIDGET_IMAGE_COLUMN_IMAGE_ID + " = ? ",
                new String[] { Integer.toString(widgetID), Integer.toString(dirInfoID), Integer.toString(fileID)},
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );
        if(c.getCount() == 0){
            c.close();
            return null;
        }
        c.moveToNext();
        WidgetImage widgetImage = new WidgetImage();
        widgetImage.id = c.getInt(c.getColumnIndex(WIDGET_IMAGE_COLUMN_ID));
        widgetImage.widgetID = c.getInt(c.getColumnIndex(WIDGET_IMAGE_COLUMN_WIDGET_ID));
        widgetImage.dirInfoID = c.getInt(c.getColumnIndex(WIDGET_IMAGE_COLUMN_DIR_INFO_ID));
        widgetImage.imageID = c.getInt(c.getColumnIndex(WIDGET_IMAGE_COLUMN_IMAGE_ID));
        widgetImage.accessCount = c.getInt(c.getColumnIndex(WIDGET_IMAGE_COLUMN_ACCESS_COUNT));
        c.close();
        return widgetImage;
    }

    public WidgetImage getWidgetImageInfo(int id){
        Cursor c = database.query(
                WIDGET_IMAGE_TABLE_NAME,
                WidgetImageProjection,
                WIDGET_IMAGE_COLUMN_ID + " = ? ",
                new String[] { Integer.toString(id) },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );
        if(c.getCount() == 0){
            c.close();
            return null;
        }
        c.moveToNext();
        WidgetImage widgetImage = new WidgetImage();
        widgetImage.id = c.getInt(c.getColumnIndex(WIDGET_IMAGE_COLUMN_ID));
        widgetImage.widgetID = c.getInt(c.getColumnIndex(WIDGET_IMAGE_COLUMN_WIDGET_ID));
        widgetImage.dirInfoID = c.getInt(c.getColumnIndex(WIDGET_IMAGE_COLUMN_DIR_INFO_ID));
        widgetImage.imageID = c.getInt(c.getColumnIndex(WIDGET_IMAGE_COLUMN_IMAGE_ID));
        widgetImage.accessCount = c.getInt(c.getColumnIndex(WIDGET_IMAGE_COLUMN_ACCESS_COUNT));
        c.close();
        return widgetImage;
    }

    public int widgetContainDir(int widgetID, String accountSignature, String repoID, String dirID){
        int dirInfoID = getDirInfoID(accountSignature, repoID, dirID);
        if(dirInfoID < 0){
            return dirInfoID;
        }
        Cursor c = database.query(
                WIDGET_IMAGE_TABLE_NAME,
                WidgetImageProjection,
                WIDGET_IMAGE_COLUMN_WIDGET_ID + " = ? and " + WIDGET_IMAGE_COLUMN_DIR_INFO_ID + " = ? ",
                new String[] { Integer.toString(widgetID), Integer.toString(dirInfoID) },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );
        if(c.getCount() == 0){
            c.close();
            return -1;
        }
        c.close();
        return dirInfoID;
    }

    private boolean hasWidgetExistsDir(int dirInfoID){
        Cursor c = database.query(
                WIDGET_IMAGE_TABLE_NAME,
                WidgetImageProjection,
                WIDGET_IMAGE_COLUMN_DIR_INFO_ID + " = ? ",
                new String[] { Integer.toString(dirInfoID) },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );
        if(c.getCount() == 0){
            c.close();
            return false;
        }
        c.close();
        return true;
    }

    public void deleteAllWidgetDir(String accountSignature, String repoID, String dirID){
        int dirInfoID = getDirInfoID(accountSignature, repoID, dirID);
        deleteAllWidgetDir(dirInfoID);
    }

    public void deleteAllWidgetDir(int dirInfoID){
        database.delete(WIDGET_IMAGE_TABLE_NAME, WIDGET_IMAGE_COLUMN_DIR_INFO_ID + " = ? ", new String[]{Integer.toString(dirInfoID)});
        deleteDir(dirInfoID);
    }

    public void deleteWidgetDir(int widgetID, String accountSignature, String repoID, String dirID){
        int dirInfoID = getDirInfoID(accountSignature, repoID, dirID);
        database.delete(WIDGET_IMAGE_TABLE_NAME, WIDGET_IMAGE_COLUMN_WIDGET_ID + " = ? and " +  WIDGET_IMAGE_COLUMN_DIR_INFO_ID + " = ? ", new String[]{Integer.toString(widgetID), Integer.toString(dirInfoID)});
        if(!hasWidgetExistsDir(dirInfoID)){
            deleteDir(dirInfoID);
        }
    }

    public void deleteWidget(int widgetID){
        database.delete(WIDGET_IMAGE_TABLE_NAME, WIDGET_IMAGE_COLUMN_WIDGET_ID + " = ? ", new String[]{Integer.toString(widgetID)});
    }

    public void deleteUnusedWidgetDir(int widgetID, List<Integer> usedDirInfoIDs){
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for(int dirInfoID:usedDirInfoIDs){
            sb.append(dirInfoID);
            ++i;
            if(i < usedDirInfoIDs.size()){
                sb.append(",");
            }
        }
        database.delete(WIDGET_IMAGE_TABLE_NAME,WIDGET_IMAGE_COLUMN_WIDGET_ID + " = ? and "
                + WIDGET_IMAGE_COLUMN_DIR_INFO_ID + " NOT IN (?);",
                new String[]{Integer.toString(widgetID), sb.toString()});
    }

    public void deleteUnusedDirs(){
        Cursor c = database.rawQuery("select distinct " + WIDGET_IMAGE_COLUMN_DIR_INFO_ID + " from " + WIDGET_IMAGE_TABLE_NAME + ";", null);
        List<Integer> usedDirInfoIDs = Lists.newArrayList();
        while(c.moveToNext()){
            usedDirInfoIDs.add(c.getInt(c.getColumnIndex(WIDGET_IMAGE_COLUMN_DIR_INFO_ID)));
        }
        c.close();
        deleteUnusedDirs(usedDirInfoIDs);
    }

    public int addWidgetImage(int widgetID, int dirInfoID, int imageID){
        int id = getWidgetImageID(widgetID, dirInfoID, imageID);
        if(id < 0){
            ContentValues values = new ContentValues();
            values.put(WIDGET_IMAGE_COLUMN_WIDGET_ID, widgetID);
            values.put(WIDGET_IMAGE_COLUMN_DIR_INFO_ID, dirInfoID);
            values.put(WIDGET_IMAGE_COLUMN_IMAGE_ID, imageID);
            database.insert(WIDGET_IMAGE_TABLE_NAME, null, values);
            id = getWidgetImageID(widgetID, dirInfoID, imageID);
        }
        return id;
    }

    public void updateWidgetImageAccessCount(int widgetID, String accountSignature, String repoID, String dirID, String filePath, int change){
        int dirInfoID = getDirInfoID(accountSignature, repoID, dirID);
        if(dirInfoID < 0){
            return;
        }
        int imageID = getImageID(dirInfoID, filePath);
        if(imageID < 0){
            return;
        }
        WidgetImage info = getWidgetImageInfo(widgetID, dirInfoID, imageID);
        if(info == null){
            return;
        }
        ContentValues value = new ContentValues();
        value.put(WIDGET_IMAGE_COLUMN_ACCESS_COUNT, info.accessCount+change);
        database.update(WIDGET_IMAGE_TABLE_NAME, value, WIDGET_IMAGE_COLUMN_ID + " = ?" , new String[]{Integer.toString(info.id)});
    }

    public List<Integer> addWidgetDir(int widgetID, int dirInfoID){
        if(dirInfoID < 0){
            return null;
        }
        List<ImageDBInfo> images = getDirImageInfo(dirInfoID);
        List<Integer> ids = Lists.newArrayList();
        for(ImageDBInfo image:images){
            int id = addWidgetImage(widgetID, dirInfoID, image.id);
            if(id >= 0){
                ids.add(id);
            }
        }
        return ids;
    }

    public int getImageNum(int widgetID){
        Cursor c = database.query(
                WIDGET_IMAGE_TABLE_NAME,
                WidgetImageProjection,
                WIDGET_IMAGE_COLUMN_WIDGET_ID + " = ? ",
                new String[] { Integer.toString(widgetID) },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );
        int res = c.getCount();
        c.close();
        return res;
    }

    public int getImageNum(int widgetID, boolean downloaded){
        Cursor c = database.rawQuery("select " + IMAGE_INFO_TABLE_NAME + "." + IMAGE_INFO_COLUMN_ID + ", " + IMAGE_INFO_COLUMN_FILE_PATH
                + " from " + WIDGET_IMAGE_TABLE_NAME + " inner join " + IMAGE_INFO_TABLE_NAME
                + " on " + WIDGET_IMAGE_TABLE_NAME + "." + WIDGET_IMAGE_COLUMN_IMAGE_ID + " = "
                + IMAGE_INFO_TABLE_NAME + "." + IMAGE_INFO_COLUMN_ID + " where "
                + WIDGET_IMAGE_COLUMN_WIDGET_ID + " = " + Integer.toString(widgetID) + " and "
                + IMAGE_INFO_COLUMN_DOWNLOADED + " = " + Integer.toString(downloaded?1:0) + ";", null);
        int res = c.getCount();
        c.close();
        return res;
    }

    public ImageInfo getImageInfo(int widgetID, int index, boolean downloaded){
        Cursor c = database.rawQuery("select " + IMAGE_INFO_TABLE_NAME + "." + IMAGE_INFO_COLUMN_ID + ", "
                + IMAGE_INFO_TABLE_NAME + "." + IMAGE_INFO_COLUMN_DIR_INFO_ID + ", " + IMAGE_INFO_TABLE_NAME + "." + IMAGE_INFO_COLUMN_FILE_PATH
                + ", " + IMAGE_INFO_TABLE_NAME + "." + IMAGE_INFO_COLUMN_FILE_SIZE
                + " from " + WIDGET_IMAGE_TABLE_NAME + " inner join " + IMAGE_INFO_TABLE_NAME
                + " on " + WIDGET_IMAGE_TABLE_NAME + "." + WIDGET_IMAGE_COLUMN_IMAGE_ID + " = "
                + IMAGE_INFO_TABLE_NAME + "." + IMAGE_INFO_COLUMN_ID + " where "
                + WIDGET_IMAGE_COLUMN_WIDGET_ID + " = " + Integer.toString(widgetID) + " and "
                + IMAGE_INFO_COLUMN_DOWNLOADED + " = " + Integer.toString(downloaded?1:0)
                + " limit " + Integer.toString(index) + ",1;", null);
        if(c.getCount() == 0){
            c.close();
            return null;
        }
        c.moveToNext();
        int dirInfoID = c.getInt(c.getColumnIndex(IMAGE_INFO_COLUMN_DIR_INFO_ID));
        String fielPath = c.getString(c.getColumnIndex(IMAGE_INFO_COLUMN_FILE_PATH));
        long fileSize = c.getLong(c.getColumnIndex(IMAGE_INFO_COLUMN_FILE_SIZE));
        DirInfo dirInfo = getDirInfo(dirInfoID);
        ImageInfo imageInfo = new ImageInfo(dirInfo, fielPath, fileSize);
        return imageInfo;
    }

    public void removeWidgetImages(int widgetID, int maxAccessTimes){
        Cursor c = database.rawQuery("select distinct " + WIDGET_IMAGE_COLUMN_IMAGE_ID + " from "
                + WIDGET_IMAGE_TABLE_NAME + " where " + WIDGET_IMAGE_COLUMN_ACCESS_COUNT + " > "
                + Integer.toString(maxAccessTimes) + " and " + WIDGET_IMAGE_COLUMN_WIDGET_ID + " = " + Integer.toString(widgetID)
                + " group by " + WIDGET_IMAGE_COLUMN_IMAGE_ID + " having " + " count(" + WIDGET_IMAGE_COLUMN_IMAGE_ID + ") == 1;",
                null,null);
        if(c.getCount() == 0){
            c.close();
        }else{
            while(c.moveToNext()){
                int imageID = c.getInt(c.getColumnIndex(WIDGET_IMAGE_COLUMN_IMAGE_ID));
                deleteImageStorage(imageID);
            }
            c.close();

        }
        ContentValues value = new ContentValues();
        value.put(WIDGET_IMAGE_COLUMN_ACCESS_COUNT, 0);
        database.update(WIDGET_IMAGE_TABLE_NAME, value,WIDGET_IMAGE_COLUMN_WIDGET_ID + " = ? and " + WIDGET_IMAGE_COLUMN_ACCESS_COUNT + " > ?", new String[]{Integer.toString(widgetID), Integer.toString(maxAccessTimes)});
//        database.delete(WIDGET_IMAGE_TABLE_NAME, WIDGET_IMAGE_COLUMN_ACCESS_COUNT + " > ?", new String[]{Integer.toString(maxAccessTimes)});
    }

}
