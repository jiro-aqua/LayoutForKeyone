package jp.gr.aqua.layoutforkeyone

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.input.InputDeviceIdentifier
import android.hardware.input.InputManager
import android.hardware.input.KeyboardLayout
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.InputDevice
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype

import com.android.internal.util.Preconditions
import rx.Observable

import java.text.Collator
import java.util.ArrayList
import java.util.Collections

class KeyboardEnumerator(val context : Context){

    data class KeyboardInfo(
            val deviceInfo: HardKeyboardDeviceInfo,
            val imi: InputMethodInfo,
            val imSubtype: InputMethodSubtype?,
            val layout: KeyboardLayout?){
        fun getDevice() : String {
            return this.deviceInfo.mDeviceName
        }
        fun getIme(context:Context) : String {
            return this.imi.loadLabel(context.packageManager).toString()
        }
    }

    fun showKeyboardLayoutScreen(info : KeyboardInfo) : Intent {
        return Intent(Intent.ACTION_MAIN).apply{
            setClassName("com.android.settings", "com.android.settings.Settings\$KeyboardLayoutPickerActivity")
            putExtra(EXTRA_INPUT_DEVICE_IDENTIFIER, info.deviceInfo.mDeviceIdentifier)
            putExtra(EXTRA_INPUT_METHOD_INFO, info.imi)
            putExtra(EXTRA_INPUT_METHOD_SUBTYPE, info.imSubtype)
        }
    }

    fun getKeyboards() : Observable<KeyboardInfo> {
        return Observable.create {
            subscriber->
            val imm = context.getSystemService(InputMethodManager::class.java)
            val im = context.getSystemService(InputManager::class.java)

            val hardKeyboards = hardKeyboards()

            if (imm != null && im != null) {
                for (deviceInfo in hardKeyboards) {
                    for (imi in imm.enabledInputMethodList) {
                        val subtypes = imm.getEnabledInputMethodSubtypeList(
                                imi, true /* allowsImplicitlySelectedSubtypes */)
                        if (subtypes.isEmpty()) {
                            // Here we use null to indicate that this IME has no subtype.
                            val nullSubtype: InputMethodSubtype? = null
                            val layout = im.getKeyboardLayoutForInputDevice(
                                    deviceInfo.mDeviceIdentifier, imi, nullSubtype)
                            subscriber.onNext(KeyboardInfo(deviceInfo,imi, nullSubtype, layout))
                            continue
                        }

                        // If the IME supports subtypes, we pick up "keyboard" subtypes only.
                        val N = subtypes.size
                        for (i in 0..N - 1) {
                            val subtype = subtypes[i]
                            if (!IM_SUBTYPE_MODE_KEYBOARD.equals(subtype.mode, ignoreCase = true)) {
                                continue
                            }
                            val layout = im.getKeyboardLayoutForInputDevice(
                                    deviceInfo.mDeviceIdentifier, imi, subtype)
                            subscriber.onNext(KeyboardInfo(deviceInfo,imi, subtype, layout))
                        }
                    }
                }
            }
            subscriber.onCompleted()
        }

    }

    class HardKeyboardDeviceInfo(
            deviceName: String?,
            val mDeviceIdentifier: InputDeviceIdentifier) {
        val mDeviceName: String

        init {
            mDeviceName = deviceName ?: ""
        }

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other == null) return false

            if (other !is HardKeyboardDeviceInfo) return false

            if (!TextUtils.equals(mDeviceName, other.mDeviceName)) {
                return false
            }
            if (mDeviceIdentifier.vendorId != other.mDeviceIdentifier.vendorId) {
                return false
            }
            if (mDeviceIdentifier.productId != other.mDeviceIdentifier.productId) {
                return false
            }
            if (!TextUtils.equals(mDeviceIdentifier.descriptor,
                    other.mDeviceIdentifier.descriptor)) {
                return false
            }

            return true
        }

        override fun hashCode(): Int{
            var result = mDeviceIdentifier.hashCode()
            result = 31 * result + mDeviceName.hashCode()
            return result
        }
    }



    private fun hardKeyboards() : ArrayList<HardKeyboardDeviceInfo>{
        val keyboards = ArrayList<HardKeyboardDeviceInfo>()
        val devicesIds = InputDevice.getDeviceIds()
        for (deviceId in devicesIds) {
            val device = InputDevice.getDevice(deviceId)
            if (device != null && !device.isVirtual && device.isFullKeyboard) {
                keyboards.add(HardKeyboardDeviceInfo(device.name, device.identifier))
            }
        }
        return keyboards
    }

    companion object {
        private val KEYBOARD_ASSISTANCE_CATEGORY = "keyboard_assistance_category"
        private val SHOW_VIRTUAL_KEYBOARD_SWITCH = "show_virtual_keyboard_switch"
        private val KEYBOARD_SHORTCUTS_HELPER = "keyboard_shortcuts_helper"
        private val IM_SUBTYPE_MODE_KEYBOARD = "keyboard"


        val EXTRA_INPUT_DEVICE_IDENTIFIER = "input_device_identifier"
        val EXTRA_INPUT_METHOD_INFO = "input_method_info"
        val EXTRA_INPUT_METHOD_SUBTYPE = "input_method_subtype"

    }


}



