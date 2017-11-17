package io.vertx.ext.dropwizard.impl;

import io.prometheus.client.Histogram;

/**
* @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
*/
class RequestMetric {

  final Histogram.Timer timer;
  final EndpointMetric endpointMetric;

  RequestMetric(Histogram.Child metric, EndpointMetric endpointMetric) {
    this.timer = metric.startTimer();
    this.endpointMetric = endpointMetric;
  }
}
