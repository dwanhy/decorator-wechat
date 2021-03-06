package com.oasisfeng.nevo.decorators.wechat;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import androidx.annotation.Nullable;

import static android.content.Intent.ACTION_INSTALL_PACKAGE;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static com.oasisfeng.nevo.decorators.wechat.WeChatDecorator.WECHAT_PACKAGE;

/**
 * Entry activity. Some ROMs (including Samsung, OnePlus) require a launcher activity to allow any component being bound by other app.
 */
@SuppressLint("ExportedPreferenceActivity")
public class WeChatDecoratorSettingsActivity extends PreferenceActivity {

	private static final String NEVOLUTION_PACKAGE = "com.oasisfeng.nevo";
	private static final String ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead";
	private static final String PLAY_STORE_PACKAGE = "com.android.vending";
	private static final String APP_MARKET_PREFIX = "market://details?id=";

	@Override protected void onCreate(@Nullable final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//noinspection deprecation
		addPreferencesFromResource(R.xml.settings);
	}

	@Override protected void onResume() {
		super.onResume();
		@SuppressWarnings("deprecation") final Preference preference_activate = findPreference(getString(R.string.pref_activate));
		boolean nevolution_installed = false, wechat_installed = false, running = false;
		try {
			getPackageManager().getApplicationInfo(NEVOLUTION_PACKAGE, 0);
			nevolution_installed = true;
			getPackageManager().getApplicationInfo(WECHAT_PACKAGE, PackageManager.GET_UNINSTALLED_PACKAGES);
			wechat_installed = true;
			final Intent service = new Intent(this, WeChatDecorator.class).setAction(NevoDecoratorService.ACTION_DECORATOR_SERVICE);
			running = mDummyReceiver.peekService(this, service) != null;
		} catch (final PackageManager.NameNotFoundException ignored) {}

		preference_activate.setEnabled(! nevolution_installed || wechat_installed);		// No reason to promote WeChat if not installed.
		preference_activate.setSelectable(! running);
		preference_activate.setSummary(! nevolution_installed ? getText(R.string.pref_activate_summary_nevo_not_installed)
				: ! wechat_installed ? getText(R.string.pref_activate_summary_wechat_not_installed)
				: running ? getText(R.string.pref_activate_summary_already_activated) : null);
		preference_activate.setOnPreferenceClickListener(! nevolution_installed ? this::installNevolution : wechat_installed && ! running ? this::activate : null);

		@SuppressWarnings("deprecation") final Preference preference_extension = findPreference(getString(R.string.pref_extension));
		boolean android_auto_installed = false;
		try {
			getPackageManager().getApplicationInfo(ANDROID_AUTO_PACKAGE, 0);
			android_auto_installed = true;
			preference_extension.setSelectable(false);
		} catch (final PackageManager.NameNotFoundException ignored) {}
		preference_extension.setEnabled(wechat_installed);
		preference_extension.setSelectable(! android_auto_installed);
		preference_extension.setSummary(android_auto_installed ? R.string.pref_extension_summary_installed
				: isPlayStoreSystemApp() ? R.string.pref_extension_summary_auto : R.string.pref_extension_summary);
		preference_extension.setOnPreferenceClickListener(android_auto_installed ? null : this::installExtension);

		@SuppressWarnings("deprecation") final Preference preference_version = findPreference(getString(R.string.pref_version));
		try {
			preference_version.setSummary(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
		} catch (final PackageManager.NameNotFoundException ignored) {}
		mVersionClickCount = 7;
		preference_version.setOnPreferenceClickListener(p -> {
			if (-- mVersionClickCount == 0) {	// Even if Android Auto is installed
				preference_extension.setEnabled(true);
				preference_extension.setSelectable(true);
				preference_extension.setSummary(R.string.pref_extension_summary);
				preference_extension.setOnPreferenceClickListener(this::installExtension);
			}
			return true;
		});
	}

	private boolean installNevolution(final @SuppressWarnings("unused") Preference preference) {
		try {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(APP_MARKET_PREFIX + NEVOLUTION_PACKAGE)));
		} catch (final ActivityNotFoundException e) {}	// TODO: Landing web page
		return true;
	}

	private boolean activate(final @SuppressWarnings("unused") Preference preference) {
		try {
			startActivityForResult(new Intent("com.oasisfeng.nevo.action.ACTIVATE_DECORATOR").setPackage(NEVOLUTION_PACKAGE)
					.putExtra("nevo.decorator", new ComponentName(this, WeChatDecorator.class))
					.putExtra("nevo.target", WECHAT_PACKAGE), 0);
		} catch (final ActivityNotFoundException e) {
			startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(NEVOLUTION_PACKAGE));
		}
		return true;
	}

	private boolean installExtension(final @SuppressWarnings("unused") Preference preference) {
		if (mVersionClickCount > 0 && isPlayStoreSystemApp())
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(APP_MARKET_PREFIX + ANDROID_AUTO_PACKAGE)).setPackage(PLAY_STORE_PACKAGE));
		else try {
			final String authority = getPackageManager().getProviderInfo(new ComponentName(this, AssetFileProvider.class), 0).authority;
			startActivity(new Intent(ACTION_INSTALL_PACKAGE, Uri.parse("content://" + authority + "/dummy-auto.apk")).addFlags(FLAG_GRANT_READ_URI_PERMISSION));
		} catch (final PackageManager.NameNotFoundException | ActivityNotFoundException ignored) {}	// Should never happen
		return true;
	}

	private boolean isPlayStoreSystemApp() {
		try {
			return (getPackageManager().getApplicationInfo(PLAY_STORE_PACKAGE, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0;
		} catch (final PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	private final BroadcastReceiver mDummyReceiver = new BroadcastReceiver() { @Override public void onReceive(final Context c, final Intent i) {}};
	private int mVersionClickCount;
}
