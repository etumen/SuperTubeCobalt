// Copyright 2017 The Cobalt Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cobalt.app;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import dev.cobalt.coat.ArtworkDownloaderDefault;
import dev.cobalt.coat.CobaltActivity;
import dev.cobalt.coat.CobaltService;
import dev.cobalt.coat.StarboardBridge;
import dev.cobalt.libraries.services.clientloginfo.ClientLogInfoModule;
import dev.cobalt.util.Holder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main Activity for the "Cobalt on Android TV" app.
 *
 * <p>The real work is done in the abstract base class. This class is really just some factory
 * methods to "inject" things that can be customized.
 */
public class MainActivity extends CobaltActivity {

  @Override
  protected StarboardBridge createStarboardBridge(String[] args, String startDeepLink) {
    Holder<Activity> activityHolder = new Holder<>();
    Holder<Service> serviceHolder = new Holder<>();
    StarboardBridge bridge =
        new StarboardBridge(
            getApplicationContext(),
            activityHolder,
            serviceHolder,
            new ArtworkDownloaderDefault(),
            args,
            startDeepLink);

    CobaltService.Factory clientLogInfoFactory =
        new ClientLogInfoModule().provideFactory(getApplicationContext());
    bridge.registerCobaltService(clientLogInfoFactory);

    return bridge;
  }

  private final Handler membershipHandler = new Handler(Looper.getMainLooper());
  private final ExecutorService membershipExecutor = Executors.newSingleThreadExecutor();
  private final Runnable refresh = this::refreshMembership;
  private final Runnable expiry = this::returnToMembership;
  private boolean verifying;
  private boolean firstResume = true;

  @Override
  protected void createContent(Bundle savedInstanceState) {
    if (HubMembership.remainingLeaseMillis() == 0) {
      returnToMembership();
      return;
    }
    super.createContent(savedInstanceState);
    scheduleMembershipChecks();
  }

  @Override
  protected void onResume() {
    if (HubMembership.remainingLeaseMillis() == 0) returnToMembership();
    super.onResume();
    if (firstResume) {
      firstResume = false;
    } else {
      refreshMembership();
    }
  }

  private void refreshMembership() {
    if (isFinishing() || isDestroyed() || verifying) return;
    if (HubMembership.remainingLeaseMillis() == 0) {
      returnToMembership();
      return;
    }
    verifying = true;
    membershipExecutor.execute(() -> {
      HubMembership.Result result = new HubMembership(this).verify();
      membershipHandler.post(() -> {
        verifying = false;
        if (isFinishing() || isDestroyed()) return;
        if (result.status != HubMembership.Status.ACTIVE) {
          returnToMembership();
          return;
        }
        HubMembership.useLease(result);
        scheduleMembershipChecks();
      });
    });
  }

  private void scheduleMembershipChecks() {
    membershipHandler.removeCallbacks(expiry);
    membershipHandler.removeCallbacks(refresh);
    membershipHandler.postDelayed(expiry, HubMembership.remainingLeaseMillis());
    membershipHandler.postDelayed(refresh, HubMembership.remainingUntilRecheckMillis());
  }

  private void returnToMembership() {
    if (isFinishing() || isDestroyed()) return;
    HubMembership.clearLease();
    getWindow().getDecorView().setVisibility(View.INVISIBLE);
    startActivity(new Intent(this, SuperTubeMembershipActivity.class));
    finish();
  }

  @Override
  protected void onDestroy() {
    membershipHandler.removeCallbacksAndMessages(null);
    membershipExecutor.shutdownNow();
    super.onDestroy();
  }
}

