import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import {provideTranslateService} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentEnvironmentRepo} from '../../repositories/api/component-environment.repo';
import {ComponentEnvironmentDto, RolloutPlanRequest} from '../../repositories/dto/component-environment.dto';
import {PlanDialogComponent} from './plan-dialog.component';

/**
 * A rollout plan needs a version and a day, nothing else; the proposed target is offered so the usual plan is
 * one date away.
 */
describe('PlanDialogComponent', () => {

  let fixture: ComponentFixture<PlanDialogComponent>;
  let plan: ReturnType<typeof vi.fn>;
  let close: ReturnType<typeof vi.fn>;

  const saveButton = (): HTMLButtonElement =>
    fixture.nativeElement.querySelector('.modal-footer .btn-primary') as HTMLButtonElement;

  const input = (id: string): HTMLInputElement => fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement;

  async function type(field: HTMLInputElement, value: string): Promise<void> {
    field.value = value;
    field.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    plan = vi.fn().mockReturnValue(of(new ComponentEnvironmentDto({id: 'cell-1'})));
    close = vi.fn();

    await TestBed.configureTestingModule({
      imports: [PlanDialogComponent],
      providers: [
        provideTranslateService({fallbackLang: 'en'}),
        {provide: NgbActiveModal, useValue: {close, dismiss: vi.fn()}},
        {provide: ComponentEnvironmentRepo, useValue: {plan}}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PlanDialogComponent);
    fixture.componentInstance.cell = new ComponentEnvironmentDto({id: 'cell-1', currentVersion: '17.2', active: true});
    fixture.componentInstance.componentName = 'PostgreSQL';
    fixture.componentInstance.environmentName = 'Production';
    fixture.componentInstance.suggestedVersion = '17.4';
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('offers the proposed target and waits for the day', async () => {
    expect(input('plannedVersion').value).toBe('17.4');
    expect(saveButton().disabled).toBe(true);

    await type(input('plannedFor'), '2026-11-01');

    expect(saveButton().disabled).toBe(false);
  });

  it('sends version and day and closes with the cell', async () => {
    await type(input('plannedFor'), '2026-11-01');

    saveButton().click();

    expect(plan).toHaveBeenCalledWith('cell-1', new RolloutPlanRequest({plannedVersion: '17.4', plannedFor: '2026-11-01',
                                                                        note: undefined}));
    expect(close).toHaveBeenCalled();
  });
});
