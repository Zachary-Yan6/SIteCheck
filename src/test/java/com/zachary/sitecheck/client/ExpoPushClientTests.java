package com.zachary.sitecheck.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zachary.sitecheck.client.PushSender.PushMessage;
import com.zachary.sitecheck.client.PushSender.PushTicket;
import com.zachary.sitecheck.config.PushProperties;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The HTTP side of push: request format and ticket parsing, against a fake Expo server. */
class ExpoPushClientTests {

    private static final String URL = "https://expo.test/--/api/v2/push/send";

    @Test
    void sendsMessagesAndReadsTicketsInOrder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer expo = MockRestServiceServer.bindTo(builder).build();
        ExpoPushClient client = new ExpoPushClient(builder, new PushProperties(URL, "secret-token"));

        expo.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].to").value("ExponentPushToken[a]"))
                .andExpect(jsonPath("$[0].title").value("Cork Docklands Office Block"))
                .andExpect(jsonPath("$[0].data.url").value("/site/3"))
                .andExpect(jsonPath("$[0].channelId").value("reminders"))
                .andRespond(withSuccess("""
                        {"data": [
                          {"status": "ok", "id": "ticket-1"},
                          {"status": "error", "message": "not registered", "details": {"error": "DeviceNotRegistered"}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<PushTicket> tickets = client.send(List.of(message("ExponentPushToken[a]"), message("ExponentPushToken[b]")));

        expo.verify();
        assertThat(tickets).extracting(PushTicket::status).containsExactly("ok", "error");
        assertThat(tickets.get(0).deviceNotRegistered()).isFalse();
        assertThat(tickets.get(1).deviceNotRegistered()).isTrue();
    }

    @Test
    void splitsIntoRequestsOfAtMost100Messages() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer expo = MockRestServiceServer.bindTo(builder).build();
        ExpoPushClient client = new ExpoPushClient(builder, new PushProperties(URL, ""));

        expo.expect(ExpectedCount.once(), requestTo(URL))
                .andExpect(jsonPath("$.length()").value(100))
                .andRespond(withSuccess(okTickets(100), MediaType.APPLICATION_JSON));
        expo.expect(ExpectedCount.once(), requestTo(URL))
                .andExpect(jsonPath("$.length()").value(5))
                .andRespond(withSuccess(okTickets(5), MediaType.APPLICATION_JSON));

        List<PushTicket> tickets = client.send(
                IntStream.range(0, 105).mapToObj(i -> message("ExponentPushToken[" + i + "]")).toList());

        expo.verify();
        assertThat(tickets).hasSize(105);
    }

    @Test
    void wrongNumberOfTicketsIsAnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer expo = MockRestServiceServer.bindTo(builder).build();
        ExpoPushClient client = new ExpoPushClient(builder, new PushProperties(URL, null));
        expo.expect(requestTo(URL)).andRespond(withSuccess(okTickets(1), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.send(List.of(message("ExponentPushToken[a]"), message("ExponentPushToken[b]"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Unexpected response from Expo push service");
    }

    @Test
    void ticketWithoutDetailsIsNotDeviceNotRegistered() {
        assertThat(new PushTicket("error", null, "rate limited", null).deviceNotRegistered()).isFalse();
        assertThat(new PushTicket("error", null, "too big", Map.of("error", "MessageTooBig")).deviceNotRegistered()).isFalse();
    }

    private static PushMessage message(String token) {
        return new PushMessage(token, "Cork Docklands Office Block", "Scaffold Inspection due today",
                Map.of("url", "/site/3"), "reminders", "default", "high");
    }

    private static String okTickets(int count) {
        return "{\"data\": [" + String.join(",", java.util.Collections.nCopies(count, "{\"status\": \"ok\"}")) + "]}";
    }
}
