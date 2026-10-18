import type { NextConfig } from "next";

const upstream = (process.env.ANALYSIS_API_ORIGIN || "http://127.0.0.1:8000").replace(/\/$/, "");

const nextConfig: NextConfig = {
  poweredByHeader: false,
  async rewrites() {
    return [{ source: "/analysis-api/:path*", destination: upstream + "/api/:path*" }];
  },
};

export default nextConfig;
