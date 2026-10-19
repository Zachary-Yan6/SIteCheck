package com.zachary.sitecheck.service;

import com.zachary.sitecheck.mapper.DeviceTokenMapper;
import com.zachary.sitecheck.security.AuthUser;
import org.springframework.stereotype.Service;

/** Push tokens of users' phones (FR-27): registered after login, removed on logout. */
@Service
public class DeviceService {

    private final DeviceTokenMapper deviceTokenMapper;

    public DeviceService(DeviceTokenMapper deviceTokenMapper) {
        this.deviceTokenMapper = deviceTokenMapper;
    }

    /**
     * Saves the token for this user. Registering the same token again just refreshes it, and a
     * token last used by someone else moves to this user - reminders follow whoever is logged in.
     */
    public void register(AuthUser user, String token, String platform) {
        deviceTokenMapper.upsert(token, user.id(), platform);
    }

    /** Removes the token if it belongs to this user; someone else's token is left alone. */
    public void unregister(AuthUser user, String token) {
        deviceTokenMapper.deleteOwn(token, user.id());
    }
}
