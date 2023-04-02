package com.seafile.seadroid2.upload;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.seafile.seadroid2.SeadroidApplication;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class UploadDBHelper extends SQLiteOpenHelper {
    private static final String DEBUG_TAG = "UploadDBHelper";
    private static Map<String, UploadDBHelper> dbHelpers = new HashMap<String, UploadDBHelper>();
    public static final int DATABASE_VERSION = 3;
    public static final String DATABASE_NAME = "CloudUpload.db";

    private SQLiteDatabase database;
    private String name = null;

    private UploadDBHelper(Context context, String name) {
        super(context, name + DATABASE_NAME, null, DATABASE_VERSION);
        this.name = name;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createAllTables(db);
    }

    public static synchronized UploadDBHelper getInstance(String name) {
        UploadDBHelper dbHelper = null;
        if (!dbHelpers.containsKey(name)) {
            dbHelper = new UploadDBHelper(SeadroidApplication.getAppContext(), name);
            dbHelper.database = dbHelper.getWritableDatabase();
            dbHelpers.put(name, dbHelper);
        }else{
            dbHelper = dbHelpers.get(name);
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
        return name + ACCOUNT_TABLE_NAME_SUFFIX;
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
    private static final String CACHE_TABLE_NAME_SUFFIX = "Cache";
    private static final String CACHE_COLUMN_ID = "id";
    private static final String CACHE_COLUMN_ACCOUNT_SIGNATURE_ID = "account_signature_id";
    private static final String CACHE_COLUMN_FILE = "file";
    private static final String CACHE_COLUMN_DATE_ADDED = "date_added";
    private static final String[] CacheProjection = {
            CACHE_COLUMN_ID,
            CACHE_COLUMN_ACCOUNT_SIGNATURE_ID,
            CACHE_COLUMN_FILE,
            CACHE_COLUMN_DATE_ADDED
    };
    private String getCacheTableName(){
        return name + CACHE_TABLE_NAME_SUFFIX;
    }
    private void createCacheTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + getCacheTableName() + " ("
                + CACHE_COLUMN_ID + " INTEGER PRIMARY KEY, "
                + CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + " TEXT NOT NULL, "
                + CACHE_COLUMN_FILE + " TEXT NOT NULL, "
                + CACHE_COLUMN_DATE_ADDED + " BIGINT NOT NULL, "
                + "FOREIGN KEY (" + CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + ") REFERENCES " + getAccountTableName() + "(" + ACCOUNT_COLUMN_ID + "))");
        db.execSQL("CREATE INDEX " + CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + "_index ON " + getCacheTableName()
                + " (" + CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + ");");
        db.execSQL("CREATE INDEX " + CACHE_COLUMN_FILE + "_index ON " + getCacheTableName()
                + " (" + CACHE_COLUMN_FILE + ");");
        db.execSQL("CREATE INDEX " + CACHE_COLUMN_DATE_ADDED + "_index ON " + getCacheTableName()
                + " (" + CACHE_COLUMN_DATE_ADDED + ");");
    }

    private void dropCacheTable(SQLiteDatabase db){
        db.execSQL("DROP TABLE IF EXISTS " + getCacheTableName() + ";");
    }

    public boolean isUploaded(String accountSignature, String path, long modified) {
        int accountID = getAccountID(accountSignature);
        if(accountID < 0){
            return false;
        }
        Cursor c = database.query(
                getCacheTableName(),
                CacheProjection,
                CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + " = ? and " + CACHE_COLUMN_FILE + " = ? and " + CACHE_COLUMN_DATE_ADDED + " = ?",
                new String[] { Integer.toString(accountID), path, Long.toString(modified) },
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );

        int count = c.getCount();
        c.close();
        return count > 0;
    }

    public boolean isUploaded(String accountSignature, String path) {
        int accountID = getAccountID(accountSignature);
        if(accountID < 0){
            return false;
        }
        Cursor c = database.query(
                getCacheTableName(),
                CacheProjection,
                CACHE_COLUMN_ACCOUNT_SIGNATURE_ID + " = ? and " + CACHE_COLUMN_FILE + " = ? ",
                new String[] { Integer.toString(accountID), path},
                null,   // don't group the rows
                null,   // don't filter by row groups
                null    // The sort order
        );

        int count = c.getCount();
        c.close();
        return count > 0;
    }

    public void markAsUploaded(String accountSignature, File file) {
        String path = file.getAbsolutePath();
        long modified = file.lastModified();

        markAsUploaded(accountSignature, path, modified);
    }

    public void markAsUploaded(String accountSignature, String path, long modified) {
        int accountID = getAccountID(accountSignature);
        if(accountID < 0){
            return;
        }
        ContentValues values = new ContentValues();
        values.put(CACHE_COLUMN_ACCOUNT_SIGNATURE_ID, accountID);
        values.put(CACHE_COLUMN_FILE, path);
        values.put(CACHE_COLUMN_DATE_ADDED, modified);

        database.insert(getCacheTableName(), null, values);
    }

    public void cleanCache() {
        database.delete(getCacheTableName(), null, null);
    }

//    // --------------------------------------------------------------------------------
//    // --------------------------------------------------------------------------------
//    // --------------------------------------------------------------------------------
//    // --------------------------------------------------------------------------------
//    // Upload account and repo info
//    private static final String ACCOUNT_REPO_TABLE_NAME_SUFFIX = "AccountRepo";
//    private static final String ACCOUNT_REPO_COLUMN_ID = "id";
//    private static final String ACCOUNT_REPO_COLUMN_ACCOUNT_SIGNATURE = "signature";
//    private static final String ACCOUNT_REPO_COLUMN_REPO_ID = "repo_id";
//    private static final String ACCOUNT_REPO_COLUMN_REPO_NAME = "repo_name";
//    private static final String ACCOUNT_REPO_COLUMN_DATA_PATH = "data_path";
//    private static final String ACCOUNT_REPO_COLUMN_DATA_PLAN = "data_plan";
//    private static final String ACCOUNT_REPO_COLUMN_AVAILABLE_SYNC = "available_sync";
//    private static final String ACCOUNT_REPO_COLUMN_EXTRA_OPTION = "extra_option";
//    private static final String ACCOUNT_REPO_COLUMN_ACTIVATE = "activate";
//    private static final String[] AccountRepoProjection = {
//            ACCOUNT_REPO_COLUMN_ID,
//            ACCOUNT_REPO_COLUMN_ACCOUNT_SIGNATURE,
//            ACCOUNT_REPO_COLUMN_REPO_ID,
//            ACCOUNT_REPO_COLUMN_REPO_NAME,
//            ACCOUNT_REPO_COLUMN_DATA_PATH,
//            ACCOUNT_REPO_COLUMN_DATA_PLAN,
//            ACCOUNT_REPO_COLUMN_AVAILABLE_SYNC,
//            ACCOUNT_REPO_COLUMN_EXTRA_OPTION,
//            ACCOUNT_REPO_COLUMN_ACTIVATE
//    };
//    private String getAccountRepoTableName(){
//        return name + ACCOUNT_REPO_TABLE_NAME_SUFFIX;
//    }
//    private void createAccountRepoTable() {
//        database.execSQL("CREATE TABLE " + getAccountRepoTableName() + " ("
//                + ACCOUNT_REPO_COLUMN_ID + " INTEGER PRIMARY KEY, "
//                + ACCOUNT_REPO_COLUMN_ACCOUNT_SIGNATURE + " TEXT NOT NULL, "
//                + ACCOUNT_REPO_COLUMN_REPO_ID + " TEXT NOT NULL, "
//                + ACCOUNT_REPO_COLUMN_REPO_NAME + " TEXT NOT NULL, "
//                + ACCOUNT_REPO_COLUMN_DATA_PATH + " TEXT NOT NULL DEFAULT \"/\", "
//                + ACCOUNT_REPO_COLUMN_DATA_PLAN + " INTEGER NOT NULL DEFAULT 0, "
//                + ACCOUNT_REPO_COLUMN_AVAILABLE_SYNC + " INTEGER NOT NULL DEFAULT 0, "
//                + ACCOUNT_REPO_COLUMN_EXTRA_OPTION + " TEXT, "
//                + ACCOUNT_REPO_COLUMN_ACTIVATE + " INTEGER NOT NULL DEFAULT 1);");
//
//        database.execSQL("CREATE INDEX " + ACCOUNT_REPO_COLUMN_ACCOUNT_SIGNATURE + "_index ON " + getAccountRepoTableName()
//                + " (" + ACCOUNT_REPO_COLUMN_ACCOUNT_SIGNATURE + ");");
//        database.execSQL("CREATE INDEX " + ACCOUNT_REPO_COLUMN_REPO_ID + "_index ON " + getAccountRepoTableName()
//                + " (" + ACCOUNT_REPO_COLUMN_REPO_ID + ");");
//        database.execSQL("CREATE INDEX " + ACCOUNT_REPO_COLUMN_REPO_NAME + "_index ON " + getAccountRepoTableName()
//                + " (" + ACCOUNT_REPO_COLUMN_REPO_NAME + ");");
//    }
//
//    private void dropAccountRepoTable(){
//        database.execSQL("DROP TABLE IF EXISTS " + getAccountRepoTableName() + ";");
//    }
//
//
//    // --------------------------------------------------------------------------------
//    // --------------------------------------------------------------------------------
//    // methods
//    public AccountRepoInfo getAccountRepoInfo(Account account){
//        if(account != null){
//            return getAccountRepoInfo(account.getSignature());
//        }
//        return null;
//    }
//
//    public AccountRepoInfo getAccountRepoInfo(String accountSignature){
//        Cursor c = database.query(
//                getAccountRepoTableName(),
//                AccountRepoProjection,
//                ACCOUNT_REPO_COLUMN_ACCOUNT_SIGNATURE + " = ? ",
//                new String[]{accountSignature},
//                null,   // don't group the rows
//                null,   // don't filter by row groups
//                null    // The sort order
//        );
//
//        if(c.getCount() == 0){
//            return null;
//        }
//        c.moveToNext();
//        int id = c.getInt(c.getColumnIndex(ACCOUNT_REPO_COLUMN_ID));
//        String signature = c.getString(c.getColumnIndex(ACCOUNT_REPO_COLUMN_ACCOUNT_SIGNATURE));
//        String repoID = c.getString(c.getColumnIndex(ACCOUNT_REPO_COLUMN_REPO_ID));
//        String repoName = c.getString(c.getColumnIndex(ACCOUNT_REPO_COLUMN_REPO_NAME));
//        String dataPath = c.getString(c.getColumnIndex(ACCOUNT_REPO_COLUMN_DATA_PATH));
//        boolean allowDataPlan = c.getInt(c.getColumnIndex(ACCOUNT_REPO_COLUMN_DATA_PLAN)) > 0;
//        String extraOptions = c.getString(c.getColumnIndex(ACCOUNT_REPO_COLUMN_EXTRA_OPTION));
//        int availableSyncs = c.getInt(c.getColumnIndex(ACCOUNT_REPO_COLUMN_AVAILABLE_SYNC));
//        boolean activate = c.getInt(c.getColumnIndex(ACCOUNT_REPO_COLUMN_ACTIVATE)) > 0;
//        c.close();
//        return new AccountRepoInfo(id, signature, repoID, repoName, dataPath, allowDataPlan, availableSyncs, extraOptions, activate);
//    }
//
//    public void addAccountRepoInfo(String accountSignature, String repoID, String repoName, String dataPath, boolean allowDataPlan, int availableSyncs, String extraOptions, boolean activate){
//        AccountRepoInfo info = getAccountRepoInfo(accountSignature);
//        ContentValues values = new ContentValues();
//        values.put(ACCOUNT_REPO_COLUMN_ACCOUNT_SIGNATURE, accountSignature);
//        values.put(ACCOUNT_REPO_COLUMN_REPO_ID, repoID);
//        values.put(ACCOUNT_REPO_COLUMN_REPO_NAME, repoName);
//        values.put(ACCOUNT_REPO_COLUMN_DATA_PATH, dataPath);
//        values.put(ACCOUNT_REPO_COLUMN_DATA_PLAN, allowDataPlan?1:0);
//        values.put(ACCOUNT_REPO_COLUMN_AVAILABLE_SYNC, availableSyncs);
//        values.put(ACCOUNT_REPO_COLUMN_EXTRA_OPTION, extraOptions);
//        values.put(ACCOUNT_REPO_COLUMN_ACTIVATE, activate?1:0);
//        if(info == null){
//            database.insert(getAccountRepoTableName(), null, values);
//        } else {
//            database.update(getAccountRepoTableName(), values, ACCOUNT_REPO_COLUMN_ID + " = ?", new String[]{Integer.toString(info.id)});
//        }
//    }
//
//    public void setAccountRepoSignature(String accountSignature, String targetSignature){
//        AccountRepoInfo info = getAccountRepoInfo(accountSignature);
//        if(info != null){
//            setAccountRepoSignature(info.id, targetSignature);
//        }
//    }
//    public void setAccountRepoSignature(int acccountRepoID, String targetSignature){
//        if(acccountRepoID < 0){
//            return;
//        }
//        ContentValues values = new ContentValues();
//        values.put(ACCOUNT_REPO_COLUMN_ACCOUNT_SIGNATURE, targetSignature);
//        database.update(getAccountRepoTableName(), values, ACCOUNT_REPO_COLUMN_ID + " = ?", new String[]{Integer.toString(acccountRepoID)});
//    }
//    public void setAccountRepoRepoInfo(String accountSignature, String repoID, String repoName, String dataPath){
//        AccountRepoInfo info = getAccountRepoInfo(accountSignature);
//        if(info != null){
//            setAccountRepoRepoInfo(info.id, repoID, repoName, dataPath);
//        }
//    }
//    public void setAccountRepoRepoInfo(int acccountRepoID, String repoID, String repoName, String dataPath){
//        if(acccountRepoID < 0){
//            return;
//        }
//        ContentValues values = new ContentValues();
//        values.put(ACCOUNT_REPO_COLUMN_REPO_ID, repoID);
//        values.put(ACCOUNT_REPO_COLUMN_REPO_NAME, repoName);
//        values.put(ACCOUNT_REPO_COLUMN_DATA_PATH, dataPath);
//        database.update(getAccountRepoTableName(), values, ACCOUNT_REPO_COLUMN_ID + " = ?", new String[]{Integer.toString(acccountRepoID)});
//    }
//    public void setAccountRepoAvailableSync(String accountSignature, int availableSyncs){
//        AccountRepoInfo info = getAccountRepoInfo(accountSignature);
//        if(info != null){
//            setAccountRepoAvailableSync(info.id, availableSyncs);
//        }
//    }
//    public void setAccountRepoAvailableSync(int acccountRepoID, int availableSyncs){
//        if(acccountRepoID < 0){
//            return;
//        }
//        ContentValues values = new ContentValues();
//        values.put(ACCOUNT_REPO_COLUMN_AVAILABLE_SYNC, availableSyncs);
//        database.update(getAccountRepoTableName(), values, ACCOUNT_REPO_COLUMN_ID + " = ?", new String[]{Integer.toString(acccountRepoID)});
//    }
//    public void setAccountRepoOptions(String accountSignature, boolean allowDataPlan, String extraOptions){
//        AccountRepoInfo info = getAccountRepoInfo(accountSignature);
//        if(info != null){
//            setAccountRepoOptions(info.id, allowDataPlan, extraOptions);
//        }
//    }
//    public void setAccountRepoOptions(int acccountRepoID, boolean allowDataPlan, String extraOptions){
//        if(acccountRepoID < 0){
//            return;
//        }
//        ContentValues values = new ContentValues();
//        values.put(ACCOUNT_REPO_COLUMN_DATA_PLAN, allowDataPlan?1:0);
//        values.put(ACCOUNT_REPO_COLUMN_EXTRA_OPTION, extraOptions);
//        database.update(getAccountRepoTableName(), values, ACCOUNT_REPO_COLUMN_ID + " = ?", new String[]{Integer.toString(acccountRepoID)});
//    }
//    public void setAccountRepoActivate(String accountSignature, boolean activate){
//        AccountRepoInfo info = getAccountRepoInfo(accountSignature);
//        if(info != null){
//            setAccountRepoActivate(info.id, activate);
//        }
//    }
//    public void setAccountRepoActivate(int acccountRepoID, boolean activate){
//        if(acccountRepoID < 0){
//            return;
//        }
//        ContentValues values = new ContentValues();
//        values.put(ACCOUNT_REPO_COLUMN_ACTIVATE, activate?1:0);
//        database.update(getAccountRepoTableName(), values, ACCOUNT_REPO_COLUMN_ID + " = ?", new String[]{Integer.toString(acccountRepoID)});
//    }
//    public void disableAccountRepo(String accountSignature){
//        setAccountRepoActivate(accountSignature, false);
//    }
//    public void disableAccountRepo(int acccountRepoID){
//        setAccountRepoActivate(acccountRepoID, false);
//    }
//    // --------------------------------------------------------------------------------
//    // --------------------------------------------------------------------------------
//    // --------------------------------------------------------------------------------
//    // --------------------------------------------------------------------------------
//    // Upload Info
//    private static final String INFO_TABLE_NAME_SUFFIX = "Info";
//    private static final String INFO_COLUMN_ID = "id";
//    private static final String INFO_COLUMN_SYNC_TYPE = "sync_type";
//    private static final String INFO_COLUMN_ACCOUNT_REPO_ID = "account_repo_id";
//    private static final String INFO_COLUMN_SELECTED_INFO = "selected_info";
//    private static final String INFO_COLUMN_COMPLETED_TIME = "completed_time";
//
//    private static final String[] InfoProjection = {
//            INFO_COLUMN_ID,
//            INFO_COLUMN_ACCOUNT_REPO_ID,
//            INFO_COLUMN_SYNC_TYPE,
//            INFO_COLUMN_SELECTED_INFO,
//            INFO_COLUMN_COMPLETED_TIME,
//    };
//    private String getInfoTableName(){
//        return name + INFO_TABLE_NAME_SUFFIX;
//    }
//    private void createInfoTable() {
//        database.execSQL("CREATE TABLE " + getInfoTableName() + " ("
//                + INFO_COLUMN_ID + " INTEGER PRIMARY KEY, "
//                + INFO_COLUMN_ACCOUNT_REPO_ID + " INTEGER NOT NULL, "
//                + INFO_COLUMN_SYNC_TYPE + " INTEGER NOT NULL, "
//                + INFO_COLUMN_SELECTED_INFO + " TEXT NOT NULL, "
//                + INFO_COLUMN_COMPLETED_TIME + " BIGINT NOT NULl);");
//
//        database.execSQL("CREATE INDEX " + INFO_COLUMN_ACCOUNT_REPO_ID + "_index ON " + getInfoTableName()
//                + " (" + INFO_COLUMN_ACCOUNT_REPO_ID + ");");
//        database.execSQL("CREATE INDEX " + INFO_COLUMN_SYNC_TYPE + "_index ON " + getInfoTableName()
//                + " (" + INFO_COLUMN_SYNC_TYPE + ");");
//        database.execSQL("CREATE INDEX " + INFO_COLUMN_SELECTED_INFO + "_index ON " + getInfoTableName()
//                + " (" + INFO_COLUMN_SELECTED_INFO + ");");
//    }
//
//    private void dropInfoTable(){
//        database.execSQL("DROP TABLE IF EXISTS " + getInfoTableName() + ";");
//    }
//
//    // --------------------------------------------------------------------------------
//    // --------------------------------------------------------------------------------
//    // methods
//
//    public SelectedUploadInfo getSelectedUploadInfo(int accountRepoID, String selectedInfo){
//        Cursor c = database.query(
//                getInfoTableName(),
//                InfoProjection,
//                INFO_COLUMN_ACCOUNT_REPO_ID + " = ? and " + INFO_COLUMN_SELECTED_INFO + " = ?",
//                new String[]{Integer.toString(accountRepoID), selectedInfo},
//                null,   // don't group the rows
//                null,   // don't filter by row groups
//                null    // The sort order
//        );
//
//        if(c.getCount() == 0){
//            return null;
//        }
//        c.moveToNext();
//        int id = c.getInt(c.getColumnIndex(INFO_COLUMN_ID));
//        int accountID = c.getInt(c.getColumnIndex(INFO_COLUMN_ACCOUNT_REPO_ID));
//        int syncType = c.getInt(c.getColumnIndex(INFO_COLUMN_SYNC_TYPE));
//        String nselectedInfo = c.getString(c.getColumnIndex(INFO_COLUMN_SELECTED_INFO));
//        int completeTime = c.getInt(c.getColumnIndex(INFO_COLUMN_COMPLETED_TIME));
//        c.close();
//        return new SelectedUploadInfo(id, accountID, syncType, nselectedInfo, completeTime);
//    }
//
//    public List<SelectedUploadInfo> getSelectedUploadInfos(int accountRepoID){
//        Cursor c = database.query(
//                getInfoTableName(),
//                InfoProjection,
//                INFO_COLUMN_ACCOUNT_REPO_ID + " = ? ",
//                new String[]{Integer.toString(accountRepoID)},
//                null,   // don't group the rows
//                null,   // don't filter by row groups
//                null    // The sort order
//        );
//
//        if(c.getCount() == 0){
//            return null;
//        }
//        List<SelectedUploadInfo> infos = Lists.newArrayList();
//        while(c.moveToNext()) {
//            int id = c.getInt(c.getColumnIndex(INFO_COLUMN_ID));
//            int accountID = c.getInt(c.getColumnIndex(INFO_COLUMN_ACCOUNT_REPO_ID));
//            int syncType = c.getInt(c.getColumnIndex(INFO_COLUMN_SYNC_TYPE));
//            String selectedInfo = c.getString(c.getColumnIndex(INFO_COLUMN_SELECTED_INFO));
//            int completedTime = c.getInt(c.getColumnIndex(INFO_COLUMN_COMPLETED_TIME));
//            SelectedUploadInfo info = new SelectedUploadInfo(id, accountID, syncType, selectedInfo, completedTime);
//            infos.add(info);
//        }
//        c.close();
//        return infos;
//    }
//
//    public void addSelectedUploadInfo(int accountRepoID, int syncType, String selectedInfo, int completedTime){
//        ContentValues values = new ContentValues();
//        values.put(INFO_COLUMN_ACCOUNT_REPO_ID, accountRepoID);
//        values.put(INFO_COLUMN_SYNC_TYPE, syncType);
//        values.put(INFO_COLUMN_SELECTED_INFO, selectedInfo);
//        values.put(INFO_COLUMN_COMPLETED_TIME, completedTime);
//        database.insert(getInfoTableName(), null, values);
//    }
//
//    public void deleteSelectedUploadInfo(int id){
//        database.delete(getInfoTableName(), INFO_COLUMN_ID + " = ? ", new String[]{Integer.toString(id)});
//    }
//
//    public void setInfoAccountRepo(int id, int accountRepoID){
//        if(id < 0){
//            return;
//        }
//        ContentValues values = new ContentValues();
//        values.put(INFO_COLUMN_ACCOUNT_REPO_ID, accountRepoID);
//        database.update(getAccountRepoTableName(), values, INFO_COLUMN_ID + " = ?", new String[]{Integer.toString(id)});
//    }
//
//    public void setSelectedUploadInfo(int id, String selectedInfo){
//        if(id < 0){
//            return;
//        }
//        ContentValues values = new ContentValues();
//        values.put(INFO_COLUMN_SELECTED_INFO, selectedInfo);
//        database.update(getAccountRepoTableName(), values, INFO_COLUMN_ID + " = ?", new String[]{Integer.toString(id)});
//    }
//
//    public void setInfoCompletedTime(int id, int completedTime){
//        if(id < 0){
//            return;
//        }
//        ContentValues values = new ContentValues();
//        values.put(INFO_COLUMN_COMPLETED_TIME, completedTime);
//        database.update(getAccountRepoTableName(), values, INFO_COLUMN_ID + " = ?", new String[]{Integer.toString(id)});
//    }
}