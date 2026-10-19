package com.zachary.sitecheck.dto;

import com.zachary.sitecheck.entity.TemplateItem;

public record TemplateItemDto(Long id, Integer position, String title, String guidance) {

    public static TemplateItemDto from(TemplateItem item) {
        return new TemplateItemDto(item.getId(), item.getPosition(), item.getTitle(), item.getGuidance());
    }
}
