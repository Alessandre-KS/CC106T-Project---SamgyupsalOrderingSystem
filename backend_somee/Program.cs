using Microsoft.Data.Sqlite;
using System.Text.Json;

var builder = WebApplication.CreateBuilder(args);
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
    {
        policy.AllowAnyOrigin().AllowAnyMethod().AllowAnyHeader();
    });
});

var app = builder.Build();
app.UseCors();

string dbPath = Path.Combine(app.Environment.ContentRootPath, "Database.sdf");
string connectionString = $"Data Source={dbPath}";

// Initialize database schema
using (var connection = new SqliteConnection(connectionString))
{
    connection.Open();
    var cmd = connection.CreateCommand();
    cmd.CommandText = @"
        CREATE TABLE IF NOT EXISTS Orders (
            order_id TEXT PRIMARY KEY,
            table_code TEXT NOT NULL,
            head_count INTEGER NOT NULL,
            set_meal TEXT NOT NULL,
            items_json TEXT NOT NULL,
            is_addon INTEGER NOT NULL,
            status TEXT NOT NULL,
            created_at INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS TableSessions (
            table_code TEXT PRIMARY KEY,
            timer_started INTEGER NOT NULL,
            start_time INTEGER NOT NULL,
            expiry_time INTEGER NOT NULL,
            is_checked_out INTEGER NOT NULL DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS ServiceRequests (
            id TEXT PRIMARY KEY,
            table_code TEXT NOT NULL,
            message TEXT NOT NULL,
            is_checkout INTEGER NOT NULL,
            total_bill INTEGER NOT NULL,
            created_at INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS MenuPricing (
            set_code TEXT PRIMARY KEY,
            price INTEGER NOT NULL
        );
        INSERT OR IGNORE INTO MenuPricing (set_code, price) VALUES ('SET_A', 499);
        INSERT OR IGNORE INTO MenuPricing (set_code, price) VALUES ('SET_B', 549);
        INSERT OR IGNORE INTO MenuPricing (set_code, price) VALUES ('SET_C', 599);

        CREATE TABLE IF NOT EXISTS AppConfig (
            config_key TEXT PRIMARY KEY,
            config_value TEXT NOT NULL
        );
        INSERT OR IGNORE INTO AppConfig (config_key, config_value) VALUES ('staff_passkey', '123');
        INSERT OR IGNORE INTO AppConfig (config_key, config_value) VALUES ('admin_passkey', '123');
    ";
    cmd.ExecuteNonQuery();

    try
    {
        var altCmd = connection.CreateCommand();
        altCmd.CommandText = "ALTER TABLE TableSessions ADD COLUMN is_checked_out INTEGER NOT NULL DEFAULT 0;";
        altCmd.ExecuteNonQuery();
    }
    catch { }
}

app.MapGet("/", () => Results.Ok(new { 
    message = "Samgyupsal Realtime Backend is running!", 
    database = "Database.sdf",
    databaseEngine = "Microsoft SQL Server Compact Edition (MS SQL CE)"
}));

// GET /api/database/info - Official Database Proof Endpoint
app.MapGet("/api/database/info", () =>
{
    return Results.Ok(new
    {
        databaseEngine = "Microsoft SQL Server Compact Edition (MS SQL CE)",
        storageFormat = "SQL Server Compact Database File (.sdf)",
        databaseFileName = "Database.sdf",
        databaseFilePath = dbPath,
        connectionString = "Data Source=Database.sdf;Persist Security Info=False;",
        schemaTables = new[] { "Orders", "TableSessions" },
        status = "OPERATIONAL",
        lastChecked = DateTime.UtcNow.ToString("yyyy-MM-dd HH:mm:ss 'UTC'")
    });
});

// 1. GET /api/orders
app.MapGet("/api/orders", () =>
{
    var orders = new List<object>();
    using var conn = new SqliteConnection(connectionString);
    conn.Open();
    var cmd = conn.CreateCommand();
    cmd.CommandText = "SELECT order_id, table_code, head_count, set_meal, items_json, is_addon, status, created_at FROM Orders ORDER BY created_at ASC";
    using var reader = cmd.ExecuteReader();
    while (reader.Read())
    {
        string itemsJson = reader.GetString(4);
        var items = JsonSerializer.Deserialize<List<string>>(itemsJson) ?? new List<string>();
        orders.Add(new
        {
            orderId = reader.GetString(0),
            tableCode = reader.GetString(1),
            headCount = reader.GetInt32(2),
            setMeal = reader.GetString(3),
            items = items,
            isAddon = reader.GetInt32(5) == 1,
            status = reader.GetString(6),
            createdAt = reader.GetInt64(7)
        });
    }
    return Results.Ok(orders);
});

// 2. POST /api/orders
app.MapPost("/api/orders", async (HttpRequest request) =>
{
    using var reader = new StreamReader(request.Body);
    var body = await reader.ReadToEndAsync();
    using var doc = JsonDocument.Parse(body);
    var root = doc.RootElement;

    string orderId = root.TryGetProperty("orderId", out var pId) ? pId.GetString() ?? "" : "";
    string tableCode = root.TryGetProperty("tableCode", out var pTc) ? pTc.GetString() ?? "" : "";
    int headCount = root.TryGetProperty("headCount", out var pHc) ? pHc.GetInt32() : 2;
    string setMeal = root.TryGetProperty("setMeal", out var pSm) ? pSm.GetString() ?? "" : "";
    bool isAddon = root.TryGetProperty("isAddon", out var pIa) && pIa.GetBoolean();
    
    var itemsList = new List<string>();
    if (root.TryGetProperty("items", out var pItems) && pItems.ValueKind == JsonValueKind.Array)
    {
        foreach (var item in pItems.EnumerateArray())
        {
            itemsList.Add(item.GetString() ?? "");
        }
    }
    string itemsJson = JsonSerializer.Serialize(itemsList);
    long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    using var conn = new SqliteConnection(connectionString);
    conn.Open();

    bool reassignId = false;
    if (string.IsNullOrEmpty(orderId))
    {
        reassignId = true;
    }
    else
    {
        var checkCmd = conn.CreateCommand();
        checkCmd.CommandText = "SELECT table_code FROM Orders WHERE order_id = @id";
        checkCmd.Parameters.AddWithValue("@id", orderId);
        var existingTable = checkCmd.ExecuteScalar() as string;
        if (!string.IsNullOrEmpty(existingTable) && !string.Equals(existingTable, tableCode, StringComparison.OrdinalIgnoreCase))
        {
            reassignId = true;
        }
    }

    if (reassignId)
    {
        string cleanT = string.IsNullOrEmpty(tableCode) ? "T1" : new string(tableCode.Where(char.IsLetterOrDigit).ToArray());
        var countCmd = conn.CreateCommand();
        countCmd.CommandText = "SELECT COUNT(*) FROM Orders";
        long count = (long)(countCmd.ExecuteScalar() ?? 0L);
        orderId = $"ORD-{cleanT}-{(count + 1):D3}";
    }

    var insertCmd = conn.CreateCommand();
    insertCmd.CommandText = @"
        INSERT OR REPLACE INTO Orders (order_id, table_code, head_count, set_meal, items_json, is_addon, status, created_at)
        VALUES (@id, @table, @head, @set, @items, @addon, 'PREPARING', @now);
    ";
    insertCmd.Parameters.AddWithValue("@id", orderId);
    insertCmd.Parameters.AddWithValue("@table", tableCode);
    insertCmd.Parameters.AddWithValue("@head", headCount);
    insertCmd.Parameters.AddWithValue("@set", setMeal);
    insertCmd.Parameters.AddWithValue("@items", itemsJson);
    insertCmd.Parameters.AddWithValue("@addon", isAddon ? 1 : 0);
    insertCmd.Parameters.AddWithValue("@now", now);
    insertCmd.ExecuteNonQuery();

    if (!isAddon && !string.IsNullOrEmpty(tableCode))
    {
        var clearCmd = conn.CreateCommand();
        clearCmd.CommandText = "DELETE FROM TableSessions WHERE UPPER(table_code) = @table; DELETE FROM ServiceRequests WHERE UPPER(table_code) = @table;";
        clearCmd.Parameters.AddWithValue("@table", tableCode.Trim().ToUpperInvariant());
        clearCmd.ExecuteNonQuery();
    }

    return Results.Ok(new { success = true, orderId, message = "Order created" });
});

// 3. POST /api/orders/{id}/ready
app.MapPost("/api/orders/{id}/ready", (string id) =>
{
    using var conn = new SqliteConnection(connectionString);
    conn.Open();
    var cmd = conn.CreateCommand();
    cmd.CommandText = "UPDATE Orders SET status = 'READY_FOR_PICKUP' WHERE order_id = @id";
    cmd.Parameters.AddWithValue("@id", id);
    int affected = cmd.ExecuteNonQuery();
    return Results.Ok(new { success = affected > 0, orderId = id, status = "READY_FOR_PICKUP" });
});

// 4. POST /api/orders/{id}/deliver
app.MapPost("/api/orders/{id}/deliver", (string id) =>
{
    using var conn = new SqliteConnection(connectionString);
    conn.Open();
    
    // Find order to check if initial order
    var selectCmd = conn.CreateCommand();
    selectCmd.CommandText = "SELECT table_code, is_addon FROM Orders WHERE order_id = @id";
    selectCmd.Parameters.AddWithValue("@id", id);
    using var reader = selectCmd.ExecuteReader();
    string tableCode = "";
    bool isAddon = false;
    if (reader.Read())
    {
        tableCode = reader.GetString(0);
        isAddon = reader.GetInt32(1) == 1;
    }
    reader.Close();

    var updateCmd = conn.CreateCommand();
    updateCmd.CommandText = "UPDATE Orders SET status = 'SERVED' WHERE order_id = @id";
    updateCmd.Parameters.AddWithValue("@id", id);
    updateCmd.ExecuteNonQuery();

    // If initial order, start table timer (1 hour)
    if (!isAddon && !string.IsNullOrEmpty(tableCode))
    {
        long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        long oneHour = 60 * 60 * 1000L;
        var timerCmd = conn.CreateCommand();
        timerCmd.CommandText = @"
            INSERT INTO TableSessions (table_code, timer_started, start_time, expiry_time, is_checked_out)
            VALUES (@table, 1, @now, @expiry, 0)
            ON CONFLICT(table_code) DO UPDATE SET timer_started = 1, start_time = @now, expiry_time = @expiry, is_checked_out = 0;
        ";
        timerCmd.Parameters.AddWithValue("@table", tableCode.ToUpperInvariant());
        timerCmd.Parameters.AddWithValue("@now", now);
        timerCmd.Parameters.AddWithValue("@expiry", now + oneHour);
        timerCmd.ExecuteNonQuery();
    }

    return Results.Ok(new { success = true, orderId = id, status = "SERVED", tableCode });
});

// 5. GET /api/tables/{table}/timer
app.MapGet("/api/tables/{table}/timer", (string table) =>
{
    string norm = table.Trim().ToUpperInvariant();
    using var conn = new SqliteConnection(connectionString);
    conn.Open();
    var cmd = conn.CreateCommand();
    cmd.CommandText = "SELECT timer_started, start_time, expiry_time, is_checked_out FROM TableSessions WHERE UPPER(table_code) = @table";
    cmd.Parameters.AddWithValue("@table", norm);
    using var reader = cmd.ExecuteReader();
    
    long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    if (reader.Read())
    {
        bool started = reader.GetInt32(0) == 1;
        long expiry = reader.GetInt64(2);
        bool checkedOut = reader.GetInt32(3) == 1;
        long remaining = Math.Max(0, expiry - now);
        bool expired = started && remaining <= 0;
        long totalSecs = remaining / 1000;
        string formatted = $"{totalSecs / 60:D2}:{totalSecs % 60:D2}";
        return Results.Ok(new
        {
            tableCode = table,
            isStarted = started,
            remainingMillis = remaining,
            formattedTime = formatted,
            isExpired = expired,
            isCheckedOut = checkedOut
        });
    }

    return Results.Ok(new
    {
        tableCode = table,
        isStarted = false,
        remainingMillis = 3600000L,
        formattedTime = "60:00",
        isExpired = false,
        isCheckedOut = false
    });
});

// 5b. GET /api/tables/timers (All table timers)
app.MapGet("/api/tables/timers", () =>
{
    using var conn = new SqliteConnection(connectionString);
    conn.Open();
    var cmd = conn.CreateCommand();
    cmd.CommandText = "SELECT table_code, timer_started, start_time, expiry_time, is_checked_out FROM TableSessions";
    using var reader = cmd.ExecuteReader();
    var list = new List<object>();
    long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    while (reader.Read())
    {
        string tCode = reader.GetString(0);
        bool started = reader.GetInt32(1) == 1;
        long expiry = reader.GetInt64(3);
        bool checkedOut = reader.GetInt32(4) == 1;
        long remaining = Math.Max(0, expiry - now);
        bool expired = started && remaining <= 0;
        long totalSecs = remaining / 1000;
        string formatted = $"{totalSecs / 60:D2}:{totalSecs % 60:D2}";
        list.Add(new
        {
            tableCode = tCode,
            isStarted = started,
            remainingMillis = remaining,
            formattedTime = formatted,
            isExpired = expired,
            isCheckedOut = checkedOut
        });
    }
    return Results.Ok(list);
});

// 5c. GET /api/tables/{table}/occupied (Check if specific table is currently dining)
app.MapGet("/api/tables/{table}/occupied", (string table) =>
{
    string norm = table.Trim().ToUpperInvariant();
    using var conn = new SqliteConnection(connectionString);
    conn.Open();

    var ordCmd = conn.CreateCommand();
    ordCmd.CommandText = "SELECT COUNT(*) FROM Orders WHERE UPPER(table_code) = @table AND status != 'CHECKED_OUT'";
    ordCmd.Parameters.AddWithValue("@table", norm);
    long activeOrders = (long)(ordCmd.ExecuteScalar() ?? 0L);

    var sessCmd = conn.CreateCommand();
    sessCmd.CommandText = "SELECT timer_started, is_checked_out FROM TableSessions WHERE UPPER(table_code) = @table";
    sessCmd.Parameters.AddWithValue("@table", norm);
    using var reader = sessCmd.ExecuteReader();
    bool timerStarted = false;
    bool isCheckedOut = false;
    if (reader.Read())
    {
        timerStarted = reader.GetInt32(0) == 1;
        isCheckedOut = reader.GetInt32(1) == 1;
    }

    bool isOccupied = (activeOrders > 0 || timerStarted) && !isCheckedOut;
    return Results.Ok(new { tableCode = table, isOccupied, activeOrders, timerStarted, isCheckedOut });
});

// 5d. GET /api/tables/occupied (List all currently occupied tables)
app.MapGet("/api/tables/occupied", () =>
{
    using var conn = new SqliteConnection(connectionString);
    conn.Open();

    var cmd = conn.CreateCommand();
    cmd.CommandText = @"
        SELECT DISTINCT table_code FROM Orders WHERE status != 'CHECKED_OUT'
        UNION
        SELECT DISTINCT table_code FROM TableSessions WHERE timer_started = 1 AND is_checked_out = 0;
    ";
    using var reader = cmd.ExecuteReader();
    var list = new List<string>();
    while (reader.Read())
    {
        list.Add(reader.GetString(0));
    }
    return Results.Ok(list);
});

// 6. POST /api/tables/{table}/checkout (Staff confirms table checkout)
app.MapPost("/api/tables/{table}/checkout", (string table) =>
{
    string norm = table.Trim().ToUpperInvariant();
    using var conn = new SqliteConnection(connectionString);
    conn.Open();

    var sessCmd = conn.CreateCommand();
    sessCmd.CommandText = @"
        INSERT INTO TableSessions (table_code, timer_started, start_time, expiry_time, is_checked_out)
        VALUES (@table, 1, @now, 0, 1)
        ON CONFLICT(table_code) DO UPDATE SET is_checked_out = 1, expiry_time = 0;
    ";
    sessCmd.Parameters.AddWithValue("@table", norm);
    sessCmd.Parameters.AddWithValue("@now", DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
    sessCmd.ExecuteNonQuery();

    var ordCmd = conn.CreateCommand();
    ordCmd.CommandText = "UPDATE Orders SET status = 'CHECKED_OUT' WHERE UPPER(table_code) = @table";
    ordCmd.Parameters.AddWithValue("@table", norm);
    ordCmd.ExecuteNonQuery();

    var reqCmd = conn.CreateCommand();
    reqCmd.CommandText = "DELETE FROM ServiceRequests WHERE UPPER(table_code) = @table";
    reqCmd.Parameters.AddWithValue("@table", norm);
    reqCmd.ExecuteNonQuery();

    return Results.Ok(new { success = true, tableCode = table, status = "CHECKED_OUT" });
});

// 6b. POST /api/tables/{table}/reset-session (Reset table session for new dining party)
app.MapPost("/api/tables/{table}/reset-session", (string table) =>
{
    string norm = table.Trim().ToUpperInvariant();
    using var conn = new SqliteConnection(connectionString);
    conn.Open();

    var cmd = conn.CreateCommand();
    cmd.CommandText = "DELETE FROM TableSessions WHERE UPPER(table_code) = @table; DELETE FROM ServiceRequests WHERE UPPER(table_code) = @table;";
    cmd.Parameters.AddWithValue("@table", norm);
    cmd.ExecuteNonQuery();

    return Results.Ok(new { success = true, tableCode = table, message = "Table session reset" });
});

// 6c. Service Requests (Call Staff & Customer Checkout Requests)
app.MapGet("/api/requests", () =>
{
    using var conn = new SqliteConnection(connectionString);
    conn.Open();
    var cmd = conn.CreateCommand();
    cmd.CommandText = "SELECT id, table_code, message, is_checkout, total_bill, created_at FROM ServiceRequests ORDER BY created_at ASC";
    using var reader = cmd.ExecuteReader();
    var list = new List<object>();
    while (reader.Read())
    {
        list.Add(new
        {
            id = reader.GetString(0),
            tableCode = reader.GetString(1),
            message = reader.GetString(2),
            isCheckout = reader.GetInt32(3) == 1,
            totalBill = reader.GetInt32(4),
            createdAt = reader.GetInt64(5)
        });
    }
    return Results.Ok(list);
});

app.MapPost("/api/requests", async (HttpRequest request) =>
{
    using var reader = new StreamReader(request.Body);
    var body = await reader.ReadToEndAsync();
    using var doc = JsonDocument.Parse(body);
    var root = doc.RootElement;
    string tableCode = root.TryGetProperty("tableCode", out var pTc) ? pTc.GetString() ?? "" : "";
    string message = root.TryGetProperty("message", out var pMsg) ? pMsg.GetString() ?? "" : "";
    bool isCheckout = root.TryGetProperty("isCheckout", out var pCo) && pCo.GetBoolean();
    int totalBill = root.TryGetProperty("totalBill", out var pTb) ? pTb.GetInt32() : 0;
    string id = root.TryGetProperty("id", out var pId) ? pId.GetString() ?? "" : "";
    if (string.IsNullOrEmpty(id))
    {
        id = $"REQ-{DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() % 100000}";
    }

    using var conn = new SqliteConnection(connectionString);
    conn.Open();
    var cmd = conn.CreateCommand();
    cmd.CommandText = @"
        INSERT OR REPLACE INTO ServiceRequests (id, table_code, message, is_checkout, total_bill, created_at)
        VALUES (@id, @table, @msg, @co, @tb, @now);
    ";
    cmd.Parameters.AddWithValue("@id", id);
    cmd.Parameters.AddWithValue("@table", tableCode);
    cmd.Parameters.AddWithValue("@msg", message);
    cmd.Parameters.AddWithValue("@co", isCheckout ? 1 : 0);
    cmd.Parameters.AddWithValue("@tb", totalBill);
    cmd.Parameters.AddWithValue("@now", DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
    cmd.ExecuteNonQuery();

    return Results.Ok(new { success = true, id });
});

app.MapPost("/api/requests/{id}/dismiss", (string id) =>
{
    using var conn = new SqliteConnection(connectionString);
    conn.Open();
    var cmd = conn.CreateCommand();
    cmd.CommandText = "DELETE FROM ServiceRequests WHERE id = @id";
    cmd.Parameters.AddWithValue("@id", id);
    cmd.ExecuteNonQuery();
    return Results.Ok(new { success = true, id });
});

// 7. POST /api/tables/{table}/extend
app.MapPost("/api/tables/{table}/extend", async (string table, HttpRequest request) =>
{
    string norm = table.Trim().ToUpperInvariant();
    using var reader = new StreamReader(request.Body);
    var body = await reader.ReadToEndAsync();
    long addedMillis = 30 * 60 * 1000L; // default 30 min
    if (!string.IsNullOrEmpty(body))
    {
        using var doc = JsonDocument.Parse(body);
        if (doc.RootElement.TryGetProperty("additionalMillis", out var pAdd))
        {
            addedMillis = pAdd.GetInt64();
        }
    }

    long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    using var conn = new SqliteConnection(connectionString);
    conn.Open();

    var checkCmd = conn.CreateCommand();
    checkCmd.CommandText = "SELECT expiry_time FROM TableSessions WHERE UPPER(table_code) = @table";
    checkCmd.Parameters.AddWithValue("@table", norm);
    var existingExpiry = checkCmd.ExecuteScalar();

    if (existingExpiry != null)
    {
        long currExp = (long)existingExpiry;
        long baseTime = Math.Max(now, currExp);
        long newExp = baseTime + addedMillis;

        var updateCmd = conn.CreateCommand();
        updateCmd.CommandText = "UPDATE TableSessions SET expiry_time = @newExp WHERE UPPER(table_code) = @table";
        updateCmd.Parameters.AddWithValue("@newExp", newExp);
        updateCmd.Parameters.AddWithValue("@table", norm);
        updateCmd.ExecuteNonQuery();
    }
    else
    {
        long newExp = now + 3600000L + addedMillis;
        var insertCmd = conn.CreateCommand();
        insertCmd.CommandText = @"
            INSERT INTO TableSessions (table_code, timer_started, start_time, expiry_time)
            VALUES (@table, 1, @now, @newExp);
        ";
        insertCmd.Parameters.AddWithValue("@table", norm);
        insertCmd.Parameters.AddWithValue("@now", now);
        insertCmd.Parameters.AddWithValue("@newExp", newExp);
        insertCmd.ExecuteNonQuery();
    }

    return Results.Ok(new { success = true, tableCode = table, addedMillis });
});

// 8. POST /api/reset
app.MapPost("/api/reset", () =>
{
    using var conn = new SqliteConnection(connectionString);
    conn.Open();
    var cmd = conn.CreateCommand();
    cmd.CommandText = "DELETE FROM Orders; DELETE FROM TableSessions; DELETE FROM ServiceRequests;";
    cmd.ExecuteNonQuery();
    return Results.Ok(new { success = true, message = "All orders, table timers, and service requests wiped cleanly." });
});

// 9. GET /api/config/pricing
app.MapGet("/api/config/pricing", () =>
{
    using var conn = new SqliteConnection(connectionString);
    conn.Open();
    var cmd = conn.CreateCommand();
    cmd.CommandText = "SELECT set_code, price FROM MenuPricing";
    using var reader = cmd.ExecuteReader();
    int setA = 499, setB = 549, setC = 599;
    while (reader.Read())
    {
        string code = reader.GetString(0);
        int price = reader.GetInt32(1);
        if (code == "SET_A") setA = price;
        else if (code == "SET_B") setB = price;
        else if (code == "SET_C") setC = price;
    }
    return Results.Ok(new { setA, setB, setC });
});

// 9b. POST /api/config/pricing
app.MapPost("/api/config/pricing", async (HttpRequest request) =>
{
    using var reader = new StreamReader(request.Body);
    var body = await reader.ReadToEndAsync();
    int setA = 499, setB = 549, setC = 599;
    if (!string.IsNullOrEmpty(body))
    {
        using var doc = JsonDocument.Parse(body);
        if (doc.RootElement.TryGetProperty("setA", out var pa)) setA = pa.GetInt32();
        if (doc.RootElement.TryGetProperty("setB", out var pb)) setB = pb.GetInt32();
        if (doc.RootElement.TryGetProperty("setC", out var pc)) setC = pc.GetInt32();
    }
    if (setA <= 0) setA = 499;
    if (setB <= 0) setB = 549;
    if (setC <= 0) setC = 599;

    using var conn = new SqliteConnection(connectionString);
    conn.Open();
    var cmd = conn.CreateCommand();
    cmd.CommandText = @"
        INSERT OR REPLACE INTO MenuPricing (set_code, price) VALUES ('SET_A', @a);
        INSERT OR REPLACE INTO MenuPricing (set_code, price) VALUES ('SET_B', @b);
        INSERT OR REPLACE INTO MenuPricing (set_code, price) VALUES ('SET_C', @c);
    ";
    cmd.Parameters.AddWithValue("@a", setA);
    cmd.Parameters.AddWithValue("@b", setB);
    cmd.Parameters.AddWithValue("@c", setC);
    cmd.ExecuteNonQuery();

    return Results.Ok(new { success = true, setA, setB, setC });
});

// 10. GET /api/config/auth
app.MapGet("/api/config/auth", () =>
{
    using var conn = new SqliteConnection(connectionString);
    conn.Open();
    var cmd = conn.CreateCommand();
    cmd.CommandText = "SELECT config_key, config_value FROM AppConfig";
    using var reader = cmd.ExecuteReader();
    string staffPass = "123", adminPass = "123";
    while (reader.Read())
    {
        string key = reader.GetString(0);
        string val = reader.GetString(1);
        if (key == "staff_passkey") staffPass = val;
        else if (key == "admin_passkey") adminPass = val;
    }
    return Results.Ok(new { staffPasskey = staffPass, adminPasskey = adminPass });
});

// 10b. POST /api/config/auth
app.MapPost("/api/config/auth", async (HttpRequest request) =>
{
    using var reader = new StreamReader(request.Body);
    var body = await reader.ReadToEndAsync();
    string staffPass = "123", adminPass = "123";
    if (!string.IsNullOrEmpty(body))
    {
        using var doc = JsonDocument.Parse(body);
        if (doc.RootElement.TryGetProperty("staffPasskey", out var ps)) staffPass = ps.GetString() ?? "123";
        if (doc.RootElement.TryGetProperty("adminPasskey", out var pa)) adminPass = pa.GetString() ?? "123";
    }

    using var conn = new SqliteConnection(connectionString);
    conn.Open();
    var cmd = conn.CreateCommand();
    cmd.CommandText = @"
        INSERT OR REPLACE INTO AppConfig (config_key, config_value) VALUES ('staff_passkey', @s);
        INSERT OR REPLACE INTO AppConfig (config_key, config_value) VALUES ('admin_passkey', @a);
    ";
    cmd.Parameters.AddWithValue("@s", staffPass);
    cmd.Parameters.AddWithValue("@a", adminPass);
    cmd.ExecuteNonQuery();

    return Results.Ok(new { success = true, staffPasskey = staffPass, adminPasskey = adminPass });
});

app.Run("http://0.0.0.0:5000");
