/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.dropwizard.impl;

import java.util.LinkedHashMap;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.TCPMetrics;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
class PrometheusTCPMetrics extends AbstractMetrics implements TCPMetrics<Void> {

  private final Histogram requests;
  private final Counter responses;
  private final Gauge connections;
// FIXME  private final Timer connections;
  private final Histogram bytesRead;
  private final Histogram bytesWritten;
  private final Counter exceptions;
  private final Gauge webSockets;

  protected volatile boolean closed;

  PrometheusTCPMetrics(CollectorRegistry registry, String baseName, LinkedHashMap<String, String> labels) {
    super(registry, baseName, labels);
    requests = histogram("requests", "method", "uri");
    responses = counter("responses", "code");
    connections = gauge("connections", "remote");
// FIXME    this.connections = timer("connections");
    exceptions = counter("exceptions", "remote", "name");
    bytesRead = histogram("bytes_read", "remote");
    bytesWritten = histogram("bytes_written", "remote");
    webSockets = gauge("websockets");
  }

  @Override
  public void close() {
    this.closed = true;
    removeAll();
  }

  static String addressName(SocketAddress address) {
    return address == null ? "?" : (address.host() + ":" + address.port());
  }

  @Override
  public Void connected(SocketAddress remoteAddress, String remoteName) {
    if (closed) {
      return null;
    }
    labels(connections, addressName(remoteAddress)).inc();
    return null;
  }

  @Override
  public void disconnected(Void ctx, SocketAddress remoteAddress) {
    if (closed) {
      return;
    }
    labels(connections, addressName(remoteAddress)).dec();
// FIXME    connections.update(System.nanoTime() - ctx, TimeUnit.NANOSECONDS);
  }

  @Override
  public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    if (closed) {
      return;
    }
    labels(bytesRead, addressName(remoteAddress)).observe(numberOfBytes);
  }

  @Override
  public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    if (closed) {
      return;
    }
    labels(bytesWritten, addressName(remoteAddress)).observe(numberOfBytes);
  }

  @Override
  public void exceptionOccurred(Void socketMetric, SocketAddress remoteAddress, Throwable t) {
    if (closed) {
      return;
    }
    labels(exceptions, addressName(remoteAddress), t.getClass().toString()).inc();
  }

  RequestMetric createRequestMetric(String method, String uri, EndpointMetric endpointMetric) {
    return new RequestMetric(labels(requests, method, uri), endpointMetric);
  }

  void responseEnd(RequestMetric metric, int statusCode) {
    if (closed) {
      return;
    }
    if (statusCode > 0) {
      labels(responses, String.valueOf(statusCode)).inc();
    }
    metric.timer.observeDuration();
    if (metric.endpointMetric != null) {
      metric.endpointMetric.decInUse();
      metric.endpointMetric.observeUsage();
    }
  }

  void webSocketConnected() {
    if (closed) {
      return;
    }
    labels(webSockets).inc();
  }

  void webSocketDisconnected() {
    if (closed) {
      return;
    }
    labels(webSockets).dec();
  }
}
