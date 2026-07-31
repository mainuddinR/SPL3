import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PullRequestDetails } from './pull-request-details';

describe('PullRequestDetails', () => {
  let component: PullRequestDetails;
  let fixture: ComponentFixture<PullRequestDetails>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PullRequestDetails]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PullRequestDetails);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
