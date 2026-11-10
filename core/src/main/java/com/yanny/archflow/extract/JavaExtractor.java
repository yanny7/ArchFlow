package com.yanny.archflow.extract;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithExtends;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.IntersectionType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnionType;
import com.github.javaparser.ast.type.WildcardType;
import com.yanny.archflow.facts.FieldFacts;
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
                .map(i -> new Import(i.getNameAsString(), i.isStatic(), i.isAsterisk()))
                .toList();
        List<TypeFacts> types = unit.getTypes().stream().map(t -> type(file, t)).toList();
        return new FileFacts(file, packageName, imports, types, null);
    }

    private TypeFacts type(String file, TypeDeclaration<?> type) {
        List<TypeRef> supertypes = new ArrayList<>();
        if (type instanceof NodeWithExtends<?> extending) {
            extending.getExtendedTypes().forEach(t -> supertypes.add(ref(t)));
        }
        if (type instanceof NodeWithImplements<?> implementing) {
            implementing.getImplementedTypes().forEach(t -> supertypes.add(ref(t)));
        }
        List<FieldFacts> fields = new ArrayList<>();
        if (type instanceof RecordDeclaration record) {
            record.getParameters().forEach(p -> fields.add(new FieldFacts(p.getNameAsString(), ref(p.getType()))));
        }
        List<MethodFacts> methods = new ArrayList<>();
        List<TypeFacts> types = new ArrayList<>();
        for (var member : type.getMembers()) {
            switch (member) {
                case FieldDeclaration field -> field.getVariables()
                        .forEach(v -> fields.add(new FieldFacts(v.getNameAsString(), ref(v.getType()))));
                case MethodDeclaration method -> methods.add(method(file, method, method.getNameAsString(), ref(method.getType())));
                case ConstructorDeclaration constructor -> methods.add(method(file, constructor, MethodFacts.CONSTRUCTOR, null));
                case TypeDeclaration<?> nested -> types.add(type(file, nested));
                default -> {
                }
            }
        }
        return new TypeFacts(type.getNameAsString(), location(file, type), supertypes, fields, methods, types);
    }

    private MethodFacts method(String file, CallableDeclaration<?> method, String name, TypeRef returnType) {
        List<Param> params = method.getParameters().stream()
                .map(p -> new Param(p.getNameAsString(), ref(p.getType()).withDims(p.isVarArgs() ? 1 : 0)))
                .toList();
        return new MethodFacts(name, params, returnType, location(file, method));
    }

    static TypeRef ref(Type type) {
        return switch (type) {
            case ClassOrInterfaceType named -> new TypeRef(named.getNameWithScope(),
                    named.getTypeArguments().map(args -> args.stream().map(JavaExtractor::ref).toList()).orElse(List.of()), 0);
            case ArrayType array -> ref(array.getComponentType()).withDims(1);
            case WildcardType wildcard -> wildcard.getExtendedType().map(JavaExtractor::ref)
                    .orElseGet(() -> new TypeRef("?", List.of(), 0));
            case UnionType union -> ref(union.getElements().get(0));
            case IntersectionType intersection -> ref(intersection.getElements().get(0));
            default -> new TypeRef(type.asString(), List.of(), 0);
        };
    }

    static Location location(String file, Node node) {
        var range = node.getRange().orElseThrow();
        return new Location(file, range.begin.line, range.end.line);
    }
}
