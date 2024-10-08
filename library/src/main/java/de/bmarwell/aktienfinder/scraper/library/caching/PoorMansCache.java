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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PoorMansCache<T> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PoorMansCache.class);

    private final int maxSize;
    private final Supplier<T> supplier;

    List<Instance<T>> availableInstances = new ArrayList<>();
    List<Instance<T>> usedInstances = new ArrayList<>();

    public PoorMansCache(int maxSize, Supplier<T> supplier) {
        this.maxSize = maxSize;
        this.supplier = supplier;
    }

    public Instance<T> get() {
        if (!availableInstances.isEmpty() && usedInstances.size() < maxSize) {
            Instance<T> usedInstance = availableInstances.removeLast();
            usedInstances.add(usedInstance);

            return usedInstance;
        }

        if (availableInstances.size() + usedInstances.size() >= maxSize) {
            return null;
        }

        if (usedInstances.size() >= maxSize || availableInstances.size() >= maxSize) {
            return null;
        }

        InstanceImpl<T> instance = createInstance();
        this.usedInstances.add(instance);

        cleanUpOld();

        return instance;
    }

    InstanceImpl<T> createInstance() {
        T object = supplier.get();
        Instant now = Instant.now();

        return new InstanceImpl<>(object, now, this.availableInstances, this.usedInstances, this::closeInstance);
    }

    void closeInstance(Instance<T> instance) {
        this.usedInstances.remove(instance);
        this.availableInstances.add(instance);
    }

    protected void cleanUpOld() {
        this.availableInstances.removeIf(i -> {
            InstanceImpl<T> instance = (InstanceImpl<T>) i;
            Instant fiveMinutesAgo = Instant.now().minusSeconds(300L);

            return instance.createdOn.isAfter(fiveMinutesAgo);
        });
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

    record InstanceImpl<T>(
            T instance,
            Instant createdOn,
            List<Instance<T>> availableInstances,
            List<Instance<T>> usedInstances,
            Consumer<Instance<T>> closer)
            implements Instance<T>, AutoCloseable {

        @Override
        public void close() throws Exception {
            this.closer().accept(this);
        }
    }
}
