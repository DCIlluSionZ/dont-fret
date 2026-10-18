import type { MetadataRoute } from "next";
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Don't Fret",
    short_name: "Don't Fret",
    description: "Turn audio into playable guitar chord sheets",
    start_url: "/analyze",
    scope: "/",
    display: "standalone",
    background_color: "#090b10",
    theme_color: "#b9ff66",
    icons: [{ src: "/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" }],
  };
}
