package com.zosh.campus;

import com.zosh.model.*;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(indexes = {@Index(columnList = "status,expiresAt"), @Index(columnList = "paymentState")})
public class Reservation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private Product product;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User buyer;

  @OneToOne(optional = false)
  private Order order;

  @Enumerated(EnumType.STRING)
  private Status status;

  @Enumerated(EnumType.STRING)
  private PaymentState paymentState;

  private long priceCents;
  private long depositCents;
  private Instant pickupSlot;
  private Instant createdAt;
  private Instant expiresAt;
  private String paymentMode;

  @Column(unique = true)
  private String checkoutId;

  private String checkoutUrl;

  @Column(unique = true)
  private String paymentIntentId;

  private String refundId;
  private int refundAttempt;
  private Instant refundCheckedAt;
  @Version private long version;

  public enum Status {
    PENDING_PAYMENT,
    RESERVED,
    COMPLETED,
    CANCELLED,
    EXPIRED
  }

  public enum PaymentState {
    NOT_REQUIRED,
    UNPAID,
    FAILED,
    PAID,
    REFUND_PENDING,
    REFUND_FAILED,
    REFUNDED
  }
}
