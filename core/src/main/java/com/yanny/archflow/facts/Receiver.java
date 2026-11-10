package com.yanny.archflow.facts;

public sealed interface Receiver {
    record Implicit() implements Receiver {
    }

    record This() implements Receiver {
    }

    record Super() implements Receiver {
    }

    record Typed(TypeRef type) implements Receiver {
    }

    record Name(String name) implements Receiver {
    }

    record Field(Receiver receiver, String name) implements Receiver {
    }

    record Result(Receiver receiver, String method, int args) implements Receiver {
    }

    record Element(Receiver receiver) implements Receiver {
    }

    record Unknown() implements Receiver {
    }
}
