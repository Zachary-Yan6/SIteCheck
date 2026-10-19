package com.zachary.sitecheck.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/** Inspector assigned to a site - table {@code site_inspectors}; drives the FR-02 access check. */
@TableName("site_inspectors")
public class SiteInspector {

    private Long siteId;
    private Long userId;
    private OffsetDateTime assignedAt;

    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime assignedAt) { this.assignedAt = assignedAt; }
}
