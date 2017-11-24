package com.xjm.gps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.util.Log;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import tw.com.prolific.driver.pl2303.PL2303Driver;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.android.hardware.USB_PERMISSION";
    private PL2303Driver driver;
    private PL2303Driver.BaudRate mBaudrate = PL2303Driver.BaudRate.B4800;
    private LocationManager locationManager;

    private TextView lonTextView;
    private TextView latTextView;
    private TextView altitudeTextView;
    private TextView velocityTextView;
    private TextView dirTextView;
    private TextView accuracyTextView;
    private TextView numTxtView;             //当前可见卫星

    private GPSPosition gpsPosition;         //另外的GPS对象
    private int satelliteNum;                //当前用的卫星数量

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
            AlertDialog.Builder normalDialog = new AlertDialog.Builder(MainActivity.this);
            normalDialog.setTitle("错误");
            normalDialog.setMessage("设备不支持USB串口通讯,程序无法运行,即将退出程序");
            normalDialog.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    MainActivity.this.finish();
                }
            });
            normalDialog.create().show();
            return;
        }
        if ((Settings.Secure.getInt(getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION, 0) == 0)) {
            AlertDialog.Builder normalDialog = new AlertDialog.Builder(MainActivity.this);
            normalDialog.setTitle("错误");
            normalDialog.setMessage("请进入系统设置打开位置模拟");
            normalDialog.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                    startActivity(intent);
                }
            });
            normalDialog.create().show();
            return;
        }
        //系统位置管理器
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE); //位置模拟
        if (locationManager == null) {
            AlertDialog.Builder normalDialog = new AlertDialog.Builder(MainActivity.this);
            normalDialog.setTitle("错误");
            normalDialog.setMessage("系统模拟位置获取出现未知错误,即将退出程序");
            normalDialog.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    MainActivity.this.finish();
                }
            });
            normalDialog.create().show();
            return;
        }
        locationManager.addTestProvider(LocationManager.GPS_PROVIDER, true, true, false, false, true, true, true
                , Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
        locationManager.setTestProviderStatus(LocationManager.GPS_PROVIDER, LocationProvider.AVAILABLE, null, System.currentTimeMillis());
        gpsPosition = new GPSPosition();
        //初始化USB驱动
        if (!driver.enumerate()) {
            numTxtView.setText("请插入合适的USB-GPS设备");
        } else {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!driver.InitByBaudRate(mBaudrate, 700)) {  //700 超时时间
                        if (!driver.PL2303Device_IsHasPermission()) {
                            Toast.makeText(MainActivity.this, "连接失败,没有权限", Toast.LENGTH_SHORT).show();
                        }
                        if (driver.PL2303Device_IsHasPermission() && (!driver.PL2303Device_IsSupportChip())) {
                            Toast.makeText(MainActivity.this, "仅支持PL2303芯片驱动", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        numTxtView.setText("GPS正在定位...");
                        handle.post(runnable);
                    }
                }
            }, 1000);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if ((Settings.Secure.getInt(getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION, 0) == 0)) {
            AlertDialog.Builder normalDialog = new AlertDialog.Builder(MainActivity.this);
            normalDialog.setTitle("错误");
            normalDialog.setMessage("请进入系统设置打开位置模拟");
            normalDialog.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                    startActivity(intent);
                    dialog.dismiss();
                }
            });
            normalDialog.create().show();
            return;
        } else {
            if (locationManager == null) {
                locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE); //位置模拟
                locationManager.addTestProvider(LocationManager.GPS_PROVIDER, true, true, false, false, true, true, true
                        , Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
                locationManager.setTestProviderStatus(LocationManager.GPS_PROVIDER, LocationProvider.AVAILABLE, null, System.currentTimeMillis());
                gpsPosition = new GPSPosition();
                if (!driver.enumerate()) {
                    numTxtView.setText("请插入合适的USB-GPS设备");
                } else {
                    new Handler().postDelayed(new Runnable() {   //必须延迟  不然初始化不成功
                        @Override
                        public void run() {
                            if (!driver.InitByBaudRate(mBaudrate, 700)) {  //700 超时时间
                                if (!driver.PL2303Device_IsHasPermission()) {
                                    Toast.makeText(MainActivity.this, "连接失败,没有权限", Toast.LENGTH_SHORT).show();
                                }
                                if (driver.PL2303Device_IsHasPermission() && (!driver.PL2303Device_IsSupportChip())) {
                                    Toast.makeText(MainActivity.this, "仅支持PL2303芯片驱动", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                numTxtView.setText("GPS正在定位...");
                                handle.post(runnable);
                            }
                        }
                    }, 1000);
                }
            }
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
                    if(locationStr.startsWith("$")){
                        lastString = locationStr;
                    }else{
                        lastString = lastString + locationStr;
                    }
                    handle.postDelayed(runnable, 100);
                    return;
                }else if(splitString.length == 2){
                    if(locationStr.startsWith("$")){
                        locationStr = splitString[0];
                    }else{
                        locationStr = lastString + splitString[0];
                    }
                    lastString = splitString[1];
                }else{
                    lastString = splitString[splitString.length - 1];
                    locationStr = splitString[splitString.length - 2];
                }
                if (locationStr.startsWith("$GPGSV")) {
                    handle.postDelayed(runnable, 400);
                    return;
                }else if (locationStr.startsWith("$GPRMC")) {
                    String[] split = locationStr.split(",");
                    if (split.length >= 9 && split[2].equals("A")) {   // A=有效定位，V=无效定位
                        try {
                            gpsPosition.lat = Latitude2Decimal(split[3], split[4]);
                            gpsPosition.lon = Longitude2Decimal(split[5], split[6]);
                            gpsPosition.velocity = Float.parseFloat(split[7]) * 0.514444f;
                            gpsPosition.dir = Float.parseFloat(split[8]);
                            gpsPosition.updatefix();
                            if(gpsPosition.fixed){
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
                            if(isShowView){
                                latTextView.setText("纬度:" + String.format("%.2f", gpsPosition.lat) + " °");
                                lonTextView.setText("经度:" + String.format("%.2f", gpsPosition.lon) + " °");
                                velocityTextView.setText("速度:" + String.format("%.2f", gpsPosition.velocity) + " m/s");
                                dirTextView.setText("方向:" + String.format("%.2f", gpsPosition.dir) + " °");
                                accuracyTextView.setText("精度:" + String.format("%.2f", gpsPosition.accuracy) + " m");
                                numTxtView.setText("可见卫星:" + satelliteNum);
                                altitudeTextView.setText("海拔:" + String.format("%.2f", gpsPosition.altitude) + " m");
                            }
                        } catch (Exception e) {
                            handle.postDelayed(runnable, 200);
                            return;
                        }
                    }
                } else if (locationStr.startsWith("$GPGGA")) {
                    String[] split = locationStr.split(","); //
                    if(split.length >= 9 && split[6].equals("1")) {
                        try {
                            gpsPosition.lat = Latitude2Decimal(split[2], split[3]);
                            gpsPosition.lon = Longitude2Decimal(split[4], split[5]);
                            gpsPosition.quality = Integer.parseInt(split[6]);
                            gpsPosition.altitude = Float.parseFloat(split[9]);
                            gpsPosition.accuracy = 7.0f * Float.parseFloat(split[8]);
                            satelliteNum = Integer.parseInt(split[7]);//当前可见卫星数量
                            gpsPosition.updatefix();
                            if(gpsPosition.fixed){
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
                            if(isShowView){
                                latTextView.setText("纬度:" + String.format("%.2f", gpsPosition.lat) + " °");
                                lonTextView.setText("经度:" + String.format("%.2f", gpsPosition.lon) + " °");
                                velocityTextView.setText("速度:" + String.format("%.2f", gpsPosition.velocity) + " m/s");
                                dirTextView.setText("方向:" + String.format("%.2f", gpsPosition.dir) + " °");
                                accuracyTextView.setText("精度:" + String.format("%.2f", gpsPosition.accuracy) + " m");
                                numTxtView.setText("可见卫星:" + satelliteNum);
                                altitudeTextView.setText("海拔:" + String.format("%.2f", gpsPosition.altitude) + " m");
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
                            if(gpsPosition.fixed){
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
            } else if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {//USB已经连接
                if (!driver.enumerate()) {
                    numTxtView.setText("请插入合适的USB-GPS设备");
                } else {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!driver.InitByBaudRate(mBaudrate, 700)) {  //700 超时时间
                                if (!driver.PL2303Device_IsHasPermission()) {
                                    Toast.makeText(MainActivity.this, "连接失败,没有权限", Toast.LENGTH_SHORT).show();
                                }
                                if (driver.PL2303Device_IsHasPermission() && (!driver.PL2303Device_IsSupportChip())) {
                                    Toast.makeText(MainActivity.this, "仅支持PL2303芯片驱动", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                numTxtView.setText("GPS正在定位...");
                                handle.post(runnable);
                            }
                        }
                    }, 1000);
                }
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