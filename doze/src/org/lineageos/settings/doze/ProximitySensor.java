/*
 * Copyright (C) 2017-2019 crDroid Android Project
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

package org.lineageos.settings.doze;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ProximitySensor implements SensorEventListener {

    private static final boolean DEBUG = false;
    private static final String TAG = "ProximitySensor";

    // Maximum time for the hand to cover the sensor: 1s
    private static final int HANDWAVE_MAX_DELTA_MS = 1000;

    // Minimum time until the device is considered to have been in the pocket: 2.5s
    private static final int POCKET_MIN_DELTA_MS = 2500;

    private SensorManager mSensorManager;
    private Sensor mSensorProximity;
    private Context mContext;
    private ExecutorService mExecutorService;

    private boolean mSawNear = false;
    private long mStateChangedTime = 0;

    private boolean mHandwaveEnabled;
    private boolean mPocketEnabled;

    private long mEntryTimestamp;

    public ProximitySensor(Context context) {
        mContext = context;
        final boolean wakeup = context.getResources().getBoolean(
                com.android.internal.R.bool.config_deviceHaveWakeUpProximity);
        mSensorManager = (SensorManager) mContext.getSystemService(
                Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mSensorProximity = mSensorManager.getDefaultSensor(
                        Sensor.TYPE_PROXIMITY, wakeup);
        }
        mExecutorService = Executors.newSingleThreadExecutor();
    }

    private Future<?> submit(Runnable runnable) {
        return mExecutorService.submit(runnable);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        boolean isNear = event.values[0] < mSensorProximity.getMaximumRange();
        if (mSawNear && !isNear) {
            if (shouldPulse(event.timestamp)) {
                Utils.launchDozePulse(mContext);
            }
        } else {
            mStateChangedTime = SystemClock.elapsedRealtime();
        }
        mSawNear = isNear;
    }

    private boolean shouldPulse(long timestamp) {
        long delta = timestamp - mStateChangedTime;

        if ((delta < HANDWAVE_MAX_DELTA_MS) &&
                 mHandwaveEnabled) {
            return true;
        } else if ((delta >= POCKET_MIN_DELTA_MS) &&
                 mPocketEnabled) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        /* Empty */
    }

    // Switching screen OFF - we enable the sensor
    protected void enable() {
        if (DEBUG) Log.d(TAG, "Enabling");
        submit(() -> {
            // We save user settings so at next screen ON call (enable())
            // we don't need to read them again from the Settings provider
            mStateChangedTime = SystemClock.elapsedRealtime();
            mSensorManager.registerListener(this, mSensorProximity,
                    SensorManager.SENSOR_DELAY_NORMAL);

        });
    }

    // Switching screen ON - we disable the sensor
    protected void disable() {
        if (DEBUG) Log.d(TAG, "Disabling");
        submit(() -> {
            mSensorManager.unregisterListener(this, mSensorProximity);
        });
    }
}
