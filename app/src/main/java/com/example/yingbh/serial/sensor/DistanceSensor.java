package com.example.yingbh.serial.sensor;

import android.util.Log;

import com.kongqw.serialportlibrary.Device;
import com.kongqw.serialportlibrary.SerialPortFinder;
import com.kongqw.serialportlibrary.SerialPortManager;
import com.kongqw.serialportlibrary.listener.OnSerialPortDataListener;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by yingbh on ${DATA}.
 */
public class DistanceSensor {
    private static final String TAG = "SerialDistance";
    private SerialPortManager mSerialPortManager;

    /* 输出模式设置指令*/
    private String CMD_CONTINOUS_MODE = "a545ea";   // 连续输出距离数据
    private String CMD_READ_MODE = "a515ba";         // 查询输出距离数据
    /*保存配置指令*/
    private String CMD_SAVE_CONFIG = "a525ca";         // 保存当前配置;包括波特率、测量模式、输出模式设置
    /*测量模式设置指令*/
    private String CMD_LONG_MEAS = "a550f5";     // 长距离测量模式
    private String CMD_FAST_MEAS = "a551f6";    // -快速测量模式
    private String CMD_HIGH_MEAS = "a552f7";     // -高精度测量模式（默认）
    private String CMD_NORMAL_MEAS = "a553f8";   // -一般测量模式
    /*波特率配置*/
    private String CMD_BAND_9600 = "a5ae53";    // 9600（默认）
    private String CMD_BAND_115200 = "a5af54";  // 115200

    public enum MeasMode{longMeas, quickMeas, highMeas, normalMeas};   // 测量模式
    public String[] MeasModeString={"长距离模式","快速模式","高精度模式","普通模式"};
    public enum OutputMode{continousMode, readMode};                            // 输出模式

    private char[] head = {0x5a,0x5a};               // 报文帧头
    private char   revType = 0x15;                  // 接收报文数据类型

    private static final int  sourceDataLen = 4095;
    private byte[] sourceData = new byte[sourceDataLen];       //距离数据缓存

    private int iLastEnd = 0;                        //数据缓存区尾地址

    private int MIN_VAILD_LEN = 8;                   //最小接收帧长度
    private int MAX_VAILD_LEN = 13;                   //最大接收帧长度

    private boolean sensorDistanceGetFlag = false;      // 传感器距离获取标志位
    private int sensorDistance = 0;                      // 传感器距离
    private byte measureMode = 2;                    // 模块测量模式  （默认2：高精度测量）

    public DistanceSensor() {

    }
    // 判断距离是否获取到
    public boolean isSensorDistanceGet(){
        return sensorDistanceGetFlag;
    }

    public int SensorDistanceValue(){
        return sensorDistance;
    }

    /**
     *功能：初始化串口（USB转TTL）
     * @param none
     * @return
     */
    public boolean initDistatnceSensor(File device, int baudRate) {
        //串口设备查询
        SerialPortFinder serialPortFinder = new SerialPortFinder();
        ArrayList<Device> devices = serialPortFinder.getDevices();
        for(Device dev: devices){
            Log.i(TAG,dev.getName());
        }

        Log.i(TAG, "initDistanceSensor: " + String.format("初始化距离传感器串口 %s  波特率 %s", device.getPath(), baudRate));

        //添加数据通信监听
        mSerialPortManager = new SerialPortManager();
        mSerialPortManager.setOnSerialPortDataListener(new OnSerialPortDataListener(){
            public void onDataReceived(byte[] bytes){
                //if(bytes.length != 0)
                {
                    //数据缓存，待解析
                    System.arraycopy(bytes, 0, sourceData, iLastEnd, bytes.length);
                    iLastEnd += bytes.length;

                  //  Log.i(TAG, "收到数据长度 = " + bytes.length + ", iLastEnd = " + iLastEnd);
                  //  String str = byteArrayToHex(bytes);
                  //  Log.d(TAG, "收到数据: " + str);
                   processDistance();
                }
            }

            public void onDataSent(byte[] bytes){
                Log.i(TAG,"发送命令："+ byteArrayToHex(bytes) + ",len = " + bytes.length);
//                PosUtil.setRs485Status(0,2);
            }
        });

        //打开串口
        boolean openSerialPort = mSerialPortManager.openSerialPort(device,baudRate);
        if(openSerialPort) {
//            PosUtil.setRs485Status(0,0);      //RS485接收模式
            Log.i(TAG,"串口初始化成功");
            //return true;
       } else {
           Log.i(TAG,"串口初始化失败");
            return false;
        }
        // 设置高精度模式
       // mSerialPortManager.sendBytes(hexToByteArray(CMD_HIGH_MEAS));
        // 配置位查询模式
        mSerialPortManager.sendBytes(hexToByteArray(CMD_READ_MODE));

        return true;
    }

    /**
     * 功能：关闭距离传感器接口
     * @param
     * @return
     */
    public void closeIrSensor() {
        mSerialPortManager.closeSerialPort();
    }

    /**
     *功能：采集距离数据
     * @param none
     * @return
     */
    public void startDataSample() {
        if(null != mSerialPortManager) {
            iLastEnd = 0;
            sensorDistanceGetFlag = false;
            mSerialPortManager.sendBytes(hexToByteArray(CMD_READ_MODE));
        }
    }
    /*
        距离报文处理
     */
    public boolean processDistance() {
        Log.d(TAG, "接收距离报文数据: iLastEnd=" + iLastEnd + "  data: " + byteArrayToHex(sourceData));
        if ((iLastEnd < MIN_VAILD_LEN)) {
            Log.i(TAG, "receive too short data!");
            return false;
        }
        /* 搜索帧头 */
        int iStart = 0;
        for (; iStart < iLastEnd; iStart++) {
            if ((sourceData[iStart] == head[0]) && (sourceData[iStart + 1] == head[1])) {
                break;
            } else {
                iStart++;
            }
        }
        Log.e(TAG, "istart = " + iStart);
        if (iStart < iLastEnd) {
            if (sourceData[iStart + 2] == revType) // 帧类型
            {
                int tmpDataLen = sourceData[iStart + 3];   // 数据量

                if( (iStart + tmpDataLen + 4) > iLastEnd)   // 数据未接收完全
                    return false;

                byte sum = 0;
                for (int i = 0; i < tmpDataLen + 4; i++)
                    sum += sourceData[i + iStart];

                if (sum == sourceData[iStart + tmpDataLen + 4]) {        // 累加和
                    measureMode = sourceData[iStart + 6];             // 测量模式
                    sensorDistance = (sourceData[iStart + 4] & 0xff) * 256 + (sourceData[iStart + 5] & 0xff); // 距离
                    Log.i(TAG,"模式：" + MeasModeString[measureMode]);
                    Log.i(TAG, "距离:" + Integer.toString(sensorDistance) + "(0x" + Integer.toHexString(sensorDistance) + ")" );

                    sensorDistanceGetFlag = true;
                    sourceData = new byte[sourceDataLen];   // 清空数据
                    iLastEnd = 0;
                    return true;
                } else {
                    Log.e(TAG, "校验和错误!,检验和：" + iStart + " " + byteToHex(sum) + "  " + byteToHex(sourceData[iStart + tmpDataLen + 4]));
                }
            } else {
                Log.e(TAG, "帧数据类型不匹配! : type" + sourceData[iStart + 2]);
            }
        }
        else {
            Log.e(TAG, "帧头标志不匹配!");
        }

        sourceData = new byte[sourceDataLen];   // 清空数据缓存
        iLastEnd = 0;
        return false;
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
    public String byteArrayToHex(byte[] bytes){
        String strHex = "";
        StringBuilder sb = new StringBuilder("");
        for (int n = 0; n < bytes.length; n++) {
            strHex = Integer.toHexString(bytes[n] & 0xFF);
            sb.append((strHex.length() == 1) ? "0" + strHex : strHex); // 每个字节由两个字符表示，位数不够，高位补0
        }
        return sb.toString().trim();
    }

    /**
     * 字节数组转十进制数组
     * @param b 需要进行转换的byte字节
     * @return  转换后的Hex字符串
     */
    public int[] hexArrayToIntArray(byte[] bytes, int len) {
        String strHex = "";
        int[] decodeNums = new int[len];
        for(int n=0; n<len; n++) {
            strHex = Integer.toHexString(bytes[n] & 0xFF);
            decodeNums[n] = Integer.parseInt(strHex,16);
        }

        return decodeNums;
    }



}
