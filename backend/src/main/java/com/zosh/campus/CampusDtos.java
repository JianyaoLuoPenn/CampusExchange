package com.zosh.campus;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class CampusDtos {
  private CampusDtos() {}

  public record ListingInput(
      @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 2000) String description,
      @NotBlank String category,
      @NotBlank String condition,
      @NotBlank @Size(max = 100) String campus,
      @NotBlank @Size(max = 100) String apartment,
      @NotBlank @Size(max = 150) String pickupArea,
      @NotBlank @Size(max = 250) String pickupAddress,
      @Min(1) @Max(100000000) long priceCents,
      @Min(0) long depositCents,
      @NotEmpty @Size(max = 20) List<@NotNull Instant> pickupSlots,
      @Size(max = 5) List<@NotBlank @Size(max = 1000) String> images) {}

  public record Listing(
      Long id,
      String title,
      String description,
      String category,
      String condition,
      String campus,
      String apartment,
      String pickupArea,
      long priceCents,
      long depositCents,
      String status,
      Long ownerId,
      String sellerName,
      List<Instant> pickupSlots,
      List<String> images) {}

  public record BookingInput(@NotNull Long productId, @NotNull Instant pickupSlot) {}

  public record Booking(
      Long id,
      Listing product,
      Long buyerId,
      String buyerName,
      String status,
      String paymentState,
      String paymentMode,
      long priceCents,
      long depositCents,
      long balanceCents,
      Instant pickupSlot,
      Instant expiresAt,
      String pickupAddress,
      String checkoutUrl) {}

  public record Login(@NotBlank @Email String email, @NotBlank String password) {}

  public record Signup(
      @NotBlank @Size(max = 100) String fullName,
      @NotBlank @Email @Size(max = 200) String email,
      @Size(min = 10, max = 72) @NotNull String password) {}
}
