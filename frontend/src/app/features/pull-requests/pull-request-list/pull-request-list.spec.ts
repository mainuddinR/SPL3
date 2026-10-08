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
});
