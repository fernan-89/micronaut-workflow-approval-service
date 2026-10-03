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

@SuppressWarnings("unchecked")
class WorkflowApprovalIndexInitializerTest {

    private final StartupEvent startup = mock(StartupEvent.class);

    private MongoDatabase databaseIn(MongoClient client, String database) {
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        when(client.getDatabase(database)).thenReturn(mongoDatabase);
        return mongoDatabase;
    }

    private MongoCollection<Document> collectionIn(MongoDatabase mongoDatabase, String collectionName) {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(mongoDatabase.getCollection(collectionName)).thenReturn(collection);
        return collection;
    }

    @Test
    @DisplayName("startup creates both the policy and request indexes in the database named by mongodb.uri")
    void createsIndexes() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase mongoDatabase = databaseIn(client, "tenant_wfa");
        MongoCollection<Document> policyCollection = collectionIn(mongoDatabase, "approval_policies");
        MongoCollection<Document> requestCollection = collectionIn(mongoDatabase, "approval_requests");
        when(policyCollection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));
        when(requestCollection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new WorkflowApprovalIndexInitializer(client, "mongodb://mongo:27017/tenant_wfa").onApplicationEvent(startup);

        ArgumentCaptor<Document> policyKeys = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<IndexOptions> policyOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(policyCollection).createIndex(policyKeys.capture(), policyOptions.capture());
        assertEquals(new Document("organisationId", 1).append("name", 1), policyKeys.getValue());
        assertEquals(WorkflowApprovalIndexInitializer.POLICY_TENANT_NAME_INDEX, policyOptions.getValue().getName());

        ArgumentCaptor<Document> requestKeys = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<IndexOptions> requestOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(requestCollection, org.mockito.Mockito.times(2)).createIndex(requestKeys.capture(), requestOptions.capture());
        assertEquals(new Document("organisationId", 1).append("subjectType", 1).append("subjectId", 1), requestKeys.getAllValues().get(0));
        assertEquals(WorkflowApprovalIndexInitializer.REQUEST_TENANT_SUBJECT_INDEX, requestOptions.getAllValues().get(0).getName());
        // the approver inbox: PENDING requests whose current stage lists the approver
        assertEquals(new Document("organisationId", 1).append("status", 1).append("eligibleApproverIds", 1), requestKeys.getAllValues().get(1));
        assertEquals(WorkflowApprovalIndexInitializer.REQUEST_INBOX_INDEX, requestOptions.getAllValues().get(1).getName());
    }

    @Test
    @DisplayName("a URI without a database uses the service default")
    void defaultDatabase() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase mongoDatabase = databaseIn(client, ApprovalPolicyMongoRepositoryAdapter.DEFAULT_DATABASE);
        MongoCollection<Document> policyCollection = collectionIn(mongoDatabase, "approval_policies");
        MongoCollection<Document> requestCollection = collectionIn(mongoDatabase, "approval_requests");
        when(policyCollection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));
        when(requestCollection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new WorkflowApprovalIndexInitializer(client, "mongodb://mongo:27017").onApplicationEvent(startup);

        verify(policyCollection).createIndex(any(), any(IndexOptions.class));
        verify(requestCollection, org.mockito.Mockito.times(2)).createIndex(any(), any(IndexOptions.class));
    }

    @Test
    @DisplayName("fail-open: an unreachable server or a rejected index is logged, never propagated, for either collection")
    void failOpen() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase mongoDatabase = databaseIn(client, "wfa_db");
        MongoCollection<Document> policyCollection = collectionIn(mongoDatabase, "approval_policies");
        MongoCollection<Document> requestCollection = collectionIn(mongoDatabase, "approval_requests");
        when(policyCollection.createIndex(any(), any(IndexOptions.class)))
                .thenReturn(Mono.error(new MongoTimeoutException("no server")));
        when(requestCollection.createIndex(any(), any(IndexOptions.class)))
                .thenReturn(Mono.error(new IllegalStateException("rejected")));

        assertDoesNotThrow(() -> new WorkflowApprovalIndexInitializer(client, "mongodb://mongo:27017/wfa_db", Duration.ofSeconds(1))
                .onApplicationEvent(startup));
    }

    @Test
    @DisplayName("collaborators, mongodb.uri and the startup event are null-checked")
    void guards() {
        MongoClient client = mock(MongoClient.class);
        assertThrows(NullPointerException.class, () -> new WorkflowApprovalIndexInitializer(null, "mongodb://mongo:27017/a"));
        assertThrows(NullPointerException.class, () -> new WorkflowApprovalIndexInitializer(client, null));
        assertThrows(NullPointerException.class, () -> new WorkflowApprovalIndexInitializer(client, "mongodb://mongo:27017/a").onApplicationEvent(null));
    }
}
