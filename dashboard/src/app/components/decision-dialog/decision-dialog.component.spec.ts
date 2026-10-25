import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import {provideTranslateService} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TaskRepo} from '../../repositories/api/task.repo';
import {UpdateDecisionDto, UpdateDecisionRequest} from '../../repositories/dto/update-decision.dto';
import {DecisionDialogComponent} from './decision-dialog.component';

/**
 * The two rules that make a decision worth recording: a postponement cannot be
 * saved without a date, and the date it sends is the end of the chosen day in
 * the reader's own time zone. Both are tested through the rendered dialog
 * rather than by calling the methods, because both are only true if the
 * template is wired to them.
 */
describe('DecisionDialogComponent', () => {

  let fixture: ComponentFixture<DecisionDialogComponent>;
  let decide: ReturnType<typeof vi.fn>;

  const saveButton = (): HTMLButtonElement =>
    fixture.nativeElement.querySelector('.modal-footer .btn-primary') as HTMLButtonElement;

  const stateSelect = (): HTMLSelectElement =>
    fixture.nativeElement.querySelector('#decisionState') as HTMLSelectElement;

  const dateInput = (): HTMLInputElement | null =>
    fixture.nativeElement.querySelector('#snoozeUntil') as HTMLInputElement | null;

  /** Picks a state the way a person does: through the select, not the field. */
  async function choose(state: string): Promise<void> {
    const select = stateSelect();
    select.value = [...select.options].find((option) => option.textContent?.trim().endsWith(state))?.value ?? state;
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function type(input: HTMLInputElement, value: string): Promise<void> {
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    decide = vi.fn().mockReturnValue(of(new UpdateDecisionDto({id: 'decision-1'})));

    await TestBed.configureTestingModule({
      imports: [DecisionDialogComponent],
      providers: [
        provideTranslateService({fallbackLang: 'en'}),
        {provide: NgbActiveModal, useValue: {close: vi.fn(), dismiss: vi.fn()}},
        {provide: TaskRepo, useValue: {decide}}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DecisionDialogComponent);
    fixture.componentInstance.componentId = 'component-1';
    fixture.componentInstance.componentName = 'PostgreSQL';
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('offers no date field until the update is postponed', async () => {
    expect(dateInput()).toBeNull();

    await choose('POSTPONED');

    expect(dateInput()).not.toBeNull();
  });

  it('refuses to save a postponement without a date', async () => {
    await choose('POSTPONED');

    // Without the date the entry would drop off the list and never come back,
    // which is the situation the whole feature replaces.
    expect(saveButton().disabled).toBe(true);

    await type(dateInput()!, '2026-12-24');

    expect(saveButton().disabled).toBe(false);
  });

  it('saves the other states straight away', async () => {
    // PLANNED is preselected, and nothing else is required for it.
    expect(saveButton().disabled).toBe(false);

    saveButton().click();

    expect(decide).toHaveBeenCalledTimes(1);
    const request = decide.mock.calls[0][0] as UpdateDecisionRequest;
    expect(request.state).toBe('PLANNED');
    expect(request.snoozeUntil).toBeUndefined();
  });

  it('sends the end of the chosen day, not its start', async () => {
    await choose('POSTPONED');
    await type(dateInput()!, '2026-12-24');

    saveButton().click();

    const request = decide.mock.calls[0][0] as UpdateDecisionRequest;
    // The bare date would be parsed as UTC midnight: postponing to today would
    // store a moment that is already past, and the entry would be back on the
    // list before the dialog has closed. What goes out is 23:59:59 local time.
    const sent = new Date(request.snoozeUntil!);
    expect(sent.getFullYear()).toBe(2026);
    expect(sent.getMonth()).toBe(11);
    expect(sent.getDate()).toBe(24);
    expect(sent.getHours()).toBe(23);
    expect(sent.getMinutes()).toBe(59);
  });

  it('drops a note that is only whitespace', async () => {
    await type(fixture.nativeElement.querySelector('#decisionNote') as HTMLInputElement, '   ');

    saveButton().click();

    const request = decide.mock.calls[0][0] as UpdateDecisionRequest;
    // Stored, it would look like an explanation in the history and say nothing.
    expect(request.note).toBeUndefined();
  });

  it('refuses to hold without a reason', async () => {
    await choose('HELD');

    // A hold stays for good; the reason is what lets somebody lift it later.
    expect(saveButton().disabled).toBe(true);

    await type(fixture.nativeElement.querySelector('#decisionNote') as HTMLInputElement, 'Last free version');

    expect(saveButton().disabled).toBe(false);
  });
});
