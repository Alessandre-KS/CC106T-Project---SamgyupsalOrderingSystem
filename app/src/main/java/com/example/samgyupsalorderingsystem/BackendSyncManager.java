package com.example.samgyupsalorderingsystem;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages real-time network sync with the central MS SQL CE / Somee / x10hosting backend.
 * Provides fallback to local in-memory state if server is offline.
 */
public class BackendSyncManager {

    private static final String TAG = "BackendSyncManager";

    // Connected to live cloud backend on x10hosting
    public static String BASE_URL = "https://cc106tsamgyup-orders.x10.mx";
    public static final String API_KEY = "samgyup-cc106-secure-token-2026";

    private static BackendSyncManager instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BackendSyncManager() {}

    public static synchronized BackendSyncManager getInstance() {
        if (instance == null) {
            instance = new BackendSyncManager();
        }
        return instance;
    }

    public static void setBaseUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            String clean = url.trim();
            if (clean.endsWith("/")) {
                clean = clean.substring(0, clean.length() - 1);
            }
            BASE_URL = clean;
        }
    }

    public interface TimerCallback {
        void onTimerSync(boolean started, long remainingMillis, String formattedTime, boolean expired);
    }

    public interface TableOccupiedCallback {
        void onResult(boolean isOccupied);
    }

    public interface OccupiedTablesCallback {
        void onResult(java.util.Set<String> occupiedTables);
    }

    public void checkTableOccupied(String tableCode, TableOccupiedCallback callback) {
        executor.execute(() -> {
            boolean occupied = false;
            try {
                String norm = AuthManager.formatTableCode(tableCode);
                if (norm == null) norm = tableCode != null ? tableCode.trim() : "";
                String encoded = java.net.URLEncoder.encode(norm, "UTF-8");
                String response = get(BASE_URL + "/api/tables/" + encoded + "/occupied");
                if (response != null) {
                    JSONObject obj = new JSONObject(response);
                    occupied = obj.optBoolean("isOccupied", false);
                } else {
                    occupied = OrderRepository.getInstance().hasInitialOrder(norm)
                            || (TableTimerManager.getInstance().isTimerStarted(norm) && !TableTimerManager.getInstance().isTableCheckedOut(norm));
                }
            } catch (Exception e) {
                String norm = AuthManager.formatTableCode(tableCode);
                if (norm == null) norm = tableCode != null ? tableCode.trim() : "";
                occupied = OrderRepository.getInstance().hasInitialOrder(norm)
                        || (TableTimerManager.getInstance().isTimerStarted(norm) && !TableTimerManager.getInstance().isTableCheckedOut(norm));
            }
            final boolean result = occupied;
            mainHandler.post(() -> {
                if (callback != null) callback.onResult(result);
            });
        });
    }

    public void getOccupiedTables(OccupiedTablesCallback callback) {
        executor.execute(() -> {
            java.util.Set<String> set = new java.util.HashSet<>();
            try {
                String response = get(BASE_URL + "/api/tables/occupied");
                if (response != null) {
                    JSONArray arr = new JSONArray(response);
                    for (int i = 0; i < arr.length(); i++) {
                        String t = arr.getString(i);
                        set.add(t.toUpperCase(java.util.Locale.US));
                        String formatted = AuthManager.formatTableCode(t);
                        if (formatted != null) set.add(formatted.toUpperCase(java.util.Locale.US));
                    }
                }
            } catch (Exception ignored) {}
            mainHandler.post(() -> {
                if (callback != null) callback.onResult(set);
            });
        });
    }

    /**
     * Sends an order created by customer to the backend database.
     */
    public void sendOrder(BacksiteOrder order, Runnable onComplete) {
        executor.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("orderId", order.getOrderId());
                json.put("tableCode", order.getTableCode());
                json.put("headCount", order.getHeadCount());
                json.put("setMeal", order.getSetMeal());
                json.put("isAddon", order.isAddon());

                JSONArray itemsArr = new JSONArray();
                for (String it : order.getItems()) {
                    itemsArr.put(it);
                }
                json.put("items", itemsArr);

                postJson(BASE_URL + "/api/orders", json.toString());
            } catch (Exception e) {
                Log.w(TAG, "Failed to send order to backend (using local fallback): " + e.getMessage());
            } finally {
                if (onComplete != null) {
                    mainHandler.post(onComplete);
                }
            }
        });
    }

    /**
     * Polls the server to sync active orders in OrderRepository.
     */
    public void syncOrders(Runnable onComplete) {
        executor.execute(() -> {
            try {
                String response = get(BASE_URL + "/api/orders");
                if (response != null) {
                    JSONArray arr = new JSONArray(response);
                    // Update OrderRepository
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String id = obj.optString("orderId");
                        String table = obj.optString("tableCode");
                        int head = obj.optInt("headCount", 2);
                        String set = obj.optString("setMeal");
                        boolean addon = obj.optBoolean("isAddon", false);
                        String statusStr = obj.optString("status", "PREPARING");

                        JSONArray itemsArr = obj.optJSONArray("items");
                        List<String> items = new ArrayList<>();
                        if (itemsArr != null) {
                            for (int j = 0; j < itemsArr.length(); j++) {
                                items.add(itemsArr.getString(j));
                            }
                        }

                        BacksiteOrder existing = OrderRepository.getInstance().findById(id);
                        if (existing == null) {
                            BacksiteOrder newOrder = OrderRepository.getInstance().createOrderWithId(id, table, head, set, items, addon);
                            if ("READY_FOR_PICKUP".equalsIgnoreCase(statusStr)) newOrder.markReady();
                            else if ("SERVED".equalsIgnoreCase(statusStr)) newOrder.markServed();
                            else if ("CHECKED_OUT".equalsIgnoreCase(statusStr)) newOrder.markCheckedOut();
                        } else {
                            if (!existing.getTableCode().equalsIgnoreCase(table)) {
                                OrderRepository.getInstance().removeOrder(id);
                                BacksiteOrder replaced = OrderRepository.getInstance().createOrderWithId(id, table, head, set, items, addon);
                                if ("READY_FOR_PICKUP".equalsIgnoreCase(statusStr)) replaced.markReady();
                                else if ("SERVED".equalsIgnoreCase(statusStr)) replaced.markServed();
                                else if ("CHECKED_OUT".equalsIgnoreCase(statusStr)) replaced.markCheckedOut();
                            } else {
                                if ("READY_FOR_PICKUP".equalsIgnoreCase(statusStr) && existing.isPreparing()) {
                                    existing.markReady();
                                } else if ("SERVED".equalsIgnoreCase(statusStr) && !existing.isServed()) {
                                    existing.markServed();
                                } else if ("CHECKED_OUT".equalsIgnoreCase(statusStr) && !existing.isCheckedOut()) {
                                    existing.markCheckedOut();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Sync orders failed: " + e.getMessage());
            } finally {
                if (onComplete != null) {
                    mainHandler.post(onComplete);
                }
            }
        });
    }

    public void markOrderReady(String orderId, Runnable onComplete) {
        executor.execute(() -> {
            try {
                postJson(BASE_URL + "/api/orders/" + orderId + "/ready", "{}");
            } catch (Exception e) {
                Log.w(TAG, "markOrderReady error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    public void markOrderDelivered(String orderId, Runnable onComplete) {
        executor.execute(() -> {
            try {
                postJson(BASE_URL + "/api/orders/" + orderId + "/deliver", "{}");
            } catch (Exception e) {
                Log.w(TAG, "markOrderDelivered error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    public void syncTimer(String tableCode, TimerCallback callback) {
        executor.execute(() -> {
            try {
                String response = get(BASE_URL + "/api/tables/" + tableCode + "/timer");
                if (response != null && callback != null) {
                    JSONObject obj = new JSONObject(response);
                    boolean started = obj.optBoolean("isStarted", false);
                    long remaining = obj.optLong("remainingMillis", 3600000L);
                    String formatted = obj.optString("formattedTime", "60:00");
                    boolean expired = obj.optBoolean("isExpired", false);
                    boolean checkedOut = obj.optBoolean("isCheckedOut", false);

                    if (checkedOut) {
                        TableTimerManager.getInstance().checkoutTable(tableCode);
                    } else if (started) {
                        TableTimerManager.getInstance().syncTimer(tableCode, remaining);
                    } else {
                        TableTimerManager.getInstance().resetTable(tableCode);
                    }

                    mainHandler.post(() -> callback.onTimerSync(started, remaining, formatted, expired));
                }
            } catch (Exception e) {
                Log.w(TAG, "syncTimer error: " + e.getMessage());
            }
        });
    }

    public void syncAllTimers(Runnable onComplete) {
        executor.execute(() -> {
            try {
                String response = get(BASE_URL + "/api/tables/timers");
                if (response != null) {
                    JSONArray arr = new JSONArray(response);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String table = obj.optString("tableCode");
                        boolean started = obj.optBoolean("isStarted", false);
                        long remaining = obj.optLong("remainingMillis", 3600000L);
                        boolean checkedOut = obj.optBoolean("isCheckedOut", false);
                        if (checkedOut) {
                            TableTimerManager.getInstance().checkoutTable(table);
                        } else if (started) {
                            TableTimerManager.getInstance().syncTimer(table, remaining);
                        } else {
                            TableTimerManager.getInstance().resetTable(table);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "syncAllTimers error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    public void resetTableSession(String tableCode, Runnable onComplete) {
        TableTimerManager.getInstance().resetTable(tableCode);
        executor.execute(() -> {
            try {
                postJson(BASE_URL + "/api/tables/" + tableCode + "/reset-session", "{}");
            } catch (Exception e) {
                Log.w(TAG, "resetTableSession error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    public void checkoutTableOnBackend(String tableCode, Runnable onComplete) {
        executor.execute(() -> {
            try {
                postJson(BASE_URL + "/api/tables/" + tableCode + "/checkout", "{}");
            } catch (Exception e) {
                Log.w(TAG, "checkoutTableOnBackend error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    public void sendRequest(String tableCode, String message, boolean isCheckout, int totalBill, Runnable onComplete) {
        executor.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("tableCode", tableCode);
                json.put("message", message);
                json.put("isCheckout", isCheckout);
                json.put("totalBill", totalBill);
                postJson(BASE_URL + "/api/requests", json.toString());
            } catch (Exception e) {
                Log.w(TAG, "sendRequest error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    public void syncRequests(Runnable onComplete) {
        executor.execute(() -> {
            try {
                String response = get(BASE_URL + "/api/requests");
                if (response != null) {
                    JSONArray arr = new JSONArray(response);
                    List<ServiceRequestManager.Request> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String id = obj.optString("id");
                        String table = obj.optString("tableCode");
                        String msg = obj.optString("message");
                        boolean isCo = obj.optBoolean("isCheckout", false);
                        int tb = obj.optInt("totalBill", 0);
                        long ts = obj.optLong("createdAt", System.currentTimeMillis());
                        list.add(new ServiceRequestManager.Request(id, table, msg, isCo, tb, ts));
                    }
                    ServiceRequestManager.getInstance().setRequests(list);
                }
            } catch (Exception e) {
                Log.w(TAG, "syncRequests error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    public void dismissRequest(String id, Runnable onComplete) {
        executor.execute(() -> {
            try {
                postJson(BASE_URL + "/api/requests/" + id + "/dismiss", "{}");
                ServiceRequestManager.getInstance().dismissRequest(id);
            } catch (Exception e) {
                Log.w(TAG, "dismissRequest error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    public void extendTimer(String tableCode, long additionalMillis, Runnable onComplete) {
        executor.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("additionalMillis", additionalMillis);
                postJson(BASE_URL + "/api/tables/" + tableCode + "/extend", json.toString());
            } catch (Exception e) {
                Log.w(TAG, "extendTimer error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    public void resetBackend(Runnable onComplete) {
        executor.execute(() -> {
            try {
                postJson(BASE_URL + "/api/reset", "{}");
            } catch (Exception e) {
                Log.w(TAG, "resetBackend error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    public void syncPricing(Runnable onComplete) {
        executor.execute(() -> {
            try {
                String response = get(BASE_URL + "/api/config/pricing");
                if (response != null) {
                    JSONObject obj = new JSONObject(response);
                    int a = obj.optInt("setA", 499);
                    int b = obj.optInt("setB", 549);
                    int c = obj.optInt("setC", 599);
                    SetMeal.updatePrices(a, b, c);
                }
            } catch (Exception e) {
                Log.w(TAG, "syncPricing error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    public void updatePricing(int a, int b, int c, Runnable onComplete) {
        executor.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("setA", a);
                json.put("setB", b);
                json.put("setC", c);
                postJson(BASE_URL + "/api/config/pricing", json.toString());
                SetMeal.updatePrices(a, b, c);
            } catch (Exception e) {
                Log.w(TAG, "updatePricing error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    public void syncAuth(Runnable onComplete) {
        executor.execute(() -> {
            try {
                String response = get(BASE_URL + "/api/config/auth");
                if (response != null) {
                    JSONObject obj = new JSONObject(response);
                    String staff = obj.optString("staffPasskey", "123");
                    String admin = obj.optString("adminPasskey", "123");
                    AuthManager.updatePasskeys(staff, admin);
                }
            } catch (Exception e) {
                Log.w(TAG, "syncAuth error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    public void updateAuth(String staffPass, String adminPass, Runnable onComplete) {
        executor.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("staffPasskey", staffPass);
                json.put("adminPasskey", adminPass);
                postJson(BASE_URL + "/api/config/auth", json.toString());
                AuthManager.updatePasskeys(staffPass, adminPass);
            } catch (Exception e) {
                Log.w(TAG, "updateAuth error: " + e.getMessage());
            } finally {
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }

    private String get(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-API-KEY", API_KEY);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line);
            }
            in.close();
            return sb.toString();
        }
        return null;
    }

    private String postJson(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("X-API-KEY", API_KEY);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
            os.flush();
        }

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line);
            }
            in.close();
            return sb.toString();
        }
        return null;
    }
}
