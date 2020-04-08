package com.example.yingbh.serial.sensor;

import android.util.Log;
import com.common.pos.api.util.PosUtil;

import com.example.yingbh.serial.activity.MainActivity;
import com.kongqw.serialportlibrary.Device;
import com.kongqw.serialportlibrary.SerialPortFinder;
import com.kongqw.serialportlibrary.SerialPortManager;
import com.kongqw.serialportlibrary.listener.OnSerialPortDataListener;
import com.minivision.parameter.util.LogUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class IrTempSensor {

    private SerialPortManager mSerialPortManager;
    private static final String TAG = "SerialIrTemp";
    private static final String TAG_FOREHAND = "Forehand";
    private final String CMD_HEAD = "e1";                     //帧头
    private final String CMD_TEMP_GET = "eee10155fffcfdff";   //温度图像数据获取命令
    private final int UNVALID_PIXEL_NUM = 4*32;                 //无效数据点，4行*32=128
    private final int VALID_TEMP_LEN = 2055-UNVALID_PIXEL_NUM*2;//有效数据长度（剔除4行错位数据，4*32*2=256）
    private final int VALID_PIXEL_NUM = 1024 - UNVALID_PIXEL_NUM; //有效像素点数目，剔除4行无效点
    private byte[] CMD_SET_PARAM;

    private byte[] sensorID = new byte[4];               //模组编号
    private boolean sensorMarkFlag = false;              //模组编号标记
    private byte[] sourceData = new byte[4096];          //温度图像数据缓存
    private int iLastEnd = 0;                            //数据缓存区尾地址
    public ArrayList<Integer> pixelList = new ArrayList<>();    //像素点温度
    public ArrayList<Integer> pixelListBackup = new ArrayList<>(1024);    //像素点温度备份
    public StringBuilder pixelMaxValue = new StringBuilder("");
    private ArrayList<Float> envList = new ArrayList<>(30);     //环境温度缓存表

    /**
     * PRD1.0.6：将补偿系数默认值调整为1
     */
    public float DECAY_RATE = 0.954f;               //默认红外衰减率0.96 -> 0.954

    private final int BACK_TEMP = 2731;             //本底温度
    private final int HUMAN_TEMP_MAX = 42;          //人体温度上限
    private final int HUMAN_TEMP_MIN = 32;          //人体温度上限
    private final int ENV_GATE = 32;           //环境温度门限，使用方法：(ENV_TEMP_GATE,HUMAN_TEMP_MAX)区间内像素点数>50，说明有人体热源接近，不记录环境温度

    private int OBJ_TEMP_MAX = BACK_TEMP + (int)(HUMAN_TEMP_MAX*10.0*DECAY_RATE);
    private int OBJ_TEMP_MIN = BACK_TEMP + (int)(HUMAN_TEMP_MIN*10.0*DECAY_RATE);
    private int ENV_TEMP_GATE = BACK_TEMP + (int)(ENV_GATE*10.0*DECAY_RATE);        //实际环温门限
    private final float objErrorTemp = 0.0f;                   //目标基准温度
    public float objTemp = 35.0f;                       //脸温
    public float envTemp = 25.0f;                       //环境温度

    public float forehandTemp = 0.0f;                   //额温
    public int forehandPosX=0,forehandPosY=0;


    private int OBJ_NORMAL_VALID_PIXEL = 10;             //常规场景有效像素点数目

    private int OBJ_COVER_VALID_PIXEL = 10;             //遮挡场景有效的像素点数目
    private float OBJ_COVER_TEMP_COMP = 0.0f;           //有遮挡场景补偿温度
    private float OBJ_NONE_COVER_TEMP_COMP = 0.0f;      //无遮挡场景补偿温度
    private int OBJ_PIXEL_GATE = 150;                   //像素点数目门限值，有遮挡/无遮挡场景区分

    // mq add
    public float ForehandTemp_5point = 0.0f;
    public float ForehandTemp_5point_1 = 0.0f;
    /**
     * 构造函数
     */
    public IrTempSensor() {
//        int[] setParamCMD = {0xee,0xb2,0x55,0xaa,0x00,0x00,0x00,0x00,0xff,0xfc,0xfd,0xff};
//        CMD_SET_PARAM = intArrayToByteArray(setParamCMD, setParamCMD.length);
//
//        byte[] data = floatToByte(DECAY_RATE);
//        for (int i=0;i<4;i++) {
//            CMD_SET_PARAM[i+4] = data[i];
//        }
    }

    /**
     * 设置回调
     */
    private Callback mCallback;
    public IrTempSensor setCallback(Callback callback) {
        this.mCallback = callback;
        return this;
    }

    /**
     * 环境温度计算
     */
    private void envSet(float temp) {
        envTemp = temp;
        Log.i(TAG,"当前环境温度：" + temp + "℃");
    }

    /**
     * 环境温度获取
     */
    public float envGet() {
        return envTemp;
    }

    /**
     * 更新温度相关的一些参数
     */
    public void updateParameters(float temperatureAdjust, float high, float normal, int pixelGate, int coverValidPixel, float tempComp) {

        DECAY_RATE = temperatureAdjust;
        OBJ_TEMP_MAX = BACK_TEMP + (int)(HUMAN_TEMP_MAX*10.0*DECAY_RATE);
        OBJ_TEMP_MIN = BACK_TEMP + (int)(HUMAN_TEMP_MIN*10.0*DECAY_RATE);
        //        OBJ_TEMP_MAX = BACK_TEMP + (int)((high+5)*10.0*DECAY_RATE);
        //        if (normal >= 5) OBJ_TEMP_MIN = BACK_TEMP + (int)((normal-5)*10.0*DECAY_RATE);
        //        else OBJ_TEMP_MIN = BACK_TEMP;
        OBJ_COVER_VALID_PIXEL = coverValidPixel;
        OBJ_PIXEL_GATE = pixelGate;
        OBJ_COVER_TEMP_COMP = tempComp;
        Log.i(TAG, String.format("product设置：更新检测上阈值 %s  检测下阈值 %s  温度校准系数 %s  有/无遮挡像素点门限值 %d  遮挡场景有效像素点数目 %d  遮挡场景补偿温度 %s",
                OBJ_TEMP_MAX, OBJ_TEMP_MIN, DECAY_RATE, OBJ_PIXEL_GATE, OBJ_COVER_VALID_PIXEL, OBJ_COVER_TEMP_COMP));
    }

    /**
     * 功能：初始化红外传感器接口
     */
    public boolean initIrSensor(File device, int baudRate) {
        //串口设备查询
        SerialPortFinder serialPortFinder = new SerialPortFinder();
        ArrayList<Device> devices = serialPortFinder.getDevices();
        for(Device dev: devices){
            Log.i(TAG, dev.getName());
        }
        Log.i(TAG, String.format("初始化红外测温传感器接口 %s  波特率 %s", device.getPath(), baudRate));

        //添加数据通信监听
        mSerialPortManager = new SerialPortManager();
        mSerialPortManager.setOnSerialPortDataListener(new OnSerialPortDataListener(){
            public void onDataReceived(byte[] bytes){
                if(iLastEnd + bytes.length < sourceData.length) {
                    //数据缓存，待解析
                    System.arraycopy(bytes, 0, sourceData, iLastEnd, bytes.length);
                    iLastEnd += bytes.length;

                    if (iLastEnd >= VALID_TEMP_LEN) {
                        //                    Log.i(TAG,"收到长度="+iLastEnd);
                        /**
                         * 一次串口返回可能多次调用onDataReceived方法，是不好判断开始和结束的，需要自行断句，这里是通过iLastEnd来做的
                         * processTemp: 对收到的数据进行处理
                         * calculateObjTemp()：计算出最终温度
                         */
                    }
                } else {
                    Log.w(TAG,"iLastEnd=" + iLastEnd + ", len=" + bytes.length);
                    Log.w(TAG,"缓冲区已满，无法接收新数据，清空缓冲区！");
                    iLastEnd = 0;
                }
            }

            public void onDataSent(byte[] bytes){
                Log.i(TAG,"发送命令："+ byteArrayToHex(bytes,bytes.length) + ",len = " + bytes.length);
                try {
                    Thread.sleep(3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                PosUtil.setRs485Status(0,0);      //RS485接收模式
            }
        });

        //打开串口
        boolean openSerialPort = mSerialPortManager.openSerialPort(device, baudRate);

        if(openSerialPort) {
//            Log.i(TAG, "发送模组参数校准命令：" + byteArrayToHex(CMD_SET_PARAM,CMD_SET_PARAM.length));
//            mSerialPortManager.sendBytes(CMD_SET_PARAM);

            PosUtil.setRs485Status(0,50);      //RS485接收模式
            Log.i(TAG,"RS485接口初始化成功");
            return true;
        } else {
            Log.i(TAG,"RS485接口初始化失败");
//            mCallback.onTemperatureDetectFailure("RS485接口初始化失败");
            return false;
        }
    }


    /**
     * 功能：采集温度图像数据
     */
    public void startDataSample() {
        if(null != mSerialPortManager) {
            PosUtil.setRs485Status(1,0);      //RS485发送模式
            iLastEnd = 0;
            mSerialPortManager.sendBytes(hexToByteArray(CMD_TEMP_GET));
        }
    }

    /**
     * 功能：温度图像数据处理
     */
    //    private void processTemp() {
    //        Log.d(TAG,"温度图像数据: len=" + iLastEnd + "; " + byteArrayToHex(sourceData,100));
    //        int iStart = 0;
    //        int totalDataLen = iLastEnd;
    //        int bufLen = 0;
    //        byte header = hexToByte(CMD_HEAD);
    //
    //        while(totalDataLen >= VALID_TEMP_LEN) {
    //            //匹配帧头
    //            if(sourceData[iStart] == header) {
    //                if((totalDataLen == VALID_TEMP_LEN) && (sensorMarkFlag != true)) {
    //                    //保存模组ID
    //                    sensorID[0] = sourceData[iStart+VALID_TEMP_LEN-4];
    //                    sensorID[1] = sourceData[iStart+VALID_TEMP_LEN-3];
    //                    sensorID[2] = sourceData[iStart+VALID_TEMP_LEN-2];
    //                    sensorID[3] = sourceData[iStart+VALID_TEMP_LEN-1];
    //                    sensorMarkFlag = true;
    //                    Log.i(TAG,"sensor ID :" + byteToHex(sensorID[0]) + " " + byteToHex(sensorID[1])
    //                            + " " + byteToHex(sensorID[2]) + " " + byteToHex(sensorID[3]));
    //                }
    //
    //                //匹配模组ID，剔除错误数据
    //                if((sensorID[0] == sourceData[iStart+VALID_TEMP_LEN-4]) &&
    //                        (sensorID[1] == sourceData[iStart+VALID_TEMP_LEN-3]) &&
    //                        (sensorID[2] == sourceData[iStart+VALID_TEMP_LEN-2]) &&
    //                        (sensorID[3] == sourceData[iStart+VALID_TEMP_LEN-1])){
    //
    //                    pixelList.clear();
    //                    int[] iData = byteArrayToIntArray(sourceData,iLastEnd);
    //                    int pixLen = (totalDataLen<=2048)?(totalDataLen/2):1024;
    //                    int m = 0, n = 0, temp = 0;
    //                    for(int i=0; i<pixLen; i++) {
    //                        m = iStart + i * 2 + 1;
    //                        n = m + 1;
    //                        temp = iData[m]*256+iData[n];
    ////                        pixelList.add((temp>BACK_TEMP)?(temp-BACK_TEMP):0);     //低于本底温度，数据有误，写入0
    ////                        Log.i(TAG,"pixel temp[" + i + "]:" + iData[m] + " " + iData[n] + "-->" + temp);
    //                        pixelList.add(temp);     //存入原始像素点温度
    //                    }
    //                    Log.i(TAG,"update pixel temperature");
    //                    bufLen = VALID_TEMP_LEN;
    //                } else {
    //                    Log.i(TAG,"temperature data error");
    //                    bufLen = 1;
    //                }
    //            } else {
    //                bufLen = 1;
    //            }
    //            totalDataLen -= bufLen;
    //            iStart += bufLen;
    //        }
    //
    //        if(iStart > 0) {
    //            //缓存前移
    //            System.arraycopy(sourceData, iStart, sourceData, 0, totalDataLen);
    //            //刷新缓存存储位置
    //            iLastEnd -= iStart;
    //        }
    //    }

    public boolean processTemp() {
        Log.d(TAG,"温度图像数据: len=" + iLastEnd + "; " + byteArrayToHex(sourceData,100) + "...");
        int iStart = 0;
        int bufLen = 0;

        if(iLastEnd < VALID_TEMP_LEN) {
            Log.i(TAG,"receive too short data!");
            return false;
        }

        //记录模组ID
        if(sensorMarkFlag != true && iLastEnd == 2055) {
            //保存模组ID
            sensorID[0] = sourceData[iLastEnd-4];
            sensorID[1] = sourceData[iLastEnd-3];
            sensorID[2] = sourceData[iLastEnd-2];
            sensorID[3] = sourceData[iLastEnd-1];
            sensorMarkFlag = true;
            Log.i(TAG,"sensor ID :" + byteToHex(sensorID[0]) + " " + byteToHex(sensorID[1])
                    + " " + byteToHex(sensorID[2]) + " " + byteToHex(sensorID[3]));
        }
        if(sensorMarkFlag == true) {
            //匹配模组ID，剔除错误数据
            if ((sensorID[0] == sourceData[iLastEnd - 4]) &&
                    (sensorID[1] == sourceData[iLastEnd - 3]) &&
                    (sensorID[2] == sourceData[iLastEnd - 2]) &&
                    (sensorID[3] == sourceData[iLastEnd - 1])) {

                pixelList.clear();
                pixelListBackup.clear();

                int[] iData = byteArrayToIntArray(sourceData, iLastEnd);
                int pixLen = VALID_PIXEL_NUM;
                iStart = iLastEnd - 6 - VALID_PIXEL_NUM*2 - 1;   //取剩余的VALID_PIXEL_NUM个点
                int m = 0, n = 0, temp = 0;

                //备份数据补齐UNVALID_PIXEL_NUM个
                for(int i = 0; i < UNVALID_PIXEL_NUM; i++) {
                    pixelListBackup.add(2731);
                }

                //像素点温度计算存储
                for (int i = 0; i < pixLen; i++) {
                    m = iStart + i * 2 + 1;
                    n = m + 1;
                    temp = iData[m] * 256 + iData[n];
                    //                Log.i(TAG,"pixel temp[" + i + "]:" + iData[m] + " " + iData[n] + "-->" + temp);
                    pixelList.add(temp);    //存入原始像素点温度
                    pixelListBackup.add(temp);
                }
                Log.i(TAG, "pixelList.size = " + pixelList.size() + ", update pixel temperature!");
            } else {
                Log.i(TAG, "temp data err!");
            }
            iLastEnd = 0;
            return true;
        }
        return false;
    }


    public void calculateObjTemp() {
        int doorStart = 0;  //阈值上限索引值
        int doorEnd = 0;    //阈值下限索引值
        int envGate = 0;
        int temp = 0;
        float envTemp = 25.0f;

        if(pixelList.size() == 0) {
            return;
        }
        Log.i(TAG, String.format("检测上阈值 %s  检测下阈值 %s  温度校准系数 %s", OBJ_TEMP_MAX, OBJ_TEMP_MIN, DECAY_RATE));
        //像素点温度降序排列
        Collections.sort(pixelList,Collections.<Integer>reverseOrder());

        //阈值索引检索
        for(int i=0; i<pixelList.size(); i++) {
            temp = pixelList.get(i);
            if(temp > OBJ_TEMP_MAX) {
                continue;
            } else {
                if(doorStart == 0) {
                    doorStart = i;
                }

                if(temp >= ENV_TEMP_GATE) {
                    envGate ++;
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

        //人体温度计算
        doorStart += 3;
        if(doorStart + OBJ_NORMAL_VALID_PIXEL < pixelList.size()) {
            pixelMaxValue = new StringBuilder("");
            Log.i(TAG, "阈值范围索引：" + doorStart + "~" + doorEnd);
            Log.d(TAG, "阈值内像素点温度极大值:");
            for (int i = doorStart; i < doorStart + OBJ_COVER_VALID_PIXEL; i++) {
                Log.d(TAG, "pixelList[ " + i + "] = " + pixelList.get(i));
                pixelMaxValue.append(pixelList.get(i));
                pixelMaxValue.append(" ");
            }

            // 场景区分，false-无遮挡，true-有遮挡
            boolean scene = (doorEnd - doorStart + 1 >= OBJ_PIXEL_GATE) ? false : true;
            //无遮挡场景，有效像素点数目固定为40，有遮挡为OBJ_PIXEL_GATE
            int areaLen = scene ? OBJ_COVER_VALID_PIXEL : OBJ_NORMAL_VALID_PIXEL;
            int sum = 0;
            for (int index = 0; index < areaLen; index++) {
                sum += pixelList.get(index + doorStart);
            }
            objTemp = (float) (sum / areaLen - BACK_TEMP) / (10 * DECAY_RATE);    //衰减
            if (scene)   //遮挡场景，温度补偿
                objTemp += OBJ_COVER_TEMP_COMP;
            else
                objTemp += 0.0f;


            if (doorEnd >= doorStart + OBJ_COVER_VALID_PIXEL) {
                Log.i(TAG, "是否遮挡：" + scene + "  有效像素点数目：" + areaLen + "  最优人体温度objTemp = " + objTemp);
            } else {
                Log.i(TAG, "温度偏低，低温人体或其它物体温度objTemp = " + objTemp);
            }

            //环境温度计算
            if(envGate - doorStart <= 500) {
                int startIndex = pixelList.size() - 25;
                sum = 0;
                for (; startIndex < pixelList.size() - 5; startIndex++) {
                    sum += pixelList.get(startIndex);
                }
                envTemp = (float) (sum / 20 - BACK_TEMP) / 10;    //衰减
                if(envTemp >= -30.0f && envTemp <= 50.0f) {
                    Log.i(TAG, "瞬时环境温度envTemp = " + envTemp);
                    envList.add(envTemp);
                    if(envList.size() >= 30) {
                        //去除极大值、极小值粗大误差，取中间值取平均
                        Collections.sort(envList,Collections.<Float>reverseOrder());
                        float sumEnv = 0.0f;
                        for(int j=10;j<20;j++) {
                            sumEnv += envList.get(j);
                        }
                        envSet(sumEnv/10.0f);
                        envList.clear();
                    }
                } else {
                    Log.i(TAG,"无效环境温度，异常值为:" + envTemp);
                }
            } else {
                Log.i(TAG,"可能有人接近，暂不测环境温度");
            }
        } else {
            Log.e(TAG, "测量数据有误");
        }
    }


    /**
     * 额温计算
     * @param arrayList
     */
    public float foreheadTempCalc() {
        int[][] pixelValue = new int[32][32];
        int line=0,column=0,sum=0,count=0,validSum=0,validCount=0;
        int[][] lineAverageV = new int[32][2];  //行整体平均+有效点数目
        int[][] lineValidAverageV = new int[32][2];  //行阈值内有效点平均+有效点数目
        int[] maxPos = new int[2];
        int centerLine = 0,centerColumn=0;      //额头峰值点坐标
        int value = 0;
        float temperature = 0.0f;
        StringBuilder averageValue = new StringBuilder("");     //行整体平均值
        StringBuilder validPixels = new StringBuilder("");      //行有效点数
        StringBuilder validAverageValue = new StringBuilder("");//行有效点平均值
        int[] centerPixel = new int[9];
        ArrayList<Integer> validTemps = new ArrayList<Integer>(9);
        int index=0;

        if(pixelListBackup.size() < 1024) {
            Log.e(TAG, "像素点数目不够，测量数据有误");
            return temperature;
        }

        //计算每行有效平均温度并统计点数,剔除前五行（可能有错位数据）
        for(int i=0;i<5;i++) {
            lineAverageV[i][0] = 0;
            lineAverageV[i][1] = 0;
            LogUtil.d(MainActivity.class, "第" + line + "行有效点数,"+ 0 + ",整体平均温度(x10)," + 0);
            averageValue.append(Integer.toString(0));
            averageValue.append(",");

            validAverageValue.append(Integer.toString(0));
            validAverageValue.append(",");

            validPixels.append(Integer.toString(0));
            validPixels.append(",");
        }
        for(int i=5*32;i<1024;i++) {
            line = i/32;
            column = i%32;
            pixelValue[line][column] = pixelListBackup.get(i) - 2731;

            if(pixelValue[line][column] < 420) {    //剔除热源点
                sum += pixelValue[line][column];
                count++;
                if(pixelValue[line][column] > 280) {
                    validSum += pixelValue[line][column];
                    validCount++;   //人体阈值内有效温度点数加1
                }
            } else {
                //剔除高温点，重置为0度
                pixelValue[line][column] = 0;
            }
            if(column == 31) {
                if(validCount != 0) {
                    //整体平均值
                    lineAverageV[line][0] = sum / count;    //剔除热源后整体平均值
                    lineAverageV[line][1] = validCount;
                    if (lineAverageV[line][0] > 420) {
                        lineAverageV[line][0] = 0;     //超出人体阈值，置为0
                        lineAverageV[line][1] = 0;
                    }

                    //阈值内有效点平均值
                    lineValidAverageV[line][0] = validSum/validCount;
                    lineValidAverageV[line][1] = validCount;

                    LogUtil.d(MainActivity.class, "第" + line + "行有效点数,"+ validCount + ",有效点平均温度," + lineValidAverageV[line][0] + ",整体平均温度(x10)," + lineAverageV[line][0]);
                } else {
                    //整体平均值
                    lineAverageV[line][0] = sum / count;
                    lineAverageV[line][1] = 0;
                    LogUtil.d(MainActivity.class, "第" + line + "行有效点数,"+ 0 + ",有效点平均温度," + 0 + ",整体平均温度(x10)," + lineAverageV[line][0]);

                    //阈值内有效点平均值
                    lineValidAverageV[line][0] = 0;
                    lineValidAverageV[line][1] = 0;
                }
                sum = 0;
                count = 0;
                validCount = 0;
                validSum = 0;

                averageValue.append(Integer.toString(lineAverageV[line][0]));
                averageValue.append(",");

                validAverageValue.append(Integer.toString(lineValidAverageV[line][0]));
                validAverageValue.append(",");

                validPixels.append(Integer.toString(lineValidAverageV[line][1]*10));
                validPixels.append(",");
            }
        }

        LogUtil.d(TAG_FOREHAND,"从上往下每行阈值内有效点数目(x10)," + validPixels);
        //        LogUtil.d(TAG_FOREHAND,"从上往下每行阈值内有效点平均温度," + validAverageValue);
        LogUtil.d(TAG_FOREHAND,"从上往下每行去高温整体平均温度," + averageValue);

        //额温峰值点位置计算
        maxPos = peakPositionCalc(lineAverageV);
        centerLine = maxPos[0];
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

        LogUtil.i(MainActivity.class,"额温峰值点位置：L=" + centerLine + ",C=" + centerColumn + ",点温," + pixelValue[centerLine][centerColumn]/(10 * DECAY_RATE));

        //额温计算，取中心点相邻行极大值附近3点
        line = centerLine-1;
        for(;line < centerLine+2;line++) {
            value = 0;
            //找出每行极大值列位置
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

            //取该行极大值左右共3个点
            for(column = centerColumn-1;column < centerColumn+2;column++) {
                validTemps.add(pixelValue[line][column]);
                LogUtil.d(TAG_FOREHAND,"第" + line + "行,第" + column + "列,峰值点温度," + pixelValue[line][column]);
            }
        }
        //像素点温度降序排列
        Collections.sort(validTemps,Collections.<Integer>reverseOrder());
        //剔除6个低值剩下取平均
        sum = 0;
        for(int i=0;i<validTemps.size()-6;i++) {
            sum+=validTemps.get(i);
        }
        temperature = (float) ((float)sum / (float)(validTemps.size()-6)) /(10 * DECAY_RATE);

        LogUtil.d(TAG_FOREHAND,"额温峰值点行," + centerLine + ",列," + centerColumn + ",额温," + temperature);

        forehandPosY = centerLine;
        forehandPosX = centerColumn;

//        //        /*--------------mq add ： 取中心上下左右5点数据值-------------*/
//        int summ = 0;
//        int max_summ = 0;
//        int x=2;
//        int centerLine5 = centerLine;
//        int centerColumn5 = 0;
//        for(;x<30;x++) {
//            summ = pixelValue[centerLine5][x-1] + pixelValue[centerLine5][x] + pixelValue[centerLine5][x+1] + pixelValue[centerLine5-1][x] + pixelValue[centerLine5+1][x];
//            if(summ > max_summ )
//            {
//                max_summ = summ;
//                centerColumn5 = x;
//            }
//        }
//
//        LogUtil.d(TAG_FOREHAND,"5点取值，第" + (centerLine5-1) + "行,第" + centerColumn5 + "列,5点峰值点温度," + pixelValue[centerLine5-1][centerColumn5]);
//        LogUtil.d(TAG_FOREHAND,"5点取值，第" + (centerLine5) + "行,第" + (centerColumn5-1) + "列,5点峰值点温度," + pixelValue[centerLine5][centerColumn5-1]);
//        LogUtil.d(TAG_FOREHAND,"5点取值，第" + (centerLine5) + "行,第" + (centerColumn5) + "列,5点峰值点温度," + pixelValue[centerLine5][centerColumn5]);
//        LogUtil.d(TAG_FOREHAND,"5点取值，第" + (centerLine5) + "行,第" + (centerColumn5+1) + "列,5点峰值点温度," + pixelValue[centerLine5][centerColumn5+1]);
//        LogUtil.d(TAG_FOREHAND,"5点取值，第" + (centerLine5+1) + "行,第" + (centerColumn5) + "列,5点峰值点温度," + pixelValue[centerLine5+1][centerColumn5]);
//        int tempSum = 0;
//        tempSum = pixelValue[centerLine5-1][centerColumn5] + pixelValue[centerLine5][centerColumn5-1] + pixelValue[centerLine5][centerColumn5]+ pixelValue[centerLine5][centerColumn5+1]+ pixelValue[centerLine5+1][centerColumn5];
//        ForehandTemp_5point = (float) ((tempSum / 5.0) /(10.0 * DECAY_RATE));   // mq 修改3 -> 3.0  10->10.0
//        ForehandTemp_5point_1 =  (float)((float)(pixelValue[centerLine5-1][centerColumn5] ) /(10.0 * DECAY_RATE));
//        LogUtil.d(TAG_FOREHAND,"5点取值，额温峰值点行," + centerLine5 + ",列," + centerColumn5 +  ",额温," + ForehandTemp_5point_1);
        return temperature;
    }

    /**
     * 额头峰值行位置计算
     */
    private int[] peakPositionCalc (int[][] data) {
        int windowLen = 3;  //滑动窗口长度
        int windowSum = 0;
        int windowPointGate = windowLen*5;  //窗口有效点数门限值
        int windowPointNum = 0;     //窗口有效像素点数目
        int[] pixelSum = new int[32-windowLen+1];
        int[] pointsSum = new int[32-windowLen+1];
        int[] maxPos = new int[2];      //第1、2个峰值行
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

        //查找第一个窗口和值最大的位置(额头)
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
        maxPos[0] = i + windowLen/2;   //取窗口中间值作为峰值行位置
        LogUtil.i(MainActivity.class,"额头位置(第1个窗口峰值)为第" + maxPos[0] + "行");

        //查找第二个窗口和值最大位置（嘴巴）
        i += 5;     //尝试跳至口腔附近
        if(i<32-windowLen) {
            value = 0;
            for(;i<32-windowLen;i++) {
                if(value <= pixelSum[i]) {
                    value = pixelSum[i];
                    if(value>pixelSum[i+1] && pointsSum[i]>=windowPointGate) {
                        break;
                    }
                }
            }

            maxPos[1] = i + windowLen/2;   //取窗口中间值作为峰值行位置
            LogUtil.i(MainActivity.class,"嘴巴位置(第2个窗口峰值)为第" + maxPos[1] + "行");
        }

        return maxPos;
    }

    /**
     * Hex字符串转byte
     * @param inHex 待转换的Hex字符串
     * @return  转换后的byte
     */
    public static byte hexToByte(String inHex){
        return (byte)Integer.parseInt(inHex,16);
    }

    /**
     * Hex字符串转byte数组
     * @param
     * @return
     */
    public byte[] hexToByteArray(String hex){
        int m = 0, n = 0;
        int byteLen = hex.length() / 2; // 每两个字符描述一个字节
        byte[] ret = new byte[byteLen];
        for (int i = 0; i < byteLen; i++) {
            m = i * 2 + 1;
            n = m + 1;
            int intVal = Integer.decode("0x" + hex.substring(i * 2, m) + hex.substring(m, n));
            ret[i] = Byte.valueOf((byte)intVal);
        }
        return ret;
    }

    /**
     * 字节转十六进制
     * @param b 需要进行转换的byte字节
     * @return  转换后的Hex字符串
     */
    public String byteToHex(byte b){
        String hex = Integer.toHexString(b & 0xFF);
        if(hex.length() < 2){
            hex = "0" + hex;
        }
        return hex;
    }

    /**
     * 字节数组转16进制字符串
     * @param
     * @return  转换后的Hex字符串
     */
    public String byteArrayToHex(byte[] bytes, int len){
        String strHex = "";
        StringBuilder sb = new StringBuilder("");
        for (int n = 0; n < len; n++) {
            strHex = Integer.toHexString(bytes[n] & 0xFF);
            sb.append((strHex.length() == 1) ? "0" + strHex : strHex); // 每个字节由两个字符表示，位数不够，高位补0
        }
        return sb.toString().trim();
    }

    /**
     * 字节数组转int数组
     * @param
     * @return  0x??形式数组
     */
    public static int[] byteArrayToIntArray(byte[] bytes, int len) {
        String strHex = "";
        int[] decodeNums = new int[len];
        for(int n=0; n<len; n++) {
            strHex = Integer.toHexString(bytes[n] & 0xFF);
            decodeNums[n] = Integer.parseInt(strHex,16);
            //            Log.d(TAG,"hex string: " + strHex + " -> decode:" + decodeNums[n]);
        }

        return decodeNums;
    }


    /**
     * int数组转字节数组
     * @param  data-int型数组
     *         len
     * @return  Hex字符串
     */
    public static byte[] intArrayToByteArray(int[] data, int len) {
        byte[] ret = new byte[len];
        for (int i = 0; i < len; i++) {
            ret[i] = Byte.valueOf((byte)data[i]);
        }
        return ret;
    }

    /**
     * 浮点转换为字节数组
     *
     * @param f
     * @return
     */
    public static byte[] floatToByte(float f) {

        // 把float转换为byte[]
        int fbit = Float.floatToIntBits(f);

        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            b[i] = (byte) (fbit >> (24 - i * 8));
        }

        // 翻转数组
        int len = b.length;
        // 建立一个与源数组元素类型相同的数组
        byte[] dest = new byte[len];
        // 为了防止修改源数组，将源数组拷贝一份副本
        System.arraycopy(b, 0, dest, 0, len);
        byte temp;
        // 将顺位第i个与倒数第i个交换
        for (int i = 0; i < len / 2; ++i) {
            temp = dest[i];
            dest[i] = dest[len - i - 1];
            dest[len - i - 1] = temp;
        }

        return dest;

    }

    /**
     * 浮点数组转字节数组
     *
     * @param f
     * @return
     */
    public static byte[] floatToByteArray(float[] f) {
        byte[] data = new byte[4*f.length];

        for(int index=0;index<f.length;index++) {
            byte[] dest = new byte[4];
            byte temp;

            // 把float转换为byte[]
            int fbit = Float.floatToIntBits(f[index]);

            for (int i = 0; i < 4; i++) {
                dest[i] = (byte) (fbit >> (24 - i * 8));
            }

            // 将顺位第i个与倒数第i个交换
            for (int i = 0; i < 4 / 2; ++i) {
                temp = dest[i];
                dest[i] = dest[4 - i - 1];
                dest[4 - i - 1] = temp;
            }

            System.arraycopy(dest, 0, data, index*4, 4);
        }

        return data;

    }

    public interface Callback {
//        //温度获取成功成功
//        void onTemperatureDetectSuccess(float temperature);
//
//        //温度获取成功失败
//        void onTemperatureDetectFailure(String e);
    }
}
