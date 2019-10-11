package part2;

import java.util.concurrent.atomic.AtomicInteger;

class SharedHttpMetric {
  static AtomicInteger successfulRequestsCount = new AtomicInteger();
  static AtomicInteger failedRequestsCount = new AtomicInteger();
}
