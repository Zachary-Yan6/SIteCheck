package com.zachary.sitecheck.client;

import com.zachary.sitecheck.config.PushProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Sends notifications through Expo's push service (FR-29), which forwards them to Google (FCM)
 * or Apple (APNs). One HTTP call per 100 messages, Expo's limit per request.
 */
@Component
public class ExpoPushClient implements PushSender {

    static final int MAX_MESSAGES_PER_REQUEST = 100;

    private final RestClient restClient;
    private final String sendUrl;

    public ExpoPushClient(RestClient.Builder builder, PushProperties properties) {
        builder.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (StringUtils.hasText(properties.accessToken())) {
            // Only needed if "enhanced push security" is switched on for the Expo project.
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken());
        }
        this.restClient = builder.build();
        this.sendUrl = properties.expoUrl();
    }

    @Override
    public List<PushTicket> send(List<PushMessage> messages) {
        List<PushTicket> tickets = new ArrayList<>(messages.size());
        for (int start = 0; start < messages.size(); start += MAX_MESSAGES_PER_REQUEST) {
            List<PushMessage> batch = messages.subList(start, Math.min(start + MAX_MESSAGES_PER_REQUEST, messages.size()));
            ExpoResponse response = restClient.post()
                    .uri(sendUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(batch)
                    .retrieve()
                    .body(ExpoResponse.class);
            if (response == null || response.data() == null || response.data().size() != batch.size()) {
                throw new IllegalStateException("Unexpected response from Expo push service: " + response);
            }
            tickets.addAll(response.data());
        }
        return tickets;
    }

    /** Expo's response body: one ticket per message, in order. */
    record ExpoResponse(List<PushTicket> data) {
    }
}
