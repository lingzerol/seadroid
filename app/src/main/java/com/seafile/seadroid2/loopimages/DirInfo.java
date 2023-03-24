package com.seafile.seadroid2.loopimages;

import android.text.TextUtils;

import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SeadroidApplication;
import com.seafile.seadroid2.SettingsManager;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.AccountManager;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.SeafDirent;
import com.seafile.seadroid2.ui.NavContext;
import com.seafile.seadroid2.util.PinyinUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DirInfo {
    private String accountSignature;
    private String repoId;
    private String repoName;
    private String dirId;
    private String dirPath;

    public static final String LOOPIMAGES_ACCOUNT_SIGNATURE_KEY = "loopimages_account_signature";
    public static final String LOOPIMAGES_REPO_ID_KEY = "loopimages_repo_id";
    public static final String LOOPIMAGES_REPO_NAME_KEY = "loopimages_repo_name";
    public static final String LOOPIMAGES_DIR_ID_KEY = "loopimages_dir_id";
    public static final String LOOPIMAGES_DIR_PATH_KEY = "loopimages_dir_path";

    public static final String spliter = ",";

    public DirInfo(String accountSignature, NavContext nav){
        this.accountSignature = accountSignature;
        this.repoId = nav.getRepoID();
        this.repoName = nav.getRepoName();
        this.dirId = nav.getDirID();
        this.dirPath = nav.getDirPath();
    }

    public DirInfo(String accountSignature, Map<String, String> info){
        if(!info.containsKey(LOOPIMAGES_REPO_ID_KEY) ||
                !info.containsKey(LOOPIMAGES_REPO_NAME_KEY) ||
                !info.containsKey(LOOPIMAGES_DIR_ID_KEY) ||
                !info.containsKey(LOOPIMAGES_DIR_PATH_KEY)){
            this.accountSignature = null;
            this.repoId = null;
            this.repoName = null;
            this.dirId = null;
            this.dirPath = null;
        }else{
            this.accountSignature = accountSignature;
            this.repoId = info.get(LOOPIMAGES_REPO_ID_KEY);
            this.repoName = info.get(LOOPIMAGES_REPO_NAME_KEY);
            this.dirId = info.get(LOOPIMAGES_DIR_ID_KEY);
            this.dirPath = info.get(LOOPIMAGES_DIR_PATH_KEY);
        }
    }

    public DirInfo(String accountSignature, String repoId, String repoName, String dirId, String dirPath){
        this.accountSignature = accountSignature;
        this.repoId = repoId;
        this.repoName = repoName;
        this.dirId = dirId;
        this.dirPath = dirPath;
    }

    public String getAccountSignature(){
        return this.accountSignature;
    }

    public String getRepoId(){
        return this.repoId;
    }

    public String getRepoName(){
        return this.repoName;
    }

    public String getDirId(){
        return this.dirId;
    }

    public void setDirId(String dirId){
        this.dirId = dirId;
    }

    public String getDirPath(){
        return this.dirPath;
    }

//    public List<SeafDirent> getSeafDirents(){
//        DataManager dataManager = new DataManager(this.account);
//        return dataManager.getCachedDirents(this.repoId, this.dirPath);
//    }

    public Map<String, String> toMap(){
        Map<String, String> res = new HashMap<String, String>();
        res.put(LOOPIMAGES_ACCOUNT_SIGNATURE_KEY, this.accountSignature);
        res.put(LOOPIMAGES_REPO_ID_KEY, getRepoId());
        res.put(LOOPIMAGES_REPO_NAME_KEY, getRepoName());
        res.put(LOOPIMAGES_DIR_ID_KEY, getDirId());
        res.put(LOOPIMAGES_DIR_PATH_KEY, getDirPath());
        return res;
    }

    public String toString(){
        String s = "";
        s += getAccountSignature();
        s += spliter + getRepoId();
        s += spliter + getRepoName();
        s += spliter + getDirId();
        s += spliter + getDirPath();
        return s;
    }

    public static DirInfo getDirInfoFromStringList(List<String> dirInfo){
        if(dirInfo.size() != 5){
            return null;
        }
        return new DirInfo(dirInfo.get(0), dirInfo.get(1), dirInfo.get(2), dirInfo.get(3), dirInfo.get(4));
    }

    public static DirInfo getDirInfoFromString(String dirInfoStr){
        if(dirInfoStr == null || dirInfoStr.length() == 0){
            return null;
        }
        List<String> infoList = Arrays.asList(TextUtils.split(dirInfoStr, DirInfo.spliter));
        if(infoList.size() != 5){
            return null;
        }
        return getDirInfoFromStringList(infoList);
    }

    public static class DirInfoComparator implements Comparator<DirInfo> {

        @Override
        public int compare(DirInfo itemA, DirInfo itemB) {
            if(itemA.getAccountSignature().equals(itemB.getAccountSignature())){
                if(itemA.getRepoName() == itemB.getRepoName()){
                    return compareString(itemA.getDirPath(), itemB.getDirPath());
                }
                return compareString(itemA.getRepoName(), itemB.getRepoName());
            }
            return compareString(itemA.getAccountSignature(), itemB.getAccountSignature());
        }

        private int compareString(String itemA, String itemB){
            // get the first character unicode from each name
            int unicodeA = itemA.codePointAt(0);
            int unicodeB = itemB.codePointAt(0);

            String strA, strB;

            // both are Chinese words
            if ((19968 < unicodeA && unicodeA < 40869) && (19968 < unicodeB && unicodeB < 40869)) {
                strA = PinyinUtils.toPinyin(SeadroidApplication.getAppContext(), itemA).toLowerCase();
                strB = PinyinUtils.toPinyin(SeadroidApplication.getAppContext(), itemB).toLowerCase();
            } else if ((19968 < unicodeA && unicodeA < 40869) && !(19968 < unicodeB && unicodeB < 40869)) {
                // itemA is Chinese and itemB is English
                return 1;
            } else if (!(19968 < unicodeA && unicodeA < 40869) && (19968 < unicodeB && unicodeB < 40869)) {
                // itemA is English and itemB is Chinese
                return -1;
            } else {
                // both are English words
                strA = itemA.toLowerCase();
                strB = itemB.toLowerCase();
            }
            return strA.compareTo(strB);
        }
    }
}

