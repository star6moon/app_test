import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db, bucket, auth } from "./admin";

/**
 * 회원 탈퇴: 내 기록·사진·댓글·좋아요·팔로우 관계를 모두 지우고 계정을 삭제합니다.
 * (기록 삭제 시 다른 사람의 좋아요·책갈피는 onEntryDeleted 가 정리합니다.)
 */
export const deleteAccount = onCall({ timeoutSeconds: 300 }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "LOGIN_REQUIRED");

  // 내 기록 (댓글 하위 컬렉션 포함)
  const entries = await db.collection("entries").where("ownerId", "==", uid).get();
  for (const doc of entries.docs) await db.recursiveDelete(doc.ref);

  // 내가 누른 좋아요: 상대 기록의 숫자를 줄이고 지웁니다.
  const likes = await db.collection(`users/${uid}/likes`).get();
  for (const like of likes.docs) {
    const entry = db.doc(`entries/${like.id}`);
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(entry);
      if (snap.exists) tx.update(entry, { likeCount: FieldValue.increment(-1) });
      tx.delete(like.ref);
    });
  }

  // 내가 쓴 댓글 (onCommentDeleted 가 댓글 수를 줄입니다)
  const comments = await db.collectionGroup("comments").where("authorId", "==", uid).get();
  const writer = db.bulkWriter();
  comments.docs.forEach((d) => writer.delete(d.ref));
  // 나를 팔로우한 사람들의 following 문서 (onFollowDeleted 가 숫자를 맞춥니다)
  const followers = await db.collection(`users/${uid}/followers`).get();
  followers.docs.forEach((d) => writer.delete(db.doc(`users/${d.id}/following/${uid}`)));
  // 내가 팔로우한 관계
  const following = await db.collection(`users/${uid}/following`).get();
  following.docs.forEach((d) => writer.delete(d.ref));
  await writer.close();

  await db.recursiveDelete(db.doc(`users/${uid}`));
  await bucket().deleteFiles({ prefix: `users/${uid}/` }).catch(() => undefined);
  await auth.deleteUser(uid);
  return { ok: true };
});
