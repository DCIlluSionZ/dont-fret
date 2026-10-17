import type { Metadata } from "next";
import type { ReactNode } from "react";

export const metadata: Metadata = {
  title: "Don't Fret",
  description: "Turn songs into playable guitar chord sheets.",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en">
      <body style={{ margin: 0, background: "#090b10", color: "#f6f1e8", fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif" }}>
        {children}
      </body>
    </html>
  );
}
