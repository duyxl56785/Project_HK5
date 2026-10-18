package com.projecthk5.sellerservice.seller.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projecthk5.sellerservice.seller.domain.SellerApplication;
import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.domain.SellerRuleException;
import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerLifecycleServiceTest {
    @Mock private SellerAccessPolicy accessPolicy;
    private SellerLifecycleService service;
    private final SellerIdentity owner = new SellerIdentity("owner-reference");
    private final SellerIdentity moderator = new SellerIdentity("moderator-reference");

    @BeforeEach
    void setUp() {
        service = new SellerLifecycleService(accessPolicy);
    }

    @Test
    void registrationStartsPendingReviewAfterAuthentication() {
        SellerApplication application = service.register(owner);
        assertEquals(owner, application.owner());
        assertEquals(SellerStatus.PENDING_REVIEW, application.status());
        assertNull(application.reviewReason());
        verify(accessPolicy).requireAuthenticated(owner);
        verifyNoMoreInteractions(accessPolicy);
    }

    @Test
    void completesRejectResubmitApproveSuspendReinstateLifecycle() {
        SellerApplication pending = service.register(owner);
        SellerApplication rejected = service.reject(pending, moderator, " Incomplete application ");
        assertEquals("Incomplete application", rejected.reviewReason());
        SellerApplication resubmitted = service.resubmit(rejected, owner);
        assertNull(resubmitted.reviewReason());
        SellerApplication approved = service.approve(resubmitted, moderator);
        SellerApplication suspended = service.suspend(approved, moderator, " Policy violation ");
        assertEquals("Policy violation", suspended.reviewReason());
        SellerApplication reinstated = service.reinstate(suspended, moderator);
        assertEquals(SellerStatus.APPROVED, reinstated.status());
        assertEquals(owner, reinstated.owner());
        assertNull(reinstated.reviewReason());
        assertEquals(SellerStatus.PENDING_REVIEW, pending.status());
        assertEquals(SellerStatus.REJECTED, rejected.status());
        assertEquals(SellerStatus.SUSPENDED, suspended.status());
        verify(accessPolicy, times(4)).requireSellerModerationPermission(moderator);
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void permitsExactlyTheExpectedStateTransitions(String operation, SellerStatus current,
            SellerStatus allowed, SellerStatus target) {
        SellerApplication before = application(current);
        SellerIdentity actor = actorFor(operation);
        if (current == allowed) {
            SellerApplication after = invoke(operation, before, actor);
            assertEquals(target, after.status());
            assertEquals(owner, after.owner());
        } else {
            assertCode(SellerRuleException.Code.INVALID_STATE, () -> invoke(operation, before, actor));
        }
        assertEquals(current, before.status());
    }

    static Stream<Arguments> transitions() {
        Object[][] operations = {
            {"RESUBMIT", SellerStatus.REJECTED, SellerStatus.PENDING_REVIEW},
            {"APPROVE", SellerStatus.PENDING_REVIEW, SellerStatus.APPROVED},
            {"REJECT", SellerStatus.PENDING_REVIEW, SellerStatus.REJECTED},
            {"SUSPEND", SellerStatus.APPROVED, SellerStatus.SUSPENDED},
            {"REINSTATE", SellerStatus.SUSPENDED, SellerStatus.APPROVED}
        };
        return Stream.of(operations).flatMap(operation -> Stream.of(SellerStatus.values())
                .map(current -> Arguments.of(operation[0], current, operation[1], operation[2])));
    }

    @Test
    void deniedRegistrationCannotCreateApplication() {
        doThrow(denied()).when(accessPolicy).requireAuthenticated(owner);
        assertCode(SellerRuleException.Code.ACCESS_DENIED, () -> service.register(owner));
        verifyNoMoreInteractions(accessPolicy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESUBMIT", "APPROVE", "REJECT", "SUSPEND", "REINSTATE"})
    void authenticationFailureStopsEveryOperation(String operation) {
        SellerIdentity actor = actorFor(operation);
        SellerApplication before = application(allowedState(operation));
        doThrow(denied()).when(accessPolicy).requireAuthenticated(actor);
        assertCode(SellerRuleException.Code.ACCESS_DENIED, () -> invoke(operation, before, actor));
        verify(accessPolicy, never()).requireSellerModerationPermission(any());
        assertEquals(allowedState(operation), before.status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVE", "REJECT", "SUSPEND", "REINSTATE"})
    void moderationPermissionIsRequiredForEveryAdministrativeOperation(String operation) {
        SellerApplication before = application(allowedState(operation));
        doThrow(denied()).when(accessPolicy).requireSellerModerationPermission(moderator);
        assertCode(SellerRuleException.Code.ACCESS_DENIED, () -> invoke(operation, before, moderator));
        verify(accessPolicy).requireAuthenticated(moderator);
        assertEquals(allowedState(operation), before.status());
    }

    @Test
    void onlyTheOwnerCanResubmitEvenIfOtherActorIsAModerator() {
        SellerApplication rejected = application(SellerStatus.REJECTED);
        assertCode(SellerRuleException.Code.NOT_OWNER, () -> service.resubmit(rejected, moderator));
        verify(accessPolicy).requireAuthenticated(moderator);
        verify(accessPolicy, never()).requireSellerModerationPermission(any());
        assertEquals(SellerStatus.REJECTED, rejected.status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVE", "REJECT", "SUSPEND", "REINSTATE"})
    void ownerCannotModerateOwnApplicationEvenWithModerationPermission(String operation) {
        SellerApplication before = application(allowedState(operation));
        assertCode(SellerRuleException.Code.SELF_MODERATION, () -> invoke(operation, before, owner));
        verify(accessPolicy).requireSellerModerationPermission(owner);
        assertEquals(allowedState(operation), before.status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESUBMIT", "APPROVE", "REJECT", "SUSPEND", "REINSTATE"})
    void rejectsMissingApplicationBeforeCallingAccessPolicy(String operation) {
        assertCode(SellerRuleException.Code.INVALID_INPUT, () -> invoke(operation, null, actorFor(operation)));
        verifyNoInteractions(accessPolicy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESUBMIT", "APPROVE", "REJECT", "SUSPEND", "REINSTATE"})
    void rejectsMissingActorBeforeCallingAccessPolicy(String operation) {
        assertCode(SellerRuleException.Code.INVALID_INPUT,
                () -> invoke(operation, application(allowedState(operation)), null));
        verifyNoInteractions(accessPolicy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REJECT", "SUSPEND"})
    void invalidReviewReasonCannotChangeApplication(String operation) {
        SellerApplication before = application(allowedState(operation));
        assertCode(SellerRuleException.Code.INVALID_INPUT, () -> {
            if (operation.equals("REJECT")) {
                service.reject(before, moderator, " \t\n");
            } else {
                service.suspend(before, moderator, null);
            }
        });
        assertEquals(allowedState(operation), before.status());
    }

    @Test
    void requiresAccessPolicyAndApplicant() {
        assertThrows(NullPointerException.class, () -> new SellerLifecycleService(null));
        assertCode(SellerRuleException.Code.INVALID_INPUT, () -> service.register(null));
        verifyNoInteractions(accessPolicy);
    }

    private SellerApplication application(SellerStatus status) {
        return new SellerApplication(owner, status,
                status == SellerStatus.REJECTED || status == SellerStatus.SUSPENDED ? "Earlier reason" : null);
    }

    private SellerIdentity actorFor(String operation) {
        return operation.equals("RESUBMIT") ? owner : moderator;
    }

    private SellerStatus allowedState(String operation) {
        return switch (operation) {
            case "RESUBMIT" -> SellerStatus.REJECTED;
            case "SUSPEND" -> SellerStatus.APPROVED;
            case "REINSTATE" -> SellerStatus.SUSPENDED;
            default -> SellerStatus.PENDING_REVIEW;
        };
    }

    private SellerApplication invoke(String operation, SellerApplication application, SellerIdentity actor) {
        return switch (operation) {
            case "RESUBMIT" -> service.resubmit(application, actor);
            case "APPROVE" -> service.approve(application, actor);
            case "REJECT" -> service.reject(application, actor, "Review rejected");
            case "SUSPEND" -> service.suspend(application, actor, "Review suspended");
            case "REINSTATE" -> service.reinstate(application, actor);
            default -> throw new IllegalArgumentException("Unexpected test operation");
        };
    }

    private SellerRuleException denied() {
        return new SellerRuleException(SellerRuleException.Code.ACCESS_DENIED, "Access denied by policy");
    }

    private void assertCode(SellerRuleException.Code code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(SellerRuleException.class, action).getCode());
    }
}
