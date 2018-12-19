package com.here.example.speedlimit;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.here.android.mpa.common.GeoBoundingBox;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.MapEngine;
import com.here.android.mpa.common.MatchedGeoPosition;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.prefetcher.MapDataPrefetcher;

import java.lang.ref.WeakReference;


public class FloatingWidget extends Service {

    private LinearLayout currentSpeedContainerView;
    private TextView currentSpeedView;
    private TextView currentSpeedLimitView;

    private boolean fetchingDataInProgress = false;

    private WindowManager mWindowManager;
    private View mFloatingWidget;

    public FloatingWidget() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mFloatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_widget, null);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mFloatingWidget, params);
        
        Button closeButton = (Button) mFloatingWidget.findViewById(R.id.close_button);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopSelf();
            }
        });

        mFloatingWidget.findViewById(R.id.root_container).setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        mWindowManager.updateViewLayout(mFloatingWidget, params);
                        return true;
                }
                return false;
            }
        });

        setElements(mFloatingWidget);
        startWatching();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFloatingWidget != null) {
            mWindowManager.removeView(mFloatingWidget);
        }
        if (MapEngine.isInitialized()) {
            PositioningManager.getInstance().removeListener(positionLister);
        }
    }

    //fragment responsibility
    
    private int meterPerSecToKmPerHour (double speed) {
        return (int) (speed * 3.6);
    }

    private int meterPerSecToMilesPerHour (double speed) {
        return (int) (speed * 2.23694);
    }

    public void startWatching() {
        PositioningManager.getInstance().addListener(new WeakReference<>(positionLister));
        MapDataPrefetcher.getInstance().addListener(prefetcherListener);
    }

    public void stopWatching() {
        PositioningManager.getInstance().removeListener(positionLister);
        MapDataPrefetcher.getInstance().removeListener(prefetcherListener);
    }

    private void setElements(View fragmentView) {
        currentSpeedView = (TextView) fragmentView.findViewById(R.id.currentSpeed);
        currentSpeedLimitView = (TextView) fragmentView.findViewById(R.id.currentSpeedLimit);
        currentSpeedContainerView = (LinearLayout) fragmentView.findViewById(R.id.currentSpeedContainer);
    }

    private void updateCurrentSpeedView(int currentSpeed, int currentSpeedLimit) {

        int backgroundColor;
        int textColor;
        if (currentSpeed > currentSpeedLimit && currentSpeedLimit > 0) {
            backgroundColor = R.color.notAllowedSpeedBackground;
            textColor = R.color.speedOverLimitText;
        } else {
            backgroundColor = R.color.allowedSpeedBackground;
            textColor = R.color.speedUnderLimitText;
        }
        currentSpeedContainerView.setBackgroundColor(getResources().getColor(backgroundColor));
        currentSpeedView.setText(String.valueOf(currentSpeed));
        currentSpeedView.setTextColor(getResources().getColor(textColor));
    }

    private void updateCurrentSpeedLimitView(int currentSpeedLimit) {

        String currentSpeedLimitText;
        int textColorId;
        int backgroundImageId;

        if (currentSpeedLimit > 0) {
            currentSpeedLimitText = String.valueOf(currentSpeedLimit);
            textColorId = R.color.limitText;
            backgroundImageId = R.drawable.limit_circle_background;
        } else {
            currentSpeedLimitText = getResources().getString(R.string.navigation_speed_limit_default);
            textColorId = R.color.noLimitText;
            backgroundImageId = R.drawable.no_limit_circle_background;
        }
        currentSpeedLimitView.setText(currentSpeedLimitText);
        currentSpeedLimitView.setTextColor(getResources().getColor(textColorId));
        currentSpeedLimitView.setBackgroundResource(backgroundImageId);
    }


    MapDataPrefetcher.Adapter prefetcherListener = new MapDataPrefetcher.Adapter() {
        @Override
        public void onStatus(int requestId, PrefetchStatus status) {
            if(status != PrefetchStatus.PREFETCH_IN_PROGRESS) {
                fetchingDataInProgress = false;
            }
        }
    };

    PositioningManager.OnPositionChangedListener positionLister = new PositioningManager.OnPositionChangedListener() {
        @Override
        public void onPositionUpdated(PositioningManager.LocationMethod locationMethod,
                                      GeoPosition geoPosition, boolean b) {

            if (PositioningManager.getInstance().getRoadElement() == null && !fetchingDataInProgress) {
                GeoBoundingBox areaAround = new GeoBoundingBox(geoPosition.getCoordinate(), 500, 500);
                MapDataPrefetcher.getInstance().fetchMapData(areaAround);
                fetchingDataInProgress = true;
            }

            if (geoPosition.isValid() && geoPosition instanceof MatchedGeoPosition) {

                MatchedGeoPosition mgp = (MatchedGeoPosition) geoPosition;

                int currentSpeedLimitTransformed = 0;
                int currentSpeed = meterPerSecToKmPerHour(mgp.getSpeed());

                if (mgp.getRoadElement() != null) {
                    double currentSpeedLimit = mgp.getRoadElement().getSpeedLimit();
                    currentSpeedLimitTransformed  = meterPerSecToKmPerHour(currentSpeedLimit);
                }

                updateCurrentSpeedView(currentSpeed, currentSpeedLimitTransformed);
                updateCurrentSpeedLimitView(currentSpeedLimitTransformed);

            } else {
                //handle error
            }
        }

        @Override
        public void onPositionFixChanged(PositioningManager.LocationMethod locationMethod,
                                         PositioningManager.LocationStatus locationStatus) {

        }
    };

}
