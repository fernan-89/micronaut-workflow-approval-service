package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.ApprovalRequestNotFoundException;
import com.thinklab.domain.exception.InvalidApprovalRequestStatusException;
import com.thinklab.domain.model.ApprovalRequest;
import com.thinklab.domain.model.ApprovalRequest.ApprovalAuditEntry;
import com.thinklab.domain.model.ApprovalRequest.ApprovalStatus;
import com.thinklab.domain.model.ApprovalRequest.Decision;
import com.thinklab.domain.repository.ApprovalRequestRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalRequestDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalRequestDocument.AuditEntryDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalRequestDocument.ApprovalRequestPersistenceMapper;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalRequestDocument.DecisionDocument;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** MongoDB Reactive Repository Adapter for the ApprovalRequest aggregate, raw reactive-streams driver. */
@Singleton
public class ApprovalRequestMongoRepositoryAdapter implements ApprovalRequestRepository {

    static final String COLLECTION_NAME = "approval_requests";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_AUDIT_TRAIL = "auditTrail";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public ApprovalRequestMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : ApprovalPolicyMongoRepositoryAdapter.DEFAULT_DATABASE;
    }

    private MongoCollection<ApprovalRequestDocument> getCollection() {
        return mongoClient.getDatabase(database)
                .getCollection(COLLECTION_NAME, ApprovalRequestDocument.class)
                .withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<ApprovalRequest> create(ApprovalRequest request) {
        ApprovalRequestDocument document = ApprovalRequestPersistenceMapper.toDocument(request);
        return Mono.from(getCollection().insertOne(document)).map(result -> request);
    }

    @Override
    public Mono<ApprovalRequest> findById(UUID id) {
        return Mono.from(getCollection().find(Filters.eq(FIELD_ID, id)).first())
                .map(ApprovalRequestPersistenceMapper::toDomain);
    }

    @Override
    public Flux<ApprovalRequest> findAllByOrganisationId(UUID organisationId, String subjectType, UUID subjectId, ApprovalStatus status) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("organisationId", organisationId));
        if (subjectType != null) {
            filters.add(Filters.eq("subjectType", subjectType));
        }
        if (subjectId != null) {
            filters.add(Filters.eq("subjectId", subjectId));
        }
        if (status != null) {
            filters.add(Filters.eq("status", status.name()));
        }

        return Flux.from(getCollection().find(Filters.and(filters)))
                .map(ApprovalRequestPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> addDecision(ApprovalRequest updated, Decision decision, ApprovalAuditEntry auditEntry) {
        // Guarded write: it only applies while the request is still PENDING and has exactly the decisions it had when it was loaded, so
        // two votes that raced for the same state cannot both be applied (the second would otherwise overwrite the stage and status).
        Bson guard = Filters.and(Filters.eq(FIELD_ID, updated.getId()), Filters.eq("status", ApprovalStatus.PENDING.name()),
                Filters.size("decisions", updated.getDecisions().size() - 1));
        Bson update = Updates.combine(
                Updates.push("decisions", DecisionDocument.fromDomain(decision)),
                Updates.set("status", updated.getStatus().name()),
                Updates.set("currentStage", updated.getCurrentStage()),
                // The stage the request now waits on: what the approver inbox searches.
                Updates.set("requiredApprovals", updated.getRequiredApprovals()),
                Updates.set("eligibleApproverIds", updated.getEligibleApproverIds()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return Mono.from(getCollection().updateOne(guard, update))
                .flatMap(result -> result.getMatchedCount() == 0
                        ? Mono.error(new InvalidApprovalRequestStatusException("ApprovalRequest was changed by someone else while this decision was being recorded; read it again and retry."))
                        : Mono.<Void>empty());
    }

    @Override
    public Flux<ApprovalRequest> findPendingFor(UUID organisationId, UUID approverId) {
        Bson filter = Filters.and(Filters.eq("organisationId", organisationId), Filters.eq("status", ApprovalStatus.PENDING.name()),
                Filters.eq("eligibleApproverIds", approverId), Filters.ne("decisions.approverId", approverId));
        return Flux.from(getCollection().find(filter)).map(ApprovalRequestPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> updateStatus(UUID id, ApprovalStatus status, ApprovalAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    private Mono<Void> executeUpdate(UUID id, Bson update) {
        return Mono.from(getCollection().updateOne(Filters.eq(FIELD_ID, id), update))
                .flatMap(result -> {
                    if (result.getMatchedCount() == 0) {
                        return Mono.error(new ApprovalRequestNotFoundException(id));
                    }
                    return Mono.empty();
                });
    }
}
