******************************************************************************************
版本号：v1.0.2
时间：2020/3/27
改动：
      1、增加人体热图显示
      2、增加距离温度校准功能


******************************************************************************************
版本号：v1.0.1
时间：2020/3/26
改动：
      1、增加本地日志功能,日志路径
      /mnt/internal_sd/DeviceMonitorLog/com.example.yingbh.serial
      2、合并测距功能模块


*****************************************************************************************
版本号：v1.0
说明  
Step 1. Add the JitPack repository to your build file

Add it in your root build.gradle at the end of repositories:

allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
  
Step 2. Add the dependency

dependencies {
        compile 'com.github.kongqw:AndroidSerialPort:1.0.1'
}
  
初始化测温模块接口
public IrTempSensor irTempSensor = new IrTempSensor();
public boolean initSensor = irTempSensor.initIrSensor(new File("/dev/ttyS1"),115200);
  
单次数据采集
irTempSensor.startDataSample();
  
索引i像素点温度读取
irTempSensor.pixelList.get(i)

  
***************注意*******************  
1、RS485接口对应设备文件"/dev/ttyS1"，波特率115200。  
2、波特率115200下，pad采集完一帧温度图像数据约180ms，为保证调用startDataSample()方法后采集到完整数据，温度计算须延时200ms以上，测温间隔须大于250ms。  
3、目标温度计算方法——caculateObjTemp()    
        a、将1024个像素点温度降序排列  
	b、根据人体温度范围34℃~42℃，检索阈值内索引值；  
	c、剔除掉索引内3个最大值  
	d、取剩下不超过40个像素点温度计算平均值（<20个像素点温度作异常处理）  
4、红外衰减系数——DECAY_RATE  
	须实测确定，厂家给的推荐值为0.965  
