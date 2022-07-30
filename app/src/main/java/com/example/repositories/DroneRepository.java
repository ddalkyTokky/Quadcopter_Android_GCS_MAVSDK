package com.example.repositories;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.LiveDataReactiveStreams;

import com.example.googlemapmavsdk.BuildConfig;
import com.example.googlemapmavsdk.R;
import com.example.io.TcpInputOutputManager;
import com.example.models.PositionRelative;
import com.example.models.Speed;
import com.google.android.gms.maps.model.LatLng;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;

import io.mavsdk.System;
import io.mavsdk.action.Action;
import io.mavsdk.camera.Camera;
import io.mavsdk.core.Core;
import io.mavsdk.geofence.Geofence;
import io.mavsdk.mavsdkserver.MavsdkServer;
import io.mavsdk.mission.Mission;
import io.mavsdk.mission_raw.MissionRaw;
import io.mavsdk.telemetry.Telemetry;
import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import android.os.Handler;
import android.os.Looper;

public class DroneRepository {
    private static final String TAG = "LOG_" + DroneRepository.class.getSimpleName();

    private static final String MAVSDK_SERVER_IP = "127.0.0.1";
    private static final int USB_BAUD_RATE = 57600;
    private static final int BUFFER_SIZE = 2048;
    private static final int IO_TIMEOUT = 1000;
    private static final long THROTTLE_TIME_MILLIS = 100;

    private int TCP_SERVER_PORT = 8888;

    private final Context mAppContext;
    private final CompositeDisposable mCompositeDisposable;
    private System mDrone;
    private MavsdkServer mMavsdkServer;
    private SerialInputOutputManager mSerialManager;
    private TcpInputOutputManager mTcpManager;

    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";

    private LiveData<PositionRelative> mPositionRelativeLiveData;
    private LiveData<Speed> mSpeedLiveData;
    private LiveData<Telemetry.Battery> mBatteryLiveData;
    private LiveData<Telemetry.GpsInfo> mGpsInfoLiveData;
    private LiveData<Telemetry.Position> mPositionLiveData;
    private LiveData<Telemetry.FlightMode> mFlightModeLiveData;
    private LiveData<Telemetry.EulerAngle> mAttitudeLiveData;
    private LiveData<Telemetry.RcStatus> mRcStatusLiveData;
    private LiveData<Core.ConnectionState> mDroneConnectionStateLiveData;

    private CountDownLatch latch = new CountDownLatch(0);

    private boolean usbConnectionStatus = false;

    private UsbSerialDriver mDriver;
    private UsbManager mManager;
    private UsbDevice mDevice;
    private String systemAddress;

    private UsbSerialPort mUsbSerialPort;

    private static final float MISSION_HEIGHT = 5f;
    private static final float MISSION_SPEED = 1f;
    private static final float RTL_RETURN_HEIGHT = 5f;

    private String completeErrorMessage;

    ExecutorService mExecutorService;

    private final Handler mainLooper;

    public DroneRepository(Application application) {
        mAppContext = application.getApplicationContext();
        mCompositeDisposable = new CompositeDisposable();

        connect();

        mainLooper = new Handler(Looper.getMainLooper());
    }

    public void connect() {
        if (usbConnectionStatus) {
            Toast.makeText(mAppContext, "Working Connection Exist", Toast.LENGTH_SHORT).show();
            return;
        }

        mPositionRelativeLiveData = null;
        mSpeedLiveData = null;
        mBatteryLiveData = null;
        mGpsInfoLiveData = null;
        mPositionLiveData = null;
        mFlightModeLiveData = null;
        mAttitudeLiveData = null;
        mRcStatusLiveData = null;

        if (initializeUSB()) {
            initializeTCP();
            initializeServerAndDrone(systemAddress);
        }
    }

    private void disconnect() {
        usbConnectionStatus = false;
        TCP_SERVER_PORT += 1;

        mTcpManager.stop();

        try {
            mUsbSerialPort.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //mMavsdkServer.stop();
        mDrone.dispose();

        /*
        mPositionRelativeLiveData.removeObservers((LifecycleOwner) mAppContext);
        mSpeedLiveData.removeObservers((LifecycleOwner) mAppContext);
        mBatteryLiveData.removeObservers((LifecycleOwner) mAppContext);
        mGpsInfoLiveData.removeObservers((LifecycleOwner) mAppContext);
        mPositionLiveData.removeObservers((LifecycleOwner) mAppContext);
        mFlightModeLiveData.removeObservers((LifecycleOwner) mAppContext);
        mAttitudeLiveData.removeObservers((LifecycleOwner) mAppContext);
        mRcStatusLiveData.removeObservers((LifecycleOwner) mAppContext);
         */
    }

    private boolean initializeUSB() {
        mManager = (UsbManager) mAppContext.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> driverList = UsbSerialProber.getDefaultProber().findAllDrivers(mManager);
        if (driverList.isEmpty()) {
            Toast.makeText(mAppContext, R.string.str_usb_device_not_found, Toast.LENGTH_SHORT).show();
            return false;
        }

        mDriver = driverList.get(0);
        mDevice = mDriver.getDevice();
        boolean hasPermission = mManager.hasPermission(mDevice);

        if (!hasPermission) {
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(mAppContext, 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
            mManager.requestPermission(mDevice, usbPermissionIntent);
            return false;
        }
        //Log.d(TAG, "initializeUsbDevice: hasPermission: " + mManager.hasPermission(mDevice));
        return true;
    }

    private void initializeTCP() {
        mUsbSerialPort = mDriver.getPorts().get(0);
        UsbDeviceConnection connection = mManager.openDevice(mDevice);

        try {
            mUsbSerialPort.open(connection);
            mUsbSerialPort.setParameters(
                    USB_BAUD_RATE,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mSerialManager = new SerialInputOutputManager(mUsbSerialPort);
        mSerialManager.setReadTimeout(IO_TIMEOUT);
        mSerialManager.setReadBufferSize(BUFFER_SIZE);
        mSerialManager.setWriteTimeout(IO_TIMEOUT);
        mSerialManager.setWriteBufferSize(BUFFER_SIZE);

        mTcpManager = new TcpInputOutputManager(TCP_SERVER_PORT);
        mTcpManager.setReadBufferSize(BUFFER_SIZE);
        mTcpManager.setWriteBufferSize(BUFFER_SIZE);

        mSerialManager.setListener(new SerialInputOutputManager.Listener() {
            @Override
            public void onNewData(byte[] data) {
                // Sending Serial data to TCP
                mTcpManager.writeAsync(data);
            }

            @Override
            public void onRunError(Exception e) {
                mainLooper.post(() -> {
                    Toast.makeText(mAppContext, "Serial Error", Toast.LENGTH_SHORT).show();
                    disconnect();
                });
            }
        });

        mTcpManager.setListener(new TcpInputOutputManager.Listener() {
            @Override
            public void onNewData(byte[] data) {
                // Sending TCP data to Serial
                mSerialManager.writeAsync(data);
            }

            @Override
            public void onRunError(Exception e) {
                mainLooper.post(() -> {
                    Toast.makeText(mAppContext, "Tcp Error", Toast.LENGTH_SHORT).show();
                    disconnect();
                });
            }
        });

        mExecutorService = Executors.newFixedThreadPool(2);
        mExecutorService.submit(mSerialManager);
        mExecutorService.submit(mTcpManager);

        systemAddress = "tcp://:" + TCP_SERVER_PORT;
    }

    private void initializeServerAndDrone(@NonNull String systemAddress) {
        mMavsdkServer = new MavsdkServer();
        int mavsdkServerPort = mMavsdkServer.run(systemAddress);
        mDrone = new System(MAVSDK_SERVER_IP, mavsdkServerPort);

        usbConnectionStatus = true;
        Toast.makeText(mAppContext, "Usb Connected", Toast.LENGTH_SHORT).show();
    }

    public void printCompleteErrorMessage() {
        if (completeErrorMessage != null) {
            Toast.makeText(mAppContext, completeErrorMessage, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(mAppContext, "No Action, Mission, Camera.... Completes & Errors", Toast.LENGTH_SHORT).show();
        }

        completeErrorMessage = null;
    }

    public void arm() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        mDrone.getAction().arm()
                .doOnComplete(() ->
                        completeErrorMessage = "Arm Done")
                .doOnError(throwable ->
                        completeErrorMessage = "Arm Error: " + ((Action.ActionException) throwable).getCode().toString())
                .subscribe(latch::getCount, throwable -> latch.getCount());
    }

    public void takeoff(float height) {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        mDrone.getAction().arm()
                .doOnComplete(() ->
                        completeErrorMessage = "Arm Done")
                .doOnError(throwable ->
                        completeErrorMessage = "Arm Error: " + ((Action.ActionException) throwable).getCode().toString())
                .andThen(mDrone.getAction()
                        .setTakeoffAltitude(height)
                        .doOnComplete(() ->
                                completeErrorMessage = "Set Takeoff Alt Done")
                        .doOnError(throwable ->
                                completeErrorMessage
                                        = "Set Takeoff Alt Error: "
                                                + ((Action.ActionException) throwable).getCode().toString()))
                .andThen(mDrone.getAction()
                        .takeoff()
                        .doOnComplete(() ->
                                completeErrorMessage = "Take off Done")
                        .doOnError(throwable ->
                                completeErrorMessage = "Take off Error: " + ((Action.ActionException) throwable).getCode().toString()))
                .subscribe(latch::getCount, throwable -> latch.getCount());
    }

    public void kill() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        mDrone.getAction().kill()
                .doOnComplete(() ->
                        completeErrorMessage = "Disarm Done")
                .doOnError(throwable ->
                        completeErrorMessage = "Disarm Error: " + ((Action.ActionException) throwable).getCode().toString())
                .subscribe(latch::getCount, throwable -> latch.getCount());
    }

    public void land() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        mDrone.getAction().land()
                .doOnComplete(() ->
                        completeErrorMessage = "Land Done")
                .doOnError(throwable ->
                        completeErrorMessage = "Land Error: " + ((Action.ActionException) throwable).getCode().toString())
                .subscribe(latch::getCount, throwable -> latch.getCount());
    }

    public void return0() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        mDrone.getAction().returnToLaunch()
                .doOnComplete(() ->
                        completeErrorMessage = "Return Done")
                .doOnError(throwable ->
                        completeErrorMessage = "Return Error: " + ((Action.ActionException) throwable).getCode().toString())
                .subscribe(latch::getCount, throwable -> latch.getCount());
    }

    public void uploadMission(List<LatLng> missionLatLngs) {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        List<MissionRaw.MissionItem> missionItems = new ArrayList<>();
        
        int sequence = 0;

        missionItems.add(new MissionRaw.MissionItem(
                sequence, //sequence
                2, //MAV_FRAME_MISSION
                178, //MAV_CMD_DO_CHANGE_SPEED
                0, //False True
                1, //False True
                1f, //Speed type (0=Airspeed, 1=Ground Speed, 2=Climb Speed, 3=Descent Speed)
                MISSION_SPEED, //Speed (-1 indicates no change) meter/sec
                -1f, //Throttle (-1 indicates no change) %
                0f, //Reserved (set to 0)
                0, //x
                0, //y
                Float.NaN, //z
                0 //MAV_MISSION_TYPE_MISSION
        ));
        sequence += 1;

        missionItems.add(new MissionRaw.MissionItem(
                sequence, //sequence
                3, //MAV_FRAME_GLOBAL_RELATIVE_ALT
                22, //MAV_CMD_NAV_TAKEOFF
                1, //False True
                1, //False True
                Float.NaN, //PARAMs
                Float.NaN,
                Float.NaN,
                Float.NaN,
                (int) (missionLatLngs.get(0).latitude * 10000000), //x
                (int) (missionLatLngs.get(0).longitude * 10000000), //y
                MISSION_HEIGHT, //z
                0 //MAV_MISSION_TYPE_MISSION
        ));
        missionLatLngs.remove(0);
        sequence += 1;

        for (LatLng latlng : missionLatLngs) {
            missionItems.add(new MissionRaw.MissionItem(
                    sequence, //sequence
                    3, //MAV_FRAME_GLOBAL_RELATIVE_ALT
                    16, //MAV_CMD_NAV_WAYPOINT
                    0, //False True
                    1, //False True
                    Float.NaN, //Hold time. (ignored by fixed wing, time to stay at waypoint for rotary wing) secs.
                    Float.NaN, //Acceptance radius (if the sphere with this radius is hit, the waypoint sequences as reached) meter.
                    Float.NaN, //0 to pass through the WP, if > 0 radius to pass by WP. Positive value for clockwise orbit, negative value for counter-clockwise orbit. Allows trajectory control.
                                //meter
                    Float.NaN, //Desired yaw angle at waypoint (rotary wing). NaN to use the current system yaw heading mode (e.g. yaw towards next waypoint, yaw to home, etc.).
                                //degree °
                    (int) (latlng.latitude * 10000000), //x
                    (int) (latlng.longitude * 10000000), //y
                    MISSION_HEIGHT, //z
                    0 //MAV_MISSION_TYPE_MISSION
                    ));
            sequence += 1;
        }

        missionItems.add(new MissionRaw.MissionItem(
                sequence, //sequence
                2, //MAV_FRAME_MISSION
                20, //MAV_CMD_NAV_RETURN_TO_LAUNCH
                0, //False True
                1, //False True
                Float.NaN, //Empty PARAMs
                Float.NaN,
                Float.NaN,
                Float.NaN,
                0, //x
                0, //y
                0f, //z
                0 //MAV_MISSION_TYPE_MISSION
        ));
        sequence += 1;


        mDrone.getMissionRaw()
                .uploadMission(missionItems)
                .doOnComplete(() -> completeErrorMessage = "Upload Mission Done")
                .doOnError(throwable ->
                        completeErrorMessage = "Upload Mission Error: " + ((MissionRaw.MissionRawException) throwable).getCode().toString())
                .subscribe(latch::getCount, throwable -> latch.getCount());
    }

    public void startMission() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        arm();
/*
        mDrone.getAction()
                .setReturnToLaunchAltitude(RTL_RETURN_HEIGHT)
                .doOnComplete(() -> completeErrorMessage = "Set RTL_RETURN_ALT Done")
                .doOnError(throwable ->
                        completeErrorMessage = "Set RTL_RETURN_ALT Error: " + ((Action.ActionException) throwable).getCode().toString())
                .subscribe(latch::getCount, throwable -> latch.getCount());


 */
        mDrone.getMissionRaw().startMission()
                .doOnComplete(() -> completeErrorMessage = "Start Mission Done")
                .doOnError(throwable ->
                        completeErrorMessage = "Start Mission Error: " + ((MissionRaw.MissionRawException) throwable).getCode().toString())
                .subscribe(latch::getCount, throwable -> latch.getCount());
    }

    public void pauseMission() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        mDrone.getMissionRaw()
                .pauseMission()
                .doOnComplete(() -> completeErrorMessage = "Pause Mission Done")
                .doOnError(throwable ->
                        completeErrorMessage = "Pause Mission Error: " + ((MissionRaw.MissionRawException) throwable).getCode().toString());
    }

    public void clearMission() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        mDrone.getMissionRaw()
                .clearMission()
                .doOnComplete(() -> completeErrorMessage = "Clear Mission Done")
                .doOnError(throwable ->
                        completeErrorMessage = "Clear Mission Error: " + ((MissionRaw.MissionRawException) throwable).getCode().toString())
                .subscribe(latch::getCount, throwable -> latch.getCount());
    }

    public void camera() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        mDrone.getCamera()
                .takePhoto()
                .doOnComplete(() -> completeErrorMessage = "Camera Take Photo Done")
                .doOnError(throwable ->
                        completeErrorMessage = "Camera Take Photo Error: " + ((Camera.CameraException) throwable).getCode().toString())
                .subscribe(latch::getCount, throwable -> latch.getCount());
    }

    public void setGeofence(List<LatLng> geofenceLatLngs) {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Geofence.Point> points = new ArrayList<>();
        for (LatLng latlng : geofenceLatLngs) {
            points.add(new Geofence.Point(latlng.latitude, latlng.longitude));
        }

        List<Geofence.Polygon> polygons = new ArrayList<>();
        polygons.add(new Geofence.Polygon(points, Geofence.Polygon.FenceType.INCLUSION));

        mDrone.getGeofence()
                .uploadGeofence(polygons)
                .doOnComplete(() -> completeErrorMessage = "Set Geofence Done")
                .doOnError(throwable ->
                        completeErrorMessage = "Set Geofence Error: " + ((Geofence.GeofenceException) throwable).getCode().toString())
                .subscribe(latch::getCount, throwable -> latch.getCount());
    }

    public void clearGeofence() {
        //clearing Geofence by causing Error.
        //Can not find clear method from mavsdk 0.7.0 Geofence.
        //This function has to be changed or deprecated in future.
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Geofence.Point> points = new ArrayList<>();
        List<Geofence.Polygon> polygons = new ArrayList<>();

        points.add(new Geofence.Point((double) 0,(double) 0));
        points.add(new Geofence.Point((double) 1,(double) 1));

        polygons.add(new Geofence.Polygon(points, Geofence.Polygon.FenceType.INCLUSION));

        mDrone.getGeofence()
                .uploadGeofence(polygons)
                .doOnComplete(() -> completeErrorMessage = "Clear Geofence Done")
                .doOnError(throwable ->
                        completeErrorMessage = "Clear Geofence Error: " + ((Geofence.GeofenceException) throwable).getCode().toString())
                .subscribe(latch::getCount, throwable -> latch.getCount());
    }

    public LiveData<Telemetry.FlightMode> getFlightMode() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (mFlightModeLiveData == null) {
            Flowable<Telemetry.FlightMode> flightModeFlowable;

            flightModeFlowable = mDrone.getTelemetry().getFlightMode()
                    .distinctUntilChanged()
                    .subscribeOn(Schedulers.io());

            //positionFlowable.onBackpressureLatest();

            mFlightModeLiveData = LiveDataReactiveStreams.fromPublisher(flightModeFlowable);
        }

        return mFlightModeLiveData;
    }

    public LiveData<Telemetry.Position> getPosition() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (mPositionLiveData == null) {
            Flowable<Telemetry.Position> positionFlowable;

            positionFlowable = mDrone.getTelemetry().getPosition()
                    .throttleLast(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io());

            //positionFlowable.onBackpressureLatest();

            mPositionLiveData = LiveDataReactiveStreams.fromPublisher(positionFlowable);
        }

        return mPositionLiveData;
    }

    public LiveData<Telemetry.GpsInfo> getGpsInfo() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (mGpsInfoLiveData == null) {
            Flowable<Telemetry.GpsInfo> gpsInfoFlowable;

            gpsInfoFlowable = mDrone.getTelemetry().getGpsInfo()
                    .throttleLast(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io());

            mGpsInfoLiveData = LiveDataReactiveStreams.fromPublisher(gpsInfoFlowable);
        }

        return mGpsInfoLiveData;
    }

    public LiveData<Telemetry.Battery> getBattery() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (mBatteryLiveData == null) {
            Flowable<Telemetry.Battery> batteryFlowable;
            batteryFlowable = mDrone.getTelemetry().getBattery()
                    .throttleLast(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io());

            mBatteryLiveData = LiveDataReactiveStreams.fromPublisher(batteryFlowable);
        }

        return mBatteryLiveData;
    }

    public LiveData<Speed> getSpeed() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (mSpeedLiveData == null) {
            Flowable<Speed> speedFlowable;

            speedFlowable = mDrone.getTelemetry().getPositionVelocityNed()
                    .throttleLast(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                    .map(positionVelocityNed -> {
                        float hspeed = (float) Math.hypot(positionVelocityNed.getVelocity().getNorthMS(),
                                positionVelocityNed.getVelocity().getEastMS());
                        float vspeed = Math.abs(positionVelocityNed.getVelocity().getDownMS());
                        return new Speed(hspeed, vspeed);
                    })
                    .subscribeOn(Schedulers.io());

            mSpeedLiveData = LiveDataReactiveStreams.fromPublisher(speedFlowable);
        }

        return mSpeedLiveData;
    }

    public LiveData<Telemetry.EulerAngle> getAttitude() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (mAttitudeLiveData == null) {
            Flowable<Telemetry.EulerAngle> attitudeFlowable;
            attitudeFlowable = mDrone.getTelemetry().getAttitudeEuler()
                    .throttleLast(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io());

            mAttitudeLiveData = LiveDataReactiveStreams.fromPublisher(attitudeFlowable);
        }

        return mAttitudeLiveData;
    }

    public LiveData<Telemetry.RcStatus> getRcStatus() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (mRcStatusLiveData == null) {
            Flowable<Telemetry.RcStatus> rcStatusFlowable;
            rcStatusFlowable = mDrone.getTelemetry().getRcStatus()
                    .throttleLast(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io());

            mRcStatusLiveData = LiveDataReactiveStreams.fromPublisher(rcStatusFlowable);
        }

        return mRcStatusLiveData;
    }

    public LiveData<Core.ConnectionState> getDroneConnectionState() {
        if (usbConnectionStatus == false) {
            Toast.makeText(mAppContext, "Usb not connected", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (mDroneConnectionStateLiveData == null) {
            Flowable<Core.ConnectionState> droneConnectionStateFlowable;
            droneConnectionStateFlowable = mDrone.getCore().getConnectionState()
                    .distinctUntilChanged()
                    .subscribeOn(Schedulers.io());

            mDroneConnectionStateLiveData = LiveDataReactiveStreams.fromPublisher(droneConnectionStateFlowable);
        }

        return mDroneConnectionStateLiveData;
    }
}
    /*
    public void destroy() {
        mCompositeDisposable.dispose();

        if(mDrone != null){
            mDrone.dispose();
        }
        if(mMavsdkServer != null){
            mMavsdkServer.stop();
        }
        if (mSerialManager != null) {
            mSerialManager.stop();
        }
        if (mTcpManager != null) {
            mTcpManager.stop();
        }
    }
    */

    /*
    Telemetry - Allow users to get vehicle telemetry and state information.
            - (e.g. battery, GPS, RC connection, flight mode etc.) and set telemetry update rates.
    Action - Enable simple actions such as arming, taking off, and landing.
    Shell - Allow to communicate with the vehicle's system shell.
    Calibration - Enable to calibrate sensors of a drone such as gyro, accelerometer, and magnetometer.
    Camera - Can be used to manage cameras that implement the MAVLink.
        - Camera Protocol: https://mavlink.io/en/protocol/camera.html.
        - Provides handling of camera trigger commands.
    Core - Access to the connection state and core configurations.
    Failure - Inject failures into system to test failsafes.
    FollowMe - Allow users to command the vehicle to follow a specific target.
            - The target is provided as a GPS coordinate and altitude.
    Ftp - Implements file transfer functionality using MAVLink FTP.
    Geofence - Enable setting a geofence.
    Gimbal - Provide control over a gimbal.
    Info - Provide information about the hardware and/or software of a system.
    LogFiles - Allow to download log files from the vehicle after a flight is complete.
            - For log streaming during flight check the logging plugin.
    ManualControl - Enable manual control using e.g. a joystick or gamepad.
    Mission - Enable waypoint missions.
    MissionRaw - Enable raw missions as exposed by MAVLink.
            - Acts as a vehicle and receives incoming missions from GCS (in raw MAVLINK format).
            - Provides current mission item state, so the server can progress through missions.
    Mocap - Allows interfacing a vehicle with a motion capture system in
        - order to allow navigation without global positioning sources available
        - (e.g. indoors, or when flying under a bridge. etc.).
    OffBoard - Control a drone with position, velocity, attitude or motor commands.
            - The module is called offboard because the commands can be sent from external sources
            - as opposed to onboard control right inside the autopilot "board".
            - Client code must specify a setpoint before starting offboard mode.
            - Mavsdk automatically sends setpoints at 20Hz (PX4 Offboard mode requires that setpoints
            - are minimally sent at 2Hz).
    Param - Provide raw access to get and set parameters.
    Tune - Enable creating and sending a tune to be played on the system.
    */
