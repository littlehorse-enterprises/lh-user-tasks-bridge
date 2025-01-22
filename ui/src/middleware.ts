import { getToken } from "next-auth/jwt";
import nextAuth from "next-auth/middleware";
import { NextResponse } from "next/server";
import { getRoles } from "./lib/utils";

const withAuth = nextAuth(async (req) => {
  const token = await getToken({ req, secret: process.env.AUTH_SECRET });
  const baseUrl = req.nextUrl.origin;
  const currentPath = req.nextUrl.pathname;
  if (!token || token.expires_at < Date.now() / 1000) {
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

export default withAuth;

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|images|favicon.ico).*)"],
};
