import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DesignBadge, DesignDisclosure, DesignEvidence } from './design-presentation';
@Component({ standalone: true, imports: [DesignBadge, DesignDisclosure, DesignEvidence], template: `
  <app-design-badge value="HIGH" [confidence]="true"/><app-design-badge value="MEDIUM" [confidence]="true"/>
  <app-design-badge value="CONFORMS"/><app-design-badge value="POTENTIAL_DEVIATION"/>
  <app-design-badge value="INSUFFICIENT_EVIDENCE"/><app-design-badge value="NOT_APPLICABLE"/>
  <details appDesignDisclosure><summary>View evidence</summary><app-design-evidence [evidence]="[{role:'Mapper',className:'AuthService',sourcePath:'AuthService.java',methodName:'register',line:18}]"/></details>` })
class PresentationHost {}
describe('Design presentation', () => {
  it('renders confidence/status chips and role-separated evidence with accessible disclosure', () => {
    TestBed.configureTestingModule({ imports: [PresentationHost] });
    const fixture = TestBed.createComponent(PresentationHost); fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    for (const value of ['high','medium','conforms','potential_deviation','insufficient_evidence','not_applicable']) expect(host.querySelector('.design-chip-' + value)).not.toBeNull();
    const details = host.querySelector('details')!, summary = host.querySelector('summary')!;
    expect(details.open).toBeFalse(); expect(summary.getAttribute('aria-expanded')).toBe('false');
    expect(summary.getAttribute('aria-controls')).toBe(details.id);
    details.open = true; details.dispatchEvent(new Event('toggle'));
    expect(summary.textContent).toContain('Hide evidence'); expect(summary.getAttribute('aria-expanded')).toBe('true');
    expect(host.querySelector('.design-evidence-role')?.textContent).toBe('Mapper');
    expect(host.querySelector('.design-evidence-row')?.textContent).toContain('AuthService.register()');
  });
});
