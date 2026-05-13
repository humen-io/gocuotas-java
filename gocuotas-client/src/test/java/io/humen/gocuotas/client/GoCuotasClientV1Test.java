package io.humen.gocuotas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

@SuppressWarnings("resource")
class GoCuotasClientV1Test {

    @Test
    void obtenerInformacionComercio_sendsBearerAcceptAndParsesBody() throws Exception {
        var auth = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api_client/v1/client",
                exchange -> {
                    auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    var accept = exchange.getRequestHeaders().getFirst("Accept");
                    if (!"application/json".equals(accept)) {
                        exchange.sendResponseHeaders(400, -1);
                        return;
                    }
                    var json =
                            "{\"id\":3851003,\"name\":\"Humen delicias\",\"cuit\":\"20340931530\","
                                    + "\"surcharge_percentage_to_online_orders\":\"0.0\","
                                    + "\"max_number_of_installments\":3}";
                    var bytes = json.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasClientV1Config(base, Duration.ofSeconds(5));
            var client = new GoCuotasClientV1(config, HttpClient.newHttpClient(), new ObjectMapper(), () -> "api-key-x");
            var info = client.obtenerInformacionComercio();
            assertEquals("Bearer api-key-x", auth.get());
            assertEquals(3851003L, info.getId());
            assertEquals("Humen delicias", info.getName());
            assertEquals("20340931530", info.getCuit());
            assertEquals("0.0", info.getSurchargePercentageToOnlineOrders());
            assertEquals(3, info.getMaxNumberOfInstallments());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void listarLiquidaciones_sendsBearerAcceptAndParsesArray() throws Exception {
        var auth = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api_client/v1/expense_settlements",
                exchange -> {
                    auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    var json =
                            "[{\"id\":1453328,\"payment_expense_method\":\"transferencia\","
                                    + "\"payment_expense_at\":\"2026-05-12\",\"due_expense_at\":\"2026-05-12\","
                                    + "\"payment_expense_retained_amount_in_cents\":0,"
                                    + "\"payment_expense_amount_in_cents\":2274953}]";
                    var bytes = json.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasClientV1Config(base, Duration.ofSeconds(5));
            var client = new GoCuotasClientV1(config, HttpClient.newHttpClient(), new ObjectMapper(), () -> "k1");
            var list = client.listarLiquidaciones();
            assertEquals("Bearer k1", auth.get());
            assertEquals(1, list.size());
            var l = list.get(0);
            assertEquals(1453328L, l.getId());
            assertEquals("transferencia", l.getPaymentExpenseMethod());
            assertEquals("2026-05-12", l.getPaymentExpenseAt());
            assertEquals("2026-05-12", l.getDueExpenseAt());
            assertEquals(0L, l.getPaymentExpenseRetainedAmountInCents());
            assertEquals(2274953L, l.getPaymentExpenseAmountInCents());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void obtenerInformacionLiquidacion_parsesDetailBody() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        var json =
                "{\"id\":1453328,\"payment_expense_method\":\"transferencia\","
                        + "\"payment_expense_at\":\"2026-05-12\",\"due_expense_at\":\"2026-05-12\","
                        + "\"payment_expense_retained_amount_in_cents\":0,\"payment_expense_amount_in_cents\":2274953,"
                        + "\"details\":[{\"id\":99,\"description\":\"Pedido\",\"delivered_at\":\"2026-05-01\","
                        + "\"due_expense_at\":\"2026-05-12\",\"amount_in_cents\":100,\"commission_amount_in_cents\":1,"
                        + "\"tax_amount_in_cents\":2,\"expense_amount_in_cents\":3,\"discarded_at\":null,"
                        + "\"status\":\"approved\",\"number_of_installments\":3,\"order_reference_id\":\"ORD-1\","
                        + "\"payment\":{\"card\":{\"number\":\"406651******6008\",\"name\":\"Visa\"}}}],"
                        + "\"normal_retention_retain_paid_orders\":[{}],\"tax_retention_retain_paid_orders\":[]}";
        server.createContext(
                "/api_client/v1/expense_settlements/1453328",
                exchange -> {
                    var bytes = json.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasClientV1Config(base, Duration.ofSeconds(5));
            var client = new GoCuotasClientV1(config, HttpClient.newHttpClient(), new ObjectMapper(), () -> "k1");
            var info = client.obtenerInformacionLiquidacion("1453328");
            assertEquals(1453328L, info.getId());
            assertEquals("transferencia", info.getPaymentExpenseMethod());
            assertEquals(1, info.getDetails().size());
            var d = info.getDetails().get(0);
            assertEquals(99L, d.getId());
            assertEquals("406651******6008", d.getPayment().getCard().getNumber());
            assertEquals(1, info.getNormalRetentionRetainPaidOrders().size());
            assertEquals(0, info.getTaxRetentionRetainPaidOrders().size());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void obtenerInformacionLiquidacionesTextoPlano_requestsAcceptTextPlain() throws Exception {
        var accept = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        var csv =
                "ID,Método de Pago,Fecha de Pago,Fecha de Vencimiento,Monto Retenido,Monto Total\n"
                        + "1453328,transferencia,12/05/2026,12/05/2026,0.0,22749.53\n";
        server.createContext(
                "/api_client/v1/expense_settlements_csvs",
                exchange -> {
                    accept.set(exchange.getRequestHeaders().getFirst("Accept"));
                    var bytes = csv.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasClientV1Config(base, Duration.ofSeconds(5));
            var client = new GoCuotasClientV1(config, HttpClient.newHttpClient(), new ObjectMapper(), () -> "k1");
            var body = client.obtenerInformacionLiquidacionesTextoPlano();
            assertEquals("text/plain", accept.get());
            assertTrue(body.contains("ID,Método de Pago"));
            assertTrue(body.contains("1453328,transferencia"));
            assertTrue(body.contains("22749.53"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void obtenerLiquidacionTextoPlano_requestsPathWithIdAndAcceptTextPlain() throws Exception {
        var pathSeen = new AtomicReference<String>();
        var accept = new AtomicReference<String>();
        var csv =
                "Descripcion,Fecha Origen,Fecha Pago\n"
                        + "Ventas,30/01/2026,12/05/2026\n"
                        + "Totales,\"\",\"\"\n";
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/",
                exchange -> {
                    pathSeen.set(exchange.getRequestURI().getPath());
                    accept.set(exchange.getRequestHeaders().getFirst("Accept"));
                    var bytes = csv.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var config = new GoCuotasClientV1Config(base, Duration.ofSeconds(5));
            var client = new GoCuotasClientV1(config, HttpClient.newHttpClient(), new ObjectMapper(), () -> "k1");
            var body = client.obtenerLiquidacionTextoPlano("1453328");
            assertTrue(pathSeen.get().endsWith("/api_client/v1/expense_settlements_csvs/1453328"));
            assertEquals("text/plain", accept.get());
            assertTrue(body.contains("Descripcion,Fecha Origen"));
            assertTrue(body.contains("Ventas,30/01/2026"));
        } finally {
            server.stop(0);
        }
    }
}
