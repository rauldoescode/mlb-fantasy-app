import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

import { AuthProvider } from "@/lib/auth-context";
import { LeagueProvider } from "@/lib/league-context";
import { AppShell } from "@/components/app-shell";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Diamond League",
  description: "Private head-to-head fantasy baseball league",
};

export const viewport = {
  themeColor: "#0f1210",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`dark ${inter.variable}`}>
      <body className="font-sans antialiased">
        <AuthProvider>
          <LeagueProvider>
            <AppShell>{children}</AppShell>
          </LeagueProvider>
        </AuthProvider>
      </body>
    </html>
  );
}
