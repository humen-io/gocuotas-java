package io.humen.gocuotas.api;

import java.util.Optional;

/**
 * Error devuelto por la API de GoCuotas con contexto HTTP. Subclases en módulos concretos (p. ej. orden no encontrada
 * en Redirect V1) amplían el contrato.
 */
public class GoCuotasApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public GoCuotasApiException(int statusCode, String responseBody, String message) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int statusCode() {
        return statusCode;
    }

    public Optional<String> responseBody() {
        return Optional.ofNullable(responseBody);
    }
}
