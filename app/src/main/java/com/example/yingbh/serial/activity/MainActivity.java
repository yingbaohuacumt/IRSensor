package com.example.yingbh.serial.activity;

import android.content.Intent;
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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Random;

import ca.hss.heatmaplib.HeatMap;
import ca.hss.heatmaplib.HeatMapMarkerCallback;

public class MainActivity extends AppCompatActivity{
    private static final String TAG = "Sensor";
    private static final String TAG_FOREHAND = "Forehand";
    private static final String TAG_FOREHAND_5 = "Forehand_5";
    private static final int UPDATE_TEMP_FLAG = 1;
    private static final int UPDATE_DISTANCE_FLAG = 2;
    private static final int DISPLAY_HOT_IMAGE_FLAG = 3;
    private static final String LOG_PATH = "/DeviceMonitorLog/com.example.yingbh.serial";
    public LogBuilder builder;
    private int  runCnts = 0;

    //红外测温模块
    public IrTempSensor irTempSensor = new IrTempSensor();
    public boolean initSensor = false;
    public float faceTemp = 0.0f;                   //脸温
    public float envTemp = 0.0f;                    //环温
    public float calTemp = 0.0f;
    public float forehandTemp = 0.0f;               //额温
    public float bodyTemp = 0.0f;                   //最终体温

    private static final int  pointNum = 5; // 取几个点做平均
    public int calNum = 0;
    private int[]   tmpDistance = {0,0,0,0,0,0};
    private float[] tmpCalTemp = {0.0f,0.0f,0.0f,0.0f,0.0f,0.0f};
    private float   avrCalTemp = 0.0f;
    private int     avrDistance = 0;
    private StringBuilder buff1= new StringBuilder("");

    //测距模块
    public int objDistance = 0;
    public DistanceSensor distanceSensor = new DistanceSensor();

    //其它
    private TextView tvTemp,tvEnvTemp,tvDistance, tvCalTemp,tvCalNum,tvPosition,tvForehandTemp;
    private Button btnSample,btnClrLog;
    private boolean btnFlag = false;

    //多线程
    private Handler uiHandler;
    private Thread sonThread;

    //热力图
    private HeatMap map;
    private boolean testAsync = true;

    private int forehandPosX=0,forehandPosY=0;


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
        tvCalNum = (TextView)findViewById(R.id.tv_calNum);
        tvPosition = (TextView)findViewById(R.id.tv_position);
        tvForehandTemp = (TextView)findViewById(R.id.tv_forehand_temp);
        //热图配置
        map = findViewById(R.id.example_map);
        map.setMinimum(20.0);
        map.setMaximum(40.0);       //热图覆盖梯度
        map.setRadius(15.0);
        Map<Float,Integer> colors = new ArrayMap<>();
        for (int i = 0; i < 41; i++) {
            float stop = ((float)i) / 40.0f;
            int color = doGradient(i * 5, 0, 200, 0xff00ff00, 0xffff0000);
            colors.put(stop, color);
        }
        map.setColorStops(colors);  //颜色梯度


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
                        Log.i(TAG,String.format("额温(℃),%.2f,脸温(℃),%.2f,体温(℃),%.2f,环温(℃),%.2f", forehandTemp, faceTemp, bodyTemp, envTemp));
                        LogUtil.i(TAG,String.format("额温(℃),%.2f,脸温(℃),%.2f,体温(℃),%.2f,环温(℃),%.2f", forehandTemp, faceTemp, bodyTemp, envTemp));
                        tvEnvTemp.setText(String.format("%.2f",envTemp));

                        if(bodyTemp > 28.0f) {
                            tvTemp.setText(String.format("%.2f",bodyTemp));
                            String message = "";
                            message = "额温峰值坐标：L-" + Integer.toString(forehandPosY) + "  C-" + Integer.toString(forehandPosX);
                            tvPosition.setText(message);
                            tvForehandTemp.setText(String.format("%.2f",forehandTemp));
                        }

                        break;
                    case UPDATE_DISTANCE_FLAG:
                        Log.i(TAG,"人脸距离：" + objDistance + "mm");
                        Log.i(TAG,String.format("校准后温度(℃)：%.2f", avrCalTemp ));

                        tvDistance.setText(String.format("%d",objDistance));
//                        tvCalNum.setText(String.format("[%d]",calNum));
                        tvCalTemp.setText(String.format("%.1f",avrCalTemp));
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
                                            //原始脸温计算
                                            irTempSensor.calculateObjTemp();
                                            faceTemp = irTempSensor.objTemp;
                                            envTemp = irTempSensor.envGet();

                                            //额温计算
                                            forehandTemp = irTempSensor.foreheadTempCalc();
                                            forehandPosX = irTempSensor.forehandPosX;
                                            forehandPosY = irTempSensor.forehandPosY;

                                            //最终体温计算：额温偏低采用脸温，否则采用额温
                                            if(forehandTemp < faceTemp - 1.0f) {
                                                bodyTemp = faceTemp;
                                            } else {
                                                bodyTemp = forehandTemp;
                                            }
                                        }
                                        uiHandler.sendEmptyMessage(UPDATE_TEMP_FLAG);

                                        if (distanceSensor.isSensorDistanceGet()) {
                                            objDistance = distanceSensor.SensorDistanceValue();
                                            uiHandler.sendEmptyMessage(UPDATE_DISTANCE_FLAG);
                                        }

                                        if(objDistance < 1200) {
                                            // 原始温度数据打印
                                            StringBuilder pixelBuff= new StringBuilder("");
                                            pixelBuff.append("原始温度数据点数:"+irTempSensor.pixelListBackup.size()+"\r\n");
                                            for(int num = 4*32; num < irTempSensor.pixelListBackup.size(); num++){
                                                if(num%32 == 0){
                                                    pixelBuff= new StringBuilder("");
                                                    pixelBuff.append("\r\n"+(num)+"-"+(num+31)+":");
                                                }
                                                pixelBuff.append(String.format(",%d",irTempSensor.pixelListBackup.get(num)));
                                                if((num+1)%32 == 0)  {
                                                    LogUtil.i("",pixelBuff.toString());
                                                }
                                            }

                                            calTemp = temp_cal(forehandTemp, ((float) objDistance) / 10);

                                            tmpCalTemp[calNum] = calTemp;
                                            tmpDistance[calNum] = objDistance;

                                            buff1.append(String.format("\r\n,脸温(℃),%.2f,环温(℃),%.2f,距离(mm),%d,校温(℃),%.2f,额温(℃),%.2f,最终体温(℃),%.1f,取5点温度,%.2f,,%.2f\r\n",
                                                    faceTemp, envTemp, objDistance, calTemp, forehandTemp, bodyTemp, irTempSensor.ForehandTemp_5point, irTempSensor.ForehandTemp_5point_1));
                                            LogUtil.i(TAG, String.format("-----------------------%d\r\n", calNum));

                                            calNum++;
                                            if (calNum >= pointNum) {
                                                boolean flag = true;
                                                int i = 1;
                                                while (i < pointNum) {
                                                    if( (Math.abs(tmpCalTemp[i] - tmpCalTemp[0]) < 0.3) &&
                                                        (Math.abs(tmpDistance[i] - tmpDistance[0] ) < 30))
//                                                    if(Math.abs(tmpDistance[i] - tmpDistance[0] ) < 30) // 只限制距离
                                                    {
                                                         Log.i(TAG,"true");
                                                    }
                                                    else{
                                                        flag = false;
                                                       // Log.i(TAG,"false");
                                                        break;
                                                    }
                                                    i++;
                                                }

                                                if (flag == true) {
                                                    // 剔除最大值温度和最小值温度，然后取平均温度和距离
//                                                int minNum = 0, maxNum = 0;
//                                                float minTemp = tmpCalTemp[0];
//                                                float maxTemp = tmpCalTemp[0];
//                                                for(int j = 1; j < pointNum;j++){   // 查找最大和最小值
//                                                    if(tmpCalTemp[j] < minTemp){
//                                                        minTemp = tmpCalTemp[j];
//                                                        minNum = j;
//                                                    }
//                                                    if(tmpCalTemp[j] >maxTemp){
//                                                        maxTemp = tmpCalTemp[j];
//                                                        maxNum = j;
//                                                    }
//                                                }
                                                    StringBuilder caltempList = new StringBuilder("");
                                                    StringBuilder distanceList = new StringBuilder("");
                                                    StringBuilder avrBuff = new StringBuilder("");

                                                    // 计算平均值
                                                    avrCalTemp = calFloatSumAverage(tmpCalTemp, pointNum);
                                                    avrDistance = calIntFloatSumAverage(tmpDistance, pointNum);

//                                                caltempList.append(String.format(",温度最大点位置,%d,最小点位置,%d,数据->,%.2f,%.2f,%.2f,%.2f,%.2f",
//                                                        maxNum,minNum,tmpCalTemp[0],tmpCalTemp[1],tmpCalTemp[2],tmpCalTemp[3],tmpCalTemp[4]));
//                                                distanceList.append(String.format(",对应距离最大点位置,%d,最小点位置,%d,数据->,%d,%d,%d,%d,%d" ,
//                                                                maxNum,minNum,tmpDistance[0],tmpDistance[1],tmpDistance[2],tmpDistance[3],tmpDistance[4]));
//                                                Log.i(TAG,caltempList.toString());
//                                                Log.i(TAG,distanceList.toString());
//                                                LogUtil.i(MainActivity.class,caltempList.toString());
//                                                LogUtil.i(MainActivity.class,distanceList.toString());

//                                                float avrCalTemp2 = calFloatSumAverage_x(tmpCalTemp,pointNum, tmpCalTemp[minNum],tmpCalTemp[maxNum]) ;
//                                                int avrDistance2 = avrDistance = calIntFloatSumAverage_x(tmpDistance,pointNum,tmpDistance[minNum],tmpDistance[maxNum]);

//                                                avrBuff.append(String.format(",平均距离(mm),%d,平均温度(℃),%.2f,平均距离2(mm),%d,平均温度2(℃),%.2f,",
//                                                avrDistance,avrCalTemp,avrDistance2,avrCalTemp2));

                                                    avrBuff.append(String.format(",%d次取平均：平均距离(mm),%d,平均温度(℃),%.1f,",pointNum, avrDistance, avrCalTemp));

                                                    Log.i(TAG, avrBuff.toString());
                                                    Log.i(TAG, buff1.toString());

                                                    LogUtil.i(MainActivity.class, avrBuff.toString());
                                                    LogUtil.i(MainActivity.class, buff1.toString());

                                                } else {
                                                    //Log.i(TAG, "-----fail ");
                                                }
                                                calNum = 0;
                                                buff1 = new StringBuilder("");

                                            }
                                        }

                                        runCnts++;
                                        if(runCnts%2 == 0) {
                                            //热图显示处理
                                            drawNewMap();
                                            uiHandler.sendEmptyMessage(DISPLAY_HOT_IMAGE_FLAG);
                                        }

                                        LogUtil.i("",",-------------------数据分割---------------------------------");

                                        Thread.sleep(50);
                                        Thread.sleep(450);
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
                    y=(float)line/32.0f+0.015625f;      //平移0.5dp显示
                    x=(float)column/32.0f+0.015625f;
                    if((line == forehandPosY) &&(column == forehandPosX)){    // mq add 显示中心位置
                        temp = 255.0f;
                    }
                    else {
                        temp = (float) (irTempSensor.pixelListBackup.get(line * 32 + column) - 2731) / 10.0f;
                    }
                    if(temp > 20.0f) {  //高于底溫才顯示
                        HeatMap.DataPoint point = new HeatMap.DataPoint(x, y, temp);
                        map.addData(point);
                    }
                }
            }
        } else {
            Log.w(TAG, "数据点过少，无法绘制热图！");
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
        return (temp - fx(distance) + fx(50.00f)  + 0.0f);      //默认偏置由0.7改为0  4-11
        //return fx(distance);
    }

    private float fx(float x) {
        //return (float)(36.30024 - 0.07277 * x + 7.27922*Math.pow(10,-4)*Math.pow(x,2) - 2.44949*Math.pow(10,-6)*Math.pow(x,3));
        //return (float)(36.10493 + 0.04623 * x - 0.00154*Math.pow(x,2) + 9.5452*Math.pow(10,-6)*Math.pow(x,3));
        // return (float)(36.13076 -0.04221 * x - 4.06591*Math.pow(10,-5)*Math.pow(x,2) + 2.0163*Math.pow(10,-6)*Math.pow(x,3));
       // return (float) (37.76075 - 0.03688 * x - 2.3927 * Math.pow(10, -4) * Math.pow(x, 2) + 3.77025 * Math.pow(10, -6) * Math.pow(x, 3));
        //return (float) (37.06443 - 0.028 * x + 1.69379 * Math.pow(10, -4) * Math.pow(x, 2) -1.15568 * Math.pow(10, -6) * Math.pow(x, 3));
        return (float) (36.94639 - 0.01847 * x - 5.61219 * Math.pow(10, -5) * Math.pow(x, 2) +2.93826 * Math.pow(10, -7) * Math.pow(x, 3));
    }

    private float calFloatSumAverage(float[] data, int num){
        float sum = 0;
        int i = 0;
        for(;i<num;i++)
            sum+= data[i];
        return sum/num;
    }
    private float calFloatSumAverage_x(float[] data, int num, float Min, float Max){
        float sum = 0;
        int i = 0;
        for(;i<num;i++)
            sum+= data[i];
        return (sum - Max - Min)/(num-2);
    }

    private int calIntFloatSumAverage(int[] data, int num){
        int sum = 0;
        int i = 0;
        for(;i<num;i++)
            sum+= data[i];
        return sum/num;
    }
    private int calIntFloatSumAverage_x(int[] data, int num, int Min, int Max){
        int sum = 0;
        int i = 0;
        for(;i<num;i++)
            sum+= data[i];
        return (sum - Max - Min)/(num-2);
    }
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
