package com.zachary.sitecheck.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zachary.sitecheck.dto.BootstrapResponse;
import com.zachary.sitecheck.dto.ScheduleDto;
import com.zachary.sitecheck.dto.SiteDto;
import com.zachary.sitecheck.dto.TemplateDto;
import com.zachary.sitecheck.dto.TemplateItemDto;
import com.zachary.sitecheck.entity.InspectionTemplate;
import com.zachary.sitecheck.entity.Role;
import com.zachary.sitecheck.entity.Site;
import com.zachary.sitecheck.entity.SiteTemplate;
import com.zachary.sitecheck.entity.TemplateItem;
import com.zachary.sitecheck.mapper.InspectionMapper;
import com.zachary.sitecheck.mapper.InspectionTemplateMapper;
import com.zachary.sitecheck.mapper.LastInspection;
import com.zachary.sitecheck.mapper.SiteMapper;
import com.zachary.sitecheck.mapper.SiteTemplateMapper;
import com.zachary.sitecheck.mapper.TemplateItemMapper;
import com.zachary.sitecheck.security.AuthUser;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncService {

    private final SiteMapper siteMapper;
    private final SiteTemplateMapper siteTemplateMapper;
    private final InspectionMapper inspectionMapper;
    private final InspectionTemplateMapper templateMapper;
    private final TemplateItemMapper templateItemMapper;

    public SyncService(SiteMapper siteMapper, SiteTemplateMapper siteTemplateMapper,
                       InspectionMapper inspectionMapper, InspectionTemplateMapper templateMapper,
                       TemplateItemMapper templateItemMapper) {
        this.siteMapper = siteMapper;
        this.siteTemplateMapper = siteTemplateMapper;
        this.inspectionMapper = inspectionMapper;
        this.templateMapper = templateMapper;
        this.templateItemMapper = templateItemMapper;
    }

    /**
     * Inspectors get only their assigned sites (FR-02); managers get every site.
     * Only templates used by those sites are included. Runs in one read-only transaction
     * so the five queries see a consistent snapshot.
     */
    @Transactional(readOnly = true)
    public BootstrapResponse bootstrap(AuthUser user) {
        // all sites
        List<Site> sites = user.role() == Role.MANAGER
                ? siteMapper.selectList(Wrappers.<Site>lambdaQuery().orderByAsc(Site::getName))
                : siteMapper.selectAssignedTo(user.id());
        if (sites.isEmpty()) {
            return new BootstrapResponse(Instant.now(), List.of(), List.of());
        }

        // all sites Id
        List<Long> siteIds = sites.stream().map(Site::getId).toList();

        // all templates that queried sites use.
        List<SiteTemplate> siteTemplates = siteTemplateMapper.selectList(
                Wrappers.<SiteTemplate>lambdaQuery().in(SiteTemplate::getSiteId, siteIds));

        // (site, template) -> when it was last inspected; pairs never inspected are missing
        Map<SiteTemplateKey, OffsetDateTime> lastInspected = inspectionMapper.selectLastInspections(siteIds).stream()
                .collect(Collectors.toMap(
                        last -> new SiteTemplateKey(last.siteId(), last.templateId()),
                        LastInspection::lastSubmittedAt));


        // site id -> its schedules for every template
        //4 (Galway) → [ ScheduleDto(templateId=1, lastInspectedAt=2026-10-12 14:00) ]
        // return for every template, last time this template is checked in this site.
        Map<Long, List<ScheduleDto>> schedulesBySite = siteTemplates.stream()
                .collect(Collectors.groupingBy(
                        SiteTemplate::getSiteId, Collectors.mapping(
                        st -> new ScheduleDto(st.getTemplateId(),
                                lastInspected.get(new SiteTemplateKey(st.getSiteId(), st.getTemplateId()))),
                        Collectors.toList()
                        )
                ));

        // return a list contains site and its scheduled template check.
        List<SiteDto> siteDtos = sites.stream()
                .map(site -> SiteDto.from(site, schedulesBySite.getOrDefault(site.getId(), List.of())))
                .toList();

        // return all template used.
        List<Long> templateIds = siteTemplates.stream()
                .map(SiteTemplate::getTemplateId)
                .distinct()
                .toList();

        // siteDtos includes a list contains site and its scheduled template check.
        // loadTemplates includes template includes its basic information and part of its item.
        return new BootstrapResponse(Instant.now(), siteDtos, loadTemplates(templateIds));
    }

    private List<TemplateDto> loadTemplates(List<Long> templateIds) {
        if (templateIds.isEmpty()) {
            return List.of();
        }
        // template id -> its items(what parts are used); groupingBy keeps the position order from the query
        //
        Map<Long, List<TemplateItemDto>> itemsByTemplate = templateItemMapper.selectList(
                        Wrappers.<TemplateItem>lambdaQuery()
                                .in(TemplateItem::getTemplateId, templateIds)
                                .orderByAsc(TemplateItem::getPosition))
                .stream()
                .collect(Collectors.groupingBy(TemplateItem::getTemplateId,
                        Collectors.mapping(TemplateItemDto::from, Collectors.toList())));

        // return List<TemplateDto>, template includes its basic information and part of its item.
        return templateMapper.selectByIds(templateIds).stream()
                .sorted(Comparator.comparing(InspectionTemplate::getName))
                .map(t -> TemplateDto.from(t, itemsByTemplate.getOrDefault(t.getId(), List.of())))
                .toList();
    }

    /** Map key for one (site, template) pair. */
    private record SiteTemplateKey(Long siteId, Long templateId) {
    }
}
