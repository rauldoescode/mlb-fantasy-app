import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "MLB Fantasy",
  description: "Private head-to-head fantasy baseball league",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
