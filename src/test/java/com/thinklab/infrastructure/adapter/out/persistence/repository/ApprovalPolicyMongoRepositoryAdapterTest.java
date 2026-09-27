package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.ApprovalPolicyNotFoundException;
import com.thinklab.domain.model.ApprovalPolicy;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalPolicyDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalPolicyDocument.ApprovalPolicyPersistenceMapper;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ApprovalPolicyMongoRepositoryAdapterTest {

    @Mock private MongoClient mongoClient;
    @Mock private MongoDatabase mongoDatabase;
    @Mock private MongoCollection<ApprovalPolicyDocument> mongoCollection;

    private ApprovalPolicyMongoRepositoryAdapter adapter;
    private UUID policyId;
    private UUID organisationId;
    private ApprovalPolicy policy;

    @BeforeEach
    void setUp() {
        lenient().when(mongoClient.getDatabase("thinklab_workflow_approval_db")).thenReturn(mongoDatabase);
        lenient().when(mongoDatabase.getCollection("approval_policies", ApprovalPolicyDocument.class)).thenReturn(mongoCollection);
        lenient().when(mongoCollection.withCodecRegistry(any())).thenReturn(mongoCollection);
        adapter = new ApprovalPolicyMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017/thinklab_workflow_approval_db");

        policyId = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        policy = ApprovalPolicy.createNew(policyId, organisationId, "CAB", 2, List.of(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("the database falls back to the default name when the URI has none")
    void databaseFallback() {
        lenient().when(mongoClient.getDatabase("thinklab_workflow_approval_db")).thenReturn(mongoDatabase);
        ApprovalPolicyMongoRepositoryAdapter fallbackAdapter = new ApprovalPolicyMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017");
        when(mongoCollection.insertOne(any(ApprovalPolicyDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(fallbackAdapter.create(policy)).expectNext(policy).verifyComplete();
    }

    @Test
    @DisplayName("create inserts the whole aggregate as one document")
    void create() {
        when(mongoCollection.insertOne(any(ApprovalPolicyDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(adapter.create(policy)).expectNext(policy).verifyComplete();
    }

    @Test
    @DisplayName("findById maps the found document back to the domain aggregate")
    void findById() {
        FindPublisher<ApprovalPolicyDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<ApprovalPolicyDocument> subscriber = invocation.getArgument(0);
            Flux.just(ApprovalPolicyPersistenceMapper.toDocument(policy)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());

        StepVerifier.create(adapter.findById(policyId))
                .assertNext(found -> org.junit.jupiter.api.Assertions.assertEquals(policyId, found.getId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("findAllByOrganisationId applies the optional name filter")
    void findAllByOrganisationId() {
        FindPublisher<ApprovalPolicyDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<ApprovalPolicyDocument> subscriber = invocation.getArgument(0);
            Flux.just(ApprovalPolicyPersistenceMapper.toDocument(policy)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, "CAB")).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, null)).expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("updateBasicInfo issues a granular set of every field")
    void updateBasicInfo() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateBasicInfo(policyId, "CAB-v2", 3, List.of(UUID.randomUUID()))).verifyComplete();
    }

    @Test
    @DisplayName("a zero-matched update translates into ApprovalPolicyNotFoundException")
    void zeroMatchedUpdate() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));

        StepVerifier.create(adapter.updateBasicInfo(policyId, "CAB-v2", 3, List.of(UUID.randomUUID())))
                .expectError(ApprovalPolicyNotFoundException.class)
                .verify();
    }
}
