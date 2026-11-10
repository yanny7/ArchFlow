package com.yanny.archflow.facts;

import java.util.List;

public sealed interface FlowFact {
    int line();

    record Call(int line, Receiver receiver, String name, int args, String text) implements FlowFact {
        public static final int ANY_ARGS = -1;
    }

    record Branch(int line, String subject, List<Arm> arms) implements FlowFact {
        public Branch {
            arms = List.copyOf(arms);
        }
    }

    record Arm(String condition, List<FlowFact> facts) {
        public Arm {
            facts = List.copyOf(facts);
        }
    }

    record Loop(int line, String header, List<FlowFact> facts) implements FlowFact {
        public Loop {
            facts = List.copyOf(facts);
        }
    }

    record Return(int line) implements FlowFact {
    }

    record Throw(int line, String text) implements FlowFact {
    }
}
