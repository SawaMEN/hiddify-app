import 'package:material_ui/material_ui.dart';

/// Renders before preferences, translations and platform plugins load.
/// Animations never delay initialization or access those services.
class StartupApp extends StatelessWidget {
  const StartupApp({required this.initialization, super.key});
  final Future<Widget> initialization;

  @override
  Widget build(BuildContext context) => FutureBuilder<Widget>(
    future: initialization,
    builder: (context, snapshot) {
      if (snapshot.hasData) return snapshot.requireData;
      return MaterialApp(
        debugShowCheckedModeBanner: false,
        home: Scaffold(
          backgroundColor: const Color(0xFF020506),
          body: snapshot.hasError ? _StartupFailure(error: snapshot.error!) : const _StartupScene(),
        ),
      );
    },
  );
}

class _StartupScene extends StatefulWidget {
  const _StartupScene();
  @override
  State<_StartupScene> createState() => _StartupSceneState();
}

class _StartupSceneState extends State<_StartupScene> with TickerProviderStateMixin, WidgetsBindingObserver {
  late final AnimationController _entrance;
  late final AnimationController _rotor;
  late final CurvedAnimation _reveal;
  bool _reducedMotion = false;
  bool _foreground = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _entrance = AnimationController(vsync: this, duration: const Duration(milliseconds: 1100));
    _rotor = AnimationController(vsync: this, duration: const Duration(seconds: 4));
    _reveal = CurvedAnimation(parent: _entrance, curve: Curves.easeOutCubic);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _reducedMotion = MediaQuery.disableAnimationsOf(context);
    _syncAnimations();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final foreground = state == AppLifecycleState.resumed;
    if (_foreground == foreground) return;
    setState(() => _foreground = foreground);
    _syncAnimations();
  }

  void _syncAnimations() {
    if (_reducedMotion || !_foreground) {
      _entrance.stop();
      _rotor.stop();
      if (_reducedMotion) _entrance.value = 1;
    } else {
      if (!_entrance.isCompleted) _entrance.forward();
      if (!_rotor.isAnimating) _rotor.repeat();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _reveal.dispose();
    _entrance.dispose();
    _rotor.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final english = WidgetsBinding.instance.platformDispatcher.locale.languageCode == 'en';
    return TickerMode(enabled: _foreground, child: Stack(
      fit: StackFit.expand,
      children: [
        const RepaintBoundary(child: CustomPaint(painter: _StartupBackdrop())),
        SafeArea(child: Center(child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 380),
            child: FadeTransition(opacity: _reveal, child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                RepaintBoundary(child: RotationTransition(
                  key: const ValueKey('startup_rotor'),
                  turns: _rotor,
                  alignment: Alignment.center,
                  child: Image.asset('assets/images/logo.png', width: 176, height: 176, semanticLabel: 'VetrOFF'),
                )),
                const SizedBox(height: 28),
                SlideTransition(
                  position: Tween<Offset>(begin: const Offset(0, .35), end: Offset.zero).animate(_reveal),
                  child: ShaderMask(
                    shaderCallback: (bounds) => const LinearGradient(
                      colors: [Color(0xFFF3F8FA), Color(0xFF72DAFF), Color(0xFF229EFF)],
                    ).createShader(bounds),
                    blendMode: BlendMode.srcIn,
                    child: const Text('VetrOFF Client', textAlign: TextAlign.center, style: TextStyle(
                      fontFamily: 'Manrope', fontSize: 30, fontWeight: FontWeight.w800, color: Colors.white,
                    )),
                  ),
                ),
                const SizedBox(height: 36),
                SizedBox(width: 22, height: 22, child: CircularProgressIndicator(
                  value: _reducedMotion ? .75 : null, strokeWidth: 2, color: const Color(0xFF55D3FF),
                )),
                const SizedBox(height: 16),
                Text(english ? 'Starting' : 'Запуск', style: const TextStyle(
                  fontFamily: 'Manrope', fontSize: 15, color: Color(0xFF9BAEBB), letterSpacing: 2,
                )),
              ],
            )),
          ),
        ))),
      ],
    ));
  }
}

class _StartupBackdrop extends CustomPainter {
  const _StartupBackdrop();
  @override
  void paint(Canvas canvas, Size size) {
    final bounds = Offset.zero & size;
    canvas.drawRect(bounds, Paint()..shader = const RadialGradient(
      center: Alignment(0, -.4), radius: .85, colors: [Color(0xFF09232E), Color(0xFF020506)],
    ).createShader(bounds));
    final line = Paint()..color = const Color(0x092CC9FF)..strokeWidth = 1;
    for (double x = 0; x < size.width; x += 56) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), line);
    }
    for (double y = 0; y < size.height; y += 56) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), line);
    }

  }
  @override
  bool shouldRepaint(_StartupBackdrop oldDelegate) => false;
}

class _StartupFailure extends StatelessWidget {
  const _StartupFailure({required this.error});
  final Object error;
  @override
  Widget build(BuildContext context) {
    final english = WidgetsBinding.instance.platformDispatcher.locale.languageCode == 'en';
    const style = TextStyle(color: Color(0xFF9BAEBB));
    return SafeArea(child: Center(child: SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const Icon(Icons.error_outline, size: 48, color: Color(0xFF55D3FF)),
        const SizedBox(height: 16),
        Text(english ? 'Unable to start VetrOFF Client' : 'Не удалось запустить VetrOFF Client',
          style: const TextStyle(color: Colors.white)),
        const SizedBox(height: 16),
        SelectableText('$error', textAlign: TextAlign.center, style: style),
        const SizedBox(height: 16),
        Text(english ? 'Close and reopen the app to retry.' : 'Закройте и снова откройте приложение.', style: style),
      ]),
    )));
  }
}
