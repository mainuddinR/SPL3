import { InfoPopover } from '../../../components/info-popover/info-popover';
import { DesignBadge, DesignEvidence, DesignDisclosure } from '../../../components/design-presentation/design-presentation';
import { Component, Input, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DesignPatternService } from '../../../services/design-pattern.service';
import { PatternConformance, PatternConformanceFinding, PatternConformanceStatus, PatternProfile } from '../../../models/design-pattern.model';

@Component({
  selector: 'app-pr-design-conformance', standalone: true,
  imports: [DesignBadge, DesignEvidence, DesignDisclosure, CommonModule, InfoPopover], templateUrl: './pr-design-conformance.html'
})
export class PrDesignConformance implements OnInit, OnDestroy {
  @Input({ required: true }) prId!: number;
  private api = inject(DesignPatternService);
  private checkRequestVersion = 0;
  private timer?: ReturnType<typeof setTimeout>;
  private destroyed = false;
  private adviceTimers: ReturnType<typeof setTimeout>[] = [];
  adviceState = signal<Record<number, { pending: boolean; error: string | null; coolingDown: boolean }>>({});
  profile = signal<PatternProfile | null>(null);
  check = signal<PatternConformance | null>(null);
  loading = signal(true);
  starting = signal(false);
  error = signal<string | null>(null);

  ngOnInit() { this.load(); }
  ngOnDestroy() { this.destroyed = true; ++this.checkRequestVersion; if (this.timer) clearTimeout(this.timer); this.adviceTimers.forEach(clearTimeout); }

  load() {
    this.api.profileForPr(this.prId).subscribe({
      next: value => { this.profile.set(value); this.loading.set(false); },
      error: () => { this.loading.set(false); }
    });
    this.refreshCheck();
  }

  refreshCheck() {
    const version = ++this.checkRequestVersion;
    this.api.latestCheck(this.prId).subscribe({
      next: value => { if (version !== this.checkRequestVersion) return; this.check.set(value); this.pollIfRunning(value); },
      error: () => { if (version === this.checkRequestVersion) this.error.set('Saved design check is unavailable.'); }
    });
  }

  run() {
    if (this.destroyed || this.starting() || this.check()?.status === 'RUNNING') return;
    ++this.checkRequestVersion;
    this.starting.set(true); this.error.set(null); this.adviceState.set({});
    this.api.runCheck(this.prId).subscribe({
      next: value => { if (this.destroyed) return; this.check.set(value); this.starting.set(false); this.pollIfRunning(value); },
      error: err => {
        this.error.set(err.status === 428 ? 'Sync Details for this PR revision, then try Pattern Check again.'
          : err.status === 401 ? 'Reconnect GitHub before checking design conformance.'
          : 'Could not start Pattern Check. Please try again.');
        this.starting.set(false);
      }
    });
  }

  private pollIfRunning(value: PatternConformance | null) {
    if (this.timer) clearTimeout(this.timer);
    if (value?.status === 'RUNNING') this.timer = setTimeout(() => this.refreshCheck(), 1800);
  }

  baselineMismatch() {
    return this.check()?.findings.some(f => f.patternType === 'REPOSITORY_BASELINE' && f.status === 'INSUFFICIENT_EVIDENCE') ?? false;
  }
  overallStatus(): PatternConformanceStatus {
    const summary = this.check()?.summary;
    if (summary) return summary.overallStatus;
    return this.count('POTENTIAL_DEVIATION') ? 'POTENTIAL_DEVIATION' : this.count('INSUFFICIENT_EVIDENCE') ? 'INSUFFICIENT_EVIDENCE'
      : this.count('CONFORMS') ? 'CONFORMS' : 'NOT_APPLICABLE';
  }
  relevantPatterns() {
    return new Set(this.check()?.findings.filter(f => f.status !== 'NOT_APPLICABLE').map(f => f.patternType)).size;
  }
  canSuggest(f: PatternConformanceFinding) {
    return f.status === 'POTENTIAL_DEVIATION' && !!f.designImpact && !!f.file && f.evidence.length > 0
      && ['STRATEGY', 'FACTORY_METHOD', 'CONTROLLER_SERVICE_REPOSITORY'].includes(f.patternType);
  }
  suggest(index: number) {
    const check = this.check(), finding = check?.findings[index], state = this.adviceState()[index];
    if (this.destroyed || !check || !finding || !this.canSuggest(finding) || finding.aiSuggestion || state?.pending || state?.coolingDown) return;
    this.adviceState.update(s => ({ ...s, [index]: { pending: true, error: null, coolingDown: false } }));
    this.api.generateDesignAdvice(this.prId, check.id, index).subscribe({
      next: result => {
        if (this.destroyed || this.check()?.id !== check.id) return;
        if (result.status === 'AVAILABLE' && result.advice) {
          this.check.update(c => c && ({ ...c, findings: c.findings.map((f, i) => i === index ? { ...f, aiSuggestion: result.advice } : f) }));
          this.adviceState.update(s => ({ ...s, [index]: { pending: false, error: null, coolingDown: false } }));
        } else {
          // Display only server-defined messages, never raw HTTP/provider bodies.
          this.adviceState.update(s => ({ ...s, [index]: { pending: false, error: result.message || 'AI advice is unavailable. The design finding is unchanged.', coolingDown: !!result.retryAfterSeconds } }));
          if (result.retryAfterSeconds) this.adviceTimers.push(setTimeout(() => {
            if (!this.destroyed && this.check()?.id === check.id) this.adviceState.update(s => ({ ...s, [index]: { ...s[index], coolingDown: false } }));
          }, Math.min(3600, Math.max(1, result.retryAfterSeconds)) * 1000));
        }
      },
      error: () => { if (!this.destroyed && this.check()?.id === check.id) this.adviceState.update(s => ({ ...s, [index]: { pending: false, error: 'Could not generate AI advice. The deterministic finding is unchanged.', coolingDown: false } })); }
    });
  }
  statusDescription(): string {
    switch (this.overallStatus()) {
      case 'CONFORMS': return 'The supported design checks found a relevant structure consistent with the saved PR-base profile. This does not guarantee the entire PR is defect-free.';
      case 'POTENTIAL_DEVIATION': return 'One or more structural changes may depart from an established design convention. Inspect the evidence and review priority before deciding on changes.';
      case 'NOT_APPLICABLE': return 'No supported design rule applies to these changes. Routine bug fixes do not need to introduce a design pattern.';
      default: return 'A reliable design verdict could not be established for one or more relevant structures. Review the reason and available evidence.';
    }
  }
  hasEligibleAdvice(): boolean {
    return this.check()?.findings.some(f => this.canSuggest(f)) ?? false;
  }
  count(status: PatternConformanceStatus) { return this.check()?.findings.filter(item => item.status === status).length ?? 0; }
  label(value: string) { return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase()); }
}
