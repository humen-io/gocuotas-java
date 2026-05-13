package io.humen.gocuotas.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Cuerpo JSON de {@code GET /api_client/v1/expense_settlements/{id}} (liquidación con detalle).
 *
 * <p>Los arrays {@code normal_retention_retain_paid_orders} y {@code tax_retention_retain_paid_orders} se exponen como
 * {@link JsonNode} para tolerar objetos vacíos u otros campos que añada la API sin romper el cliente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class InformacionLiquidacion {

    private long id;

    @JsonProperty("payment_expense_method")
    private String paymentExpenseMethod;

    @JsonProperty("payment_expense_at")
    private String paymentExpenseAt;

    @JsonProperty("due_expense_at")
    private String dueExpenseAt;

    @JsonProperty("payment_expense_retained_amount_in_cents")
    private long paymentExpenseRetainedAmountInCents;

    @JsonProperty("payment_expense_amount_in_cents")
    private long paymentExpenseAmountInCents;

    private List<LiquidacionDetalleItem> details;

    @JsonProperty("normal_retention_retain_paid_orders")
    private List<JsonNode> normalRetentionRetainPaidOrders;

    @JsonProperty("tax_retention_retain_paid_orders")
    private List<JsonNode> taxRetentionRetainPaidOrders;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPaymentExpenseMethod() {
        return paymentExpenseMethod;
    }

    public void setPaymentExpenseMethod(String paymentExpenseMethod) {
        this.paymentExpenseMethod = paymentExpenseMethod;
    }

    public String getPaymentExpenseAt() {
        return paymentExpenseAt;
    }

    public void setPaymentExpenseAt(String paymentExpenseAt) {
        this.paymentExpenseAt = paymentExpenseAt;
    }

    public String getDueExpenseAt() {
        return dueExpenseAt;
    }

    public void setDueExpenseAt(String dueExpenseAt) {
        this.dueExpenseAt = dueExpenseAt;
    }

    public long getPaymentExpenseRetainedAmountInCents() {
        return paymentExpenseRetainedAmountInCents;
    }

    public void setPaymentExpenseRetainedAmountInCents(long paymentExpenseRetainedAmountInCents) {
        this.paymentExpenseRetainedAmountInCents = paymentExpenseRetainedAmountInCents;
    }

    public long getPaymentExpenseAmountInCents() {
        return paymentExpenseAmountInCents;
    }

    public void setPaymentExpenseAmountInCents(long paymentExpenseAmountInCents) {
        this.paymentExpenseAmountInCents = paymentExpenseAmountInCents;
    }

    public List<LiquidacionDetalleItem> getDetails() {
        return details;
    }

    public void setDetails(List<LiquidacionDetalleItem> details) {
        this.details = details;
    }

    public List<JsonNode> getNormalRetentionRetainPaidOrders() {
        return normalRetentionRetainPaidOrders;
    }

    public void setNormalRetentionRetainPaidOrders(List<JsonNode> normalRetentionRetainPaidOrders) {
        this.normalRetentionRetainPaidOrders = normalRetentionRetainPaidOrders;
    }

    public List<JsonNode> getTaxRetentionRetainPaidOrders() {
        return taxRetentionRetainPaidOrders;
    }

    public void setTaxRetentionRetainPaidOrders(List<JsonNode> taxRetentionRetainPaidOrders) {
        this.taxRetentionRetainPaidOrders = taxRetentionRetainPaidOrders;
    }
}
