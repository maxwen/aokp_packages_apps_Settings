/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.SeekBarDialogPreference;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;

public class ButtonBrightnessPreference extends SeekBarDialogPreference implements
        SeekBar.OnSeekBarChangeListener, CheckBox.OnCheckedChangeListener {

    private final int mButtonBrightnessMinimum = 0;
    private final int mButtonBrightnessMaximum = 255;

    private SeekBar mSeekBar;
    private int mOldBrightness;
    private int mCurBrightness = -1;
    private boolean mRestoredOldState;
    private CheckBox mCheckBox;
    private boolean mUseScreenBrightness = true;
    private boolean mOldUseScreenBrightness = true;
        
    private static final int SEEK_BAR_RANGE = 10000;


    public ButtonBrightnessPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.preference_dialog_button_brightness);
        setDialogIcon(R.drawable.ic_settings_display);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        mRestoredOldState = false;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mUseScreenBrightness = Settings.System.getBoolean(getContext().getContentResolver(),
                Settings.System.BUTTON_USE_SCREEN_BRIGHTNESS, true);
        mOldUseScreenBrightness = mUseScreenBrightness;
        int brightnessMode = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, 0);        

        mSeekBar = getSeekBar(view);
        mSeekBar.setMax(SEEK_BAR_RANGE);
        mOldBrightness = getBrightness();
        mSeekBar.setProgress(mOldBrightness);

        mCheckBox = (CheckBox)view.findViewById(R.id.use_screen_brightness);
        mCheckBox.setOnCheckedChangeListener(this);
        mCheckBox.setChecked(mUseScreenBrightness);
        mSeekBar.setEnabled(!mUseScreenBrightness);
        mSeekBar.setOnSeekBarChangeListener(this);

        if (brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC){
            mSeekBar.setEnabled(false);
            mCheckBox.setEnabled(false);
        }
    }

    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromTouch) {
        setBrightness(progress);
    }

    public void onStartTrackingTouch(SeekBar seekBar) {
        // NA
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
        // NA
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        mUseScreenBrightness = isChecked;
        mSeekBar.setProgress(getBrightness());
        mSeekBar.setEnabled(!mUseScreenBrightness);
        setBrightness(mSeekBar.getProgress());
    }
    
    private int getBrightness() {
        float brightness = 0;
         
        if (mUseScreenBrightness){
            brightness = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 100);
        } else {
            if (mCurBrightness < 0) {
                brightness = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.BUTTON_BRIGHTNESS, 100);
            } else {
                brightness = mCurBrightness;
            }
        }
        brightness = (brightness - mButtonBrightnessMinimum)
                / (mButtonBrightnessMaximum - mButtonBrightnessMinimum);
        
        return (int)(brightness*SEEK_BAR_RANGE);
    }

    private void onButtonBrightnessChanged() {
        mSeekBar.setProgress(getBrightness());
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        final ContentResolver resolver = getContext().getContentResolver();

        if (positiveResult) {
            setBrightness(mSeekBar.getProgress());
        } else {
            restoreOldState();
        }
    }
    
    private void restoreOldState() {
        if (mRestoredOldState) return;

        mUseScreenBrightness = mOldUseScreenBrightness;
        setBrightness(mOldBrightness);
        mRestoredOldState = true;
    }

    private void setBrightness(int brightness) {
        Settings.System.putBoolean(getContext().getContentResolver(),
                Settings.System.BUTTON_USE_SCREEN_BRIGHTNESS, mUseScreenBrightness);

        if (!mUseScreenBrightness){
            int range = (mButtonBrightnessMaximum - mButtonBrightnessMinimum);
            brightness = (brightness * range)/SEEK_BAR_RANGE + mButtonBrightnessMinimum;

            mCurBrightness = brightness;
            Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.BUTTON_BRIGHTNESS, brightness);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (getDialog() == null || !getDialog().isShowing()) return superState;

        // Save the dialog state
        final SavedState myState = new SavedState(superState);
        myState.progress = mSeekBar.getProgress();
        myState.oldProgress = mOldBrightness;
        myState.curBrightness = mCurBrightness;
        myState.useScreenBrightness = mUseScreenBrightness;
        myState.oldUseScreenBrightness = mOldUseScreenBrightness;

        // Restore the old state when the activity or dialog is being paused
        restoreOldState();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        mOldBrightness = myState.oldProgress;
        setBrightness(myState.progress);
        mCurBrightness = myState.curBrightness;
        mUseScreenBrightness = myState.useScreenBrightness;
        mOldUseScreenBrightness = myState.oldUseScreenBrightness;
    }

    private static class SavedState extends BaseSavedState {

        int progress;
        int oldProgress;
        int curBrightness;
        boolean oldUseScreenBrightness;
        boolean useScreenBrightness;

        public SavedState(Parcel source) {
            super(source);
            progress = source.readInt();
            oldProgress = source.readInt();
            curBrightness = source.readInt();
            useScreenBrightness = source.readInt() == 1;
            oldUseScreenBrightness = source.readInt() == 1;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(progress);
            dest.writeInt(oldProgress);
            dest.writeInt(curBrightness);
            dest.writeInt(useScreenBrightness ? 1 : 0);
            dest.writeInt(oldUseScreenBrightness ? 1 : 0);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {

            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}

