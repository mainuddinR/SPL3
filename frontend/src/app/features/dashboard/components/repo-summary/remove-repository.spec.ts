import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RemoveRepository } from './remove-repository';

describe('RemoveRepository', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [RemoveRepository], providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  const create = () => {
    const fixture = TestBed.createComponent(RemoveRepository);
    fixture.componentRef.setInput('projectId', 7); fixture.componentRef.setInput('name', 'owner/demo');
    fixture.detectChanges(); return fixture;
  };
  it('shows an accessible local-only dialog and cancellation does not send a request', () => {
    const fixture = create();
    expect(fixture.nativeElement.querySelector('[role="dialog"]').getAttribute('aria-modal')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('GitHub repository itself will not be deleted');
    const closed = spyOn(fixture.componentInstance.closed, 'emit');
    fixture.componentInstance.cancel(); expect(closed).toHaveBeenCalled();
    TestBed.inject(HttpTestingController).expectNone('http://localhost:8080/api/projects/7');
  });
  it('prevents duplicate deletion and closing while busy then emits success', () => {
    const fixture = create(), component = fixture.componentInstance;
    const removed = spyOn(component.removed, 'emit'), closed = spyOn(component.closed, 'emit');
    component.remove(); component.remove(); component.cancel();
    fixture.detectChanges();
    const buttons = fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>;
    expect([...buttons].every(button => button.disabled)).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Deleting');
    expect(closed).not.toHaveBeenCalled();
    const request = TestBed.inject(HttpTestingController).expectOne('http://localhost:8080/api/projects/7');
    expect(request.request.method).toBe('DELETE'); request.flush(null);
    expect(removed).toHaveBeenCalledTimes(1);
  });
  it('closes on Escape without submitting or using course wording', () => {
    const fixture = create(); const closed = spyOn(fixture.componentInstance.closed, 'emit');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(closed).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).not.toContain('SPL3');
  });
  it('keeps the dialog open and hides backend detail on failure', () => {
    const fixture = create(), component = fixture.componentInstance;
    const removed = spyOn(component.removed, 'emit'); component.remove();
    TestBed.inject(HttpTestingController).expectOne('http://localhost:8080/api/projects/7')
      .flush({ message: 'Oracle secret internal error' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(component.busy).toBeFalse(); expect(removed).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Repository could not be removed');
    expect(fixture.nativeElement.textContent).not.toContain('Oracle secret');
  });
});
