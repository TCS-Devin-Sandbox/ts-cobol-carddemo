package com.carddemo.interest.pilot;

/** Typed terminal outcomes of a pilot run. Every one fails the run and commits no pilot outputs (INT-R10). */
public abstract sealed class PilotFailure extends RuntimeException {

    private PilotFailure(String message) {
        super(message);
    }

    /** Diagnostic category; never contains account, card or customer identifiers. */
    public abstract String category();

    /** INT-R10: an input dataset is missing, framed wrongly or holds a field outside its PICTURE. */
    public static final class UnreadableInput extends PilotFailure {
        private final String dataset;

        public UnreadableInput(String dataset, long recordNumber, int offset, String reason) {
            super("dataset=" + dataset + " record=" + recordNumber + " offset=" + offset + " reason=" + reason);
            this.dataset = dataset;
        }

        public String dataset() {
            return dataset;
        }

        @Override
        public String category() {
            return "UNREADABLE_INPUT";
        }
    }

    /** INT-R10 / INT-H08: a required keyed read returned INVALID KEY (lab status 23) and the legacy path abends. */
    public static final class RequiredLookupMissing extends PilotFailure {
        private final String dd;
        private final String operation;
        private final String fileStatus;

        public RequiredLookupMissing(String dd, String operation, String fileStatus) {
            super("dd=" + dd + " op=" + operation + " status=" + fileStatus);
            this.dd = dd;
            this.operation = operation;
            this.fileStatus = fileStatus;
        }

        public String dd() {
            return dd;
        }

        public String operation() {
            return operation;
        }

        public String fileStatus() {
            return fileStatus;
        }

        @Override
        public String category() {
            return "REQUIRED_LOOKUP_MISSING";
        }
    }

    /** A host outcome the evidence does not prove (e.g. INT-H03 non-unique AIX order); held, not guessed. */
    public static final class UnprovenHostOutcome extends PilotFailure {
        private final String ruleId;

        public UnprovenHostOutcome(String ruleId, String detail) {
            super("rule=" + ruleId + " " + detail);
            this.ruleId = ruleId;
        }

        public String ruleId() {
            return ruleId;
        }

        @Override
        public String category() {
            return "HELD_UNPROVEN_HOST_OUTCOME";
        }
    }
}
