package io.humen.gocuotas.redirect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class CreateCheckoutResponse {

    @JsonProperty("url_init")
    private String urlInit;

    public String getUrlInit() {
        return urlInit;
    }

    public void setUrlInit(String urlInit) {
        this.urlInit = urlInit;
    }
}
