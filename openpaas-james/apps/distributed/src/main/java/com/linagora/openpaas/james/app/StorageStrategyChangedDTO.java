package com.linagora.openpaas.james.app;

import java.util.Objects;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.server.blob.deduplication.StorageStrategy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

class StorageStrategyChangedDTO implements EventDTO {

    static StorageStrategyChangedDTO from(StorageStrategyChanged storageStrategyChanged, String type) {
        Preconditions.checkNotNull(storageStrategyChanged);

        StorageStrategy storageStrategy = storageStrategyChanged.getStorageStrategy();
        return new StorageStrategyChangedDTO(
                storageStrategyChanged.eventId().serialize(),
                storageStrategyChanged.getAggregateId().asAggregateKey(),
                type,
                storageStrategy.name());
    }

    static StorageStrategyChangedDTO from(StorageStrategyChanged storageStrategyChanged) {
        return from(storageStrategyChanged, StorageStrategyModule.TYPE_NAME);
    }

    private final int eventId;
    private final String aggregateKey;
    private final String type;
    private final String storageStrategy;

    @JsonCreator
    StorageStrategyChangedDTO(
            @JsonProperty("eventId") int eventId,
            @JsonProperty("aggregateKey") String aggregateKey,
            @JsonProperty("type") String type,
            @JsonProperty("storageStrategy") String storageStrategy) {
        this.eventId = eventId;
        this.aggregateKey = aggregateKey;
        this.type = type;
        this.storageStrategy = storageStrategy;
    }

    @JsonIgnore
    public StorageStrategyChanged toEvent() {
        return new StorageStrategyChanged(
            EventId.fromSerialized(eventId),
            () -> aggregateKey,
            StorageStrategy.valueOf(storageStrategy));
    }

    public int getEventId() {
        return eventId;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public String getType() {
        return type;
    }

    public String getStorageStrategy() {
        return storageStrategy;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof StorageStrategyChangedDTO) {
            StorageStrategyChangedDTO that = (StorageStrategyChangedDTO) o;

            return Objects.equals(this.eventId, that.eventId)
                && Objects.equals(this.aggregateKey, that.aggregateKey)
                && Objects.equals(this.type, that.type)
                && Objects.equals(this.storageStrategy, that.storageStrategy);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(eventId, aggregateKey, type, storageStrategy);
    }
}
