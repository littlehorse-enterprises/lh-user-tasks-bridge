/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  reactStrictMode: false,
  experimental: {
    instrumentationHook: true,
    serverComponentsExternalPackages: ["@opentelemetry/instrumentation"],
  },
};
export default nextConfig;
