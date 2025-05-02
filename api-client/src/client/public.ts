import {
  GetIdentityProviderConfigParams,
  IdentityProviderListDTO,
} from "../types";
import { objectToURLSearchParams } from "../utils";
import { LHUTBApiClient } from "../client";

export class PublicController {
  private client: LHUTBApiClient;

  constructor(client: LHUTBApiClient) {
    this.client = client;
  }

  /**
   * Gets the public configuration of the IdP(s) used by a specific tenant
   */
  async getIdentityProviderConfig(
    params: GetIdentityProviderConfigParams,
  ): Promise<IdentityProviderListDTO> {
    const queryParams = objectToURLSearchParams(params);
    return this.client.fetch<IdentityProviderListDTO>(
      `/config?${queryParams.toString()}`,
    );
  }
}
