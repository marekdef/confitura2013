
package com.android.vending.licensing;

import com.android.vending.licensing.ILicenseResultListener;

// Android library projects do not yet support AIDL, so this has been
// precompiled into the src directory.
oneway interface ILicensingService {
  void checkLicense(long nonce, String packageName, in ILicenseResultListener listener);
}