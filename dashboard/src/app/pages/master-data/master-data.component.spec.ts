import {provideZoneChangeDetection} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService, TranslateService} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentRepo} from '../../repositories/api/component.repo';
import {EnvironmentRepo} from '../../repositories/api/environment.repo';
import {ProjectRepo} from '../../repositories/api/project.repo';
import {UpdateCheckRepo} from '../../repositories/api/update-check.repo';
import {ComponentDto, ComponentPackageDto, ComponentRequest} from '../../repositories/dto/component.dto';
import {ComponentLinkDto, ComponentLinkRequest} from '../../repositories/dto/component-link.dto';
import {ProjectDto} from '../../repositories/dto/project.dto';
import {MasterDataComponent} from './master-data.component';

/**
 * Where the inventory is maintained. What is tested is the one thing a form like
 * this gets wrong quietly: a row that vanishes on save. Half filled rows are
 * refused, and the three lists are always sent — an omitted list would leave
 * behind exactly the packages, stops and dependencies somebody just removed.
 */
describe('MasterDataComponent', () => {

  let fixture: ComponentFixture<MasterDataComponent>;
  let update: ReturnType<typeof vi.fn>;
  let create: ReturnType<typeof vi.fn>;
  let findComponents: ReturnType<typeof vi.fn>;

  /** Projects, environments, components — in that order on the page. */
  const COMPONENTS = 2;

  const buttonIn = (section: number, label: string): HTMLButtonElement | undefined => {
    const area = fixture.nativeElement.querySelectorAll('section')[section] as HTMLElement;
    return [...area.querySelectorAll('button')]
      .find((button) => (button as HTMLButtonElement).textContent?.trim() === label) as HTMLButtonElement | undefined;
  };

  const field = (id: string): HTMLInputElement =>
    fixture.nativeElement.querySelector('#' + id) as HTMLInputElement;

  const alert = (): HTMLElement | null => fixture.nativeElement.querySelector('.alert-danger');

  async function click(button: HTMLButtonElement | undefined): Promise<void> {
    button!.click();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function type(input: HTMLInputElement, value: string): Promise<void> {
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const docker = new ComponentDto({
    id: 'component-1', projectId: 'project-1', name: 'Docker', sourceType: 'APT_REPOSITORY',
    sourceRef: 'https://download.docker.com/linux/ubuntu',
    packages: [new ComponentPackageDto({id: 'package-1', name: 'docker-ce', sortOrder: 0, leadingPackage: true})]
  });

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(MasterDataComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    create = vi.fn().mockReturnValue(of(docker));
    update = vi.fn().mockReturnValue(of(docker));
    findComponents = vi.fn().mockReturnValue(of([docker]));

    await TestBed.configureTestingModule({
      imports: [MasterDataComponent],
      providers: [
        provideZoneChangeDetection({eventCoalescing: true}),
        provideRouter([]),
        provideTranslateService({fallbackLang: 'en'}),
        {
          provide: ProjectRepo,
          useValue: {findAll: () => of([new ProjectDto({id: 'project-1', name: 'Platform'})])}
        },
        {provide: EnvironmentRepo, useValue: {findAllByProject: () => of([])}},
        {provide: ComponentRepo, useValue: {findAllByProject: findComponents, create, update, delete: vi.fn()}},
        {provide: UpdateCheckRepo, useValue: {previewRule: vi.fn()}}
      ]
    }).compileComponents();

    TestBed.inject(TranslateService).setTranslation('en', {
      'common.new': 'New',
      'common.edit': 'Edit',
      'common.save': 'Save',
      'common.remove': 'Remove',
      'masterData.dependencies.add': 'Add dependency',
      'masterData.dependencies.incomplete': 'Every dependency needs a component and a version range',
      'masterData.links.add': 'Add link'
    });
  });

  it('refuses a half filled dependency instead of dropping it', async () => {
    await render();
    await click(buttonIn(COMPONENTS, 'Edit'));
    await click(buttonIn(COMPONENTS, 'Add dependency'));

    // Only the range, no component. Dropping the row would look exactly like a
    // stored one, and weeks later the dependency simply is not there.
    // The range field of the new row, found by the example the form shows in it.
    const range = fixture.nativeElement.querySelector('input[placeholder=">=14 <18"]') as HTMLInputElement;
    await type(range, '>=14 <18');
    await click(buttonIn(COMPONENTS, 'Save'));

    expect(alert()?.textContent).toContain('Every dependency needs a component and a version range');
    expect(update).not.toHaveBeenCalled();
  });

  it('sends every list, so removing the last entry removes it', async () => {
    await render();
    await click(buttonIn(COMPONENTS, 'Edit'));

    // The component came in with one package; removing it has to reach the
    // server as an empty list. An omitted list leaves the package standing, and
    // the version dialog would keep demanding a version per package for a
    // component that no longer has any.
    await click(buttonIn(COMPONENTS, 'Remove'));
    await click(buttonIn(COMPONENTS, 'Save'));

    const request = update.mock.calls[0][1] as ComponentRequest;
    expect(update).toHaveBeenCalledWith('component-1', request);
    expect(request.packages).toEqual([]);
    expect(request.upgradeStops).toEqual([]);
    expect(request.dependencies).toEqual([]);
    expect(request.links).toEqual([]);
  });

  it('sends the links kept by hand, an empty row left out', async () => {
    findComponents.mockReturnValue(of([new ComponentDto({
      ...docker, links: [new ComponentLinkDto({url: 'https://wiki.example.com/docker', kind: 'OTHER', origin: 'MANUAL'})]
    })]));
    await render();
    await click(buttonIn(COMPONENTS, 'Edit'));

    const inputs = (): HTMLInputElement[] => [...fixture.nativeElement.querySelectorAll('input[placeholder="https://"]')] as HTMLInputElement[];
    expect(inputs().map((input) => input.value)).toEqual(['https://wiki.example.com/docker']);
    await click(buttonIn(COMPONENTS, 'Add link'));
    await type(inputs()[1], ' https://gitlab.example.com/docker ');
    await click(buttonIn(COMPONENTS, 'Add link'));
    await click(buttonIn(COMPONENTS, 'Save'));

    const request = update.mock.calls[0][1] as ComponentRequest;
    expect(request.links).toEqual([new ComponentLinkRequest({url: 'https://wiki.example.com/docker'}),
                                   new ComponentLinkRequest({url: 'https://gitlab.example.com/docker'})]);
  });

  it('puts a copy button behind the name and the source of every component', async () => {
    await render();

    const row = fixture.nativeElement.querySelectorAll('section')[COMPONENTS].querySelector('tbody tr') as HTMLTableRowElement;
    expect(row.querySelectorAll('vc-copy-value').length).toBe(2);
  });

  it('does not save a component without a name', async () => {
    await render();
    await click(buttonIn(COMPONENTS, 'New'));

    await click(buttonIn(COMPONENTS, 'Save'));

    expect(create).not.toHaveBeenCalled();

    await type(field('componentName'), 'Valkey');
    await click(buttonIn(COMPONENTS, 'Save'));

    expect(create).toHaveBeenCalledTimes(1);
    expect((create.mock.calls[0][0] as ComponentRequest).name).toBe('Valkey');
  });
});
