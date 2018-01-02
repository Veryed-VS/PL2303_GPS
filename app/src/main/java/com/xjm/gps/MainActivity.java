package com.xjm.gps;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import tw.com.prolific.driver.pl2303.PL2303Driver;

public class MainActivity extends AppCompatActivity {
    private static final String ACTION_USB_PERMISSION = "com.android.hardware.USB_PERMISSION";
    private PL2303Driver driver;
    private PL2303Driver.BaudRate mBaudrate = PL2303Driver.BaudRate.B4800;
    private LocationManager locationManager;
    //显示控件
    private TextView lonTextView;
    private TextView latTextView;
    private TextView altitudeTextView;
    private TextView velocityTextView;
    private TextView dirTextView;
    private TextView accuracyTextView;
    private TextView numTxtView;             //当前可见卫星

    private GPSPosition gpsPosition;         //另外的GPS对象
    private int satelliteNum;                //当前用的卫星数量
    private static final int MY_PERMISSIONS_REQUEST_FINE_LOCATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lat_text = getString(R.string.lat_str);
        accuracy_text = getString(R.string.accuracy_str);
        lon_text = getString(R.string.lon_str);
        speed_text = getString(R.string.speed_str);
        dir_text = getString(R.string.dir_str);
        satellite_text = getString(R.string.satellite_str);
        altitude_text = getString(R.string.altitude_str);

        //控件初始化
        lonTextView = findViewById(R.id.lon_textView);
        latTextView = findViewById(R.id.lat_textView);
        altitudeTextView = findViewById(R.id.altitude_textView);
        velocityTextView = findViewById(R.id.velocity_textView);
        dirTextView = findViewById(R.id.dir_textView);
        accuracyTextView = findViewById(R.id.accuracy_textView);
        numTxtView = findViewById(R.id.satellite_num_textView);
        //系统USB管理器
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        //监听USB设备的拔插
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbStateReceiver, filter);
        //PL2303驱动
        driver = new PL2303Driver(usbManager, this, ACTION_USB_PERMISSION);
        if (!driver.PL2303USBFeatureSupported()) {  //判断设备是不是支持USB设备
            new MaterialDialog.Builder(MainActivity.this)
                    .title(getString(R.string.error))
                    .content(getString(R.string.error_text))
                    .cancelable(false)
                    .positiveText(getString(R.string.ok_text))
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                            MainActivity.this.finish();
                        }
                    }).show();
            return;
        }
        //系统位置管理器
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE); //位置模拟
        if (locationManager == null) {
            new MaterialDialog.Builder(MainActivity.this)
                    .title(getString(R.string.error))
                    .content(getString(R.string.location_error))
                    .cancelable(false)
                    .positiveText(getString(R.string.ok_text))
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                            MainActivity.this.finish();
                        }
                    }).show();
            return;
        }
        initLocation();
    }

    private void showInfoPop(){
        View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.info_pop,null);
        final MaterialDialog dialog = new MaterialDialog.Builder(MainActivity.this)
                .customView(view,false).cancelable(false).show();
        view.findViewById(R.id.ok_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
//                Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                ComponentName componentName = intent.resolveActivity(getPackageManager());
                if(componentName == null){
                    new MaterialDialog.Builder(MainActivity.this)
                            .title(getString(R.string.error))
                            .content(getString(R.string.error_dev))
                            .positiveText(getString(R.string.ok_text))
                            .cancelable(false)
                            .onPositive(new MaterialDialog.SingleButtonCallback() {
                                @Override
                                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                    MainActivity.this.finish();
                                }
                            }).show();
                }else{
                    startActivity(intent);
                }
            }
        });

    }

    private void initLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_FINE_LOCATION);
                return;
            } else {
                try {
                    String providerStr = LocationManager.GPS_PROVIDER;
                    LocationProvider provider = locationManager.getProvider(providerStr);
                    if (provider != null) {
                        locationManager.addTestProvider(
                                provider.getName()
                                , provider.requiresNetwork()
                                , provider.requiresSatellite()
                                , provider.requiresCell()
                                , provider.hasMonetaryCost()
                                , provider.supportsAltitude()
                                , provider.supportsSpeed()
                                , provider.supportsBearing()
                                , provider.getPowerRequirement()
                                , provider.getAccuracy());
                    } else {
                        locationManager.addTestProvider(providerStr, true, true, false,
                                false, true, true, true,
                                Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
                    }
                    locationManager.setTestProviderEnabled(providerStr, true);
                    locationManager.setTestProviderStatus(providerStr, LocationProvider.AVAILABLE, null, System.currentTimeMillis());
                } catch (SecurityException e) {
//                    AlertDialog.Builder normalDialog = new AlertDialog.Builder(MainActivity.this);
//                    normalDialog.setTitle(getString(R.string.error));
//                    normalDialog.setCancelable(false);
//                    normalDialog.setMessage(getString(R.string.open_location));
//                    normalDialog.setPositiveButton(getString(R.string.ok_text), new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//                            dialog.dismiss();
//                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
//                            startActivity(intent);
//                        }
//                    });
//                    normalDialog.create().show();
                    showInfoPop();
                    return;
                }
            }
        } else {
            if (Settings.Secure.getInt(getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION, 0) != 0) {
                new MaterialDialog.Builder(MainActivity.this).title(getString(R.string.error))
                        .cancelable(false)
                        .content(getString(R.string.open_location))
                        .positiveText(getString(R.string.ok_text))
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                //Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                                //startActivity(intent);
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                                ComponentName componentName = intent.resolveActivity(getPackageManager());
                                if(componentName == null){
                                    new MaterialDialog.Builder(MainActivity.this).title(getString(R.string.error))
                                            .content(getString(R.string.error_dev)).positiveText(getString(R.string.ok_text))
                                            .onPositive(new MaterialDialog.SingleButtonCallback() {
                                                @Override
                                                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                                    MainActivity.this.finish();
                                                }
                                            }).show();
                                }else{
                                    startActivity(intent);
                                }
                            }
                        }).show();
                return;
            } else {
                locationManager.addTestProvider(LocationManager.GPS_PROVIDER, true, true, false, false, true, true, true
                        , Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
                locationManager.setTestProviderStatus(LocationManager.GPS_PROVIDER, LocationProvider.AVAILABLE, null, System.currentTimeMillis());
            }
        }
        //初始化USB驱动
        if (!driver.enumerate()) {
            numTxtView.setText(getString(R.string.insert_error));
        } else {
            gpsPosition = new GPSPosition();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!driver.InitByBaudRate(mBaudrate, 700)) {  //700 超时时间
                        if (!driver.PL2303Device_IsHasPermission()) {
                            Toast.makeText(MainActivity.this, getString(R.string.permission_error), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (driver.PL2303Device_IsHasPermission() && (!driver.PL2303Device_IsSupportChip())) {
                            Toast.makeText(MainActivity.this, getString(R.string.usb_error), Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else {
                        numTxtView.setText(getString(R.string.location_ing));
                        handle.post(runnable);
                    }
                }
            }, 1000);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_FINE_LOCATION && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initLocation();
        } else {
            finish();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (locationManager == null) {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE); //位置模拟
            if (locationManager == null) {
                new MaterialDialog.Builder(MainActivity.this).title(getString(R.string.error))
                        .cancelable(false)
                        .content(getString(R.string.location_error))
                        .positiveText(getString(R.string.ok_text))
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                dialog.dismiss();
                                MainActivity.this.finish();
                            }
                        }).show();
                return;
            }
        }
        if (gpsPosition == null) {
            initLocation();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        isShowView = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        isShowView = false;
    }

    private class GPSPosition {
        public float accuracy; //定位精度
        public float altitude; //海拔高度
        public float dir;      //方向
        public double lat;     //经度
        public double lon;     //纬度
        public float velocity; //速度

        public int quality;    //定位质量单点定位才行
        public boolean fixed;  //是否修正数据

        public GPSPosition() {
            this.lat = 0.0;
            this.lon = 0.0;
            this.dir = 0.0f;
            this.altitude = 0.0f;
            this.velocity = 0.0f;
            this.accuracy = 0.0f;
            this.fixed = false;
            this.quality = 0;
        }

        public void updatefix() {
            boolean fixed = false;
            if (this.lon == Double.NaN || this.lat == Double.NaN) {
                this.fixed = false;
                return;
            }
            if (this.quality > 0) {
                fixed = true;
            }
            this.fixed = fixed;
        }
    }

    private String lat_text = null;
    private String accuracy_text = null;
    private String lon_text = null;
    private String speed_text = null;
    private String dir_text = null;
    private String satellite_text = null;
    private String altitude_text = null;
    private boolean isShowView;
    private String lastString = "";
    private Handler handle = new Handler();
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            byte[] bytes = new byte[4096];
            if (driver.read(bytes) > 0) {
                String locationStr = new String(bytes).trim();
                String[] splitString = locationStr.split("\r\n");
                if (splitString.length == 1) {
                    if (locationStr.startsWith("$")) {
                        lastString = locationStr;
                    } else {
                        lastString = lastString + locationStr;
                    }
                    handle.postDelayed(runnable, 100);
                    return;
                } else if (splitString.length == 2) {
                    if (locationStr.startsWith("$")) {
                        locationStr = splitString[0];
                    } else {
                        locationStr = lastString + splitString[0];
                    }
                    lastString = splitString[1];
                } else {
                    lastString = splitString[splitString.length - 1];
                    locationStr = splitString[splitString.length - 2];
                }
                if (locationStr.startsWith("$GPGSV")) {
                    handle.postDelayed(runnable, 400);
                    return;
                } else if (locationStr.startsWith("$GPRMC")) {
                    String[] split = locationStr.split(",");
                    if (split.length >= 9 && split[2].equals("A")) {   // A=有效定位，V=无效定位
                        try {
                            gpsPosition.lat = Latitude2Decimal(split[3], split[4]);
                            gpsPosition.lon = Longitude2Decimal(split[5], split[6]);
                            gpsPosition.velocity = Float.parseFloat(split[7]) * 0.514444f;
                            gpsPosition.dir = Float.parseFloat(split[8]);
                            gpsPosition.updatefix();
                            if (gpsPosition.fixed) {
                                Location mockLocation = new Location(LocationManager.GPS_PROVIDER);
                                mockLocation.setLatitude(gpsPosition.lat);             //纬度
                                mockLocation.setLongitude(gpsPosition.lon);            //经度
                                mockLocation.setSpeed(gpsPosition.velocity);           //地面速度
                                mockLocation.setBearing(gpsPosition.dir);              //方向
                                mockLocation.setAccuracy(gpsPosition.accuracy);        //精度
                                mockLocation.setAltitude(gpsPosition.altitude);        //海拔
                                mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());// 没有就会报错 返回系统启动到现在的毫秒数,包含休眠时间
                                mockLocation.setTime(System.currentTimeMillis());      //不能使用GPS时间，会导致严重的时间不一致 跟系统的时间不一致
                                locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation);
                            }
                            if (isShowView) {
                                latTextView.setText(lat_text + String.format("%.2f", gpsPosition.lat) + " °");
                                lonTextView.setText(lon_text + String.format("%.2f", gpsPosition.lon) + " °");
                                velocityTextView.setText(speed_text + String.format("%.2f", gpsPosition.velocity) + " m/s");
                                dirTextView.setText(dir_text + String.format("%.2f", gpsPosition.dir) + " °");
                                accuracyTextView.setText(accuracy_text + String.format("%.2f", gpsPosition.accuracy) + " m");
                                numTxtView.setText(satellite_text + satelliteNum);
                                altitudeTextView.setText(altitude_text + String.format("%.2f", gpsPosition.altitude) + " m");
                            }
                        } catch (Exception e) {
                            handle.postDelayed(runnable, 200);
                            return;
                        }
                    }
                } else if (locationStr.startsWith("$GPGGA")) {
                    String[] split = locationStr.split(","); //
                    if (split.length >= 9 && split[6].equals("1")) {
                        try {
                            gpsPosition.lat = Latitude2Decimal(split[2], split[3]);
                            gpsPosition.lon = Longitude2Decimal(split[4], split[5]);
                            gpsPosition.quality = Integer.parseInt(split[6]);
                            gpsPosition.altitude = Float.parseFloat(split[9]);
                            gpsPosition.accuracy = 7.0f * Float.parseFloat(split[8]);
                            satelliteNum = Integer.parseInt(split[7]);//当前可见卫星数量
                            gpsPosition.updatefix();
                            if (gpsPosition.fixed) {
                                Location mockLocation = new Location(LocationManager.GPS_PROVIDER);
                                mockLocation.setLatitude(gpsPosition.lat);             //纬度
                                mockLocation.setLongitude(gpsPosition.lon);            //经度
                                mockLocation.setSpeed(gpsPosition.velocity);           //地面速度
                                mockLocation.setBearing(gpsPosition.dir);              //方向
                                mockLocation.setAccuracy(gpsPosition.accuracy);        //精度
                                mockLocation.setAltitude(gpsPosition.altitude);        //海拔
                                mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()); // 没有就会报错 返回系统启动到现在的毫秒数
                                mockLocation.setTime(System.currentTimeMillis());      //不能使用GPS时间，会导致严重的时间不一致 跟系统的时间不一致
                                locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation); //写出当前位置
                            }
                            if (isShowView) {
                                latTextView.setText(lat_text + String.format("%.2f", gpsPosition.lat) + " °");
                                lonTextView.setText(lon_text + String.format("%.2f", gpsPosition.lon) + " °");
                                velocityTextView.setText(speed_text + String.format("%.2f", gpsPosition.velocity) + " m/s");
                                dirTextView.setText(dir_text + String.format("%.2f", gpsPosition.dir) + " °");
                                accuracyTextView.setText(accuracy_text + String.format("%.2f", gpsPosition.accuracy) + " m");
                                numTxtView.setText(satellite_text + satelliteNum);
                                altitudeTextView.setText(altitude_text + String.format("%.2f", gpsPosition.altitude) + " m");
                            }
                        } catch (Exception e) {
                            handle.postDelayed(runnable, 200);
                            return;
                        }
                    }
                } else if (locationStr.startsWith("$GPGSA")) {
                    String[] split = locationStr.split(",");
                    if (split.length >= 16) {
                        try {
                            gpsPosition.accuracy = 7.0f * Float.parseFloat(split[16]); //定位精度
                            gpsPosition.updatefix();
                            if (gpsPosition.fixed) {
                                Location mockLocation = new Location(LocationManager.GPS_PROVIDER);
                                mockLocation.setLatitude(gpsPosition.lat);             //纬度
                                mockLocation.setLongitude(gpsPosition.lon);            //经度
                                mockLocation.setSpeed(gpsPosition.velocity);           //地面速度
                                mockLocation.setBearing(gpsPosition.dir);              //方向
                                mockLocation.setAccuracy(gpsPosition.accuracy);        //精度
                                mockLocation.setAltitude(gpsPosition.altitude);        //海拔
                                mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()); // 没有就会报错 返回系统启动到现在的毫秒数
                                mockLocation.setTime(System.currentTimeMillis());      //不能使用GPS时间，会导致严重的时间不一致 跟系统的时间不一致
                                locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation); //写出当前位置
                            }
                        } catch (Exception e) {
                            handle.postDelayed(runnable, 200);
                            return;
                        }
                    }
                }
            }
            handle.postDelayed(runnable, 200);
        }
    };

    private double Latitude2Decimal(String s, String s2) {
        double n;
        if (s.isEmpty()) {
            n = Double.NaN;
        } else {
            try {
                double n2 = Double.parseDouble(s.substring(2)) / 60.0 + Double.parseDouble(s.substring(0, 2));
                boolean startsWith = s2.startsWith("S");
                n = n2;
                if (startsWith) {
                    return -n2;
                }
            } catch (Exception ex) {
                return Double.NaN;
            }
        }
        return n;
    }

    private double Longitude2Decimal(String s, String s2) {
        double n;
        if (s.isEmpty()) {
            n = Double.NaN;
        } else {
            try {
                double n2 = Double.parseDouble(s.substring(3)) / 60.0 + Double.parseDouble(s.substring(0, 3));
                boolean startsWith = s2.startsWith("W");
                n = n2;
                if (startsWith) {
                    return -n2;
                }
            } catch (Exception ex) {
                return Double.NaN;
            }
        }
        return n;
    }

    //USB设备的拔插监听
    private final BroadcastReceiver usbStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {//USB被拔出
                MainActivity.this.finish();
            }
        }
    };

    @Override
    protected void onDestroy() {
        handle.removeCallbacks(runnable);
        if (locationManager != null && locationManager.getProvider(LocationManager.GPS_PROVIDER) != null) {
            while (true) {
                try {
                    locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
                    locationManager.clearTestProviderEnabled(LocationManager.GPS_PROVIDER);
                    locationManager.clearTestProviderLocation(LocationManager.GPS_PROVIDER);
                    locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
                    locationManager = null;
                    break;
                } catch (SecurityException e) {
                    continue;
                }
            }
        }
        unregisterReceiver(usbStateReceiver);
        if (driver != null) {
            driver.end();
            driver = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!driver.enumerate()) {   //没有插GPS就退出吧
                MainActivity.this.finish();
            } else {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                startActivity(intent);
            }
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }
}