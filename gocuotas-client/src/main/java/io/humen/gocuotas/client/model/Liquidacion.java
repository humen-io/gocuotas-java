package io.humen.gocuotas.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Elemento del listado {@code GET /api_client/v1/expense_settlements} (liquidaciones / expense settlements).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Liquidacion {

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
}
