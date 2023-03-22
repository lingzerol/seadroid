package com.seafile.seadroid2.upload;

import com.google.common.collect.Lists;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AccountRepoInfo {
    public int id = -1;
    public String accountSignature = null;
    public String repoID = null;
    public String repoName = null;
    public String dataPath = null;
    public boolean allowDataPlan = false;
    public String extraOptions = null;
    public Integer availableSyncs = 0;
    public boolean activate = false;

    public AccountRepoInfo(int accountRepoId, String accountSignature, String repoID, String repoName, String dataPath, boolean allowDataPlan, Integer availableSyncs, String extraOptions, boolean activate){
        this.id = accountRepoId;
        this.accountSignature = accountSignature;
        this.repoID = repoID;
        this.repoName = repoName;
        this.dataPath = dataPath;
        this.allowDataPlan = allowDataPlan;
        this.extraOptions = extraOptions;
        this.activate =activate;
        this.availableSyncs = availableSyncs;
    }

    public AccountRepoInfo(){

    }

//    public static Set<Integer> parseAvailableSyncs(String availableSyncs){
//        Set<Integer> res = new HashSet<Integer>();
//        for(String sync : availableSyncs.split(";")){
//            res.add(Integer.parseInt(sync));
//        }
//        return res;
//    }
//
//    public static String encodeAvailableSyncs(Set<Integer> availableSyncs){
//        StringBuilder res = new StringBuilder();
//        int i = 0;
//        for(Integer sync: availableSyncs){
//            res.append(sync);
//            if(i+1<availableSyncs.size()){
//                res.append(";");
//            }
//            ++i;
//        }
//        return res.toString();
//    }
    public boolean isSyncAvailable(int synType){
        return ((synType&this.availableSyncs) > 0);
    }
}
