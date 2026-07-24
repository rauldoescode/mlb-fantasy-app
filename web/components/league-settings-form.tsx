"use client";

import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";

import {
  api,
  ApiError,
  type LeagueResponse,
  type UpdateLeagueSettingsRequest,
} from "@/lib/api";
import { useLeague } from "@/lib/league-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const MAX_MEMBER_OPTIONS = [2, 4, 6, 8, 10, 12];

function capToMillions(salaryCap: number): string {
  return String(Math.round(salaryCap / 1_000_000));
}

export function LeagueSettingsForm({ league }: { league: LeagueResponse }) {
  const { selectLeagueAfterAction } = useLeague();
  const [name, setName] = useState(league.name);
  const [salaryCapM, setSalaryCapM] = useState(capToMillions(league.salaryCap));
  const [rosterSize, setRosterSize] = useState(String(league.rosterSize));
  const [maxMembers, setMaxMembers] = useState(league.maxMembers);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setName(league.name);
    setSalaryCapM(capToMillions(league.salaryCap));
    setRosterSize(String(league.rosterSize));
    setMaxMembers(league.maxMembers);
    setError(null);
  }, [league.id, league.name, league.salaryCap, league.rosterSize, league.maxMembers]);

  const parsedCapM = Number(salaryCapM);
  const parsedRoster = Number(rosterSize);
  const nextCap = Number.isFinite(parsedCapM) ? parsedCapM * 1_000_000 : NaN;
  const nextRoster = Number.isFinite(parsedRoster) ? parsedRoster : NaN;

  const dirty =
    name.trim() !== league.name ||
    nextCap !== league.salaryCap ||
    nextRoster !== league.rosterSize ||
    maxMembers !== league.maxMembers;

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    const trimmedName = name.trim();
    if (!trimmedName) {
      setError("League name cannot be blank");
      return;
    }
    if (!Number.isFinite(parsedCapM) || parsedCapM <= 0) {
      setError("Salary cap must be greater than 0");
      return;
    }
    if (!Number.isInteger(parsedRoster) || parsedRoster < 5 || parsedRoster > 15) {
      setError("Roster size must be a whole number between 5 and 15");
      return;
    }
    if (maxMembers < league.memberCount) {
      setError(
        `Cannot set max members below current membership (${league.memberCount})`
      );
      return;
    }

    const body: UpdateLeagueSettingsRequest = {};
    if (trimmedName !== league.name) body.name = trimmedName;
    if (nextCap !== league.salaryCap) body.salaryCap = nextCap;
    if (nextRoster !== league.rosterSize) body.rosterSize = nextRoster;
    if (maxMembers !== league.maxMembers) body.maxMembers = maxMembers;

    if (Object.keys(body).length === 0) return;

    const loweringMembers = body.maxMembers != null && body.maxMembers < league.maxMembers;
    const loweringRoster = body.rosterSize != null && body.rosterSize < league.rosterSize;
    if (
      (loweringMembers || loweringRoster) &&
      !window.confirm(
        "Lowering max members or roster size may be blocked if teams already exceed the new limits. Continue?"
      )
    ) {
      return;
    }

    setSaving(true);
    try {
      const updated = await api.updateLeagueSettings(league.id, body);
      selectLeagueAfterAction(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't update league settings");
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4 border-t border-border pt-5">
      <div>
        <p className="text-sm font-semibold">League settings</p>
        <p className="text-xs text-muted-foreground">
          Changes apply immediately to roster adds and new joins.
        </p>
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="settings-league-name">League name</Label>
        <Input
          id="settings-league-name"
          required
          maxLength={60}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-1.5">
          <Label htmlFor="settings-salary-cap">Salary cap ($M)</Label>
          <Input
            id="settings-salary-cap"
            type="number"
            required
            min={1}
            step={1}
            value={salaryCapM}
            onChange={(e) => setSalaryCapM(e.target.value)}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="settings-roster-size">Roster size</Label>
          <Input
            id="settings-roster-size"
            type="number"
            required
            min={5}
            max={15}
            value={rosterSize}
            onChange={(e) => setRosterSize(e.target.value)}
          />
        </div>
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="settings-max-members">Max members</Label>
        <select
          id="settings-max-members"
          value={maxMembers}
          onChange={(e) => setMaxMembers(Number(e.target.value))}
          className="flex h-10 w-full rounded-lg border border-input bg-secondary/40 px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {MAX_MEMBER_OPTIONS.map((n) => (
            <option key={n} value={n} disabled={n < league.memberCount}>
              {n} teams
              {n < league.memberCount ? " (below current membership)" : ""}
            </option>
          ))}
        </select>
      </div>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      <Button type="submit" disabled={!dirty || saving}>
        {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
        Save settings
      </Button>
    </form>
  );
}
