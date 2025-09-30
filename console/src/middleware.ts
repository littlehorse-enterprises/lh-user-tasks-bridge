import { getToken } from "next-auth/jwt";
import { NextResponse } from "next/server";
import { getRoles } from "./lib/utils";
import { auth } from "./auth";

export default auth(async (req) => {
  const secureCookie = req.nextUrl.protocol === "https:";
  const token = await getToken({
    req,
    secret: process.env.AUTH_SECRET,
    secureCookie,
  });
  const baseUrl = req.nextUrl.origin;
  const currentPath = req.nextUrl.pathname;
  if (!token || token.expiresAt < Math.floor(Date.now() / 1000)) {
    return NextResponse.redirect(
      `${baseUrl}/api/auth/signin?callbackUrl=${currentPath}`,
    );
  }

  // Check if token is valid
  try {
    const response = await fetch(
      `${process.env.AUTH_KEYCLOAK_ISSUER}/protocol/openid-connect/userinfo`,
      {
        headers: {
          Authorization: `Bearer ${token.accessToken}`,
        },
      },
    );
    if (!response.ok) {
      return NextResponse.redirect(
        `${baseUrl}/api/auth/signin?callbackUrl=${currentPath}`,
      );
    }
  } catch (error) {
    return NextResponse.redirect(
      `${baseUrl}/api/auth/signin?callbackUrl=${currentPath}`,
    );
  }

  // Redirect to tenant after login
  if (currentPath === "/" && token.decoded.allowed_tenant) {
    if (getRoles(token.decoded).includes("lh-user-tasks-admin"))
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
    !getRoles(token.decoded).includes("lh-user-tasks-admin")
  ) {
    return NextResponse.redirect(`${baseUrl}/${token.decoded.allowed_tenant}`);
  }

  return NextResponse.next();
});

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|images|favicon.ico).*)"],
};
