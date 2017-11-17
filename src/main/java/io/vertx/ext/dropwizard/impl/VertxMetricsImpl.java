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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.VertxOptions;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.DatagramSocketMetrics;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.PoolMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
class VertxMetricsImpl extends AbstractMetrics implements VertxMetrics {

  private final DropwizardMetricsOptions options;
  private final Gauge timers;
  private final Gauge verticles;
  private Handler<Void> doneHandler;
  private final boolean shutdown;
  private final Map<String, HttpClientReporter> clientReporters = new HashMap<>();

  VertxMetricsImpl(CollectorRegistry registry, boolean shutdown, VertxOptions options, DropwizardMetricsOptions metricsOptions, String baseName) {
    super(registry, baseName, new LinkedHashMap<>());

    this.options = metricsOptions;
    this.shutdown = shutdown;
    this.timers = gauge("timers");
    this.verticles = gauge("verticles", "name");

    // FIXME: is it really something that changes over time?
    Gauge eventLoopSize = gauge("event_loop_size");
    eventLoopSize.setChild(new Gauge.Child() {
      @Override public double get() {
        return options.getEventLoopPoolSize();
      }
    });
    Gauge workerPoolSize = gauge("worker_pool_size");
    workerPoolSize.setChild(new Gauge.Child() {
      @Override public double get() {
        return options.getWorkerPoolSize();
      }
    });
    if (options.isClustered()) {
      // FIXME: Set as fixed config labels? (if it won't change)
//      gauge(options::getClusterHost, "cluster-host");
//      gauge(options::getClusterPort, "cluster-port");
    }
  }

  DropwizardMetricsOptions getOptions() {
    return options;
  }

  @Override
  String projectName(String name) {
    // Special case for vertx we keep the name as is
    return name;
  }

  @Override
  public void verticleDeployed(Verticle verticle) {
    labels(verticles, verticleName(verticle)).inc();
  }

  @Override
  public void verticleUndeployed(Verticle verticle) {
    labels(verticles, verticleName(verticle)).dec();
  }

  @Override
  public void timerCreated(long id) {
    timers.inc();
  }

  @Override
  public void timerEnded(long id, boolean cancelled) {
    timers.dec();
  }

  @Override
  public EventBusMetrics createMetrics(EventBus eventBus) {
    return new PrometheusEventBusMetrics(registry(), "eventbus");
  }

  @Override
  public HttpServerMetrics<?, ?, ?> createMetrics(HttpServer server, SocketAddress localAddress, HttpServerOptions options) {
    LinkedHashMap<String, String> globalLabels = new LinkedHashMap<>();
    globalLabels.put("local", PrometheusTCPMetrics.addressName(localAddress));
    return new HttpServerMetricsImpl(registry(), "http_servers", globalLabels);
  }

  @Override
  public synchronized HttpClientMetrics<?, ?, ?, ?, ?> createMetrics(HttpClient client, HttpClientOptions options) {
    String name = options.getMetricsName();
    LinkedHashMap<String, String> globalLabels = new LinkedHashMap<>();
    final String key;
    if (name != null && name.length() > 0) {
      key = "http_clients_" + name;
      globalLabels.put("client", name);
    } else {
      key = "http_clients";
    }
    HttpClientReporter reporter = clientReporters.computeIfAbsent(key,
      id -> new HttpClientReporter(registry(), "http_clients", id, globalLabels));
    return new PrometheusHttpClientMetrics(reporter, options, globalLabels, this::closed);
  }

  private synchronized void closed(HttpClientReporter reporter, Integer maxPoolSize) {
    if (reporter.decMaxPoolSize(maxPoolSize)) {
      clientReporters.remove(reporter.id);
      reporter.close();
    }
  }

  @Override
  public TCPMetrics<?> createMetrics(SocketAddress localAddress, NetServerOptions options) {
    LinkedHashMap<String, String> globalLabels = new LinkedHashMap<>();
    globalLabels.put("local", PrometheusTCPMetrics.addressName(localAddress));
    return new PrometheusTCPMetrics(registry(), "net_servers", globalLabels);
  }

  @Override
  public TCPMetrics<?> createMetrics(NetClientOptions options) {
    String name = options.getMetricsName();
    LinkedHashMap<String, String> globalLabels = new LinkedHashMap<>();
    if (name != null && name.length() > 0) {
      globalLabels.put("client", name);
    }
    return new PrometheusTCPMetrics(registry(), "net_clients", globalLabels);
  }

  @Override
  public DatagramSocketMetrics createMetrics(DatagramSocket socket, DatagramSocketOptions options) {
    return new DatagramSocketMetricsImpl(registry(), "datagram", new LinkedHashMap<>());
  }

  @Override
  public <P> PoolMetrics<?> createMetrics(P pool, String poolType, String poolName, int maxPoolSize) {
    LinkedHashMap<String, String> globalLabels = new LinkedHashMap<>();
    globalLabels.put("pool_type", poolType);
    globalLabels.put("pool_name", poolName);
    return new PoolMetricsImpl(registry(), "pools", globalLabels, maxPoolSize);
  }

  @Override
  public void close() {
    if (shutdown) {
      RegistryHelper.shutdown(registry);
      if (options.getRegistryName() != null) {
        SharedMetricRegistries.remove(options.getRegistryName());
      }
    }
    List<HttpClientReporter> reporters;
    synchronized (this) {
      reporters = new ArrayList<>(clientReporters.values());
    }
    for (HttpClientReporter reporter : reporters) {
      reporter.close();
    }
    if (doneHandler != null) {
      doneHandler.handle(null);
    }
  }

  @Override
  public boolean isMetricsEnabled() {
    return true;
  }

  void setDoneHandler(Handler<Void> handler) {
    this.doneHandler = handler;
  }

  private static String verticleName(Verticle verticle) {
    return verticle.getClass().getName();
  }

}
