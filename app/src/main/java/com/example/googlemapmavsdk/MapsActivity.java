package com.example.googlemapmavsdk;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.googlemapmavsdk.databinding.ActivityMapsBinding;

import com.example.repositories.DroneRepository;

import java.lang.reflect.Array;
import java.net.InterfaceAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    private Marker mMarker;
    private List<Marker> missionMarkers = new ArrayList<Marker>();
    private List<LatLng> missionLatLngs = new ArrayList<LatLng>();

    private DroneRepository mDroneRepository;

    private AtomicReference<Double> currentLat = new AtomicReference<>((double) 37.2974);
    private AtomicReference<Double> currentLong = new AtomicReference<>((double) 126.8356);
    private AtomicReference<Float> currentAlt = new AtomicReference<>((float) -1f);

    private AtomicReference<String> mFlightMode = new AtomicReference<>((String) "Preparing... Press Again.");

    private AtomicReference<Float> batteryPercentage = new AtomicReference<>((float) -1f);
    private AtomicReference<Float> batteryVoltage = new AtomicReference<>((float) -1f);

    private AtomicReference<Float> speedXY = new AtomicReference<>((float) -1f);
    private AtomicReference<Float> speedZ = new AtomicReference<>((float) -1f);

    private AtomicReference<Float> eulerPitch = new AtomicReference<>((float) -999f);
    private AtomicReference<Float> eulerRoll = new AtomicReference<>((float) -999f);
    private AtomicReference<Float> eulerYaw = new AtomicReference<>((float) -999f);

    private AtomicReference<Boolean> rcOnceAvailable = new AtomicReference<>((boolean) false);
    private AtomicReference<Boolean> rcIsAvailable = new AtomicReference<>((boolean) false);
    private AtomicReference<Float> rcSignalStrength = new AtomicReference<>((float) -1f);

    private AtomicReference<Boolean> droneConnectionState = new AtomicReference<>((boolean) false);

    private AtomicReference<Integer> totalMission = new AtomicReference<>((int) -1);
    private AtomicReference<Integer> currentMission = new AtomicReference<>((int) -1);

    private static final float ZOOM_SCALE = 17f;
    private static final float MISSION_MARKER_COLOR = 180f;
    private static float TAKEOFF_HEIGHT = 3f;
    private static float RTL_RETURN_HEIGHT = 7f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mDroneRepository = new DroneRepository(getApplication());
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        LatLng initialPoint = new LatLng(currentLat.get(), currentLong.get());
        mMarker = mMap.addMarker(new MarkerOptions().position(initialPoint).title("Marker in ERICA"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(initialPoint));
        mMap.moveCamera(CameraUpdateFactory.zoomTo(ZOOM_SCALE));

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng point) {
                missionMarkers.add(
                        mMap.addMarker(
                                new MarkerOptions()
                                        .position(point)
                                        .icon(BitmapDescriptorFactory.defaultMarker(MISSION_MARKER_COLOR))
                                        .title(String.valueOf(missionMarkers.size() + 1))));

                missionLatLngs.add(point);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_maps, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.connect:
                mDroneRepository.connect();
                break;
            case R.id.where:
                try {
                    mDroneRepository.getPosition().observe(this, position -> {
                        currentLat.set(position.getLatitudeDeg());
                        currentLong.set(position.getLongitudeDeg());
                        currentAlt.set(position.getRelativeAltitudeM());

                        mMarker.remove();
                        LatLng newPosition = new LatLng(currentLat.get(), currentLong.get());
                        mMarker = mMap.addMarker(new MarkerOptions().position(newPosition).title("DRONE"));

                        mMap.moveCamera(CameraUpdateFactory.newLatLng(
                                new LatLng(currentLat.get(), currentLong.get())
                        ));
                        mMap.moveCamera(CameraUpdateFactory.zoomTo(ZOOM_SCALE));
                    });

                    Toast.makeText(getApplication(), "Alt : " + currentAlt.get(), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                }
                break;
            case R.id.printErrorMessage:
                mDroneRepository.printCompleteErrorMessage();
                break;
            case R.id.arm:
                mDroneRepository.arm();
                break;
            case R.id.takeoff:
                mDroneRepository.takeoff(TAKEOFF_HEIGHT);
                break;
            case R.id.kill:
                mDroneRepository.kill();
                break;
            case R.id.land:
                mDroneRepository.land();
                break;
            case R.id.return0:
                mDroneRepository.return0(RTL_RETURN_HEIGHT);
                break;
            case R.id.uploadMission:
                if (missionLatLngs.size() >= 1) {
                    missionLatLngs.add(0, new LatLng(currentLat.get(), currentLong.get()));
                    mDroneRepository.uploadMission(missionLatLngs);
                } else {
                    Toast.makeText(getApplication(), "No Mission Item", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.startMission:
                mDroneRepository.startMission();
                break;
            case R.id.pauseMission:
                mDroneRepository.pauseMission();
                break;
            case R.id.clearMission:
                mDroneRepository.clearMission();
                break;
            case R.id.missionProgress:
                try {
                    mDroneRepository.getMissionProgress().observe(this, missionProgress -> {
                        totalMission.set(missionProgress.getTotal());
                        currentMission.set(missionProgress.getCurrent());

                        int temp_total = totalMission.get();
                        int temp_current = currentMission.get();

                        if(temp_total == temp_current){
                            if(temp_total > 0){
                                //mDroneRepository.return0(RTL_RETURN_HEIGHT);
                                //totalMission.set(-1);
                                //currentMission.set(-1);
                            }
                        }
                    });
                    Toast.makeText(getApplication(),
                            "Total: " + totalMission.get()
                                    + " Current: " + currentMission.get(),
                            Toast.LENGTH_SHORT).show();

                    /*if(totalMission.get() == currentMission.get()){
                        Toast.makeText(getApplication(),"Mission Finished", Toast.LENGTH_SHORT).show();
                    }*/
                } catch (Exception e) {
                }
                break;
            /*case R.id.isMissionFinished:
                mDroneRepository.isMissionFinished();
                try {
                    Toast.makeText(getApplication(),
                            mDroneRepository.isMissionFinished().toString(),
                            Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                }
                break;*/
            case R.id.camera:
                mDroneRepository.camera();
                break;
            case R.id.flightMode:
                try {
                    mDroneRepository.getFlightMode().observe(this, flightMode -> {
                        mFlightMode.set(flightMode.name());
                    });
                    Toast.makeText(getApplication(), mFlightMode.get(), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                }
                break;
            case R.id.battery:
                try {
                    mDroneRepository.getBattery().observe(this, battery -> {
                        batteryPercentage.set(battery.getRemainingPercent() * 100);
                        batteryVoltage.set(battery.getVoltageV());
                    });
                    if ((batteryPercentage.get() == -1f)
                            && (batteryVoltage.get() == -1f)) {
                        Toast.makeText(getApplication(), "Preparing... Press Again.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplication(), batteryPercentage + "% :: " + batteryVoltage + "V", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                }
                break;
            case R.id.speed:
                try {
                    mDroneRepository.getSpeed().observe(this, speed -> {
                        speedXY.set(speed.getHspeed());
                        speedZ.set(speed.getVspeed());
                    });
                    if ((speedXY.get() == -1f)
                            && (speedZ.get() == -1f)) {
                        Toast.makeText(getApplication(),
                                "Preparing... Press Again.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplication(),
                                "SpeedXY : " + speedXY + " m/s", Toast.LENGTH_SHORT).show();
                        Toast.makeText(getApplication(),
                                "SpeedZ : " + speedZ + " m/s", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                }
                break;
            case R.id.attitude:
                try {
                    mDroneRepository.getAttitude().observe(this, eulerAngle -> {
                        eulerPitch.set(eulerAngle.getPitchDeg());
                        eulerRoll.set(eulerAngle.getRollDeg());
                        eulerYaw.set(eulerAngle.getYawDeg());
                    });
                    if ((eulerRoll.get() == -999f)
                            && (eulerPitch.get() == -999f)
                            && (eulerYaw.get() == -999f)) {
                        Toast.makeText(getApplication(),
                                "Preparing... Press Again.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplication(),
                                eulerRoll.get() + "° : "
                                + eulerPitch.get() + "° : "
                                + eulerYaw.get() + "°",
                                Toast.LENGTH_SHORT).show();
                        }
                } catch (Exception e) {
                }
                break;
            case R.id.rcStatus:
                try {
                    mDroneRepository.getRcStatus().observe(this, rcStatus -> {
                        rcOnceAvailable.set(rcStatus.getWasAvailableOnce());
                        rcIsAvailable.set(rcStatus.getIsAvailable());
                        rcSignalStrength.set(rcStatus.getSignalStrengthPercent());
                    });
                    if (rcOnceAvailable.get() == false) {
                        Toast.makeText(getApplication(),
                                "Rc Contorl Waiting for Connection...", Toast.LENGTH_SHORT).show();
                    } else {
                        if (rcIsAvailable.get() == true) {
                            Toast.makeText(getApplication(),
                                    "Rc Control Available : " + rcSignalStrength.get() + "%",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getApplication(),
                                    "** RC Control Disconnected!! **",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                }
                break;
            case R.id.droneConnectionState:
                try {
                    mDroneRepository.getDroneConnectionState().observe(this, connectionState -> {
                        droneConnectionState.set(connectionState.getIsConnected());
                    });
                    if (droneConnectionState.get()) {
                        Toast.makeText(getApplication(), "Drone is found and connected", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplication(), "Waiting for any Drone or System to connect...", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                }
                break;
            case R.id.setGeofence:
                List<LatLng> geofenceLatLngs = new ArrayList<LatLng>();
                geofenceLatLngs = missionLatLngs;

                if (geofenceLatLngs.size() >= 1) {
                    mDroneRepository.setGeofence(geofenceLatLngs);
                } else {
                    Toast.makeText(getApplication(), "No Geofence Item", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.clearGeofence:
                mDroneRepository.clearGeofence();
                Toast.makeText(getApplication(), "Clear Geofence", Toast.LENGTH_SHORT).show();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }
}