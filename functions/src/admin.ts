import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { getAuth } from "firebase-admin/auth";
import { getMessaging } from "firebase-admin/messaging";
import { setGlobalOptions } from "firebase-functions/v2";

// Firestore 가 서울(asia-northeast3)에 있으므로 함수도 같은 지역에서 실행합니다.
setGlobalOptions({ region: "asia-northeast3", maxInstances: 10 });

initializeApp();

export const db = getFirestore();
export const bucket = () => getStorage().bucket();
export const auth = getAuth();
export const messaging = getMessaging();
