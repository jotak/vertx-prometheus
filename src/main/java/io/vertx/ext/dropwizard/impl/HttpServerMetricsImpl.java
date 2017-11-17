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
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.HttpServerMetrics;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
class HttpServerMetricsImpl extends PrometheusTCPMetrics implements HttpServerMetrics<RequestMetric, Void, Void> {

  HttpServerMetricsImpl(CollectorRegistry registry, String baseName, LinkedHashMap<String, String> globalLabels) {
    super(registry, baseName, globalLabels);
  }

  @Override
  public RequestMetric requestBegin(Void socketMetric, HttpServerRequest request) {
    return createRequestMetric(request.method().toString(), request.uri(), null);
  }

  @Override
  public Void upgrade(RequestMetric requestMetric, ServerWebSocket serverWebSocket) {
    webSocketConnected();
    return null;
  }

  @Override
  public void responseEnd(RequestMetric requestMetric, HttpServerResponse response) {
    super.responseEnd(requestMetric, response.getStatusCode());
  }

  @Override
  public void requestReset(RequestMetric requestMetric) {
  }

  @Override
  public RequestMetric responsePushed(Void socketMetric, HttpMethod method, String uri, HttpServerResponse response) {
    return createRequestMetric(method.toString(), uri, null);
  }

  @Override
  public Void connected(Void socketMetric, ServerWebSocket serverWebSocket) {
    webSocketConnected();
    return null;
  }

  @Override
  public void disconnected(Void serverWebSocketMetric) {
    webSocketDisconnected();
  }
}
