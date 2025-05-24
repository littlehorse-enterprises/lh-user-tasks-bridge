import { execSync } from "child_process";

// Get version from git tag or commit
function getVersion() {
  try {
    // Try to get the latest git tag
    const tag = execSync("git describe --tags --abbrev=0", {
      encoding: "utf8",
    }).trim();
    return tag;
  } catch {
    try {
      // Fallback to short commit hash
      const commit = execSync("git rev-parse --short HEAD", {
        encoding: "utf8",
      }).trim();
      return `dev-${commit}`;
    } catch {
      // Final fallback
      return "unknown";
    }
  }
}

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  reactStrictMode: false,
  experimental: {
    instrumentationHook: true,
    serverComponentsExternalPackages: ["@opentelemetry/instrumentation"],
  },
  env: {
    API_CLIENT_VERSION: getVersion(),
  },
};
export default nextConfig;
