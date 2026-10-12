import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentDeleted } from "firebase-functions/v2/firestore";
import { FieldValue, Timestamp, GeoPoint } from "firebase-admin/firestore";
import { getDownloadURL } from "firebase-admin/storage";
import { db, bucket } from "./admin";
import { requireAppCheck } from "./identify";
import { speciesKey, isCatalogSpecies } from "./catalog/species";
import { distanceMeters, geohash, MIN_DISTANCE_METERS } from "./geo";

const str = (v: unknown, max: number): string | null =>
  typeof v === "string" && v.length <= max ? v : null;
const num = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);

export function entryPhotoPath(ownerId: string, entryId: string, thumb = false) {
  return `users/${ownerId}/entries/${entryId}${thumb ? "_thumb" : ""}.jpg`;
}

/**
 * 도감 기록 등록. 앱이 사진을 Storage 에 올린 뒤 호출합니다.
 * 서버에서 위치 필수 · 같은 종 10m 규칙을 다시 확인하므로, 수정된 앱으로도 우회할 수 없습니다.
 * (Firestore 규칙은 앱이 entries 문서를 직접 만드는 것을 막습니다.)
 */
export const createEntry = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "LOGIN_REQUIRED");
  requireAppCheck(request.app);
  const d = (request.data ?? {}) as Record<string, unknown>;

  const entryId = str(d.entryId, 40);
  if (!entryId || !/^[A-Za-z0-9]{10,40}$/.test(entryId)) throw new HttpsError("invalid-argument", "BAD_ENTRY_ID");
  const scientificName = str(d.scientificName, 200);
  if (!scientificName) throw new HttpsError("invalid-argument", "BAD_NAME");
  const score = num(d.score) ?? 0;
  const capturedAt = num(d.capturedAt);
  if (capturedAt === null || capturedAt > Date.now() + 10 * 60_000) throw new HttpsError("invalid-argument", "BAD_TIME");
  const loc = (d.location ?? null) as Record<string, unknown> | null;
  const lat = num(loc?.latitude);
  const lng = num(loc?.longitude);
  if (lat === null || lng === null || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    throw new HttpsError("failed-precondition", "LOCATION_REQUIRED");
  }
  const memo = (str(d.memo, 1000) ?? "").trim();
  const isPublic = d.isPublic === true;

  const ref = db.collection("entries").doc(entryId);
  if ((await ref.get()).exists) throw new HttpsError("already-exists", "ENTRY_EXISTS");

  // 사진이 실제로 올라와 있어야 합니다.
  const photo = bucket().file(entryPhotoPath(uid, entryId));
  const [photoExists] = await photo.exists();
  if (!photoExists) throw new HttpsError("failed-precondition", "PHOTO_MISSING");
  const thumb = bucket().file(entryPhotoPath(uid, entryId, true));
  const [thumbExists] = await thumb.exists();

  // 같은 종을 반경 안에 이미 등록했는지 확인
  const key = speciesKey(scientificName);
  const mine = await db.collection("entries").where("ownerId", "==", uid).get();
  for (const doc of mine.docs) {
    const gp = doc.get("location") as GeoPoint | undefined;
    if (!gp || speciesKey(String(doc.get("scientificName") ?? "")) !== key) continue;
    const distance = distanceMeters(lat, lng, gp.latitude, gp.longitude);
    if (distance < MIN_DISTANCE_METERS) {
      throw new HttpsError("failed-precondition", "NEARBY_DUPLICATE", { entryId: doc.id, distance });
    }
  }

  const user = await db.doc(`users/${uid}`).get();
  const ownerName = String(user.get("displayName") ?? request.auth?.token.name ?? "식물 탐험가");

  await ref.set({
    ownerId: uid,
    ownerName,
    scientificName,
    commonName: str(d.commonName, 200),
    commonNameLanguage: str(d.commonNameLanguage, 10),
    gbifId: str(d.gbifId, 40),
    family: str(d.family, 200),
    genus: str(d.genus, 200),
    score,
    photoUrl: await getDownloadURL(photo),
    thumbUrl: thumbExists ? await getDownloadURL(thumb) : null,
    photoPath: photo.name,
    capturedAt: Timestamp.fromMillis(capturedAt),
    location: new GeoPoint(lat, lng),
    locationAccuracy: num(loc?.accuracyMeters),
    placeName: str(loc?.placeName, 200),
    geohash: geohash(lat, lng),
    memo,
    isPublic,
    likeCount: 0,
    commentCount: 0,
    createdAt: FieldValue.serverTimestamp(),
  });
  return { id: entryId };
});

/** 사용자 통계(기록 수, 종 수)를 다시 계산합니다. 랭킹·프로필에 씁니다. */
export async function recomputeStats(uid: string) {
  const snap = await db.collection("entries").where("ownerId", "==", uid).get();
  const species = new Set<string>();
  const catalogSpecies = new Set<string>();
  for (const doc of snap.docs) {
    const name = String(doc.get("scientificName") ?? "");
    const key = speciesKey(name);
    species.add(key);
    if (isCatalogSpecies(name)) catalogSpecies.add(key);
  }
  await db.doc(`users/${uid}`).set(
    { stats: { records: snap.size, species: species.size, catalogSpecies: catalogSpecies.size, updatedAt: FieldValue.serverTimestamp() } },
    { merge: true },
  );
}

export const onEntryCreated = onDocumentCreated("entries/{entryId}", async (event) => {
  const ownerId = event.data?.get("ownerId");
  if (typeof ownerId === "string") await recomputeStats(ownerId);
});

/** 기록이 지워지면 사진, 댓글, 다른 사람들의 좋아요·책갈피를 함께 정리합니다. */
export const onEntryDeleted = onDocumentDeleted("entries/{entryId}", async (event) => {
  const entryId = event.params.entryId;
  const ownerId = event.data?.get("ownerId");

  await db.recursiveDelete(db.collection(`entries/${entryId}/comments`));
  for (const group of ["likes", "bookmarks"]) {
    const refs = await db.collectionGroup(group).where("entryId", "==", entryId).get();
    const writer = db.bulkWriter();
    refs.docs.forEach((d) => writer.delete(d.ref));
    await writer.close();
  }
  if (typeof ownerId === "string") {
    await Promise.all([
      bucket().file(entryPhotoPath(ownerId, entryId)).delete({ ignoreNotFound: true }),
      bucket().file(entryPhotoPath(ownerId, entryId, true)).delete({ ignoreNotFound: true }),
    ]);
    await recomputeStats(ownerId);
  }
});

/** 신고가 3명 이상 쌓인 기록은 자동으로 비공개(숨김) 처리합니다. */
export const REPORT_HIDE_THRESHOLD = 3;

export const onReportCreated = onDocumentCreated("reports/{reportId}", async (event) => {
  const type = event.data?.get("targetType");
  const targetId = event.data?.get("targetId");
  if (typeof targetId !== "string") return;
  const reports = await db.collection("reports").where("targetType", "==", type).where("targetId", "==", targetId).get();
  const reporters = new Set(reports.docs.map((d) => d.get("reporterId")));
  if (reporters.size < REPORT_HIDE_THRESHOLD) return;

  if (type === "entry") {
    await db.doc(`entries/${targetId}`).update({ isPublic: false, moderation: "hidden" }).catch(() => undefined);
  } else if (type === "comment") {
    const entryId = event.data?.get("entryId");
    if (typeof entryId === "string") await db.doc(`entries/${entryId}/comments/${targetId}`).delete().catch(() => undefined);
  }
});

/**
 * 예전 버전에서 만든 내 데이터를 새 구조에 맞춥니다 (앱이 로그인 후 한 번 호출).
 * - 지오해시가 없는 기록에 지오해시 추가 (지도 '모두의 기록' 에 나오도록)
 * - 통계(기록 수, 종 수) 다시 계산 (프로필·랭킹용)
 */
export const migrateMyData = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "LOGIN_REQUIRED");
  const snap = await db.collection("entries").where("ownerId", "==", uid).get();
  let updated = 0;
  let batch = db.batch();
  let pending = 0;
  for (const doc of snap.docs) {
    const loc = doc.get("location");
    if (doc.get("geohash") || !(loc instanceof GeoPoint)) continue;
    batch.update(doc.ref, { geohash: geohash(loc.latitude, loc.longitude) });
    updated++;
    if (++pending === 400) {
      await batch.commit();
      batch = db.batch();
      pending = 0;
    }
  }
  if (pending > 0) await batch.commit();
  await recomputeStats(uid);
  return { updated };
});
