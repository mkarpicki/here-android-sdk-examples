package com.here.example.speedlimit;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import android.support.v4.app.ActivityCompat;
import android.widget.Toast;

import com.here.android.mpa.common.ApplicationContext;
import com.here.android.mpa.common.MapEngine;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.guidance.NavigationManager;


public class MainActivity extends AppCompatActivity {

    private AppCompatActivity self;

    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1;
    private static final int APP_PERMISSION_REQUEST = 102; //5469

    private static final String[] RUNTIME_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.SYSTEM_ALERT_WINDOW
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        self = this;

        // check Android version to request permissions
        if (hasPermissions(this, RUNTIME_PERMISSIONS)) {
            initSDK();
        } else {
            ActivityCompat
                    .requestPermissions(this, RUNTIME_PERMISSIONS, REQUEST_CODE_ASK_PERMISSIONS);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == APP_PERMISSION_REQUEST && resultCode == RESULT_OK) {
            start();
        } else {
            //error ("Draw over other app permission not enable.");
        }
    }

    private void start() {
        startPositioningManager();
        startNavigationManager();
        startWidget();
    }

    private void initSDK() {
        ApplicationContext appContext = new ApplicationContext(this);

        MapEngine.getInstance().init(appContext, new OnEngineInitListener() {
            @Override
            public void onEngineInitializationCompleted(Error error) {
                if (error == Error.NONE) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(self)) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, APP_PERMISSION_REQUEST);
                    } else {
                        start();
                    }

                } else {
                    //handle error here
                }
            }
        });
    }

    /**
     * Only when the app's target SDK is 23 or higher, it requests each dangerous permissions it
     * needs when the app is running.
     */
    private static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS: {
                for (int index = 0; index < permissions.length; index++) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {

                        /*
                         * If the user turned down the permission request in the past and chose the
                         * Don't ask again option in the permission request system dialog.
                         */
                        if (!ActivityCompat
                                .shouldShowRequestPermissionRationale(this, permissions[index])) {
                            Toast.makeText(this, "Required permission " + permissions[index]
                                                   + " not granted. "
                                                   + "Please go to settings and turn on for sample app",
                                           Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Required permission " + permissions[index]
                                    + " not granted", Toast.LENGTH_LONG).show();
                        }
                    }
                }

                initSDK();
                break;
            }
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (MapEngine.isInitialized()) {
            MapEngine.getInstance().onResume();
            startPositioningManager();
        }
    }

    @Override
    protected void onPause() {
        if (MapEngine.isInitialized()) {
            stopPositioningManager();
        }
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void startPositioningManager() {
        boolean positioningManagerStarted = PositioningManager.getInstance().start(PositioningManager.LocationMethod.GPS_NETWORK);

        if (!positioningManagerStarted) {
            //handle error here
        }
    }

    private void stopPositioningManager() {
        PositioningManager.getInstance().stop();
    }

    private void startNavigationManager() {
        NavigationManager.Error navError = NavigationManager.getInstance().startTracking();

        if (navError != NavigationManager.Error.NONE) {
            //handle error navError.toString());
        }

    }
//
//    private void activateSpeedLimitFragment() {
//        SpeedLimitFragment speedLimitFragment = (SpeedLimitFragment)
//                getSupportFragmentManager().findFragmentById(R.id.speedLimitFragment);
//
//        if (speedLimitFragment != null) {
//            speedLimitFragment.startListeners();
//        }
//    }

    private void startWidget() {
        startService(new Intent(MainActivity.this, FloatingWidget.class));
        finish();
    }
}