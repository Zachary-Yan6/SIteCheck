package com.zachary.sitecheck.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.util.UUID;

/** The answer to one checklist item - table {@code inspection_results}. */
@TableName("inspection_results")
public class InspectionResult {

    private UUID inspectionId;
    private Long templateItemId;
    private ItemResult result;
    private String note;

    public UUID getInspectionId() { return inspectionId; }
    public void setInspectionId(UUID inspectionId) { this.inspectionId = inspectionId; }

    public Long getTemplateItemId() { return templateItemId; }
    public void setTemplateItemId(Long templateItemId) { this.templateItemId = templateItemId; }

    public ItemResult getResult() { return result; }
    public void setResult(ItemResult result) { this.result = result; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
