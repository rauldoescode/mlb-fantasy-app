"use client";

import { useRef, useState } from "react";
import { Camera, Loader2 } from "lucide-react";

import { UserAvatar } from "@/components/user-avatar";
import { useAuth } from "@/lib/auth-context";
import { api, ApiError } from "@/lib/api";
import { cn } from "@/lib/utils";

const TARGET_SIZE = 256;

function resizeToSquareDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("Could not read that file"));
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error("Could not read that image"));
      img.onload = () => {
        const canvas = document.createElement("canvas");
        canvas.width = TARGET_SIZE;
        canvas.height = TARGET_SIZE;
        const ctx = canvas.getContext("2d");
        if (!ctx) {
          reject(new Error("Canvas not supported"));
          return;
        }
        const side = Math.min(img.width, img.height);
        const sx = (img.width - side) / 2;
        const sy = (img.height - side) / 2;
        ctx.drawImage(img, sx, sy, side, side, 0, 0, TARGET_SIZE, TARGET_SIZE);
        resolve(canvas.toDataURL("image/jpeg", 0.85));
      };
      img.src = reader.result as string;
    };
    reader.readAsDataURL(file);
  });
}

export function AvatarUpload({ className }: { className?: string }) {
  const { user, setUser } = useAuth();
  const inputRef = useRef<HTMLInputElement>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!user) return null;

  async function handleFile(file: File) {
    setError(null);
    setIsUploading(true);
    try {
      const dataUrl = await resizeToSquareDataUrl(file);
      const updated = await api.updateAvatar(dataUrl);
      setUser(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't upload that photo");
    } finally {
      setIsUploading(false);
    }
  }

  return (
    <div className={cn("flex flex-col items-center gap-3", className)}>
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        className="group relative"
        disabled={isUploading}
      >
        <UserAvatar name={user.displayName} avatarUrl={user.avatarUrl} size="xl" />
        <span className="absolute inset-0 flex items-center justify-center rounded-full bg-black/0 text-transparent transition-colors group-hover:bg-black/50 group-hover:text-white">
          {isUploading ? <Loader2 className="h-5 w-5 animate-spin" /> : <Camera className="h-5 w-5" />}
        </span>
      </button>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) void handleFile(file);
          e.target.value = "";
        }}
      />
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        className="text-xs font-semibold text-primary hover:underline"
        disabled={isUploading}
      >
        {isUploading ? "Uploading…" : "Change photo"}
      </button>
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  );
}
