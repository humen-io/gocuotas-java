package io.humen.gocuotas.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Elemento del array {@code details} en {@code GET /api_client/v1/expense_settlements/{id}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class LiquidacionDetalleItem {

    private long id;
    private String description;

    @JsonProperty("delivered_at")
    private String deliveredAt;

    @JsonProperty("due_expense_at")
    private String dueExpenseAt;

    @JsonProperty("amount_in_cents")
    private long amountInCents;

    @JsonProperty("commission_amount_in_cents")
    private long commissionAmountInCents;

    @JsonProperty("tax_amount_in_cents")
    private long taxAmountInCents;

    @JsonProperty("expense_amount_in_cents")
    private long expenseAmountInCents;

    @JsonProperty("discarded_at")
    private String discardedAt;

    private String status;

    @JsonProperty("number_of_installments")
    private int numberOfInstallments;

    @JsonProperty("order_reference_id")
    private String orderReferenceId;

    private LiquidacionPago payment;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(String deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getDueExpenseAt() {
        return dueExpenseAt;
    }

    public void setDueExpenseAt(String dueExpenseAt) {
        this.dueExpenseAt = dueExpenseAt;
    }

    public long getAmountInCents() {
        return amountInCents;
    }

    public void setAmountInCents(long amountInCents) {
        this.amountInCents = amountInCents;
    }

    public long getCommissionAmountInCents() {
        return commissionAmountInCents;
    }

    public void setCommissionAmountInCents(long commissionAmountInCents) {
        this.commissionAmountInCents = commissionAmountInCents;
    }

    public long getTaxAmountInCents() {
        return taxAmountInCents;
    }

    public void setTaxAmountInCents(long taxAmountInCents) {
        this.taxAmountInCents = taxAmountInCents;
    }

    public long getExpenseAmountInCents() {
        return expenseAmountInCents;
    }

    public void setExpenseAmountInCents(long expenseAmountInCents) {
        this.expenseAmountInCents = expenseAmountInCents;
    }

    public String getDiscardedAt() {
        return discardedAt;
    }

    public void setDiscardedAt(String discardedAt) {
        this.discardedAt = discardedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getNumberOfInstallments() {
        return numberOfInstallments;
    }

    public void setNumberOfInstallments(int numberOfInstallments) {
        this.numberOfInstallments = numberOfInstallments;
    }

    public String getOrderReferenceId() {
        return orderReferenceId;
    }

    public void setOrderReferenceId(String orderReferenceId) {
        this.orderReferenceId = orderReferenceId;
    }

    public LiquidacionPago getPayment() {
        return payment;
    }

    public void setPayment(LiquidacionPago payment) {
        this.payment = payment;
    }
}
