import { onDocumentCreated, onDocumentDeleted, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { FieldValue } from "firebase-admin/firestore";
import { db, messaging } from "./admin";
import { parsePrefs, wantsNotification } from "./notificationPrefs";

type NotificationType = "like" | "comment" | "follow";

interface NotificationInput {
  type: NotificationType;
  targetUid: string;
  actorUid: string;
  entryId?: string;
  text?: string;
}

async function displayName(uid: string): Promise<string> {
  const snap = await db.doc(`users/${uid}`).get();
  return String(snap.get("displayName") ?? "식물 탐험가");
}

/** 알림을 남기고 휴대폰으로 푸시를 보냅니다. 본인 행동이나 차단한 사용자의 행동은 알리지 않습니다. */
export async function notify(input: NotificationInput) {
  const { type, targetUid, actorUid } = input;
  if (targetUid === actorUid) return;
  const blocked = await db.doc(`users/${targetUid}/blocked/${actorUid}`).get();
  if (blocked.exists) return;

  const actorName = await displayName(actorUid);
  const body =
    type === "like" ? `${actorName}님이 내 기록을 좋아해요`
    : type === "comment" ? `${actorName}님의 댓글: ${(input.text ?? "").slice(0, 80)}`
    : `${actorName}님이 나를 팔로우하기 시작했어요`;
  await deliver(targetUid, { type, actorUid, actorName, entryId: input.entryId ?? null, body });
}

/** 신고 처리·경고·활동 정지처럼 서비스가 보내는 알림 (보낸 사람 없음) */
export async function notifySystem(targetUid: string, body: string, entryId?: string) {
  await deliver(targetUid, { type: "moderation", actorUid: "", actorName: "PlantDex", entryId: entryId ?? null, body });
}

async function deliver(
  targetUid: string,
  n: { type: string; actorUid: string; actorName: string; entryId: string | null; body: string },
) {
  // 설정에서 끈 종류는 알림 목록에도 남기지 않습니다. 푸시만 끈 경우는 목록에만 남깁니다.
  const prefs = parsePrefs((await db.doc(`users/${targetUid}/private/prefs`).get()).data());
  if (!wantsNotification(prefs, n.type)) return;
  await db.collection(`users/${targetUid}/notifications`).add({
    ...n,
    read: false,
    createdAt: FieldValue.serverTimestamp(),
  });

  if (!prefs.push) return;
  const fcm = await db.doc(`users/${targetUid}/private/fcm`).get();
  const tokens = (fcm.get("tokens") as string[] | undefined) ?? [];
  if (tokens.length === 0) return;
  const res = await messaging.sendEachForMulticast({
    tokens,
    notification: { title: "PlantDex", body: n.body },
    data: { type: n.type, entryId: n.entryId ?? "", actorUid: n.actorUid },
    android: { notification: { channelId: "social" } },
  });
  // 더 이상 쓰이지 않는 토큰은 지웁니다.
  const stale = tokens.filter((_, i) => {
    const code = res.responses[i]?.error?.code ?? "";
    return code.includes("registration-token-not-registered") || code.includes("invalid-registration-token");
  });
  if (stale.length > 0) await fcm.ref.update({ tokens: FieldValue.arrayRemove(...stale) });
}

async function entryOwner(entryId: string): Promise<string | null> {
  const snap = await db.doc(`entries/${entryId}`).get();
  const owner = snap.get("ownerId");
  return typeof owner === "string" ? owner : null;
}

// ── 좋아요 ─────────────────────────────────────────────
export const onLikeCreated = onDocumentCreated("users/{uid}/likes/{entryId}", async (event) => {
  const owner = await entryOwner(event.params.entryId);
  if (owner) await notify({ type: "like", targetUid: owner, actorUid: event.params.uid, entryId: event.params.entryId });
});

// ── 댓글 ───────────────────────────────────────────────
export const onCommentCreated = onDocumentCreated("entries/{entryId}/comments/{commentId}", async (event) => {
  const { entryId } = event.params;
  const authorId = event.data?.get("authorId");
  if (typeof authorId !== "string") return;
  // 작성자 이름은 서버에서 실제 닉네임으로 채웁니다.
  await event.data?.ref.update({ authorName: await displayName(authorId) });
  await db.doc(`entries/${entryId}`).update({ commentCount: FieldValue.increment(1) }).catch(() => undefined);
  const owner = await entryOwner(entryId);
  if (owner) await notify({ type: "comment", targetUid: owner, actorUid: authorId, entryId, text: String(event.data?.get("text") ?? "") });
});

export const onCommentDeleted = onDocumentDeleted("entries/{entryId}/comments/{commentId}", async (event) => {
  const entry = db.doc(`entries/${event.params.entryId}`);
  if ((await entry.get()).exists) await entry.update({ commentCount: FieldValue.increment(-1) });
});

// ── 팔로우 ─────────────────────────────────────────────
// 앱은 users/{uid}/following/{target} 만 쓰고, 상대방의 followers 와 숫자는 서버가 맞춥니다.
export const onFollowCreated = onDocumentCreated("users/{uid}/following/{target}", async (event) => {
  const { uid, target } = event.params;
  const batch = db.batch();
  batch.set(db.doc(`users/${target}/followers/${uid}`), { createdAt: FieldValue.serverTimestamp() });
  batch.set(db.doc(`users/${uid}`), { followingCount: FieldValue.increment(1) }, { merge: true });
  batch.set(db.doc(`users/${target}`), { followerCount: FieldValue.increment(1) }, { merge: true });
  await batch.commit();
  await notify({ type: "follow", targetUid: target, actorUid: uid });
});

export const onFollowDeleted = onDocumentDeleted("users/{uid}/following/{target}", async (event) => {
  const { uid, target } = event.params;
  const batch = db.batch();
  batch.delete(db.doc(`users/${target}/followers/${uid}`));
  batch.set(db.doc(`users/${uid}`), { followingCount: FieldValue.increment(-1) }, { merge: true });
  batch.set(db.doc(`users/${target}`), { followerCount: FieldValue.increment(-1) }, { merge: true });
  await batch.commit();
});

// ── 닉네임 변경 ────────────────────────────────────────
/** 닉네임을 바꾸면 내 기록과 댓글에 적힌 이름도 바꿉니다. */
export const onUserUpdated = onDocumentUpdated("users/{uid}", async (event) => {
  const before = event.data?.before.get("displayName");
  const after = event.data?.after.get("displayName");
  if (typeof after !== "string" || before === after) return;
  const { uid } = event.params;
  const writer = db.bulkWriter();
  const entries = await db.collection("entries").where("ownerId", "==", uid).get();
  entries.docs.forEach((d) => writer.update(d.ref, { ownerName: after }));
  const comments = await db.collectionGroup("comments").where("authorId", "==", uid).get();
  comments.docs.forEach((d) => writer.update(d.ref, { authorName: after }));
  await writer.close();
});
