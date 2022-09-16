package com.eebax.geofencing;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bugfender.sdk.Bugfender;
import com.eebax.geofencing.databinding.ActivityMapsBinding;
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
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

enum AlarmMode {
    ENABLED,
    DISABLED
}

public class MapsFragment extends Fragment {
    private static final Object MODE_PRIVATE = "geofence_key";
    GoogleMap mMap;
    private ActivityMapsBinding binding;
    Button button;
    LatLng selectedCoord = null;
    AlarmMode alarmMode = AlarmMode.DISABLED;
    private LatLng currentLatLng;
    private float GEOFENCE_RADIUS = 200;
    private String GEOFENCE_ID = "SOME_GEOFENCE_ID";
    CheckBox enterCheck, exitCheck;
    Button startStopButton;
    GeofencingClient geofencingClient;
    private GeofenceHelper geofenceHelper;
    private int FINE_LOCATION_ACCESS_REQUEST_CODE = 10001;
    private int BACKGROUND_LOCATION_ACCESS_REQUEST_CODE = 10002;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {


        View view = inflater.inflate(R.layout.fragment_maps, container, false);

        SharedPreferences sharedPreferences = this.getActivity().getSharedPreferences("geofence_pref", Context.MODE_PRIVATE);

        startStopButton = (Button) view.findViewById(R.id.startStopButton);
        enterCheck = (CheckBox) view.findViewById(R.id.enterCheckBoxMain);
        exitCheck = (CheckBox) view.findViewById(R.id.exitCheckBoxMain);
        enterCheck.setChecked(sharedPreferences.getBoolean("enter_checkBox", false));
        exitCheck.setChecked(sharedPreferences.getBoolean("exit_checkBox", false));

//        final View mapView = getFragmentManager().findFragmentById(R.id.map).getView();

        SupportMapFragment supportMapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map);
        supportMapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {

                geofencingClient = LocationServices.getGeofencingClient(getActivity());
                geofenceHelper = new GeofenceHelper(getActivity());

                float zoomLevel = 15.0f;


                Bugfender.init(getContext(), "n34zQbmQYSpei2VhZhOOpVylyXegDYHN", BuildConfig.DEBUG);
                Bugfender.enableCrashReporting();
                Bugfender.enableUIEventLogging(getActivity().getApplication());
                Bugfender.enableLogcatLogging(); // optional, if you want logs automatically collected from logcat

                mMap = googleMap;
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

                double lat, lon;
                if(sharedPreferences.getFloat("position_lat", 15f) != 15f) {
                    lat = sharedPreferences.getFloat("position_lat", 15f);
                    lon = sharedPreferences.getFloat("position_lon", 15f);
                    handleMapLongClick(new LatLng(lat, lon));
                    alarmMode = AlarmMode.ENABLED;
                    refreshMapState();
                    Bugfender.d("log", "Alarm mode check");
                }

                mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                    @Override
                    public void onMapLongClick(@NonNull LatLng latLng) {
                        if (alarmMode == AlarmMode.DISABLED){
                            Util.log("Map click");
                            Bugfender.d("log", "Map click(Alarm ENABLED)");
                            selectedCoord = latLng;
//                            TODO
//                            For old sdk we dont need permission
                            if (Build.VERSION.SDK_INT < 29) {
                                handleMapLongClick(latLng);
                                return;
                            }

                            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                handleMapLongClick(latLng);
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
                                alarmMode = AlarmMode.DISABLED;
                                editor.putFloat("position_lat", 15f);
                                editor.putFloat("position_lon", 15f);
                                editor.putBoolean("enter_checkBox", false);
                                editor.putBoolean("exit_checkBox", false);
                                Util.log("startStopButton disabled");
                                Bugfender.d("log", "startStopButton disabled");
                                break;

                            case DISABLED:
                                Util.log("startStopButton enabled");
                                Bugfender.d("log", "startStopButton enabled");
                                if (selectedCoord != null) {
                                    if (!exitCheck.isChecked() && !enterCheck.isChecked()) {
                                        Toast.makeText(getContext(),R.string.empty_checkbox_toast, Toast.LENGTH_SHORT).show();
                                    } else {
                                        alarmMode = AlarmMode.ENABLED;
                                        addGeofence(selectedCoord, GEOFENCE_RADIUS);
                                        editor.putFloat("position_lat", (float) selectedCoord.latitude );
                                        editor.putFloat("position_lon", (float) selectedCoord.longitude);
                                        editor.putBoolean("enter_checkBox", enterCheck.isChecked());
                                        editor.putBoolean("exit_checkBox", exitCheck.isChecked());
                                        editor.commit();
                                        Location myLocation = mMap.getMyLocation();
                                        LatLng myLatLng = new LatLng(myLocation.getLatitude(),
                                                myLocation.getLongitude());
                                                    LatLngBounds.Builder bld = new LatLngBounds.Builder();
                                                    bld.include(selectedCoord);
                                                    bld.include(myLatLng);
                                                    LatLngBounds bounds = bld.build();
                                                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 70));
                                    }
                                } else
                                    Toast.makeText(getContext(),R.string.empty_map_toast, Toast.LENGTH_SHORT).show();
                                break;
                        }
                        refreshMapState();
                    }
                }
            }
        });

        return view;
    }

    @SuppressLint("ResourceAsColor")
    private void refreshMapState() {
        switch (alarmMode) {
            case ENABLED:
                Bugfender.d("log", "refreshMapState (ENABLED)");
                enterCheck.setClickable(false);
                exitCheck.setClickable(false);
                startStopButton.setBackgroundColor(R.color.design_default_color_error);
                startStopButton.setText(R.string.stop_button);
                break;

            case DISABLED:
                Bugfender.d("log", "refreshMapState (DISABLED)");
                enterCheck.setClickable(true);
                exitCheck.setClickable(true);
                startStopButton.setBackgroundColor(R.color.design_default_color_primary_dark);
                startStopButton.setText(R.string.start_button);
                break;
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


    private void handleMapLongClick(LatLng latLng) {
        mMap.clear();
        addCircle(latLng, GEOFENCE_RADIUS);
    }

    private void addGeofence(LatLng latLng, float radius) {
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
            geofence = geofenceHelper.getGeofence(GEOFENCE_ID, latLng, radius, Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT);
            Bugfender.d("log", "adding geofence (both checkbox)");
        } else if (enterCheck.isChecked() && !exitCheck.isChecked()) {
            geofence = geofenceHelper.getGeofence(GEOFENCE_ID, latLng, radius, Geofence.GEOFENCE_TRANSITION_ENTER);
            Bugfender.d("log", "adding geofence (on enter checkbox)");
        } else if (!enterCheck.isChecked() && exitCheck.isChecked()) {
            geofence = geofenceHelper.getGeofence(GEOFENCE_ID, latLng, radius, Geofence.GEOFENCE_TRANSITION_EXIT);
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