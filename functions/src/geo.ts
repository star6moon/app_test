/** 위치 관련 계산: 거리(하버사인), 지오해시 (앱의 NearbyRule / GeoHash 와 같은 계산) */

export function distanceMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const r = 6_371_008.8;
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(lat2 - lat1);
  const dLon = rad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(Math.min(1, Math.max(0, a))));
}

const BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

/** 지오해시 (기본 10자리 ≈ 1m). 지도 화면 영역으로 기록을 찾을 때 씁니다. */
export function geohash(lat: number, lng: number, precision = 10): string {
  let latMin = -90, latMax = 90, lngMin = -180, lngMax = 180;
  let hash = "";
  let bit = 0, ch = 0, even = true;
  while (hash.length < precision) {
    if (even) {
      const mid = (lngMin + lngMax) / 2;
      if (lng >= mid) { ch = (ch << 1) | 1; lngMin = mid; } else { ch = ch << 1; lngMax = mid; }
    } else {
      const mid = (latMin + latMax) / 2;
      if (lat >= mid) { ch = (ch << 1) | 1; latMin = mid; } else { ch = ch << 1; latMax = mid; }
    }
    even = !even;
    if (++bit === 5) { hash += BASE32[ch]; bit = 0; ch = 0; }
  }
  return hash;
}

/** 같은 계정은 같은 종을 기존 기록에서 이 거리 이상 떨어진 곳에서만 등록할 수 있습니다 (앱의 NearbyRule 과 같은 값). */
export const MIN_DISTANCE_METERS = 10;
