/*
 * Copyright 2015-2018 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.fogmeng.monitor.collectors.collectorKafka;

import com.fogmeng.monitor.collectors.Collector;
import com.fogmeng.monitor.collectors.CollectorMetrics;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Consumes spans from Kafka messages, ignoring malformed input */
final class KafkaCollectorWorker implements Runnable {
  static final Logger LOG = LoggerFactory.getLogger(KafkaCollectorWorker.class);
  static final Callback<Void> NOOP =
      new Callback<Void>() {
        @Override
        public void onSuccess(Void value) {}

        @Override
        public void onError(Throwable t) {}
      };

  final Properties properties;
  final List<String> topics;
  final Collector collector;
  final CollectorMetrics metrics;
  /** Kafka topic partitions currently assigned to this worker. List is not modifiable. */
  final AtomicReference<List<TopicPartition>> assignedPartitions =
      new AtomicReference<>(Collections.emptyList());

  KafkaCollectorWorker(KafkaCollector.Builder builder) {
    properties = builder.properties;
    topics = Arrays.asList(builder.topic.split(","));
    collector = builder.delegate.build();
    metrics = builder.metrics;
  }

  @Override
  public void run() {
    try (KafkaConsumer kafkaConsumer = new KafkaConsumer<>(properties)) {
      kafkaConsumer.subscribe(
        topics,
        new ConsumerRebalanceListener() {
          @Override
          public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            assignedPartitions.set(Collections.emptyList());
          }

          @Override
          public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            assignedPartitions.set(Collections.unmodifiableList(new ArrayList<>(partitions)));
          }
        });
      LOG.info("Kafka consumer starting polling loop.");
      while (true) {
        final ConsumerRecords<byte[], byte[]> consumerRecords = kafkaConsumer.poll(1000);
        LOG.debug("Kafka polling returned batch of {} messages.", consumerRecords.count());
        for (ConsumerRecord<byte[], byte[]> record : consumerRecords) {
          metrics.incrementMessages();
          final byte[] bytes = record.value();

          if (bytes.length < 2) { // need two bytes to check if protobuf
            metrics.incrementMessagesDropped();
          } else {
            // If we received legacy single-span encoding, decode it into a singleton list
            if (!protobuf3(bytes) && bytes[0] <= 16 && bytes[0] != 12 /* thrift, but not list */) {
              metrics.incrementBytes(bytes.length);
              try {
                Span span = SpanBytesDecoder.THRIFT.decodeOne(bytes);
                collector.accept(Collections.singletonList(span), NOOP);
              } catch (RuntimeException e) {
                metrics.incrementMessagesDropped();
              }
            } else {
              collector.acceptSpans(bytes, NOOP);
            }
          }
        }
      }
    } catch (InterruptException e) {
      // Interrupts are normal on shutdown, intentionally swallow
    } catch (RuntimeException | Error e) {
      LOG.warn("Unexpected error in polling loop spans", e);
      throw e;
    } finally {
      LOG.info("Kafka consumer polling loop stopped.");
      LOG.info("Closing Kafka consumer...");
      LOG.info("Kafka consumer closed.");
    }
  }

  /* span key or trace ID key */
  static boolean protobuf3(byte[] bytes) {
    return bytes[0] == 10 && bytes[1] != 0; // varint follows and won't be zero
  }
}
