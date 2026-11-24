// Copyright 2026 The Cobalt Authors. All Rights Reserved.
// Use of this source code is governed by a BSD-style license found in LICENSE.

package dev.cobalt.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

final class HubMembership {
  enum Status { ACTIVE, EXPIRED, RECOVERY, BLOCKED, UNAVAILABLE }

  private static final String PRODUCT = "SUPERTUBE";
  private static final String CLIENT_ID = "supertube-tv";
  private static final String OAUTH_SCOPE = "openid offline_access app:bootstrap";
  private static final String API_BASE = "https://api.isinnet.net";
  private static final String IDENTITY_BASE = "https://id.isinnet.net";
  private static final String KEY_ALIAS = "supertube-hub-session-v2";
  private static final String PREFS = "supertube_hub_identity";
  private static final String INSTALLATION_ID = "installation_id";
  private static final int DEFAULT_RECHECK_SECONDS = 3600;
  private static final int URL_SAFE = android.util.Base64.URL_SAFE
      | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING;

  private static long leaseUntil;
  private static long recheckAt;

  private final Context context;
  private final AtomicFile sessionFile;

  HubMembership(Context context) {
    this.context = context.getApplicationContext();
    sessionFile = new AtomicFile(new File(this.context.getNoBackupFilesDir(), "hub_app_session_v2"));
  }

  static long remainingLeaseMillis() {
    return Math.max(0, leaseUntil - SystemClock.elapsedRealtime());
  }

  static void useLease(Result result) {
    leaseUntil = result.deadline;
    recheckAt = SystemClock.elapsedRealtime() + result.recheckAfterMillis;
  }

  static long remainingUntilRecheckMillis() {
    return Math.max(0, recheckAt - SystemClock.elapsedRealtime());
  }

  static void clearLease() {
    leaseUntil = 0;
    recheckAt = 0;
  }

  DeviceAuthorization beginAuthorization() throws Exception {
    JSONObject response = formRequest(
        IDENTITY_BASE + "/oauth/device/code",
        new String[][] {
          {"client_id", CLIENT_ID},
          {"scope", OAUTH_SCOPE},
        },
        true);
    return new DeviceAuthorization(
        response.getString("device_code"),
        response.getString("user_code"),
        response.getInt("expires_in"),
        response.getInt("interval"));
  }

  static Intent handoffRequest(DeviceAuthorization authorization) {
    return new Intent("net.isinnet.agent.APP_HANDOFF")
        .setClassName("net.isinnet.agent", "net.isinnet.agent.AppHandoffActivity")
        .putExtra("userCode", authorization.userCode);
  }

  Result completeAuthorization(DeviceAuthorization authorization) {
    long deadline = SystemClock.elapsedRealtime() + authorization.expiresInSeconds * 1000L;
    int intervalSeconds = Math.max(1, authorization.intervalSeconds);
    while (SystemClock.elapsedRealtime() < deadline) {
      try {
        JSONObject token = formRequest(
            IDENTITY_BASE + "/oauth/token",
            new String[][] {
              {"grant_type", "urn:ietf:params:oauth:grant-type:device_code"},
              {"client_id", CLIENT_ID},
              {"device_code", authorization.deviceCode},
            },
            false);
        StoredTokens stored = parseTokenResponse(token, null);
        writeTokens(stored);
        return verify();
      } catch (OAuthPendingException pending) {
        if ("slow_down".equals(pending.code)) intervalSeconds += 5;
        try {
          Thread.sleep(intervalSeconds * 1000L);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return new Result(Status.UNAVAILABLE);
        }
      } catch (Exception error) {
        return new Result(Status.UNAVAILABLE);
      }
    }
    return new Result(Status.UNAVAILABLE);
  }

  Result verify() {
    try {
      StoredTokens tokens = readTokens();
      if (tokens == null) return new Result(Status.RECOVERY);
      tokens = ensureUsable(tokens);
      if (tokens == null) return new Result(Status.RECOVERY);

      long startedAt = SystemClock.elapsedRealtime();
      Response response = apiRequest("/v1/bootstrap", tokens.accessToken);
      if (response.status == 401) {
        tokens = refresh(tokens);
        if (tokens == null) return new Result(Status.RECOVERY);
        response = apiRequest("/v1/bootstrap", tokens.accessToken);
      }
      if (response.status == 200) {
        JSONObject subscription = response.body.getJSONObject("subscription");
        long seconds = subscription.getLong("remainingSeconds");
        if ("ACTIVE".equals(subscription.getString("status"))
            && response.body.getJSONObject("entitlements").getBoolean(PRODUCT)
            && !response.body.getJSONObject("device").getBoolean("revoked")
            && seconds > 0) {
          long deadline = startedAt + Math.max(0, seconds) * 1000L;
          if (deadline > SystemClock.elapsedRealtime()) {
            return new Result(Status.ACTIVE, deadline, recheckIntervalMillis(response.body));
          }
        }
        return new Result(Status.EXPIRED);
      }

      JSONObject error = response.body.optJSONObject("error");
      String code = error == null ? "" : error.optString("code");
      if (response.status == 403 && ("SUBSCRIPTION_EXPIRED".equals(code)
          || "SUBSCRIPTION_PAUSED".equals(code) || "APP_NOT_ENTITLED".equals(code))) {
        return new Result(Status.EXPIRED);
      }
      if (response.status == 403 || response.status == 409 || response.status == 426) {
        return new Result(Status.BLOCKED);
      }
    } catch (Exception ignored) {
      // Keep the saved refresh token when Hub is temporarily unavailable.
    }
    return new Result(Status.UNAVAILABLE);
  }

  private StoredTokens ensureUsable(StoredTokens tokens) throws Exception {
    long nowSeconds = System.currentTimeMillis() / 1000L;
    if (tokens.expiresAtEpochSeconds > nowSeconds + 30) return tokens;
    return refresh(tokens);
  }

  private StoredTokens refresh(StoredTokens previous) throws Exception {
    try {
      JSONObject response = formRequest(
          IDENTITY_BASE + "/oauth/token",
          new String[][] {
            {"grant_type", "refresh_token"},
            {"client_id", CLIENT_ID},
            {"refresh_token", previous.refreshToken},
          },
          false);
      StoredTokens refreshed = parseTokenResponse(response, previous.refreshToken);
      writeTokens(refreshed);
      return refreshed;
    } catch (OAuthInvalidGrantException invalid) {
      sessionFile.delete();
      return null;
    }
  }

  private StoredTokens parseTokenResponse(JSONObject json, String previousRefreshToken)
      throws Exception {
    String tokenType = json.getString("token_type");
    if (!"Bearer".equalsIgnoreCase(tokenType)) {
      throw new GeneralSecurityException("Unsupported OAuth token type");
    }
    String accessToken = json.getString("access_token");
    String refreshToken = json.optString("refresh_token", previousRefreshToken);
    if (refreshToken == null || refreshToken.isEmpty()) {
      throw new GeneralSecurityException("Missing OAuth refresh token");
    }
    long expiresIn = json.getLong("expires_in");
    if (expiresIn <= 0) throw new GeneralSecurityException("Invalid OAuth token lifetime");
    String scope = json.optString("scope", OAUTH_SCOPE);
    if (!containsScope(scope, "app:bootstrap")) {
      throw new GeneralSecurityException("Missing app:bootstrap scope");
    }
    return new StoredTokens(
        accessToken,
        refreshToken,
        System.currentTimeMillis() / 1000L + expiresIn);
  }

  private static boolean containsScope(String scopes, String expected) {
    for (String scope : scopes.trim().split("\\s+")) {
      if (expected.equals(scope)) return true;
    }
    return false;
  }

  private JSONObject formRequest(String url, String[][] fields, boolean includeDeviceInfo)
      throws Exception {
    HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
    try {
      connection.setRequestMethod("POST");
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(8000);
      connection.setUseCaches(false);
      connection.setDoOutput(true);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      if (includeDeviceInfo) {
        connection.setRequestProperty("x-isinnet-installation-id", installationId());
        connection.setRequestProperty("x-isinnet-device-platform", "ANDROID_TV");
        connection.setRequestProperty(
            "x-isinnet-device-name",
            (Build.MANUFACTURER + " " + Build.MODEL).trim());
      }

      StringBuilder encoded = new StringBuilder();
      for (String[] field : fields) {
        if (encoded.length() > 0) encoded.append('&');
        encoded.append(URLEncoder.encode(field[0], "UTF-8"));
        encoded.append('=');
        encoded.append(URLEncoder.encode(field[1], "UTF-8"));
      }
      byte[] body = encoded.toString().getBytes(StandardCharsets.UTF_8);
      connection.setFixedLengthStreamingMode(body.length);
      try (OutputStream output = connection.getOutputStream()) {
        output.write(body);
      }

      int status = connection.getResponseCode();
      JSONObject json = readJson(connection, status);
      if (status >= 200 && status < 300) return json;
      String oauthError = json.optString("error");
      if ("authorization_pending".equals(oauthError) || "slow_down".equals(oauthError)) {
        throw new OAuthPendingException(oauthError);
      }
      if ("invalid_grant".equals(oauthError) || "invalid_token".equals(oauthError)) {
        throw new OAuthInvalidGrantException();
      }
      throw new java.io.IOException("OAuth request failed: HTTP " + status);
    } finally {
      connection.disconnect();
    }
  }

  private Response apiRequest(String path, String token) throws Exception {
    HttpURLConnection connection =
        (HttpURLConnection) URI.create(API_BASE + path).toURL().openConnection();
    try {
      connection.setRequestMethod("GET");
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(8000);
      connection.setUseCaches(false);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Authorization", "Bearer " + token);
      connection.setRequestProperty("X-Isinnet-App-Build", Long.toString(appBuild()));
      int status = connection.getResponseCode();
      return new Response(status, readJson(connection, status));
    } finally {
      connection.disconnect();
    }
  }

  private static JSONObject readJson(HttpURLConnection connection, int status) throws Exception {
    InputStream input = status < 400 ? connection.getInputStream() : connection.getErrorStream();
    if (input == null) return new JSONObject();
    try (InputStream stream = input) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[2048];
      int count;
      while ((count = stream.read(buffer)) != -1) {
        if (bytes.size() + count > 65536) throw new java.io.IOException("Hub response too large");
        bytes.write(buffer, 0, count);
      }
      return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }
  }

  private String installationId() {
    SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    String existing = prefs.getString(INSTALLATION_ID, null);
    if (existing != null && !existing.isEmpty()) return existing;
    String created = UUID.randomUUID().toString();
    if (!prefs.edit().putString(INSTALLATION_ID, created).commit()) {
      throw new IllegalStateException("Could not persist installation id");
    }
    return created;
  }

  @SuppressWarnings("deprecation")
  private long appBuild() throws Exception {
    PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
  }

  private static long recheckIntervalMillis(JSONObject payload) {
    Object field = payload.opt("recheckAfterSeconds");
    if (field instanceof Number) {
      long value = ((Number) field).longValue();
      if (value > 0 && value <= 86400) return value * 1000L;
    }
    return DEFAULT_RECHECK_SECONDS * 1000L;
  }

  private static KeyStore keys() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    return store;
  }

  private StoredTokens readTokens() throws Exception {
    byte[] data;
    try {
      data = sessionFile.readFully();
    } catch (FileNotFoundException ignored) {
      return null;
    }
    if (data.length <= 12) return null;
    SecretKey key = (SecretKey) keys().getKey(KEY_ALIAS, null);
    if (key == null) return null;
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, data, 0, 12));
      JSONObject json = new JSONObject(
          new String(cipher.doFinal(data, 12, data.length - 12), StandardCharsets.UTF_8));
      return new StoredTokens(
          json.getString("accessToken"),
          json.getString("refreshToken"),
          json.getLong("expiresAtEpochSeconds"));
    } catch (GeneralSecurityException error) {
      sessionFile.delete();
      return null;
    }
  }

  private void writeTokens(StoredTokens tokens) throws Exception {
    SecretKey key = (SecretKey) keys().getKey(KEY_ALIAS, null);
    if (key == null) {
      KeyGenerator generator =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      generator.init(new KeyGenParameterSpec.Builder(
          KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .build());
      key = generator.generateKey();
    }
    JSONObject json = new JSONObject()
        .put("accessToken", tokens.accessToken)
        .put("refreshToken", tokens.refreshToken)
        .put("expiresAtEpochSeconds", tokens.expiresAtEpochSeconds);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key);
    byte[] encrypted = cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8));
    if (cipher.getIV().length != 12) throw new GeneralSecurityException("Unexpected IV size");
    FileOutputStream output = sessionFile.startWrite();
    try {
      output.write(cipher.getIV());
      output.write(encrypted);
      sessionFile.finishWrite(output);
    } catch (Exception error) {
      sessionFile.failWrite(output);
      throw error;
    }
  }

  static final class DeviceAuthorization {
    final String deviceCode;
    final String userCode;
    final int expiresInSeconds;
    final int intervalSeconds;

    DeviceAuthorization(
        String deviceCode,
        String userCode,
        int expiresInSeconds,
        int intervalSeconds) {
      this.deviceCode = deviceCode;
      this.userCode = userCode;
      this.expiresInSeconds = expiresInSeconds;
      this.intervalSeconds = intervalSeconds;
    }
  }

  static final class Result {
    final Status status;
    final long deadline;
    final long recheckAfterMillis;

    Result(Status status) {
      this(status, 0, 0);
    }

    Result(Status status, long deadline, long recheckAfterMillis) {
      this.status = status;
      this.deadline = deadline;
      this.recheckAfterMillis = recheckAfterMillis;
    }
  }

  private static final class StoredTokens {
    final String accessToken;
    final String refreshToken;
    final long expiresAtEpochSeconds;

    StoredTokens(String accessToken, String refreshToken, long expiresAtEpochSeconds) {
      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
      this.expiresAtEpochSeconds = expiresAtEpochSeconds;
    }
  }

  private static final class Response {
    final int status;
    final JSONObject body;

    Response(int status, JSONObject body) {
      this.status = status;
      this.body = body;
    }
  }

  private static final class OAuthPendingException extends Exception {
    final String code;
    OAuthPendingException(String code) {
      this.code = code;
    }
  }

  private static final class OAuthInvalidGrantException extends Exception {}
}
