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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.SimpleCollector;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.Measured;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;

/**
 * Base Codahale metrics object.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public abstract class AbstractMetrics implements Metrics {

  public static AbstractMetrics unwrap(Measured measured) {
    MetricsProvider provider = (MetricsProvider) measured;
    Metrics baseMetrics = provider.getMetrics();
    if (baseMetrics instanceof AbstractMetrics) {
      return (AbstractMetrics) baseMetrics;
    }
    return null;
  }

  private final ConcurrentMap<String, Collector> collectors = new ConcurrentHashMap<>();
  private final LinkedHashMap<String, String> globalLabels; // order matters
  private final CollectorRegistry registry;
  private final String baseName;

  AbstractMetrics(CollectorRegistry registry, String baseName, LinkedHashMap<String, String> globalLabels) {
    this.registry = registry;
    this.baseName = baseName;
    this.globalLabels = globalLabels;
  }

  /**
   * Will return the metrics that correspond with a given base name.
   *
   * @return the map of metrics where the key is the name of the metric (excluding the base name) and the value is
   * the json data representing that metric
   */
  public JsonObject metrics(String baseName) {
    Map<String, Object> map = collectors.
        entrySet().
        stream().
        filter(e -> e.getKey().startsWith(baseName)).
        collect(Collectors.toMap(
            e -> projectName(e.getKey()),
            e -> Helper.convertMetric(e.getValue(), TimeUnit.SECONDS, TimeUnit.MILLISECONDS)));
    return new JsonObject(map);
  }

  /**
   * Will return the metrics that correspond with this measured object.
   *
   * @see #metrics(String)
   */
  public JsonObject metrics() {
    return metrics(baseName());
  }

  String projectName(String name) {
    String baseName = baseName();
    return name.substring(baseName.length() + 1);
  }

  protected CollectorRegistry registry() {
    return registry;
  }

  public String baseName() {
    return baseName;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  private String fullName(String name) {
    return baseName + "_" + name;
  }

  private Collector collector(Supplier<SimpleCollector.Builder> sup, String name, String... labels) {
    String fullName = fullName(name);
    final Collector collector;
    if (labels.length > 0 || !globalLabels.isEmpty()) {
      String[] allLabels = globalLabels.keySet().toArray(new String[labels.length + globalLabels.size()]);
      System.arraycopy(labels, 0, allLabels, globalLabels.size(), labels.length);
      collector = sup.get().name(fullName).labelNames(allLabels).register(registry);
    } else {
      collector = sup.get().name(fullName).labelNames(labels).register(registry);
    }
    collectors.put(fullName, collector);
    return collector;
  }

  protected Gauge gauge(String name, String... labels) {
    return (Gauge) collector(Gauge::build, name, labels);
  }

  protected Counter counter(String name, String... labels) {
    return (Counter) collector(Counter::build, name, labels);
  }

  protected Histogram histogram(String name, String... labels) {
    return (Histogram) collector(Histogram::build, name, labels);
  }

  protected <C> C labels(SimpleCollector<C> collector, String... labels) {
    if (labels.length == 0 && globalLabels.isEmpty()) {
      return collector.labels();
    }
    String[] allLabels = globalLabels.values().toArray(new String[labels.length + globalLabels.size()]);
    System.arraycopy(labels, 0, allLabels, globalLabels.size(), labels.length);
    return collector.labels(allLabels);
  }

//  protected Meter meter(String... names) {
//    try {
//      return registry.meter(nameOf(names));
//    } catch (Exception e) {
//      return new Meter();
//    }
//  }
//
//  protected Timer timer(String... names) {
//    try {
//      return registry.timer(nameOf(names));
//    } catch (Exception e) {
//      return new Timer();
//    }
//  }

//  protected ThroughputMeter throughputMeter(String... names) {
//    try {
//      return RegistryHelper.throughputMeter(registry, nameOf(names));
//    } catch (Exception e) {
//      return new ThroughputMeter();
//    }
//  }
//
//  protected ThroughputTimer throughputTimer(String... names) {
//    try {
//      return RegistryHelper.throughputTimer(registry, nameOf(names));
//    } catch (Exception e) {
//      return new ThroughputTimer();
//    }
//  }

  void remove(String name) {
    Collector c = collectors.get(name);
    if (c != null) {
      registry.unregister(c);
      collectors.remove(name);
    }
  }

  void removeAll() {
    // Remove only those in collectors, as the registry might be shared
    collectors.forEach((name, c) -> registry.unregister(c));
    collectors.clear();
  }
}
