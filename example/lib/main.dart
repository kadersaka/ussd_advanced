import 'package:flutter/material.dart';

import 'package:ussd_advanced/ussd_advanced.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  late TextEditingController _controller;
  late TextEditingController _numbercontroller;
  late TextEditingController _amountcontroller;
  String _response = '';


  // Future<void> _checkPermission() async {
  //   final permission = await Caller.checkPermission();
  //   print('Caller permission $permission');
  //   setState(() => hasPermission = permission);
  // }
  //
  // Future<void> _requestPermission() async {
  //   await Caller.requestPermissions();
  //   await _checkPermission();
  // }


  @override
  void initState() {
    super.initState();
    // _checkPermission();
    _controller = TextEditingController();
    _numbercontroller = TextEditingController();
    _amountcontroller = TextEditingController();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Ussd Test'),
        ),
        body: Container(
          padding: EdgeInsets.symmetric(horizontal: 15.0, vertical: 20.0),
          child: SingleChildScrollView(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              mainAxisSize: MainAxisSize.max,
              children: [
                // text input
                // TextField(
                //   controller: _controller,
                //   keyboardType: TextInputType.phone,
                //   decoration: const InputDecoration(labelText: 'USSD BENIN'),
                // ),
                // text input
                TextField(
                  controller: _numbercontroller,
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(labelText: 'numero de payement'),
                ),

                TextField(
                  controller: _amountcontroller,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'amount'),
                ),

                // dispaly responce if any
                if (_response != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    child: Text(_response),
                  ),

                // buttons
                Row(
                  mainAxisSize: MainAxisSize.max,
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    // ElevatedButton(
                    //   onPressed: () {
                    //     UssdAdvanced.sendUssd(
                    //         code: _controller.text, subscriptionId: 1);
                    //   },
                    //   child: const Text('norma\nrequest'),
                    // ),
                    ElevatedButton(
                      onPressed: () async {
                        String? _res = await UssdAdvanced.multisessionUssd(code: "*123#", subscriptionId: -1);
                        setState(() {
                          _response += '$_res \n';
                          _response += '--------------------------------\n';
                        });
                        print('--------------------------');
                        print(_res?.split("\n"));


                        String? _res2 = await UssdAdvanced.sendMessage('6');
                        setState(() {
                          _response += '--------------------------------\n';
                          _response += '$_res2 \n';
                        });
                        print('--------------------------');
                        print(_res2);


                      },
                      child: const Text('Benin'),
                    ),
                    ElevatedButton(
                      onPressed: () async {
                        setState(() {
                          _response += '*********************************************************************************\n';
                        });

/*
  *110#
  * 1
  * 1
  * numero
  * montant
  * pin 6235
  * 0
 */

                        String? _res = await UssdAdvanced.multisessionUssd(code: '110#', subscriptionId: -1);
                        setState(() {
                          _response += '$_res \n';
                          _response += '--------------------------------\n';
                        });
                        print('--------------------------');
                        print(_res?.split("\n"));


                        String? _res2 = await UssdAdvanced.sendMessage('1');
                        setState(() {
                          _response += '--------------------------------\n';
                          _response += '$_res2 \n';
                        });
                        print('--------------------------');
                        print(_res2);


                        String? _res3 = await UssdAdvanced.sendMessage('1');
                        setState(() {
                          _response += '--------------------------------\n';
                          _response += '$_res3 \n';
                        });
                        print('--------------------------');
                        print(_res3);


                        String? _res4 = await UssdAdvanced.sendMessage(_numbercontroller.text);
                        setState(() {
                          _response += '--------------------------------\n';
                          _response += '$_res4 \n';
                        });
                        print('--------------------------');
                        print(_res4);


                        String? _res5 = await UssdAdvanced.sendMessage(_amountcontroller.text);
                        setState(() {
                          _response += '--------------------------------\n';
                          _response += '$_res5 \n';
                        });
                        print('--------------------------');
                        print(_res5);


                        String? _res6 = await UssdAdvanced.sendMessage('6235');
                        setState(() {
                          _response += '--------------------------------\n';
                          _response += '$_res6 \n';
                        });
                        print('--------------------------');
                        print(_res6);


                        String? _res7 = await UssdAdvanced.sendMessage('0');
                        setState(() {
                          _response += '--------------------------------\n';
                          _response += '$_res7 \n';
                        });
                        print('--------------------------');
                        print(_res7);








                        await UssdAdvanced.cancelSession();
                      },
                      child: const Text('send request'),
                    ),
                  ],
                )
              ],
            ),
          ),
        ),
      ),
    );
  }
}
