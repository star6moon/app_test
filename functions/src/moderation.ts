import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "./admin";
import { notifySystem } from "./social";
import { recomputeStats } from "./entries";
import { speciesKey } from "./catalog/species";
import {
  POLICY,
  REASON_CODES,
  REASON_LABELS,
  ReasonCode,
  ReportInfo,
  TARGET_REASONS,
  TargetType,
  decideAction,
  suspensionDays,
} from "./moderationPolicy";

function asReason(v: unknown): ReasonCode {
  return REASON_CODES.includes(v as ReasonCode) ? (v as ReasonCode) : "other";
}

function millis(v: unknown): number {
  return v instanceof Timestamp ? v.toMillis() : 0;
}

/** 지금 활동 정지 중이면 끝나는 시각(ms), 아니면 null */
export async function suspendedUntil(uid: string): Promise<number | null> {
  const snap = await db.doc(`users/${uid}/private/moderation`).get();
  const until = millis(snap.get("suspendedUntil"));
  return until > Date.now() ? until : null;
}

function formatDate(ms: number): string {
  const d = new Date(ms + 9 * 3600_000); // 한국 시간
  return `${d.getUTCMonth() + 1}월 ${d.getUTCDate()}일 ${String(d.getUTCHours()).padStart(2, "0")}:${String(d.getUTCMinutes()).padStart(2, "0")}`;
}

// ── 신고가 들어올 때 ─────────────────────────────────────────

export const onReportCreated = onDocumentCreated("reports/{reportId}", async (event) => {
  const type = event.data?.get("targetType") as TargetType | undefined;
  const targetId = event.data?.get("targetId");
  if (typeof targetId !== "string" || !type || !(type in TARGET_REASONS)) return;
  const entryId = event.data?.get("entryId");

  // 주인은 신고 내용이 아니라 실제 데이터에서 확인합니다.
  let ownerId: string | null = null;
  let current: FirebaseFirestore.DocumentSnapshot | null = null;
  if (type === "entry") {
    current = await db.doc(`entries/${targetId}`).get();
    ownerId = current.exists ? String(current.get("ownerId")) : null;
  } else if (type === "comment") {
    if (typeof entryId !== "string") return;
    current = await db.doc(`entries/${entryId}/comments/${targetId}`).get();
    ownerId = current.exists ? String(current.get("authorId")) : null;
  } else {
    ownerId = targetId;
  }
  if (!ownerId) return; // 이미 지워진 대상

  const snap = await db.collection("reports").where("targetType", "==", type).where("targetId", "==", targetId).get();
  const reports: ReportInfo[] = snap.docs.map((d) => ({
    reporterId: String(d.get("reporterId")),
    reasonCode: asReason(d.get("reasonCode")),
    createdAtMillis: millis(d.get("createdAt")) || Date.now(),
  }));
  const decision = decideAction(type, reports, ownerId, millis(current?.get("correctedAt")));
  const reasonLabel = decision.reason ? REASON_LABELS[decision.reason] : "";

  if (type === "entry" && current) {
    const moderation = current.get("moderation");
    if (decision.action === "hide" && moderation !== "hidden") {
      await current.ref.update({ isPublic: false, moderation: "hidden", moderationReason: decision.reason });
      await notifySystem(ownerId, `신고가 쌓여 내 기록이 숨겨졌어요 (${reasonLabel}). 다시 공개할 수 없어요.`, targetId);
    } else if (decision.action === "needs_correction" && !moderation) {
      await current.ref.update({ isPublic: false, moderation: "needs_correction", moderationReason: "wrong_info" });
      await notifySystem(
        ownerId,
        "'식물 정보가 일치하지 않는다'는 신고가 쌓여 기록이 비공개로 바뀌었어요. 기록에서 식물 정보를 고치면 다시 공개할 수 있어요.",
        targetId,
      );
    }
  } else if (type === "comment" && current && decision.action === "hide") {
    await current.ref.delete();
    await notifySystem(ownerId, `신고가 쌓여 내 댓글이 삭제됐어요 (${reasonLabel}).`, typeof entryId === "string" ? entryId : undefined);
  }

  if (decision.strike && decision.reason) await addStrike(ownerId, `${type}_${targetId}`, decision.reason);
});

/** 경고 1회. 같은 대상으로는 한 번만 받고, 기간 안에 정해진 수가 모이면 활동을 정지합니다. */
async function addStrike(uid: string, key: string, reason: ReasonCode) {
  const strikeRef = db.doc(`users/${uid}/strikes/${key}`);
  try {
    await strikeRef.create({ reason, consumed: false, createdAt: FieldValue.serverTimestamp() });
  } catch {
    return; // 이 대상으로 이미 경고를 받음
  }

  const since = Date.now() - POLICY.strikeWindowDays * 86_400_000;
  const open = await db.collection(`users/${uid}/strikes`).where("consumed", "==", false).get();
  const active = open.docs.filter((d) => d.id === key || millis(d.get("createdAt")) >= since);
  const modRef = db.doc(`users/${uid}/private/moderation`);

  if (active.length < POLICY.strikesPerSuspension) {
    await modRef.set({ activeStrikes: active.length, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    await notifySystem(
      uid,
      `신고 누적 경고를 받았어요 (${REASON_LABELS[reason]}). 경고 ${active.length}/${POLICY.strikesPerSuspension}회 — ` +
        `${POLICY.strikesPerSuspension}회가 되면 활동이 정지돼요.`,
    );
    return;
  }

  const mod = await modRef.get();
  const number = Number(mod.get("suspensionCount") ?? 0) + 1;
  const days = suspensionDays(number);
  const start = Math.max(Date.now(), millis(mod.get("suspendedUntil")));
  const until = start + days * 86_400_000;
  const batch = db.batch();
  active.forEach((d) => batch.update(d.ref, { consumed: true }));
  batch.set(
    modRef,
    {
      suspendedUntil: Timestamp.fromMillis(until),
      suspensionCount: number,
      activeStrikes: 0,
      lastReason: reason,
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
  await batch.commit();
  await notifySystem(
    uid,
    `신고 누적 경고가 ${POLICY.strikesPerSuspension}회가 되어 ${days}일 동안 기록·댓글·좋아요·팔로우가 제한돼요 (${formatDate(until)}까지).`,
  );
}

// ── 정보 정정 ────────────────────────────────────────────────

const str = (v: unknown, max: number): string | null =>
  typeof v === "string" && v.trim().length > 0 && v.length <= max ? v.trim() : null;

/**
 * '정보 일치하지 않음' 으로 비공개가 된 기록의 식물 정보를 주인이 고칩니다.
 * 다른 종으로 바꿔야 하고, 고치면 다시 공개할 수 있습니다 (공개는 주인이 직접 켭니다).
 */
export const correctEntry = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "LOGIN_REQUIRED");
  const d = (request.data ?? {}) as Record<string, unknown>;
  const entryId = str(d.entryId, 40);
  const scientificName = str(d.scientificName, 200);
  if (!entryId || !scientificName) throw new HttpsError("invalid-argument", "BAD_INPUT");

  const ref = db.doc(`entries/${entryId}`);
  const entry = await ref.get();
  if (!entry.exists || entry.get("ownerId") !== uid) throw new HttpsError("permission-denied", "NOT_OWNER");
  if (entry.get("moderation") !== "needs_correction") throw new HttpsError("failed-precondition", "NOT_NEEDED");
  if (speciesKey(scientificName) === speciesKey(String(entry.get("scientificName") ?? ""))) {
    throw new HttpsError("invalid-argument", "SAME_SPECIES");
  }

  const commonName = str(d.commonName, 200);
  await ref.update({
    scientificName,
    commonName,
    commonNameLanguage: commonName ? "ko" : null,
    gbifId: null,
    family: str(d.family, 200),
    genus: scientificName.split(/\s+/)[0] ?? null,
    moderation: FieldValue.delete(),
    moderationReason: FieldValue.delete(),
    correctedAt: FieldValue.serverTimestamp(),
  });
  await recomputeStats(uid);
  return { ok: true };
});
