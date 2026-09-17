package com.zosh.campus;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
  @Query("select r.product.id from Reservation r where r.id=:id")
  java.util.Optional<Long> productId(@Param("id") Long id);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Reservation r where r.id=:id")
  java.util.Optional<Reservation> lockById(@Param("id") Long id);

  @Query(
      "select r from Reservation r where r.buyer.id=:user or r.product.owner.id=:user order by r.id"
          + " desc")
  List<Reservation> forUser(@Param("user") Long user);

  @Query(
      "select r.id from Reservation r where"
          + " r.status=com.zosh.campus.Reservation$Status.PENDING_PAYMENT and r.expiresAt <= :now")
  List<Long> expired(@Param("now") Instant now);

  @Query(
      "select r.id from Reservation r where"
          + " r.paymentState=com.zosh.campus.Reservation$PaymentState.REFUND_PENDING")
  List<Long> refunds();
}
