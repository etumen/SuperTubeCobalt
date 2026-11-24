// Copyright 2026 The Cobalt Authors. All Rights Reserved.
// Use of this source code is governed by a BSD-style license found in LICENSE.

package dev.cobalt.app;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SuperTubeMembershipActivity extends Activity {
  private static final int REQUEST_HANDOFF = 2305;
  private static final List<String> VIDEO_HOSTS =
      Arrays.asList("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be");

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private HubMembership membership;
  private TextView title;
  private Button retry;
  private boolean busy;
  private boolean visible;
  private boolean recoverWhenPolling;
  private HubMembership.DeviceAuthorization pendingAuthorization;
  private final Runnable poll = () -> verify(recoverWhenPolling);

  @Override
  public void onCreate(Bundle savedState) {
    super.onCreate(savedState);
    membership = new HubMembership(this);

    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setGravity(Gravity.CENTER);
    layout.setBackgroundColor(Color.rgb(9, 9, 12));
    int padding = Math.round(24 * getResources().getDisplayMetrics().density);
    layout.setPadding(padding, padding, padding, padding);

    title = new TextView(this);
    title.setTextColor(Color.WHITE);
    title.setTextSize(24);
    title.setGravity(Gravity.CENTER);
    title.setText("SuperTube �yeli�iniz kontrol ediliyor.");
    layout.addView(title);

    retry = new Button(this);
    retry.setText("Tekrar Dene");
    retry.setOnClickListener(view -> verify(true));
    retry.setVisibility(View.GONE);
    layout.addView(retry);

    setContentView(layout);
    verify(true);
  }

  @Override
  protected void onStart() {
    super.onStart();
    visible = true;
    if (!busy) verify(true);
  }

  @Override
  protected void onStop() {
    visible = false;
    handler.removeCallbacks(poll);
    super.onStop();
  }

  private void verify(boolean allowHandoff) {
    if (busy) return;
    busy = true;
    retry.setEnabled(false);
    retry.setVisibility(View.GONE);
    handler.removeCallbacks(poll);
    executor.execute(() -> {
      HubMembership.Result result = membership.verify();
      runOnUiThread(() -> onResult(result, allowHandoff));
    });
  }

  private void beginAgentApproval() {
    title.setText("I�INNET Agent �zerinden SuperTube etkinle�tiriliyor.");
    executor.execute(() -> {
      try {
        HubMembership.DeviceAuthorization authorization = membership.beginAuthorization();
        Intent intent = HubMembership.handoffRequest(authorization);
        runOnUiThread(() -> {
          if (!visible || isFinishing() || isDestroyed()) {
            busy = false;
            return;
          }
          pendingAuthorization = authorization;
          try {
            startActivityForResult(intent, REQUEST_HANDOFF);
          } catch (Exception error) {
            pendingAuthorization = null;
            busy = false;
            showUnavailable();
          }
        });
      } catch (Exception error) {
        runOnUiThread(() -> {
          busy = false;
          showUnavailable();
        });
      }
    });
  }

  private void onResult(HubMembership.Result result, boolean allowHandoff) {
    if (isFinishing() || isDestroyed()) return;

    if (result.status == HubMembership.Status.RECOVERY && allowHandoff && visible) {
      beginAgentApproval();
      return;
    }

    busy = false;
    if (result.status == HubMembership.Status.ACTIVE) {
      if (!visible) return;
      HubMembership.useLease(result);
      startActivity(playbackIntent());
      finish();
      return;
    }

    recoverWhenPolling = result.status == HubMembership.Status.EXPIRED;
    switch (result.status) {
      case EXPIRED:
        title.setText("SuperTube �yeli�iniz sona erdi.");
        break;
      case BLOCKED:
        title.setText("Bu cihazda SuperTube eri�imi do�rulanamad�.");
        break;
      default:
        title.setText("Ba�lant� kurulamad�. Daha sonra tekrar deneyin.");
        break;
    }
    retry.setVisibility(View.VISIBLE);
    retry.setEnabled(true);
    retry.requestFocus();
    if (visible) handler.postDelayed(poll, 30000);
  }

  private void showUnavailable() {
    if (isFinishing() || isDestroyed()) return;
    title.setText("I�INNET Agent veya Hub ba�lant�s� kurulamad�.");
    retry.setVisibility(View.VISIBLE);
    retry.setEnabled(true);
    retry.requestFocus();
  }

  @Override
  @SuppressWarnings("deprecation")
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != REQUEST_HANDOFF) return;

    HubMembership.DeviceAuthorization authorization = pendingAuthorization;
    pendingAuthorization = null;
    if (resultCode != RESULT_OK || authorization == null) {
      busy = false;
      showUnavailable();
      return;
    }

    title.setText("SuperTube oturumu haz�rlan�yor.");
    executor.execute(() -> {
      HubMembership.Result result = membership.completeAuthorization(authorization);
      runOnUiThread(() -> onResult(result, false));
    });
  }

  private Intent playbackIntent() {
    Intent source = getIntent();
    Intent target = new Intent(Intent.ACTION_MAIN).setClass(this, MainActivity.class);
    if (source == null) return target;

    Uri uri = source.getData();
    if (Intent.ACTION_VIEW.equals(source.getAction()) && uri != null
        && VIDEO_HOSTS.contains(uri.getHost())
        && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
      return target.setAction(Intent.ACTION_VIEW).setData(uri);
    }
    if (MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(source.getAction())) {
      return target.setAction(source.getAction())
          .putExtra(SearchManager.QUERY, source.getStringExtra(SearchManager.QUERY));
    }
    return target;
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    executor.shutdownNow();
    super.onDestroy();
  }
}

