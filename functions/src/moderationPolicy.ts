// 신고 정책과 판단 규칙 (Firebase 없이 테스트할 수 있게 따로 둡니다).
// ── 신고 정책 ────────────────────────────────────────────────
// 숫자는 이곳만 바꾸면 됩니다. 같은 사람이 여러 번 신고해도 한 명으로 셉니다.

export type TargetType = "entry" | "comment" | "user";
export type ReasonCode = "not_real_photo" | "wrong_info" | "inappropriate_photo" | "abuse" | "spam" | "other";

export const REASON_CODES: ReasonCode[] = ["not_real_photo", "wrong_info", "inappropriate_photo", "abuse", "spam", "other"];

/** 대상마다 고를 수 있는 사유 (앱의 ReportReason 과 같게) */
export const TARGET_REASONS: Record<TargetType, ReasonCode[]> = {
  entry: ["not_real_photo", "wrong_info", "inappropriate_photo", "abuse", "spam", "other"],
  comment: ["abuse", "spam", "other"],
  user: ["inappropriate_photo", "abuse", "spam", "other"],
};

/** 경고(스트라이크)가 쌓이는 사유: 부적절한 사진, 욕설/비하, 상업적 광고 */
export const STRIKE_REASONS: ReadonlySet<ReasonCode> = new Set(["inappropriate_photo", "abuse", "spam"]);

export const POLICY = {
  /** '식물 정보가 일치하지 않음' 신고가 이만큼 쌓이면 정보를 고칠 때까지 비공개 */
  wrongInfoReporters: 2,
  /** 부적절한 사진·욕설·광고 신고가 이만큼 쌓이면 숨기고(댓글은 삭제) 주인에게 경고 1회 */
  violationReporters: 3,
  /** 그 밖의 신고(실제 사진 아님·기타 포함)가 이만큼 쌓이면 숨김 (경고 없음) */
  hideReporters: 3,
  /** 이 기간 안의 경고만 셉니다 */
  strikeWindowDays: 90,
  /** 경고가 이만큼 모이면 활동 정지 */
  strikesPerSuspension: 3,
  /** 정지 기간: 첫 번째 3일, 두 번째 7일, 그 뒤로는 30일 */
  suspensionDays: [3, 7, 30],
};

export interface ReportInfo {
  reporterId: string;
  reasonCode: ReasonCode;
  createdAtMillis: number;
}

export type ModerationAction = "none" | "needs_correction" | "hide";

/**
 * 한 대상에 쌓인 신고로 할 일을 정합니다 (순수 함수, 테스트 가능).
 * - 주인 본인의 신고는 세지 않습니다.
 * - '정보 일치하지 않음' 은 마지막으로 정보를 고친 뒤([correctedAtMillis])의 신고만 셉니다.
 */
export function decideAction(
  targetType: TargetType,
  reports: ReportInfo[],
  ownerId: string,
  correctedAtMillis = 0,
): { action: ModerationAction; strike: boolean; reason: ReasonCode | null } {
  const valid = reports.filter((r) => r.reporterId !== ownerId && TARGET_REASONS[targetType].includes(r.reasonCode));
  const reporters = (pred: (r: ReportInfo) => boolean) => new Set(valid.filter(pred).map((r) => r.reporterId)).size;

  const violations = reporters((r) => STRIKE_REASONS.has(r.reasonCode));
  if (violations >= POLICY.violationReporters) {
    const top = mostReported(valid.filter((r) => STRIKE_REASONS.has(r.reasonCode)));
    return { action: targetType === "user" ? "none" : "hide", strike: true, reason: top };
  }
  const hideVotes = reporters((r) => r.reasonCode !== "wrong_info");
  if (targetType !== "user" && hideVotes >= POLICY.hideReporters) {
    return { action: "hide", strike: false, reason: mostReported(valid.filter((r) => r.reasonCode !== "wrong_info")) };
  }
  if (targetType === "entry") {
    const wrongInfo = reporters((r) => r.reasonCode === "wrong_info" && r.createdAtMillis > correctedAtMillis);
    if (wrongInfo >= POLICY.wrongInfoReporters) return { action: "needs_correction", strike: false, reason: "wrong_info" };
  }
  return { action: "none", strike: false, reason: null };
}

function mostReported(reports: ReportInfo[]): ReasonCode | null {
  const byReason = new Map<ReasonCode, Set<string>>();
  for (const r of reports) byReason.set(r.reasonCode, (byReason.get(r.reasonCode) ?? new Set()).add(r.reporterId));
  let best: ReasonCode | null = null;
  let bestCount = 0;
  for (const code of REASON_CODES) {
    const count = byReason.get(code)?.size ?? 0;
    if (count > bestCount) [best, bestCount] = [code, count];
  }
  return best;
}

/** [suspensionNumber] 번째 정지의 기간(일) */
export function suspensionDays(suspensionNumber: number): number {
  const days = POLICY.suspensionDays;
  return days[Math.min(Math.max(suspensionNumber, 1), days.length) - 1];
}

export const REASON_LABELS: Record<ReasonCode, string> = {
  not_real_photo: "실제 식물을 찍은 사진이 아님",
  wrong_info: "식물 정보가 일치하지 않음",
  inappropriate_photo: "부적절한 사진",
  abuse: "욕설/비하",
  spam: "상업적 광고 및 판매",
  other: "기타",
};

