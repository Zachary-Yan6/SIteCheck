package com.zachary.sitecheck.dto;

import com.zachary.sitecheck.entity.InspectionTemplate;
import java.util.List;

/** A template with its checklist items, in position order. */
public record TemplateDto(Long id, String name, String description, Integer frequencyDays,
                          List<TemplateItemDto> items) {

    public static TemplateDto from(InspectionTemplate template, List<TemplateItemDto> items) {
        return new TemplateDto(template.getId(), template.getName(), template.getDescription(),
                template.getFrequencyDays(), items);
    }
}
