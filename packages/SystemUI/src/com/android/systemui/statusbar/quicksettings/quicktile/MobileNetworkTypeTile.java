package com.android.systemui.statusbar.quicksettings.quicktile;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.internal.telephony.Phone;
import com.android.systemui.R;
import com.android.systemui.statusbar.quicksettings.QuickSettingsContainerView;
import com.android.systemui.statusbar.quicksettings.QuickSettingsController;

public class MobileNetworkTypeTile extends QuickSettingsTile {

    private static final String TAG = "NetworkModeQuickSettings";

    // retrieved from Phone.apk
    private static final String ACTION_NETWORK_MODE_CHANGED = "com.android.internal.telephony.NETWORK_MODE_CHANGED";
    private static final String ACTION_MODIFY_NETWORK_MODE = "com.android.internal.telephony.MODIFY_NETWORK_MODE";
    private static final String EXTRA_NETWORK_MODE = "networkMode";

    private static final int STATE_2G_AND_3G = 1;
    private static final int STATE_2G_ONLY = 2;
    private static final int STATE_TURNING_ON = 3;
    private static final int STATE_TURNING_OFF = 4;
    private static final int STATE_INTERMEDIATE = 5;
    private static final int STATE_UNEXPECTED = 6;
    private static final int NO_NETWORK_MODE_YET = -99;
    private static final int NETWORK_MODE_UNKNOWN = -100;

    private static final int CM_MODE_3G2G = 0;
    private static final int CM_MODE_3GONLY = 1;
    private static final int CM_MODE_BOTH = 2;

    private int mMode = NO_NETWORK_MODE_YET;
    private int mIntendedMode = NO_NETWORK_MODE_YET;
    private int mInternalState = STATE_INTERMEDIATE;
    private int mState;

    public MobileNetworkTypeTile(Context context,
            LayoutInflater inflater, QuickSettingsContainerView container,
            QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        updateState();

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                int currentMode = getCurrentCMMode();

                Intent intent = new Intent(ACTION_MODIFY_NETWORK_MODE);
                switch (mMode) {
                    case Phone.NT_MODE_WCDMA_PREF:
                    case Phone.NT_MODE_GSM_UMTS:
                        intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_GSM_ONLY);
                        mInternalState = STATE_TURNING_OFF;
                        mIntendedMode = Phone.NT_MODE_GSM_ONLY;
                        break;
                    case Phone.NT_MODE_WCDMA_ONLY:
                        if (currentMode == CM_MODE_3GONLY) {
                            intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_GSM_ONLY);
                            mInternalState = STATE_TURNING_OFF;
                            mIntendedMode = Phone.NT_MODE_GSM_ONLY;
                        } else {
                            intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_WCDMA_PREF);
                            mInternalState = STATE_TURNING_ON;
                            mIntendedMode = Phone.NT_MODE_WCDMA_PREF;
                        }
                        break;
                    case Phone.NT_MODE_GSM_ONLY:
                        if (currentMode == CM_MODE_3GONLY || currentMode == CM_MODE_BOTH) {
                            intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_WCDMA_ONLY);
                            mInternalState = STATE_TURNING_ON;
                            mIntendedMode = Phone.NT_MODE_WCDMA_ONLY;
                        } else {
                            intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_WCDMA_PREF);
                            mInternalState = STATE_TURNING_ON;
                            mIntendedMode = Phone.NT_MODE_WCDMA_PREF;
                        }
                        break;
                }

                mMode = NETWORK_MODE_UNKNOWN;
                mContext.sendBroadcast(intent);
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.phone", "com.android.phone.Settings");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startSettingsActivity(intent);
                return true;
            }
        };

        qsc.registerAction(ACTION_NETWORK_MODE_CHANGED, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getExtras() != null) {
            mMode = intent.getExtras().getInt(EXTRA_NETWORK_MODE);
            //Update to actual state
            mIntendedMode = mMode;
        }

        //need to clear intermediate states and update the tile
        mInternalState = networkModeToState();
        applyNetworkTypeChanges();
    }

    private void applyNetworkTypeChanges(){
        updateState();
        updateQuickSettings();
    }

    protected void updateState() {
        mMode = get2G3G(mContext);
        mState = networkModeToState();

        mLabel = mContext.getString(R.string.quick_settings_network_type);

        switch (mState) {
            case STATE_UNEXPECTED:
                mDrawable = R.drawable.stat_2g3g_off;
                break;
            case STATE_2G_ONLY:
                mDrawable = R.drawable.stat_2g3g_off;
                break;
            case STATE_2G_AND_3G:
                if (mMode == Phone.NT_MODE_WCDMA_ONLY) {
                    mDrawable = R.drawable.stat_3g_on;
                } else {
                    mDrawable = R.drawable.stat_2g3g_on;
                }
                break;
            case STATE_INTERMEDIATE:
                if (mInternalState == STATE_TURNING_ON) {
                    if (mIntendedMode == Phone.NT_MODE_WCDMA_ONLY) {
                        mDrawable = R.drawable.stat_3g_on;
                    } else {
                        mDrawable = R.drawable.stat_2g3g_on;
                    }
                } else {
                    mDrawable = R.drawable.stat_2g3g_off;
                }
                break;
        }
    }

    private static int get2G3G(Context context) {
        int state = 99;
        try {
            state = Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.PREFERRED_NETWORK_MODE);
        } catch (SettingNotFoundException e) {
            // Do nothing
        }
        return state;
    }

    private int networkModeToState() {
        if (mInternalState == STATE_TURNING_ON || mInternalState == STATE_TURNING_OFF) {
            return STATE_INTERMEDIATE;
        }

        switch (mMode) {
            case Phone.NT_MODE_WCDMA_PREF:
            case Phone.NT_MODE_WCDMA_ONLY:
            case Phone.NT_MODE_GSM_UMTS:
                return STATE_2G_AND_3G;
            case Phone.NT_MODE_GSM_ONLY:
                return STATE_2G_ONLY;
            case Phone.NT_MODE_CDMA:
            case Phone.NT_MODE_CDMA_NO_EVDO:
            case Phone.NT_MODE_EVDO_NO_CDMA:
            case Phone.NT_MODE_GLOBAL:
                // need to check what is going on
                Log.d(TAG, "Unexpected network mode (" + mMode + ")");
                return STATE_UNEXPECTED;
        }
        return STATE_INTERMEDIATE;
    }

    private int getCurrentCMMode() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.EXPANDED_NETWORK_MODE,
                CM_MODE_3G2G);
    }
}
