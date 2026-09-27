package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.ApprovalRequestNotFoundException;
import com.thinklab.domain.model.ApprovalRequest;
import com.thinklab.domain.model.ApprovalRequest.ApprovalAuditEntry;
import com.thinklab.domain.model.ApprovalRequest.ApprovalStatus;
import com.thinklab.domain.model.ApprovalRequest.Decision;
import com.thinklab.domain.model.ApprovalRequest.DecisionOutcome;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalRequestDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalRequestDocument.ApprovalRequestPersistenceMapper;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ApprovalRequestMongoRepositoryAdapterTest {

    @Mock private MongoClient mongoClient;
    @Mock private MongoDatabase mongoDatabase;
    @Mock private MongoCollection<ApprovalRequestDocument> mongoCollection;

    private ApprovalRequestMongoRepositoryAdapter adapter;
    private UUID requestId;
    private UUID organisationId;
    private UUID approverA;
    private ApprovalRequest request;
    private ApprovalAuditEntry entry;

    @BeforeEach
    void setUp() {
        lenient().when(mongoClient.getDatabase("thinklab_workflow_approval_db")).thenReturn(mongoDatabase);
        lenient().when(mongoDatabase.getCollection("approval_requests", ApprovalRequestDocument.class)).thenReturn(mongoCollection);
        lenient().when(mongoCollection.withCodecRegistry(any())).thenReturn(mongoCollection);
        adapter = new ApprovalRequestMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017/thinklab_workflow_approval_db");

        requestId = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        approverA = UUID.randomUUID();
        request = ApprovalRequest.createNew(requestId, organisationId, "ChangeRequest", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, List.of(approverA), "op-1");
        entry = new ApprovalAuditEntry(Instant.now(), "DECISION_CAPTURED", "op-1", ApprovalStatus.PENDING, ApprovalStatus.APPROVED, "detail");
    }

    @Test
    @DisplayName("the database falls back to the default name when the URI has none")
    void databaseFallback() {
        lenient().when(mongoClient.getDatabase("thinklab_workflow_approval_db")).thenReturn(mongoDatabase);
        ApprovalRequestMongoRepositoryAdapter fallbackAdapter = new ApprovalRequestMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017");
        when(mongoCollection.insertOne(any(ApprovalRequestDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(fallbackAdapter.create(request)).expectNext(request).verifyComplete();
    }

    @Test
    @DisplayName("create inserts the whole aggregate as one document")
    void create() {
        when(mongoCollection.insertOne(any(ApprovalRequestDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(adapter.create(request)).expectNext(request).verifyComplete();
    }

    @Test
    @DisplayName("findById maps the found document back to the domain aggregate")
    void findById() {
        FindPublisher<ApprovalRequestDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<ApprovalRequestDocument> subscriber = invocation.getArgument(0);
            Flux.just(ApprovalRequestPersistenceMapper.toDocument(request)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());

        StepVerifier.create(adapter.findById(requestId))
                .assertNext(found -> org.junit.jupiter.api.Assertions.assertEquals(requestId, found.getId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("findAllByOrganisationId applies the optional subjectType/subjectId/status filters")
    void findAllByOrganisationId() {
        FindPublisher<ApprovalRequestDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<ApprovalRequestDocument> subscriber = invocation.getArgument(0);
            Flux.just(ApprovalRequestPersistenceMapper.toDocument(request)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, "ChangeRequest", UUID.randomUUID(), ApprovalStatus.PENDING))
                .expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, null, null, null)).expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("addDecision pushes the decision, sets status and pushes the audit entry")
    void addDecision() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        Decision decision = new Decision(approverA, DecisionOutcome.APPROVE, "ok", Instant.now());
        StepVerifier.create(adapter.addDecision(requestId, decision, ApprovalStatus.APPROVED, entry)).verifyComplete();
    }

    @Test
    @DisplayName("updateStatus sets the status and pushes the audit entry")
    void updateStatus() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateStatus(requestId, ApprovalStatus.CANCELLED, entry)).verifyComplete();
    }

    @Test
    @DisplayName("a zero-matched update translates into ApprovalRequestNotFoundException")
    void zeroMatchedUpdate() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));

        StepVerifier.create(adapter.updateStatus(requestId, ApprovalStatus.CANCELLED, entry))
                .expectError(ApprovalRequestNotFoundException.class)
                .verify();
    }
}
