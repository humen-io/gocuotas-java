package io.humen.gocuotas.redirect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.humen.gocuotas.api.GoCuotasApiException;
import io.humen.gocuotas.redirect.model.AuthenticationRequest;
import io.humen.gocuotas.redirect.model.AuthenticationResponse;
import io.humen.gocuotas.redirect.model.CreateCheckoutRequest;
import io.humen.gocuotas.redirect.model.CreateCheckoutResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Cliente HTTP para la <a href="https://gocuotas-api.stoplight.io/docs/gocuotas/dae8814842a1e-api-redirect-v1">API Redirect V1</a> de GoCuotas.
 *
 * <p>Flujo habitual: (1) {@link #authenticate(String, String)} con email del comercio y contraseña (en entorno suele
 * ser la misma {@value #GOCUOTAS_API_KEY_ENV} del panel); (2) {@link #createCheckout(String, CreateCheckoutRequest)} o
 * {@link #createCheckout(CreateCheckoutRequest)} (checkout con Bearer = valor de {@value #GOCUOTAS_API_KEY_ENV}); (3)
 * redirigir al comprador a {@link CreateCheckoutResponse#getUrlInit()}.
 * <p>El listado de órdenes (GET {@value GoCuotasRedirectConfig#PATH_ORDERS}) exige query {@code delivered_start} y
 * {@code delivered_end} con formato {@code YYYY-MM-DD HH:mm} (según Stoplight). Con variables de entorno usá
 * {@value #GOCUOTAS_DELIVERED_START_ENV} y {@value #GOCUOTAS_DELIVERED_END_ENV}.
 * <p>Para {@link #listarOrdenes()}, {@link #buscarOrden(String)}, {@link #listOrders()}, {@link #getOrder(String)},
 * {@link #refundOrder(String)} y {@link #reembolsarOrden(String)} sin Bearer explícito: si está definida
 * {@value #GOCUOTAS_JWT_ENV}, se usa como Bearer;
 * si no, se llama a {@link #authenticate(String, String)} con {@value #GOCUOTAS_EMAIL_ENV} y
 * {@value #GOCUOTAS_API_KEY_ENV} (contraseña) y el JWT resultante se reutiliza en la misma instancia del cliente.
 */
public final class GoCuotasRedirectClient {

    /** JWT para operaciones de órdenes sin Bearer explícito; si está definido, tiene prioridad sobre authenticate. */
    public static final String GOCUOTAS_JWT_ENV = "GOCUOTAS_JWT";

    /** Email del comercio (panel GoCuotas) para obtener JWT usado en operaciones de órdenes vía variables de entorno. */
    public static final String GOCUOTAS_EMAIL_ENV = "GOCUOTAS_EMAIL";

    /** Nombre de la variable de entorno: API key del panel; como contraseña de {@link #authenticate(String, String)} para JWT de órdenes. */
    public static final String GOCUOTAS_API_KEY_ENV = "GOCUOTAS_API_KEY";

    /** Inicio del rango {@code delivered_start} para GET listado de órdenes (formato {@code YYYY-MM-DD HH:mm}). */
    public static final String GOCUOTAS_DELIVERED_START_ENV = "GOCUOTAS_DELIVERED_START";

    /** Fin del rango {@code delivered_end} para GET listado de órdenes (formato {@code YYYY-MM-DD HH:mm}). */
    public static final String GOCUOTAS_DELIVERED_END_ENV = "GOCUOTAS_DELIVERED_END";

    private final GoCuotasRedirectConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Supplier<String> apiKeyFromEnvironment;

    /** JWT obtenido por authenticate (email + API key); no se usa si {@value #GOCUOTAS_JWT_ENV} está definida. */
    private volatile String cachedJwtFromCredentials;

    public GoCuotasRedirectClient(GoCuotasRedirectConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), new ObjectMapper(), null);
    }

    /**
     * Constructor para tests: {@code apiKeySupplierOverride} sustituye la lectura de {@value GoCuotasRedirectClient#GOCUOTAS_API_KEY_ENV}.
     */
    GoCuotasRedirectClient(
            GoCuotasRedirectConfig config,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Supplier<String> apiKeySupplierOverride) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.apiKeyFromEnvironment =
                apiKeySupplierOverride != null ? apiKeySupplierOverride : GoCuotasRedirectClient::readRequiredApiKeyFromEnv;
    }

    private static String readRequiredApiKeyFromEnv() {
        String key = System.getenv(GOCUOTAS_API_KEY_ENV);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Definí la variable de entorno " + GOCUOTAS_API_KEY_ENV + " (API key del panel; también contraseña para JWT de órdenes).");
        }
        return key.trim();
    }

    /**
     * Bearer para endpoints de órdenes sin token explícito en la firma: {@value #GOCUOTAS_JWT_ENV} si existe; si no,
     * authenticate con {@value #GOCUOTAS_EMAIL_ENV} + {@value #GOCUOTAS_API_KEY_ENV} (una vez por instancia, en caché).
     */
    private String bearerForOrderRequests() throws IOException, InterruptedException {
        String fromEnv = System.getenv(GOCUOTAS_JWT_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String cached = cachedJwtFromCredentials;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cachedJwtFromCredentials != null) {
                return cachedJwtFromCredentials;
            }
            String email = System.getenv(GOCUOTAS_EMAIL_ENV);
            if (email == null || email.isBlank()) {
                throw new IllegalStateException(
                        "Para órdenes sin Bearer explícito definí "
                                + GOCUOTAS_JWT_ENV
                                + ", o bien "
                                + GOCUOTAS_EMAIL_ENV
                                + " (email del comercio) y "
                                + GOCUOTAS_API_KEY_ENV
                                + " (se usa como contraseña al pedir el token).");
            }
            String password = readRequiredApiKeyFromEnv();
            AuthenticationResponse auth = authenticate(email.trim(), password);
            String token = auth.getToken();
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("GoCuotas no devolvió token JWT en la respuesta de authenticate.");
            }
            cachedJwtFromCredentials = token.trim();
            return cachedJwtFromCredentials;
        }
    }

    private URI ordersListUri(String deliveredStart, String deliveredEnd) {
        Objects.requireNonNull(deliveredStart, "deliveredStart");
        Objects.requireNonNull(deliveredEnd, "deliveredEnd");
        String q = "delivered_start="
                + URLEncoder.encode(deliveredStart.trim(), StandardCharsets.UTF_8)
                + "&delivered_end="
                + URLEncoder.encode(deliveredEnd.trim(), StandardCharsets.UTF_8);
        return config.resolve(GoCuotasRedirectConfig.PATH_ORDERS + "?" + q);
    }

    private DeliveredRange readRequiredDeliveredRangeFromEnvironment() {
        String start = System.getenv(GOCUOTAS_DELIVERED_START_ENV);
        String end = System.getenv(GOCUOTAS_DELIVERED_END_ENV);
        if (start == null || start.isBlank() || end == null || end.isBlank()) {
            throw new IllegalStateException(
                    "Para listar órdenes definí "
                            + GOCUOTAS_DELIVERED_START_ENV
                            + " y "
                            + GOCUOTAS_DELIVERED_END_ENV
                            + " con formato `YYYY-MM-DD HH:mm` (ej. 2021-01-19 10:00), como en la documentación Redirect V1.");
        }
        return new DeliveredRange(start.trim(), end.trim());
    }

    private record DeliveredRange(String start, String end) {}

    /**
     * POST {@value GoCuotasRedirectConfig#PATH_AUTHENTICATION} — obtiene un token JWT para el comercio.
     * La contraseña suele ser la de la cuenta en GoCuotas; en integraciones por variables de entorno puede usarse
     * el valor de {@value #GOCUOTAS_API_KEY_ENV} como contraseña (igual que en {@link #buscarOrden(String)} sin bearer).
     */
    public AuthenticationResponse authenticate(String email, String password) throws IOException, InterruptedException {
        var uri = config.resolve(GoCuotasRedirectConfig.PATH_AUTHENTICATION);
        var body = objectMapper.writeValueAsString(new AuthenticationRequest(email, password));
        var request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return sendJson(request, AuthenticationResponse.class);
    }

    /**
     * POST {@value GoCuotasRedirectConfig#PATH_CHECKOUTS} — crea el checkout y devuelve la URL de inicio de pago.
     *
     * @param bearerToken token JWT de {@link #authenticate(String, String)} o API key del panel
     *     (según indique la documentación para tu integración), sin el prefijo {@code Bearer }.
     */
    public CreateCheckoutResponse createCheckout(String bearerToken, CreateCheckoutRequest request)
            throws IOException, InterruptedException {
        Objects.requireNonNull(bearerToken, "bearerToken");
        Objects.requireNonNull(request, "request");
        var uri = config.resolve(GoCuotasRedirectConfig.PATH_CHECKOUTS);
        var body = objectMapper.writeValueAsString(request);
        var httpRequest = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return sendJson(httpRequest, CreateCheckoutResponse.class);
    }

    /**
     * Igual que {@link #createCheckout(String, CreateCheckoutRequest)} con Bearer tomado de la variable de entorno
     * {@value GoCuotasRedirectClient#GOCUOTAS_API_KEY_ENV}.
     */
    public CreateCheckoutResponse createCheckout(CreateCheckoutRequest request) throws IOException, InterruptedException {
        return createCheckout(apiKeyFromEnvironment.get(), request);
    }

    /**
     * Lista las órdenes del comercio (GET {@value GoCuotasRedirectConfig#PATH_ORDERS} con {@code delivered_start} y
     * {@code delivered_end}). El JSON exacto depende de la documentación Redirect V1.
     *
     * @param deliveredStart inicio del rango, formato {@code YYYY-MM-DD HH:mm}
     * @param deliveredEnd fin del rango, formato {@code YYYY-MM-DD HH:mm}
     */
    public JsonNode listarOrdenes(String bearerToken, String deliveredStart, String deliveredEnd)
            throws IOException, InterruptedException {
        return objectMapper.readTree(listOrders(bearerToken, deliveredStart, deliveredEnd));
    }

    /**
     * Igual que {@link #listarOrdenes(String, String, String)} usando {@value #GOCUOTAS_DELIVERED_START_ENV} y
     * {@value #GOCUOTAS_DELIVERED_END_ENV}.
     */
    public JsonNode listarOrdenes(String bearerToken) throws IOException, InterruptedException {
        var range = readRequiredDeliveredRangeFromEnvironment();
        return listarOrdenes(bearerToken, range.start(), range.end());
    }

    /**
     * Igual que {@link #listarOrdenes(String)} con Bearer de {@value #GOCUOTAS_JWT_ENV} o authenticate
     * ({@value #GOCUOTAS_EMAIL_ENV} + {@value #GOCUOTAS_API_KEY_ENV}).
     */
    public JsonNode listarOrdenes() throws IOException, InterruptedException {
        return listarOrdenes(bearerForOrderRequests());
    }

    /**
     * Obtiene una orden por identificador (GET {@value GoCuotasRedirectConfig#PATH_ORDERS}/{@code identificadorOrden}).
     * Suele coincidir con la referencia que enviaste en el checkout ({@code order_reference_id}); validá el criterio
     * en Stoplight si tu integración usa otro id.
     *
     * @param bearerToken JWT o API key (sin prefijo {@code Bearer }).
     * @throws OrderNotFoundException si la API responde {@value OrderNotFoundException#HTTP_STATUS} para ese identificador
     */
    public JsonNode buscarOrden(String bearerToken, String identificadorOrden) throws IOException, InterruptedException {
        Objects.requireNonNull(identificadorOrden, "identificadorOrden");
        return objectMapper.readTree(getOrder(bearerToken, identificadorOrden));
    }

    /**
     * Igual que {@link #buscarOrden(String, String)} con Bearer de {@value #GOCUOTAS_JWT_ENV} o authenticate
     * ({@value #GOCUOTAS_EMAIL_ENV} + {@value #GOCUOTAS_API_KEY_ENV}).
     *
     * @throws OrderNotFoundException si la API responde {@value OrderNotFoundException#HTTP_STATUS} para ese identificador
     */
    public JsonNode buscarOrden(String identificadorOrden) throws IOException, InterruptedException {
        return buscarOrden(bearerForOrderRequests(), identificadorOrden);
    }

    /**
     * GET listado de órdenes con query {@code delivered_start} y {@code delivered_end} (formato {@code YYYY-MM-DD HH:mm}).
     *
     * @see #listarOrdenes(String, String, String)
     */
    public String listOrders(String bearerToken, String deliveredStart, String deliveredEnd)
            throws IOException, InterruptedException {
        var uri = ordersListUri(deliveredStart, deliveredEnd);
        return sendRaw(HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + Objects.requireNonNull(bearerToken, "bearerToken"))
                .GET()
                .build());
    }

    /**
     * Igual que {@link #listOrders(String, String, String)} usando {@value #GOCUOTAS_DELIVERED_START_ENV} y
     * {@value #GOCUOTAS_DELIVERED_END_ENV}.
     *
     * @see #listarOrdenes(String)
     */
    public String listOrders(String bearerToken) throws IOException, InterruptedException {
        var range = readRequiredDeliveredRangeFromEnvironment();
        return listOrders(bearerToken, range.start(), range.end());
    }

    /**
     * Igual que {@link #listOrders(String)} con Bearer de {@value #GOCUOTAS_JWT_ENV} o authenticate
     * ({@value #GOCUOTAS_EMAIL_ENV} + {@value #GOCUOTAS_API_KEY_ENV}).
     *
     * @see #listarOrdenes()
     */
    public String listOrders() throws IOException, InterruptedException {
        return listOrders(bearerForOrderRequests());
    }

    /**
     * GET {@value GoCuotasRedirectConfig#PATH_ORDERS}/{@code orderId} — detalle de una orden (JSON en bruto).
     *
     * @see #buscarOrden(String, String)
     * @throws OrderNotFoundException si la API responde {@value OrderNotFoundException#HTTP_STATUS} para ese identificador
     */
    public String getOrder(String bearerToken, String orderId) throws IOException, InterruptedException {
        Objects.requireNonNull(orderId, "orderId");
        var path = GoCuotasRedirectConfig.PATH_ORDERS + "/" + urlEncodePathSegment(orderId);
        return sendRawOrderById(
                orderId,
                HttpRequest.newBuilder(config.resolve(path))
                        .timeout(config.requestTimeout())
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer " + Objects.requireNonNull(bearerToken, "bearerToken"))
                        .GET()
                        .build());
    }

    /**
     * Igual que {@link #getOrder(String, String)} con Bearer de {@value #GOCUOTAS_JWT_ENV} o authenticate
     * ({@value #GOCUOTAS_EMAIL_ENV} + {@value #GOCUOTAS_API_KEY_ENV}).
     *
     * @see #buscarOrden(String)
     * @throws OrderNotFoundException si la API responde {@value OrderNotFoundException#HTTP_STATUS} para ese identificador
     */
    public String getOrder(String orderId) throws IOException, InterruptedException {
        return getOrder(bearerForOrderRequests(), orderId);
    }

    /**
     * DELETE {@value GoCuotasRedirectConfig#PATH_ORDERS}/{@code orderId} — reembolso según Redirect V1.
     * Envía {@code Accept} y {@code Content-Type: application/json} como en la documentación de la API.
     *
     * @throws OrderNotFoundException si la API responde {@value OrderNotFoundException#HTTP_STATUS} para ese identificador
     */
    public String refundOrder(String bearerToken, String orderId) throws IOException, InterruptedException {
        Objects.requireNonNull(orderId, "orderId");
        var path = GoCuotasRedirectConfig.PATH_ORDERS + "/" + urlEncodePathSegment(orderId);
        return sendRawOrderById(
                orderId,
                HttpRequest.newBuilder(config.resolve(path))
                        .timeout(config.requestTimeout())
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + Objects.requireNonNull(bearerToken, "bearerToken"))
                        .DELETE()
                        .build());
    }

    /**
     * Igual que {@link #refundOrder(String, String)} con Bearer de {@value #GOCUOTAS_JWT_ENV} o authenticate
     * ({@value #GOCUOTAS_EMAIL_ENV} + {@value #GOCUOTAS_API_KEY_ENV}).
     *
     * @throws OrderNotFoundException si la API responde {@value OrderNotFoundException#HTTP_STATUS} para ese identificador
     */
    public String refundOrder(String orderId) throws IOException, InterruptedException {
        return refundOrder(bearerForOrderRequests(), orderId);
    }

    /**
     * Igual que {@link #refundOrder(String, String)} pero parsea el cuerpo JSON (típicamente un array de órdenes con
     * {@code id}, {@code amount_in_cents}, {@code status} según Redirect V1).
     *
     * @see #reembolsarOrden(String)
     * @throws OrderNotFoundException si la API responde {@value OrderNotFoundException#HTTP_STATUS} para ese identificador
     */
    public JsonNode reembolsarOrden(String bearerToken, String orderId) throws IOException, InterruptedException {
        return objectMapper.readTree(refundOrder(bearerToken, orderId));
    }

    /**
     * Igual que {@link #reembolsarOrden(String, String)} con Bearer de {@value #GOCUOTAS_JWT_ENV} o authenticate
     * ({@value #GOCUOTAS_EMAIL_ENV} + {@value #GOCUOTAS_API_KEY_ENV}).
     *
     * @throws OrderNotFoundException si la API responde {@value OrderNotFoundException#HTTP_STATUS} para ese identificador
     */
    public JsonNode reembolsarOrden(String orderId) throws IOException, InterruptedException {
        return reembolsarOrden(bearerForOrderRequests(), orderId);
    }

    private static String urlEncodePathSegment(String id) {
        return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private <T> T sendJson(HttpRequest request, Class<T> type) throws IOException, InterruptedException {
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var body = response.body();
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return objectMapper.readValue(body, type);
        }
        throw new GoCuotasApiException(
                response.statusCode(),
                body,
                "GoCuotas API error HTTP " + response.statusCode() + ": " + truncate(body));
    }

    private String sendRawOrderById(String logicalOrderId, HttpRequest request)
            throws IOException, InterruptedException {
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var body = response.body();
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return body;
        }
        if (status == OrderNotFoundException.HTTP_STATUS) {
            throw new OrderNotFoundException(
                    logicalOrderId,
                    body,
                    "GoCuotas API: orden no encontrada (HTTP " + status + "): " + truncate(body));
        }
        throw new GoCuotasApiException(
                status,
                body,
                "GoCuotas API error HTTP " + status + ": " + truncate(body));
    }

    private String sendRaw(HttpRequest request) throws IOException, InterruptedException {
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var body = response.body();
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return body;
        }
        throw new GoCuotasApiException(
                response.statusCode(),
                body,
                "GoCuotas API error HTTP " + response.statusCode() + ": " + truncate(body));
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }
}
