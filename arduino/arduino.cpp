#include <SoftwareSerial.h>
int R[] = {2, 7, A5, 5, 13, A4, 12, A2}; //行
int C[] = {6, 15, 14, 3, A3, 4, 8, 9}; //列
SoftwareSerial softSerial(10, 11);
unsigned char right[8][8] =       //大“心型”的数据
{
  0, 0, 0, 0, 0, 0, 0, 0,
  0, 0, 0, 0, 0, 0, 0, 1,
  0, 0, 0, 0, 0, 0, 1, 0,
  0, 0, 0, 0, 0, 1, 0, 0,
  1, 0, 0, 0, 1, 0, 0, 0,
  0, 1, 0, 1, 0, 0, 0, 0,
  0, 0, 1, 0, 0, 0, 0, 0,
  0, 0, 0, 0, 0, 0, 0, 0,
};

unsigned char wrong[8][8] =      //小“心型”的数据
{ 1, 0, 0, 0, 0, 0, 0, 1,
  0, 1, 0, 0, 0, 0, 1, 0,
  0, 0, 1, 0, 0, 1, 0, 0,
  0, 0, 0, 1, 1, 0, 0, 0,
  0, 0, 0, 1, 1, 0, 0, 0,
  0, 0, 1, 0, 0, 1, 0, 0,
  0, 1, 0, 0, 0, 0, 1, 0,
  1, 0, 0, 0, 0, 0, 0, 1,
};

void setup()
{

  Serial.begin(9600); //设定硬串口波特率
  softSerial.begin(9600); //设定软串口波特率
  //循环定义行列PIN 为输出模式
  for (int i = 0; i < 8; i++)
  {
    pinMode(R[i], OUTPUT);
    pinMode(C[i], OUTPUT);
  }
}

void loop()
{
  //  for (int i = 0 ; i < 100 ; i++)       //循环显示100次
  //  {
  //    Display(right);                   //显示大”心形“
  //  }
  //  for (int i = 0 ; i < 50 ; i++)        //循环显示50次
  //  {
  //    Display(wrong);                 //显示小“心形”
  //  }
while(1){
  if (softSerial.available()) { //如果HC-06发来数据
    int k = softSerial.read(); //读取1个字节的数据
    Serial.println(k); //通过硬串口打印输出
    if (k == 49)
      for (int i = 0 ; i < 100 ; i++)       //循环显示100次
      {
        Display(right);                   //显示大”心形“
      }
    else

      for (int i = 0 ; i < 100 ; i++)       //循环显示100次
      {
        Display(wrong);                 //显示大”心形“
      }
  }
}
}

void Display(unsigned char dat[8][8])   //显示函数
{
   
  for (int c = 0; c < 8; c++)
  {
    digitalWrite(C[c], LOW); //选通第c列

    //循环
    for (int r = 0; r < 8; r++)
    {
      digitalWrite(R[r], dat[r][c]);
    }
    delay(1);
    Clear();  //清空显示去除余晖
  }
}

void Clear()                          //清空显示
{
  for (int i = 0; i < 8; i++)
  {
    digitalWrite(R[i], LOW);
    digitalWrite(C[i], HIGH);
  }
}
