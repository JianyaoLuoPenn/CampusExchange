package com.zosh.campus;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
public class PaymentEvent {
    @Id public String id;
    public Instant receivedAt;
    public PaymentEvent() {}
    public PaymentEvent(String id, Instant at) { this.id=id; this.receivedAt=at; }
}
