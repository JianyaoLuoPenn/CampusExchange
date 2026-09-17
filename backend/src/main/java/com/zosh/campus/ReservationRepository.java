package com.zosh.campus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
public interface ReservationRepository extends JpaRepository<Reservation,Long> {
    @Query("select r from Reservation r where r.buyer.id=:user or r.product.owner.id=:user order by r.id desc")
    List<Reservation> forUser(@Param("user") Long user);
    @Query("select r.id from Reservation r where r.status=com.zosh.campus.Reservation$Status.PENDING_PAYMENT and r.expiresAt <= :now")
    List<Long> expired(@Param("now") Instant now);
    @Query("select r.id from Reservation r where r.paymentState=com.zosh.campus.Reservation$PaymentState.REFUND_PENDING")
    List<Long> refunds();
}
