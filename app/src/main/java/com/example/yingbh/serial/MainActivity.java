package com.example.yingbh.serial;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


import java.io.File;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SerialIrTemp";
    public float DECAY_RATE = 0.965f;                   //红外衰减率
    public int BACK_TEMP = 2731;                        //本底温度
    public int OBJ_TEMP_MAX = BACK_TEMP + (int)(42*10.0*DECAY_RATE);
    public int OBJ_TEMP_MIN = BACK_TEMP + (int)(34*10.0*DECAY_RATE);
    public float objBaseTemp = 34.0f;                   //目标基准温度
    public float objTemp = 34.0f;                       //目标温度

    //红外测温模块
    public IrTempSensor irTempSensor = new IrTempSensor();
    public boolean initSensor = false;

    //其它
    private TextView txv;
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

        //获取温度显示文本ID
        txv = (TextView) findViewById(R.id.txtTemp);

        //红外测温模块接口初始化
        initSensor = irTempSensor.initIrSensor(new File("/dev/ttyS1"),115200);
        if(initSensor) {
            Log.i(TAG,"init sensor success");

        }

        uiHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                Log.i(TAG,"人体温度：" + Float.toString(objTemp) + "℃");
                txv.setText(Float.toString(objTemp));
                return true;
            }
        });
    }

    /**
     * 功能：人体温度测量
     * @param
     * @return
     */
    public void sampleData(View v) {
        Button btn = (Button)findViewById(R.id.btnSample);
        if(initSensor) {
            if(false == btnFlag) {
                btn.setText("停止检测");
                btnFlag = true;
                sonThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while(btnFlag) {
                            try {
                                irTempSensor.startDataSample();
                                Thread.sleep(300);
                                if(irTempSensor.processTemp()) {
                                    caculateObjTemp();
                                }
                                uiHandler.sendEmptyMessage(1);
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
                sonThread.start();
            } else {
                btn.setText("开始检测");
                btnFlag = false;
            }
        }
    }

    /**
     * 功能：目标温度计算
     * @param
     * @return
     */
    public void caculateObjTemp() {
        int doorStart = 0;  //阈值上限索引值
        int doorEnd = 0;    //阈值下限索引值
        int temp = 0;

        if(irTempSensor.pixelList.size() == 0) {
            return;
        }

        Log.i(TAG,"人体温度范围：34℃~42℃(" + OBJ_TEMP_MIN + "~" + OBJ_TEMP_MAX + ")");

        //像素点温度降序排列
        Collections.sort(irTempSensor.pixelList,Collections.<Integer>reverseOrder());
//        Log.i(TAG,"20个极大值像素点温度:");
//        for(int index=0; index<20; index++) {
//            Log.i(TAG,"pixelList[" + index + "] = " + irTempSensor.pixelList.get(index));
//        }

        //阈值索引检索
        for(int i=0;i<irTempSensor.pixelList.size();i++) {
            temp = irTempSensor.pixelList.get(i);
            if(temp > OBJ_TEMP_MAX) {
                continue;
            } else {
                if(doorStart == 0) {
                    doorStart = i;
                }

                if(temp > OBJ_TEMP_MIN) {
                    doorEnd = i;
                    continue;
                } else {
                    doorEnd = i-1;
                    break;
                }
            }
        }

        //剔除阈值内极大值
        doorStart += 3;
        Log.i(TAG,"像素点温度数目 = " + irTempSensor.pixelList.size());
        Log.i(TAG,"阈值范围索引：" + doorStart + "~" + doorEnd);
//        Log.i(TAG,"阈值范围内像素点温度:");
//        for(int index=doorStart; index<doorStart+50; index++) {
//            Log.i(TAG,"pixelList[" + index + "] = " + irTempSensor.pixelList.get(index));
//        }

        if(doorEnd >= doorStart+20) {
            int areaLen = (doorEnd-doorStart+1 >= 40)?40:(doorEnd-doorStart+1);
            int sum = 0;
            for(int index=0; index<areaLen; index++) {
                sum += irTempSensor.pixelList.get(index + doorStart);
            }
            objTemp = (float)(sum/areaLen - BACK_TEMP)/(10*DECAY_RATE);
        } else {
            //不足20个点，数据异常处理
            objTemp = objBaseTemp;
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
