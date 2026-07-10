import type { MatchupResponse } from "./api";

/** Picks the matchup most relevant to "right now": live > next upcoming > most recent final. */
export function pickCurrentMatchup(
  matchups: MatchupResponse[],
  userId: string
): MatchupResponse | null {
  const mine = matchups.filter((m) => m.userOneId === userId || m.userTwoId === userId);
  if (mine.length === 0) return null;

  const inProgress = mine.filter((m) => m.status === "IN_PROGRESS");
  if (inProgress.length > 0) {
    return inProgress.sort((a, b) => a.weekNumber - b.weekNumber)[0];
  }

  const scheduled = mine.filter((m) => m.status === "SCHEDULED");
  if (scheduled.length > 0) {
    return scheduled.sort((a, b) => a.weekNumber - b.weekNumber)[0];
  }

  return mine.sort((a, b) => b.weekNumber - a.weekNumber)[0];
}

export function opponentIdFor(matchup: MatchupResponse, userId: string) {
  return matchup.userOneId === userId ? matchup.userTwoId : matchup.userOneId;
}

export function scoresFor(matchup: MatchupResponse, userId: string) {
  const isUserOne = matchup.userOneId === userId;
  return {
    myScore: isUserOne ? matchup.userOneScore : matchup.userTwoScore,
    opponentScore: isUserOne ? matchup.userTwoScore : matchup.userOneScore,
  };
}
