/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.confitura.lvl;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.util.Log;
import android.widget.LinearLayout;
import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.Policy;
import com.google.android.vending.licensing.ServerManagedPolicy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings.Secure;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Welcome to the world of Android Market licensing. We're so glad to have you
 * onboard!
 * <p>
 * The first thing you need to do is get your hands on your public key.
 * Update the BASE64_PUBLIC_KEY constant below with your encoded public key,
 * which you can find on the
 * <a href="http://market.android.com/publish/editProfile">Edit Profile</a>
 * page of the Market publisher site.
 * <p>
 * Log in with the same account on your Cupcake (1.5) or higher phone or
 * your FroYo (2.2) emulator with the Google add-ons installed. Change the
 * test response on the Edit Profile page, press Save, and see how this
 * application responds when you check your license.
 * <p>
 * After you get this sample running, peruse the
 * <a href="http://developer.android.com/guide/publishing/licensing.html">
 * licensing documentation.</a>
 */
public class LVLActivity extends Activity {
    private static final String BASE64_PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAiTUqNbNYW7Wz8pC575zbT2LyFLKkHiLZI8rzOHql/rbvpuNoGJs46QvmSQDQGA9/gvdMpqfY3j2bi2ZET4y9v9k7aAPtkCy9AWtkpmzNGpzNgWGFDdDJSPJedB0f0LJW9+9aBPs8wVAg7OCZU4G7p+PIbbHkE4VblFotwL2Qc1PImn5FTvf9TIiQiroBWPCrKIfCGaKvJz5MCbwq1YVu2qhkmtub2Rl69MtdiR2qjg804BnXJ+xKoeCudAkhWhdI9MbVgglU0TQw469lvwKOTMC/Q9VcKTtqJ/IJzHu+P9kmML5f3XSKbMy9RvC4pwNaZFNET/ArfQr87iaPoCpz4QIDAQAB";

    // Generate your own 20 random bytes, and put them here.
    private static final byte[] SALT = new byte[] {
            -46, 65, 30, -128, -103, -57, 74, -64, 51, 88, -95, -45, 77, -117, -36, -113, -11, 32, -64,
            89
    };
    private static final String TAG = "LVLActivity";

    private TextView mStatusText;
    private Button mCheckLicenseButton;

    private LicenseCheckerCallback mLicenseCheckerCallback;
    private LicenseChecker mChecker;

    // Server response codes. Repeated
    private static final int LICENSED = 65537;
    private static final int NOT_LICENSED = 131074;
    private static final int LICENSED_OLD_KEY = 196611;
    private static final int ERROR_NOT_MARKET_MANAGED = 262148;
    private static final int ERROR_SERVER_FAILURE = 327685;
    private static final int ERROR_OVER_QUOTA = 393222;

    private static final int ERROR_CONTACTING_SERVER = 6684774;
    private static final int ERROR_INVALID_PACKAGE_NAME = 6750311;
    private static final int ERROR_NON_MATCHING_UID = 6815848;

    private static final int ERROR_TAMPERED = 6815849;
    private LinearLayout mBackground;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);

        mStatusText = (TextView) findViewById(R.id.status_text);
        mCheckLicenseButton = (Button) findViewById(R.id.check_license_button);
        mCheckLicenseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                doCheck();
            }
        });
        mBackground = (LinearLayout) findViewById(R.id.background);

        mLicenseCheckerCallback = new LicenseCheckerCallback() {
            public void yesDoAllow(int policyReason) {
                if (isFinishing()) {
                    // Don't update UI if Activity is finishing.
                    return;
                }

                if (!checkIntegrityWithCertificate() || !checkIntegrityWithCrc32()) {
                    pleaseDoNotAllow(ERROR_TAMPERED);
                    return;
                }
                // Should yesDoAllow user access.
                displayResult(getString(R.string.allow));
                mBackground.post(new Runnable() {
                    @Override
                    public void run() {
                        mBackground.setBackgroundColor(Color.GREEN);
                    }
                });

            }

            public void pleaseDoNotAllow(int policyReason) {
                if (isFinishing()) {
                    // Don't update UI if Activity is finishing.
                    return;
                }
                displayResult(getString(R.string.dont_allow));
                // Should not yesDoAllow access. In most cases, the app should assume
                // the user has access unless it encounters this. If it does,
                // the app should inform the user of their unlicensed ways
                // and then either shut down the app or limit the user to a
                // restricted set of features.
                // In this example, we show a dialog that takes the user to Market.
                // If the reason for the lack of license is that the service is
                // unavailable or there is another problem, we display a
                // retry button on the dialog and a different message.
                displayDialog(policyReason == Policy.RETRY);
                mBackground.post(new Runnable() {
                    @Override
                    public void run() {
                        mBackground.setBackgroundColor((Color.RED));
                    }
                });
            }

            public void applicationError(int errorCode) {
                if (isFinishing()) {
                    // Don't update UI if Activity is finishing.
                    return;
                }
                // This is a polite way of saying the developer made a mistake
                // while setting up or calling the license checker library.
                // Please examine the error code and fix the error.
                String result = String.format(getString(R.string.application_error), errorCode);
                result += "\n" + explainErrorCode(errorCode);
                displayResult(result);
            }
        };
        // Try to use more data here. ANDROID_ID is a single point of attack.
        String deviceId = Secure.getString(getContentResolver(), Secure.ANDROID_ID);

        // Library calls this when it's done.
        // Construct the LicenseChecker with a policy.
        mChecker = new LicenseChecker(
                this, new ServerManagedPolicy(this,
                new AESObfuscator(SALT, getPackageName(), deviceId)),
                BASE64_PUBLIC_KEY);
        doCheck();
    }

    private String explainErrorCode(int errorCode) {
        switch (errorCode)
        {
            case LICENSED:
                return "The application is licensed to the user. The user has purchased the application or the application only exists as a draft. Allow access according to Policy constraints.";
            case LICENSED_OLD_KEY:
                return "Can indicate that the key pair used by the installed application version is invalid or compromised.\n" +
                        "The application can yesDoAllow access if needed or inform the user that an upgrade is available and limit further use until upgrade.";
            case NOT_LICENSED:
                return "The application is not licensed to the user. Do not yesDoAllow access.";
            case ERROR_CONTACTING_SERVER:
                return "Local error - the Google Play application was not able to reach the licensing server, possibly because of network availability problems.\n" +
                        "Retry the license check according to Policy retry limits.";
            case ERROR_SERVER_FAILURE:
                return "Server error - the server could not load the application's key pair for licensing.\n" +
                        "Retry the license check according to Policy retry limits.";
            case ERROR_INVALID_PACKAGE_NAME:
                return "Local error - the application requested a license check for a package that is not installed on the device.\n" +
                        "Typically caused by a development error.";
            case ERROR_NON_MATCHING_UID:
                return "Local error - the application requested a license check for a package whose UID (package, user ID pair) does not match that of the requesting application.\n" +
                        "Typically caused by a development error.";
            case ERROR_NOT_MARKET_MANAGED:
                return "Server error - the application (package name) was not recognized by Google Play.";
            case ERROR_TAMPERED:
                return "Local error - Tampered apk.";
        }
        return "";
    }

    protected Dialog onCreateDialog(int id) {
        final boolean bRetry = id == 1;
        return new AlertDialog.Builder(this)
                .setTitle(R.string.unlicensed_dialog_title)
                .setMessage(bRetry ? R.string.unlicensed_dialog_retry_body : R.string.unlicensed_dialog_body)
                .setPositiveButton(bRetry ? R.string.retry_button : R.string.buy_button, new DialogInterface.OnClickListener() {
                    boolean mRetry = bRetry;
                    public void onClick(DialogInterface dialog, int which) {
                        if ( mRetry ) {
                            doCheck();
                        } else {
                            Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                                    "http://market.android.com/details?id=" + getPackageName()));
                            startActivity(marketIntent);
                        }
                    }
                })
                .setNegativeButton(R.string.quit_button, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                }).create();
    }

    private void doCheck() {
        mCheckLicenseButton.setEnabled(false);
        setProgressBarIndeterminateVisibility(true);
        mStatusText.setText(R.string.checking_license);
        mChecker.checkAccess(mLicenseCheckerCallback);
    }

    private void displayResult(final String result) {
        mStatusText.post(new Runnable() {
            public void run() {
                mStatusText.setText(result);
                setProgressBarIndeterminateVisibility(false);
                mCheckLicenseButton.setEnabled(true);
            }
        });
    }

    private void displayDialog(final boolean showRetry) {
        mCheckLicenseButton.post(new Runnable() {
            public void run() {
                setProgressBarIndeterminateVisibility(false);
                showDialog(showRetry ? 1 : 0);
                mCheckLicenseButton.setEnabled(true);
            }
        });
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        mChecker.onDestroy();
    }


    private boolean checkIntegrityWithCertificate() {
        try {
            final PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            final String expectedSignature = getResources().getString(R.string.signature);
            final String signatureFromApk = packageInfo.signatures[0].toCharsString();
            Log.d(TAG, String.format("Comparing\n%s\nto\n%s", expectedSignature, signatureFromApk));
            return expectedSignature.equals(signatureFromApk);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "PackageManager.NameNotFoundException", e);
        }
        return false;
    }

    private boolean checkIntegrityWithCrc32() {
        try {
            JarFile jarFile = new JarFile(getPackageCodePath());
            final String expectedCrc32 = getResources().getString(R.string.crc32);
            final JarEntry jarEntry = jarFile.getJarEntry("classes.dex");
            final String crcFromJar = Long.toHexString(jarEntry.getCrc());
            Log.d(TAG, String.format("Comparing\n%s\nto\n%s", expectedCrc32, crcFromJar));
            return expectedCrc32.equals(crcFromJar);
        } catch (IOException e) {
            Log.e(TAG, "PackageManager.NameNotFoundException", e);
        }
        return false;
    }

    /**
     * This method does not work with antilvl-1.4
     */
    private boolean isAntiLVL() {
        try {
            Class.forName("smaliHook");
            return true;
        } catch (ClassNotFoundException e) {

        }
        return false;
    }
}