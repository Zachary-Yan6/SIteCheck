package com.zachary.sitecheck.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zachary.sitecheck.dto.UserResponse;
import com.zachary.sitecheck.entity.Role;
import com.zachary.sitecheck.entity.SiteInspector;
import com.zachary.sitecheck.entity.User;
import com.zachary.sitecheck.exception.ApiException;
import com.zachary.sitecheck.mapper.SiteInspectorMapper;
import com.zachary.sitecheck.mapper.SiteMapper;
import com.zachary.sitecheck.mapper.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Managers deciding who works on which site ({@code site_inspectors}). That table drives both what
 * an inspector downloads at bootstrap and the server-side access check in {@link SiteAccessService}.
 */
@Service
public class SiteAssignmentService {

    private final SiteMapper siteMapper;
    private final UserMapper userMapper;
    private final SiteInspectorMapper siteInspectorMapper;

    public SiteAssignmentService(SiteMapper siteMapper, UserMapper userMapper, SiteInspectorMapper siteInspectorMapper) {
        this.siteMapper = siteMapper;
        this.userMapper = userMapper;
        this.siteInspectorMapper = siteInspectorMapper;
    }

    /** Every inspector account, including ones that signed up themselves and have no sites yet. */
    @Transactional(readOnly = true)
    public List<UserResponse> inspectors() {
        return userMapper.selectList(Wrappers.<User>lambdaQuery()
                        .eq(User::getRole, Role.INSPECTOR)
                        .orderByAsc(User::getFullName))
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> assignedTo(Long siteId) {
        requireSite(siteId);
        return userMapper.selectAssignedToSite(siteId).stream().map(UserResponse::from).toList();
    }

    /** Idempotent: assigning someone who is already on the site changes nothing. */
    @Transactional
    public void assign(Long siteId, Long userId) {
        requireSite(siteId);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw ApiException.notFound("User not found");
        }
        if (user.getRole() != Role.INSPECTOR) {
            throw ApiException.badRequest("Only inspectors can be assigned to sites");
        }
        siteInspectorMapper.insertIfAbsent(siteId, userId);
    }

    /** Idempotent: removing someone who isn't on the site changes nothing. */
    @Transactional
    public void unassign(Long siteId, Long userId) {
        requireSite(siteId);
        siteInspectorMapper.delete(Wrappers.<SiteInspector>lambdaQuery()
                .eq(SiteInspector::getSiteId, siteId)
                .eq(SiteInspector::getUserId, userId));
    }

    private void requireSite(Long siteId) {
        if (siteMapper.selectById(siteId) == null) {
            throw ApiException.notFound("Site not found");
        }
    }
}
