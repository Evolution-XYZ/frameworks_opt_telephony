/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.internal.telephony.satellite;

import static android.telephony.satellite.SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SUCCESS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Looper;
import android.telephony.satellite.SatelliteDatagram;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DatagramControllerTest extends TelephonyTest {
    private static final String TAG = "DatagramControllerTest";

    private DatagramController mDatagramControllerUT;

    @Mock private DatagramReceiver mMockDatagramReceiver;
    @Mock private DatagramDispatcher mMockDatagramDispatcher;
    @Mock private PointingAppController mMockPointingAppController;
    @Mock private SatelliteSessionController mMockSatelliteSessionController;

    private static final int SUB_ID = 0;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");

        replaceInstance(DatagramDispatcher.class, "sInstance", null,
                mMockDatagramDispatcher);
        replaceInstance(DatagramReceiver.class, "sInstance", null,
                mMockDatagramReceiver);
        replaceInstance(SatelliteSessionController.class, "sInstance", null,
                mMockSatelliteSessionController);
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        mDatagramControllerUT = new DatagramController(
                mContext, Looper.myLooper(), mMockPointingAppController);

        // Move both send and receive to IDLE state
        mDatagramControllerUT.updateSendStatus(SUB_ID, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                0, SATELLITE_RESULT_SUCCESS);
        mDatagramControllerUT.updateReceiveStatus(SUB_ID, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                0, SATELLITE_RESULT_SUCCESS);
        pushDemoModeSosDatagram();
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        super.tearDown();
    }

    @Test
    public void testUpdateSendStatus() throws Exception {
        testUpdateSendStatus(true, SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING);
        testUpdateSendStatus(true, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        testUpdateSendStatus(false, SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING);
    }

    @Test
    public void testUpdateReceiveStatus() throws Exception {
        testUpdateReceiveStatus(true, SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING);
        testUpdateReceiveStatus(true, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        testUpdateReceiveStatus(false, SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING);
    }

    @Test
    public void testSetDeviceAlignedWithSatellite() throws Exception {
        testSetDeviceAlignedWithSatellite(true);
        testSetDeviceAlignedWithSatellite(false);
    }

    private void testUpdateSendStatus(boolean isDemoMode, int sendState) {
        mDatagramControllerUT.setDemoMode(isDemoMode);
        clearAllInvocations();

        int sendPendingCount = 1;
        int errorCode = SATELLITE_RESULT_SUCCESS;
        mDatagramControllerUT.updateSendStatus(SUB_ID, sendState, sendPendingCount, errorCode);

        verify(mMockSatelliteSessionController)
                .onDatagramTransferStateChanged(eq(sendState), anyInt());
        verify(mMockPointingAppController).updateSendDatagramTransferState(
                eq(SUB_ID), eq(sendState), eq(sendPendingCount), eq(errorCode));

        if (isDemoMode) {
            if (sendState == SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE) {
                verify(mMockDatagramReceiver).pollPendingSatelliteDatagrams(
                        anyInt(), any(Consumer.class));
            }
        } else {
            verify(mMockDatagramReceiver, never()).pollPendingSatelliteDatagrams(
                    anyInt(), any(Consumer.class));
        }
    }

    private void testUpdateReceiveStatus(boolean isDemoMode, int receiveState) {
        mDatagramControllerUT.setDemoMode(isDemoMode);
        clearAllInvocations();

        int receivePendingCount = 1;
        int errorCode = SATELLITE_RESULT_SUCCESS;
        mDatagramControllerUT.updateReceiveStatus(
                SUB_ID, receiveState, receivePendingCount, errorCode);

        verify(mMockSatelliteSessionController)
                .onDatagramTransferStateChanged(anyInt(), eq(receiveState));
        verify(mMockPointingAppController).updateReceiveDatagramTransferState(
                eq(SUB_ID), eq(receiveState), eq(receivePendingCount), eq(errorCode));

        if (isDemoMode && receiveState == SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE) {
            verify(mMockDatagramReceiver).pollPendingSatelliteDatagrams(
                    anyInt(), any(Consumer.class));
        } else {
            verify(mMockDatagramReceiver, never()).pollPendingSatelliteDatagrams(
                    anyInt(), any(Consumer.class));
        }
    }

    private void testSetDeviceAlignedWithSatellite(boolean isAligned) {
        mDatagramControllerUT.setDemoMode(true);
        clearAllInvocations();

        mDatagramControllerUT.setDeviceAlignedWithSatellite(isAligned);
        verify(mMockDatagramDispatcher).setDeviceAlignedWithSatellite(eq(isAligned));
        verify(mMockDatagramReceiver).setDeviceAlignedWithSatellite(eq(isAligned));
        if (isAligned) {
            verify(mMockDatagramReceiver).pollPendingSatelliteDatagrams(
                    anyInt(), any(Consumer.class));
        } else {
            verify(mMockDatagramReceiver, never()).pollPendingSatelliteDatagrams(
                    anyInt(), any(Consumer.class));
        }
    }

    private void clearAllInvocations() {
        clearInvocations(mMockSatelliteSessionController);
        clearInvocations(mMockPointingAppController);
        clearInvocations(mMockDatagramReceiver);
        clearInvocations(mMockDatagramDispatcher);
    }

    private void pushDemoModeSosDatagram() {
        String testMessage = "This is a test datagram message";
        SatelliteDatagram datagram = new SatelliteDatagram(testMessage.getBytes());
        mDatagramControllerUT.setDemoMode(true);
        mDatagramControllerUT.pushDemoModeDatagram(DATAGRAM_TYPE_SOS_MESSAGE, datagram);
    }
}
