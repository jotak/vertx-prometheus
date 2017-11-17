package io.vertx.ext.dropwizard.impl;

import java.util.LinkedHashMap;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpClientReporter extends PrometheusTCPMetrics {

  private final Gauge totalMaxPoolSize;
  String id;

  HttpClientReporter(CollectorRegistry registry, String baseName, String id, LinkedHashMap<String, String> labels) {
    super(registry, baseName, labels);
    totalMaxPoolSize = gauge("connections_max_pool_size");
    this.id = id;
  }

  void incMaxPoolSize(int delta) {
    labels(totalMaxPoolSize).inc(delta);
  }

  boolean decMaxPoolSize(int delta) {
    labels(totalMaxPoolSize).dec(delta);
    return labels(totalMaxPoolSize).get() == 0d;
  }
}
