package com.example.aircleaner;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    static final int REQUEST_ENABLE_BT = 10;        //블루투스 활성상태 식별자
    BluetoothAdapter mBluetoothAdapter;             //블루투스 어댑터
    int mPairedDeviceCount = 0;
    Set<BluetoothDevice> mDevices;                  //블루투스 디바이스 클래스의 집합형식 변수 선언
    BluetoothDevice mRemoteDevice;
    BluetoothSocket mSocket = null;
    OutputStream mOutputStream = null;
    InputStream mInputStream = null;
    Thread mWorkerThread = null;
    String mStrDelimiter = "\n";
    char mCharDelimiter = '\n';
    byte[] readBuffer;
    int readBufferPosition;
    TextView dust;
    ImageView imageView;
    ToggleButton toggleButton;
    ToggleButton sleeptoggle;
    Button btn;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        switch(requestCode){
            case REQUEST_ENABLE_BT:
                if(resultCode == RESULT_OK){        //블루투스 승인요청 확인 눌렀을 경우
                    selectDevice();
                }
                else if(resultCode == RESULT_CANCELED){     //블루투스 승인 요청 취소 눌렀을 경우
                    Toast.makeText(getApplicationContext(), "승인을 취소하였습니다.", Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    void checkBluetooth(){                //블루투스 체크
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();   //어댑터를 얻어옴
        if(mBluetoothAdapter == null){            //장치가 블루투스를 지원하지 않을 경우
            Toast.makeText(getApplicationContext(), "블루투스를 지원하지 않는 장비입니다.", Toast.LENGTH_LONG).show();
            finish();
        }
        else{                                         //장치가 블루투스를 지원하는 경우
            if(!mBluetoothAdapter.isEnabled()){      //비활성 상태인 경우
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);  //동의 구하는 다이얼로그
            }
            else{                               //활성 상태인경우
                selectDevice();
            }
        }
    }

    void selectDevice(){            //블루투스 승인요청 확인 했을 경우
        mDevices = mBluetoothAdapter.getBondedDevices();     //페어링된 장치 목록 얻어옴
        mPairedDeviceCount = mDevices.size();                //페어링된 장치 개수
        if(mPairedDeviceCount == 0){  //페어링된 장치가 없는 경우
            Toast.makeText(getApplicationContext(), "페어링된 장치가 없습니다.", Toast.LENGTH_LONG).show();
            finish();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);  //페어링된 장치목록을 보여줌 (다이얼로그)
        builder.setTitle("블루투스 장치 선택");        //다이얼로그 제목

        //페어링된 블루투스 장치의 이름 목록 작성
        List<String> listItems = new ArrayList<String>();
        for (BluetoothDevice device : mDevices){         //for문으로 리스트에 디바이스명을 저장
            listItems.add(device.getName());
        }
        listItems.add("취소");                            //취소항목 추가
        final CharSequence[] items = listItems.toArray(new CharSequence[listItems.size()]);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                if(item == mPairedDeviceCount){                   //취소를 누른경우
                    Toast.makeText(getApplicationContext(), "연결을 취소하였습니다.", Toast.LENGTH_LONG).show();
                    finish();
                }
                else{
                    connectToSelectedDevice(items[item].toString());    //선택한 페어링에 대해 소켓 생성 연결
                }
            }
        });
        builder.setCancelable(false);   //뒤로가기 버튼 금지
        AlertDialog alert = builder.create();
        alert.show();
    }

    void beginListenForDate(){                               //데이터 수신 준비 및 처리
        final Handler handler = new Handler();
        readBuffer = new byte[1024];                        //수신 버퍼
        readBufferPosition = 0;                             //버퍼 내 수신 문자 저장 위치
        mWorkerThread = new Thread(new Runnable() {         //문자열 수신 쓰레드
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {           //현재 쓰레드 상태가 인터럽트가 아닐경우
                    try {
                        int bytesAvailable = mInputStream.available();    //수신 데이터 확인
                        if (bytesAvailable > 0) {                          //데이터가 수신된 경우
                            byte[] packetBytes = new byte[bytesAvailable];
                            mInputStream.read(packetBytes);                //데이터 읽어옴
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == mCharDelimiter) {               //읽어온 데이터가 \n(끝) 일경우
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    //readBuffer의 내용을 처음부터 encodedBytes에 복사함
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "UTF-8");   //UTF-8형태로 인코딩
                                    readBufferPosition = 0;
                                    handler.post(new Runnable() {
                                        public void run() {
                                            char array[] = data.toCharArray();       //아두이노에서 읽어온 데이터를 array[]에 저장
                                            dust.setText(data);                   //텍스트뷰에 미세먼지값 출력
                                            if ((array[0])!= '0'){          //아두이노에서 데이터를 보냈을경우
                                                toggleButton.setChecked(true);                   //전원 ON
                                                if (Integer.parseInt(data) <= 50) {                //미세먼지 좋음
                                                    imageView.setImageResource(R.drawable.good);
                                                } else if (Integer.parseInt(data) <= 100) {        //미세먼지 보통
                                                    imageView.setImageResource(R.drawable.normal);
                                                } else {                                           //미세먼지 나쁨
                                                    imageView.setImageResource(R.drawable.bad);
                                                }
                                            }
                                            else {
                                                toggleButton.setChecked(false);          //전원 OFF
                                            }
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        finish();
                    }
                }
            }
        });
        mWorkerThread.start();      //스레드 시작
    }

    void sendData(String msg){                  //아두이노에 보낼 데이터
        msg += mStrDelimiter;                   //\n 끝값 추가
        try{
            mOutputStream.write(msg.getBytes());      //문자열 전송
        }catch (Exception e){
            Toast.makeText(getApplicationContext(), "값을 보내지 못했습니다.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    BluetoothDevice getDeviceFromBondedList(String name){        //페어링된 블루투스 장치 이름으로 찾기
        BluetoothDevice selectedDevice = null;
        for(BluetoothDevice device : mDevices){
            if(name.equals(device.getName())){              //디바이스 이름 비교
                selectedDevice = device;
                break;
            }
        }
        return selectedDevice;
    }

    @Override
    protected void onDestroy(){                        //소켓닫기 및 수신 쓰레드 종료
        try{
            mWorkerThread.interrupt();
            mInputStream.close();
            mOutputStream.close();
            mSocket.close();
        }catch (Exception e){}
        super.onDestroy();
    }

    void connectToSelectedDevice(String selectedDeviceName){            //블루투스 디바이스 연결
        mRemoteDevice = getDeviceFromBondedList(selectedDeviceName);    //페어링 선택된 장치의 이름을 가져온다.
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");       //SSP에 사용되는 UUID
        try{
            mSocket = mRemoteDevice.createRfcommSocketToServiceRecord(uuid);   //소켓 생성
            mSocket.connect();
            mOutputStream = mSocket.getOutputStream();       //데이터 송수신을 위한 스트림
            mInputStream = mSocket.getInputStream();
            beginListenForDate();                      //데이터 수신 처리
        }catch (Exception e){
            Toast.makeText(getApplicationContext(), "소켓 생성을 실패하였습니다.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dust = (TextView) findViewById(R.id.dustView);
        imageView = (ImageView) findViewById(R.id.imageView);
        toggleButton = (ToggleButton) findViewById(R.id.toggleButton);
        sleeptoggle = (ToggleButton)findViewById(R.id.Sleeptoggle);
        sleeptoggle.setEnabled(false);                   //토글버튼 기본값 설정(OFF)
        btn = (Button) findViewById(R.id.Endbutton);
        btn.setOnClickListener(this);
        checkBluetooth();                               //블루투스 환경 체크

        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked == true) {                      //전원버튼 활성화
                    Toast.makeText(MainActivity.this, "전원-ON", Toast.LENGTH_SHORT).show();
                    sendData("1");                      //아두이노에 전원ON(1) 메세지 전달
                    sleeptoggle.setEnabled(true);
                } else {                                      //전원버튼 비활성화
                    Toast.makeText(MainActivity.this, "전원-OFF", Toast.LENGTH_SHORT).show();
                    sendData("0");                      //아두이노에 전원OFF(0) 메세지 전달
                    sleeptoggle.setEnabled(false);
                    imageView.setImageResource(R.drawable.good);
                    dust.setText("0");
                }
            }
        });
        sleeptoggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(toggleButton.isChecked() == true){                //전원ON 일떄만 작동
                    if (isChecked == true) {                         //취침모드 활성화
                        Toast.makeText(MainActivity.this, "취침모드-ON", Toast.LENGTH_SHORT).show();
                        sendData("2");                         //아두이노에 취침모드ON(2) 메세지 전달
                    } else {                                         //취침모드 비활성화
                        Toast.makeText(MainActivity.this, "취침모드-OFF", Toast.LENGTH_SHORT).show();
                        sendData("3");                        //아두이노에 취침모드OFF(3) 메세지 전달
                    }
                }
            }
        });
    }
    public  void onClick(View v){
        switch(v.getId()){
            case R.id.Endbutton:                 //프로그램 종료버튼
                sendData("0");           //아두이노에 종료(0) 메세지 전달
                finish();
                break;
        }
    }
}
