package com.yanny.archflow.extract;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithExtends;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.IntersectionType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnionType;
import com.github.javaparser.ast.type.WildcardType;
import com.yanny.archflow.facts.FieldFacts;
import com.yanny.archflow.facts.FlowFact;
import com.yanny.archflow.facts.FileFacts;
import com.yanny.archflow.facts.Import;
import com.yanny.archflow.facts.MethodFacts;
import com.yanny.archflow.facts.Param;
import com.yanny.archflow.facts.TypeFacts;
import com.yanny.archflow.facts.TypeRef;
import com.yanny.archflow.model.Location;

import java.util.ArrayList;
import java.util.List;

public final class JavaExtractor {
    private final JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25));

    public FileFacts extract(String file, String source) {
        ParseResult<CompilationUnit> result = parser.parse(source);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return FileFacts.failed(file, result.getProblems().toString());
        }
        CompilationUnit unit = result.getResult().get();
        String packageName = unit.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
        List<Import> imports = unit.getImports().stream()
                .map(i -> new Import(i.getNameAsString(), i.isStatic(), i.isAsterisk(), line(i)))
                .toList();
        List<TypeFacts> types = unit.getTypes().stream().map(t -> type(file, t)).toList();
        return new FileFacts(file, packageName, imports, types, null);
    }

    private TypeFacts type(String file, TypeDeclaration<?> type) {
        TypeRef superclass = null;
        List<TypeRef> interfaces = new ArrayList<>();
        if (type instanceof ClassOrInterfaceDeclaration declaration && declaration.isInterface()) {
            declaration.getExtendedTypes().forEach(t -> interfaces.add(ref(t)));
        } else if (type instanceof NodeWithExtends<?> extending && extending.getExtendedTypes().isNonEmpty()) {
            superclass = ref(extending.getExtendedTypes(0));
        }
        if (type instanceof NodeWithImplements<?> implementing) {
            implementing.getImplementedTypes().forEach(t -> interfaces.add(ref(t)));
        }
        List<FieldFacts> fields = new ArrayList<>();
        if (type instanceof RecordDeclaration record) {
            record.getParameters().forEach(p -> fields.add(new FieldFacts(p.getNameAsString(), ref(p.getType()))));
        }
        List<MethodFacts> methods = new ArrayList<>();
        List<TypeFacts> types = new ArrayList<>();
        List<Node> staticParts = new ArrayList<>();
        List<Node> instanceParts = new ArrayList<>();
        if (type instanceof EnumDeclaration enumeration) {
            enumeration.getEntries().forEach(entry -> staticParts.addAll(entry.getArguments()));
        }
        boolean isInterface = type instanceof ClassOrInterfaceDeclaration declaration && declaration.isInterface();
        for (var member : type.getMembers()) {
            switch (member) {
                case FieldDeclaration field -> {
                    List<Node> parts = field.isStatic() || isInterface ? staticParts : instanceParts;
                    for (var variable : field.getVariables()) {
                        fields.add(new FieldFacts(variable.getNameAsString(), ref(variable.getType())));
                        if (variable.getInitializer().isPresent()) {
                            parts.add(variable);
                        }
                    }
                }
                case InitializerDeclaration initializer -> (initializer.isStatic() ? staticParts : instanceParts).add(initializer.getBody());
                case MethodDeclaration method -> methods.add(method(file, method, method.getNameAsString(),
                        method.getType(), method.getBody().orElse(null)));
                case ConstructorDeclaration constructor -> methods.add(method(file, constructor, MethodFacts.CONSTRUCTOR,
                        null, constructor.getBody()));
                case TypeDeclaration<?> nested -> types.add(type(file, nested));
                default -> {
                }
            }
        }
        initializer(file, methods, MethodFacts.STATIC_INIT, staticParts);
        initializer(file, methods, MethodFacts.INSTANCE_INIT, instanceParts);
        if (type instanceof RecordDeclaration record) {
            for (var component : record.getParameters()) {
                String name = component.getNameAsString();
                if (methods.stream().noneMatch(m -> m.name().equals(name) && m.params().isEmpty())) {
                    methods.add(new MethodFacts(name, List.of(), ref(component.getType()), location(file, component), false,
                            List.of(), List.of()));
                }
            }
        }
        return new TypeFacts(type.getNameAsString(), location(file, type), superclass, interfaces, fields, methods, types);
    }

    private void initializer(String file, List<MethodFacts> methods, String name, List<Node> parts) {
        FlowExtractor.Extracted extracted = FlowExtractor.initializers(parts);
        if (!extracted.flow().isEmpty() || !extracted.implementations().isEmpty()) {
            Location location = new Location(file, location(file, parts.getFirst()).line(), location(file, parts.getLast()).endLine());
            methods.add(new MethodFacts(name, List.of(), null, location, false, extracted.flow(), extracted.implementations()));
        }
    }

    private MethodFacts method(String file, CallableDeclaration<?> method, String name, Type returnType, BlockStmt body) {
        List<Param> params = method.getParameters().stream()
                .map(p -> new Param(p.getNameAsString(), ref(p.getType()).withDims(p.isVarArgs() ? 1 : 0), p.isVarArgs()))
                .toList();
        FlowExtractor.Extracted extracted = body == null
                ? new FlowExtractor.Extracted(List.of(), List.of())
                : FlowExtractor.body(method.getParameters(), returnType, body);
        return new MethodFacts(name, params, returnType == null ? null : ref(returnType), location(file, method), body == null,
                extracted.flow(), extracted.implementations());
    }

    static TypeRef ref(Type type) {
        return switch (type) {
            case ClassOrInterfaceType named -> new TypeRef(named.getNameWithScope(),
                    named.getTypeArguments().map(args -> args.stream().map(JavaExtractor::ref).toList()).orElse(List.of()), 0, line(named));
            case ArrayType array -> ref(array.getComponentType()).withDims(1);
            case WildcardType wildcard -> wildcard.getExtendedType().map(JavaExtractor::ref)
                    .orElseGet(() -> new TypeRef("?", List.of(), 0, line(wildcard)));
            case UnionType union -> ref(union.getElements().get(0));
            case IntersectionType intersection -> ref(intersection.getElements().get(0));
            default -> new TypeRef(type.asString(), List.of(), 0, line(type));
        };
    }

    private static int line(Node node) {
        return node.getRange().map(range -> range.begin.line).orElse(0);
    }

    static Location location(String file, Node node) {
        var range = node.getRange().orElseThrow();
        return new Location(file, range.begin.line, range.end.line);
    }
}
