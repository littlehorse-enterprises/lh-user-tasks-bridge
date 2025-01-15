import { getToken } from "next-auth/jwt";
import nextAuth from "next-auth/middleware";
import { NextResponse } from "next/server";

const withAuth = nextAuth(async (req) => {
  const token = await getToken({ req, secret: process.env.AUTH_SECRET });
  const baseUrl = req.nextUrl.origin;
  const currentPath = req.nextUrl.pathname;
  if (!token || token.expires_at < Date.now() / 1000) {
    return NextResponse.redirect(
      `${baseUrl}/api/auth/signin?callbackUrl=${currentPath}`,
    );
  }

  // Call keycloak to check if token is valid
  const keycloakResponse = await fetch(
    `${process.env.AUTH_KEYCLOAK_ISSUER}/protocol/openid-connect/userinfo`,
    {
      headers: {
        Authorization: `Bearer ${token.access_token}`,
      },
    },
  );

  if (!keycloakResponse.ok) {
    return NextResponse.redirect(
      `${baseUrl}/api/auth/signin?callbackUrl=${currentPath}`,
    );
  }

  // Redirect to tenant after login
  if (currentPath === "/" && token.decoded.allowed_tenant) {
    if (
      token.decoded.realm_access.roles.includes("lh-sso-workflow-bridge-admin")
    )
      return NextResponse.redirect(
        `${baseUrl}/${token.decoded.allowed_tenant}/admin`,
      );
    return NextResponse.redirect(`${baseUrl}/${token.decoded.allowed_tenant}`);
  }

  // Check if current path matches allowed tenant
  if (currentPath.split("/")[1] !== token.decoded.allowed_tenant) {
    return NextResponse.redirect(
      `${baseUrl}/${token.decoded.allowed_tenant}/${currentPath.split("/").slice(2).join("/")}`,
    );
  }

  // Check if current path is admin and user is not admin
  if (
    currentPath.includes("/admin") &&
    !token.decoded.realm_access.roles.includes("lh-sso-workflow-bridge-admin")
  ) {
    return NextResponse.redirect(`${baseUrl}/${token.decoded.allowed_tenant}`);
  }

  return NextResponse.next();
});

export default withAuth;

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|images|favicon.ico).*)"],
};
