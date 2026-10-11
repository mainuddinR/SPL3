package com.mainuddin.codereview.design;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class JavaTypeIndexBuilder {
    public JavaTypeIndex build(Map<String, String> sources) {
        java.nio.file.Path tempDir = null;
        try {
            tempDir = java.nio.file.Files.createTempDirectory("spl3-design-");
            for (var entry : sources.entrySet()) {
                java.nio.file.Path file = tempDir.resolve(entry.getKey());
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, entry.getValue());
            }
            com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver typeSolver = new com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver(
                    new com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver(false),
                    new com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver(tempDir)
            );
            JavaParser parser = new JavaParser(new ParserConfiguration()
                    .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
                    .setSymbolResolver(new com.github.javaparser.symbolsolver.JavaSymbolSolver(typeSolver)));

            List<JavaTypeIndex.Type> types = new ArrayList<>();
            for (var entry : sources.entrySet()) {
                var parsed = parser.parse(entry.getValue());
                if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
                    // Only the bounded repository-relative filename is exposed; never source text,
                    // parser tokens, GitHub responses or stack traces.
                    String path = entry.getKey().replaceAll("[^A-Za-z0-9_./-]", "_");
                    if (path.length() > 160) path = "..." + path.substring(path.length() - 157);
                    throw new DesignSourceException(
                            "SOURCE_PARSE_FAILED",
                            "JavaParser could not parse " + path
                                    + ". Check that file for invalid or unsupported Java syntax.",
                            null,
                            null);
                }
                var unit = parsed.getResult().orElseThrow();
                String packageName = unit.getPackageDeclaration().map(value -> value.getNameAsString()).orElse("");
                
                // Collect explicit imports and wildcard imports for resolution logic later if needed
                List<String> explicitImports = new ArrayList<>();
                List<String> wildcardImports = new ArrayList<>();
                for (var imp : unit.getImports()) {
                    if (imp.isAsterisk()) wildcardImports.add(imp.getNameAsString());
                    else explicitImports.add(imp.getNameAsString());
                }

                for (TypeDeclaration<?> ast : unit.findAll(TypeDeclaration.class)) {
                    if (!(ast instanceof ClassOrInterfaceDeclaration) && !(ast instanceof EnumDeclaration)
                            && !(ast instanceof RecordDeclaration)) continue;
                    Set<String> extended = new HashSet<>();
                    Set<String> implemented = new HashSet<>();
                    if (ast instanceof ClassOrInterfaceDeclaration declaration) {
                        declaration.getExtendedTypes().forEach(value -> {
                            try { extended.add(value.resolve().describe()); } catch (Exception e) { extended.add(value.getNameWithScope()); }
                        });
                        declaration.getImplementedTypes().forEach(value -> {
                            try { implemented.add(value.resolve().describe()); } catch (Exception e) { implemented.add(value.getNameWithScope()); }
                        });
                    } else if (ast instanceof EnumDeclaration declaration) {
                        declaration.getImplementedTypes().forEach(value -> {
                            try { implemented.add(value.resolve().describe()); } catch (Exception e) { implemented.add(value.getNameWithScope()); }
                        });
                    }
                    Set<String> annotations = new HashSet<>();
                    ast.getAnnotations().forEach(value -> annotations.add(simple(value.getNameAsString())));
                    Set<String> fields = new HashSet<>();
                    ast.getFields().forEach(field -> field.getVariables().forEach(variable -> {
                        try { fields.add(variable.getType().resolve().describe().replaceAll("<.*", "").replace("[]", "")); }
                        catch (Exception e) { fields.add(variable.getType().asString().replaceAll("<.*", "").replace("[]", "")); }
                    }));
                    Set<String> parameters = new HashSet<>();
                    ast.getConstructors().forEach(ctor -> ctor.getParameters().forEach(parameter -> {
                        try { parameters.add(parameter.getType().resolve().describe().replaceAll("<.*", "").replace("[]", "")); }
                        catch (Exception e) { parameters.add(parameter.getType().asString().replaceAll("<.*", "").replace("[]", "")); }
                    }));
                    if (ast instanceof RecordDeclaration record)
                        record.getParameters().forEach(parameter -> {
                            try { parameters.add(parameter.getType().resolve().describe().replaceAll("<.*", "").replace("[]", "")); }
                            catch (Exception e) { parameters.add(simple(parameter.getType().asString())); }
                        });
                    types.add(new JavaTypeIndex.Type(packageName, entry.getKey(), ast.getNameAsString(), ast,
                            Set.copyOf(extended), Set.copyOf(implemented), Set.copyOf(annotations), Set.copyOf(fields),
                            Set.copyOf(parameters), ast.getMethods(), ast.findAll(MethodCallExpr.class).stream()
                                    .filter(call -> call.findAncestor(TypeDeclaration.class).orElse(null) == ast).toList(),
                            ast.findAll(ObjectCreationExpr.class).stream()
                                    .filter(call -> call.findAncestor(TypeDeclaration.class).orElse(null) == ast).toList()));
                }
            }
            Map<String, JavaTypeIndex.Type> qualified = new HashMap<>();
            Map<String, List<JavaTypeIndex.Type>> simpleMap = new HashMap<>();
            for (var type : types) {
                qualified.put(type.qualifiedName(), type);
                simpleMap.computeIfAbsent(type.name(), ignored -> new ArrayList<>()).add(type);
            }
            types.stream().collect(java.util.stream.Collectors.groupingBy(JavaTypeIndex.Type::qualifiedName))
                    .forEach((name, matches) -> { if (matches.size() > 1) qualified.remove(name); });
            return new JavaTypeIndex(List.copyOf(types), Map.copyOf(qualified), Map.copyOf(simpleMap), sources.size());
        } catch (java.io.IOException e) {
            throw new DesignSourceException("TEMP_DIR_FAILED", "Failed to create temporary directory for type resolution.", null, null);
        } finally {
            if (tempDir != null) {
                try { org.springframework.util.FileSystemUtils.deleteRecursively(tempDir); } catch (Exception ignored) {}
            }
        }
    }

    public static String simple(String type) {
        String value = type.replaceAll("<.*", "").replace("[]", "").trim();
        int dot = value.lastIndexOf('.');
        return dot < 0 ? value : value.substring(dot + 1);
    }
}
