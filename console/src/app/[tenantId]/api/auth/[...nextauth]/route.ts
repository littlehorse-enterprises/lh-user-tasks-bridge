'use server';
import type { NextApiRequest, NextApiResponse } from "next";
import NextAuth from "next-auth/next";
import { authOptions } from "./authOptions";
import { DynamicKeycloakProvider, getProvidersConfig } from "./DynamicKeycloakProvider";

async function auth(req: NextApiRequest, res: NextApiResponse) {
  const providers = getProvidersConfig("keycloak").map((provider) => {
    return DynamicKeycloakProvider({
      clientId: provider.clientId,
      issuer: provider.issuer,
    });
  });

  return await NextAuth(req, res, { ...authOptions, providers });
}

export { auth as GET, auth as POST };
