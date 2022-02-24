/*
 * Tuner Things Ethanol Content Analyzer
 * Version 0.1 Beta
 * 
 * This program was developed for the Seeeduino Nano with a Grove - OLED Display 0.96" (SSD1315) 
 * but will work for many different Arduino devices with a little modification. Tuner Things ECA was developed thanks
 * to many developers and pieces of source code from around the web. The majority of the code can be found on various forums
 * from various developers however I've developed a good bit of the code relating to the bluetooth connectivity because the 
 * other options for BT ethanol analyzers on the Google Play Store were seriously outdated and did not work with my own personal
 * device. Major thanks to all of the various posters on the automotive forums that I spent HOURS scouring for help.
 */

 
#include <Arduino.h>
#include <U8g2lib.h>
#include <SoftwareSerial.h>

#ifdef U8X8_HAVE_HW_SPI
#include <SPI.h>
#endif
#ifdef U8X8_HAVE_HW_I2C
#include <Wire.h>
#endif

// Initializes the u8g2 screen
U8G2_SSD1306_128X64_NONAME_F_SW_I2C u8g2(U8G2_R0, /* clock=*/ SCL, /* data=*/ SDA, /* reset=*/ U8X8_PIN_NONE); 

// Initializes HC-06 Bluetooth module and links to pins 2-Tx and 3-Rx
SoftwareSerial hc06(2,3);


int inpPin = 8;     //define input pin to 8
int outPin = 11;    //define PWM output, possible pins with u8g2 and 32khz freq. are 3 and 11 (Nano and Uno)

//Define global variables
volatile uint16_t revTick;    //Ticks per revolution
uint16_t pwm_output  = 0;     //integer for storing PWM value (0-255 value)
int HZ;                       //unsigned 16bit integer for storing HZ input
int ethanol = 0;              //Store ethanol percentage here
float expectedv;              //store expected voltage here - range for typical GM sensors is usually 0.5-4.5v
int duty;                     //Duty cycle (0.0-100.0)
float period;                 //Store period time here (eg.0.0025 s)
float temperature = 0;        //Store fuel temperature here
int fahr = 0;
int cels = 0;
static long highTime = 0;
static long lowTime = 0;
static long tempPulse;

void setupTimer()   // setup timer1
{           
  TCCR1A = 0;      // normal mode
  TCCR1B = 132;    // (10000100) Falling edge trigger, Timer = CPU Clock/256, noise cancellation on
  TCCR1C = 0;      // normal mode
  TIMSK1 = 33;     // (00100001) Input capture and overflow interupts enabled
  TCNT1 = 0;       // start from 0
}

ISR(TIMER1_CAPT_vect)    // PULSE DETECTED!  (interrupt automatically triggered, not called by main program)
{
  revTick = ICR1;      // save duration of last revolution
  TCNT1 = 0;       // restart timer for next revolution
}

ISR(TIMER1_OVF_vect)    // counter overflow/timeout
{ revTick = 0; }        // Ticks per second = 0

void setup()
{
  // Begins both BT and Serial output transmissions - 9600 Baud
  hc06.begin(9600);
  Serial.begin(9600);
  pinMode(inpPin,INPUT);
  
  setPwmFrequency(outPin,1); //Modify frequency on PWM output
  setupTimer();
  
  // Initialize Screen and set smaller font for logo
  u8g2.begin();
  u8g2.setFont(u8g2_font_9x18B_tf );
  u8g2.setCursor(20,30);
  u8g2.print("TUNER THINGS");
  u8g2.setCursor(30,50);
  u8g2.print("Ethanol");
  u8g2.setCursor(50,60);
  u8g2.print("Analyzer");
  u8g2.sendBuffer();
  delay(400);
  
  

}
 
void loop(){  
  // Clears Memory
  u8g2.clearBuffer();    
  // Sets Font               
  u8g2.setFont(u8g2_font_profont22_tf);

  getfueltemp(inpPin); //read fuel temp from input duty cycle
  
  if (revTick > 0) // Avoid dividing by zero, sample in the HZ
    {HZ = 62200 / revTick;}     // 3456000ticks per minute, 57600 per second 
    else                     
    {HZ = 0;}                   //needs real sensor test to determine correct tickrate

  //calculate ethanol percentage
    if (HZ > 50) // Avoid dividing by zero
    {ethanol = (HZ-50);}
    else
    {ethanol = 0;}

  if (ethanol > 99) // Avoid overflow in PWM
    {ethanol = 99;}

  expectedv = ((((HZ-50.0)*0.01)*4)+0.5);
  
  //Screen calculations
  pwm_output = 1.1 * (255 * (expectedv/5.0)); //calculate output PWM for ECU
  
  // Sends ETOH percentage and temp to HC-06
  hc06.print(ethanol);
  hc06.print(temperature);
  
  // Sets cursors and outputs for ETOH %, PWM Output Signal voltage, and Temperature in F
  u8g2.setCursor(0, 20);
  u8g2.print("Eth:     %");
  u8g2.setCursor(70, 20);
  u8g2.print(ethanol);
  u8g2.setCursor(0, 40);
  u8g2.print("         V");
  u8g2.setCursor(40, 40);
  u8g2.print(pwm_output);
  u8g2.setCursor(0,60);
  u8g2.print("         F");  
  u8g2.setCursor(50,60); 

  // Calculates F from C
  int newTemp = ((temperature * 1.8)+32);
  u8g2.print(newTemp);
  u8g2.print(char(176)); 

  //PWM output
  analogWrite(outPin, pwm_output); //write the PWM value to output pin
  u8g2.sendBuffer();
  delay(100);  //make screen more easily readable by not updating it too often
  
}

// Read fuel temp from input duty cycle
void getfueltemp(int inpPin){
  highTime = 0;
  lowTime = 0;

  tempPulse = pulseIn(inpPin,HIGH);
  if(tempPulse>highTime){
    highTime = tempPulse;
  }

  tempPulse = pulseIn(inpPin,LOW);
  if(tempPulse>lowTime){
    lowTime = tempPulse;
  }
  
// Calculate duty cycle (integer extra decimal)
duty = ((100*(highTime/(double (lowTime+highTime)))));

// Calculate total period time
float T = (float(1.0/float(HZ)));  

// Calculate the active period time (100-duty)*T
float period = float(100-duty)*T;

// Convert ms to whole number             
float temp2 = float(10) * float(period);

//Calculate temperature for display (1ms = -40, 5ms = 80)
temperature = ((40.25 * temp2)-81.25);        
int cels = int(temperature);
cels = cels*0.1;
float fahrtemp = ((temperature*1.8)+32);
fahr = fahrtemp*0.1;

}

// This code snippet raises the timers linked to the PWM outputs
// This way the PWM frequency can be raised or lowered. Prescaler of 1 sets PWM output to 32KHz (pin 3, 11)
void setPwmFrequency(int pin, int divisor) { 
  byte mode;                                 
  if(pin == 5 || pin == 6 || pin == 9 || pin == 10) {
    switch(divisor) {
      case 1: mode = 0x01; break;
      case 8: mode = 0x02; break;
      case 64: mode = 0x03; break;
      case 256: mode = 0x04; break;
      case 1024: mode = 0x05; break;
      default: return;
    }
    if(pin == 5 || pin == 6) {
      TCCR0B = TCCR0B & 0b11111000 | mode;
    } else {
      TCCR1B = TCCR1B & 0b11111000 | mode;
    }
  } else if(pin == 3 || pin == 11) {
    switch(divisor) {
      case 1: mode = 0x01; break;
      case 8: mode = 0x02; break;
      case 32: mode = 0x03; break;
      case 64: mode = 0x04; break;
      case 128: mode = 0x05; break;
      case 256: mode = 0x06; break;
      case 1024: mode = 0x7; break;
      default: return;
    }
    TCCR2B = TCCR2B & 0b11111000 | mode;
  }
}
