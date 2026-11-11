package com.yanny.archflow.facts;

public record Import(String name, boolean isStatic, boolean asterisk, int line) {
}
