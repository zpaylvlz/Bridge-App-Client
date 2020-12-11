 package com.example.senrsorandr8;


 import android.annotation.SuppressLint;
 import android.app.Activity;
 import android.content.Intent;
 import android.hardware.Sensor;
 import android.icu.text.CaseMap;
 import android.net.Uri;
 import android.os.Bundle;
 import android.os.Environment;
 import android.os.FileUtils;
 import android.provider.DocumentsContract;
 import android.provider.Settings;
 import android.util.Log;
 import android.view.View;
 import android.widget.Button;
 import android.widget.EditText;
 import android.widget.TextView;

 import androidx.annotation.Nullable;
 import androidx.appcompat.app.AppCompatActivity;

 import org.json.JSONObject;

 import java.io.BufferedReader;
 import java.io.File;
 import java.io.FileInputStream;
 import java.io.FileNotFoundException;
 import java.io.FileWriter;
 import java.io.IOException;
 import java.io.InputStream;
 import java.io.InputStreamReader;
 import java.io.PrintWriter;
 import java.net.Socket;
 import java.net.URI;
 import java.net.URISyntaxException;
 import java.sql.Timestamp;
 import java.util.ArrayList;
 import java.util.Calendar;
 import java.util.Date;
 import java.util.Iterator;
 import java.util.List;

 @SuppressLint("SetTextI18n")
 public class MainActivity extends AppCompatActivity {
     Thread TConnect = null;
     EditText etIP;
     TextView tvMessages;
     EditText etFolder;
     Button btnSend;
     Button btnFile;
     String SERVER_IP;
     int SERVER_PORT = 8000;
     List<String> fps = new ArrayList<String>();// To store selected filepath
     List<String> Mac = new ArrayList<String>();// To store each mac address of sensor
     @Override
     protected void onCreate(Bundle savedInstanceState) {
         super.onCreate(savedInstanceState);
         setContentView(R.layout.activity_main);
         etIP = findViewById(R.id.etIP);
         tvMessages = findViewById(R.id.tvMessages);
         etFolder = findViewById(R.id.etFolder);
         btnSend = findViewById(R.id.btnSend);
         btnFile = findViewById(R.id.btnFile);

         Button btnConnect = findViewById(R.id.btnConnect);
         btnConnect.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                 tvMessages.setText("");
                 SERVER_IP = etIP.getText().toString().trim();
                 TConnect = new Thread(new ThreadConnect());
                 TConnect.start();
             }
             //Connect to server according to IP Address
         });
         btnSend.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                 try {
                     new Thread((new ThreandSyncSend(fps.get(0),Mac.get(0),fps.get(1),Mac.get(1)))).start();
                     //just for initialize two files

                     /*for (int i = 0; i < fps.size();i++){
                         new Thread( new ThreadSend1(fps.get(i),Mac.get(i),0));
                     }*/
                    // initialize multiple files
                 }
                 catch(Exception e){
                 }
                 //Start sending data tuples to server
             }
         });
         btnFile.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                 Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                 intent.addCategory(Intent.CATEGORY_OPENABLE);
                 intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                 intent.setType("*/*");
                 startActivityForResult(
                         Intent.createChooser(intent, "Open CSV"), 9999);
             }
             //Select xsens real-time log files from device
         });
     }
     @Override
     public void onActivityResult(int requestCode, int resultCode, Intent data) {
         super.onActivityResult(requestCode, resultCode, data);

         if (requestCode == 9999 && resultCode == Activity.RESULT_OK){
            if(null != data) {
                if (null != data.getClipData()) { // checking multiple selection or not
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        // get Android tree uri of selected file
                        String str = uri.getPath();

                        String[] temp = str.split("/");
                        //trim the file path and get file name and it's parent directory,
                        // but it's not a good way to solve this problem

                        String[] SensorMac = temp[temp.length-1].split("_");
                        //split the file name to get the sensor mac address
                        fps.add(Environment.getExternalStorageDirectory().getAbsolutePath()+
                                "/Android/data/com.xsens.dot.android/files/logs/"+
                                temp[temp.length - 2] +"/"+ temp[temp.length - 1]);

                        Mac.add(SensorMac[0]);
                    }
                }
                else {//Only select one file
                    Uri uri = data.getData();
                    String str = uri.getPath();
                    String[] temp = str.split("/");
                    String[] SensorMac = temp[temp.length-1].split("_");
                    fps.add(Environment.getExternalStorageDirectory().getAbsolutePath()+
                            "/Android/data/com.xsens.dot.android/files/logs/"+
                            temp[temp.length-2] +"/"+ temp[temp.length-1]);
                    Mac.add(SensorMac[0]);

                }
            }
         }
     }
     private PrintWriter output;
     private BufferedReader input;




     class ThreadConnect implements Runnable {
         public void run() {
             Socket socket;
             try {
                 socket = new Socket(SERVER_IP, SERVER_PORT);
                 output = new PrintWriter(socket.getOutputStream());
                 input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 runOnUiThread(new Runnable() {
                     @Override
                     public void run() {
                         tvMessages.setText("Connected\n");
                     }
                 });
             } catch (IOException e) {
                 e.printStackTrace();
             }
         }
     }
     private long ConvertingTime(long income){//Converting Timestamp
         if (income % 10 >= 5){
             income /= 10;
             income += 1;
         }
         else{
             income /= 10;
         }
         return  income;

     }

     class ThreandSyncSend implements Runnable{
         private  String fp1;
         private  String fp2;
         private  String ma1;
         private  String ma2;

         private long LeadingLines1;
         private long LeadingLines2;

         ThreandSyncSend (String csvf1, String csvmac1, String csvf2, String csvmac2){
             this.fp1 = csvf1;
             this.fp2 = csvf2;
             this.ma1 = csvmac1;
             this.ma2 = csvmac2;
             this.LeadingLines1 = 0;
             this.LeadingLines2 = 0;
         }

         @Override
         public void run(){
             File f1 = new File(fp1);
             File f2 = new File(fp2);
             if (f1.exists() && f2.exists()){
                 try {//checking data alignment, but it doesn't work
                     InputStream ISR1 = new FileInputStream(fp1);
                     BufferedReader Buff1 = new BufferedReader(new InputStreamReader(ISR1));
                     InputStream ISR2 = new FileInputStream(fp2);
                     BufferedReader Buff2 = new BufferedReader(new InputStreamReader(ISR2));
                     try {
                         String CsvLine1 = "";
                         String CsvLine2 = "";
                         for (int i = 0; i < 10; i++){
                             CsvLine1 = Buff1.readLine();
                             CsvLine2 = Buff2.readLine();
                         }
                         String[] SensorData1 = CsvLine1.split(", ");
                         String[] SensorData2 = CsvLine2.split(", ");
                         long curr1 = ConvertingTime(Long.parseLong(SensorData1[1]));
                         long curr2 = ConvertingTime(Long.parseLong(SensorData2[1]));
                         if (curr1 == curr2){

                         }
                         else if (curr1 > curr2){
                             while (curr1 > curr2){
                                 CsvLine2 = Buff2.readLine();
                                 SensorData2 = CsvLine2.split(", ");
                                 curr2 = ConvertingTime(Long.parseLong(SensorData2[1]));
                                 LeadingLines2++;
                             }
                         }
                         else if (curr2 > curr1){
                             while (curr2 > curr1){
                                 CsvLine1 = Buff1.readLine();
                                 SensorData1 = CsvLine1.split(", ");
                                 curr1 = ConvertingTime(Long.parseLong(SensorData1[1]));
                                 LeadingLines1++;
                             }
                         }
                     }
                     catch (Exception e) {output.write(e.toString());output.flush();
                     }
                     finally {
                         try {
                             ISR1.close();
                             ISR2.close();
                             new Thread (new ThreadSend1(fp1,ma1,LeadingLines2)).start();
                             new Thread (new ThreadSend2(fp2,ma2,LeadingLines1)).start();
                             //after checking alignment, start initialize two files
                         }
                         catch (IOException e) {output.write(e.toString());output.flush();
                         }
                     }
                 }
                 catch (FileNotFoundException e) {output.write(e.toString());output.flush();
                 }
             }
         }
     }
     class ThreadSend1 implements Runnable{//initialize file: skip bytes to the bottom of file
         private  String fp;
         private  String MacAddr;
         private long SkippedLines;
         private long Leadinglines;
         ThreadSend1(String csvf, String csvmac,long csvLeading){
             this.fp = csvf;
             this.MacAddr = csvmac;
             this.SkippedLines = 0;
             this.Leadinglines = csvLeading;
         }
         @Override
         public  void  run(){
             File f = new File(fp);
             if (f.exists()) {
                 try {
                     InputStream ISR = new FileInputStream(f);
                     BufferedReader Buff = new BufferedReader(new InputStreamReader(ISR));
                     try {
                         String CsvLine;
                         //Buff.skip(SkippedLines);
                         while ((CsvLine = Buff.readLine()) != null) {
                             SkippedLines += CsvLine.length();
                             //counting skip bytes
                         }
                     }
                     catch (Exception e) {
                     }
                     finally {
                         try {
                             ISR.close();
                             new Thread(new Send1Cont(fp,MacAddr,SkippedLines,Leadinglines)).start();
                             // start sending data tuple to server
                         }
                         catch (IOException e) {
                         }
                     }
                 }
                 catch (FileNotFoundException e) {
                 }
             }
         }
     }
     class  Send1Cont implements  Runnable{
         private  String fp;
         private  String MacAddr;
         private long SkippedLines;
         private long LeadingLines;
         Send1Cont(String csvf, String csvmac,long csvskip,long csvleading){
             this.fp = csvf;
             this.MacAddr = csvmac;
             this.SkippedLines = csvskip;
             this.LeadingLines = csvleading;
         }
         @Override
         public void run(){
             File f = new File(fp);
             try {
                 InputStream ISR = new FileInputStream(f);
                 BufferedReader Buff = new BufferedReader(new InputStreamReader(ISR));
                 try {
                     String CsvLine;
                     Buff.skip(SkippedLines);
                     //skip bytes that already used
                     while ((CsvLine = Buff.readLine()) != null) {
                             String[] SensorData = CsvLine.split(", ");
                             if (SensorData.length >= 9){
                                 SkippedLines += CsvLine.length();;

                                 Calendar c = Calendar.getInstance();
                                 JSONObject Jobj = new JSONObject();
                                 Jobj.put("Sensor", MacAddr);
                                 Jobj.put("TimeStamp", SensorData[1]);
                                 Jobj.put("QuatW", SensorData[2]);
                                 Jobj.put("QuatX", SensorData[3]);
                                 Jobj.put("QuatY", SensorData[4]);
                                 Jobj.put("QuatZ",SensorData[5]);
                                 Jobj.put("AccX", SensorData[6]);
                                 Jobj.put("AccY", SensorData[7]);
                                 Jobj.put("AccZ", SensorData[8]);
                                 Jobj.put("AndroidReadTime",
                                         c.get(Calendar.HOUR_OF_DAY)+":"+c.get(Calendar.MINUTE)+":"+c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND));
                                 //pack data tuple into JSON

                                 output.write("!"+Jobj.toString() + "!|");
                                 //send to server
                                 output.flush();
                             }
                     }
                 }
                 catch (Exception e) {
                     output.write(e.toString());
                     output.flush();
                 }
                 finally {
                     try {
                         ISR.close();
                         new Thread(new Send1Cont(fp,MacAddr,SkippedLines,LeadingLines)).start();
                     }
                     catch (IOException e) {
                         output.write(e.toString());
                         output.flush();
                     }
                 }
             }
             catch (FileNotFoundException e) {
                 output.write(e.toString());
                 output.flush();
             }
         }
     }
     class ThreadSend2 implements Runnable{
         private  String fp;
         private  String MacAddr;
         private long SkippedLines;
         private long LeadingLines;
         ThreadSend2(String csvf, String csvmac,long csvleading){
             this.fp = csvf;
             this.MacAddr = csvmac;
             this.SkippedLines = 0;
             this.LeadingLines = csvleading;
         }
         @Override
         public  void  run(){
             File f = new File(fp);
             if (f.exists()) {
                 try {
                     InputStream ISR = new FileInputStream(f);
                     BufferedReader Buff = new BufferedReader(new InputStreamReader(ISR));
                     try {
                         String CsvLine;

                         while ((CsvLine = Buff.readLine()) != null) {
                             SkippedLines += CsvLine.length();
                         }
                     }
                     catch (Exception e) {
                     }
                     finally {
                         try {
                             ISR.close();
                             new Thread(new Send2Cont(fp,MacAddr,SkippedLines,LeadingLines)).start();
                         }
                         catch (IOException e) {
                         }
                     }
                 }
                 catch (FileNotFoundException e) {
                 }

             }
         }
     }
     class  Send2Cont implements  Runnable{
         private  String fp;
         private  String MacAddr;
         private long SkippedLines;
         private long LeadingLines;
         Send2Cont(String csvf, String csvmac,long csvskip,long csvleading){
             this.fp = csvf;
             this.MacAddr = csvmac;
             this.SkippedLines = csvskip;
             this.LeadingLines = csvleading;
         }
         @Override
         public void run(){
             File f = new File(fp);
             try {
                 InputStream ISR = new FileInputStream(f);
                 BufferedReader Buff = new BufferedReader(new InputStreamReader(ISR));
                 try {
                     String CsvLine;
                     /*for (int i = 0; i < SkippedLines - LeadingLines; i++){
                         Buff.readLine();
                     }*/
                     Buff.skip(SkippedLines);
                     while ((CsvLine = Buff.readLine()) != null) {
                         //try{
                         String[] SensorData = CsvLine.split(", ");
                         if (SensorData.length >= 9){
                             SkippedLines += CsvLine.length();
                            Calendar c = Calendar.getInstance();
                             JSONObject Jobj = new JSONObject();
                             Jobj.put("Sensor", MacAddr);
                             Jobj.put("TimeStamp", SensorData[1]);
                             Jobj.put("QuatW", SensorData[2]);
                             Jobj.put("QuatX", SensorData[3]);
                             Jobj.put("QuatY", SensorData[4]);
                             Jobj.put("QuatZ",SensorData[5]);
                             Jobj.put("AccX", SensorData[6]);
                             Jobj.put("AccY", SensorData[7]);
                             Jobj.put("AccZ", SensorData[8]);
                             Jobj.put("AndroidReadTime",
                                     c.get(Calendar.HOUR_OF_DAY)+":"+c.get(Calendar.MINUTE)+":"+c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND));
                             output.write("@"+Jobj.toString() + "@|");
                             output.flush();
                         }
                     }
                 }
                 catch (Exception e) {
                     output.write(e.toString());
                     output.flush();
                 }
                 finally {
                     try {
                         ISR.close();
                         new Thread(new Send1Cont(fp,MacAddr,SkippedLines,LeadingLines)).start();
                     }
                     catch (IOException e) {
                         output.write(e.toString());
                         output.flush();
                     }
                 }
             }
             catch (FileNotFoundException e) {
                 output.write(e.toString());
                 output.flush();
             }
         }
     }
 }