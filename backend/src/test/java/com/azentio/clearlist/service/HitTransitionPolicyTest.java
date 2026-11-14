package com.azentio.clearlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azentio.clearlist.entity.HitStatus;
import com.azentio.clearlist.exception.InvalidTransitionException;
import com.azentio.clearlist.exception.TransitionNotPermittedException;
import com.azentio.clearlist.security.Role;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Full role x from x to matrix for the disposition rules. */
class HitTransitionPolicyTest {

    private final HitTransitionPolicy policy = new HitTransitionPolicy();

    @ParameterizedTest(name = "{0}: {1} -> {2} = {3}")
    @CsvSource({
            // allowed
            "ANALYST,    OPEN,           ESCALATED,       OK",
            "ANALYST,    OPEN,           FALSE_POSITIVE,  OK",
            "SUPERVISOR, ESCALATED,      CONFIRMED_MATCH, OK",
            "SUPERVISOR, ESCALATED,      FALSE_POSITIVE,  OK",
            // transition exists, but for the other role -> 403
            "ANALYST,    ESCALATED,      CONFIRMED_MATCH, 403",
            "ANALYST,    ESCALATED,      FALSE_POSITIVE,  403",
            "SUPERVISOR, OPEN,           ESCALATED,       403",
            "SUPERVISOR, OPEN,           FALSE_POSITIVE,  403",
            // role can never set the target status -> 403
            "ANALYST,    OPEN,           CONFIRMED_MATCH, 403",
            // role could set the target, but not from this status -> 409
            "SUPERVISOR, OPEN,           CONFIRMED_MATCH, 409",
            "ANALYST,    FALSE_POSITIVE, ESCALATED,       409",
            "SUPERVISOR, CONFIRMED_MATCH,FALSE_POSITIVE,  409",
            // nobody can ever move a hit back to OPEN -> 409
            "SUPERVISOR, ESCALATED,      OPEN,            409",
            "ANALYST,    OPEN,           OPEN,            409",
    })
    void appliesRules(Role role, HitStatus from, HitStatus to, String expected) {
        switch (expected) {
            case "OK" -> assertThat(policy.authorise(Set.of(role), from, to)).isEqualTo(role);
            case "403" -> assertThatThrownBy(() -> policy.authorise(Set.of(role), from, to))
                    .isInstanceOf(TransitionNotPermittedException.class);
            case "409" -> assertThatThrownBy(() -> policy.authorise(Set.of(role), from, to))
                    .isInstanceOf(InvalidTransitionException.class);
            default -> throw new IllegalArgumentException(expected);
        }
    }
}
