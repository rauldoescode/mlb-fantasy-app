import type {
  MatchupDetailResponse,
  MatchupLineupSide,
  ScoreBreakdown,
} from "./api";

export type OrientedMatchupSides = {
  leftLineup: MatchupLineupSide;
  rightLineup: MatchupLineupSide;
  leftBreakdown: ScoreBreakdown;
  rightBreakdown: ScoreBreakdown;
  leftUserId: string;
  rightUserId: string;
  leftLabel: string;
  rightLabel: string;
  leftEditable: boolean;
  rightEditable: boolean;
};

/** Empty lineup used when the API omits a side (older server / partial payload). */
export function emptySide(userId: string | null | undefined): MatchupLineupSide {
  return {
    userId: userId ?? "",
    teamName: "",
    starters: [],
    bench: [],
    totalPoints: 0,
  };
}

function emptyBreakdown(userId: string): ScoreBreakdown {
  return { userId, totalPoints: 0, categoryPoints: {} };
}

/** Put the viewer's team on the left when they are a participant. */
export function orientSides(
  detail: MatchupDetailResponse,
  userId: string
): OrientedMatchupSides {
  const youAreOne = detail.matchup.userOneId === userId;
  const youAreTwo = detail.matchup.userTwoId === userId;
  const swap = youAreTwo;
  const isParticipant = youAreOne || youAreTwo;

  const oneSide = detail.userOneLineup ?? emptySide(detail.matchup.userOneId);
  const twoSide = detail.userTwoLineup ?? emptySide(detail.matchup.userTwoId);

  const leftLineup = swap ? twoSide : oneSide;
  const rightLineup = swap ? oneSide : twoSide;
  const leftBreakdown =
    (swap ? detail.userTwoBreakdown : detail.userOneBreakdown) ??
    emptyBreakdown(swap ? detail.matchup.userTwoId : detail.matchup.userOneId);
  const rightBreakdown =
    (swap ? detail.userOneBreakdown : detail.userTwoBreakdown) ??
    emptyBreakdown(swap ? detail.matchup.userOneId : detail.matchup.userTwoId);
  const leftUserId = swap ? detail.matchup.userTwoId : detail.matchup.userOneId;
  const rightUserId = swap ? detail.matchup.userOneId : detail.matchup.userTwoId;

  const leftEditable =
    Boolean(detail.lineupEditable) && isParticipant && leftUserId === userId;
  const rightEditable =
    Boolean(detail.lineupEditable) && isParticipant && rightUserId === userId;

  const leftLabel =
    isParticipant && leftUserId === userId
      ? "You"
      : leftLineup.teamName?.trim() || "Opponent";
  const rightLabel =
    isParticipant && rightUserId === userId
      ? "You"
      : rightLineup.teamName?.trim() || "Opponent";

  return {
    leftLineup: { ...leftLineup, teamName: leftLabel },
    rightLineup: { ...rightLineup, teamName: rightLabel },
    leftBreakdown,
    rightBreakdown,
    leftUserId,
    rightUserId,
    leftLabel,
    rightLabel,
    leftEditable,
    rightEditable,
  };
}
