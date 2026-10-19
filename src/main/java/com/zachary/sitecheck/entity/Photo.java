package com.zachary.sitecheck.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Photo evidence for a checklist item - table {@code photos} (FR-13). */
@TableName("photos")
public class Photo {

    @TableId(type = IdType.INPUT)  // UUID generated on the phone
    private UUID id;
    private UUID inspectionId;
    private Long templateItemId;
    private String s3Key;
    private PhotoStatus status;
    private OffsetDateTime takenAt;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private OffsetDateTime uploadedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getInspectionId() { return inspectionId; }
    public void setInspectionId(UUID inspectionId) { this.inspectionId = inspectionId; }

    public Long getTemplateItemId() { return templateItemId; }
    public void setTemplateItemId(Long templateItemId) { this.templateItemId = templateItemId; }

    public String getS3Key() { return s3Key; }
    public void setS3Key(String s3Key) { this.s3Key = s3Key; }

    public PhotoStatus getStatus() { return status; }
    public void setStatus(PhotoStatus status) { this.status = status; }

    public OffsetDateTime getTakenAt() { return takenAt; }
    public void setTakenAt(OffsetDateTime takenAt) { this.takenAt = takenAt; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
