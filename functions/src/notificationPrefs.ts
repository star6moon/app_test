/** 사용자가 설정에서 고른 알림 종류 (users/{uid}/private/prefs). 문서가 없으면 모두 켜진 것으로 봅니다. */
export interface NotificationPrefs {
  notifyLike: boolean;
  notifyComment: boolean;
  notifyFollow: boolean;
  /** false 면 앱 안 알림 목록에만 남기고 휴대폰 푸시는 보내지 않습니다 */
  push: boolean;
}

export const DEFAULT_PREFS: NotificationPrefs = { notifyLike: true, notifyComment: true, notifyFollow: true, push: true };

export function parsePrefs(data: Record<string, unknown> | undefined): NotificationPrefs {
  const bool = (key: keyof NotificationPrefs) => (typeof data?.[key] === "boolean" ? (data[key] as boolean) : DEFAULT_PREFS[key]);
  return { notifyLike: bool("notifyLike"), notifyComment: bool("notifyComment"), notifyFollow: bool("notifyFollow"), push: bool("push") };
}

/** 이 종류의 알림을 남길지. 신고 처리 같은 서비스 알림(moderation)은 끌 수 없습니다. */
export function wantsNotification(prefs: NotificationPrefs, type: string): boolean {
  switch (type) {
    case "like": return prefs.notifyLike;
    case "comment": return prefs.notifyComment;
    case "follow": return prefs.notifyFollow;
    default: return true;
  }
}
