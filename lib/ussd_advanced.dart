/// Run ussd code directly in your application
import 'dart:async';

import 'package:flutter/services.dart';

class UssdAdvanced {
  static const MethodChannel _channel =
      MethodChannel('method.com.phan_tech/ussd_advanced');
  //Initialize BasicMessageChannel
  static const BasicMessageChannel<String> _basicMessageChannel =
      BasicMessageChannel("message.com.phan_tech/ussd_advanced", StringCodec());

  static Future<void> sendUssd(
      {required String code, int subscriptionId = 1}) async {
    await _channel.invokeMethod(
        'sendUssd', {"subscriptionId": subscriptionId, "code": code});
  }

  static Future<String?> sendAdvancedUssd(
      {required String code, int subscriptionId = 1}) async {
    final String? response = await _channel
        .invokeMethod('sendAdvancedUssd',
            {"subscriptionId": subscriptionId, "code": code})
        .timeout(const Duration(seconds: 30))
        .catchError((e) {
          throw e;
        });
    return response;
  }

  static Future<String?> multisessionUssd(
      {required String code, int subscriptionId = 1}) async {
    var _codeItem = _CodeAndBody.fromUssdCode(code);
    String response = await _channel.invokeMethod('multisessionUssd', {
          "subscriptionId": subscriptionId,
          "code": _codeItem.code
        }).catchError((e) {
          throw e;
        }) ??
        '';

    if (_codeItem.messages != null) {
      var _res = await sendMultipleMessages(_codeItem.messages!);
      response += "\n$_res";
    }
    return response;
  }

  static Future<void> cancelSession() async {
    await _channel
        .invokeMethod(
      'multisessionUssdCancel',
    )
        .catchError((e) {
      throw e;
    });
  }

  static Future<String?> sendMessage(String message) async {
    var _response = await _basicMessageChannel.send(message).catchError((e) {
      throw e;
    });
    return _response;
  }

  static Future<String?> sendMultipleMessages(List<String> messages) async {
    var _response = "";
    for (var m in messages) {
      var _res = await sendMessage(m);
      _response += "\n$_res";
    }

    return _response;
  }

  static Stream<String?> onEnd() {
    StreamController<String?> _streamController = StreamController<String?>();
    _basicMessageChannel.setMessageHandler((message) async {
      _streamController.add(message);
      return message ?? '';
    });

    return _streamController.stream;
  }
}

class _CodeAndBody {
  _CodeAndBody(this.code, this.messages);

  _CodeAndBody.fromUssdCode(String input) {
    if (input.startsWith("*")) {
      // 🔹 Cas 1 : code du type *123*45#
      var core = input.substring(1, input.length - 1); // enlève * au début et # à la fin
      var items = core.split("*");

      code = '*${items[0]}#'; // racine (ex: *123#)
      messages = items.length > 1 ? items.sublist(1) : null;

    } else if (input.startsWith("#")) {
      // 🔹 Cas 2 : code du type #124#2#
      var core = input.substring(1, input.length - 1); // enlève # au début et à la fin
      var items = core.split("#");

      code = '#${items[0]}#'; // racine (ex: #124#)
      messages = items.length > 1 ? items.sublist(1) : null;

    } else {
      // 🔹 Cas non reconnu
      code = input;
      messages = null;
    }
  }

  late String code;
  List<String>? messages;
}
