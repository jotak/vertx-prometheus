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
import java.util.concurrent.ConcurrentLinkedDeque;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.spi.metrics.EventBusMetrics;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
class PrometheusEventBusMetrics extends AbstractMetrics implements EventBusMetrics<PrometheusEventBusMetrics.HandlerMetric> {

  private static final String LOCAL = "local";
  private static final String REMOTE = "remote";

  private final Gauge handlerCount;
  private final Gauge pending;
  private final Histogram processTime;
  private final Counter processFailures;
  private final Histogram messagesBytes;
  private final Counter messages;
  private final Counter replyFailures;

  PrometheusEventBusMetrics(CollectorRegistry registry, String baseName) {
    super(registry, baseName, new LinkedHashMap<>());

    handlerCount = gauge("handlers",
      "address");
    pending = gauge("pending",
      "address", "origin");
    processTime = histogram("process_time",
      "address");
    processFailures = counter("process_failures",
      "address");
    messages = counter("messages",
      "address", "status", "origin");
    replyFailures = counter("reply_failures",
      "address", "failure");
    messagesBytes = histogram("message_bytes",
      "address", "direction");
  }

  @Override
  public void messageWritten(String address, int size) {
    labels(messagesBytes, address, "out").observe(size);
  }

  @Override
  public void messageRead(String address, int size) {
    labels(messagesBytes, address, "in").observe(size);
  }

  @Override
  public void close() {
  }

  @Override
  public HandlerMetric handlerRegistered(String address, String repliedAddress) {
    labels(handlerCount, address).inc();
    return new HandlerMetric(address);
  }

  @Override
  public void handlerUnregistered(HandlerMetric handler) {
    labels(handlerCount, handler.address).dec();
  }

  @Override
  public void scheduleMessage(HandlerMetric handler, boolean local) {
    labels(pending, handler.address, local ? LOCAL : REMOTE).inc();
  }

  @Override
  public void beginHandleMessage(HandlerMetric handler, boolean local) {
    labels(pending, handler.address, local ? LOCAL : REMOTE).dec();
    handler.startTimer(processTime);
  }

  @Override
  public void endHandleMessage(HandlerMetric handler, Throwable failure) {
    handler.endTimer();
    if (failure != null) {
      labels(processFailures, handler.address).inc();
    }
  }

  @Override
  public void messageSent(String address, boolean publish, boolean local, boolean remote) {
    labels(messages, address, publish ? "published" : "sent", local ? LOCAL : REMOTE).inc();
  }

  @Override
  public void messageReceived(String address, boolean publish, boolean local, int handlers) {
    labels(messages, address, "received", local ? LOCAL : REMOTE).inc();
    if (handlers > 0) {
      labels(messages, address, "delivered", local ? LOCAL : REMOTE).inc();
    }
  }

  @Override
  public void replyFailure(String address, ReplyFailure failure) {
    labels(replyFailures, address, failure.name()).inc();
  }

  public class HandlerMetric {
    final String address;
    final ConcurrentLinkedDeque<Histogram.Timer> timers = new ConcurrentLinkedDeque<>();

    HandlerMetric(String address) {
      this.address = address;
    }

    void startTimer(Histogram histogram) {
      timers.addFirst(labels(histogram, address).startTimer());
    }

    void endTimer() {
      Histogram.Timer t = timers.pollLast();
      if (t != null) {
        t.observeDuration();
      }
    }
  }
}
