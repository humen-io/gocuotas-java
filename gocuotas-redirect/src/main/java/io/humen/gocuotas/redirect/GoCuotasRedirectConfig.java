package io.humen.gocuotas.redirect;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuración del cliente (base URL y timeouts de petición HTTP).
 */
public final class GoCuotasRedirectConfig {

    public static final String DEFAULT_BASE_URL = "https://www.gocuotas.com";
    public static final String PATH_AUTHENTICATION = "/api_redirect/v1/authentication";
    public static final String PATH_CHECKOUTS = "/api_redirect/v1/checkouts";
    public static final String PATH_ORDERS = "/api_redirect/v1/orders";

    private final URI baseUri;
    private final Duration requestTimeout;

    public GoCuotasRedirectConfig(URI baseUri, Duration requestTimeout) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.requestTimeout =
                requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
    }

    public static GoCuotasRedirectConfig defaultConfig() {
        return new GoCuotasRedirectConfig(URI.create(DEFAULT_BASE_URL), Duration.ofSeconds(30));
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
