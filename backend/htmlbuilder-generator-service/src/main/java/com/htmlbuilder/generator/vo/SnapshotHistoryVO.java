package com.htmlbuilder.generator.vo;

import java.util.List;

public class SnapshotHistoryVO {

    private List<String> branches;
    /** 该任务下存在快照的页面（用于前端按页切换历史） */
    private List<String> pages;
    private List<SnapshotVO> snapshots;

    public SnapshotHistoryVO(List<String> branches, List<String> pages, List<SnapshotVO> snapshots) {
        this.branches = branches;
        this.pages = pages;
        this.snapshots = snapshots;
    }

    public List<String> getBranches() { return branches; }
    public void setBranches(List<String> branches) { this.branches = branches; }
    public List<String> getPages() { return pages; }
    public void setPages(List<String> pages) { this.pages = pages; }
    public List<SnapshotVO> getSnapshots() { return snapshots; }
    public void setSnapshots(List<SnapshotVO> snapshots) { this.snapshots = snapshots; }
}