import { InfoPopover } from '../../../components/info-popover/info-popover';
import { DesignBadge, DesignEvidence, DesignDisclosure } from '../../../components/design-presentation/design-presentation';
import { Component, Input, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DesignPatternService } from '../../../services/design-pattern.service';
import { PatternConformance, PatternConformanceStatus, PatternProfile } from '../../../models/design-pattern.model';

@Component({
  selector: 'app-pr-design-conformance', standalone: true,
  imports: [DesignBadge, DesignEvidence, DesignDisclosure, CommonModule, RouterLink, InfoPopover], templateUrl: './pr-design-conformance.html'
})
export class PrDesignConformance implements OnInit, OnDestroy {
  @Input({ required: true }) prId!: number;
  private api = inject(DesignPatternService);
  private checkRequestVersion = 0;
  private timer?: ReturnType<typeof setTimeout>;
  profile = signal<PatternProfile | null>(null);
  check = signal<PatternConformance | null>(null);
  loading = signal(true);
  starting = signal(false);
  error = signal<string | null>(null);

  ngOnInit() { this.load(); }
  ngOnDestroy() { if (this.timer) clearTimeout(this.timer); }

  load() {
    this.api.profileForPr(this.prId).subscribe({
      next: value => { this.profile.set(value); this.loading.set(false); },
      error: () => { this.error.set('Repository design profile is unavailable.'); this.loading.set(false); }
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
    if (this.starting() || this.check()?.status === 'RUNNING' || this.profile()?.status !== 'COMPLETED') return;
    ++this.checkRequestVersion;
    this.starting.set(true); this.error.set(null);
    this.api.runCheck(this.prId).subscribe({
      next: value => { this.check.set(value); this.starting.set(false); this.pollIfRunning(value); },
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
    return this.count('POTENTIAL_DEVIATION') ? 'POTENTIAL_DEVIATION' : this.count('CONFORMS') ? 'CONFORMS'
      : this.count('INSUFFICIENT_EVIDENCE') ? 'INSUFFICIENT_EVIDENCE' : 'NOT_APPLICABLE';
  }
  count(status: PatternConformanceStatus) { return this.check()?.findings.filter(item => item.status === status).length ?? 0; }
  label(value: string) { return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase()); }
}
