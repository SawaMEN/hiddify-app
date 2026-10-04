import 'package:flutter/material.dart';

/// Render startup and failures before plugin and storage initialization finishes.
class StartupApp extends StatelessWidget {
  const StartupApp({required this.initialization, super.key});

  final Future<Widget> initialization;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Widget>(
      future: initialization,
      builder: (context, snapshot) {
        if (snapshot.hasData) return snapshot.requireData;
        return MaterialApp(
          initialRoute: '/',
          debugShowCheckedModeBanner: false,
          home: Scaffold(
            body: SafeArea(
              child: Center(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (snapshot.hasError) ...[
                        const Icon(Icons.error_outline, size: 48),
                        const SizedBox(height: 16),
                        const Text('Unable to start Hiddify'),
                        const SizedBox(height: 16),
                        SelectableText('${snapshot.error}', textAlign: TextAlign.center),
                        const SizedBox(height: 16),
                        const Text('Close and reopen the app to retry.'),
                      ] else ...[
                        const CircularProgressIndicator(),
                        const SizedBox(height: 24),
                        const Text('Starting Hiddify…'),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
