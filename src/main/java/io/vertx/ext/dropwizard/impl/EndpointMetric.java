package io.vertx.ext.dropwizard.impl;

import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class EndpointMetric {

  private final HttpClientReporter reporter;
  private final String remote;
  private final Histogram queueDelay;
  private final Gauge queueSize;
  private final Gauge connections;
  private final Histogram.Timer usage;
  private final Histogram ttfb;
  private final Gauge inUse;

  private Histogram.Timer ttfbTimer;

  EndpointMetric(HttpClientReporter reporter, String name) {
    this.reporter = reporter;
    this.remote = name;
    queueDelay = reporter.histogram("queue_delay", "remote");
    queueSize = reporter.gauge("queue_size", "remote");
    connections = reporter.gauge("connections", "remote");
    usage = reporter.labels(reporter.histogram("usage", "remote"), remote).startTimer();
    ttfb = reporter.histogram("ttfb", "remote");
    inUse = reporter.gauge("in_use", "remote");
  }

  void startTtfb() {
    this.ttfbTimer = reporter.labels(ttfb, remote).startTimer();
  }

  void endTtfb() {
    if (ttfbTimer != null) {
      ttfbTimer.observeDuration();
    }
  }

  void incInUse() {
    reporter.labels(inUse, remote).inc();
  }

  void decInUse() {
    reporter.labels(inUse, remote).dec();
  }

  void incConnections() {
    reporter.labels(connections, remote).inc();
  }

  void decConnections() {
    reporter.labels(connections, remote).dec();
  }

  void observeUsage() {
    usage.observeDuration();
  }

  Histogram.Timer enqueued() {
    reporter.labels(queueSize, remote).inc();
    return reporter.labels(queueDelay, remote).startTimer();
  }

  void dequeued(Histogram.Timer timer) {
    reporter.labels(queueSize, remote).dec();
    timer.observeDuration();
  }
}
