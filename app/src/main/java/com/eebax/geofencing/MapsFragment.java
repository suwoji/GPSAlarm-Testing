package com.eebax.geofencing;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bugfender.sdk.Bugfender;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.maps.android.SphericalUtil;

import java.util.Timer;
import java.util.TimerTask;

enum AlarmMode {
    ENABLED,
    DISABLED;

    public int startButtonName() {
        switch (this) {
            case ENABLED:
                return R.string.stop_button;
            case DISABLED:
                return R.string.start_button;
            default:
                return 0;
        }
    }

    public int startButtonColor() {
        switch (this) {
            case ENABLED:
                return com.google.android.material.R.color.androidx_core_ripple_material_light;
            case DISABLED:
                return com.google.android.material.R.color.material_slider_active_tick_marks_color;
            default:
                return R.color.black;
        }
    }
}

enum MapCameraMode {
    LOCK,
    FREE,
    LOCK_TIMER_MOVE;
}


public class MapsFragment extends Fragment {
    private static final Object MODE_PRIVATE = "geofence_key";
    private GoogleMap mMap;
    private LatLng selectedCoord = null;
    private AlarmMode alarmMode = AlarmMode.DISABLED;
    private MapCameraMode mapCameraMode = MapCameraMode.FREE;
    private float GEOFENCE_RADIUS = 200;
    private CheckBox enterCheck, exitCheck;
    private RelativeLayout settingsView;
    private AppCompatButton startStopButton;
    private GeofencingClient geofencingClient;
    private GeofenceHelper geofenceHelper;
    private int FINE_LOCATION_ACCESS_REQUEST_CODE = 10001;
    private int BACKGROUND_LOCATION_ACCESS_REQUEST_CODE = 10002;
    private boolean isMapLoaded = false;
    Timer mTimer;
    mTimerTask mTimerTask;

    private void setAlarmMode(AlarmMode alarmMode) {
        this.alarmMode = alarmMode;

        switch (alarmMode) {
            case ENABLED:
                mapCameraMode = MapCameraMode.LOCK;
                refreshLockCameraIfNeeded();
                break;
            case DISABLED:
                cancelTimer();
                mapCameraMode = MapCameraMode.FREE;
                break;
        }

        SharedPreferences sharedPreferences = this.getActivity().getSharedPreferences("geofence_pref", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("alarm_enabled", alarmMode == AlarmMode.ENABLED);
//        boolean test = (alarmMode == AlarmMode.ENABLED);
//        editor.putBoolean("alarm_enabled", (alarmMode == AlarmMode.ENABLED) ? true : false);
    }



    private void setSelectedCoord(LatLng selectedCoord) {
        this.selectedCoord = selectedCoord;
        refreshLockCameraIfNeeded();
        refreshViews();
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {


        View view = inflater.inflate(R.layout.fragment_maps, container, false);

        SharedPreferences sharedPreferences = this.getActivity().getSharedPreferences("geofence_pref", Context.MODE_PRIVATE);

        startStopButton = (AppCompatButton) view.findViewById(R.id.startStopButton);
        enterCheck = (CheckBox) view.findViewById(R.id.enterCheckBoxMain);
        exitCheck = (CheckBox) view.findViewById(R.id.exitCheckBoxMain);
        settingsView = (RelativeLayout) view.findViewById(R.id.modeButton);
        enterCheck.setChecked(sharedPreferences.getBoolean("enter_checkBox", false));
        exitCheck.setChecked(sharedPreferences.getBoolean("exit_checkBox", false));
        double lat, lon;

        setAlarmMode(sharedPreferences.getBoolean("alarm_enabled", false) ? AlarmMode.ENABLED : AlarmMode.DISABLED);
        lat = sharedPreferences.getFloat("position_lat", 15f);
        lon = sharedPreferences.getFloat("position_lon", 15f);
        if (lat != 15f && lon != 15f) {
            setSelectedCoord(new LatLng(lat, lon));
        }

        SupportMapFragment supportMapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map);
        supportMapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                isMapLoaded = true;
                geofencingClient = LocationServices.getGeofencingClient(getActivity());
                geofenceHelper = new GeofenceHelper(getActivity());

                float zoomLevel = 15.0f;

                Bugfender.init(getContext(), "n34zQbmQYSpei2VhZhOOpVylyXegDYHN", BuildConfig.DEBUG);
                Bugfender.enableCrashReporting();
                Bugfender.enableUIEventLogging(getActivity().getApplication());
                Bugfender.enableLogcatLogging();

                mMap = googleMap;
                enableUserLocation();
                if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                mMap.setMyLocationEnabled(true);
                LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
                Criteria criteria = new Criteria();

                Location location = locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, false));
                if (location != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 13));

                    CameraPosition cameraPosition = new CameraPosition.Builder()
                            .target(new LatLng(location.getLatitude(), location.getLongitude()))      // Sets the center of the map to location user
                            .zoom(17)                   // Sets the zoom
//                            .bearing(90)                // Sets the orientation of the camera to east
                            //   .tilt(40)                   // Sets the tilt of the camera to 30 degrees
                            .build();                   // Creates a CameraPosition from the builder
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                    Bugfender.d("log", "Camera move");
                }

                refreshViews();

                if (alarmMode == AlarmMode.ENABLED && selectedCoord != null) {
                    resubscribeToGeofence(selectedCoord, GEOFENCE_RADIUS);
                }

                mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                    @Override
                    public void onMapLongClick(@NonNull LatLng latLng) {
                        if (alarmMode == AlarmMode.DISABLED) {
                            Util.log("Map click");
                            Bugfender.d("log", "Map click(Alarm ENABLED)");
                            setSelectedCoord(latLng);

                            if (Build.VERSION.SDK_INT < 29) {
                                return;
                            }

                            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                Bugfender.d("log", "Background location permission SUCCESS");
                            } else {
                                if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                                    //We show a dialog and ask for permission
                                    ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_LOCATION_ACCESS_REQUEST_CODE);
                                    Bugfender.d("log", "Background location permission SUCCESS");
                                } else {
                                    Toast.makeText(getContext(), R.string.no_permission_toast, Toast.LENGTH_SHORT).show();
                                    Util.log("Permission failure");
                                    Bugfender.d("log", "Permission failure");
                                }
                            }
                        } else {
                            Bugfender.d("log", "Map click(Alarm DISABLED)");
                            Toast.makeText(getContext(), R.string.map_disable_toast, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                mMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
                    @Override
                    public void onCameraMove() {
                        if (mapCameraMode == MapCameraMode.LOCK || mapCameraMode == MapCameraMode.LOCK_TIMER_MOVE) {
                            mapCameraMode = MapCameraMode.LOCK_TIMER_MOVE;
                            restartMapCameraTimer();
                        }
                    }
                });
                mMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
                    @Override
                    public void onMyLocationChange(@NonNull Location location) {
                        refreshLockCameraIfNeeded();
                    }
                });
            }
        });



        startStopButton.setOnClickListener(new View.OnClickListener() {
            SharedPreferences.Editor editor = sharedPreferences.edit();

            @SuppressLint("ResourceAsColor")
            @Override
            public void onClick(View view) {
                switch (view.getId()) {
                    case R.id.startStopButton: {
                        switch (alarmMode) {
                            case ENABLED:
                                setAlarmMode(AlarmMode.DISABLED);
                                editor.putFloat("position_lat", 15f);
                                editor.putFloat("position_lon", 15f);
                                editor.putBoolean("enter_checkBox", false);
                                editor.putBoolean("exit_checkBox", false);
                                mMap.clear();
                                Util.log("startStopButton disabled");
                                Bugfender.d("log", "startStopButton disabled");
                                break;

                            case DISABLED:
                                Util.log("startStopButton enabled");
                                Bugfender.d("log", "startStopButton enabled");
                                if (selectedCoord != null) {
                                    if (!exitCheck.isChecked() && !enterCheck.isChecked()) {
                                        Toast.makeText(getContext(), R.string.empty_checkbox_toast, Toast.LENGTH_SHORT).show();
                                    } else {
                                        setAlarmMode(AlarmMode.ENABLED);
                                        resubscribeToGeofence(selectedCoord, GEOFENCE_RADIUS);
                                        editor.putFloat("position_lat", (float) selectedCoord.latitude);
                                        editor.putFloat("position_lon", (float) selectedCoord.longitude);
                                        editor.putBoolean("enter_checkBox", enterCheck.isChecked());
                                        editor.putBoolean("exit_checkBox", exitCheck.isChecked());
                                        editor.commit();
                                    }
                                } else
                                    Toast.makeText(getContext(), R.string.empty_map_toast, Toast.LENGTH_SHORT).show();
                                break;
                        }
                        refreshViews();
                    }
                }
            }
        });

        return view;
    }

    private void refreshLockCameraIfNeeded(){
        if(mapCameraMode != MapCameraMode.LOCK) return;

        LatLngBounds.Builder bld = new LatLngBounds.Builder();
        LatLng w = SphericalUtil.computeOffset(selectedCoord, GEOFENCE_RADIUS, 90);
        LatLng e = SphericalUtil.computeOffset(selectedCoord, GEOFENCE_RADIUS, 270);
        LatLng n = SphericalUtil.computeOffset(selectedCoord, GEOFENCE_RADIUS, 0);
        LatLng s = SphericalUtil.computeOffset(selectedCoord, GEOFENCE_RADIUS, 180);
        bld.include(n);
        bld.include(s);
        bld.include(e);
        bld.include(w);
        Location myLocation = mMap.getMyLocation();
        bld.include(new LatLng(myLocation.getLatitude(), myLocation.getLongitude()));
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bld.build(), 70));
    }


    private void restartMapCameraTimer() {
        mTimerTask = new mTimerTask();
        cancelTimer();
        mTimer = new Timer();
        mTimer.schedule(mTimerTask, 5000);
    }

    private void cancelTimer() {
        if (mTimer != null) {
            mTimer.cancel();
        }
    }

    class mTimerTask extends TimerTask {
        @Override
        public void run() {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mapCameraMode = MapCameraMode.LOCK;
                    refreshLockCameraIfNeeded();
                }
            });
        }
    }

    private void redrawMapCircle() {
        mMap.clear();
        if (selectedCoord != null) {
            addCircle(selectedCoord, GEOFENCE_RADIUS);
        }
    }

    @SuppressLint("ResourceAsColor")
    private void refreshViews() {
        if (!isMapLoaded) return;

        redrawMapCircle();

        switch (alarmMode) {
            case ENABLED:
                Bugfender.d("log", "refreshMapState (ENABLED)");
                enterCheck.setClickable(false);
                exitCheck.setClickable(false);
                settingsView.setAlpha(0.6f);
                break;

            case DISABLED:
                Bugfender.d("log", "refreshMapState (DISABLED)");
                enterCheck.setClickable(true);
                exitCheck.setClickable(true);
                settingsView.setAlpha(1f);
                break;
        }
        startStopButton.setText(getContext().getString(alarmMode.startButtonName()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startStopButton.setBackgroundColor(getContext().getColor(alarmMode.startButtonColor()));
        }
    }

    private void enableUserLocation() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        } else {
            //Ask for permission
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                //We need to show user a dialog for displaying why the permission is needed and then ask for the permission...
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_LOCATION_ACCESS_REQUEST_CODE);
            } else {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_LOCATION_ACCESS_REQUEST_CODE);
            }
        }
    }

    private void resubscribeToGeofence(LatLng latLng, float radius) {
        Geofence geofence = null;
//        if (enterCheck.isChecked()) {
//            if (exitCheck.isChecked()) {
//                geofence = geofenceHelper.getGeofence(GEOFENCE_ID, latLng, radius, Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT);
//            } else {
//                geofence = geofenceHelper.getGeofence(GEOFENCE_ID, latLng, radius, Geofence.GEOFENCE_TRANSITION_ENTER);
//            }
//        } else {
//            if (exitCheck.isChecked()) {
//                geofence = geofenceHelper.getGeofence(GEOFENCE_ID, latLng, radius, Geofence.GEOFENCE_TRANSITION_EXIT);
//            } else {
//                Toast.makeText(getContext(), "ne vibran mode", Toast.LENGTH_SHORT).show();
//            }
//        }

        if (enterCheck.isChecked() && exitCheck.isChecked()) {
            geofence = geofenceHelper.getGeofence(latLng, radius, Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT);
            Bugfender.d("log", "adding geofence (both checkbox)");
        } else if (enterCheck.isChecked() && !exitCheck.isChecked()) {
            geofence = geofenceHelper.getGeofence(latLng, radius, Geofence.GEOFENCE_TRANSITION_ENTER);
            Bugfender.d("log", "adding geofence (on enter checkbox)");
        } else if (!enterCheck.isChecked() && exitCheck.isChecked()) {
            geofence = geofenceHelper.getGeofence(latLng, radius, Geofence.GEOFENCE_TRANSITION_EXIT);
            Bugfender.d("log", "adding geofence (on exit checkbox)");
        } else {
            Toast.makeText(getContext(), R.string.empty_checkbox_toast, Toast.LENGTH_SHORT).show();
            Bugfender.d("log", "adding geofence (empty checkbox)");
            return;
        }

        GeofencingRequest geofencingRequest = geofenceHelper.getGeofencingRequest(geofence);
        PendingIntent pendingIntent = geofenceHelper.getPendingIntent();

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Util.log("Check Location permission");
            Bugfender.d("log", "Check Location permission");
            return;
        }
        geofencingClient.addGeofences(geofencingRequest, pendingIntent).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Util.log("Geofence added");
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                String errorMessage = geofenceHelper.getErrorString(e);
                Util.log("Geofence adding failure: " + errorMessage);
                Bugfender.d("log", "Geofence adding failure: " + errorMessage);
            }
        });
    }

    private void addCircle(LatLng latLng, float radius) {
        CircleOptions circleOptions = new CircleOptions();
        circleOptions.center(latLng);
        circleOptions.radius(radius);
        circleOptions.strokeColor(Color.argb(255, 255, 0, 0));
        circleOptions.fillColor(Color.argb(64, 255, 0, 0));
        circleOptions.strokeWidth(4);
        mMap.addCircle(circleOptions);
        Bugfender.d("log", "add Circle");
    }

}