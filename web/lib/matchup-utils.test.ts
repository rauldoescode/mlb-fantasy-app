import { describe, expect, it } from "vitest";

import type { MatchupResponse } from "./api";
import { opponentIdFor, pickCurrentMatchup, scoresFor } from "./matchup-utils";

function matchup(partial: Partial<MatchupResponse> & Pick<MatchupResponse, "id" | "weekNumber" | "status">): MatchupResponse {
  return {
    leagueId: "l1",
    userOneId: "u1",
    userTwoId: "u2",
    userOneScore: 1,
    userTwoScore: 2,
    winnerId: null,
    ...partial,
  };
}

describe("pickCurrentMatchup", () => {
  it("prefers in-progress over scheduled and final", () => {
    const picked = pickCurrentMatchup(
      [
        matchup({ id: "a", weekNumber: 1, status: "FINAL" }),
        matchup({ id: "b", weekNumber: 3, status: "SCHEDULED" }),
        matchup({ id: "c", weekNumber: 2, status: "IN_PROGRESS" }),
      ],
      "u1"
    );
    expect(picked?.id).toBe("c");
  });

  it("returns null when the user has no matchups", () => {
    expect(pickCurrentMatchup([matchup({ id: "a", weekNumber: 1, status: "FINAL", userOneId: "x", userTwoId: "y" })], "u1")).toBeNull();
  });
});

describe("opponentIdFor / scoresFor", () => {
  it("resolves opponent and scores from either side", () => {
    const m = matchup({
      id: "a",
      weekNumber: 1,
      status: "IN_PROGRESS",
      userOneScore: 11,
      userTwoScore: 9,
    });
    expect(opponentIdFor(m, "u1")).toBe("u2");
    expect(scoresFor(m, "u2")).toEqual({ myScore: 9, opponentScore: 11 });
  });
});
