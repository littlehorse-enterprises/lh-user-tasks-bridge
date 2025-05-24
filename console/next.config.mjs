import { execSync } from "child_process";

// Get version from git tag or commit
function getVersion() {
  // In GitHub Actions, use GITHUB_REF if available for tag builds
  const githubRef = process.env.GITHUB_REF;
  if (githubRef && githubRef.startsWith("refs/tags/")) {
    return githubRef.replace("refs/tags/", "");
    // "v1.0.0"
  }

  // Use GITHUB_SHA for commit-based versions in GitHub Actions
  const githubSha = process.env.GITHUB_SHA;
  if (githubSha) {
    return `dev-${githubSha.substring(0, 7)}`;
    // "dev-78fda87"
  }

  try {
    // Try to get the latest git tag
    const tag = execSync("git describe --tags --abbrev=0", {
      encoding: "utf8",
    }).trim();
    return tag;
    // "v1.0.0"
  } catch {
    try {
      // Fallback to short commit hash
      const commit = execSync("git rev-parse --short HEAD", {
        encoding: "utf8",
      }).trim();
      return `dev-${commit}`;
      // "dev-78fda87"
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
