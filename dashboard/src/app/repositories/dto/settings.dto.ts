import {DebianVersionDisplay} from './enums.dto';

/** What the dashboard has to know about this installation before it shows a version. */
export class SettingsDto {

  public debianVersions!: DebianVersionDisplay;

  constructor(init?: Partial<SettingsDto>) {
    Object.assign(this, init);
  }
}
