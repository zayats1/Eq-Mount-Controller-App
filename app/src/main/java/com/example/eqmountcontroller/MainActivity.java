package com.example.eqmountcontroller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.mtp.MtpDevice;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

import com.ederdoski.simpleble.interfaces.BleCallback;
import com.ederdoski.simpleble.models.BluetoothLE;
import com.ederdoski.simpleble.utils.BluetoothLEHelper;

import java.io.ByteArrayInputStream;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;

//FIXME NullPointerException with BluetoothGatt (need to debug with real phone)
public class MainActivity extends AppCompatActivity {
    private BluetoothLEHelper ble;
    BluetoothAdapter myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private final BluetoothDevice device = myBluetoothAdapter.getRemoteDevice("C8:E5:65:A2:AE:23"); // enter your device's MAC adress (JDY-23)
    // for new modules copy C8E565A2AE23 (AT+MACmac)

    private EditText ServoPositionDisplay;
    private EditText BetweenStepDelayDisplay;

    private ToggleButton ConnectionState;

    private SeekBar ServoPosSetter;
    private SeekBar BetweenStepDelaySetter;

    private final String Servo = "D";
    private final int ServoMinRotationAngle = 0;
    private final int ServoMaxRotationAngle = 270;

    private final int MaxBetweenStepDelay = 999; // F or B 999
    private final int MinBetweenStepDelay = 10;

    private final String myDeviceServiceUUID = "0000ffe0-0000-1000-8000-00805f9b34fb";
    private final String myDeviceServiceCharacteristicUUID = "0000ffe2-0000-1000-8000-00805f9b34fb";  // Write characteristic UUID

    private String stepper_motor_rotation_dir = "S";  // Motor doesn't rotate  by default   dir can be (- > "F", "B"< - , "S")
    private int stepper_motor_delay = MinBetweenStepDelay;


    private BleCallback myBleCallbacks() {

        return new BleCallback() {
            @Override
            public void onBleConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onBleConnectionStateChange(gatt, status, newState);

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connected to GATT server.", Toast.LENGTH_SHORT).show());
                }

                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Disconnected from GATT server.", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onBleServiceDiscovered(BluetoothGatt gatt, int status) {
                super.onBleServiceDiscovered(gatt, status);
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e("Ble ServiceDiscovered", "onServicesDiscovered received: " + status);
                }
            }

            @Override
            public void onBleCharacteristicChange(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onBleCharacteristicChange(gatt, characteristic);
                Log.i("BluetoothLEHelper", "onCharacteristicChanged Value: " + Arrays.toString(characteristic.getValue()));
            }

            @Override
            public void onBleRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onBleRead(gatt, characteristic, status);

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i("TAG", Arrays.toString(characteristic.getValue()));
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "onCharacteristicRead : " + Arrays.toString(characteristic.getValue()), Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onBleWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onBleWrite(gatt, characteristic, status);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "onCharacteristicWrite Status : " + status, Toast.LENGTH_SHORT).show());
            }
        };
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button stepperForwardMoving = (Button) findViewById(R.id.forward);
        Button stepperBackwardMoving = (Button) findViewById(R.id.backward);
        Button stepperStopMoving = (Button) findViewById(R.id.stop);

        ConnectionState = (ToggleButton) findViewById(R.id.toggleConnectionButton);

        ServoPositionDisplay = (EditText) findViewById(R.id.servo_position_display);
        BetweenStepDelayDisplay = (EditText) findViewById(R.id.between_steps_delay_display);

        ServoPosSetter = (SeekBar) findViewById(R.id.servo_pos_setter);
        ServoPosSetter.setMin(ServoMinRotationAngle);
        ServoPosSetter.setMax(ServoMaxRotationAngle);
        ServoPosSetter.setOnSeekBarChangeListener(ServoPosChangeListener);

        BetweenStepDelaySetter = (SeekBar) findViewById(R.id.between_steps_delay_setter);
        BetweenStepDelaySetter.setMin(MinBetweenStepDelay);
        BetweenStepDelaySetter.setMax(MaxBetweenStepDelay);
        BetweenStepDelaySetter.setOnSeekBarChangeListener(BetweenStepDelayChangeListener);

        ble = new BluetoothLEHelper(this);

        ConnectionState.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                try {
                    if (ble.isConnected()) {
                        ble.disconnect();
                        ConnectionState.setChecked(false);
                    } else {
                        ble.connect(device, myBleCallbacks());
                        ConnectionState.setChecked(true);
                    }
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "make sure the device is working correct ", Toast.LENGTH_SHORT).show());
                }
            }
        });

        stepperForwardMoving.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepper_motor_rotation_dir = "F";
                stepper_motor_control();
            }
        });

        stepperBackwardMoving.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepper_motor_rotation_dir = "B";
                stepper_motor_control();

            }
        });

        stepperStopMoving.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepper_motor_rotation_dir = "S";
                stepper_motor_control();
            }
        });

    }

    private final SeekBar.OnSeekBarChangeListener ServoPosChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            int servo_position_degree = seekBar.getProgress();
            ServoPositionDisplay.setText(String.valueOf(servo_position_degree));
            sendData(Servo, servo_position_degree);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }

    };

    private final SeekBar.OnSeekBarChangeListener BetweenStepDelayChangeListener = new SeekBar.OnSeekBarChangeListener() {


        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            stepper_motor_delay = seekBar.getProgress();
            stepper_motor_control();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    private void stepper_motor_control() {
        BetweenStepDelayDisplay.setText((String) (String.valueOf(stepper_motor_delay) + stepper_motor_rotation_dir));
        sendData(stepper_motor_rotation_dir, stepper_motor_delay);
    }

    private void sendData(String ctrl, int value) {
        String done = String.valueOf(value);
        try {
            if (ble.isConnected()) {
                if (value < 100 && value > 10) {
                    done = "0" + done;
                } else if (value < 10) {
                    done = "00" + done;
                }
                ble.write(myDeviceServiceUUID, myDeviceServiceCharacteristicUUID, ctrl + done + "\r\n");
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "make sure the device is working correct ", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ble.disconnect();
    }
}