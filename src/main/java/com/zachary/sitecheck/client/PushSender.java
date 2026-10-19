package com.zachary.sitecheck.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** Sends push notifications. An interface so tests can check what would be sent without Expo. */
public interface PushSender {

    /** Returns one ticket per message, in the same order. */
    List<PushTicket> send(List<PushMessage> messages);

    /** One notification to one phone - the Expo push message format. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PushMessage(String to, String title, String body, Map<String, Object> data,
                       String channelId, String sound, String priority) {
    }

    /** Expo's answer for one message: "ok", or "error" with details.error (e.g. DeviceNotRegistered). */
    record PushTicket(String status, String id, String message, Map<String, Object> details) {

        public boolean deviceNotRegistered() {
            return "error".equals(status) && details != null && "DeviceNotRegistered".equals(details.get("error"));
        }
    }
}
