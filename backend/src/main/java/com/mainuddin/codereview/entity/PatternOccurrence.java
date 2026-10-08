package com.mainuddin.codereview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pattern_occurrences")
@Getter @Setter
public class PatternOccurrence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "profile_id", nullable = false)
    private RepositoryPatternProfile profile;
    @Column(name = "pattern_type", nullable = false)
    private String patternType;
    @Column(name = "pattern_family", nullable = false)
    private String patternFamily;
    @Column(name = "confidence", nullable = false)
    private String confidence;
    @Lob @Column(name = "rationale")
    private String rationale;
    @Lob @Column(name = "confidence_reason")
    private String confidenceReason;
    @Lob @Column(name = "evidence_json")
    private String evidenceJson;
}
