package io.humen.gocuotas.redirect.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta del paso de autenticación (email y contraseña del comercio).
 * El nombre exacto del campo de token puede variar; se aceptan alias comunes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AuthenticationResponse {

    @JsonProperty("token")
    @JsonAlias({"access_token", "accessToken", "jwt"})
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
