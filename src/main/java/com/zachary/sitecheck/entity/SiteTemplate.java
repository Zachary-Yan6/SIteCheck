package com.zachary.sitecheck.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/** Template required at a site - table {@code site_templates}; drives overdue sorting (FR-08). */
@TableName("site_templates")
public class SiteTemplate {

    private Long siteId;
    private Long templateId;

    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
}
