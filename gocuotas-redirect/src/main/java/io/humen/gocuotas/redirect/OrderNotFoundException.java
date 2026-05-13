package io.humen.gocuotas.redirect;

import io.humen.gocuotas.api.GoCuotasApiException;
import java.util.Objects;

/**
 * Respuesta HTTP 404 al operar sobre una orden por identificador que no existe en GoCuotas
 * ({@code GET} o {@code DELETE} {@code /api_redirect/v1/orders/{id}}). El código exacto está en {@link #HTTP_STATUS}.
 */
public final class OrderNotFoundException extends GoCuotasApiException {

    /** Código HTTP asociado a orden inexistente en operaciones por id. */
    public static final int HTTP_STATUS = 404;

    private final String orderId;

    /**
     * @param orderId identificador de orden enviado en la petición (mismo valor que en el cliente, sin codificar path)
     * @param responseBody cuerpo de error de la API, si lo hubo
     * @param message mensaje para {@link Throwable#getMessage()}
     */
    public OrderNotFoundException(String orderId, String responseBody, String message) {
        super(HTTP_STATUS, responseBody, message);
        this.orderId = Objects.requireNonNull(orderId, "orderId");
    }

    /** Identificador de orden de la petición que produjo el 404. */
    public String orderId() {
        return orderId;
    }
}
