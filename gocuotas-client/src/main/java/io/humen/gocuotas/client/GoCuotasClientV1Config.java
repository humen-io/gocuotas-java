package io.humen.gocuotas.client;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuración del cliente API Client V1 (base URL y timeout de petición).
 */
public final class GoCuotasClientV1Config {

    public static final String DEFAULT_BASE_URL = "https://www.gocuotas.com";

    /** Ruta GET para información del comercio (Bearer = API key de comercio). */
    public static final String PATH_CLIENT = "/api_client/v1/client";

    /** Ruta GET para liquidaciones (expense settlements). */
    public static final String PATH_EXPENSE_SETTLEMENTS = "/api_client/v1/expense_settlements";

    /** Ruta GET para liquidaciones en CSV / texto plano ({@code Accept: text/plain}). */
    public static final String PATH_EXPENSE_SETTLEMENTS_CSVS = "/api_client/v1/expense_settlements_csvs";

    private final URI baseUri;
    private final Duration requestTimeout;

    public GoCuotasClientV1Config(URI baseUri, Duration requestTimeout) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
    }

    public static GoCuotasClientV1Config defaultConfig() {
        return new GoCuotasClientV1Config(URI.create(DEFAULT_BASE_URL), Duration.ofSeconds(60));
    }

    public URI baseUri() {
        return baseUri;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public URI resolve(String path) {
        String p = path.startsWith("/") ? path : "/" + path;
        return baseUri.resolve(p);
    }
}
