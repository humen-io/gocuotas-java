package io.humen.gocuotas.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.humen.gocuotas.api.GoCuotasApiException;
import io.humen.gocuotas.client.model.ComercioResponse;
import io.humen.gocuotas.client.model.InformacionLiquidacion;
import io.humen.gocuotas.client.model.Liquidacion;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Cliente HTTP para la <strong>API Client V1</strong> de GoCuotas ({@code /api_client/v1/...}).
 *
 * <p>Autenticación: cabecera {@code Authorization: Bearer} con la <strong>API key de comercio</strong> (no confundir
 * con el flujo JWT de Redirect V1 salvo que tu panel use la misma clave por política de GoCuotas).
 */
public final class GoCuotasClientV1 {

    /** API key de comercio para métodos sin Bearer explícito en la firma (salvo los que reciben la clave como parámetro). */
    public static final String GOCUOTAS_COMMERCE_API_KEY_ENV = "GOCUOTAS_COMMERCE_API_KEY";

    /** Id numérico o referencia de liquidación para {@link #obtenerInformacionLiquidacion()} y {@link #obtenerLiquidacionTextoPlano()} sin id en la firma. */
    public static final String GOCUOTAS_LIQUIDACION_ID_ENV = "GOCUOTAS_LIQUIDACION_ID";

    private final GoCuotasClientV1Config config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Supplier<String> commerceApiKeyFromEnvironment;

    public GoCuotasClientV1(GoCuotasClientV1Config config) {
        this(
                config,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                new ObjectMapper(),
                null);
    }

    /**
     * Constructor para tests: {@code commerceApiKeySupplierOverride} sustituye la lectura de
     * {@value #GOCUOTAS_COMMERCE_API_KEY_ENV}.
     */
    GoCuotasClientV1(
            GoCuotasClientV1Config config,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Supplier<String> commerceApiKeySupplierOverride) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.commerceApiKeyFromEnvironment =
                commerceApiKeySupplierOverride != null
                        ? commerceApiKeySupplierOverride
                        : GoCuotasClientV1::readRequiredCommerceApiKeyFromEnv;
    }

    private static String readRequiredCommerceApiKeyFromEnv() {
        String key = System.getenv(GOCUOTAS_COMMERCE_API_KEY_ENV);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Definí la variable de entorno " + GOCUOTAS_COMMERCE_API_KEY_ENV + " (API key de comercio).");
        }
        return key.trim();
    }

    /**
     * GET {@value GoCuotasClientV1Config#PATH_CLIENT} — información del comercio asociada a la API key.
     *
     * @param commerceApiKey API key de comercio (sin prefijo {@code Bearer }).
     */
    public ComercioResponse obtenerInformacionComercio(String commerceApiKey) throws IOException, InterruptedException {
        Objects.requireNonNull(commerceApiKey, "commerceApiKey");
        var uri = config.resolve(GoCuotasClientV1Config.PATH_CLIENT);
        var request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + commerceApiKey.trim())
                .GET()
                .build();
        return sendJson(request, ComercioResponse.class);
    }

    /**
     * Igual que {@link #obtenerInformacionComercio(String)} con Bearer tomado de {@value #GOCUOTAS_COMMERCE_API_KEY_ENV}.
     */
    public ComercioResponse obtenerInformacionComercio() throws IOException, InterruptedException {
        return obtenerInformacionComercio(commerceApiKeyFromEnvironment.get());
    }

    /**
     * GET {@value GoCuotasClientV1Config#PATH_EXPENSE_SETTLEMENTS} — liquidaciones (expense settlements) del comercio.
     *
     * @param commerceApiKey API key de comercio (sin prefijo {@code Bearer }).
     */
    public List<Liquidacion> listarLiquidaciones(String commerceApiKey) throws IOException, InterruptedException {
        Objects.requireNonNull(commerceApiKey, "commerceApiKey");
        var uri = config.resolve(GoCuotasClientV1Config.PATH_EXPENSE_SETTLEMENTS);
        var request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + commerceApiKey.trim())
                .GET()
                .build();
        return sendJson(request, new TypeReference<List<Liquidacion>>() {});
    }

    /**
     * Igual que {@link #listarLiquidaciones(String)} con Bearer tomado de {@value #GOCUOTAS_COMMERCE_API_KEY_ENV}.
     */
    public List<Liquidacion> listarLiquidaciones() throws IOException, InterruptedException {
        return listarLiquidaciones(commerceApiKeyFromEnvironment.get());
    }

    /**
     * GET {@value GoCuotasClientV1Config#PATH_EXPENSE_SETTLEMENTS}/{@code liquidacionId} — liquidación con
     * {@code details} y retenciones según API Client V1.
     *
     * @param commerceApiKey API key de comercio (sin prefijo {@code Bearer }).
     * @param liquidacionId identificador en la ruta (se codifica como segmento de URL).
     */
    public InformacionLiquidacion obtenerInformacionLiquidacion(String commerceApiKey, String liquidacionId)
            throws IOException, InterruptedException {
        Objects.requireNonNull(commerceApiKey, "commerceApiKey");
        Objects.requireNonNull(liquidacionId, "liquidacionId");
        var path = GoCuotasClientV1Config.PATH_EXPENSE_SETTLEMENTS + "/" + urlEncodePathSegment(liquidacionId);
        var uri = config.resolve(path);
        var request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + commerceApiKey.trim())
                .GET()
                .build();
        return sendJson(request, InformacionLiquidacion.class);
    }

    /**
     * Igual que {@link #obtenerInformacionLiquidacion(String, String)} con Bearer tomado de
     * {@value #GOCUOTAS_COMMERCE_API_KEY_ENV}.
     */
    public InformacionLiquidacion obtenerInformacionLiquidacion(String liquidacionId)
            throws IOException, InterruptedException {
        return obtenerInformacionLiquidacion(commerceApiKeyFromEnvironment.get(), liquidacionId);
    }

    /**
     * Igual que {@link #obtenerInformacionLiquidacion(String, String)} con API key en {@value #GOCUOTAS_COMMERCE_API_KEY_ENV}
     * e id en {@value #GOCUOTAS_LIQUIDACION_ID_ENV}.
     */
    public InformacionLiquidacion obtenerInformacionLiquidacion() throws IOException, InterruptedException {
        return obtenerInformacionLiquidacion(commerceApiKeyFromEnvironment.get(), readRequiredLiquidacionIdFromEnv());
    }

    /**
     * GET {@value GoCuotasClientV1Config#PATH_EXPENSE_SETTLEMENTS_CSVS} — liquidaciones como texto plano (CSV según
     * API Client V1; cabecera {@code Accept: text/plain}).
     *
     * @param commerceApiKey API key de comercio (sin prefijo {@code Bearer }).
     * @return cuerpo de la respuesta tal cual (p. ej. líneas CSV con encabezado y filas).
     */
    public String obtenerInformacionLiquidacionesTextoPlano(String commerceApiKey)
            throws IOException, InterruptedException {
        Objects.requireNonNull(commerceApiKey, "commerceApiKey");
        var uri = config.resolve(GoCuotasClientV1Config.PATH_EXPENSE_SETTLEMENTS_CSVS);
        var request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "text/plain")
                .header("Authorization", "Bearer " + commerceApiKey.trim())
                .GET()
                .build();
        return sendAndReadBodyOrThrow(request);
    }

    /**
     * Igual que {@link #obtenerInformacionLiquidacionesTextoPlano(String)} con Bearer tomado de
     * {@value #GOCUOTAS_COMMERCE_API_KEY_ENV}.
     */
    public String obtenerInformacionLiquidacionesTextoPlano() throws IOException, InterruptedException {
        return obtenerInformacionLiquidacionesTextoPlano(commerceApiKeyFromEnvironment.get());
    }

    /**
     * GET {@value GoCuotasClientV1Config#PATH_EXPENSE_SETTLEMENTS_CSVS}/{@code liquidacionId} — una liquidación como
     * texto plano (CSV detallado; {@code Accept: text/plain}).
     *
     * @param commerceApiKey API key de comercio (sin prefijo {@code Bearer }).
     * @param liquidacionId identificador en la ruta (se codifica como segmento de URL).
     * @return cuerpo de la respuesta tal cual (cabeceras y filas según API Client V1).
     */
    public String obtenerLiquidacionTextoPlano(String commerceApiKey, String liquidacionId)
            throws IOException, InterruptedException {
        Objects.requireNonNull(commerceApiKey, "commerceApiKey");
        Objects.requireNonNull(liquidacionId, "liquidacionId");
        var path = GoCuotasClientV1Config.PATH_EXPENSE_SETTLEMENTS_CSVS + "/" + urlEncodePathSegment(liquidacionId);
        var uri = config.resolve(path);
        var request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "text/plain")
                .header("Authorization", "Bearer " + commerceApiKey.trim())
                .GET()
                .build();
        return sendAndReadBodyOrThrow(request);
    }

    /**
     * Igual que {@link #obtenerLiquidacionTextoPlano(String, String)} con Bearer tomado de
     * {@value #GOCUOTAS_COMMERCE_API_KEY_ENV}.
     */
    public String obtenerLiquidacionTextoPlano(String liquidacionId) throws IOException, InterruptedException {
        return obtenerLiquidacionTextoPlano(commerceApiKeyFromEnvironment.get(), liquidacionId);
    }

    /**
     * Igual que {@link #obtenerLiquidacionTextoPlano(String, String)} con API key en {@value #GOCUOTAS_COMMERCE_API_KEY_ENV}
     * e id en {@value #GOCUOTAS_LIQUIDACION_ID_ENV}.
     */
    public String obtenerLiquidacionTextoPlano() throws IOException, InterruptedException {
        return obtenerLiquidacionTextoPlano(commerceApiKeyFromEnvironment.get(), readRequiredLiquidacionIdFromEnv());
    }

    private static String readRequiredLiquidacionIdFromEnv() {
        String id = System.getenv(GOCUOTAS_LIQUIDACION_ID_ENV);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException(
                    "Definí la variable de entorno " + GOCUOTAS_LIQUIDACION_ID_ENV + " (id de la liquidación).");
        }
        return id.trim();
    }

    private static String urlEncodePathSegment(String id) {
        return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String sendAndReadBodyOrThrow(HttpRequest request) throws IOException, InterruptedException {
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var body = response.body();
        int code = response.statusCode();
        if (code >= 200 && code < 300) {
            return body;
        }
        throw new GoCuotasApiException(
                code,
                body,
                "GoCuotas API Client V1 error HTTP " + code + ": " + truncate(body));
    }

    private <T> T sendJson(HttpRequest request, Class<T> type) throws IOException, InterruptedException {
        return objectMapper.readValue(sendAndReadBodyOrThrow(request), type);
    }

    private <T> T sendJson(HttpRequest request, TypeReference<T> typeRef) throws IOException, InterruptedException {
        return objectMapper.readValue(sendAndReadBodyOrThrow(request), typeRef);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }
}
