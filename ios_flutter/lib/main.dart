import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app_state.dart';
import 'ui/screens/shell_screen.dart';
import 'ui/theme/glass_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);

  final appState = CampusAppState();
  await appState.init();

  runApp(CampusPocketIosApp(appState: appState));
}

class CampusPocketIosApp extends StatelessWidget {
  final CampusAppState appState;

  const CampusPocketIosApp({super.key, required this.appState});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '冻小梨',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.system,
      theme: buildCampusTheme(Brightness.light),
      darkTheme: buildCampusTheme(Brightness.dark),
      home: ShellScreen(appState: appState),
    );
  }
}
