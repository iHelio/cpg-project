/*
 * Copyright 2026 ihelio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ihelio.cpg.infrastructure.event;

import com.ihelio.cpg.domain.event.ProcessEvent;
import com.ihelio.cpg.domain.event.ProcessEventPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory implementation of the ProcessEventPublisher.
 *
 * <p>Provides a simple event bus for development and testing.
 * Subscribers can register to receive events synchronously or asynchronously.
 */
@Component
public class InMemoryEventPublisher implements ProcessEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventPublisher.class);

    private final List<Consumer<ProcessEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final List<ProcessEvent> publishedEvents = new CopyOnWriteArrayList<>();
    private final ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public void publish(ProcessEvent event) {
        log.debug("Publishing event: {} ({})", event.eventType(), event.eventId());
        publishedEvents.add(event);

        for (Consumer<ProcessEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                log.warn("Event subscriber error for event {}: {}",
                    event.eventType(), e.getMessage());
            }
        }
    }

    @Override
    public void publishAsync(ProcessEvent event) {
        asyncExecutor.submit(() -> publish(event));
    }

    @Override
    public void publishAll(List<ProcessEvent> events) {
        for (ProcessEvent event : events) {
            publish(event);
        }
    }

    /**
     * Subscribes to receive all published events.
     *
     * @param subscriber the event consumer
     */
    public void subscribe(Consumer<ProcessEvent> subscriber) {
        subscribers.add(subscriber);
    }

    /**
     * Subscribes to receive events of a specific type.
     *
     * @param eventType the event type to subscribe to
     * @param subscriber the event consumer
     */
    public void subscribe(String eventType, Consumer<ProcessEvent> subscriber) {
        subscribers.add(event -> {
            if (event.eventType().equals(eventType)) {
                subscriber.accept(event);
            }
        });
    }

    /**
     * Unsubscribes a subscriber.
     *
     * @param subscriber the subscriber to remove
     */
    public void unsubscribe(Consumer<ProcessEvent> subscriber) {
        subscribers.remove(subscriber);
    }

    /**
     * Returns all published events (for testing).
     *
     * @return list of published events
     */
    public List<ProcessEvent> getPublishedEvents() {
        return new ArrayList<>(publishedEvents);
    }

    /**
     * Returns events of a specific type (for testing).
     *
     * @param eventType the event type
     * @return list of matching events
     */
    public List<ProcessEvent> getEventsOfType(String eventType) {
        return publishedEvents.stream()
            .filter(e -> e.eventType().equals(eventType))
            .toList();
    }

    /**
     * Clears all published events (for testing).
     */
    public void clearEvents() {
        publishedEvents.clear();
    }

    /**
     * Clears all subscribers.
     */
    public void clearSubscribers() {
        subscribers.clear();
    }
}
