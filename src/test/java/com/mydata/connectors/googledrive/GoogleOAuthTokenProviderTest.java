package com.mydata.connectors.googledrive;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleOAuthTokenProviderTest {
    private HttpServer server;
    private final List<String> requestBodies = new ArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void 토큰을_발급받고_만료_전에는_캐시를_재사용한다() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/token", exchange -> {
            calls.incrementAndGet();
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                {"access_token":"token-1","expires_in":3600,"token_type":"Bearer"}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        GoogleOAuthTokenProvider provider = provider(java.time.Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));

        assertThat(provider.accessToken()).isEqualTo("token-1");
        assertThat(provider.accessToken()).isEqualTo("token-1");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(requestBodies.getFirst())
            .contains("grant_type=refresh_token")
            .contains("client_id=client-1")
            .contains("client_secret=secret-1")
            .contains("refresh_token=refresh-1");
    }

    @Test
    void 만료가_지나면_토큰을_갱신한다() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/token", exchange -> {
            int call = calls.incrementAndGet();
            byte[] body = ("{\"access_token\":\"token-" + call + "\",\"expires_in\":1,\"token_type\":\"Bearer\"}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        MutableClock clock = new MutableClock(Instant.parse("2026-07-28T00:00:00Z"));
        GoogleOAuthTokenProvider provider = provider(clock);

        assertThat(provider.accessToken()).isEqualTo("token-1");
        clock.advanceSeconds(120);
        assertThat(provider.accessToken()).isEqualTo("token-2");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void 갱신_실패는_상태코드와_함께_예외를_던진다() {
        server.createContext("/token", exchange -> {
            byte[] body = "{\"error\":\"invalid_grant\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        GoogleOAuthTokenProvider provider = provider(java.time.Clock.systemUTC());

        assertThatThrownBy(provider::accessToken)
            .isInstanceOf(GoogleDriveApiException.class)
            .satisfies(error -> assertThat(((GoogleDriveApiException) error).statusCode()).isEqualTo(400));
    }

    private GoogleOAuthTokenProvider provider(java.time.Clock clock) {
        return new GoogleOAuthTokenProvider(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            URI.create("http://localhost:" + server.getAddress().getPort() + "/token"),
            "client-1",
            "secret-1",
            "refresh-1",
            Duration.ofSeconds(5),
            clock
        );
    }

    private static final class MutableClock extends java.time.Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
