import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import { defineSecret, defineBoolean, defineInt } from "firebase-functions/params";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./admin";

/** Pl@ntNet API 키는 앱에 넣지 않고 서버 비밀값으로 둡니다: firebase functions:secrets:set PLANTNET_API_KEY */
const PLANTNET_API_KEY = defineSecret("PLANTNET_API_KEY");

/** true 로 바꾸면 App Check 토큰이 없는 요청(우리 앱이 아닌 곳)을 거부합니다. */
export const APP_CHECK_REQUIRED = defineBoolean("APP_CHECK_REQUIRED", { default: false });

/** 사용자 한 명이 하루에 식별할 수 있는 횟수 (Pl@ntNet 무료 한도 500회/일을 모두가 나눠 씀) */
const DAILY_LIMIT_PER_USER = defineInt("IDENTIFY_DAILY_LIMIT", { default: 50 });

const MAX_IMAGES = 3;
const MAX_IMAGE_BYTES = 3 * 1024 * 1024;

interface IdentifyRequest {
  images?: unknown;
  lang?: unknown;
}

export function requireAppCheck(app: unknown) {
  if (APP_CHECK_REQUIRED.value() && !app) {
    throw new HttpsError("failed-precondition", "APP_CHECK_REQUIRED");
  }
}

/** 하루 사용량을 1 늘리고, 한도를 넘으면 거부합니다. (한국 시간 기준 날짜) */
async function consumeQuota(uid: string) {
  const day = new Date(Date.now() + 9 * 3600_000).toISOString().slice(0, 10);
  const ref = db.doc(`users/${uid}/private/quota`);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const count = snap.exists && snap.get("day") === day ? (snap.get("count") as number) : 0;
    if (count >= DAILY_LIMIT_PER_USER.value()) {
      throw new HttpsError("resource-exhausted", "DAILY_LIMIT");
    }
    tx.set(ref, { day, count: count + 1, updatedAt: FieldValue.serverTimestamp() });
  });
}

async function callPlantNet(images: Buffer[], lang: string, noReject: boolean) {
  const url = new URL("https://my-api.plantnet.org/v2/identify/all");
  url.searchParams.set("api-key", PLANTNET_API_KEY.value());
  url.searchParams.set("lang", lang);
  url.searchParams.set("nb-results", "5");
  url.searchParams.set("include-related-images", "false");
  if (noReject) url.searchParams.set("no-reject", "true");
  const form = new FormData();
  images.forEach((img, i) => {
    form.append("images", new Blob([new Uint8Array(img)], { type: "image/jpeg" }), `plant_${i}.jpg`);
    form.append("organs", "auto");
  });
  const res = await fetch(url, { method: "POST", body: form });
  return { status: res.status, body: await res.text() };
}

function resultCount(body: string): number | undefined {
  try {
    const results = JSON.parse(body).results;
    return Array.isArray(results) ? results.length : undefined;
  } catch {
    return undefined;
  }
}

function messageOf(body: string): string {
  try {
    return String(JSON.parse(body).message ?? "");
  } catch {
    return body.slice(0, 200);
  }
}

/**
 * 사진(최대 3장, base64 JPEG)으로 식물을 식별합니다.
 * Pl@ntNet 응답(status, body)을 그대로 돌려주고, 앱이 기존과 같은 방식으로 해석합니다.
 */
export const identifyPlant = onCall(
  { secrets: [PLANTNET_API_KEY], memory: "512MiB", timeoutSeconds: 60 },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "LOGIN_REQUIRED");
    requireAppCheck(request.app);

    const data = (request.data ?? {}) as IdentifyRequest;
    if (!Array.isArray(data.images) || data.images.length < 1 || data.images.length > MAX_IMAGES) {
      throw new HttpsError("invalid-argument", "IMAGES_1_TO_3");
    }
    const images = data.images.map((b64) => {
      if (typeof b64 !== "string") throw new HttpsError("invalid-argument", "BAD_IMAGE");
      const buf = Buffer.from(b64, "base64");
      if (buf.length === 0 || buf.length > MAX_IMAGE_BYTES) throw new HttpsError("invalid-argument", "IMAGE_TOO_LARGE");
      return buf;
    });
    let lang = typeof data.lang === "string" && /^[a-z]{2,3}$/.test(data.lang) ? data.lang : "en";

    await consumeQuota(uid);

    // 지원하지 않는 옵션(언어, no-reject)은 빼고 다시 시도합니다.
    let noReject = true;
    for (let attempt = 0; attempt < 3; attempt++) {
      const res = await callPlantNet(images, lang, noReject);
      // Firebase 콘솔 → Functions → identifyPlant → 로그 에서 Pl@ntNet 응답을 확인할 수 있습니다.
      logger.info("plantnet response", {
        uid,
        attempt,
        status: res.status,
        noReject,
        lang,
        images: images.length,
        results: resultCount(res.body),
        message: res.status === 200 ? undefined : messageOf(res.body),
      });
      if (res.status === 400) {
        const msg = messageOf(res.body).toLowerCase();
        if (noReject && msg.includes("reject")) { noReject = false; continue; }
        if (lang !== "en" && msg.includes("lang")) { lang = "en"; continue; }
      }
      return { status: res.status, body: res.body, lang };
    }
    throw new HttpsError("internal", "PLANTNET_REJECTED_OPTIONS");
  },
);
