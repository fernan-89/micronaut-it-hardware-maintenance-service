package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.model.WorkOrder.Comment;
import com.thinklab.domain.model.WorkOrder.Part;
import com.thinklab.domain.model.WorkOrder.Priority;
import com.thinklab.domain.model.WorkOrder.RmaDetails;
import com.thinklab.domain.model.WorkOrder.WorkOrderAuditEntry;
import com.thinklab.domain.model.WorkOrder.WorkOrderStatus;
import com.thinklab.domain.model.WorkOrder.WorkOrderType;
import com.thinklab.infrastructure.adapter.out.persistence.entity.WorkOrderDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.WorkOrderDocument.WorkOrderPersistenceMapper;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class WorkOrderMongoRepositoryAdapterTest {

    @Mock private MongoClient mongoClient;
    @Mock private MongoDatabase mongoDatabase;
    @Mock private MongoCollection<WorkOrderDocument> mongoCollection;

    private WorkOrderMongoRepositoryAdapter adapter;
    private UUID workOrderId;
    private UUID organisationId;
    private WorkOrder workOrder;
    private WorkOrderAuditEntry entry;

    @BeforeEach
    void setUp() {
        lenient().when(mongoClient.getDatabase("thinklab_hardware_maintenance_db")).thenReturn(mongoDatabase);
        lenient().when(mongoDatabase.getCollection("work_orders", WorkOrderDocument.class)).thenReturn(mongoCollection);
        lenient().when(mongoCollection.withCodecRegistry(any())).thenReturn(mongoCollection);
        adapter = new WorkOrderMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017/thinklab_hardware_maintenance_db");

        workOrderId = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        workOrder = WorkOrder.createNew(workOrderId, organisationId, UUID.randomUUID(), UUID.randomUUID(),
                "t", "s", WorkOrderType.CORRECTIVE, null, null, "op-1");
        entry = new WorkOrderAuditEntry(Instant.now(), "ACTION", "op-1", WorkOrderStatus.REQUESTED, WorkOrderStatus.TRIAGED, "detail");
    }

    @Test
    @DisplayName("the database falls back to the default name when the URI has none")
    void databaseFallback() {
        lenient().when(mongoClient.getDatabase("thinklab_hardware_maintenance_db")).thenReturn(mongoDatabase);
        WorkOrderMongoRepositoryAdapter fallbackAdapter = new WorkOrderMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017");
        when(mongoCollection.insertOne(any(WorkOrderDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(fallbackAdapter.create(workOrder)).expectNext(workOrder).verifyComplete();
    }

    @Test
    @DisplayName("create inserts the whole aggregate as one document")
    void create() {
        when(mongoCollection.insertOne(any(WorkOrderDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(adapter.create(workOrder)).expectNext(workOrder).verifyComplete();
    }

    @Test
    @DisplayName("findById maps the found document back to the domain aggregate")
    void findById() {
        FindPublisher<WorkOrderDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<WorkOrderDocument> subscriber = invocation.getArgument(0);
            Flux.just(WorkOrderPersistenceMapper.toDocument(workOrder)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());

        StepVerifier.create(adapter.findById(workOrderId))
                .assertNext(found -> org.junit.jupiter.api.Assertions.assertEquals(workOrderId, found.getId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("findAllByOrganisationId applies the optional status and requesterId filters")
    void findAllByOrganisationId() {
        FindPublisher<WorkOrderDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<WorkOrderDocument> subscriber = invocation.getArgument(0);
            Flux.just(WorkOrderPersistenceMapper.toDocument(workOrder)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, WorkOrderStatus.REQUESTED, UUID.randomUUID())).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, null, null)).expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("updateBasicInfo issues a granular set of every field")
    void updateBasicInfo() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateBasicInfo(workOrderId, "t2", "s2", "loc", UUID.randomUUID(), entry)).verifyComplete();
    }

    @Test
    @DisplayName("updateStatus issues a granular status set")
    void updateStatus() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateStatus(workOrderId, WorkOrderStatus.TRIAGED, entry)).verifyComplete();
    }

    @Test
    @DisplayName("updateTriage sets priority and both SLA due dates")
    void updateTriage() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateTriage(workOrderId, Priority.P1, Instant.now(), Instant.now(), WorkOrderStatus.TRIAGED, entry)).verifyComplete();
    }

    @Test
    @DisplayName("updateRma sets the RMA snapshot, including the null case (vendor-returned clearing nothing to clear)")
    void updateRma() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        RmaDetails rma = new RmaDetails("Acme", "CASE-1", Instant.now(), null);
        StepVerifier.create(adapter.updateRma(workOrderId, rma, WorkOrderStatus.ESCALATED_TO_VENDOR, entry)).verifyComplete();
    }

    @Test
    @DisplayName("updateCompleteRepair sets diagnosis and accumulated labor minutes")
    void updateCompleteRepair() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateCompleteRepair(workOrderId, "diag", 30, WorkOrderStatus.QUALITY_CHECK, entry)).verifyComplete();
    }

    @Test
    @DisplayName("updatePass sets the resolution code")
    void updatePass() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updatePass(workOrderId, "RC-1", WorkOrderStatus.RESOLVED, entry)).verifyComplete();
    }

    @Test
    @DisplayName("addPart pushes onto the parts array")
    void addPart() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        Part part = new Part(UUID.randomUUID(), "Flex cable", 1);
        StepVerifier.create(adapter.addPart(workOrderId, part, entry)).verifyComplete();
    }

    @Test
    @DisplayName("addComment pushes onto the comments array")
    void addComment() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        Comment comment = new Comment(UUID.randomUUID(), "op-1", "note", false, Instant.now());
        StepVerifier.create(adapter.addComment(workOrderId, comment, entry)).verifyComplete();
    }

    @Test
    @DisplayName("a zero-matched update translates into WorkOrderNotFoundException")
    void zeroMatchedUpdate() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));

        StepVerifier.create(adapter.updateStatus(workOrderId, WorkOrderStatus.TRIAGED, entry))
                .expectError(WorkOrderNotFoundException.class)
                .verify();
    }
}
