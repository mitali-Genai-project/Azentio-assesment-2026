package com.azentio.clearlist.service;

import static com.azentio.clearlist.entity.HitStatus.CONFIRMED_MATCH;
import static com.azentio.clearlist.entity.HitStatus.ESCALATED;
import static com.azentio.clearlist.entity.HitStatus.FALSE_POSITIVE;
import static com.azentio.clearlist.entity.HitStatus.OPEN;

import com.azentio.clearlist.entity.HitStatus;
import com.azentio.clearlist.exception.InvalidTransitionException;
import com.azentio.clearlist.exception.TransitionNotPermittedException;
import com.azentio.clearlist.security.Role;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Who may move a hit between which statuses.
 *
 * <pre>
 *   ANALYST:    OPEN      -> FALSE_POSITIVE | ESCALATED
 *   SUPERVISOR: ESCALATED -> CONFIRMED_MATCH | FALSE_POSITIVE
 * </pre>
 *
 * Deciding between 403 and 409 when a request is not allowed:
 * <ul>
 *   <li>403 - the transition exists but belongs to another role (analyst ESCALATED -> CONFIRMED_MATCH), or the
 *       caller's role can never set the target status (analyst -> CONFIRMED_MATCH from anywhere).</li>
 *   <li>409 - the caller's role could set the target status, just not from the hit's current status
 *       (supervisor OPEN -> CONFIRMED_MATCH), or no role can ever make it (e.g. back to OPEN).</li>
 * </ul>
 */
@Component
public class HitTransitionPolicy {

    private static final Map<Role, Set<Transition>> RULES = Map.of(
            Role.ANALYST, Set.of(new Transition(OPEN, FALSE_POSITIVE), new Transition(OPEN, ESCALATED)),
            Role.SUPERVISOR, Set.of(new Transition(ESCALATED, CONFIRMED_MATCH), new Transition(ESCALATED, FALSE_POSITIVE)));

    /** @return the role that authorises the transition (recorded in the audit trail) */
    public Role authorise(Set<Role> actorRoles, HitStatus from, HitStatus to) {
        Transition requested = new Transition(from, to);
        for (Role role : actorRoles) {
            if (RULES.get(role).contains(requested)) {
                return role;
            }
        }
        String description = from + " -> " + to;
        boolean someRoleMayDoThis = RULES.values().stream().anyMatch(rules -> rules.contains(requested));
        if (someRoleMayDoThis) {
            throw new TransitionNotPermittedException("Your role may not perform " + description);
        }
        boolean anyRoleSetsTarget = RULES.values().stream().flatMap(Set::stream).anyMatch(t -> t.to() == to);
        boolean actorSetsTarget = actorRoles.stream().flatMap(r -> RULES.get(r).stream()).anyMatch(t -> t.to() == to);
        if (!anyRoleSetsTarget || actorSetsTarget) {
            throw new InvalidTransitionException("A hit in status " + from + " cannot be moved to " + to);
        }
        throw new TransitionNotPermittedException("Your role may not set status " + to);
    }

    private record Transition(HitStatus from, HitStatus to) {
    }
}
