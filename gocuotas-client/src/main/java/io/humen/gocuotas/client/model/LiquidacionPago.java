package io.humen.gocuotas.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class LiquidacionPago {

    private LiquidacionTarjeta card;

    public LiquidacionTarjeta getCard() {
        return card;
    }

    public void setCard(LiquidacionTarjeta card) {
        this.card = card;
    }
}
