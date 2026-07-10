"use client";

import { useState } from "react";
import { Loader2, Globe, Lock } from "lucide-react";

import { api, ApiError, type LeagueVisibility } from "@/lib/api";
import { useLeague } from "@/lib/league-context";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

const MAX_MEMBER_OPTIONS = [2, 4, 6, 8, 10, 12];
const CURRENT_YEAR = new Date().getFullYear();

export function CreateLeagueDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { selectLeagueAfterAction } = useLeague();
  const [name, setName] = useState("");
  const [teamName, setTeamName] = useState("");
  const [seasonYear, setSeasonYear] = useState(CURRENT_YEAR);
  const [visibility, setVisibility] = useState<LeagueVisibility>("PRIVATE");
  const [maxMembers, setMaxMembers] = useState(10);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [salaryCapM, setSalaryCapM] = useState<string>("");
  const [rosterSize, setRosterSize] = useState<string>("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function reset() {
    setName("");
    setTeamName("");
    setSeasonYear(CURRENT_YEAR);
    setVisibility("PRIVATE");
    setMaxMembers(10);
    setShowAdvanced(false);
    setSalaryCapM("");
    setRosterSize("");
    setError(null);
  }

  function handleOpenChange(next: boolean) {
    if (!next) reset();
    onOpenChange(next);
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      const league = await api.createLeague({
        name: name.trim(),
        seasonYear,
        teamName: teamName.trim(),
        visibility,
        maxMembers,
        salaryCap: salaryCapM ? Number(salaryCapM) * 1_000_000 : undefined,
        rosterSize: rosterSize ? Number(rosterSize) : undefined,
      });
      selectLeagueAfterAction(league);
      handleOpenChange(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't create the league");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Create a league</DialogTitle>
          <DialogDescription>Set up a new league and become its commissioner.</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="space-y-1.5">
            <Label htmlFor="league-name">League name</Label>
            <Input
              id="league-name"
              required
              maxLength={70}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Sunday Sluggers"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="team-name">Your team name</Label>
              <Input
                id="team-name"
                required
                maxLength={70}
                value={teamName}
                onChange={(e) => setTeamName(e.target.value)}
                placeholder="The Dingers"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="season-year">Season</Label>
              <Input
                id="season-year"
                type="number"
                required
                min={2000}
                max={2100}
                value={seasonYear}
                onChange={(e) => setSeasonYear(Number(e.target.value))}
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>Visibility</Label>
            <div className="grid grid-cols-2 gap-2">
              <VisibilityButton
                active={visibility === "PRIVATE"}
                onClick={() => setVisibility("PRIVATE")}
                icon={<Lock className="h-4 w-4" />}
                title="Private"
                subtitle="Invite by code"
              />
              <VisibilityButton
                active={visibility === "PUBLIC"}
                onClick={() => setVisibility("PUBLIC")}
                icon={<Globe className="h-4 w-4" />}
                title="Public"
                subtitle="Anyone can join"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="max-members">Max members</Label>
            <select
              id="max-members"
              value={maxMembers}
              onChange={(e) => setMaxMembers(Number(e.target.value))}
              className="flex h-10 w-full rounded-lg border border-input bg-secondary/40 px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {MAX_MEMBER_OPTIONS.map((n) => (
                <option key={n} value={n}>
                  {n} teams
                </option>
              ))}
            </select>
          </div>

          <button
            type="button"
            onClick={() => setShowAdvanced((s) => !s)}
            className="self-start text-xs font-semibold text-primary hover:underline"
          >
            {showAdvanced ? "Hide advanced options" : "Advanced options"}
          </button>

          {showAdvanced ? (
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="salary-cap">Salary cap ($M)</Label>
                <Input
                  id="salary-cap"
                  type="number"
                  min={0}
                  step={1}
                  value={salaryCapM}
                  onChange={(e) => setSalaryCapM(e.target.value)}
                  placeholder="50"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="roster-size">Roster size</Label>
                <Input
                  id="roster-size"
                  type="number"
                  min={1}
                  max={40}
                  value={rosterSize}
                  onChange={(e) => setRosterSize(e.target.value)}
                  placeholder="10"
                />
              </div>
            </div>
          ) : null}

          {error ? <p className="text-sm text-destructive">{error}</p> : null}

          <Button type="submit" size="lg" disabled={isSubmitting}>
            {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Create league
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function VisibilityButton({
  active,
  onClick,
  icon,
  title,
  subtitle,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  title: string;
  subtitle: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex flex-col items-start gap-1 rounded-lg border border-border bg-secondary/30 p-3 text-left transition-colors hover:bg-secondary/60",
        active && "border-primary bg-primary/10 hover:bg-primary/10"
      )}
    >
      <span className={cn("flex items-center gap-1.5 font-semibold", active && "text-primary")}>
        {icon}
        {title}
      </span>
      <span className="text-xs text-muted-foreground">{subtitle}</span>
    </button>
  );
}
