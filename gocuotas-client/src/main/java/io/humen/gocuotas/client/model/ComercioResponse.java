package io.humen.gocuotas.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo JSON de {@code GET /api_client/v1/client} según API Client V1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ComercioResponse {

    private long id;
    private String name;
    private String cuit;

    @JsonProperty("surcharge_percentage_to_online_orders")
    private String surchargePercentageToOnlineOrders;

    @JsonProperty("max_number_of_installments")
    private int maxNumberOfInstallments;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCuit() {
        return cuit;
    }

    public void setCuit(String cuit) {
        this.cuit = cuit;
    }

    public String getSurchargePercentageToOnlineOrders() {
        return surchargePercentageToOnlineOrders;
    }

    public void setSurchargePercentageToOnlineOrders(String surchargePercentageToOnlineOrders) {
        this.surchargePercentageToOnlineOrders = surchargePercentageToOnlineOrders;
    }

    public int getMaxNumberOfInstallments() {
        return maxNumberOfInstallments;
    }

    public void setMaxNumberOfInstallments(int maxNumberOfInstallments) {
        this.maxNumberOfInstallments = maxNumberOfInstallments;
    }
}
