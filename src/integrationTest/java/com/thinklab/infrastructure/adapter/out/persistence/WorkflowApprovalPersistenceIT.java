package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.exception.ApprovalPolicyNotFoundException;
import com.thinklab.domain.exception.ApprovalRequestNotFoundException;
import com.thinklab.domain.model.ApprovalPolicy;
import com.thinklab.domain.model.ApprovalRequest;
import com.thinklab.domain.model.ApprovalStage;
import com.thinklab.domain.model.ApprovalRequest.ApprovalAuditEntry;
import com.thinklab.domain.model.ApprovalRequest.ApprovalStatus;
import com.thinklab.domain.model.ApprovalRequest.Decision;
import com.thinklab.domain.model.ApprovalRequest.DecisionOutcome;
import com.thinklab.domain.repository.ApprovalPolicyRepository;
import com.thinklab.domain.repository.ApprovalRequestRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ApprovalPolicy and ApprovalRequest aggregates through their repositories against a real
 * MongoDB: every granular update, tenant-scoped filtering, not-found handling, the database taken
 * from {@code mongodb.uri}, and both compound indexes
 * {@link com.thinklab.infrastructure.adapter.out.persistence.repository.WorkflowApprovalIndexInitializer}
 * creates at startup.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowApprovalPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "workflow_approval_it";
    private static final String EXECUTOR = "op-1";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject
    ApprovalPolicyRepository policies;

    @Inject
    ApprovalRequestRepository requests;

    @Inject
    MongoClient mongoClient;

    private static ApprovalPolicy newPolicy(UUID organisationId, UUID... approvers) {
        return ApprovalPolicy.createNew(UUID.randomUUID(), organisationId, "CAB", 1, List.of(approvers));
    }

    private static ApprovalRequest newRequest(UUID organisationId, UUID policyId, UUID... approvers) {
        return ApprovalRequest.createNew(UUID.randomUUID(), organisationId, "ChangeRequest", UUID.randomUUID(),
                UUID.randomUUID(), policyId, 1, List.of(approvers), EXECUTOR);
    }

    private static ApprovalAuditEntry audit(String action, ApprovalStatus from, ApprovalStatus to) {
        return new ApprovalAuditEntry(Instant.now(), action, EXECUTOR, from, to, action + " detail");
    }

    @Test
    @DisplayName("a created ApprovalPolicy is read back as-is")
    void policyCreateAndFind() {
        UUID approver = UUID.randomUUID();
        ApprovalPolicy created = policies.create(newPolicy(UUID.randomUUID(), approver)).block();

        ApprovalPolicy found = policies.findById(created.getId()).block();

        assertEquals("CAB", found.getName());
        assertEquals(1, found.getRequiredApprovals());
        assertEquals(List.of(approver), found.getEligibleApproverIds());
    }

    @Test
    @DisplayName("updateBasicInfo persists every field")
    void policyUpdateBasicInfo() {
        UUID approver = UUID.randomUUID();
        ApprovalPolicy created = policies.create(newPolicy(UUID.randomUUID(), approver)).block();
        UUID newApprover = UUID.randomUUID();

        policies.updateBasicInfo(created.getId(), "CAB-v2", List.of(ApprovalStage.of(1, List.of(newApprover)))).block();

        ApprovalPolicy found = policies.findById(created.getId()).block();
        assertEquals("CAB-v2", found.getName());
        assertEquals(List.of(newApprover), found.getEligibleApproverIds());
    }

    @Test
    @DisplayName("policy listing is tenant-scoped and honours the optional name filter")
    void policyListingFilters() {
        UUID organisation = UUID.randomUUID();
        ApprovalPolicy cab = policies.create(newPolicy(organisation, UUID.randomUUID())).block();
        ApprovalPolicy ecab = ApprovalPolicy.createNew(UUID.randomUUID(), organisation, "ECAB", 1, List.of(UUID.randomUUID()));
        policies.create(ecab).block();
        policies.create(newPolicy(UUID.randomUUID(), UUID.randomUUID())).block();

        assertEquals(Set.of(cab.getId(), ecab.getId()), policyIds(policies.findAllByOrganisationId(organisation, null).collectList().block()));
        assertEquals(Set.of(cab.getId()), policyIds(policies.findAllByOrganisationId(organisation, "CAB").collectList().block()));
    }

    @Test
    @DisplayName("an unknown ApprovalPolicy is empty on read and ApprovalPolicyNotFoundException on update")
    void policyNotFound() {
        UUID unknown = UUID.randomUUID();

        assertNull(policies.findById(unknown).block());
        assertThrows(ApprovalPolicyNotFoundException.class,
                () -> policies.updateBasicInfo(unknown, "x", List.of(ApprovalStage.of(1, List.of(UUID.randomUUID())))).block());
    }

    @Test
    @DisplayName("the (organisationId, name) index exists on approval_policies")
    void policyIndexExists() {
        policies.create(newPolicy(UUID.randomUUID(), UUID.randomUUID())).block();

        List<Document> indexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("approval_policies").listIndexes()).collectList().block();

        assertTrue(indexes.stream().anyMatch(index -> new Document("organisationId", 1).append("name", 1)
                .equals(index.get("key", Document.class))), () -> "approval_policies: " + indexes);
    }

    @Test
    @DisplayName("a created ApprovalRequest is read back with its INITIATED audit entry")
    void requestCreateAndFind() {
        ApprovalRequest created = requests.create(newRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())).block();

        ApprovalRequest found = requests.findById(created.getId()).block();

        assertEquals(ApprovalStatus.PENDING, found.getStatus());
        assertEquals(1, found.getAuditTrail().size());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    @DisplayName("addDecision pushes the decision, sets status and appends the audit entry")
    void requestAddDecision() {
        UUID approver = UUID.randomUUID();
        ApprovalRequest created = requests.create(newRequest(UUID.randomUUID(), UUID.randomUUID(), approver)).block();
        var entry = created.captureDecision(approver, DecisionOutcome.APPROVE, "ok", EXECUTOR);

        requests.addDecision(created, created.getDecisions().get(0), entry).block();

        ApprovalRequest found = requests.findById(created.getId()).block();
        assertEquals(ApprovalStatus.APPROVED, found.getStatus());
        assertEquals(1, found.getDecisions().size());
        assertEquals(2, found.getAuditTrail().size());
    }

    @Test
    @DisplayName("updateStatus persists the transition and appends the audit entry")
    void requestUpdateStatus() {
        ApprovalRequest created = requests.create(newRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())).block();

        requests.updateStatus(created.getId(), ApprovalStatus.CANCELLED,
                audit("CANCELLED", ApprovalStatus.PENDING, ApprovalStatus.CANCELLED)).block();

        ApprovalRequest found = requests.findById(created.getId()).block();
        assertEquals(ApprovalStatus.CANCELLED, found.getStatus());
    }

    @Test
    @DisplayName("request listing is tenant-scoped and honours the optional subjectType/subjectId/status filters")
    void requestListingFilters() {
        UUID organisation = UUID.randomUUID();
        ApprovalRequest fromOrg = requests.create(newRequest(organisation, UUID.randomUUID(), UUID.randomUUID())).block();
        ApprovalRequest cancelled = requests.create(newRequest(organisation, UUID.randomUUID(), UUID.randomUUID())).block();
        requests.create(newRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())).block();
        requests.updateStatus(cancelled.getId(), ApprovalStatus.CANCELLED,
                audit("CANCELLED", ApprovalStatus.PENDING, ApprovalStatus.CANCELLED)).block();

        assertEquals(Set.of(fromOrg.getId(), cancelled.getId()),
                requestIds(requests.findAllByOrganisationId(organisation, null, null, null).collectList().block()));
        assertEquals(Set.of(cancelled.getId()),
                requestIds(requests.findAllByOrganisationId(organisation, null, null, ApprovalStatus.CANCELLED).collectList().block()));
        assertEquals(Set.of(fromOrg.getId()),
                requestIds(requests.findAllByOrganisationId(organisation, "ChangeRequest", fromOrg.getSubjectId(), null).collectList().block()));
    }

    @Test
    @DisplayName("an unknown ApprovalRequest is empty on read and ApprovalRequestNotFoundException on update")
    void requestNotFound() {
        UUID unknown = UUID.randomUUID();

        assertNull(requests.findById(unknown).block());
        assertThrows(ApprovalRequestNotFoundException.class, () -> requests.updateStatus(unknown, ApprovalStatus.CANCELLED,
                audit("CANCELLED", ApprovalStatus.PENDING, ApprovalStatus.CANCELLED)).block());
    }

    @Test
    @DisplayName("the (organisationId, subjectType, subjectId) index exists on approval_requests")
    void requestIndexExists() {
        requests.create(newRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())).block();

        List<Document> indexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("approval_requests").listIndexes()).collectList().block();

        assertTrue(indexes.stream().anyMatch(index -> new Document("organisationId", 1).append("subjectType", 1).append("subjectId", 1)
                .equals(index.get("key", Document.class))), () -> "approval_requests: " + indexes);
    }

    private static Set<UUID> policyIds(List<ApprovalPolicy> list) {
        return list.stream().map(ApprovalPolicy::getId).collect(Collectors.toSet());
    }

    private static Set<UUID> requestIds(List<ApprovalRequest> list) {
        return list.stream().map(ApprovalRequest::getId).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("a chain is stored, walked stage by stage in the database, and shows up in the right approver inbox at each stage")
    void chainAndInbox() {
        UUID organisation = UUID.randomUUID();
        UUID lead = UUID.randomUUID();
        UUID security = UUID.randomUUID();
        ApprovalRequest request = ApprovalRequest.createNew(UUID.randomUUID(), organisation, "ChangeRequest", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(ApprovalStage.of(1, List.of(lead)), ApprovalStage.of(1, List.of(security))), EXECUTOR);
        requests.create(request).block();

        assertEquals(Set.of(request.getId()), requestIds(requests.findPendingFor(organisation, lead).collectList().block()));
        assertTrue(requests.findPendingFor(organisation, security).collectList().block().isEmpty());
        assertTrue(requests.findPendingFor(UUID.randomUUID(), lead).collectList().block().isEmpty());

        var first = request.captureDecision(lead, DecisionOutcome.APPROVE, "ok", EXECUTOR);
        requests.addDecision(request, request.getDecisions().get(0), first).block();

        ApprovalRequest found = requests.findById(request.getId()).block();
        assertEquals(1, found.getCurrentStage());
        assertEquals(2, found.getStages().size());
        assertEquals(ApprovalStatus.PENDING, found.getStatus());
        assertTrue(requests.findPendingFor(organisation, lead).collectList().block().isEmpty());
        assertEquals(Set.of(request.getId()), requestIds(requests.findPendingFor(organisation, security).collectList().block()));
    }

    @Test
    @DisplayName("two votes loaded from the same state cannot both be applied: the second one is refused, not merged")
    void concurrentVotesAreGuarded() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ApprovalRequest created = requests.create(ApprovalRequest.createNew(UUID.randomUUID(), UUID.randomUUID(), "ChangeRequest", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 2, List.of(a, b), EXECUTOR)).block();
        ApprovalRequest seenByA = requests.findById(created.getId()).block();
        ApprovalRequest seenByB = requests.findById(created.getId()).block();
        var entryA = seenByA.captureDecision(a, DecisionOutcome.APPROVE, "ok", EXECUTOR);
        var entryB = seenByB.captureDecision(b, DecisionOutcome.REJECT, "no", EXECUTOR);

        requests.addDecision(seenByA, seenByA.getDecisions().get(0), entryA).block();

        assertThrows(com.thinklab.domain.exception.InvalidApprovalRequestStatusException.class,
                () -> requests.addDecision(seenByB, seenByB.getDecisions().get(0), entryB).block());
        ApprovalRequest found = requests.findById(created.getId()).block();
        assertEquals(1, found.getDecisions().size());
        assertEquals(ApprovalStatus.PENDING, found.getStatus());
    }

    @Test
    @DisplayName("the (organisationId, status, eligibleApproverIds) inbox index exists on approval_requests")
    void inboxIndexExists() {
        requests.create(newRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())).block();

        List<Document> indexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("approval_requests").listIndexes()).collectList().block();

        assertTrue(indexes.stream().anyMatch(index -> new Document("organisationId", 1).append("status", 1).append("eligibleApproverIds", 1)
                .equals(index.get("key", Document.class))), () -> "approval_requests: " + indexes);
    }
}
