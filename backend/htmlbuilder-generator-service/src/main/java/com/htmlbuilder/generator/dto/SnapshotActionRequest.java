package com.htmlbuilder.generator.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 历史操作请求：restore 用 snapshotId；branch 同时需要 name。
 */
public class SnapshotActionRequest {

    @NotNull(message = "快照ID不能为空")
    private Long snapshotId;

    private String name;

    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}