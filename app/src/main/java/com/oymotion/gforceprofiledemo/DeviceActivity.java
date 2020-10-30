package com.oymotion.gforceprofiledemo;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.oymotion.gforceprofile.BluetoothDeviceStateEx;
import com.oymotion.gforceprofile.DataNotifFlags;
import com.oymotion.gforceprofile.GF_RET_CODE;
import com.oymotion.gforceprofile.GForceProfile;
import com.oymotion.gforceprofile.NotifDataType;
import com.oymotion.gforceprofile.ResponseResult;
import com.oymotion.gforceprofile.UserNotificationCallback;

import java.util.Arrays;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class DeviceActivity extends AppCompatActivity {
    @BindView(R.id.connect)
    Button btn_conncet;
    @BindView(R.id.start)
    Button btn_start;
    public static final String EXTRA_MAC_ADDRESS = "extra_mac_address";
    private BluetoothDevice bluetoothDevice;
    private BluetoothDeviceStateEx state = BluetoothDeviceStateEx.disconnected;
    private boolean setSucceeded = false;
    private boolean returnSucceeded = false;
    private String macAddress;
    private TextView textViewState;
    private TextView textViewQuaternion;
    private Handler handler;
    private Runnable runnable;
    private boolean notifying = false;
    private UserNotificationCallback notifyCb;

    private GForceProfile gForceProfile;

    @OnClick(R.id.connect)
    public void onConnectClick() {
        if (state == BluetoothDeviceStateEx.disconnected) {
            gForceProfile.setDevice(macAddress);
            GF_RET_CODE ret_code = gForceProfile.connect(false);

            if (ret_code != GF_RET_CODE.GF_SUCCESS) {
                Log.e("DeviceActivity", "Connect failed, ret_code: " + ret_code);
                textViewState.setText("Connect failed, ret_code: " + ret_code);
                return;
            }

            handler.removeCallbacks(runnable);
            handler.postDelayed(runnable, 500);
        } else {
            gForceProfile.disconnect();
        }

    }

    @OnClick(R.id.set)
    public void onSetClick() {
        if (state != BluetoothDeviceStateEx.ready || setSucceeded) return;

        GF_RET_CODE result = gForceProfile.setDataNotifSwitch(DataNotifFlags.DNF_QUATERNION, new UserNotificationCallback() {
            @Override
            public void onData(byte[] data) {
                Log.i("DeviceActivity", "onData: " + Arrays.toString(data));

                if (data[0] == ResponseResult.RSP_CODE_SUCCESS) {
                    returnSucceeded = true;

                    runOnUiThread(new Runnable() {
                        public void run() {
                            textViewState.setText("Device State: " + "Set Notification succeeded");
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            textViewState.setText("Device State: " + "Set Notification failed, resp code: " + data[0]);
                        }
                    });
                }
            }
        }, 5);

        if (result == GF_RET_CODE.GF_SUCCESS) {
            setSucceeded = true;
        } else {
            runOnUiThread(new Runnable() {
                public void run() {
                    textViewState.setText("Device State: " + "Set Notification failed: " + result.toString());
                }
            });
        }
    }

    @OnClick(R.id.start)
    public void onStartClick() {
        if (notifying) {
            btn_start.setText("Start Notification");

            gForceProfile.stopDataNotification(notifyCb);

            notifying = false;
        } else {
            if (state != BluetoothDeviceStateEx.ready || returnSucceeded == false) return;

            notifyCb = new UserNotificationCallback() {
                @Override
                public void onData(byte[] data) {
                    if (data[0] == NotifDataType.NTF_QUAT_FLOAT_DATA && data.length == 17) {
                        byte[] W = new byte[4];
                        byte[] X = new byte[4];
                        byte[] Y = new byte[4];
                        byte[] Z = new byte[4];

                        System.arraycopy(data, 1, W, 0, 4);
                        System.arraycopy(data, 1, X, 0, 4);
                        System.arraycopy(data, 1, Y, 0, 4);
                        System.arraycopy(data, 1, Z, 0, 4);

                        float w = getFloat(W);
                        float x = getFloat(X);
                        float y = getFloat(Y);
                        float z = getFloat(Z);

                        runOnUiThread(new Runnable() {
                            public void run() {
                                textViewQuaternion.setText("W: " + w + "\nX: " + x + "\nY: " + y + "\nZ: " + z);
                            }
                        });
                    }
                }
            };

            gForceProfile.startDataNotification(notifyCb);

            btn_start.setText("Stop Notification");
            notifying = true;
        }
    }

    public static float getFloat(byte[] b) {
        int accum = 0;
        accum = accum | (b[0] & 0xff) << 0;
        accum = accum | (b[1] & 0xff) << 8;
        accum = accum | (b[2] & 0xff) << 16;
        accum = accum | (b[3] & 0xff) << 24;
        System.out.println(accum);
        return Float.intBitsToFloat(accum);
    }

    void updateState() {
        BluetoothDeviceStateEx newState = gForceProfile.getState();
        if (state != newState) {
            runOnUiThread(new Runnable() {
                public void run() {
                    textViewState.setText("Device State: " + newState.toString());
                }
            });

            state = newState;

            if (state == BluetoothDeviceStateEx.disconnected) {
                btn_conncet.setText("Connect");
            } else if (state == BluetoothDeviceStateEx.ready || state == BluetoothDeviceStateEx.connected) {
                btn_conncet.setText("Disconnect");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);
        ButterKnife.bind(this);
        macAddress = getIntent().getStringExtra(EXTRA_MAC_ADDRESS);
        getSupportActionBar().setSubtitle(getString(R.string.mac_address, macAddress));
        handler = new Handler();
        runnable = new Runnable() {
            public void run() {
                updateState();
                handler.postDelayed(this, 500);
            }
        };

        gForceProfile = new GForceProfile(this);
        textViewState = this.findViewById(R.id.text_device_state);
        textViewQuaternion = this.findViewById(R.id.text_quaternion);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(runnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        gForceProfile.disconnect();
    }
}
