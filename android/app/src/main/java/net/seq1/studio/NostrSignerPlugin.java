package net.seq1.studio;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "NostrSigner")
public class NostrSignerPlugin extends Plugin {

    private static final String AMBER_PACKAGE = "com.greenart7c3.nostrsigner";

    // Check whether Amber is installed — called by the web app before showing UI
    @PluginMethod
    public void isAmberInstalled(PluginCall call) {
        boolean installed = false;
        try {
            getContext().getPackageManager().getPackageInfo(AMBER_PACKAGE, 0);
            installed = true;
        } catch (PackageManager.NameNotFoundException e) {
            installed = false;
        }
        JSObject ret = new JSObject();
        ret.put("installed", installed);
        call.resolve(ret);
    }

    // NIP-55: get the user's public key from Amber
    @PluginMethod
    public void getPublicKey(PluginCall call) {
        if (!isAmberAvailable()) {
            call.reject("AMBER_NOT_INSTALLED", "Amber is not installed. Install it from F-Droid or the Play Store.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"));
        intent.setPackage(AMBER_PACKAGE);
        intent.putExtra("type", "get_public_key");
        intent.putExtra("compressionType", "none");
        intent.putExtra("returnType", "signature");
        startActivityForResult(call, intent, "handleGetPublicKeyResult");
    }

    @ActivityCallback
    private void handleGetPublicKeyResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("USER_CANCELLED", "User cancelled Amber authorisation.");
            return;
        }
        String pubkey = result.getData().getStringExtra("result");
        if (pubkey == null || pubkey.isEmpty()) {
            call.reject("NO_PUBKEY", "Amber returned no public key.");
            return;
        }
        JSObject ret = new JSObject();
        ret.put("pubkey", pubkey);
        call.resolve(ret);
    }

    // NIP-55: sign a Nostr event via Amber
    @PluginMethod
    public void signEvent(PluginCall call) {
        if (!isAmberAvailable()) {
            call.reject("AMBER_NOT_INSTALLED", "Amber is not installed.");
            return;
        }
        String eventJson = call.getString("event");
        String pubkey = call.getString("pubkey", "");

        if (eventJson == null || eventJson.isEmpty()) {
            call.reject("INVALID_EVENT", "No event JSON provided.");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:" + eventJson));
        intent.setPackage(AMBER_PACKAGE);
        intent.putExtra("type", "sign_event");
        intent.putExtra("compressionType", "none");
        intent.putExtra("returnType", "signature");
        intent.putExtra("current_user", pubkey);
        startActivityForResult(call, intent, "handleSignEventResult");
    }

    @ActivityCallback
    private void handleSignEventResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("USER_CANCELLED", "User cancelled signing in Amber.");
            return;
        }
        String signedEvent = result.getData().getStringExtra("event");
        String sig = result.getData().getStringExtra("result");

        if (signedEvent == null && sig == null) {
            call.reject("NO_RESULT", "Amber returned no signed event.");
            return;
        }
        JSObject ret = new JSObject();
        ret.put("signedEvent", signedEvent != null ? signedEvent : "{}");
        ret.put("sig", sig != null ? sig : "");
        call.resolve(ret);
    }

    private boolean isAmberAvailable() {
        try {
            getContext().getPackageManager().getPackageInfo(AMBER_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
