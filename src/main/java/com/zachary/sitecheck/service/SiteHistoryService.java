package com.zachary.sitecheck.service;

import com.zachary.sitecheck.dto.SiteInspectionDto;
import com.zachary.sitecheck.mapper.SiteHistoryMapper;
import com.zachary.sitecheck.mapper.SiteHistoryMapper.FailedTitleRow;
import com.zachary.sitecheck.mapper.SiteHistoryMapper.HistoryRow;
import com.zachary.sitecheck.security.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteHistoryService {

    public static final int DEFAULT_LIMIT = 30;
    static final int MAX_LIMIT = 100;

    private final SiteHistoryMapper historyMapper;
    private final SiteAccessService siteAccessService;

    public SiteHistoryService(SiteHistoryMapper historyMapper, SiteAccessService siteAccessService) {
        this.historyMapper = historyMapper;
        this.siteAccessService = siteAccessService;
    }

    /**
     * The site's most recent inspections by anyone (FR-09) - so an inspector also sees what a
     * colleague found last week. Inspectors only for their own sites, managers for all.
     */
    @Transactional(readOnly = true)
    public List<SiteInspectionDto> history(AuthUser user, Long siteId, int limit) {
        siteAccessService.requireCanView(user, siteId);

        List<HistoryRow> rows = historyMapper.selectHistory(siteId, Math.clamp(limit, 1, MAX_LIMIT));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(HistoryRow::id).toList();
        Map<UUID, List<String>> failedTitles = historyMapper.selectFailedTitles(ids).stream()
                .collect(Collectors.groupingBy(FailedTitleRow::inspectionId,
                        Collectors.mapping(FailedTitleRow::title, Collectors.toList())));

        return rows.stream()
                .map(row -> new SiteInspectionDto(row.id(), row.templateName(), row.inspectorName(),
                        row.submittedAt(), row.itemCount().intValue(), row.failCount().intValue(),
                        failedTitles.getOrDefault(row.id(), List.of())))
                .toList();
    }
}
