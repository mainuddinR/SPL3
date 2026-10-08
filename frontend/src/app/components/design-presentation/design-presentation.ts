import { AfterViewInit, Component, Directive, ElementRef, HostListener, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PatternEvidence } from '../../models/design-pattern.model';

@Component({ selector: 'app-design-badge', standalone: true, template: `<span class="design-chip" [class]="'design-chip design-chip-' + value.toLowerCase()">{{ count === null ? '' : count + ' ' }}{{ label() }}{{ confidence ? ' confidence' : '' }}</span>` })
export class DesignBadge {
  @Input({ required: true }) value = '';
  @Input() confidence = false;
  @Input() count: number | null = null;
  label() { return this.value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase()); }
}

@Component({ selector: 'app-design-evidence', standalone: true, imports: [CommonModule], template: `
  <div class="design-evidence-grid">
    <div *ngFor="let item of evidence" class="design-evidence-row">
      <span class="design-evidence-role">{{ item.role }}</span>
      <p class="mt-2 break-all font-semibold">{{ item.className }}<span *ngIf="item.methodName">.{{ item.methodName }}()</span></p>
      <p class="mt-1 break-all font-mono text-xs text-gray-500">{{ item.sourcePath }}<span *ngIf="item.line"> : {{ item.line }}</span></p>
    </div>
  </div>` })
export class DesignEvidence { @Input({ required: true }) evidence: PatternEvidence[] = []; }

@Directive({ selector: 'details[appDesignDisclosure]', standalone: true })
export class DesignDisclosure implements AfterViewInit {
  private static nextId = 0;
  private host = inject(ElementRef<HTMLDetailsElement>);
  private label = '';
  ngAfterViewInit() {
    const details = this.host.nativeElement;
    details.id ||= `design-disclosure-${DesignDisclosure.nextId++}`;
    this.label = details.querySelector('summary')?.textContent?.trim() || 'View evidence';
    this.update();
  }
  @HostListener('toggle') update() {
    const details = this.host.nativeElement, summary = details.querySelector('summary');
    if (!summary || !this.label) return;
    summary.classList.add('design-disclosure');
    summary.setAttribute('aria-expanded', String(details.open));
    summary.setAttribute('aria-controls', details.id);
    summary.textContent = (details.open ? this.label.replace(/^View/, 'Hide') : this.label) + (details.open ? ' \u25b4' : ' \u25be');
  }
}
