package com.ihs.odkate.base;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.preferences.AdminKeys;
import org.odk.collect.android.preferences.AdminPreferencesActivity;
import org.odk.collect.android.preferences.PreferenceKeys;
import org.odk.collect.android.provider.FormsProviderAPI;
import org.odk.collect.android.utilities.ApplicationConstants;
import org.odk.collect.android.utilities.ToastUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

import static com.ihs.odkate.base.OdkateUtils.copyAssetForms;
import static com.ihs.odkate.base.OdkateUtils.copyAssetForms;
import static com.ihs.odkate.base.OdkateUtils.copyXFormToBin;

/**
 * Created by Maimoona on 7/14/2017.
 */

public class Odkate extends Collect{
    public static final int ODK_FORM_ENTRY_REQUEST_CODE = 11;

    @Override
    public void onCreate() {
        super.onCreate();

        setupODK(this);
    }

    /** setup ODK whenever application is launched. This should cover everything ODK does in its init Activities
     * @param context
     */
    public static void setupODK(Context context){
        // has to be at the start of app using odk engine to make sure that ODK dirs are setup before calling form entry
        Collect.createODKDirs();

        // make sure to keep this line before overriding preferences otherwise first run would be set to false
        boolean firstRun = PreferenceManager.getDefaultSharedPreferences(Collect.getInstance()).getBoolean(PreferenceKeys.KEY_FIRST_RUN, true);

        // override ODK preferences
        overridePreferences(context);

        // app crashes if we do set it up , so donot set it up
        // setupGoogleAnalytics();

        // copy forms from assets/xforms to odk/forms so that collect can make use of it
        if (firstRun){
            copyAssetForms(context);
        }
    }

    // onListItemClick of org.odk.collect.android.activities.FormChooserList explains how form is loaded
    public static void launchODKForm(long id, Activity context){
        Uri formUri = ContentUris.withAppendedId(FormsProviderAPI.FormsColumns.CONTENT_URI, id);

        Log.v(Odkate.class.getName(), "Launching URI "+formUri);

        Intent intent = new Intent(Intent.ACTION_EDIT, formUri);
        intent.putExtra(ApplicationConstants.BundleKeys.FORM_MODE, ApplicationConstants.FormModes.EDIT_SAVED);
        context.startActivityForResult(intent, ODK_FORM_ENTRY_REQUEST_CODE);
    }

    public static void launchODKForm(String formId, String entityId, Activity context, Map<String, String> overrides) throws IllegalAccessException, IOException {
        copyXFormToBin(formId, entityId, overrides);

        Cursor formcursor = new FormsDao().getFormsCursorForFormId(formId);
        try {
            if (formcursor.getCount() > 0) {
                formcursor.moveToFirst();

                launchODKForm(formcursor.getLong(formcursor.getColumnIndex(FormsProviderAPI.FormsColumns._ID)), context);
            }
            else throw new IllegalAccessException("Form with given id ("+formId+") not exists. Make sure application life cycle is managed properly");
        }
        finally {
            formcursor.close();
        }
    }

    /**
     * Override ODK-collect preferences to make sure app does not allow users to modify app behaviour just as ODK does!
     */
    private static boolean overridePreferences(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Collect.getInstance());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        SharedPreferences sharedPreferencesAdmin = context.getSharedPreferences(AdminPreferencesActivity.ADMIN_PREFERENCES, MODE_WORLD_WRITEABLE);
        SharedPreferences.Editor editorAdmin = sharedPreferencesAdmin.edit();

        // get the package info object with version number
        PackageInfo packageInfo = null;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e(e, "Unable to get package info");
        }

        boolean firstRun = sharedPreferences.getBoolean(PreferenceKeys.KEY_FIRST_RUN, true);

        // if you've increased version code, then update the version number and set firstRun to true
        if (sharedPreferences.getLong(PreferenceKeys.KEY_LAST_VERSION, 0) < packageInfo.versionCode) {
            editor.putLong(PreferenceKeys.KEY_LAST_VERSION, packageInfo.versionCode);

            editor.putBoolean(PreferenceKeys.KEY_FIRST_RUN, false);

            editor.putBoolean(PreferenceKeys.KEY_SHOW_SPLASH, false);
            editorAdmin.putBoolean(AdminKeys.KEY_SHOW_SPLASH_SCREEN, false);

            //editor.putBoolean(PreferenceKeys.KEY_DELETE_AFTER_SEND, false);
            //editor.putBoolean(PreferenceKeys.KEY_INSTANCE_SYNC, false);
            editor.putBoolean(PreferenceKeys.KEY_ANALYTICS, false);
            editorAdmin.putBoolean(AdminKeys.KEY_ANALYTICS, false);

            editor.putBoolean(PreferenceKeys.KEY_TIMER_LOG_ENABLED, false);
            editorAdmin.putBoolean(AdminKeys.KEY_TIMER_LOG_ENABLED, false);

            editor.putBoolean(PreferenceKeys.KEY_DELETE_AFTER_SEND, false);
            editorAdmin.putBoolean(AdminKeys.KEY_DELETE_AFTER_SEND, false);

            editorAdmin.putBoolean(AdminKeys.KEY_SAVE_MID, false);
            editorAdmin.putBoolean(AdminKeys.KEY_ACCESS_SETTINGS, false);
            editorAdmin.putBoolean(AdminKeys.KEY_SAVE_AS, false);
            editorAdmin.putBoolean(AdminKeys.KEY_MARK_AS_FINALIZED, false);

            // donot allow autosend so that Collect doesnot try making network searches or calls
            editor.putString(PreferenceKeys.KEY_AUTOSEND, "off");
            editorAdmin.putString(AdminKeys.KEY_AUTOSEND, "off");

            editor.putString(PreferenceKeys.KEY_NAVIGATION, PreferenceKeys.NAVIGATION_BUTTONS);
            editorAdmin.putString(AdminKeys.KEY_NAVIGATION, PreferenceKeys.NAVIGATION_BUTTONS);

            editor.putBoolean(PreferenceKeys.KEY_COMPLETED_DEFAULT, true);
            editorAdmin.putBoolean(AdminKeys.KEY_DEFAULT_TO_FINALIZED, true);

            editor.putBoolean("delete_saved", false);//// TODO: 7/14/2017
            editorAdmin.putBoolean("delete_saved", false);//// TODO: 7/14/2017
        }

        editor.commit();
        editorAdmin.commit();

        // as specified in MainActivity of ODK-collect the settings should be updatable via collect.settings
        // to override settings again, this file should be resent and pasted.
        // This code after loading and updating settings deletes this file.
        File f = new File(Collect.ODK_ROOT + "/collect.settings");
        if (f.exists()) {
            boolean success = loadSharedPreferencesFromFile(f);
            if (success) {
                ToastUtils.showLongToast(org.odk.collect.android.R.string.settings_successfully_loaded_file_notification);
                f.delete();
            } else {
                ToastUtils.showLongToast(org.odk.collect.android.R.string.corrupt_settings_file_notification);
            }
        }

        return firstRun;
    }

    private static boolean loadSharedPreferencesFromFile(File src) {
        // this should probably be in a thread if it ever gets big
        boolean res = false;
        ObjectInputStream input = null;
        try {
            input = new ObjectInputStream(new FileInputStream(src));
            SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(Collect.getInstance()).edit();
            prefEdit.clear();
            // first object is preferences
            Map<String, ?> entries = (Map<String, ?>) input.readObject();
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                if (v instanceof Boolean) {
                    prefEdit.putBoolean(key, ((Boolean) v).booleanValue());
                } else if (v instanceof Float) {
                    prefEdit.putFloat(key, ((Float) v).floatValue());
                } else if (v instanceof Integer) {
                    prefEdit.putInt(key, ((Integer) v).intValue());
                } else if (v instanceof Long) {
                    prefEdit.putLong(key, ((Long) v).longValue());
                } else if (v instanceof String) {
                    prefEdit.putString(key, ((String) v));
                }
            }
            prefEdit.apply();

            // second object is admin options
            SharedPreferences.Editor adminEdit = Collect.getInstance().getSharedPreferences(AdminPreferencesActivity.ADMIN_PREFERENCES,
                    0).edit();
            adminEdit.clear();
            // first object is preferences
            Map<String, ?> adminEntries = (Map<String, ?>) input.readObject();
            for (Map.Entry<String, ?> entry : adminEntries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                if (v instanceof Boolean) {
                    adminEdit.putBoolean(key, ((Boolean) v).booleanValue());
                } else if (v instanceof Float) {
                    adminEdit.putFloat(key, ((Float) v).floatValue());
                } else if (v instanceof Integer) {
                    adminEdit.putInt(key, ((Integer) v).intValue());
                } else if (v instanceof Long) {
                    adminEdit.putLong(key, ((Long) v).longValue());
                } else if (v instanceof String) {
                    adminEdit.putString(key, ((String) v));
                }
            }
            adminEdit.apply();

            res = true;
        } catch (IOException | ClassNotFoundException e) {
            Timber.e(e, "Exception while loading preferences from file due to : %s ", e.getMessage());
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException ex) {
                Timber.e(ex, "Exception thrown while closing an input stream due to: %s ", ex.getMessage());
            }
        }
        return res;
    }

    // This flag must be set each time the app starts up
    /*private static void setupGoogleAnalytics() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(Collect
                .getInstance());
        boolean isAnalyticsEnabled = settings.getBoolean(PreferenceKeys.KEY_ANALYTICS, true);
        GoogleAnalytics googleAnalytics = GoogleAnalytics.getInstance(Collect.getInstance());
        googleAnalytics.setAppOptOut(!isAnalyticsEnabled);
    }*/
}
