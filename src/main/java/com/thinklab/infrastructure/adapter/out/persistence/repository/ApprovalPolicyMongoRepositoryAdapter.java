package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.ApprovalPolicyNotFoundException;
import com.thinklab.domain.model.ApprovalPolicy;
import com.thinklab.domain.model.ApprovalStage;
import com.thinklab.domain.repository.ApprovalPolicyRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalPolicyDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.StageDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalPolicyDocument.ApprovalPolicyPersistenceMapper;
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

/** MongoDB Reactive Repository Adapter for the ApprovalPolicy aggregate, raw reactive-streams driver. */
@Singleton
public class ApprovalPolicyMongoRepositoryAdapter implements ApprovalPolicyRepository {

    static final String DEFAULT_DATABASE = "thinklab_workflow_approval_db";
    static final String COLLECTION_NAME = "approval_policies";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public ApprovalPolicyMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : DEFAULT_DATABASE;
    }

    private MongoCollection<ApprovalPolicyDocument> getCollection() {
        return mongoClient.getDatabase(database)
                .getCollection(COLLECTION_NAME, ApprovalPolicyDocument.class)
                .withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<ApprovalPolicy> create(ApprovalPolicy policy) {
        ApprovalPolicyDocument document = ApprovalPolicyPersistenceMapper.toDocument(policy);
        return Mono.from(getCollection().insertOne(document)).map(result -> policy);
    }

    @Override
    public Mono<ApprovalPolicy> findById(UUID id) {
        return Mono.from(getCollection().find(Filters.eq(FIELD_ID, id)).first())
                .map(ApprovalPolicyPersistenceMapper::toDomain);
    }

    @Override
    public Flux<ApprovalPolicy> findAllByOrganisationId(UUID organisationId, String name) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("organisationId", organisationId));
        if (name != null) {
            filters.add(Filters.eq("name", name));
        }

        return Flux.from(getCollection().find(Filters.and(filters)))
                .map(ApprovalPolicyPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> updateBasicInfo(UUID id, String name, List<ApprovalStage> stages) {
        Bson update = Updates.combine(
                Updates.set("name", name),
                Updates.set("stages", StageDocument.fromDomain(stages)),
                // The first stage stays in the original fields, so a reader that predates chains still sees a quorum.
                Updates.set("requiredApprovals", stages.get(0).requiredApprovals()),
                Updates.set("eligibleApproverIds", stages.get(0).eligibleApproverIds()),
                Updates.set(FIELD_UPDATED_AT, Instant.now())
        );
        return Mono.from(getCollection().updateOne(Filters.eq(FIELD_ID, id), update))
                .flatMap(result -> {
                    if (result.getMatchedCount() == 0) {
                        return Mono.error(new ApprovalPolicyNotFoundException(id));
                    }
                    return Mono.empty();
                });
    }
}
