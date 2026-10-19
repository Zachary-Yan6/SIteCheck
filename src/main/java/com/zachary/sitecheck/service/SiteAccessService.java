package com.zachary.sitecheck.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zachary.sitecheck.entity.Role;
import com.zachary.sitecheck.entity.SiteInspector;
import com.zachary.sitecheck.exception.ApiException;
import com.zachary.sitecheck.mapper.SiteInspectorMapper;
import com.zachary.sitecheck.security.AuthUser;
import org.springframework.stereotype.Service;

/**
 * Server-side check that an inspector works on a site (FR-02). Every endpoint that takes a
 * site id from the client must call this - the app hiding other sites is not security.
 */
@Service
public class SiteAccessService {

    private final SiteInspectorMapper siteInspectorMapper;

    public SiteAccessService(SiteInspectorMapper siteInspectorMapper) {
        this.siteInspectorMapper = siteInspectorMapper;
    }

    /** Throws 403 unless the inspector is assigned to the site. */
    public void requireAssigned(AuthUser user, Long siteId) {
        boolean assigned = siteInspectorMapper.exists(Wrappers.<SiteInspector>lambdaQuery()
                .eq(SiteInspector::getSiteId, siteId)
                .eq(SiteInspector::getUserId, user.id()));
        if (!assigned) {
            // Same answer whether the site doesn't exist or belongs to someone else,
            // so the response does not reveal which site ids exist.
            throw ApiException.forbidden("You are not assigned to this site");
        }
    }

    /** Read access: managers may look at every site, inspectors only at their own. */
    public void requireCanView(AuthUser user, Long siteId) {
        if (user.role() != Role.MANAGER) {
            requireAssigned(user, siteId);
        }
    }
}
