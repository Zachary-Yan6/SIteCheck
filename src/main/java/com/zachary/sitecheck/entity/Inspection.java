package com.zachary.sitecheck.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A submitted, read-only inspection - table {@code inspections}. */
@TableName("inspections")
public class Inspection {

    @TableId(type = IdType.INPUT)  // UUID generated on the phone; also the idempotency key (FR-20)
    private UUID id;
    private Long siteId;
    private Long templateId;
    private Long inspectorId;
    private OffsetDateTime startedAt;
    private OffsetDateTime submittedAt;
    private OffsetDateTime receivedAt;
    private byte[] signaturePng;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public Long getInspectorId() { return inspectorId; }
    public void setInspectorId(Long inspectorId) { this.inspectorId = inspectorId; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }

    public byte[] getSignaturePng() { return signaturePng; }
    public void setSignaturePng(byte[] signaturePng) { this.signaturePng = signaturePng; }
}
