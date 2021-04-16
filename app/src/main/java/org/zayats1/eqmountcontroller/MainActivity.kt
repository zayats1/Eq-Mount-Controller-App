package org.zayats1.eqmountcontroller

import androidx.appcompat.app.AppCompatActivity
import com.ederdoski.simpleble.utils.BluetoothLEHelper
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import com.ederdoski.simpleble.interfaces.BleCallback
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import java.util.*

class MainActivity : AppCompatActivity() {
    private var ble: BluetoothLEHelper? = null
    var myBluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // for new modules? change it's MAC adress to C8E565A2AE23 (AT+MACmac)
    private val device: BluetoothDevice? = this.myBluetoothAdapter?.getRemoteDevice("C8:E5:65:A2:AE:23") // enter your device's MAC adress (JDY-23)

    private val myDeviceServiceUUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
    private val myDeviceServiceCharacteristicUUID = "0000ffe2-0000-1000-8000-00805f9b34fb" // Write characteristic UUID

    private var ServoPositionDisplay: EditText? = null
    private var BetweenStepDelayDisplay: EditText? = null
    private var ConnectionState: ToggleButton? = null
    private var BetweenStepDelaySetter: SeekBar? = null

    private val Servo = "D"   // Digital servo
    private val ServoMinRotationAngle: Int = 20
    private val ServoMaxRotationAngle: Int = 180
    private var servo_current_position: Int = 90

    private val MinBetweenStepDelay: Int = 20
    private val MaxBetweenStepDelay: Int = 999 // F or B + S999

    private var messageSengingStatus: Int = 0
    private val maxTimeOut: Int = 100


    private var stepper_motor_rotation_dir = "S" // Motor doesn't rotate  by default   dir can be (- > "F", "B"< - , "S")
    private var stepper_motor_delay = MinBetweenStepDelay
    private fun myBleCallbacks(): BleCallback {
        return object : BleCallback() {
            override fun onBleConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onBleConnectionStateChange(gatt, status, newState)
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Connected to GATT server.", Toast.LENGTH_SHORT).show() }
                }
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Disconnected from GATT server.", Toast.LENGTH_SHORT).show() }
                }
            }

            override fun onBleServiceDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onBleServiceDiscovered(gatt, status)
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e("Ble ServiceDiscovered", "onServicesDiscovered received: $status")
                }
            }

            override fun onBleCharacteristicChange(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                super.onBleCharacteristicChange(gatt, characteristic)
                Log.i("BluetoothLEHelper", "onCharacteristicChanged Value: " + Arrays.toString(characteristic.value))
            }

            override fun onBleRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                super.onBleRead(gatt, characteristic, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i("TAG", Arrays.toString(characteristic.value))
                    runOnUiThread { Toast.makeText(this@MainActivity, "onCharacteristicRead : " + Arrays.toString(characteristic.value), Toast.LENGTH_SHORT).show() }
                }
            }

            override fun onBleWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                super.onBleWrite(gatt, characteristic, status)
                messageSengingStatus = status
                //debug only
                // runOnUiThread { Toast.makeText(this@MainActivity, "onCharacteristicWrite Status : $status", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val servoDownFaster = findViewById<View>(R.id.servo_down_faster) as Button
        val servoDownSlower = findViewById<View>(R.id.servo_down_slower) as Button
        val servoUpSlower = findViewById<View>(R.id.servo_up_slower) as Button
        val servoUpFaster = findViewById<View>(R.id.servo_up_faster) as Button

        val stepperForwardMoving = findViewById<View>(R.id.forward) as Button
        val stepperBackwardMoving = findViewById<View>(R.id.backward) as Button
        val stepperStopMoving = findViewById<View>(R.id.stop) as Button
        val stepperHoldMoving = findViewById<View>(R.id.hold) as Button

        ConnectionState = findViewById<View>(R.id.toggleConnectionButton) as ToggleButton
        ServoPositionDisplay = findViewById<View>(R.id.servo_position_display) as EditText
        BetweenStepDelayDisplay = findViewById<View>(R.id.between_steps_delay_display) as EditText
        BetweenStepDelaySetter = findViewById<View>(R.id.between_steps_delay_setter) as SeekBar
        BetweenStepDelaySetter!!.min = MinBetweenStepDelay
        BetweenStepDelaySetter!!.max = MaxBetweenStepDelay
        BetweenStepDelaySetter!!.setOnSeekBarChangeListener(BetweenStepDelayChangeListener)

        ble = BluetoothLEHelper(this)
        ConnectionState!!.setOnClickListener {
            try {
                if (ble!!.isConnected) {
                    ble!!.disconnect()
                    ConnectionState!!.isChecked = false
                } else {
                    ble!!.connect(device, myBleCallbacks())
                    if (ble!!.isConnected)
                        ConnectionState!!.isChecked = true

                }
            } catch (e: NullPointerException) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this@MainActivity, "make sure the device is working correct ", Toast.LENGTH_SHORT).show() }
            }

        }
        stepperForwardMoving.setOnClickListener {
            stepper_motor_rotation_dir = "F"
            stepper_motor_control()
        }
        stepperBackwardMoving.setOnClickListener {
            stepper_motor_rotation_dir = "B"
            stepper_motor_control()
        }
        stepperStopMoving.setOnClickListener {
            stepper_motor_rotation_dir = "S"
            stepper_motor_control()
        }
        stepperHoldMoving.setOnClickListener {
            stepper_motor_rotation_dir = "H"
            stepper_motor_control()
        }

        servoDownFaster.setOnClickListener {
            servo_position_change_on(-5)
        }

        servoDownSlower.setOnClickListener {
            servo_position_change_on(-1)
        }

        servoUpFaster.setOnClickListener {
            servo_position_change_on(5)
        }

        servoUpSlower.setOnClickListener {
            servo_position_change_on(1)
        }
    }


    private val BetweenStepDelayChangeListener: OnSeekBarChangeListener = object : OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            stepper_motor_delay = seekBar.progress
            stepper_motor_control()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        override fun onStopTrackingTouch(seekBar: SeekBar) {}
    }


    private fun stepper_motor_control() {
        BetweenStepDelayDisplay!!.setText((stepper_motor_delay.toString() + stepper_motor_rotation_dir))
        sendData(stepper_motor_rotation_dir, stepper_motor_delay)
    }

    private fun servo_position_change_on(diff_degree: Int) {
        servo_current_position += diff_degree
        if (servo_current_position < ServoMinRotationAngle) {
            servo_current_position = ServoMinRotationAngle
        } else if (servo_current_position > ServoMaxRotationAngle) {
            servo_current_position = ServoMaxRotationAngle
        }
        ServoPositionDisplay!!.setText(servo_current_position.toString())
        sendData(Servo, servo_current_position)
    }


    private fun sendData(ctrl: String, value: Int) {
        var done: String = value.toString()
        var timeOut = 0
        if (ble!!.isConnected) {
            try {
                when {
                    value in 10..99 -> done = "0$done"
                    value < 10 -> done = "00$done"
                }

                do {
                    ble!!.write(myDeviceServiceUUID, myDeviceServiceCharacteristicUUID, ctrl+ done)
                    timeOut++;
                    if (timeOut >= maxTimeOut) break
                } while (messageSengingStatus != 1)
                timeOut = 0

            } catch (e: NullPointerException) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this@MainActivity, "make sure the device is working correct ", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ble!!.disconnect()
    }
}