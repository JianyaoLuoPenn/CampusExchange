package com.zosh.campus;
import com.zosh.model.*;
import com.zosh.repository.*;
import com.zosh.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import static com.zosh.campus.Reservation.Status.*;
import static com.zosh.campus.Reservation.PaymentState.*;
import static com.zosh.campus.CampusDtos.*;

@Service @RequiredArgsConstructor
public class MarketplaceService {
    private final ProductRepository products;
    private final UserRepository users;
    private final OrderRepository orders;
    private final ReservationRepository reservations;
    private final PaymentEventRepository events;
    private final EntityManager em;
    private final Clock clock;
    private final DepositGateway gateway;
    @Value("${campus.hold-minutes}") private long holdMinutes;
    private static final Set<String> CATEGORIES=Set.of("Furniture","Electronics","Textbooks","Home essentials");
    private static final Set<String> CONDITIONS=Set.of("Like new","Good","Fair");

    User user(String email) {
        User u=users.findByEmail(email);
        if(u==null) throw error(401,"Sign in again"); return u;
    }
    static ResponseStatusException error(int status,String reason) { return new ResponseStatusException(HttpStatus.valueOf(status),reason); }
    Product lock(long id) { return products.lockById(id).orElseThrow(()->error(404,"Listing not found")); }
    // Every mutation locks Product first, then refreshes Reservation. This order avoids deadlocks
    // and stale snapshots under MySQL REPEATABLE READ when a competing transaction just committed.
    Reservation lockedReservation(long id) {
        Reservation r=reservations.findById(id).orElseThrow(()->error(404,"Reservation not found"));
        lock(r.getProduct().getId()); em.refresh(r); return r;
    }
    void participant(Reservation r,User u) {
        if(!r.getBuyer().getId().equals(u.getId()) && !r.getProduct().getOwner().getId().equals(u.getId())) throw error(403,"Only transaction participants can access this reservation");
    }
    @Transactional(readOnly=true)
    public List<Listing> search(String q,String category,Long min,Long max,String condition,String campus,String apartment,int page) {
        if(page<0 || (min!=null && min<0) || (max!=null && max<0) || (min!=null && max!=null && min>max)) throw error(400,"Invalid filters");
        Specification<Product> spec=(root,query,cb)->cb.isNotNull(root.get("owner"));
        if(q!=null && !q.isBlank()) { String pattern="%"+q.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
            spec=spec.and((root,query,cb)->cb.or(cb.like(cb.lower(root.get("title")),pattern,'\\'),cb.like(cb.lower(root.get("description")),pattern,'\\'))); }
        String[][] filters={{"campusCategory",category},{"itemCondition",condition},{"campus",campus},{"apartment",apartment}};
        for(String[] f:filters) if(f[1]!=null&&!f[1].isBlank()) spec=spec.and((root,query,cb)->cb.equal(root.get(f[0]),f[1]));
        if(min!=null) spec=spec.and((root,query,cb)->cb.ge(root.get("priceCents"),min));
        if(max!=null) spec=spec.and((root,query,cb)->cb.le(root.get("priceCents"),max));
        return products.findAll(spec,PageRequest.of(page,24,org.springframework.data.domain.Sort.by("id").descending())).stream().map(this::listing).toList();
    }
    @Transactional(readOnly=true) public Listing details(long id) {
        Product p=products.findById(id).orElseThrow(()->error(404,"Listing not found"));
        if(p.getOwner()==null) throw error(404,"Listing not found"); return listing(p);
    }
    Listing listing(Product p) { return new Listing(p.getId(),p.getTitle(),p.getDescription(),p.getCampusCategory(),p.getItemCondition(),p.getCampus(),p.getApartment(),p.getPickupArea(),p.getPriceCents(),p.getDepositCents(),p.getListingStatus(),p.getOwner().getId(),p.getOwner().getFullName(),List.copyOf(p.getPickupSlots()),List.copyOf(p.getImages())); }
    @Transactional public Listing publish(String email,ListingInput in) {
        if(!CATEGORIES.contains(in.category()) || !CONDITIONS.contains(in.condition())) throw error(400,"Choose a supported category and condition");
        if(in.depositCents()>in.priceCents()) throw error(400,"Deposit cannot exceed the price");
        if(in.pickupSlots().stream().anyMatch(t->!t.isAfter(clock.instant())) || new HashSet<>(in.pickupSlots()).size()!=in.pickupSlots().size()) throw error(400,"Pickup slots must be distinct future times");
        if(in.images()!=null && in.images().stream().anyMatch(url->!url.matches("https://[^\\s]+"))) throw error(400,"Images must use HTTPS URLs");
        Product p=new Product(); p.setOwner(user(email)); p.setTitle(in.title().trim()); p.setDescription(in.description());
        p.setCampusCategory(in.category());p.setItemCondition(in.condition());p.setCampus(in.campus().trim());p.setApartment(in.apartment().trim());
        p.setPickupArea(in.pickupArea());p.setPickupAddress(in.pickupAddress());p.setPriceCents(in.priceCents());p.setDepositCents(in.depositCents());
        p.setPickupSlots(new ArrayList<>(in.pickupSlots())); p.setImages(in.images()==null?new ArrayList<>():new ArrayList<>(in.images()));
        p.setQuantity(1);p.setCreatedAt(LocalDateTime.now(clock)); return listing(products.save(p));
    }
    @Transactional public Booking reserve(String email,BookingInput in) {
        User buyer=user(email); Product p=lock(in.productId());
        if(p.getOwner()==null) throw error(404,"Listing not found");
        if(p.getOwner().getId().equals(buyer.getId())) throw error(400,"You cannot reserve your own listing");
        if(p.getActiveReservationId()!=null) { Reservation old=reservations.findById(p.getActiveReservationId()).orElseThrow(); em.refresh(old); expire(old); }
        if(!"AVAILABLE".equals(p.getListingStatus())) throw error(409,"This item is already held or sold");
        if(!p.getPickupSlots().contains(in.pickupSlot()) || !in.pickupSlot().isAfter(clock.instant())) throw error(400,"Choose an available future pickup time");
        Order order=new Order(); order.setUser(buyer); order.setTotalItem(1); order.setOrderStatus(OrderStatus.PENDING);
        OrderItem item=new OrderItem(); item.setProduct(p); item.setQuantity(1);item.setOrder(order);item.setUserId(buyer.getId());order.getOrderItems().add(item);
        order=orders.save(order);
        Reservation r=new Reservation();r.setProduct(p);r.setBuyer(buyer);r.setOrder(order);r.setPriceCents(p.getPriceCents());r.setDepositCents(p.getDepositCents());
        r.setPickupSlot(in.pickupSlot());r.setCreatedAt(clock.instant());r.setPaymentMode(gateway.mode());
        boolean deposit=p.getDepositCents()>0;
        r.setStatus(deposit?PENDING_PAYMENT:RESERVED);r.setPaymentState(deposit?UNPAID:NOT_REQUIRED);
        r.setExpiresAt(deposit?Collections.min(List.of(clock.instant().plus(Duration.ofMinutes(holdMinutes)),in.pickupSlot())):null);
        reservations.saveAndFlush(r);p.setActiveReservationId(r.getId());p.setListingStatus(deposit?"PENDING_PAYMENT":"RESERVED");p.setQuantity(0);
        return booking(r);
    }
    @Transactional(readOnly=true) public List<Booking> mine(String email) { return reservations.forUser(user(email).getId()).stream().map(this::booking).toList(); }
    @Transactional public Booking get(String email,long id) { Reservation r=lockedReservation(id); participant(r,user(email)); expire(r);return booking(r); }
    Booking booking(Reservation r) {
        // An unpaid, expired or cancelled reservation does not reveal the precise address.
        String address=(r.getStatus()==RESERVED || r.getStatus()==COMPLETED)?r.getProduct().getPickupAddress():null;
        return new Booking(r.getId(),listing(r.getProduct()),r.getBuyer().getId(),r.getBuyer().getFullName(),r.getStatus().name(),r.getPaymentState().name(),r.getPaymentMode(),r.getPriceCents(),r.getDepositCents(),r.getPriceCents()-r.getDepositCents(),r.getPickupSlot(),r.getExpiresAt(),address,r.getCheckoutUrl());
    }
    @Transactional public Booking cancel(String email,long id) {
        Reservation r=lockedReservation(id);participant(r,user(email));
        if(r.getStatus()==COMPLETED) throw error(409,"Completed transactions cannot be cancelled");
        if(r.getStatus()==CANCELLED || r.getStatus()==EXPIRED) return booking(r);
        r.setStatus(CANCELLED);r.getOrder().setOrderStatus(OrderStatus.CANCELLED);release(r);
        if(r.getPaymentState()==PAID) r.setPaymentState(REFUND_PENDING);
        return booking(r);
    }
    @Transactional public Booking complete(String email,long id) {
        Reservation r=lockedReservation(id);
        if(!r.getProduct().getOwner().getId().equals(user(email).getId())) throw error(403,"Only the seller can confirm completion");
        if(r.getStatus()==COMPLETED)return booking(r);
        if(r.getStatus()!=RESERVED) throw error(409,"Only reserved transactions can complete");
        r.setStatus(COMPLETED);r.getProduct().setListingStatus("SOLD");r.getOrder().setOrderStatus(OrderStatus.DELIVERED);return booking(r);
    }
    void release(Reservation r) {
        Product p=r.getProduct();
        if(Objects.equals(p.getActiveReservationId(),r.getId())) {p.setActiveReservationId(null);p.setListingStatus("AVAILABLE");p.setQuantity(1);}
    }
    void expire(Reservation r) {
        if(r.getStatus()==PENDING_PAYMENT && !r.getExpiresAt().isAfter(clock.instant())) {r.setStatus(EXPIRED);r.getOrder().setOrderStatus(OrderStatus.CANCELLED);release(r);}
    }
    @Transactional public void expireOne(long id) { expire(lockedReservation(id)); }
    @Transactional public Booking checkout(String email,long id) {
        Reservation r=lockedReservation(id);
        if(!r.getBuyer().getId().equals(user(email).getId()))throw error(403,"Only the buyer can pay");
        expire(r); if(r.getStatus()!=PENDING_PAYMENT) return booking(r);
        if(r.getCheckoutId()==null) {DepositGateway.Checkout result=gateway.checkout(r);r.setCheckoutId(result.id());r.setCheckoutUrl(result.url());}
        return booking(r);
    }
    @Transactional public Booking simulate(String email,long id,boolean success) {
        Reservation r=lockedReservation(id);
        if(!"mock".equals(r.getPaymentMode()) || !"mock".equals(gateway.mode()))throw error(403,"Simulation is disabled in Stripe mode");
        if(!r.getBuyer().getId().equals(user(email).getId()))throw error(403,"Only the buyer can simulate their payment");
        if(r.getDepositCents()==0)throw error(400,"No deposit is required");
        payment(r,success,"mock_pi_"+id);return booking(r);
    }
    void payment(Reservation r,boolean paid,String intent) {
        if(r.getPaymentState()==PAID || r.getPaymentState()==REFUND_PENDING || r.getPaymentState()==REFUND_FAILED || r.getPaymentState()==REFUNDED)return;
        expire(r);
        if(!paid) {r.setPaymentState(FAILED);return;}
        r.setPaymentIntentId(intent);
        if(r.getStatus()==PENDING_PAYMENT && Objects.equals(r.getProduct().getActiveReservationId(),r.getId())) {
            r.setPaymentState(PAID);r.setStatus(RESERVED);r.getProduct().setListingStatus("RESERVED");r.getOrder().setOrderStatus(OrderStatus.CONFIRMED);
        } else {r.setPaymentState(REFUND_PENDING);} // Late payment never takes an item from a new buyer.
    }
    @Transactional public void verifiedPayment(String eventId,long id,DepositGateway.Verified verified) {
        Reservation r=lockedReservation(id);
        if(events.existsById(eventId))return;
        if(!"stripe".equals(r.getPaymentMode()) || !Objects.equals(r.getCheckoutId(),verified.sessionId()) || r.getDepositCents()!=verified.amount() || !"usd".equals(verified.currency()) || verified.live())throw error(400,"Payment does not match reservation");
        if(verified.paid() && (verified.intent()==null || verified.intent().isBlank()))throw error(400,"Missing payment intent");
        payment(r,verified.paid(),verified.intent());events.save(new PaymentEvent(eventId,clock.instant()));
    }
    @Transactional public void refundOne(long id) {
        Reservation r=lockedReservation(id); if(r.getPaymentState()!=REFUND_PENDING)return;
        if(r.getRefundCheckedAt()!=null && r.getRefundCheckedAt().isAfter(clock.instant().minusSeconds(10)))return;
        r.setRefundCheckedAt(clock.instant());
        try {DepositGateway.RefundResult result=gateway.refund(r);r.setRefundId(result.id());
            if("succeeded".equals(result.status()))r.setPaymentState(REFUNDED);
            else if("failed".equals(result.status()) || "canceled".equals(result.status()))r.setPaymentState(REFUND_FAILED);
        } catch(RuntimeException e) {
            // An ambiguous timeout is still pending. Retry with the SAME idempotency key.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Refund remains pending for reservation {} ({})",id,e.getClass().getSimpleName());
        }
    }
    @Transactional public Booking retryRefund(String email,long id) {
        Reservation r=lockedReservation(id);participant(r,user(email));
        if(r.getPaymentState()==REFUND_FAILED) {r.setRefundAttempt(r.getRefundAttempt()+1);r.setRefundId(null);r.setRefundCheckedAt(null);r.setPaymentState(REFUND_PENDING);}return booking(r);
    }
}
