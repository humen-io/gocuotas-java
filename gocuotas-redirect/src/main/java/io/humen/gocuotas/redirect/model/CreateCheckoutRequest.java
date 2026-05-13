package io.humen.gocuotas.redirect.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo del POST {@code /api_redirect/v1/checkouts} según la documentación Redirect V1
 * (montos en centavos, referencia de orden, URLs de éxito y fallo, datos del comprador).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class CreateCheckoutRequest {

    @JsonProperty("amount_in_cents")
    private final long amountInCents;

    private final String email;

    @JsonProperty("order_reference_id")
    private final String orderReferenceId;

    @JsonProperty("phone_number")
    private final String phoneNumber;

    @JsonProperty("url_success")
    private final String urlSuccess;

    @JsonProperty("url_failure")
    private final String urlFailure;

    @JsonProperty("webhook_url")
    private final String webhookUrl;

    public CreateCheckoutRequest(
            long amountInCents,
            String email,
            String orderReferenceId,
            String phoneNumber,
            String urlSuccess,
            String urlFailure,
            String webhookUrl) {
        this.amountInCents = amountInCents;
        this.email = email;
        this.orderReferenceId = orderReferenceId;
        this.phoneNumber = phoneNumber;
        this.urlSuccess = urlSuccess;
        this.urlFailure = urlFailure;
        this.webhookUrl = webhookUrl;
    }

    public long getAmountInCents() {
        return amountInCents;
    }

    public String getEmail() {
        return email;
    }

    public String getOrderReferenceId() {
        return orderReferenceId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getUrlSuccess() {
        return urlSuccess;
    }

    public String getUrlFailure() {
        return urlFailure;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long amountInCents;
        private String email;
        private String orderReferenceId;
        private String phoneNumber;
        private String urlSuccess;
        private String urlFailure;
        private String webhookUrl;

        public Builder amountInCents(long amountInCents) {
            this.amountInCents = amountInCents;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder orderReferenceId(String orderReferenceId) {
            this.orderReferenceId = orderReferenceId;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder urlSuccess(String urlSuccess) {
            this.urlSuccess = urlSuccess;
            return this;
        }

        public Builder urlFailure(String urlFailure) {
            this.urlFailure = urlFailure;
            return this;
        }

        public Builder webhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
            return this;
        }

        public CreateCheckoutRequest build() {
            return new CreateCheckoutRequest(
                    amountInCents,
                    email,
                    orderReferenceId,
                    phoneNumber,
                    urlSuccess,
                    urlFailure,
                    webhookUrl);
        }
    }
}
