package com.example.yingbh.serial.activity;

import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.AnyThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;


import com.example.yingbh.serial.sensor.IrTempSensor;
import com.example.yingbh.serial.R;
import com.minivision.parameter.util.LogBuilder;
import com.minivision.parameter.util.LogUtil;
import com.example.yingbh.serial.sensor.DistanceSensor;


import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Random;

import ca.hss.heatmaplib.HeatMap;
import ca.hss.heatmaplib.HeatMapMarkerCallback;

public class MainActivity extends AppCompatActivity{
    private static final String TAG = "Sensor";
    private static final int UPDATE_TEMP_FLAG = 1;
    private static final int UPDATE_DISTANCE_FLAG = 2;
    private static final int DISPLAY_HOT_IMAGE_FLAG = 3;
    private static final String LOG_PATH = "/DeviceMonitorLog/com.example.yingbh.serial";
    public LogBuilder builder;
    private int runCnts = 0;

    //红外测温模块
    public IrTempSensor irTempSensor = new IrTempSensor();
    public boolean initSensor = false;
    public float objTemp = 35.0f;                       //目标温度
    public float envTemp = 0.0f;
    public float calTemp = 0.0f;

    //测距模块
    public int objDistance = 50;
    public DistanceSensor distanceSensor = new DistanceSensor();

    //其它
    private TextView tvTemp,tvEnvTemp,tvDistance, tvCalTemp;
    private Button btnSample,btnClrLog;
    private boolean btnFlag = false;

    //多线程
    private Handler uiHandler;
    private Thread sonThread;

    //热力图
    private HeatMap map;
    private boolean testAsync = true;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //先检测是否有之前留存的/sdcard/DeviceMonitorLog/"com.example.yingbh.serial"文件
        File file = new File(Environment.getExternalStorageDirectory() + LOG_PATH);
        if (file.exists()) {
            Log.i(TAG,"log path: " + file.getPath());
            boolean deleteResult = file.delete();
            Log.i(TAG, "Old file delete result: " + deleteResult);
        } else {
            Log.i(TAG,"log path: " + file.getPath() + " not exists!");
        }

        //初始化日志工具
        builder = new LogBuilder().withContext(this).setTag(TAG).setOnLine(false);
        LogUtil.setLogBuilder(builder);

        LogUtil.i(MainActivity.class,"***************************************************");
        LogUtil.i(MainActivity.class,"**************** 测温测距日志记录 ****************");
        LogUtil.i(MainActivity.class,"***************************************************");

        tvTemp = (TextView) findViewById(R.id.tv_temp);
        tvEnvTemp = (TextView) findViewById(R.id.tv_envtemp);
        tvDistance = (TextView) findViewById(R.id.tv_distance);
        btnSample = (Button) findViewById(R.id.btn_sample);
        btnClrLog = (Button) findViewById(R.id.btn_clr_log);
        tvCalTemp = (TextView)findViewById(R.id.tv_calTemp) ;

        //热图配置
        map = findViewById(R.id.example_map);
        map.setMinimum(20.0);
        map.setMaximum(40.0);       //热图覆盖梯度
        //        map.setLeftPadding(100);
        //        map.setRightPadding(100);
        //        map.setTopPadding(100);
        //        map.setBottomPadding(100);
        map.setRadius(15.0);
        Map<Float,Integer> colors = new ArrayMap<>();
        for (int i = 0; i < 41; i++) {
            float stop = ((float)i) / 40.0f;
            int color = doGradient(i * 5, 0, 200, 0xff00ff00, 0xffff0000);
            colors.put(stop, color);
        }
        map.setColorStops(colors);  //颜色梯度

        //        map.setOnMapClickListener(new HeatMap.OnMapClickListener() {
        //            @Override
        //            public void onMapClicked(int x, int y, HeatMap.DataPoint closest) {
        //                addData();
        //            }
        //        });


        //红外测温模块接口初始化
        initSensor = irTempSensor.initIrSensor(new File("/dev/ttyS1"),115200);
        if(initSensor) {
            Log.i(TAG,"init sensor success");
        }
        //距离传感器初始化
        initSensor = distanceSensor.initDistatnceSensor(new File("/dev/ttyUSB0"),9600);
        if(initSensor) {
            Log.i(TAG,"init distance sensor success");
        }
        else{
            Log.e(TAG,"init distance sensor failed");
        }

        uiHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case UPDATE_TEMP_FLAG:
                        Log.i(TAG,String.format("人脸温度(℃)：%.2f；?环境温度(℃)：%.2f", objTemp, envTemp));
                        tvTemp.setText(String.format("%.2f",objTemp));
                        tvEnvTemp.setText(String.format("%.2f",envTemp));
                        break;
                    case UPDATE_DISTANCE_FLAG:
                        Log.i(TAG,"人脸距离：" + objDistance + "mm");
                        Log.i(TAG,String.format("校准后温度(℃)：%.2f", calTemp ));

                        tvDistance.setText(String.format("%d",objDistance));
                        tvCalTemp.setText(String.format("%.2f",calTemp));
                        break;
                    case DISPLAY_HOT_IMAGE_FLAG:
                        Log.i(TAG,"热成像图显示");
                        addData();
                        break;
                }

                return true;
            }
        });


        //开始检测按键onClick事件
        btnSample.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(initSensor) {
                    if(false == btnFlag) {
                        btnSample.setText("停止检测");
                        btnClrLog.setEnabled(false);
                        btnFlag = true;
                        sonThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                while(btnFlag) {
                                    try {
                                        irTempSensor.startDataSample();
                                        distanceSensor.startDataSample();   // 获取距离
                                        Thread.sleep(400);
                                        if(irTempSensor.processTemp()) {
                                            irTempSensor.calculateObjTemp();
                                            objTemp = irTempSensor.objTemp;
                                            envTemp = irTempSensor.envGet();
                                        }
                                        uiHandler.sendEmptyMessage(UPDATE_TEMP_FLAG);

                                        if (distanceSensor.isSensorDistanceGet()) {
                                            objDistance = distanceSensor.SensorDistanceValue();
                                            uiHandler.sendEmptyMessage(UPDATE_DISTANCE_FLAG);
                                        }

                                        calTemp = temp_cal(objTemp, ((float)objDistance)/10);


                                        LogUtil.i(MainActivity.class,String.format(",脸温(℃),%.2f,环温(℃),%.2f,距离(mm),%d,校温(℃),%.2f,像素点温度极大值,%s"
                                        , objTemp, envTemp, objDistance,calTemp,irTempSensor.pixelMaxValue));

                                        runCnts++;
                                        if(runCnts%2 == 0) {
                                            //热图显示处理
                                            drawNewMap();
                                            uiHandler.sendEmptyMessage(DISPLAY_HOT_IMAGE_FLAG);
                                        }
                                        Thread.sleep(50);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        });
                        sonThread.start();
                    } else {
                        btnSample.setText("开始检测");
                        btnFlag = false;
                        btnClrLog.setEnabled(true);
                    }
                }
            }
        });

        //清除日志按键事件
        btnClrLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String directory = Environment.getExternalStorageDirectory() + LOG_PATH;
                String[] allFiles;
                File folder;
                String scanpath;

                Log.i(TAG,"log path: " + directory);
                folder = new File(directory);
                //存入所有文件名
                allFiles = folder.list();
                if(allFiles.length == 0) {
                    Toast.makeText(MainActivity.this,"暂无日志", Toast.LENGTH_LONG).show();
                    Log.i(TAG,"暂无日志");
                } else {
                    //遍历数组
                    for(int i=0;i<allFiles.length;i++) {
                        scanpath = folder + "/" + allFiles[i];
                        File file = new File(scanpath);
                        if(file.exists()) {
                            Log.i(TAG,"log file: " + file.getPath());
                            try {
                                LogUtil.release();

                                file.delete();
                                LogUtil.setLogBuilder(builder);
                                LogUtil.i(MainActivity.class,"***************************************************");
                                LogUtil.i(MainActivity.class,"**************** 测温测距日志记录 ****************");
                                LogUtil.i(MainActivity.class,"***************************************************");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        }
                    }
                }
            }
        });

    }

    private void addData() {
        if (testAsync) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
//                    drawNewMap();
                    map.forceRefreshOnWorkerThread();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            map.invalidate();
                        }
                    });
                }
            });
        }
        else {
//            drawNewMap();
            map.forceRefresh();
        }
    }

    @AnyThread
    private void drawNewMap() {
        map.clearData();
        if(irTempSensor.pixelListBackup.size() >= 1024) {
            float x,y,temp;
            for (int line=5;line<32;line++) {       //屏蔽前5排可能异常数据点
                for(int column=0;column<32;column++) {
                    y=(float)line/32.0f+0.015625f;
                    x=(float)column/32.0f+0.015625f;
                    temp = (float) (irTempSensor.pixelListBackup.get(line*32+column) - 2731)/10.0f;
                    HeatMap.DataPoint point = new HeatMap.DataPoint(x,y,temp);
                    map.addData(point);
                }
            }
        }

    }


    private static int doGradient(double value, double min, double max, int min_color, int max_color) {
        if (value >= max) {
            return max_color;
        }
        if (value <= min) {
            return min_color;
        }
        float[] hsvmin = new float[3];
        float[] hsvmax = new float[3];
        float frac = (float)((value - min) / (max - min));
        Color.RGBToHSV(Color.red(min_color), Color.green(min_color), Color.blue(min_color), hsvmin);
        Color.RGBToHSV(Color.red(max_color), Color.green(max_color), Color.blue(max_color), hsvmax);
        float[] retval = new float[3];
        for (int i = 0; i < 3; i++) {
            retval[i] = interpolate(hsvmin[i], hsvmax[i], frac);
        }
        return Color.HSVToColor(retval);
    }

    private static float interpolate(float a, float b, float proportion) {
        return (a + ((b - a) * proportion));
    }

    private float temp_cal(float temp, float distance)
    {
        return (temp - fx(distance) + fx(50.00f));
    }

    private float fx(float x){
        return (float)(36.30024 - 0.07277 * x + 7.27922*Math.pow(10,-4)*Math.pow(x,2) - 2.44949*Math.pow(10,-6)*Math.pow(x,3));
    }
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
