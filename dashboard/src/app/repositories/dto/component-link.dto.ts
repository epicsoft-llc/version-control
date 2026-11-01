import {LinkKind, LinkOrigin} from './enums.dto';
import {CheckState} from './update-check.dto';

export class ComponentLinkDto {

  public url!: string;
  public kind!: LinkKind;
  public origin!: LinkOrigin;

  constructor(init?: Partial<ComponentLinkDto>) {
    Object.assign(this, init);
  }
}

export class ComponentLinkRequest {

  public url!: string;

  constructor(init?: Partial<ComponentLinkRequest>) {
    Object.assign(this, init);
  }
}

export class LinkSearchDto {

  public componentId!: string;
  public links: ComponentLinkDto[] = [];
  public checkedAt?: string;
  public checkState?: CheckState;
  public errorMessage?: string;

  constructor(init?: Partial<LinkSearchDto>) {
    Object.assign(this, init);
  }
}
