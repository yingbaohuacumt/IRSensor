package com.example.yingbh.serial.activity;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


import com.example.yingbh.serial.sensor.IrTempSensor;
import com.example.yingbh.serial.R;
import com.example.yingbh.serial.sensor.DistanceSensor;

import java.io.File;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Sensor";
    private static final int UPDATE_TEMP_FLAG = 1;
    private static final int UPDATE_DISTANCE_FLAG = 2;

    //红外测温模块
    public IrTempSensor irTempSensor = new IrTempSensor();
    public boolean initSensor = false;
    public float objTemp = 35.0f;                       //目标温度

    //测距模块
    public int objDistance = 50;
    public DistanceSensor distanceSensor = new DistanceSensor();

    //其它
    private TextView tvTemp,tvDistance;
    private Button btnSample;
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

        tvTemp = (TextView) findViewById(R.id.tv_temp);
        tvDistance = (TextView) findViewById(R.id.tv_distance);
        btnSample = (Button) findViewById(R.id.btn_sample);

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
                        Log.i(TAG,"人脸温度：" + Float.toString(objTemp) + "℃");
                        tvTemp.setText(String.format("%.2f",objTemp));
                        break;
                    case UPDATE_DISTANCE_FLAG:
                        Log.i(TAG,"人脸距离：" + objDistance + "mm");
                        tvDistance.setText(String.format("%d",objDistance));
                        break;
                }

                return true;
            }
        });

        //按钮onClick事件
        btnSample.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(initSensor) {
                    if(false == btnFlag) {
                        btnSample.setText("停止检测");
                        btnFlag = true;
                        sonThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                while(btnFlag) {
                                    try {
                                        irTempSensor.startDataSample();distanceSensor.startDataSample();   // 获取距离
                                        Thread.sleep(300);
                                           if(irTempSensor.processTemp()) {
                                            irTempSensor.calculateObjTemp();
                                            objTemp = irTempSensor.objTemp;
                                            uiHandler.sendEmptyMessage(UPDATE_TEMP_FLAG);
                                        }
                                       // Thread.sleep(50);

                                        //Thread.sleep(300);
                                        if (distanceSensor.isSensorDistanceGet()) {
                                            objDistance = distanceSensor.SensorDistanceValue();
                                            uiHandler.sendEmptyMessage(UPDATE_DISTANCE_FLAG);
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
