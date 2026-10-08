package com.mainuddin.codereview.design;

public record PatternDiagnostics(int javaFileCount, int typeCount, int interfaceCount, int abstractClassCount,
        int controllerCount, int serviceCount, int repositoryCount, int relationshipCount, int detectedPatternCount,
        String relationshipDefinition) {
    public PatternDiagnostics(int javaFileCount, int typeCount, int interfaceCount, int abstractClassCount,
            int controllerCount, int serviceCount, int repositoryCount, int relationshipCount, int detectedPatternCount) {
        this(javaFileCount, typeCount, interfaceCount, abstractClassCount, controllerCount, serviceCount,
                repositoryCount, relationshipCount, detectedPatternCount, "LEGACY_DECLARATIONS");
    }
    public static PatternDiagnostics from(JavaTypeIndex index, int patterns) {
        int interfaces = 0, abstracts = 0, controllers = 0, services = 0, repositories = 0;
        for (var type : index.types()) {
            if (type.isInterface()) interfaces++;
            if (type.isAbstract()) abstracts++;
            if (type.hasAnnotation("Controller") || type.hasAnnotation("RestController")) controllers++;
            if (type.hasAnnotation("Service")) services++;
            if (ArchitecturalPatternRules.repository(type)) repositories++;

        }
        return new PatternDiagnostics(index.javaFileCount(), index.types().size(), interfaces, abstracts,
                controllers, services, repositories, index.declaredRelationships().size(), patterns, "REPOSITORY_LOCAL_BY_KIND_V1");
    }
    // Compact counts fit the existing 255-character progress column; source is never persisted.
    public String savedProgress() { return ("REPOSITORY_LOCAL_BY_KIND_V1".equals(relationshipDefinition) ? "CompletedV2|" : "Completed|") + javaFileCount + "," + typeCount + "," + interfaceCount + "," + abstractClassCount + "," + controllerCount + "," + serviceCount + "," + repositoryCount + "," + relationshipCount + "," + detectedPatternCount; }
    public static PatternDiagnostics read(String progress) {
        if (progress == null || !(progress.startsWith("Completed|") || progress.startsWith("CompletedV2|"))) return null;
        try {
            boolean v2 = progress.startsWith("CompletedV2|");
            var n = progress.substring(v2 ? 12 : 10).split(",");
            if (n.length != 9) return null;
            return new PatternDiagnostics(Integer.parseInt(n[0]), Integer.parseInt(n[1]), Integer.parseInt(n[2]), Integer.parseInt(n[3]), Integer.parseInt(n[4]), Integer.parseInt(n[5]), Integer.parseInt(n[6]), Integer.parseInt(n[7]), Integer.parseInt(n[8]), v2 ? "REPOSITORY_LOCAL_BY_KIND_V1" : "LEGACY_DECLARATIONS");
        } catch (NumberFormatException invalid) { return null; }
    }
}
