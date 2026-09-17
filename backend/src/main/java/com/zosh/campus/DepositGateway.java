package com.zosh.campus;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
@Component
public class DepositGateway {
    @Value("${campus.payment-mode}") private String mode;
    @Value("${campus.stripe-key}") private String key;
    @Value("${campus.webhook-secret}") private String webhookSecret;
    @Value("${campus.frontend-url}") private String frontend;
    @Value("${campus.hold-minutes}") private long holdMinutes;
    @PostConstruct void validate() {
        if(!mode.equals("mock")&&!mode.equals("stripe"))throw new IllegalStateException("PAYMENT_MODE must be mock or stripe");
        if(mode.equals("stripe") && (!key.startsWith("sk_test_") || !webhookSecret.startsWith("whsec_")))throw new IllegalStateException("Stripe test mode requires STRIPE_SECRET_KEY=sk_test_... and STRIPE_WEBHOOK_SECRET=whsec_...");
        if(holdMinutes<1 || holdMinutes>120)throw new IllegalStateException("HOLD_MINUTES must be between 1 and 120");
        Stripe.setConnectTimeout(5000); Stripe.setReadTimeout(10000);
    }
    public String mode(){return mode;}
    public String webhookSecret(){return webhookSecret;}
    RequestOptions options(String idempotency) {
        RequestOptions.RequestOptionsBuilder b=RequestOptions.builder().setApiKey(key);
        if(idempotency!=null)b.setIdempotencyKey(idempotency);return b.build();
    }
    public record Checkout(String id,String url) {}
    public record Verified(long reservationId,String sessionId,long amount,String currency,boolean paid,boolean live,String intent) {}
    public record RefundResult(String id,String status) {}
    public Checkout checkout(Reservation r) {
        if(mode.equals("mock"))return new Checkout("mock_cs_"+r.getId(),null);
        try {
            SessionCreateParams params=SessionCreateParams.builder().setMode(SessionCreateParams.Mode.PAYMENT)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setSuccessUrl(frontend+"/reservations?returned=1").setCancelUrl(frontend+"/reservations")
                .putMetadata("reservation_id",r.getId().toString()).setClientReferenceId(r.getId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder().setQuantity(1L)
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder().setCurrency("usd").setUnitAmount(r.getDepositCents())
                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder().setName("CampusExchange pickup deposit #"+r.getId()).build()).build()).build()).build();
            Session session=Session.create(params,options("campus-checkout-"+r.getId()));
            return new Checkout(session.getId(),session.getUrl());
        } catch(Exception e) {throw MarketplaceService.error(502,"Stripe checkout could not be created. Retry before your hold expires.");}
    }
    public Verified verify(String sessionId) {
        try {
            Session s=Session.retrieve(sessionId,options(null));
            return new Verified(Long.parseLong(s.getMetadata().get("reservation_id")),s.getId(),s.getAmountTotal(),s.getCurrency(),"paid".equals(s.getPaymentStatus()),Boolean.TRUE.equals(s.getLivemode()),s.getPaymentIntent());
        } catch(Exception e) { throw MarketplaceService.error(502,"Unable to verify Stripe payment; retry later"); }
    }
    public RefundResult refund(Reservation r) {
        if("mock".equals(r.getPaymentMode()))return new RefundResult("mock_refund_"+r.getId(),"succeeded");
        if(!mode.equals("stripe"))throw new IllegalStateException("Restore Stripe configuration to refund this payment");
        try {
            Refund refund=r.getRefundId()!=null?Refund.retrieve(r.getRefundId(),options(null)):
                Refund.create(RefundCreateParams.builder().setPaymentIntent(r.getPaymentIntentId()).setAmount(r.getDepositCents()).build(),options("campus-refund-"+r.getId()+"-"+r.getRefundAttempt()));
            return new RefundResult(refund.getId(),refund.getStatus());
        } catch(Exception e) {throw new IllegalStateException("Refund request needs reconciliation",e);}
    }
}
