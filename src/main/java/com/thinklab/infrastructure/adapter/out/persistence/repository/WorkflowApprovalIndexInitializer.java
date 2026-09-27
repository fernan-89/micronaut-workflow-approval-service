package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Creates the tenant-scoped indexes on {@code approval_policies} and {@code approval_requests} at
 * startup: {@code (organisationId, name)} matches {@link ApprovalPolicyMongoRepositoryAdapter}'s own
 * filter order, {@code (organisationId, subjectType, subjectId)} matches
 * {@link ApprovalRequestMongoRepositoryAdapter}'s - the exact lookup a client Service Domain runs to
 * find "the approval request for my ChangeRequest [id]". Both adapters use the driver directly, so
 * the kit's generic {@code MongoIndexInitializer} does not see either (same rationale as
 * {@code AssetIndexInitializer}/{@code SiteIndexInitializer}/{@code WorkOrderIndexInitializer}).
 *
 * <p>Fail-open: a failed {@code createIndex} is logged and the application still starts. Turn it off
 * with {@code thinklab.mongo.create-indexes=false}.
 */
@Singleton
@Requires(property = "thinklab.mongo.create-indexes", notEquals = "false")
public class WorkflowApprovalIndexInitializer implements ApplicationEventListener<StartupEvent> {

    static final String POLICY_TENANT_NAME_INDEX = "organisationId_1_name_1";
    static final String REQUEST_TENANT_SUBJECT_INDEX = "organisationId_1_subjectType_1_subjectId_1";

    private static final Logger log = LoggerFactory.getLogger(WorkflowApprovalIndexInitializer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final MongoClient mongoClient;
    private final String database;
    private final Duration timeout;

    @Inject
    public WorkflowApprovalIndexInitializer(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this(mongoClient, mongoUri, TIMEOUT);
    }

    WorkflowApprovalIndexInitializer(MongoClient mongoClient, String mongoUri, Duration timeout) {
        this.mongoClient = Objects.requireNonNull(mongoClient, "Infrastructure constraint violated: MongoClient cannot be null.");
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : ApprovalPolicyMongoRepositoryAdapter.DEFAULT_DATABASE;
        this.timeout = timeout;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Objects.requireNonNull(event, "Application constraint violated: StartupEvent cannot be null.");
        ensureIndex(ApprovalPolicyMongoRepositoryAdapter.COLLECTION_NAME, POLICY_TENANT_NAME_INDEX,
                new Document("organisationId", 1).append("name", 1));
        ensureIndex(ApprovalRequestMongoRepositoryAdapter.COLLECTION_NAME, REQUEST_TENANT_SUBJECT_INDEX,
                new Document("organisationId", 1).append("subjectType", 1).append("subjectId", 1));
    }

    private void ensureIndex(String collectionName, String indexName, Document keys) {
        try {
            Mono.from(mongoClient.getDatabase(database).getCollection(collectionName)
                    .createIndex(keys, new IndexOptions().name(indexName))).block(timeout);
            log.info("[MONGO_INDEXES] Ensured index [{}] on [{}.{}]", indexName, database, collectionName);
        } catch (MongoTimeoutException e) {
            log.error("[MONGO_INDEXES] MongoDB unreachable; index [{}] was not created. Reason: {}", indexName, e.getMessage());
        } catch (RuntimeException e) {
            log.error("[MONGO_INDEXES] Could not create index [{}] on [{}.{}]: {}", indexName, database, collectionName, e.getMessage());
        }
    }
}
