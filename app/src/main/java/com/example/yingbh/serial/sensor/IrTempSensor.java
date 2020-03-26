package com.example.yingbh.serial.sensor;

import android.util.Log;
import com.common.pos.api.util.PosUtil;

import com.kongqw.serialportlibrary.Device;
import com.kongqw.serialportlibrary.SerialPortFinder;
import com.kongqw.serialportlibrary.SerialPortManager;
import com.kongqw.serialportlibrary.listener.OnSerialPortDataListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class IrTempSensor {

    private SerialPortManager mSerialPortManager;
    private final String TAG = "SerialIrTemp";
    private final String CMD_HEAD = "e1";                     //帧头
    private final String CMD_TEMP_GET = "eee10155fffcfdff";   //温度图像数据获取命令
    private final int VALID_TEMP_LEN = 2020;                  //正确数据帧长度，必须大于2000，推荐2030以上，别乱改！！
    private byte[] CMD_SET_PARAM;

    private byte[] sensorID = new byte[4];               //模组编号
    private boolean sensorMarkFlag = false;              //模组编号标记
    private byte[] sourceData = new byte[4096];          //温度图像数据缓存
    private int iLastEnd = 0;                            //数据缓存区尾地址
    private ArrayList<Integer> pixelList = new ArrayList<>();    //像素点温度
    public StringBuilder pixelMaxValue = new StringBuilder("");

    /**
     * PRD1.0.6：将补偿系数默认值调整为1
     */
    private float DECAY_RATE = 1.0f;               //默认红外衰减率0.96
    private final int BACK_TEMP = 2731;             //本底温度
    private final int HUMAN_TEMP_MAX = 42;          //人体温度上限
    private final int HUMAN_TEMP_MIN = 32;          //人体温度上限

    private int OBJ_TEMP_MAX = BACK_TEMP + (int)(HUMAN_TEMP_MAX*10.0*DECAY_RATE);
    private int OBJ_TEMP_MIN = BACK_TEMP + (int)(HUMAN_TEMP_MIN*10.0*DECAY_RATE);
    private final float objErrorTemp = 0.0f;                   //目标基准温度
    public float objTemp = 35.0f;                       //目标温度
    public float envTemp = 25.0f;                       //环境温度
    private int OBJ_COVER_VALID_PIXEL = 20;             //遮挡场景有效的像素点数目
    private float OBJ_COVER_TEMP_COMP = 0.0f;           //有遮挡场景补偿温度
    private float OBJ_NONE_COVER_TEMP_COMP = 0.0f;      //无遮挡场景补偿温度
    private int OBJ_PIXEL_GATE = 150;                   //像素点数目门限值，有遮挡/无遮挡场景区分
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
                //数据缓存，待解析
                System.arraycopy(bytes, 0, sourceData, iLastEnd, bytes.length);
                iLastEnd += bytes.length;

                if(iLastEnd >= VALID_TEMP_LEN) {
                    //                    Log.i(TAG,"收到长度="+iLastEnd);
                    /**
                     * 一次串口返回可能多次调用onDataReceived方法，是不好判断开始和结束的，需要自行断句，这里是通过iLastEnd来做的
                     * processTemp: 对收到的数据进行处理
                     * calculateObjTemp()：计算出最终温度
                     */
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
                int[] iData = byteArrayToIntArray(sourceData, iLastEnd);
                int pixLen = 1000;
                iStart = iLastEnd - 6 - 2000 - 1;   //取剩余的1000个点
                int m = 0, n = 0, temp = 0;
                for (int i = 0; i < pixLen; i++) {
                    m = iStart + i * 2 + 1;
                    n = m + 1;
                    temp = iData[m] * 256 + iData[n];
                    //                Log.i(TAG,"pixel temp[" + i + "]:" + iData[m] + " " + iData[n] + "-->" + temp);
                    pixelList.add(temp);    //存入原始像素点温度
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
        int temp = 0;

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
        if(doorStart + 40 < pixelList.size()) {
            pixelMaxValue = new StringBuilder("");
            Log.i(TAG, "阈值范围索引：" + doorStart + "~" + doorEnd);
            Log.i(TAG, "阈值内像素点温度极大值:");
            for (int i = doorStart; i < doorStart + OBJ_COVER_VALID_PIXEL; i++) {
                Log.i(TAG, "pixelList[ " + i + "] = " + pixelList.get(i));
                pixelMaxValue.append(pixelList.get(i));
                pixelMaxValue.append(" ");
            }

            // 场景区分，false-无遮挡，true-有遮挡
            boolean scene = (doorEnd - doorStart + 1 >= OBJ_PIXEL_GATE) ? false : true;
            //无遮挡场景，有效像素点数目固定为40，有遮挡为OBJ_PIXEL_GATE
            int areaLen = scene ? OBJ_COVER_VALID_PIXEL : 40;
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
            int startIndex = pixelList.size() - 25;
            sum = 0;
            for (; startIndex < pixelList.size() - 5; startIndex++) {
                sum += pixelList.get(startIndex);
            }
            envTemp = (float) (sum / 20 - BACK_TEMP) / 10;    //衰减
            Log.i(TAG, "环境温度envTemp = " + envTemp);
        } else {
            Log.e(TAG, "测量数据有误");
        }
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
