"use client";

import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";

import { api, ApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const CATEGORY_LABELS: Record<string, string> = {
  hits: "Hits",
  home_runs: "Home runs",
  rbi: "RBI",
  stolen_bases: "Stolen bases",
  strikeouts_batting: "Strikeouts (batting)",
  innings_pitched: "Innings pitched",
  earned_runs: "Earned runs",
  pitching_wins: "Pitching wins",
  strikeouts_pitching: "Strikeouts (pitching)",
};

const CATEGORY_ORDER = Object.keys(CATEGORY_LABELS);

function valuesEqual(a: Record<string, number>, b: Record<string, number>): boolean {
  return CATEGORY_ORDER.every((key) => Number(a[key] ?? 0) === Number(b[key] ?? 0));
}

export function ScoringRulesForm({ leagueId }: { leagueId: string }) {
  const [values, setValues] = useState<Record<string, string> | null>(null);
  const [saved, setSaved] = useState<Record<string, number> | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setValues(null);
    setSaved(null);
    setLoadError(null);
    setError(null);
    api
      .getScoringRules(leagueId)
      .then((res) => {
        if (cancelled) return;
        const next: Record<string, string> = {};
        const snapshot: Record<string, number> = {};
        for (const key of CATEGORY_ORDER) {
          const n = res.pointValues[key] ?? 0;
          snapshot[key] = n;
          next[key] = String(n);
        }
        setSaved(snapshot);
        setValues(next);
      })
      .catch((err) => {
        if (!cancelled) {
          setLoadError(err instanceof ApiError ? err.message : "Couldn't load scoring rules");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [leagueId]);

  if (loadError) {
    return <p className="text-sm text-destructive">{loadError}</p>;
  }

  if (!values || !saved) {
    return (
      <div className="flex items-center gap-2 border-t border-border pt-5 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" />
        Loading scoring rules…
      </div>
    );
  }

  const parsed: Record<string, number> = {};
  let parseError: string | null = null;
  for (const key of CATEGORY_ORDER) {
    const n = Number(values[key]);
    if (!Number.isFinite(n)) {
      parseError = `Invalid value for ${CATEGORY_LABELS[key]}`;
      break;
    }
    parsed[key] = n;
  }

  const dirty = !parseError && !valuesEqual(parsed, saved);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (parseError || !dirty) {
      setError(parseError);
      return;
    }
    setError(null);
    setSaving(true);
    try {
      const updated = await api.updateScoringRules(leagueId, parsed);
      const snapshot: Record<string, number> = {};
      const next: Record<string, string> = {};
      for (const key of CATEGORY_ORDER) {
        const n = updated.pointValues[key] ?? 0;
        snapshot[key] = n;
        next[key] = String(n);
      }
      setSaved(snapshot);
      setValues(next);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't update scoring rules");
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4 border-t border-border pt-5">
      <div>
        <p className="text-sm font-semibold">Scoring rules</p>
        <p className="text-xs text-muted-foreground">
          Points per unit. Changes affect in-progress weeks until a matchup is finalized.
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {CATEGORY_ORDER.map((key) => (
          <div key={key} className="space-y-1.5">
            <Label htmlFor={`scoring-${key}`}>{CATEGORY_LABELS[key]}</Label>
            <Input
              id={`scoring-${key}`}
              type="number"
              step="0.5"
              value={values[key]}
              onChange={(e) => setValues((prev) => (prev ? { ...prev, [key]: e.target.value } : prev))}
            />
          </div>
        ))}
      </div>

      {error || parseError ? (
        <p className="text-sm text-destructive">{error ?? parseError}</p>
      ) : null}

      <Button type="submit" disabled={!dirty || saving || !!parseError}>
        {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
        Save scoring rules
      </Button>
    </form>
  );
}
