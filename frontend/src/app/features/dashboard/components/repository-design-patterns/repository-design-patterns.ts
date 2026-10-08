import { DesignBadge, DesignEvidence, DesignDisclosure } from '../../../../components/design-presentation/design-presentation';
import { InfoPopover } from '../../../../components/info-popover/info-popover';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DesignPatternService } from '../../../../services/design-pattern.service';
import { PatternProfile } from '../../../../models/design-pattern.model';

@Component({
  selector: 'app-repository-design-patterns', standalone: true,
  imports: [DesignBadge, DesignEvidence, DesignDisclosure, CommonModule, RouterLink, InfoPopover], templateUrl: './repository-design-patterns.html'
})
export class RepositoryDesignPatterns implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private api = inject(DesignPatternService);
  private timer?: ReturnType<typeof setTimeout>;
  projectId = Number(this.route.snapshot.paramMap.get('projectId'));
  profile = signal<PatternProfile | null>(null);
  loading = signal(true);
  refreshing = signal(false);
  error = signal<string | null>(null);

  ngOnInit() { this.load(); }
  ngOnDestroy() { if (this.timer) clearTimeout(this.timer); }

  load() {
    if (!Number.isInteger(this.projectId) || this.projectId < 1) {
      this.error.set('Invalid repository.'); this.loading.set(false); return;
    }
    this.api.profile(this.projectId).subscribe({
      next: value => { this.profile.set(value); this.loading.set(false); this.refreshing.set(false); this.pollIfRunning(value); },
      error: err => { this.error.set(err.status === 404 ? 'Repository not found or no longer available.' : 'Design profile is unavailable. Please try again.'); this.loading.set(false); this.refreshing.set(false); }
    });
  }

  refresh() {
    if (this.refreshing() || this.profile()?.status === 'RUNNING') return;
    this.error.set(null); this.refreshing.set(true);
    this.api.refreshProfile(this.projectId).subscribe({
      next: value => { this.profile.set(value); this.refreshing.set(false); this.pollIfRunning(value); },
      error: err => {
        this.error.set(err.status === 401 ? 'Reconnect GitHub before analyzing repository design.'
          : 'Could not start design analysis. Please try again.');
        this.refreshing.set(false);
      }
    });
  }

  private pollIfRunning(value: PatternProfile | null) {
    if (this.timer) clearTimeout(this.timer);
    if (value?.status === 'RUNNING') this.timer = setTimeout(() => this.load(), 1800);
  }

  label(value: string) { return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase()); }
  supportedPatterns(family: 'GOF' | 'ARCHITECTURAL') {
    return family === 'GOF' ? ['Strategy', 'Factory Method', 'Builder', 'Singleton', 'Observer', 'Adapter', 'Template Method', 'Decorator']
      : ['Controller-Service-Repository layering', 'Repository abstraction', 'Constructor dependency injection', 'DTO / Mapper separation'];
  }
  metrics(profile: PatternProfile) {
    const d = profile.diagnostics;
    return d ? [
      { label: 'Java files', value: d.javaFileCount }, { label: 'Types discovered', value: d.typeCount },
      { label: 'Interfaces', value: d.interfaceCount }, { label: 'Abstract classes', value: d.abstractClassCount },
      { label: 'Controllers', value: d.controllerCount }, { label: 'Services', value: d.serviceCount },
      { label: 'Repositories', value: d.repositoryCount }, { label: d.relationshipDefinition === 'REPOSITORY_LOCAL_BY_KIND_V1' ? 'Repository-local relationships' : 'Declared relationships', value: d.relationshipCount },
      { label: 'Detected patterns', value: d.detectedPatternCount }
    ] : [];
  }
  failureTitle(profile: PatternProfile) {
    if (['TOO_MANY_JAVA_FILES', 'JAVA_FILE_TOO_LARGE', 'TOTAL_SOURCE_TOO_LARGE', 'TREE_RESPONSE_TOO_LARGE'].includes(profile.failureCode || '')) return 'Repository too large for pattern analysis';
    return 'Pattern analysis could not be completed';
  }
  failureDescription(profile: PatternProfile) {
    const number = (value: number) => value.toLocaleString('en-US');
    if (profile.failureCode === 'TOO_MANY_JAVA_FILES') return profile.observed != null && profile.limit != null
      ? `${number(profile.observed)} Java files were found. Current safe analysis limit: ${number(profile.limit)} files.`
      : 'The repository exceeds the safe Java-file count limit.';
    const messages: Record<string, string> = {
      JAVA_FILE_TOO_LARGE: 'A Java source file exceeds the safe file-size limit.',
      TOTAL_SOURCE_TOO_LARGE: 'The combined Java source exceeds the safe analysis size limit.',
      TREE_RESPONSE_TOO_LARGE: 'Repository metadata exceeds the safe download limit.',
      TREE_TRUNCATED: 'GitHub returned an incomplete repository tree. A complete profile cannot be created safely.',
      SOURCE_BLOB_INVALID: 'A Java source file could not be verified or decoded safely. Please retry.',
      SOURCE_PARSE_FAILED: 'A Java source file could not be parsed into a complete profile.'
    };
    return messages[profile.failureCode || ''] || profile.failureMessage || 'Please check GitHub access and try refreshing the profile.';
  }
  occurrencesFor(patternType: string) {
    return this.profile()?.occurrences.filter(item => item.patternType === patternType) ?? [];
  }
  patterns(family: 'GOF' | 'ARCHITECTURAL') {
    return this.profile()?.occurrences.filter(item => item.patternFamily === family) ?? [];
  }
}
