// 앱과 서버가 같은 도감 데이터로 종을 판단하도록, 앱의 catalogs.json 을 빌드 때 복사합니다.
const fs = require("fs");
const path = require("path");
const src = path.join(__dirname, "..", "..", "app", "src", "main", "assets", "catalogs.json");
const dest = path.join(__dirname, "..", "src", "catalog", "catalogs.json");
fs.mkdirSync(path.dirname(dest), { recursive: true });
fs.copyFileSync(src, dest);
console.log("copied", path.relative(process.cwd(), src), "->", path.relative(process.cwd(), dest));
