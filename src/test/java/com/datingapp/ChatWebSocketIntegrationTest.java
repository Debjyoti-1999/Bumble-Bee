package com.datingapp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketIntegrationTest extends ApiTestSupport {

    @LocalServerPort
    private int port;

    private final StandardWebSocketClient client = new StandardWebSocketClient();
    private final List<WebSocketSession> openSessions = new ArrayList<>();

    /** Collects frames received by one client connection. */
    private static final class Inbox extends TextWebSocketHandler {
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            frames.add(message.getPayload());
        }
    }

    private record Connection(WebSocketSession session, Inbox inbox) {
    }

    @AfterEach
    void closeSessions() throws Exception {
        for (WebSocketSession s : openSessions) {
            if (s.isOpen()) s.close();
        }
    }

    private Connection connect(TestUser user) throws Exception {
        return connect("ws://localhost:" + port + "/ws/chat?access_token=" + user.token());
    }

    private Connection connect(String uri) throws Exception {
        Inbox inbox = new Inbox();
        WebSocketSession session = client.execute(inbox, new WebSocketHttpHeaders(), URI.create(uri))
                .get(5, TimeUnit.SECONDS);
        openSessions.add(session);
        return new Connection(session, inbox);
    }

    private JsonNode nextFrame(Connection c) throws Exception {
        String frame = c.inbox().frames.poll(5, TimeUnit.SECONDS);
        assertThat(frame).as("expected a WebSocket frame").isNotNull();
        return json.readTree(frame);
    }

    private void send(Connection c, Object payload) throws Exception {
        c.session().sendMessage(new TextMessage(json.writeValueAsString(payload)));
    }

    @Test
    void handshakeWithoutValidTokenIsRejected() {
        assertThatThrownBy(() -> connect("ws://localhost:" + port + "/ws/chat"))
                .isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect("ws://localhost:" + port + "/ws/chat?access_token=garbage"))
                .isInstanceOf(ExecutionException.class);
        // The old scheme (?userId=) must not authenticate anyone.
        assertThatThrownBy(() -> connect("ws://localhost:" + port + "/ws/chat?userId=1"))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void bothUsersAreNotifiedOfANewMatch() throws Exception {
        TestUser alice = register("Alice");
        TestUser bob = register("Bob");
        Connection aliceWs = connect(alice);
        Connection bobWs = connect(bob);

        long matchId = createMatch(alice, bob);

        JsonNode toAlice = nextFrame(aliceWs);
        assertThat(toAlice.get("type").asString()).isEqualTo("MATCH");
        assertThat(toAlice.get("matchId").asLong()).isEqualTo(matchId);
        assertThat(toAlice.get("matchedUserId").asLong()).isEqualTo(bob.id());
        assertThat(toAlice.get("matchedUserName").asString()).isEqualTo("Bob");

        JsonNode toBob = nextFrame(bobWs);
        assertThat(toBob.get("type").asString()).isEqualTo("MATCH");
        assertThat(toBob.get("matchedUserName").asString()).isEqualTo("Alice");
    }

    @Test
    void webSocketMessageIsDeliveredToRecipientAndEchoedToSender() throws Exception {
        TestUser alice = register("Alice");
        TestUser bob = register("Bob");
        long matchId = createMatch(alice, bob);
        Connection aliceWs = connect(alice);
        Connection bobWs = connect(bob);

        send(aliceWs, Map.of("type", "CHAT", "matchId", matchId, "content", "Hello Bob"));

        JsonNode received = nextFrame(bobWs);
        assertThat(received.get("type").asString()).isEqualTo("CHAT");
        assertThat(received.get("content").asString()).isEqualTo("Hello Bob");
        assertThat(received.get("senderId").asLong()).isEqualTo(alice.id());
        assertThat(received.get("sentAt").asString()).endsWith("Z");

        JsonNode echo = nextFrame(aliceWs);
        assertThat(echo.get("messageId").asLong()).isEqualTo(received.get("messageId").asLong());
    }

    @Test
    void restMessageIsPushedToRecipient() throws Exception {
        TestUser alice = register("Alice");
        TestUser bob = register("Bob");
        long matchId = createMatch(alice, bob);
        Connection bobWs = connect(bob);

        sendMessage(alice, matchId, "Sent over REST");

        JsonNode received = nextFrame(bobWs);
        assertThat(received.get("type").asString()).isEqualTo("CHAT");
        assertThat(received.get("content").asString()).isEqualTo("Sent over REST");
    }

    @Test
    void badFramesGetAnErrorReplyAndTheConnectionStaysOpen() throws Exception {
        TestUser alice = register("Alice");
        TestUser bob = register("Bob");
        TestUser outsider = register("Outsider");
        long matchId = createMatch(alice, bob);
        Connection outsiderWs = connect(outsider);

        outsiderWs.session().sendMessage(new TextMessage("{not json"));
        assertThat(nextFrame(outsiderWs).get("type").asString()).isEqualTo("ERROR");

        send(outsiderWs, Map.of("type", "CHAT", "content", "missing match id"));
        assertThat(nextFrame(outsiderWs).get("message").asString()).contains("matchId");

        send(outsiderWs, Map.of("type", "CHAT", "matchId", matchId, "content", "let me in"));
        assertThat(nextFrame(outsiderWs).get("message").asString()).isEqualTo("Match not found");

        Connection aliceWs = connect(alice);
        send(aliceWs, Map.of("type", "CHAT", "matchId", matchId, "content", " "));
        assertThat(nextFrame(aliceWs).get("type").asString()).isEqualTo("ERROR");

        assertThat(outsiderWs.session().isOpen()).isTrue();
        assertThat(aliceWs.session().isOpen()).isTrue();
    }
}
