package com.zosh.campus;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationJobs {
  private final ReservationRepository reservations;
  private final MarketplaceService market;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${campus.sweep-ms:5000}")
  public void sweep() {
    for (Long id : reservations.expired(clock.instant())) run(id, () -> market.expireOne(id));
    for (Long id : reservations.refunds()) run(id, () -> market.refundOne(id));
  }

  private void run(Long id, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn("Reservation maintenance will retry {} ({})", id, e.getClass().getSimpleName());
    }
  }
}
