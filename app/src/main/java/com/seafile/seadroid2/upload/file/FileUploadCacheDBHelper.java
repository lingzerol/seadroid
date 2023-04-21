package com.seafile.seadroid2.upload.file;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.seafile.seadroid2.SeadroidApplication;
import com.seafile.seadroid2.util.Utils;

import java.io.File;

public class FileUploadCacheDBHelper extends SQLiteOpenHelper {
    private static final String DEBUG_TAG = "FileUploadCacheDBHelper";
    private static FileUploadCacheDBHelper dbHelper = null;
    public static final int DATABASE_VERSION = 3;
    public static final String DATABASE_NAME = "FileUpload.db";

    private SQLiteDatabase database;

    private FileUploadCacheDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createAllTables(db);
    }

    public static synchronized FileUploadCacheDBHelper getInstance() {
        if (dbHelper == null) {
            dbHelper = new FileUploadCacheDBHelper(SeadroidApplication.getAppContext());
            dbHelper.database = dbHelper.getWritableDatabase();
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
        dropCacheTable(db);
        dropAccountTable(db);
    }

    private void createAllTables(SQLiteDatabase db){
        createAccountTable(db);
        createCacheTable(db);
    }

    // If you change the database schema, you must increment the database
    // version.
    // account table
    private static final String ACCOUNT_TABLE_NAME_SUFFIX = "Account";
    private static final String ACCOUNT_COLUMN_ID = "id";
    private static final String ACCOUNT_COLUMN_ACCOUNT_SIGNATURE = "account_signature";
    private static final String[] AccountProjection = {
            ACCOUNT_COLUMN_ID,
            ACCOUNT_COLUMN_ACCOUNT_SIGNATURE,
    };

    private String getAccountTableName(){
        return ACCOUNT_TABLE_NAME_SUFFIX;
    }

    private void createAccountTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + getAccountTableName() + " ("
                + ACCOUNT_COLUMN_ID + " INTEGER PRIMARY KEY, "
                + ACCOUNT_COLUMN_ACCOUNT_SIGNATURE + " TEXT NOT NULL)");
        db.execSQL("CREATE INDEX " + ACCOUNT_COLUMN_ACCOUNT_SIGNATURE + "_index ON " + getAccountTableName()
                + " (" + ACCOUNT_COLUMN_ACCOUNT_SIGNATURE + ");");
    }

    private void dropAccountTable(SQLiteDatabase db){
        db.execSQL("DROP TABLE IF EXISTS " + getAccountTableName() + ";");
    }

    private int findAccountID(String accountSignature){
        Cursor c = database.query(
                getAccountTableName(),
                AccountProjection,
                ACCOUNT_COLUMN_ACCOUNT_SIGNATURE + " = ? ",
                new String[] { accountSignature },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );

        if(c.getCount() == 0){
            c.close();
            return -1;
        }
        c.moveToNext();
        int id = c.getInt(c.getColumnIndex(ACCOUNT_COLUMN_ID));
        c.close();
        return id;
    }

    private int getAccountID(String accountSignature){
        int id = findAccountID(accountSignature);
        if(id < 0){
            ContentValues values = new ContentValues();
            values.put(ACCOUNT_COLUMN_ACCOUNT_SIGNATURE, accountSignature);
            database.insert(getAccountTableName(), null, values);
        }else{
            return id;
        }
        return findAccountID(accountSignature);
    }

    // --------------------------------------------------------------------------------
    // --------------------------------------------------------------------------------
    // --------------------------------------------------------------------------------
    // --------------------------------------------------------------------------------
    // Cache table
    private static final String CACHE_TABLE_NAME = "DirCache";
    private static final String CACHE_COLUMN_ID = "id";
    private static final String CACHE_COLUMN_ACCOUNT_SIGNATURE_ID = "account_signature_id";
    private static final String CACHE_COLUMN_LOCAL_PATH = "local_path";
    private static final String CACHE_COLUMN_DIR_NAME = "dir_name";
    private static final String[] CacheProjection = {
            CACHE_COLUMN_ID,
            CACHE_COLUMN_ACCOUNT_SIGNATURE_ID,
            CACHE_COLUMN_LOCAL_PATH,
            CACHE_COLUMN_DIR_NAME
    };
    private String getCacheTableName(){
        return CACHE_TABLE_NAME;
    }
    private void createCacheTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + getCacheTableName() + " ("
                + CACHE_COLUMN_ID + " INTEGER PRIMARY KEY, "
                + CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + " TEXT NOT NULL, "
                + CACHE_COLUMN_LOCAL_PATH + " TEXT NOT NULL, "
                + CACHE_COLUMN_DIR_NAME + " TEXT NOT NULL, "
                + "FOREIGN KEY (" + CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + ") REFERENCES " + getAccountTableName() + "(" + ACCOUNT_COLUMN_ID + "))");
        db.execSQL("CREATE INDEX " + CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + "_index ON " + getCacheTableName()
                + " (" + CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + ");");
        db.execSQL("CREATE INDEX " + CACHE_COLUMN_LOCAL_PATH + "_cache_index ON " + getCacheTableName()
                + " (" + CACHE_COLUMN_LOCAL_PATH + ");");
        db.execSQL("CREATE INDEX " + CACHE_COLUMN_DIR_NAME + "_cache_index ON " + getCacheTableName()
                + " (" + CACHE_COLUMN_DIR_NAME + ");");
    }

    private void dropCacheTable(SQLiteDatabase db){
        db.execSQL("DROP TABLE IF EXISTS " + getCacheTableName() + ";");
    }

    private boolean DirNameExists(String accountSignature, String dirName){
        int accountID = getAccountID(accountSignature);
        if(accountID < 0){
            return false;
        }
        Cursor c = database.query(
                getCacheTableName(),
                CacheProjection,
                CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + " = ? and " + CACHE_COLUMN_DIR_NAME + " = ? ",
                new String[] { Integer.toString(accountID), dirName },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );

        int count = c.getCount();
        c.close();
        return count > 0;
    }

    private boolean PathExists(String accountSignature, String path){
        int accountID = getAccountID(accountSignature);
        if(accountID < 0){
            return false;
        }
        Cursor c = database.query(
                getCacheTableName(),
                CacheProjection,
                CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + " = ? and " + CACHE_COLUMN_LOCAL_PATH + " = ? ",
                new String[] { Integer.toString(accountID), path },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );

        int count = c.getCount();
        c.close();
        return count > 0;
    }


    private boolean PathDirMappingExists(String accountSignature, String path, String dirName){
        int accountID = getAccountID(accountSignature);
        if(accountID < 0){
            return false;
        }
        Cursor c = database.query(
                getCacheTableName(),
                CacheProjection,
                CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + " = ? and " + CACHE_COLUMN_LOCAL_PATH + " = ? and " + CACHE_COLUMN_DIR_NAME + " = ? ",
                new String[] { Integer.toString(accountID), path, dirName },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );

        int count = c.getCount();
        c.close();
        return count > 0;
    }

    private void insertPathDirMapping(String accountSignature, String path, String dirName){
        int accountID = getAccountID(accountSignature);
        if(accountID < 0){
            return;
        }
        ContentValues value = new ContentValues();
        value.put(CACHE_COLUMN_ACCOUNT_SIGNATURE_ID, accountID);
        value.put(CACHE_COLUMN_LOCAL_PATH, path);
        value.put(CACHE_COLUMN_DIR_NAME, dirName);
        database.insert(CACHE_TABLE_NAME, null, value);
    }

    public String getUploadDirName(String accountSignature, String path){
        int accountID = getAccountID(accountSignature);
        if(accountID < 0){
            return null;
        }
        Cursor c = database.query(
                getCacheTableName(),
                CacheProjection,
                CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + " = ? and " + CACHE_COLUMN_LOCAL_PATH + " = ? ",
                new String[] { Integer.toString(accountID), path },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );

        int count = c.getCount();
        if(count > 0){
            c.moveToNext();
            String dirName = c.getString(c.getColumnIndex(CACHE_COLUMN_DIR_NAME));
            c.close();
            return dirName;
        }
        c.close();
        String dirName = Utils.fileNameFromPath(Utils.removeLastPathSeperator(path));
        String uniqueDirName = dirName;
        int i = 0;
        while(true){
            if(i > 0){
                uniqueDirName = dirName + " (" + i + ")";
            }
            if(!DirNameExists(accountSignature, dirName)){
                insertPathDirMapping(accountSignature, path, uniqueDirName);
                if(PathDirMappingExists(accountSignature, path, uniqueDirName)){
                    return uniqueDirName;
                }else{
                    return null;
                }
            }
            ++i;
        }
    }

    public void cleanCache() {
        database.delete(getCacheTableName(), null, null);
    }

}