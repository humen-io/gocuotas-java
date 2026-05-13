package io.humen.gocuotas.redirect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.humen.gocuotas.api.GoCuotasApiException;
import io.humen.gocuotas.redirect.model.CreateCheckoutRequest;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

@SuppressWarnings("resource")
class GoCuotasRedirectClientTest {

    @Test
    void getOrder_usesGetWithEncodedId() throws Exception {
        var method = new AtomicReference<String>();
        var path = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/",
                exchange -> {
                    method.set(exchange.getRequestMethod());
                    path.set(exchange.getRequestURI().getRawPath());
                    var json = "{\"id\":\"x\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(config);
            var body = client.getOrder("tok", "a/b");
            assertEquals("GET", method.get());
            assertTrue(path.get().endsWith("/api_redirect/v1/orders/a%2Fb"));
            assertTrue(body.contains("\"id\":\"x\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void authenticate_postsJsonWithoutAuthHeader() throws Exception {
        var captured = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api_redirect/v1/authentication",
                exchange -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(405, -1);
                        return;
                    }
                    captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    var json = "{\"token\":\"jwt-from-server\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(config);
            var auth = client.authenticate("seller@example.com", "secret");
            assertEquals("jwt-from-server", auth.getToken());
            var mapper = new ObjectMapper();
            var tree = mapper.readTree(captured.get());
            assertEquals("seller@example.com", tree.get("email").asText());
            assertEquals("secret", tree.get("password").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void createCheckout_sendsBearerAndReturnsUrlInit() throws Exception {
        var authHeader = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api_redirect/v1/checkouts",
                exchange -> {
                    authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    var json = "{\"url_init\":\"https://pay.gocuotas.example/start\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(201, json.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(config);
            var req = CreateCheckoutRequest.builder()
                    .amountInCents(10_000L)
                    .email("buyer@example.com")
                    .orderReferenceId("ORD-1")
                    .phoneNumber("3515551234")
                    .urlSuccess("https://shop.example/ok")
                    .urlFailure("https://shop.example/ko")
                    .webhookUrl(null)
                    .build();
            var res = client.createCheckout("api-key-or-jwt", req);
            assertEquals("Bearer api-key-or-jwt", authHeader.get());
            assertEquals("https://pay.gocuotas.example/start", res.getUrlInit());
            var mapper = new ObjectMapper();
            var tree = mapper.readTree(body.get());
            assertEquals(10_000, tree.get("amount_in_cents").asLong());
            assertEquals("ORD-1", tree.get("order_reference_id").asText());
            assertTrue(!tree.has("webhook_url") || tree.get("webhook_url").isNull());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void createCheckout_withoutBearerString_usesApiKeySupplierSameAsEnvOverload() throws Exception {
        var authHeader = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api_redirect/v1/checkouts",
                exchange -> {
                    authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    var json = "{\"url_init\":\"https://pay.example/u\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(201, json.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(
                    config, HttpClient.newHttpClient(), new ObjectMapper(), () -> "key-from-GOCUOTAS_API_KEY");
            var req = CreateCheckoutRequest.builder()
                    .amountInCents(1L)
                    .email("e@e.e")
                    .orderReferenceId("r")
                    .phoneNumber("1")
                    .urlSuccess("https://a")
                    .urlFailure("https://b")
                    .build();
            var res = client.createCheckout(req);
            assertEquals("Bearer key-from-GOCUOTAS_API_KEY", authHeader.get());
            assertEquals("https://pay.example/u", res.getUrlInit());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void apiError_throwsGoCuotasApiException() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api_redirect/v1/checkouts",
                exchange -> {
                    var msg = "{\"error\":\"invalid_amount\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(422, msg.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(msg.getBytes(StandardCharsets.UTF_8));
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(config);
            var req = CreateCheckoutRequest.builder()
                    .amountInCents(1L)
                    .email("a@b.c")
                    .orderReferenceId("x")
                    .phoneNumber("1")
                    .urlSuccess("https://a")
                    .urlFailure("https://b")
                    .build();
            var ex = assertThrows(GoCuotasApiException.class, () -> client.createCheckout("t", req));
            assertEquals(422, ex.statusCode());
            assertTrue(ex.getMessage().contains("422"));
            assertTrue(ex.responseBody().orElse("").contains("invalid_amount"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refundOrder_usesDeleteWithJsonHeadersAndBody() throws Exception {
        var method = new AtomicReference<String>();
        var contentType = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api_redirect/v1/orders/ref-42",
                exchange -> {
                    method.set(exchange.getRequestMethod());
                    contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                    if ("DELETE".equals(exchange.getRequestMethod())) {
                        var json = "[{\"id\":7,\"amount_in_cents\":100,\"status\":\"refunded\"}]";
                        var bytes = json.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(config);
            var body = client.refundOrder("tok", "ref-42");
            assertEquals("DELETE", method.get());
            assertEquals("application/json", contentType.get());
            assertTrue(body.contains("\"id\":7"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reembolsarOrden_parsesJsonArray() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api_redirect/v1/orders/x-1",
                exchange -> {
                    if ("DELETE".equals(exchange.getRequestMethod())) {
                        var json = "[{\"id\":1,\"amount_in_cents\":50,\"status\":\"ok\"}]";
                        var bytes = json.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(config);
            var node = client.reembolsarOrden("tok", "x-1");
            assertTrue(node.isArray());
            assertEquals(1, node.size());
            assertEquals(1, node.get(0).get("id").asInt());
            assertEquals("ok", node.get(0).get("status").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getOrder_http404_throwsOrderNotFoundException() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api_redirect/v1/orders/absent-99",
                exchange -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(405, -1);
                        return;
                    }
                    var err = "{\"message\":\"not found\"}";
                    var bytes = err.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(404, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(config);
            var ex = assertThrows(OrderNotFoundException.class, () -> client.getOrder("tok", "absent-99"));
            assertEquals("absent-99", ex.orderId());
            assertEquals(OrderNotFoundException.HTTP_STATUS, ex.statusCode());
            assertTrue(ex.responseBody().orElse("").contains("not found"));
            assertTrue(ex.getMessage().contains("404"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void buscarOrden_http404_isOrderNotFoundException() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api_redirect/v1/orders/missing-ref",
                exchange -> exchange.sendResponseHeaders(404, -1));
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(config);
            var ex = assertThrows(OrderNotFoundException.class, () -> client.buscarOrden("tok", "missing-ref"));
            assertEquals("missing-ref", ex.orderId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refundOrder_http404_throwsOrderNotFoundException() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api_redirect/v1/orders/gone-7",
                exchange -> {
                    if (!"DELETE".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(405, -1);
                        return;
                    }
                    exchange.sendResponseHeaders(404, -1);
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(config);
            var ex = assertThrows(OrderNotFoundException.class, () -> client.refundOrder("tok", "gone-7"));
            assertEquals("gone-7", ex.orderId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void listOrders_http404_isPlainGoCuotasApiException() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api_redirect/v1/orders", exchange -> exchange.sendResponseHeaders(404, -1));
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(config);
            var ex = assertThrows(GoCuotasApiException.class, () -> client.listOrders("tok", "2020-01-01 00:00", "2020-02-01 00:00"));
            assertEquals(404, ex.statusCode());
            assertFalse(ex instanceof OrderNotFoundException);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void listOrders_usesGetWithDeliveredQueryParams() throws Exception {
        var method = new AtomicReference<String>();
        var rawQuery = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api_redirect/v1/orders",
                exchange -> {
                    method.set(exchange.getRequestMethod());
                    rawQuery.set(exchange.getRequestURI().getRawQuery());
                    var json = "[]";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(config);
            assertEquals("[]", client.listOrders("tok", "2021-01-19 10:00", "2024-12-19 20:00"));
            assertEquals("GET", method.get());
            assertTrue(rawQuery.get().contains("delivered_start="));
            assertTrue(rawQuery.get().contains("delivered_end="));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void listarOrdenes_parsesJsonArray() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api_redirect/v1/orders",
                exchange -> {
                    var json = "[{\"order_reference_id\":\"A\"}]";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(config);
            var node = client.listarOrdenes("tok", "2021-01-19 10:00", "2024-12-19 20:00");
            assertTrue(node.isArray());
            assertEquals("A", node.get(0).get("order_reference_id").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void buscarOrden_parsesJsonObject() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/",
                exchange -> {
                    var json = "{\"order_reference_id\":\"PED-1\",\"status\":\"approved\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasRedirectConfig(base, Duration.ofSeconds(5));
            var client = new GoCuotasRedirectClient(config);
            var node = client.buscarOrden("tok", "PED-1");
            assertTrue(node.isObject());
            assertEquals("PED-1", node.get("order_reference_id").asText());
            assertEquals("approved", node.get("status").asText());
        } finally {
            server.stop(0);
        }
    }
}
