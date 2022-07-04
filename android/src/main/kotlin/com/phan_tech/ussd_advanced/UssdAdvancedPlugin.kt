package com.phan_tech.ussd_advanced

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.UssdResponseCallback
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.*
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.util.concurrent.CompletableFuture


/** UssdAdvancedPlugin */
class UssdAdvancedPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, BasicMessageChannel.MessageHandler<String?>  {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private var context: Context? = null
  private var activity: Activity? = null
  private var senderActivity: Activity? = null
  private val ussdApi: USSDApi = USSDController
  private var event: AccessibilityEvent? = null


  private lateinit var basicMessageChannel: BasicMessageChannel<String>


  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "method.com.phan_tech/ussd_advanced")
    channel.setMethodCallHandler(this)
    this.context = flutterPluginBinding.applicationContext
    basicMessageChannel  = BasicMessageChannel(
      flutterPluginBinding.binaryMessenger,
      "message.com.phan_tech/ussd_advanced", StringCodec.INSTANCE
    )
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }

  override fun onDetachedFromActivityForConfigChanges() {
    senderActivity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    senderActivity = binding.activity
  }

  private fun setListener(){
    basicMessageChannel.setMessageHandler(this)
  }

  override fun onMessage(message: String?, reply: BasicMessageChannel.Reply<String?>) {
    Log.d("PAL-USSD", "++++++++++++++++++++++++++++++++++++++++onMessage $message")

    if(message != null){
      USSDController.send2(message, event!!){
        event = AccessibilityEvent.obtain(it)
        Log.d("PAL-USSD", "++++++++++++++++++++++++++++++++++++++++onMessage : "+event.toString())

        try {
          if(it.text.isNotEmpty()) {
            Log.d("PAL-USSD", "++++++++++++++++++++++++++++++++++++++++onMessage try if: ${it.text}")
            reply.reply(it.text.first().toString())
          }else{
            Log.d("PAL-USSD", "++++++++++++++++++++++++++++++++++++++++onMessage try else")

            reply.reply(null)
          }
        } catch (e: Exception){
          Log.d("PAL-USSD", "++++++++++++++++++++++++++++++++++++++++onMessage exception: $e")

        }

      }
    }
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    Log.d("PAL-USSD", "--------------------------------onMethodCall"+call.method)

    var subscriptionId:Int = 1
    var code:String? = ""

    if(call.method == "sendUssd" ||call.method == "sendAdvancedUssd" ||call.method == "multisessionUssd"){
      val subscriptionIdInteger = call.argument<Int>("subscriptionId")
        ?: throw RequestParamsException(
          "Incorrect parameter type: `subscriptionId` must be an int"
        )
      subscriptionId = subscriptionIdInteger
      if (subscriptionId < -1 ) {
        throw RequestParamsException(
          "Incorrect parameter value: `subscriptionId` must be >= -1"
        )
      }
      code = call.argument<String>("code")
      if (code == null) {
        throw RequestParamsException("Incorrect parameter type: `code` must be a String")
      }
      if (code.isEmpty()) {
        throw RequestParamsException(
          "Incorrect parameter value: `code` must not be an empty string"
        )
      }
    }

    when (call.method) {
      "sendUssd" -> {
        result.success(defaultUssdService(code!!, subscriptionId))
      }
      "sendAdvancedUssd" -> {
        Log.d("PAL-USSD", "--------------------------------onMethodCall: sendAdvancedUssd")

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
          Log.d("PAL-USSD", "--------------------------------onMethodCall: sendAdvancedUssd if")

          val res = singleSessionUssd(code!!, subscriptionId)
          if(res != null){
            Log.d("PAL-USSD", "--------------------------------onMethodCall: res != null")

            res.exceptionally { e: Throwable? ->
              if (e is RequestExecutionException) {
                Log.d("PAL-USSD", "--------------------------------onMethodCall: e is RequestExecutionException")

                result.error(
                  RequestExecutionException.type, e.message, null
                )
              } else {
                Log.d("PAL-USSD", "--------------------------------onMethodCall: e is not RequestExecutionException")

                result.error(RequestExecutionException.type, e?.message, null)
              }
              null
            }.thenAccept(result::success);

          }else{
            Log.d("PAL-USSD", "--------------------------------onMethodCall: res == null")

            result.success(res);
          }
        }else{
          Log.d("PAL-USSD", "--------------------------------onMethodCall: sendAdvancedUssd else")

          result.success(defaultUssdService(code!!, subscriptionId))
        }
      }
      "multisessionUssd" -> {

        // check permissions
        if (ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
          if (!ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.CALL_PHONE)) {
            ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.CALL_PHONE), 2)
          }
        }else if (ContextCompat.checkSelfPermission(this.context!!, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
          if (!ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.READ_PHONE_STATE)) {
            ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.READ_PHONE_STATE), 2)
          }
        }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
          multisessionUssd(code!!, subscriptionId, result)

        }else{
          result.success(defaultUssdService(code!!, subscriptionId))
        }

      }
      "multisessionUssdCancel" ->{
        multisessionUssdCancel()
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    Log.d("PAL-USSD", "--------------------------------onDetachedFromEngine")

    channel.setMethodCallHandler(null)
  }

  private class RequestExecutionException internal constructor(override var message: String) :
    Exception() {
    companion object {
      var type = "ussd_plugin_ussd_execution_failure"
    }
  }

  private class RequestParamsException internal constructor(override var message: String) :
    Exception() {
    companion object {
      var type = "ussd_plugin_incorrect__parameters"
    }
  }




  // for android 8+
  private fun singleSessionUssd(ussdCode:String, subscriptionId:Int) : CompletableFuture<String>?{
    Log.d("PAL-USSD", "--------------------------------singleSessionUssd")

    // use defaulft sim
    val _useDefault: Boolean = subscriptionId == -1

    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
      var res:CompletableFuture<String> = CompletableFuture<String>()
      // check permissions
      if (ContextCompat.checkSelfPermission(this.context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.CALL_PHONE)) {
        } else {
          ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.CALL_PHONE), 2)
        }
      }

      // get TelephonyManager
      val tm = this.context!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

      val simManager: TelephonyManager = tm.createForSubscriptionId(subscriptionId)

      // callback
      val callback =
        object : UssdResponseCallback() {
          override fun onReceiveUssdResponse(
            telephonyManager: TelephonyManager, request: String, response: CharSequence
          ) {
            Log.d("PAL-USSD",
              "--------------------------------singleSessionUssd onReceiveUssdResponse $response"
            )
            res.complete(response.toString())
          }

          override fun onReceiveUssdResponseFailed(
            telephonyManager: TelephonyManager, request: String, failureCode: Int
          ) {
            Log.d("PAL-USSD",
              "--------------------------------singleSessionUssd onReceiveUssdResponse $failureCode"
            )

            when (failureCode) {
                      TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> {
                res.completeExceptionally(RequestExecutionException("USSD_ERROR_SERVICE_UNAVAIL"))
              }
              TelephonyManager.USSD_RETURN_FAILURE -> {
                res.completeExceptionally(RequestExecutionException("USSD_RETURN_FAILURE"))
              }
              else -> {
                res.completeExceptionally(RequestExecutionException("unknown error"))
              }
            }
          }
        }

      if(_useDefault){
        tm.sendUssdRequest(
          ussdCode,
          callback,
          Handler(Looper.getMainLooper())
        )

      }else{
        simManager.sendUssdRequest(
          ussdCode,
          callback,
          Handler(Looper.getMainLooper())
        )
      }

      Log.d("PAL-USSD",
        "--------------------------------singleSessionUssd onReceiveUssdResponse $res"
      )

      return res
    }
    else{
      // if sdk is less than 26
      Log.d("PAL-USSD", "--------------------------------singleSessionUssd is less than 26")

      defaultUssdService(ussdCode, subscriptionId)
      return  null
    }

  }

  private fun multisessionUssd(ussdCode:String, subscriptionId:Int, @NonNull result: Result){
    Log.d("PAL-USSD", "++++++++++++++++++++++++++++++++++++++++multisessionUssd")

    var slot = subscriptionId
    if(subscriptionId == -1){
      slot = 0
    }

    ussdApi.callUSSDInvoke(activity!!, ussdCode, slot, object : USSDController.CallbackInvoke {

      override fun responseInvoke(ev: AccessibilityEvent) {
        Log.d("PAL-USSD", "++++++++++++++++++++++++++++++++++++++++multisessionUssd responseInvoke")

        event = AccessibilityEvent.obtain(ev)
        setListener()

        try {
          Log.d("PAL-USSD", "++++++++++++++++++++++++++++++++++++++++multisessionUssd responseInvoke try")

          if(ev.text.isNotEmpty()) {
            Log.d("PAL-USSD", "++++++++++++++++++++++++++++++++++++++++multisessionUssd responseInvoke"+ev.text)
            result.success(ev.text.first().toString())
          }else{
            Log.d("PAL-USSD",
              "++++++++++++++++++++++++++++++++++++++++multisessionUssd responseInvoke v.text.isempty$result"
            )

            result.success(null)
          }
        }catch (e: Exception){
          Log.d("PAL-USSD",
            "++++++++++++++++++++++++++++++++++++++++multisessionUssd responseInvoke xception $e"
          )

        }
      }

      override fun over(message: String) {
        Log.d("PAL-USSD", "++++++++++++++++++++++++++++++++++++++++multisessionUssd over")
        try {
          basicMessageChannel.setMessageHandler(null)
          basicMessageChannel.send(message)
          Log.d("PAL-USSD", "++++++++++++++++++++++++++++++++++++++++multisessionUssd over $message")

          result.success(message)
        }catch (e: Exception){
          Log.d("PAL-USSD", "++++++++++++++++++++++++++++++++++++++++multisessionUssd over excception $message")
        }

      }
    })
  }

  private fun multisessionUssdCancel(){
    Log.d("PAL-USSD", "--------------------------------multisessionUssdCancel")

    if(event != null){
      basicMessageChannel.setMessageHandler(null)
      ussdApi.cancel2(event!!);
    }
  }

  private val simSlotName = arrayOf(
    "extra_asus_dial_use_dualsim",
    "com.android.phone.extra.slot",
    "slot",
    "simslot",
    "sim_slot",
    "subscription",
    "Subscription",
    "phone",
    "com.android.phone.DialingMode",
    "simSlot",
    "slot_id",
    "simId",
    "simnum",
    "phone_type",
    "slotId",
    "slotIdx"
  )

  // multiple for all
  private fun defaultUssdService(ussdCode:String, subscriptionId:Int){
    Log.d("PAL-USSD", "--------------------------------defaultUssdService")

    if (ContextCompat.checkSelfPermission(this.context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
      if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.CALL_PHONE)) {
      } else {
        ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.CALL_PHONE), 2)
      }
    }
    try {
      // use defaulft sim
      val _useDefault: Boolean = subscriptionId == -1

      val sim:Int = subscriptionId -1
      var number:String = ussdCode;
      number = number.replace("#", "%23");
      if (!number.startsWith("tel:")) {
        number = String.format("tel:%s", number);
      }
      val intent =
        Intent(if (isTelephonyEnabled()) Intent.ACTION_CALL else Intent.ACTION_VIEW)
      intent.data = Uri.parse(number)

      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);


      if(!_useDefault){
        intent.putExtra("com.android.phone.force.slot", true);
        intent.putExtra("Cdma_Supp", true);

        for (s in simSlotName) intent.putExtra(s, sim)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
          if (ContextCompat.checkSelfPermission(this.context!!, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.READ_PHONE_STATE)) {
            } else {
              ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.READ_PHONE_STATE), 2)
            }
          }
          val telecomManager = this.context!!.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

          val phoneAccountHandleList = telecomManager.callCapablePhoneAccounts
          if (phoneAccountHandleList != null && phoneAccountHandleList.isNotEmpty())
            intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE",
              phoneAccountHandleList[sim]
            );
        }
      }


      this.context!!.startActivity(intent)

    } catch (e: Exception) {
      throw e
    }
  }

  private fun isTelephonyEnabled(): Boolean {
    Log.d("PAL-USSD", "--------------------------------isTelephonyEnabled")

    val tm = this.context!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    Log.d("PAL-USSD",
      ("--------------------------------isTelephonyEnabled"+(tm.phoneType != TelephonyManager.PHONE_TYPE_NONE))
    )

    return tm.phoneType != TelephonyManager.PHONE_TYPE_NONE

  }




}
