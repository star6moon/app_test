// 앱과 똑같이 서버 함수 identifyPlant 를 불러 식별이 되는지 확인합니다 (deploy.yml 의 identify-test).
// 사용법: node scripts/identify-test.mjs 사진1.jpg [사진2.jpg ...]
// 필요한 환경 변수: GOOGLE_APPLICATION_CREDENTIALS(서비스 계정 키 파일), WEB_API_KEY, ANDROID_PACKAGE, ANDROID_CERT_SHA1
import fs from "node:fs";
import path from "node:path";
import { initializeApp, cert } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";

const TEST_UID = "identify-test-bot";
const REGION = "asia-northeast3";

const serviceAccount = JSON.parse(fs.readFileSync(process.env.GOOGLE_APPLICATION_CREDENTIALS, "utf8"));
const projectId = serviceAccount.project_id;
initializeApp({ credential: cert(serviceAccount), projectId });

// 테스트용 사용자로 로그인한 것과 같은 ID 토큰을 받습니다.
const customToken = await getAuth().createCustomToken(TEST_UID);
const signIn = await fetch(
  `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${process.env.WEB_API_KEY}`,
  {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      // API 키가 안드로이드 앱 전용으로 제한된 경우를 위해 앱과 같은 정보를 보냅니다.
      "X-Android-Package": process.env.ANDROID_PACKAGE ?? "",
      "X-Android-Cert": process.env.ANDROID_CERT_SHA1 ?? "",
    },
    body: JSON.stringify({ token: customToken, returnSecureToken: true }),
  },
);
const signInBody = await signIn.json();
if (!signIn.ok) {
  console.log(`로그인 실패 (${signIn.status}): ${JSON.stringify(signInBody.error ?? signInBody)}`);
  process.exit(1);
}

let failed = 0;
for (const file of process.argv.slice(2)) {
  const image = fs.readFileSync(file);
  const res = await fetch(`https://${REGION}-${projectId}.cloudfunctions.net/identifyPlant`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${signInBody.idToken}` },
    body: JSON.stringify({ data: { images: [image.toString("base64")], lang: "ko" } }),
  });
  const text = await res.text();
  let summary;
  try {
    const json = JSON.parse(text);
    if (json.error) {
      summary = `서버 오류: ${json.error.status} ${json.error.message}`;
      failed++;
    } else {
      const { status, body, lang } = json.result;
      const results = status === 200 ? JSON.parse(body).results : [];
      summary = status === 200
        ? `성공 (lang=${lang}) → ` + results.slice(0, 3)
          .map((r) => `${r.species.scientificNameWithoutAuthor} ${(r.score * 100).toFixed(1)}%`).join(", ")
        : `Pl@ntNet ${status} (lang=${lang}): ${body.slice(0, 300)}`;
      if (status !== 200) failed++;
    }
  } catch {
    summary = `HTTP ${res.status}: ${text.slice(0, 300)}`;
    failed++;
  }
  console.log(`${path.basename(file)} (${Math.round(image.length / 1024)}KB): ${summary}`);
}
process.exit(failed ? 1 : 0);
