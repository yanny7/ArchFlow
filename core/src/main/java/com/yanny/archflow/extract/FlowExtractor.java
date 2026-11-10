package com.yanny.archflow.extract;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.yanny.archflow.facts.FlowFact;
import com.yanny.archflow.facts.MethodFacts;
import com.yanny.archflow.facts.Receiver;
import com.yanny.archflow.facts.TypeRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class FlowExtractor {
    private final Map<String, Receiver> locals = new HashMap<>();
    private boolean inLambda;

    private FlowExtractor(List<Parameter> params) {
        params.forEach(this::declare);
    }

    static List<FlowFact> body(List<Parameter> params, BlockStmt body) {
        List<FlowFact> out = new ArrayList<>();
        new FlowExtractor(params).statement(body, out);
        return out;
    }

    static List<FlowFact> initializers(List<Node> parts) {
        FlowExtractor extractor = new FlowExtractor(List.of());
        List<FlowFact> out = new ArrayList<>();
        for (Node part : parts) {
            switch (part) {
                case Expression expression -> extractor.expression(expression, out);
                case Statement statement -> extractor.statement(statement, out);
                default -> throw new IllegalArgumentException(part.getClass().getName());
            }
        }
        return out;
    }

    private void statement(Statement statement, List<FlowFact> out) {
        int line = line(statement);
        switch (statement) {
            case BlockStmt block -> block.getStatements().forEach(s -> statement(s, out));
            case ExpressionStmt expression -> expression(expression.getExpression(), out);
            case IfStmt ifStmt -> ifChain(ifStmt, out);
            case SwitchStmt switchStmt -> switchBranch(line, switchStmt.getSelector(), switchStmt.getEntries(), out);
            case ForStmt loop -> {
                loop.getInitialization().forEach(e -> expression(e, out));
                List<FlowFact> body = new ArrayList<>();
                loop.getCompare().ifPresent(e -> expression(e, body));
                statement(loop.getBody(), body);
                loop.getUpdate().forEach(e -> expression(e, body));
                String header = "for (" + join(loop.getInitialization()) + "; "
                        + loop.getCompare().map(FlowExtractor::text).orElse("") + "; " + join(loop.getUpdate()) + ")";
                out.add(new FlowFact.Loop(line, header, body));
            }
            case ForEachStmt loop -> {
                expression(loop.getIterable(), out);
                VariableDeclarator variable = loop.getVariable().getVariables().get(0);
                declare(variable.getNameAsString(), variable.getType(), new Receiver.Element(receiver(loop.getIterable())));
                out.add(new FlowFact.Loop(line, "for (" + text(loop.getVariable()) + " : " + text(loop.getIterable()) + ")",
                        statements(loop.getBody())));
            }
            case WhileStmt loop -> {
                List<FlowFact> body = new ArrayList<>();
                expression(loop.getCondition(), body);
                statement(loop.getBody(), body);
                out.add(new FlowFact.Loop(line, "while (" + text(loop.getCondition()) + ")", body));
            }
            case DoStmt loop -> {
                List<FlowFact> body = statements(loop.getBody());
                expression(loop.getCondition(), body);
                out.add(new FlowFact.Loop(line, "do while (" + text(loop.getCondition()) + ")", body));
            }
            case TryStmt tryStmt -> {
                tryStmt.getResources().forEach(e -> expression(e, out));
                statement(tryStmt.getTryBlock(), out);
                for (CatchClause clause : tryStmt.getCatchClauses()) {
                    declare(clause.getParameter());
                    String condition = "catch (" + text(clause.getParameter()) + ")";
                    out.add(new FlowFact.Branch(line(clause), null, List.of(new FlowFact.Arm(condition, statements(clause.getBody())))));
                }
                tryStmt.getFinallyBlock().ifPresent(b -> statement(b, out));
            }
            case ReturnStmt returnStmt -> {
                returnStmt.getExpression().ifPresent(e -> expression(e, out));
                if (!inLambda) {
                    out.add(new FlowFact.Return(line));
                }
            }
            case ThrowStmt throwStmt -> {
                if (throwStmt.getExpression() instanceof ObjectCreationExpr creation) {
                    creation.getArguments().forEach(e -> expression(e, out));
                } else {
                    expression(throwStmt.getExpression(), out);
                }
                out.add(new FlowFact.Throw(line, compact(throwStmt.getExpression())));
            }
            case ExplicitConstructorInvocationStmt invocation -> {
                invocation.getArguments().forEach(e -> expression(e, out));
                Receiver receiver = invocation.isThis() ? new Receiver.This() : new Receiver.Super();
                String text = invocation.isThis() ? "this()" : "super()";
                out.add(new FlowFact.Call(line, receiver, MethodFacts.CONSTRUCTOR, receivers(invocation.getArguments()), text));
            }
            case LabeledStmt labeled -> statement(labeled.getStatement(), out);
            case SynchronizedStmt synchronizedStmt -> {
                expression(synchronizedStmt.getExpression(), out);
                statement(synchronizedStmt.getBody(), out);
            }
            case LocalClassDeclarationStmt ignored -> {
            }
            case LocalRecordDeclarationStmt ignored -> {
            }
            default -> children(statement, out);
        }
    }

    private List<FlowFact> statements(Statement statement) {
        List<FlowFact> out = new ArrayList<>();
        statement(statement, out);
        return out;
    }

    private void ifChain(IfStmt ifStmt, List<FlowFact> out) {
        expression(ifStmt.getCondition(), out);
        List<FlowFact.Arm> arms = new ArrayList<>();
        IfStmt current = ifStmt;
        while (true) {
            arms.add(new FlowFact.Arm(text(current.getCondition()), statements(current.getThenStmt())));
            Statement otherwise = current.getElseStmt().orElse(null);
            if (otherwise instanceof IfStmt next && expressions(next.getCondition()).isEmpty()) {
                current = next;
            } else {
                if (otherwise != null) {
                    arms.add(new FlowFact.Arm(null, statements(otherwise)));
                }
                break;
            }
        }
        out.add(new FlowFact.Branch(line(ifStmt), null, arms));
    }

    private void switchBranch(int line, Expression selector, List<SwitchEntry> entries, List<FlowFact> out) {
        expression(selector, out);
        List<FlowFact.Arm> arms = new ArrayList<>();
        for (SwitchEntry entry : entries) {
            List<FlowFact> facts = new ArrayList<>();
            entry.getStatements().forEach(s -> statement(s, facts));
            arms.add(new FlowFact.Arm(caseLabel(entry), facts));
        }
        out.add(new FlowFact.Branch(line, text(selector), arms));
    }

    private static String caseLabel(SwitchEntry entry) {
        if (entry.isDefault() || entry.getLabels().isEmpty()) {
            return null;
        }
        return join(entry.getLabels()) + entry.getGuard().map(g -> " when " + text(g)).orElse("");
    }

    private List<FlowFact> expressions(Expression expression) {
        List<FlowFact> out = new ArrayList<>();
        expression(expression, out);
        return out;
    }

    private void expression(Expression expression, List<FlowFact> out) {
        int line = line(expression);
        switch (expression) {
            case MethodCallExpr call -> {
                call.getScope().ifPresent(e -> expression(e, out));
                call.getArguments().forEach(e -> expression(e, out));
                Receiver receiver = call.getScope().map(this::receiver).orElse(new Receiver.Implicit());
                out.add(new FlowFact.Call(line, receiver, call.getNameAsString(), receivers(call.getArguments()), compact(call)));
            }
            case ObjectCreationExpr creation -> {
                creation.getScope().ifPresent(e -> expression(e, out));
                creation.getArguments().forEach(e -> expression(e, out));
                Receiver receiver = new Receiver.Typed(JavaExtractor.ref(creation.getType()));
                out.add(new FlowFact.Call(line, receiver, MethodFacts.CONSTRUCTOR, receivers(creation.getArguments()), compact(creation)));
            }
            case MethodReferenceExpr reference -> {
                Receiver receiver;
                if (reference.getScope() instanceof TypeExpr type) {
                    receiver = type.getType() instanceof ClassOrInterfaceType named && named.getTypeArguments().isEmpty()
                            ? nameChain(named.getNameWithScope())
                            : new Receiver.Typed(JavaExtractor.ref(type.getType()));
                } else {
                    expression(reference.getScope(), out);
                    receiver = receiver(reference.getScope());
                }
                String name = reference.getIdentifier().equals("new") ? MethodFacts.CONSTRUCTOR : reference.getIdentifier();
                out.add(FlowFact.Call.anyArgs(line, receiver, name, compact(reference)));
            }
            case LambdaExpr lambda -> {
                lambda.getParameters().forEach(this::declare);
                boolean outer = inLambda;
                inLambda = true;
                statement(lambda.getBody(), out);
                inLambda = outer;
            }
            case ConditionalExpr conditional -> {
                expression(conditional.getCondition(), out);
                List<FlowFact> then = expressions(conditional.getThenExpr());
                List<FlowFact> otherwise = expressions(conditional.getElseExpr());
                if (!then.isEmpty() || !otherwise.isEmpty()) {
                    out.add(new FlowFact.Branch(line, null, List.of(
                            new FlowFact.Arm(text(conditional.getCondition()), then), new FlowFact.Arm(null, otherwise))));
                }
            }
            case SwitchExpr switchExpr -> switchBranch(line, switchExpr.getSelector(), switchExpr.getEntries(), out);
            case VariableDeclarationExpr declaration -> {
                for (VariableDeclarator variable : declaration.getVariables()) {
                    variable.getInitializer().ifPresent(e -> expression(e, out));
                    Receiver inferred = variable.getInitializer().map(this::receiver).orElse(new Receiver.Unknown());
                    declare(variable.getNameAsString(), variable.getType(), inferred);
                }
            }
            case TypePatternExpr pattern -> declare(pattern.getNameAsString(), pattern.getType(), new Receiver.Unknown());
            default -> children(expression, out);
        }
    }

    private void children(Node node, List<FlowFact> out) {
        for (Node child : node.getChildNodes()) {
            switch (child) {
                case Expression expression -> expression(expression, out);
                case Statement statement -> statement(statement, out);
                default -> children(child, out);
            }
        }
    }

    private Receiver receiver(Expression expression) {
        return switch (expression) {
            case NameExpr name -> locals.getOrDefault(name.getNameAsString(), new Receiver.Name(name.getNameAsString()));
            case ThisExpr thisExpr -> thisExpr.getTypeName()
                    .<Receiver>map(t -> typed(t.asString()))
                    .orElse(new Receiver.This());
            case SuperExpr ignored -> new Receiver.Super();
            case FieldAccessExpr field -> new Receiver.Field(receiver(field.getScope()), field.getNameAsString());
            case MethodCallExpr call -> new Receiver.Result(call.getScope().map(this::receiver).orElse(new Receiver.Implicit()),
                    call.getNameAsString(), receivers(call.getArguments()));
            case ObjectCreationExpr creation -> new Receiver.Typed(JavaExtractor.ref(creation.getType()));
            case CastExpr cast -> new Receiver.Typed(JavaExtractor.ref(cast.getType()));
            case EnclosedExpr enclosed -> receiver(enclosed.getInner());
            case ArrayAccessExpr access -> new Receiver.Element(receiver(access.getName()));
            case StringLiteralExpr ignored -> typed("String");
            case TextBlockLiteralExpr ignored -> typed("String");
            case AssignExpr assign -> receiver(assign.getTarget());
            default -> new Receiver.Unknown();
        };
    }

    private List<Receiver> receivers(List<Expression> expressions) {
        return expressions.stream().map(this::receiver).toList();
    }

    private Receiver nameChain(String dotted) {
        String[] parts = dotted.split("\\.");
        Receiver receiver = locals.getOrDefault(parts[0], new Receiver.Name(parts[0]));
        for (int i = 1; i < parts.length; i++) {
            receiver = new Receiver.Field(receiver, parts[i]);
        }
        return receiver;
    }

    private static Receiver typed(String name) {
        return new Receiver.Typed(new TypeRef(name, List.of(), 0));
    }

    private void declare(Parameter param) {
        declare(param.getNameAsString(), param.getType(), new Receiver.Unknown());
    }

    private void declare(String name, Type type, Receiver inferred) {
        locals.put(name, type.isVarType() || type.isUnknownType() ? inferred : new Receiver.Typed(JavaExtractor.ref(type)));
    }

    private static String compact(Expression expression) {
        return switch (expression) {
            case NameExpr name -> name.getNameAsString();
            case ThisExpr thisExpr -> thisExpr.getTypeName().map(t -> t.asString() + ".").orElse("") + "this";
            case SuperExpr ignored -> "super";
            case FieldAccessExpr field -> compact(field.getScope()) + "." + field.getNameAsString();
            case MethodCallExpr call -> call.getScope().map(s -> compact(s) + ".").orElse("") + call.getNameAsString() + "()";
            case ObjectCreationExpr creation -> "new " + creation.getType().getNameWithScope() + "()";
            case MethodReferenceExpr reference -> compact(reference.getScope()) + "::" + reference.getIdentifier();
            case TypeExpr type -> type.getType().asString();
            case EnclosedExpr enclosed -> compact(enclosed.getInner());
            case CastExpr cast -> compact(cast.getExpression());
            case ArrayAccessExpr access -> compact(access.getName()) + "[]";
            default -> "…";
        };
    }

    private static String text(Node node) {
        return node.toString().replaceAll("\\s+", " ");
    }

    private static String join(List<? extends Node> nodes) {
        return nodes.stream().map(FlowExtractor::text).collect(Collectors.joining(", "));
    }

    private static int line(Node node) {
        return node.getBegin().orElseThrow().line;
    }
}
