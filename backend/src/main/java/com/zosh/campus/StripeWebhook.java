package com.zosh.campus;
import com.stripe.net.Webhook;
import com.stripe.model.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class StripeWebhook {
    private final DepositGateway gateway; private final MarketplaceService market; private final ObjectMapper json;
    @PostMapping("/api/campus/webhooks/stripe")
    public Map<String,Boolean> receive(@RequestBody String body,@RequestHeader("Stripe-Signature") String signature) throws Exception {
        if(!"stripe".equals(gateway.mode()))throw MarketplaceService.error(400,"Stripe webhooks are disabled in simulation mode");
        Event event;
        try {event=Webhook.constructEvent(body,signature,gateway.webhookSecret());}
        catch(Exception e){throw MarketplaceService.error(400,"Invalid Stripe signature");}
        // Retrieve the current provider state after signature verification. Old event payloads cannot regress paid/refunded states.
        if(event.getType().equals("checkout.session.completed") || event.getType().equals("checkout.session.async_payment_succeeded") || event.getType().equals("checkout.session.async_payment_failed") || event.getType().equals("checkout.session.expired")) {
            String sessionId=json.readTree(body).path("data").path("object").path("id").asText();
            DepositGateway.Verified v=gateway.verify(sessionId);market.verifiedPayment(event.getId(),v.reservationId(),v);
        }
        return Map.of("received",true);
    }
}
