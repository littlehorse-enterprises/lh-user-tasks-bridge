import fs from "fs";
import { KeycloakProfile } from "next-auth/providers/keycloak";
import { OAuthConfig, OAuthUserConfig } from "next-auth/providers/oauth";
import yaml from "yaml";

export const DynamicKeycloakProvider: DynamicProvider<KeycloakProfile> = (
  options
) => {
  return {
    id: `keycloak-${options.clientId}`,
    name: "Keycloak",
    wellKnown: `${options.issuer}/.well-known/openid-configuration`,
    type: "oauth",
    authorization: { params: { scope: "openid email profile" } },
    checks: ["pkce", "state"],
    idToken: true,
    profile(profile) {
      return {
        id: profile.sub,
        name: profile.name ?? profile.preferred_username,
        email: profile.email,
        image: profile.picture,
      };
    },
    style: { logo: "/keycloak.svg", bg: "#fff", text: "#000" },
    options: { ...options, clientSecret: "" },
  };
};

type ProviderConfig = {
  type: "keycloak";
  clientId: string;
  issuer: string;
};

type TenantConfig = {
  name: string;
  providers: ProviderConfig[];
};

export const getProvidersConfig = (type: ProviderConfig["type"]) => {
  const groups = groupByProviderType();

  return groups[type];
};

const groupByProviderType = (): Record<string, ProviderConfig[]> => {
  const configFile = process.env.PROVIDERS_CONFIG_FILE || "./providers.yaml";
  const config = fs.readFileSync(configFile, "utf8");
  const { tenants }: { tenants: TenantConfig[] } = yaml.parse(config);

  return tenants.reduce<Record<string, ProviderConfig[]>>((acc, tenants) => {
    for (const provider of tenants.providers) {
      acc[provider.type] = [...(acc[provider.type] ?? []), provider];
    }
    return acc;
  }, {});
};

type DynamicProvider<T> = (
  options: Omit<OAuthUserConfig<T>, "clientSecret">
) => OAuthConfig<T>;

export const ProviderMapping: Record<ProviderConfig["type"], DynamicProvider<any>> = {
  keycloak: DynamicKeycloakProvider,
};

