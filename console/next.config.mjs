/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  reactStrictMode: false,
  experimental: {
    instrumentationHook: true,
    serverComponentsExternalPackages: ["@opentelemetry/instrumentation"],
  },
  env: {
    NEXT_PUBLIC_API_CLIENT_VERSION: "v0.0.0-dev",
  },
};
export default nextConfig;
