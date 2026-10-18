<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// -------------------------------------------------------------
// Database Connection (MySQL or SQLite fallback)
// -------------------------------------------------------------
$host = 'localhost';
$dbname = 'samgyup_db';
$username = 'root';
$password = '';

try {
    if (!empty($password) || ($username !== 'root' && !empty($username))) {
        $pdo = new PDO("mysql:host=$host;dbname=$dbname;charset=utf8mb4", $username, $password);
    } else {
        $pdo = new PDO('sqlite:' . __DIR__ . '/Database.sdf');
    }
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
} catch (Exception $e) {
    // Default to local SQLite/SDF file
    $pdo = new PDO('sqlite:' . __DIR__ . '/Database.sdf');
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
}

// -------------------------------------------------------------
// Schema Initialization
// -------------------------------------------------------------
$pdo->exec("
    CREATE TABLE IF NOT EXISTS Orders (
        order_id VARCHAR(50) PRIMARY KEY,
        table_code VARCHAR(50) NOT NULL,
        head_count INT NOT NULL,
        set_meal VARCHAR(50) NOT NULL,
        items_json TEXT NOT NULL,
        is_addon INT NOT NULL,
        status VARCHAR(50) NOT NULL,
        created_at BIGINT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS TableSessions (
        table_code VARCHAR(50) PRIMARY KEY,
        timer_started INT NOT NULL,
        start_time BIGINT NOT NULL,
        expiry_time BIGINT NOT NULL,
        is_checked_out INT NOT NULL DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS ServiceRequests (
        id VARCHAR(50) PRIMARY KEY,
        table_code VARCHAR(50) NOT NULL,
        message TEXT NOT NULL,
        is_checkout INT NOT NULL,
        total_bill INT NOT NULL,
        created_at BIGINT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS MenuPricing (
        set_code VARCHAR(50) PRIMARY KEY,
        price INT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS AppConfig (
        config_key VARCHAR(50) PRIMARY KEY,
        config_value VARCHAR(255) NOT NULL
    );
");

// Upgrade schema if needed
try { $pdo->exec("ALTER TABLE TableSessions ADD COLUMN is_checked_out INT NOT NULL DEFAULT 0"); } catch (Exception $e) {}

// Default seed data
try {
    $countPricing = (int)$pdo->query("SELECT COUNT(*) FROM MenuPricing")->fetchColumn();
    if ($countPricing === 0) {
        $pdo->exec("REPLACE INTO MenuPricing (set_code, price) VALUES ('SET_A', 499), ('SET_B', 549), ('SET_C', 599)");
    }
} catch (Exception $e) {}

try {
    $countConfig = (int)$pdo->query("SELECT COUNT(*) FROM AppConfig")->fetchColumn();
    if ($countConfig === 0) {
        $pdo->exec("REPLACE INTO AppConfig (config_key, config_value) VALUES ('staff_passkey', '123'), ('admin_passkey', '123')");
    }
} catch (Exception $e) {}

// -------------------------------------------------------------
// Request Routing
// -------------------------------------------------------------
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$uri = parse_url($_SERVER['REQUEST_URI'] ?? '', PHP_URL_PATH);

// Normalize path to match routes (e.g. /api/orders)
$path = $uri;
if (($pos = strpos($path, '/api')) !== false) {
    $path = substr($path, $pos);
}
$path = rtrim($path, '/');
$action = $_GET['action'] ?? '';

// Helper to get JSON payload
function getJsonInput() {
    $raw = file_get_contents('php://input');
    return json_decode($raw, true) ?: [];
}

// 1. Root / Server Info / Database Info
if ($path === '' || $path === '/api' || $path === '/api/database/info' || $action === 'info') {
    echo json_encode([
        'status' => 'online',
        'server' => 'Samgyupsal Realtime Backend (x10hosting)',
        'databaseEngine' => 'Microsoft SQL Server Compact Edition (MS SQL CE) / MySQL',
        'databaseFile' => 'Database.sdf',
        'lastChecked' => gmdate('Y-m-d H:i:s \U\T\C')
    ]);
    exit;
}

// 2. Orders: GET /api/orders
if (($method === 'GET' && $path === '/api/orders') || $action === 'get_orders') {
    $stmt = $pdo->query("SELECT * FROM Orders ORDER BY created_at ASC");
    $orders = [];
    while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
        $orders[] = [
            'orderId' => $row['order_id'],
            'tableCode' => $row['table_code'],
            'headCount' => (int)$row['head_count'],
            'setMeal' => $row['set_meal'],
            'items' => json_decode($row['items_json'], true) ?: [],
            'isAddon' => (int)$row['is_addon'] === 1,
            'status' => $row['status'],
            'createdAt' => (int)$row['created_at']
        ];
    }
    echo json_encode($orders);
    exit;
}

// 3. Orders: POST /api/orders
if (($method === 'POST' && $path === '/api/orders') || $action === 'create_order') {
    $data = getJsonInput();
    $orderId = trim($data['orderId'] ?? '');
    $tableCode = trim($data['tableCode'] ?? 'Table 1');
    $headCount = (int)($data['headCount'] ?? 2);
    $setMeal = $data['setMeal'] ?? 'SET MEAL A';
    $isAddon = !empty($data['isAddon']) ? 1 : 0;
    $itemsJson = json_encode($data['items'] ?? []);
    $now = round(microtime(true) * 1000);

    if (empty($orderId)) {
        $count = (int)$pdo->query("SELECT COUNT(*) FROM Orders")->fetchColumn();
        $cleanT = preg_replace('/[^A-Za-z0-9]/', '', $tableCode);
        $orderId = sprintf("ORD-%s-%03d", $cleanT ?: 'T1', $count + 1);
    }

    $stmt = $pdo->prepare("REPLACE INTO Orders (order_id, table_code, head_count, set_meal, items_json, is_addon, status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'PREPARING', ?)");
    $stmt->execute([$orderId, $tableCode, $headCount, $setMeal, $itemsJson, $isAddon, $now]);

    // Clear old table session and requests on initial order
    if ($isAddon === 0 && !empty($tableCode)) {
        $tUpper = strtoupper($tableCode);
        $pdo->prepare("DELETE FROM TableSessions WHERE UPPER(table_code) = ?")->execute([$tUpper]);
        $pdo->prepare("DELETE FROM ServiceRequests WHERE UPPER(table_code) = ?")->execute([$tUpper]);
    }

    echo json_encode(['success' => true, 'orderId' => $orderId, 'message' => 'Order created']);
    exit;
}

// 4. Ready Order: POST /api/orders/{id}/ready
if (preg_match('#^/api/orders/([^/]+)/ready$#', $path, $m) || $action === 'ready_order') {
    $id = $m[1] ?? ($_GET['id'] ?? '');
    $stmt = $pdo->prepare("UPDATE Orders SET status = 'READY_FOR_PICKUP' WHERE order_id = ?");
    $stmt->execute([$id]);
    echo json_encode(['success' => true, 'orderId' => $id, 'status' => 'READY_FOR_PICKUP']);
    exit;
}

// 5. Deliver Order: POST /api/orders/{id}/deliver
if (preg_match('#^/api/orders/([^/]+)/deliver$#', $path, $m) || $action === 'deliver_order') {
    $id = $m[1] ?? ($_GET['id'] ?? '');
    $stmt = $pdo->prepare("SELECT table_code, is_addon FROM Orders WHERE order_id = ?");
    $stmt->execute([$id]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);

    $pdo->prepare("UPDATE Orders SET status = 'SERVED' WHERE order_id = ?")->execute([$id]);

    $tableCode = $row['table_code'] ?? '';
    if ($row && (int)$row['is_addon'] === 0) {
        $table = strtoupper(trim($tableCode));
        $now = round(microtime(true) * 1000);
        $oneHour = 3600000;
        $pdo->prepare("REPLACE INTO TableSessions (table_code, timer_started, start_time, expiry_time, is_checked_out) VALUES (?, 1, ?, ?, 0)")
            ->execute([$table, $now, $now + $oneHour]);
    }
    echo json_encode(['success' => true, 'orderId' => $id, 'status' => 'SERVED', 'tableCode' => $tableCode]);
    exit;
}

// 6. Table Timers: GET /api/tables/timers (All table timers)
if (($method === 'GET' && $path === '/api/tables/timers') || $action === 'get_all_timers') {
    $stmt = $pdo->query("SELECT * FROM TableSessions");
    $list = [];
    $now = round(microtime(true) * 1000);
    while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
        $started = (int)$row['timer_started'] === 1;
        $expiry = (int)$row['expiry_time'];
        $checkedOut = (int)($row['is_checked_out'] ?? 0) === 1;
        $remaining = max(0, $expiry - $now);
        $totalSecs = floor($remaining / 1000);
        $mins = floor($totalSecs / 60);
        $secs = $totalSecs % 60;
        $list[] = [
            'tableCode' => $row['table_code'],
            'isStarted' => $started,
            'remainingMillis' => $remaining,
            'formattedTime' => sprintf("%02d:%02d", $mins, $secs),
            'isExpired' => $started && $remaining <= 0,
            'isCheckedOut' => $checkedOut
        ];
    }
    echo json_encode($list);
    exit;
}

// 7. Table Occupied: GET /api/tables/occupied (List all currently occupied tables)
if (($method === 'GET' && $path === '/api/tables/occupied') || $action === 'get_occupied') {
    $stmt = $pdo->query("
        SELECT DISTINCT table_code FROM Orders WHERE status != 'CHECKED_OUT'
        UNION
        SELECT DISTINCT table_code FROM TableSessions WHERE timer_started = 1 AND is_checked_out = 0
    ");
    $list = $stmt->fetchAll(PDO::FETCH_COLUMN) ?: [];
    echo json_encode(array_values($list));
    exit;
}

// 8. Specific Table Occupied: GET /api/tables/{table}/occupied
if (preg_match('#^/api/tables/([^/]+)/occupied$#', $path, $m) || $action === 'check_occupied') {
    $table = strtoupper(trim(urldecode($m[1] ?? ($_GET['table'] ?? ''))));
    $stmt = $pdo->prepare("SELECT COUNT(*) FROM Orders WHERE UPPER(table_code) = ? AND status != 'CHECKED_OUT'");
    $stmt->execute([$table]);
    $activeOrders = (int)$stmt->fetchColumn();

    $stmt2 = $pdo->prepare("SELECT timer_started, is_checked_out FROM TableSessions WHERE UPPER(table_code) = ?");
    $stmt2->execute([$table]);
    $sRow = $stmt2->fetch(PDO::FETCH_ASSOC);
    $timerStarted = $sRow && (int)$sRow['timer_started'] === 1;
    $isCheckedOut = $sRow && (int)($sRow['is_checked_out'] ?? 0) === 1;

    $isOccupied = ($activeOrders > 0 || $timerStarted) && !$isCheckedOut;
    echo json_encode([
        'tableCode' => $table,
        'isOccupied' => $isOccupied,
        'activeOrders' => $activeOrders,
        'timerStarted' => $timerStarted,
        'isCheckedOut' => $isCheckedOut
    ]);
    exit;
}

// 9. Specific Table Timer: GET /api/tables/{table}/timer
if (preg_match('#^/api/tables/([^/]+)/timer$#', $path, $m) || $action === 'get_timer') {
    $table = strtoupper(trim(urldecode($m[1] ?? ($_GET['table'] ?? ''))));
    $stmt = $pdo->prepare("SELECT * FROM TableSessions WHERE UPPER(table_code) = ?");
    $stmt->execute([$table]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);

    $now = round(microtime(true) * 1000);
    if ($row) {
        $started = (int)$row['timer_started'] === 1;
        $expiry = (int)$row['expiry_time'];
        $checkedOut = (int)($row['is_checked_out'] ?? 0) === 1;
        $remaining = max(0, $expiry - $now);
        $totalSecs = floor($remaining / 1000);
        $mins = floor($totalSecs / 60);
        $secs = $totalSecs % 60;
        echo json_encode([
            'tableCode' => $table,
            'isStarted' => $started,
            'remainingMillis' => $remaining,
            'formattedTime' => sprintf("%02d:%02d", $mins, $secs),
            'isExpired' => $started && $remaining <= 0,
            'isCheckedOut' => $checkedOut
        ]);
        exit;
    }
    echo json_encode([
        'tableCode' => $table,
        'isStarted' => false,
        'remainingMillis' => 3600000,
        'formattedTime' => "60:00",
        'isExpired' => false,
        'isCheckedOut' => false
    ]);
    exit;
}

// 10. Table Checkout: POST /api/tables/{table}/checkout
if (preg_match('#^/api/tables/([^/]+)/checkout$#', $path, $m) || $action === 'checkout_table') {
    $table = strtoupper(trim(urldecode($m[1] ?? ($_GET['table'] ?? ''))));
    $now = round(microtime(true) * 1000);

    $pdo->prepare("REPLACE INTO TableSessions (table_code, timer_started, start_time, expiry_time, is_checked_out) VALUES (?, 1, ?, 0, 1)")
        ->execute([$table, $now]);
    $pdo->prepare("UPDATE Orders SET status = 'CHECKED_OUT' WHERE UPPER(table_code) = ?")->execute([$table]);
    $pdo->prepare("DELETE FROM ServiceRequests WHERE UPPER(table_code) = ?")->execute([$table]);

    echo json_encode(['success' => true, 'tableCode' => $table, 'status' => 'CHECKED_OUT']);
    exit;
}

// 11. Table Reset Session: POST /api/tables/{table}/reset-session
if (preg_match('#^/api/tables/([^/]+)/reset-session$#', $path, $m) || $action === 'reset_session') {
    $table = strtoupper(trim(urldecode($m[1] ?? ($_GET['table'] ?? ''))));
    $pdo->prepare("DELETE FROM TableSessions WHERE UPPER(table_code) = ?")->execute([$table]);
    $pdo->prepare("DELETE FROM ServiceRequests WHERE UPPER(table_code) = ?")->execute([$table]);
    echo json_encode(['success' => true, 'tableCode' => $table, 'message' => 'Table session reset']);
    exit;
}

// 12. Table Extend Timer: POST /api/tables/{table}/extend
if (preg_match('#^/api/tables/([^/]+)/extend$#', $path, $m) || $action === 'extend_timer') {
    $table = strtoupper(trim(urldecode($m[1] ?? ($_GET['table'] ?? ''))));
    $data = getJsonInput();
    $added = (int)($data['additionalMillis'] ?? 1800000); // 30m default

    $stmt = $pdo->prepare("SELECT expiry_time FROM TableSessions WHERE UPPER(table_code) = ?");
    $stmt->execute([$table]);
    $currExp = $stmt->fetchColumn();

    $now = round(microtime(true) * 1000);
    if ($currExp !== false) {
        $base = max($now, (int)$currExp);
        $newExp = $base + $added;
        $pdo->prepare("UPDATE TableSessions SET expiry_time = ? WHERE UPPER(table_code) = ?")->execute([$newExp, $table]);
    } else {
        $newExp = $now + 3600000 + $added;
        $pdo->prepare("REPLACE INTO TableSessions (table_code, timer_started, start_time, expiry_time, is_checked_out) VALUES (?, 1, ?, ?, 0)")
            ->execute([$table, $now, $newExp]);
    }
    echo json_encode(['success' => true, 'tableCode' => $table, 'addedMillis' => $added]);
    exit;
}

// 13. Service Requests: GET /api/requests
if (($method === 'GET' && $path === '/api/requests') || $action === 'get_requests') {
    $stmt = $pdo->query("SELECT * FROM ServiceRequests ORDER BY created_at ASC");
    $list = [];
    while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
        $list[] = [
            'id' => $row['id'],
            'tableCode' => $row['table_code'],
            'message' => $row['message'],
            'isCheckout' => (int)$row['is_checkout'] === 1,
            'totalBill' => (int)$row['total_bill'],
            'createdAt' => (int)$row['created_at']
        ];
    }
    echo json_encode($list);
    exit;
}

// 14. Service Requests: POST /api/requests
if (($method === 'POST' && $path === '/api/requests') || $action === 'create_request') {
    $data = getJsonInput();
    $tableCode = trim($data['tableCode'] ?? '');
    $message = trim($data['message'] ?? '');
    $isCheckout = !empty($data['isCheckout']) ? 1 : 0;
    $totalBill = (int)($data['totalBill'] ?? 0);
    $id = trim($data['id'] ?? '');
    if (empty($id)) {
        $id = 'REQ-' . (round(microtime(true) * 1000) % 100000);
    }
    $now = round(microtime(true) * 1000);

    $stmt = $pdo->prepare("REPLACE INTO ServiceRequests (id, table_code, message, is_checkout, total_bill, created_at) VALUES (?, ?, ?, ?, ?, ?)");
    $stmt->execute([$id, $tableCode, $message, $isCheckout, $totalBill, $now]);
    echo json_encode(['success' => true, 'id' => $id]);
    exit;
}

// 15. Dismiss Request: POST /api/requests/{id}/dismiss
if (preg_match('#^/api/requests/([^/]+)/dismiss$#', $path, $m) || $action === 'dismiss_request') {
    $id = $m[1] ?? ($_GET['id'] ?? '');
    $stmt = $pdo->prepare("DELETE FROM ServiceRequests WHERE id = ?");
    $stmt->execute([$id]);
    echo json_encode(['success' => true, 'id' => $id]);
    exit;
}

// 16. Wipe Database: POST /api/reset
if (($method === 'POST' && $path === '/api/reset') || $action === 'reset') {
    $pdo->exec("DELETE FROM Orders; DELETE FROM TableSessions; DELETE FROM ServiceRequests;");
    echo json_encode(['success' => true, 'message' => 'All orders, table timers, and service requests wiped cleanly.']);
    exit;
}

// 17. Menu Pricing: GET /api/config/pricing
if (($method === 'GET' && $path === '/api/config/pricing') || $action === 'get_pricing') {
    $stmt = $pdo->query("SELECT set_code, price FROM MenuPricing");
    $setA = 499; $setB = 549; $setC = 599;
    while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
        if ($row['set_code'] === 'SET_A') $setA = (int)$row['price'];
        if ($row['set_code'] === 'SET_B') $setB = (int)$row['price'];
        if ($row['set_code'] === 'SET_C') $setC = (int)$row['price'];
    }
    echo json_encode(['setA' => $setA, 'setB' => $setB, 'setC' => $setC]);
    exit;
}

// 18. Menu Pricing: POST /api/config/pricing
if (($method === 'POST' && $path === '/api/config/pricing') || $action === 'update_pricing') {
    $data = getJsonInput();
    $setA = max(1, (int)($data['setA'] ?? 499));
    $setB = max(1, (int)($data['setB'] ?? 549));
    $setC = max(1, (int)($data['setC'] ?? 599));

    $pdo->prepare("REPLACE INTO MenuPricing (set_code, price) VALUES ('SET_A', ?), ('SET_B', ?), ('SET_C', ?)")
        ->execute([$setA, $setB, $setC]);

    echo json_encode(['success' => true, 'setA' => $setA, 'setB' => $setB, 'setC' => $setC]);
    exit;
}

// 19. Auth Passkeys: GET /api/config/auth
if (($method === 'GET' && $path === '/api/config/auth') || $action === 'get_auth') {
    $stmt = $pdo->query("SELECT config_key, config_value FROM AppConfig");
    $staff = '123'; $admin = '123';
    while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
        if ($row['config_key'] === 'staff_passkey') $staff = $row['config_value'];
        if ($row['config_key'] === 'admin_passkey') $admin = $row['config_value'];
    }
    echo json_encode(['staffPasskey' => $staff, 'adminPasskey' => $admin]);
    exit;
}

// 20. Auth Passkeys: POST /api/config/auth
if (($method === 'POST' && $path === '/api/config/auth') || $action === 'update_auth') {
    $data = getJsonInput();
    $staff = trim($data['staffPasskey'] ?? '123');
    $admin = trim($data['adminPasskey'] ?? '123');

    $pdo->prepare("REPLACE INTO AppConfig (config_key, config_value) VALUES ('staff_passkey', ?), ('admin_passkey', ?)")
        ->execute([$staff, $admin]);

    echo json_encode(['success' => true, 'staffPasskey' => $staff, 'adminPasskey' => $admin]);
    exit;
}

// Fallback response
echo json_encode([
    'status' => 'online',
    'server' => 'Samgyupsal Ordering System API',
    'requested_path' => $path
]);
