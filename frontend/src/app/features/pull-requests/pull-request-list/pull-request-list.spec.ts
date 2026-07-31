import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PullRequestList } from './pull-request-list';

describe('PullRequestList', () => {
  let component: PullRequestList;
  let fixture: ComponentFixture<PullRequestList>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PullRequestList]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PullRequestList);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
