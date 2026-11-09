package com.htmlbuilder.project.vo;

/**
 * 点赞切换结果
 */
public class LikeResultVO {
    private Boolean liked;
    private Long likeCount;

    public LikeResultVO() {
    }

    public LikeResultVO(Boolean liked, Long likeCount) {
        this.liked = liked;
        this.likeCount = likeCount;
    }

    public Boolean getLiked() { return liked; }
    public void setLiked(Boolean liked) { this.liked = liked; }
    public Long getLikeCount() { return likeCount; }
    public void setLikeCount(Long likeCount) { this.likeCount = likeCount; }
}