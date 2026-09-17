package com.zosh.campus;

import static com.zosh.campus.CampusDtos.*;
import static com.zosh.campus.Reservation.PaymentState.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zosh.domain.USER_ROLE;
import com.zosh.model.*;
import com.zosh.repository.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "campus.sweep-ms=3600000",
      "campus.demo=false",
      "campus.frontend-url=http://localhost:5173"
    })
@AutoConfigureMockMvc
class MarketplaceIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    String url =
        System.getenv()
            .getOrDefault(
                "TEST_DB_URL", "jdbc:h2:mem:campus;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER");
    r.add("spring.datasource.url", () -> url);
    r.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "sa"));
    r.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
    r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @Autowired MarketplaceService market;
  @Autowired UserRepository users;
  @Autowired ProductRepository products;
  @Autowired ReservationRepository reservations;
  @Autowired PaymentEventRepository events;
  @Autowired PlatformTransactionManager transactions;
  @Autowired MockMvc mvc;
  @MockBean Clock clock;
  @MockBean DepositGateway gateway;
  Instant now = Instant.parse("2030-01-01T12:00:00Z");
  String seller, buyer, other;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(now);
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    when(gateway.mode()).thenReturn("mock");
    String id = UUID.randomUUID().toString();
    seller = "seller-" + id + "@test.dev";
    buyer = "buyer-" + id + "@test.dev";
    other = "other-" + id + "@test.dev";
    for (String email : List.of(seller, buyer, other)) {
      User u = new User();
      u.setFullName(email);
      u.setEmail(email);
      u.setPassword("unused");
      u.setRole(USER_ROLE.ROLE_CUSTOMER);
      users.save(u);
    }
  }

  Listing listing(long deposit) {
    return market.publish(
        seller,
        new ListingInput(
            "Desk",
            "Used oak desk",
            "Furniture",
            "Good",
            "North Campus",
            "Maple Court",
            "Lobby",
            "PRIVATE UNIT 9",
            6500,
            deposit,
            List.of(now.plusSeconds(7200)),
            List.of()));
  }

  Booking reserve(Listing p, String email) {
    return market.reserve(email, new BookingInput(p.id(), p.pickupSlots().get(0)));
  }

  void stripe(long id) {
    new TransactionTemplate(transactions)
        .execute(
            s -> {
              Reservation r = reservations.findById(id).orElseThrow();
              r.setPaymentMode("stripe");
              r.setCheckoutId("cs_" + id);
              return null;
            });
  }

  DepositGateway.Verified paid(Booking r) {
    return new DepositGateway.Verified(
        r.id(), "cs_" + r.id(), r.depositCents(), "usd", true, false, "pi_" + r.id());
  }

  @Test
  void concurrentBuyersCanNeverBothReserveTheSameItem() throws Exception {
    Listing p = listing(0);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
    try {
      List<Future<Boolean>> jobs = new ArrayList<>();
      for (String email : List.of(buyer, other))
        jobs.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  try {
                    reserve(p, email);
                    return true;
                  } catch (org.springframework.web.server.ResponseStatusException e) {
                    assertThat(e.getStatusCode().value()).isEqualTo(409);
                    return false;
                  }
                }));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      int winners = 0;
      for (Future<Boolean> f : jobs) if (f.get(10, TimeUnit.SECONDS)) winners++;
      assertThat(winners).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
    assertThat(market.details(p.id()).status()).isEqualTo("RESERVED");
  }

  @Test
  void noDepositReservesImmediatelyAndOnlySellerCanComplete() {
    Booking r = reserve(listing(0), buyer);
    assertThat(r.paymentState()).isEqualTo("NOT_REQUIRED");
    assertThat(r.pickupAddress()).isEqualTo("PRIVATE UNIT 9");
    assertThatThrownBy(() -> market.complete(buyer, r.id())).hasMessageContaining("403");
    assertThat(market.complete(seller, r.id()).status()).isEqualTo("COMPLETED");
    assertThatThrownBy(() -> market.cancel(buyer, r.id())).hasMessageContaining("409");
    assertThat(market.details(r.product().id()).status()).isEqualTo("SOLD");
  }

  @Test
  void exactExpiryReleasesAndAllowsAnotherBuyer() {
    Listing p = listing(1000);
    Booking r = reserve(p, buyer);
    assertThat(r.pickupAddress()).isNull();
    when(clock.instant()).thenReturn(r.expiresAt());
    market.expireOne(r.id());
    assertThat(market.get(buyer, r.id()).status()).isEqualTo("EXPIRED");
    assertThat(reserve(p, other).status()).isEqualTo("PENDING_PAYMENT");
  }

  @Test
  void duplicatePaidEventsAreIdempotent() {
    Booking r = reserve(listing(1000), buyer);
    stripe(r.id());
    String event = "evt_" + r.id();
    market.verifiedPayment(event, r.id(), paid(r));
    market.verifiedPayment(event, r.id(), paid(r));
    market.verifiedPayment(event + "_duplicate_object", r.id(), paid(r));
    assertThat(market.get(buyer, r.id()).status()).isEqualTo("RESERVED");
    assertThat(market.get(buyer, r.id()).paymentState()).isEqualTo("PAID");
    assertThat(events.findById(event)).isPresent();
  }

  @Test
  void cancellationKeepsPendingUntilProviderConfirmsRefundAndCanRetryDefiniteFailure() {
    Booking r = reserve(listing(1000), buyer);
    market.simulate(buyer, r.id(), true);
    assertThat(market.cancel(buyer, r.id()).paymentState()).isEqualTo("REFUND_PENDING");
    when(gateway.refund(any())).thenReturn(new DepositGateway.RefundResult("re_1", "pending"));
    market.refundOne(r.id());
    assertThat(market.get(buyer, r.id()).paymentState()).isEqualTo("REFUND_PENDING");
    when(clock.instant()).thenReturn(now.plusSeconds(11));
    when(gateway.refund(any())).thenReturn(new DepositGateway.RefundResult("re_1", "failed"));
    market.refundOne(r.id());
    assertThat(market.get(buyer, r.id()).paymentState()).isEqualTo("REFUND_FAILED");
    market.retryRefund(buyer, r.id());
    when(gateway.refund(any())).thenReturn(new DepositGateway.RefundResult("re_2", "succeeded"));
    market.refundOne(r.id());
    assertThat(market.get(buyer, r.id()).paymentState()).isEqualTo("REFUNDED");
    assertThat(market.cancel(seller, r.id()).paymentState()).isEqualTo("REFUNDED");
  }

  @Test
  void ambiguousRefundTimeoutReusesAttemptAndStaysPending() {
    Booking r = reserve(listing(1000), buyer);
    market.simulate(buyer, r.id(), true);
    market.cancel(buyer, r.id());
    when(gateway.refund(any())).thenThrow(new IllegalStateException("timeout"));
    market.refundOne(r.id());
    assertThat(market.get(buyer, r.id()).paymentState()).isEqualTo("REFUND_PENDING");
    assertThat(reservations.findById(r.id()).orElseThrow().getRefundAttempt()).isZero();
  }

  @Test
  void latePaymentRefundsOldReservationWithoutStealingNewHold() {
    Listing p = listing(1000);
    Booking old = reserve(p, buyer);
    stripe(old.id());
    when(clock.instant()).thenReturn(old.expiresAt());
    Booking next = reserve(p, other);
    market.verifiedPayment("evt_late_" + old.id(), old.id(), paid(old));
    assertThat(market.get(buyer, old.id()).status()).isEqualTo("EXPIRED");
    assertThat(market.get(buyer, old.id()).paymentState()).isEqualTo("REFUND_PENDING");
    assertThat(market.get(other, next.id()).status()).isEqualTo("PENDING_PAYMENT");
    assertThat(products.findById(p.id()).orElseThrow().getActiveReservationId())
        .isEqualTo(next.id());
  }

  @Test
  void cancellationBeforePaymentAlsoRefundsLateSuccess() {
    Booking r = reserve(listing(1000), buyer);
    stripe(r.id());
    market.cancel(buyer, r.id());
    market.verifiedPayment("evt_cancel_" + r.id(), r.id(), paid(r));
    assertThat(market.get(buyer, r.id()).status()).isEqualTo("CANCELLED");
    assertThat(market.get(buyer, r.id()).paymentState()).isEqualTo("REFUND_PENDING");
  }

  @Test
  void failuresCanRecoverButCannotRegressSuccessfulPayment() {
    Booking r = reserve(listing(1000), buyer);
    market.simulate(buyer, r.id(), false);
    assertThat(market.get(buyer, r.id()).paymentState()).isEqualTo("FAILED");
    market.simulate(buyer, r.id(), true);
    market.simulate(buyer, r.id(), false);
    assertThat(market.get(buyer, r.id()).paymentState()).isEqualTo("PAID");
  }

  @Test
  void wrongAmountAndLivePaymentsAreRejectedWithoutReceipt() {
    Booking r = reserve(listing(1000), buyer);
    stripe(r.id());
    String event = "evt_bad_" + r.id();
    assertThatThrownBy(
            () ->
                market.verifiedPayment(
                    event,
                    r.id(),
                    new DepositGateway.Verified(
                        r.id(), "cs_" + r.id(), 999, "usd", true, false, "pi_wrong")))
        .hasMessageContaining("400");
    assertThatThrownBy(
            () ->
                market.verifiedPayment(
                    event,
                    r.id(),
                    new DepositGateway.Verified(
                        r.id(), "cs_" + r.id(), 1000, "usd", true, true, "pi_live")))
        .hasMessageContaining("400");
    assertThat(events.existsById(event)).isFalse();
    assertThat(market.get(buyer, r.id()).paymentState()).isEqualTo("UNPAID");
  }

  @Test
  void privacyOwnershipAndLegacyRoutesAreEnforcedAtHttpBoundary() throws Exception {
    Listing p = listing(1000);
    Booking r = reserve(p, buyer);
    mvc.perform(get("/api/campus/listings/" + p.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pickupAddress").doesNotExist())
        .andExpect(jsonPath("$.seller.password").doesNotExist());
    mvc.perform(get("/api/campus/reservations/" + r.id())).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/campus/reservations/" + r.id()).with(user(other)))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/campus/reservations/" + r.id() + "/cancel").with(user(other)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/campus/reservations").with(user(other))).andExpect(content().json("[]"));
    mvc.perform(get("/api/campus/reservations/" + r.id()).with(user(seller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pickupAddress").isEmpty());
    mvc.perform(get("/api/payment/fake").with(user(buyer))).andExpect(status().isForbidden());
    mvc.perform(get("/api/campus/reservations").header("Authorization", "x"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void filteringIsCrossApartmentAndCombinesConditions() {
    listing(0);
    assertThat(
            market.search(
                "oak", "Furniture", 6000L, 7000L, "Good", "North Campus", "Maple Court", 0))
        .isNotEmpty();
    assertThat(market.search(null, null, null, null, null, null, "Nonexistent apartment", 0))
        .isEmpty();
    assertThat(market.search("%", null, null, null, null, null, null, 0)).isEmpty();
  }

  @Test
  void selfBookingInvalidSlotsAndInvalidDepositsAreRejected() {
    Listing p = listing(0);
    assertThatThrownBy(() -> reserve(p, seller)).hasMessageContaining("400");
    assertThatThrownBy(() -> market.reserve(buyer, new BookingInput(p.id(), now.plusSeconds(5))))
        .hasMessageContaining("400");
    assertThatThrownBy(() -> listing(6501)).hasMessageContaining("400");
  }

  @Test
  void simulationCannotSettleStripeReservations() {
    Booking r = reserve(listing(1000), buyer);
    stripe(r.id());
    assertThatThrownBy(() -> market.simulate(buyer, r.id(), true)).hasMessageContaining("403");
  }

  @Test
  void signedWebhookRejectsForgedPayload() throws Exception {
    when(gateway.mode()).thenReturn("stripe");
    when(gateway.webhookSecret()).thenReturn("whsec_test_only");
    mvc.perform(
            post("/api/campus/webhooks/stripe")
                .contentType("application/json")
                .header("Stripe-Signature", "t=1,v1=forged")
                .content("{}"))
        .andExpect(status().isBadRequest());
    verify(gateway, never()).verify(anyString());
  }

  @Test
  void signedSuccessWebhookCanBeRedeliveredWithoutDuplicatingState() throws Exception {
    Booking r = reserve(listing(1000), buyer);
    stripe(r.id());
    when(gateway.mode()).thenReturn("stripe");
    when(gateway.webhookSecret()).thenReturn("whsec_test_only");
    when(gateway.verify("cs_" + r.id())).thenReturn(paid(r));
    String payload =
        "{\"id\":\"evt_signed_"
            + r.id()
            + "\",\"object\":\"event\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_"
            + r.id()
            + "\",\"object\":\"checkout.session\"}}}";
    long timestamp = System.currentTimeMillis() / 1000;
    javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
    mac.init(
        new javax.crypto.spec.SecretKeySpec(
            "whsec_test_only".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
    String signature =
        "t="
            + timestamp
            + ",v1="
            + java.util.HexFormat.of()
                .formatHex(
                    mac.doFinal(
                        (timestamp + "." + payload)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/campus/webhooks/stripe")
                  .contentType("application/json")
                  .header("Stripe-Signature", signature)
                  .content(payload))
          .andExpect(status().isOk());
    assertThat(market.get(buyer, r.id()).status()).isEqualTo("RESERVED");
  }

  @Test
  void cancellationRacingPaymentAlwaysEndsCancelledAndRefundPending() throws Exception {
    Booking r = reserve(listing(1000), buyer);
    stripe(r.id());
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<?> payment =
          pool.submit(
              () -> {
                try {
                  start.await();
                  market.verifiedPayment("evt_race_" + r.id(), r.id(), paid(r));
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              });
      Future<?> cancellation =
          pool.submit(
              () -> {
                try {
                  start.await();
                  market.cancel(buyer, r.id());
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              });
      start.countDown();
      payment.get(10, TimeUnit.SECONDS);
      cancellation.get(10, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
    assertThat(market.get(buyer, r.id()).status()).isEqualTo("CANCELLED");
    assertThat(market.get(buyer, r.id()).paymentState()).isEqualTo("REFUND_PENDING");
  }

  @Test
  void replayAfterRefundCannotMarkDepositPaidAgain() {
    Booking r = reserve(listing(1000), buyer);
    stripe(r.id());
    market.verifiedPayment("evt_first_" + r.id(), r.id(), paid(r));
    market.cancel(buyer, r.id());
    when(gateway.refund(any()))
        .thenReturn(new DepositGateway.RefundResult("re_replay", "succeeded"));
    market.refundOne(r.id());
    market.verifiedPayment("evt_new_delivery_" + r.id(), r.id(), paid(r));
    assertThat(market.get(buyer, r.id()).paymentState()).isEqualTo("REFUNDED");
  }

  @Test
  void realJwtSignupAuthenticationAndUnknownFields() throws Exception {
    String email = "signup-" + UUID.randomUUID() + "@example.test";
    String response =
        mvc.perform(
                post("/api/campus/auth/signup")
                    .contentType("application/json")
                    .content(
                        "{\"fullName\":\"Test Person\",\"email\":\""
                            + email
                            + "\",\"password\":\"StrongTestPassword123!\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token =
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("token").asText();
    mvc.perform(get("/api/campus/reservations").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/campus/auth/signup")
                .contentType("application/json")
                .content(
                    "{\"fullName\":\"Injected"
                        + " Admin\",\"email\":\"fake@example.test\",\"password\":\"StrongPassword123!\",\"role\":\"ROLE_ADMIN\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unpaidCancellationDoesNotIssueRefundAndUnauthorizedPaymentsFail() {
    Booking r = reserve(listing(1000), buyer);
    assertThatThrownBy(() -> market.simulate(other, r.id(), true)).hasMessageContaining("403");
    assertThatThrownBy(() -> market.checkout(seller, r.id())).hasMessageContaining("403");
    assertThat(market.cancel(buyer, r.id()).paymentState()).isEqualTo("UNPAID");
    market.refundOne(r.id());
    verify(gateway, never()).refund(any());
  }

  @Test
  void modeSwitchCannotCreateRealCheckoutForMockReservation() {
    Booking r = reserve(listing(1000), buyer);
    when(gateway.mode()).thenReturn("stripe");
    assertThatThrownBy(() -> market.checkout(buyer, r.id())).hasMessageContaining("409");
    verify(gateway, never()).checkout(any());
  }

  @Test
  void modeSwitchCannotCreateMockCheckoutForStripeReservation() {
    Booking r = reserve(listing(1000), buyer);
    stripe(r.id());
    assertThatThrownBy(() -> market.checkout(buyer, r.id())).hasMessageContaining("409");
    verify(gateway, never()).checkout(any());
  }

  @Test
  void stripeMinimumIsValidatedBeforeCreatingAnUnpayableListing() {
    when(gateway.mode()).thenReturn("stripe");
    assertThatThrownBy(() -> listing(49)).hasMessageContaining("$0.50");
    assertThat(listing(50).depositCents()).isEqualTo(50);
    when(gateway.mode()).thenReturn("mock");
    assertThat(listing(1).depositCents()).isEqualTo(1);
  }

  @Test
  void longHttpsPhotoUrlFitsTheDatabaseColumn() {
    String url = "https://example.test/photo?token=" + "a".repeat(400);
    Listing p =
        market.publish(
            seller,
            new ListingInput(
                "Desk",
                "Used",
                "Furniture",
                "Good",
                "North Campus",
                "Maple Court",
                "Lobby",
                "PRIVATE UNIT 9",
                6500,
                0,
                List.of(now.plusSeconds(7200)),
                List.of(url)));
    assertThat(market.details(p.id()).images()).containsExactly(url);
  }

  @Test
  void nullImageAndFractionalCentsAreRejectedAtTheBoundary() throws Exception {
    String payload =
        "{\"title\":\"Desk\",\"description\":\"Used\",\"category\":\"Furniture\",\"condition\":\"Good\",\"campus\":\"North"
            + " Campus\",\"apartment\":\"Maple"
            + " Court\",\"pickupArea\":\"Lobby\",\"pickupAddress\":\"Private\",\"priceCents\":6500,\"depositCents\":0,\"pickupSlots\":[\"2030-01-02T12:00:00Z\"],\"images\":[null]}";
    mvc.perform(
            post("/api/campus/listings")
                .with(user(seller))
                .contentType("application/json")
                .content(payload))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/campus/listings")
                .with(user(seller))
                .contentType("application/json")
                .content(payload.replace("[null]", "[]").replace(":6500", ":6500.5")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void bothLoopbackBrowserOriginsWorkButUnrelatedOriginsAreDenied() throws Exception {
    for (String origin : List.of("http://localhost:5173", "http://127.0.0.1:5173")) {
      mvc.perform(
              options("/api/campus/auth/login")
                  .header("Origin", origin)
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }
    mvc.perform(
            options("/api/campus/auth/login")
                .header("Origin", "https://unrelated.example")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
  }
}
