package com.carddemo.interest.handoff;

import java.util.UUID;

/** Typed handoff results (standards S03/S12): only {@link Released} carries a success condition. */
public sealed interface HandoffOutcome {
    UUID runId();

    /** Shadow rehearsal: packet recorded PRIVATE, no host call, no success condition. */
    record ShadowRehearsed(UUID runId, String payloadSha256) implements HandoffOutcome {}

    /** Verified matching receipt; run RELEASED and the success condition emitted exactly once. */
    record Released(UUID runId, String generation, boolean alreadyReleased) implements HandoffOutcome {}

    /** Fail closed: publication HELD (or refused), downstream stays held. */
    record Held(UUID runId, HoldReason reason) implements HandoffOutcome {}
}
