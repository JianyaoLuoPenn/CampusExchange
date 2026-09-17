package com.zosh.campus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;
import static com.zosh.campus.CampusDtos.*;
@RestController @RequestMapping("/api/campus") @RequiredArgsConstructor
public class CampusController {
    private final MarketplaceService market; private final DepositGateway gateway;
    @GetMapping("/config") public Map<String,Object> config(){return Map.of("paymentMode",gateway.mode(),"currency","USD","cancellationRule","Cancel before seller confirms completion for a full deposit refund. Offline balance is not processed by this platform.");}
    @GetMapping("/listings") public List<Listing> search(@RequestParam(required=false) String q,@RequestParam(required=false) String category,
        @RequestParam(required=false) Long min,@RequestParam(required=false) Long max,@RequestParam(required=false) String condition,
        @RequestParam(required=false) String campus,@RequestParam(required=false) String apartment,@RequestParam(defaultValue="0") int page) {
        return market.search(q,category,min,max,condition,campus,apartment,page);
    }
    @GetMapping("/listings/{id}") public Listing details(@PathVariable long id){return market.details(id);}
    @PostMapping("/listings") public Listing publish(Principal p,@Valid @RequestBody ListingInput in){return market.publish(p.getName(),in);}
    @PostMapping("/reservations") public Booking reserve(Principal p,@Valid @RequestBody BookingInput in){return market.reserve(p.getName(),in);}
    @GetMapping("/reservations") public List<Booking> mine(Principal p){return market.mine(p.getName());}
    @GetMapping("/reservations/{id}") public Booking get(Principal p,@PathVariable long id){return market.get(p.getName(),id);}
    @PostMapping("/reservations/{id}/cancel") public Booking cancel(Principal p,@PathVariable long id){return market.cancel(p.getName(),id);}
    @PostMapping("/reservations/{id}/complete") public Booking complete(Principal p,@PathVariable long id){return market.complete(p.getName(),id);}
    @PostMapping("/reservations/{id}/checkout") public Booking checkout(Principal p,@PathVariable long id){return market.checkout(p.getName(),id);}
    @PostMapping("/reservations/{id}/retry-refund") public Booking retry(Principal p,@PathVariable long id){return market.retryRefund(p.getName(),id);}
    public record Simulation(boolean success) {}
    @PostMapping("/reservations/{id}/simulate") public Booking simulate(Principal p,@PathVariable long id,@RequestBody Simulation in){return market.simulate(p.getName(),id,in.success());}
}
