/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2021 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2021 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.core.ipc.twin.api;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MockTwinPublisherBroker implements TwinPublisherBroker {

    private Function<TwinRequest, TwinResponse> twinProvider;
    private final Hashtable<String, Object> kafkaConfig = new Hashtable<>();
    private KafkaProducer<String, byte[]> rpcResponseProducer;
    private KafkaConsumerRunner kafkaConsumerRunner;
    private ObjectMapper objectMapper = new ObjectMapper();

    public MockTwinPublisherBroker(Hashtable<String, Object> config) {
        kafkaConfig.putAll(config);
        kafkaConfig.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        kafkaConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getCanonicalName());
        kafkaConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getCanonicalName());
        kafkaConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getCanonicalName());
        kafkaConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getCanonicalName());
        kafkaConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "OpenNMS");
    }

    public void init() {
        rpcResponseProducer = new KafkaProducer<String, byte[]>(kafkaConfig);
        KafkaConsumer<String, byte[]> kafkaConsumer = new KafkaConsumer<>(kafkaConfig);
        kafkaConsumerRunner = new KafkaConsumerRunner(kafkaConsumer);
        Executors.newSingleThreadExecutor().execute(kafkaConsumerRunner);
    }

    @Override
    public SinkUpdate register(Function<TwinRequest, TwinResponse> twinProvider) {
        this.twinProvider = twinProvider;
        return this::update;
    }

    public void update(TwinResponse twinResponse) {
        KafkaProducer<String, byte[]> producer = new KafkaProducer<>(kafkaConfig);
        try {
            byte[] value = objectMapper.writeValueAsBytes(twinResponse);
            ProducerRecord<String, byte[]> producerRecord = new ProducerRecord<>(MockTwinSubscriberBroker.sinkTopic, twinResponse.getKey(), value);
            producer.send(producerRecord);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

    }

    private class KafkaConsumerRunner implements Runnable {

        private final KafkaConsumer<String, byte[]> kafkaConsumer;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        public KafkaConsumerRunner(KafkaConsumer<String, byte[]> kafkaConsumer) {
            this.kafkaConsumer = kafkaConsumer;
        }

        @Override
        public void run() {
            try {
                kafkaConsumer.subscribe(Arrays.asList(MockTwinSubscriberBroker.rpcRequestTopic));
                while (!closed.get()) {
                    ConsumerRecords<String, byte[]> records = kafkaConsumer.poll(Duration.ofMillis(Long.MAX_VALUE));
                    for (ConsumerRecord<String, byte[]> record : records) {

                        CompletableFuture.runAsync(() -> {
                            try {
                                MockTwinRequest twinRequest = objectMapper.readValue(record.value(), MockTwinRequest.class);
                                TwinResponse twinResponse = twinProvider.apply(twinRequest);
                                byte[] response = objectMapper.writeValueAsBytes(twinResponse);
                                ProducerRecord<String, byte[]> producerRecord =
                                        new ProducerRecord<>(MockTwinSubscriberBroker.rpcResponseTopic, twinResponse.getKey(), response);
                                rpcResponseProducer.send(producerRecord);
                            } catch (IOException e) {
                                // Ignore
                            }
                        });
                    }
                }
            } catch (Exception e) {
                //Ignore
            }
        }

        public void close() {
            closed.set(true);
        }
    }

    public void destroy() {
        if (kafkaConsumerRunner != null) {
            kafkaConsumerRunner.close();
        }
    }
}
