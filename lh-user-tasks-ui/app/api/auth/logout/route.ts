import { getServerSession } from "next-auth";
import { authOptions } from "@/utils/authOptions";
import { getIdToken } from "@/utils/SessionTokens";

export async function GET(): Promise<Response> {
  const session = await getServerSession(authOptions);

  if (session) {
    const idToken: string | null = await getIdToken();

    if (!idToken) {
      return new Response("ID Token not found", { status: 400 });
    }

    const url = `${process.env.KEYCLOAK_HOST}/realms/${
      process.env.KEYCLOAK_REALM
    }/protocol/openid-connect/logout?id_token_hint=${idToken}&post_logout_redirect_uri=${encodeURIComponent(
      process.env.NEXTAUTH_URL || ""
    )}`;

    await fetch(url, { method: "GET" });
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  }
  return new Response(null, { status: 200 });
}
