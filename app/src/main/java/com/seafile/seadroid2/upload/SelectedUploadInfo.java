package com.seafile.seadroid2.upload;

public class SelectedUploadInfo {
    public int id = -1;
    public int accountRepoID = -1;
    public int syncType = 0;
    public String selectedInfo = null;
    public int completedTime = -1;
    public SelectedUploadInfo(int id, int accountRepoID, int syncType, String selectedInfo, int completedTime){
        this.id = id;
        this.accountRepoID = accountRepoID;
        this.syncType = syncType;
        this.selectedInfo = selectedInfo;
        this.completedTime = completedTime;
    }
    public SelectedUploadInfo(){

    }
}
