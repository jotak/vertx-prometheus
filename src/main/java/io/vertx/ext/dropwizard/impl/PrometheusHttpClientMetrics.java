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
import java.util.function.BiConsumer;

import io.prometheus.client.Histogram;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.WebSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
class PrometheusHttpClientMetrics extends AbstractMetrics implements HttpClientMetrics<RequestMetric, Void, Void, EndpointMetric, Histogram.Timer> {

  private final HttpClientReporter clientReporter;
  private final int maxPoolSize;
  private final BiConsumer<HttpClientReporter, Integer> onClose;

  PrometheusHttpClientMetrics(HttpClientReporter clientReporter,
                              HttpClientOptions options,
                              LinkedHashMap<String, String> labels,
                              BiConsumer<HttpClientReporter, Integer> onClose) {
    super(clientReporter.registry(), clientReporter.baseName(), labels);
    this.clientReporter = clientReporter;
    this.onClose = onClose;
    clientReporter.incMaxPoolSize(maxPoolSize = options.getMaxPoolSize());
  }

  @Override
  public EndpointMetric createEndpoint(String host, int port, int maxPoolSize) {
    return new EndpointMetric(clientReporter, host + ":" + port);
  }

  @Override
  public void closeEndpoint(String host, int port, EndpointMetric endpointMetric) {
  }

  @Override
  public Histogram.Timer enqueueRequest(EndpointMetric endpointMetric) {
    return endpointMetric.enqueued();
  }

  @Override
  public void dequeueRequest(EndpointMetric endpointMetric, Histogram.Timer timer) {
    endpointMetric.dequeued(timer);
  }

  @Override
  public void endpointConnected(EndpointMetric endpointMetric,Void socketMetric) {
    if (endpointMetric != null) {
      endpointMetric.incConnections();
    }
  }

  @Override
  public void endpointDisconnected(EndpointMetric endpointMetric, Void socketMetric) {
    if (endpointMetric != null) {
      endpointMetric.decConnections();
    }
  }

  @Override
  public RequestMetric requestBegin(EndpointMetric endpointMetric, Void socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
    if (endpointMetric != null) {
      endpointMetric.incInUse();
    }
    return clientReporter.createRequestMetric(request.method().toString(), request.uri(), endpointMetric);
  }

  @Override
  public void requestEnd(RequestMetric requestMetric) {
    if (requestMetric.endpointMetric != null) {
      requestMetric.endpointMetric.startTtfb();
    }
  }

  @Override
  public void responseBegin(RequestMetric requestMetric, HttpClientResponse response) {
    if (requestMetric.endpointMetric != null) {
      requestMetric.endpointMetric.endTtfb();
    }
  }

  @Override
  public RequestMetric responsePushed(EndpointMetric endpointMetric, Void socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
    if (endpointMetric != null) {
      endpointMetric.incInUse();
    }
    return requestBegin(endpointMetric, socketMetric, localAddress, remoteAddress, request);
  }

  @Override
  public void requestReset(RequestMetric requestMetric) {
    clientReporter.responseEnd(requestMetric, 0);
  }

  @Override
  public void responseEnd(RequestMetric requestMetric, HttpClientResponse response) {
    clientReporter.responseEnd(requestMetric, response.statusCode());
  }

  @Override
  public Void connected(EndpointMetric endpointMetric, Void socketMetric, WebSocket webSocket) {
    clientReporter.webSocketConnected();
    return null;
  }

  @Override
  public void disconnected(Void webSocketMetric) {
    clientReporter.webSocketDisconnected();
  }

  @Override
  public Void connected(SocketAddress remoteAddress, String remoteName) {
    return clientReporter.connected(remoteAddress, remoteName);
  }

  @Override
  public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
    clientReporter.disconnected(socketMetric, remoteAddress);
  }

  @Override
  public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    clientReporter.bytesRead(socketMetric, remoteAddress, numberOfBytes);
  }

  @Override
  public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    clientReporter.bytesWritten(socketMetric, remoteAddress, numberOfBytes);
  }

  @Override
  public void exceptionOccurred(Void socketMetric, SocketAddress remoteAddress, Throwable t) {
    clientReporter.exceptionOccurred(socketMetric, remoteAddress, t);
  }

  @Override
  public boolean isEnabled() {
    return clientReporter.isEnabled();
  }

  @Override
  public void close() {
    onClose.accept(clientReporter, maxPoolSize);
  }
}
