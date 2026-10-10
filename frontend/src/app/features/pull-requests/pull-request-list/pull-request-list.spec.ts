import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { PullRequestList } from './pull-request-list';
import { PullRequestService } from '../../../services/pull-request.service';
import { NotificationService } from '../../../services/notification.service';
import { PullRequest } from '../../../models/pull-request.model';

describe('PullRequestList', () => {
  let fixture: ComponentFixture<PullRequestList>;
  let component: PullRequestList;
  let service: jasmine.SpyObj<PullRequestService>;
  let router: jasmine.SpyObj<Router>;
  const pr = (id: number, state: string): PullRequest => ({ id, githubPrId: id + 100,
    number: id, title: `PR ${id}`, state, htmlUrl: '', body: '',
    createdAt: '2026-10-07T10:00:00', updatedAt: '2026-10-07T10:00:00' });

  beforeEach(async () => {
    service = jasmine.createSpyObj<PullRequestService>('PullRequestService', ['getPullRequests', 'syncPullRequests']);
    service.getPullRequests.and.returnValue(of([pr(1, 'open'), pr(2, 'closed'), pr(3, 'merged')]));
    service.syncPullRequests.and.returnValue(of(void 0));
    router = jasmine.createSpyObj<Router>('Router', ['navigate', 'createUrlTree', 'serializeUrl']);
    router.createUrlTree.and.returnValue({} as ReturnType<Router['createUrlTree']>);
    router.serializeUrl.and.returnValue('/dashboard/projects/7/design-patterns');
    Object.defineProperty(router, 'events', { value: of() });
    await TestBed.configureTestingModule({ imports: [PullRequestList], providers: [
      { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ projectId: '7' })) } },
      { provide: PullRequestService, useValue: service },
      { provide: NotificationService, useValue: { refresh: jasmine.createSpy('refresh') } },
      { provide: Router, useValue: router }
    ] }).compileComponents();
    fixture = TestBed.createComponent(PullRequestList);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows readable Open, Closed, and available Merged chips with responsive rows', () => {
    const chips = fixture.nativeElement.querySelectorAll('.pr-state-chip') as NodeListOf<HTMLElement>;
    expect(Array.from(chips).map(chip => chip.textContent?.trim())).toEqual(['Open', 'Closed', 'Merged']);
    expect(chips[0].classList.contains('pr-state-open')).toBeTrue();
    expect(chips[1].classList.contains('pr-state-closed')).toBeTrue();
    expect(chips[2].classList.contains('pr-state-merged')).toBeTrue();
    expect(fixture.nativeElement.querySelector('.pr-list-table td[data-label="Status"]')).not.toBeNull();
    document.documentElement.classList.add('dark');
    expect(getComputedStyle(chips[0]).color).not.toBe(getComputedStyle(chips[1]).color);
    expect(getComputedStyle(chips[1]).color).not.toBe('rgb(127, 29, 29)');
    document.documentElement.classList.remove('dark');
  });

  it('preserves PR navigation and manual sync', () => {
    component.viewDetails(1);
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard/pulls', 1]);
    component.syncPullRequests();
    expect(service.syncPullRequests).toHaveBeenCalledWith(7);
    expect(service.getPullRequests).toHaveBeenCalledTimes(2);
  });

  it('filters by the visible GitHub PR number, accepting 146 and #146', () => {
    component.pullRequests.set([
      { ...pr(99, 'open'), number: 146 },
      { ...pr(98, 'closed'), number: 143 },
      { ...pr(97, 'merged'), number: 2 }
    ]);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('#pr-number-search') as HTMLInputElement;
    expect(input).not.toBeNull();
    input.value = '146';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.pr-list-table tbody tr');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('#146');
    expect(rows[0].textContent).not.toContain('#143');
    expect(component.filteredPullRequests()[0].id).toBe(99);

    input.value = '#146';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(component.filteredPullRequests().map(pr => pr.number)).toEqual([146]);

    component.viewDetails(component.filteredPullRequests()[0].id);
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard/pulls', 99]);
  });

  it('supports partial PR numbers, no-match messaging and clearing', () => {
    component.pullRequests.set([
      { ...pr(21, 'open'), number: 146 },
      { ...pr(22, 'closed'), number: 143 },
      { ...pr(23, 'merged'), number: 2 }
    ]);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('#pr-number-search') as HTMLInputElement;

    input.value = '14';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(component.filteredPullRequests().map(pr => pr.number)).toEqual([146, 143]);

    input.value = '999';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.pr-list-table tbody tr').length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('No pull request found for "999"');

    const clear = fixture.nativeElement.querySelector('button[aria-label="Clear PR number search"]') as HTMLButtonElement;
    clear.click();
    fixture.detectChanges();
    expect(component.prNumberSearch()).toBe('');
    expect(component.filteredPullRequests().length).toBe(3);
    expect(fixture.nativeElement.querySelectorAll('.pr-list-table tbody tr').length).toBe(3);
  });

  it('keeps the filter when GitHub sync refreshes the PR list', () => {
    component.prNumberSearch.set('#2');
    fixture.detectChanges();
    component.syncPullRequests();
    fixture.detectChanges();
    expect(component.filteredPullRequests().map(pr => pr.number)).toEqual([2]);
    expect(service.syncPullRequests).toHaveBeenCalledWith(7);
  });
});
