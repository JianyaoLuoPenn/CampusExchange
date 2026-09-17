package com.zosh.campus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.stripe.model.Refund;
import com.stripe.model.RefundCollection;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

class DepositGatewayTest {
  DepositGateway gateway(String mode) {
    DepositGateway g = new DepositGateway();
    ReflectionTestUtils.setField(g, "mode", mode);
    ReflectionTestUtils.setField(g, "key", "sk_test_unit_only");
    ReflectionTestUtils.setField(g, "webhookSecret", "whsec_unit_only");
    ReflectionTestUtils.setField(g, "frontend", "http://localhost:5173");
    ReflectionTestUtils.setField(g, "holdMinutes", 15L);
    return g;
  }

  Reservation reservation() {
    Reservation r = new Reservation();
    r.setId(72L);
    r.setDepositCents(1000);
    r.setPaymentMode("stripe");
    r.setPaymentIntentId("pi_test");
    return r;
  }

  @Test
  void checkoutUsesStableIdempotencyAmountAndReservationMetadata() {
    DepositGateway g = gateway("stripe");
    Reservation r = reservation();
    Session result = new Session();
    result.setId("cs_test");
    result.setUrl("https://checkout.stripe.com/test");
    try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
      stripe
          .when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
          .thenAnswer(
              call -> {
                SessionCreateParams p = call.getArgument(0);
                RequestOptions o = call.getArgument(1);
                assertThat(o.getIdempotencyKey()).isEqualTo("campus-checkout-72");
                assertThat(p.getMetadata()).containsEntry("reservation_id", "72");
                assertThat(p.getLineItems().get(0).getPriceData().getUnitAmount()).isEqualTo(1000);
                assertThat(p.getLineItems().get(0).getQuantity()).isEqualTo(1L);
                return result;
              });
      assertThat(g.checkout(r).id()).isEqualTo("cs_test");
      assertThat(g.checkout(r).id()).isEqualTo("cs_test");
    }
  }

  @Test
  void lostRefundResponseIsRecoveredByProviderMetadataWithoutCreatingAnotherRefund() {
    DepositGateway g = gateway("stripe");
    Refund existing = new Refund();
    existing.setId("re_existing");
    existing.setStatus("succeeded");
    existing.setAmount(1000L);
    existing.setMetadata(Map.of("reservation_id", "72"));
    RefundCollection list = new RefundCollection();
    list.setData(List.of(existing));
    try (MockedStatic<Refund> stripe = mockStatic(Refund.class)) {
      stripe.when(() -> Refund.list(anyMap(), any(RequestOptions.class))).thenReturn(list);
      assertThat(g.refund(reservation()))
          .isEqualTo(new DepositGateway.RefundResult("re_existing", "succeeded"));
      stripe.verify(
          () -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)), never());
    }
  }

  @Test
  void refundUsesFullDepositAndStableAttemptKey() {
    DepositGateway g = gateway("stripe");
    RefundCollection list = new RefundCollection();
    list.setData(List.of());
    Refund result = new Refund();
    result.setId("re_pending");
    result.setStatus("pending");
    try (MockedStatic<Refund> stripe = mockStatic(Refund.class)) {
      stripe.when(() -> Refund.list(anyMap(), any(RequestOptions.class))).thenReturn(list);
      stripe
          .when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)))
          .thenAnswer(
              call -> {
                RefundCreateParams p = call.getArgument(0);
                RequestOptions o = call.getArgument(1);
                assertThat(p.getAmount()).isEqualTo(1000L);
                assertThat(p.getPaymentIntent()).isEqualTo("pi_test");
                assertThat(o.getIdempotencyKey()).isEqualTo("campus-refund-72-0");
                return result;
              });
      assertThat(g.refund(reservation()).status()).isEqualTo("pending");
    }
  }

  @Test
  void liveOrMissingStripeKeysAreRefusedAndMockIsExplicit() {
    DepositGateway g = gateway("stripe");
    ReflectionTestUtils.setField(g, "key", "sk_live_forbidden");
    assertThatThrownBy(g::validate).isInstanceOf(IllegalStateException.class);
    ReflectionTestUtils.setField(g, "key", "");
    assertThatThrownBy(g::validate).isInstanceOf(IllegalStateException.class);
    DepositGateway mock = gateway("mock");
    ReflectionTestUtils.setField(mock, "key", "");
    mock.validate();
    assertThat(mock.mode()).isEqualTo("mock");
  }
}
