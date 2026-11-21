package com.carddemo.batch.common;

import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

public final class StepCheckpoint {
    private static final String COMMITTED = "carddemo.committed";

    private StepCheckpoint() { }

    // Only wrap single-transaction tasklets that share the repository's DataSource.
    public static Tasklet once(Tasklet work) {
        return (contribution, context) -> {
            var checkpoint = contribution.getStepExecution().getExecutionContext();
            if (checkpoint.containsKey(COMMITTED)) return RepeatStatus.FINISHED;
            var result = work.execute(contribution, context);
            if (result == RepeatStatus.FINISHED) checkpoint.putInt(COMMITTED, 1);
            return result;
        };
    }
}
