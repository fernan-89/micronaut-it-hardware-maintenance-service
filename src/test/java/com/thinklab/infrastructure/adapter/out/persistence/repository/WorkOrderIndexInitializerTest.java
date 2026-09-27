package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.micronaut.context.event.StartupEvent;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderIndexInitializerTest {

    private final StartupEvent startup = mock(StartupEvent.class);

    @SuppressWarnings("unchecked")
    private MongoCollection<Document> collectionIn(MongoClient client, String database) {
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase(database)).thenReturn(mongoDatabase);
        when(mongoDatabase.getCollection("work_orders")).thenReturn(collection);
        return collection;
    }

    @Test
    @DisplayName("startup creates the tenant/status/requester index in the database named by mongodb.uri")
    void createsIndex() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, "tenant_work_orders");
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new WorkOrderIndexInitializer(client, "mongodb://mongo:27017/tenant_work_orders").onApplicationEvent(startup);

        ArgumentCaptor<Document> keys = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<IndexOptions> options = ArgumentCaptor.forClass(IndexOptions.class);
        verify(collection).createIndex(keys.capture(), options.capture());

        assertEquals(new Document("organisationId", 1).append("status", 1).append("requesterId", 1), keys.getValue());
        assertEquals(WorkOrderIndexInitializer.TENANT_STATUS_REQUESTER_INDEX, options.getValue().getName());
    }

    @Test
    @DisplayName("a URI without a database uses the service default")
    void defaultDatabase() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, WorkOrderMongoRepositoryAdapter.DEFAULT_DATABASE);
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new WorkOrderIndexInitializer(client, "mongodb://mongo:27017").onApplicationEvent(startup);

        verify(collection).createIndex(any(), any(IndexOptions.class));
    }

    @Test
    @DisplayName("fail-open: an unreachable server or a rejected index is logged, never propagated")
    void failOpen() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, "wo_db");
        when(collection.createIndex(any(), any(IndexOptions.class)))
                .thenReturn(Mono.error(new MongoTimeoutException("no server")));
        WorkOrderIndexInitializer initializer = new WorkOrderIndexInitializer(client, "mongodb://mongo:27017/wo_db", Duration.ofSeconds(1));

        assertDoesNotThrow(() -> initializer.onApplicationEvent(startup));

        MongoCollection<Document> collection2 = collectionIn(client, "wo_db");
        when(collection2.createIndex(any(), any(IndexOptions.class)))
                .thenReturn(Mono.error(new IllegalStateException("rejected")));
        assertDoesNotThrow(() -> new WorkOrderIndexInitializer(client, "mongodb://mongo:27017/wo_db", Duration.ofSeconds(1)).onApplicationEvent(startup));
    }

    @Test
    @DisplayName("collaborators, mongodb.uri and the startup event are null-checked")
    void guards() {
        MongoClient client = mock(MongoClient.class);
        assertThrows(NullPointerException.class, () -> new WorkOrderIndexInitializer(null, "mongodb://mongo:27017/a"));
        assertThrows(NullPointerException.class, () -> new WorkOrderIndexInitializer(client, null));
        assertThrows(NullPointerException.class, () -> new WorkOrderIndexInitializer(client, "mongodb://mongo:27017/a").onApplicationEvent(null));
    }
}
