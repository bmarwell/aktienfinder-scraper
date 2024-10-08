/*
 * Copyright (C) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.bmarwell.aktienfinder.scraper.library.caching;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PoorMansCache<T> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PoorMansCache.class);

    private static AtomicInteger creationCounter = new AtomicInteger();

    private final int maxSize;
    private final Supplier<T> supplier;

    private final List<Instance<T>> availableInstances = Collections.synchronizedList(new ArrayList<>());
    private final List<Instance<T>> usedInstances = Collections.synchronizedList(new ArrayList<>());
    private boolean interrupted = false;

    public PoorMansCache(int maxSize, Supplier<T> supplier) {
        this.maxSize = maxSize;
        this.supplier = supplier;
    }

    public synchronized Instance<T> get() {
        if (this.interrupted) {
            return null;
        }

        cleanUpOld();

        if (!availableInstances.isEmpty()) {
            // instance available
            Instance<T> usedInstance = availableInstances.removeLast();
            usedInstances.add(usedInstance);

            LOG.debug("returning available instance: {}", usedInstance);

            return usedInstance;
        }

        if (totalInstances() < maxSize) {
            LOG.debug("total instances: [{}] < maxSize [{}]", totalInstances(), maxSize);
            // no available instance, but we can still create new ones
            InstanceImpl<T> instance = createInstance();
            this.usedInstances.add(instance);

            LOG.debug("returning new instance: {}", instance);

            return instance;
        }

        return null;
    }

    public Instance<T> getBlocking(Duration timeout, Duration waitTime) throws TimeoutException, InterruptedException {
        Instant start = Instant.now();

        Instance<T> instance = get();
        while (instance == null && Instant.now().isBefore(start.plusSeconds(timeout.getSeconds()))) {
            try {
                TimeUnit.MILLISECONDS.sleep(waitTime.toMillis());
            } catch (InterruptedException interruptedException) {
                this.interrupted = true;
                Thread.currentThread().interrupt();
                throw interruptedException;
            }

            instance = get();
        }

        if (instance == null) {
            throw new TimeoutException("instance not available in time");
        }

        return instance;
    }

    public Instance<T> getBlocking() throws TimeoutException, InterruptedException {
        return getBlocking(Duration.ofSeconds(10L), Duration.ofMillis(500L));
    }

    InstanceImpl<T> createInstance() {
        int instanceNumber = creationCounter.incrementAndGet();

        T object = supplier.get();
        Instant now = Instant.now();

        return new InstanceImpl<>(object, instanceNumber, now, this::closeInstance);
    }

    void closeInstance(Instance<T> instance) {
        this.usedInstances.remove(instance);
        this.availableInstances.add(instance);
    }

    protected void cleanUpOld() {
        if (this.interrupted) {
            return;
        }

        this.availableInstances.removeIf(i -> {
            InstanceImpl<T> instance = (InstanceImpl<T>) i;
            Instant fiveMinutesAgo = Instant.now().minusSeconds(300L);

            return instance.createdOn.isAfter(fiveMinutesAgo);
        });
    }

    int totalInstances() {
        LOG.trace("used/active: [{}], available: [{}]", usedInstances.size(), availableInstances.size());
        return usedInstances.size() + availableInstances.size();
    }

    @Override
    public void close() throws IOException {
        for (Instance<T> availableInstance : this.availableInstances) {
            T instance = availableInstance.instance();
            if (instance instanceof AutoCloseable closeableInstance) {
                try {
                    closeableInstance.close();
                } catch (Exception closeEx) {
                    LOG.error("unable to close instance: [{}].", instance, closeEx);
                }
            }
        }
    }

    public interface Instance<T> extends AutoCloseable {

        T instance();
    }

    record InstanceImpl<T>(T instance, int instanceNumber, Instant createdOn, Consumer<Instance<T>> closer)
            implements Instance<T>, AutoCloseable {

        @Override
        public void close() throws Exception {
            LOG.debug("closing (returning) instance: [{}].", this);
            this.closer().accept(this);
        }
    }
}
