package com.example.yingbh.serial;

import android.util.Log;

import com.common.pos.api.util.PosUtil;

import com.kongqw.serialportlibrary.Device;
import com.kongqw.serialportlibrary.SerialPortFinder;
import com.kongqw.serialportlibrary.SerialPortManager;
import com.kongqw.serialportlibrary.listener.OnSerialPortDataListener;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by yingbh on ${DATA}.
 */
public class IrTempSensor {
    private static final String TAG = "SerialIrTemp";
    private SerialPortManager mSerialPortManager;
    private String CMD_HEAD = "e1";                     //帧头
    private String CMD_TEMP_GET = "eee10155fffcfdff";   //温度图像数据获取命令
    private int VALID_TEMP_LEN = 2055;                   //正确数据帧长度
    private byte[] sensorID = new byte[4];               //模组编号
    private boolean sensorMarkFlag = false;              //模组编号标记

    public byte[] sourceData = new byte[4096];          //温度图像数据缓存
    public int iLastEnd = 0;                            //数据缓存区尾地址
    public ArrayList<Integer> pixelList = new ArrayList<>(1024);    //像素点温度


    public IrTempSensor() {

    }

    /**
     *功能：初始化红外传感器接口
     * @param none
     * @return
     */
    public boolean initIrSensor(File device, int baudRate) {
        //串口设备查询
        SerialPortFinder serialPortFinder = new SerialPortFinder();
        ArrayList<Device> devices = serialPortFinder.getDevices();
        for(Device dev: devices){
            Log.i(TAG,dev.getName());
        }

        Log.i(TAG, "initIrSensor: " + String.format("初始化红外测温传感器接口 %s  波特率 %s", device.getPath(), baudRate));

        //添加数据通信监听
        mSerialPortManager = new SerialPortManager();
        mSerialPortManager.setOnSerialPortDataListener(new OnSerialPortDataListener(){
            public void onDataReceived(byte[] bytes){
                //数据缓存，待解析
                System.arraycopy(bytes, 0, sourceData, iLastEnd, bytes.length);
                iLastEnd += bytes.length;

//                Log.i(TAG,"收到数据长度 = " + bytes.length + ", iLastEnd = " + iLastEnd);
//                String str = byteArrayToHex(bytes);
//                Log.d(TAG,"收到数据: " + str);

                if(iLastEnd >= VALID_TEMP_LEN) {
                    processTemp();
                }
            }

            public void onDataSent(byte[] bytes){
                Log.i(TAG,"发送命令："+ byteArrayToHex(bytes) + ",len = " + bytes.length);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Log.i(TAG,"fail to delay 10ms");
                }
                PosUtil.setRs485Status(0);      //RS485接收模式
            }
        });

        //打开串口
        boolean openSerialPort = mSerialPortManager.openSerialPort(device,baudRate);
        if(openSerialPort) {
            PosUtil.setRs485Status(0);      //RS485接收模式
            Log.i(TAG,"RS485接口初始化成功");
            return true;
        } else {
            Log.i(TAG,"RS485接口初始化失败");
            return false;
        }
    }

    /**
     * 功能：关闭红外测温传感器接口
     * @param
     * @return
     */
    public void closeIrSensor() {
        mSerialPortManager.closeSerialPort();
    }

    /**
     *功能：采集温度图像数据
     * @param none
     * @return
     */
    public void startDataSample() {
        if(null != mSerialPortManager) {
            PosUtil.setRs485Status(1);      //RS485发送模式
            mSerialPortManager.sendBytes(hexToByteArray(CMD_TEMP_GET));
        }
    }

    /**
     *功能：温度图像数据处理
     * @param none
     * @return
     */
    public void processTemp() {
        Log.d(TAG,"温度图像数据: len=" + iLastEnd + "; " + byteArrayToHex(sourceData));
        int iStart = 0;
        int totalDataLen = iLastEnd;
        int bufLen = 0;
        byte header = hexToByte(CMD_HEAD);

        while(totalDataLen >= VALID_TEMP_LEN) {
            //匹配帧头
            if(sourceData[iStart] == header) {
//                Log.i(TAG,"iStart = " + iStart + ", totalDataLen = " + totalDataLen);
                if((totalDataLen == VALID_TEMP_LEN) && (sensorMarkFlag != true)) {
                    //保存模组ID
                    sensorID[0] = sourceData[iStart+VALID_TEMP_LEN-4];
                    sensorID[1] = sourceData[iStart+VALID_TEMP_LEN-3];
                    sensorID[2] = sourceData[iStart+VALID_TEMP_LEN-2];
                    sensorID[3] = sourceData[iStart+VALID_TEMP_LEN-1];
                    sensorMarkFlag = true;
                    Log.i(TAG,"sensor ID :" + byteToHex(sensorID[0]) + " " + byteToHex(sensorID[1])
                            + " " + byteToHex(sensorID[2]) + " " + byteToHex(sensorID[3]));
                }

                //匹配模组ID，剔除错误数据
                if((sensorID[0] == sourceData[iStart+VALID_TEMP_LEN-4]) &&
                    (sensorID[1] == sourceData[iStart+VALID_TEMP_LEN-3]) &&
                    (sensorID[2] == sourceData[iStart+VALID_TEMP_LEN-2]) &&
                    (sensorID[3] == sourceData[iStart+VALID_TEMP_LEN-1])){

                    pixelList.clear();
                    int[] iData = hexArrayToIntArray(sourceData,iLastEnd);
                    int pixLen = (totalDataLen<=2048)?(totalDataLen/2):1024;
                    int m = 0, n = 0, temp = 0;
                    for(int i=0;i<pixLen;i++) {
                        m = iStart + i * 2 + 1;
                        n = m + 1;
                        temp = iData[m]*256+iData[n];
//                        Log.i(TAG,"pixel temp[" + i + "]:" + iData[m] + " " + iData[n] + "-->" + temp);
                        pixelList.add(temp);    //存入原始像素点温度
                    }
//                    Log.i(TAG,"pixelList.size = " + pixelList.size() + ", update pixel temperature!");
                    bufLen = VALID_TEMP_LEN;
                } else {
                    Log.i(TAG,"temp data error!");
                    bufLen = 1;
                }
            } else {
                bufLen = 1;
            }
            totalDataLen -= bufLen;
            iStart += bufLen;
        }

        if(iStart > 0) {
            //缓存前移
            System.arraycopy(sourceData, iStart, sourceData, 0, totalDataLen);
            //刷新缓存存储位置
            iLastEnd -= iStart;

//            Log.i(TAG,"iStart = " + iStart + ", iLastEnd = " + iLastEnd);
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
