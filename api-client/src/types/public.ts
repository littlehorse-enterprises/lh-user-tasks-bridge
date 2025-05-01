// Enums
export enum IdentityProviderVendor {
  KEYCLOAK = "KEYCLOAK",
  AUTH0 = "AUTH0",
  OKTA = "OKTA",
  ZITADEL = "ZITADEL",
}

// Response DTOs
export interface IdentityProviderDTO {
  vendor: IdentityProviderVendor;
  labelName: string;
  issuer: string;
  clientId: string;
  authorities: string[];
}

export interface IdentityProviderListDTO {
  providers: IdentityProviderDTO[];
}

// Request Params
export interface GetIdentityProviderConfigParams {}
