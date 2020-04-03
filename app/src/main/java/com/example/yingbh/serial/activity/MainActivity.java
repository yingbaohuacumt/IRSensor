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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Random;

import ca.hss.heatmaplib.HeatMap;
import ca.hss.heatmaplib.HeatMapMarkerCallback;

public class MainActivity extends AppCompatActivity{
    private static final String TAG = "Sensor";
    private static final String TAG_FOREHAND = "Forehand";
    private static final int UPDATE_TEMP_FLAG = 1;
    private static final int UPDATE_DISTANCE_FLAG = 2;
    private static final int DISPLAY_HOT_IMAGE_FLAG = 3;
    private static final String LOG_PATH = "/DeviceMonitorLog/com.example.yingbh.serial";
    public LogBuilder builder;
    private int  runCnts = 0;

    //红外测温模块
    public IrTempSensor irTempSensor = new IrTempSensor();
    public boolean initSensor = false;
    public float objTemp = 35.0f;                   //目标温度
    public float envTemp = 0.0f;
    public float calTemp = 0.0f;
    public float forehandTemp = 0.0f;               //额温

    private static final int  pointNum = 5;
    public int calNum = 0;
    private int[]   tmpDistance = {0,0,0,0,0,0};
    private float[] tmpCalTemp = {0.0f,0.0f,0.0f,0.0f,0.0f,0.0f};
    private float   avrCalTemp = 0.0f;
    private int     avrDistance = 0;
    public String[] pixel[];
    private StringBuilder buff1= new StringBuilder("");
    private StringBuilder buff2= new StringBuilder("");
    private StringBuilder buff3= new StringBuilder("");
    //测距模块
    public int objDistance = 50;
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
    private int posX=0,posY=0;

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
        //        map.setLeftPadding(100);
        //        map.setRightPadding(100);
        //        map.setTopPadding(100);
        //        map.setBottomPadding(100);
//        map.setMarkerCallback(new HeatMapMarkerCallback.CircleHeatMapMarker(0xff9400D3));
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
                        Log.i(TAG,String.format("人脸温度(℃)：%.2f；环境温度(℃)：%.2f", objTemp, envTemp));
                        tvTemp.setText(String.format("%.2f",objTemp));
                        tvEnvTemp.setText(String.format("%.2f",envTemp));
                        break;
                    case UPDATE_DISTANCE_FLAG:
                        Log.i(TAG,"人脸距离：" + objDistance + "mm");
                        Log.i(TAG,String.format("校准后温度(℃)：%.2f", calTemp ));

                        tvDistance.setText(String.format("%d",objDistance));
                        tvCalNum.setText(String.format("[%d]",calNum));
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

                                        tmpCalTemp[calNum] = calTemp;
                                        tmpDistance[calNum] = objDistance;

                                        buff1.append(String.format("\r\n,脸温(℃),%.2f,环温(℃),%.2f,距离(mm),%d,校温(℃),%.2f,像素点温度极大值,%s\r\n" , objTemp, envTemp, objDistance, calTemp, irTempSensor.pixelMaxValue));
                                        Log.i(TAG, String.format("%d\r\n",calNum));
                                        Log.i(TAG,buff1.toString());
                                        calNum++;
                                        if(calNum >= pointNum)
                                        {
                                            boolean flag = false;
                                            int i = 1;
                                            while(i< pointNum)
                                            {
                                                if( (Math.abs(tmpCalTemp[i] - tmpCalTemp[0]) < 0.3) &&
                                                        (Math.abs(tmpDistance[i] - tmpDistance[0] ) < 30))
                                                {
                                                    flag = true;
                                                    Log.i(TAG,"true");
                                                }
                                                else{
                                                    flag = false;
                                                    Log.i(TAG,"false");
                                                    break;
                                                }
                                                i++;
                                            }
                                            if(flag == true){
                                                avrCalTemp =calFloatSumAverage(tmpCalTemp,pointNum);
                                                avrDistance = calIntFloatSumAverage(tmpDistance,pointNum);
                                                Log.i(TAG,"-----write log ");
                                                LogUtil.i("",buff1.toString());
                                                LogUtil.i(MainActivity.class,String.format("平均距离(mm),%d,平均温度(℃),%.2f",avrDistance,avrCalTemp));
                                            }
                                            else
                                                Log.i(TAG,"-----fail ");
                                            calNum = 0;
                                            buff1 = new StringBuilder("");
                                        }

                                        runCnts++;
                                        if(runCnts%2 == 0) {
                                            //热图显示处理
                                            drawNewMap();
                                            uiHandler.sendEmptyMessage(DISPLAY_HOT_IMAGE_FLAG);
                                        }

                                        //额温计算
                                        foreheadTempCalc(irTempSensor.pixelListBackup);
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

    /**
     * 额温计算
     * @param arrayList
     */
    private void foreheadTempCalc(ArrayList<Integer> arrayList) {
        int[][] pixelValue = new int[32][32];
        int line=0,column=0,sum=0,count=0,validCount=0;
        int[][] lineAverageV = new int[32][2];  //行阈值内有效值平均+数目
        int centerLine = 0,centerColumn=0;      //额头峰值点坐标
        int value = 0;
        StringBuilder averageValue = new StringBuilder("");
        int[] centerPixel = new int[9];
        int index=0;

        if(arrayList.size() < 1024) {
            return;
        }

        //计算每行有效平均温度并统计点数,剔除前五行（可能有错位数据）
        for(int i=0;i<5;i++) {
            lineAverageV[i][0] = 0;
            lineAverageV[i][1] = 0;
            LogUtil.d(MainActivity.class, "第" + line + "行有效点数,"+ 0 + ",平均温度(x10)," + 0);
            averageValue.append(Integer.toString(0));
            averageValue.append(",");
        }
        for(int i=5*32;i<1024;i++) {
            line = i/32;
            column = i%32;
            pixelValue[line][column] = arrayList.get(i) - 2731;
            if(pixelValue[line][column] < 420) {    //剔除热源点
                sum += pixelValue[line][column];
                count++;
                if(pixelValue[line][column] > 280) {
                    validCount++;   //人体阈值内有效温度点数加1
                }
            }
            if(column == 31) {
                if(validCount != 0) {
                    lineAverageV[line][0] = sum / count;    //剔除热源后整体平均值
                    lineAverageV[line][1] = validCount;
                    if (lineAverageV[line][0] > 420) {
                        lineAverageV[line][0] = 0;     //超出人体阈值，置为0
                        lineAverageV[line][1] = 0;
                    }

                    LogUtil.d(MainActivity.class, "第" + line + "行有效点数,"+ validCount + ",平均温度(x10)," + lineAverageV[line][0]);
                } else {
                    lineAverageV[line][0] = sum / count;
                    lineAverageV[line][1] = 0;
                    LogUtil.d(MainActivity.class, "第" + line + "行有效点数,"+ 0 + ",平均温度(x10)," + lineAverageV[line][0]);
                }
                sum = 0;
                count = 0;
                validCount = 0;

                averageValue.append(Integer.toString(lineAverageV[line][0]));
                averageValue.append(",");
            }
        }

        LogUtil.d(TAG_FOREHAND,"从上往下每行平均温度," + averageValue);

        //额温峰值点位置计算
        centerLine = peakPositionCalc(lineAverageV);
        if(centerLine == 5) {   //远离行边界
            centerLine += 1;
        } else if(centerLine == 31) {
            centerLine -= 1;
        }

        for(int i=0;i<32;i++) {
            if(value <= pixelValue[centerLine][i]) {
                value = pixelValue[centerLine][i];
                centerColumn = i;
            }
        }
        if(centerColumn == 0) {   //远离列边界
            centerColumn += 1;
        } else if(centerColumn == 31) {
            centerColumn -= 1;
        }

        LogUtil.i(MainActivity.class,"额温峰值点位置：L=" + centerLine + ",C=" + centerColumn);
        LogUtil.d(TAG_FOREHAND,"额温峰值点行," + centerLine + ",列," + centerColumn);

        //额温计算
        line = centerLine-1;
        index=0;
        for(;line < centerLine+2;line++) {
            for(column = centerColumn-1;column < centerColumn+2;column++) {
                centerPixel[index++] = pixelValue[line][column];
                LogUtil.d(MainActivity.class,"额温计算用坐标," + index + ",line=" + line + ",column=" + column);
            }
        }

        sum = 0;
        for(int i=0;i<9;i++) {
            sum+=centerPixel[i];
        }
        forehandTemp = (float) (sum / 9) /(10 * irTempSensor.DECAY_RATE);

        //界面刷新
        posX = centerLine;
        posY = centerColumn;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String message = "";
                message = "额温峰值坐标：L-" + Integer.toString(posX) + "  C-" + Integer.toString(posY);
                tvPosition.setText(message);

                tvForehandTemp.setText(String.format("%.2f",forehandTemp));
            }
        });
    }

    /**
     * 额头峰值行位置计算
     */
    private int peakPositionCalc (int[][] data) {
        int windowLen = 5;  //滑动窗口长度
        int windowSum = 0;
        int windowPointGate = windowLen*5;  //窗口有效点数门限值
        int windowPointNum = 0;     //窗口有效像素点数目
        int[] pixelSum = new int[32-windowLen+1];
        int[] pointsSum = new int[32-windowLen+1];
        int maxPos = 0;
        int value = 0;
        StringBuilder windowValue = new StringBuilder("");

        LogUtil.d(MainActivity.class,"窗口大小：" + windowLen);
        //计算每个滑动窗口内的和值
        for(int i=0;i<32-windowLen+1;i++) {
            windowSum = 0;
            windowPointNum = 0;
            for(int j=0;j<windowLen;j++) {
                windowSum += data[i+j][0];
                windowPointNum += data[i+j][1];
            }
            pixelSum[i] = windowSum;
            pointsSum[i] = windowPointNum;
            LogUtil.d(MainActivity.class,"第" + i + "个窗口有效点数," + windowPointNum + ",和值(x10)," + windowSum);
            windowValue.append(Integer.toString(windowSum));
            windowValue.append(",");
        }
        LogUtil.d(TAG_FOREHAND,"从上往下窗口和值," + windowValue);

        //查找第一个窗口和值最大的位置
        int i=0;
        for(i=0;i<32-windowLen;i++) {
            if(value <= pixelSum[i]) {
                value = pixelSum[i];
                //判断是否大于后一个窗口值,且有效点数满足要求，是则为第一个窗口峰值
                if(value>pixelSum[i+1] && pointsSum[i]>=windowPointGate) {
                    break;
                }
            }
        }
        maxPos = i + windowLen/2;   //取窗口中间值作为峰值行位置

        LogUtil.i(MainActivity.class,"额头位置(第一个窗口峰值)为第" + maxPos + "行");

        return maxPos;
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
                    y=(float)line/32.0f+0.015625f;      //平移0.5dp显示
                    x=(float)column/32.0f+0.015625f;
                    temp = (float) (irTempSensor.pixelListBackup.get(line*32+column) - 2731)/10.0f;
                    if(temp > 20.0f) {  //高于底溫才顯示
                        HeatMap.DataPoint point = new HeatMap.DataPoint(x, y, temp);
                        map.addData(point);
                    }
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

    private float fx(float x) {
        //return (float)(36.30024 - 0.07277 * x + 7.27922*Math.pow(10,-4)*Math.pow(x,2) - 2.44949*Math.pow(10,-6)*Math.pow(x,3));
        //return (float)(36.10493 + 0.04623 * x - 0.00154*Math.pow(x,2) + 9.5452*Math.pow(10,-6)*Math.pow(x,3));
        // return (float)(36.13076 -0.04221 * x - 4.06591*Math.pow(10,-5)*Math.pow(x,2) + 2.0163*Math.pow(10,-6)*Math.pow(x,3));
        return (float) (37.76075 - 0.03688 * x - 2.3927 * Math.pow(10, -4) * Math.pow(x, 2) + 3.77025 * Math.pow(10, -6) * Math.pow(x, 3));
    }

    private float calFloatSumAverage(float[] data, int num){
        float sum = 0;
        int i = 0;
        for(;i<num;i++)
            sum+= data[i];
        return sum/num;
    }
    private int calIntFloatSumAverage(int[] data, int num){
        int sum = 0;
        int i = 0;
        for(;i<num;i++)
            sum+= data[i];
        return sum/num;
    }
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
