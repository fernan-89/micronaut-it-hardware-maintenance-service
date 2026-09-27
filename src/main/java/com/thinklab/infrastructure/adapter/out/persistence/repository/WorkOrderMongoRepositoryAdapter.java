package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.model.WorkOrder.Comment;
import com.thinklab.domain.model.WorkOrder.Part;
import com.thinklab.domain.model.WorkOrder.Priority;
import com.thinklab.domain.model.WorkOrder.RmaDetails;
import com.thinklab.domain.model.WorkOrder.WorkOrderAuditEntry;
import com.thinklab.domain.model.WorkOrder.WorkOrderStatus;
import com.thinklab.domain.repository.WorkOrderRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.WorkOrderDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.WorkOrderDocument.AuditEntryDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.WorkOrderDocument.CommentDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.WorkOrderDocument.PartDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.WorkOrderDocument.RmaDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.WorkOrderDocument.WorkOrderPersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter for the WorkOrder aggregate, same pattern as
 * {@code AssetMongoRepositoryAdapter}: every transition is a single atomic {@code $set}/{@code $push}
 * that also appends the forensic audit entry, so state and ledger can never diverge.
 */
@Singleton
public class WorkOrderMongoRepositoryAdapter implements WorkOrderRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderMongoRepositoryAdapter.class);
    static final String DEFAULT_DATABASE = "thinklab_hardware_maintenance_db";
    static final String COLLECTION_NAME = "work_orders";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_AUDIT_TRAIL = "auditTrail";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public WorkOrderMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : DEFAULT_DATABASE;
    }

    private MongoCollection<WorkOrderDocument> getCollection() {
        return mongoClient.getDatabase(database)
                .getCollection(COLLECTION_NAME, WorkOrderDocument.class)
                .withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<WorkOrder> create(WorkOrder workOrder) {
        log.debug("[PERSISTENCE] Monolithic create for WorkOrder Aggregate: {}", workOrder.getId());

        WorkOrderDocument document = WorkOrderPersistenceMapper.toDocument(workOrder);
        return Mono.from(getCollection().insertOne(document)).map(result -> workOrder);
    }

    @Override
    public Mono<WorkOrder> findById(UUID id) {
        return Mono.from(getCollection().find(Filters.eq(FIELD_ID, id)).first())
                .map(WorkOrderPersistenceMapper::toDomain);
    }

    @Override
    public Flux<WorkOrder> findAllByOrganisationId(UUID organisationId, WorkOrderStatus status, UUID requesterId) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("organisationId", organisationId));
        if (status != null) {
            filters.add(Filters.eq("status", status.name()));
        }
        if (requesterId != null) {
            filters.add(Filters.eq("requesterId", requesterId));
        }

        return Flux.from(getCollection().find(Filters.and(filters)))
                .map(WorkOrderPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> updateBasicInfo(UUID id, String title, String symptom, String location, UUID loanerAssetId, WorkOrderAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("title", title),
                Updates.set("symptom", symptom),
                Updates.set("location", location),
                Updates.set("loanerAssetId", loanerAssetId),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> updateStatus(UUID id, WorkOrderStatus status, WorkOrderAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> updateTriage(UUID id, Priority priority, Instant slaResponseDueAt, Instant slaResolutionDueAt,
                                    WorkOrderStatus status, WorkOrderAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("priority", priority.name()),
                Updates.set("slaResponseDueAt", slaResponseDueAt),
                Updates.set("slaResolutionDueAt", slaResolutionDueAt),
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> updateRma(UUID id, RmaDetails rma, WorkOrderStatus status, WorkOrderAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("rma", RmaDocument.fromDomain(rma)),
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> updateCompleteRepair(UUID id, String diagnosis, int laborMinutes, WorkOrderStatus status, WorkOrderAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("diagnosis", diagnosis),
                Updates.set("laborMinutes", laborMinutes),
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> updatePass(UUID id, String resolutionCode, WorkOrderStatus status, WorkOrderAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("resolutionCode", resolutionCode),
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> addPart(UUID id, Part part, WorkOrderAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.push("parts", PartDocument.fromDomain(part)),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> addComment(UUID id, Comment comment, WorkOrderAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.push("comments", CommentDocument.fromDomain(comment)),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    private Mono<Void> executeUpdate(UUID id, Bson update) {
        return Mono.from(getCollection().updateOne(Filters.eq(FIELD_ID, id), update))
                .flatMap(result -> {
                    if (result.getMatchedCount() == 0) {
                        return Mono.error(new WorkOrderNotFoundException(id));
                    }
                    return Mono.empty();
                });
    }
}
