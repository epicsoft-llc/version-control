import {provideZoneChangeDetection} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it} from 'vitest';
import {isDefined} from '../../libs/utils/vc.utils';
import {VersionComponent} from './version.component';

describe('VersionComponent', () => {

  let fixture: ComponentFixture<VersionComponent>;

  const span = (): HTMLSpanElement => fixture.nativeElement.querySelector('span') as HTMLSpanElement;

  function show(value: string | undefined, fallback?: string): void {
    fixture.componentRef.setInput('value', value);
    if (isDefined(fallback)) {
      fixture.componentRef.setInput('fallback', fallback);
    }
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VersionComponent],
      providers: [provideZoneChangeDetection({eventCoalescing: true})]
    }).compileComponents();
    fixture = TestBed.createComponent(VersionComponent);
  });

  it('shows a package version short and keeps the recorded one on hover', () => {
    show('5:29.9.2-1~ubuntu.24.04~noble');

    expect(span().textContent?.trim()).toBe('29.9.2-1');
    expect(span().getAttribute('title')).toBe('5:29.9.2-1~ubuntu.24.04~noble');
  });

  it('adds no hover text where nothing was left out', () => {
    show('17.6');

    expect(span().textContent?.trim()).toBe('17.6');
    expect(span().hasAttribute('title')).toBe(false);
  });

  it('shows the fallback for a missing version', () => {
    show(undefined, '–');

    expect(span().textContent?.trim()).toBe('–');
    expect(span().hasAttribute('title')).toBe(false);
  });
});
