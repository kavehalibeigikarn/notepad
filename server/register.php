<?php
// دریافت ثبت‌نام‌های یادداشت‌یار. روی هر هاست PHP (با SQLite) کار می‌کند.
// ۱) ADMIN_TOKEN را عوض کنید. ۲) پوشهٔ data/ باید قابل‌نوشتن باشد (و از وب بسته باشد: .htaccess همراه است).
// دریافت لیست:  register.php?token=ADMIN_TOKEN            (JSON)
//               register.php?token=ADMIN_TOKEN&format=csv (CSV برای اکسل)
const ADMIN_TOKEN = 'CHANGE_ME_TO_A_LONG_RANDOM_STRING';

header('Content-Type: application/json; charset=utf-8');
$dir = __DIR__ . '/data';
if (!is_dir($dir)) { mkdir($dir, 0700, true); file_put_contents("$dir/.htaccess", "Require all denied\nDeny from all\n"); }
$db = new PDO('sqlite:' . $dir . '/users.sqlite');
$db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$db->exec('CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT, phone TEXT UNIQUE, name TEXT, edition TEXT,
  version_code INTEGER, install_id TEXT, consent_at INTEGER, created_at TEXT, ip_hash TEXT)');

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
  if (!hash_equals(ADMIN_TOKEN, $_GET['token'] ?? '')) { http_response_code(403); echo '{"error":"forbidden"}'; exit; }
  $rows = $db->query('SELECT phone,name,edition,version_code,consent_at,created_at FROM users ORDER BY id DESC')->fetchAll(PDO::FETCH_ASSOC);
  if (($_GET['format'] ?? '') === 'csv') {
    header('Content-Type: text/csv; charset=utf-8'); echo "\xEF\xBB\xBF" . "phone,name,edition,version_code,consent_at,created_at\n";
    foreach ($rows as $r) echo implode(',', array_map(fn($v) => '"' . str_replace('"', '""', (string)$v) . '"', $r)) . "\n";
  } else echo json_encode($rows, JSON_UNESCAPED_UNICODE);
  exit;
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') { http_response_code(405); exit; }
$in = json_decode(file_get_contents('php://input'), true);
$phone = preg_replace('/\D/', '', $in['phone'] ?? '');
if (!preg_match('/^09\d{9}$/', $phone) || empty($in['consent'])) { http_response_code(400); echo '{"error":"invalid"}'; exit; }

// محدودیت ساده: حداکثر ۳۰ ثبت‌نام در ساعت از هر IP
$ipHash = hash('sha256', ($_SERVER['REMOTE_ADDR'] ?? '') . 'yy-salt');
$st = $db->prepare("SELECT COUNT(*) FROM users WHERE ip_hash=? AND created_at > datetime('now','-1 hour')");
$st->execute([$ipHash]);
if ((int)$st->fetchColumn() >= 30) { http_response_code(429); echo '{"error":"rate"}'; exit; }

$st = $db->prepare('INSERT INTO users (phone,name,edition,version_code,install_id,consent_at,created_at,ip_hash)
  VALUES (?,?,?,?,?,?,datetime("now"),?) ON CONFLICT(phone) DO UPDATE SET name=excluded.name, edition=excluded.edition, version_code=excluded.version_code');
$st->execute([
  $phone, mb_substr((string)($in['name'] ?? ''), 0, 80), mb_substr((string)($in['edition'] ?? ''), 0, 10),
  (int)($in['versionCode'] ?? 0), mb_substr((string)($in['installId'] ?? ''), 0, 64), (int)(($in['consentAt'] ?? 0) / 1000), $ipHash,
]);
echo '{"ok":true}';
