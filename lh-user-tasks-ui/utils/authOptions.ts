import { NextAuthOptions } from "next-auth";
import KeycloakProvider from "next-auth/providers/keycloak";
import { jwtDecode } from "jwt-decode";


export const authOptions: NextAuthOptions = {
    providers: [
      KeycloakProvider({
        clientId: `${process.env.KEYCLOAK_CLIENT_ID}`,
        clientSecret: `${process.env.KEYCLOAK_CLIENT_SECRET}`,
        issuer: `${process.env.KEYCLOAK_HOST}/realms/${process.env.KEYCLOAK_REALM}`,
      }),
    ],
  
    callbacks: {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      async jwt({ token, account }: any) {
        const nowTimeStamp = Math.floor(Date.now() / 1000);
  
        if (account) {
          token.decoded = jwtDecode(account.access_token);
          token.access_token = account.access_token;
          token.id_token = account.id_token;
          token.expires_at = account.expires_at;
          token.refresh_token = account.refresh_token;
          return token;
        }
        if (nowTimeStamp < token.expires_at) {
          return token;
        }
      },
         // eslint-disable-next-line @typescript-eslint/no-explicit-any
      async session({ session, token }: any) {
        session.access_token = token.access_token;
        session.id_token = token.id_token;
        session.roles = token.decoded.realm_access.roles;
        session.error = token.error;
        return session;
      },
    },
  };