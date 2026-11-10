package com.yanny.archflow.model;

import java.util.List;

public sealed interface Step {
    int line();

    record Call(int line, String target, String text) implements Step {
        public boolean resolved() {
            return target != null;
        }
    }

    record Branch(int line, String subject, List<Arm> arms) implements Step {
        public Branch {
            arms = List.copyOf(arms);
        }
    }

    record Arm(String condition, List<Step> steps) {
        public Arm {
            steps = List.copyOf(steps);
        }
    }

    record Loop(int line, String header, List<Step> steps) implements Step {
        public Loop {
            steps = List.copyOf(steps);
        }
    }

    record Return(int line) implements Step {
    }

    record Throw(int line, String text) implements Step {
    }
}
