package com.example.yingbh.serial.activity;

import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import com.example.yingbh.serial.sensor.IrTempSensor;
import com.example.yingbh.serial.R;
import com.minivision.parameter.util.LogBuilder;
import com.minivision.parameter.util.LogUtil;

import java.io.File;
import java.io.FileWriter;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Sensor";
    private static final int UPDATE_TEMP_FLAG = 1;
    private static final int UPDATE_DISTANCE_FLAG = 2;
    private static final String LOG_PATH = "/DeviceMonitorLog/com.example.yingbh.serial";
    public LogBuilder builder;

    //红外测温模块
    public IrTempSensor irTempSensor = new IrTempSensor();
    public boolean initSensor = false;
    public float objTemp = 35.0f;                       //目标温度
    public float envTemp = 0.0f;

    //测距模块
    public int objDistance = 50;

    //其它
    private TextView tvTemp,tvEnvTemp,tvDistance;
    private Button btnSample,btnClrLog;
    private boolean btnFlag = false;

    //多线程
    private Handler uiHandler;
    private Thread sonThread;

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

        //红外测温模块接口初始化
        initSensor = irTempSensor.initIrSensor(new File("/dev/ttyS1"),115200);
        if(initSensor) {
            Log.i(TAG,"init sensor success");
        }

        uiHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case UPDATE_TEMP_FLAG:
                        Log.i(TAG,String.format("人脸温度(℃)：%.2f；环境温度(℃)：%.2f", objTemp, envTemp));
                        tvTemp.setText(String.format("%.2f",objTemp));
                        tvEnvTemp.setText(String.format("%.2f",envTemp));
                        break;
                    case UPDATE_DISTANCE_FLAG:
                        Log.i(TAG,"人脸距离：" + objDistance + "cm");
                        tvTemp.setText(String.format("%d",objDistance));
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
                                        Thread.sleep(400);
                                        if(irTempSensor.processTemp()) {
                                            irTempSensor.calculateObjTemp();
                                            objTemp = irTempSensor.objTemp;
                                            envTemp = irTempSensor.envTemp;
                                        }
                                        uiHandler.sendEmptyMessage(UPDATE_TEMP_FLAG);

                                        LogUtil.i(MainActivity.class,String.format(",脸温(℃),%.2f,环温(℃),%.2f,距离(cm),%d,像素点温度极大值,%s"
                                                , objTemp, envTemp, objDistance,irTempSensor.pixelMaxValue));
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


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
