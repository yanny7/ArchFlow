package com.yanny.archflow.facts;

import java.util.List;

public sealed interface FlowFact {
    int line();

    record Call(int line, Receiver receiver, String name, List<Receiver> args, String text) implements FlowFact {
        public Call {
            args = args == null ? null : List.copyOf(args);
        }

        public static Call anyArgs(int line, Receiver receiver, String name, String text) {
            return new Call(line, receiver, name, null, text);
        }
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
