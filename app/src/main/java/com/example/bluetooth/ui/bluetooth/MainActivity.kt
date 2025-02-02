package com.example.bluetooth.ui.bluetooth

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import com.example.bluetooth.BuildConfig
import com.example.bluetooth.databinding.ActivityMainBinding
import com.example.bluetooth.util.AppPermission.getPermissionList
import com.example.bluetooth.util.repeatOnStarted
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val viewModel: BluetoothViewModel by viewModels()

    private lateinit var itemAdapter: DeviceListAdapter

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { isGranted ->
            val list = mutableListOf<String>()
            isGranted.forEach { (permission, state) ->
                if (state) {
                    if (checkSelfPermission(ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_DENIED) {
                        permissionDialog(this)
                    }
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        Toast.makeText(this, "권한 설정을 하지 않으면 어플을 사용할 수 없습니다.", Toast.LENGTH_SHORT)
                            .show()
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID))
                        startActivity(intent)
                        finish()
                    }
                    list.add(permission)
                }
            }

            if(list.isNotEmpty()) {
                requestPermissions(list.toTypedArray(), 3)
            }
        }

    private val bluetoothEnableLaunch: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "블루투스를 활성화하였습니다.", Toast.LENGTH_SHORT)
                finish()
            } else {
                Toast.makeText(this, "블루투스를 활성화해주세요.", Toast.LENGTH_SHORT)
                finish()
            }
        }

    private val connectListener: (String) -> Unit = { address ->
        viewModel.connectListener(address)
    }

    private val disconnectListener: () -> Unit = {
        viewModel.disconnectListener()
    }

    private val permission = getPermissionList()

    private fun backgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestPermissions(
                this,
                arrayOf(
                    ACCESS_BACKGROUND_LOCATION,
                ), 3
            )
        }
    }

    private fun permissionDialog(context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("백그라운드 위치 권한을 항상 허용으로 설정해주세요.")

        val listener = DialogInterface.OnClickListener { _, p1 ->
            when (p1) {
                DialogInterface.BUTTON_POSITIVE ->
                    backgroundPermission()
            }
        }

        builder.setPositiveButton("네", listener)
        builder.setNegativeButton("아니오", null)

        builder.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish()
        }

        if (
            permission.all { permission ->
                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            if (checkSelfPermission(ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_DENIED) {
                permissionDialog(this)
            }
        } else {
            requestPermissionLauncher.launch(permission)
        }

        itemAdapter = DeviceListAdapter(connectListener, disconnectListener)

        binding.rvDeviceList.apply {
            adapter = itemAdapter
        }

        setDeviceObserve()
        setPermissionObserve()
        setBluetoothStateObserve()
        setActivityStateObserve()
        setLocationObserve()

        viewModel.setLocation()
        viewModel.setBluetoothService()
        viewModel.setBindBluetoothService()

    }

    private fun setDeviceObserve() {
        repeatOnStarted {
            viewModel.devices.collect {
                if(it.isEmpty()) {
                    binding.tvResult.visibility = View.VISIBLE
                    binding.tvResult.text = "검색결과가 없습니다."
                } else {
                    binding.tvResult.visibility = View.GONE
                }
                itemAdapter.submitList(it)
            }
        }
    }

    private fun setBluetoothStateObserve() {
        repeatOnStarted {
            viewModel.bluetoothState.collect {
                if (it) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBtIntent.putExtra("requestCode", -1)
                    bluetoothEnableLaunch.launch(enableBtIntent)
                }
            }
        }
    }

    private fun setPermissionObserve() {
        repeatOnStarted {
            viewModel.permission.collect {
                if(it) {
                    requestPermissionLauncher.launch(permission)
                }
            }
        }
    }

    private fun setActivityStateObserve() {
        repeatOnStarted {
            viewModel.activityState.collect {
                if(it) {
                    finish()
                }
            }
        }
    }

    private fun setLocationObserve() {
        repeatOnStarted {
            viewModel.location.collect {
                if(viewModel.isNear(it)) {
                    viewModel.scanBluetooth()
                } else {
                    viewModel.stopScan()
                    binding.tvResult.text = "지정 위치가 아닙니다."
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(
            grantResults.all {
                it == PackageManager.PERMISSION_DENIED
            }
        ) {
            finish()
        } else {
            if (checkSelfPermission(ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_DENIED) {
                permissionDialog(this)
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onPause() {
        super.onPause()
        viewModel.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.unregisterReceiver()
    }

}