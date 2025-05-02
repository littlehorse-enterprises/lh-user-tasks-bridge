import { LHUTBApiClient } from "../client";

/**
 * Methods for initializing tenant-OIDC integration
 */
export class InitController {
  private client: LHUTBApiClient;

  constructor(client: LHUTBApiClient) {
    this.client = client;
  }

  /**
   * Checks that the integration between your Identity Provider and LittleHorse Kernel is valid
   * @returns Promise that resolves when successful or rejects on error
   */
  checkConnection(): Promise<void> {
    return this.client.fetch<void>(`/init`);
  }
}
