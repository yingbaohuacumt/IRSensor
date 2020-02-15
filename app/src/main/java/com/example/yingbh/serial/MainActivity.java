package com.example.yingbh.serial;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.kongqw.serialportlibrary.Device;
import com.kongqw.serialportlibrary.SerialPortFinder;
import com.kongqw.serialportlibrary.SerialPortManager;
import com.kongqw.serialportlibrary.listener.OnSerialPortDataListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Timer;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SerialIrTemp";
    public float DECAY_RATE = 0.965f;                   //红外衰减率
    public int BACK_TEMP = 2731;                        //本底温度
    public int OBJ_TEMP_MAX = BACK_TEMP + (int)(42*10.0*DECAY_RATE);
    public int OBJ_TEMP_MIN = BACK_TEMP + (int)(34*10.0*DECAY_RATE);
    public float objBaseTemp = 35.0f;                   //目标基准温度
    public float objTemp = 35.0f;                       //目标温度

    //红外测温模块
    public IrTempSensor irTempSensor = new IrTempSensor();
    public boolean initSensor = false;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //红外测温模块接口初始化
        initSensor = irTempSensor.initIrSensor(new File("/dev/ttyUSB0"),921600);
        if(initSensor) {
            Log.i(TAG,"init sensor success");
        }
    }

    /**
     * 功能：单次数据采集
     * @param
     * @return
     */
    public void sampleData(View v) {
        if(initSensor) {
            irTempSensor.startDataSample();
        }
    }
    /**
     * 功能：读取目标温度
     * @param
     * @return
     */
    public void displayTemp(View v) {
        TextView txv;
        txv= (TextView) findViewById(R.id.txtTemp);

        caculateObjTemp();
        txv.setText(String.valueOf(objTemp));
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
        Log.i(TAG,"像素点温度数目 = " + irTempSensor.pixelList.size());
        Log.i(TAG,"排序后像素点温度:");
//        for(int i=0;i<irTempSensor.pixelList.size();i++) {
//            Log.i(TAG,"pixelList[" + i + "] = " + irTempSensor.pixelList.get(i));
//        }

        //阈值索引检索
        for(int i=0;i<irTempSensor.pixelList.size();i++) {
            temp = irTempSensor.pixelList.get(i);
            if(temp > OBJ_TEMP_MAX) {
                continue;
            } else {
                if(doorStart != 0) {
                    doorStart = i;
                }

                if(temp > OBJ_TEMP_MIN) {
                    continue;
                } else {
                    doorEnd = i-1;
                    break;
                }
            }
        }

        //剔除阈值内极大值
        doorStart += 3;
        Log.i(TAG,"阈值范围索引：" + doorStart + "~" + doorEnd);
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
