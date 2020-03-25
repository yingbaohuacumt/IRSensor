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

import java.io.File;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SerialIrTemp";
    private static final int UPDATE_TEMP_FLAG = 1;
    private static final int UPDATE_DISTANCE_FLAG = 2;
    public float objTemp = 34.0f;                       //目标温度

    //红外测温模块
    public IrTempSensor irTempSensor = new IrTempSensor();
    public boolean initSensor = false;

    //测距模块

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

        uiHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                Log.i(TAG,"人体温度：" + Float.toString(objTemp) + "℃");
                tvTemp.setText(String.format("%.2f",objTemp));
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
                                        irTempSensor.startDataSample();
                                        Thread.sleep(400);
                                        if(irTempSensor.processTemp()) {
                                            irTempSensor.calculateObjTemp();
                                            objTemp = irTempSensor.objTemp;
                                        }
                                        uiHandler.sendEmptyMessage(UPDATE_TEMP_FLAG);
                                        Thread.sleep(100);
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
